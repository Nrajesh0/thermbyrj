package com.example.ui.screens

import android.content.ContentUris
import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.ImageLoader
import coil.decode.VideoFrameDecoder
import com.example.formatBytes
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.*

class FileCategoryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val categoryName = intent.getStringExtra("CATEGORY_NAME") ?: "Files"
        setContent {
            AppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Obsidian
                ) {
                    FileCategoryScreen(
                        categoryName = categoryName,
                        onBack = { finish() }
                    )
                }
            }
        }
    }
}

data class FileInfo(val id: Long, val name: String, val size: Long, val path: String, val dateModified: Long, val contentUri: Uri)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileCategoryScreen(categoryName: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var files by remember { mutableStateOf<List<FileInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var fileToDelete by remember { mutableStateOf<FileInfo?>(null) }
    
    // View modes & Filters
    var viewMode by remember { mutableStateOf("List") } // "List" or "Grid"
    var filterOption by remember { mutableStateOf("All") } // "All", "> 50MB", "Recent"
    var sortOption by remember { mutableStateOf("Date") } // "Date", "Size", "Name"

    val manageStorageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
            Toast.makeText(context, "God Mode Granted", Toast.LENGTH_SHORT).show()
        }
    }

    val loadFiles = {
        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                files = getFilesForCategory(context, categoryName)
                isLoading = false
            }
        }
    }

    LaunchedEffect(categoryName) { loadFiles() }

    val filteredFiles = remember(files, filterOption, sortOption) {
        val filtered = when (filterOption) {
            "> 50MB" -> files.filter { it.size > 50 * 1024 * 1024 }
            "Recent" -> {
                val weekAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)
                files.filter { it.dateModified > weekAgo }
            }
            else -> files
        }
        when (sortOption) {
            "Size" -> filtered.sortedByDescending { it.size }
            "Name" -> filtered.sortedBy { it.name.lowercase() }
            else -> filtered.sortedByDescending { it.dateModified }
        }
    }

    if (fileToDelete != null) {
        AlertDialog(
            onDismissRequest = { fileToDelete = null },
            title = { Text("Secure Delete", color = TextPrimary) },
            text = { Text("Are you sure you want to securely wipe '${fileToDelete?.name}'? This process overwrites the file multiple times and cannot be undone.", color = TextSecondary) },
            containerColor = Color(0xFF1E1E1E),
            confirmButton = {
                TextButton(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
                            Toast.makeText(context, "God Mode required for Secure Wipe", Toast.LENGTH_LONG).show()
                            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))
                            manageStorageLauncher.launch(intent)
                            fileToDelete = null
                        } else {
                            val toDelete = fileToDelete
                            fileToDelete = null
                            if (toDelete != null) {
                                coroutineScope.launch {
                                    isLoading = true
                                    val success = withContext(Dispatchers.IO) {
                                        secureDeleteFile(context, toDelete)
                                    }
                                    if (!success) {
                                        Toast.makeText(context, "Secure wipe failed.", Toast.LENGTH_SHORT).show()
                                    }
                                    loadFiles()
                                }
                            }
                        }
                    }
                ) {
                    Text("Wipe File", color = AuraRose)
                }
            },
            dismissButton = {
                TextButton(onClick = { fileToDelete = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(categoryName, color = TextPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary) }
                },
                actions = {
                    IconButton(onClick = { viewMode = if (viewMode == "List") "Grid" else "List" }) {
                        Icon(
                            if (viewMode == "List") Icons.Default.GridView else Icons.Default.ViewList, 
                            contentDescription = "Toggle View", 
                            tint = TextPrimary
                        )
                    }
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
                            text = { Text("Sort by Date", color = if (sortOption == "Date") AuraCyan else TextPrimary) },
                            onClick = { sortOption = "Date"; expanded = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Sort by Size", color = if (sortOption == "Size") AuraCyan else TextPrimary) },
                            onClick = { sortOption = "Size"; expanded = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Sort by Name", color = if (sortOption == "Name") AuraCyan else TextPrimary) },
                            onClick = { sortOption = "Name"; expanded = false }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AuraCyan)
            }
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                // Filters Row
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), 
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("All", "> 50MB", "Recent").forEach { opt ->
                        FilterChip(
                            selected = filterOption == opt,
                            onClick = { filterOption = opt },
                            label = { Text(opt) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AuraCyan.copy(alpha = 0.2f),
                                selectedLabelColor = AuraCyan,
                                labelColor = TextSecondary,
                                containerColor = Color.Transparent
                            ),
                            border = if (filterOption == opt) BorderStroke(1.dp, AuraCyan) else BorderStroke(1.dp, GlassBorder)
                        )
                    }
                }

                if (filteredFiles.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("No files match criteria", color = TextSecondary)
                    }
                } else if (viewMode == "List") {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 32.dp)
                    ) {
                        items(filteredFiles, key = { it.id }) { file ->
                            CompactFileRow(
                                file = file,
                                onClick = { openFile(context, file.path) },
                                onDeleteClick = { fileToDelete = file }
                            )
                            HorizontalDivider(color = Color.White.copy(alpha = 0.05f), modifier = Modifier.padding(horizontal = 24.dp))
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                        contentPadding = PaddingValues(bottom = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(filteredFiles, key = { it.id }) { file ->
                            GridFileItem(
                                file = file,
                                onClick = { openFile(context, file.path) },
                                onDeleteClick = { fileToDelete = file }
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun secureDeleteFile(context: Context, fileInfo: FileInfo): Boolean {
    val file = File(fileInfo.path)
    if (!file.exists()) {
        try {
            context.contentResolver.delete(fileInfo.contentUri, null, null)
        } catch (e: Exception) {}
        return true
    }
    
    try {
        val length = file.length()
        if (length > 0) {
            val raf = RandomAccessFile(file, "rws")
            val bufferSize = 4096
            val buffer = ByteArray(bufferSize)
            
            // Pass 1: Zeros
            raf.seek(0)
            for (i in 0 until length step bufferSize.toLong()) {
                val writeLen = minOf(bufferSize.toLong(), length - i).toInt()
                raf.write(ByteArray(writeLen) { 0x00 })
            }
            
            // Pass 2: Ones
            raf.seek(0)
            for (i in 0 until length step bufferSize.toLong()) {
                val writeLen = minOf(bufferSize.toLong(), length - i).toInt()
                raf.write(ByteArray(writeLen) { 0xFF.toByte() })
            }
            
            // Pass 3: Random
            val secureRandom = SecureRandom()
            raf.seek(0)
            for (i in 0 until length step bufferSize.toLong()) {
                val writeLen = minOf(bufferSize.toLong(), length - i).toInt()
                secureRandom.nextBytes(buffer)
                raf.write(buffer, 0, writeLen)
            }
            
            // Force hardware sync barrier
            raf.fd.sync()
            
            // Truncate file to 0 bytes to destroy logical size metadata
            raf.setLength(0)
            raf.close()
        }
        
        // Obfuscate timestamps to Epoch 1970
        file.setLastModified(0L)
        
        // Obfuscate the filename before deletion
        val dummyFile = File(file.parent, UUID.randomUUID().toString())
        file.renameTo(dummyFile)
        dummyFile.delete()
        
        // Remove from MediaStore
        context.contentResolver.delete(fileInfo.contentUri, null, null)
        
        return true
    } catch (e: Exception) {
        e.printStackTrace()
        // Fallback standard delete
        val deleted = file.delete()
        try {
            context.contentResolver.delete(fileInfo.contentUri, null, null)
        } catch (e2: Exception) {}
        return deleted
    }
}

private fun openFile(context: Context, path: String) {
    try {
        val file = File(path)
        val extension = MimeTypeMap.getFileExtensionFromUrl(Uri.fromFile(file).toString())
        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase()) ?: "*/*"
        
        val intent = Intent(Intent.ACTION_VIEW).apply {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            setDataAndType(uri, mimeType)
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(Intent.createChooser(intent, "Open with"))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private fun getFilesForCategory(context: Context, categoryName: String): List<FileInfo> {
    val fileList = mutableListOf<FileInfo>()
    val baseUri = when (categoryName) {
        "Images" -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        "Videos" -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        "Audio" -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        "Documents" -> MediaStore.Files.getContentUri("external")
        else -> null
    } ?: return emptyList()

    val projection = arrayOf(
        MediaStore.MediaColumns._ID,
        MediaStore.MediaColumns.DISPLAY_NAME,
        MediaStore.MediaColumns.SIZE,
        MediaStore.MediaColumns.DATA,
        MediaStore.MediaColumns.DATE_MODIFIED
    )

    try {
        context.contentResolver.query(baseUri, projection, null, null, null)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val pathCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol) ?: "Unknown"
                val size = cursor.getLong(sizeCol)
                val path = cursor.getString(pathCol) ?: ""
                val date = cursor.getLong(dateCol) * 1000L // convert to ms
                val contentUri = ContentUris.withAppendedId(baseUri, id)
                
                // For documents, filter out media types
                if (categoryName == "Documents") {
                    val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(File(path).extension.lowercase()) ?: ""
                    if (!mimeType.startsWith("image/") && !mimeType.startsWith("video/") && !mimeType.startsWith("audio/")) {
                        fileList.add(FileInfo(id, name, size, path, date, contentUri))
                    }
                } else {
                    fileList.add(FileInfo(id, name, size, path, date, contentUri))
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return fileList
}

@Composable
fun CompactFileRow(file: FileInfo, onClick: () -> Unit, onDeleteClick: () -> Unit) {
    val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    val dateString = dateFormat.format(Date(file.dateModified))
    
    Row(
        verticalAlignment = Alignment.CenterVertically, 
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 24.dp)
    ) {
        Box(
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(GlassBorder),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.InsertDriveFile, contentDescription = null, tint = AuraCyan, modifier = Modifier.size(24.dp))
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(file.contentUri)
                    .crossfade(true)
                    .build(),
                imageLoader = ImageLoader.Builder(LocalContext.current)
                    .components { add(VideoFrameDecoder.Factory()) }
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(file.name, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatBytes(file.size), style = MaterialTheme.typography.bodySmall, color = AuraOrange)
                Text(" • $dateString", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        IconButton(onClick = onDeleteClick) {
            Icon(Icons.Default.DeleteForever, contentDescription = "Secure Delete", tint = AuraRose)
        }
    }
}

@Composable
fun GridFileItem(file: FileInfo, onClick: () -> Unit, onDeleteClick: () -> Unit) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(GlassBorder)
            .clickable { onClick() }
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.InsertDriveFile, contentDescription = null, tint = AuraCyan, modifier = Modifier.size(32.dp))
        }
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(file.contentUri)
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        
        // Overlays
        Box(
            modifier = Modifier.fillMaxSize().padding(4.dp)
        ) {
            // Delete button top right
            IconButton(
                onClick = onDeleteClick,
                modifier = Modifier.align(Alignment.TopEnd).size(28.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(Icons.Default.DeleteForever, contentDescription = "Secure Delete", tint = AuraRose, modifier = Modifier.size(16.dp))
            }
            
            // Size bottom left
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(formatBytes(file.size), style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = Color.White)
            }
        }
    }
}
