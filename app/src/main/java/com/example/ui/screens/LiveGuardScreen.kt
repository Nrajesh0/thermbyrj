package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.ScreenShare
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderShared
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MarkunreadMailbox
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.GlassCard
import com.example.startTrackingServiceSafely
import com.example.service.AppRefreshCoordinator
import com.example.service.AuditSeverity
import com.example.service.GuardEvent
import com.example.service.LiveCaptureState
import com.example.service.LiveGuardMonitor
import com.example.service.PermissionAccessEvent
import com.example.service.PermissionCategory
import com.example.service.PlainPermissionInfo
import com.example.service.PrivacyAnalyzer
import com.example.service.SecurityAuditVector
import com.example.ui.theme.AuraCyan
import com.example.ui.theme.AuraGreen
import com.example.ui.theme.AuraOrange
import com.example.ui.theme.AuraPurple
import com.example.ui.theme.AuraRose
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassPanel
import com.example.ui.theme.Obsidian
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private fun hasPostNotificationsPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
}

private fun openAppNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    }
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        try {
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null)
                )
            )
        } catch (_: Exception) {
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveGuardScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val prefs = remember(context) { context.getSharedPreferences("thermal_prefs", Context.MODE_PRIVATE) }

    val guardMonitor = remember(context) { LiveGuardMonitor.getInstance(context) }
    val captureState by guardMonitor.captureState.collectAsStateWithLifecycle()
    val events by guardMonitor.events.collectAsStateWithLifecycle()
    val ignoredPackages by guardMonitor.ignoredPackages.collectAsStateWithLifecycle()
    val rulesConfig by guardMonitor.rulesConfig.collectAsStateWithLifecycle()
    val activeSnoozes by guardMonitor.activeSnoozes.collectAsStateWithLifecycle()
    val acknowledgedVectors by guardMonitor.acknowledgedVectors.collectAsStateWithLifecycle()

    var bgMonitoringEnabled by remember { mutableStateOf(prefs.getBoolean("background_monitoring", true)) }
    var liveGuardAlertsEnabled by remember { mutableStateOf(prefs.getBoolean("live_guard_alerts", true)) }
    var hasNotificationPermission by remember { mutableStateOf(hasPostNotificationsPermission(context)) }

    val notificationPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted
        if (!granted) {
            openAppNotificationSettings(context)
        }
    }

    var activeSubTab by remember { mutableStateOf("CHECKS") } // "CHECKS", "TIMELINE", "LOG"
    var auditVectors by remember { mutableStateOf(guardMonitor.peekCachedAuditVectors() ?: emptyList()) }
    var accessTimeline by remember { mutableStateOf(PrivacyAnalyzer.peekCachedTimeline(false) ?: emptyList()) }
    var activeTimelineFilter by remember { mutableStateOf("ALL") } // "ALL", "LOCATION", "MIC", "CAMERA", "CONTACTS_SMS", "STORAGE_MEDIA", "SPECIAL_ACCESS"
    var hasUsageAccess by remember { mutableStateOf(PrivacyAnalyzer.hasUsageStatsPermission(context)) }

    var isScanning by remember { mutableStateOf(false) }
    var scanProgress by remember { mutableFloatStateOf(0f) }
    var selectedVector by remember { mutableStateOf<SecurityAuditVector?>(null) }
    var refreshTrigger by remember { mutableIntStateOf(0) }
    val globalRefreshVersion by AppRefreshCoordinator.refreshVersion.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        guardMonitor.startMonitoring()
    }

    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
            when (key) {
                "background_monitoring" -> bgMonitoringEnabled = sp.getBoolean("background_monitoring", true)
                "live_guard_alerts" -> liveGuardAlertsEnabled = sp.getBoolean("live_guard_alerts", true)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasUsageAccess = PrivacyAnalyzer.hasUsageStatsPermission(context)
                hasNotificationPermission = hasPostNotificationsPermission(context)
                bgMonitoringEnabled = prefs.getBoolean("background_monitoring", true)
                liveGuardAlertsEnabled = prefs.getBoolean("live_guard_alerts", true)
                refreshTrigger++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val latestSensorEventId = remember(events) {
        events.firstOrNull { it.type != "AUDIT" }?.id ?: 0L
    }

    // Full 12-vector audit + timeline refresh only on lifecycle resume, app install/remove, or trust/ack changes
    LaunchedEffect(
        refreshTrigger,
        globalRefreshVersion,
        ignoredPackages,
        acknowledgedVectors
    ) {
        if (!isScanning) {
            try {
                val (vectors, timeline) = withContext(Dispatchers.IO) {
                    val v = guardMonitor.runDeepSecurityAudit(includeSystemApps = false, logAuditEvent = false)
                    val t = PrivacyAnalyzer.getPermissionAccessTimeline(
                        context,
                        includeSystemApps = false,
                        forceRefresh = true
                    )
                    v to t
                }
                auditVectors = vectors
                accessTimeline = timeline
                if (selectedVector != null) {
                    selectedVector = vectors.find { it.id == selectedVector?.id }
                }
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (_: Throwable) {
            }
        }
    }

    // Fast lightweight timeline refresh when live sensor state changes (avoids re-running all 12 APK audit vectors)
    LaunchedEffect(
        latestSensorEventId,
        captureState.isScreenRecordingOrCasting,
        captureState.isMicActive,
        captureState.isCameraActive,
        captureState.isBackgroundLocationActive,
        captureState.isClipboardAccessedRecently
    ) {
        if (!isScanning && auditVectors.isNotEmpty()) {
            try {
                val timeline = withContext(Dispatchers.IO) {
                    PrivacyAnalyzer.getPermissionAccessTimeline(
                        context,
                        includeSystemApps = false,
                        forceRefresh = true
                    )
                }
                accessTimeline = timeline
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (_: Throwable) {
            }
        }
    }

    val filteredTimeline = remember(accessTimeline, activeTimelineFilter) {
        when (activeTimelineFilter) {
            "LOCATION" -> accessTimeline.filter { it.category == PermissionCategory.LOCATION }
            "MIC" -> accessTimeline.filter { it.permissionInfo.permissionName == android.Manifest.permission.RECORD_AUDIO }
            "CAMERA" -> accessTimeline.filter { it.permissionInfo.permissionName == android.Manifest.permission.CAMERA }
            "CONTACTS_SMS" -> accessTimeline.filter { it.category == PermissionCategory.CONTACTS_SMS }
            "STORAGE_MEDIA" -> accessTimeline.filter { it.category == PermissionCategory.STORAGE_MEDIA }
            "SPECIAL_ACCESS" -> accessTimeline.filter { it.category == PermissionCategory.SPECIAL_ACCESS }
            else -> accessTimeline
        }
    }

    fun triggerAnimatedDeepScan() {
        if (isScanning) return
        coroutineScope.launch {
            isScanning = true
            scanProgress = 0.12f
            try {
                val (results, timeline) = coroutineScope {
                    val scanJob = async(Dispatchers.IO) {
                        val v = guardMonitor.runDeepSecurityAudit(includeSystemApps = false, logAuditEvent = true)
                        val t = PrivacyAnalyzer.getPermissionAccessTimeline(
                            context,
                            includeSystemApps = false,
                            forceRefresh = true
                        )
                        v to t
                    }
                    // Advance progress smoothly while the real scan executes concurrently
                    var current = 0.18f
                    while (!scanJob.isCompleted && current < 0.92f) {
                        scanProgress = current
                        delay(110)
                        current = (current + 0.14f).coerceAtMost(0.92f)
                    }
                    val output = scanJob.await()
                    scanProgress = 1f
                    output
                }
                auditVectors = results
                accessTimeline = timeline
                if (selectedVector != null) {
                    selectedVector = results.find { it.id == selectedVector?.id }
                }
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (_: Throwable) {
            } finally {
                isScanning = false
            }
        }
    }

    selectedVector?.let { vector ->
        ModalBottomSheet(
            onDismissRequest = { selectedVector = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = GlassPanel,
            contentColor = TextPrimary
        ) {
            AuditVectorDetailSheet(
                vector = vector,
                onOpenSettings = { actionStr ->
                    try {
                        context.startActivity(Intent(actionStr))
                    } catch (_: Throwable) {
                        try {
                            context.startActivity(Intent(Settings.ACTION_SETTINGS))
                        } catch (_: Throwable) {
                        }
                    }
                },
                onInspectApp = { pkg ->
                    try {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", pkg, null)
                            )
                        )
                    } catch (_: Throwable) {
                    }
                },
                onIgnoreApp = { pkg, appLabel ->
                    guardMonitor.ignorePackage(pkg)
                    Toast.makeText(context, "Trusted $appLabel in Live Guard", Toast.LENGTH_SHORT).show()
                },
                onToggleAcknowledge = {
                    if (vector.isAcknowledged) {
                        guardMonitor.unacknowledgeVector(vector.id)
                        Toast.makeText(context, "Restored active check for ${vector.title}", Toast.LENGTH_SHORT).show()
                    } else {
                        guardMonitor.acknowledgeVector(vector)
                        Toast.makeText(context, "Marked ${vector.title} as Expected (Safe)", Toast.LENGTH_SHORT).show()
                    }
                },
                onClose = { selectedVector = null }
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(14.dp))

        // Minimalist Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(end = 46.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val activeRuleCount = ignoredPackages.size + activeSnoozes.size
            val modeTint = if (activeSnoozes.isNotEmpty()) AuraOrange else AuraCyan
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "LIVE GUARD",
                    style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 2.5.sp, fontWeight = FontWeight.Bold),
                    color = if (captureState.isScreenRecordingOrCasting) AuraRose else AuraCyan
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(100.dp))
                        .background(modeTint.copy(alpha = 0.14f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (activeSnoozes.isNotEmpty()) "${activeSnoozes.size} Snoozed" else rulesConfig.alertMode.title,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                        color = modeTint,
                        maxLines = 1
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .height(36.dp)
                        .testTag("guard_rules_button")
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (activeRuleCount > 0) modeTint.copy(alpha = 0.14f) else GlassPanel.copy(alpha = 0.7f))
                        .border(1.dp, if (activeRuleCount > 0) modeTint.copy(alpha = 0.4f) else GlassBorder, RoundedCornerShape(12.dp))
                        .clickable {
                            try {
                                context.startActivity(Intent(context, GuardRulesActivity::class.java))
                            } catch (_: Throwable) {
                            }
                        }
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Tune,
                            contentDescription = "Guard Rules",
                            tint = if (activeRuleCount > 0) modeTint else TextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (activeRuleCount > 0) "Rules ($activeRuleCount)" else "Rules",
                            color = if (activeRuleCount > 0) modeTint else TextSecondary,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("guard_test_alert_button")
                        .clip(RoundedCornerShape(12.dp))
                        .background(AuraPurple.copy(alpha = 0.14f))
                        .border(1.dp, AuraPurple.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .clickable {
                            guardMonitor.triggerSimulatedGuardTest()
                            Toast.makeText(
                                context,
                                "Sample Guard alert triggered — check Snooze & Trust actions",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.NotificationsActive,
                        contentDescription = "Test Alert",
                        tint = AuraPurple,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        if (!bgMonitoringEnabled || !liveGuardAlertsEnabled || !hasNotificationPermission) {
            Spacer(modifier = Modifier.height(10.dp))
            val (warnTitle, warnSubtitle, btnLabel, warnColor, onFix) = when {
                !bgMonitoringEnabled -> GuardWarningBannerData(
                    title = "Background Guard Paused",
                    subtitle = "Alerts & screen-off monitoring are paused in Settings.",
                    actionLabel = "Turn On",
                    tint = AuraRose,
                    onAction = {
                        prefs.edit().putBoolean("background_monitoring", true).apply()
                        startTrackingServiceSafely(context)
                    }
                )
                !liveGuardAlertsEnabled -> GuardWarningBannerData(
                    title = "Alert Notifications Muted",
                    subtitle = "Live Mic, Cam & Screen alerts are muted in System Preferences.",
                    actionLabel = "Unmute",
                    tint = AuraOrange,
                    onAction = {
                        prefs.edit().putBoolean("live_guard_alerts", true).apply()
                    }
                )
                else -> GuardWarningBannerData(
                    title = "Notifications Blocked by Android",
                    subtitle = "Allow notification access so Guard alerts can reach you.",
                    actionLabel = "Allow",
                    tint = AuraRose,
                    onAction = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            try {
                                notificationPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } catch (_: Throwable) {
                                openAppNotificationSettings(context)
                            }
                        } else {
                            openAppNotificationSettings(context)
                        }
                    }
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("guard_status_warning_banner")
                    .clip(RoundedCornerShape(16.dp))
                    .background(warnColor.copy(alpha = 0.12f))
                    .border(1.dp, warnColor.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = warnColor, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(warnTitle, color = warnColor, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                    Text(warnSubtitle, color = TextSecondary, style = MaterialTheme.typography.labelSmall)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .heightIn(min = 36.dp)
                        .clip(RoundedCornerShape(100.dp))
                        .background(warnColor)
                        .clickable { onFix() }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(btnLabel, color = Obsidian, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Compact Live Sensor Hero Card
        CompactHardwareShieldCard(
            captureState = captureState,
            onScanClick = { triggerAnimatedDeepScan() },
            isScanning = isScanning,
            scanProgress = scanProgress,
            onScreenClick = {
                auditVectors.firstOrNull { it.id == "screen_recording" }?.let {
                    selectedVector = it
                } ?: run {
                    try {
                        context.startActivity(Intent(context, GuardRulesActivity::class.java))
                    } catch (_: Throwable) {
                    }
                }
            },
            onMicClick = {
                activeSubTab = "TIMELINE"
                activeTimelineFilter = "MIC"
            },
            onCameraClick = {
                activeSubTab = "TIMELINE"
                activeTimelineFilter = "CAMERA"
            },
            onLocationClick = {
                if (!captureState.isAppOpsIntrospectionAvailable) {
                    auditVectors.firstOrNull { it.id == "bg_location" }?.let {
                        selectedVector = it
                    } ?: run {
                        activeSubTab = "TIMELINE"
                        activeTimelineFilter = "LOCATION"
                    }
                } else {
                    activeSubTab = "TIMELINE"
                    activeTimelineFilter = "LOCATION"
                }
            },
            onClipboardClick = {
                if (!captureState.isAppOpsIntrospectionAvailable) {
                    auditVectors.firstOrNull { it.id == "clipboard_access" }?.let {
                        selectedVector = it
                    } ?: run {
                        activeSubTab = "TIMELINE"
                        activeTimelineFilter = "SPECIAL_ACCESS"
                    }
                } else {
                    activeSubTab = "TIMELINE"
                    activeTimelineFilter = "SPECIAL_ACCESS"
                }
            }
        )

        Spacer(modifier = Modifier.height(10.dp))

        // 2-Way Segmented Switcher: SECURITY CHECKS (Posture) | ACTIVITY TIMELINE (Verified History)
        val flaggedCount = auditVectors.count { it.severity != AuditSeverity.SAFE }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(100.dp))
                .background(GlassPanel.copy(alpha = 0.8f))
                .border(1.dp, GlassBorder, RoundedCornerShape(100.dp))
                .padding(3.dp)
        ) {
            val tabs = listOf(
                Triple("CHECKS", if (flaggedCount == 0) "SECURITY CHECKS (SAFE)" else "SECURITY CHECKS ($flaggedCount)", AuraCyan),
                Triple("TIMELINE", "ACTIVITY TIMELINE (${accessTimeline.size})", AuraOrange)
            )
            tabs.forEach { (key, label, tint) ->
                val selected = activeSubTab == key
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .testTag("guard_subtab_${key.lowercase()}")
                        .clip(RoundedCornerShape(100.dp))
                        .background(if (selected) tint.copy(alpha = 0.18f) else Color.Transparent)
                        .clickable { activeSubTab = key }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = if (selected) tint else TextSecondary,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.6.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        when (activeSubTab) {
            "CHECKS" -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 112.dp)
                ) {
                    items(auditVectors, key = { it.id }) { vector ->
                        MinimalAuditRow(
                            vector = vector,
                            onClick = { selectedVector = vector }
                        )
                    }
                }
            }

            "TIMELINE" -> {
                val locCount = accessTimeline.count { it.category == PermissionCategory.LOCATION }
                val micCount = accessTimeline.count { it.permissionInfo.permissionName == android.Manifest.permission.RECORD_AUDIO }
                val camCount = accessTimeline.count { it.permissionInfo.permissionName == android.Manifest.permission.CAMERA }
                val msgCount = accessTimeline.count { it.category == PermissionCategory.CONTACTS_SMS }
                val fileCount = accessTimeline.count { it.category == PermissionCategory.STORAGE_MEDIA }
                val specCount = accessTimeline.count { it.category == PermissionCategory.SPECIAL_ACCESS }

                // Filter Strip + OS Log Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        GuardFilterPill("All (${accessTimeline.size})", activeTimelineFilter == "ALL", AuraOrange) {
                            activeTimelineFilter = "ALL"
                        }
                        GuardFilterPill("Location ($locCount)", activeTimelineFilter == "LOCATION", AuraCyan) {
                            activeTimelineFilter = "LOCATION"
                        }
                        GuardFilterPill("Mic ($micCount)", activeTimelineFilter == "MIC", AuraRose) {
                            activeTimelineFilter = "MIC"
                        }
                        GuardFilterPill("Camera ($camCount)", activeTimelineFilter == "CAMERA", AuraOrange) {
                            activeTimelineFilter = "CAMERA"
                        }
                        GuardFilterPill("SMS/Contacts ($msgCount)", activeTimelineFilter == "CONTACTS_SMS", AuraPurple) {
                            activeTimelineFilter = "CONTACTS_SMS"
                        }
                        GuardFilterPill("Files ($fileCount)", activeTimelineFilter == "STORAGE_MEDIA", AuraGreen) {
                            activeTimelineFilter = "STORAGE_MEDIA"
                        }
                        GuardFilterPill("Special / Screen ($specCount)", activeTimelineFilter == "SPECIAL_ACCESS", AuraRose) {
                            activeTimelineFilter = "SPECIAL_ACCESS"
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))
                    if (events.size > 1) {
                        Box(
                            modifier = Modifier
                                .heightIn(min = 36.dp)
                                .clip(RoundedCornerShape(100.dp))
                                .background(GlassPanel.copy(alpha = 0.7f))
                                .border(1.dp, GlassBorder, RoundedCornerShape(100.dp))
                                .clickable {
                                    guardMonitor.clearEvents()
                                    Toast.makeText(context, "Timeline history cleared", Toast.LENGTH_SHORT).show()
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.DeleteSweep,
                                contentDescription = "Clear History",
                                tint = TextSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Box(
                        modifier = Modifier
                            .heightIn(min = 36.dp)
                            .testTag("timeline_system_log_button")
                            .clip(RoundedCornerShape(100.dp))
                            .background(AuraOrange.copy(alpha = 0.14f))
                            .border(1.dp, AuraOrange.copy(alpha = 0.35f), RoundedCornerShape(100.dp))
                            .clickable {
                                if (!PrivacyAnalyzer.openOsPrivacyDashboard(context)) {
                                    Toast.makeText(context, "Privacy settings unavailable on this OS", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "OS Log",
                            color = AuraOrange,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                val groupedTimeline = remember(filteredTimeline) {
                    val cal = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    val startOfToday = cal.timeInMillis
                    val startOfYesterday = startOfToday - 86_400_000L

                    filteredTimeline.groupBy { ev ->
                        when {
                            ev.timestamp >= startOfToday -> "TODAY"
                            ev.timestamp >= startOfYesterday -> "YESTERDAY"
                            else -> "EARLIER"
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 112.dp)
                ) {
                    // 1. Usage Access Prompt if not yet granted
                    if (!hasUsageAccess) {
                        item(key = "usage_access_banner") {
                            GlassCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        try {
                                            context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                                        } catch (_: Throwable) {
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Enable Exact Activity Timestamps",
                                            color = AuraOrange,
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                                        )
                                        Text(
                                            text = "Grant Usage Access for exact app permission timestamps.",
                                            color = TextSecondary,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(AuraOrange)
                                            .padding(horizontal = 10.dp, vertical = 5.dp)
                                    ) {
                                        Text(
                                            text = "Grant",
                                            color = Obsidian,
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                    }

                    // 2. Connected Vertical Spine Timeline Nodes Grouped by Day
                    if (filteredTimeline.isEmpty()) {
                        item(key = "empty_timeline") {
                            GlassCard(modifier = Modifier.fillMaxWidth()) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = if (activeTimelineFilter == "ALL") {
                                            "No verified permission accesses yet"
                                        } else {
                                            "No verified accesses for this filter"
                                        },
                                        color = TextPrimary,
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = "dot only logs verified hardware & AppOps events—never guessed when you open or install an app. Android restricts third-party apps from reading historical AppOps logs; check OS Log for Android's built-in Privacy Dashboard.",
                                        color = TextSecondary,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(AuraOrange.copy(alpha = 0.16f))
                                            .border(1.dp, AuraOrange.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                            .clickable {
                                                if (!PrivacyAnalyzer.openOsPrivacyDashboard(context)) {
                                                    Toast.makeText(context, "Privacy settings unavailable on this OS", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = "Open OS Privacy Dashboard",
                                            color = AuraOrange,
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        listOf("TODAY", "YESTERDAY", "EARLIER").forEach { bucketLabel ->
                            val bucketEvents = groupedTimeline[bucketLabel].orEmpty()
                            if (bucketEvents.isNotEmpty()) {
                                item(key = "bucket_header_$bucketLabel") {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp, bottom = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = bucketLabel,
                                            color = TextTertiary,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 10.sp,
                                                letterSpacing = 1.6.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                        Text(
                                            text = "${bucketEvents.size} events",
                                            color = TextTertiary,
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)
                                        )
                                    }
                                }
                                itemsIndexed(
                                    items = bucketEvents,
                                    key = { index, item -> "${bucketLabel}_${item.id}_$index" }
                                ) { index, item ->
                                    TimelineConnectedSpineRow(
                                        event = item,
                                        isFirst = index == 0,
                                        isLast = index == bucketEvents.lastIndex,
                                        onClick = {
                                            if (item.packageName == "system.sensor") {
                                                try {
                                                    context.startActivity(Intent(Settings.ACTION_PRIVACY_SETTINGS))
                                                } catch (_: Throwable) {
                                                }
                                            } else {
                                                PrivacyAnalyzer.openSpecificPermissionSettings(
                                                    context,
                                                    item.packageName,
                                                    item.permissionInfo
                                                )
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GuardFilterPill(
    label: String,
    selected: Boolean,
    tint: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .background(if (selected) tint.copy(alpha = 0.18f) else GlassPanel.copy(alpha = 0.6f))
            .border(1.dp, if (selected) tint else GlassBorder, RoundedCornerShape(100.dp))
            .clickable { onClick() }
            .padding(horizontal = 11.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            color = if (selected) tint else TextSecondary,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold)
        )
    }
}

private fun permissionVisuals(perm: PlainPermissionInfo, cat: PermissionCategory): Pair<Color, ImageVector> {
    return when (perm.permissionName) {
        android.Manifest.permission.RECORD_AUDIO -> Color(0xFFFF3B5C) to Icons.Default.Mic
        android.Manifest.permission.CAMERA -> Color(0xFFFF8A00) to Icons.Default.CameraAlt
        android.Manifest.permission.ACCESS_BACKGROUND_LOCATION -> Color(0xFF0091EA) to Icons.Default.MyLocation
        android.Manifest.permission.ACCESS_FINE_LOCATION -> Color(0xFF00C4D4) to Icons.Default.LocationOn
        android.Manifest.permission.ACCESS_COARSE_LOCATION -> Color(0xFF26A69A) to Icons.Default.Explore
        android.Manifest.permission.READ_SMS -> Color(0xFF8B5CF6) to Icons.Default.Sms
        android.Manifest.permission.RECEIVE_SMS -> Color(0xFFD946EF) to Icons.Default.MarkunreadMailbox
        android.Manifest.permission.SEND_SMS -> Color(0xFF6366F1) to Icons.AutoMirrored.Filled.Send
        android.Manifest.permission.READ_CALL_LOG -> Color(0xFFEC4899) to Icons.Default.Call
        android.Manifest.permission.READ_CONTACTS -> Color(0xFFA855F7) to Icons.Default.Contacts
        android.Manifest.permission.READ_PHONE_STATE -> Color(0xFF3B82F6) to Icons.Default.PhoneAndroid
        android.Manifest.permission.MANAGE_EXTERNAL_STORAGE -> Color(0xFF10B981) to Icons.Default.FolderShared
        android.Manifest.permission.READ_MEDIA_IMAGES -> Color(0xFF22C55E) to Icons.Default.Image
        android.Manifest.permission.READ_MEDIA_VIDEO -> Color(0xFF84CC16) to Icons.Default.Videocam
        android.Manifest.permission.READ_EXTERNAL_STORAGE -> Color(0xFF14B8A6) to Icons.Default.SdStorage
        android.Manifest.permission.WRITE_EXTERNAL_STORAGE -> Color(0xFF059669) to Icons.Default.Save
        android.Manifest.permission.SYSTEM_ALERT_WINDOW -> Color(0xFFE11D48) to Icons.Default.Layers
        "SPECIAL_ACCESSIBILITY_SERVICE" -> Color(0xFFDC2626) to Icons.Default.AccessibilityNew
        "SPECIAL_NOTIFICATION_LISTENER" -> Color(0xFFF59E0B) to Icons.Default.NotificationsActive
        "SPECIAL_DEVICE_ADMIN" -> Color(0xFFB91C1C) to Icons.Default.AdminPanelSettings
        android.Manifest.permission.REQUEST_INSTALL_PACKAGES -> Color(0xFFF97316) to Icons.Default.SystemUpdate
        android.Manifest.permission.PACKAGE_USAGE_STATS -> Color(0xFF0EA5E9) to Icons.Default.QueryStats
        "SPECIAL_CLIPBOARD_READ" -> Color(0xFFA855F7) to Icons.Default.ContentPaste
        "SPECIAL_SCREEN_CAPTURE" -> Color(0xFFFF3B5C) to Icons.AutoMirrored.Filled.ScreenShare
        "SPECIAL_SCREEN_MIRROR" -> Color(0xFFFF6D00) to Icons.AutoMirrored.Filled.ScreenShare
        "SPECIAL_CUSTOM_KEYBOARD" -> Color(0xFFF59E0B) to Icons.Default.Tune
        "SPECIAL_USB_DEBUGGING" -> Color(0xFFEC4899) to Icons.Default.Security
        "SPECIAL_SIDELOADED_APP" -> Color(0xFFF97316) to Icons.Default.SystemUpdate
        "SPECIAL_STEALTH_STALKERWARE" -> Color(0xFFFF3B5C) to Icons.Default.Warning
        "SPECIAL_APK_CERT_TROJAN" -> Color(0xFFA855F7) to Icons.Default.Shield
        else -> when (cat) {
            PermissionCategory.LOCATION -> AuraCyan to Icons.Default.LocationOn
            PermissionCategory.CAMERA_MIC -> AuraOrange to Icons.Default.Mic
            PermissionCategory.CONTACTS_SMS -> AuraPurple to Icons.Default.Sms
            PermissionCategory.STORAGE_MEDIA -> AuraGreen to Icons.Default.Folder
            PermissionCategory.SPECIAL_ACCESS -> AuraRose to Icons.Default.Shield
            PermissionCategory.ALL -> AuraCyan to Icons.Default.AccessTime
        }
    }
}

@Composable
private fun TimelineConnectedSpineRow(
    event: PermissionAccessEvent,
    isFirst: Boolean,
    isLast: Boolean,
    onClick: () -> Unit
) {
    val (tint, catIcon) = remember(event.permissionInfo.permissionName, event.category) {
        permissionVisuals(event.permissionInfo, event.category)
    }

    val isLiveNow = event.id.startsWith("live_now_")
    val clockLabel = remember(event.timestamp, isLiveNow) {
        if (isLiveNow) "ACTIVE" else SimpleDateFormat("hh:mm a", Locale.US).format(Date(event.timestamp))
    }
    val dateOrRelLabel = remember(event.timestamp, isLiveNow) {
        if (isLiveNow) "LIVE NOW" else formatRelativeTimelineTime(event.timestamp)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Left Time Column
        Column(
            modifier = Modifier
                .width(58.dp)
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = dateOrRelLabel,
                color = if (isLiveNow) AuraRose else tint,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                textAlign = TextAlign.End,
                maxLines = 1
            )
            Text(
                text = clockLabel,
                color = if (isLiveNow) AuraOrange else TextTertiary,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = if (isLiveNow) FontWeight.Bold else FontWeight.Normal),
                textAlign = TextAlign.End,
                maxLines = 1
            )
        }

        // 2. Center Connected Vertical Line Spine + Glowing Node
        Box(
            modifier = Modifier
                .width(26.dp)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                if (size.width <= 1f || size.height <= 1f) return@Canvas
                val cx = size.width / 2f
                val cy = size.height / 2f
                val lineColor = tint.copy(alpha = 0.35f)

                if (!isFirst) {
                    drawLine(
                        color = lineColor,
                        start = Offset(cx, 0f),
                        end = Offset(cx, cy),
                        strokeWidth = 2.dp.toPx()
                    )
                }
                if (!isLast) {
                    drawLine(
                        color = lineColor,
                        start = Offset(cx, cy),
                        end = Offset(cx, size.height),
                        strokeWidth = 2.dp.toPx()
                    )
                }
                // Horizontal connector tick to card
                drawLine(
                    color = lineColor,
                    start = Offset(cx, cy),
                    end = Offset(size.width, cy),
                    strokeWidth = 1.5.dp.toPx()
                )
                // Glowing node
                drawCircle(
                    color = tint.copy(alpha = 0.24f),
                    radius = 7.dp.toPx(),
                    center = Offset(cx, cy)
                )
                drawCircle(
                    color = tint,
                    radius = 3.8.dp.toPx(),
                    center = Offset(cx, cy)
                )
            }
        }

        // 3. Right Compact Event Card
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 4.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (isLiveNow) tint.copy(alpha = 0.14f) else GlassPanel.copy(alpha = 0.65f))
                .border(1.dp, if (isLiveNow) tint.copy(alpha = 0.65f) else GlassBorder, RoundedCornerShape(14.dp))
                .clickable { onClick() }
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val bmp = remember(event.packageName) { getCachedAppIconBitmap(event.packageName, event.icon) }
                Image(
                    bitmap = bmp,
                    contentDescription = event.appName,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                )

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = event.appName,
                        color = TextPrimary,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${event.permissionTitle} • ${event.accessContext}",
                        color = TextSecondary,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(tint.copy(alpha = 0.14f))
                        .border(1.dp, tint.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Icon(catIcon, contentDescription = null, tint = tint, modifier = Modifier.size(11.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = event.shortTag,
                        color = tint,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}

private fun formatRelativeTimelineTime(timestamp: Long): String {
    if (timestamp <= 0L) return "Recent"
    val diff = (System.currentTimeMillis() - timestamp).coerceAtLeast(0L)
    val mins = diff / 60_000L
    val hours = mins / 60L
    val days = hours / 24L
    return when {
        mins < 1L -> "Just now"
        mins < 60L -> "${mins}m ago"
        hours < 24L -> "${hours}h ago"
        days < 7L -> "${days}d ago"
        else -> SimpleDateFormat("MMM d", Locale.US).format(Date(timestamp))
    }
}

private data class GuardWarningBannerData(
    val title: String,
    val subtitle: String,
    val actionLabel: String,
    val tint: Color,
    val onAction: () -> Unit
)

@Composable
private fun CompactHardwareShieldCard(
    captureState: LiveCaptureState,
    onScanClick: () -> Unit,
    isScanning: Boolean,
    scanProgress: Float,
    onScreenClick: () -> Unit,
    onMicClick: () -> Unit,
    onCameraClick: () -> Unit,
    onLocationClick: () -> Unit,
    onClipboardClick: () -> Unit
) {
    val anyActiveCapture = captureState.isScreenRecordingOrCasting ||
        captureState.isMicActive ||
        captureState.isCameraActive ||
        captureState.isBackgroundLocationActive ||
        captureState.isClipboardAccessedRecently
    val heroAccent = when {
        captureState.isScreenRecordingOrCasting -> AuraRose
        captureState.isMicActive || captureState.isCameraActive || captureState.isBackgroundLocationActive -> AuraOrange
        captureState.isClipboardAccessedRecently -> AuraPurple
        else -> AuraGreen
    }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (anyActiveCapture) {
                PulsingShieldAvatar(heroAccent = heroAccent)
            } else {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(heroAccent.copy(alpha = 0.16f))
                        .border(1.5.dp, heroAccent.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = heroAccent,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                val activeSensorSummary = buildList {
                    if (captureState.isMicActive) {
                        val micNames = captureState.activeMicApps.takeIf { it.isNotEmpty() }?.joinToString(", ")
                            ?: "${captureState.activeMicSessions.coerceAtLeast(1)} session"
                        add("Mic: $micNames")
                    }
                    if (captureState.isCameraActive) {
                        val camNames = captureState.activeCameraApps.takeIf { it.isNotEmpty() }?.joinToString(", ")
                            ?: "${captureState.busyCameraIds.size.coerceAtLeast(1)} active"
                        add("Camera: $camNames")
                    }
                    if (captureState.isBackgroundLocationActive) {
                        val locNames = captureState.activeLocationApps.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: "Active"
                        add("GPS: $locNames")
                    }
                    if (captureState.isClipboardAccessedRecently) {
                        val clipNames = captureState.recentClipboardApps.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: "Read"
                        add("Clipboard: $clipNames")
                    }
                }.joinToString(" • ")

                Text(
                    text = when {
                        captureState.isScreenRecordingOrCasting -> "SCREEN CAPTURE DETECTED"
                        captureState.isMicActive && captureState.isCameraActive -> "MIC & CAMERA IN USE"
                        captureState.isMicActive -> "MICROPHONE IN USE"
                        captureState.isCameraActive -> "CAMERA IN USE"
                        captureState.isBackgroundLocationActive -> "BACKGROUND GPS ACTIVE"
                        captureState.isClipboardAccessedRecently -> "CLIPBOARD READ DETECTED"
                        else -> "SHIELD ACTIVE"
                    },
                    color = heroAccent,
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold)
                )
                Text(
                    text = when {
                        captureState.isScreenRecordingOrCasting ->
                            captureState.activeCaptureDisplays.firstOrNull() ?: "Display is being mirrored or recorded"
                        activeSensorSummary.isNotEmpty() -> activeSensorSummary
                        captureState.isAppOpsIntrospectionAvailable ->
                            "Screen, Mic, Cam, GPS & Clipboard live monitors ready"
                        else ->
                            "Live Screen, Mic & Cam active • GPS & Clipboard via OS Posture"
                    },
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Button(
                onClick = onScanClick,
                enabled = !isScanning,
                modifier = Modifier.height(36.dp).testTag("run_deep_threat_scan_button"),
                colors = ButtonDefaults.buttonColors(containerColor = AuraCyan),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                shape = RoundedCornerShape(100.dp)
            ) {
                Icon(Icons.Default.Radar, contentDescription = null, tint = Obsidian, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isScanning) "..." else "Scan",
                    color = Obsidian,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Single Compact Row of 5 Hardware Sensor Pills
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            LiveSensorChip(
                modifier = Modifier.weight(1f),
                icon = Icons.AutoMirrored.Filled.ScreenShare,
                label = "Screen",
                status = if (captureState.isScreenRecordingOrCasting) "Rec" else "Live",
                isActive = captureState.isScreenRecordingOrCasting,
                activeColor = AuraRose,
                onClick = onScreenClick
            )
            LiveSensorChip(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Mic,
                label = "Mic",
                status = if (captureState.isMicActive) "Live" else "Idle",
                isActive = captureState.isMicActive,
                activeColor = AuraOrange,
                onClick = onMicClick
            )
            LiveSensorChip(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.CameraAlt,
                label = "Cam",
                status = if (captureState.isCameraActive) "Live" else "Idle",
                isActive = captureState.isCameraActive,
                activeColor = AuraOrange,
                onClick = onCameraClick
            )
            LiveSensorChip(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.MyLocation,
                label = "GPS",
                status = when {
                    captureState.isBackgroundLocationActive -> "Active"
                    captureState.isAppOpsIntrospectionAvailable -> "Idle"
                    else -> "Audit"
                },
                isActive = captureState.isBackgroundLocationActive,
                activeColor = AuraOrange,
                idleColor = if (captureState.isAppOpsIntrospectionAvailable) AuraGreen else AuraCyan,
                onClick = onLocationClick
            )
            LiveSensorChip(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.ContentPaste,
                label = "Clip",
                status = when {
                    captureState.isClipboardAccessedRecently -> "Read"
                    captureState.isAppOpsIntrospectionAvailable -> "Safe"
                    else -> "OS Safe"
                },
                isActive = captureState.isClipboardAccessedRecently,
                activeColor = AuraPurple,
                idleColor = if (captureState.isAppOpsIntrospectionAvailable) AuraGreen else AuraCyan,
                onClick = onClipboardClick
            )
        }

        if (isScanning) {
            Spacer(modifier = Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { scanProgress },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                color = AuraCyan,
                trackColor = GlassBorder.copy(alpha = 0.45f),
                strokeCap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun PulsingShieldAvatar(heroAccent: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "guard_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )
    Box(
        modifier = Modifier
            .size(38.dp)
            .graphicsLayer {
                scaleX = pulseScale
                scaleY = pulseScale
            }
            .clip(CircleShape)
            .background(heroAccent.copy(alpha = 0.16f))
            .border(1.5.dp, heroAccent.copy(alpha = 0.5f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = heroAccent,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun LiveSensorChip(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    status: String,
    isActive: Boolean,
    activeColor: Color,
    idleColor: Color = AuraGreen,
    onClick: () -> Unit
) {
    val tint = if (isActive) activeColor else idleColor
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isActive) tint.copy(alpha = 0.16f) else Obsidian.copy(alpha = 0.4f))
            .border(1.dp, if (isActive) tint.copy(alpha = 0.5f) else GlassBorder, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 7.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(15.dp))
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            color = TextPrimary,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
            maxLines = 1
        )
        Text(
            text = status,
            color = if (isActive) tint else TextTertiary,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
            maxLines = 1
        )
    }
}

@Composable
private fun MinimalAuditRow(
    vector: SecurityAuditVector,
    onClick: () -> Unit
) {
    val sevColor = when (vector.severity) {
        AuditSeverity.DANGER -> AuraRose
        AuditSeverity.WARNING -> AuraOrange
        AuditSeverity.SAFE -> AuraGreen
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .background(GlassPanel.copy(alpha = 0.58f))
            .border(
                1.dp,
                if (vector.severity != AuditSeverity.SAFE) sevColor.copy(alpha = 0.4f) else GlassBorder,
                RoundedCornerShape(13.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (vector.severity == AuditSeverity.SAFE) Icons.Default.CheckCircle else Icons.Default.Warning,
            contentDescription = null,
            tint = sevColor,
            modifier = Modifier.size(19.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = vector.title,
                color = TextPrimary,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = vector.plainSummary,
                color = TextSecondary,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(sevColor.copy(alpha = 0.14f))
                .padding(horizontal = 7.dp, vertical = 3.dp)
        ) {
            Text(
                text = vector.statusBadge,
                color = sevColor,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp)
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun AuditVectorDetailSheet(
    vector: SecurityAuditVector,
    onOpenSettings: (String) -> Unit,
    onInspectApp: (String) -> Unit,
    onIgnoreApp: (packageName: String, appLabel: String) -> Unit,
    onToggleAcknowledge: () -> Unit,
    onClose: () -> Unit
) {
    val sevColor = when (vector.severity) {
        AuditSeverity.DANGER -> AuraRose
        AuditSeverity.WARNING -> AuraOrange
        AuditSeverity.SAFE -> AuraGreen
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = vector.title,
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = vector.plainSummary,
                    color = sevColor,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                )
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(GlassBorder.copy(alpha = 0.22f))
                .padding(12.dp)
        ) {
            Text(
                text = vector.detailedAdvice,
                color = TextPrimary,
                style = MaterialTheme.typography.bodySmall
            )
        }

        if (vector.extraDetails.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            vector.extraDetails.forEach { detail ->
                Text(
                    text = "• $detail",
                    color = sevColor,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }

        val canAcknowledge = vector.id != "screen_recording" &&
            vector.id != "live_mic_cam" &&
            (vector.severity != AuditSeverity.SAFE || vector.isAcknowledged) &&
            vector.currentSignature.isNotBlank()

        if (vector.settingsAction != null || canAcknowledge) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (vector.settingsAction != null) {
                    Button(
                        onClick = { onOpenSettings(vector.settingsAction) },
                        colors = ButtonDefaults.buttonColors(containerColor = AuraCyan),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = vector.actionButtonLabel.uppercase(),
                            color = Obsidian,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = Obsidian, modifier = Modifier.size(14.dp))
                    }
                }
                if (canAcknowledge) {
                    Button(
                        onClick = onToggleAcknowledge,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (vector.isAcknowledged) GlassBorder else AuraGreen.copy(alpha = 0.18f)
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .border(
                                1.dp,
                                if (vector.isAcknowledged) GlassBorder else AuraGreen.copy(alpha = 0.55f),
                                RoundedCornerShape(14.dp)
                            )
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = if (vector.isAcknowledged) TextSecondary else AuraGreen,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (vector.isAcknowledged) "UNMARK SAFE" else "MARK AS SAFE",
                            color = if (vector.isAcknowledged) TextPrimary else AuraGreen,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        if (vector.flaggedApps.isNotEmpty()) {
            var appSearch by remember(vector.id) { mutableStateOf("") }
            val shownApps = remember(vector.flaggedApps, appSearch) {
                if (appSearch.isBlank()) {
                    vector.flaggedApps
                } else {
                    val q = appSearch.trim()
                    vector.flaggedApps.filter {
                        it.appName.contains(q, ignoreCase = true) ||
                            it.packageName.contains(q, ignoreCase = true) ||
                            it.installerName.contains(q, ignoreCase = true)
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "ALL FLAGGED APPS (${vector.flaggedApps.size})",
                color = TextTertiary,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            )
            if (vector.flaggedApps.size > 6) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = appSearch,
                    onValueChange = { appSearch = it },
                    placeholder = { Text("Filter apps...", color = TextTertiary, style = MaterialTheme.typography.labelSmall) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp)) },
                    trailingIcon = {
                        if (appSearch.isNotEmpty()) {
                            IconButton(onClick = { appSearch = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextSecondary, modifier = Modifier.size(14.dp))
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AuraCyan,
                        unfocusedBorderColor = GlassBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            shownApps.forEach { app ->
                val bmp = remember(app.packageName) { getCachedAppIconBitmap(app.packageName, app.icon) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onInspectApp(app.packageName) }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        bitmap = bmp,
                        contentDescription = app.appName,
                        modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp))
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = app.appName,
                            color = TextPrimary,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = app.installerName,
                            color = TextTertiary,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    Box(
                        modifier = Modifier
                            .heightIn(min = 36.dp)
                            .clip(RoundedCornerShape(100.dp))
                            .background(GlassBorder.copy(alpha = 0.4f))
                            .clickable { onIgnoreApp(app.packageName, app.appName) }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Ignore",
                            color = TextSecondary,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Revoke",
                        color = AuraCyan,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = AuraCyan, modifier = Modifier.size(13.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
private fun GuardEventRow(
    event: GuardEvent,
    isIgnored: Boolean,
    onToggleIgnore: (String) -> Unit
) {
    val color = when (event.severity) {
        AuditSeverity.DANGER -> AuraRose
        AuditSeverity.WARNING -> AuraOrange
        AuditSeverity.SAFE -> AuraCyan
    }
    val timeStr = remember(event.timestamp) {
        SimpleDateFormat("hh:mm a", Locale.US).format(Date(event.timestamp))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(GlassPanel.copy(alpha = 0.5f))
            .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = event.title,
                    color = TextPrimary,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = timeStr,
                    color = TextTertiary,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = event.description,
                    color = TextSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (event.packageName != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .heightIn(min = 36.dp)
                            .clip(RoundedCornerShape(100.dp))
                            .background(if (isIgnored) AuraCyan.copy(alpha = 0.14f) else GlassBorder.copy(alpha = 0.4f))
                            .clickable { onToggleIgnore(event.packageName) }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isIgnored) "Restore" else "Ignore",
                            color = if (isIgnored) AuraCyan else TextSecondary,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }
    }
}
