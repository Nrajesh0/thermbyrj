package com.example

import android.Manifest
import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.HorizontalDivider
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
import com.example.ui.theme.AccentPreset
import com.example.ui.theme.AppTheme
import com.example.ui.theme.AuraCyan
import com.example.ui.theme.AuraGreen
import com.example.ui.theme.AuraOrange
import com.example.ui.theme.AuraRose
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassPanel
import com.example.ui.theme.Obsidian
import com.example.ui.theme.SurfacePreset
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import androidx.lifecycle.lifecycleScope
import com.example.service.PrivacyAnalyzer
import com.example.ui.theme.TextTertiary
import com.example.ui.theme.ThemeState
import com.example.viewmodel.ThermalViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

class MainActivity : ComponentActivity() {
    private val viewModel: ThermalViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("thermal_prefs", Context.MODE_PRIVATE)
        ThemeState.loadFromPrefs(this)
        if (prefs.getBoolean("background_monitoring", true)) {
            startTrackingServiceSafely(this)
        }

        // Pre-warm Privacy & Guard caches on background IO thread so tab switches are 0ms instant
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val summary = PrivacyAnalyzer.analyzeDevicePrivacy(applicationContext, includeSystemApps = false)
                PrivacyAnalyzer.getPrivacyChanges(applicationContext, summary.appReports)
                PrivacyAnalyzer.analyzeAppTelemetryAndTrackers(applicationContext, includeSystemApps = false)
                LiveGuardMonitor.getInstance(applicationContext).runDeepSecurityAudit(includeSystemApps = false)
                PrivacyAnalyzer.getPermissionAccessTimeline(applicationContext, includeSystemApps = false)
            } catch (_: Exception) {
            }
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
                "theme_accent", "theme_surface", "ultra_minimal_cards", "ambient_glow" -> {
                    ThemeState.loadFromPrefs(context)
                }
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
        pagerState.currentPage == 1 -> AuraCyan
        pagerState.currentPage == 2 -> if (captureState.isMicActive || captureState.isCameraActive) AuraOrange else AuraCyan
        liveData.batteryTemp >= criticalTemp -> AuraRose
        liveData.batteryTemp >= warningTemp -> AuraOrange
        liveData.batteryTemp >= 20f -> AuraGreen
        else -> AuraCyan
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Obsidian)) {
        val isExpandedScreen = maxWidth > 600.dp

        if (ThemeState.showAmbientGlow) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(ambientColor.copy(alpha = 0.14f), Color.Transparent),
                        center = Offset(size.width * 0.8f, size.height * 0.1f),
                        radius = size.width * 0.7f
                    )
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(ambientColor.copy(alpha = 0.07f), Color.Transparent),
                        center = Offset(size.width * 0.2f, size.height * 0.6f),
                        radius = size.width * 0.8f
                    )
                )
            }
        }

        if (isExpandedScreen) {
            Row(modifier = Modifier.fillMaxSize()) {
                NavigationRail(
                    modifier = Modifier.fillMaxHeight().width(92.dp).statusBarsPadding(),
                    containerColor = GlassPanel.copy(alpha = 0.8f)
                ) {
                    Spacer(Modifier.height(20.dp))
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
                    .padding(bottom = 12.dp)
                    .fillMaxWidth(0.92f)
                    .clip(RoundedCornerShape(100.dp))
                    .background(GlassPanel.copy(alpha = 0.92f))
                    .border(1.dp, GlassBorder, RoundedCornerShape(100.dp))
                    .padding(horizontal = 6.dp, vertical = 5.dp)
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
                                .background(if (selected) AuraCyan.copy(alpha = 0.16f) else Color.Transparent)
                                .clickable {
                                    if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(index)
                                    }
                                }
                                .padding(horizontal = if (selected) 16.dp else 14.dp, vertical = 10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = tab.title,
                                    tint = if (selected) AuraCyan else TextSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                                AnimatedVisibility(visible = selected) {
                                    Row {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = tab.title,
                                            color = TextPrimary,
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontWeight = FontWeight.SemiBold,
                                                letterSpacing = 0.4.sp
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
    val corner = if (ThemeState.ultraMinimalCards) 16.dp else 24.dp
    val pad = if (ThemeState.ultraMinimalCards) 14.dp else 20.dp
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(corner))
            .background(GlassPanel.copy(alpha = 0.65f))
            .border(1.dp, GlassBorder, RoundedCornerShape(corner))
    ) {
        Column(modifier = Modifier.padding(pad), content = content)
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextTertiary)
        Spacer(modifier = Modifier.width(12.dp))
        Text(value, style = MaterialTheme.typography.bodySmall, color = TextPrimary, fontWeight = FontWeight.Medium)
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (log10(bytes.toDouble()) / log10(1000.0)).toInt().coerceIn(0, units.lastIndex)
    return String.format(Locale.US, "%.1f %s", bytes / 1000.0.pow(digitGroups.toDouble()), units[digitGroups])
}

/**
 * Returns (totalBytes, freeBytes) matching Android Settings -> Storage using StorageStatsManager on API 26+,
 * with fallback to StatFs.
 */
fun queryDeviceStorageTotals(context: Context): Pair<Long, Long> {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        try {
            val ssm = context.getSystemService(Context.STORAGE_STATS_SERVICE) as? StorageStatsManager
            if (ssm != null) {
                val total = ssm.getTotalBytes(StorageManager.UUID_DEFAULT)
                val free = ssm.getFreeBytes(StorageManager.UUID_DEFAULT)
                if (total > 0L) {
                    return Pair(total, free.coerceIn(0L, total))
                }
            }
        } catch (_: Exception) {
        }
    }
    return try {
        val statFs = StatFs(Environment.getDataDirectory().path)
        Pair(statFs.totalBytes, statFs.availableBytes)
    } catch (_: Exception) {
        Pair(0L, 0L)
    }
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

    val rotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "orb_rotation_anim"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // The Orb (Top Right)
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 10.dp, end = 16.dp)
                .size(44.dp)
                .testTag("settings_orb_button")
                .semantics {
                    contentDescription = "Open system preferences"
                    role = Role.Button
                }
                .clip(RoundedCornerShape(22.dp))
                .clickable {
                    if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    isExpanded = !isExpanded
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier.size(28.dp).graphicsLayer {
                    rotationZ = rotation
                }
            ) {
                drawArc(
                    color = AuraCyan.copy(alpha = 0.75f),
                    startAngle = 0f,
                    sweepAngle = 270f,
                    useCenter = false,
                    style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
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
            enter = fadeIn(tween(250)) + scaleIn(
                initialScale = 0.1f,
                transformOrigin = TransformOrigin(0.9f, 0.1f),
                animationSpec = tween(250, easing = FastOutSlowInEasing)
            ),
            exit = fadeOut(tween(200)) + scaleOut(
                targetScale = 0.1f,
                transformOrigin = TransformOrigin(0.9f, 0.1f),
                animationSpec = tween(200, easing = FastOutSlowInEasing)
            ),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Obsidian.copy(alpha = 0.97f))
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
                        .padding(horizontal = 24.dp, vertical = 20.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { /* Consume background clicks */ }
                ) {
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        "THEME & APPEARANCE",
                        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 3.sp, fontWeight = FontWeight.Bold),
                        color = AuraCyan
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Accent Color Swatches
                    Text("Accent Color", color = TextPrimary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AccentPreset.entries.forEach { preset ->
                            val selected = ThemeState.accentPreset == preset
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(100.dp))
                                    .background(if (selected) preset.color.copy(alpha = 0.2f) else GlassPanel)
                                    .border(1.5.dp, if (selected) preset.color else GlassBorder, RoundedCornerShape(100.dp))
                                    .clickable {
                                        ThemeState.accentPreset = preset
                                        prefs.edit().putString("theme_accent", preset.key).apply()
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(preset.color),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (selected) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(11.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = preset.label,
                                    color = if (selected) TextPrimary else TextSecondary,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Background Surface Mode (Pure White, Soft White, OLED Black, Obsidian, Dark Slate)
                    Text("Background Surface", color = TextPrimary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SurfacePreset.entries.forEach { preset ->
                            val selected = ThemeState.surfacePreset == preset
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (selected) AuraCyan.copy(alpha = 0.16f) else GlassPanel)
                                    .border(1.5.dp, if (selected) AuraCyan else GlassBorder, RoundedCornerShape(12.dp))
                                    .clickable {
                                        ThemeState.surfacePreset = preset
                                        prefs.edit().putString("theme_surface", preset.key).apply()
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clip(CircleShape)
                                        .background(preset.bg)
                                        .border(1.dp, if (preset.isLight) Color(0xFF94A3B8) else Color(0x55FFFFFF), CircleShape)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = preset.label,
                                    color = if (selected) AuraCyan else TextSecondary,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    SettingsToggleRow(
                        title = "Ultra-Minimal Compact Cards",
                        subtitle = "Sleek, compact spacing and minimal card padding",
                        checked = ThemeState.ultraMinimalCards,
                        onCheckedChange = { enabled ->
                            ThemeState.ultraMinimalCards = enabled
                            prefs.edit().putBoolean("ultra_minimal_cards", enabled).apply()
                        }
                    )

                    SettingsToggleRow(
                        title = "Ambient Background Glow",
                        subtitle = "Subtle neon radial backdrop (turn off for pure OLED)",
                        checked = ThemeState.showAmbientGlow,
                        onCheckedChange = { enabled ->
                            ThemeState.showAmbientGlow = enabled
                            prefs.edit().putBoolean("ambient_glow", enabled).apply()
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = GlassBorder)
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        "SYSTEM PREFERENCES",
                        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 3.sp, fontWeight = FontWeight.Bold),
                        color = AuraCyan
                    )

                    Spacer(modifier = Modifier.height(8.dp))

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
                        subtitle = "Warn immediately if an app records screen or audio",
                        checked = liveGuardAlertsEnabled,
                        onCheckedChange = { enabled ->
                            if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            prefs.edit().putBoolean("live_guard_alerts", enabled).apply()
                        }
                    )

                    SettingsToggleRow(
                        title = "Background Guard & Thermal Service",
                        subtitle = "Keep guard & thermal monitor active in background",
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

                    Spacer(modifier = Modifier.height(16.dp))

                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .testTag("settings_close_button")
                            .clip(RoundedCornerShape(100.dp))
                            .clickable { isExpanded = false }
                            .background(GlassPanel)
                            .border(1.dp, GlassBorder, RoundedCornerShape(100.dp))
                            .padding(horizontal = 36.dp, vertical = 12.dp)
                    ) {
                        Text(
                            "CLOSE",
                            color = TextPrimary,
                            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))
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
            .padding(vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
            Text(
                "${value.toInt()}°C (Crit ${(value + 5).toInt()}°C)",
                color = AuraCyan,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
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
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(title, color = if (checked) TextPrimary else TextSecondary, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(2.dp))
            Text(subtitle, color = TextTertiary, style = MaterialTheme.typography.labelSmall)
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
