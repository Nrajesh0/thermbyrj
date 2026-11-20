package com.example.service

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Build
import android.widget.RemoteViews
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.example.MainActivity
import com.example.R
import com.example.data.ThermalRecord
import com.example.ui.theme.AccentPreset
import com.example.ui.theme.AuraCyan
import com.example.ui.theme.AuraGreen
import com.example.ui.theme.AuraOrange
import com.example.ui.theme.AuraPurple
import com.example.ui.theme.AuraRose
import com.example.ui.theme.SurfacePreset
import java.util.Locale

class ThermalWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        try {
            ThermalMonitor.getInstance(context).forceUpdate()
            updateAllWidgets(context, appWidgetManager = appWidgetManager, appWidgetIds = appWidgetIds)

            val prefs = context.getSharedPreferences("thermal_prefs", Context.MODE_PRIVATE)
            if (prefs.getBoolean("background_monitoring", true) &&
                (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || MainActivity.isUiForeground)
            ) {
                com.example.startTrackingServiceSafely(context)
            }
        } catch (_: Throwable) {
        }
    }

    companion object {
        fun updateAllWidgets(
            context: Context,
            recentRecords: List<ThermalRecord>? = null,
            appWidgetManager: AppWidgetManager? = AppWidgetManager.getInstance(context),
            appWidgetIds: IntArray = try {
                appWidgetManager?.getAppWidgetIds(ComponentName(context, ThermalWidgetProvider::class.java)) ?: IntArray(0)
            } catch (_: Throwable) {
                IntArray(0)
            }
        ) {
            if (appWidgetManager == null || appWidgetIds.isEmpty()) return
            try {

            val monitor = ThermalMonitor.getInstance(context)
            val live = monitor.liveData.value
            val temp = live.batteryTemp
            val batteryPct = live.batteryLevel

            val prefs = context.getSharedPreferences("thermal_prefs", Context.MODE_PRIVATE)
            val accentKey = prefs.getString("theme_accent", "CYAN") ?: "CYAN"
            val surfaceKey = prefs.getString("theme_surface", "OBSIDIAN") ?: "OBSIDIAN"
            val accentPreset = AccentPreset.entries.find { it.key == accentKey } ?: AccentPreset.CYAN
            val surfacePreset = SurfacePreset.entries.find { it.key == surfaceKey } ?: SurfacePreset.OBSIDIAN

            val warningTemp = prefs.getFloat("warning_temp", 35f)
            val criticalTemp = warningTemp + 5f

            val resolvedAccent = if (accentPreset == AccentPreset.MONO && surfacePreset.isLight) {
                Color(0xFF0F172A)
            } else if (surfacePreset.isLight && accentPreset == AccentPreset.CYAN) {
                Color(0xFF0891B2)
            } else {
                accentPreset.color
            }

            val primaryTextColor = if (surfacePreset.isLight) 0xFF0F172A.toInt() else 0xFFF8FAFC.toInt()
            val trackColor = if (surfacePreset.isLight) 0x1F0F172A else 0x29FFFFFF

            val tempColor = when {
                temp >= criticalTemp -> AuraRose.toArgb()
                temp >= warningTemp -> AuraOrange.toArgb()
                temp >= 20f -> resolvedAccent.toArgb()
                else -> AuraCyan.toArgb()
            }

            val captureState = LiveGuardMonitor.getInstance(context).captureState.value
            val (statusLabel, statusColor) = when {
                captureState.isScreenRecordingOrCasting -> "Screen Rec" to AuraRose.toArgb()
                captureState.isMicActive -> {
                    val app = captureState.activeMicApps.firstOrNull()
                    (if (app != null) "Mic: $app" else "Mic Live") to AuraOrange.toArgb()
                }
                captureState.isCameraActive -> {
                    val app = captureState.activeCameraApps.firstOrNull()
                    (if (app != null) "Cam: $app" else "Cam Live") to AuraOrange.toArgb()
                }
                captureState.isBackgroundLocationActive -> "GPS Live" to AuraOrange.toArgb()
                captureState.isClipboardAccessedRecently -> "Clipboard" to AuraPurple.toArgb()
                else -> "dot · Safe" to resolvedAccent.toArgb()
            }

            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val views = RemoteViews(context.packageName, R.layout.widget_thermal)
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)
            views.setImageViewBitmap(
                R.id.widget_bg,
                renderWidgetBackgroundBitmap(
                    bgColor = surfacePreset.bg.toArgb(),
                    borderColor = surfacePreset.border.toArgb()
                )
            )
            views.setTextViewText(R.id.widget_status_text, statusLabel)
            views.setTextColor(R.id.widget_status_text, statusColor)

            if (temp > 0f) {
                views.setTextViewText(
                    R.id.widget_temp_text,
                    "${String.format(Locale.US, "%.1f", temp)}°C · $batteryPct%"
                )
                views.setTextColor(R.id.widget_temp_text, primaryTextColor)
                val chartBmp = if (!recentRecords.isNullOrEmpty() && recentRecords.size >= 2) {
                    renderThermalSparklineBitmap(recentRecords, tempColor)
                } else {
                    renderThermalGaugeBitmap(temp, warningTemp, criticalTemp, tempColor, trackColor, primaryTextColor)
                }
                views.setImageViewBitmap(R.id.widget_sparkline, chartBmp)
            } else {
                views.setTextViewText(R.id.widget_temp_text, "$batteryPct%")
                views.setTextColor(R.id.widget_temp_text, primaryTextColor)
            }

            appWidgetManager.updateAppWidget(appWidgetIds, views)
            } catch (_: Throwable) {
            }
        }

        private fun renderWidgetBackgroundBitmap(bgColor: Int, borderColor: Int): Bitmap {
            val width = 320
            val height = 160
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            val radius = 34f
            val rect = RectF(2f, 2f, width - 2f, height - 2f)

            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = bgColor
                style = Paint.Style.FILL
            }
            canvas.drawRoundRect(rect, radius, radius, fillPaint)

            val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = borderColor
                style = Paint.Style.STROKE
                strokeWidth = 2.5f
            }
            canvas.drawRoundRect(rect, radius, radius, strokePaint)
            return bmp
        }

        private fun renderThermalSparklineBitmap(records: List<ThermalRecord>, lineColor: Int): Bitmap {
            val width = 400
            val height = 120
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val valid = records.filter { it.batteryTemp.isFinite() }
            if (valid.size < 2) return bitmap
            val canvas = Canvas(bitmap)

            val minTemp = valid.minOf { it.batteryTemp } - 1.5f
            val maxTemp = valid.maxOf { it.batteryTemp } + 1.5f
            val timeStart = valid.first().timestamp
            val timeRange = (valid.last().timestamp - timeStart).coerceAtLeast(0L)
            val tempRange = (maxTemp - minTemp).coerceAtLeast(1f)

            val path = Path()
            var firstPoint = true
            valid.forEachIndexed { idx, record ->
                val x = if (timeRange == 0L) {
                    (idx.toFloat() / (valid.size - 1).coerceAtLeast(1)) * width
                } else {
                    (((record.timestamp - timeStart).toFloat() / timeRange) * width).coerceIn(0f, width.toFloat())
                }
                val y = (height - 8f - (((record.batteryTemp - minTemp) / tempRange) * (height - 16f)))
                    .coerceIn(4f, height - 4f)
                if (firstPoint) {
                    path.moveTo(x, y)
                    firstPoint = false
                } else {
                    path.lineTo(x, y)
                }
            }

            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = lineColor
                style = Paint.Style.STROKE
                strokeWidth = 5f
                strokeJoin = Paint.Join.ROUND
                strokeCap = Paint.Cap.ROUND
            }
            canvas.drawPath(path, paint)
            return bitmap
        }

        private fun renderThermalGaugeBitmap(
            temp: Float,
            warningTemp: Float,
            criticalTemp: Float,
            activeColor: Int,
            trackColor: Int,
            tickColor: Int
        ): Bitmap {
            val width = 280
            val height = 36
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)

            val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = trackColor
                style = Paint.Style.FILL
            }
            val barRect = RectF(4f, 12f, width - 4f, 24f)
            canvas.drawRoundRect(barRect, 6f, 6f, trackPaint)

            val fraction = ((temp - 15f) / ((criticalTemp + 10f) - 15f)).coerceIn(0.08f, 1f)
            val fillWidth = (barRect.width() * fraction)
            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = activeColor
                style = Paint.Style.FILL
            }
            canvas.drawRoundRect(
                RectF(barRect.left, barRect.top, barRect.left + fillWidth, barRect.bottom),
                6f,
                6f,
                fillPaint
            )

            val warnX = barRect.left + barRect.width() * ((warningTemp - 15f) / ((criticalTemp + 10f) - 15f)).coerceIn(0f, 1f)
            val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = tickColor
                alpha = 130
                strokeWidth = 2f
            }
            canvas.drawLine(warnX, 8f, warnX, 28f, tickPaint)

            return bmp
        }
    }
}
