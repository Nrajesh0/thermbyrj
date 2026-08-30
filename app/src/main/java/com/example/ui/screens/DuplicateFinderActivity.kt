package com.example.ui.screens

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FilterNone
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.formatBytes
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import androidx.compose.foundation.clickable

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuplicateFinderScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var duplicates by remember { mutableStateOf<List<DuplicateGroup>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var refreshTrigger by remember { mutableStateOf(0) }

    LaunchedEffect(refreshTrigger) {
        coroutineScope.launch {
            withContext(Dispatchers.IO) {
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
                        val pathCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
                        val dateCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)

                        while (cursor.moveToNext()) {
                            val id = cursor.getLong(idCol)
                            val name = cursor.getString(nameCol) ?: "Unknown"
                            val size = cursor.getLong(sizeCol)
                            val path = cursor.getString(pathCol) ?: ""
                            val date = cursor.getLong(dateCol) * 1000L
                            val contentUri = ContentUris.withAppendedId(uri, id)
                            allFiles.add(FileInfo(id, name, size, path, date, contentUri))
                        }
                    }
                } catch (e: Exception) {}
                
                // Group by size and name (heuristic for duplicate without hashing)
                val groups = allFiles.groupBy { "${it.size}_${it.name}" }
                    .filter { it.value.size > 1 }
                    .map { DuplicateGroup(it.value.first().size, it.value.first().name, it.value) }
                    .sortedByDescending { it.size * (it.files.size - 1) } // sort by wasted space
                
                duplicates = groups
                isLoading = false
            }
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Duplicate Finder", color = TextPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = AuraCyan)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Scanning filesystem for duplicates...", color = TextSecondary)
                }
            }
        } else if (duplicates.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(duplicates) { group ->
                        com.example.GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(group.name, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
                                    Text(formatBytes(group.size), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                group.files.forEachIndexed { index, file ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(file.path.takeLast(30), style = MaterialTheme.typography.bodySmall, color = TextSecondary, maxLines = 1)
                                        if (index > 0) {
                                            IconButton(
                                                onClick = {
                                                    try {
                                                        context.contentResolver.delete(file.contentUri, null, null)
                                                        java.io.File(file.path).delete()
                                                        android.widget.Toast.makeText(context, "Deleted", android.widget.Toast.LENGTH_SHORT).show()
                                                        refreshTrigger++
                                                    } catch (e: Exception) {}
                                                },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(Icons.Default.DeleteSweep, contentDescription = "Delete", tint = AuraRose)
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
