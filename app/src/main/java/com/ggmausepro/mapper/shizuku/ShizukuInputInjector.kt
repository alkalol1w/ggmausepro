package com.ggmausepro.mapper.shizuku

import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.InputEvent
import android.view.KeyEvent
import android.view.MotionEvent
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

/**
 * Shizuku ayrıcalığı ile doğrudan Android InputManager'a erişir.
 * Sıfır gecikme (Zero-Latency) ile dokunma ve fare hareketlerini simüle eder.
 */
object ShizukuInputInjector {
    private const val TAG = "ShizukuInputInjector"

    // INJECT_INPUT_EVENT_MODE_ASYNC = 0 (Gecikmesiz, anında döner)
    // INJECT_INPUT_EVENT_MODE_WAIT_FOR_RESULT = 1
    private const val INJECT_INPUT_EVENT_MODE_ASYNC = 0

    private var inputManagerInstance: Any? = null
    private var injectInputEventMethod: Method? = null
    private var isInitialized = false

    // Çoklu dokunma için pointer takibi (pointerId -> PointerState)
    data class PointerState(
        val pointerId: Int,
        var x: Float,
        var y: Float,
        var downTime: Long
    )

    private val activePointers = ConcurrentHashMap<Int, PointerState>()
    private var nextPointerId = 0

    fun initInjector(): Boolean {
        return try {
            val binder: IBinder = ShizukuManager.getInputServiceBinder() ?: return false
            val stubClass = Class.forName("android.hardware.input.IInputManager\$Stub")
            val asInterfaceMethod = stubClass.getMethod("asInterface", IBinder::class.java)
            inputManagerInstance = asInterfaceMethod.invoke(null, binder)

            val iInputManagerClass = Class.forName("android.hardware.input.IInputManager")
            injectInputEventMethod = iInputManagerClass.getMethod(
                "injectInputEvent",
                InputEvent::class.java,
                Int::class.javaPrimitiveType
            )

            isInitialized = true
            Log.d(TAG, "Shizuku Input Injector başarıyla başlatıldı (Zero-Latency mode)!")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Shizuku Input Injector başlatılamadı", e)
            isInitialized = false
            false
        }
    }

    fun onBinderDead() {
        inputManagerInstance = null
        injectInputEventMethod = null
        isInitialized = false
        activePointers.clear()
    }

    fun isReady(): Boolean = isInitialized && inputManagerInstance != null

    /**
     * Ham InputEvent enjekte eder (KeyEvent veya MotionEvent)
     */
    fun injectEvent(event: InputEvent): Boolean {
        if (!isInitialized || inputManagerInstance == null || injectInputEventMethod == null) {
            if (!initInjector()) return false
        }

        return try {
            val result = injectInputEventMethod?.invoke(
                inputManagerInstance,
                event,
                INJECT_INPUT_EVENT_MODE_ASYNC
            )
            result as? Boolean ?: true
        } catch (e: Exception) {
            Log.e(TAG, "Event inject hatası", e)
            false
        }
    }

    // ==========================================
    // DOKUNMA (TOUCH) İŞLEMLERİ
    // ==========================================

    /**
     * Anlık dokunma (Tap) simülasyonu
     */
    fun injectTap(x: Float, y: Float, durationMs: Long = 30) {
        val pointerId = allocatePointerId()
        val now = SystemClock.uptimeMillis()

        sendTouchEvent(MotionEvent.ACTION_DOWN, pointerId, x, y, now, now)

        if (durationMs > 0) {
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                val eventTime = SystemClock.uptimeMillis()
                sendTouchEvent(MotionEvent.ACTION_UP, pointerId, x, y, now, eventTime)
                releasePointerId(pointerId)
            }, durationMs)
        } else {
            sendTouchEvent(MotionEvent.ACTION_UP, pointerId, x, y, now, now + 10)
            releasePointerId(pointerId)
        }
    }

    /**
     * Basılı tutma başlangıcı (Touch Down)
     */
    fun injectTouchDown(pointerId: Int, x: Float, y: Float) {
        val now = SystemClock.uptimeMillis()
        activePointers[pointerId] = PointerState(pointerId, x, y, now)
        sendTouchEvent(MotionEvent.ACTION_DOWN, pointerId, x, y, now, now)
    }

    /**
     * Dokunma hareketi (Touch Move / Sürükleme / Analog joystick / Mouse Aim)
     */
    fun injectTouchMove(pointerId: Int, newX: Float, newY: Float) {
        val state = activePointers[pointerId]
        val now = SystemClock.uptimeMillis()
        val downTime = state?.downTime ?: now

        if (state != null) {
            state.x = newX
            state.y = newY
        } else {
            activePointers[pointerId] = PointerState(pointerId, newX, newY, now)
        }

        sendTouchEvent(MotionEvent.ACTION_MOVE, pointerId, newX, newY, downTime, now)
    }

    /**
     * Dokunmayı bırakma (Touch Up)
     */
    fun injectTouchUp(pointerId: Int, x: Float, y: Float) {
        val state = activePointers.remove(pointerId)
        val now = SystemClock.uptimeMillis()
        val downTime = state?.downTime ?: now

        sendTouchEvent(MotionEvent.ACTION_UP, pointerId, x, y, downTime, now)
    }

    /**
     * Düşük gecikmeli MotionEvent oluşturup gönderir
     */
    private fun sendTouchEvent(
        action: Int,
        pointerId: Int,
        x: Float,
        y: Float,
        downTime: Long,
        eventTime: Long
    ) {
        val pp = MotionEvent.PointerProperties().apply {
            id = pointerId
            toolType = MotionEvent.TOOL_TYPE_FINGER
        }
        val pc = MotionEvent.PointerCoords().apply {
            this.x = x
            this.y = y
            this.pressure = 1.0f
            this.size = 1.0f
        }

        val event = MotionEvent.obtain(
            downTime,
            eventTime,
            action,
            1,
            arrayOf(pp),
            arrayOf(pc),
            0,
            0,
            1.0f,
            1.0f,
            0,
            0,
            InputDevice.SOURCE_TOUCHSCREEN,
            0
        )

        injectEvent(event)
        event.recycle()
    }

    // ==========================================
    // KLAVYE / DONANIM TUŞLARI
    // ==========================================

    fun injectKeyEvent(keyCode: Int, action: Int) {
        val now = SystemClock.uptimeMillis()
        val event = KeyEvent(now, now, action, keyCode, 0, 0)
        injectEvent(event)
    }

    private fun allocatePointerId(): Int {
        var id = nextPointerId++
        if (nextPointerId > 9) nextPointerId = 0
        return id
    }

    private fun releasePointerId(id: Int) {
        activePointers.remove(id)
    }
}
