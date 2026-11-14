package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.net.Uri
import android.provider.Settings
import android.view.Display
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ManageSearch
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.DataSaverOn
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.FolderShared
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.PermMedia
import androidx.compose.material.icons.filled.PhonelinkSetup
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.example.service.LiveThermalData
import com.example.service.PrivacyAnalyzer
import com.example.ui.components.ThermalChart
import com.example.ui.theme.AuraCyan
import com.example.ui.theme.AuraGreen
import com.example.ui.theme.AuraOrange
import com.example.ui.theme.AuraPurple
import com.example.ui.theme.AuraRose
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassPanel
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import com.example.viewmodel.ThermalViewModel
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

enum class HiddenSettingCategory(
    val key: String,
    val shortLabel: String,
    val sectionTitle: String,
    val color: Color
) {
    ALL("ALL", "All", "ALL", AuraCyan),
    NETWORK("NETWORK", "Network", "CONNECTIVITY & RADIO", AuraCyan),
    BATTERY("BATTERY", "Battery", "BATTERY & BACKGROUND", AuraGreen),
    DISPLAY("DISPLAY", "Display", "DISPLAY & STATUS BAR", AuraOrange),
    NOTIFICATIONS("NOTIFICATIONS", "Notifications", "NOTIFICATIONS & ALERTS", AuraPurple),
    SPECIAL_ACCESS("SPECIAL_ACCESS", "Special Access", "SPECIAL APP ACCESS", AuraOrange),
    PRIVACY("PRIVACY", "Privacy", "PRIVACY, SECURITY & INPUT", AuraRose),
    DEVELOPER("DEVELOPER", "System", "SYSTEM & DEVELOPER", AuraCyan)
}

data class HiddenSettingTarget(
    val pkg: String? = null,
    val cls: String? = null,
    val action: String? = null,
    val dataUri: String? = null,
    val includeAppPackageUri: Boolean = false
)

data class HiddenSettingItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val category: HiddenSettingCategory,
    val icon: ImageVector,
    val liveBadge: String? = null,
    val secretDialerCode: String? = null,
    val targets: List<HiddenSettingTarget>
)

