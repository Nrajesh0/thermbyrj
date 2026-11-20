package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.GlassCard
import com.example.formatBytes
import com.example.service.StorageScanCache
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class JunkFileItem(
    val file: File,
    val size: Long,
    val badge: String? = null
)

data class JunkCategory(
    val id: String,
    val title: String,
    val description: String,
    val items: List<JunkFileItem>,
    val totalSize: Long,
    val icon: ImageVector,
    val color: Color
)

class DeepCleanActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Obsidian) {
                    DeepCleanScreen(onBack = { finish() })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeepCleanScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isScanning by remember { mutableStateOf(true) }
    var categories by remember { mutableStateOf<List<JunkCategory>>(emptyList()) }
    var selectedPaths by remember { mutableStateOf<Set<String>>(emptySet()) }
    var expandedCategories by remember { mutableStateOf<Set<String>>(emptySet()) }
    var scanTrigger by remember { mutableIntStateOf(0) }
    var hasManageStorage by remember {
        mutableStateOf(com.example.service.PermissionUtils.hasAllFilesPermission())
    }

    val manageStorageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        hasManageStorage = com.example.service.PermissionUtils.hasAllFilesPermission()
        scanTrigger++
    }

    val selectedBytes = remember(categories, selectedPaths) {
        categories.sumOf { cat ->
            cat.items.filter { it.file.absolutePath in selectedPaths }.sumOf { it.size }
        }
    }

    val selectedCount = remember(categories, selectedPaths) {
        categories.sumOf { cat ->
            cat.items.count { it.file.absolutePath in selectedPaths }
        }
    }

    fun openFilePreview(file: File) {
        if (file.isDirectory) return
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val ext = file.extension.lowercase()
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Preview File"))
        } catch (_: Exception) {
            Toast.makeText(context, "Cannot preview this item", Toast.LENGTH_SHORT).show()
        }
    }

    fun cleanFilesAndPruneEmptyParents(items: List<JunkFileItem>, rootDir: File): Pair<Int, Long> {
        var freed = 0L
        var removedCount = 0
        val uri = MediaStore.Files.getContentUri("external")

        items.forEach { item ->
            val f = item.file
            val len = item.size
            val parent = f.parentFile
            var deleted = false
            try {
                if (f.isFile) {
                    val rows = context.contentResolver.delete(
                        uri,
                        "${MediaStore.MediaColumns.DATA} = ?",
                        arrayOf(f.absolutePath)
                    )
                    if (rows > 0) deleted = true
                }
            } catch (_: Exception) {
            }
            if (f.exists()) {
                if (f.deleteRecursively()) deleted = true
            }
            if (deleted || !f.exists()) {
                freed += len
                removedCount++
                // Prune any parent directories that became empty after removing this junk file
                var ptr = parent
                while (ptr != null && ptr != rootDir && ptr.exists()) {
                    val children = ptr.listFiles()
                    if (children != null && children.isEmpty()) {
                        ptr.delete()
                        ptr = ptr.parentFile
                    } else {
                        break
                    }
                }
            }
        }
        if (removedCount > 0) {
            StorageScanCache.invalidate()
        }
        return removedCount to freed
    }

    LaunchedEffect(scanTrigger) {
        isScanning = true
        val scanResult = withContext(Dispatchers.IO) {
            val root = Environment.getExternalStorageDirectory()
            val apkItems = mutableListOf<JunkFileItem>()
            val tempItems = mutableListOf<JunkFileItem>()
            val logItems = mutableListOf<JunkFileItem>()
            val zeroByteItems = mutableListOf<JunkFileItem>()
            val cacheItems = mutableListOf<JunkFileItem>()
            val emptyFolders = mutableListOf<JunkFileItem>()

            fun getDirSize(dir: File): Long {
                var size = 0L
                dir.walkTopDown().forEach { if (it.isFile) size += it.length() }
                return size
            }

            // Returns true if this directory is effectively empty (contains no files and only empty subdirs)
            fun scanDirectory(dir: File): Boolean {
                val list = dir.listFiles() ?: return false
                if (list.isEmpty()) {
                    if (dir != root) {
                        emptyFolders.add(JunkFileItem(dir, 0L, "Empty dir"))
                    }
                    return true
                }

                var hasNonEmptyChild = false
                for (f in list) {
                    if (f.isDirectory) {
                        val nameLower = f.name.lowercase()
                        if (nameLower == ".thumbnails" || nameLower == "cache" || nameLower == ".cache" || nameLower == ".trash") {
                            val dirSize = getDirSize(f)
                            if (dirSize > 0L) {
                                cacheItems.add(JunkFileItem(f, dirSize, "Cache folder"))
                            }
                            hasNonEmptyChild = true
                        } else if (!f.name.startsWith(".") && f.name != "Android") {
                            val childEmpty = scanDirectory(f)
                            if (!childEmpty) hasNonEmptyChild = true
                        } else {
                            hasNonEmptyChild = true
                        }
                    } else {
                        val len = f.length()
                        val nameLower = f.name.lowercase()
                        val ext = f.extension.lowercase()

                        // Never touch hidden marker files like .nomedia (0-byte media-scanner shields)
                        if (f.name.startsWith(".")) {
                            hasNonEmptyChild = true
                            continue
                        }

                        when {
                            ext == "apk" || ext == "xapk" || ext == "apks" -> {
                                val badge = inspectApkBadge(context, f)
                                apkItems.add(JunkFileItem(f, len, badge))
                                hasNonEmptyChild = true
                            }
                            len == 0L || ext == "crdownload" || ext == "part" || ext == "download" -> {
                                val badge = if (len == 0L) "0 B file" else "Incomplete"
                                zeroByteItems.add(JunkFileItem(f, len, badge))
                                hasNonEmptyChild = true
                            }
                            ext == "tmp" || ext == "temp" || ext == "bak" || ext == "old" || ext == "swp" || nameLower.endsWith("~") -> {
                                tempItems.add(JunkFileItem(f, len, ".${ext.ifEmpty { "tmp" }}"))
                                hasNonEmptyChild = true
                            }
                            ext == "log" || nameLower.startsWith("logcat") -> {
                                logItems.add(JunkFileItem(f, len, "Log"))
                                hasNonEmptyChild = true
                            }
                            else -> {
                                hasNonEmptyChild = true
                            }
                        }
                    }
                }

                if (!hasNonEmptyChild && dir != root) {
                    emptyFolders.add(JunkFileItem(dir, 0L, "Nested empty"))
                    return true
                }
                return false
            }

            scanDirectory(root)

            val builtCategories = mutableListOf<JunkCategory>()
            if (apkItems.isNotEmpty()) {
                val sorted = apkItems.sortedByDescending { it.size }
                builtCategories.add(
                    JunkCategory(
                        id = "apks",
                        title = "APK Installers",
                        description = "Installed/outdated APKs auto-selected; review newer/uninstalled",
                        items = sorted,
                        totalSize = sorted.sumOf { it.size },
                        icon = Icons.Default.Android,
                        color = AuraGreen
                    )
                )
            }
            if (cacheItems.isNotEmpty()) {
                val sorted = cacheItems.sortedByDescending { it.size }
                builtCategories.add(
                    JunkCategory(
                        id = "cache",
                        title = "Thumbnails & Media Cache",
                        description = "Cached previews (.thumbnails, .cache, .trash)",
                        items = sorted,
                        totalSize = sorted.sumOf { it.size },
                        icon = Icons.Default.Cached,
                        color = AuraRose
                    )
                )
            }
            if (tempItems.isNotEmpty()) {
                val sorted = tempItems.sortedByDescending { it.size }
                builtCategories.add(
                    JunkCategory(
                        id = "temp",
                        title = "Temporary & Backup Files",
                        description = ".tmp, .bak, .old & residual swap files",
                        items = sorted,
                        totalSize = sorted.sumOf { it.size },
                        icon = Icons.Default.CleaningServices,
                        color = AuraOrange
                    )
                )
            }
            if (logItems.isNotEmpty()) {
                val sorted = logItems.sortedByDescending { it.size }
                builtCategories.add(
                    JunkCategory(
                        id = "logs",
                        title = "Log & Crash Reports",
                        description = "Debug logs (.log) left by apps",
                        items = sorted,
                        totalSize = sorted.sumOf { it.size },
                        icon = Icons.AutoMirrored.Filled.Article,
                        color = AuraPurple
                    )
                )
            }
            if (zeroByteItems.isNotEmpty()) {
                val sorted = zeroByteItems.sortedByDescending { it.size }
                builtCategories.add(
                    JunkCategory(
                        id = "zerobyte",
                        title = "0-Byte & Incomplete Files",
                        description = "Empty files & failed .part/.crdownload files",
                        items = sorted,
                        totalSize = sorted.sumOf { it.size },
                        icon = Icons.Default.BrokenImage,
                        color = AuraCyan
                    )
                )
            }
            if (emptyFolders.isNotEmpty()) {
                builtCategories.add(
                    JunkCategory(
                        id = "empty_dirs",
                        title = "Empty Folders",
                        description = "Empty directories & nested empty trees",
                        items = emptyFolders,
                        totalSize = 0L,
                        icon = Icons.Default.FolderOff,
                        color = AuraCyan
                    )
                )
            }

            val safeAutoSelectedPaths = builtCategories.flatMap { cat ->
                if (cat.id == "apks") {
                    cat.items.filter { it.badge == "Already Installed" || it.badge == "Outdated APK" }
                } else {
                    cat.items
                }
            }.map { it.file.absolutePath }.toSet()
            builtCategories.toList() to safeAutoSelectedPaths
        }
        categories = scanResult.first
        selectedPaths = scanResult.second
        isScanning = false
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Deep Clean", color = TextPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                actions = {
                    if (!isScanning) {
                        IconButton(onClick = { scanTrigger++ }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Rescan", tint = AuraCyan)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        bottomBar = {
            if (!isScanning && categories.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(GlassPanel)
                        .padding(horizontal = 24.dp, vertical = 14.dp)
                        .navigationBarsPadding()
                ) {
                    Button(
                        enabled = selectedCount > 0,
                        onClick = {
                            val itemsToClean = categories.flatMap { cat ->
                                cat.items.filter { it.file.absolutePath in selectedPaths }
                            }
                            if (itemsToClean.isEmpty()) return@Button
                            isScanning = true
                            scope.launch(Dispatchers.IO) {
                                val root = Environment.getExternalStorageDirectory()
                                val (removedCount, freedBytes) = cleanFilesAndPruneEmptyParents(itemsToClean, root)
                                if (removedCount > 0) StorageScanCache.invalidate()
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(
                                        context,
                                        "Cleaned $removedCount items (${formatBytes(freedBytes)} freed)",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    scanTrigger++
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AuraOrange),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.CleaningServices, contentDescription = null, tint = Color.Black)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Clean $selectedCount Selected (${formatBytes(selectedBytes)})",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    ) { padding ->
        if (isScanning) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = AuraOrange)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Scanning storage for residual junk...", color = TextSecondary)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 24.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (!hasManageStorage && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    item {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, tint = AuraOrange)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("All Files Access", color = TextPrimary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                    Text("Grant access to detect APKs, empty folders, .tmp & .log files.", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        try {
                                            val intent = Intent(
                                                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                                Uri.parse("package:${context.packageName}")
                                            )
                                            manageStorageLauncher.launch(intent)
                                        } catch (_: Throwable) {
                                            com.example.service.PermissionUtils.launchAllFilesAccessSettings(context)
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = AuraOrange)
                                ) {
                                    Text("Grant", color = Color.Black, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                if (categories.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 80.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = "System Clean",
                                    tint = AuraGreen,
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("System is Squeaky Clean!", color = TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("No obsolete APKs, temp files, logs, or empty folders found.", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                } else {
                    item {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("SELECTED TO CLEAN", color = TextTertiary, style = MaterialTheme.typography.labelSmall, letterSpacing = 1.2.sp)
                                    Text(formatBytes(selectedBytes), color = AuraOrange, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("$selectedCount Items", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                                    Text("Tap a card to review files", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }

                    items(categories, key = { it.id }) { cat ->
                        val isExpanded = cat.id in expandedCategories
                        val catPaths = remember(cat) { cat.items.map { it.file.absolutePath }.toSet() }
                        val checkedInCat = catPaths.count { it in selectedPaths }
                        val allChecked = checkedInCat == cat.items.size && cat.items.isNotEmpty()

                        GlassCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    expandedCategories = if (isExpanded) {
                                        expandedCategories - cat.id
                                    } else {
                                        expandedCategories + cat.id
                                    }
                                }
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = allChecked,
                                        onCheckedChange = { checked ->
                                            selectedPaths = if (checked) {
                                                selectedPaths + catPaths
                                            } else {
                                                selectedPaths - catPaths
                                            }
                                        },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = cat.color,
                                            uncheckedColor = TextTertiary,
                                            checkmarkColor = Color.Black
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(CircleShape)
                                            .background(cat.color.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(cat.icon, contentDescription = cat.title, tint = cat.color, modifier = Modifier.size(22.dp))
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(cat.title, color = TextPrimary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                                        Text(cat.description, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                                        Text(
                                            "$checkedInCat/${cat.items.size} selected · ${formatBytes(cat.totalSize)}",
                                            color = cat.color,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            val toDelete = cat.items.filter { it.file.absolutePath in selectedPaths }
                                                .ifEmpty { cat.items }
                                            scope.launch(Dispatchers.IO) {
                                                val root = Environment.getExternalStorageDirectory()
                                                val (cnt, freed) = cleanFilesAndPruneEmptyParents(toDelete, root)
                                                withContext(Dispatchers.Main) {
                                                    Toast.makeText(
                                                        context,
                                                        "Cleaned $cnt items from ${cat.title} (${formatBytes(freed)})",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                    scanTrigger++
                                                }
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Clean ${cat.title}", tint = cat.color)
                                    }
                                    Icon(
                                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                                        tint = TextSecondary
                                    )
                                }

                                AnimatedVisibility(visible = isExpanded) {
                                    Column(modifier = Modifier.padding(top = 10.dp)) {
                                        HorizontalDivider(color = GlassBorder)
                                        Spacer(modifier = Modifier.height(6.dp))
                                        cat.items.take(50).forEach { item ->
                                            val path = item.file.absolutePath
                                            val itemChecked = path in selectedPaths
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { openFilePreview(item.file) }
                                                    .padding(vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Checkbox(
                                                    checked = itemChecked,
                                                    onCheckedChange = { checked ->
                                                        selectedPaths = if (checked) {
                                                            selectedPaths + path
                                                        } else {
                                                            selectedPaths - path
                                                        }
                                                    },
                                                    colors = CheckboxDefaults.colors(
                                                        checkedColor = cat.color,
                                                        uncheckedColor = TextTertiary,
                                                        checkmarkColor = Color.Black
                                                    )
                                                )
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text(
                                                            item.file.name,
                                                            color = TextPrimary,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            fontWeight = FontWeight.Medium,
                                                            modifier = Modifier.weight(1f, fill = false)
                                                        )
                                                        if (item.badge != null) {
                                                            Spacer(modifier = Modifier.width(6.dp))
                                                            Text(
                                                                item.badge,
                                                                color = cat.color,
                                                                fontSize = 9.sp,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }
                                                    }
                                                    Text(
                                                        item.file.parent ?: "",
                                                        color = TextTertiary,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        style = MaterialTheme.typography.labelSmall
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    formatBytes(item.size),
                                                    color = TextSecondary,
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                            }
                                        }
                                        if (cat.items.size > 50) {
                                            Text(
                                                "+ ${cat.items.size - 50} more items",
                                                color = TextTertiary,
                                                style = MaterialTheme.typography.labelSmall,
                                                modifier = Modifier.padding(start = 12.dp, top = 4.dp)
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
    }
}

private fun inspectApkBadge(context: Context, apkFile: File): String {
    return try {
        val pm = context.packageManager
        val archiveInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageArchiveInfo(apkFile.absolutePath, 0)
        } ?: return "APK Installer"

        val pkgName = archiveInfo.packageName
        val archiveVer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            archiveInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            archiveInfo.versionCode.toLong()
        }

        val installedInfo = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(pkgName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(pkgName, 0)
            }
        } catch (_: Exception) {
            null
        }

        if (installedInfo == null) {
            "Not Installed"
        } else {
            val installedVer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                installedInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                installedInfo.versionCode.toLong()
            }
            when {
                archiveVer < installedVer -> "Outdated APK"
                archiveVer == installedVer -> "Already Installed"
                else -> "Newer APK"
            }
        }
    } catch (_: Exception) {
        "APK Installer"
    }
}
