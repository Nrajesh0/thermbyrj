package com.example.service

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
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
        val temp = monitor.liveData.value.batteryTemp

        if (temp > 0f) {
            val prefs = context.getSharedPreferences("thermal_prefs", Context.MODE_PRIVATE)
            val warningTemp = prefs.getFloat("warning_temp", 35f)
            val criticalTemp = warningTemp + 5f

            val color = when {
                temp >= criticalTemp -> AuraRose.toArgb()
                temp >= warningTemp -> AuraOrange.toArgb()
                temp >= 20f -> AuraGreen.toArgb()
                else -> AuraCyan.toArgb()
            }
            val views = RemoteViews(context.packageName, R.layout.widget_thermal)
            views.setTextViewText(R.id.widget_temp_text, "${String.format(Locale.US, "%.1f", temp)}°C")
            views.setTextColor(R.id.widget_temp_text, color)
            appWidgetManager.updateAppWidget(appWidgetIds, views)
        }

        val prefs = context.getSharedPreferences("thermal_prefs", Context.MODE_PRIVATE)
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
}
