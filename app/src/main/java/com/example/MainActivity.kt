package com.example
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke

import android.os.BatteryManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.service.LiveThermalData
import com.example.ui.components.ThermalChart
import com.example.ui.screens.*
import com.example.ui.theme.*
import com.example.viewmodel.ThermalViewModel
import kotlinx.coroutines.launch

import android.content.Context
import android.content.SharedPreferences
import android.content.Intent
import com.example.service.ThermalTrackerService
import androidx.compose.ui.platform.LocalConfiguration

class MainActivity : ComponentActivity() {
    private val viewModel: ThermalViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Start the background tracking service
        val serviceIntent = Intent(this, ThermalTrackerService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        enableEdgeToEdge()
        setContent {
            AppTheme {
                MainScreen(viewModel)
            }
        }
    }
}

data class TabItem(val title: String, val selectedIcon: ImageVector, val unselectedIcon: ImageVector)

@Composable
fun MainScreen(viewModel: ThermalViewModel) {
    val pagerState = rememberPagerState(pageCount = { 4 })
    val coroutineScope = rememberCoroutineScope()
    val liveData by viewModel.thermalMonitor.liveData.collectAsStateWithLifecycle()
    
    val tabs = listOf(
        TabItem("Core", Icons.Filled.Speed, Icons.Outlined.Speed),
        TabItem("Device", Icons.Filled.Memory, Icons.Outlined.Memory),
        TabItem("Power", Icons.Filled.BatteryFull, Icons.Outlined.BatteryFull),
        TabItem("Trends", Icons.Filled.Analytics, Icons.Outlined.Analytics)
    )
    // Force live reload

    // Dynamic theming based on battery temperature
    val ambientColor = when {
        liveData.batteryTemp >= 40f -> AuraRose
        liveData.batteryTemp >= 35f -> AuraOrange
        liveData.batteryTemp >= 20f -> AuraGreen
        else -> AuraCyan
    }

    val isExpandedScreen = LocalConfiguration.current.screenWidthDp > 600

    Box(modifier = Modifier.fillMaxSize().background(Obsidian)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(ambientColor.copy(alpha = 0.15f), Color.Transparent),
                    center = Offset(size.width * 0.8f, size.height * 0.1f),
                    radius = size.width * 0.7f
                )
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(ambientColor.copy(alpha = 0.08f), Color.Transparent),
                    center = Offset(size.width * 0.2f, size.height * 0.6f),
                    radius = size.width * 0.8f
                )
            )
        }

        if (isExpandedScreen) {
            Row(modifier = Modifier.fillMaxSize()) {
                NavigationRail(
                    modifier = Modifier.fillMaxHeight().width(100.dp),
                    containerColor = Color(0xFF10121A).copy(alpha = 0.65f)
                ) {
                    Spacer(Modifier.height(48.dp))
                    tabs.forEachIndexed { index, tab ->
                        val selected = pagerState.currentPage == index
                        NavigationRailItem(
                            selected = selected,
                            onClick = { coroutineScope.launch { pagerState.animateScrollToPage(index) } },
                            icon = { Icon(if (selected) tab.selectedIcon else tab.unselectedIcon, contentDescription = tab.title) },
                            label = { Text(tab.title) },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = ambientColor,
                                selectedTextColor = ambientColor,
                                indicatorColor = Color.White.copy(alpha = 0.1f),
                                unselectedIconColor = TextSecondary,
                                unselectedTextColor = TextSecondary
                            )
                        )
                    }
                }
                
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize().weight(1f)
                ) { page ->
                    when (page) {
                        0 -> DashboardScreen(viewModel)
                        1 -> DeviceScreen(viewModel)
                        2 -> PowerScreen(viewModel)
                        3 -> HistoryScreen(viewModel)
                    }
                }
            }
        } else {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (page) {
                    0 -> DashboardScreen(viewModel)
                    1 -> DeviceScreen(viewModel)
                    2 -> PowerScreen(viewModel)
                    3 -> HistoryScreen(viewModel)
                }
            }

            Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp)
                .fillMaxWidth(0.95f)
                .clip(RoundedCornerShape(100.dp))
                .background(Color(0xFF10121A).copy(alpha = 0.65f))
                .border(1.dp, GlassBorder, RoundedCornerShape(100.dp))
                .padding(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEachIndexed { index, tab ->
                    val selected = pagerState.currentPage == index
                    val interactionSource = remember { MutableInteractionSource() }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(100.dp))
                            .background(if (selected) Color.White.copy(alpha = 0.1f) else Color.Transparent)
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null
                            ) {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(index)
                                }
                            }
                            .padding(horizontal = if (selected) 16.dp else 12.dp, vertical = 12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = tab.title,
                                tint = if (selected) TextPrimary else TextSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                            AnimatedVisibility(visible = selected) {
                                Row {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = tab.title,
                                        color = TextPrimary,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            letterSpacing = 0.5.sp
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        }
        MorphingSettingsOrb()
    }
}

