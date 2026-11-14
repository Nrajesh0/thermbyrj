package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
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
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.SystemUpdate
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.GlassCard
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveGuardScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    val guardMonitor = remember(context) { LiveGuardMonitor.getInstance(context) }
    val captureState by guardMonitor.captureState.collectAsStateWithLifecycle()
    val events by guardMonitor.events.collectAsStateWithLifecycle()

    var activeSubTab by remember { mutableStateOf("CHECKS") } // "CHECKS", "TIMELINE", "LOG"
    var auditVectors by remember { mutableStateOf(guardMonitor.peekCachedAuditVectors() ?: emptyList()) }
    var accessTimeline by remember { mutableStateOf(PrivacyAnalyzer.peekCachedTimeline(false) ?: emptyList()) }
    var activeTimelineFilter by remember { mutableStateOf("ALL") } // "ALL", "LOCATION", "MIC", "CAMERA", "CONTACTS_SMS", "STORAGE_MEDIA", "SPECIAL_ACCESS"
    var hasUsageAccess by remember { mutableStateOf(PrivacyAnalyzer.hasUsageStatsPermission(context)) }

    var isScanning by remember { mutableStateOf(false) }
    var scanProgress by remember { mutableFloatStateOf(0f) }
    var selectedVector by remember { mutableStateOf<SecurityAuditVector?>(null) }
    var refreshTrigger by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        guardMonitor.startMonitoring()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                guardMonitor.refreshAllStates()
                hasUsageAccess = PrivacyAnalyzer.hasUsageStatsPermission(context)
                refreshTrigger++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(
        refreshTrigger,
        captureState.isScreenRecordingOrCasting,
        captureState.isMicActive,
        captureState.isCameraActive
    ) {
        if (!isScanning) {
            withContext(Dispatchers.IO) {
                val vectors = guardMonitor.runDeepSecurityAudit(includeSystemApps = false)
                val timeline = PrivacyAnalyzer.getPermissionAccessTimeline(context, includeSystemApps = false)
                auditVectors = vectors
                accessTimeline = timeline
                if (selectedVector != null) {
                    selectedVector = vectors.find { it.id == selectedVector?.id }
                }
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
            for (step in 1..4) {
                scanProgress = step * 0.25f
                delay(180)
            }
            val (results, timeline) = withContext(Dispatchers.IO) {
                val v = guardMonitor.runDeepSecurityAudit(includeSystemApps = false)
                val t = PrivacyAnalyzer.getPermissionAccessTimeline(context, includeSystemApps = false)
                v to t
            }
            auditVectors = results
            accessTimeline = timeline
            isScanning = false
        }
    }

    if (selectedVector != null) {
        val vector = selectedVector!!
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
                    } catch (_: Exception) {
                        try {
                            context.startActivity(Intent(Settings.ACTION_SETTINGS))
                        } catch (_: Exception) {
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
                    } catch (_: Exception) {
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
            modifier = Modifier.fillMaxWidth().padding(end = 56.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "LIVE GUARD",
                    style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 3.sp, fontWeight = FontWeight.Bold),
                    color = if (captureState.isScreenRecordingOrCasting) AuraRose else AuraCyan
                )
                Text(
                    text = "Sensors, Permission Timeline & Audit",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            Box(
                modifier = Modifier
                    .testTag("guard_test_alert_button")
                    .clip(RoundedCornerShape(100.dp))
                    .background(AuraPurple.copy(alpha = 0.16f))
                    .border(1.dp, AuraPurple.copy(alpha = 0.4f), RoundedCornerShape(100.dp))
                    .clickable {
                        guardMonitor.triggerSimulatedGuardTest()
                        Toast.makeText(context, "Test alert triggered", Toast.LENGTH_SHORT).show()
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.NotificationsActive, contentDescription = "Test Alert", tint = AuraPurple, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Test", color = AuraPurple, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Compact Live Sensor Hero Card
        CompactHardwareShieldCard(
            captureState = captureState,
            onScanClick = { triggerAnimatedDeepScan() },
            isScanning = isScanning,
            scanProgress = scanProgress
        )

        Spacer(modifier = Modifier.height(10.dp))

        // 3-Way Segmented Switcher: CHECKS | TIMELINE | EVENT LOG
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
                Triple("CHECKS", if (flaggedCount == 0) "CHECKS (SAFE)" else "CHECKS ($flaggedCount)", AuraCyan),
                Triple("TIMELINE", "TIMELINE (${accessTimeline.size})", AuraOrange),
                Triple("LOG", "EVENT LOG (${events.size})", AuraPurple)
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
                        GuardFilterPill("Overlays ($specCount)", activeTimelineFilter == "SPECIAL_ACCESS", AuraRose) {
                            activeTimelineFilter = "SPECIAL_ACCESS"
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(100.dp))
                            .background(AuraOrange.copy(alpha = 0.14f))
                            .border(1.dp, AuraOrange.copy(alpha = 0.35f), RoundedCornerShape(100.dp))
                            .clickable {
                                try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW_PERMISSION_USAGE))
                                } catch (_: Exception) {
                                    try {
                                        context.startActivity(Intent(Settings.ACTION_PRIVACY_SETTINGS))
                                    } catch (_: Exception) {}
                                }
                            }
                            .padding(horizontal = 9.dp, vertical = 4.dp)
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
                                        } catch (_: Exception) {}
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
                                Text(
                                    text = "No recent permission accesses for this filter.",
                                    color = TextSecondary,
                                    style = MaterialTheme.typography.bodySmall
                                )
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
                                            PrivacyAnalyzer.openSpecificPermissionSettings(
                                                context,
                                                item.packageName,
                                                item.permissionInfo
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            "LOG" -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 112.dp)
                ) {
                    if (events.isNotEmpty()) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = { guardMonitor.clearEvents() }) {
                                    Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Clear Log", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                    if (events.isEmpty()) {
                        item {
                            GlassCard(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    "No background alerts recorded.",
                                    color = TextSecondary,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    } else {
                        itemsIndexed(events, key = { index, ev -> "${ev.id}_$index" }) { _, ev ->
                            GuardEventRow(ev)
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

    val clockLabel = remember(event.timestamp) {
        SimpleDateFormat("hh:mm a", Locale.US).format(Date(event.timestamp))
    }
    val dateOrRelLabel = remember(event.timestamp) {
        formatRelativeTimelineTime(event.timestamp)
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
                color = tint,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                textAlign = TextAlign.End,
                maxLines = 1
            )
            Text(
                text = clockLabel,
                color = TextTertiary,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
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
                .background(GlassPanel.copy(alpha = 0.65f))
                .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
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

@Composable
private fun CompactHardwareShieldCard(
    captureState: LiveCaptureState,
    onScanClick: () -> Unit,
    isScanning: Boolean,
    scanProgress: Float
) {
    val anyActiveCapture = captureState.isScreenRecordingOrCasting || captureState.isMicActive || captureState.isCameraActive
    val heroAccent = when {
        captureState.isScreenRecordingOrCasting -> AuraRose
        captureState.isMicActive || captureState.isCameraActive -> AuraOrange
        else -> AuraGreen
    }

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

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .graphicsLayer {
                        if (anyActiveCapture) {
                            scaleX = pulseScale
                            scaleY = pulseScale
                        }
                    }
                    .clip(CircleShape)
                    .background(heroAccent.copy(alpha = 0.16f))
                    .border(1.5.dp, heroAccent.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (anyActiveCapture) Icons.Default.Warning else Icons.Default.Security,
                    contentDescription = null,
                    tint = heroAccent,
                    modifier = Modifier.size(22.dp)
                )
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
                }.joinToString(" • ")

                Text(
                    text = when {
                        captureState.isScreenRecordingOrCasting -> "SCREEN CAPTURE DETECTED"
                        captureState.isMicActive && captureState.isCameraActive -> "MIC & CAMERA IN USE"
                        captureState.isMicActive -> "MICROPHONE IN USE"
                        captureState.isCameraActive -> "CAMERA IN USE"
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
                        else -> "No background screen or audio capture"
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

        Spacer(modifier = Modifier.height(12.dp))

        // 3 Compact Sensor Pills
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LiveSensorChip(
                modifier = Modifier.weight(1f),
                icon = Icons.AutoMirrored.Filled.ScreenShare,
                label = "Screen",
                status = if (captureState.isScreenRecordingOrCasting) "Rec" else "Safe",
                isActive = captureState.isScreenRecordingOrCasting,
                activeColor = AuraRose
            )
            LiveSensorChip(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Mic,
                label = "Mic",
                status = if (captureState.isMicActive) (captureState.activeMicApps.firstOrNull() ?: "Live") else "Silent",
                isActive = captureState.isMicActive,
                activeColor = AuraOrange
            )
            LiveSensorChip(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.CameraAlt,
                label = "Camera",
                status = if (captureState.isCameraActive) (captureState.activeCameraApps.firstOrNull() ?: "Live") else "Idle",
                isActive = captureState.isCameraActive,
                activeColor = AuraOrange
            )
        }

        if (isScanning) {
            Spacer(modifier = Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { scanProgress },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                color = AuraCyan,
                trackColor = Color.White.copy(alpha = 0.08f),
                strokeCap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun LiveSensorChip(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    status: String,
    isActive: Boolean,
    activeColor: Color
) {
    val tint = if (isActive) activeColor else AuraGreen
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(tint.copy(alpha = 0.1f))
            .border(1.dp, tint.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
            .padding(vertical = 8.dp, horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Column {
            Text(label, color = TextSecondary, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp))
            Text(
                text = status,
                color = tint,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
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
            .clip(RoundedCornerShape(18.dp))
            .background(GlassPanel.copy(alpha = 0.58f))
            .border(
                1.dp,
                if (vector.severity != AuditSeverity.SAFE) sevColor.copy(alpha = 0.4f) else GlassBorder,
                RoundedCornerShape(18.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (vector.severity == AuditSeverity.SAFE) Icons.Default.CheckCircle else Icons.Default.Warning,
            contentDescription = null,
            tint = sevColor,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = vector.title,
                color = TextPrimary,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
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
                .clip(RoundedCornerShape(100.dp))
                .background(sevColor.copy(alpha = 0.14f))
                .padding(horizontal = 8.dp, vertical = 4.dp)
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
                .background(Color.White.copy(alpha = 0.05f))
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

        if (vector.settingsAction != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = { onOpenSettings(vector.settingsAction) },
                colors = ButtonDefaults.buttonColors(containerColor = AuraCyan),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = vector.actionButtonLabel.uppercase(),
                    color = Obsidian,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = Obsidian, modifier = Modifier.size(14.dp))
            }
        }

        if (vector.flaggedApps.isNotEmpty()) {
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "APPS (${vector.flaggedApps.size})",
                color = TextTertiary,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            vector.flaggedApps.take(10).forEach { app ->
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
                    Text(
                        text = "Revoke",
                        color = AuraCyan,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = AuraCyan, modifier = Modifier.size(13.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
private fun GuardEventRow(event: GuardEvent) {
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
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = timeStr,
                    color = TextTertiary,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Text(
                text = event.description,
                color = TextSecondary,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}
