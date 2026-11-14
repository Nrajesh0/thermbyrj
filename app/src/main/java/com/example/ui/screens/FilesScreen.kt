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
import android.app.Activity
import android.graphics.Bitmap
import android.util.LruCache
import androidx.activity.result.IntentSenderRequest
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterNone
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.SdStorage
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.GlassCard
import com.example.InfoRow
import com.example.formatBytes
import com.example.queryDeviceStorageTotals
import com.example.service.FileManagerOps
import com.example.service.FileShredder
import com.example.service.PendingExplorerNavigation
import com.example.service.PermissionUtils
import com.example.service.PreferencesBackupManager
import com.example.service.RecentMediaFile
import com.example.service.SharedClipboardHolder
import com.example.service.SharedClipboardOp
import com.example.service.ShredJobCoordinator
import com.example.service.ShredStandard
import com.example.service.ShredTarget
import com.example.service.AppRefreshCoordinator
import com.example.service.StorageScanCache
import com.example.ui.components.ActiveFilePreview
import com.example.ui.components.BuiltInFilePreviewHost
import com.example.ui.components.ExpandableSearchBar
import com.example.ui.components.SearchIconToggleButton
import com.example.ui.components.openExternalWithProvider
import com.example.ui.components.resolveInAppViewerType
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

data class StorageCategory(
    val name: String,
    val size: Long,
    val color: Color,
    val icon: ImageVector
)

data class BrowserFileItem(
    val file: File,
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModified: Long,
    val childCount: Int
)

private val explorerThumbCache = LruCache<String, Bitmap>(96)

