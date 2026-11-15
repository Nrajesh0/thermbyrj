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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.service.AppRefreshCoordinator
import com.example.service.LiveGuardMonitor
import com.example.service.PermissionUtils
import com.example.service.ThermalTrackerService
import com.example.service.ThermalWidgetProvider
import com.example.ui.screens.FilesScreen
import com.example.ui.screens.GuardRulesActivity
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
import android.widget.Toast
import com.example.service.PreferencesBackupManager
import com.example.service.PrivacyAnalyzer
import com.example.ui.theme.TextTertiary
import com.example.ui.theme.ThemeState
import com.example.viewmodel.ThermalViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

class MainActivity : ComponentActivity() {
    companion object {
        @Volatile
        var isUiForeground: Boolean = false
    }

    private val viewModel: ThermalViewModel by viewModels()
    private var activityScreenRecordingConsumer: java.util.function.Consumer<Int>? = null
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            LiveGuardMonitor.getInstance(applicationContext).notifyActiveChecksNow()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        PreferencesBackupManager.autoRestoreIfCleared(this)
        com.example.service.NotificationChannels.ensureCreated(this)
        val prefs = getSharedPreferences("thermal_prefs", Context.MODE_PRIVATE)
        ThemeState.loadFromPrefs(this)
        if (prefs.getBoolean("background_monitoring", true)) {
            startTrackingServiceSafely(this)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            try {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } catch (_: Throwable) {
            }
        }

        // Staggered, low-impact background pre-warm after initial UI and storage scan settle
        lifecycleScope.launch(Dispatchers.IO) {
            com.example.ui.screens.VaultManager.cleanupStalePreviewCache(applicationContext)
            delay(1_200L)
            try {
                if (PrivacyAnalyzer.peekCachedPrivacySummary(false) == null) {
                    val summary = PrivacyAnalyzer.analyzeDevicePrivacy(applicationContext, includeSystemApps = false)
                    delay(250L)
                    PrivacyAnalyzer.getPrivacyChanges(applicationContext, summary.appReports)
                }
                delay(300L)
                val guard = LiveGuardMonitor.getInstance(applicationContext)
                guard.runDeepSecurityAudit(includeSystemApps = false)
                delay(300L)
                PrivacyAnalyzer.getPermissionAccessTimeline(applicationContext, includeSystemApps = false, forceRefresh = true)
            } catch (_: Throwable) {
            }
        }

