package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.service.LiveGuardMonitor
import com.example.service.ThermalTrackerService
import com.example.ui.screens.FilesScreen
import com.example.ui.screens.LiveGuardScreen
import com.example.ui.screens.PrivacyShieldScreen
import com.example.ui.screens.VitalsScreen
import com.example.ui.theme.AppTheme
import com.example.ui.theme.AuraCyan
import com.example.ui.theme.AuraGreen
import com.example.ui.theme.AuraOrange
import com.example.ui.theme.AuraRose
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassPanel
import com.example.ui.theme.Obsidian
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import com.example.viewmodel.ThermalViewModel
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

class MainActivity : ComponentActivity() {
    private val viewModel: ThermalViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("thermal_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("background_monitoring", true)) {
            startTrackingServiceSafely(this)
        }

        enableEdgeToEdge()
        setContent {
            AppTheme {
                MainScreen(viewModel)
            }
        }
    }
}

fun startTrackingServiceSafely(context: Context) {
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

fun stopTrackingServiceSafely(context: Context) {
    try {
        context.stopService(Intent(context, ThermalTrackerService::class.java))
    } catch (_: Exception) {
    }
}

data class TabItem(val title: String, val selectedIcon: ImageVector, val unselectedIcon: ImageVector)

@Composable
fun MainScreen(viewModel: ThermalViewModel) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val prefs = remember { context.getSharedPreferences("thermal_prefs", Context.MODE_PRIVATE) }

    var warningTemp by remember { mutableFloatStateOf(prefs.getFloat("warning_temp", 35f)) }
    var hapticEnabled by remember { mutableStateOf(prefs.getBoolean("haptic_feedback", true)) }
    var bgMonitoringEnabled by remember { mutableStateOf(prefs.getBoolean("background_monitoring", true)) }
    var liveGuardAlertsEnabled by remember { mutableStateOf(prefs.getBoolean("live_guard_alerts", true)) }

    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
            when (key) {
                "warning_temp" -> warningTemp = sp.getFloat("warning_temp", 35f)
                "haptic_feedback" -> hapticEnabled = sp.getBoolean("haptic_feedback", true)
                "background_monitoring" -> bgMonitoringEnabled = sp.getBoolean("background_monitoring", true)
                "live_guard_alerts" -> liveGuardAlertsEnabled = sp.getBoolean("live_guard_alerts", true)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        LiveGuardMonitor.getInstance(context).startMonitoring()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && bgMonitoringEnabled) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    val pagerState = rememberPagerState(pageCount = { 4 })
    val coroutineScope = rememberCoroutineScope()
    val liveData by viewModel.thermalMonitor.liveData.collectAsStateWithLifecycle()
    val captureState by LiveGuardMonitor.getInstance(context).captureState.collectAsStateWithLifecycle()

    val tabs = remember {
        listOf(
            TabItem("Files", Icons.Filled.Folder, Icons.Outlined.Folder),
            TabItem("Privacy", Icons.Filled.Shield, Icons.Outlined.Shield),
            TabItem("Guard", Icons.Filled.Security, Icons.Outlined.Security),
            TabItem("Vitals", Icons.Filled.Speed, Icons.Outlined.Speed)
        )
    }

    val criticalTemp = warningTemp + 5f
    val ambientColor = when {
        captureState.isScreenRecordingOrCasting -> AuraRose
        pagerState.currentPage == 0 -> AuraCyan
        pagerState.currentPage == 1 -> AuraGreen
        pagerState.currentPage == 2 -> if (captureState.isMicActive || captureState.isCameraActive) AuraOrange else AuraCyan
        liveData.batteryTemp >= criticalTemp -> AuraRose
        liveData.batteryTemp >= warningTemp -> AuraOrange
        liveData.batteryTemp >= 20f -> AuraGreen
        else -> AuraCyan
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Obsidian)) {
        val isExpandedScreen = maxWidth > 600.dp

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
                    modifier = Modifier.fillMaxHeight().width(100.dp).statusBarsPadding(),
                    containerColor = Color(0xFF10121A).copy(alpha = 0.65f)
                ) {
                    Spacer(Modifier.height(24.dp))
                    tabs.forEachIndexed { index, tab ->
                        val selected = pagerState.currentPage == index
                        NavigationRailItem(
                            selected = selected,
                            onClick = {
                                if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                coroutineScope.launch { pagerState.animateScrollToPage(index) }
                            },
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
                        0 -> FilesScreen()
                        1 -> PrivacyShieldScreen()
                        2 -> LiveGuardScreen()
                        3 -> VitalsScreen(viewModel, warningTemp)
                    }
                }
            }
        } else {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (page) {
                    0 -> FilesScreen()
                    1 -> PrivacyShieldScreen()
                    2 -> LiveGuardScreen()
                    3 -> VitalsScreen(viewModel, warningTemp)
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp)
                    .fillMaxWidth(0.92f)
                    .clip(RoundedCornerShape(100.dp))
                    .background(Color(0xFF10121A).copy(alpha = 0.82f))
                    .border(1.dp, GlassBorder, RoundedCornerShape(100.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    tabs.forEachIndexed { index, tab ->
                        val selected = pagerState.currentPage == index

                        Box(
                            modifier = Modifier
                                .testTag("nav_tab_${tab.title.lowercase()}")
                                .clip(RoundedCornerShape(100.dp))
                                .background(if (selected) Color.White.copy(alpha = 0.12f) else Color.Transparent)
                                .clickable {
                                    if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(index)
                                    }
                                }
                                .padding(horizontal = if (selected) 16.dp else 14.dp, vertical = 12.dp)
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

        MorphingSettingsOrb(
            prefs = prefs,
            hapticEnabled = hapticEnabled,
            bgMonitoringEnabled = bgMonitoringEnabled,
            liveGuardAlertsEnabled = liveGuardAlertsEnabled,
            warningTemp = warningTemp
        )
    }
}

@Composable
fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(32.dp))
            .background(GlassPanel.copy(alpha = 0.55f))
            .border(1.dp, GlassBorder, RoundedCornerShape(32.dp))
    ) {
        Column(modifier = Modifier.padding(24.dp), content = content)
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
        Spacer(modifier = Modifier.width(12.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, fontWeight = FontWeight.Medium)
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (log10(bytes.toDouble()) / log10(1024.0)).toInt().coerceIn(0, units.lastIndex)
    return String.format(Locale.US, "%.1f %s", bytes / 1024.0.pow(digitGroups.toDouble()), units[digitGroups])
}

fun formatUptime(millis: Long): String {
    val seconds = (millis / 1000) % 60
    val minutes = (millis / (1000 * 60)) % 60
    val hours = (millis / (1000 * 60 * 60)) % 24
    val days = millis / (1000 * 60 * 60 * 24)
    return if (days > 0) "${days}d ${hours}h ${minutes}m" else "${hours}h ${minutes}m ${seconds}s"
}

@Composable
fun MorphingSettingsOrb(
    prefs: SharedPreferences,
    hapticEnabled: Boolean,
    bgMonitoringEnabled: Boolean,
    liveGuardAlertsEnabled: Boolean,
    warningTemp: Float
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var isExpanded by remember { mutableStateOf(false) }

    BackHandler(enabled = isExpanded) {
        isExpanded = false
    }

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
        initialValue = 0.85f,
        targetValue = 1.15f,
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
                .statusBarsPadding()
                .padding(top = 12.dp, end = 16.dp)
                .size(48.dp)
                .testTag("settings_orb_button")
                .semantics {
                    contentDescription = "Open system preferences"
                    role = Role.Button
                }
                .clip(RoundedCornerShape(24.dp))
                .clickable {
                    if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    isExpanded = !isExpanded
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier.size(32.dp).graphicsLayer {
                    scaleX = pulse
                    scaleY = pulse
                    rotationZ = rotation
                }
            ) {
                drawArc(
                    color = AuraCyan.copy(alpha = 0.65f),
                    startAngle = 0f,
                    sweepAngle = 270f,
                    useCenter = false,
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                )
                drawArc(
                    color = AuraRose.copy(alpha = 0.85f),
                    startAngle = 90f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(4.dp.toPx(), 4.dp.toPx()),
                    size = Size(size.width - 8.dp.toPx(), size.height - 8.dp.toPx()),
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }

        // Full Screen Overlay
        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn(tween(300)) + scaleIn(
                initialScale = 0.1f,
                transformOrigin = TransformOrigin(0.9f, 0.1f),
                animationSpec = tween(300, easing = FastOutSlowInEasing)
            ),
            exit = fadeOut(tween(250)) + scaleOut(
                targetScale = 0.1f,
                transformOrigin = TransformOrigin(0.9f, 0.1f),
                animationSpec = tween(250, easing = FastOutSlowInEasing)
            ),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Obsidian.copy(alpha = 0.96f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { isExpanded = false }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 28.dp, vertical = 24.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { /* Consume background clicks */ }
                ) {
                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        "SYSTEM PREFERENCES",
                        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 4.sp, fontWeight = FontWeight.Bold),
                        color = AuraCyan
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    SettingsToggleRow(
                        title = "Haptic Feedback",
                        subtitle = "Tactile feedback on navigation and controls",
                        checked = hapticEnabled,
                        onCheckedChange = { enabled ->
                            prefs.edit().putBoolean("haptic_feedback", enabled).apply()
                            if (enabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    )

                    SettingsToggleRow(
                        title = "Live Screen & Mic Guard Alerts",
                        subtitle = "Warn immediately if an app records screen or audio in background",
                        checked = liveGuardAlertsEnabled,
                        onCheckedChange = { enabled ->
                            if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            prefs.edit().putBoolean("live_guard_alerts", enabled).apply()
                        }
                    )

                    SettingsToggleRow(
                        title = "Background Guard & Thermal Service",
                        subtitle = "Keep screen-recording guard & thermal telemetry active in background",
                        checked = bgMonitoringEnabled,
                        onCheckedChange = { enabled ->
                            if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            prefs.edit().putBoolean("background_monitoring", enabled).apply()
                            if (enabled) {
                                startTrackingServiceSafely(context)
                            } else {
                                stopTrackingServiceSafely(context)
                            }
                        }
                    )

                    SettingsSliderRow(
                        title = "Warning Temp Limit",
                        value = warningTemp,
                        onValueChange = { newTemp ->
                            prefs.edit().putFloat("warning_temp", newTemp).apply()
                        }
                    )

                    Spacer(modifier = Modifier.weight(1f, fill = false))
                    Spacer(modifier = Modifier.height(32.dp))

                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .testTag("settings_close_button")
                            .clip(RoundedCornerShape(100.dp))
                            .clickable { isExpanded = false }
                            .background(Color.White.copy(alpha = 0.12f))
                            .padding(horizontal = 36.dp, vertical = 14.dp)
                    ) {
                        Text(
                            "CLOSE",
                            color = TextPrimary,
                            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
fun SettingsSliderRow(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit
) {
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
            Text(
                "${value.toInt()}°C (Crit ${(value + 5).toInt()}°C)",
                color = AuraCyan,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Slider(
            value = value.coerceIn(30f, 50f),
            onValueChange = onValueChange,
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
fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(title, color = if (checked) TextPrimary else TextSecondary, style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(2.dp))
            Text(subtitle, color = TextTertiary, style = MaterialTheme.typography.bodySmall)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
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
