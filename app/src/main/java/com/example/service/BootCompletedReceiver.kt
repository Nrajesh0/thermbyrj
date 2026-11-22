package com.example.service

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

/**
 * Restores 24/7 Thermal & Live Guard monitoring after device reboot or app update
 * when the user has enabled Background Monitoring.
 *
 * On Android 15+ where `specialUse` foreground services may be restricted from
 * direct BOOT_COMPLETED start, falls back gracefully to a one-tap resume notification.
 */
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }

        val appContext = context.applicationContext ?: context
        val prefs = try {
            appContext.getSharedPreferences("thermal_prefs", Context.MODE_PRIVATE)
        } catch (_: Throwable) {
            return
        }
        if (!prefs.getBoolean("background_monitoring", true)) return

        val started = try {
            val svcIntent = Intent(appContext, ThermalTrackerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appContext.startForegroundService(svcIntent)
            } else {
                appContext.startService(svcIntent)
            }
            true
        } catch (_: Throwable) {
            false
        }

        if (!started) {
            postResumePromptNotification(appContext)
        }
    }

    private fun postResumePromptNotification(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            NotificationChannels.ensureCreated(context)
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            val pi = PendingIntent.getActivity(
                context,
                BOOT_RESUME_NOTIF_ID,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notif = NotificationCompat.Builder(context, NotificationChannels.TRACKER_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_dot)
                .setContentTitle(context.getString(R.string.notif_boot_resume_title))
                .setContentText(context.getString(R.string.notif_boot_resume_body))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build()
            nm.notify(BOOT_RESUME_NOTIF_ID, notif)
        } catch (_: Throwable) {
        }
    }

    companion object {
        private const val BOOT_RESUME_NOTIF_ID = 1009
    }
}
