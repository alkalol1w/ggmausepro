package com.ggmausepro.mapper.manager

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.view.InputDevice
import android.view.MotionEvent
import com.ggmausepro.mapper.util.EventBus
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Manages mouse input capture for aiming/camera control.
 * Converts mouse delta X/Y movements to swipe gestures on the aim area of the screen.
 */
object MouseCaptureManager {

    private const val TAG = "MouseCaptureManager"
    private const val PREFS_NAME = "ggmausepro_mouse"

    // Sensitivity multiplier (higher = faster camera)
    var sensitivity: Float = 2.5f
        set(value) {
            field = value.coerceIn(0.1f, 20.0f)
            saveSettings()
        }

    // Aim area configuration
    var aimCenterX: Float = 0f
        private set
    var aimCenterY: Float = 0f
        private set
    var aimRadius: Float = 300f
        private set

    // Dead zone: mouse movements below this threshold are ignored
    var deadZone: Float = 1.5f
        set(value) {
            field = value.coerceIn(0f, 20f)
            saveSettings()
        }

    // Invert Y axis
    var invertY: Boolean = false
        set(value) {
            field = value
            saveSettings()
        }

    // Smoothing factor (0 = no smoothing, 1 = maximum smoothing)
    var smoothing: Float = 0.3f
        set(value) {
            field = value.coerceIn(0f, 0.95f)
            saveSettings()
        }

    // Whether mouse capture is currently active
    var isCaptured: Boolean = false
        private set

    // Screen dimensions
    private var screenWidth: Float = 1920f
    private var screenHeight: Float = 1080f

    // Current virtual aim position (where the next swipe will originate)
    private var currentAimX: Float = 0f
    private var currentAimY: Float = 0f

    // Previous smoothed delta for interpolation
    private var smoothedDeltaX: Float = 0f
    private var smoothedDeltaY: Float = 0f

    // Accumulated sub-pixel deltas
    private var accumulatedDeltaX: Float = 0f
    private var accumulatedDeltaY: Float = 0f

    private lateinit var prefs: SharedPreferences

    fun getSensitivity(): Float = sensitivity
    fun setSensitivity(value: Float) { sensitivity = value }
    fun isInvertY(): Boolean = invertY
    fun setInvertY(value: Boolean) { invertY = value }

    // Callback interface for processed mouse movements
    interface MouseMoveListener {
        fun onMouseMove(fromX: Float, fromY: Float, toX: Float, toY: Float, duration: Long)
    }

    private var moveListener: MouseMoveListener? = null

    /**
     * Initialize with context and screen dimensions.
     */
    fun init(context: Context) {
        screenWidth = context.resources.displayMetrics.widthPixels.toFloat()
        screenHeight = context.resources.displayMetrics.heightPixels.toFloat()

        // Default aim area is right-center of screen (where camera look typically is)
        aimCenterX = screenWidth * 0.65f
        aimCenterY = screenHeight * 0.40f
        aimRadius = min(screenWidth, screenHeight) * 0.30f

        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadSettings()

        // Reset aim position to center
        resetAimPosition()
    }

    /**
     * Set the listener for processed mouse movements.
     */
    fun setMoveListener(listener: MouseMoveListener?) {
        moveListener = listener
    }

    /**
     * Configure the aim area on screen.
     */
    fun setAimArea(centerX: Float, centerY: Float, radius: Float) {
        aimCenterX = centerX.coerceIn(0f, screenWidth)
        aimCenterY = centerY.coerceIn(0f, screenHeight)
        aimRadius = radius.coerceIn(50f, min(screenWidth, screenHeight) * 0.5f)
        resetAimPosition()
        saveSettings()
    }

    /**
     * Reset the virtual aim position to the aim area center.
     */
    fun resetAimPosition() {
        currentAimX = aimCenterX
        currentAimY = aimCenterY
        smoothedDeltaX = 0f
        smoothedDeltaY = 0f
        accumulatedDeltaX = 0f
        accumulatedDeltaY = 0f
    }