@Composable
fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(32.dp))
            .background(GlassPanel.copy(alpha = 0.5f))
            .border(1.dp, GlassBorder, RoundedCornerShape(32.dp))
    ) {
        Column(modifier = Modifier.padding(28.dp), content = content)
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextTertiary)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, fontWeight = FontWeight.Medium)
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format("%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

fun formatUptime(millis: Long): String {
    val seconds = (millis / 1000) % 60
    val minutes = (millis / (1000 * 60)) % 60
    val hours = (millis / (1000 * 60 * 60)) % 24
    val days = millis / (1000 * 60 * 60 * 24)
    return if (days > 0) "${days}d ${hours}h ${minutes}m" else "${hours}h ${minutes}m ${seconds}s"
}

@Composable
fun MorphingSettingsOrb() {
    var isExpanded by remember { mutableStateOf(false) }
    
    val infiniteTransition = rememberInfiniteTransition(label = "orb_rotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orb_rotation_anim"
    )

    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb_pulse_anim"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        
        // The Orb (Top Right)
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 48.dp, end = 24.dp)
                .size(48.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { isExpanded = !isExpanded },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(32.dp).graphicsLayer {
                scaleX = pulse
                scaleY = pulse
                rotationZ = rotation
            }) {
                drawArc(
                    color = AuraCyan.copy(alpha = 0.6f),
                    startAngle = 0f,
                    sweepAngle = 270f,
                    useCenter = false,
                    style = Stroke(width = 4.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                )
                drawArc(
                    color = AuraRose.copy(alpha = 0.8f),
                    startAngle = 90f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(4.dp.toPx(), 4.dp.toPx()),
                    size = Size(size.width - 8.dp.toPx(), size.height - 8.dp.toPx()),
                    style = Stroke(width = 2.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                )
            }
        }

        // Full Screen Overlay
        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn(tween(400)) + scaleIn(initialScale = 0.05f, transformOrigin = TransformOrigin(0.9f, 0.1f), animationSpec = tween(400, easing = FastOutSlowInEasing)),
            exit = fadeOut(tween(300)) + scaleOut(targetScale = 0.05f, transformOrigin = TransformOrigin(0.9f, 0.1f), animationSpec = tween(300, easing = FastOutSlowInEasing)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Obsidian.copy(alpha = 0.95f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { isExpanded = false } // Click background to close
            ) {
                // Settings Content
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { /* Consume clicks so they don't close the overlay */ }
                ) {
                    Spacer(modifier = Modifier.height(64.dp))
                    
                    Text(
                        "SYSTEM PREFERENCES", 
                        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 4.sp, fontWeight = FontWeight.Bold),
                        color = AuraCyan
                    )
                    
                    Spacer(modifier = Modifier.height(48.dp))
                    
                    SettingsToggleRow(title = "Haptic Feedback", initial = true)
                    SettingsToggleRow(title = "Background Monitoring", initial = true)
                    val prefs = LocalContext.current.getSharedPreferences("thermal_prefs", Context.MODE_PRIVATE)
                    SettingsSliderRow(title = "Warning Temp Limit", prefs = prefs)
                    SettingsToggleRow(title = "Auto-Calibration", initial = false)
                    
                    Spacer(modifier = Modifier.weight(1f))
                    
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .clip(RoundedCornerShape(100.dp))
                            .clickable { isExpanded = false }
                            .background(Color.White.copy(alpha = 0.1f))
                            .padding(horizontal = 32.dp, vertical = 12.dp)
                    ) {
                        Text("CLOSE", color = TextPrimary, style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 2.sp))
                    }
                    
                    Spacer(modifier = Modifier.height(48.dp))
                }
            }
        }
    }
}

@Composable
fun SettingsSliderRow(title: String, prefs: SharedPreferences) {
    var value by remember { mutableStateOf(prefs.getFloat("warning_temp", 35f)) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, color = TextPrimary, style = MaterialTheme.typography.bodyLarge)
            Text("${value.toInt()}°C", color = AuraCyan, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
        }
        Slider(
            value = value,
            onValueChange = { 
                value = it 
                prefs.edit().putFloat("warning_temp", it).apply()
            },
            valueRange = 30f..50f,
            steps = 19,
            colors = SliderDefaults.colors(
                thumbColor = AuraCyan,
                activeTrackColor = AuraCyan,
                inactiveTrackColor = GlassPanel
            )
        )
    }
}

@Composable
fun SettingsToggleRow(title: String, initial: Boolean) {
    var checked by remember { mutableStateOf(initial) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { checked = !checked }
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = if (checked) TextPrimary else TextSecondary, style = MaterialTheme.typography.bodyLarge)
        Switch(
            checked = checked,
            onCheckedChange = { checked = it },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Obsidian,
                checkedTrackColor = AuraCyan,
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = GlassPanel,
                uncheckedBorderColor = TextSecondary.copy(alpha = 0.3f)
            )
        )
    }
}
