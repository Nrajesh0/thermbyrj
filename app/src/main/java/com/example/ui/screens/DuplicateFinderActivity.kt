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
import java.io.RandomAccessFile
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
        mutableStateOf(com.example.service.PermissionUtils.hasAllFilesPermission())
    }

    val manageStorageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        hasManageStorage = com.example.service.PermissionUtils.hasAllFilesPermission()
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
        statusText = "Scanning storage for duplicate files..."
        val computedGroups = withContext(Dispatchers.IO) {
            try {
                val root = Environment.getExternalStorageDirectory()
                val seenCanonicalPaths = HashSet<String>()
                val candidateFiles = mutableListOf<File>()

                fun walk(dir: File) {
                    val list = dir.listFiles() ?: return
                    for (f in list) {
                        if (f.isDirectory) {
                            if (!f.name.startsWith(".") && f.name != "Android") {
                                walk(f)
                            }
                        } else if (f.isFile && f.canRead()) {
                            val len = f.length()
                            if (len >= DuplicateHashVerifier.MIN_FILE_SIZE_BYTES) {
                                val canon = try {
                                    f.canonicalPath
                                } catch (_: Exception) {
                                    f.absolutePath
                                }
                                if (seenCanonicalPaths.add(canon)) {
                                    candidateFiles.add(f)
                                }
                            }
                        }
                    }
                }

                walk(root)
                DuplicateHashVerifier.findExactDuplicates(candidateFiles) { stageMsg ->
                    scope.launch(Dispatchers.Main) {
                        statusText = stageMsg
                    }
                }
            } catch (_: Throwable) {
                emptyList()
            }
        }
        duplicateGroups = computedGroups
        isScanning = false
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
                                    Text("SHA-256 verified · Tap to preview", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
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

/**
 * Three-stage exact duplicate engine:
 * 1. Exact byte-length grouping (>= 1 KB)
 * 2. Fast partial SHA-256 fingerprint (head 8 KB + mid 8 KB + tail 8 KB)
 * 3. Full-file streaming SHA-256 digest before ever flagging files as identical
 */
internal object DuplicateHashVerifier {
    const val MIN_FILE_SIZE_BYTES = 1024L
    private const val SAMPLE_WINDOW_BYTES = 8192
    private const val FULL_BUFFER_BYTES = 65536

    fun findExactDuplicates(
        files: List<File>,
        onStage: (String) -> Unit = {}
    ): List<DuplicateGroup> {
        val sizeMap = mutableMapOf<Long, MutableList<File>>()
        for (file in files) {
            if (!file.isFile) continue
            val len = file.length()
            if (len >= MIN_FILE_SIZE_BYTES) {
                sizeMap.getOrPut(len) { mutableListOf() }.add(file)
            }
        }

        val sizeMatches = sizeMap.filter { it.value.size > 1 }
        if (sizeMatches.isEmpty()) return emptyList()

        onStage("Fingerprinting ${sizeMatches.size} size matches...")
        val partialCandidates = mutableListOf<Pair<Long, List<File>>>()
        for ((size, group) in sizeMatches) {
            val partialMap = mutableMapOf<String, MutableList<File>>()
            for (file in group) {
                val fp = computePartialFingerprint(file, size)
                if (fp.isNotEmpty()) {
                    partialMap.getOrPut(fp) { mutableListOf() }.add(file)
                }
            }
            for ((_, matches) in partialMap) {
                if (matches.size > 1) {
                    partialCandidates.add(size to matches)
                }
            }
        }

        if (partialCandidates.isEmpty()) return emptyList()

        onStage("Verifying exact SHA-256 matches (${partialCandidates.size} groups)...")
        val results = mutableListOf<DuplicateGroup>()
        for ((size, candidates) in partialCandidates) {
            val fullHashMap = mutableMapOf<String, MutableList<File>>()
            for (file in candidates) {
                val fullHash = computeFullSha256(file)
                if (fullHash.isNotEmpty()) {
                    fullHashMap.getOrPut(fullHash) { mutableListOf() }.add(file)
                }
            }
            for ((hash, dupes) in fullHashMap) {
                if (dupes.size > 1) {
                    val sorted = dupes.sortedWith(compareBy<File>({ it.lastModified() }, { it.absolutePath }))
                    results.add(DuplicateGroup(hash = hash, size = size, files = sorted))
                }
            }
        }

        return results
            .distinctBy { it.hash }
            .sortedByDescending { it.size * (it.files.size - 1) }
    }

    fun computePartialFingerprint(file: File, expectedLength: Long = file.length()): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            digest.update(expectedLength.toString().toByteArray())
            val buf = ByteArray(SAMPLE_WINDOW_BYTES)
            RandomAccessFile(file, "r").use { raf ->
                val actualLen = raf.length()
                if (actualLen <= SAMPLE_WINDOW_BYTES * 3L) {
                    raf.seek(0L)
                    var read: Int
                    while (raf.read(buf).also { read = it } != -1) {
                        digest.update(buf, 0, read)
                    }
                } else {
                    // Head 8 KB
                    raf.seek(0L)
                    val headRead = raf.read(buf, 0, SAMPLE_WINDOW_BYTES)
                    if (headRead > 0) digest.update(buf, 0, headRead)

                    // Middle 8 KB
                    val midOffset = (actualLen / 2L) - (SAMPLE_WINDOW_BYTES / 2L)
                    raf.seek(midOffset.coerceAtLeast(0L))
                    val midRead = raf.read(buf, 0, SAMPLE_WINDOW_BYTES)
                    if (midRead > 0) digest.update(buf, 0, midRead)

                    // Tail 8 KB
                    val tailOffset = (actualLen - SAMPLE_WINDOW_BYTES).coerceAtLeast(0L)
                    raf.seek(tailOffset)
                    val tailRead = raf.read(buf, 0, SAMPLE_WINDOW_BYTES)
                    if (tailRead > 0) digest.update(buf, 0, tailRead)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            ""
        }
    }

    fun computeFullSha256(file: File): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(FULL_BUFFER_BYTES)
            FileInputStream(file).use { fis ->
                var read: Int
                while (fis.read(buffer).also { read = it } != -1) {
                    digest.update(buffer, 0, read)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            ""
        }
    }
}