    /**
     * Start mouse capture mode.
     */
    fun startCapture() {
        isCaptured = true
        resetAimPosition()
        Log.d(TAG, "Mouse capture started")
    }

    /**
     * Stop mouse capture mode.
     */
    fun stopCapture() {
        isCaptured = false
        smoothedDeltaX = 0f
        smoothedDeltaY = 0f
        accumulatedDeltaX = 0f
        accumulatedDeltaY = 0f
        Log.d(TAG, "Mouse capture stopped")
    }

    /**
     * Process a raw mouse motion event.
     * Call this from InputMapperService.onMotionEvent() or a captured pointer callback.
     *
     * @param event The MotionEvent from the mouse
     * @return true if the event was consumed
     */
    fun processMouseMotion(event: MotionEvent): Boolean {
        if (!isCaptured) return false

        if (event.source and InputDevice.SOURCE_MOUSE == 0 &&
            event.source and InputDevice.SOURCE_MOUSE_RELATIVE == 0) {
            return false
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_HOVER_MOVE, MotionEvent.ACTION_MOVE -> {
                // Get relative mouse movement
                // AXIS_RELATIVE_X/Y are available on API 26+ with pointer capture
                var deltaX = event.getAxisValue(MotionEvent.AXIS_RELATIVE_X)
                var deltaY = event.getAxisValue(MotionEvent.AXIS_RELATIVE_Y)

                // Fallback: if relative axes are 0, try calculating from history
                if (deltaX == 0f && deltaY == 0f && event.historySize > 0) {
                    deltaX = event.x - event.getHistoricalX(event.historySize - 1)
                    deltaY = event.y - event.getHistoricalY(event.historySize - 1)
                }

                // If still 0 from HOVER, use raw coords relative to last known
                if (deltaX == 0f && deltaY == 0f) {
                    return false
                }

                processDeltas(deltaX, deltaY)
                return true
            }
        }
        return false
    }

    /**
     * Process raw mouse deltas directly (for use with pointer capture API).
     */
    fun processDeltas(rawDeltaX: Float, rawDeltaY: Float) {
        if (!isCaptured) return

        // Apply dead zone
        val dx = if (abs(rawDeltaX) < deadZone) 0f else rawDeltaX
        val dy = if (abs(rawDeltaY) < deadZone) 0f else rawDeltaY

        if (dx == 0f && dy == 0f) return

        // Apply sensitivity
        val scaledDx = dx * sensitivity
        val scaledDy = dy * sensitivity * (if (invertY) -1f else 1f)

        // Accumulate sub-pixel movement
        accumulatedDeltaX += scaledDx
        accumulatedDeltaY += scaledDy

        // Apply smoothing (exponential moving average)
        smoothedDeltaX = smoothedDeltaX * smoothing + accumulatedDeltaX * (1f - smoothing)
        smoothedDeltaY = smoothedDeltaY * smoothing + accumulatedDeltaY * (1f - smoothing)

        // Only dispatch if accumulated movement is significant (at least 1 pixel)
        if (abs(smoothedDeltaX) < 1f && abs(smoothedDeltaY) < 1f) return

        // Calculate target position
        val fromX = currentAimX
        val fromY = currentAimY
        val toX = (currentAimX + smoothedDeltaX).coerceIn(
            aimCenterX - aimRadius,
            aimCenterX + aimRadius
        )
        val toY = (currentAimY + smoothedDeltaY).coerceIn(
            aimCenterY - aimRadius,
            aimCenterY + aimRadius
        )

        // Check if we've hit the edge of aim area; if so, reset to center for infinite movement
        val atEdgeX = toX == aimCenterX - aimRadius || toX == aimCenterX + aimRadius
        val atEdgeY = toY == aimCenterY - aimRadius || toY == aimCenterY + aimRadius

        // Dispatch the swipe
        val distance = max(abs(toX - fromX), abs(toY - fromY))
        val duration = max(16L, min(50L, (distance / 5f).toLong()))

        moveListener?.onMouseMove(fromX, fromY, toX, toY, duration)

        // Update current aim position
        currentAimX = toX
        currentAimY = toY

        // Reset to center if at edge for continuous rotation
        if (atEdgeX || atEdgeY) {
            currentAimX = aimCenterX
            currentAimY = aimCenterY
        }

        // Reset accumulators
        accumulatedDeltaX = 0f
        accumulatedDeltaY = 0f
        smoothedDeltaX = 0f
        smoothedDeltaY = 0f
    }

