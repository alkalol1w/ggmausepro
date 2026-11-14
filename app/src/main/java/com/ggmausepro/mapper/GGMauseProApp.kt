package com.ggmausepro.mapper

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.ggmausepro.mapper.manager.KeyMappingManager
import com.ggmausepro.mapper.shizuku.ShizukuManager

class GGMauseProApp : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Bildirim kanallarını oluştur
        createNotificationChannels()

        // Shizuku yöneticisini başlat
        ShizukuManager.init()

        // Tuş atama yöneticisini başlat
        KeyMappingManager.init(this)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_OVERLAY_ID,
                "GGMausePro Overlay Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Klavye ve mouse eşleme servisi arka plan bildirimi"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_OVERLAY_ID = "ggmausepro_overlay_channel"
        lateinit var instance: GGMauseProApp
            private set
    }
}
