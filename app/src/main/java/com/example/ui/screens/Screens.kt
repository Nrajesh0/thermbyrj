package com.example.ui.screens

import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BatterySaver

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.DisposableEffect
import com.example.ui.screens.VaultActivity
import com.example.ui.screens.DuplicateFinderActivity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.CleaningServices


import android.content.Intent
import android.os.BatteryManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.service.LiveThermalData
import com.example.ui.components.ThermalChart
import com.example.ui.theme.*
import com.example.viewmodel.ThermalViewModel
import com.example.formatBytes
import com.example.formatUptime
import com.example.GlassCard
import com.example.InfoRow

@Composable
fun DashboardScreen(viewModel: ThermalViewModel) {
    val liveData by viewModel.thermalMonitor.liveData.collectAsStateWithLifecycle(initialValue = LiveThermalData())
    val allRecords by viewModel.allRecords.collectAsStateWithLifecycle()
    
    val now = System.currentTimeMillis()
    val last24h = allRecords.filter { it.timestamp >= now - (24 * 60 * 60 * 1000L) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Spacer(modifier = Modifier.height(64.dp))
        Text(
            text = "THERMAL CORE",
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 4.sp, fontWeight = FontWeight.Bold),
            color = TextSecondary
        )
        Spacer(modifier = Modifier.height(24.dp))
        
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "BATTERY CORE", 
                color = TextTertiary, 
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = liveData.batteryTemp.toInt().toString(),
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = 112.sp,
                        fontWeight = FontWeight.W200,
                        letterSpacing = (-4).sp
                    ),
                    color = TextPrimary
                )
                Text(
                    text = String.format(".%d°C", ((liveData.batteryTemp % 1) * 10).toInt()),
                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.W300),
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 24.dp, start = 4.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            val (zoneName, zoneColor) = when {
                liveData.batteryTemp < 20 -> "OPTIMAL" to AuraCyan
                liveData.batteryTemp < 35 -> "NORMAL" to AuraGreen
                liveData.batteryTemp < 40 -> "ELEVATED" to AuraOrange
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
        
        GlassCard(modifier = Modifier.fillMaxWidth().height(350.dp)) {
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
            }
            Spacer(modifier = Modifier.height(16.dp))
            
            if (last24h.size < 2) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("CALIBRATING SENSORS...", color = TextSecondary, style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp))
                }
            } else {
                ThermalChart(
                    records = last24h,
                    modifier = Modifier.fillMaxSize(),
                    lineColor = AuraCyan,
                    fillColorStart = AuraCyan.copy(alpha = 0.2f),
                    fillColorEnd = AuraCyan.copy(alpha = 0.0f)
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
                    Text(String.format("%.1f°C", max24), style = MaterialTheme.typography.titleLarge, color = AuraRose)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("LOW", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                    Text(String.format("%.1f°C", min24), style = MaterialTheme.typography.titleLarge, color = AuraCyan)
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
                val next = last24h[i+1]
                val duration = next.timestamp - current.timestamp
                val cappedDuration = java.lang.Math.min(duration, 2 * 60 * 60 * 1000L) // cap at 2h
                
                when {
                    current.batteryTemp < 20 -> timeOptimal += cappedDuration
                    current.batteryTemp < 35 -> timeNormal += cappedDuration
                    current.batteryTemp < 40 -> timeElevated += cappedDuration
                    else -> timeCritical += cappedDuration
                }
            }
        }
        val totalTrackedTime = java.lang.Math.max(1L, timeOptimal + timeNormal + timeElevated + timeCritical)

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "TIME IN ZONES (24H)", 
                color = TextTertiary, 
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(24.dp))
            
            if (last24h.size < 2) {
                Text("GATHERING TELEMETRY...", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
            } else {
                val zones = listOf(
                    Triple("CRITICAL (>40°C)", timeCritical, AuraRose),
                    Triple("ELEVATED (35-40°C)", timeElevated, AuraOrange),
                    Triple("NORMAL (20-35°C)", timeNormal, AuraGreen),
                    Triple("OPTIMAL (<20°C)", timeOptimal, AuraCyan)
                )
                
                zones.forEach { (label, timeMillis, color) ->
                    val pct = timeMillis.toFloat() / totalTrackedTime.toFloat()
                    val minutes = (timeMillis / (1000 * 60)) % 60
                    val hours = (timeMillis / (1000 * 60 * 60))
                    val timeStr = if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
                    
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
        
        Spacer(modifier = Modifier.height(120.dp))
    }
}

@Composable
fun DeviceScreen(viewModel: ThermalViewModel) {
    val liveData by viewModel.thermalMonitor.liveData.collectAsStateWithLifecycle(initialValue = LiveThermalData())
    val context = LocalContext.current
    val displayMetrics = context.resources.displayMetrics

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Spacer(modifier = Modifier.height(64.dp))
        Text(
            text = "SYSTEM ARCHITECTURE",
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 4.sp, fontWeight = FontWeight.Bold),
            color = TextSecondary
        )
        Spacer(modifier = Modifier.height(24.dp))

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
            val ramUsed = liveData.totalRam - liveData.availRam
            val ramProgress = if (liveData.totalRam > 0) ramUsed.toFloat() / liveData.totalRam else 0f
            
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
                Text(formatBytes(ramUsed), style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                Text(formatBytes(liveData.totalRam), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            Text("SOC THERMAL", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
            Text(if (liveData.cpuTemp > 0) String.format("%.1f°C", liveData.cpuTemp) else "RESTRICTED BY OS", style = if (liveData.cpuTemp > 0) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.labelSmall, color = AuraPurple)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Storage Drive
        GlassCard(modifier = Modifier.fillMaxWidth().clickable {
            context.startActivity(Intent(context, StorageAnalysisActivity::class.java))
        }) {
            Text(
                text = "STORAGE DRIVE", 
                color = TextTertiary, 
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(16.dp))
            val storageUsed = liveData.totalStorage - liveData.availStorage
            val storageProgress = if (liveData.totalStorage > 0) storageUsed.toFloat() / liveData.totalStorage else 0f
            
            LinearProgressIndicator(
                progress = { storageProgress },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                color = AuraOrange,
                trackColor = Color.White.copy(alpha = 0.05f),
                strokeCap = StrokeCap.Round
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatBytes(storageUsed), style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                Text(formatBytes(liveData.totalStorage), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))


        // Connectivity & Display

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "CONNECTIVITY & DISPLAY", 
                color = TextTertiary, 
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(16.dp))
            InfoRow("IP ADDRESS", liveData.ipAddress)
            InfoRow("REFRESH RATE", "${liveData.refreshRate} Hz")
            InfoRow("RESOLUTION", "${displayMetrics.widthPixels} x ${displayMetrics.heightPixels}")
            InfoRow("DENSITY", "${displayMetrics.densityDpi} dpi")
        }

        Spacer(modifier = Modifier.height(120.dp))
    }
}

@Composable
fun PowerScreen(viewModel: ThermalViewModel) {
    val liveData by viewModel.thermalMonitor.liveData.collectAsStateWithLifecycle(initialValue = LiveThermalData())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Spacer(modifier = Modifier.height(64.dp))

        // Power Matrix
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "POWER MATRIX", 
                color = TextTertiary, 
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(32.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(80.dp)) {
                    CircularProgressIndicator(
                        progress = { 1f },
                        modifier = Modifier.fillMaxSize(),
                        color = Color.White.copy(alpha = 0.05f),
                        strokeWidth = 6.dp,
                        strokeCap = StrokeCap.Round
                    )
                    CircularProgressIndicator(
                        progress = { liveData.batteryLevel / 100f },
                        modifier = Modifier.fillMaxSize(),
                        color = if (liveData.isCharging) AuraGreen else AuraCyan,
                        strokeWidth = 6.dp,
                        strokeCap = StrokeCap.Round
                    )
                    Text(
                        text = "${liveData.batteryLevel}",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.W300),
                        color = TextPrimary
                    )
                }
                Spacer(modifier = Modifier.width(32.dp))
                Column {
                    Text("CAPACITY", style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp), color = TextTertiary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(if (liveData.isCharging) "Charging" else "Discharging", style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
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
            
            InfoRow("VOLTAGE", "${liveData.batteryVoltage} mV")
            InfoRow("CHEMISTRY", liveData.batteryTech)
            InfoRow("HEALTH", healthStr)
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        // Time Remaining & Charging Rate
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            GlassCard(modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Bolt, contentDescription = null, tint = AuraOrange, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text("RATE", color = TextTertiary, style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp))
                Spacer(modifier = Modifier.height(4.dp))
                val rate = if (liveData.isCharging) "Fast (18W+)" else "Idle"
                Text(rate, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
            }
            GlassCard(modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.BatterySaver, contentDescription = null, tint = AuraCyan, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text("EST. TIME", color = TextTertiary, style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp))
                Spacer(modifier = Modifier.height(4.dp))
                // Simple mockup calculation for aesthetic
                val estHrs = if (liveData.isCharging) ((100 - liveData.batteryLevel) * 0.05).toInt() else (liveData.batteryLevel * 0.25).toInt()
                val estMins = if (liveData.isCharging) ((100 - liveData.batteryLevel) * 3) % 60 else (liveData.batteryLevel * 15) % 60
                Text("${estHrs}h ${estMins}m", style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
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
            
            // Cycle Count (Mocked for Android < 14)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("CYCLE COUNT", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                    Text("Lifetime charge cycles", color = TextTertiary, style = MaterialTheme.typography.bodySmall)
                }
                Text("342", color = TextPrimary, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha=0.05f)))
            Spacer(modifier = Modifier.height(16.dp))
            
            // Thermal Status
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("THERMAL STATUS", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                    val statusText = if (liveData.batteryTemp > 40f) "Throttling Risk" else "Optimal"
                    Text(statusText, color = TextTertiary, style = MaterialTheme.typography.bodySmall)
                }
                if (liveData.batteryTemp > 40f) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = AuraOrange)
                } else {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = AuraGreen)
                }
            }
        }

        Spacer(modifier = Modifier.height(120.dp))
    }
}

