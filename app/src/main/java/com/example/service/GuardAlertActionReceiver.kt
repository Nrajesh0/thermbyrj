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
import com.example.ui.screens.GuardRulesActivity

class GuardAlertActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val pkg = intent.getStringExtra(EXTRA_PACKAGE_NAME)
        val sensorName = intent.getStringExtra(EXTRA_SENSOR_TYPE) ?: GuardSensorType.MIC.name
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 1003)
        val monitor = LiveGuardMonitor.getInstance(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        when (action) {
            ACTION_SNOOZE_1H -> {
                val sensor = try {
                    GuardSensorType.valueOf(sensorName)
                } catch (_: Exception) {
                    GuardSensorType.MIC
                }
                val targetKey = if (!pkg.isNullOrBlank()) pkg else "SENSOR:${sensor.name}"
                val label = resolveAppLabel(context, pkg) ?: "All ${sensor.label} Alerts"
                monitor.snoozeTarget(targetKey, label, durationMs = 3_600_000L)
                nm.cancel(notificationId)
                postQuietConfirmation(
                    context = context,
                    nm = nm,
                    title = "Snoozed for 1 hour: $label",
                    text = "Tap to manage or resume in Guard Rules.",
                    undoAction = null
                )
            }

            ACTION_TRUST_APP -> {
                if (!pkg.isNullOrBlank()) {
                    val label = resolveAppLabel(context, pkg) ?: pkg
                    monitor.setAppTrust(
                        packageName = pkg,
                        sensors = GuardSensorType.entries.toSet(),
                        source = "Notification action"
                    )
                    nm.cancel(notificationId)

                    val undoIntent = Intent(context, GuardAlertActionReceiver::class.java).apply {
                        this.action = ACTION_UNDO_TRUST
                        putExtra(EXTRA_PACKAGE_NAME, pkg)
                        putExtra(EXTRA_NOTIFICATION_ID, CONFIRM_NOTIFICATION_ID)
                    }
                    val undoPi = PendingIntent.getBroadcast(
                        context,
                        pkg.hashCode(),
                        undoIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    postQuietConfirmation(
                        context = context,
                        nm = nm,
                        title = "Trusted $label in Live Guard",
                        text = "Alerts muted for $label. Tap to review rules.",
                        undoAction = NotificationCompat.Action.Builder(0, "Undo", undoPi).build()
                    )
                }
            }

            ACTION_UNDO_TRUST -> {
                if (!pkg.isNullOrBlank()) {
                    monitor.removeAppTrust(pkg)
                }
                nm.cancel(notificationId)
            }
        }
    }

    private fun resolveAppLabel(context: Context, pkg: String?): String? {
        if (pkg.isNullOrBlank()) return null
        return try {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (_: Exception) {
            pkg
        }
    }

    private fun postQuietConfirmation(
        context: Context,
        nm: NotificationManager,
        title: String,
        text: String,
        undoAction: NotificationCompat.Action?
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val openRulesIntent = Intent(context, GuardRulesActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val openPi = PendingIntent.getActivity(
            context,
            2041,
            openRulesIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        NotificationChannels.ensureCreated(context)
        val builder = NotificationCompat.Builder(context, NotificationChannels.GUARD_ALERT_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .setAutoCancel(true)
            .setTimeoutAfter(9000L)
            .setContentIntent(openPi)
        if (undoAction != null) {
            builder.addAction(undoAction)
        }
        nm.notify(CONFIRM_NOTIFICATION_ID, builder.build())
    }

    companion object {
        const val ACTION_SNOOZE_1H = "com.example.action.GUARD_SNOOZE_1H"
        const val ACTION_TRUST_APP = "com.example.action.GUARD_TRUST_APP"
        const val ACTION_UNDO_TRUST = "com.example.action.GUARD_UNDO_TRUST"

        const val EXTRA_PACKAGE_NAME = "extra_package_name"
        const val EXTRA_SENSOR_TYPE = "extra_sensor_type"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
        private const val CONFIRM_NOTIFICATION_ID = 1004
    }
}
