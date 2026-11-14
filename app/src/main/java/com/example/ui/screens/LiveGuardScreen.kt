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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.ScreenShare
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Security
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
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

    var activeSubTab by remember { mutableStateOf("CHECKS") } // "CHECKS" or "LOG"
    var auditVectors by remember { mutableStateOf<List<SecurityAuditVector>>(emptyList()) }
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
                refreshTrigger++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(refreshTrigger) {
        if (!isScanning) {
            val vectors = withContext(Dispatchers.IO) {
                guardMonitor.runDeepSecurityAudit(includeSystemApps = false)
            }
            auditVectors = vectors
            if (selectedVector != null) {
                selectedVector = vectors.find { it.id == selectedVector?.id }
            }
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
            val results = withContext(Dispatchers.IO) {
                guardMonitor.runDeepSecurityAudit(includeSystemApps = false)
            }
            auditVectors = results
            isScanning = false
        }
    }

    if (selectedVector != null) {
        val vector = selectedVector!!
        ModalBottomSheet(
            onDismissRequest = { selectedVector = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Color(0xFF0F111A),
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
        Spacer(modifier = Modifier.height(18.dp))

        // Minimalist Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(end = 56.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "LIVE GUARD",
                    style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 4.sp, fontWeight = FontWeight.Bold),
                    color = if (captureState.isScreenRecordingOrCasting) AuraRose else AuraCyan
                )
                Text(
                    text = "Screen, Mic & Spyware Protection",
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

        Spacer(modifier = Modifier.height(14.dp))

        // Compact Live Sensor Hero Card
        CompactHardwareShieldCard(
            captureState = captureState,
            onScanClick = { triggerAnimatedDeepScan() },
            isScanning = isScanning,
            scanProgress = scanProgress
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Segmented Switcher: CHECKS | LOG
        val flaggedCount = auditVectors.count { it.severity != AuditSeverity.SAFE }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(100.dp))
                .background(GlassPanel.copy(alpha = 0.7f))
                .border(1.dp, GlassBorder, RoundedCornerShape(100.dp))
                .padding(4.dp)
        ) {
            val tabs = listOf(
                "CHECKS" to if (flaggedCount == 0) "CHECKS (ALL SAFE)" else "CHECKS ($flaggedCount REVIEW)",
                "LOG" to "EVENT LOG (${events.size})"
            )
            tabs.forEach { (key, label) ->
                val selected = activeSubTab == key
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(100.dp))
                        .background(if (selected) AuraCyan.copy(alpha = 0.18f) else Color.Transparent)
                        .clickable { activeSubTab = key }
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

        Spacer(modifier = Modifier.height(12.dp))

        if (activeSubTab == "CHECKS") {
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
        } else {
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
                    items(events, key = { it.id }) { ev ->
                        GuardEventRow(ev)
                    }
                }
            }
        }
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
                Text(
                    text = when {
                        captureState.isScreenRecordingOrCasting -> "SCREEN CAPTURE DETECTED"
                        captureState.isMicActive || captureState.isCameraActive -> "MIC OR CAMERA IN USE"
                        else -> "SHIELD ACTIVE"
                    },
                    color = heroAccent,
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold)
                )
                Text(
                    text = when {
                        captureState.isScreenRecordingOrCasting -> "Display is being mirrored or recorded"
                        captureState.isMicActive || captureState.isCameraActive -> "Hardware sensor active right now"
                        else -> "No background screen or audio capture"
                    },
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall
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
                status = if (captureState.isMicActive) "Live" else "Silent",
                isActive = captureState.isMicActive,
                activeColor = AuraOrange
            )
            LiveSensorChip(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.CameraAlt,
                label = "Camera",
                status = if (captureState.isCameraActive) "Live" else "Idle",
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
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
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
                val bmp = remember(app.packageName) { app.icon.toSafeBitmap().asImageBitmap() }
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
