package com.example.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.data.AppDatabase
import com.example.data.ThermalRecord
import com.example.data.ThermalRepository
import com.example.ui.screens.GuardRulesActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

class ThermalTrackerService : Service() {

    companion object {
        @Volatile
        var isServiceRunning: Boolean = false
    }

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob + safeCoroutineExceptionHandler())
    private lateinit var thermalMonitor: ThermalMonitor
    private lateinit var liveGuardMonitor: LiveGuardMonitor
    private lateinit var repository: ThermalRepository

    private val TRACKER_CHANNEL_ID = NotificationChannels.TRACKER_CHANNEL_ID
    private val ALERT_CHANNEL_ID = NotificationChannels.ALERT_CHANNEL_ID
    private val GUARD_ALERT_CHANNEL_ID = NotificationChannels.GUARD_ALERT_CHANNEL_ID
    private val NOTIFICATION_ID = 1001
    private val ALERT_NOTIFICATION_ID = 1002
    private val GUARD_ALERT_NOTIFICATION_ID = 1003

    private var lastAlertState = 0 // 0: Normal, 1: Elevated, 2: Critical
    private var trackingJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true

        try {
            thermalMonitor = ThermalMonitor.getInstance(this)
            thermalMonitor.startMonitoring()

            liveGuardMonitor = LiveGuardMonitor.getInstance(this)
            liveGuardMonitor.onHighPriorityAlert = null
            liveGuardMonitor.onGuardAlert = { payload ->
                try {
                    val prefs = getSharedPreferences("thermal_prefs", Context.MODE_PRIVATE)
                    if (prefs.getBoolean("live_guard_alerts", true)) {
                        sendGuardNotification(payload)
                    }
                } catch (_: Throwable) {
                }
            }
            liveGuardMonitor.startMonitoring()

            val db = AppDatabase.getInstance(this)
            repository = ThermalRepository(db.thermalDao())

            createNotificationChannels()

            // Prune records older than 8 days once on service startup to keep DB fast and compact
            serviceScope.launch {
                try {
                    val eightDaysAgo = System.currentTimeMillis() - (8L * 24 * 60 * 60 * 1000L)
                    repository.deleteRecordsOlderThan(eightDaysAgo)
                } catch (_: Throwable) {
                }
            }
        } catch (_: Throwable) {
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        isServiceRunning = true
        val notification = createNotification("Monitoring device thermals & privacy guard...")

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (_: Throwable) {
            // If the OS rejects foreground promotion during a Smart Sidebar / multi-window focus transition,
            // keep monitoring in-process without crashing and stop the foreground service request cleanly.
            isServiceRunning = false
            try {
                stopSelf()
            } catch (_: Throwable) {
            }
            return START_NOT_STICKY
        }

        startTrackingLoop()

        serviceScope.launch {
            try {
                delay(1000)
                if (::thermalMonitor.isInitialized) {
                    val temp = thermalMonitor.liveData.value.batteryTemp
                    if (temp > 0f) {
                        updateWidget(temp)
                    }
                }
            } catch (_: Throwable) {
            }
        }

        return START_STICKY
    }

    private var lastRecordedTime = 0L
    private var lastRecordedTemp = -100f
    private var lastWidgetTemp = -100f

    private fun startTrackingLoop() {
        if (!::thermalMonitor.isInitialized || !::repository.isInitialized) return
        if (trackingJob?.isActive != true) {
            trackingJob = serviceScope.launch {
                try {
                    thermalMonitor.liveData.collect { live ->
                        if (live.batteryTemp <= 0f) return@collect
                        try {
                            checkThermalAlerts(live.batteryTemp)
                            maybeRecordAndUpdateWidget(live)
                        } catch (_: Throwable) {
                        }
                    }
                } catch (_: Throwable) {
                }
            }
        }
    }

    private suspend fun maybeRecordAndUpdateWidget(live: LiveThermalData) {
        val now = System.currentTimeMillis()
        val tempDiff = kotlin.math.abs(live.batteryTemp - lastRecordedTemp)
        val timeDiff = now - lastRecordedTime

        // Record on first reading, every 3 minutes, or when temperature shifts >= 0.5°C after 30s
        if (lastRecordedTime == 0L || timeDiff >= 180_000L || (tempDiff >= 0.5f && timeDiff >= 30_000L)) {
            val latestCpuTemp = if (!com.example.MainActivity.isUiForeground) {
                thermalMonitor.refreshCpuTempOnly().takeIf { it > 0f } ?: live.cpuTemp
            } else {
                live.cpuTemp
            }
            repository.insertRecord(
                ThermalRecord(
                    timestamp = now,
                    batteryTemp = live.batteryTemp,
                    cpuTemp = latestCpuTemp,
                    batteryLevel = live.batteryLevel
                )
            )
            lastRecordedTime = now
            lastRecordedTemp = live.batteryTemp
        }

        if (kotlin.math.abs(live.batteryTemp - lastWidgetTemp) >= 0.2f) {
            updateWidget(live.batteryTemp)
            updateForegroundNotification(live)
            lastWidgetTemp = live.batteryTemp
        }
    }

    private fun updateForegroundNotification(live: LiveThermalData) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val tempText = String.format(Locale.US, "%.1f°C", live.batteryTemp)
            val chargeSuffix = if (live.isCharging) " • Charging (${live.batteryLevel}%)" else " • ${live.batteryLevel}%"
            val guardSuffix = if (::liveGuardMonitor.isInitialized && liveGuardMonitor.isMonitoringActive()) " • Guard On" else ""
            nm.notify(NOTIFICATION_ID, createNotification("$tempText$chargeSuffix$guardSuffix"))
        } catch (_: Throwable) {
        }
    }

    private suspend fun updateWidget(@Suppress("UNUSED_PARAMETER") temp: Float) {
        try {
            val widgetManager = AppWidgetManager.getInstance(this) ?: return
            val widgetIds = widgetManager.getAppWidgetIds(ComponentName(this, ThermalWidgetProvider::class.java))
            if (widgetIds.isEmpty()) return
            val oneHourAgo = System.currentTimeMillis() - (60 * 60 * 1000L)
            val recentRecords = repository.getRecordsSinceSync(oneHourAgo)
            ThermalWidgetProvider.updateAllWidgets(
                context = this,
                recentRecords = recentRecords,
                appWidgetManager = widgetManager,
                appWidgetIds = widgetIds
            )
        } catch (_: Throwable) {
        }
    }

    private fun checkThermalAlerts(temp: Float) {
        val prefs = getSharedPreferences("thermal_prefs", Context.MODE_PRIVATE)
        val warningTemp = prefs.getFloat("warning_temp", 35f)
        val criticalTemp = warningTemp + 5f
        val hysteresis = 1.0f

        val nextState = when (lastAlertState) {
            2 -> when {
                temp >= criticalTemp - hysteresis -> 2
                temp >= warningTemp -> 1
                else -> 0
            }
            1 -> when {
                temp >= criticalTemp -> 2
                temp >= warningTemp - hysteresis -> 1
                else -> 0
            }
            else -> when {
                temp >= criticalTemp -> 2
                temp >= warningTemp -> 1
                else -> 0
            }
        }

        if (nextState != lastAlertState) {
            val alertsEnabled = prefs.getBoolean("thermal_alerts_enabled", true)
            if (alertsEnabled) {
                if (nextState == 2 && lastAlertState < 2) {
                    sendAlertNotification(
                        "Critical Temperature",
                        "Device temperature has exceeded ${String.format(Locale.US, "%.0f", criticalTemp)}°C (${String.format(Locale.US, "%.1f", temp)}°C). Please let it cool down."
                    )
                } else if (nextState == 1 && lastAlertState < 1) {
                    sendAlertNotification(
                        "Elevated Temperature",
                        "Device temperature has reached ${String.format(Locale.US, "%.1f", temp)}°C."
                    )
                }
            }
            lastAlertState = nextState
        }
    }

    private fun buildOpenAppPendingIntent(requestCode: Int): PendingIntent? {
        return try {
            val openIntent = Intent(this, com.example.MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            PendingIntent.getActivity(
                this,
                requestCode,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } catch (_: Throwable) {
            null
        }
    }

    private fun sendAlertNotification(title: String, message: String) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val builder = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(message)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
            buildOpenAppPendingIntent(ALERT_NOTIFICATION_ID)?.let { builder.setContentIntent(it) }
            notificationManager.notify(ALERT_NOTIFICATION_ID, builder.build())
        } catch (_: Throwable) {
        }
    }

    private fun sendGuardNotification(payload: GuardAlertPayload) {
        if (::liveGuardMonitor.isInitialized) {
            liveGuardMonitor.sendGuardNotificationDirect(payload)
        }
    }

    private fun createNotification(content: String): Notification {
        val builder = NotificationCompat.Builder(this, TRACKER_CHANNEL_ID)
            .setContentTitle("dot Shield Active")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
        buildOpenAppPendingIntent(NOTIFICATION_ID)?.let { builder.setContentIntent(it) }
        return builder.build()
    }

    private fun createNotificationChannels() {
        NotificationChannels.ensureCreated(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isServiceRunning = false
        super.onDestroy()
        serviceJob.cancel()
        if (::liveGuardMonitor.isInitialized) {
            liveGuardMonitor.onGuardAlert = null
            if (!com.example.MainActivity.isUiForeground) {
                liveGuardMonitor.stopMonitoring()
            }
        }
        if (::thermalMonitor.isInitialized && !com.example.MainActivity.isUiForeground) {
            thermalMonitor.stopMonitoring()
        }
    }
}
