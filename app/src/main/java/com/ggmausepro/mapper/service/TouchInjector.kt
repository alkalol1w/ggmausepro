package com.ggmausepro.mapper.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.SystemClock
import android.util.Log
import com.ggmausepro.mapper.shizuku.ShizukuInputInjector
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Dokunma simülasyonu yöneticisi.
 * Shizuku aktif olduğunda doğrudan donanım seviyesinde ZERO-LATENCY (0 gecikme) ile çalışır.
 * Shizuku yoksa AccessibilityService fallback olarak çalışır.
 */
class TouchInjector(private val service: AccessibilityService?) {

    companion object {
        private const val TAG = "TouchInjector"
        private const val MAX_TOUCH_POINTS = 10
        private const val DEFAULT_TAP_DURATION = 35L
        private const val DEFAULT_LONG_PRESS_DURATION = 500L
        private const val DEFAULT_SWIPE_DURATION = 150L
        private const val DOUBLE_TAP_DELAY = 60L
        private const val CONTINUED_STROKE_SEGMENT_DURATION = 50L
    }

    private data class ActiveTouch(
        val id: Int,
        var currentX: Float,
        var currentY: Float,
        var isActive: Boolean = true,
        var gestureInProgress: Boolean = false,
        var lastDispatchTime: Long = SystemClock.uptimeMillis()
    )

    private val activeTouches = ConcurrentHashMap<Int, ActiveTouch>()
    private val touchIdGenerator = AtomicInteger(0)
    private val toggleStates = ConcurrentHashMap<Int, Boolean>()
    private val gestureLock = Any()

    private val defaultCallback = object : AccessibilityService.GestureResultCallback() {
        override fun onCompleted(gestureDescription: GestureDescription?) {}
        override fun onCancelled(gestureDescription: GestureDescription?) {}
    }

