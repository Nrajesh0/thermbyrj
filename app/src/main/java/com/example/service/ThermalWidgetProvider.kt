package com.example.service

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import com.example.MainActivity
import com.example.R
import com.example.ui.theme.AuraCyan
import com.example.ui.theme.AuraGreen
import com.example.ui.theme.AuraOrange
import com.example.ui.theme.AuraRose
import java.util.Locale

class ThermalWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val monitor = ThermalMonitor.getInstance(context)
        monitor.forceUpdate()
        val live = monitor.liveData.value
        val temp = live.batteryTemp
        val batteryPct = live.batteryLevel

        val prefs = context.getSharedPreferences("thermal_prefs", Context.MODE_PRIVATE)
        val warningTemp = prefs.getFloat("warning_temp", 35f)
        val criticalTemp = warningTemp + 5f

        val color = when {
            temp >= criticalTemp -> AuraRose.toArgb()
            temp >= warningTemp -> AuraOrange.toArgb()
            temp >= 20f -> AuraGreen.toArgb()
            else -> AuraCyan.toArgb()
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

        if (temp > 0f) {
            views.setTextViewText(
                R.id.widget_temp_text,
                "${String.format(Locale.US, "%.1f", temp)}°C · $batteryPct%"
            )
            views.setTextColor(R.id.widget_temp_text, color)
            views.setImageViewBitmap(
                R.id.widget_sparkline,
                renderThermalGaugeBitmap(temp, warningTemp, criticalTemp, color)
            )
        } else {
            views.setTextViewText(R.id.widget_temp_text, "$batteryPct%")
            views.setTextColor(R.id.widget_temp_text, AuraCyan.toArgb())
        }

        appWidgetManager.updateAppWidget(appWidgetIds, views)

        if (prefs.getBoolean("background_monitoring", true)) {
            try {
                val serviceIntent = Intent(context, ThermalTrackerService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun renderThermalGaugeBitmap(
        temp: Float,
        warningTemp: Float,
        criticalTemp: Float,
        activeColor: Int
    ): Bitmap {
        val width = 240
        val height = 36
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x33FFFFFF
            style = Paint.Style.FILL
        }
        val barRect = RectF(8f, 12f, width - 8f, 24f)
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
            color = 0x88FFFFFF.toInt()
            strokeWidth = 2f
        }
        canvas.drawLine(warnX, 8f, warnX, 28f, tickPaint)

        return bmp
    }
}