@Composable
fun VitalsScreen(viewModel: ThermalViewModel, warningTemp: Float = 35f) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val liveData by viewModel.thermalMonitor.liveData.collectAsStateWithLifecycle(initialValue = LiveThermalData())
    val allRecords by viewModel.allRecords.collectAsStateWithLifecycle()

    var selectedCategory by remember { mutableStateOf(HiddenSettingCategory.ALL) }
    var searchQuery by remember { mutableStateOf("") }
    var showChartInTempCard by remember { mutableStateOf(true) }
    var statusRefreshTick by remember { mutableIntStateOf(0) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                statusRefreshTick++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val allHiddenSettings = remember(context, statusRefreshTick) {
        buildHiddenSettingsCatalog(context)
    }

    val groupedSettings = remember(allHiddenSettings, selectedCategory, searchQuery) {
        val q = searchQuery.trim()
        val filtered = allHiddenSettings.filter { item ->
            val catMatch = selectedCategory == HiddenSettingCategory.ALL || item.category == selectedCategory
            val queryMatch = q.isEmpty() ||
                item.title.contains(q, ignoreCase = true) ||
                item.subtitle.contains(q, ignoreCase = true) ||
                item.category.shortLabel.contains(q, ignoreCase = true) ||
                (item.secretDialerCode?.contains(q, ignoreCase = true) == true)
            catMatch && queryMatch
        }
        HiddenSettingCategory.entries
            .filter { it != HiddenSettingCategory.ALL }
            .mapNotNull { cat ->
                val list = filtered.filter { it.category == cat }
                if (list.isEmpty()) null else (cat to list)
            }
    }

    val criticalTemp = warningTemp + 5f
    val tempColor = when {
        liveData.batteryTemp >= criticalTemp -> AuraRose
        liveData.batteryTemp >= warningTemp -> AuraOrange
        liveData.batteryTemp >= 20f -> AuraGreen
        else -> AuraCyan
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(14.dp))

        // Ultra-clean Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 52.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "THERMAL & SETTINGS",
                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 3.sp, fontWeight = FontWeight.Bold),
                color = tempColor
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(100.dp))
                    .background(GlassPanel)
                    .border(1.dp, GlassBorder, RoundedCornerShape(100.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "${allHiddenSettings.size}",
                    color = TextSecondary,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Compact Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    "Search settings...",
                    color = TextTertiary,
                    style = MaterialTheme.typography.bodySmall
                )
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
                focusedContainerColor = GlassPanel.copy(alpha = 0.65f),
                unfocusedContainerColor = GlassPanel.copy(alpha = 0.65f),
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Clean Category Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            HiddenSettingCategory.entries.forEach { cat ->
                val selected = selectedCategory == cat
                Box(
                    modifier = Modifier
                        .testTag("vitals_subtab_${cat.key.lowercase()}")
                        .clip(RoundedCornerShape(100.dp))
                        .background(if (selected) cat.color.copy(alpha = 0.16f) else GlassPanel.copy(alpha = 0.65f))
                        .border(1.dp, if (selected) cat.color else GlassBorder, RoundedCornerShape(100.dp))
                        .clickable { selectedCategory = cat }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = cat.shortLabel,
                        color = if (selected) cat.color else TextSecondary,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Single Temperature Card + Grouped Native-Style Settings Options
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 112.dp)
        ) {
            // 1. SINGLE TEMPERATURE TRACKING CARD
            item(key = "single_temp_tracking_card") {
                SingleTempTrackingCard(
                    liveData = liveData,
                    allRecords = allRecords,
                    warningTemp = warningTemp,
                    showChart = showChartInTempCard,
                    onToggleChart = { showChartInTempCard = !showChartInTempCard }
                )
            }

            if (groupedSettings.isEmpty()) {
                item(key = "empty_settings") {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "No settings found.",
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            } else {
                groupedSettings.forEach { (category, itemsInGroup) ->
                    item(key = "group_${category.key}") {
                        SettingsOptionGroup(
                            category = category,
                            items = itemsInGroup,
                            onItemClick = { item -> launchHiddenSetting(context, item) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsOptionGroup(
    category: HiddenSettingCategory,
    items: List<HiddenSettingItem>,
    onItemClick: (HiddenSettingItem) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = category.sectionTitle,
            color = category.color,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                letterSpacing = 1.6.sp,
                fontWeight = FontWeight.Bold
            ),
            modifier = Modifier.padding(start = 6.dp, bottom = 6.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(GlassPanel.copy(alpha = 0.72f))
                .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
        ) {
            items.forEachIndexed { index, item ->
                SettingsOptionRow(
                    item = item,
                    accent = category.color,
                    onClick = { onItemClick(item) }
                )
                if (index < items.lastIndex) {
                    HorizontalDivider(
                        color = GlassBorder.copy(alpha = 0.55f),
                        thickness = 0.75.dp,
                        modifier = Modifier.padding(start = 52.dp, end = 14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsOptionRow(
    item: HiddenSettingItem,
    accent: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(accent.copy(alpha = 0.13f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.title,
                tint = accent,
                modifier = Modifier.size(17.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                color = TextPrimary,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = item.subtitle,
                color = TextTertiary,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        val tagText = item.liveBadge ?: item.secretDialerCode
        if (tagText != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(accent.copy(alpha = 0.12f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = tagText,
                    color = accent,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold)
                )
            }
        }

        Spacer(modifier = Modifier.width(4.dp))

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = TextTertiary,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun SingleTempTrackingCard(
    liveData: LiveThermalData,
    allRecords: List<com.example.data.ThermalRecord>,
    warningTemp: Float,
    showChart: Boolean,
    onToggleChart: () -> Unit
) {
    val criticalTemp = warningTemp + 5f
    val last24h = remember(allRecords) {
        val cutoff = System.currentTimeMillis() - (24L * 60 * 60 * 1000L)
        allRecords
            .filter { it.timestamp >= cutoff }
            .sortedBy { it.timestamp }
    }

    val tenths = (liveData.batteryTemp * 10f).roundToInt()
    val wholeTemp = tenths / 10
    val fracTemp = abs(tenths % 10)

    val max24 = last24h.maxOfOrNull { it.batteryTemp } ?: liveData.batteryTemp
    val min24 = last24h.minOfOrNull { it.batteryTemp } ?: liveData.batteryTemp

    val (zoneName, zoneColor) = when {
        liveData.batteryTemp <= 0f -> "SENSING" to TextSecondary
        liveData.batteryTemp < 20f -> "OPTIMAL" to AuraCyan
        liveData.batteryTemp < warningTemp -> "NORMAL" to AuraGreen
        liveData.batteryTemp < criticalTemp -> "ELEVATED" to AuraOrange
        else -> "CRITICAL" to AuraRose
    }

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("unified_temp_tracking_card")
            .clickable { onToggleChart() }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "TEMPERATURE",
                        color = TextTertiary,
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(zoneColor.copy(alpha = 0.14f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = zoneName,
                            color = zoneColor,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = if (liveData.batteryTemp > 0f) "$wholeTemp.$fracTemp°C" else "--.-°C",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Peak ${String.format(Locale.US, "%.1f°", max24)} • Low ${String.format(Locale.US, "%.1f°", min24)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(bottom = 3.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(100.dp))
                    .background(AuraCyan.copy(alpha = 0.14f))
                    .border(1.dp, AuraCyan.copy(alpha = 0.35f), RoundedCornerShape(100.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (showChart) "Hide 24h" else "24h Curve",
                    color = AuraCyan,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold)
                )
            }
        }

        AnimatedVisibility(visible = showChart) {
            Column {
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(82.dp)
                ) {
                    if (last24h.size < 2) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                "Collecting 24h samples...",
                                color = TextTertiary,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    } else {
                        ThermalChart(
                            records = last24h,
                            modifier = Modifier.fillMaxSize(),
                            lineColor = zoneColor,
                            fillColorStart = zoneColor.copy(alpha = 0.22f),
                            fillColorEnd = zoneColor.copy(alpha = 0.0f),
                            warningTemp = warningTemp,
                            criticalTemp = criticalTemp
                        )
                    }
                }
            }
        }
    }
}

private fun launchHiddenSetting(context: Context, item: HiddenSettingItem) {
    for (target in item.targets) {
        val intent = Intent().apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (target.action != null) {
                action = target.action
            }
            if (target.pkg != null && target.cls != null) {
                component = ComponentName(target.pkg, target.cls)
            } else if (target.pkg != null) {
                setPackage(target.pkg)
            }
            if (target.dataUri != null) {
                data = Uri.parse(target.dataUri)
            } else if (target.includeAppPackageUri) {
                data = Uri.parse("package:${context.packageName}")
            }
        }
        try {
            context.startActivity(intent)
            return
        } catch (_: Exception) {
        }
    }

    if (item.secretDialerCode != null) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Secret Code", item.secretDialerCode))
            val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(item.secretDialerCode)}")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            Toast.makeText(
                context,
                "Copied ${item.secretDialerCode} — dial to open menu",
                Toast.LENGTH_SHORT
            ).show()
            context.startActivity(dialIntent)
            return
        } catch (_: Exception) {
        }
    }

    try {
        context.startActivity(Intent(Settings.ACTION_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
    } catch (_: Exception) {
    }
}

internal fun buildHiddenSettingsCatalog(context: Context): List<HiddenSettingItem> {
    val cr = context.contentResolver

    val dnsModeBadge = try {
        when (Settings.Global.getString(cr, "private_dns_mode")) {
            "hostname" -> "ENCRYPTED"
            "opportunistic" -> "AUTO"
            "off" -> "OFF"
            else -> null
        }
    } catch (_: Exception) {
        null
    }

    val refreshRateBadge = try {
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val rate = dm.getDisplay(Display.DEFAULT_DISPLAY)?.refreshRate?.roundToInt() ?: 60
        "${rate}Hz"
    } catch (_: Exception) {
        null
    }

    val animScaleBadge = try {
        val scale = Settings.Global.getFloat(cr, Settings.Global.ANIMATOR_DURATION_SCALE, 1.0f)
        if (scale == 0f) "OFF" else String.format(Locale.US, "%.1fx", scale)
    } catch (_: Exception) {
        null
    }

    val adbBadge = try {
        val on = Settings.Global.getInt(cr, Settings.Global.ADB_ENABLED, 0) == 1
        if (on) "ADB ON" else "ADB OFF"
    } catch (_: Exception) {
        null
    }

    val notifListenerCount = try {
        PrivacyAnalyzer.getEnabledNotificationListeners(context).size
    } catch (_: Exception) {
        0
    }

    val dpiBadge = try {
        "${context.resources.displayMetrics.densityDpi} DPI"
    } catch (_: Exception) {
        null
    }

    return listOf(
        // 1. CONNECTIVITY & RADIO
        HiddenSettingItem(
            id = "net_radio_5g_lock",
            title = "5G / LTE Band Lock",
            subtitle = "RadioInfo • NR / LTE force",
            category = HiddenSettingCategory.NETWORK,
            icon = Icons.Default.CellTower,
            secretDialerCode = "*#*#4636#*#*",
            targets = listOf(
                HiddenSettingTarget(pkg = "com.android.phone", cls = "com.android.phone.settings.RadioInfo"),
                HiddenSettingTarget(pkg = "com.android.settings", cls = "com.android.settings.RadioInfo"),
                HiddenSettingTarget(pkg = "com.android.settings", cls = "com.android.settings.TestingSettings"),
                HiddenSettingTarget(action = Settings.ACTION_NETWORK_OPERATOR_SETTINGS),
                HiddenSettingTarget(action = Settings.ACTION_DATA_ROAMING_SETTINGS)
            )
        ),
        HiddenSettingItem(
            id = "net_private_dns",
            title = "Private DNS",
            subtitle = "Encrypted DNS-over-TLS",
            category = HiddenSettingCategory.NETWORK,
            icon = Icons.Default.Dns,
            liveBadge = dnsModeBadge,
            targets = listOf(
                HiddenSettingTarget(pkg = "com.android.settings", cls = "com.android.settings.Settings\$NetworkDashboardActivity"),
                HiddenSettingTarget(action = Settings.ACTION_WIRELESS_SETTINGS)
            )
        ),
        HiddenSettingItem(
            id = "net_wifi_calling",
            title = "Wi-Fi Calling (VoWiFi)",
            subtitle = "Voice & SMS over Wi-Fi",
            category = HiddenSettingCategory.NETWORK,
            icon = Icons.Default.SettingsInputAntenna,
            targets = listOf(
                HiddenSettingTarget(action = "android.settings.WIFI_CALLING_SETTINGS"),
                HiddenSettingTarget(pkg = "com.android.settings", cls = "com.android.settings.Settings\$WifiCallingSettingsActivity"),
                HiddenSettingTarget(action = Settings.ACTION_NETWORK_OPERATOR_SETTINGS)
            )
        ),
        HiddenSettingItem(
            id = "net_saved_wifi",
            title = "Saved Wi-Fi & QR Passwords",
            subtitle = "SSIDs & randomized MAC",
            category = HiddenSettingCategory.NETWORK,
            icon = Icons.Default.Wifi,
            targets = listOf(
                HiddenSettingTarget(action = "android.settings.WIFI_SAVED_NETWORK_SETTINGS"),
                HiddenSettingTarget(pkg = "com.android.settings", cls = "com.android.settings.wifi.savedaccesspoints2.SavedAccessPointsWifiSettings2"),
                HiddenSettingTarget(action = Settings.ACTION_WIFI_IP_SETTINGS),
                HiddenSettingTarget(action = Settings.ACTION_WIFI_SETTINGS)
            )
        ),
        HiddenSettingItem(
            id = "net_hotspot_band",
            title = "Hotspot 5GHz & USB Tethering",
            subtitle = "AP band & RNDIS tether",
            category = HiddenSettingCategory.NETWORK,
            icon = Icons.Default.WifiTethering,
            targets = listOf(
                HiddenSettingTarget(pkg = "com.android.settings", cls = "com.android.settings.TetherSettings"),
                HiddenSettingTarget(action = "android.settings.TETHER_SETTINGS"),
                HiddenSettingTarget(action = Settings.ACTION_WIRELESS_SETTINGS)
            )
        ),
        HiddenSettingItem(
            id = "net_vpn_killswitch",
            title = "Always-On VPN & Lockdown",
            subtitle = "VPN profiles & kill-switch",
            category = HiddenSettingCategory.NETWORK,
            icon = Icons.Default.VpnKey,
            targets = listOf(
                HiddenSettingTarget(action = Settings.ACTION_VPN_SETTINGS)
            )
        ),
        HiddenSettingItem(
            id = "net_apn_settings",
            title = "APN & IMS Settings",
            subtitle = "Carrier Access Point Names",
            category = HiddenSettingCategory.NETWORK,
            icon = Icons.Default.SimCard,
            targets = listOf(
                HiddenSettingTarget(action = Settings.ACTION_APN_SETTINGS),
                HiddenSettingTarget(action = Settings.ACTION_DATA_ROAMING_SETTINGS)
            )
        ),
        HiddenSettingItem(
            id = "net_operators",
            title = "Network Operators",
            subtitle = "Manual carrier scan",
            category = HiddenSettingCategory.NETWORK,
            icon = Icons.Default.CellTower,
            targets = listOf(
                HiddenSettingTarget(action = Settings.ACTION_NETWORK_OPERATOR_SETTINGS)
            )
        ),
        HiddenSettingItem(
            id = "net_data_saver",
            title = "Data Saver & Data Usage",
            subtitle = "Background data firewall",
            category = HiddenSettingCategory.NETWORK,
            icon = Icons.Default.DataSaverOn,
            targets = listOf(
                HiddenSettingTarget(action = "android.settings.DATA_SAVER_SETTINGS"),
                HiddenSettingTarget(action = Settings.ACTION_IGNORE_BACKGROUND_DATA_RESTRICTIONS_SETTINGS, includeAppPackageUri = true),
                HiddenSettingTarget(action = "android.settings.DATA_USAGE_SETTINGS")
            )
        ),
        HiddenSettingItem(
            id = "net_wireless_cast",
            title = "Wireless Display & Cast",
            subtitle = "Miracast & screen mirror",
            category = HiddenSettingCategory.NETWORK,
            icon = Icons.Default.Cast,
            targets = listOf(
                HiddenSettingTarget(action = Settings.ACTION_CAST_SETTINGS),
                HiddenSettingTarget(action = Settings.ACTION_AIRPLANE_MODE_SETTINGS)
            )
        ),
        HiddenSettingItem(
            id = "net_airplane_radios",
            title = "Airplane & Radio Toggles",
            subtitle = "Quick wireless disconnect",
            category = HiddenSettingCategory.NETWORK,
            icon = Icons.Default.Flight,
            targets = listOf(
                HiddenSettingTarget(action = Settings.ACTION_AIRPLANE_MODE_SETTINGS),
                HiddenSettingTarget(action = Settings.ACTION_WIRELESS_SETTINGS)
            )
        ),

        // 2. BATTERY & BACKGROUND
        HiddenSettingItem(
            id = "bat_usage_summary",
            title = "Battery Usage Graph",
            subtitle = "Screen-on & app drain",
            category = HiddenSettingCategory.BATTERY,
            icon = Icons.Default.BarChart,
            targets = listOf(
                HiddenSettingTarget(action = Intent.ACTION_POWER_USAGE_SUMMARY),
                HiddenSettingTarget(action = Settings.ACTION_BATTERY_SAVER_SETTINGS)
            )
        ),
        HiddenSettingItem(
            id = "bat_doze_whitelist",
            title = "Doze & Battery Whitelist",
            subtitle = "Unrestricted background apps",
            category = HiddenSettingCategory.BATTERY,
            icon = Icons.Default.BatteryChargingFull,
            targets = listOf(
                HiddenSettingTarget(action = Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
                HiddenSettingTarget(action = Settings.ACTION_BATTERY_SAVER_SETTINGS)
            )
        ),
        HiddenSettingItem(
            id = "bat_oem_powerkeeper",
            title = "OEM Power & Thermal Engine",
            subtitle = "PowerKeeper / Device Care",
            category = HiddenSettingCategory.BATTERY,
            icon = Icons.Default.Bolt,
            targets = listOf(
                HiddenSettingTarget(pkg = "com.miui.powerkeeper", cls = "com.miui.powerkeeper.ui.HiddenAppsContainerManagementActivity"),
                HiddenSettingTarget(pkg = "com.miui.securitycenter", cls = "com.miui.powercenter.PowerMainActivity"),
                HiddenSettingTarget(pkg = "com.samsung.android.lool", cls = "com.samsung.android.sm.ui.battery.BatteryActivity"),
                HiddenSettingTarget(action = Intent.ACTION_POWER_USAGE_SUMMARY),
                HiddenSettingTarget(action = Settings.ACTION_BATTERY_SAVER_SETTINGS)
            )
        ),
        HiddenSettingItem(
            id = "bat_autostart_guard",
            title = "Auto-Start Manager",
            subtitle = "Boot & background launch",
            category = HiddenSettingCategory.BATTERY,
            icon = Icons.Default.Speed,
            targets = listOf(
                HiddenSettingTarget(pkg = "com.miui.securitycenter", cls = "com.miui.permcenter.autostart.AutoStartManagementActivity"),
                HiddenSettingTarget(pkg = "com.coloros.safecenter", cls = "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
                HiddenSettingTarget(pkg = "com.oplus.safecenter", cls = "com.oplus.safecenter.permission.startup.StartupAppListActivity"),
                HiddenSettingTarget(pkg = "com.vivo.permissionmanager", cls = "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
                HiddenSettingTarget(action = Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            )
        ),
        HiddenSettingItem(
            id = "bat_running_services",
            title = "Running Services & RAM",
            subtitle = "Live active processes",
            category = HiddenSettingCategory.BATTERY,
            icon = Icons.Default.Memory,
            targets = listOf(
                HiddenSettingTarget(pkg = "com.android.settings", cls = "com.android.settings.Settings\$RunningServicesActivity"),
                HiddenSettingTarget(pkg = "com.android.settings", cls = "com.android.settings.Settings\$MemorySettingsActivity"),
                HiddenSettingTarget(action = Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
            )
        ),
        HiddenSettingItem(
            id = "bat_gpu_driver",
            title = "GPU & Game Driver",
            subtitle = "Per-app graphics driver",
            category = HiddenSettingCategory.BATTERY,
            icon = Icons.Default.SportsEsports,
            targets = listOf(
                HiddenSettingTarget(pkg = "com.android.settings", cls = "com.android.settings.Settings\$GraphicsDriverDashboardActivity"),
                HiddenSettingTarget(action = "android.settings.GRAPHICS_DRIVER_SETTINGS"),
                HiddenSettingTarget(action = Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
            )
        ),

        // 3. DISPLAY & STATUS BAR
        HiddenSettingItem(
            id = "disp_systemui_tuner",
            title = "SystemUI Tuner",
            subtitle = "Status bar icons & clock",
            category = HiddenSettingCategory.DISPLAY,
            icon = Icons.Default.Tune,
            targets = listOf(
                HiddenSettingTarget(pkg = "com.android.systemui", cls = "com.android.systemui.tuner.TunerActivity"),
                HiddenSettingTarget(pkg = "com.android.systemui", cls = "com.android.systemui.DemoMode"),
                HiddenSettingTarget(action = "com.android.settings.action.DEMO_MODE"),
                HiddenSettingTarget(action = Settings.ACTION_DISPLAY_SETTINGS)
            )
        ),
        HiddenSettingItem(
            id = "disp_refresh_rate",
            title = "Refresh Rate",
            subtitle = "60Hz / 90Hz / 120Hz mode",
            category = HiddenSettingCategory.DISPLAY,
            icon = Icons.Default.Speed,
            liveBadge = refreshRateBadge,
            targets = listOf(
                HiddenSettingTarget(pkg = "com.android.settings", cls = "com.android.settings.display.ScreenRefreshRateActivity"),
                HiddenSettingTarget(pkg = "com.miui.powerkeeper", cls = "com.miui.powerkeeper.ui.ExtremeModeEntryActivity"),
                HiddenSettingTarget(action = "com.xiaomi.intent.action.SCREEN_REFRESH_RATE"),
                HiddenSettingTarget(action = Settings.ACTION_DISPLAY_SETTINGS)
            )
        ),
        HiddenSettingItem(
            id = "disp_size_dpi",
            title = "Display Size & DPI Scaling",
            subtitle = "Screen zoom & font scale",
            category = HiddenSettingCategory.DISPLAY,
            icon = Icons.Default.AspectRatio,
            liveBadge = dpiBadge,
            targets = listOf(
                HiddenSettingTarget(pkg = "com.android.settings", cls = "com.android.settings.Settings\$ScreenZoomSettingsActivity"),
                HiddenSettingTarget(action = Settings.ACTION_DISPLAY_SETTINGS)
            )
        ),
        HiddenSettingItem(
            id = "disp_anim_scale",
            title = "Animation Scales",
            subtitle = "Window & transition speed",
            category = HiddenSettingCategory.DISPLAY,
            icon = Icons.Default.Animation,
            liveBadge = animScaleBadge,
            targets = listOf(
                HiddenSettingTarget(action = Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
            )
        ),
        HiddenSettingItem(
            id = "disp_extra_dim",
            title = "Extra Dim",
            subtitle = "Sub-minimum OLED brightness",
            category = HiddenSettingCategory.DISPLAY,
            icon = Icons.Default.ColorLens,
            targets = listOf(
                HiddenSettingTarget(action = "android.settings.REDUCE_BRIGHT_COLORS_SETTINGS"),
                HiddenSettingTarget(action = Settings.ACTION_NIGHT_DISPLAY_SETTINGS),
                HiddenSettingTarget(action = Settings.ACTION_ACCESSIBILITY_SETTINGS)
            )
        ),
        HiddenSettingItem(
            id = "disp_color_gamut",
            title = "Color Profile & Night Light",
            subtitle = "sRGB / DCI-P3 & eye comfort",
            category = HiddenSettingCategory.DISPLAY,
            icon = Icons.Default.ColorLens,
            targets = listOf(
                HiddenSettingTarget(pkg = "com.android.settings", cls = "com.android.settings.display.ColorModeActivity"),
                HiddenSettingTarget(action = Settings.ACTION_NIGHT_DISPLAY_SETTINGS),
                HiddenSettingTarget(action = Settings.ACTION_DISPLAY_SETTINGS)
            )
        ),
        HiddenSettingItem(
            id = "disp_screen_saver",
            title = "Screen Saver & Standby",
            subtitle = "Dock & charging display",
            category = HiddenSettingCategory.DISPLAY,
            icon = Icons.Default.Bedtime,
            targets = listOf(
                HiddenSettingTarget(action = Settings.ACTION_DREAM_SETTINGS),
                HiddenSettingTarget(action = Settings.ACTION_DISPLAY_SETTINGS)
            )
        ),
        HiddenSettingItem(
            id = "disp_captions",
            title = "Live Caption & Subtitles",
            subtitle = "System-wide audio captions",
            category = HiddenSettingCategory.DISPLAY,
            icon = Icons.Default.ClosedCaption,
            targets = listOf(
                HiddenSettingTarget(action = Settings.ACTION_CAPTIONING_SETTINGS),
                HiddenSettingTarget(action = Settings.ACTION_ACCESSIBILITY_SETTINGS)
            )
        ),

        // 4. NOTIFICATIONS & ALERTS
        HiddenSettingItem(
            id = "notif_history_log",
            title = "Notification History",
            subtitle = "Dismissed & deleted alerts",
            category = HiddenSettingCategory.NOTIFICATIONS,
            icon = Icons.Default.History,
            targets = listOf(
                HiddenSettingTarget(action = "android.settings.NOTIFICATION_HISTORY"),
                HiddenSettingTarget(pkg = "com.android.settings", cls = "com.android.settings.notification.history.NotificationHistoryActivity"),
                HiddenSettingTarget(pkg = "com.android.settings", cls = "com.android.settings.Settings\$NotificationStationActivity"),
                HiddenSettingTarget(action = Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            )
        ),
        HiddenSettingItem(
            id = "notif_all_apps",
            title = "All App Notifications",
            subtitle = "Per-app alert channels",
            category = HiddenSettingCategory.NOTIFICATIONS,
            icon = Icons.Default.Notifications,
            targets = listOf(
                HiddenSettingTarget(action = "android.settings.ALL_APPS_NOTIFICATION_SETTINGS"),
                HiddenSettingTarget(pkg = "com.android.settings", cls = "com.android.settings.Settings\$ConfigureNotificationSettingsActivity")
            )
        ),
        HiddenSettingItem(
            id = "notif_listeners_audit",
            title = "Notification Listeners",
            subtitle = "Apps reading notifications",
            category = HiddenSettingCategory.NOTIFICATIONS,
            icon = Icons.Default.NotificationsActive,
            liveBadge = "$notifListenerCount ACTIVE",
            targets = listOf(
                HiddenSettingTarget(action = "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"),
                HiddenSettingTarget(action = Settings.ACTION_PRIVACY_SETTINGS)
            )
        ),
        HiddenSettingItem(
            id = "notif_dnd_bypass",
            title = "Do Not Disturb Access",
            subtitle = "DND & priority bypass",
            category = HiddenSettingCategory.NOTIFICATIONS,
            icon = Icons.Default.DoNotDisturbOn,
            targets = listOf(
                HiddenSettingTarget(action = Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS),
                HiddenSettingTarget(action = Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS)
            )
        ),
        HiddenSettingItem(
            id = "notif_bubbles",
            title = "Chat Bubbles",
            subtitle = "Floating conversation bubbles",
            category = HiddenSettingCategory.NOTIFICATIONS,
            icon = Icons.Default.PictureInPictureAlt,
            targets = listOf(
                HiddenSettingTarget(action = Settings.ACTION_APP_NOTIFICATION_BUBBLE_SETTINGS, includeAppPackageUri = true),
                HiddenSettingTarget(pkg = "com.android.settings", cls = "com.android.settings.Settings\$ConfigureNotificationSettingsActivity")
            )
        ),
        HiddenSettingItem(
            id = "notif_emergency_alerts",
            title = "Wireless Emergency Alerts",
            subtitle = "AMBER & severe broadcasts",
            category = HiddenSettingCategory.NOTIFICATIONS,
            icon = Icons.Default.Warning,
            targets = listOf(
                HiddenSettingTarget(action = "android.settings.WIRELESS_EMERGENCY_ALERTS_SETTINGS"),
                HiddenSettingTarget(pkg = "com.android.cellbroadcastreceiver", cls = "com.android.cellbroadcastreceiver.CellBroadcastSettings"),
                HiddenSettingTarget(pkg = "com.google.android.cellbroadcastreceiver", cls = "com.android.cellbroadcastreceiver.CellBroadcastSettings")
            )
        ),

        // 5. SPECIAL APP ACCESS
        HiddenSettingItem(
            id = "spec_overlay",
            title = "Display Over Other Apps",
            subtitle = "Floating screen overlays",
            category = HiddenSettingCategory.SPECIAL_ACCESS,
            icon = Icons.Default.Layers,
            targets = listOf(
                HiddenSettingTarget(action = Settings.ACTION_MANAGE_OVERLAY_PERMISSION),
                HiddenSettingTarget(action = Settings.ACTION_MANAGE_OVERLAY_PERMISSION, includeAppPackageUri = true)
            )
        ),
        HiddenSettingItem(
            id = "spec_write_settings",
            title = "Modify System Settings",
            subtitle = "Apps changing system flags",
            category = HiddenSettingCategory.SPECIAL_ACCESS,
            icon = Icons.Default.EditNote,
            targets = listOf(
                HiddenSettingTarget(action = Settings.ACTION_MANAGE_WRITE_SETTINGS),
                HiddenSettingTarget(action = Settings.ACTION_MANAGE_WRITE_SETTINGS, includeAppPackageUri = true)
            )
        ),
        HiddenSettingItem(
            id = "spec_usage_access",
            title = "Usage Data Access",
            subtitle = "App screen-time trackers",
            category = HiddenSettingCategory.SPECIAL_ACCESS,
            icon = Icons.Default.QueryStats,
            targets = listOf(
                HiddenSettingTarget(action = Settings.ACTION_USAGE_ACCESS_SETTINGS)
            )
        ),
        HiddenSettingItem(
            id = "spec_all_files",
            title = "All Files Access",
            subtitle = "Full storage permission",
            category = HiddenSettingCategory.SPECIAL_ACCESS,
            icon = Icons.Default.FolderShared,
            targets = listOf(
                HiddenSettingTarget(action = "android.settings.MANAGE_ALL_FILES_ACCESS_PERMISSION"),
                HiddenSettingTarget(action = Settings.ACTION_MANAGE_ALL_APPLICATIONS_SETTINGS)
            )
        ),
        HiddenSettingItem(
            id = "spec_unknown_sources",
            title = "Install Unknown Apps",
            subtitle = "APK sideloading sources",
            category = HiddenSettingCategory.SPECIAL_ACCESS,
            icon = Icons.Default.InstallMobile,
            targets = listOf(
                HiddenSettingTarget(action = "android.settings.MANAGE_UNKNOWN_APP_SOURCES"),
                HiddenSettingTarget(action = Settings.ACTION_SECURITY_SETTINGS)
            )
        ),
        HiddenSettingItem(
            id = "spec_exact_alarms",
            title = "Alarms & Reminders",
            subtitle = "Exact background wakeups",
            category = HiddenSettingCategory.SPECIAL_ACCESS,
            icon = Icons.Default.Alarm,
            targets = listOf(
                HiddenSettingTarget(action = "android.settings.REQUEST_SCHEDULE_EXACT_ALARM"),
                HiddenSettingTarget(action = Settings.ACTION_APPLICATION_SETTINGS)
            )
        ),
        HiddenSettingItem(
            id = "spec_pip_windows",
            title = "Picture-in-Picture (PiP)",
            subtitle = "Floating mini video windows",
            category = HiddenSettingCategory.SPECIAL_ACCESS,
            icon = Icons.Default.PictureInPictureAlt,
            targets = listOf(
                HiddenSettingTarget(action = "android.settings.PICTURE_IN_PICTURE_SETTINGS"),
                HiddenSettingTarget(action = Settings.ACTION_APPLICATION_SETTINGS)
            )
        ),
        HiddenSettingItem(
            id = "spec_manage_media",
            title = "Media Management Apps",
            subtitle = "Silent gallery edit access",
            category = HiddenSettingCategory.SPECIAL_ACCESS,
            icon = Icons.Default.PermMedia,
            targets = listOf(
                HiddenSettingTarget(action = "android.settings.REQUEST_MANAGE_MEDIA"),
                HiddenSettingTarget(action = Settings.ACTION_APPLICATION_SETTINGS)
            )
        ),
        HiddenSettingItem(
            id = "spec_vr_listeners",
            title = "VR Helper Services",
            subtitle = "Persistent VR listeners",
            category = HiddenSettingCategory.SPECIAL_ACCESS,
            icon = Icons.Default.PhonelinkSetup,
            targets = listOf(
                HiddenSettingTarget(action = Settings.ACTION_VR_LISTENER_SETTINGS),
                HiddenSettingTarget(action = Settings.ACTION_APPLICATION_SETTINGS)
            )
        ),

        // 6. PRIVACY, SECURITY & INPUT
        HiddenSettingItem(
            id = "priv_dashboard",
            title = "Privacy Dashboard",
            subtitle = "OS 7-day sensor timeline",
            category = HiddenSettingCategory.PRIVACY,
            icon = Icons.Default.PrivacyTip,
            targets = listOf(
                HiddenSettingTarget(action = "android.intent.action.REVIEW_PERMISSION_USAGE"),
                HiddenSettingTarget(action = Intent.ACTION_VIEW_PERMISSION_USAGE),
                HiddenSettingTarget(action = Settings.ACTION_PRIVACY_SETTINGS)
            )
        ),
        HiddenSettingItem(
            id = "priv_trusted_certs",
            title = "Trusted Root Certificates",
            subtitle = "System & user SSL CA certs",
            category = HiddenSettingCategory.PRIVACY,
            icon = Icons.Default.VerifiedUser,
            targets = listOf(
                HiddenSettingTarget(action = "com.android.settings.TRUSTED_CREDENTIALS_USER"),
                HiddenSettingTarget(action = "com.android.settings.TRUSTED_CREDENTIALS"),
                HiddenSettingTarget(pkg = "com.android.settings", cls = "com.android.settings.Settings\$TrustedCredentialsSettingsActivity"),
                HiddenSettingTarget(action = Settings.ACTION_SECURITY_SETTINGS)
            )
        ),
        HiddenSettingItem(
            id = "priv_user_credentials",
            title = "User Credentials & Keystore",
            subtitle = "Installed VPN & Wi-Fi certs",
            category = HiddenSettingCategory.PRIVACY,
            icon = Icons.Default.Badge,
            targets = listOf(
                HiddenSettingTarget(action = "com.android.settings.USER_CREDENTIALS"),
                HiddenSettingTarget(pkg = "com.android.settings", cls = "com.android.settings.Settings\$UserCredentialsSettingsActivity"),
                HiddenSettingTarget(action = Settings.ACTION_SECURITY_SETTINGS)
            )
        ),
        HiddenSettingItem(
            id = "priv_device_admins",
            title = "Device Admin Apps",
            subtitle = "Lock & wipe privileges",
            category = HiddenSettingCategory.PRIVACY,
            icon = Icons.Default.AdminPanelSettings,
            targets = listOf(
                HiddenSettingTarget(pkg = "com.android.settings", cls = "com.android.settings.DeviceAdminSettings"),
                HiddenSettingTarget(pkg = "com.android.settings", cls = "com.android.settings.Settings\$DeviceAdminSettingsActivity"),
                HiddenSettingTarget(action = Settings.ACTION_SECURITY_SETTINGS)
            )
        ),
        HiddenSettingItem(
            id = "priv_accessibility",
            title = "Accessibility Services",
            subtitle = "Screen-reading & automation",
            category = HiddenSettingCategory.PRIVACY,
            icon = Icons.Default.AccessibilityNew,
            targets = listOf(
                HiddenSettingTarget(action = Settings.ACTION_ACCESSIBILITY_SETTINGS)
            )
        ),
        HiddenSettingItem(
            id = "priv_autofill",
            title = "Autofill & Password Service",
            subtitle = "Credential & passkey manager",
            category = HiddenSettingCategory.PRIVACY,
            icon = Icons.Default.Password,
            targets = listOf(
                HiddenSettingTarget(action = Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE, includeAppPackageUri = true),
                HiddenSettingTarget(action = "android.settings.AUTOFILL_SETTINGS"),
                HiddenSettingTarget(action = Settings.ACTION_INPUT_METHOD_SETTINGS)
            )
        ),
        HiddenSettingItem(
            id = "priv_default_apps",
            title = "Default Apps",
            subtitle = "Browser, launcher & dialer",
            category = HiddenSettingCategory.PRIVACY,
            icon = Icons.Default.Apps,
            targets = listOf(
                HiddenSettingTarget(action = Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
                HiddenSettingTarget(action = Settings.ACTION_APPLICATION_SETTINGS)
            )
        ),
        HiddenSettingItem(
            id = "priv_deep_links",
            title = "Opening Links",
            subtitle = "Verified domain URL routing",
            category = HiddenSettingCategory.PRIVACY,
            icon = Icons.Default.Link,
            targets = listOf(
                HiddenSettingTarget(action = "android.settings.MANAGE_DOMAIN_URLS"),
                HiddenSettingTarget(action = Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, includeAppPackageUri = true),
                HiddenSettingTarget(action = Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
            )
        ),
        HiddenSettingItem(
            id = "priv_per_app_lang",
            title = "Per-App Language",
            subtitle = "Individual app locales",
            category = HiddenSettingCategory.PRIVACY,
            icon = Icons.Default.Language,
            targets = listOf(
                HiddenSettingTarget(action = "android.settings.APP_LOCALE_SETTINGS"),
                HiddenSettingTarget(pkg = "com.android.settings", cls = "com.android.settings.localepicker.AppLocalePickerActivity"),
                HiddenSettingTarget(action = Settings.ACTION_LOCALE_SETTINGS)
            )
        ),
        HiddenSettingItem(
            id = "priv_regional_prefs",
            title = "Regional Units & Calendar",
            subtitle = "°C/°F & first day of week",
            category = HiddenSettingCategory.PRIVACY,
            icon = Icons.Default.Language,
            targets = listOf(
                HiddenSettingTarget(action = "android.settings.REGIONAL_PREFERENCES_SETTINGS"),
                HiddenSettingTarget(pkg = "com.android.settings", cls = "com.android.settings.Settings\$RegionalPreferencesActivity"),
                HiddenSettingTarget(action = Settings.ACTION_LOCALE_SETTINGS)
            )
        ),
        HiddenSettingItem(
            id = "priv_keyboards",
            title = "Keyboards & Physical Input",
            subtitle = "IME & hardware keymaps",
            category = HiddenSettingCategory.PRIVACY,
            icon = Icons.Default.Keyboard,
            targets = listOf(
                HiddenSettingTarget(action = Settings.ACTION_INPUT_METHOD_SETTINGS),
                HiddenSettingTarget(action = Settings.ACTION_HARD_KEYBOARD_SETTINGS)
            )
        ),
        HiddenSettingItem(
            id = "priv_voice_tts",
            title = "Voice Input & Text-to-Speech",
            subtitle = "Speech engine & assistant",
            category = HiddenSettingCategory.PRIVACY,
            icon = Icons.Default.RecordVoiceOver,
            targets = listOf(
                HiddenSettingTarget(action = Settings.ACTION_VOICE_INPUT_SETTINGS),
                HiddenSettingTarget(action = "com.android.settings.TTS_SETTINGS")
            )
        ),

        // 7. SYSTEM & DEVELOPER
        HiddenSettingItem(
            id = "dev_options_wireless_adb",
            title = "Developer Options",
            subtitle = "Wireless ADB & BT codecs",
            category = HiddenSettingCategory.DEVELOPER,
            icon = Icons.Default.Code,
            liveBadge = adbBadge,
            targets = listOf(
                HiddenSettingTarget(action = Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
                HiddenSettingTarget(action = Settings.ACTION_DEVICE_INFO_SETTINGS)
            )
        ),
        HiddenSettingItem(
            id = "dev_hardware_cit",
            title = "Hardware CIT Test Menu",
            subtitle = "Xiaomi / MIUI sensor test",
            category = HiddenSettingCategory.DEVELOPER,
            icon = Icons.Default.Build,
            secretDialerCode = "*#*#6484#*#*",
            targets = listOf(
                HiddenSettingTarget(pkg = "com.miui.cit", cls = "com.miui.cit.CitLauncherActivity"),
                HiddenSettingTarget(pkg = "com.miui.cit", cls = "com.miui.cit.home.CitHomeActivity"),
                HiddenSettingTarget(action = Settings.ACTION_DEVICE_INFO_SETTINGS)
            )
        ),
        HiddenSettingItem(
            id = "dev_samsung_hw_test",
            title = "OEM Service & Screen Test",
            subtitle = "Samsung / OnePlus hardware test",
            category = HiddenSettingCategory.DEVELOPER,
            icon = Icons.Default.Security,
            secretDialerCode = "*#0*#",
            targets = listOf(
                HiddenSettingTarget(pkg = "com.sec.android.app.servicemodeapp", cls = "com.sec.android.app.servicemodeapp.ServiceModeApp"),
                HiddenSettingTarget(pkg = "com.oplus.engineermode", cls = "com.oplus.engineermode.EngineerModeMain"),
                HiddenSettingTarget(action = Settings.ACTION_DEVICE_INFO_SETTINGS)
            )
        ),
        HiddenSettingItem(
            id = "dev_webview_provider",
            title = "System WebView",
            subtitle = "Active Chromium WebView",
            category = HiddenSettingCategory.DEVELOPER,
            icon = Icons.Default.PhonelinkSetup,
            targets = listOf(
                HiddenSettingTarget(action = Settings.ACTION_WEBVIEW_SETTINGS),
                HiddenSettingTarget(action = Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
            )
        ),
        HiddenSettingItem(
            id = "dev_storage_manager",
            title = "Storage Manager & Cache",
            subtitle = "Free space & system partitions",
            category = HiddenSettingCategory.DEVELOPER,
            icon = Icons.Default.Storage,
            targets = listOf(
                HiddenSettingTarget(action = "android.os.storage.action.MANAGE_STORAGE"),
                HiddenSettingTarget(action = Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
            )
        ),
        HiddenSettingItem(
            id = "dev_system_update",
            title = "System & Play Updates",
            subtitle = "OS OTA & security patch",
            category = HiddenSettingCategory.DEVELOPER,
            icon = Icons.Default.SystemUpdate,
            targets = listOf(
                HiddenSettingTarget(action = "android.settings.SYSTEM_UPDATE_SETTINGS"),
                HiddenSettingTarget(pkg = "com.google.android.gms", cls = "com.google.android.gms.update.SystemUpdateActivity"),
                HiddenSettingTarget(action = Settings.ACTION_DEVICE_INFO_SETTINGS)
            )
        ),
        HiddenSettingItem(
            id = "dev_nfc_print_spooler",
            title = "NFC Payment & Print Spooler",
            subtitle = "Default wallet & print service",
            category = HiddenSettingCategory.DEVELOPER,
            icon = Icons.Default.Nfc,
            targets = listOf(
                HiddenSettingTarget(action = Settings.ACTION_NFC_PAYMENT_SETTINGS),
                HiddenSettingTarget(action = Settings.ACTION_NFC_SETTINGS),
                HiddenSettingTarget(action = Settings.ACTION_PRINT_SETTINGS)
            )
        ),
        HiddenSettingItem(
            id = "dev_search_dictionary",
            title = "Search & Personal Dictionary",
            subtitle = "System search & shortcuts",
            category = HiddenSettingCategory.DEVELOPER,
            icon = Icons.AutoMirrored.Filled.ManageSearch,
            targets = listOf(
                HiddenSettingTarget(action = Settings.ACTION_SEARCH_SETTINGS),
                HiddenSettingTarget(action = Settings.ACTION_USER_DICTIONARY_SETTINGS)
            )
        )
    )
}