    /**
     * Tek tık (Tap) - Shizuku öncelikli, anında iletim
     */
    fun simulateTap(x: Float, y: Float, duration: Long = DEFAULT_TAP_DURATION) {
        if (ShizukuInputInjector.isReady()) {
            ShizukuInputInjector.injectTap(x, y, duration)
            return
        }

        if (service == null) return
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, duration, false)
        dispatchSingleStroke(stroke)
    }

    /**
     * Çift tık
     */
    fun simulateDoubleTap(x: Float, y: Float) {
        if (ShizukuInputInjector.isReady()) {
            ShizukuInputInjector.injectTap(x, y, 20)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                ShizukuInputInjector.injectTap(x, y, 20)
            }, 60)
            return
        }

        if (service == null) return
        val path1 = Path().apply { moveTo(x, y) }
        val stroke1 = GestureDescription.StrokeDescription(path1, 0L, DEFAULT_TAP_DURATION, false)

        val path2 = Path().apply { moveTo(x, y) }
        val stroke2 = GestureDescription.StrokeDescription(
            path2,
            DEFAULT_TAP_DURATION + DOUBLE_TAP_DELAY,
            DEFAULT_TAP_DURATION,
            false
        )

        val gesture = GestureDescription.Builder()
            .addStroke(stroke1)
            .addStroke(stroke2)
            .build()

        synchronized(gestureLock) {
            service.dispatchGesture(gesture, defaultCallback, null)
        }
    }

    /**
     * Kaydırma / Nişan alma / Kamera hareketi (Swipe)
     */
    fun simulateSwipe(
        fromX: Float, fromY: Float,
        toX: Float, toY: Float,
        duration: Long = DEFAULT_SWIPE_DURATION
    ) {
        if (ShizukuInputInjector.isReady()) {
            val pointerId = 7 // Nişan/Aim için özel pointer ID
            ShizukuInputInjector.injectTouchDown(pointerId, fromX, fromY)
            ShizukuInputInjector.injectTouchMove(pointerId, toX, toY)
            ShizukuInputInjector.injectTouchUp(pointerId, toX, toY)
            return
        }

        if (service == null) return
        val path = Path().apply {
            moveTo(fromX, fromY)
            lineTo(toX, toY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, duration, false)
        dispatchSingleStroke(stroke)
    }

    /**
     * Sürekli basma başlat (WASD hareket veya ateş tuşuna basılı tutma)
     */
    fun startContinuousTouch(id: Int, x: Float, y: Float): Int {
        val touchId = if (id >= 0) id else touchIdGenerator.incrementAndGet()

        if (ShizukuInputInjector.isReady()) {
            activeTouches[touchId] = ActiveTouch(touchId, x, y)
            ShizukuInputInjector.injectTouchDown(touchId % 10, x, y)
            return touchId
        }

        if (service == null) return touchId
        if (activeTouches.size >= MAX_TOUCH_POINTS) return -1

        val touch = ActiveTouch(touchId, x, y)
        activeTouches[touchId] = touch

        val path = Path().apply {
            moveTo(x, y)
            lineTo(x + 0.1f, y + 0.1f)
        }

        val stroke = GestureDescription.StrokeDescription(
            path,
            0L,
            CONTINUED_STROKE_SEGMENT_DURATION,
            true
        )

        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        touch.gestureInProgress = true

        synchronized(gestureLock) {
            service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    val t = activeTouches[touchId]
                    if (t != null && t.isActive) {
                        t.gestureInProgress = false
                        continueTouchIfNeeded(touchId)
                    }
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    activeTouches[touchId]?.let {
                        it.gestureInProgress = false
                        it.isActive = false
                    }
                }
            }, null)
        }

        return touchId
    }

    /**
     * Sürekli basılan noktayı hareket ettir (Analog joystick sürükleme)
     */
    fun moveContinuousTouch(id: Int, newX: Float, newY: Float) {
        val touch = activeTouches[id] ?: return
        touch.currentX = newX
        touch.currentY = newY

        if (ShizukuInputInjector.isReady()) {
            ShizukuInputInjector.injectTouchMove(id % 10, newX, newY)
            return
        }

        if (!touch.gestureInProgress) {
            continueTouchIfNeeded(id)
        }
    }

    /**
     * Sürekli basmayı sonlandır (Parmağı ekrandan kaldır)
     */
    fun endContinuousTouch(id: Int) {
        val touch = activeTouches.remove(id) ?: return
        touch.isActive = false

        if (ShizukuInputInjector.isReady()) {
            ShizukuInputInjector.injectTouchUp(id % 10, touch.currentX, touch.currentY)
            return
        }

        if (!touch.gestureInProgress) {
            finalizeTouch(id, touch)
        }
    }

    fun isToggleActive(keyCode: Int): Boolean = toggleStates[keyCode] == true

    fun toggle(keyCode: Int, x: Float, y: Float): Boolean {
        val wasActive = toggleStates[keyCode] == true
        return if (wasActive) {
            endContinuousTouch(keyCode)
            toggleStates[keyCode] = false
            false
        } else {
            startContinuousTouch(keyCode, x, y)
            toggleStates[keyCode] = true
            true
        }
    }

    fun endAllTouches() {
        if (ShizukuInputInjector.isReady()) {
            activeTouches.forEach { (id, touch) ->
                ShizukuInputInjector.injectTouchUp(id % 10, touch.currentX, touch.currentY)
            }
        }
        activeTouches.clear()
        toggleStates.clear()
    }

    private fun continueTouchIfNeeded(id: Int) {
        if (service == null) return
        val touch = activeTouches[id] ?: return
        if (!touch.isActive) {
            finalizeTouch(id, touch)
            return
        }
        if (touch.gestureInProgress) return

        val path = Path().apply {
            moveTo(touch.currentX, touch.currentY)
            lineTo(touch.currentX + 0.1f, touch.currentY + 0.1f)
        }

        val stroke = GestureDescription.StrokeDescription(
            path,
            0L,
            CONTINUED_STROKE_SEGMENT_DURATION,
            true
        )

        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        touch.gestureInProgress = true

        synchronized(gestureLock) {
            service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    val t = activeTouches[id]
                    if (t != null) {
                        t.gestureInProgress = false
                        if (t.isActive) {
                            continueTouchIfNeeded(id)
                        } else {
                            finalizeTouch(id, t)
                        }
                    }
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    activeTouches.remove(id)
                }
            }, null)
        }
    }

    private fun finalizeTouch(id: Int, touch: ActiveTouch) {
        if (service == null) return
        val path = Path().apply { moveTo(touch.currentX, touch.currentY) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, DEFAULT_TAP_DURATION, false)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        synchronized(gestureLock) {
            service.dispatchGesture(gesture, defaultCallback, null)
        }
    }

    private fun dispatchSingleStroke(stroke: GestureDescription.StrokeDescription) {
        if (service == null) return
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        synchronized(gestureLock) {
            service.dispatchGesture(gesture, defaultCallback, null)
        }
    }
}
