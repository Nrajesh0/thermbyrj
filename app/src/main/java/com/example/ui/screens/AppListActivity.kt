package com.example.ui.screens

import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.os.storage.StorageManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.GlassCard
import com.example.formatBytes
import com.example.service.StorageScanCache
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class RiskLevel { HIGH, MEDIUM, LOW }

data class AppItem(
    val name: String,
    val packageName: String,
    val versionName: String,
    val totalSize: Long,
    val appBytes: Long,
    val dataBytes: Long,
    val cacheBytes: Long,
    val installTime: Long,
    val lastTimeUsed: Long,
    val isSystemApp: Boolean,
    val icon: ImageBitmap?,
    val riskLevel: RiskLevel,
    val dangerousPermissions: List<String>
)

private val HIGH_RISK_PERMS = mapOf(
    "android.permission.CAMERA" to "Camera",
    "android.permission.RECORD_AUDIO" to "Microphone",
    "android.permission.ACCESS_FINE_LOCATION" to "Precise Location",
    "android.permission.ACCESS_COARSE_LOCATION" to "Coarse Location",
    "android.permission.READ_CONTACTS" to "Read Contacts",
    "android.permission.READ_SMS" to "Read SMS",
    "android.permission.SEND_SMS" to "Send SMS",
    "android.permission.READ_CALL_LOG" to "Call Logs",
    "android.permission.SYSTEM_ALERT_WINDOW" to "Draw Over Other Apps"
)

class AppListActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Obsidian) {
                    AppListScreen(onBack = { finish() })
                }
            }
        }
    }
}

