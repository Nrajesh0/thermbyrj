package com.example.ui.screens

import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.GlassCard
import com.example.formatBytes
import com.example.ui.theme.AppTheme
import com.example.ui.theme.AuraCyan
import com.example.ui.theme.AuraGreen
import com.example.ui.theme.AuraOrange
import com.example.ui.theme.AuraRose
import com.example.ui.theme.Obsidian
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest

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

data class DuplicateGroup(val size: Long, val name: String, val files: List<FileInfo>)

private fun computeQuickSignature(fileInfo: FileInfo): String {
    val file = File(fileInfo.path)
    if (!file.exists() || !file.canRead()) {
        return "${fileInfo.size}_${fileInfo.name}"
    }
    return try {
        val digest = MessageDigest.getInstance("SHA-256")
        val len = file.length()
        val buf = ByteArray(4096)
        RandomAccessFile(file, "r").use { raf ->
            val headRead = raf.read(buf)
            if (headRead > 0) digest.update(buf, 0, headRead)
            if (len > 8192) {
                raf.seek(len / 2)
                val midRead = raf.read(buf)
                if (midRead > 0) digest.update(buf, 0, midRead)
                raf.seek(len - 4096)
                val tailRead = raf.read(buf)
                if (tailRead > 0) digest.update(buf, 0, tailRead)
            }
        }
        val hashHex = digest.digest().joinToString("") { "%02x".format(it) }
        "${fileInfo.size}_$hashHex"
    } catch (_: Exception) {
        "${fileInfo.size}_${fileInfo.name}"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuplicateFinderScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var duplicates by remember { mutableStateOf<List<DuplicateGroup>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var refreshTrigger by remember { mutableIntStateOf(0) }

    val deleteIntentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            Toast.makeText(context, "Duplicate deleted", Toast.LENGTH_SHORT).show()
            refreshTrigger++
        }
    }

    LaunchedEffect(refreshTrigger) {
        isLoading = true
        duplicates = withContext(Dispatchers.IO) {
            val allFiles = mutableListOf<FileInfo>()
            val uri = MediaStore.Files.getContentUri("external")
            val projection = arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.DATA,
                MediaStore.MediaColumns.DATE_MODIFIED
            )

            try {
                context.contentResolver.query(uri, projection, "${MediaStore.MediaColumns.SIZE} > ?", arrayOf("100000"), null)?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                    val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                    val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                    val pathCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                    val dateCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)

                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idCol)
                        val name = cursor.getString(nameCol) ?: "Unknown"
                        val size = cursor.getLong(sizeCol)
                        val path = if (pathCol >= 0) cursor.getString(pathCol) ?: "" else ""
                        val date = cursor.getLong(dateCol) * 1000L
                        val contentUri = ContentUris.withAppendedId(uri, id)
                        allFiles.add(FileInfo(id, name, size, path, date, contentUri))
                    }
                }
            } catch (_: Exception) {
            }

            // First narrow by size, then verify content signature
            allFiles.groupBy { it.size }
                .values
                .filter { it.size > 1 }
                .flatMap { sameSize -> sameSize.groupBy { computeQuickSignature(it) }.values }
                .filter { it.size > 1 }
                .map { groupFiles ->
                    val sorted = groupFiles.sortedBy { it.dateModified }
                    DuplicateGroup(sorted.first().size, sorted.first().name, sorted)
                }
                .sortedByDescending { it.size * (it.files.size - 1) }
        }
        isLoading = false
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = AuraCyan)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Scanning filesystem for duplicates...", color = TextSecondary)
                }
            }
        } else if (duplicates.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No duplicates found! Your drive is clean.", color = AuraGreen)
            }
        } else {
            val totalWasted = duplicates.sumOf { it.size * (it.files.size - 1) }

            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(AuraOrange.copy(alpha = 0.1f))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("WASTED SPACE", style = MaterialTheme.typography.labelSmall, color = AuraOrange, letterSpacing = 2.sp)
                        Text(formatBytes(totalWasted), style = MaterialTheme.typography.headlineLarge, color = AuraOrange, fontWeight = FontWeight.Bold)
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize().navigationBarsPadding(),
                    contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(duplicates) { group ->
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        group.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                                    )
                                    Text(formatBytes(group.size), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                group.files.forEachIndexed { index, file ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            file.path.ifBlank { file.name }.takeLast(32),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                                        )
                                        if (index > 0) {
                                            IconButton(
                                                onClick = {
                                                    coroutineScope.launch {
                                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                                            try {
                                                                val deletedDirect = withContext(Dispatchers.IO) {
                                                                    val f = File(file.path)
                                                                    val fileDeleted = f.exists() && f.delete()
                                                                    val rows = try {
                                                                        context.contentResolver.delete(file.contentUri, null, null)
                                                                    } catch (_: Exception) {
                                                                        0
                                                                    }
                                                                    fileDeleted || rows > 0
                                                                }
                                                                if (deletedDirect) {
                                                                    Toast.makeText(context, "Deleted", Toast.LENGTH_SHORT).show()
                                                                    refreshTrigger++
                                                                } else {
                                                                    val pi = MediaStore.createDeleteRequest(context.contentResolver, listOf(file.contentUri))
                                                                    deleteIntentLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
                                                                }
                                                            } catch (e: Exception) {
                                                                Toast.makeText(context, "Unable to delete file", Toast.LENGTH_SHORT).show()
                                                            }
                                                        } else {
                                                            try {
                                                                val deleted = withContext(Dispatchers.IO) {
                                                                    val rows = context.contentResolver.delete(file.contentUri, null, null)
                                                                    val fDeleted = File(file.path).delete()
                                                                    rows > 0 || fDeleted
                                                                }
                                                                if (deleted) {
                                                                    Toast.makeText(context, "Deleted", Toast.LENGTH_SHORT).show()
                                                                    refreshTrigger++
                                                                }
                                                            } catch (e: Exception) {
                                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is RecoverableSecurityException) {
                                                                    val req = IntentSenderRequest.Builder(e.userAction.actionIntent.intentSender).build()
                                                                    deleteIntentLauncher.launch(req)
                                                                } else {
                                                                    Toast.makeText(context, "Delete failed", Toast.LENGTH_SHORT).show()
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            ) {
                                                Icon(Icons.Default.DeleteSweep, contentDescription = "Delete duplicate ${file.name}", tint = AuraRose)
                                            }
                                        } else {
                                            Text("ORIGINAL", style = MaterialTheme.typography.labelSmall, color = AuraGreen)
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
