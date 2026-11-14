package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AdsClick
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.GlassCard
import com.example.service.AppPrivacyReport
import com.example.service.DevicePrivacySummary
import com.example.service.PermissionCategory
import com.example.service.PermissionRiskLevel
import com.example.service.PlainPermissionInfo
import com.example.service.PrivacyAnalyzer
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
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PrivacyShieldScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var summary by remember { mutableStateOf<DevicePrivacySummary?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var includeSystemApps by remember { mutableStateOf(false) }
    var activeFilter by remember { mutableStateOf("ALL") } // "ALL", "HIGH_RISK", "ONLINE_RISK", "UNUSED", or PermissionCategory.name
    var searchQuery by remember { mutableStateOf("") }
    var selectedAppForModal by remember { mutableStateOf<AppPrivacyReport?>(null) }
    var showDictionaryModal by remember { mutableStateOf(false) }
    var refreshTrigger by remember { mutableIntStateOf(0) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshTrigger++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(refreshTrigger, includeSystemApps) {
        isLoading = true
        val result = withContext(Dispatchers.IO) {
            PrivacyAnalyzer.analyzeDevicePrivacy(context, includeSystemApps = includeSystemApps)
        }
        summary = result
        if (selectedAppForModal != null) {
            selectedAppForModal = result.appReports.find { it.packageName == selectedAppForModal?.packageName }
        }
        isLoading = false
    }

    val currentSummary = summary

    val filteredApps = remember(currentSummary, activeFilter, searchQuery) {
        val list = currentSummary?.appReports ?: emptyList()
        val now = System.currentTimeMillis()
        val sevenDaysMs = 7L * 24 * 60 * 60 * 1000L

        val byFilter = when (activeFilter) {
            "ALL" -> list
            "HIGH_RISK" -> list.filter { it.riskScore >= 35 }
            "ONLINE_RISK" -> list.filter { it.canUploadSensitiveData }
            "UNUSED" -> list.filter { it.riskScore >= 14 && it.lastTimeUsedMs > 0L && (now - it.lastTimeUsedMs) > sevenDaysMs }
            else -> {
                val cat = PermissionCategory.entries.find { it.name == activeFilter }
                if (cat != null) list.filter { app -> app.grantedPermissions.any { it.category == cat } } else list
            }
        }
        if (searchQuery.isBlank()) {
            byFilter
        } else {
            byFilter.filter {
                it.appName.contains(searchQuery.trim(), ignoreCase = true) ||
                    it.packageName.contains(searchQuery.trim(), ignoreCase = true)
            }
        }
    }

    if (selectedAppForModal != null) {
        val app = selectedAppForModal!!
        ModalBottomSheet(
            onDismissRequest = { selectedAppForModal = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Color(0xFF0F111A),
            contentColor = TextPrimary
        ) {
            PlainEnglishAppPermissionSheet(
                app = app,
                onRevokeAllClick = {
                    val appPermsIntent = Intent("android.intent.action.MANAGE_APP_PERMISSIONS").apply {
                        putExtra(Intent.EXTRA_PACKAGE_NAME, app.packageName)
                    }
                    try {
                        context.startActivity(appPermsIntent)
                    } catch (_: Exception) {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", app.packageName, null)
                            )
                        )
                    }
                },
                onSpecificPermissionClick = { perm ->
                    PrivacyAnalyzer.openSpecificPermissionSettings(context, app.packageName, perm)
                },
                onUninstallClick = {
                    context.startActivity(
                        Intent(
                            Intent.ACTION_DELETE,
                            Uri.fromParts("package", app.packageName, null)
                        )
                    )
                },
                onClose = { selectedAppForModal = null }
            )
        }
    }

    if (showDictionaryModal) {
        ModalBottomSheet(
            onDismissRequest = { showDictionaryModal = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Color(0xFF0F111A),
            contentColor = TextPrimary
        ) {
            PlainEnglishDictionarySheet(
                onPermissionClick = { perm ->
                    PrivacyAnalyzer.openSpecificPermissionSettings(context, null, perm)
                },
                onClose = { showDictionaryModal = false }
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
                    text = "PRIVACY SHIELD",
                    style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 4.sp, fontWeight = FontWeight.Bold),
                    color = AuraGreen
                )
                Text(
                    text = "Permission Score & Ad Telemetry",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(100.dp))
                    .background(AuraCyan.copy(alpha = 0.14f))
                    .border(1.dp, AuraCyan.copy(alpha = 0.35f), RoundedCornerShape(100.dp))
                    .clickable { showDictionaryModal = true }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = "Guide", tint = AuraCyan, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Guide", color = AuraCyan, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (isLoading || currentSummary == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AuraGreen)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 112.dp)
            ) {
                // Compact Privacy Score Hero Card
                item {
                    PrivacyScoreHeroCard(
                        summary = currentSummary,
                        activeFilter = activeFilter,
                        onSelectQuickFilter = { filterKey ->
                            activeFilter = if (activeFilter == filterKey) "ALL" else filterKey
                        }
                    )
                }

                // Dedicated Button/Card to Open Ad & Telemetry Collector Radar Page
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("open_telemetry_radar_card")
                            .clip(RoundedCornerShape(22.dp))
                            .background(AuraOrange.copy(alpha = 0.12f))
                            .border(1.dp, AuraOrange.copy(alpha = 0.4f), RoundedCornerShape(22.dp))
                            .clickable {
                                context.startActivity(Intent(context, DataTelemetryActivity::class.java))
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(AuraOrange.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.AdsClick, contentDescription = null, tint = AuraOrange, modifier = Modifier.size(20.dp))
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Ad & Telemetry Data Radar",
                                        color = TextPrimary,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = "See which apps collect ad data, trackers & network bytes",
                                        color = TextSecondary,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = "Open Radar", tint = AuraOrange)
                        }
                    }
                }

                // Category Filter Pills
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val chips = listOf(
                            Triple("ALL", "All (${currentSummary.appReports.size})", AuraCyan),
                            Triple("ONLINE_RISK", "Online + Sensors (${currentSummary.dataExfiltrationRiskCount})", AuraRose),
                            Triple("CAMERA_MIC", "Cam & Mic (${currentSummary.categoryCounts[PermissionCategory.CAMERA_MIC] ?: 0})", AuraOrange),
                            Triple("LOCATION", "Location (${currentSummary.categoryCounts[PermissionCategory.LOCATION] ?: 0})", AuraOrange),
                            Triple("CONTACTS_SMS", "SMS & Contacts (${currentSummary.categoryCounts[PermissionCategory.CONTACTS_SMS] ?: 0})", AuraRose),
                            Triple("STORAGE_MEDIA", "Files (${currentSummary.categoryCounts[PermissionCategory.STORAGE_MEDIA] ?: 0})", AuraCyan),
                            Triple("SPECIAL_ACCESS", "Special (${currentSummary.categoryCounts[PermissionCategory.SPECIAL_ACCESS] ?: 0})", AuraPurple)
                        )
                        chips.forEach { (key, label, tint) ->
                            val isSelected = activeFilter == key
                            Box(
                                modifier = Modifier
                                    .testTag("privacy_cat_${key.lowercase()}")
                                    .clip(RoundedCornerShape(100.dp))
                                    .background(if (isSelected) tint.copy(alpha = 0.2f) else GlassPanel.copy(alpha = 0.6f))
                                    .border(1.dp, if (isSelected) tint else GlassBorder, RoundedCornerShape(100.dp))
                                    .clickable { activeFilter = key }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) tint else TextSecondary,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }
                }

                // Search & User/System Toggle
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Search apps...", color = TextTertiary, style = MaterialTheme.typography.bodySmall) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = AuraCyan) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextSecondary)
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AuraCyan,
                                unfocusedBorderColor = GlassBorder,
                                focusedContainerColor = GlassPanel.copy(alpha = 0.55f),
                                unfocusedContainerColor = GlassPanel.copy(alpha = 0.55f),
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (includeSystemApps) AuraPurple.copy(alpha = 0.2f) else GlassPanel.copy(alpha = 0.6f))
                                .border(1.dp, if (includeSystemApps) AuraPurple else GlassBorder, RoundedCornerShape(16.dp))
                                .clickable { includeSystemApps = !includeSystemApps }
                                .padding(horizontal = 12.dp, vertical = 14.dp)
                        ) {
                            Text(
                                text = if (includeSystemApps) "All" else "User",
                                color = if (includeSystemApps) AuraPurple else TextSecondary,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }

                items(filteredApps, key = { it.packageName }) { app ->
                    AppPrivacyCard(
                        app = app,
                        onClick = { selectedAppForModal = app },
                        onPermissionChipClick = { perm ->
                            PrivacyAnalyzer.openSpecificPermissionSettings(context, app.packageName, perm)
                        },
                        onQuickRevoke = {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", app.packageName, null)
                                )
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PrivacyScoreHeroCard(
    summary: DevicePrivacySummary,
    activeFilter: String,
    onSelectQuickFilter: (String) -> Unit
) {
    val scoreColor = when {
        summary.privacyScore >= 85 -> AuraCyan
        summary.privacyScore >= 70 -> AuraGreen
        summary.privacyScore >= 50 -> AuraOrange
        else -> AuraRose
    }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(102.dp)) {
                Canvas(modifier = Modifier.size(92.dp)) {
                    val stroke = 9.dp.toPx()
                    drawArc(
                        color = GlassBorder,
                        startAngle = 135f,
                        sweepAngle = 270f,
                        useCenter = false,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                    val sweep = (summary.privacyScore / 100f) * 270f
                    drawArc(
                        color = scoreColor,
                        startAngle = 135f,
                        sweepAngle = sweep,
                        useCenter = false,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${summary.privacyScore}",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )
                    Text(
                        text = "GRADE ${summary.letterGrade}",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                        color = scoreColor
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = summary.statusHeadline,
                    color = scoreColor,
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp, fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = summary.statusExplanation,
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
                if (summary.topActionTips.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Tip: ${summary.topActionTips.first()}",
                        color = AuraCyan,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Interactive Quick Filter Pills inside Hero
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ScoreFilterTile(
                modifier = Modifier.weight(1f),
                label = "High Risk",
                value = "${summary.highRiskAppCount}",
                color = AuraRose,
                selected = activeFilter == "HIGH_RISK",
                onClick = { onSelectQuickFilter("HIGH_RISK") }
            )
            ScoreFilterTile(
                modifier = Modifier.weight(1f),
                label = "Online + Sensor",
                value = "${summary.dataExfiltrationRiskCount}",
                color = AuraOrange,
                selected = activeFilter == "ONLINE_RISK",
                onClick = { onSelectQuickFilter("ONLINE_RISK") }
            )
            ScoreFilterTile(
                modifier = Modifier.weight(1f),
                label = "Unused Risky",
                value = "${summary.unusedRiskyAppCount}",
                color = AuraCyan,
                selected = activeFilter == "UNUSED",
                onClick = { onSelectQuickFilter("UNUSED") }
            )
        }
    }
}

@Composable
private fun ScoreFilterTile(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) color.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.04f))
            .border(1.dp, if (selected) color else GlassBorder, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(vertical = 8.dp, horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = color)
            Text(label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = TextSecondary, maxLines = 1)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppPrivacyCard(
    app: AppPrivacyReport,
    onClick: () -> Unit,
    onPermissionChipClick: (PlainPermissionInfo) -> Unit,
    onQuickRevoke: () -> Unit
) {
    val badgeColor = when (app.riskBadge) {
        "HIGH RISK" -> AuraRose
        "MODERATE" -> AuraOrange
        "LOW RISK" -> AuraCyan
        else -> AuraGreen
    }

    val iconBitmap = remember(app.packageName) {
        app.icon.toSafeBitmap().asImageBitmap()
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(GlassPanel.copy(alpha = 0.58f))
            .border(1.dp, GlassBorder, RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    bitmap = iconBitmap,
                    contentDescription = app.appName,
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = app.appName,
                        color = TextPrimary,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (app.hasInternetAccess) Icons.Default.CloudUpload else Icons.Default.CloudOff,
                            contentDescription = null,
                            tint = if (app.canUploadSensitiveData) AuraOrange else TextTertiary,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = buildString {
                                append(if (app.hasInternetAccess) "Online" else "Offline")
                                append(" • ")
                                append(app.installerName)
                            },
                            color = if (app.isSideloaded) AuraOrange else TextTertiary,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                // Direct 1-Tap Revoke Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(100.dp))
                        .background(badgeColor.copy(alpha = 0.14f))
                        .border(1.dp, badgeColor.copy(alpha = 0.4f), RoundedCornerShape(100.dp))
                        .clickable { onQuickRevoke() }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = if (app.grantedPermissions.isEmpty()) "Safe" else "Revoke",
                        color = badgeColor,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }

            if (app.grantedPermissions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    app.grantedPermissions.take(4).forEach { perm ->
                        val chipColor = when (perm.riskLevel) {
                            PermissionRiskLevel.CRITICAL -> AuraRose
                            PermissionRiskLevel.HIGH -> AuraOrange
                            PermissionRiskLevel.MEDIUM -> AuraCyan
                            PermissionRiskLevel.LOW -> TextSecondary
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(chipColor.copy(alpha = 0.12f))
                                .clickable { onPermissionChipClick(perm) }
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = perm.shortTag,
                                color = chipColor,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                    if (app.grantedPermissions.size > 4) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White.copy(alpha = 0.08f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "+${app.grantedPermissions.size - 4}",
                                color = TextSecondary,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlainEnglishAppPermissionSheet(
    app: AppPrivacyReport,
    onRevokeAllClick: () -> Unit,
    onSpecificPermissionClick: (PlainPermissionInfo) -> Unit,
    onUninstallClick: () -> Unit,
    onClose: () -> Unit
) {
    val iconBitmap = remember(app.packageName) {
        app.icon.toSafeBitmap().asImageBitmap()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                bitmap = iconBitmap,
                contentDescription = app.appName,
                modifier = Modifier.size(46.dp).clip(RoundedCornerShape(12.dp))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.appName,
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = if (app.canUploadSensitiveData) "Has Internet + Sensitive Sensors" else if (app.hasInternetAccess) "Has Internet Access" else "Offline App",
                    color = if (app.canUploadSensitiveData) AuraOrange else TextSecondary,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onRevokeAllClick,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = AuraCyan),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Shield, contentDescription = null, tint = Obsidian, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "ALL PERMISSIONS",
                    color = Obsidian,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                )
            }

            if (!app.isSystem) {
                OutlinedButton(
                    onClick = onUninstallClick,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Uninstall", tint = AuraRose, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Uninstall", color = AuraRose, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (app.grantedPermissions.isEmpty()) {
            Text(
                "No sensitive permissions granted.",
                color = AuraGreen,
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            Text(
                text = "TAP ANY PERMISSION TO MANAGE (${app.grantedPermissions.size})",
                color = TextTertiary,
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(10.dp))

            app.grantedPermissions.forEach { perm ->
                PermissionExplanationItem(
                    perm = perm,
                    actionLabel = "Revoke",
                    onClick = { onSpecificPermissionClick(perm) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
private fun PermissionExplanationItem(
    perm: PlainPermissionInfo,
    actionLabel: String = "Manage",
    onClick: () -> Unit
) {
    val riskColor = when (perm.riskLevel) {
        PermissionRiskLevel.CRITICAL -> AuraRose
        PermissionRiskLevel.HIGH -> AuraOrange
        PermissionRiskLevel.MEDIUM -> AuraCyan
        PermissionRiskLevel.LOW -> AuraGreen
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(GlassPanel.copy(alpha = 0.7f))
            .border(1.dp, riskColor.copy(alpha = 0.28f), RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = perm.friendlyTitle,
                    color = TextPrimary,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(100.dp))
                        .background(riskColor.copy(alpha = 0.16f))
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = perm.riskLevel.label,
                        color = riskColor,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = perm.simpleExplanation,
                color = TextPrimary,
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = perm.whyItMatters,
                color = TextTertiary,
                style = MaterialTheme.typography.labelSmall
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Direct 1-tap button on the permission card itself
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(100.dp))
                .background(AuraCyan.copy(alpha = 0.14f))
                .border(1.dp, AuraCyan.copy(alpha = 0.35f), RoundedCornerShape(100.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(
                text = actionLabel,
                color = AuraCyan,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.width(3.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = actionLabel,
                tint = AuraCyan,
                modifier = Modifier.size(12.dp)
            )
        }
    }
}

@Composable
private fun PlainEnglishDictionarySheet(
    onPermissionClick: (PlainPermissionInfo) -> Unit,
    onClose: () -> Unit
) {
    val allPerms = remember { PrivacyAnalyzer.getPlainDictionary() }
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
            Text(
                "PERMISSION GUIDE (TAP TO MANAGE)",
                color = AuraCyan,
                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        allPerms.forEach { perm ->
            PermissionExplanationItem(
                perm = perm,
                actionLabel = "Open",
                onClick = { onPermissionClick(perm) }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        Spacer(modifier = Modifier.height(20.dp))
    }
}
