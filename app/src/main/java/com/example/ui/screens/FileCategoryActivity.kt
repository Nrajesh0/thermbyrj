package com.example.ui.screens

import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VideoLibrary
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.GlassCard
import com.example.InfoRow
import com.example.formatBytes
import com.example.service.FileManagerOps
import com.example.service.FileShredder
import com.example.service.PendingExplorerNavigation
import com.example.service.PermissionUtils
import com.example.service.SharedClipboardHolder
import com.example.service.SharedClipboardOp
import com.example.service.ShredJobCoordinator
import com.example.service.ShredStandard
import com.example.service.ShredTarget
import com.example.service.StorageScanCache
import com.example.service.safeStartActivity
import com.example.service.toSafeComposeBitmap
import com.example.ui.components.ActiveFilePreview
import com.example.ui.components.BuiltInFilePreviewHost
import com.example.ui.components.ExpandableSearchBar
import com.example.ui.components.SearchIconToggleButton
import com.example.ui.components.openExternalWithProvider
import com.example.ui.components.resolveInAppViewerType
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class FileItem(
    val id: Long,
    val name: String,
    val size: Long,
    val dateModified: Long,
    val path: String,
    val contentUri: Uri,
    val mimeType: String
)

class FileCategoryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val categoryName = intent.getStringExtra("CATEGORY_NAME") ?: "Files"
        enableEdgeToEdge()
        setContent {
            AppTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Obsidian) {
                    FileCategoryScreen(categoryName = categoryName, onBack = { finish() })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun FileCategoryScreen(categoryName: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var files by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedAlbum by rememberSaveable { mutableStateOf("ALL") }
    var sortOption by rememberSaveable { mutableStateOf("Size (High to Low)") }
    var showSortMenu by remember { mutableStateOf(false) }

    var isGridView by rememberSaveable {
        mutableStateOf(categoryName == "Images" || categoryName == "Videos")
    }

    var selectedItems by remember { mutableStateOf<Set<FileItem>>(emptySet()) }
    val isSelectionMode = selectedItems.isNotEmpty()

    var itemForMenu by remember { mutableStateOf<FileItem?>(null) }
    var itemForDetails by remember { mutableStateOf<FileItem?>(null) }
    var itemForRename by remember { mutableStateOf<FileItem?>(null) }
    var renameInput by remember { mutableStateOf("") }
    var activeFilePreview by remember { mutableStateOf<ActiveFilePreview?>(null) }

    var showNormalDeleteDialog by remember { mutableStateOf(false) }
    var showShredDialog by remember { mutableStateOf(false) }
    var showAllFilesPromptDialog by remember { mutableStateOf(false) }
    var blockedShredCount by remember { mutableIntStateOf(0) }
    var selectedShredStandard by remember { mutableStateOf(ShredStandard.DOD_5220_22_M) }
    var pendingDeleteUris by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    var refreshTrigger by remember { mutableIntStateOf(0) }
    var mediaPermPromptAttempted by rememberSaveable { mutableStateOf(false) }

    var hasMediaPerm by remember { mutableStateOf(PermissionUtils.hasMediaPermissions(context)) }
    var hasManageStorage by remember { mutableStateOf(PermissionUtils.hasAllFilesPermission()) }

    val shredState by ShredJobCoordinator.state.collectAsStateWithLifecycle()
    var lastHandledShredCompletionId by rememberSaveable { mutableStateOf(shredState.completionId) }

    val mediaPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        val granted = PermissionUtils.hasMediaPermissions(context)
        hasMediaPerm = granted
        if (!granted && mediaPermPromptAttempted) {
            Toast.makeText(context, "Enable Photos, Videos & Audio in App Permissions", Toast.LENGTH_SHORT).show()
            PermissionUtils.launchAppDetailsSettings(context)
        }
        mediaPermPromptAttempted = true
        refreshTrigger++
    }

    val deleteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val pendingSet = pendingDeleteUris.toSet()
        pendingDeleteUris = emptyList()
        if (result.resultCode == Activity.RESULT_OK) {
            if (pendingSet.isNotEmpty()) {
                val removed = files.filter { it.contentUri.toString() in pendingSet }.toSet()
                removed.forEach { mediaThumbnailCache.remove(it.contentUri) }
                files = files.filterNot { it in removed }
                selectedItems = selectedItems - removed
            } else {
                selectedItems.forEach { mediaThumbnailCache.remove(it.contentUri) }
                files = files.filterNot { it in selectedItems }
                selectedItems = emptySet()
            }
            StorageScanCache.invalidate()
            refreshTrigger++
            Toast.makeText(context, "Deleted successfully", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Deletion cancelled or denied", Toast.LENGTH_SHORT).show()
        }
    }

    BackHandler(enabled = isSelectionMode) {
        selectedItems = emptySet()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasMediaPerm = PermissionUtils.hasMediaPermissions(context)
                hasManageStorage = PermissionUtils.hasAllFilesPermission()
                refreshTrigger++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(shredState.completionId) {
        if (!shredState.isRunning && shredState.completionId != 0L && shredState.completionId != lastHandledShredCompletionId) {
            lastHandledShredCompletionId = shredState.completionId
            val result = shredState.lastResult
            if (result != null) {
                val blockedUris = result.blockedForOverwrite.mapNotNull { it.contentUri?.toString() }.toSet()
                val failedUris = result.failedTargets.mapNotNull { it.contentUri?.toString() }.toSet()
                val deletedItems = selectedItems.filter { item ->
                    val uriStr = item.contentUri.toString()
                    val stillExistsOnDisk = item.path.isNotBlank() && File(item.path).exists()
                    uriStr !in blockedUris && uriStr !in failedUris && (!result.wasCancelled || !stillExistsOnDisk)
                }.toSet()
                deletedItems.forEach { mediaThumbnailCache.remove(it.contentUri) }
                files = files.filterNot { it in deletedItems }
                selectedItems = selectedItems - deletedItems
                StorageScanCache.invalidate()
                refreshTrigger++

                if (result.blockedForOverwrite.isNotEmpty() && !PermissionUtils.hasAllFilesPermission()) {
                    blockedShredCount = result.blockedForOverwrite.size
                    showShredDialog = false
                    showAllFilesPromptDialog = true
                } else {
                    showShredDialog = false
                    val msg = when {
                        result.wasCancelled ->
                            "Shredding cancelled (${result.shreddedCount} file(s) completed)"
                        result.blockedForOverwrite.isNotEmpty() ->
                            "Shredded ${result.shreddedCount} file(s); ${result.blockedForOverwrite.size} blocked."
                        else ->
                            "Shredded ${result.shreddedCount} file(s) (${formatBytes(result.totalBytesWiped)})"
                    }
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            } else {
                showShredDialog = false
            }
        }
    }

    LaunchedEffect(categoryName, refreshTrigger, hasMediaPerm, hasManageStorage) {
        if (files.isEmpty()) isLoading = true
        val loaded = withContext(Dispatchers.IO) {
            fetchFilesForCategory(context, categoryName)
        }
        files = loaded
        val liveKeys = loaded.map { it.contentUri }.toSet()
        if (selectedItems.isNotEmpty()) {
            selectedItems = selectedItems.filter { it.contentUri in liveKeys }.toSet()
        }
        isLoading = false
    }

    val albums = remember(files) {
        files.mapNotNull { item ->
            val p = item.path
            if (p.isNotBlank()) File(p).parentFile?.name?.takeIf { it.isNotBlank() } else null
        }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }
    }

    val sortedFiles = remember(files, selectedAlbum, sortOption, searchQuery) {
        val albumFiltered = if (selectedAlbum == "ALL") {
            files
        } else {
            files.filter { item ->
                item.path.isNotBlank() && File(item.path).parentFile?.name == selectedAlbum
            }
        }
        val filtered = if (searchQuery.isBlank()) {
            albumFiltered
        } else {
            val q = searchQuery.trim().lowercase()
            albumFiltered.filter { it.name.lowercase().contains(q) }
        }
        val deduped = filtered.distinctBy { "${it.id}_${it.path}" }
        when (sortOption) {
            "Size (High to Low)" -> deduped.sortedByDescending { it.size }
            "Size (Low to High)" -> deduped.sortedBy { it.size }
            "Date (Newest)" -> deduped.sortedByDescending { it.dateModified }
            "Date (Oldest)" -> deduped.sortedBy { it.dateModified }
            "Name (A-Z)" -> deduped.sortedBy { it.name.lowercase() }
            else -> deduped
        }
    }

    fun toggleSelection(item: FileItem) {
        selectedItems = if (selectedItems.contains(item)) {
            selectedItems - item
        } else {
            selectedItems + item
        }
    }

    fun openFile(item: FileItem) {
        val f = if (item.path.isNotBlank()) File(item.path) else null
        if (f != null && f.exists()) {
            val vType = resolveInAppViewerType(f)
            if (vType != null) {
                activeFilePreview = ActiveFilePreview(f, item.contentUri, vType)
                return
            }
        }
        openExternalWithProvider(context, f, item.contentUri, item.mimeType)
    }

    fun jumpToParentFolder(item: FileItem) {
        val parent = if (item.path.isNotBlank()) File(item.path).parentFile else null
        if (parent != null && parent.exists()) {
            PendingExplorerNavigation.openFolder(parent.absolutePath)
            onBack()
        } else {
            Toast.makeText(context, "Folder path unavailable", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareSelectedFiles() {
        val uris = ArrayList<Uri>()
        selectedItems.forEach { item ->
            try {
                val uri = if (item.path.isNotBlank() && File(item.path).exists()) {
                    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(item.path))
                } else {
                    item.contentUri
                }
                uris.add(uri)
            } catch (_: Exception) {
            }
        }
        if (uris.isEmpty()) return
        try {
            val firstSelected = selectedItems.firstOrNull()
            val firstUri = uris.firstOrNull() ?: return
            val intent = if (uris.size == 1) {
                Intent(Intent.ACTION_SEND).apply {
                    type = firstSelected?.mimeType ?: "*/*"
                    putExtra(Intent.EXTRA_STREAM, firstUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            } else {
                Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "*/*"
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
            context.safeStartActivity(Intent.createChooser(intent, "Share Files"), "Unable to share selected files")
        } catch (_: Throwable) {
            Toast.makeText(context, "Unable to share selected files", Toast.LENGTH_SHORT).show()
        }
    }

    fun deleteSelectedStandard() {
        val itemsToDelete = selectedItems.toList()
        if (itemsToDelete.isEmpty()) return
        scope.launch(Dispatchers.IO) {
            val failedUris = mutableListOf<Uri>()
            val deletedItems = mutableSetOf<FileItem>()
            var fallbackSender: android.content.IntentSender? = null

            for (item in itemsToDelete) {
                var unlinked = false
                if (item.path.isNotBlank()) {
                    try {
                        val f = File(item.path)
                        if (!f.exists() || f.delete()) {
                            unlinked = true
                        }
                    } catch (_: Exception) {
                    }
                }

                if (unlinked) {
                    FileShredder.cleanupMediaStoreEntry(context, item.path, item.contentUri)
                    deletedItems.add(item)
                } else {
                    try {
                        val rows = context.contentResolver.delete(item.contentUri, null, null)
                        if (rows > 0) {
                            deletedItems.add(item)
                        } else {
                            failedUris.add(item.contentUri)
                        }
                    } catch (e: SecurityException) {
                        failedUris.add(item.contentUri)
                        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q &&
                            e is android.app.RecoverableSecurityException &&
                            fallbackSender == null
                        ) {
                            fallbackSender = e.userAction.actionIntent.intentSender
                        }
                    } catch (_: Exception) {
                        failedUris.add(item.contentUri)
                    }
                }
            }

            withContext(Dispatchers.Main) {
                if (deletedItems.isNotEmpty()) {
                    deletedItems.forEach { mediaThumbnailCache.remove(it.contentUri) }
                    files = files.filterNot { it in deletedItems }
                    selectedItems = selectedItems - deletedItems
                    StorageScanCache.invalidate()
                    refreshTrigger++
                }
                if (failedUris.isNotEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    try {
                        pendingDeleteUris = failedUris.map { it.toString() }
                        val pendingIntent = MediaStore.createDeleteRequest(context.contentResolver, failedUris)
                        deleteLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
                    } catch (_: Exception) {
                        pendingDeleteUris = emptyList()
                        Toast.makeText(context, "Could not delete protected file(s)", Toast.LENGTH_SHORT).show()
                    }
                } else if (fallbackSender != null) {
                    try {
                        pendingDeleteUris = failedUris.map { it.toString() }
                        deleteLauncher.launch(IntentSenderRequest.Builder(fallbackSender).build())
                    } catch (_: Exception) {
                        pendingDeleteUris = emptyList()
                        Toast.makeText(context, "Could not delete protected file(s)", Toast.LENGTH_SHORT).show()
                    }
                } else if (deletedItems.isNotEmpty()) {
                    Toast.makeText(context, "Deleted ${deletedItems.size} file(s)", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun shredSelectedFiles() {
        val itemsSnapshot = selectedItems.toList()
        if (itemsSnapshot.isEmpty()) return
        val targets = itemsSnapshot.map { ShredTarget(File(it.path), it.contentUri) }
        ShredJobCoordinator.startBackgroundShred(
            context = context,
            targets = targets,
            standard = selectedShredStandard
        )
    }

    if (showNormalDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showNormalDeleteDialog = false },
            containerColor = GlassPanel,
            title = { Text("Normal Delete (${selectedItems.size})", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Fast standard OS removal. Removes files without multi-pass byte overwriting (use Shredder if deleting sensitive documents).",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showNormalDeleteDialog = false
                        deleteSelectedStandard()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AuraCyan)
                ) {
                    Text("DELETE", color = Obsidian, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        showNormalDeleteDialog = false
                        showShredDialog = true
                    }) {
                        Text("Use Shredder", color = AuraRose, fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = { showNormalDeleteDialog = false }) {
                        Text("Cancel", color = TextSecondary)
                    }
                }
            }
        )
    }

    if (showAllFilesPromptDialog) {
        AlertDialog(
            onDismissRequest = { showAllFilesPromptDialog = false },
            containerColor = GlassPanel,
            title = { Text("All Files Access Needed for Shredder", color = AuraOrange, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Android blocks byte-level DoD/NIST overwriting on shared media for $blockedShredCount file(s) without All Files Access. Grant All Files Access to shred directly, or use Normal Delete instead.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showAllFilesPromptDialog = false
                        PermissionUtils.launchAllFilesAccessSettings(context)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AuraOrange)
                ) {
                    Text("OPEN SETTINGS", color = Obsidian, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Row {
                    if (selectedItems.isNotEmpty()) {
                        TextButton(onClick = {
                            showAllFilesPromptDialog = false
                            deleteSelectedStandard()
                        }) {
                            Text("Normal Delete", color = AuraCyan, fontWeight = FontWeight.Bold)
                        }
                    }
                    TextButton(onClick = { showAllFilesPromptDialog = false }) {
                        Text("Cancel", color = TextSecondary)
                    }
                }
            }
        )
    }

    if (showShredDialog) {
        AlertDialog(
            onDismissRequest = { showShredDialog = false },
            containerColor = GlassPanel,
            title = { Text("Standard File Shredder", color = AuraRose, fontWeight = FontWeight.Bold) },
            text = {
                if (shredState.isRunning) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Text(shredState.statusText.ifBlank { "Overwriting blocks & obfuscating filename..." }, color = TextSecondary)
                        Spacer(modifier = Modifier.height(16.dp))
                        LinearProgressIndicator(
                            progress = { shredState.progress },
                            color = AuraRose,
                            trackColor = GlassBorder,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            "Shredding runs in a foreground service so it keeps going if you switch apps. Force-closing the app mid-shred leaves completed files wiped, queued files untouched, and only the active file partially overwritten.",
                            color = TextTertiary,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "Select wipe standard for ${selectedItems.size} file(s):",
                            color = TextPrimary,
                            style = MaterialTheme.typography.bodySmall
                        )
                        ShredStandard.entries.forEach { std ->
                            val selected = selectedShredStandard == std
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (selected) AuraRose.copy(alpha = 0.14f) else Obsidian.copy(alpha = 0.4f))
                                    .clickable { selectedShredStandard = std }
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                    contentDescription = std.title,
                                    tint = if (selected) AuraRose else TextTertiary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        std.title,
                                        color = if (selected) AuraRose else TextPrimary,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        std.subtitle,
                                        color = TextTertiary,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                        if (!hasManageStorage && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(AuraOrange.copy(alpha = 0.12f))
                                    .border(1.dp, AuraOrange.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                    .clickable { PermissionUtils.launchAllFilesAccessSettings(context) }
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "All Files Access needed to overwrite videos/photos directly. Tap to grant.",
                                    color = AuraOrange,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (shredState.isRunning) {
                    TextButton(onClick = { showShredDialog = false }) {
                        Text("Run in Background", color = AuraCyan, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = { shredSelectedFiles() },
                        colors = ButtonDefaults.buttonColors(containerColor = AuraRose)
                    ) {
                        Text("SHRED FOREVER", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                if (shredState.isRunning) {
                    TextButton(onClick = { ShredJobCoordinator.cancelCurrentShred() }) {
                        Text("Stop Shred", color = AuraRose, fontWeight = FontWeight.Bold)
                    }
                } else {
                    TextButton(onClick = { showShredDialog = false }) {
                        Text("Cancel", color = TextSecondary)
                    }
                }
            }
        )
    }

    // Built-in Viewers Host
    BuiltInFilePreviewHost(
        preview = activeFilePreview,
        onDismiss = { activeFilePreview = null },
        onExtractZip = { zipFile ->
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    FileManagerOps.extractZipArchive(context, zipFile, zipFile.parentFile ?: context.filesDir)
                }
                Toast.makeText(context, if (ok) "Extracted ${zipFile.name}" else "Extract failed", Toast.LENGTH_SHORT).show()
                refreshTrigger++
            }
        },
        onShare = { f, uri ->
            val match = files.firstOrNull { it.path == f.absolutePath || it.contentUri == uri }
            if (match != null) {
                selectedItems = setOf(match)
                shareSelectedFiles()
                selectedItems = emptySet()
            }
        },
        onDelete = { f, uri ->
            val match = files.firstOrNull { it.path == f.absolutePath || it.contentUri == uri }
            if (match != null) {
                selectedItems = setOf(match)
                showNormalDeleteDialog = true
            }
        },
        onShowDetails = { f ->
            activeFilePreview = null
            itemForDetails = files.firstOrNull { it.path == f.absolutePath }
        },
        onFileSaved = { refreshTrigger++ }
    )

    // Rename Dialog
    itemForRename?.let { target ->
        AlertDialog(
            onDismissRequest = { itemForRename = null },
            containerColor = GlassPanel,
            title = { Text("Rename File", color = TextPrimary, fontWeight = FontWeight.Bold) },
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
                TextButton(onClick = {
                    val newName = renameInput.trim()
                    if (newName.isNotEmpty() && newName != target.name && target.path.isNotBlank()) {
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) {
                                FileManagerOps.renameFileSafely(context, File(target.path), newName)
                            }
                            Toast.makeText(
                                context,
                                if (ok) "Renamed to $newName" else "Grant All Files Access to rename",
                                Toast.LENGTH_SHORT
                            ).show()
                            if (ok) refreshTrigger++
                        }
                    }
                    itemForRename = null
                }) {
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

    // Item Details Sheet
    itemForDetails?.let { target ->
        CategoryFileDetailsSheet(
            item = target,
            onShowInFolder = {
                itemForDetails = null
                jumpToParentFolder(target)
            },
            onClose = { itemForDetails = null }
        )
    }

    // Item Context Action Sheet
    itemForMenu?.let { target ->
        ModalBottomSheet(
            onDismissRequest = { itemForMenu = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = GlassPanel,
            contentColor = TextPrimary
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Text(
                    text = target.name,
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${formatBytes(target.size)} • ${File(target.path).parentFile?.name ?: categoryName}",
                    color = TextTertiary,
                    style = MaterialTheme.typography.labelSmall
                )
                Spacer(modifier = Modifier.height(16.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    maxItemsInEachRow = 4
                ) {
                    CategorySheetAction(Icons.Default.FolderOpen, "In Folder", AuraCyan) {
                        itemForMenu = null
                        jumpToParentFolder(target)
                    }
                    CategorySheetAction(Icons.Default.Edit, "Rename", AuraCyan) {
                        itemForMenu = null
                        renameInput = target.name
                        itemForRename = target
                    }
                    CategorySheetAction(Icons.Default.ContentCopy, "Copy", AuraCyan) {
                        itemForMenu = null
                        if (target.path.isNotBlank()) {
                            SharedClipboardHolder.setClipboard(setOf(target.path), SharedClipboardOp.COPY)
                            Toast.makeText(context, "Copied — open Explorer folder to paste", Toast.LENGTH_SHORT).show()
                        }
                    }
                    CategorySheetAction(Icons.AutoMirrored.Filled.DriveFileMove, "Move", AuraOrange) {
                        itemForMenu = null
                        if (target.path.isNotBlank()) {
                            SharedClipboardHolder.setClipboard(setOf(target.path), SharedClipboardOp.MOVE)
                            Toast.makeText(context, "Ready to move — open destination in Explorer", Toast.LENGTH_SHORT).show()
                        }
                    }
                    CategorySheetAction(Icons.Default.Share, "Share", AuraCyan) {
                        itemForMenu = null
                        selectedItems = setOf(target)
                        shareSelectedFiles()
                        selectedItems = emptySet()
                    }
                    CategorySheetAction(Icons.Default.Info, "Details", TextSecondary) {
                        itemForMenu = null
                        itemForDetails = target
                    }
                    CategorySheetAction(Icons.Default.Delete, "Delete", TextSecondary) {
                        itemForMenu = null
                        selectedItems = setOf(target)
                        showNormalDeleteDialog = true
                    }
                    CategorySheetAction(Icons.Default.DeleteForever, "Shred", AuraRose) {
                        itemForMenu = null
                        selectedItems = setOf(target)
                        showShredDialog = true
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    if (isSelectionMode) {
                        Text("${selectedItems.size} Selected", color = AuraCyan, fontWeight = FontWeight.Bold)
                    } else {
                        Text("$categoryName (${sortedFiles.size})", color = TextPrimary, fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    if (isSelectionMode) {
                        IconButton(onClick = { selectedItems = emptySet() }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear Selection", tint = TextPrimary)
                        }
                    } else {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                        }
                    }
                },
                actions = {
                    if (isSelectionMode) {
                        val singleItem = if (selectedItems.size == 1) selectedItems.firstOrNull() else null
                        if (singleItem != null) {
                            IconButton(onClick = { itemForMenu = singleItem }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More Actions", tint = AuraCyan)
                            }
                        }
                        IconButton(onClick = {
                            selectedItems = if (selectedItems.size == sortedFiles.size) emptySet() else sortedFiles.toSet()
                        }) {
                            Icon(Icons.Default.SelectAll, contentDescription = "Select All", tint = TextPrimary)
                        }
                        IconButton(onClick = { shareSelectedFiles() }) {
                            Icon(Icons.Default.Share, contentDescription = "Share Selected", tint = AuraCyan)
                        }
                        IconButton(onClick = { showNormalDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Normal Delete", tint = TextPrimary)
                        }
                        IconButton(onClick = { showShredDialog = true }) {
                            Icon(Icons.Default.DeleteForever, contentDescription = "Secure Shred", tint = AuraRose)
                        }
                    } else {
                        SearchIconToggleButton(
                            isExpanded = searchExpanded,
                            hasQuery = searchQuery.isNotBlank(),
                            accentColor = AuraCyan,
                            size = 36.dp,
                            tag = "category_search_toggle"
                        ) {
                            if (searchExpanded) {
                                searchExpanded = false
                                searchQuery = ""
                            } else {
                                searchExpanded = true
                            }
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(onClick = { isGridView = !isGridView }) {
                            Icon(
                                if (isGridView) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                                contentDescription = "Toggle View",
                                tint = AuraCyan
                            )
                        }
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort", tint = TextPrimary)
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false },
                                modifier = Modifier.background(GlassPanel)
                            ) {
                                listOf(
                                    "Size (High to Low)",
                                    "Size (Low to High)",
                                    "Date (Newest)",
                                    "Date (Oldest)",
                                    "Name (A-Z)"
                                ).forEach { option ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                option,
                                                color = if (option == sortOption) AuraCyan else TextPrimary
                                            )
                                        },
                                        onClick = {
                                            sortOption = option
                                            showSortMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            ExpandableSearchBar(
                visible = searchExpanded,
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                placeholder = "Search $categoryName...",
                onClose = {
                    searchQuery = ""
                    searchExpanded = false
                },
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            // Album / Folder Filter Chips
            if (!isLoading && albums.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val allChipSelected = selectedAlbum == "ALL"
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(100.dp))
                            .background(if (allChipSelected) AuraCyan.copy(alpha = 0.18f) else GlassPanel.copy(alpha = 0.65f))
                            .border(1.dp, if (allChipSelected) AuraCyan else GlassBorder, RoundedCornerShape(100.dp))
                            .clickable { selectedAlbum = "ALL" }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "All (${files.size})",
                            color = if (allChipSelected) AuraCyan else TextSecondary,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                    albums.forEach { (albumName, count) ->
                        val active = selectedAlbum == albumName
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(100.dp))
                                .background(if (active) AuraCyan.copy(alpha = 0.18f) else GlassPanel.copy(alpha = 0.65f))
                                .border(1.dp, if (active) AuraCyan else GlassBorder, RoundedCornerShape(100.dp))
                                .clickable { selectedAlbum = albumName }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "$albumName ($count)",
                                color = if (active) AuraCyan else TextSecondary,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
                            )
                        }
                    }
                }
            }

            if (!hasMediaPerm && !hasManageStorage) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(AuraCyan.copy(alpha = 0.12f))
                        .border(1.dp, AuraCyan.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Media Access Needed", color = AuraCyan, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        Text("Allow Photos, Videos & Audio to view & manage $categoryName.", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            try {
                                mediaPermLauncher.launch(PermissionUtils.requiredMediaPermissions())
                            } catch (_: Throwable) {
                                PermissionUtils.launchAppDetailsSettings(context)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AuraCyan)
                    ) {
                        Text("Grant", color = Obsidian, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (shredState.isRunning && !showShredDialog) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(AuraRose.copy(alpha = 0.14f))
                        .border(1.dp, AuraRose.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                        .clickable { showShredDialog = true }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(shredState.statusText.ifBlank { "Shredding in background..." }, color = AuraRose, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { shredState.progress },
                            color = AuraRose,
                            trackColor = GlassBorder,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AuraCyan)
                }
            } else if (sortedFiles.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (searchQuery.isNotBlank()) "No matching files found"
                            else "No files found in $categoryName",
                            color = TextSecondary
                        )
                        if (searchQuery.isBlank() && !hasMediaPerm && !hasManageStorage) {
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedButton(onClick = {
                                try {
                                    mediaPermLauncher.launch(PermissionUtils.requiredMediaPermissions())
                                } catch (_: Throwable) {
                                    PermissionUtils.launchAppDetailsSettings(context)
                                }
                            }) {
                                Text("Grant Media Permission", color = AuraCyan)
                            }
                        }
                    }
                }
            } else {
                if (isGridView) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(sortedFiles, key = { "${it.id}_${it.path}" }) { file ->
                            val isSelected = selectedItems.contains(file)
                            FileGridItem(
                                file = file,
                                category = categoryName,
                                isSelected = isSelected,
                                isSelectionMode = isSelectionMode,
                                onClick = {
                                    if (isSelectionMode) toggleSelection(file) else openFile(file)
                                },
                                onLongClick = { toggleSelection(file) }
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(sortedFiles, key = { "${it.id}_${it.path}" }) { file ->
                            val isSelected = selectedItems.contains(file)
                            FileListItem(
                                file = file,
                                category = categoryName,
                                isSelected = isSelected,
                                isSelectionMode = isSelectionMode,
                                onClick = {
                                    if (isSelectionMode) toggleSelection(file) else openFile(file)
                                },
                                onLongClick = { toggleSelection(file) },
                                onMoreClick = { itemForMenu = file }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategorySheetAction(
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryFileDetailsSheet(
    item: FileItem,
    onShowInFolder: () -> Unit,
    onClose: () -> Unit
) {
    var sha256Hash by remember(item.path) { mutableStateOf<String?>(null) }
    LaunchedEffect(item.path) {
        if (item.path.isNotBlank() && item.size in 1..(50L * 1024 * 1024)) {
            sha256Hash = withContext(Dispatchers.IO) {
                FileManagerOps.computeSha256Short(File(item.path))
            }
        }
    }
    val dateStr = remember(item.dateModified) {
        SimpleDateFormat("MMM d, yyyy • hh:mm a", Locale.US).format(Date(item.dateModified * 1000))
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
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            Text(
                text = "FILE DETAILS",
                color = AuraCyan,
                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(14.dp))
            InfoRow("NAME", item.name)
            InfoRow("ALBUM / FOLDER", File(item.path).parentFile?.name ?: "Shared Storage")
            InfoRow("MIME TYPE", item.mimeType)
            InfoRow("SIZE", formatBytes(item.size))
            InfoRow("MODIFIED", dateStr)
            sha256Hash?.let { hash ->
                InfoRow("SHA-256", hash)
            }
            if (item.path.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("PATH", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                Text(item.path, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = onShowInFolder,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = AuraCyan)
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, tint = Obsidian, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("OPEN FOLDER IN EXPLORER", color = Obsidian, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

private val mediaThumbnailCache = LruCache<Uri, Bitmap>(64)

@Composable
fun MediaThumbnail(
    file: FileItem,
    category: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var bitmap by remember(file.contentUri) { mutableStateOf(mediaThumbnailCache.get(file.contentUri)) }

    LaunchedEffect(file.contentUri, category) {
        if ((category == "Images" || category == "Videos" || category == "APKs" || file.name.endsWith(".apk", true)) && bitmap == null) {
            val signal = CancellationSignal()
            val reg = coroutineContext[kotlinx.coroutines.Job]?.invokeOnCompletion {
                signal.cancel()
            }
            try {
                val loaded = withContext(Dispatchers.IO) {
                    try {
                        if (category == "APKs" || file.name.endsWith(".apk", true)) {
                            if (file.path.isNotBlank()) FileManagerOps.loadFileThumbnailBitmap(context, File(file.path)) else null
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            context.contentResolver.loadThumbnail(file.contentUri, Size(256, 256), signal)
                        } else null
                    } catch (_: Exception) {
                        null
                    }
                }
                if (loaded != null) {
                    mediaThumbnailCache.put(file.contentUri, loaded)
                    bitmap = loaded
                }
            } finally {
                reg?.dispose()
            }
        }
    }

    val (icon, color) = when (category) {
        "Images" -> Pair(Icons.Default.Image, AuraCyan)
        "Videos" -> Pair(Icons.Default.VideoLibrary, AuraOrange)
        "Audio" -> Pair(Icons.Default.AudioFile, AuraRose)
        "Documents" -> Pair(Icons.Default.Description, AuraGreen)
        "Downloads" -> Pair(Icons.Default.Download, AuraCyan)
        "APKs" -> Pair(Icons.Default.Android, AuraGreen)
        else -> Pair(Icons.AutoMirrored.Filled.InsertDriveFile, Color.Gray)
    }

    Box(
        modifier = modifier.background(color.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
    ) {
        val safeBmp = remember(bitmap) { bitmap.toSafeComposeBitmap() }
        if (safeBmp != null) {
            Image(
                bitmap = safeBmp.asImageBitmap(),
                contentDescription = file.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (category == "Videos") {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.PlayCircleOutline, contentDescription = null, tint = Color.White)
                }
            }
        } else {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(28.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileGridItem(
    file: FileItem,
    category: String,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        MediaThumbnail(file = file, category = category, modifier = Modifier.fillMaxSize())

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(horizontal = 6.dp, vertical = 4.dp)
        ) {
            Text(
                text = formatBytes(file.size),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                maxLines = 1
            )
        }

        if (isSelectionMode) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (isSelected) AuraCyan.copy(alpha = 0.25f) else Color.Transparent)
                    .padding(6.dp),
                contentAlignment = Alignment.TopEnd
            ) {
                Icon(
                    imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (isSelected) AuraCyan else Color.White
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileListItem(
    file: FileItem,
    category: String,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMoreClick: () -> Unit = {}
) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }
    val folderName = remember(file.path) {
        if (file.path.isNotBlank()) File(file.path).parentFile?.name else null
    }

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MediaThumbnail(
                file = file,
                category = category,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isSelected) AuraCyan else TextPrimary,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                val folderPart = if (!folderName.isNullOrBlank()) "  •  $folderName" else ""
                Text(
                    text = "${formatBytes(file.size)}  •  ${dateFormat.format(Date(file.dateModified * 1000))}$folderPart",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (isSelectionMode) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (isSelected) AuraCyan else TextTertiary
                )
            } else {
                IconButton(onClick = onMoreClick, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "File Actions",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

private fun fetchFilesForCategory(context: Context, category: String): List<FileItem> {
    if (category == "Downloads" && PermissionUtils.hasAllFilesPermission()) {
        val dlDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
        if (dlDir != null && dlDir.exists()) {
            val rawFiles = dlDir.walkTopDown().filter { it.isFile && it.length() > 0L }.toList()
            if (rawFiles.isNotEmpty()) {
                return rawFiles.mapIndexed { idx, f ->
                    val uri = FileShredder.findMediaContentUriForFile(context, f) ?: Uri.fromFile(f)
                    val ext = f.extension.lowercase()
                    val mime = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
                    FileItem(
                        id = idx.toLong() + 1L,
                        name = f.name,
                        size = f.length(),
                        dateModified = f.lastModified() / 1000L,
                        path = f.absolutePath,
                        contentUri = uri,
                        mimeType = mime
                    )
                }
            }
        }
    }

    val list = mutableListOf<FileItem>()
    val baseUri: Uri = when (category) {
        "Images" -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        "Videos" -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        "Audio" -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        else -> MediaStore.Files.getContentUri("external")
    }

    val defaultMime = when (category) {
        "Images" -> "image/*"
        "Videos" -> "video/*"
        "Audio" -> "audio/*"
        "APKs" -> "application/vnd.android.package-archive"
        else -> "*/*"
    }

    val projection = arrayOf(
        MediaStore.MediaColumns._ID,
        MediaStore.MediaColumns.DISPLAY_NAME,
        MediaStore.MediaColumns.SIZE,
        MediaStore.MediaColumns.DATE_MODIFIED,
        MediaStore.MediaColumns.DATA,
        MediaStore.MediaColumns.MIME_TYPE
    )

    val selection = if (category == "Documents") {
        "${MediaStore.Files.FileColumns.MEDIA_TYPE} NOT IN (${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE}, ${MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO}, ${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO}) AND ${MediaStore.MediaColumns.SIZE} > 0"
    } else {
        "${MediaStore.MediaColumns.SIZE} > 0"
    }

    try {
        context.contentResolver.query(
            baseUri,
            projection,
            selection,
            null,
            "${MediaStore.MediaColumns.SIZE} DESC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
            val dataCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
            val mimeCol = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)

            val docExtensions = setOf(
                "pdf", "txt", "doc", "docx", "ppt", "pptx", "xls", "xlsx",
                "csv", "rtf", "zip", "apk", "json", "xml", "md", "epub"
            )

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val path = if (dataCol >= 0) cursor.getString(dataCol) ?: "" else ""
                val rawName = cursor.getString(nameCol)
                val name = if (!rawName.isNullOrBlank()) rawName else File(path).name.ifBlank { "Item_$id" }
                val size = cursor.getLong(sizeCol)
                val date = cursor.getLong(dateCol)
                val mime = (if (mimeCol >= 0) cursor.getString(mimeCol) else null) ?: defaultMime

                if (size <= 0) continue

                val ext = File(path.ifBlank { name }).extension.lowercase()
                when (category) {
                    "Documents" -> if (ext !in docExtensions) continue
                    "APKs" -> if (ext != "apk") continue
                    "Downloads" -> {
                        if (!path.contains("/Download/", ignoreCase = true) && !path.contains("/Downloads/", ignoreCase = true)) {
                            continue
                        }
                    }
                }

                val itemUri = ContentUris.withAppendedId(baseUri, id)
                list.add(FileItem(id, name, size, date, path, itemUri, mime))
            }
        }
    } catch (_: Exception) {
    }
    return list
}
