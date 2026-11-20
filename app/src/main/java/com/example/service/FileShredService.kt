package com.example.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.formatBytes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class FileShredService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + safeCoroutineExceptionHandler())
    private var collectorJob: Job? = null

    companion object {
        private const val SHRED_CHANNEL_ID = NotificationChannels.SHRED_CHANNEL_ID
        private const val SHRED_NOTIF_ID = 2001
        private const val SHRED_DONE_NOTIF_ID = 2002
    }

    override fun onCreate() {
        super.onCreate()
        try {
            createChannel()
        } catch (_: Throwable) {
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val initialNotif = buildProgressNotification("Preparing secure file shredder...", 0, 1, indeterminate = true)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceCompat.startForeground(
                    this,
                    SHRED_NOTIF_ID,
                    initialNotif,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(SHRED_NOTIF_ID, initialNotif)
            }
        } catch (_: Throwable) {
            try {
                stopSelf()
            } catch (_: Throwable) {
            }
            return START_NOT_STICKY
        }

        if (collectorJob?.isActive != true) {
            collectorJob = serviceScope.launch {
                try {
                    var wasRunning = false
                    ShredJobCoordinator.state.collect { st ->
                        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                        if (st.isRunning) {
                            wasRunning = true
                            val pct = (st.progress * 100f).toInt().coerceIn(0, 100)
                            if (canPostNotification() && nm != null) {
                                try {
                                    nm.notify(
                                        SHRED_NOTIF_ID,
                                        buildProgressNotification(
                                            st.statusText.ifBlank { "Shredding files..." },
                                            pct,
                                            100,
                                            indeterminate = st.totalFiles <= 0
                                        )
                                    )
                                } catch (_: Throwable) {
                                }
                            }
                        } else if (wasRunning) {
                            postCompletionNotification(st.lastResult)
                            try {
                                ServiceCompat.stopForeground(this@FileShredService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                                stopSelf()
                            } catch (_: Throwable) {
                            }
                        } else if (!st.isRunning) {
                            try {
                                ServiceCompat.stopForeground(this@FileShredService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                                stopSelf()
                            } catch (_: Throwable) {
                            }
                        }
                    }
                } catch (_: Throwable) {
                }
            }
        }

        return START_NOT_STICKY
    }

    private fun canPostNotification(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        } catch (_: Throwable) {
            false
        }
    }

    private fun postCompletionNotification(result: ShredResult?) {
        try {
            if (result == null || !canPostNotification()) return
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val text = if (result.blockedForOverwrite.isNotEmpty()) {
                "Shredded ${result.shreddedCount} file(s); ${result.blockedForOverwrite.size} blocked (needs All Files Access)."
            } else {
                "Permanently shredded ${result.shreddedCount} file(s) (${formatBytes(result.totalBytesWiped)})."
            }
            val pi = PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notif = NotificationCompat.Builder(this, SHRED_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setContentTitle("Secure Shredder Complete")
                .setContentText(text)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build()
            nm.notify(SHRED_DONE_NOTIF_ID, notif)
        } catch (_: Throwable) {
        }
    }

    private fun buildProgressNotification(
        text: String,
        progress: Int,
        max: Int,
        indeterminate: Boolean
    ) = NotificationCompat.Builder(this, SHRED_CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_menu_delete)
        .setContentTitle("Secure File Shredder Active")
        .setContentText(text)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setProgress(max, progress, indeterminate)
        .build()

    private fun createChannel() {
        NotificationChannels.ensureCreated(this)
    }

    override fun onDestroy() {
        try {
            collectorJob?.cancel()
            serviceScope.cancel()
        } catch (_: Throwable) {
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