@Suppress("DEPRECATION")
private fun hasUsagePermission(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
    } else {
        @Suppress("DEPRECATION")
        appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
    }
    return mode == AppOpsManager.MODE_ALLOWED
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppListScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<AppItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var sortOption by remember { mutableStateOf("Size") }
    var showSortMenu by remember { mutableStateOf(false) }
    var showSystemApps by remember { mutableStateOf(false) }
    var selectedAppForAudit by remember { mutableStateOf<AppItem?>(null) }
    var refreshTrigger by remember { mutableIntStateOf(0) }
    var hasUsageAccess by remember { mutableStateOf(hasUsagePermission(context)) }

    val uninstallLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        StorageScanCache.invalidate()
        refreshTrigger++
    }

    val usageAccessLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        hasUsageAccess = hasUsagePermission(context)
        refreshTrigger++
    }

    LaunchedEffect(refreshTrigger) {
        isLoading = true
        if (refreshTrigger > 0) delay(600)
        hasUsageAccess = hasUsagePermission(context)
        withContext(Dispatchers.IO) {
            apps = fetchInstalledApps(context)
            isLoading = false
        }
    }

    val filteredAndSortedApps = remember(apps, sortOption, showSystemApps, searchQuery) {
        val q = searchQuery.trim().lowercase()
        val filtered = apps.filter {
            (if (showSystemApps) true else !it.isSystemApp) &&
                (q.isEmpty() || it.name.lowercase().contains(q) || it.packageName.lowercase().contains(q))
        }
        when (sortOption) {
            "Size" -> filtered.sortedByDescending { it.totalSize }
            "InstallNew" -> filtered.sortedWith(
                compareByDescending<AppItem> { it.installTime }.thenBy { it.name.lowercase() }
            )
            "InstallOld" -> filtered.sortedWith(
                compareBy<AppItem> { if (it.installTime > 0L) it.installTime else Long.MAX_VALUE }.thenBy { it.name.lowercase() }
            )
            "Risk" -> filtered.sortedWith(compareBy<AppItem> { it.riskLevel.ordinal }.thenByDescending { it.dangerousPermissions.size })
            "Usage" -> filtered.sortedWith(
                compareBy<AppItem> { if (it.lastTimeUsed == 0L) 0 else 1 }
                    .thenBy { it.lastTimeUsed }
                    .thenByDescending { it.totalSize }
            )
            "Name" -> filtered.sortedBy { it.name.lowercase() }
            else -> filtered
        }
    }

    if (selectedAppForAudit != null) {
        val app = selectedAppForAudit!!
        AlertDialog(
            onDismissRequest = { selectedAppForAudit = null },
            containerColor = GlassPanel,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (app.riskLevel == RiskLevel.HIGH) Icons.Default.Warning else Icons.Default.Security,
                        contentDescription = null,
                        tint = when (app.riskLevel) {
                            RiskLevel.HIGH -> AuraRose
                            RiskLevel.MEDIUM -> AuraOrange
                            RiskLevel.LOW -> AuraGreen
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(app.name, color = TextPrimary, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            },
            text = {
                Column {
                    Text("Package: ${app.packageName}", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("APK Size", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                            Text(formatBytes(app.appBytes), style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                        }
                        Column {
                            Text("User Data", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                            Text(formatBytes(app.dataBytes), style = MaterialTheme.typography.bodyMedium, color = AuraCyan)
                        }
                        Column {
                            Text("Cache", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                            Text(formatBytes(app.cacheBytes), style = MaterialTheme.typography.bodyMedium, color = AuraOrange)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = GlassBorder)
                    Spacer(modifier = Modifier.height(12.dp))

                    Text("SECURITY & PRIVACY AUDIT", style = MaterialTheme.typography.labelSmall, color = AuraCyan, letterSpacing = 1.sp)
                    Spacer(modifier = Modifier.height(8.dp))

                    if (app.dangerousPermissions.isEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AuraGreen, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("No high-risk sensitive permissions granted.", color = AuraGreen, style = MaterialTheme.typography.bodySmall)
                        }
                    } else {
                        Text("Active Sensitive Permissions:", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(6.dp))
                        app.dangerousPermissions.forEach { perm ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(AuraRose))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(perm, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!app.isSystemApp) {
                        OutlinedButton(
                            onClick = {
                                val pkg = app.packageName
                                selectedAppForAudit = null
                                val intent = Intent(Intent.ACTION_DELETE).apply {
                                    data = Uri.parse("package:$pkg")
                                }
                                uninstallLauncher.launch(intent)
                            }
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = AuraRose, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Uninstall", color = AuraRose)
                        }
                    }
                    Button(
                        onClick = {
                            val pkg = app.packageName
                            selectedAppForAudit = null
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.parse("package:$pkg")
                            }
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AuraCyan)
                    ) {
                        Text("App Settings", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedAppForAudit = null }) {
                    Text("Close", color = TextSecondary)
                }
            }
        )
    }

    var isSearchVisible by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("App Manager (${filteredAndSortedApps.size})", color = TextPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                actions = {
                    SearchIconToggleButton(
                        isExpanded = isSearchVisible,
                        hasQuery = searchQuery.isNotEmpty(),
                        modifier = Modifier.padding(end = 4.dp),
                        onClick = {
                            isSearchVisible = !isSearchVisible
                            if (!isSearchVisible) searchQuery = ""
                        }
                    )
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort", tint = AuraCyan)
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false },
                            modifier = Modifier.background(GlassPanel)
                        ) {
                            listOf(
                                "Size" to "Sort by Size",
                                "InstallNew" to "Sort by Install Time (New to Old)",
                                "InstallOld" to "Sort by Install Time (Old to New)",
                                "Risk" to "Sort by Privacy Risk",
                                "Usage" to "Sort by Least Used",
                                "Name" to "Sort by Name (A-Z)"
                            ).forEach { (key, label) ->
                                DropdownMenuItem(
                                    text = { Text(label, color = if (sortOption == key) AuraCyan else TextPrimary) },
                                    onClick = {
                                        sortOption = key
                                        showSortMenu = false
                                    }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    if (!isLoading) {
                        StorageScanCache.invalidate()
                        refreshTrigger++
                    }
                },
                containerColor = GlassPanel,
                contentColor = AuraCyan,
                modifier = Modifier.testTag("app_list_bottom_refresh_button")
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh Apps", tint = AuraCyan, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Refresh", color = AuraCyan, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            ExpandableSearchBar(
                visible = isSearchVisible || searchQuery.isNotEmpty(),
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                onClose = {
                    searchQuery = ""
                    isSearchVisible = false
                },
                placeholder = "Search apps...",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 4.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = !showSystemApps,
                    onClick = { showSystemApps = false },
                    label = { Text("User Apps") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AuraCyan.copy(alpha = 0.2f),
                        selectedLabelColor = AuraCyan
                    )
                )
                FilterChip(
                    selected = showSystemApps,
                    onClick = { showSystemApps = true },
                    label = { Text("Include System") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AuraPurple.copy(alpha = 0.2f),
                        selectedLabelColor = AuraPurple
                    )
                )
            }

            if (sortOption == "Usage" && !hasUsageAccess) {
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 4.dp)
                        .clickable {
                            usageAccessLauncher.launch(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                        }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = AuraOrange)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "Tap to grant Usage Access for accurate 'Least Used' sorting & exact cache sizes.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = AuraPurple)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Auditing Apps & Storage...", color = TextSecondary)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredAndSortedApps, key = { it.packageName }) { app ->
                        AppItemCard(app = app, onClick = { selectedAppForAudit = app })
                    }
                }
            }
        }
    }
}

