package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.graphics.asImageBitmap
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
import com.example.service.AppTelemetryReport
import com.example.service.DevicePrivacySummary
import com.example.service.DeviceTelemetrySummary
import com.example.service.PermissionCategory
import com.example.service.PermissionRiskLevel
import com.example.service.PlainPermissionInfo
import com.example.service.PrivacyAnalyzer
import com.example.service.PrivacyChangeEvent
import com.example.service.PrivacyChangeType
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PrivacyShieldScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var activeSubTab by remember { mutableStateOf("APPS") } // "APPS", "CHANGES", "TRACKERS"
    var includeSystemApps by remember { mutableStateOf(false) }
    var summary by remember { mutableStateOf(PrivacyAnalyzer.peekCachedPrivacySummary(false)) }
    var telemetrySummary by remember { mutableStateOf(PrivacyAnalyzer.peekCachedTelemetry(false)) }
    var changeEvents by remember { mutableStateOf(PrivacyAnalyzer.peekCachedChanges() ?: emptyList()) }
    var isLoading by remember { mutableStateOf(summary == null) }

    // Sub-tab filter states
    var activeAppFilter by remember { mutableStateOf("ALL") }
    var activeChangeFilter by remember { mutableStateOf("ALL") } // "ALL", "REVOKED", "ADDED", "NEW_APP"
    var activeTrackerFilter by remember { mutableStateOf("ALL") } // "ALL", "HEAVY", "AD_ID", "BOOT"
    var searchQuery by remember { mutableStateOf("") }

    // Bottom sheets
    var selectedAppForModal by remember { mutableStateOf<AppPrivacyReport?>(null) }
    var selectedTrackerApp by remember { mutableStateOf<AppTelemetryReport?>(null) }
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
        isLoading = summary == null
        withContext(Dispatchers.IO) {
            val privResult = PrivacyAnalyzer.analyzeDevicePrivacy(context, includeSystemApps = includeSystemApps)
            val changes = PrivacyAnalyzer.getPrivacyChanges(context, privResult.appReports)
            val telemResult = PrivacyAnalyzer.analyzeAppTelemetryAndTrackers(context, includeSystemApps = includeSystemApps)
            summary = privResult
            changeEvents = changes
            telemetrySummary = telemResult
            if (selectedAppForModal != null) {
                selectedAppForModal = privResult.appReports.find { r -> r.packageName == selectedAppForModal?.packageName }
            }
            if (selectedTrackerApp != null) {
                selectedTrackerApp = telemResult.appReports.find { r -> r.packageName == selectedTrackerApp?.packageName }
            }
        }
        isLoading = false
    }

    val currentSummary = summary
    val currentTelemetry = telemetrySummary

    // Filtered Apps for APPS tab
    val filteredApps = remember(currentSummary, activeAppFilter, searchQuery) {
        val list = currentSummary?.appReports ?: emptyList()
        val byFilter = when (activeAppFilter) {
            "ALL" -> list
            "HIGH_RISK" -> list.filter { it.riskScore >= 35 }
            else -> {
                val cat = PermissionCategory.entries.find { it.name == activeAppFilter }
                if (cat != null) list.filter { app -> app.grantedPermissions.any { it.category == cat } } else list
            }
        }
        if (searchQuery.isBlank()) {
            byFilter
        } else {
            val q = searchQuery.trim()
            byFilter.filter {
                it.appName.contains(q, ignoreCase = true) ||
                    it.packageName.contains(q, ignoreCase = true) ||
                    it.grantedPermissions.any { p -> p.shortTag.contains(q, ignoreCase = true) || p.friendlyTitle.contains(q, ignoreCase = true) }
            }
        }
    }

    // Filtered Changes for CHANGES tab
    val filteredChanges = remember(changeEvents, activeChangeFilter, searchQuery) {
        val byType = when (activeChangeFilter) {
            "REVOKED" -> changeEvents.filter { it.changeType == PrivacyChangeType.PERM_REVOKED || it.changeType == PrivacyChangeType.APP_REMOVED }
            "ADDED" -> changeEvents.filter { it.changeType == PrivacyChangeType.PERM_ADDED }
            "NEW_APP" -> changeEvents.filter { it.changeType == PrivacyChangeType.NEW_APP }
            else -> changeEvents
        }
        if (searchQuery.isBlank()) {
            byType
        } else {
            val q = searchQuery.trim()
            byType.filter {
                it.appName.contains(q, ignoreCase = true) ||
                    it.permissionTitle.contains(q, ignoreCase = true)
            }
        }
    }

    // Filtered Trackers for TRACKERS tab
    val filteredTrackers = remember(currentTelemetry, activeTrackerFilter, searchQuery) {
        val list = currentTelemetry?.appReports ?: emptyList()
        val byFilter = when (activeTrackerFilter) {
            "HEAVY" -> list.filter { it.telemetryScore >= 35 || it.detectedAdNetworks.isNotEmpty() }
            "AD_ID" -> list.filter { it.usesAdId }
            "BOOT" -> list.filter { it.runsAtBoot }
            else -> list
        }
        if (searchQuery.isBlank()) {
            byFilter
        } else {
            val q = searchQuery.trim()
            byFilter.filter {
                it.appName.contains(q, ignoreCase = true) ||
                    it.detectedAdNetworks.any { sdk -> sdk.contains(q, ignoreCase = true) } ||
                    it.detectedAnalyticsSdks.any { sdk -> sdk.contains(q, ignoreCase = true) }
            }
        }
    }

    // App Permission Bottom Sheet
    if (selectedAppForModal != null) {
        val app = selectedAppForModal!!
        ModalBottomSheet(
            onDismissRequest = { selectedAppForModal = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = GlassPanel,
            contentColor = TextPrimary
        ) {
            MinimalAppPermissionSheet(
                app = app,
                onManageAllClick = {
                    PrivacyAnalyzer.openAppPermissionScreen(context, app.packageName)
                },
                onSpecificPermissionClick = { perm ->
                    PrivacyAnalyzer.openSpecificPermissionSettings(context, app.packageName, perm)
                },
                onUninstallClick = {
                    context.startActivity(
                        Intent(Intent.ACTION_DELETE, Uri.fromParts("package", app.packageName, null))
                    )
                },
                onClose = { selectedAppForModal = null }
            )
        }
    }

    // Tracker Inspection Bottom Sheet
    if (selectedTrackerApp != null) {
        val app = selectedTrackerApp!!
        ModalBottomSheet(
            onDismissRequest = { selectedTrackerApp = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = GlassPanel,
            contentColor = TextPrimary
        ) {
            MinimalTrackerDetailSheet(
                app = app,
                onRestrictBackgroundClick = {
                    try {
                        val intent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                            Intent(
                                Settings.ACTION_IGNORE_BACKGROUND_DATA_RESTRICTIONS_SETTINGS,
                                Uri.parse("package:${app.packageName}")
                            )
                        } else {
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", app.packageName, null)
                            )
                        }
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", app.packageName, null)
                            )
                        )
                    }
                },
                onClose = { selectedTrackerApp = null }
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

        // Header with inline Privacy Score & System Toggle
        Row(
            modifier = Modifier.fillMaxWidth().padding(end = 52.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "PRIVACY",
                    style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 3.sp, fontWeight = FontWeight.Bold),
                    color = AuraGreen
                )
                if (currentSummary != null) {
                    Spacer(modifier = Modifier.width(10.dp))
                    val scoreColor = when {
                        currentSummary.privacyScore >= 80 -> AuraGreen
                        currentSummary.privacyScore >= 60 -> AuraCyan
                        currentSummary.privacyScore >= 40 -> AuraOrange
                        else -> AuraRose
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(100.dp))
                            .background(scoreColor.copy(alpha = 0.14f))
                            .border(1.dp, scoreColor.copy(alpha = 0.35f), RoundedCornerShape(100.dp))
                            .padding(horizontal = 9.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "${currentSummary.privacyScore}/100",
                            color = scoreColor,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }

            // Compact System Apps Toggle
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(100.dp))
                    .background(if (includeSystemApps) AuraCyan.copy(alpha = 0.16f) else GlassPanel)
                    .border(1.dp, if (includeSystemApps) AuraCyan else GlassBorder, RoundedCornerShape(100.dp))
                    .clickable { includeSystemApps = !includeSystemApps }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    text = if (includeSystemApps) "System: ON" else "System: OFF",
                    color = if (includeSystemApps) AuraCyan else TextSecondary,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 3-Way Segmented Sub-Tab Switcher: APPS | CHANGES | TRACKERS
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(100.dp))
                .background(GlassPanel.copy(alpha = 0.8f))
                .border(1.dp, GlassBorder, RoundedCornerShape(100.dp))
                .padding(3.dp)
        ) {
            val appCount = currentSummary?.appReports?.size ?: 0
            val changeCount = changeEvents.size
            val trackerCount = currentTelemetry?.heavyCollectorsCount ?: 0

            val tabs = listOf(
                Triple("APPS", "APPS ($appCount)", AuraGreen),
                Triple("CHANGES", "CHANGES ($changeCount)", AuraCyan),
                Triple("TRACKERS", "TRACKERS ($trackerCount)", AuraPurple)
            )

            tabs.forEach { (key, label, tint) ->
                val selected = activeSubTab == key
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .testTag("privacy_subtab_${key.lowercase()}")
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

        // Compact Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                val hint = when (activeSubTab) {
                    "CHANGES" -> "Search permission changes..."
                    "TRACKERS" -> "Search apps or tracker SDKs..."
                    else -> "Search apps or permissions..."
                }
                Text(hint, color = TextTertiary, style = MaterialTheme.typography.bodySmall)
            },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = "Search", tint = AuraCyan, modifier = Modifier.size(18.dp))
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextSecondary, modifier = Modifier.size(16.dp))
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AuraCyan,
                unfocusedBorderColor = GlassBorder,
                focusedContainerColor = GlassPanel.copy(alpha = 0.6f),
                unfocusedContainerColor = GlassPanel.copy(alpha = 0.6f),
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            )
        )

        Spacer(modifier = Modifier.height(10.dp))

        if (isLoading || currentSummary == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AuraGreen, strokeWidth = 2.dp)
            }
        } else {
            when (activeSubTab) {
                "APPS" -> {
                    // Single horizontal filter pill strip
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val pills = listOf(
                            Triple("ALL", "All (${currentSummary.appReports.size})", AuraGreen),
                            Triple("HIGH_RISK", "High Risk (${currentSummary.highRiskAppCount})", AuraRose),
                            Triple(PermissionCategory.CAMERA_MIC.name, "Cam & Mic (${currentSummary.categoryCounts[PermissionCategory.CAMERA_MIC] ?: 0})", AuraOrange),
                            Triple(PermissionCategory.LOCATION.name, "Location (${currentSummary.categoryCounts[PermissionCategory.LOCATION] ?: 0})", AuraCyan),
                            Triple(PermissionCategory.CONTACTS_SMS.name, "Contacts/SMS (${currentSummary.categoryCounts[PermissionCategory.CONTACTS_SMS] ?: 0})", AuraPurple),
                            Triple(PermissionCategory.STORAGE_MEDIA.name, "Files (${currentSummary.categoryCounts[PermissionCategory.STORAGE_MEDIA] ?: 0})", AuraCyan),
                            Triple(PermissionCategory.SPECIAL_ACCESS.name, "Special (${currentSummary.categoryCounts[PermissionCategory.SPECIAL_ACCESS] ?: 0})", AuraRose)
                        )
                        pills.forEach { (key, label, tint) ->
                            FilterPill(
                                label = label,
                                selected = activeAppFilter == key,
                                tint = tint,
                                onClick = { activeAppFilter = key }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 112.dp)
                    ) {
                        if (filteredApps.isEmpty()) {
                            item {
                                GlassCard(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = "No matching apps in this category.",
                                        color = TextSecondary,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        } else {
                            items(filteredApps, key = { it.packageName }) { app ->
                                AppPermissionChipCard(
                                    app = app,
                                    onCardClick = { selectedAppForModal = app },
                                    onPermissionChipClick = { perm ->
                                        PrivacyAnalyzer.openSpecificPermissionSettings(context, app.packageName, perm)
                                    }
                                )
                            }
                        }
                    }
                }

                "CHANGES" -> {
                    val revokedCount = changeEvents.count { it.changeType == PrivacyChangeType.PERM_REVOKED || it.changeType == PrivacyChangeType.APP_REMOVED }
                    val addedCount = changeEvents.count { it.changeType == PrivacyChangeType.PERM_ADDED }
                    val newAppCount = changeEvents.count { it.changeType == PrivacyChangeType.NEW_APP }

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
                            FilterPill("All (${changeEvents.size})", activeChangeFilter == "ALL", AuraCyan) {
                                activeChangeFilter = "ALL"
                            }
                            FilterPill("Revoked ($revokedCount)", activeChangeFilter == "REVOKED", AuraGreen) {
                                activeChangeFilter = "REVOKED"
                            }
                            FilterPill("Granted ($addedCount)", activeChangeFilter == "ADDED", AuraOrange) {
                                activeChangeFilter = "ADDED"
                            }
                            FilterPill("New Apps ($newAppCount)", activeChangeFilter == "NEW_APP", AuraPurple) {
                                activeChangeFilter = "NEW_APP"
                            }
                        }

                        if (changeEvents.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Clear",
                                color = AuraRose,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        changeEvents = PrivacyAnalyzer.clearPrivacyChangeLog(
                                            context,
                                            currentSummary.appReports
                                        )
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 112.dp)
                    ) {
                        if (filteredChanges.isEmpty()) {
                            item {
                                GlassCard(modifier = Modifier.fillMaxWidth()) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.History,
                                            contentDescription = null,
                                            tint = AuraCyan,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = "No permission or app changes recorded yet. Revoke or grant any permission to see it logged here.",
                                            color = TextSecondary,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        } else {
                            itemsIndexed(
                                items = filteredChanges,
                                key = { index, ev -> "${ev.id}_$index" }
                            ) { _, ev ->
                                MinimalChangeEventRow(
                                    event = ev,
                                    onClick = {
                                        val matchingApp = currentSummary.appReports.find { it.packageName == ev.packageName }
                                        if (matchingApp != null) {
                                            selectedAppForModal = matchingApp
                                        } else {
                                            PrivacyAnalyzer.openAppPermissionScreen(context, ev.packageName)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                "TRACKERS" -> {
                    val telem = currentTelemetry
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
                            FilterPill("All (${telem?.appReports?.size ?: 0})", activeTrackerFilter == "ALL", AuraPurple) {
                                activeTrackerFilter = "ALL"
                            }
                            FilterPill("Ad SDKs (${telem?.heavyCollectorsCount ?: 0})", activeTrackerFilter == "HEAVY", AuraRose) {
                                activeTrackerFilter = "HEAVY"
                            }
                            FilterPill("Ad ID (${telem?.adIdAppsCount ?: 0})", activeTrackerFilter == "AD_ID", AuraOrange) {
                                activeTrackerFilter = "AD_ID"
                            }
                            FilterPill("Boot (${telem?.bootTrackersCount ?: 0})", activeTrackerFilter == "BOOT", AuraCyan) {
                                activeTrackerFilter = "BOOT"
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(100.dp))
                                .background(AuraPurple.copy(alpha = 0.14f))
                                .border(1.dp, AuraPurple.copy(alpha = 0.35f), RoundedCornerShape(100.dp))
                                .clickable {
                                    try {
                                        context.startActivity(Intent("com.google.android.gms.settings.ADS_PRIVACY"))
                                    } catch (_: Exception) {
                                        try {
                                            context.startActivity(Intent(Settings.ACTION_PRIVACY_SETTINGS))
                                        } catch (_: Exception) {}
                                    }
                                }
                                .padding(horizontal = 9.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Ad ID",
                                color = AuraPurple,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 112.dp)
                    ) {
                        if (filteredTrackers.isEmpty()) {
                            item {
                                GlassCard(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = "No apps matched this tracker filter.",
                                        color = TextSecondary,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        } else {
                            items(filteredTrackers, key = { it.packageName }) { report ->
                                MinimalTrackerAppRow(
                                    report = report,
                                    onClick = { selectedTrackerApp = report }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterPill(
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppPermissionChipCard(
    app: AppPrivacyReport,
    onCardClick: () -> Unit,
    onPermissionChipClick: (PlainPermissionInfo) -> Unit
) {
    val badgeColor = when (app.riskBadge) {
        "HIGH RISK" -> AuraRose
        "MODERATE" -> AuraOrange
        "LOW RISK" -> AuraCyan
        else -> AuraGreen
    }

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCardClick() }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val bmp = remember(app.packageName) { getCachedAppIconBitmap(app.packageName, app.icon) }
            Image(
                bitmap = bmp,
                contentDescription = app.appName,
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
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
                val lastUsedStr = if (app.lastTimeUsedMs > 0L) {
                    "Used ${formatRelativeShortTime(app.lastTimeUsedMs)}"
                } else {
                    app.installerName
                }
                Text(
                    text = "${app.installerName} • $lastUsedStr",
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
                    .background(badgeColor.copy(alpha = 0.14f))
                    .border(1.dp, badgeColor.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            ) {
                Text(
                    text = app.riskBadge,
                    color = badgeColor,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold)
                )
            }
        }

        if (app.grantedPermissions.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                app.grantedPermissions.forEach { perm ->
                    val pColor = when (perm.riskLevel) {
                        PermissionRiskLevel.CRITICAL -> AuraRose
                        PermissionRiskLevel.HIGH -> AuraOrange
                        PermissionRiskLevel.MEDIUM -> AuraCyan
                        PermissionRiskLevel.LOW -> AuraGreen
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(pColor.copy(alpha = 0.12f))
                            .border(1.dp, pColor.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .clickable { onPermissionChipClick(perm) }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = perm.shortTag,
                            color = pColor,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        )
                    }
                }
                if (app.hasInternetAccess) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.06f))
                            .border(1.dp, GlassBorder, RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "+ Internet",
                            color = TextSecondary,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)
                        )
                    }
                }
            }
        } else {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "No sensitive permissions granted",
                color = AuraGreen,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun MinimalChangeEventRow(
    event: PrivacyChangeEvent,
    onClick: () -> Unit
) {
    val (tint, icon, badgeText) = when (event.changeType) {
        PrivacyChangeType.PERM_REVOKED -> Triple(AuraGreen, Icons.Default.RemoveCircleOutline, "REVOKED")
        PrivacyChangeType.PERM_ADDED -> Triple(AuraOrange, Icons.Default.AddCircleOutline, "GRANTED")
        PrivacyChangeType.NEW_APP -> Triple(AuraCyan, Icons.Default.NewReleases, "NEW APP")
        PrivacyChangeType.APP_REMOVED -> Triple(AuraRose, Icons.Default.Delete, "REMOVED")
    }

    val timeLabel = remember(event.timestamp) { formatRelativeShortTime(event.timestamp) }

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = event.appName,
                        color = TextPrimary,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "• $timeLabel",
                        color = TextTertiary,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)
                    )
                }
                Text(
                    text = "${event.permissionTitle} (${event.detail})",
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
                    .background(tint.copy(alpha = 0.14f))
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            ) {
                Text(
                    text = badgeText,
                    color = tint,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}

@Composable
private fun MinimalTrackerAppRow(
    report: AppTelemetryReport,
    onClick: () -> Unit
) {
    val badgeColor = when (report.severityBadge) {
        "HEAVY" -> AuraRose
        "MODERATE" -> AuraOrange
        "LIGHT" -> AuraCyan
        else -> AuraGreen
    }

    val totalTrackers = report.detectedAdNetworks.size + report.detectedAnalyticsSdks.size
    val subtitle = remember(report) {
        val parts = mutableListOf<String>()
        if (report.detectedAdNetworks.isNotEmpty()) {
            parts.add("${report.detectedAdNetworks.size} Ad SDKs")
        }
        if (report.detectedAnalyticsSdks.isNotEmpty()) {
            parts.add("${report.detectedAnalyticsSdks.size} Analytics")
        }
        if (report.usesAdId) parts.add("Ad ID")
        if (report.runsAtBoot) parts.add("Auto-Boot")
        if (parts.isEmpty()) "0 known trackers" else parts.joinToString(" • ")
    }

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val bmp = remember(report.packageName) { getCachedAppIconBitmap(report.packageName, report.icon) }
            Image(
                bitmap = bmp,
                contentDescription = report.appName,
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = report.appName,
                    color = TextPrimary,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
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
                    .background(badgeColor.copy(alpha = 0.14f))
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            ) {
                Text(
                    text = if (totalTrackers > 0) "$totalTrackers SDKs" else "CLEAN",
                    color = badgeColor,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}

@Composable
private fun MinimalAppPermissionSheet(
    app: AppPrivacyReport,
    onManageAllClick: () -> Unit,
    onSpecificPermissionClick: (PlainPermissionInfo) -> Unit,
    onUninstallClick: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val bmp = remember(app.packageName) { getCachedAppIconBitmap(app.packageName, app.icon) }
            Image(
                bitmap = bmp,
                contentDescription = app.appName,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.appName,
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "${app.grantedPermissions.size} active permissions • ${app.installerName}",
                    color = TextSecondary,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onManageAllClick,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = AuraCyan, contentColor = Obsidian),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Permissions", fontWeight = FontWeight.Bold)
            }

            if (!app.isSystem) {
                OutlinedButton(
                    onClick = onUninstallClick,
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AuraRose.copy(alpha = 0.5f))
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = AuraRose, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Uninstall", color = AuraRose, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (app.grantedPermissions.isEmpty()) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AuraGreen, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("No sensitive permissions granted.", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
            }
        } else {
            Text(
                text = "TAP ANY PERMISSION TO REVOKE",
                color = TextTertiary,
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(8.dp))

            app.grantedPermissions.forEach { perm ->
                val pColor = when (perm.riskLevel) {
                    PermissionRiskLevel.CRITICAL -> AuraRose
                    PermissionRiskLevel.HIGH -> AuraOrange
                    PermissionRiskLevel.MEDIUM -> AuraCyan
                    PermissionRiskLevel.LOW -> AuraGreen
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.04f))
                        .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
                        .clickable { onSpecificPermissionClick(perm) }
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = perm.friendlyTitle,
                            color = TextPrimary,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = perm.simpleExplanation,
                            color = TextSecondary,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(pColor.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Revoke",
                            color = pColor,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MinimalTrackerDetailSheet(
    app: AppTelemetryReport,
    onRestrictBackgroundClick: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val bmp = remember(app.packageName) { getCachedAppIconBitmap(app.packageName, app.icon) }
            Image(
                bitmap = bmp,
                contentDescription = app.appName,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.appName,
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "Score ${app.telemetryScore}/100 • ${app.severityBadge}",
                    color = TextSecondary,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Button(
            onClick = onRestrictBackgroundClick,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = AuraPurple, contentColor = Obsidian),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Restrict Background Data", fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (app.detectedAdNetworks.isNotEmpty()) {
            Text(
                text = "AD NETWORKS (${app.detectedAdNetworks.size})",
                color = AuraRose,
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                app.detectedAdNetworks.forEach { sdk ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(AuraRose.copy(alpha = 0.14f))
                            .padding(horizontal = 9.dp, vertical = 4.dp)
                    ) {
                        Text(sdk, color = AuraRose, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        if (app.detectedAnalyticsSdks.isNotEmpty()) {
            Text(
                text = "ANALYTICS SDKS (${app.detectedAnalyticsSdks.size})",
                color = AuraOrange,
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                app.detectedAnalyticsSdks.forEach { sdk ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(AuraOrange.copy(alpha = 0.14f))
                            .padding(horizontal = 9.dp, vertical = 4.dp)
                    ) {
                        Text(sdk, color = AuraOrange, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        if (app.telemetrySignals.isNotEmpty()) {
            Text(
                text = "BEHAVIOR SIGNALS",
                color = TextTertiary,
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(6.dp))
            app.telemetrySignals.forEach { sig ->
                Text(
                    text = "• $sig",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

private fun formatRelativeShortTime(timestamp: Long): String {
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
