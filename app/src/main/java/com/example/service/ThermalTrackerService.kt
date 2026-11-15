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
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
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

        thermalMonitor = ThermalMonitor.getInstance(this)
        thermalMonitor.startMonitoring()

        liveGuardMonitor = LiveGuardMonitor.getInstance(this)
        liveGuardMonitor.onHighPriorityAlert = null
        liveGuardMonitor.onGuardAlert = { payload ->
            val prefs = getSharedPreferences("thermal_prefs", Context.MODE_PRIVATE)
            if (prefs.getBoolean("live_guard_alerts", true)) {
                sendGuardNotification(payload)
            }
        }
        liveGuardMonitor.startMonitoring()

        val db = AppDatabase.getInstance(this)
        repository = ThermalRepository(db.thermalDao())

        createNotificationChannels()

        // Prune records older than 8 days once on service startup to keep DB fast and compact
        serviceScope.launch {
            val eightDaysAgo = System.currentTimeMillis() - (8L * 24 * 60 * 60 * 1000L)
            repository.deleteRecordsOlderThan(eightDaysAgo)
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
            delay(1000)
            val temp = thermalMonitor.liveData.value.batteryTemp
            if (temp > 0f) {
                updateWidget(temp)
            }
        }

        return START_STICKY
    }

    private var lastRecordedTime = 0L
    private var lastRecordedTemp = -100f
    private var lastWidgetTemp = -100f

    private fun startTrackingLoop() {
        if (trackingJob?.isActive != true) {
            trackingJob = serviceScope.launch {
                thermalMonitor.liveData.collect { live ->
                    if (live.batteryTemp <= 0f) return@collect

                    checkThermalAlerts(live.batteryTemp)
                    maybeRecordAndUpdateWidget(live)
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
            lastWidgetTemp = live.batteryTemp
        }
    }

    private suspend fun updateWidget(@Suppress("UNUSED_PARAMETER") temp: Float) {
        val widgetManager = AppWidgetManager.getInstance(this)
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
    }

    private fun checkThermalAlerts(temp: Float) {
        val prefs = getSharedPreferences("thermal_prefs", Context.MODE_PRIVATE)
        val warningTemp = prefs.getFloat("warning_temp", 35f)
        val criticalTemp = warningTemp + 5f

        val currentState = when {
            temp >= criticalTemp -> 2 // Critical
            temp >= warningTemp -> 1 // Elevated
            else -> 0 // Normal/Optimal
        }

        if (currentState != lastAlertState) {
            val alertsEnabled = prefs.getBoolean("thermal_alerts_enabled", true)
            if (alertsEnabled) {
                if (currentState == 2) {
                    sendAlertNotification(
                        "Critical Temperature",
                        "Device temperature has exceeded ${String.format(Locale.US, "%.0f", criticalTemp)}°C (${String.format(Locale.US, "%.1f", temp)}°C). Please let it cool down."
                    )
                } else if (currentState == 1 && lastAlertState < 1) {
                    sendAlertNotification(
                        "Elevated Temperature",
                        "Device temperature has reached ${String.format(Locale.US, "%.1f", temp)}°C."
                    )
                }
            }
            lastAlertState = currentState
        }
    }

    private fun sendAlertNotification(title: String, message: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val alertNotification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(ALERT_NOTIFICATION_ID, alertNotification)
    }

    private fun sendGuardNotification(payload: GuardAlertPayload) {
        if (::liveGuardMonitor.isInitialized) {
            liveGuardMonitor.sendGuardNotificationDirect(payload)
        }
    }

    private fun createNotification(content: String): Notification {
        return NotificationCompat.Builder(this, TRACKER_CHANNEL_ID)
            .setContentTitle("dot Shield Active")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
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
