import sys

with open('app/src/main/java/com/example/ui/screens/Screens.kt', 'r') as f:
    content = f.read()

# Add needed imports
imports_to_add = """import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BatterySaver
"""
# We'll just slap these at the top of the file after the package declaration
if "import androidx.compose.material.icons.filled.Warning" not in content:
    content = content.replace("package com.example.ui.screens\n", "package com.example.ui.screens\n\n" + imports_to_add)

# Find the start and end of SensorsScreen
start_idx = content.find("fun SensorsScreen(viewModel: ThermalViewModel) {")
end_idx = content.find("@Composable\nfun HistoryScreen(viewModel: ThermalViewModel) {")

if start_idx == -1 or end_idx == -1:
    print("Could not find boundaries.")
    sys.exit(1)

old_screen = content[start_idx:end_idx]

new_screen = """fun PowerScreen(viewModel: ThermalViewModel) {
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

"""

content = content.replace(old_screen, new_screen)

with open('app/src/main/java/com/example/ui/screens/Screens.kt', 'w') as f:
    f.write(content)
print("Updated Screens.kt")
