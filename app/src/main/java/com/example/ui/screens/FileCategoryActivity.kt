package com.example.ui.screens

import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import com.example.formatBytes
import com.example.ui.theme.AppTheme
import com.example.ui.theme.AuraCyan
import com.example.ui.theme.AuraOrange
import com.example.ui.theme.AuraRose
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassPanel
import com.example.ui.theme.Obsidian
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

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

data class FileInfo(
    val id: Long,
    val name: String,
    val size: Long,
    val path: String,
    val dateModified: Long,
    val contentUri: Uri
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileCategoryScreen(categoryName: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var files by remember { mutableStateOf<List<FileInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var fileToDelete by remember { mutableStateOf<FileInfo?>(null) }

    var viewMode by remember { mutableStateOf("List") }
    var filterOption by remember { mutableStateOf("All") }
    var sortOption by remember { mutableStateOf("Date") }

    val sharedImageLoader = remember(context) {
        ImageLoader.Builder(context)
            .components { add(VideoFrameDecoder.Factory()) }
            .build()
    }

    val loadFiles: () -> Unit = {
        coroutineScope.launch {
            isLoading = true
            files = withContext(Dispatchers.IO) {
                getFilesForCategory(context, categoryName)
            }
            isLoading = false
        }
    }

    val deleteIntentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            Toast.makeText(context, "File deleted", Toast.LENGTH_SHORT).show()
            loadFiles()
        } else {
            Toast.makeText(context, "Deletion cancelled", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(categoryName) { loadFiles() }

    val filteredFiles = remember(files, filterOption, sortOption) {
        val filtered = when (filterOption) {
            "> 50MB" -> files.filter { it.size > 50L * 1024L * 1024L }
            "Recent" -> {
                val weekAgo = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000L)
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
        val target = fileToDelete!!
        val hasAllFilesAccess = Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()
        AlertDialog(
            onDismissRequest = { fileToDelete = null },
            title = { Text(if (hasAllFilesAccess) "Secure Delete" else "Delete File", color = TextPrimary) },
            text = {
                Text(
                    if (hasAllFilesAccess) {
                        "Are you sure you want to securely wipe '${target.name}'? This overwrites the file before removal and cannot be undone."
                    } else {
                        "Delete '${target.name}' from device storage?"
                    },
                    color = TextSecondary
                )
            },
            containerColor = GlassPanel,
            confirmButton = {
                TextButton(
                    onClick = {
                        fileToDelete = null
                        if (hasAllFilesAccess) {
                            coroutineScope.launch {
                                isLoading = true
                                val success = withContext(Dispatchers.IO) {
                                    secureDeleteFile(context, target)
                                }
                                Toast.makeText(
                                    context,
                                    if (success) "File securely wiped" else "Secure wipe failed",
                                    Toast.LENGTH_SHORT
                                ).show()
                                loadFiles()
                            }
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            try {
                                val pi = MediaStore.createDeleteRequest(context.contentResolver, listOf(target.contentUri))
                                deleteIntentLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
                            } catch (e: Exception) {
                                Toast.makeText(context, "Unable to delete file: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            coroutineScope.launch {
                                try {
                                    val rows = withContext(Dispatchers.IO) {
                                        context.contentResolver.delete(target.contentUri, null, null)
                                    }
                                    if (rows > 0) {
                                        Toast.makeText(context, "File deleted", Toast.LENGTH_SHORT).show()
                                        loadFiles()
                                    }
                                } catch (e: Exception) {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is RecoverableSecurityException) {
                                        val request = IntentSenderRequest.Builder(e.userAction.actionIntent.intentSender).build()
                                        deleteIntentLauncher.launch(request)
                                    } else {
                                        Toast.makeText(context, "Delete failed", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    }
                ) {
                    Text(if (hasAllFilesAccess) "Wipe File" else "Delete", color = AuraRose)
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
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = { viewMode = if (viewMode == "List") "Grid" else "List" }) {
                        Icon(
                            if (viewMode == "List") Icons.Default.GridView else Icons.AutoMirrored.Filled.ViewList,
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
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AuraCyan)
            }
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
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
                        modifier = Modifier.fillMaxSize().navigationBarsPadding(),
                        contentPadding = PaddingValues(bottom = 32.dp)
                    ) {
                        items(filteredFiles, key = { it.id }) { file ->
                            CompactFileRow(
                                file = file,
                                imageLoader = sharedImageLoader,
                                onClick = { openFile(context, file) },
                                onDeleteClick = { fileToDelete = file }
                            )
                            HorizontalDivider(color = Color.White.copy(alpha = 0.05f), modifier = Modifier.padding(horizontal = 24.dp))
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 8.dp),
                        contentPadding = PaddingValues(bottom = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(filteredFiles, key = { it.id }) { file ->
                            GridFileItem(
                                file = file,
                                imageLoader = sharedImageLoader,
                                onClick = { openFile(context, file) },
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
        return try {
            context.contentResolver.delete(fileInfo.contentUri, null, null) > 0
        } catch (_: Exception) {
            false
        }
    }

    return try {
        val length = file.length()
        if (length > 0) {
            val raf = RandomAccessFile(file, "rws")
            val bufferSize = 4096
            val zeros = ByteArray(bufferSize) { 0x00 }
            val ones = ByteArray(bufferSize) { 0xFF.toByte() }
            val randomBuf = ByteArray(bufferSize)
            val secureRandom = SecureRandom()

            raf.seek(0)
            var i = 0L
            while (i < length) {
                val writeLen = minOf(bufferSize.toLong(), length - i).toInt()
                raf.write(zeros, 0, writeLen)
                i += writeLen
            }

            raf.seek(0)
            i = 0L
            while (i < length) {
                val writeLen = minOf(bufferSize.toLong(), length - i).toInt()
                raf.write(ones, 0, writeLen)
                i += writeLen
            }

            raf.seek(0)
            i = 0L
            while (i < length) {
                val writeLen = minOf(bufferSize.toLong(), length - i).toInt()
                secureRandom.nextBytes(randomBuf)
                raf.write(randomBuf, 0, writeLen)
                i += writeLen
            }

            raf.fd.sync()
            raf.setLength(0)
            raf.close()
        }

        file.setLastModified(0L)
        val dummyFile = File(file.parent, UUID.randomUUID().toString())
        if (file.renameTo(dummyFile)) {
            dummyFile.delete()
        } else {
            file.delete()
        }

        try {
            context.contentResolver.delete(fileInfo.contentUri, null, null)
        } catch (_: Exception) {
        }
        true
    } catch (_: Exception) {
        val deleted = file.delete()
        try {
            context.contentResolver.delete(fileInfo.contentUri, null, null)
        } catch (_: Exception) {
        }
        deleted
    }
}

private fun openFile(context: Context, fileInfo: FileInfo) {
    try {
        val ext = File(fileInfo.name).extension.lowercase()
        val mimeType = context.contentResolver.getType(fileInfo.contentUri)
            ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            ?: "*/*"

        val uri = try {
            val file = File(fileInfo.path)
            if (file.exists() && file.canRead()) {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            } else {
                fileInfo.contentUri
            }
        } catch (_: Exception) {
            fileInfo.contentUri
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(Intent.createChooser(intent, "Open with"))
    } catch (_: Exception) {
        Toast.makeText(context, "No app available to open this file", Toast.LENGTH_SHORT).show()
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
            val pathCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)

            val docExtensions = setOf("pdf", "txt", "doc", "docx", "ppt", "pptx", "xls", "xlsx", "csv", "rtf")
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol) ?: "Unknown"
                val size = cursor.getLong(sizeCol)
                val path = if (pathCol >= 0) cursor.getString(pathCol) ?: "" else ""
                val date = cursor.getLong(dateCol) * 1000L
                val contentUri = ContentUris.withAppendedId(baseUri, id)

                if (categoryName == "Documents") {
                    val candidate = path.ifBlank { name }
                    val ext = File(candidate).extension.lowercase()
                    if (ext in docExtensions) {
                        fileList.add(FileInfo(id, name, size, path, date, contentUri))
                    }
                } else {
                    fileList.add(FileInfo(id, name, size, path, date, contentUri))
                }
            }
        }
    } catch (_: Exception) {
    }
    return fileList
}

@Composable
fun CompactFileRow(
    file: FileInfo,
    imageLoader: ImageLoader,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val context = LocalContext.current
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }
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
                model = ImageRequest.Builder(context)
                    .data(file.contentUri)
                    .crossfade(true)
                    .build(),
                imageLoader = imageLoader,
                contentDescription = file.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(file.name, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.padding(top = 2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatBytes(file.size), style = MaterialTheme.typography.bodySmall, color = AuraOrange)
                Text(" • $dateString", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        IconButton(onClick = onDeleteClick) {
            Icon(Icons.Default.DeleteForever, contentDescription = "Delete ${file.name}", tint = AuraRose)
        }
    }
}

@Composable
fun GridFileItem(
    file: FileInfo,
    imageLoader: ImageLoader,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val context = LocalContext.current
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
            model = ImageRequest.Builder(context)
                .data(file.contentUri)
                .crossfade(true)
                .build(),
            imageLoader = imageLoader,
            contentDescription = file.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        Box(modifier = Modifier.fillMaxSize().padding(4.dp)) {
            IconButton(
                onClick = onDeleteClick,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(36.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
            ) {
                Icon(Icons.Default.DeleteForever, contentDescription = "Delete ${file.name}", tint = AuraRose, modifier = Modifier.size(18.dp))
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(formatBytes(file.size), style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = Color.White)
            }
        }
    }
}
