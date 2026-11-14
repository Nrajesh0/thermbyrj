package com.example.ui.screens

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Process
import android.os.StatFs
import android.os.storage.StorageManager
import android.provider.MediaStore
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FilterNone
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.GlassCard
import com.example.formatBytes
import com.example.ui.theme.AuraCyan
import com.example.ui.theme.AuraGreen
import com.example.ui.theme.AuraOrange
import com.example.ui.theme.AuraPurple
import com.example.ui.theme.AuraRose
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.min

data class StorageCategory(
    val name: String,
    val size: Long,
    val color: Color,
    val icon: ImageVector
)

private fun hasUsageStatsPermission(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
    } else {
        @Suppress("DEPRECATION")
        appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
    }
    return mode == AppOpsManager.MODE_ALLOWED
}

private fun hasMediaPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageAnalysisScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var categories by remember { mutableStateOf<List<StorageCategory>>(emptyList()) }
    var totalSpace by remember { mutableLongStateOf(0L) }
    var usedSpace by remember { mutableLongStateOf(0L) }
    var freeSpace by remember { mutableLongStateOf(0L) }
    var isAnalyzing by remember { mutableStateOf(true) }
    var hasUsageAccess by remember { mutableStateOf(hasUsageStatsPermission(context)) }
    var hasManageStorage by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager() else true
        )
    }
    var hasPermission by remember { mutableStateOf(hasMediaPermission(context)) }
    var hasRequestedOnce by remember { mutableStateOf(false) }
    var analysisTrigger by remember { mutableIntStateOf(0) }

    val manageStorageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        hasManageStorage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager() else true
        analysisTrigger++
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasPermission = hasMediaPermission(context)
        hasRequestedOnce = true
        analysisTrigger++
    }

    val usageAccessLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        hasUsageAccess = hasUsageStatsPermission(context)
        analysisTrigger++
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val newUsage = hasUsageStatsPermission(context)
                val newManage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager() else true
                val newMedia = hasMediaPermission(context)
                if (newUsage != hasUsageAccess || newManage != hasManageStorage || newMedia != hasPermission) {
                    hasUsageAccess = newUsage
                    hasManageStorage = newManage
                    hasPermission = newMedia
                    analysisTrigger++
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        if (!hasPermission && !hasRequestedOnce) {
            hasRequestedOnce = true
            val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                arrayOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.READ_MEDIA_AUDIO
                )
            } else {
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            permissionLauncher.launch(perms)
        }
    }

    LaunchedEffect(analysisTrigger, hasPermission, hasUsageAccess) {
        isAnalyzing = true
        withContext(Dispatchers.IO) {
            val statFs = StatFs(Environment.getDataDirectory().path)
            val tot = statFs.totalBytes
            val free = statFs.availableBytes
            val used = max(0L, tot - free)

            val imageSize = if (hasPermission) getMediaSize(context, MediaStore.Images.Media.EXTERNAL_CONTENT_URI) else 0L
            val videoSize = if (hasPermission) getMediaSize(context, MediaStore.Video.Media.EXTERNAL_CONTENT_URI) else 0L
            val audioSize = if (hasPermission) getMediaSize(context, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI) else 0L
            val docsSize = if (hasPermission) getDocumentSize(context) else 0L

            val mediaTotal = imageSize + videoSize + audioSize + docsSize
            val otherUsed = max(0L, used - mediaTotal)

            var exactAppSize = 0L
            if (hasUsageStatsPermission(context)) {
                try {
                    val storageStatsManager = context.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
                    val pm = context.packageManager
                    val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                    for (app in packages) {
                        try {
                            val stats = storageStatsManager.queryStatsForUid(StorageManager.UUID_DEFAULT, app.uid)
                            exactAppSize += stats.appBytes + stats.dataBytes + stats.cacheBytes
                        } catch (_: Exception) {
                        }
                    }
                } catch (_: Exception) {
                }
            }

            val appSize = if (exactAppSize > 0L) min(exactAppSize, otherUsed) else (otherUsed * 0.4).toLong()
            val systemSize = max(0L, otherUsed - appSize)

            totalSpace = tot
            freeSpace = free
            usedSpace = used
            categories = listOf(
                StorageCategory("Images", imageSize, AuraCyan, Icons.Default.Image),
                StorageCategory("Videos", videoSize, AuraOrange, Icons.Default.VideoLibrary),
                StorageCategory("Audio", audioSize, AuraRose, Icons.Default.AudioFile),
                StorageCategory("Documents", max(0L, docsSize), AuraGreen, Icons.Default.Description),
                StorageCategory("Apps & Data", appSize, AuraPurple, Icons.Default.Apps),
                StorageCategory("System", systemSize, Color.Gray, Icons.Default.SystemUpdate)
            ).sortedByDescending { it.size }
            isAnalyzing = false
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Storage Analysis", color = TextPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        if (isAnalyzing) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = AuraCyan)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Analyzing File System...",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    StorageDonutChart(totalSpace, usedSpace, freeSpace, categories)
                }

                if (!hasPermission) {
                    item {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, tint = AuraCyan)
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Media Access Needed", color = TextPrimary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                    Text("Grant media permissions to scan Images, Videos, Audio & Docs.", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            arrayOf(
                                                Manifest.permission.READ_MEDIA_IMAGES,
                                                Manifest.permission.READ_MEDIA_VIDEO,
                                                Manifest.permission.READ_MEDIA_AUDIO
                                            )
                                        } else {
                                            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                                        }
                                        permissionLauncher.launch(perms)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = AuraCyan)
                                ) {
                                    Text("Grant", color = Color.Black)
                                }
                            }
                        }
                    }
                }

                if (!hasManageStorage && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    item {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Settings, contentDescription = null, tint = AuraRose)
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("All Files Access", color = TextPrimary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                    Text("Grant All Files Access for Deep Clean & Secure Wipe.", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        val intent = Intent(
                                            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                            Uri.parse("package:${context.packageName}")
                                        )
                                        manageStorageLauncher.launch(intent)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = AuraRose)
                                ) {
                                    Text("Grant", color = Color.White)
                                }
                            }
                        }
                    }
                }

                if (!hasUsageAccess) {
                    item {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Settings, contentDescription = null, tint = AuraOrange)
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Precise App Storage", color = TextPrimary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                    Text("Grant Usage Access for exact app sizes.", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        usageAccessLauncher.launch(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = AuraOrange)
                                ) {
                                    Text("Grant", color = Color.White)
                                }
                            }
                        }
                    }
                }

                item {
                    Text("ADVANCED TOOLS", style = MaterialTheme.typography.labelSmall, color = TextTertiary, letterSpacing = 2.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        GlassCard(
                            modifier = Modifier.weight(1f).clickable {
                                context.startActivity(Intent(context, DuplicateFinderActivity::class.java))
                            }
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                Icon(Icons.Default.FilterNone, contentDescription = "Duplicate Finder", tint = AuraCyan, modifier = Modifier.size(28.dp))
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("Duplicate\nFinder", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp), color = TextPrimary, textAlign = TextAlign.Center)
                            }
                        }

                        GlassCard(
                            modifier = Modifier.weight(1f).clickable {
                                context.startActivity(Intent(context, DeepCleanActivity::class.java))
                            }
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                Icon(Icons.Default.CleaningServices, contentDescription = "Deep Clean", tint = AuraOrange, modifier = Modifier.size(28.dp))
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("Deep\nClean", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp), color = TextPrimary, textAlign = TextAlign.Center)
                            }
                        }

                        GlassCard(
                            modifier = Modifier.weight(1f).clickable {
                                context.startActivity(Intent(context, VaultActivity::class.java))
                            }
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                Icon(Icons.Default.Lock, contentDescription = "Secure Vault", tint = AuraRose, modifier = Modifier.size(28.dp))
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("Secure\nVault", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp), color = TextPrimary, textAlign = TextAlign.Center)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                item {
                    Text("CATEGORIES", style = MaterialTheme.typography.labelSmall, color = TextTertiary, letterSpacing = 2.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                }

                items(categories.size) { index ->
                    val cat = categories[index]
                    CategoryItemRow(cat, totalSpace) {
                        if (cat.name in listOf("Images", "Videos", "Audio", "Documents")) {
                            val intent = Intent(context, FileCategoryActivity::class.java).apply {
                                putExtra("CATEGORY_NAME", cat.name)
                            }
                            context.startActivity(intent)
                        } else if (cat.name == "Apps & Data") {
                            val intent = Intent(context, AppListActivity::class.java)
                            context.startActivity(intent)
                        }
                    }
                }

                item { Spacer(modifier = Modifier.navigationBarsPadding().height(32.dp)) }
            }
        }
    }
}

