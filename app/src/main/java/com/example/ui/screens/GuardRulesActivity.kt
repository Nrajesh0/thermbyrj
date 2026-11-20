package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
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
import com.example.service.DiscoveredSystemSensorApp
import com.example.service.GuardAlertMode
import com.example.service.GuardSensorType
import com.example.service.GuardSystemAppDiscovery
import com.example.service.LiveGuardMonitor
import com.example.service.PrivacyAnalyzer
import com.example.service.SnoozeRule
import com.example.service.TrustedAppRule
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GuardRulesActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeState.loadFromPrefs(this)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Obsidian) {
                    GuardRulesScreen(onBack = { finish() })
                }
            }
        }
    }
}

private data class InstalledAppEntry(
    val packageName: String,
    val appName: String,
    val icon: Drawable?
)

private fun hasGuardNotificationPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
}

private fun openGuardNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    }
    try {
        context.startActivity(intent)
    } catch (_: Throwable) {
        try {
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null)
                )
            )
        } catch (_: Throwable) {
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GuardRulesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val guardMonitor = remember(context) { LiveGuardMonitor.getInstance(context) }
    val prefs = remember(context) { context.getSharedPreferences("thermal_prefs", Context.MODE_PRIVATE) }

    val rulesConfig by guardMonitor.rulesConfig.collectAsStateWithLifecycle()
    val trustedRules by guardMonitor.trustedRules.collectAsStateWithLifecycle()
    val activeSnoozes by guardMonitor.activeSnoozes.collectAsStateWithLifecycle()

    var hasUsageAccess by remember { mutableStateOf(PrivacyAnalyzer.hasUsageStatsPermission(context)) }
    var bgMonitoringEnabled by remember { mutableStateOf(prefs.getBoolean("background_monitoring", true)) }
    var liveGuardAlertsEnabled by remember { mutableStateOf(prefs.getBoolean("live_guard_alerts", true)) }
    var hasNotificationPermission by remember { mutableStateOf(hasGuardNotificationPermission(context)) }

    val notificationPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted
        if (!granted) {
            openGuardNotificationSettings(context)
        }
    }

    var discoveredSystemApps by remember { mutableStateOf<List<DiscoveredSystemSensorApp>>(emptyList()) }
    var showAddAppSheet by remember { mutableStateOf(false) }
    var installedUserApps by remember { mutableStateOf<List<InstalledAppEntry>>(emptyList()) }
    var refreshTrigger by remember { mutableIntStateOf(0) }

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
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasUsageAccess = PrivacyAnalyzer.hasUsageStatsPermission(context)
                hasNotificationPermission = hasGuardNotificationPermission(context)
                bgMonitoringEnabled = prefs.getBoolean("background_monitoring", true)
                liveGuardAlertsEnabled = prefs.getBoolean("live_guard_alerts", true)
                refreshTrigger++
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    LaunchedEffect(refreshTrigger) {
        discoveredSystemApps = withContext(Dispatchers.IO) {
            GuardSystemAppDiscovery.discoverSystemSensorApps(context)
        }
    }

    LaunchedEffect(showAddAppSheet) {
        if (showAddAppSheet && installedUserApps.isEmpty()) {
            installedUserApps = withContext(Dispatchers.IO) {
                val pm = context.packageManager
                try {
                    pm.getInstalledApplications(0)
                        .filter {
                            it.packageName != context.packageName &&
                                ((it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 ||
                                    (it.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0)
                        }
                        .map { ai ->
                            InstalledAppEntry(
                                packageName = ai.packageName,
                                appName = pm.getApplicationLabel(ai).toString(),
                                icon = try { pm.getApplicationIcon(ai) } catch (_: Exception) { null }
                            )
                        }
                        .sortedBy { it.appName.lowercase() }
                } catch (_: Exception) {
                    emptyList()
                }
            }
        }
    }

    if (showAddAppSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAddAppSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = GlassPanel,
            contentColor = TextPrimary
        ) {
            AddAppRuleSheet(
                apps = installedUserApps,
                alreadyTrusted = trustedRules.keys,
                onSelectApp = { app ->
                    guardMonitor.setAppTrust(
                        packageName = app.packageName,
                        sensors = setOf(GuardSensorType.MIC, GuardSensorType.CAMERA),
                        source = "Manual rule"
                    )
                    showAddAppSheet = false
                    Toast.makeText(context, "Trusted ${app.appName} (Mic & Camera)", Toast.LENGTH_SHORT).show()
                },
                onClose = { showAddAppSheet = false }
            )
        }
    }

    Scaffold(
        containerColor = Obsidian,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "GUARD RULES & ALERTS",
                            style = MaterialTheme.typography.labelLarge.copy(
                                letterSpacing = 2.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = AuraCyan
                        )
                        Text(
                            text = "Control call, foreground & per-app alerts",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("guard_rules_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Obsidian)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 36.dp)
        ) {
            if (!bgMonitoringEnabled || !liveGuardAlertsEnabled || !hasNotificationPermission) {
                item(key = "guard_rules_status_warning") {
                    val (title, subtitle, buttonLabel, tint) = when {
                        !bgMonitoringEnabled -> listOf("Background Guard Paused", "Turn on background monitoring so Guard rules run continuously.", "Turn On") to AuraRose
                        !liveGuardAlertsEnabled -> listOf("Guard Alerts Muted", "Live alerts are muted in System Preferences.", "Unmute") to AuraOrange
                        else -> listOf("Notifications Blocked", "Grant notification permission to receive Guard alerts.", "Allow") to AuraRose
                    }.let { (texts, color) ->
                        GuardStatusTuple(texts[0], texts[1], texts[2], color)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("guard_rules_status_banner")
                            .clip(RoundedCornerShape(16.dp))
                            .background(tint.copy(alpha = 0.12f))
                            .border(1.dp, tint.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(title, color = tint, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                            Text(subtitle, color = TextSecondary, style = MaterialTheme.typography.labelSmall)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .heightIn(min = 36.dp)
                                .clip(RoundedCornerShape(100.dp))
                                .background(tint)
                                .clickable {
                                    when {
                                        !bgMonitoringEnabled -> {
                                            prefs.edit().putBoolean("background_monitoring", true).commit()
                                            startTrackingServiceSafely(context)
                                        }
                                        !liveGuardAlertsEnabled -> {
                                            prefs.edit().putBoolean("live_guard_alerts", true).commit()
                                        }
                                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                                            try {
                                                notificationPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                            } catch (_: Throwable) {
                                                openGuardNotificationSettings(context)
                                            }
                                        }
                                        else -> openGuardNotificationSettings(context)
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(buttonLabel, color = Obsidian, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                }
            }

            // 1. Alert Mode Section
            item(key = "header_mode") {
                SectionLabel("ALERT MODE")
            }

            items(GuardAlertMode.entries, key = { it.name }) { mode ->
                val selected = rulesConfig.alertMode == mode
                val tint = when (mode) {
                    GuardAlertMode.SMART_COVERT -> AuraCyan
                    GuardAlertMode.ALL_ACTIVITY -> AuraOrange
                    GuardAlertMode.SILENT_LOG -> AuraPurple
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("guard_mode_${mode.name.lowercase()}")
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (selected) tint.copy(alpha = 0.12f) else GlassPanel.copy(alpha = 0.65f))
                        .border(1.dp, if (selected) tint else GlassBorder, RoundedCornerShape(16.dp))
                        .clickable {
                            guardMonitor.updateRulesConfig(rulesConfig.copy(alertMode = mode))
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = mode.title,
                        tint = if (selected) tint else TextTertiary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = mode.title,
                            color = if (selected) tint else TextPrimary,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = mode.subtitle,
                            color = TextSecondary,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }

            // 2. Smart Filters Section
            item(key = "header_filters") {
                Spacer(modifier = Modifier.height(4.dp))
                SectionLabel("SMART FILTERS")
            }

            item(key = "smart_filters_card") {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    FilterToggleRow(
                        title = "1.8s Activation Debounce",
                        subtitle = "Skip brief face-unlock flashes & quick probe taps",
                        checked = rulesConfig.debounceEnabled,
                        onCheckedChange = {
                            guardMonitor.updateRulesConfig(rulesConfig.copy(debounceEnabled = it))
                        }
                    )
                    HorizontalDivider(color = GlassBorder, modifier = Modifier.padding(vertical = 8.dp))
                    FilterToggleRow(
                        title = "Silence During Calls",
                        subtitle = "Mute Mic/Cam buzz during Phone & VoIP calls (still logged)",
                        checked = rulesConfig.silenceDuringCalls,
                        onCheckedChange = {
                            guardMonitor.updateRulesConfig(rulesConfig.copy(silenceDuringCalls = it))
                        }
                    )
                    HorizontalDivider(color = GlassBorder, modifier = Modifier.padding(vertical = 8.dp))
                    FilterToggleRow(
                        title = "Quiet for Open Apps",
                        subtitle = if (hasUsageAccess) {
                            "Skip alerts when you actively have the app open on screen"
                        } else {
                            "Grant Usage Access so dot knows which app is on screen"
                        },
                        checked = rulesConfig.quietForForegroundApps,
                        onCheckedChange = {
                            guardMonitor.updateRulesConfig(rulesConfig.copy(quietForForegroundApps = it))
                        }
                    )
                    if (!hasUsageAccess && rulesConfig.quietForForegroundApps) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(AuraOrange.copy(alpha = 0.16f))
                                .border(1.dp, AuraOrange.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .clickable {
                                    try {
                                        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                                    } catch (_: Throwable) {
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "Grant Usage Access",
                                color = AuraOrange,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                    HorizontalDivider(color = GlassBorder, modifier = Modifier.padding(vertical = 8.dp))
                    FilterToggleRow(
                        title = "Screen-Off Battery Saver",
                        subtitle = "Pause periodic polling when locked; keep zero-CPU OS callbacks",
                        checked = rulesConfig.pausePollingScreenOff,
                        onCheckedChange = {
                            guardMonitor.updateRulesConfig(rulesConfig.copy(pausePollingScreenOff = it))
                        }
                    )
                }
            }

            // 3. Active Snoozes Section
            item(key = "header_snoozes") {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionLabel("TEMPORARY SNOOZE (1H)")
                    if (activeSnoozes.isNotEmpty()) {
                        TextButton(onClick = { guardMonitor.clearAllSnoozes() }) {
                            Text("Resume All", color = AuraCyan, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                }
            }

            item(key = "snooze_quick_row") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuickActionPill("Snooze All 1h", Icons.Default.NotificationsOff, AuraPurple) {
                        guardMonitor.snoozeTarget("ALL", "All Guard Alerts", 3_600_000L)
                    }
                    QuickActionPill("Snooze Mic 1h", Icons.Default.PhoneInTalk, AuraOrange) {
                        guardMonitor.snoozeTarget("SENSOR:MIC", "All Mic Alerts", 3_600_000L)
                    }
                    QuickActionPill("Snooze Cam 1h", Icons.Default.Timer, AuraCyan) {
                        guardMonitor.snoozeTarget("SENSOR:CAMERA", "All Camera Alerts", 3_600_000L)
                    }
                }
            }

            items(activeSnoozes, key = { "snooze_${it.targetKey}" }) { snooze ->
                SnoozeRuleRow(
                    snooze = snooze,
                    onResume = { guardMonitor.cancelSnooze(snooze.targetKey) }
                )
            }

            // 4. Trusted & Ignored Apps Section
            item(key = "header_trusted") {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionLabel("TRUSTED & IGNORED APPS (${trustedRules.size})")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (trustedRules.isNotEmpty()) {
                            TextButton(onClick = { guardMonitor.clearIgnoredPackages() }) {
                                Text("Clear All", color = AuraRose, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                            }
                        }
                        Box(
                            modifier = Modifier
                                .testTag("guard_add_app_rule_btn")
                                .clip(RoundedCornerShape(100.dp))
                                .background(AuraCyan.copy(alpha = 0.16f))
                                .border(1.dp, AuraCyan.copy(alpha = 0.4f), RoundedCornerShape(100.dp))
                                .clickable { showAddAppSheet = true }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Add, contentDescription = "Add App", tint = AuraCyan, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Add App", color = AuraCyan, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                            }
                        }
                    }
                }
            }

            if (trustedRules.isEmpty()) {
                item(key = "empty_trusted") {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "No apps trusted yet. Add WhatsApp, Recorder, or trusted apps here—or tap Trust App on any alert notification.",
                            color = TextSecondary,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            } else {
                items(
                    items = trustedRules.values.sortedByDescending { it.addedAtMs },
                    key = { "rule_${it.packageName}" }
                ) { rule ->
                    TrustedAppRuleCard(
                        rule = rule,
                        onUpdateSensors = { updated ->
                            guardMonitor.setAppTrust(rule.packageName, updated, rule.source)
                        },
                        onRemove = {
                            guardMonitor.removeAppTrust(rule.packageName)
                            Toast.makeText(context, "Alerts re-enabled", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }

            // 5. Dynamic System Call & Recorder Apps Section
            if (discoveredSystemApps.isNotEmpty()) {
                item(key = "header_system_apps") {
                    Spacer(modifier = Modifier.height(4.dp))
                    SectionLabel("BUILT-IN CALL & RECORDER APPS")
                }

                item(key = "system_apps_card") {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Detected via OS default Dialer & FLAG_SYSTEM roles on this device. Toggle to silence routine built-in alerts:",
                            color = TextSecondary,
                            style = MaterialTheme.typography.labelSmall
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        discoveredSystemApps.forEachIndexed { idx, sysApp ->
                            if (idx > 0) {
                                HorizontalDivider(color = GlassBorder, modifier = Modifier.padding(vertical = 6.dp))
                            }
                            val isTrusted = trustedRules.containsKey(sysApp.packageName)
                            SystemAppTrustRow(
                                app = sysApp,
                                isTrusted = isTrusted,
                                onToggle = { checked ->
                                    if (checked) {
                                        guardMonitor.setAppTrust(
                                            packageName = sysApp.packageName,
                                            sensors = sysApp.recommendedSensors,
                                            source = sysApp.roleLabel
                                        )
                                    } else {
                                        guardMonitor.removeAppTrust(sysApp.packageName)
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

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = TextTertiary,
        style = MaterialTheme.typography.labelSmall.copy(
            fontSize = 10.sp,
            letterSpacing = 1.6.sp,
            fontWeight = FontWeight.Bold
        )
    )
}

@Composable
private fun FilterToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = TextPrimary,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = subtitle,
                color = TextSecondary,
                style = MaterialTheme.typography.labelSmall
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Obsidian,
                checkedTrackColor = AuraCyan,
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = GlassPanel
            )
        )
    }
}

@Composable
private fun QuickActionPill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .background(tint.copy(alpha = 0.14f))
            .border(1.dp, tint.copy(alpha = 0.35f), RoundedCornerShape(100.dp))
            .clickable { onClick() }
            .padding(horizontal = 11.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = label,
            color = tint,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
        )
    }
}

@Composable
private fun SnoozeRuleRow(
    snooze: SnoozeRule,
    onResume: () -> Unit
) {
    val minsLeft = ((snooze.expiresAtMs - System.currentTimeMillis()).coerceAtLeast(0L) / 60_000L) + 1L
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AuraPurple.copy(alpha = 0.10f))
            .border(1.dp, AuraPurple.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Timer, contentDescription = null, tint = AuraPurple, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = snooze.label,
                color = TextPrimary,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${minsLeft}m remaining",
                color = AuraPurple,
                style = MaterialTheme.typography.labelSmall
            )
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(100.dp))
                .background(AuraPurple.copy(alpha = 0.18f))
                .clickable { onResume() }
                .padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Text("Resume", color = AuraPurple, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TrustedAppRuleCard(
    rule: TrustedAppRule,
    onUpdateSensors: (Set<GuardSensorType>) -> Unit,
    onRemove: () -> Unit
) {
    val context = LocalContext.current
    val pm = context.packageManager
    val (appName, icon) = remember(rule.packageName) {
        try {
            val ai = pm.getApplicationInfo(rule.packageName, 0)
            pm.getApplicationLabel(ai).toString() to pm.getApplicationIcon(ai)
        } catch (_: Exception) {
            rule.packageName to null
        }
    }
    val addedDate = remember(rule.addedAtMs) {
        SimpleDateFormat("MMM d, hh:mm a", Locale.US).format(Date(rule.addedAtMs))
    }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                val bmp = remember(rule.packageName) { getCachedAppIconBitmap(rule.packageName, icon) }
                Image(
                    bitmap = bmp,
                    contentDescription = appName,
                    modifier = Modifier.size(34.dp).clip(RoundedCornerShape(8.dp))
                )
                Spacer(modifier = Modifier.width(10.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = appName,
                    color = TextPrimary,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${rule.source} • $addedDate",
                    color = TextTertiary,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box(
                modifier = Modifier
                    .heightIn(min = 36.dp)
                    .clip(RoundedCornerShape(100.dp))
                    .background(AuraRose.copy(alpha = 0.14f))
                    .border(1.dp, AuraRose.copy(alpha = 0.35f), RoundedCornerShape(100.dp))
                    .clickable { onRemove() }
                    .padding(horizontal = 11.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Turn On Alerts", color = AuraRose, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp))
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            GuardSensorType.entries.forEach { sensor ->
                val trusted = sensor in rule.trustedSensors
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (trusted) AuraGreen.copy(alpha = 0.16f) else GlassPanel)
                        .border(
                            1.dp,
                            if (trusted) AuraGreen.copy(alpha = 0.5f) else GlassBorder,
                            RoundedCornerShape(8.dp)
                        )
                        .clickable {
                            val updated = if (trusted) {
                                rule.trustedSensors - sensor
                            } else {
                                rule.trustedSensors + sensor
                            }
                            onUpdateSensors(updated)
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (trusted) "✓ ${sensor.label} Trusted" else sensor.label,
                        color = if (trusted) AuraGreen else TextSecondary,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    )
                }
            }
        }
    }
}

@Composable
private fun SystemAppTrustRow(
    app: DiscoveredSystemSensorApp,
    isTrusted: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val pm = context.packageManager
    val icon = remember(app.packageName) {
        try { pm.getApplicationIcon(app.packageName) } catch (_: Exception) { null }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!isTrusted) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            val bmp = remember(app.packageName) { getCachedAppIconBitmap(app.packageName, icon) }
            Image(
                bitmap = bmp,
                contentDescription = app.appName,
                modifier = Modifier.size(30.dp).clip(RoundedCornerShape(8.dp))
            )
            Spacer(modifier = Modifier.width(10.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.appName,
                color = TextPrimary,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${app.roleLabel} • Trusts ${app.recommendedSensors.joinToString { it.label }}",
                color = TextTertiary,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Switch(
            checked = isTrusted,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Obsidian,
                checkedTrackColor = AuraGreen,
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = GlassPanel
            )
        )
    }
}

@Composable
private fun AddAppRuleSheet(
    apps: List<InstalledAppEntry>,
    alreadyTrusted: Set<String>,
    onSelectApp: (InstalledAppEntry) -> Unit,
    onClose: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(apps, query, alreadyTrusted) {
        apps.filter {
            it.packageName !in alreadyTrusted &&
                (query.isBlank() ||
                    it.appName.contains(query, ignoreCase = true) ||
                    it.packageName.contains(query, ignoreCase = true))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Add Trusted App Rule",
                color = TextPrimary,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search apps (e.g., WhatsApp, Recorder)", color = TextTertiary) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AuraCyan,
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
                .height(340.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(filtered, key = { it.packageName }) { app ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelectApp(app) }
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (app.icon != null) {
                        val bmp = remember(app.packageName) { getCachedAppIconBitmap(app.packageName, app.icon) }
                        Image(
                            bitmap = bmp,
                            contentDescription = app.appName,
                            modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp))
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = app.appName,
                            color = TextPrimary,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = app.packageName,
                            color = TextTertiary,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(Icons.Default.Shield, contentDescription = "Trust", tint = AuraCyan, modifier = Modifier.size(16.dp))
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
    }
}

private data class GuardStatusTuple(
    val title: String,
    val subtitle: String,
    val buttonLabel: String,
    val tint: Color
)