        enableEdgeToEdge()
        setContent {
            AppTheme {
                MainScreen(viewModel)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        isUiForeground = true
        com.example.service.ThermalMonitor.getInstance(applicationContext).startMonitoring()
        val guard = LiveGuardMonitor.getInstance(applicationContext)
        guard.startMonitoring()
        if (Build.VERSION.SDK_INT >= 35 && activityScreenRecordingConsumer == null) {
            try {
                val appCtx = applicationContext
                val consumer = java.util.function.Consumer<Int> { state ->
                    try {
                        LiveGuardMonitor.getInstance(appCtx).onOsScreenRecordingStateChanged(state == 1)
                    } catch (_: Throwable) {
                    }
                }
                val initialState = windowManager.addScreenRecordingCallback(mainExecutor, consumer)
                activityScreenRecordingConsumer = consumer
                guard.onOsScreenRecordingStateChanged(initialState == 1)
            } catch (_: Throwable) {
            }
        }
    }

    override fun onStop() {
        if (Build.VERSION.SDK_INT >= 35) {
            activityScreenRecordingConsumer?.let { consumer ->
                try {
                    windowManager.removeScreenRecordingCallback(consumer)
                } catch (_: Throwable) {
                }
                activityScreenRecordingConsumer = null
            }
        }
        super.onStop()
        isUiForeground = false
        val prefs = getSharedPreferences("thermal_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("background_monitoring", true)) {
            com.example.service.ThermalMonitor.getInstance(applicationContext).stopMonitoring()
            LiveGuardMonitor.getInstance(applicationContext).stopMonitoring()
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
    var thermalAlertsEnabled by remember { mutableStateOf(prefs.getBoolean("thermal_alerts_enabled", true)) }
    val syncScope = rememberCoroutineScope()

    DisposableEffect(prefs) {
        var backupJob: Job? = null
        var widgetJob: Job? = null
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
            when (key) {
                "warning_temp" -> {
                    warningTemp = sp.getFloat("warning_temp", 35f)
                    widgetJob?.cancel()
                    widgetJob = syncScope.launch(Dispatchers.IO) {
                        ThermalWidgetProvider.updateAllWidgets(context)
                    }
                }
                "haptic_feedback" -> hapticEnabled = sp.getBoolean("haptic_feedback", true)
                "background_monitoring" -> bgMonitoringEnabled = sp.getBoolean("background_monitoring", true)
                "live_guard_alerts" -> liveGuardAlertsEnabled = sp.getBoolean("live_guard_alerts", true)
                "thermal_alerts_enabled" -> thermalAlertsEnabled = sp.getBoolean("thermal_alerts_enabled", true)
                "theme_accent", "theme_surface", "ultra_minimal_cards", "ambient_glow" -> {
                    ThemeState.loadFromPrefs(context)
                    widgetJob?.cancel()
                    widgetJob = syncScope.launch(Dispatchers.IO) {
                        ThermalWidgetProvider.updateAllWidgets(context)
                    }
                }
            }
            if (key != null && key != "prefs_initialized") {
                backupJob?.cancel()
                backupJob = syncScope.launch(Dispatchers.IO) {
                    delay(500L)
                    PreferencesBackupManager.saveMirrorSnapshot(context)
                }
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            backupJob?.cancel()
            widgetJob?.cancel()
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
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
    val pendingExplorerPath by com.example.service.PendingExplorerNavigation.targetFolderPath.collectAsStateWithLifecycle()
    val liveData by viewModel.thermalMonitor.liveData.collectAsStateWithLifecycle()
    val captureState by LiveGuardMonitor.getInstance(context).captureState.collectAsStateWithLifecycle()
    val isRefreshingAll by AppRefreshCoordinator.isRefreshing.collectAsStateWithLifecycle()

    LaunchedEffect(pendingExplorerPath) {
        if (pendingExplorerPath != null && pagerState.currentPage != 0) {
            pagerState.animateScrollToPage(0)
        }
    }

    val tabs = remember {
        listOf(
            TabItem("Files", Icons.Filled.Folder, Icons.Outlined.Folder),
            TabItem("Privacy", Icons.Filled.Shield, Icons.Outlined.Shield),
            TabItem("Guard", Icons.Filled.Security, Icons.Outlined.Security),
            TabItem("Tools", Icons.Filled.Tune, Icons.Outlined.Tune)
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
                                indicatorColor = AuraCyan.copy(alpha = 0.16f),
                                unselectedIconColor = TextSecondary,
                                unselectedTextColor = TextSecondary
                            )
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    NavigationRailItem(
                        selected = isRefreshingAll,
                        onClick = {
                            if (!isRefreshingAll) {
                                if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                coroutineScope.launch {
                                    AppRefreshCoordinator.triggerRefreshAll(context)
                                    Toast.makeText(context, "Refreshed", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        icon = {
                            if (isRefreshingAll) {
                                CircularProgressIndicator(
                                    color = AuraCyan,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(18.dp)
                                )
                            } else {
                                Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                            }
                        },
                        label = { Text("Refresh") },
                        modifier = Modifier.testTag("bottom_refresh_button"),
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = AuraCyan,
                            selectedTextColor = AuraCyan,
                            indicatorColor = AuraCyan.copy(alpha = 0.16f),
                            unselectedIconColor = AuraCyan,
                            unselectedTextColor = TextSecondary
                        )
                    )
                    Spacer(Modifier.height(16.dp))
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

            // Floating Refresh Button positioned above the footbar
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 20.dp, bottom = 76.dp)
                    .size(48.dp)
                    .testTag("bottom_refresh_button")
                    .clip(CircleShape)
                    .background(GlassPanel.copy(alpha = 0.92f))
                    .border(
                        1.dp,
                        if (isRefreshingAll) AuraCyan else GlassBorder,
                        CircleShape
                    )
                    .clickable(enabled = !isRefreshingAll) {
                        if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        coroutineScope.launch {
                            AppRefreshCoordinator.triggerRefreshAll(context)
                            Toast.makeText(context, "Refreshed", Toast.LENGTH_SHORT).show()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (isRefreshingAll) {
                    CircularProgressIndicator(
                        color = AuraCyan,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Refresh",
                        tint = AuraCyan,
                        modifier = Modifier.size(20.dp)
                    )
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
            thermalAlertsEnabled = thermalAlertsEnabled,
            warningTemp = warningTemp
        )
    }
}

@Composable
fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val corner = if (ThemeState.ultraMinimalCards) 14.dp else 18.dp
    val pad = if (ThemeState.ultraMinimalCards) 12.dp else 15.dp
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
    thermalAlertsEnabled: Boolean,
    warningTemp: Float
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var isExpanded by remember { mutableStateOf(false) }
    var autoDocBackup by remember { mutableStateOf(prefs.getBoolean("auto_backup_documents", true)) }

    var hasNotifPerm by remember { mutableStateOf(PermissionUtils.hasNotificationPermission(context)) }
    var hasMediaPerm by remember { mutableStateOf(PermissionUtils.hasMediaPermissions(context)) }
    var hasAllFilesPerm by remember { mutableStateOf(PermissionUtils.hasAllFilesPermission()) }
    var hasUsagePerm by remember { mutableStateOf(PermissionUtils.hasUsageStatsPermission(context)) }
    var mediaPermRequestedBefore by remember { mutableStateOf(false) }
    var notifPermRequestedBefore by remember { mutableStateOf(false) }

    fun refreshPermissionStates() {
        hasNotifPerm = PermissionUtils.hasNotificationPermission(context)
        hasMediaPerm = PermissionUtils.hasMediaPermissions(context)
        hasAllFilesPerm = PermissionUtils.hasAllFilesPermission()
        hasUsagePerm = PermissionUtils.hasUsageStatsPermission(context)
    }

    val orbNotifLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotifPerm = granted
        if (!granted && notifPermRequestedBefore) {
            PermissionUtils.launchNotificationSettings(context)
        }
        notifPermRequestedBefore = true
    }

    val orbMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        val granted = PermissionUtils.hasMediaPermissions(context)
        hasMediaPerm = granted
        if (!granted && mediaPermRequestedBefore) {
            PermissionUtils.launchAppDetailsSettings(context)
        }
        mediaPermRequestedBefore = true
    }

    val orbSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        refreshPermissionStates()
    }

    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshPermissionStates()
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    LaunchedEffect(isExpanded) {
        if (isExpanded) refreshPermissionStates()
    }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) { PreferencesBackupManager.exportToUri(context, uri) }
                Toast.makeText(
                    context,
                    if (ok) "Settings & Guard rules exported" else "Failed to export backup",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) { PreferencesBackupManager.importFromUri(context, uri) }
                if (ok) {
                    autoDocBackup = prefs.getBoolean("auto_backup_documents", true)
                    Toast.makeText(context, "Settings & Guard rules restored!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Invalid backup file", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

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
                .size(38.dp)
                .testTag("settings_orb_button")
                .semantics {
                    contentDescription = "Open system preferences"
                    role = Role.Button
                }
                .clip(RoundedCornerShape(12.dp))
                .background(GlassPanel.copy(alpha = 0.7f))
                .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
                .clickable {
                    if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    isExpanded = !isExpanded
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier.size(20.dp).graphicsLayer {
                    rotationZ = rotation
                }
            ) {
                drawArc(
                    color = AuraCyan.copy(alpha = 0.85f),
                    startAngle = 0f,
                    sweepAngle = 270f,
                    useCenter = false,
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                )
                drawArc(
                    color = AuraRose.copy(alpha = 0.85f),
                    startAngle = 90f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(3.5.dp.toPx(), 3.5.dp.toPx()),
                    size = Size(size.width - 7.dp.toPx(), size.height - 7.dp.toPx()),
                    style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
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
                        subtitle = "Warn immediately if an app records screen, mic or camera",
                        checked = liveGuardAlertsEnabled,
                        onCheckedChange = { enabled ->
                            if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            prefs.edit().putBoolean("live_guard_alerts", enabled).apply()
                        }
                    )

                    SettingsToggleRow(
                        title = "Thermal Overheat Alerts",
                        subtitle = "Warn when battery reaches your Warning or Critical temp limit",
                        checked = thermalAlertsEnabled,
                        onCheckedChange = { enabled ->
                            if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            prefs.edit().putBoolean("thermal_alerts_enabled", enabled).apply()
                        }
                    )

                    SettingsToggleRow(
                        title = "Background Guard & Thermal Service",
                        subtitle = "Keep background status monitor active in the notification shade",
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

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_open_guard_rules")
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                isExpanded = false
                                context.startActivity(Intent(context, GuardRulesActivity::class.java))
                            }
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                "Guard Rules, Alert Modes & Snoozes",
                                color = TextPrimary,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "Smart Covert filtering, call silencing, snoozes & trusted apps",
                                color = TextTertiary,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Open Guard Rules",
                            tint = AuraCyan
                        )
                    }

                    SettingsSliderRow(
                        title = "Warning Temp Limit",
                        value = warningTemp,
                        onValueChange = { newTemp ->
                            prefs.edit().putFloat("warning_temp", newTemp).apply()
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = GlassBorder)
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        "APP PERMISSIONS",
                        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 3.sp, fontWeight = FontWeight.Bold),
                        color = AuraCyan
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    SettingsPermissionRow(
                        title = "Notifications",
                        subtitle = "System categories: Background Monitor, Live Guard, Thermal Alerts & Shredder",
                        granted = hasNotifPerm,
                        tag = "perm_row_notifications",
                        onAction = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotifPerm) {
                                orbNotifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                PermissionUtils.launchNotificationSettings(context)
                            }
                        }
                    )

                    SettingsPermissionRow(
                        title = "Media Access (Photos, Videos, Audio)",
                        subtitle = "Scan & manage photos, videos, audio and documents",
                        granted = hasMediaPerm || hasAllFilesPerm,
                        tag = "perm_row_media",
                        onAction = {
                            if (!hasMediaPerm) {
                                orbMediaLauncher.launch(PermissionUtils.requiredMediaPermissions())
                            } else {
                                PermissionUtils.launchAppDetailsSettings(context)
                            }
                        }
                    )

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        SettingsPermissionRow(
                            title = "All Files Access",
                            subtitle = "Folder Explorer, Deep Clean, Duplicates & direct byte Shredder",
                            granted = hasAllFilesPerm,
                            tag = "perm_row_all_files",
                            onAction = {
                                try {
                                    orbSettingsLauncher.launch(PermissionUtils.buildAllFilesAccessIntent(context))
                                } catch (_: Exception) {
                                    PermissionUtils.launchAllFilesAccessSettings(context)
                                }
                            }
                        )
                    }

                    SettingsPermissionRow(
                        title = "Usage Access",
                        subtitle = "Identify foreground app for Live Guard, Timeline & App sizes",
                        granted = hasUsagePerm,
                        tag = "perm_row_usage_access",
                        onAction = {
                            try {
                                orbSettingsLauncher.launch(Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS))
                            } catch (_: Exception) {
                                PermissionUtils.launchUsageAccessSettings(context)
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = GlassBorder)
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        "BACKUP & RESTORE SETTINGS",
                        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 3.sp, fontWeight = FontWeight.Bold),
                        color = AuraCyan
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    SettingsToggleRow(
                        title = "Auto-Backup to Documents",
                        subtitle = "Sync theme, rules & starred folders to Documents/dot (when All Files Access is granted)",
                        checked = autoDocBackup,
                        onCheckedChange = { enabled ->
                            autoDocBackup = enabled
                            prefs.edit().putBoolean("auto_backup_documents", enabled).apply()
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .testTag("export_settings_button")
                                .clip(RoundedCornerShape(12.dp))
                                .background(AuraCyan.copy(alpha = 0.14f))
                                .border(1.dp, AuraCyan.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                .clickable {
                                    exportBackupLauncher.launch("dot_settings_backup.json")
                                }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "EXPORT JSON",
                                color = AuraCyan,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .testTag("import_settings_button")
                                .clip(RoundedCornerShape(12.dp))
                                .background(GlassPanel)
                                .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
                                .clickable {
                                    importBackupLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                                }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "RESTORE JSON",
                                color = TextPrimary,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }

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
    var sliderPos by remember(value) { mutableFloatStateOf(value.coerceIn(30f, 50f)) }

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
                "${sliderPos.toInt()}°C (Crit ${(sliderPos + 5).toInt()}°C)",
                color = AuraCyan,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Slider(
            value = sliderPos,
            onValueChange = { sliderPos = it },
            onValueChangeFinished = { onValueChange(sliderPos) },
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

@Composable
fun SettingsPermissionRow(
    title: String,
    subtitle: String,
    granted: Boolean,
    tag: String,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onAction() }
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = TextPrimary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(2.dp))
            Text(subtitle, color = TextTertiary, style = MaterialTheme.typography.labelSmall)
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(100.dp))
                .background(if (granted) AuraGreen.copy(alpha = 0.16f) else AuraOrange.copy(alpha = 0.18f))
                .border(1.dp, if (granted) AuraGreen.copy(alpha = 0.5f) else AuraOrange.copy(alpha = 0.55f), RoundedCornerShape(100.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (granted) "GRANTED" else "GRANT",
                color = if (granted) AuraGreen else AuraOrange,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
            )
        }
    }
}
