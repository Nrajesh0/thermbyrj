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
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.GlassCard
import com.example.service.AppPrivacyReport
import com.example.service.AppRefreshCoordinator
import com.example.service.AppTelemetryReport
import com.example.service.DevicePrivacySummary
import com.example.service.DeviceTelemetrySummary
import com.example.service.PermissionCategory
import com.example.service.PermissionRiskLevel
import com.example.service.PlainPermissionInfo
import com.example.service.PrivacyAnalyzer
import com.example.service.PrivacyChangeEvent
import com.example.service.PrivacyChangeType
import com.example.ui.components.ExpandableSearchBar
import com.example.ui.components.SearchIconToggleButton
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
    var isTelemetryLoading by remember { mutableStateOf(false) }

    // Sub-tab filter & sort states
    var activeAppFilter by remember { mutableStateOf("ALL") }
    var appSortOption by remember { mutableStateOf("RISK") } // "RISK", "NEW_TO_OLD", "OLD_TO_NEW", "NAME"
    var showSortMenu by remember { mutableStateOf(false) }
    var activeChangeFilter by remember { mutableStateOf("ALL") } // "ALL", "REVOKED", "ADDED", "NEW_APP"
    var activeTrackerFilter by remember { mutableStateOf("ALL") } // "ALL", "HEAVY", "AD_ID", "BOOT"
    var searchQuery by remember { mutableStateOf("") }

    // Bottom sheets
    var selectedAppForModal by remember { mutableStateOf<AppPrivacyReport?>(null) }
    var selectedTrackerApp by remember { mutableStateOf<AppTelemetryReport?>(null) }
    var showPermissionGuide by remember { mutableStateOf(false) }
    var refreshTrigger by remember { mutableIntStateOf(0) }
    val globalRefreshVersion by AppRefreshCoordinator.refreshVersion.collectAsStateWithLifecycle()

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshTrigger++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(refreshTrigger, globalRefreshVersion, includeSystemApps) {
        isLoading = summary == null
        val forceFresh = refreshTrigger > 1 || globalRefreshVersion > 0
        val (privResult, changes) = withContext(Dispatchers.IO) {
            val priv = PrivacyAnalyzer.analyzeDevicePrivacy(
                context,
                includeSystemApps = includeSystemApps,
                forceRefresh = forceFresh
            )
            val chg = PrivacyAnalyzer.getPrivacyChanges(
                context = context,
                currentReports = priv.appReports,
                dispatchNotifications = true
            )
            priv to chg
        }
        summary = privResult
        changeEvents = changes
        if (selectedAppForModal != null) {
            selectedAppForModal = privResult.appReports.find { r -> r.packageName == selectedAppForModal?.packageName }
        }
        isLoading = false
    }

    // Load Ad/Tracker Telemetry lazily when the user actually visits TRACKERS (or refreshes an already-loaded telemetry snapshot)
    LaunchedEffect(activeSubTab, refreshTrigger, globalRefreshVersion, includeSystemApps) {
        if (activeSubTab != "TRACKERS" && telemetrySummary == null) return@LaunchedEffect
        if (telemetrySummary == null) {
            isTelemetryLoading = true
        }
        val forceFresh = refreshTrigger > 1 || globalRefreshVersion > 0
        val telemResult = withContext(Dispatchers.IO) {
            PrivacyAnalyzer.analyzeAppTelemetryAndTrackers(
                context,
                includeSystemApps = includeSystemApps,
                forceRefresh = forceFresh
            )
        }
        telemetrySummary = telemResult
        if (selectedTrackerApp != null) {
            selectedTrackerApp = telemResult.appReports.find { r -> r.packageName == selectedTrackerApp?.packageName }
        }
        isTelemetryLoading = false
    }

    val currentSummary = summary
    val currentTelemetry = telemetrySummary

    // Filtered & Sorted Apps for APPS tab
    val filteredApps = remember(currentSummary, activeAppFilter, appSortOption, searchQuery) {
        val list = currentSummary?.appReports ?: emptyList()
        val byFilter = when (activeAppFilter) {
            "ALL" -> list
            "HIGH_RISK" -> list.filter { it.riskScore >= 35 }
            "THREATS" -> list.filter { it.threatSignals.isNotEmpty() || it.isHiddenStealthApp || it.isImpersonatingSystemOrBrand || it.hasUntrustedOrDebugCert }
            "SIDELOADED" -> list.filter { it.isSideloaded }
            else -> {
                val cat = PermissionCategory.entries.find { it.name == activeAppFilter }
                if (cat != null) list.filter { app -> app.grantedPermissions.any { it.category == cat } } else list
            }
        }
        val bySearch = if (searchQuery.isBlank()) {
            byFilter
        } else {
            val q = searchQuery.trim()
            byFilter.filter {
                it.appName.contains(q, ignoreCase = true) ||
                    it.packageName.contains(q, ignoreCase = true) ||
                    it.certSha256.contains(q, ignoreCase = true) ||
                    it.threatSignals.any { s -> s.title.contains(q, ignoreCase = true) || s.description.contains(q, ignoreCase = true) } ||
                    it.grantedPermissions.any { p -> p.shortTag.contains(q, ignoreCase = true) || p.friendlyTitle.contains(q, ignoreCase = true) }
            }
        }
        when (appSortOption) {
            "NEW_TO_OLD" -> bySearch.sortedWith(
                compareByDescending<AppPrivacyReport> { it.firstInstallTimeMs.takeIf { ts -> ts > 0L } ?: it.lastUpdateTimeMs }
                    .thenByDescending { it.riskScore }
                    .thenBy { it.appName.lowercase() }
            )
            "OLD_TO_NEW" -> bySearch.sortedWith(
                compareBy<AppPrivacyReport> {
                    val ts = it.firstInstallTimeMs.takeIf { t -> t > 0L } ?: it.lastUpdateTimeMs
                    if (ts > 0L) ts else Long.MAX_VALUE
                }
                    .thenByDescending { it.riskScore }
                    .thenBy { it.appName.lowercase() }
            )
            "NAME" -> bySearch.sortedBy { it.appName.lowercase() }
            else -> bySearch
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
    selectedAppForModal?.let { app ->
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
                    try {
                        context.startActivity(
                            Intent(Intent.ACTION_DELETE, Uri.fromParts("package", app.packageName, null))
                        )
                    } catch (_: Throwable) {
                    }
                },
                onClose = { selectedAppForModal = null }
            )
        }
    }

    // Tracker Inspection Bottom Sheet
    selectedTrackerApp?.let { app ->
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
                        try {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", app.packageName, null)
                                )
                            )
                        } catch (_: Throwable) {
                        }
                    }
                },
                onClose = { selectedTrackerApp = null }
            )
        }
    }

    if (showPermissionGuide) {
        ModalBottomSheet(
            onDismissRequest = { showPermissionGuide = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = GlassPanel,
            contentColor = TextPrimary
        ) {
            PermissionGuideSheet(onClose = { showPermissionGuide = false })
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(14.dp))

        var isSearchVisible by remember { mutableStateOf(false) }

        // Header with inline Privacy Score & Compact Actions
        Row(
            modifier = Modifier.fillMaxWidth().padding(end = 46.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "PRIVACY",
                    style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 2.5.sp, fontWeight = FontWeight.Bold),
                    color = AuraGreen
                )
                if (currentSummary != null) {
                    Spacer(modifier = Modifier.width(8.dp))
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
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${currentSummary.privacyScore}/100",
                            color = scoreColor,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }

            // Compact Search + Sort + Guide Icon Buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SearchIconToggleButton(
                    isExpanded = isSearchVisible,
                    hasQuery = searchQuery.isNotEmpty(),
                    onClick = {
                        isSearchVisible = !isSearchVisible
                        if (!isSearchVisible) searchQuery = ""
                    }
                )

                Box {
                    val isCustomSort = appSortOption != "RISK"
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("privacy_sort_button")
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isCustomSort) AuraCyan.copy(alpha = 0.16f) else GlassPanel.copy(alpha = 0.7f))
                            .border(1.dp, if (isCustomSort) AuraCyan else GlassBorder, RoundedCornerShape(12.dp))
                            .clickable { showSortMenu = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Sort,
                            contentDescription = "Sort Apps",
                            tint = if (isCustomSort) AuraCyan else TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false },
                        modifier = Modifier.background(GlassPanel)
                    ) {
                        listOf(
                            "RISK" to "Sort by Risk (High to Low)",
                            "NEW_TO_OLD" to "Sort by Install Time (New to Old)",
                            "OLD_TO_NEW" to "Sort by Install Time (Old to New)",
                            "NAME" to "Sort by Name (A-Z)"
                        ).forEach { (key, label) ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = label,
                                        color = if (appSortOption == key) AuraCyan else TextPrimary,
                                        fontWeight = if (appSortOption == key) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                onClick = {
                                    appSortOption = key
                                    showSortMenu = false
                                }
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .testTag("privacy_guide_button")
                        .clip(RoundedCornerShape(12.dp))
                        .background(GlassPanel.copy(alpha = 0.7f))
                        .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
                        .clickable { showPermissionGuide = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = "Permission Guide",
                        tint = AuraGreen,
                        modifier = Modifier.size(18.dp)
                    )
                }
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

        val hint = when (activeSubTab) {
            "CHANGES" -> "Search permission changes..."
            "TRACKERS" -> "Search apps or tracker SDKs..."
            else -> "Search apps or permissions..."
        }
        ExpandableSearchBar(
            visible = isSearchVisible || searchQuery.isNotEmpty(),
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            onClose = {
                searchQuery = ""
                isSearchVisible = false
            },
            placeholder = hint,
            modifier = Modifier.padding(top = 8.dp)
        )

        Spacer(modifier = Modifier.height(10.dp))

        if (isLoading || currentSummary == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AuraGreen, strokeWidth = 2.dp)
            }
        } else {
            when (activeSubTab) {
                "APPS" -> {
                    // Single horizontal filter pill strip (including System Apps toggle)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val sideloadCount = currentSummary.appReports.count { it.isSideloaded }
                        val pills = listOf(
                            Triple("ALL", "All (${currentSummary.appReports.size})", AuraGreen),
                            Triple("HIGH_RISK", "High Risk (${currentSummary.highRiskAppCount})", AuraRose),
                            Triple("THREATS", "Threats (${currentSummary.threatFlaggedAppCount})", AuraRose),
                            Triple("SIDELOADED", "Sideloaded ($sideloadCount)", AuraOrange),
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
                        FilterPill(
                            label = if (includeSystemApps) "System: ON" else "System: OFF",
                            selected = includeSystemApps,
                            tint = AuraCyan,
                            onClick = { includeSystemApps = !includeSystemApps }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
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

                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
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
                    if (isTelemetryLoading && telem == null) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = AuraPurple, strokeWidth = 2.dp)
                        }
                    } else {
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
                                .heightIn(min = 36.dp)
                                .testTag("privacy_ad_settings_button")
                                .clip(RoundedCornerShape(100.dp))
                                .background(AuraPurple.copy(alpha = 0.14f))
                                .border(1.dp, AuraPurple.copy(alpha = 0.35f), RoundedCornerShape(100.dp))
                                .clickable {
                                    try {
                                        context.startActivity(Intent("com.google.android.gms.settings.ADS_PRIVACY"))
                                    } catch (_: Throwable) {
                                        try {
                                            context.startActivity(Intent(Settings.ACTION_PRIVACY_SETTINGS))
                                        } catch (_: Throwable) {
                                        }
                                    }
                                }
                                .padding(horizontal = 11.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Ad Settings",
                                color = AuraPurple,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                        contentPadding = PaddingValues(bottom = 112.dp)
                    ) {
                        item(key = "telemetry_radar_banner") {
                            GlassCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("open_data_telemetry_button")
                                    .clickable {
                                        try {
                                            context.startActivity(Intent(context, DataTelemetryActivity::class.java))
                                        } catch (_: Throwable) {
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(AuraOrange.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.DataUsage,
                                            contentDescription = "Data Usage & Telemetry Radar",
                                            tint = AuraOrange,
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Full Data & Tracker Breakdown",
                                            color = TextPrimary,
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                                        )
                                        Text(
                                            text = "Inspect upload/download bytes & 6 telemetry filters",
                                            color = TextSecondary,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                    Icon(
                                        Icons.Default.ChevronRight,
                                        contentDescription = "Open",
                                        tint = AuraOrange,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

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

    val hasChips = app.threatSignals.isNotEmpty() || app.grantedPermissions.isNotEmpty()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .background(GlassPanel.copy(alpha = 0.62f))
            .border(
                1.dp,
                if (app.threatSignals.any { it.isCritical }) AuraRose.copy(alpha = 0.45f) else GlassBorder,
                RoundedCornerShape(13.dp)
            )
            .clickable { onCardClick() }
            .padding(horizontal = 12.dp, vertical = 9.dp)
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
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.appName,
                    color = TextPrimary,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val installTime = app.firstInstallTimeMs.takeIf { it > 0L } ?: app.lastUpdateTimeMs
                val subtitleText = buildString {
                    append(app.installerName)
                    if (installTime > 0L) {
                        append(" • ")
                        append(formatRelativeShortTime(installTime))
                    } else if (app.lastTimeUsedMs > 0L) {
                        append(" • Used ")
                        append(formatRelativeShortTime(app.lastTimeUsedMs))
                    }
                }
                Text(
                    text = subtitleText,
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
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                )
            }
        }

        if (hasChips) {
            Spacer(modifier = Modifier.height(6.dp))
            val maxTotalChips = if (app.threatSignals.isNotEmpty()) 3 else 4
            val shownThreats = app.threatSignals.take(1)
            val remainingSlots = (maxTotalChips - shownThreats.size).coerceAtLeast(1)
            val shownPerms = app.grantedPermissions.take(remainingSlots)
            val hiddenCount = (app.threatSignals.size - shownThreats.size) +
                (app.grantedPermissions.size - shownPerms.size)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                shownThreats.forEach { sig ->
                    val sigColor = if (sig.isCritical) AuraRose else AuraOrange
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(sigColor.copy(alpha = 0.16f))
                            .border(1.dp, sigColor.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                            .clickable { onCardClick() }
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "⚠ ${sig.shortChip}",
                            color = sigColor,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                            maxLines = 1
                        )
                    }
                }
                shownPerms.forEach { perm ->
                    val pColor = when (perm.riskLevel) {
                        PermissionRiskLevel.CRITICAL -> AuraRose
                        PermissionRiskLevel.HIGH -> AuraOrange
                        PermissionRiskLevel.MEDIUM -> AuraCyan
                        PermissionRiskLevel.LOW -> AuraGreen
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(pColor.copy(alpha = 0.11f))
                            .border(1.dp, pColor.copy(alpha = 0.28f), RoundedCornerShape(6.dp))
                            .clickable { onPermissionChipClick(perm) }
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = perm.shortTag,
                            color = pColor,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                            maxLines = 1
                        )
                    }
                }
                if (hiddenCount > 0) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(GlassBorder.copy(alpha = 0.35f))
                            .border(1.dp, GlassBorder, RoundedCornerShape(6.dp))
                            .clickable { onCardClick() }
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "+$hiddenCount",
                            color = TextSecondary,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold),
                            maxLines = 1
                        )
                    }
                }
            }
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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .background(GlassPanel.copy(alpha = 0.62f))
            .border(1.dp, GlassBorder, RoundedCornerShape(13.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        }

        Spacer(modifier = Modifier.width(10.dp))

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
                    style = MaterialTheme.typography.labelSmall
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
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
            )
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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .background(GlassPanel.copy(alpha = 0.62f))
            .border(1.dp, GlassBorder, RoundedCornerShape(13.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val bmp = remember(report.packageName) { getCachedAppIconBitmap(report.packageName, report.icon) }
        Image(
            bitmap = bmp,
            contentDescription = report.appName,
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
        )

        Spacer(modifier = Modifier.width(10.dp))

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

        if (app.threatSignals.isNotEmpty()) {
            Text(
                text = "OFFLINE THREAT & STALKERWARE SIGNALS (${app.threatSignals.size})",
                color = AuraRose,
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(8.dp))
            app.threatSignals.forEach { sig ->
                val sigColor = if (sig.isCritical) AuraRose else AuraOrange
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(sigColor.copy(alpha = 0.12f))
                        .border(1.dp, sigColor.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = sig.title,
                            color = sigColor,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.weight(1f)
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(sigColor.copy(alpha = 0.2f))
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = sig.category.label,
                                color = sigColor,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = sig.description,
                        color = TextPrimary,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        if (app.certSha256.isNotBlank()) {
            val clipContext = LocalContext.current
            val certTint = if (app.hasUntrustedOrDebugCert || app.isDebuggableApk) AuraOrange else AuraCyan
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Obsidian.copy(alpha = 0.45f))
                    .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
                    .clickable {
                        try {
                            val cm = clipContext.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                            cm?.setPrimaryClip(
                                android.content.ClipData.newPlainText(
                                    "APK SHA-256",
                                    "${app.packageName}\nSHA-256: ${app.certSha256}"
                                )
                            )
                            android.widget.Toast.makeText(
                                clipContext,
                                "Copied APK SHA-256 fingerprint",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        } catch (_: Exception) {
                        }
                    }
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "APK SIGNING CERTIFICATE (TAP TO COPY)",
                        color = certTint,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = when {
                            app.hasUntrustedOrDebugCert -> "TEST / DEBUG KEY"
                            app.isDebuggableApk -> "DEBUGGABLE APK"
                            else -> "PRODUCTION CERT"
                        },
                        color = if (app.hasUntrustedOrDebugCert || app.isDebuggableApk) AuraOrange else AuraGreen,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Signer: ${app.certSignerSummary.ifBlank { "Standard X.509" }} • Entry Point: ${if (app.hasLauncherIcon) "Visible" else "Hidden (Headless)"}",
                    color = TextSecondary,
                    style = MaterialTheme.typography.labelSmall
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "SHA-256: ${app.certSha256}",
                    color = TextTertiary,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

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
                        .background(GlassBorder.copy(alpha = 0.22f))
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

@Composable
private fun PermissionGuideSheet(onClose: () -> Unit) {
    val dictionary = remember { PrivacyAnalyzer.getPlainDictionary() }
    var query by remember { mutableStateOf("") }
    val filtered = remember(dictionary, query) {
        if (query.isBlank()) {
            dictionary
        } else {
            val q = query.trim()
            dictionary.filter {
                it.friendlyTitle.contains(q, ignoreCase = true) ||
                    it.shortTag.contains(q, ignoreCase = true) ||
                    it.simpleExplanation.contains(q, ignoreCase = true) ||
                    it.whyItMatters.contains(q, ignoreCase = true)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "PERMISSION GUIDE",
                    color = AuraGreen,
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "Plain-English explanation of what apps can access",
                    color = TextSecondary,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search permissions (e.g., Location, SMS)...", color = TextTertiary, style = MaterialTheme.typography.bodySmall) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = AuraGreen, modifier = Modifier.size(18.dp)) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextSecondary, modifier = Modifier.size(16.dp))
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AuraGreen,
                unfocusedBorderColor = GlassBorder,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(10.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .height(380.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(filtered, key = { it.permissionName }) { info ->
                val riskColor = when (info.riskLevel) {
                    PermissionRiskLevel.CRITICAL -> AuraRose
                    PermissionRiskLevel.HIGH -> AuraOrange
                    PermissionRiskLevel.MEDIUM -> AuraCyan
                    PermissionRiskLevel.LOW -> AuraGreen
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Obsidian.copy(alpha = 0.45f))
                        .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = info.friendlyTitle,
                            color = TextPrimary,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(riskColor.copy(alpha = 0.15f))
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = info.riskLevel.name,
                                color = riskColor,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = info.simpleExplanation,
                        color = TextSecondary,
                        style = MaterialTheme.typography.labelSmall
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tip: ${info.whyItMatters}",
                        color = riskColor,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)
                    )
                }
            }
        }
    }
}
