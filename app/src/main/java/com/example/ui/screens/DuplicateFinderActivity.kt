package com.example.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.example.ui.theme.AuraRose
import com.example.ui.theme.GlassPanel
import com.example.ui.theme.Obsidian
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

data class DuplicateGroup(
    val hash: String,
    val size: Long,
    val files: List<File>
)

class DuplicateFinderActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Obsidian) {
                    DuplicateFinderScreen(onBack = { finish() })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuplicateFinderScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isScanning by remember { mutableStateOf(true) }
    var duplicateGroups by remember { mutableStateOf<List<DuplicateGroup>>(emptyList()) }
    var statusText by remember { mutableStateOf("Scanning storage...") }
    var scanTrigger by remember { mutableIntStateOf(0) }
    var showSmartCleanConfirm by remember { mutableStateOf(false) }
    var hasManageStorage by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager() else true
        )
    }

    val manageStorageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        hasManageStorage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager() else true
        scanTrigger++
    }

    val recoverableBytes = remember(duplicateGroups) {
        duplicateGroups.sumOf { group ->
            group.size * (group.files.size - 1).coerceAtLeast(0)
        }
    }

    val duplicateCount = remember(duplicateGroups) {
        duplicateGroups.sumOf { (it.files.size - 1).coerceAtLeast(0) }
    }

    fun deleteFileComplete(file: File): Boolean {
        var deleted = false
        try {
            val uri = MediaStore.Files.getContentUri("external")
            val rows = context.contentResolver.delete(
                uri,
                "${MediaStore.MediaColumns.DATA} = ?",
                arrayOf(file.absolutePath)
            )
            if (rows > 0) deleted = true
        } catch (_: Exception) {
        }
        if (file.exists()) {
            if (file.delete()) deleted = true
        }
        return deleted || !file.exists()
    }

    fun openFilePreview(file: File) {
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
            Toast.makeText(context, "No app found to preview this file", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(scanTrigger) {
        isScanning = true
        withContext(Dispatchers.IO) {
            val root = Environment.getExternalStorageDirectory()
            val sizeMap = mutableMapOf<Long, MutableList<File>>()

            fun walk(dir: File) {
                val list = dir.listFiles() ?: return
                for (f in list) {
                    if (f.isDirectory) {
                        if (!f.name.startsWith(".") && f.name != "Android") {
                            walk(f)
                        }
                    } else {
                        val len = f.length()
                        if (len > 10240) { // Only scan files > 10KB
                            sizeMap.getOrPut(len) { mutableListOf() }.add(f)
                        }
                    }
                }
            }

            walk(root)
            val potentialGroups = sizeMap.filter { it.value.size > 1 }
            statusText = "Comparing ${potentialGroups.size} size matches..."

            val results = mutableListOf<DuplicateGroup>()
            for ((size, files) in potentialGroups) {
                val hashMap = mutableMapOf<String, MutableList<File>>()
                for (f in files) {
                    val h = getFastMd5(f)
                    if (h.isNotEmpty()) {
                        hashMap.getOrPut(h) { mutableListOf() }.add(f)
                    }
                }
                for ((hash, dupes) in hashMap) {
                    if (dupes.size > 1) {
                        // Sort oldest first so index 0 is the "original"
                        val sorted = dupes.sortedBy { it.lastModified() }
                        results.add(DuplicateGroup(hash, size, sorted))
                    }
                }
            }

            duplicateGroups = results.sortedByDescending { it.size * (it.files.size - 1) }
            isScanning = false
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Duplicate Finder", color = TextPrimary, fontWeight = FontWeight.Bold) },
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
            if (!isScanning && duplicateGroups.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(GlassPanel)
                        .padding(horizontal = 24.dp, vertical = 14.dp)
                        .navigationBarsPadding()
                ) {
                    Button(
                        onClick = { showSmartCleanConfirm = true },
                        colors = ButtonDefaults.buttonColors(containerColor = AuraCyan),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.CleaningServices, contentDescription = null, tint = Color.Black)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Smart Clean $duplicateCount Copies (${formatBytes(recoverableBytes)})",
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
                    CircularProgressIndicator(color = AuraCyan)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(statusText, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 24.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (!hasManageStorage && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    item {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, tint = AuraOrange)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Full Storage Scan", color = TextPrimary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                    Text("Grant All Files Access to detect non-media duplicates.", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
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
                                    colors = ButtonDefaults.buttonColors(containerColor = AuraOrange)
                                ) {
                                    Text("Grant", color = Color.Black, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                if (duplicateGroups.isEmpty()) {
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
                                    contentDescription = "No Duplicates",
                                    tint = AuraGreen,
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("No Duplicate Files Found!", color = TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("Your storage is free of redundant copies.", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
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
                                    Text("RECOVERABLE SPACE", color = TextTertiary, style = MaterialTheme.typography.labelSmall, letterSpacing = 1.2.sp)
                                    Text(formatBytes(recoverableBytes), color = AuraCyan, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("${duplicateGroups.size} Groups", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                                    Text("Tap any file to preview", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }

                    items(duplicateGroups, key = { it.hash }) { group ->
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "${group.files.size} Identical Copies",
                                        color = AuraCyan,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        "${formatBytes(group.size)} each",
                                        color = TextSecondary,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                group.files.forEachIndexed { idx, file ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { openFilePreview(file) }
                                            .padding(vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    file.name,
                                                    color = TextPrimary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    modifier = Modifier.weight(1f, fill = false)
                                                )
                                                if (idx == 0) {
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        "ORIGINAL",
                                                        color = AuraGreen,
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                            Text(
                                                file.parent ?: "",
                                                color = TextTertiary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                        IconButton(onClick = {
                                            scope.launch(Dispatchers.IO) {
                                                val deleted = deleteFileComplete(file)
                                                if (deleted) StorageScanCache.invalidate()
                                                withContext(Dispatchers.Main) {
                                                    if (deleted) {
                                                        val newFiles = group.files.filter { it != file }
                                                        duplicateGroups = if (newFiles.size <= 1) {
                                                            duplicateGroups.filter { it != group }
                                                        } else {
                                                            duplicateGroups.map { if (it == group) group.copy(files = newFiles) else it }
                                                        }
                                                    } else {
                                                        Toast.makeText(context, "Could not delete file", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            }
                                        }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete Duplicate", tint = AuraRose)
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

    if (showSmartCleanConfirm) {
        AlertDialog(
            onDismissRequest = { showSmartCleanConfirm = false },
            containerColor = GlassPanel,
            title = { Text("Smart Clean Duplicates?", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Keeps the oldest original file in each group and deletes $duplicateCount redundant copies (${formatBytes(recoverableBytes)}).",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSmartCleanConfirm = false
                        isScanning = true
                        statusText = "Removing duplicate copies..."
                        scope.launch(Dispatchers.IO) {
                            var freed = 0L
                            var removedCount = 0
                            val remainingGroups = mutableListOf<DuplicateGroup>()
                            for (group in duplicateGroups) {
                                val keep = group.files.first()
                                val leftovers = mutableListOf(keep)
                                for (dupe in group.files.drop(1)) {
                                    if (deleteFileComplete(dupe)) {
                                        freed += group.size
                                        removedCount++
                                    } else {
                                        leftovers.add(dupe)
                                    }
                                }
                                if (leftovers.size > 1) {
                                    remainingGroups.add(group.copy(files = leftovers))
                                }
                            }
                            if (removedCount > 0) StorageScanCache.invalidate()
                            withContext(Dispatchers.Main) {
                                duplicateGroups = remainingGroups
                                isScanning = false
                                Toast.makeText(
                                    context,
                                    "Cleaned $removedCount duplicates (${formatBytes(freed)} freed)",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AuraRose)
                ) {
                    Text("Clean All", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSmartCleanConfirm = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}

private fun getFastMd5(file: File): String {
    return try {
        val digest = MessageDigest.getInstance("MD5")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(8192)
            val read = fis.read(buffer)
            if (read > 0) digest.update(buffer, 0, read)
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    } catch (_: Exception) {
        ""
    }
}
