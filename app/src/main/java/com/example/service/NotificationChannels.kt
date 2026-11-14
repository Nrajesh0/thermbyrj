package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object NotificationChannels {
    const val TRACKER_CHANNEL_ID = "thermal_tracker_channel"
    const val ALERT_CHANNEL_ID = "thermal_alert_channel"
    const val GUARD_ALERT_CHANNEL_ID = "live_guard_alert_channel"
    const val SHRED_CHANNEL_ID = "file_shred_channel"

    fun ensureCreated(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val trackerChannel = NotificationChannel(
            TRACKER_CHANNEL_ID,
            "Background Monitor Status",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Persistent status indicator while background thermal & privacy monitoring is active"
        }

        val thermalAlertChannel = NotificationChannel(
            ALERT_CHANNEL_ID,
            "Thermal Overheat Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Warnings when device temperature reaches your elevated or critical threshold"
        }

        val guardAlertChannel = NotificationChannel(
            GUARD_ALERT_CHANNEL_ID,
            "Live Guard Privacy Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Security warnings when an app uses the camera, microphone, or screen recording"
        }

        val shredChannel = NotificationChannel(
            SHRED_CHANNEL_ID,
            "Secure File Shredder",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Progress and completion status for secure file shredding"
        }

        manager.createNotificationChannels(
            listOf(trackerChannel, thermalAlertChannel, guardAlertChannel, shredChannel)
        )
    }
}
