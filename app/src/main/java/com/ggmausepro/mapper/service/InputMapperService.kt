package com.ggmausepro.mapper.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.accessibility.AccessibilityEvent
import com.ggmausepro.mapper.manager.ActionType
import com.ggmausepro.mapper.manager.KeyMapping
import com.ggmausepro.mapper.manager.KeyMappingManager
import com.ggmausepro.mapper.manager.MouseCaptureManager
import com.ggmausepro.mapper.util.EventBus
import java.util.concurrent.ConcurrentHashMap

/**
 * Core AccessibilityService that intercepts keyboard/mouse events and
 * dispatches touch gestures to map PC input to mobile touch controls.
 */
class InputMapperService : AccessibilityService(), MouseCaptureManager.MouseMoveListener {

    companion object {
        private const val TAG = "InputMapperService"

        @Volatile
        var instance: InputMapperService? = null
            private set

        @Volatile
        var isMappingActive: Boolean = false
            private set
    }

    private lateinit var touchInjector: TouchInjector
    private val handler = Handler(Looper.getMainLooper())

    // Track which keys are currently held down
    private val heldKeys = ConcurrentHashMap<Int, Boolean>()

    // Track active continuous movements (WASD)
    private val activeMovements = ConcurrentHashMap<Int, MovementState>()

    // Movement simulation interval in ms
    private val movementIntervalMs = 16L // ~60fps

    /**
     * State for continuous movement simulation (WASD keys).
     */
    private data class MovementState(
        val keyCode: Int,
        val mapping: KeyMapping,
        var isActive: Boolean = true,
        var runnable: Runnable? = null
    )

    override fun onCreate() {
        super.onCreate()
        instance = this
        KeyMappingManager.init(this)
        MouseCaptureManager.init(this)
        MouseCaptureManager.setMoveListener(this)
        touchInjector = TouchInjector(this)
        Log.i(TAG, "InputMapperService created")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()

        serviceInfo = serviceInfo.apply {
            // Request key events and motion events
            flags = flags or
                AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                flags = flags or AccessibilityServiceInfo.FLAG_REQUEST_FINGERPRINT_GESTURES
            }
        }

        EventBus.subscribe<EventBus.ToggleMappingRequest>(this) {
            toggleMapping()
        }

        EventBus.subscribe<EventBus.StopServiceRequest>(this) {
            stopMapping()
            disableSelf()
        }

        Log.i(TAG, "InputMapperService connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not used for our purposes, but required by the interface
    }

    override fun onInterrupt() {
        Log.w(TAG, "InputMapperService interrupted")
        stopMapping()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopMapping()
        MouseCaptureManager.setMoveListener(null)
        EventBus.unregister(this)
        instance = null
        Log.i(TAG, "InputMapperService destroyed")
    }

    /**
     * Intercept keyboard key events.
     * This is called on the service's main thread for low-latency processing.
     */
    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (!isMappingActive) return false

        val keyCode = event.keyCode

        // ESC key: send BACK gesture
        if (keyCode == KeyEvent.KEYCODE_ESCAPE && event.action == KeyEvent.ACTION_DOWN) {
            if (!event.isLongPress && event.repeatCount == 0) {
                performGlobalAction(GLOBAL_ACTION_BACK)
                return true
            }
        }

        val mapping = KeyMappingManager.getMapping(keyCode) ?: return false
        if (!mapping.enabled) return false

