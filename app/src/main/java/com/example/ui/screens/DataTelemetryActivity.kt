package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AdsClick
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import com.example.formatBytes
import com.example.service.AppTelemetryReport
import com.example.service.DeviceTelemetrySummary
import com.example.service.PrivacyAnalyzer
import com.example.ui.components.ExpandableSearchBar
import com.example.ui.components.SearchIconToggleButton
import com.example.ui.theme.AppTheme
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
import com.example.ui.theme.ThemeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DataTelemetryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeState.loadFromPrefs(this)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Obsidian) {
                    DataTelemetryScreen(onBack = { finish() })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DataTelemetryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var summary by remember { mutableStateOf<DeviceTelemetrySummary?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var includeSystemApps by remember { mutableStateOf(false) }
    var activeFilter by remember { mutableStateOf("ALL") } // "ALL", "HEAVY", "ADS", "ANALYTICS", "BOOT", "CLEAN"
    var searchQuery by remember { mutableStateOf("") }
    var selectedApp by remember { mutableStateOf<AppTelemetryReport?>(null) }
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
            PrivacyAnalyzer.analyzeAppTelemetryAndTrackers(context, includeSystemApps = includeSystemApps)
        }
        summary = result
        if (selectedApp != null) {
            selectedApp = result.appReports.find { it.packageName == selectedApp?.packageName }
        }
        isLoading = false
    }

    val currentSummary = summary

    val filteredApps = remember(currentSummary, activeFilter, searchQuery) {
        val list = currentSummary?.appReports ?: emptyList()
        val byFilter = when (activeFilter) {
            "HEAVY" -> list.filter { it.telemetryScore >= 45 }
            "ADS" -> list.filter { it.detectedAdNetworks.isNotEmpty() || it.usesAdId }
            "ANALYTICS" -> list.filter { it.detectedAnalyticsSdks.isNotEmpty() }
            "BOOT" -> list.filter { it.runsAtBoot && it.telemetryScore > 0 }
            "CLEAN" -> list.filter { it.telemetryScore == 0 }
            else -> list
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

    if (selectedApp != null) {
        val app = selectedApp!!
        ModalBottomSheet(
            onDismissRequest = { selectedApp = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = GlassPanel,
            contentColor = TextPrimary
        ) {
            TelemetryDetailSheet(
                app = app,
                onManageDataClick = {
                    val dataIntent = Intent(
                        Settings.ACTION_IGNORE_BACKGROUND_DATA_RESTRICTIONS_SETTINGS,
                        Uri.parse("package:${app.packageName}")
                    )
                    try {
                        context.startActivity(dataIntent)
                    } catch (_: Exception) {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", app.packageName, null)
                            )
                        )
                    }
                },
                onOpenAppSettings = {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", app.packageName, null)
                        )
                    )
                },
                onUninstallClick = {
                    context.startActivity(
                        Intent(
                            Intent.ACTION_DELETE,
                            Uri.fromParts("package", app.packageName, null)
                        )
                    )
                },
                onClose = { selectedApp = null }
            )
        }
    }

    var isSearchVisible by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Obsidian,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "AD & TELEMETRY RADAR",
                            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 3.sp, fontWeight = FontWeight.Bold),
                            color = AuraOrange
                        )
                        Text(
                            "Trackers, Ad Networks & Data Collectors",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                actions = {
                    SearchIconToggleButton(
                        isExpanded = isSearchVisible,
                        hasQuery = searchQuery.isNotEmpty(),
                        modifier = Modifier.padding(end = 6.dp),
                        onClick = {
                            isSearchVisible = !isSearchVisible
                            if (!isSearchVisible) searchQuery = ""
                        }
                    )
                    Box(
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .clip(RoundedCornerShape(100.dp))
                            .background(if (includeSystemApps) AuraPurple.copy(alpha = 0.2f) else GlassPanel.copy(alpha = 0.6f))
                            .border(1.dp, if (includeSystemApps) AuraPurple else GlassBorder, RoundedCornerShape(100.dp))
                            .clickable { includeSystemApps = !includeSystemApps }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (includeSystemApps) "All Apps" else "User Only",
                            color = if (includeSystemApps) AuraPurple else TextSecondary,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        if (isLoading || currentSummary == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = AuraOrange)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Scanning app SDKs, Ad IDs & network telemetry...", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 48.dp)
            ) {
                // Hero Summary Card
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            TelemetryStatItem("Trackers", "${currentSummary.totalTrackersDetected}", AuraRose)
                            TelemetryStatItem("Worst Apps", "${currentSummary.heavyCollectorsCount}", AuraOrange)
                            TelemetryStatItem("Uses Ad ID", "${currentSummary.adIdAppsCount}", AuraCyan)
                            TelemetryStatItem("Boot Sync", "${currentSummary.bootTrackersCount}", AuraPurple)
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = {
                                val adsIntent = Intent("com.google.android.gms.settings.ADS_PRIVACY")
                                try {
                                    context.startActivity(adsIntent)
                                } catch (_: Exception) {
                                    try {
                                        context.startActivity(Intent(Settings.ACTION_PRIVACY_SETTINGS))
                                    } catch (_: Exception) {
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().testTag("reset_ad_id_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = AuraOrange.copy(alpha = 0.2f)),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.AdsClick, contentDescription = null, tint = AuraOrange, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "DELETE / RESET ADVERTISING ID IN ANDROID",
                                color = AuraOrange,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }

                // Filter Chips
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val filters = listOf(
                            Triple("ALL", "Ranked (${currentSummary.appReports.size})", AuraCyan),
                            Triple("HEAVY", "Worst (${currentSummary.heavyCollectorsCount})", AuraRose),
                            Triple("ADS", "Ad Networks (${currentSummary.adIdAppsCount})", AuraOrange),
                            Triple("ANALYTICS", "Analytics", AuraPurple),
                            Triple("BOOT", "Auto-Start (${currentSummary.bootTrackersCount})", AuraOrange),
                            Triple("CLEAN", "Zero Trackers", AuraGreen)
                        )
                        filters.forEach { (key, label, tint) ->
                            val selected = activeFilter == key
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(100.dp))
                                    .background(if (selected) tint.copy(alpha = 0.2f) else GlassPanel.copy(alpha = 0.6f))
                                    .border(1.dp, if (selected) tint else GlassBorder, RoundedCornerShape(100.dp))
                                    .clickable { activeFilter = key }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = label,
                                    color = if (selected) tint else TextSecondary,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }
                }

                // Expandable Search Bar
                if (isSearchVisible || searchQuery.isNotEmpty()) {
                    item {
                        ExpandableSearchBar(
                            visible = true,
                            query = searchQuery,
                            onQueryChange = { searchQuery = it },
                            onClose = {
                                searchQuery = ""
                                isSearchVisible = false
                            },
                            placeholder = "Search apps or tracker SDKs..."
                        )
                    }
                }

                items(filteredApps, key = { it.packageName }) { app ->
                    TelemetryAppRow(
                        app = app,
                        onClick = { selectedApp = app }
                    )
                }
            }
        }
    }
}