private fun getMediaSize(context: Context, uri: Uri): Long {
    var size = 0L
    try {
        val projection = arrayOf(MediaStore.MediaColumns.SIZE)
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            while (cursor.moveToNext()) {
                size += max(0L, cursor.getLong(sizeColumn))
            }
        }
    } catch (_: Exception) {
    }
    return size
}

private fun getDocumentSize(context: Context): Long {
    var size = 0L
    try {
        val uri = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATA
        )
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val nameColumn = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
            val dataColumn = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
            val docExtensions = setOf(
                "pdf", "txt", "doc", "docx", "ppt", "pptx", "xls", "xlsx",
                "csv", "rtf", "zip", "apk", "json", "xml", "md", "epub"
            )
            while (cursor.moveToNext()) {
                val path = if (dataColumn >= 0) cursor.getString(dataColumn) ?: "" else ""
                val displayName = if (nameColumn >= 0) cursor.getString(nameColumn) ?: "" else ""
                val candidate = path.ifBlank { displayName }
                val ext = File(candidate).extension.lowercase()
                if (ext in docExtensions) {
                    size += max(0L, cursor.getLong(sizeColumn))
                }
            }
        }
    } catch (_: Exception) {
    }
    return size
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StorageDonutChart(total: Long, used: Long, free: Long, categories: List<StorageCategory>) {
    val safeTotal = max(1L, total)
    val usedPct = ((used.toFloat() / safeTotal.toFloat()) * 100f).toInt().coerceIn(0, 100)

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(200.dp)) {
                Canvas(modifier = Modifier.size(160.dp)) {
                    val strokeWidth = 20.dp.toPx()
                    drawCircle(
                        color = GlassBorder,
                        radius = size.width / 2,
                        style = Stroke(width = strokeWidth)
                    )

                    if (total > 0L) {
                        var startAngle = -90f
                        categories.forEach { cat ->
                            val sweepAngle = (cat.size.toFloat() / safeTotal.toFloat()) * 360f
                            if (sweepAngle > 2.5f) {
                                drawArc(
                                    color = cat.color,
                                    startAngle = startAngle,
                                    sweepAngle = sweepAngle - 2f,
                                    useCenter = false,
                                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                                )
                                startAngle += sweepAngle
                            }
                        }
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$usedPct%",
                        style = MaterialTheme.typography.headlineLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text("USED", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("TOTAL", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                    Text(formatBytes(total), style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("USED", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                    Text(formatBytes(used), style = MaterialTheme.typography.bodyLarge, color = AuraOrange)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("FREE", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                    Text(formatBytes(free), style = MaterialTheme.typography.bodyLarge, color = AuraCyan)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.filter { it.size > 0 }.forEach { cat ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(cat.color))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(cat.name, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryItemRow(cat: StorageCategory, totalSpace: Long, onClick: () -> Unit = {}) {
    val isInteractive = cat.name in listOf("Images", "Videos", "Audio", "Documents", "Apps & Data")
    val safeTotal = max(1L, totalSpace)
    val modifier = if (isInteractive) {
        Modifier.clickable { onClick() }
    } else {
        Modifier
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(modifier)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(cat.color.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(cat.icon, contentDescription = cat.name, tint = cat.color, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(cat.name, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { (cat.size.toFloat() / safeTotal.toFloat()).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                color = cat.color,
                trackColor = Color.White.copy(alpha = 0.05f),
                strokeCap = StrokeCap.Round
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(formatBytes(cat.size), style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
    }
}