@Composable
fun AppItemCard(app: AppItem, onClick: () -> Unit) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }

    val (riskColor, riskText) = when (app.riskLevel) {
        RiskLevel.HIGH -> Pair(AuraRose, "High Risk (${app.dangerousPermissions.size})")
        RiskLevel.MEDIUM -> Pair(AuraOrange, "Med Risk (${app.dangerousPermissions.size})")
        RiskLevel.LOW -> Pair(AuraGreen, "Safe")
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
            if (app.icon != null) {
                Image(
                    bitmap = app.icon,
                    contentDescription = app.name,
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(AuraPurple.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Android, contentDescription = null, tint = AuraPurple)
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = app.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(riskColor.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(riskText, color = riskColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                val installLabel = if (app.installTime > 0L) {
                    "Installed ${dateFormat.format(Date(app.installTime))}"
                } else {
                    "Installed"
                }
                val usageString = if (app.lastTimeUsed > 0L) {
                    val daysAgo = ((System.currentTimeMillis() - app.lastTimeUsed) / (1000 * 60 * 60 * 24)).toInt()
                    val usedPart = if (daysAgo == 0) "Used today" else if (daysAgo > 30) "Unused ($daysAgo days)" else "Used ${daysAgo}d ago"
                    "$installLabel • $usedPart"
                } else {
                    installLabel
                }

                Text(
                    text = "${formatBytes(app.totalSize)}  •  $usageString",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (usageString.contains("Unused")) AuraOrange else TextTertiary
                )
            }
        }
    }
}

private val globalAppIconCache = android.util.LruCache<String, ImageBitmap>(220)

internal fun getCachedAppIconBitmap(packageName: String, drawable: Drawable): ImageBitmap {
    synchronized(globalAppIconCache) {
        globalAppIconCache.get(packageName)?.let { return it }
    }
    val bmp = drawable.toSafeBitmap(maxSizePx = 96).asImageBitmap()
    synchronized(globalAppIconCache) {
        globalAppIconCache.put(packageName, bmp)
    }
    return bmp
}

internal fun Drawable.toSafeBitmap(maxSizePx: Int = 96): Bitmap {
    if (this is BitmapDrawable && this.bitmap != null) {
        val src = this.bitmap
        if (src.width <= maxSizePx && src.height <= maxSizePx) {
            return src
        }
        return Bitmap.createScaledBitmap(src, maxSizePx, maxSizePx, true)
    }
    val rawW = if (intrinsicWidth > 0) intrinsicWidth else maxSizePx
    val rawH = if (intrinsicHeight > 0) intrinsicHeight else maxSizePx
    val width = rawW.coerceIn(1, maxSizePx)
    val height = rawH.coerceIn(1, maxSizePx)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bitmap
}

private fun drawableToImageBitmap(packageName: String, drawable: Drawable): ImageBitmap? {
    return try {
        getCachedAppIconBitmap(packageName, drawable)
    } catch (_: Exception) {
        null
    }
}

private fun fetchInstalledApps(context: Context): List<AppItem> {
    val pm = context.packageManager
    val hasUsage = hasUsagePermission(context)
    val storageStatsManager = if (hasUsage) {
        context.getSystemService(Context.STORAGE_STATS_SERVICE) as? StorageStatsManager
    } else null

    val usageMap = mutableMapOf<String, Long>()
    if (hasUsage) {
        try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val end = System.currentTimeMillis()
            val start = end - (1000L * 60 * 60 * 24 * 365)
            val stats = usm.queryAndAggregateUsageStats(start, end)
            stats.forEach { (pkg, uStat) ->
                usageMap[pkg] = uStat.lastTimeUsed
            }
        } catch (_: Exception) {
        }
    }

    val packages = try {
        pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
    } catch (_: Exception) {
        emptyList()
    }

    val result = mutableListOf<AppItem>()

    for (pkgInfo in packages) {
        val appInfo = pkgInfo.applicationInfo ?: continue
        val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
            (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0

        val name = try {
            pm.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            pkgInfo.packageName
        }

        val version = pkgInfo.versionName ?: "1.0"

        var appBytes = 0L
        var dataBytes = 0L
        var cacheBytes = 0L

        if (storageStatsManager != null) {
            try {
                val stats = storageStatsManager.queryStatsForPackage(StorageManager.UUID_DEFAULT, pkgInfo.packageName, Process.myUserHandle())
                appBytes = stats.appBytes
                dataBytes = stats.dataBytes
                cacheBytes = stats.cacheBytes
            } catch (_: Exception) {
                appBytes = appInfo.sourceDir?.let { File(it).length() } ?: 0L
            }
        } else {
            appBytes = appInfo.sourceDir?.let { File(it).length() } ?: 0L
        }

        val totalSize = appBytes + dataBytes + cacheBytes

        val grantedDangerous = mutableListOf<String>()
        val reqPerms = pkgInfo.requestedPermissions
        val reqFlags = pkgInfo.requestedPermissionsFlags
        if (reqPerms != null && reqFlags != null) {
            for (i in reqPerms.indices) {
                val permName = reqPerms[i]
                val isGranted = (reqFlags[i] and android.content.pm.PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
                if (isGranted && HIGH_RISK_PERMS.containsKey(permName)) {
                    grantedDangerous.add(HIGH_RISK_PERMS[permName]!!)
                }
            }
        }

        val riskLevel = when {
            grantedDangerous.size >= 3 || grantedDangerous.contains("Draw Over Other Apps") || grantedDangerous.contains("Read SMS") -> RiskLevel.HIGH
            grantedDangerous.isNotEmpty() -> RiskLevel.MEDIUM
            else -> RiskLevel.LOW
        }

        val icon = try {
            drawableToImageBitmap(pkgInfo.packageName, pm.getApplicationIcon(appInfo))
        } catch (_: Exception) {
            null
        }

        result.add(
            AppItem(
                name = name,
                packageName = pkgInfo.packageName,
                versionName = version,
                totalSize = totalSize,
                appBytes = appBytes,
                dataBytes = dataBytes,
                cacheBytes = cacheBytes,
                installTime = pkgInfo.firstInstallTime,
                lastTimeUsed = usageMap[pkgInfo.packageName] ?: 0L,
                isSystemApp = isSystem,
                icon = icon,
                riskLevel = riskLevel,
                dangerousPermissions = grantedDangerous.distinct()
            )
        )
    }

    return result
}
