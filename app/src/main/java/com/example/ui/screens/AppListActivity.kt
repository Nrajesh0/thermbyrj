package com.example.ui.screens

import android.Manifest
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
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.formatBytes
import com.example.ui.theme.AppTheme
import com.example.ui.theme.AuraCyan
import com.example.ui.theme.AuraOrange
import com.example.ui.theme.AuraPurple
import com.example.ui.theme.AuraRose
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassPanel
import com.example.ui.theme.Obsidian
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

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

data class AppItemInfo(
    val name: String,
    val packageName: String,
    val size: Long,
    val isSystem: Boolean,
    val icon: Drawable,
    val dangerousPerms: Int = 0,
    val foregroundTimeMs: Long = 0L
)

fun Drawable.toSafeBitmap(): Bitmap {
    if (this is BitmapDrawable && this.bitmap != null) return this.bitmap
    val width = if (intrinsicWidth > 0) intrinsicWidth else 144
    val height = if (intrinsicHeight > 0) intrinsicHeight else 144
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bitmap
}

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
    var apps by remember { mutableStateOf<List<AppItemInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var hasUsageAccess by remember { mutableStateOf(hasUsagePermission(context)) }

    var filterOption by remember { mutableStateOf("User") }
    var sortOption by remember { mutableStateOf("Size") }

    var selectedApps by remember { mutableStateOf(setOf<String>()) }
    var refreshTrigger by remember { mutableIntStateOf(0) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasUsageAccess = hasUsagePermission(context)
                refreshTrigger++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(refreshTrigger) {
        val appList = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val result = mutableListOf<AppItemInfo>()
            try {
                val storageStatsManager = context.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
                val usageMap: Map<String, Long> = if (hasUsagePermission(context)) {
                    try {
                        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
                        val end = System.currentTimeMillis()
                        val start = end - 24L * 60 * 60 * 1000L
                        usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)
                            ?.associate { it.packageName to it.totalTimeInForeground }
                            ?: emptyMap()
                    } catch (_: Exception) {
                        emptyMap()
                    }
                } else {
                    emptyMap()
                }

                val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                val dangerousList = setOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.READ_CONTACTS,
                    Manifest.permission.READ_SMS,
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )

                for (app in packages) {
                    val name = pm.getApplicationLabel(app).toString()
                    var size = 0L
                    try {
                        val stats = storageStatsManager.queryStatsForUid(StorageManager.UUID_DEFAULT, app.uid)
                        size = stats.appBytes + stats.dataBytes + stats.cacheBytes
                    } catch (_: Exception) {
                        try {
                            size = File(app.sourceDir).length()
                        } catch (_: Exception) {
                        }
                    }

                    val isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    val icon = pm.getApplicationIcon(app)

                    var dangerousPerms = 0
                    try {
                        val packageInfo = pm.getPackageInfo(app.packageName, PackageManager.GET_PERMISSIONS)
                        val requestedPerms = packageInfo.requestedPermissions
                        if (requestedPerms != null) {
                            dangerousPerms = requestedPerms.count { it in dangerousList }
                        }
                    } catch (_: Exception) {
                    }

                    val fgMs = usageMap[app.packageName] ?: 0L
                    result.add(AppItemInfo(name, app.packageName, size, isSystem, icon, dangerousPerms, fgMs))
                }
            } catch (_: Exception) {
            }
            result
        }
        apps = appList
        isLoading = false
        val currentPackages = appList.map { it.packageName }.toSet()
        selectedApps = selectedApps.intersect(currentPackages)
    }

    val filteredApps = remember(apps, filterOption, sortOption) {
        val filtered = when (filterOption) {
            "User" -> apps.filter { !it.isSystem }
            "System" -> apps.filter { it.isSystem }
            else -> apps
        }
        when (sortOption) {
            "Size" -> filtered.sortedByDescending { it.size }
            "Name" -> filtered.sortedBy { it.name.lowercase() }
            "Usage" -> filtered.sortedByDescending { it.foregroundTimeMs }
            "Privacy" -> filtered.sortedByDescending { it.dangerousPerms }
            else -> filtered
        }
    }

    val isSelectionMode = selectedApps.isNotEmpty()

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            if (isSelectionMode) {
                TopAppBar(
                    title = { Text("${selectedApps.size} Selected", color = TextPrimary, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { selectedApps = emptySet() }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear Selection", tint = TextPrimary)
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            selectedApps.forEach { pkg ->
                                val intent = Intent(Intent.ACTION_DELETE)
                                intent.data = Uri.parse("package:$pkg")
                                context.startActivity(intent)
                            }
                            selectedApps = emptySet()
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Uninstall Selected", tint = AuraRose)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = AuraPurple.copy(alpha = 0.2f))
                )
            } else {
                TopAppBar(
                    title = { Text("Apps & Data", color = TextPrimary, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                        }
                    },
                    actions = {
                        var expanded by remember { mutableStateOf(false) }
                        IconButton(onClick = { expanded = true }) {
                            Icon(Icons.Default.Sort, contentDescription = "Sort", tint = TextPrimary)
                        }
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                            containerColor = GlassPanel
                        ) {
                            DropdownMenuItem(
                                text = { Text("Sort by Size", color = if (sortOption == "Size") AuraCyan else TextPrimary) },
                                onClick = { sortOption = "Size"; expanded = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Sort by Name", color = if (sortOption == "Name") AuraCyan else TextPrimary) },
                                onClick = { sortOption = "Name"; expanded = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Sort by 24h Usage", color = if (sortOption == "Usage") AuraOrange else TextPrimary) },
                                onClick = { sortOption = "Usage"; expanded = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Sort by Privacy Risk", color = if (sortOption == "Privacy") AuraRose else TextPrimary) },
                                onClick = { sortOption = "Privacy"; expanded = false }
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        }
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AuraCyan)
            }
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("User", "System", "All").forEach { opt ->
                        FilterChip(
                            selected = filterOption == opt,
                            onClick = { filterOption = opt },
                            label = { Text(opt) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AuraPurple.copy(alpha = 0.2f),
                                selectedLabelColor = AuraPurple,
                                labelColor = TextSecondary,
                                containerColor = Color.Transparent
                            ),
                            border = if (filterOption == opt) BorderStroke(1.dp, AuraPurple) else BorderStroke(1.dp, GlassBorder)
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize().navigationBarsPadding(),
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    items(filteredApps, key = { it.packageName }) { app ->
                        val isSelected = selectedApps.contains(app.packageName)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (isSelected) AuraPurple.copy(alpha = 0.1f) else Color.Transparent)
                                .clickable {
                                    if (!app.isSystem && isSelectionMode) {
                                        selectedApps = if (isSelected) selectedApps - app.packageName else selectedApps + app.packageName
                                    } else {
                                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                            data = Uri.parse("package:${app.packageName}")
                                        }
                                        context.startActivity(intent)
                                    }
                                }
                                .padding(vertical = 12.dp, horizontal = 24.dp)
                        ) {
                            if (!app.isSystem) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { checked ->
                                        selectedApps = if (checked) selectedApps + app.packageName else selectedApps - app.packageName
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = AuraCyan,
                                        uncheckedColor = TextSecondary
                                    )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }

                            val bitmap = remember(app.packageName) { app.icon.toSafeBitmap().asImageBitmap() }
                            Image(
                                bitmap = bitmap,
                                contentDescription = app.name,
                                modifier = Modifier.size(40.dp).clip(CircleShape)
                            )
                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(app.name, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, maxLines = 1)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(formatBytes(app.size), style = MaterialTheme.typography.bodySmall, color = AuraCyan)
                                    if (sortOption == "Usage") {
                                        Text(" • ", color = TextSecondary)
                                        Icon(Icons.Default.Schedule, contentDescription = null, tint = AuraOrange, modifier = Modifier.size(12.dp))
                                        val mins = (app.foregroundTimeMs / 60_000L).toInt()
                                        val usageLabel = if (!hasUsageAccess) " Grant Usage Access" else " ${mins}m (24h)"
                                        Text(usageLabel, style = MaterialTheme.typography.bodySmall, color = AuraOrange)
                                    } else if (sortOption == "Privacy") {
                                        Text(" • ", color = TextSecondary)
                                        Icon(Icons.Default.PrivacyTip, contentDescription = null, tint = AuraRose, modifier = Modifier.size(12.dp))
                                        Text(" ${app.dangerousPerms} risks", style = MaterialTheme.typography.bodySmall, color = AuraRose)
                                    } else {
                                        Text(" • ", color = TextSecondary)
                                        Text(if (app.isSystem) "System" else "User", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                    }
                                }
                            }

                            IconButton(
                                onClick = {
                                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.parse("package:${app.packageName}")
                                    }
                                    context.startActivity(intent)
                                }
                            ) {
                                Icon(Icons.Default.Info, contentDescription = "App Info", tint = TextSecondary)
                            }

                            if (!app.isSystem && !isSelectionMode) {
                                IconButton(
                                    onClick = {
                                        val intent = Intent(Intent.ACTION_DELETE).apply {
                                            data = Uri.parse("package:${app.packageName}")
                                        }
                                        context.startActivity(intent)
                                    }
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Uninstall ${app.name}", tint = AuraRose)
                                }
                            }
                        }
                        HorizontalDivider(color = Color.White.copy(alpha = 0.05f), modifier = Modifier.padding(horizontal = 24.dp))
                    }
                }
            }
        }
    }
}