    /**
     * Process a mouse button event.
     * @return the virtual keyCode for the button, or 0 if not a mouse button
     */
    fun processMouseButton(event: MotionEvent): Int {
        if (event.source and InputDevice.SOURCE_MOUSE == 0) return 0

        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP -> {
                when {
                    event.buttonState and MotionEvent.BUTTON_PRIMARY != 0 ||
                    event.actionButton == MotionEvent.BUTTON_PRIMARY ->
                        KeyMappingManager.MOUSE_LEFT_CLICK
                    event.buttonState and MotionEvent.BUTTON_SECONDARY != 0 ||
                    event.actionButton == MotionEvent.BUTTON_SECONDARY ->
                        KeyMappingManager.MOUSE_RIGHT_CLICK
                    event.buttonState and MotionEvent.BUTTON_TERTIARY != 0 ||
                    event.actionButton == MotionEvent.BUTTON_TERTIARY ->
                        KeyMappingManager.MOUSE_MIDDLE_CLICK
                    else -> 0
                }
            }
            MotionEvent.ACTION_BUTTON_PRESS, MotionEvent.ACTION_BUTTON_RELEASE -> {
                when (event.actionButton) {
                    MotionEvent.BUTTON_PRIMARY -> KeyMappingManager.MOUSE_LEFT_CLICK
                    MotionEvent.BUTTON_SECONDARY -> KeyMappingManager.MOUSE_RIGHT_CLICK
                    MotionEvent.BUTTON_TERTIARY -> KeyMappingManager.MOUSE_MIDDLE_CLICK
                    else -> 0
                }
            }
            else -> 0
        }
    }

    /**
     * Check if a MotionEvent is a mouse button press.
     */
    fun isMouseButtonDown(event: MotionEvent): Boolean {
        return event.actionMasked == MotionEvent.ACTION_DOWN ||
               event.actionMasked == MotionEvent.ACTION_BUTTON_PRESS
    }

    /**
     * Check if a MotionEvent is a mouse button release.
     */
    fun isMouseButtonUp(event: MotionEvent): Boolean {
        return event.actionMasked == MotionEvent.ACTION_UP ||
               event.actionMasked == MotionEvent.ACTION_BUTTON_RELEASE
    }

    // --- Persistence ---

    private fun loadSettings() {
        sensitivity = prefs.getFloat("sensitivity", 2.5f)
        deadZone = prefs.getFloat("dead_zone", 1.5f)
        invertY = prefs.getBoolean("invert_y", false)
        smoothing = prefs.getFloat("smoothing", 0.3f)
        aimCenterX = prefs.getFloat("aim_center_x", aimCenterX)
        aimCenterY = prefs.getFloat("aim_center_y", aimCenterY)
        aimRadius = prefs.getFloat("aim_radius", aimRadius)
    }

    private fun saveSettings() {
        if (!::prefs.isInitialized) return
        prefs.edit()
            .putFloat("sensitivity", sensitivity)
            .putFloat("dead_zone", deadZone)
            .putBoolean("invert_y", invertY)
            .putFloat("smoothing", smoothing)
            .putFloat("aim_center_x", aimCenterX)
            .putFloat("aim_center_y", aimCenterY)
            .putFloat("aim_radius", aimRadius)
            .apply()
    }
}
