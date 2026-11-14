package com.example.ui.screens

import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.automirrored.filled.ViewList
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.GlassCard
import com.example.formatBytes
import com.example.service.FileShredder
import com.example.service.ShredStandard
import com.example.service.ShredTarget
import com.example.ui.theme.AppTheme
import com.example.ui.theme.AuraCyan
import com.example.ui.theme.AuraGreen
import com.example.ui.theme.AuraOrange
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FileCategoryScreen(categoryName: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var files by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var sortOption by remember { mutableStateOf("Size (High to Low)") }
    var showSortMenu by remember { mutableStateOf(false) }

    var isGridView by remember {
        mutableStateOf(categoryName == "Images" || categoryName == "Videos")
    }

    var selectedItems by remember { mutableStateOf<Set<FileItem>>(emptySet()) }
    val isSelectionMode = selectedItems.isNotEmpty()

    var showNormalDeleteDialog by remember { mutableStateOf(false) }
    var showShredDialog by remember { mutableStateOf(false) }
    var selectedShredStandard by remember { mutableStateOf(ShredStandard.DOD_5220_22_M) }
    var isShredding by remember { mutableStateOf(false) }
    var shredProgress by remember { mutableFloatStateOf(0f) }
    var shredStatusText by remember { mutableStateOf("") }

    val deleteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            files = files.filterNot { it in selectedItems }
            selectedItems = emptySet()
            Toast.makeText(context, "Files deleted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Deletion cancelled or denied", Toast.LENGTH_SHORT).show()
        }
    }

    BackHandler(enabled = isSelectionMode) {
        selectedItems = emptySet()
    }

    LaunchedEffect(categoryName) {
        isLoading = true
        withContext(Dispatchers.IO) {
            files = fetchFilesForCategory(context, categoryName)
            isLoading = false
        }
    }

    val sortedFiles = remember(files, sortOption, searchQuery) {
        val filtered = if (searchQuery.isBlank()) {
            files
        } else {
            val q = searchQuery.trim().lowercase()
            files.filter { it.name.lowercase().contains(q) }
        }
        when (sortOption) {
            "Size (High to Low)" -> filtered.sortedByDescending { it.size }
            "Size (Low to High)" -> filtered.sortedBy { it.size }
            "Date (Newest)" -> filtered.sortedByDescending { it.dateModified }
            "Date (Oldest)" -> filtered.sortedBy { it.dateModified }
            "Name (A-Z)" -> filtered.sortedBy { it.name.lowercase() }
            else -> filtered
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
        try {
            val uri: Uri = if (item.path.isNotBlank() && File(item.path).exists()) {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(item.path))
            } else {
                item.contentUri
            }
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, item.mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Open with"))
        } catch (_: Exception) {
            Toast.makeText(context, "No application available to open this file", Toast.LENGTH_SHORT).show()
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
            val intent = if (uris.size == 1) {
                Intent(Intent.ACTION_SEND).apply {
                    type = selectedItems.first().mimeType
                    putExtra(Intent.EXTRA_STREAM, uris.first())
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            } else {
                Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "*/*"
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
            context.startActivity(Intent.createChooser(intent, "Share Files"))
        } catch (_: Exception) {
            Toast.makeText(context, "Unable to share selected files", Toast.LENGTH_SHORT).show()
        }
    }

    fun deleteSelectedStandard() {
        val itemsToDelete = selectedItems.toList()
        scope.launch(Dispatchers.IO) {
            val failedUris = mutableListOf<Uri>()
            val deletedItems = mutableSetOf<FileItem>()

            for (item in itemsToDelete) {
                try {
                    var deleted = false
                    if (item.path.isNotBlank()) {
                        val f = File(item.path)
                        if (f.exists() && f.delete()) {
                            deleted = true
                        }
                    }
                    val rows = context.contentResolver.delete(item.contentUri, null, null)
                    if (rows > 0 || deleted) {
                        deletedItems.add(item)
                    } else {
                        failedUris.add(item.contentUri)
                    }
                } catch (_: SecurityException) {
                    failedUris.add(item.contentUri)
                } catch (_: Exception) {
                    failedUris.add(item.contentUri)
                }
            }

            withContext(Dispatchers.Main) {
                if (deletedItems.isNotEmpty()) {
                    files = files.filterNot { it in deletedItems }
                    selectedItems = selectedItems - deletedItems
                }
                if (failedUris.isNotEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    try {
                        val pendingIntent = MediaStore.createDeleteRequest(context.contentResolver, failedUris)
                        deleteLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
                    } catch (_: Exception) {
                        Toast.makeText(context, "Could not delete some protected files", Toast.LENGTH_SHORT).show()
                    }
                } else if (deletedItems.isNotEmpty()) {
                    Toast.makeText(context, "Deleted ${deletedItems.size} files", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun shredSelectedFiles() {
        isShredding = true
        shredProgress = 0f
        shredStatusText = "Starting ${selectedShredStandard.title}..."
        val itemsSnapshot = selectedItems.toList()
        scope.launch {
            val targets = itemsSnapshot.map { ShredTarget(File(it.path), it.contentUri) }
            val result = withContext(Dispatchers.IO) {
                FileShredder.shredTargets(
                    context = context,
                    targets = targets,
                    standard = selectedShredStandard
                ) { done, total, label ->
                    shredProgress = done.toFloat() / total.toFloat()
                    shredStatusText = label
                }
            }

            val deletedUris = itemsSnapshot
                .filter { item ->
                    result.blockedForOverwrite.none { it.contentUri == item.contentUri } &&
                        result.failedTargets.none { it.contentUri == item.contentUri }
                }
                .toSet()

            files = files.filterNot { it in deletedUris }
            selectedItems = selectedItems - deletedUris
            isShredding = false
            showShredDialog = false

            if (result.blockedForOverwrite.isNotEmpty()) {
                val hint = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !android.os.Environment.isExternalStorageManager()) {
                    "Grant All Files Access for byte-level DoD/NIST shredding, or use Normal Delete for ${result.blockedForOverwrite.size} protected file(s)."
                } else {
                    "Shredded ${result.shreddedCount} file(s); ${result.blockedForOverwrite.size} blocked from direct overwrite."
                }
                Toast.makeText(context, hint, Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(
                    context,
                    "Shredded ${result.shreddedCount} file(s) (${formatBytes(result.totalBytesWiped)})",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
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

    if (showShredDialog) {
        AlertDialog(
            onDismissRequest = { if (!isShredding) showShredDialog = false },
            containerColor = GlassPanel,
            title = { Text("Standard File Shredder", color = AuraRose, fontWeight = FontWeight.Bold) },
            text = {
                if (isShredding) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Text(shredStatusText.ifBlank { "Overwriting blocks & obfuscating filename..." }, color = TextSecondary)
                        Spacer(modifier = Modifier.height(16.dp))
                        LinearProgressIndicator(
                            progress = { shredProgress },
                            color = AuraRose,
                            trackColor = GlassBorder,
                            modifier = Modifier.fillMaxWidth()
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
                    }
                }
            },
            confirmButton = {
                if (!isShredding) {
                    Button(
                        onClick = { shredSelectedFiles() },
                        colors = ButtonDefaults.buttonColors(containerColor = AuraRose)
                    ) {
                        Text("SHRED FOREVER", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                if (!isShredding) {
                    TextButton(onClick = { showShredDialog = false }) {
                        Text("Cancel", color = TextSecondary)
                    }
                }
            }
        )
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
            if (!isLoading && files.isNotEmpty()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search $categoryName...", color = TextTertiary) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = AuraCyan) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextSecondary)
                            }
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AuraCyan,
                        unfocusedBorderColor = GlassBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                )
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AuraCyan)
                }
            } else if (sortedFiles.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (searchQuery.isNotBlank()) "No matching files found"
                        else "No files found in $categoryName",
                        color = TextSecondary
                    )
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
                                onLongClick = { toggleSelection(file) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MediaThumbnail(
    file: FileItem,
    category: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var bitmap by remember(file.contentUri) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(file.contentUri) {
        if (category == "Images" || category == "Videos") {
            withContext(Dispatchers.IO) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        bitmap = context.contentResolver.loadThumbnail(file.contentUri, Size(256, 256), null)
                    }
                } catch (_: Exception) {
                }
            }
        }
    }

    val (icon, color) = when (category) {
        "Images" -> Pair(Icons.Default.Image, AuraCyan)
        "Videos" -> Pair(Icons.Default.VideoLibrary, AuraOrange)
        "Audio" -> Pair(Icons.Default.AudioFile, AuraRose)
        "Documents" -> Pair(Icons.Default.Description, AuraGreen)
        else -> Pair(Icons.AutoMirrored.Filled.InsertDriveFile, Color.Gray)
    }

    Box(
        modifier = modifier.background(color.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
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
    onLongClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }

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
                Text(
                    text = "${formatBytes(file.size)}  •  ${dateFormat.format(Date(file.dateModified * 1000))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary
                )
            }
            if (isSelectionMode) {
                Spacer(modifier = Modifier.width(12.dp))
                Icon(
                    imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (isSelected) AuraCyan else TextTertiary
                )
            }
        }
    }
}

private fun fetchFilesForCategory(context: Context, category: String): List<FileItem> {
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

    try {
        context.contentResolver.query(
            baseUri,
            projection,
            null,
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

                if (category == "Documents") {
                    val ext = File(path.ifBlank { name }).extension.lowercase()
                    if (ext !in docExtensions) continue
                }

                val itemUri = ContentUris.withAppendedId(baseUri, id)
                list.add(FileItem(id, name, size, date, path, itemUri, mime))
            }
        }
    } catch (_: Exception) {
    }
    return list
}
