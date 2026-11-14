package com.example.ui.screens

import android.content.Intent
import android.os.BatteryManager
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.GlassCard
import com.example.InfoRow
import com.example.formatBytes
import com.example.formatUptime
import com.example.service.LiveThermalData
import com.example.ui.components.ThermalChart
import com.example.ui.theme.AuraCyan
import com.example.ui.theme.AuraGreen
import com.example.ui.theme.AuraOrange
import com.example.ui.theme.AuraPurple
import com.example.ui.theme.AuraRose
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import com.example.viewmodel.ThermalViewModel
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun DashboardScreen(viewModel: ThermalViewModel, warningTemp: Float = 35f) {
    val liveData by viewModel.thermalMonitor.liveData.collectAsStateWithLifecycle(initialValue = LiveThermalData())
    val allRecords by viewModel.allRecords.collectAsStateWithLifecycle()

    val criticalTemp = warningTemp + 5f
    val now = System.currentTimeMillis()
    val last24h = allRecords
        .filter { it.timestamp >= now - (24L * 60 * 60 * 1000L) }
        .sortedBy { it.timestamp }

    val tenths = (liveData.batteryTemp * 10f).roundToInt()
    val wholeTemp = tenths / 10
    val fracTemp = abs(tenths % 10)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(modifier = Modifier.height(14.dp))

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "BATTERY CORE",
                color = TextTertiary,
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (liveData.batteryTemp > 0f) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = wholeTemp.toString(),
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontSize = 112.sp,
                            fontWeight = FontWeight.W200,
                            letterSpacing = (-4).sp
                        ),
                        color = TextPrimary
                    )
                    Text(
                        text = ".$fracTemp°C",
                        style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.W300),
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 24.dp, start = 4.dp)
                    )
                }
            } else {
                Text(
                    text = "--.-°C",
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = 72.sp,
                        fontWeight = FontWeight.W200
                    ),
                    color = TextSecondary
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            val (zoneName, zoneColor) = when {
                liveData.batteryTemp <= 0f -> "SENSING" to TextSecondary
                liveData.batteryTemp < 20f -> "OPTIMAL" to AuraCyan
                liveData.batteryTemp < warningTemp -> "NORMAL" to AuraGreen
                liveData.batteryTemp < criticalTemp -> "ELEVATED" to AuraOrange
                else -> "CRITICAL" to AuraRose
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(100.dp))
                    .background(zoneColor.copy(alpha = 0.1f))
                    .border(1.dp, zoneColor.copy(alpha = 0.2f), RoundedCornerShape(100.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(zoneColor))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "SYSTEM $zoneName",
                    color = zoneColor,
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp, fontWeight = FontWeight.Bold)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        GlassCard(modifier = Modifier.fillMaxWidth().height(340.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "24H FLUX",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp, fontWeight = FontWeight.Bold),
                    color = TextTertiary
                )
                Text(
                    text = "${last24h.size} samples",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            if (last24h.size < 2) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "COLLECTING THERMAL SAMPLES",
                            color = TextSecondary,
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Trend curve plots automatically after 2+ readings",
                            color = TextTertiary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            } else {
                ThermalChart(
                    records = last24h,
                    modifier = Modifier.fillMaxSize(),
                    lineColor = AuraCyan,
                    fillColorStart = AuraCyan.copy(alpha = 0.2f),
                    fillColorEnd = AuraCyan.copy(alpha = 0.0f),
                    warningTemp = warningTemp,
                    criticalTemp = criticalTemp
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        val max24 = last24h.maxOfOrNull { it.batteryTemp } ?: liveData.batteryTemp
        val min24 = last24h.minOfOrNull { it.batteryTemp } ?: liveData.batteryTemp

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "24H EXTREMES",
                color = TextTertiary,
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("PEAK", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                    Text(String.format(Locale.US, "%.1f°C", max24), style = MaterialTheme.typography.titleLarge, color = AuraRose)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("LOW", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                    Text(String.format(Locale.US, "%.1f°C", min24), style = MaterialTheme.typography.titleLarge, color = AuraCyan)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        var timeOptimal = 0L
        var timeNormal = 0L
        var timeElevated = 0L
        var timeCritical = 0L

        if (last24h.size >= 2) {
            for (i in 0 until last24h.size - 1) {
                val current = last24h[i]
                val next = last24h[i + 1]
                val duration = max(0L, next.timestamp - current.timestamp)
                val cappedDuration = min(duration, 2L * 60 * 60 * 1000L)

                when {
                    current.batteryTemp < 20f -> timeOptimal += cappedDuration
                    current.batteryTemp < warningTemp -> timeNormal += cappedDuration
                    current.batteryTemp < criticalTemp -> timeElevated += cappedDuration
                    else -> timeCritical += cappedDuration
                }
            }
        }
        val totalTrackedTime = max(1L, timeOptimal + timeNormal + timeElevated + timeCritical)

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "TIME IN ZONES (24H)",
                color = TextTertiary,
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(24.dp))

            if (last24h.size < 2) {
                Text("Gathering initial telemetry...", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            } else {
                val zones = listOf(
                    Triple("CRITICAL (>${criticalTemp.toInt()}°C)", timeCritical, AuraRose),
                    Triple("ELEVATED (${warningTemp.toInt()}-${criticalTemp.toInt()}°C)", timeElevated, AuraOrange),
                    Triple("NORMAL (20-${warningTemp.toInt()}°C)", timeNormal, AuraGreen),
                    Triple("OPTIMAL (<20°C)", timeOptimal, AuraCyan)
                )

                zones.forEach { (label, timeMillis, color) ->
                    val pct = (timeMillis.toFloat() / totalTrackedTime.toFloat()).coerceIn(0f, 1f)
                    val seconds = (timeMillis / 1000) % 60
                    val minutes = (timeMillis / (1000 * 60)) % 60
                    val hours = (timeMillis / (1000 * 60 * 60))
                    val timeStr = when {
                        hours > 0 -> "${hours}h ${minutes}m"
                        minutes > 0 -> "${minutes}m ${seconds}s"
                        else -> "${seconds}s"
                    }

                    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(label, style = MaterialTheme.typography.labelSmall, color = TextPrimary)
                            Text(timeStr, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { pct },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = color,
                            trackColor = Color.White.copy(alpha = 0.05f),
                            strokeCap = StrokeCap.Round
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.navigationBarsPadding().height(104.dp))
    }
}

@Composable
fun DeviceScreen(viewModel: ThermalViewModel) {
    val liveData by viewModel.thermalMonitor.liveData.collectAsStateWithLifecycle(initialValue = LiveThermalData())
    val context = LocalContext.current
    val displayMetrics = context.resources.displayMetrics

    LaunchedEffect(Unit) {
        viewModel.thermalMonitor.refreshSystemSpecs()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(modifier = Modifier.height(14.dp))

        // Identity
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "IDENTITY",
                color = TextTertiary,
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(16.dp))
            InfoRow("MODEL", liveData.deviceModel.uppercase())
            InfoRow("MANUFACTURER", liveData.deviceManufacturer.uppercase())
            InfoRow("BOARD", liveData.board)
            InfoRow("HARDWARE", liveData.hardware)
            InfoRow("BOOTLOADER", liveData.bootloader)
            InfoRow("OS VERSION", "Android ${liveData.osVersion}")
            InfoRow("API LEVEL", "${liveData.apiLevel}")
            InfoRow("UPTIME", formatUptime(liveData.uptime))
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Compute Engine
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "COMPUTE ENGINE",
                color = TextTertiary,
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(16.dp))
            InfoRow("CPU CORES", "${liveData.cpuCores} Threads")
            InfoRow("ARCHITECTURE", liveData.cpuArch)

            Spacer(modifier = Modifier.height(16.dp))
            val ramUsed = max(0L, liveData.totalRam - liveData.availRam)
            val ramProgress = if (liveData.totalRam > 0) (ramUsed.toFloat() / liveData.totalRam).coerceIn(0f, 1f) else 0f

            Text("SYSTEM RAM", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { ramProgress },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                color = AuraPurple,
                trackColor = Color.White.copy(alpha = 0.05f),
                strokeCap = StrokeCap.Round
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${formatBytes(ramUsed)} used", style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                Text(formatBytes(liveData.totalRam), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text("SOC THERMAL", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                if (liveData.cpuTemp > 0) String.format(Locale.US, "%.1f°C", liveData.cpuTemp) else "RESTRICTED BY OS",
                style = if (liveData.cpuTemp > 0) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.labelSmall,
                color = AuraPurple
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Storage Drive
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("storage_drive_card")
                .clickable {
                    context.startActivity(Intent(context, StorageAnalysisActivity::class.java))
                }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "STORAGE DRIVE",
                    color = TextTertiary,
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("ANALYZE", style = MaterialTheme.typography.labelSmall, color = AuraOrange)
                    Icon(Icons.Default.ChevronRight, contentDescription = "Open storage analysis", tint = AuraOrange, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            val storageUsed = max(0L, liveData.totalStorage - liveData.availStorage)
            val storageProgress = if (liveData.totalStorage > 0) (storageUsed.toFloat() / liveData.totalStorage).coerceIn(0f, 1f) else 0f

            LinearProgressIndicator(
                progress = { storageProgress },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                color = AuraOrange,
                trackColor = Color.White.copy(alpha = 0.05f),
                strokeCap = StrokeCap.Round
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${formatBytes(storageUsed)} used", style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                Text(formatBytes(liveData.totalStorage), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Connectivity & Display
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "NETWORK & DISPLAY",
                color = TextTertiary,
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(16.dp))
            InfoRow("LOCAL IP", liveData.ipAddress)
            InfoRow("REFRESH RATE", "${liveData.refreshRate.roundToInt()} Hz")
            InfoRow("RESOLUTION", "${displayMetrics.widthPixels} x ${displayMetrics.heightPixels}")
            InfoRow("DENSITY", "${displayMetrics.densityDpi} dpi")
        }

        Spacer(modifier = Modifier.navigationBarsPadding().height(104.dp))
    }
}

@Composable
fun PowerScreen(viewModel: ThermalViewModel, warningTemp: Float = 35f) {
    val liveData by viewModel.thermalMonitor.liveData.collectAsStateWithLifecycle(initialValue = LiveThermalData())
    val criticalTemp = warningTemp + 5f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(modifier = Modifier.height(14.dp))

        // Power Matrix
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "POWER MATRIX",
                color = TextTertiary,
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(28.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(84.dp)) {
                    CircularProgressIndicator(
                        progress = { 1f },
                        modifier = Modifier.fillMaxSize(),
                        color = Color.White.copy(alpha = 0.05f),
                        strokeWidth = 6.dp,
                        strokeCap = StrokeCap.Round
                    )
                    CircularProgressIndicator(
                        progress = { (liveData.batteryLevel / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxSize(),
                        color = if (liveData.isCharging) AuraGreen else AuraCyan,
                        strokeWidth = 6.dp,
                        strokeCap = StrokeCap.Round
                    )
                    Text(
                        text = "${liveData.batteryLevel}%",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.W300),
                        color = TextPrimary
                    )
                }
                Spacer(modifier = Modifier.width(28.dp))
                Column {
                    Text("STATUS", style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp), color = TextTertiary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        if (liveData.isCharging) "Charging (${liveData.chargingSource})" else "Discharging",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            val healthStr = when (liveData.batteryHealth) {
                BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
                BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
                BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
                BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Volt"
                BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Failed"
                else -> "Unknown"
            }

            InfoRow("VOLTAGE", if (liveData.batteryVoltage > 0) "${liveData.batteryVoltage} mV" else "Unavailable")
            InfoRow("CHEMISTRY", liveData.batteryTech.ifBlank { "Unknown" })
            InfoRow("HEALTH", healthStr)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Charging Rate & Time Remaining
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            GlassCard(modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Bolt, contentDescription = "Power rate", tint = AuraOrange, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.height(14.dp))
                Text("CURRENT", color = TextTertiary, style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp))
                Spacer(modifier = Modifier.height(4.dp))
                val currentText = when {
                    liveData.batteryCurrentMa != 0 -> {
                        val watts = (abs(liveData.batteryCurrentMa) * max(1, liveData.batteryVoltage)) / 1_000_000f
                        if (watts >= 0.5f) {
                            String.format(Locale.US, "%d mA (%.1fW)", liveData.batteryCurrentMa, watts)
                        } else {
                            "${liveData.batteryCurrentMa} mA"
                        }
                    }
                    liveData.isCharging -> liveData.chargingSource
                    else -> "Idle"
                }
                Text(currentText, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
            }
            GlassCard(modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.BatterySaver, contentDescription = "Charge time", tint = AuraCyan, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.height(14.dp))
                Text("TO FULL", color = TextTertiary, style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp))
                Spacer(modifier = Modifier.height(4.dp))
                val timeText = when {
                    !liveData.isCharging -> "On Battery"
                    liveData.batteryLevel >= 100 -> "Full"
                    liveData.chargeTimeRemainingMs > 0L -> {
                        val totalMins = (liveData.chargeTimeRemainingMs / 60_000L).toInt()
                        val hrs = totalMins / 60
                        val mins = totalMins % 60
                        if (hrs > 0) "${hrs}h ${mins}m" else "${mins}m"
                    }
                    else -> "Estimating"
                }
                Text(timeText, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Battery Wear & Thermal Alert
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "DEEP ANALYTICS",
                color = TextTertiary,
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(24.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("CYCLE COUNT", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                    Text("Lifetime charge cycles", color = TextTertiary, style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    text = if (liveData.cycleCount >= 0) liveData.cycleCount.toString() else "N/A",
                    color = TextPrimary,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.05f)))
            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("THERMAL STATUS", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                    val statusText = when {
                        liveData.batteryTemp >= criticalTemp -> "Throttling Risk"
                        liveData.batteryTemp >= warningTemp -> "Elevated"
                        else -> "Optimal"
                    }
                    Text(statusText, color = TextTertiary, style = MaterialTheme.typography.bodySmall)
                }
                if (liveData.batteryTemp >= warningTemp) {
                    Icon(
                        Icons.Filled.Warning,
                        contentDescription = "Elevated temperature",
                        tint = if (liveData.batteryTemp >= criticalTemp) AuraRose else AuraOrange
                    )
                } else {
                    Icon(Icons.Filled.CheckCircle, contentDescription = "Optimal temperature", tint = AuraGreen)
                }
            }
        }

        Spacer(modifier = Modifier.navigationBarsPadding().height(104.dp))
    }
}

@Composable
fun HistoryScreen(viewModel: ThermalViewModel, warningTemp: Float = 35f) {
    val allRecords by viewModel.allRecords.collectAsStateWithLifecycle()
    val criticalTemp = warningTemp + 5f
    val now = System.currentTimeMillis()
    val last7Days = allRecords
        .filter { it.timestamp >= now - (7L * 24 * 60 * 60 * 1000L) }
        .sortedBy { it.timestamp }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(modifier = Modifier.height(14.dp))

        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .height(380.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "7-DAY CYCLE",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp, fontWeight = FontWeight.Bold),
                    color = TextTertiary
                )
                Text(
                    text = "${last7Days.size} samples",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            if (last7Days.size < 2) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "INSUFFICIENT TELEMETRY",
                            color = TextSecondary,
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Keep monitoring active to build your 7-day trend",
                            color = TextTertiary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            } else {
                ThermalChart(
                    records = last7Days,
                    modifier = Modifier.fillMaxSize(),
                    lineColor = AuraOrange,
                    fillColorStart = AuraOrange.copy(alpha = 0.2f),
                    fillColorEnd = AuraOrange.copy(alpha = 0.0f),
                    warningTemp = warningTemp,
                    criticalTemp = criticalTemp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (last7Days.isNotEmpty()) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                val maxTemp = last7Days.maxOf { it.batteryTemp }
                val minTemp = last7Days.minOf { it.batteryTemp }
                val avgTemp = last7Days.map { it.batteryTemp }.average()

                Text(
                    text = "7-DAY AGGREGATES",
                    color = TextTertiary,
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(24.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(String.format(Locale.US, "%.1f°", maxTemp), style = MaterialTheme.typography.headlineMedium, color = AuraRose)
                        Text("PEAK", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(String.format(Locale.US, "%.1f°", avgTemp), style = MaterialTheme.typography.headlineMedium, color = AuraCyan)
                        Text("AVERAGE", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(String.format(Locale.US, "%.1f°", minTemp), style = MaterialTheme.typography.headlineMedium, color = AuraGreen)
                        Text("LOW", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.navigationBarsPadding().height(104.dp))
    }
}
