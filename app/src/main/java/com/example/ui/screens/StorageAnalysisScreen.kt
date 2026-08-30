package com.example.ui.screens

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.Process
import android.os.StatFs
import android.net.Uri
import android.os.storage.StorageManager
import android.provider.MediaStore
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import com.example.ui.screens.VaultActivity
import com.example.ui.screens.DuplicateFinderActivity
import com.example.ui.screens.DeepCleanActivity
import androidx.compose.material.icons.filled.FilterNone
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Lock
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.GlassCard
import com.example.formatBytes
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
        appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
    }
    return mode == AppOpsManager.MODE_ALLOWED
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageAnalysisScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var categories by remember { mutableStateOf<List<StorageCategory>>(emptyList()) }
    var totalSpace by remember { mutableStateOf(0L) }
    var usedSpace by remember { mutableStateOf(0L) }
    var freeSpace by remember { mutableStateOf(0L) }
    var isAnalyzing by remember { mutableStateOf(true) }
    var hasUsageAccess by remember { mutableStateOf(hasUsageStatsPermission(context)) }
    var hasManageStorage by remember { 
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager() else true
        )
    }

    val manageStorageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        hasManageStorage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager() else true
    }

    var hasPermission by remember { 
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
            } else {
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasPermission = permissions.values.all { it }
    }

    val usageAccessLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        hasUsageAccess = hasUsageStatsPermission(context)
        if (hasUsageAccess) {
            isAnalyzing = true
        }
    }

    LaunchedEffect(hasPermission, hasUsageAccess, isAnalyzing) {
        if (!hasPermission) {
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
        } else if (isAnalyzing) {
            withContext(Dispatchers.IO) {
                val statFs = StatFs(Environment.getDataDirectory().path)
                totalSpace = statFs.totalBytes
                freeSpace = statFs.availableBytes
                usedSpace = totalSpace - freeSpace

                val imageSize = getMediaSize(context, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                val videoSize = getMediaSize(context, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
                val audioSize = getMediaSize(context, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)
                val docsSize = getDocumentSize(context)

                val otherUsed = maxOf(0L, usedSpace - (imageSize + videoSize + audioSize + docsSize))
                
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
                            } catch (e: Exception) {}
                        }
                    } catch (e: Exception) {}
                }

                val appSize = if (exactAppSize > 0) exactAppSize else (otherUsed * 0.4).toLong() 
                val systemSize = maxOf(0L, otherUsed - appSize)

                categories = listOf(
                    StorageCategory("Images", imageSize, AuraCyan, Icons.Default.Image),
                    StorageCategory("Videos", videoSize, AuraOrange, Icons.Default.VideoLibrary),
                    StorageCategory("Audio", audioSize, AuraRose, Icons.Default.AudioFile),
                    StorageCategory("Documents", maxOf(0L, docsSize), AuraGreen, Icons.Default.Description),
                    StorageCategory("Apps & Data", appSize, AuraPurple, Icons.Default.Apps),
                    StorageCategory("System", systemSize, Color.Gray, Icons.Default.SystemUpdate)
                ).sortedByDescending { it.size }

                kotlinx.coroutines.delay(800)
                isAnalyzing = false
            }
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Storage Analysis", color = TextPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        if (isAnalyzing) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = AuraCyan)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (!hasPermission) "Waiting for permissions..." else "Analyzing File System...", 
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

                if (!hasUsageAccess) {
                if (!hasManageStorage && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    item {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Settings, contentDescription = null, tint = AuraRose)
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("God Mode Storage", color = TextPrimary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                    Text("Grant full access for Secure Deletion.", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = { 
                                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))
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
                        com.example.GlassCard(
                            modifier = Modifier.weight(1f).clickable {
                                context.startActivity(Intent(context, DuplicateFinderActivity::class.java))
                            }
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                                Icon(Icons.Default.FilterNone, contentDescription = "Duplicate Finder", tint = AuraCyan, modifier = Modifier.size(28.dp))
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("Duplicate\nFinder", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp), color = TextPrimary, textAlign = TextAlign.Center)
                            }
                        }
                        
                        com.example.GlassCard(
                            modifier = Modifier.weight(1f).clickable {
                                context.startActivity(Intent(context, DeepCleanActivity::class.java))
                            }
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                                Icon(Icons.Default.CleaningServices, contentDescription = "Deep Clean", tint = AuraOrange, modifier = Modifier.size(28.dp))
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("Deep\nClean", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp), color = TextPrimary, textAlign = TextAlign.Center)
                            }
                        }
                        
                        com.example.GlassCard(
                            modifier = Modifier.weight(1f).clickable {
                                context.startActivity(Intent(context, VaultActivity::class.java))
                            }
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                                Icon(Icons.Default.Lock, contentDescription = "Secure Vault", tint = AuraRose, modifier = Modifier.size(28.dp))
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("Secure\nVault", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp), color = TextPrimary, textAlign = TextAlign.Center)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
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
                
                item { Spacer(modifier = Modifier.height(32.dp)) }
            }
        }
    }
}

private fun getMediaSize(context: Context, uri: android.net.Uri): Long {
    var size = 0L
    try {
        val projection = arrayOf(MediaStore.MediaColumns.SIZE)
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            while (cursor.moveToNext()) {
                size += cursor.getLong(sizeColumn)
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return size
}

private fun getDocumentSize(context: Context): Long {
    var size = 0L
    try {
        val uri = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.DATA)
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
            val docExtensions = setOf("pdf", "txt", "doc", "docx", "ppt", "pptx", "xls", "xlsx", "csv", "rtf")
            while (cursor.moveToNext()) {
                val path = cursor.getString(dataColumn) ?: ""
                val ext = java.io.File(path).extension.lowercase()
                if (ext in docExtensions) {
                    size += cursor.getLong(sizeColumn)
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return size
}

@Composable
fun StorageDonutChart(total: Long, used: Long, free: Long, categories: List<StorageCategory>) {
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

                    var startAngle = -90f
                    categories.forEach { cat ->
                        val sweepAngle = (cat.size.toFloat() / total) * 360f
                        if (sweepAngle > 1f) {
                            drawArc(
                                color = cat.color,
                                startAngle = startAngle,
                                sweepAngle = sweepAngle - 2f, // Small gap
                                useCenter = false,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                            )
                            startAngle += sweepAngle
                        }
                    }
                }
                
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${((used.toFloat() / total) * 100).toInt()}%",
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
        }
    }
}

@Composable
fun CategoryItemRow(cat: StorageCategory, totalSpace: Long, onClick: () -> Unit = {}) {
    val modifier = if (cat.name in listOf("Images", "Videos", "Audio", "Documents", "Apps & Data")) {
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
                progress = { (cat.size.toFloat() / totalSpace).coerceIn(0f, 1f) },
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