@Suppress("DEPRECATION")
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
@Volatile
private var cachedRecentFiles: List<RecentMediaFile> = emptyList()
@Volatile
private var cachedFilesTimeMs: Long = 0L
@Volatile
private var cachedFilesPermKey: String = ""
@Volatile
private var cachedFilesGeneration: Long = -1L
@Volatile
private var forceFilesRescan: Boolean = false

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilesScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val bookmarkPrefs = remember { context.getSharedPreferences("file_bookmarks_prefs", Context.MODE_PRIVATE) }

    var activeSubTab by rememberSaveable { mutableStateOf("SMART_HUB") } // "SMART_HUB" or "FOLDER_BROWSER"
    var categories by remember { mutableStateOf(cachedFilesCategories) }
    var recentFiles by remember { mutableStateOf(cachedRecentFiles) }
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
    val globalRefreshVersion by AppRefreshCoordinator.refreshVersion.collectAsStateWithLifecycle()
    LaunchedEffect(globalRefreshVersion) {
        if (globalRefreshVersion > 0) {
            forceFilesRescan = true
            refreshTrigger++
        }
    }
    var mediaPermPromptAttempted by rememberSaveable { mutableStateOf(false) }

    // Storage Volumes & Folder Browser state
    val storageVolumes = remember(hasManageStorage) { FileManagerOps.getAvailableStorageVolumes(context) }
    var selectedVolumeRootPath by rememberSaveable { mutableStateOf(storageVolumes.first().rootDir.absolutePath) }
    val rootDir = remember(selectedVolumeRootPath, storageVolumes) {
        storageVolumes.firstOrNull { it.rootDir.absolutePath == selectedVolumeRootPath }?.rootDir
            ?: storageVolumes.first().rootDir
    }
    var currentDirPath by rememberSaveable { mutableStateOf(rootDir.absolutePath) }
    val currentDir = remember(currentDirPath, rootDir) {
        File(currentDirPath).takeIf { it.exists() } ?: rootDir
    }
    fun updateCurrentDir(dir: File) {
        currentDirPath = dir.absolutePath
    }
    var browserItems by remember { mutableStateOf<List<BrowserFileItem>>(emptyList()) }
    var isBrowserLoading by remember { mutableStateOf(false) }
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var searchRecursive by rememberSaveable { mutableStateOf(false) }
    var recursiveResults by remember { mutableStateOf<List<BrowserFileItem>>(emptyList()) }
    var isRecursiveSearching by remember { mutableStateOf(false) }
    var sortBy by rememberSaveable { mutableStateOf("Date") } // "Name", "Size", "Date"
    var sortAscending by rememberSaveable { mutableStateOf(false) }
    var isExplorerGrid by rememberSaveable { mutableStateOf(false) }
    var showHiddenFiles by rememberSaveable { mutableStateOf(false) }
    var showOnlyStarred by rememberSaveable { mutableStateOf(false) }
    var starredPaths by remember {
        mutableStateOf(bookmarkPrefs.getStringSet("starred_paths", emptySet()) ?: emptySet())
    }

    var selectedPaths by remember { mutableStateOf<Set<String>>(emptySet()) }
    val clipboard by SharedClipboardHolder.clipboard.collectAsStateWithLifecycle()

    // Action sheet & Dialog states
    var itemForMenu by remember { mutableStateOf<BrowserFileItem?>(null) }
    var itemForDetails by remember { mutableStateOf<BrowserFileItem?>(null) }
    var itemForRename by remember { mutableStateOf<BrowserFileItem?>(null) }
    var renameInput by remember { mutableStateOf("") }
    var showCreateMenu by remember { mutableStateOf(false) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var showCreateFileDialog by remember { mutableStateOf(false) }
    var newFileName by remember { mutableStateOf("") }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var deleteWithShredder by remember { mutableStateOf(false) }
    var selectedShredStandard by remember { mutableStateOf(ShredStandard.DOD_5220_22_M) }
    var pendingDeletePaths by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    var activeFilePreview by remember { mutableStateOf<ActiveFilePreview?>(null) }
    var interruptedShredPath by remember { mutableStateOf(FileShredder.peekInterruptedShredPath(context)) }

    val shredState by ShredJobCoordinator.state.collectAsStateWithLifecycle()
    var lastHandledShredCompletionId by rememberSaveable { mutableStateOf(shredState.completionId) }
    val pendingNavTarget by PendingExplorerNavigation.targetFolderPath.collectAsStateWithLifecycle()

    LaunchedEffect(pendingNavTarget) {
        val target = PendingExplorerNavigation.consume()
        if (!target.isNullOrBlank()) {
            val f = File(target)
            if (f.exists() && f.isDirectory) {
                storageVolumes.firstOrNull { f.absolutePath.startsWith(it.rootDir.absolutePath) }?.let { vol ->
                    selectedVolumeRootPath = vol.rootDir.absolutePath
                }
                showOnlyStarred = false
                searchExpanded = false
                searchQuery = ""
                updateCurrentDir(f)
                activeSubTab = "FOLDER_BROWSER"
                refreshTrigger++
            }
        }
    }

    fun openFileSmart(file: File, uri: Uri? = null) {
        val viewerType = resolveInAppViewerType(file)
        if (viewerType != null) {
            activeFilePreview = ActiveFilePreview(file = file, contentUri = uri, type = viewerType)
        } else {
            openExternalWithProvider(context, file, uri)
        }
    }

    fun toggleStar(path: String) {
        val updated = if (path in starredPaths) starredPaths - path else starredPaths + path
        starredPaths = updated
        bookmarkPrefs.edit().putStringSet("starred_paths", updated).apply()
        coroutineScope.launch(Dispatchers.IO) {
            PreferencesBackupManager.saveMirrorSnapshot(context)
        }
    }

    BackHandler(enabled = activeSubTab == "FOLDER_BROWSER" && (selectedPaths.isNotEmpty() || currentDir.absolutePath != rootDir.absolutePath)) {
        if (selectedPaths.isNotEmpty()) {
            selectedPaths = emptySet()
        } else {
            currentDir.parentFile?.let { parent ->
                if (parent.absolutePath.startsWith(rootDir.absolutePath)) {
                    updateCurrentDir(parent)
                }
            }
        }
    }

    val manageStorageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        hasManageStorage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager() else true
        forceFilesRescan = true
        StorageScanCache.invalidate()
        refreshTrigger++
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        val granted = checkMediaPermission(context)
        hasPermission = granted
        if (!granted && mediaPermPromptAttempted) {
            Toast.makeText(context, "Enable Photos, Videos & Audio in App Permissions", Toast.LENGTH_SHORT).show()
            PermissionUtils.launchAppDetailsSettings(context)
        }
        mediaPermPromptAttempted = true
        forceFilesRescan = true
        StorageScanCache.invalidate()
        refreshTrigger++
    }

    val usageAccessLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        hasUsageAccess = checkUsageStatsPermission(context)
        forceFilesRescan = true
        StorageScanCache.invalidate()
        refreshTrigger++
    }

    val explorerDeleteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val pendingSet = pendingDeletePaths.toSet()
        pendingDeletePaths = emptyList()
        if (result.resultCode == Activity.RESULT_OK) {
            selectedPaths = selectedPaths - pendingSet
            StorageScanCache.invalidate()
            forceFilesRescan = true
            refreshTrigger++
            Toast.makeText(context, "Deleted ${pendingSet.size.coerceAtLeast(1)} item(s)", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Deletion cancelled or denied", Toast.LENGTH_SHORT).show()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasUsageAccess = checkUsageStatsPermission(context)
                hasManageStorage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager() else true
                hasPermission = checkMediaPermission(context)
                interruptedShredPath = if (shredState.isRunning) null else FileShredder.peekInterruptedShredPath(context)
                refreshTrigger++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(shredState.completionId) {
        if (!shredState.isRunning && shredState.completionId != 0L && shredState.completionId != lastHandledShredCompletionId) {
            lastHandledShredCompletionId = shredState.completionId
            val res = shredState.lastResult
            showDeleteConfirm = false
            selectedPaths = emptySet()
            interruptedShredPath = FileShredder.peekInterruptedShredPath(context)
            forceFilesRescan = true
            StorageScanCache.invalidate()
            refreshTrigger++
            if (res != null) {
                val msg = when {
                    res.blockedForOverwrite.isNotEmpty() ->
                        "Shredded ${res.shreddedCount}; ${res.blockedForOverwrite.size} blocked without All Files Access"
                    else ->
                        "Shredded ${res.shreddedCount} file(s) (${formatBytes(res.totalBytesWiped)})"
                }
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    LaunchedEffect(refreshTrigger, hasPermission, hasUsageAccess, hasManageStorage) {
        val permKey = "${hasPermission}_${hasUsageAccess}_${hasManageStorage}"
        val now = System.currentTimeMillis()
        val currentGen = StorageScanCache.generation
        if (!forceFilesRescan &&
            cachedFilesGeneration == currentGen &&
            cachedFilesCategories.isNotEmpty() &&
            cachedFilesPermKey == permKey &&
            (now - cachedFilesTimeMs) < 30_000L
        ) {
            totalSpace = cachedFilesTotal
            freeSpace = cachedFilesFree
            usedSpace = cachedFilesUsed
            categories = cachedFilesCategories
            recentFiles = cachedRecentFiles
            isAnalyzing = false
            return@LaunchedEffect
        }
        forceFilesRescan = false
        if (categories.isEmpty()) isAnalyzing = true
        withContext(Dispatchers.IO) {
            val (tot, free) = queryDeviceStorageTotals(context)
            val used = max(0L, tot - free)

            val canScanMedia = hasPermission || hasManageStorage
            val imageSize = if (canScanMedia) queryMediaSize(context, MediaStore.Images.Media.EXTERNAL_CONTENT_URI) else 0L
            val videoSize = if (canScanMedia) queryMediaSize(context, MediaStore.Video.Media.EXTERNAL_CONTENT_URI) else 0L
            val audioSize = if (canScanMedia) queryMediaSize(context, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI) else 0L
            val docsSize = if (canScanMedia) queryDocsSize(context) else 0L
            val downloadsSize = if (canScanMedia) FileManagerOps.queryDownloadsSize(context) else 0L
            val apksSize = if (canScanMedia) FileManagerOps.queryApksSize(context) else 0L
            val recents = if (canScanMedia) FileManagerOps.queryRecentFiles(context, limit = 12) else emptyList()

            val mediaTotal = imageSize + videoSize + audioSize + docsSize
            val otherUsed = max(0L, used - mediaTotal)

            var exactAppSize = 0L
            if (checkUsageStatsPermission(context)) {
                try {
                    val storageStatsManager = context.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
                    val pm = context.packageManager
                    val packages = pm.getInstalledApplications(0)
                    val seenUids = HashSet<Int>(packages.size)
                    for (app in packages) {
                        if (!seenUids.add(app.uid)) continue
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
                StorageCategory("Downloads", downloadsSize, AuraCyan, Icons.Default.Download),
                StorageCategory("APKs", apksSize, AuraGreen, Icons.Default.Android),
                StorageCategory("Apps & Data", appSize, AuraPurple, Icons.Default.Apps),
                StorageCategory("System", systemSize, Color.Gray, Icons.Default.SystemUpdate)
            ).sortedByDescending { it.size }

            cachedFilesTotal = tot
            cachedFilesFree = free
            cachedFilesUsed = used
            cachedFilesCategories = newCats
            cachedRecentFiles = recents
            cachedFilesTimeMs = System.currentTimeMillis()
            cachedFilesPermKey = permKey
            cachedFilesGeneration = currentGen

            totalSpace = tot
            freeSpace = free
            usedSpace = used
            categories = newCats
            recentFiles = recents
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

    LaunchedEffect(searchQuery, searchRecursive, currentDir, showHiddenFiles, refreshTrigger, activeSubTab) {
        if (activeSubTab == "FOLDER_BROWSER" && searchRecursive && searchQuery.trim().length >= 2) {
            isRecursiveSearching = true
            val found = withContext(Dispatchers.IO) {
                FileManagerOps.searchFilesRecursively(currentDir, searchQuery, showHiddenFiles)
                    .map { toBrowserFileItem(it) }
            }
            recursiveResults = found
            isRecursiveSearching = false
        } else {
            recursiveResults = emptyList()
            isRecursiveSearching = false
        }
    }

    val filteredBrowserItems = remember(browserItems, recursiveResults, searchQuery, searchRecursive, sortBy, sortAscending) {
        val baseList = if (searchRecursive && searchQuery.trim().length >= 2) {
            recursiveResults
        } else if (searchQuery.isBlank()) {
            browserItems
        } else {
            browserItems.filter { it.name.contains(searchQuery.trim(), ignoreCase = true) }
        }
        baseList.sortedWith { a, b ->
            if (a.isDirectory != b.isDirectory) {
                if (a.isDirectory) -1 else 1
            } else {
                val cmp = when (sortBy) {
                    "Size" -> a.sizeBytes.compareTo(b.sizeBytes)
                    "Name" -> a.name.lowercase().compareTo(b.name.lowercase())
                    else -> a.lastModified.compareTo(b.lastModified)
                }
                if (sortAscending) cmp else -cmp
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
                            StorageScanCache.invalidate()
                            forceFilesRescan = true
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

    // Create New Text File Dialog
    if (showCreateFileDialog) {
        AlertDialog(
            onDismissRequest = { showCreateFileDialog = false },
            containerColor = GlassPanel,
            title = { Text("New Text File", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newFileName,
                    onValueChange = { newFileName = it },
                    label = { Text("File name (e.g., notes.txt)", color = TextSecondary) },
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
                        val trimmed = newFileName.trim()
                        if (trimmed.isNotEmpty()) {
                            val finalName = if (trimmed.contains('.')) trimmed else "$trimmed.txt"
                            val targetFile = File(currentDir, finalName)
                            val created = try {
                                if (!targetFile.exists()) targetFile.createNewFile() else false
                            } catch (_: Exception) {
                                false
                            }
                            newFileName = ""
                            showCreateFileDialog = false
                            if (created) {
                                StorageScanCache.invalidate()
                                forceFilesRescan = true
                                refreshTrigger++
                                openFileSmart(targetFile)
                            } else {
                                Toast.makeText(context, "Could not create file (check All Files Access)", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                ) {
                    Text("CREATE & EDIT", color = AuraCyan, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFileDialog = false }) {
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
                            coroutineScope.launch {
                                val ok = withContext(Dispatchers.IO) {
                                    FileManagerOps.renameFileSafely(context, target.file, newName)
                                }
                                Toast.makeText(context, if (ok) "Renamed to $newName" else "Rename failed", Toast.LENGTH_SHORT).show()
                                selectedPaths = emptySet()
                                forceFilesRescan = true
                                refreshTrigger++
                            }
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

    // Dual Delete / Shredder Confirmation Dialog
    if (showDeleteConfirm && selectedPaths.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = GlassPanel,
            title = {
                Text(
                    text = if (deleteWithShredder) "Shred ${selectedPaths.size} Item(s)?" else "Delete ${selectedPaths.size} Item(s)?",
                    color = if (deleteWithShredder) AuraRose else TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                if (shredState.isRunning) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            shredState.statusText.ifBlank { "Overwriting blocks & cleaning metadata..." },
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { shredState.progress },
                            color = AuraRose,
                            trackColor = GlassBorder,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            "Runs in a foreground service if you switch apps. Force-killing the app mid-shred leaves completed files wiped, queued files intact, and only the active file partially overwritten.",
                            color = TextTertiary,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Mode Selector: Normal Delete vs Shredder Delete
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (!deleteWithShredder) AuraCyan.copy(alpha = 0.16f) else Obsidian.copy(alpha = 0.45f))
                                    .border(1.dp, if (!deleteWithShredder) AuraCyan else GlassBorder, RoundedCornerShape(10.dp))
                                    .clickable { deleteWithShredder = false }
                                    .padding(vertical = 8.dp, horizontal = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "Normal Delete",
                                    color = if (!deleteWithShredder) AuraCyan else TextSecondary,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (deleteWithShredder) AuraRose.copy(alpha = 0.16f) else Obsidian.copy(alpha = 0.45f))
                                    .border(1.dp, if (deleteWithShredder) AuraRose else GlassBorder, RoundedCornerShape(10.dp))
                                    .clickable { deleteWithShredder = true }
                                    .padding(vertical = 8.dp, horizontal = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "Shred Forever",
                                    color = if (deleteWithShredder) AuraRose else TextSecondary,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }

                        if (!deleteWithShredder) {
                            Text(
                                "Fast standard OS removal (unlinks file immediately). Cannot be undone.",
                                color = TextSecondary,
                                style = MaterialTheme.typography.bodySmall
                            )
                        } else {
                            ShredStandard.entries.forEach { std ->
                                val selected = selectedShredStandard == std
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (selected) AuraRose.copy(alpha = 0.12f) else Obsidian.copy(alpha = 0.4f))
                                        .border(1.dp, if (selected) AuraRose else GlassBorder, RoundedCornerShape(10.dp))
                                        .clickable { selectedShredStandard = std }
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                        contentDescription = std.title,
                                        tint = if (selected) AuraRose else TextTertiary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            std.title,
                                            color = if (selected) AuraRose else TextPrimary,
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                        )
                                        Text(
                                            std.subtitle,
                                            color = TextTertiary,
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (shredState.isRunning) {
                    TextButton(onClick = { showDeleteConfirm = false }) {
                        Text("RUN IN BACKGROUND", color = AuraCyan, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = {
                            if (!deleteWithShredder) {
                                val pathsSnapshot = selectedPaths.toList()
                                showDeleteConfirm = false
                                coroutineScope.launch {
                                    val (deletedCount, failedUris, failedPaths) = withContext(Dispatchers.IO) {
                                        var count = 0
                                        val urisToPrompt = mutableListOf<Uri>()
                                        val pathList = mutableListOf<String>()
                                        pathsSnapshot.forEach { path ->
                                            val f = File(path)
                                            val contentUri = if (f.isFile) FileShredder.findMediaContentUriForFile(context, f) else null
                                            if (FileShredder.normalDeleteFile(context, f, contentUri)) {
                                                count++
                                            } else if (contentUri != null) {
                                                urisToPrompt.add(contentUri)
                                                pathList.add(path)
                                            }
                                        }
                                        Triple(count, urisToPrompt, pathList)
                                    }
                                    if (deletedCount > 0) {
                                        Toast.makeText(context, "Deleted $deletedCount item(s)", Toast.LENGTH_SHORT).show()
                                        selectedPaths = emptySet()
                                        StorageScanCache.invalidate()
                                        forceFilesRescan = true
                                        refreshTrigger++
                                    }
                                    if (failedUris.isNotEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                        try {
                                            pendingDeletePaths = failedPaths
                                            val pendingIntent = MediaStore.createDeleteRequest(context.contentResolver, failedUris)
                                            explorerDeleteLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
                                        } catch (_: Exception) {
                                            pendingDeletePaths = emptyList()
                                            Toast.makeText(context, "Grant All Files Access to delete protected items", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            } else {
                                val targets = selectedPaths.map { ShredTarget(File(it)) }
                                ShredJobCoordinator.startBackgroundShred(
                                    context = context,
                                    targets = targets,
                                    standard = selectedShredStandard
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (deleteWithShredder) AuraRose else AuraCyan
                        )
                    ) {
                        Text(
                            if (deleteWithShredder) "SHRED FOREVER" else "DELETE",
                            color = if (deleteWithShredder) Color.White else Obsidian,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            dismissButton = {
                if (!shredState.isRunning) {
                    TextButton(onClick = { showDeleteConfirm = false }) {
                        Text("CANCEL", color = TextSecondary)
                    }
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
            containerColor = GlassPanel,
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
                        SharedClipboardHolder.setClipboard(setOf(path), SharedClipboardOp.COPY)
                        itemForMenu = null
                        Toast.makeText(context, "Copied — open destination folder to paste", Toast.LENGTH_SHORT).show()
                    }
                    SimpleSheetAction(Icons.AutoMirrored.Filled.DriveFileMove, "Move", AuraOrange) {
                        SharedClipboardHolder.setClipboard(setOf(path), SharedClipboardOp.MOVE)
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
                                if (isZip) FileManagerOps.extractZipArchive(context, target.file, currentDir)
                                else FileManagerOps.compressFilesToZip(listOf(target.file), File(currentDir, "${target.file.nameWithoutExtension}.zip"))
                            }
                            Toast.makeText(
                                context,
                                if (ok) (if (isZip) "Extracted archive" else "Created .zip archive") else "Archive operation failed",
                                Toast.LENGTH_SHORT
                            ).show()
                            forceFilesRescan = true
                            refreshTrigger++
                        }
                    }
                    if (!target.isDirectory) {
                        SimpleSheetAction(Icons.Default.Lock, "Vault", AuraPurple) {
                            itemForMenu = null
                            coroutineScope.launch {
                                val ok = withContext(Dispatchers.IO) { FileManagerOps.encryptFileToVault(context, target.file) }
                                Toast.makeText(context, if (ok) "Locked in Vault" else "Vault failed", Toast.LENGTH_SHORT).show()
                                forceFilesRescan = true
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
                    SimpleSheetAction(Icons.Default.Delete, "Delete", TextSecondary) {
                        itemForMenu = null
                        selectedPaths = setOf(path)
                        deleteWithShredder = false
                        showDeleteConfirm = true
                    }
                    SimpleSheetAction(Icons.Default.DeleteForever, "Shred", AuraRose) {
                        itemForMenu = null
                        selectedPaths = setOf(path)
                        deleteWithShredder = true
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

    // Built-in Image Lightbox, Text Viewer/Editor, Zip Inspector & APK Inspector
    BuiltInFilePreviewHost(
        preview = activeFilePreview,
        onDismiss = { activeFilePreview = null },
        onExtractZip = { zipFile ->
            coroutineScope.launch {
                val ok = withContext(Dispatchers.IO) {
                    FileManagerOps.extractZipArchive(context, zipFile, zipFile.parentFile ?: currentDir)
                }
                Toast.makeText(context, if (ok) "Extracted ${zipFile.name}" else "Extract failed", Toast.LENGTH_SHORT).show()
                forceFilesRescan = true
                refreshTrigger++
            }
        },
        onShare = { f, _ -> shareFileWithProvider(context, f) },
        onVault = { f, uri ->
            coroutineScope.launch {
                val ok = withContext(Dispatchers.IO) { FileManagerOps.encryptFileToVault(context, f, uri) }
                Toast.makeText(context, if (ok) "Locked in Vault" else "Vault failed", Toast.LENGTH_SHORT).show()
                forceFilesRescan = true
                refreshTrigger++
            }
        },
        onDelete = { f, _ ->
            selectedPaths = setOf(f.absolutePath)
            deleteWithShredder = false
            showDeleteConfirm = true
        },
        onShowDetails = { f ->
            activeFilePreview = null
            itemForDetails = toBrowserFileItem(f)
        },
        onFileSaved = {
            StorageScanCache.invalidate()
            forceFilesRescan = true
            refreshTrigger++
        }
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(14.dp))

        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .padding(end = 46.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "FILES",
                    style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 2.5.sp, fontWeight = FontWeight.Bold),
                    color = AuraCyan
                )
                if (totalSpace > 0L) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(100.dp))
                            .background(GlassPanel)
                            .border(1.dp, GlassBorder, RoundedCornerShape(100.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${formatBytes(freeSpace)} free",
                            color = TextSecondary,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Segmented Switcher: OVERVIEW | EXPLORER
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(100.dp))
                .background(GlassPanel.copy(alpha = 0.8f))
                .border(1.dp, GlassBorder, RoundedCornerShape(100.dp))
                .padding(3.dp)
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
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = if (selected) AuraCyan else TextSecondary,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

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

                    if (interruptedShredPath != null && !shredState.isRunning) {
                        val remPath = interruptedShredPath!!
                        item {
                            GlassCard(modifier = Modifier.fillMaxWidth()) {
                                Column {
                                    Text(
                                        "Interrupted Shred Remnant Detected",
                                        color = AuraRose,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "App was closed mid-shred leaving '${File(remPath).name}' partially overwritten on disk.",
                                        color = TextSecondary,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = {
                                                FileShredder.clearInterruptedShredPath(context)
                                                interruptedShredPath = null
                                                ShredJobCoordinator.startBackgroundShred(
                                                    context = context,
                                                    targets = listOf(ShredTarget(File(remPath))),
                                                    standard = selectedShredStandard
                                                )
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = AuraRose)
                                        ) {
                                            Text("Finish Shred", color = Color.White, fontWeight = FontWeight.Bold)
                                        }
                                        TextButton(onClick = {
                                            FileShredder.clearInterruptedShredPath(context)
                                            interruptedShredPath = null
                                        }) {
                                            Text("Dismiss", color = TextSecondary)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (shredState.isRunning) {
                        item {
                            GlassCard(modifier = Modifier.fillMaxWidth()) {
                                Column {
                                    Text(
                                        shredState.statusText.ifBlank { "Shredding in progress..." },
                                        color = AuraRose,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    LinearProgressIndicator(
                                        progress = { shredState.progress },
                                        color = AuraRose,
                                        trackColor = GlassBorder,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }

                    if (!hasPermission && !hasManageStorage) {
                        item {
                            PermissionGrantBanner(
                                icon = Icons.Default.FolderOpen,
                                accentColor = AuraCyan,
                                title = "Media Access",
                                subtitle = "Scan photos, videos & docs",
                                onGrant = {
                                    permissionLauncher.launch(PermissionUtils.requiredMediaPermissions())
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
                                subtitle = "Required to copy, rename, zip, shred & clean all folders",
                                onGrant = {
                                    try {
                                        manageStorageLauncher.launch(PermissionUtils.buildAllFilesAccessIntent(context))
                                    } catch (_: Exception) {
                                        PermissionUtils.launchAllFilesAccessSettings(context)
                                    }
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

                    if (recentFiles.isNotEmpty()) {
                        item {
                            Text(
                                "RECENT FILES",
                                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp, fontWeight = FontWeight.Bold),
                                color = TextTertiary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(recentFiles, key = { it.contentUri.toString() }) { recent ->
                                    RecentFileChipCard(recent) {
                                        val f = File(recent.path)
                                        openFileSmart(f, recent.contentUri)
                                    }
                                }
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
                                            "Copy, move, rename, zip, inspect & edit files",
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
                                if (cat.name in listOf("Images", "Videos", "Audio", "Documents", "Downloads", "APKs")) {
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
            if (activeSubTab == "FOLDER_BROWSER") {
                Column(modifier = Modifier.fillMaxSize()) {
                    if (!hasManageStorage && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        PermissionGrantBanner(
                            icon = Icons.Default.Settings,
                            accentColor = AuraRose,
                            title = "All Files Access Needed",
                            subtitle = "Required to browse, rename, move & overwrite files when shredding",
                            onGrant = {
                                try {
                                    manageStorageLauncher.launch(PermissionUtils.buildAllFilesAccessIntent(context))
                                } catch (_: Exception) {
                                    PermissionUtils.launchAllFilesAccessSettings(context)
                                }
                            }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    // Quick Jump Shortcuts: Volumes (if >1) | Internal | Downloads | Camera | Docs | Pictures | Starred
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (storageVolumes.size > 1) {
                            storageVolumes.forEach { vol ->
                                val activeVol = rootDir.absolutePath == vol.rootDir.absolutePath && !showOnlyStarred
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(100.dp))
                                        .background(if (activeVol) AuraPurple.copy(alpha = 0.2f) else GlassPanel.copy(alpha = 0.6f))
                                        .border(1.dp, if (activeVol) AuraPurple else GlassBorder, RoundedCornerShape(100.dp))
                                        .clickable {
                                            showOnlyStarred = false
                                            selectedVolumeRootPath = vol.rootDir.absolutePath
                                            updateCurrentDir(vol.rootDir)
                                            selectedPaths = emptySet()
                                        }
                                        .padding(horizontal = 12.dp, vertical = 7.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.SdStorage, contentDescription = null, tint = if (activeVol) AuraPurple else TextSecondary, modifier = Modifier.size(13.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(vol.label, color = if (activeVol) AuraPurple else TextSecondary, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                                    }
                                }
                            }
                        }

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
                                            updateCurrentDir(dir)
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

                    Spacer(modifier = Modifier.height(8.dp))

                    // Sleek Compact Action Toolbar (Breadcrumbs on left, Search Icon + Create + Hidden + Grid on right)
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
                                            updateCurrentDir(parent)
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
                                                updateCurrentDir(file)
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

                        Spacer(modifier = Modifier.width(6.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            SearchIconToggleButton(
                                isExpanded = searchExpanded,
                                hasQuery = searchQuery.isNotBlank(),
                                accentColor = AuraCyan,
                                size = 36.dp,
                                tag = "explorer_search_toggle"
                            ) {
                                if (searchExpanded) {
                                    searchExpanded = false
                                    searchQuery = ""
                                } else {
                                    searchExpanded = true
                                }
                            }

                            Box {
                                IconButton(
                                    onClick = { showCreateMenu = true },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(GlassPanel.copy(alpha = 0.7f))
                                        .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Create New", tint = AuraCyan, modifier = Modifier.size(18.dp))
                                }
                                DropdownMenu(
                                    expanded = showCreateMenu,
                                    onDismissRequest = { showCreateMenu = false },
                                    modifier = Modifier.background(GlassPanel)
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("New Folder", color = TextPrimary) },
                                        leadingIcon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null, tint = AuraCyan) },
                                        onClick = {
                                            showCreateMenu = false
                                            showCreateFolderDialog = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("New Text File", color = TextPrimary) },
                                        leadingIcon = { Icon(Icons.Default.NoteAdd, contentDescription = null, tint = AuraGreen) },
                                        onClick = {
                                            showCreateMenu = false
                                            showCreateFileDialog = true
                                        }
                                    )
                                }
                            }

                            IconButton(
                                onClick = { showHiddenFiles = !showHiddenFiles },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (showHiddenFiles) AuraPurple.copy(alpha = 0.2f) else GlassPanel.copy(alpha = 0.7f))
                                    .border(1.dp, if (showHiddenFiles) AuraPurple else GlassBorder, RoundedCornerShape(12.dp))
                            ) {
                                Icon(
                                    imageVector = if (showHiddenFiles) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = "Toggle hidden files",
                                    tint = if (showHiddenFiles) AuraPurple else TextSecondary,
                                    modifier = Modifier.size(17.dp)
                                )
                            }

                            IconButton(
                                onClick = { isExplorerGrid = !isExplorerGrid },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isExplorerGrid) AuraCyan.copy(alpha = 0.18f) else GlassPanel.copy(alpha = 0.7f))
                                    .border(1.dp, if (isExplorerGrid) AuraCyan else GlassBorder, RoundedCornerShape(12.dp))
                            ) {
                                Icon(
                                    imageVector = if (isExplorerGrid) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                                    contentDescription = "Toggle Grid or List View",
                                    tint = if (isExplorerGrid) AuraCyan else TextSecondary,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }
                    }

                    ExpandableSearchBar(
                        visible = searchExpanded,
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        placeholder = if (searchRecursive) "Search all subfolders..." else "Filter current folder...",
                        onClose = {
                            searchQuery = ""
                            searchExpanded = false
                        },
                        trailingExtra = {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (searchRecursive) AuraCyan.copy(alpha = 0.2f) else GlassPanel.copy(alpha = 0.7f))
                                    .border(1.dp, if (searchRecursive) AuraCyan else GlassBorder, RoundedCornerShape(12.dp))
                                    .clickable { searchRecursive = !searchRecursive }
                                    .padding(horizontal = 10.dp, vertical = 14.dp)
                            ) {
                                Text(
                                    text = if (searchRecursive) "Deep: ON" else "Deep: OFF",
                                    color = if (searchRecursive) AuraCyan else TextSecondary,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Sort Chips + Direction + Select All
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("Date", "Size", "Name").forEach { mode ->
                                val active = sortBy == mode
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(100.dp))
                                        .background(if (active) AuraCyan.copy(alpha = 0.15f) else Color.Transparent)
                                        .border(1.dp, if (active) AuraCyan.copy(alpha = 0.4f) else GlassBorder, RoundedCornerShape(100.dp))
                                        .clickable {
                                            if (active) sortAscending = !sortAscending else {
                                                sortBy = mode
                                                sortAscending = (mode == "Name")
                                            }
                                        }
                                        .padding(horizontal = 9.dp, vertical = 4.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(mode, color = if (active) AuraCyan else TextTertiary, style = MaterialTheme.typography.labelSmall)
                                        if (active) {
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Icon(
                                                imageVector = if (sortAscending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                                                contentDescription = null,
                                                tint = AuraCyan,
                                                modifier = Modifier.size(11.dp)
                                            )
                                        }
                                    }
                                }
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
                                text = "${clip.paths.size} item(s) ready to ${if (clip.operation == SharedClipboardOp.COPY) "copy" else "move"}",
                                color = TextPrimary,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Button(
                                    onClick = {
                                        coroutineScope.launch {
                                            val count = withContext(Dispatchers.IO) {
                                                FileManagerOps.executeClipboardPaste(context, clip, currentDir)
                                            }
                                            Toast.makeText(
                                                context,
                                                "${if (clip.operation == SharedClipboardOp.COPY) "Copied" else "Moved"} $count item(s)",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            SharedClipboardHolder.clear()
                                            selectedPaths = emptySet()
                                            forceFilesRescan = true
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
                                IconButton(onClick = { SharedClipboardHolder.clear() }, modifier = Modifier.size(28.dp)) {
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
                                    SharedClipboardHolder.setClipboard(selectedPaths, SharedClipboardOp.COPY)
                                    selectedPaths = emptySet()
                                    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                                }
                                MiniBarAction(Icons.AutoMirrored.Filled.DriveFileMove, "Move", AuraOrange) {
                                    SharedClipboardHolder.setClipboard(selectedPaths, SharedClipboardOp.MOVE)
                                    selectedPaths = emptySet()
                                    Toast.makeText(context, "Open target folder to Move", Toast.LENGTH_SHORT).show()
                                }
                                MiniBarAction(Icons.Default.Archive, "Zip", AuraGreen) {
                                    val targets = selectedPaths.map { File(it) }
                                    coroutineScope.launch {
                                        val archiveName = if (targets.size == 1) "${targets.first().nameWithoutExtension}.zip" else "archive_${System.currentTimeMillis() / 1000}.zip"
                                        val ok = withContext(Dispatchers.IO) {
                                            FileManagerOps.compressFilesToZip(targets, File(currentDir, archiveName))
                                        }
                                        Toast.makeText(context, if (ok) "Saved $archiveName" else "Zip failed", Toast.LENGTH_SHORT).show()
                                        selectedPaths = emptySet()
                                        forceFilesRescan = true
                                        refreshTrigger++
                                    }
                                }
                                MiniBarAction(Icons.Default.Lock, "Vault", AuraPurple) {
                                    coroutineScope.launch {
                                        val lockedCount = withContext(Dispatchers.IO) {
                                            selectedPaths.count { p ->
                                                val f = File(p)
                                                f.isFile && FileManagerOps.encryptFileToVault(context, f)
                                            }
                                        }
                                        Toast.makeText(context, "Locked $lockedCount file(s) in Vault", Toast.LENGTH_SHORT).show()
                                        selectedPaths = emptySet()
                                        forceFilesRescan = true
                                        refreshTrigger++
                                    }
                                }
                                MiniBarAction(Icons.Default.Delete, "Delete", TextSecondary) {
                                    deleteWithShredder = false
                                    showDeleteConfirm = true
                                }
                                MiniBarAction(Icons.Default.DeleteForever, "Shred", AuraRose) {
                                    deleteWithShredder = true
                                    showDeleteConfirm = true
                                }
                                IconButton(onClick = { selectedPaths = emptySet() }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextSecondary, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    if (isBrowserLoading || isRecursiveSearching) {
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
                    } else if (isExplorerGrid) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 112.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            gridItems(filteredBrowserItems, key = { it.file.absolutePath }) { item ->
                                val path = item.file.absolutePath
                                val isSelected = path in selectedPaths
                                BrowserGridCard(
                                    item = item,
                                    isSelected = isSelected,
                                    isStarred = path in starredPaths,
                                    onClick = {
                                        if (selectedPaths.isNotEmpty()) {
                                            selectedPaths = if (path in selectedPaths) selectedPaths - path else selectedPaths + path
                                        } else if (item.isDirectory) {
                                            showOnlyStarred = false
                                            updateCurrentDir(item.file)
                                        } else {
                                            openFileSmart(item.file)
                                        }
                                    },
                                    onLongClick = { itemForMenu = item }
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
                                    relativeHint = if (searchRecursive && searchQuery.trim().length >= 2) {
                                        item.file.parentFile?.relativeToOrNull(currentDir)?.path?.takeIf { it.isNotBlank() }
                                    } else null,
                                    onToggleSelect = {
                                        selectedPaths = if (path in selectedPaths) selectedPaths - path else selectedPaths + path
                                    },
                                    onClick = {
                                        if (selectedPaths.isNotEmpty()) {
                                            selectedPaths = if (path in selectedPaths) selectedPaths - path else selectedPaths + path
                                        } else if (item.isDirectory) {
                                            showOnlyStarred = false
                                            updateCurrentDir(item.file)
                                        } else {
                                            openFileSmart(item.file)
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
                calculatedSize = FileManagerOps.computeRecursiveSize(item.file)
            } else if (item.sizeBytes in 1..(50L * 1024 * 1024)) {
                sha256Hash = FileManagerOps.computeSha256Short(item.file)
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
        containerColor = GlassPanel,
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
private fun ExplorerThumbnail(
    file: File,
    isDirectory: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val ext = file.extension.lowercase()
    val supportsThumb = !isDirectory && (
        ext in FileManagerOps.IMAGE_EXTENSIONS ||
            ext in setOf("mp4", "mkv", "mov", "avi", "webm") ||
            ext == "apk"
        )
    var bitmap by remember(file.absolutePath) {
        mutableStateOf(if (supportsThumb) explorerThumbCache.get(file.absolutePath) else null)
    }

    LaunchedEffect(file.absolutePath, supportsThumb) {
        if (supportsThumb && bitmap == null) {
            val bmp = withContext(Dispatchers.IO) {
                FileManagerOps.loadFileThumbnailBitmap(context, file)
            }
            if (bmp != null) {
                explorerThumbCache.put(file.absolutePath, bmp)
                bitmap = bmp
            }
        }
    }

    val (icon, tint) = when {
        isDirectory -> Icons.Default.Folder to AuraCyan
        ext in FileManagerOps.IMAGE_EXTENSIONS -> Icons.Default.Image to AuraCyan
        ext in setOf("mp4", "mkv", "mov", "avi", "webm") -> Icons.Default.VideoLibrary to AuraOrange
        ext in setOf("mp3", "wav", "flac", "m4a", "ogg") -> Icons.Default.AudioFile to AuraRose
        ext == "apk" -> Icons.Default.Android to AuraGreen
        ext == "zip" -> Icons.Default.FolderZip to AuraGreen
        ext in setOf("pdf", "doc", "docx", "txt", "xls", "xlsx", "ppt", "pptx", "json", "md", "xml") -> Icons.Default.Description to AuraPurple
        else -> Icons.AutoMirrored.Filled.InsertDriveFile to TextSecondary
    }

    Box(
        modifier = modifier.background(tint.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = file.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun RecentFileChipCard(
    recent: RecentMediaFile,
    onClick: () -> Unit
) {
    val f = remember(recent.path) { File(recent.path) }
    Column(
        modifier = Modifier
            .width(126.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(GlassPanel.copy(alpha = 0.65f))
            .border(1.dp, GlassBorder, RoundedCornerShape(18.dp))
            .clickable { onClick() }
            .padding(10.dp)
    ) {
        ExplorerThumbnail(
            file = f,
            isDirectory = false,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(12.dp))
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = recent.name,
            color = TextPrimary,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = formatBytes(recent.size),
            color = TextTertiary,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BrowserGridCard(
    item: BrowserFileItem,
    isSelected: Boolean,
    isStarred: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) AuraCyan.copy(alpha = 0.16f) else GlassPanel.copy(alpha = 0.6f))
            .border(1.dp, if (isSelected) AuraCyan else GlassBorder, RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(74.dp)
                .clip(RoundedCornerShape(10.dp))
        ) {
            ExplorerThumbnail(file = item.file, isDirectory = item.isDirectory, modifier = Modifier.fillMaxSize())
            if (isStarred) {
                Icon(
                    Icons.Default.Star,
                    contentDescription = "Starred",
                    tint = AuraOrange,
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(14.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = item.name,
            color = TextPrimary,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = if (item.isDirectory) "${item.childCount} items" else formatBytes(item.sizeBytes),
            color = TextTertiary,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)
        )
    }
}

@Composable
private fun BrowserItemCard(
    item: BrowserFileItem,
    isSelected: Boolean,
    isStarred: Boolean,
    relativeHint: String? = null,
    onToggleSelect: () -> Unit,
    onClick: () -> Unit,
    onMoreClick: () -> Unit
) {
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
        ExplorerThumbnail(
            file = item.file,
            isDirectory = item.isDirectory,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
        )
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
            val baseMeta = if (item.isDirectory) {
                "${item.childCount} items • $dateStr"
            } else {
                "${formatBytes(item.sizeBytes)} • $dateStr"
            }
            val metaText = if (!relativeHint.isNullOrBlank()) "$baseMeta • $relativeHint" else baseMeta
            Text(metaText, color = TextTertiary, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onToggleSelect, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector = if (isSelected) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                contentDescription = "Select",
                tint = if (isSelected) AuraCyan else TextTertiary,
                modifier = Modifier.size(20.dp)
            )
        }
        IconButton(onClick = onMoreClick, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "Actions",
                tint = TextSecondary,
                modifier = Modifier.size(20.dp)
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
        val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} NOT IN (${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE}, ${MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO}, ${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO}) AND ${MediaStore.MediaColumns.SIZE} > 0"
        context.contentResolver.query(uri, projection, selection, null, null)?.use { cursor ->
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "${formatBytes(free)} Free",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = AuraCyan
                )
                Text(
                    text = "${formatBytes(used)} used of ${formatBytes(total)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(100.dp))
                    .background(AuraCyan.copy(alpha = 0.14f))
                    .border(1.dp, AuraCyan.copy(alpha = 0.35f), RoundedCornerShape(100.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "$usedPct% USED",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = AuraCyan
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Segmented horizontal storage bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(GlassBorder.copy(alpha = 0.45f))
        ) {
            categories.filter { it.size > 0L }.forEach { cat ->
                val weightVal = (cat.size.toFloat() / safeTotal.toFloat()).coerceIn(0.01f, 1f)
                Box(
                    modifier = Modifier
                        .weight(weightVal)
                        .height(8.dp)
                        .background(cat.color)
                )
            }
            val freeWeight = (free.toFloat() / safeTotal.toFloat()).coerceIn(0f, 1f)
            if (freeWeight > 0.01f) {
                Spacer(modifier = Modifier.weight(freeWeight))
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            categories.filter { it.size > 0 }.forEach { cat ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(cat.color))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "${cat.name} (${formatBytes(cat.size)})",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = TextSecondary
                    )
                }
            }
        }
    }
}

@Composable
fun CategoryItemRow(cat: StorageCategory, totalSpace: Long, onClick: () -> Unit = {}) {
    val isInteractive = cat.name in listOf("Images", "Videos", "Audio", "Documents", "Downloads", "APKs", "Apps & Data")
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
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(cat.color.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(cat.icon, contentDescription = cat.name, tint = cat.color, modifier = Modifier.size(16.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(cat.name, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
            Spacer(modifier = Modifier.height(3.dp))
            LinearProgressIndicator(
                progress = { (cat.size.toFloat() / safeTotal.toFloat()).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                color = cat.color,
                trackColor = GlassBorder.copy(alpha = 0.45f),
                strokeCap = StrokeCap.Round
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(formatBytes(cat.size), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = TextSecondary)
    }
}
