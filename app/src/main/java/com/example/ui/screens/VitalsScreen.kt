package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.service.LiveThermalData
import com.example.ui.theme.AuraCyan
import com.example.ui.theme.AuraGreen
import com.example.ui.theme.AuraOrange
import com.example.ui.theme.AuraPurple
import com.example.ui.theme.AuraRose
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassPanel
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import com.example.viewmodel.ThermalViewModel
import java.util.Locale
import kotlin.math.max

@Composable
fun VitalsScreen(viewModel: ThermalViewModel, warningTemp: Float = 35f) {
    val liveData by viewModel.thermalMonitor.liveData.collectAsStateWithLifecycle(initialValue = LiveThermalData())
    var selectedSection by remember { mutableStateOf("THERMAL") } // "THERMAL", "BATTERY", "TRENDS", "SYSTEM"

    val criticalTemp = warningTemp + 5f
    val tempColor = when {
        liveData.batteryTemp >= criticalTemp -> AuraRose
        liveData.batteryTemp >= warningTemp -> AuraOrange
        liveData.batteryTemp >= 20f -> AuraGreen
        else -> AuraCyan
    }

    val ramUsed = max(0L, liveData.totalRam - liveData.availRam)
    val ramPct = if (liveData.totalRam > 0) ((ramUsed.toFloat() / liveData.totalRam) * 100f).toInt() else 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Spacer(modifier = Modifier.height(20.dp))

            // Header + Live Summary Strip
            Row(
                modifier = Modifier.fillMaxWidth().padding(end = 56.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "SYSTEM VITALS",
                        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 4.sp, fontWeight = FontWeight.Bold),
                        color = tempColor
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Unified Thermal, Battery, Trends & Hardware Hub",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Quick Telemetry Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(GlassPanel.copy(alpha = 0.65f))
                    .border(1.dp, GlassBorder, RoundedCornerShape(20.dp))
                    .padding(vertical = 10.dp, horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                VitalsMiniStat(
                    label = "TEMP",
                    value = if (liveData.batteryTemp > 0f) String.format(Locale.US, "%.1f°C", liveData.batteryTemp) else "--°C",
                    color = tempColor
                )
                VitalsMiniStat(
                    label = "BATTERY",
                    value = "${liveData.batteryLevel}% ${if (liveData.isCharging) "⚡" else ""}".trim(),
                    color = if (liveData.isCharging) AuraGreen else AuraCyan
                )
                VitalsMiniStat(
                    label = "RAM LOAD",
                    value = "$ramPct%",
                    color = AuraPurple
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Segmented Bar: THERMAL | BATTERY | TRENDS | SYSTEM
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(100.dp))
                    .background(GlassPanel.copy(alpha = 0.7f))
                    .border(1.dp, GlassBorder, RoundedCornerShape(100.dp))
                    .padding(4.dp)
            ) {
                val sections = listOf(
                    "THERMAL" to "THERMAL",
                    "BATTERY" to "BATTERY",
                    "TRENDS" to "7D FLUX",
                    "SYSTEM" to "SPECS"
                )
                sections.forEach { (key, label) ->
                    val selected = selectedSection == key
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .testTag("vitals_subtab_${key.lowercase()}")
                            .clip(RoundedCornerShape(100.dp))
                            .background(if (selected) AuraCyan.copy(alpha = 0.18f) else Color.Transparent)
                            .clickable { selectedSection = key }
                            .padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (selected) AuraCyan else TextSecondary,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        )
                    }
                }
            }
        }

        // Render the selected Vitals view seamlessly
        Box(modifier = Modifier.weight(1f)) {
            when (selectedSection) {
                "THERMAL" -> DashboardScreen(viewModel = viewModel, warningTemp = warningTemp)
                "BATTERY" -> PowerScreen(viewModel = viewModel, warningTemp = warningTemp)
                "TRENDS" -> HistoryScreen(viewModel = viewModel, warningTemp = warningTemp)
                "SYSTEM" -> DeviceScreen(viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun VitalsMiniStat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextTertiary)
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = color
        )
    }
}
