package com.ggmausepro.mapper.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.ggmausepro.mapper.GGMauseProApp
import com.ggmausepro.mapper.R
import com.ggmausepro.mapper.activity.MainActivity
import com.ggmausepro.mapper.manager.KeyMapping
import com.ggmausepro.mapper.manager.KeyMappingManager
import com.ggmausepro.mapper.shizuku.ShizukuManager
import com.ggmausepro.mapper.util.EventBus

class OverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var floatButtonView: View? = null
    private val overlayKeyViews = mutableListOf<View>()
    private var isEditMode = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        startForeground(NOTIFICATION_ID, createNotification())
        showFloatButton()

        EventBus.subscribe<EventBus.ToggleMappingRequest>(this) {
            updateFloatButtonState()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_TOGGLE_EDIT_MODE) {
            toggleEditMode()
        }
        return START_STICKY
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, GGMauseProApp.CHANNEL_OVERLAY_ID)
            .setContentTitle("GGMausePro Aktif")
            .setContentText("Klavye ve Fare eşleme servisi arka planda çalışıyor.")
            .setSmallIcon(R.drawable.indicator_active)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showFloatButton() {
        if (floatButtonView != null) return

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 50
            y = 200
        }

        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.overlay_control_button, null)
        floatButtonView = view

        val btnIcon = view.findViewById<ImageView>(R.id.btnControlIcon)
        val btnEdit = view.findViewById<ImageView>(R.id.btnEditLayout)

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isMoving = false

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isMoving = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                        isMoving = true
                        params.x = initialX + dx
                        params.y = initialY + dy
                        windowManager?.updateViewLayout(view, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isMoving) {
                        // Tıklama: Mapping aç/kapat
                        InputMapperService.instance?.toggleMapping()
                        updateFloatButtonState()
                    }
                    true
                }
                else -> false
            }
        }

        btnEdit?.setOnClickListener {
            toggleEditMode()
        }

        windowManager?.addView(view, params)
        updateFloatButtonState()
    }

    private fun updateFloatButtonState() {
        floatButtonView?.let { view ->
            val icon = view.findViewById<ImageView>(R.id.btnControlIcon)
            val isActive = InputMapperService.isMappingActive
            icon?.setImageResource(
                if (isActive) R.drawable.indicator_active else R.drawable.indicator_inactive
            )
        }
    }

    /**
     * Düzenleme modunu açar/kapatır.
     * Düzenleme modunda tuşlar ekranda sürüklenebilir baloncuklar olarak görünür.
     */
    fun toggleEditMode() {
        isEditMode = !isEditMode
        if (isEditMode) {
            showKeyOverlayElements()
        } else {
            hideKeyOverlayElements()
            // Pozisyonları kaydet
            KeyMappingManager.saveCurrentProfile()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showKeyOverlayElements() {
        hideKeyOverlayElements()

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val mappings = KeyMappingManager.getCurrentMappings()
        val inflater = LayoutInflater.from(this)

        for (mapping in mappings) {
            val keyView = inflater.inflate(R.layout.overlay_key_button, null)
            val keyLabel = keyView.findViewById<TextView>(R.id.txtKeyLabel)
            keyLabel.text = mapping.label.ifEmpty { mapping.keyName }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = mapping.screenX.toInt()
                y = mapping.screenY.toInt()
            }

            var initialX = 0
            var initialY = 0
            var initialTouchX = 0f
            var initialTouchY = 0f

            keyView.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()
                        params.x = initialX + dx
                        params.y = initialY + dy
                        mapping.screenX = params.x.toFloat()
                        mapping.screenY = params.y.toFloat()
                        windowManager?.updateViewLayout(keyView, params)
                        true
                    }
                    else -> false
                }
            }

            windowManager?.addView(keyView, params)
            overlayKeyViews.add(keyView)
        }
    }

    private fun hideKeyOverlayElements() {
        for (view in overlayKeyViews) {
            try {
                windowManager?.removeView(view)
            } catch (e: Exception) {
                // Ignore
            }
        }
        overlayKeyViews.clear()
    }

    override fun onDestroy() {
        super.onDestroy()
        hideKeyOverlayElements()
        floatButtonView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                // Ignore
            }
        }
        floatButtonView = null
        EventBus.unregister(this)
    }

    companion object {
        const val NOTIFICATION_ID = 2001
        const val ACTION_TOGGLE_EDIT_MODE = "com.ggmausepro.mapper.TOGGLE_EDIT_MODE"
    }
}
