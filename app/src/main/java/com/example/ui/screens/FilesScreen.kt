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
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterNone
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.security.crypto.EncryptedFile
import com.example.GlassCard
import com.example.InfoRow
import com.example.formatBytes
import com.example.queryDeviceStorageTotals
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
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.math.max
import kotlin.math.min

data class BrowserFileItem(
    val file: File,
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModified: Long,
    val childCount: Int
)

private enum class ClipboardOperation {
    COPY, MOVE
}

private data class FileClipboard(
    val paths: Set<String>,
    val operation: ClipboardOperation
)

private fun checkUsageStatsPermission(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
    } else {
        @Suppress("DEPRECATION")
        appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
    }
    return mode == AppOpsManager.MODE_ALLOWED
}

private fun checkMediaPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }
}

@Volatile
private var cachedFilesTotal: Long = 0L
@Volatile
private var cachedFilesUsed: Long = 0L
@Volatile
private var cachedFilesFree: Long = 0L
@Volatile
private var cachedFilesCategories: List<StorageCategory> = emptyList()

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilesScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val bookmarkPrefs = remember { context.getSharedPreferences("file_bookmarks_prefs", Context.MODE_PRIVATE) }

    var activeSubTab by remember { mutableStateOf("SMART_HUB") } // "SMART_HUB" or "FOLDER_BROWSER"
    var categories by remember { mutableStateOf(cachedFilesCategories) }
    var totalSpace by remember { mutableLongStateOf(cachedFilesTotal) }
    var usedSpace by remember { mutableLongStateOf(cachedFilesUsed) }
    var freeSpace by remember { mutableLongStateOf(cachedFilesFree) }
    var isAnalyzing by remember { mutableStateOf(cachedFilesCategories.isEmpty()) }

    var hasUsageAccess by remember { mutableStateOf(checkUsageStatsPermission(context)) }
    var hasManageStorage by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager() else true
        )
    }
    var hasPermission by remember { mutableStateOf(checkMediaPermission(context)) }
    var refreshTrigger by remember { mutableIntStateOf(0) }

    // Folder Browser state
    val rootDir = remember {
        val ext = Environment.getExternalStorageDirectory()
        if (ext != null && ext.exists()) ext else context.filesDir
    }
    var currentDir by remember { mutableStateOf(rootDir) }
    var browserItems by remember { mutableStateOf<List<BrowserFileItem>>(emptyList()) }
    var isBrowserLoading by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var sortBy by remember { mutableStateOf("Date") } // "Name", "Size", "Date"
    var showHiddenFiles by remember { mutableStateOf(false) }
    var showOnlyStarred by remember { mutableStateOf(false) }
    var starredPaths by remember {
        mutableStateOf(bookmarkPrefs.getStringSet("starred_paths", emptySet()) ?: emptySet())
    }

    var selectedPaths by remember { mutableStateOf<Set<String>>(emptySet()) }
    var clipboard by remember { mutableStateOf<FileClipboard?>(null) }

    // Action sheet & Dialog states
    var itemForMenu by remember { mutableStateOf<BrowserFileItem?>(null) }
    var itemForDetails by remember { mutableStateOf<BrowserFileItem?>(null) }
    var itemForRename by remember { mutableStateOf<BrowserFileItem?>(null) }
    var renameInput by remember { mutableStateOf("") }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    fun toggleStar(path: String) {
        val updated = if (path in starredPaths) starredPaths - path else starredPaths + path
        starredPaths = updated
        bookmarkPrefs.edit().putStringSet("starred_paths", updated).apply()
    }

    BackHandler(enabled = activeSubTab == "FOLDER_BROWSER" && (selectedPaths.isNotEmpty() || currentDir.absolutePath != rootDir.absolutePath)) {
        if (selectedPaths.isNotEmpty()) {
            selectedPaths = emptySet()
        } else {
            currentDir.parentFile?.let { parent ->
                if (parent.absolutePath.startsWith(rootDir.absolutePath)) {
                    currentDir = parent
                }
            }
        }
    }

    val manageStorageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        hasManageStorage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager() else true
        refreshTrigger++
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasPermission = checkMediaPermission(context)
        refreshTrigger++
    }

    val usageAccessLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        hasUsageAccess = checkUsageStatsPermission(context)
        refreshTrigger++
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasUsageAccess = checkUsageStatsPermission(context)
                hasManageStorage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager() else true
                hasPermission = checkMediaPermission(context)
                refreshTrigger++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(refreshTrigger, hasPermission, hasUsageAccess, hasManageStorage) {
        if (categories.isEmpty()) isAnalyzing = true
        withContext(Dispatchers.IO) {
            val (tot, free) = queryDeviceStorageTotals(context)
            val used = max(0L, tot - free)

            val imageSize = if (hasPermission || hasManageStorage) queryMediaSize(context, MediaStore.Images.Media.EXTERNAL_CONTENT_URI) else 0L
            val videoSize = if (hasPermission || hasManageStorage) queryMediaSize(context, MediaStore.Video.Media.EXTERNAL_CONTENT_URI) else 0L
            val audioSize = if (hasPermission || hasManageStorage) queryMediaSize(context, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI) else 0L
            val docsSize = if (hasPermission || hasManageStorage) queryDocsSize(context) else 0L

            val mediaTotal = imageSize + videoSize + audioSize + docsSize
            val otherUsed = max(0L, used - mediaTotal)

            var exactAppSize = 0L
            if (checkUsageStatsPermission(context)) {
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

            val newCats = listOf(
                StorageCategory("Images", imageSize, AuraCyan, Icons.Default.Image),
                StorageCategory("Videos", videoSize, AuraOrange, Icons.Default.VideoLibrary),
                StorageCategory("Audio", audioSize, AuraRose, Icons.Default.AudioFile),
                StorageCategory("Documents", max(0L, docsSize), AuraGreen, Icons.Default.Description),
                StorageCategory("Apps & Data", appSize, AuraPurple, Icons.Default.Apps),
                StorageCategory("System", systemSize, Color.Gray, Icons.Default.SystemUpdate)
            ).sortedByDescending { it.size }

            cachedFilesTotal = tot
            cachedFilesFree = free
            cachedFilesUsed = used
            cachedFilesCategories = newCats

            totalSpace = tot
            freeSpace = free
            usedSpace = used
            categories = newCats
            isAnalyzing = false
        }
    }

    LaunchedEffect(currentDir, refreshTrigger, activeSubTab, showHiddenFiles, showOnlyStarred, starredPaths) {
        if (activeSubTab == "FOLDER_BROWSER") {
            isBrowserLoading = true
            browserItems = withContext(Dispatchers.IO) {
                if (showOnlyStarred) {
                    starredPaths.mapNotNull { path ->
                        val f = File(path)
                        if (f.exists()) toBrowserFileItem(f) else null
                    }
                } else {
                    loadDirectoryContents(currentDir, showHiddenFiles)
                }
            }
            isBrowserLoading = false
        }
    }

    val filteredBrowserItems = remember(browserItems, searchQuery, sortBy) {
        val searched = if (searchQuery.isBlank()) {
            browserItems
        } else {
            browserItems.filter { it.name.contains(searchQuery.trim(), ignoreCase = true) }
        }
        searched.sortedWith { a, b ->
            if (a.isDirectory != b.isDirectory) {
                if (a.isDirectory) -1 else 1
            } else {
                when (sortBy) {
                    "Size" -> b.sizeBytes.compareTo(a.sizeBytes)
                    "Name" -> a.name.lowercase().compareTo(b.name.lowercase())
                    else -> b.lastModified.compareTo(a.lastModified)
                }
            }
        }
    }

    // Create Folder Dialog
    if (showCreateFolderDialog) {
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            containerColor = GlassPanel,
            title = { Text("New Folder", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    label = { Text("Folder name", color = TextSecondary) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AuraCyan,
                        unfocusedBorderColor = GlassBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = newFolderName.trim()
                        if (trimmed.isNotEmpty()) {
                            val created = try {
                                File(currentDir, trimmed).mkdirs()
                            } catch (_: Exception) {
                                false
                            }
                            Toast.makeText(
                                context,
                                if (created) "Folder created" else "Grant All Files Access first",
                                Toast.LENGTH_SHORT
                            ).show()
                            newFolderName = ""
                            showCreateFolderDialog = false
                            refreshTrigger++
                        }
                    }
                ) {
                    Text("CREATE", color = AuraCyan, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFolderDialog = false }) {
                    Text("CANCEL", color = TextSecondary)
                }
            }
        )
    }

    // Rename Dialog
    if (itemForRename != null) {
        val target = itemForRename!!
        AlertDialog(
            onDismissRequest = { itemForRename = null },
            containerColor = GlassPanel,
            title = { Text("Rename", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    label = { Text("New name", color = TextSecondary) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AuraCyan,
                        unfocusedBorderColor = GlassBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val newName = renameInput.trim()
                        if (newName.isNotEmpty() && newName != target.name) {
                            val dest = File(target.file.parentFile, newName)
                            val ok = try {
                                target.file.renameTo(dest)
                            } catch (_: Exception) {
                                false
                            }
                            Toast.makeText(context, if (ok) "Renamed to $newName" else "Rename failed", Toast.LENGTH_SHORT).show()
                            selectedPaths = emptySet()
                            refreshTrigger++
                        }
                        itemForRename = null
                    }
                ) {
                    Text("SAVE", color = AuraCyan, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { itemForRename = null }) {
                    Text("CANCEL", color = TextSecondary)
                }
            }
        )
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirm && selectedPaths.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = GlassPanel,
            title = { Text("Delete ${selectedPaths.size} Item(s)?", color = AuraRose, fontWeight = FontWeight.Bold) },
            text = { Text("This cannot be undone.", color = TextSecondary) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        coroutineScope.launch {
                            val deletedCount = withContext(Dispatchers.IO) {
                                var count = 0
                                selectedPaths.forEach { path ->
                                    val f = File(path)
                                    val ok = try {
                                        if (f.isDirectory) f.deleteRecursively() else f.delete()
                                    } catch (_: Exception) {
                                        false
                                    }
                                    if (ok) count++
                                }
                                count
                            }
                            Toast.makeText(context, "Deleted $deletedCount item(s)", Toast.LENGTH_SHORT).show()
                            selectedPaths = emptySet()
                            refreshTrigger++
                        }
                    }
                ) {
                    Text("DELETE", color = AuraRose, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("CANCEL", color = TextSecondary)
                }
            }
        )
    }

    // Simpler Single-Item Context Menu Sheet
    if (itemForMenu != null) {
        val target = itemForMenu!!
        val path = target.file.absolutePath
        val isStarred = path in starredPaths
        val isZip = !target.isDirectory && target.file.extension.equals("zip", ignoreCase = true)

        ModalBottomSheet(
            onDismissRequest = { itemForMenu = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Color(0xFF0F111A),
            contentColor = TextPrimary
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (target.isDirectory) Icons.Default.Folder else Icons.AutoMirrored.Filled.InsertDriveFile,
                        contentDescription = null,
                        tint = AuraCyan,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = target.name,
                            color = TextPrimary,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (target.isDirectory) "${target.childCount} items" else formatBytes(target.sizeBytes),
                            color = TextTertiary,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Clean Action Grid (2 rows of 4 simple icon actions)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    maxItemsInEachRow = 4
                ) {
                    SimpleSheetAction(Icons.Default.Edit, "Rename", AuraCyan) {
                        itemForMenu = null
                        renameInput = target.name
                        itemForRename = target
                    }
                    SimpleSheetAction(Icons.Default.ContentCopy, "Copy", AuraCyan) {
                        clipboard = FileClipboard(setOf(path), ClipboardOperation.COPY)
                        itemForMenu = null
                        Toast.makeText(context, "Copied — open destination folder to paste", Toast.LENGTH_SHORT).show()
                    }
                    SimpleSheetAction(Icons.AutoMirrored.Filled.DriveFileMove, "Move", AuraOrange) {
                        clipboard = FileClipboard(setOf(path), ClipboardOperation.MOVE)
                        itemForMenu = null
                        Toast.makeText(context, "Ready to move — open destination folder", Toast.LENGTH_SHORT).show()
                    }
                    SimpleSheetAction(
                        icon = if (isStarred) Icons.Default.Star else Icons.Default.StarBorder,
                        label = if (isStarred) "Unstar" else "Star",
                        tint = AuraOrange
                    ) {
                        toggleStar(path)
                        itemForMenu = null
                    }
                    SimpleSheetAction(
                        icon = if (isZip) Icons.Default.Unarchive else Icons.Default.FolderZip,
                        label = if (isZip) "Extract" else "Zip",
                        tint = AuraGreen
                    ) {
                        itemForMenu = null
                        coroutineScope.launch {
                            val ok = withContext(Dispatchers.IO) {
                                if (isZip) extractZipArchive(target.file, currentDir)
                                else compressFilesToZip(listOf(target.file), File(currentDir, "${target.file.nameWithoutExtension}.zip"))
                            }
                            Toast.makeText(
                                context,
                                if (ok) (if (isZip) "Extracted archive" else "Created .zip archive") else "Archive operation failed",
                                Toast.LENGTH_SHORT
                            ).show()
                            refreshTrigger++
                        }
                    }
                    if (!target.isDirectory) {
                        SimpleSheetAction(Icons.Default.Lock, "Vault", AuraPurple) {
                            itemForMenu = null
                            coroutineScope.launch {
                                val ok = withContext(Dispatchers.IO) { encryptLocalFileToVault(context, target.file) }
                                Toast.makeText(context, if (ok) "Locked in Vault" else "Vault failed", Toast.LENGTH_SHORT).show()
                                refreshTrigger++
                            }
                        }
                        SimpleSheetAction(Icons.Default.Share, "Share", AuraCyan) {
                            itemForMenu = null
                            shareFileWithProvider(context, target.file)
                        }
                    }
                    SimpleSheetAction(Icons.Default.Info, "Details", TextSecondary) {
                        itemForMenu = null
                        itemForDetails = target
                    }
                    SimpleSheetAction(Icons.Default.Delete, "Delete", AuraRose) {
                        itemForMenu = null
                        selectedPaths = setOf(path)
                        showDeleteConfirm = true
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // File / Folder Properties (Details) Sheet
    if (itemForDetails != null) {
        val target = itemForDetails!!
        FileDetailsSheet(
            item = target,
            onClose = { itemForDetails = null }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(18.dp))

        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(end = 56.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "FILES",
                    style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 4.sp, fontWeight = FontWeight.Bold),
                    color = AuraCyan
                )
                Text(
                    text = "Storage, Explorer & Vault",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Segmented Switcher: OVERVIEW | EXPLORER
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(100.dp))
                .background(GlassPanel.copy(alpha = 0.7f))
                .border(1.dp, GlassBorder, RoundedCornerShape(100.dp))
                .padding(4.dp)
        ) {
            val modes = listOf("SMART_HUB" to "OVERVIEW", "FOLDER_BROWSER" to "EXPLORER")
            modes.forEach { (key, label) ->
                val selected = activeSubTab == key
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .testTag("files_mode_${key.lowercase()}")
                        .clip(RoundedCornerShape(100.dp))
                        .background(if (selected) AuraCyan.copy(alpha = 0.18f) else Color.Transparent)
                        .clickable { activeSubTab = key }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = if (selected) AuraCyan else TextSecondary,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (activeSubTab == "SMART_HUB") {
            if (isAnalyzing) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AuraCyan)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(bottom = 112.dp)
                ) {
                    item {
                        StorageDonutChart(totalSpace, usedSpace, freeSpace, categories)
                    }

                    if (!hasPermission && !hasManageStorage) {
                        item {
                            PermissionGrantBanner(
                                icon = Icons.Default.FolderOpen,
                                accentColor = AuraCyan,
                                title = "Media Access",
                                subtitle = "Scan photos, videos & docs",
                                onGrant = {
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
                            )
                        }
                    }

                    if (!hasManageStorage && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        item {
                            PermissionGrantBanner(
                                icon = Icons.Default.Settings,
                                accentColor = AuraRose,
                                title = "All Files Access",
                                subtitle = "Required to copy, rename, zip & clean all folders",
                                onGrant = {
                                    val intent = Intent(
                                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                    manageStorageLauncher.launch(intent)
                                }
                            )
                        }
                    }

                    if (!hasUsageAccess) {
                        item {
                            PermissionGrantBanner(
                                icon = Icons.Default.Apps,
                                accentColor = AuraOrange,
                                title = "App Size Access",
                                subtitle = "Measure exact app cache & storage",
                                onGrant = {
                                    usageAccessLauncher.launch(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                                }
                            )
                        }
                    }

                    // Quick Action Tools Dock
                    item {
                        Text(
                            "QUICK TOOLS",
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp, fontWeight = FontWeight.Bold),
                            color = TextTertiary
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            QuickToolCard(
                                modifier = Modifier.weight(1f),
                                icon = Icons.Default.FilterNone,
                                title = "Duplicates",
                                tint = AuraCyan,
                                tag = "tool_duplicates"
                            ) {
                                context.startActivity(Intent(context, DuplicateFinderActivity::class.java))
                            }
                            QuickToolCard(
                                modifier = Modifier.weight(1f),
                                icon = Icons.Default.CleaningServices,
                                title = "Clean Junk",
                                tint = AuraOrange,
                                tag = "tool_deep_clean"
                            ) {
                                context.startActivity(Intent(context, DeepCleanActivity::class.java))
                            }
                            QuickToolCard(
                                modifier = Modifier.weight(1f),
                                icon = Icons.Default.Lock,
                                title = "Vault",
                                tint = AuraRose,
                                tag = "tool_vault"
                            ) {
                                context.startActivity(Intent(context, VaultActivity::class.java))
                            }
                            QuickToolCard(
                                modifier = Modifier.weight(1f),
                                icon = Icons.Default.Apps,
                                title = "Apps",
                                tint = AuraPurple,
                                tag = "tool_apps"
                            ) {
                                context.startActivity(Intent(context, AppListActivity::class.java))
                            }
                        }
                    }

                    // Open Folder Explorer Card
                    item {
                        GlassCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { activeSubTab = "FOLDER_BROWSER" }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(AuraCyan.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Folder, contentDescription = null, tint = AuraCyan)
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            "Open Folder Explorer",
                                            color = TextPrimary,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            "Copy, move, rename, zip & manage folders",
                                            color = TextSecondary,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = AuraCyan)
                            }
                        }
                    }

                    item {
                        Text(
                            "CATEGORIES",
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp, fontWeight = FontWeight.Bold),
                            color = TextTertiary
                        )
                    }

                    items(categories) { cat ->
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            CategoryItemRow(cat, totalSpace) {
                                if (cat.name in listOf("Images", "Videos", "Audio", "Documents")) {
                                    val intent = Intent(context, FileCategoryActivity::class.java).apply {
                                        putExtra("CATEGORY_NAME", cat.name)
                                    }
                                    context.startActivity(intent)
                                } else if (cat.name == "Apps & Data") {
                                    context.startActivity(Intent(context, AppListActivity::class.java))
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // EXPLORER VIEW
            Column(modifier = Modifier.fillMaxSize()) {
                // Quick Jump Shortcuts: Internal | Downloads | Camera | Docs | Pictures | Starred
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val shortcuts = remember(rootDir) {
                        listOf(
                            "Internal" to rootDir,
                            "Downloads" to Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                            "Camera" to Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
                            "Pictures" to Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                            "Documents" to Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
                        )
                    }
                    shortcuts.forEach { (label, dir) ->
                        val active = !showOnlyStarred && dir != null && currentDir.absolutePath == dir.absolutePath
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(100.dp))
                                .background(if (active) AuraCyan.copy(alpha = 0.18f) else GlassPanel.copy(alpha = 0.6f))
                                .border(1.dp, if (active) AuraCyan else GlassBorder, RoundedCornerShape(100.dp))
                                .clickable {
                                    showOnlyStarred = false
                                    if (dir != null) {
                                        if (!dir.exists()) dir.mkdirs()
                                        currentDir = dir
                                        selectedPaths = emptySet()
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = label,
                                color = if (active) AuraCyan else TextSecondary,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }

                    // Starred Filter Chip
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(100.dp))
                            .background(if (showOnlyStarred) AuraOrange.copy(alpha = 0.2f) else GlassPanel.copy(alpha = 0.6f))
                            .border(1.dp, if (showOnlyStarred) AuraOrange else GlassBorder, RoundedCornerShape(100.dp))
                            .clickable {
                                showOnlyStarred = !showOnlyStarred
                                selectedPaths = emptySet()
                            }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = AuraOrange, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Starred (${starredPaths.size})",
                                color = if (showOnlyStarred) AuraOrange else TextSecondary,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Search + New Folder + Hidden Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Search files...", color = TextTertiary, style = MaterialTheme.typography.bodySmall) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = AuraCyan) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextSecondary)
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AuraCyan,
                            unfocusedBorderColor = GlassBorder,
                            focusedContainerColor = GlassPanel.copy(alpha = 0.55f),
                            unfocusedContainerColor = GlassPanel.copy(alpha = 0.55f),
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )

                    IconButton(
                        onClick = { showCreateFolderDialog = true },
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(GlassPanel.copy(alpha = 0.7f))
                            .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
                    ) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = "New Folder", tint = AuraCyan, modifier = Modifier.size(20.dp))
                    }

                    IconButton(
                        onClick = { showHiddenFiles = !showHiddenFiles },
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (showHiddenFiles) AuraPurple.copy(alpha = 0.2f) else GlassPanel.copy(alpha = 0.7f))
                            .border(1.dp, if (showHiddenFiles) AuraPurple else GlassBorder, RoundedCornerShape(14.dp))
                    ) {
                        Icon(
                            imageVector = if (showHiddenFiles) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = "Toggle hidden files",
                            tint = if (showHiddenFiles) AuraPurple else TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Breadcrumb Bar + Sort + Select All
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!showOnlyStarred && currentDir.absolutePath != rootDir.absolutePath) {
                            IconButton(
                                onClick = {
                                    currentDir.parentFile?.let { parent ->
                                        currentDir = parent
                                        selectedPaths = emptySet()
                                    }
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Up", tint = AuraCyan, modifier = Modifier.size(16.dp))
                            }
                        }

                        if (showOnlyStarred) {
                            Text(
                                text = "Starred Items",
                                color = AuraOrange,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        } else {
                            val segments = remember(currentDir, rootDir) {
                                buildBreadcrumbs(rootDir, currentDir)
                            }
                            segments.forEachIndexed { idx, (label, file) ->
                                Text(
                                    text = label,
                                    color = if (idx == segments.lastIndex) AuraCyan else TextSecondary,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable {
                                            currentDir = file
                                            selectedPaths = emptySet()
                                        }
                                        .padding(horizontal = 5.dp, vertical = 3.dp)
                                )
                                if (idx < segments.lastIndex) {
                                    Text("/", color = TextTertiary, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf("Date", "Size", "Name").forEach { mode ->
                            val active = sortBy == mode
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(100.dp))
                                    .background(if (active) AuraCyan.copy(alpha = 0.15f) else Color.Transparent)
                                    .border(1.dp, if (active) AuraCyan.copy(alpha = 0.4f) else GlassBorder, RoundedCornerShape(100.dp))
                                    .clickable { sortBy = mode }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(mode, color = if (active) AuraCyan else TextTertiary, style = MaterialTheme.typography.labelSmall)
                            }
                        }

                        if (filteredBrowserItems.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    selectedPaths = if (selectedPaths.size == filteredBrowserItems.size) {
                                        emptySet()
                                    } else {
                                        filteredBrowserItems.map { it.file.absolutePath }.toSet()
                                    }
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.Default.DoneAll,
                                    contentDescription = "Select All",
                                    tint = if (selectedPaths.size == filteredBrowserItems.size) AuraCyan else TextTertiary,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Clipboard Paste Bar (when Copy or Move is active)
                if (clipboard != null) {
                    val clip = clipboard!!
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(AuraGreen.copy(alpha = 0.15f))
                            .border(1.dp, AuraGreen.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${clip.paths.size} item(s) ready to ${if (clip.operation == ClipboardOperation.COPY) "copy" else "move"}",
                            color = TextPrimary,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        val count = withContext(Dispatchers.IO) {
                                            executeClipboardPaste(clip, currentDir)
                                        }
                                        Toast.makeText(
                                            context,
                                            "${if (clip.operation == ClipboardOperation.COPY) "Copied" else "Moved"} $count item(s)",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        clipboard = null
                                        selectedPaths = emptySet()
                                        refreshTrigger++
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = AuraGreen),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(Icons.Default.ContentPaste, contentDescription = null, tint = Obsidian, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Paste Here", color = Obsidian, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(onClick = { clipboard = null }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Cancel paste", tint = TextSecondary, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Multi-Select Simple Action Bar
                if (selectedPaths.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(GlassPanel.copy(alpha = 0.92f))
                            .border(1.dp, AuraCyan.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${selectedPaths.size} selected",
                            color = AuraCyan,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(start = 4.dp)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (selectedPaths.size == 1) {
                                val singleFile = File(selectedPaths.first())
                                MiniBarAction(Icons.Default.Edit, "Rename", AuraCyan) {
                                    renameInput = singleFile.name
                                    itemForRename = toBrowserFileItem(singleFile)
                                }
                            }
                            MiniBarAction(Icons.Default.ContentCopy, "Copy", AuraCyan) {
                                clipboard = FileClipboard(selectedPaths, ClipboardOperation.COPY)
                                selectedPaths = emptySet()
                                Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                            }
                            MiniBarAction(Icons.AutoMirrored.Filled.DriveFileMove, "Move", AuraOrange) {
                                clipboard = FileClipboard(selectedPaths, ClipboardOperation.MOVE)
                                selectedPaths = emptySet()
                                Toast.makeText(context, "Open target folder to Move", Toast.LENGTH_SHORT).show()
                            }
                            MiniBarAction(Icons.Default.Archive, "Zip", AuraGreen) {
                                val targets = selectedPaths.map { File(it) }
                                coroutineScope.launch {
                                    val archiveName = if (targets.size == 1) "${targets.first().nameWithoutExtension}.zip" else "archive_${System.currentTimeMillis() / 1000}.zip"
                                    val ok = withContext(Dispatchers.IO) {
                                        compressFilesToZip(targets, File(currentDir, archiveName))
                                    }
                                    Toast.makeText(context, if (ok) "Saved $archiveName" else "Zip failed", Toast.LENGTH_SHORT).show()
                                    selectedPaths = emptySet()
                                    refreshTrigger++
                                }
                            }
                            MiniBarAction(Icons.Default.Lock, "Vault", AuraPurple) {
                                coroutineScope.launch {
                                    val lockedCount = withContext(Dispatchers.IO) {
                                        selectedPaths.count { p ->
                                            val f = File(p)
                                            f.isFile && encryptLocalFileToVault(context, f)
                                        }
                                    }
                                    Toast.makeText(context, "Locked $lockedCount file(s) in Vault", Toast.LENGTH_SHORT).show()
                                    selectedPaths = emptySet()
                                    refreshTrigger++
                                }
                            }
                            MiniBarAction(Icons.Default.Delete, "Delete", AuraRose) {
                                showDeleteConfirm = true
                            }
                            IconButton(onClick = { selectedPaths = emptySet() }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextSecondary, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (isBrowserLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AuraCyan)
                    }
                } else if (filteredBrowserItems.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize().padding(bottom = 100.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(44.dp))
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                if (!hasManageStorage && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                    "Grant 'All Files Access' in Overview to browse folders."
                                } else {
                                    "Empty folder"
                                },
                                color = TextSecondary,
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 112.dp)
                    ) {
                        items(filteredBrowserItems, key = { it.file.absolutePath }) { item ->
                            val path = item.file.absolutePath
                            val isSelected = path in selectedPaths
                            val isStarred = path in starredPaths
                            BrowserItemCard(
                                item = item,
                                isSelected = isSelected,
                                isStarred = isStarred,
                                onToggleSelect = {
                                    selectedPaths = if (path in selectedPaths) selectedPaths - path else selectedPaths + path
                                },
                                onClick = {
                                    if (selectedPaths.isNotEmpty()) {
                                        selectedPaths = if (path in selectedPaths) selectedPaths - path else selectedPaths + path
                                    } else if (item.isDirectory) {
                                        showOnlyStarred = false
                                        currentDir = item.file
                                    } else {
                                        openFileWithProvider(context, item.file)
                                    }
                                },
                                onMoreClick = {
                                    itemForMenu = item
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
private fun SimpleSheetAction(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(74.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(vertical = 10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            color = TextPrimary,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1
        )
    }
}

@Composable
private fun MiniBarAction(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 7.dp, vertical = 4.dp)
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(17.dp))
        Text(label, color = tint, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FileDetailsSheet(
    item: BrowserFileItem,
    onClose: () -> Unit
) {
    var calculatedSize by remember { mutableLongStateOf(item.sizeBytes) }
    var sha256Hash by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(item.file) {
        withContext(Dispatchers.IO) {
            if (item.isDirectory) {
                calculatedSize = computeRecursiveSize(item.file)
            } else if (item.sizeBytes in 1..(50L * 1024 * 1024)) {
                sha256Hash = computeSha256Short(item.file)
            }
        }
    }

    val dateStr = remember(item.lastModified) {
        SimpleDateFormat("MMM d, yyyy • hh:mm a", Locale.US).format(Date(item.lastModified))
    }
    val perms = remember(item.file) {
        buildString {
            append(if (item.file.canRead()) "Read " else "")
            append(if (item.file.canWrite()) "• Write " else "")
            append(if (item.file.canExecute()) "• Exec" else "")
        }.ifBlank { "Restricted" }
    }

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF0F111A),
        contentColor = TextPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            Text(
                text = "ITEM DETAILS",
                color = AuraCyan,
                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(14.dp))
            InfoRow("NAME", item.name)
            InfoRow("TYPE", if (item.isDirectory) "Folder (${item.childCount} items)" else "${item.file.extension.uppercase().ifBlank { "FILE" }} File")
            InfoRow("SIZE", formatBytes(calculatedSize))
            InfoRow("MODIFIED", dateStr)
            InfoRow("ACCESS", perms)
            if (sha256Hash != null) {
                InfoRow("SHA-256", sha256Hash!!)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("PATH", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
            Text(item.file.absolutePath, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PermissionGrantBanner(
    icon: ImageVector,
    accentColor: Color,
    title: String,
    subtitle: String,
    onGrant: () -> Unit
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = accentColor)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = TextPrimary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text(subtitle, color = TextSecondary, style = MaterialTheme.typography.labelSmall)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onGrant,
                colors = ButtonDefaults.buttonColors(containerColor = accentColor)
            ) {
                Text("Grant", color = Obsidian, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun QuickToolCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    tint: Color,
    tag: String,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .testTag(tag)
            .clip(RoundedCornerShape(22.dp))
            .background(GlassPanel.copy(alpha = 0.6f))
            .border(1.dp, GlassBorder, RoundedCornerShape(22.dp))
            .clickable { onClick() }
            .padding(vertical = 14.dp, horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = title, tint = tint, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = TextPrimary,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun BrowserItemCard(
    item: BrowserFileItem,
    isSelected: Boolean,
    isStarred: Boolean,
    onToggleSelect: () -> Unit,
    onClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    val ext = item.file.extension.lowercase()
    val (icon, tint) = when {
        item.isDirectory -> Icons.Default.Folder to AuraCyan
        ext in setOf("jpg", "jpeg", "png", "webp", "gif", "heic") -> Icons.Default.Image to AuraCyan
        ext in setOf("mp4", "mkv", "mov", "avi", "webm") -> Icons.Default.VideoLibrary to AuraOrange
        ext in setOf("mp3", "wav", "flac", "m4a", "ogg") -> Icons.Default.AudioFile to AuraRose
        ext == "apk" -> Icons.Default.Android to AuraGreen
        ext == "zip" -> Icons.Default.FolderZip to AuraGreen
        ext in setOf("pdf", "doc", "docx", "txt", "xls", "xlsx", "ppt", "pptx") -> Icons.Default.Description to AuraPurple
        else -> Icons.AutoMirrored.Filled.InsertDriveFile to TextSecondary
    }

    val dateStr = remember(item.lastModified) {
        SimpleDateFormat("MMM d", Locale.US).format(Date(item.lastModified))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(if (isSelected) AuraCyan.copy(alpha = 0.14f) else GlassPanel.copy(alpha = 0.55f))
            .border(1.dp, if (isSelected) AuraCyan.copy(alpha = 0.45f) else GlassBorder, RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.name,
                    color = TextPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (isStarred) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(Icons.Default.Star, contentDescription = "Starred", tint = AuraOrange, modifier = Modifier.size(14.dp))
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            val metaText = if (item.isDirectory) {
                "${item.childCount} items • $dateStr"
            } else {
                "${formatBytes(item.sizeBytes)} • $dateStr"
            }
            Text(metaText, color = TextTertiary, style = MaterialTheme.typography.labelSmall)
        }
        IconButton(onClick = onToggleSelect, modifier = Modifier.size(34.dp)) {
            Icon(
                imageVector = if (isSelected) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                contentDescription = "Select",
                tint = if (isSelected) AuraCyan else TextTertiary,
                modifier = Modifier.size(19.dp)
            )
        }
        IconButton(onClick = onMoreClick, modifier = Modifier.size(34.dp)) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "Actions",
                tint = TextSecondary,
                modifier = Modifier.size(19.dp)
            )
        }
    }
}

private fun toBrowserFileItem(f: File): BrowserFileItem {
    val isDir = f.isDirectory
    val count = if (isDir) {
        try {
            f.list()?.size ?: 0
        } catch (_: Exception) {
            0
        }
    } else 0
    return BrowserFileItem(
        file = f,
        name = f.name,
        isDirectory = isDir,
        sizeBytes = if (isDir) 0L else f.length(),
        lastModified = f.lastModified(),
        childCount = count
    )
}

private fun loadDirectoryContents(dir: File, showHidden: Boolean): List<BrowserFileItem> {
    val children = try {
        dir.listFiles()?.toList().orEmpty()
    } catch (_: Exception) {
        emptyList()
    }
    return children
        .filter { showHidden || !it.name.startsWith(".") }
        .map { toBrowserFileItem(it) }
}

private fun executeClipboardPaste(clip: FileClipboard, targetDir: File): Int {
    var successCount = 0
    for (srcPath in clip.paths) {
        val src = File(srcPath)
        if (!src.exists()) continue
        var dest = File(targetDir, src.name)
        if (dest.absolutePath == src.absolutePath) {
            if (clip.operation == ClipboardOperation.MOVE) continue
            dest = File(targetDir, "${src.nameWithoutExtension}_copy.${src.extension}".trimEnd('.'))
        } else if (dest.exists()) {
            dest = File(targetDir, "${src.nameWithoutExtension}_${System.currentTimeMillis() % 1000}.${src.extension}".trimEnd('.'))
        }
        try {
            if (clip.operation == ClipboardOperation.COPY) {
                src.copyRecursively(dest, overwrite = true)
                successCount++
            } else {
                if (src.renameTo(dest)) {
                    successCount++
                } else {
                    src.copyRecursively(dest, overwrite = true)
                    if (src.isDirectory) src.deleteRecursively() else src.delete()
                    successCount++
                }
            }
        } catch (_: Exception) {
        }
    }
    return successCount
}

private fun compressFilesToZip(sources: List<File>, destZip: File): Boolean {
    return try {
        ZipOutputStream(BufferedOutputStream(FileOutputStream(destZip))).use { zos ->
            for (src in sources) {
                addFileToZip(zos, src, src.name)
            }
        }
        true
    } catch (_: Exception) {
        false
    }
}

private fun addFileToZip(zos: ZipOutputStream, file: File, entryName: String) {
    if (file.isDirectory) {
        val children = file.listFiles() ?: return
        for (child in children) {
            addFileToZip(zos, child, "$entryName/${child.name}")
        }
    } else {
        FileInputStream(file).use { fis ->
            BufferedInputStream(fis).use { origin ->
                val entry = ZipEntry(entryName)
                zos.putNextEntry(entry)
                origin.copyTo(zos)
                zos.closeEntry()
            }
        }
    }
}

private fun extractZipArchive(zipFile: File, targetParentDir: File): Boolean {
    return try {
        val outDir = File(targetParentDir, zipFile.nameWithoutExtension)
        if (!outDir.exists()) outDir.mkdirs()
        val canonicalOutDir = outDir.canonicalPath
        ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val outFile = File(outDir, entry.name)
                if (!outFile.canonicalPath.startsWith(canonicalOutDir)) {
                    break // Zip-slip protection
                }
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { fos ->
                        zis.copyTo(fos)
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        true
    } catch (_: Exception) {
        false
    }
}

private fun computeRecursiveSize(file: File): Long {
    return try {
        if (file.isFile) file.length()
        else file.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    } catch (_: Exception) {
        0L
    }
}

private fun computeSha256Short(file: File): String? {
    return try {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(8192)
            var read: Int
            while (input.read(buf).also { read = it } != -1) {
                md.update(buf, 0, read)
            }
        }
        md.digest().take(8).joinToString("") { "%02x".format(it) } + "..."
    } catch (_: Exception) {
        null
    }
}

private fun buildBreadcrumbs(root: File, current: File): List<Pair<String, File>> {
    val result = mutableListOf<Pair<String, File>>()
    var ptr: File? = current
    while (ptr != null) {
        val label = if (ptr.absolutePath == root.absolutePath) "Internal" else ptr.name.ifBlank { "Root" }
        result.add(0, label to ptr)
        if (ptr.absolutePath == root.absolutePath) break
        ptr = ptr.parentFile
    }
    return result
}

private fun encryptLocalFileToVault(context: Context, sourceFile: File): Boolean {
    return try {
        val uuid = UUID.randomUUID().toString()
        val vaultDir = VaultManager.getVaultDir(context)
        val destFile = File(vaultDir, "$uuid.enc")
        val encryptedFile = EncryptedFile.Builder(
            context,
            destFile,
            VaultManager.getMasterKey(context),
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
        ).build()

        sourceFile.inputStream().use { input ->
            encryptedFile.openFileOutput().use { output ->
                input.copyTo(output)
            }
        }
        VaultManager.getRegistry(context).edit().putString(uuid, sourceFile.name).apply()
        sourceFile.delete()
        true
    } catch (_: Exception) {
        false
    }
}

private fun openFileWithProvider(context: Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val ext = file.extension.lowercase()
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Open with"))
    } catch (_: Exception) {
        Toast.makeText(context, "No app found to open this file", Toast.LENGTH_SHORT).show()
    }
}

private fun shareFileWithProvider(context: Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val ext = file.extension.lowercase()
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share file"))
    } catch (_: Exception) {
        Toast.makeText(context, "Unable to share file", Toast.LENGTH_SHORT).show()
    }
}

private fun queryMediaSize(context: Context, uri: Uri): Long {
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

private fun queryDocsSize(context: Context): Long {
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