        return when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (event.repeatCount == 0) {
                    handleKeyDown(mapping)
                }
                true // Consume the event
            }
            KeyEvent.ACTION_UP -> {
                handleKeyUp(mapping)
                true // Consume the event
            }
            else -> false
        }
    }

    /**
     * Process generic motion events (mouse movement, scroll, etc.)
     */
    override fun onMotionEvent(event: MotionEvent) {
        if (!isMappingActive) return

        // Handle mouse movement for aiming
        if (MouseCaptureManager.processMouseMotion(event)) {
            return
        }

        // Handle mouse button clicks
        val mouseButton = MouseCaptureManager.processMouseButton(event)
        if (mouseButton != 0) {
            val mapping = KeyMappingManager.getMapping(mouseButton) ?: return
            if (!mapping.enabled) return

            if (MouseCaptureManager.isMouseButtonDown(event)) {
                handleKeyDown(mapping)
            } else if (MouseCaptureManager.isMouseButtonUp(event)) {
                handleKeyUp(mapping)
            }
        }
    }

    /**
     * Handle a key being pressed down.
     */
    private fun handleKeyDown(mapping: KeyMapping) {
        if (heldKeys[mapping.keyCode] == true) return // Already held
        heldKeys[mapping.keyCode] = true

        when (mapping.action) {
            ActionType.TAP -> {
                touchInjector.simulateTap(mapping.screenX, mapping.screenY)
            }

            ActionType.HOLD -> {
                touchInjector.startContinuousTouch(
                    mapping.keyCode,
                    mapping.screenX,
                    mapping.screenY
                )
            }

            ActionType.SWIPE -> {
                startContinuousMovement(mapping)
            }

            ActionType.TOGGLE -> {
                touchInjector.toggle(
                    mapping.keyCode,
                    mapping.screenX,
                    mapping.screenY
                )
            }
        }
    }

    /**
     * Handle a key being released.
     */
    private fun handleKeyUp(mapping: KeyMapping) {
        heldKeys.remove(mapping.keyCode)

        when (mapping.action) {
            ActionType.TAP -> {
                // Tap already dispatched on key down, nothing to do
            }

            ActionType.HOLD -> {
                touchInjector.endContinuousTouch(mapping.keyCode)
            }

            ActionType.SWIPE -> {
                stopContinuousMovement(mapping.keyCode)
            }

            ActionType.TOGGLE -> {
                // Toggle state was changed on key down, nothing to do on key up
            }
        }
    }

    /**
     * Start continuous swipe movement for WASD-style keys.
     * Repeatedly dispatches swipe gestures while the key is held.
     */
    private fun startContinuousMovement(mapping: KeyMapping) {
        // Stop any existing movement for this key
        stopContinuousMovement(mapping.keyCode)

        val state = MovementState(mapping.keyCode, mapping)

        val runnable = object : Runnable {
            override fun run() {
                if (!state.isActive || !isMappingActive) {
                    activeMovements.remove(mapping.keyCode)
                    return
                }

                // Combine with other active movements for diagonal movement
                val combinedX = calculateCombinedMovementX()
                val combinedY = calculateCombinedMovementY()

                if (combinedX != 0f || combinedY != 0f) {
                    val startX = mapping.screenX
                    val startY = mapping.screenY
                    val endX = startX + combinedX
                    val endY = startY + combinedY

                    touchInjector.simulateSwipe(
                        startX, startY, endX, endY,
                        movementIntervalMs
                    )
                }

                handler.postDelayed(this, movementIntervalMs)
            }
        }

        state.runnable = runnable
        activeMovements[mapping.keyCode] = state

        // Start immediately
        handler.post(runnable)
    }

    /**
     * Stop continuous movement for a specific key.
     */
    private fun stopContinuousMovement(keyCode: Int) {
        val state = activeMovements.remove(keyCode) ?: return
        state.isActive = false
        state.runnable?.let { handler.removeCallbacks(it) }
    }

    /**
     * Stop all continuous movements.
     */
    private fun stopAllContinuousMovements() {
        activeMovements.keys.toList().forEach { stopContinuousMovement(it) }
    }

    /**
     * Calculate combined X movement from all active WASD movements.
     */
    private fun calculateCombinedMovementX(): Float {
        var totalX = 0f
        activeMovements.values.forEach { state ->
            if (state.isActive) {
                totalX += (state.mapping.swipeEndX - state.mapping.screenX)
            }
        }
        return totalX
    }

    /**
     * Calculate combined Y movement from all active WASD movements.
     */
    private fun calculateCombinedMovementY(): Float {
        var totalY = 0f
        activeMovements.values.forEach { state ->
            if (state.isActive) {
                totalY += (state.mapping.swipeEndY - state.mapping.screenY)
            }
        }
        return totalY
    }

    /**
     * Callback from MouseCaptureManager for processed mouse movements.
     */
    override fun onMouseMove(fromX: Float, fromY: Float, toX: Float, toY: Float, duration: Long) {
        if (!isMappingActive) return
        touchInjector.simulateSwipe(fromX, fromY, toX, toY, duration)
    }

    /**
     * Toggle mapping active/inactive.
     */
    fun toggleMapping() {
        if (isMappingActive) {
            stopMapping()
        } else {
            startMapping()
        }
    }

    /**
     * Activate key mapping.
     */
    fun startMapping() {
        isMappingActive = true
        MouseCaptureManager.startCapture()
        EventBus.post(EventBus.MappingStateChanged(true))
        Log.i(TAG, "Mapping activated")
    }

    /**
     * Deactivate key mapping and release all touches.
     */
    fun stopMapping() {
        isMappingActive = false
        MouseCaptureManager.stopCapture()
        stopAllContinuousMovements()
        touchInjector.endAllTouches()
        heldKeys.clear()
        EventBus.post(EventBus.MappingStateChanged(false))
        Log.i(TAG, "Mapping deactivated")
    }
}