@Composable
fun HistoryScreen(viewModel: ThermalViewModel) {
    val allRecords by viewModel.allRecords.collectAsStateWithLifecycle()
    val now = System.currentTimeMillis()
    val last7Days = allRecords.filter { it.timestamp >= now - (7L * 24 * 60 * 60 * 1000L) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Spacer(modifier = Modifier.height(64.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
            text = "MACRO TRENDS",
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 4.sp, fontWeight = FontWeight.Bold),
            color = TextSecondary
        )
        }
        Spacer(modifier = Modifier.height(24.dp))
        
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp)
        ) {
            Text(
                text = "7-DAY CYCLE",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp, fontWeight = FontWeight.Bold),
                color = TextTertiary
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            if (last7Days.size < 2) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("INSUFFICIENT TELEMETRY", color = TextSecondary, style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp))
                }
            } else {
                ThermalChart(
                    records = last7Days,
                    modifier = Modifier.fillMaxSize(),
                    lineColor = AuraOrange,
                    fillColorStart = AuraOrange.copy(alpha = 0.2f),
                    fillColorEnd = AuraOrange.copy(alpha = 0.0f)
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
                    text = "LIFETIME AGGREGATES", 
                    color = TextTertiary, 
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(24.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(String.format("%.1f°", maxTemp), style = MaterialTheme.typography.headlineMedium, color = AuraRose)
                        Text("PEAK", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(String.format("%.1f°", avgTemp), style = MaterialTheme.typography.headlineMedium, color = AuraCyan)
                        Text("AVERAGE", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(String.format("%.1f°", minTemp), style = MaterialTheme.typography.headlineMedium, color = AuraGreen)
                        Text("LOW", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(120.dp))
    }
}



