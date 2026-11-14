package com.example.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.data.AppDatabase
import com.example.data.ThermalRecord
import com.example.data.ThermalRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

class ThermalTrackerService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private lateinit var thermalMonitor: ThermalMonitor
    private lateinit var liveGuardMonitor: LiveGuardMonitor
    private lateinit var repository: ThermalRepository

    private val TRACKER_CHANNEL_ID = "thermal_tracker_channel"
    private val ALERT_CHANNEL_ID = "thermal_alert_channel"
    private val NOTIFICATION_ID = 1001
    private val ALERT_NOTIFICATION_ID = 1002
    private val GUARD_ALERT_NOTIFICATION_ID = 1003

    private var lastAlertState = 0 // 0: Normal, 1: Elevated, 2: Critical
    private var trackingJob: Job? = null
    private var periodicSamplingJob: Job? = null

    override fun onCreate() {
        super.onCreate()

        thermalMonitor = ThermalMonitor.getInstance(this)
        thermalMonitor.startMonitoring()

        liveGuardMonitor = LiveGuardMonitor.getInstance(this)
        liveGuardMonitor.onHighPriorityAlert = { title, message ->
            val prefs = getSharedPreferences("thermal_prefs", Context.MODE_PRIVATE)
            if (prefs.getBoolean("live_guard_alerts", true)) {
                sendGuardNotification(title, message)
            }
        }
        liveGuardMonitor.startMonitoring()

        val db = AppDatabase.getInstance(this)
        repository = ThermalRepository(db.thermalDao())

        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification("Monitoring device thermals...")

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

        if (periodicSamplingJob?.isActive != true) {
            periodicSamplingJob = serviceScope.launch {
                while (isActive) {
                    delay(30_000L)
                    val live = thermalMonitor.liveData.value
                    if (live.batteryTemp > 0f) {
                        maybeRecordAndUpdateWidget(live)
                    }
                }
            }
        }
    }

    private suspend fun maybeRecordAndUpdateWidget(live: LiveThermalData) {
        val now = System.currentTimeMillis()
        val tempDiff = kotlin.math.abs(live.batteryTemp - lastRecordedTemp)
        val timeDiff = now - lastRecordedTime

        // Record immediately on first valid reading, every 30s, or on >= 0.5°C delta after 10s
        if (lastRecordedTime == 0L || timeDiff >= 30_000L || (tempDiff >= 0.5f && timeDiff >= 10_000L)) {
            repository.insertRecord(
                ThermalRecord(
                    timestamp = now,
                    batteryTemp = live.batteryTemp,
                    cpuTemp = live.cpuTemp,
                    batteryLevel = live.batteryLevel
                )
            )
            lastRecordedTime = now
            lastRecordedTemp = live.batteryTemp
        }

        if (kotlin.math.abs(live.batteryTemp - lastWidgetTemp) >= 0.1f) {
            updateWidget(live.batteryTemp)
            lastWidgetTemp = live.batteryTemp
        }
    }

    private suspend fun updateWidget(temp: Float) {
        val appWidgetManager = android.appwidget.AppWidgetManager.getInstance(this)
        val componentName = android.content.ComponentName(this, ThermalWidgetProvider::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
        if (appWidgetIds.isEmpty()) return

        val oneHourAgo = System.currentTimeMillis() - (60 * 60 * 1000L)
        val recentRecords = repository.getRecordsSinceSync(oneHourAgo)

        val views = android.widget.RemoteViews(packageName, com.example.R.layout.widget_thermal)
        views.setTextViewText(com.example.R.id.widget_temp_text, "${String.format(Locale.US, "%.1f", temp)}°C")

        val prefs = getSharedPreferences("thermal_prefs", Context.MODE_PRIVATE)
        val warningTemp = prefs.getFloat("warning_temp", 35f)
        val criticalTemp = warningTemp + 5f

        val color = when {
            temp >= criticalTemp -> com.example.ui.theme.AuraRose.toArgb()
            temp >= warningTemp -> com.example.ui.theme.AuraOrange.toArgb()
            temp >= 20f -> com.example.ui.theme.AuraGreen.toArgb()
            else -> com.example.ui.theme.AuraCyan.toArgb()
        }
        views.setTextColor(com.example.R.id.widget_temp_text, color)

        if (recentRecords.size >= 2) {
            val bitmap = createSparklineBitmap(recentRecords, color)
            views.setImageViewBitmap(com.example.R.id.widget_sparkline, bitmap)
        }

        appWidgetManager.updateAppWidget(appWidgetIds, views)
    }

    private fun createSparklineBitmap(records: List<ThermalRecord>, lineColor: Int): android.graphics.Bitmap {
        val width = 400
        val height = 150
        val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)

        val minTemp = records.minOf { it.batteryTemp } - 2f
        val maxTemp = records.maxOf { it.batteryTemp } + 2f
        val timeStart = records.first().timestamp
        val timeRange = records.last().timestamp - timeStart
        val tempRange = (maxTemp - minTemp).coerceAtLeast(1f)

        val path = android.graphics.Path()
        var firstPoint = true

        records.forEach { record ->
            val x = if (timeRange == 0L) 0f else ((record.timestamp - timeStart).toFloat() / timeRange) * width
            val y = height - (((record.batteryTemp - minTemp) / tempRange) * height)
            if (firstPoint) {
                path.moveTo(x, y)
                firstPoint = false
            } else {
                path.lineTo(x, y)
            }
        }

        val paint = android.graphics.Paint().apply {
            color = lineColor
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 6f
            isAntiAlias = true
            strokeJoin = android.graphics.Paint.Join.ROUND
            strokeCap = android.graphics.Paint.Cap.ROUND
        }

        canvas.drawPath(path, paint)
        return bitmap
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

    private fun sendGuardNotification(title: String, message: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val guardNotification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(GUARD_ALERT_NOTIFICATION_ID, guardNotification)
    }

    private fun createNotification(content: String): Notification {
        return NotificationCompat.Builder(this, TRACKER_CHANNEL_ID)
            .setContentTitle("Thermal Monitor Active")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val trackerChannel = NotificationChannel(
                TRACKER_CHANNEL_ID,
                "Thermal Tracking",
                NotificationManager.IMPORTANCE_LOW
            )
            val alertChannel = NotificationChannel(
                ALERT_CHANNEL_ID,
                "Thermal Alerts",
                NotificationManager.IMPORTANCE_HIGH
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(trackerChannel)
            manager.createNotificationChannel(alertChannel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }
}