@Composable
private fun TelemetryStatItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TelemetryAppRow(
    app: AppTelemetryReport,
    onClick: () -> Unit
) {
    val badgeColor = when (app.severityBadge) {
        "HEAVY" -> AuraRose
        "MODERATE" -> AuraOrange
        "LIGHT" -> AuraCyan
        else -> AuraGreen
    }
    val bmp = remember(app.packageName) { getCachedAppIconBitmap(app.packageName, app.icon) }
    val allTags = remember(app) {
        app.detectedAdNetworks + app.detectedAnalyticsSdks + app.telemetrySignals
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
                    bitmap = bmp,
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
                    val trafficStr = if (app.uploadedBytes > 0L || app.downloadedBytes > 0L) {
                        "↑ ${formatBytes(app.uploadedBytes)} • ↓ ${formatBytes(app.downloadedBytes)}"
                    } else {
                        "${app.detectedAdNetworks.size} Ad SDKs • ${app.detectedAnalyticsSdks.size} Trackers"
                    }
                    Text(
                        text = trafficStr,
                        color = TextTertiary,
                        style = MaterialTheme.typography.labelSmall
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(100.dp))
                        .background(badgeColor.copy(alpha = 0.14f))
                        .border(1.dp, badgeColor.copy(alpha = 0.4f), RoundedCornerShape(100.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (app.telemetryScore == 0) "CLEAN" else "${app.telemetryScore} PTS",
                        color = badgeColor,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }

            if (allTags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    allTags.take(4).forEach { tag ->
                        val isAd = tag in app.detectedAdNetworks || tag == "Reads Advertising ID"
                        val tagColor = if (isAd) AuraRose else AuraOrange
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(tagColor.copy(alpha = 0.12f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(tag, color = tagColor, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    if (allTags.size > 4) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(GlassBorder.copy(alpha = 0.45f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text("+${allTags.size - 4}", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TelemetryDetailSheet(
    app: AppTelemetryReport,
    onManageDataClick: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onUninstallClick: () -> Unit,
    onClose: () -> Unit
) {
    val bmp = remember(app.packageName) { getCachedAppIconBitmap(app.packageName, app.icon) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                bitmap = bmp,
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
                    text = "Collector Score: ${app.telemetryScore}/100 (${app.severityBadge})",
                    color = if (app.telemetryScore >= 45) AuraRose else AuraOrange,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                )
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Action Buttons: Restrict Background Data | App Info | Uninstall
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onManageDataClick,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = AuraCyan),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.DataUsage, contentDescription = null, tint = Obsidian, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "RESTRICT BACKGROUND DATA",
                    color = Obsidian,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                )
            }
            if (!app.isSystem) {
                OutlinedButton(
                    onClick = onUninstallClick,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = AuraRose, modifier = Modifier.size(16.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (app.uploadedBytes > 0L || app.downloadedBytes > 0L) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("UPLOADED", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                        Text(formatBytes(app.uploadedBytes), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), color = AuraRose)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("DOWNLOADED", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                        Text(formatBytes(app.downloadedBytes), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), color = AuraCyan)
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (app.detectedAdNetworks.isNotEmpty()) {
            Text("ADVERTISING NETWORKS (${app.detectedAdNetworks.size})", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = AuraRose)
            Spacer(modifier = Modifier.height(6.dp))
            app.detectedAdNetworks.forEach { ad ->
                SimpleTrackerRow(ad, "Serves targeted ads & profiles user behavior", AuraRose, onOpenAppSettings)
                Spacer(modifier = Modifier.height(6.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (app.detectedAnalyticsSdks.isNotEmpty()) {
            Text("TELEMETRY & ANALYTICS SDKS (${app.detectedAnalyticsSdks.size})", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = AuraOrange)
            Spacer(modifier = Modifier.height(6.dp))
            app.detectedAnalyticsSdks.forEach { sdk ->
                SimpleTrackerRow(sdk, "Logs in-app events, crashes & session usage", AuraOrange, onOpenAppSettings)
                Spacer(modifier = Modifier.height(6.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (app.telemetrySignals.isNotEmpty()) {
            Text("COLLECTION SIGNALS", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = AuraCyan)
            Spacer(modifier = Modifier.height(6.dp))
            app.telemetrySignals.forEach { sig ->
                SimpleTrackerRow(sig, "Tap to open App Settings & restrict", AuraCyan, onOpenAppSettings)
                Spacer(modifier = Modifier.height(6.dp))
            }
        }

        if (app.detectedAdNetworks.isEmpty() && app.detectedAnalyticsSdks.isEmpty() && app.telemetrySignals.isEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AuraGreen)
                Spacer(modifier = Modifier.width(8.dp))
                Text("No known ad networks or telemetry trackers detected.", color = AuraGreen, style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
private fun SimpleTrackerRow(
    title: String,
    subtitle: String,
    color: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(GlassPanel.copy(alpha = 0.7f))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Analytics, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextPrimary, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
            Text(subtitle, color = TextSecondary, style = MaterialTheme.typography.labelSmall)
        }
        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
    }
}
