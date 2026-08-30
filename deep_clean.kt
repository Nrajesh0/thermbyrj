package com.example.ui.screens

import android.content.Context
import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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

class DeepCleanActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Obsidian) {
                    DeepCleanScreen(onBack = { finish() })
                }
            }
        }
    }
}

data class JunkCategory(
    val name: String, 
    val description: String, 
    val icon: ImageVector,
    val files: MutableList<File> = mutableListOf(),
    var totalSize: Long = 0L,
    var isExpanded: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeepCleanScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var categories by remember { mutableStateOf<List<JunkCategory>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var totalJunkSize by remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                val apkCat = JunkCategory("APK Files", "Obsolete app installers", Icons.Default.Android)
                val logCat = JunkCategory("Log Files", "System and app log outputs", Icons.Default.Article)
                val thumbCat = JunkCategory("Thumbnail Caches", "Leftover image previews", Icons.Default.Image)
                val emptyDirCat = JunkCategory("Empty Folders", "Leftover empty directories", Icons.Default.FolderOpen)

                val corpseCat = JunkCategory("Corpse Data", "Leftovers from uninstalled apps", Icons.Default.DeleteSweep)
                val tempCat = JunkCategory("Temp & Backup", "Temporary, .bak, and .old fragments", Icons.Default.Restore)
                
                val installedPackages = try {
                    context.packageManager.getInstalledPackages(0).map { it.packageName }.toSet()
                } catch (e: Exception) {
                    emptySet<String>()
                }
                
                fun getFolderSize(folder: File): Long {
                    var length = 0L
                    val files = folder.listFiles()
                    if (files != null) {
                        for (file in files) {
                            if (file.isFile) {
                                length += file.length()
                            } else {
                                length += getFolderSize(file)
                            }
                        }
                    }
                    return length
                }
                
                try {
                    val root = Environment.getExternalStorageDirectory()
                    
                    // Simple recursive scan (up to a limit to prevent hanging)
                    fun scanDirectory(dir: File, depth: Int = 0) {
                        if (depth > 8) return // Limit depth
                        val files = dir.listFiles() ?: return
                        
                        // Check for Corpse Data
                        if (depth == 2 && dir.parentFile?.name == "Android" && (dir.name == "data" || dir.name == "obb" || dir.name == "media")) {
                            for (pkgFolder in files) {
                                if (pkgFolder.isDirectory && pkgFolder.name.contains(".") && !installedPackages.contains(pkgFolder.name)) {
                                    corpseCat.files.add(pkgFolder)
                                    corpseCat.totalSize += getFolderSize(pkgFolder)
                                } else {
                                    scanDirectory(pkgFolder, depth + 1)
                                }
                            }
                            return
                        }
                        
                        // Check for LOST.DIR
                        if (depth == 1 && dir.name == "LOST.DIR") {
                            for (f in files) {
                                tempCat.files.add(f)
                                tempCat.totalSize += if (f.isFile) f.length() else getFolderSize(f)
                            }
                            return
                        }
                        
                        if (files.isEmpty() && dir.absolutePath != root.absolutePath) {
                            emptyDirCat.files.add(dir)
                            return
                        }
                        
                        for (file in files) {
                            if (file.isDirectory) {
                                if (file.name == ".thumbnails" || file.name == ".cache") {
                                    file.walkTopDown().filter { it.isFile }.forEach {
                                        thumbCat.files.add(it)
                                        thumbCat.totalSize += it.length()
                                    }
                                } else {
                                    scanDirectory(file, depth + 1)
                                }
                            } else {
                                val lower = file.name.lowercase()
                                if (lower.endsWith(".apk")) {
                                    apkCat.files.add(file)
                                    apkCat.totalSize += file.length()
                                } else if (lower.endsWith(".log")) {
                                    logCat.files.add(file)
                                    logCat.totalSize += file.length()
                                } else if (lower.endsWith(".tmp") || lower.endsWith(".bak") || lower.endsWith(".old")) {
                                    tempCat.files.add(file)
                                    tempCat.totalSize += file.length()
                                }
                            }
                        }
                    }
                    
                    scanDirectory(root)
                    
                } catch (e: Exception) {}
                
                categories = listOf(apkCat, thumbCat, logCat, corpseCat, tempCat, emptyDirCat).filter { it.files.isNotEmpty() }
                totalJunkSize = categories.sumOf { it.totalSize }
                isLoading = false
            }
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Deep Clean", color = TextPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        floatingActionButton = {
            if (!isLoading && categories.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = {
                        coroutineScope.launch {
                            withContext(Dispatchers.IO) {
                                categories.forEach { cat ->
                                    cat.files.toList().forEach { file ->
                                        try {
                                            if (file.isDirectory) file.deleteRecursively() else file.delete()
                                        } catch (e: Exception) {}
                                    }
                                    cat.files.clear()
                                    cat.totalSize = 0
                                }
                                categories = emptyList()
                                totalJunkSize = 0L
                            }
                        }
                    },
                    containerColor = AuraOrange,
                    icon = { Icon(Icons.Default.CleaningServices, contentDescription = null, tint = Color.White) },
                    text = { Text("Clean All Junk", color = Color.White, fontWeight = FontWeight.Bold) }
                )
            }
        }
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = AuraOrange)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Scanning for junk files...", color = TextSecondary)
                }
            }
        } else if (categories.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Your system is clean!", color = AuraGreen)
            }
        } else {
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
                        Text("TOTAL JUNK FOUND", style = MaterialTheme.typography.labelSmall, color = AuraOrange, letterSpacing = 2.sp)
                        Text(formatBytes(totalJunkSize), style = MaterialTheme.typography.headlineLarge, color = AuraOrange, fontWeight = FontWeight.Bold)
                    }
                }
                
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 100.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(categories) { cat ->
                        var expanded by remember { mutableStateOf(false) }
                        com.example.GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }, 
                                    horizontalArrangement = Arrangement.SpaceBetween, 
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(cat.icon, contentDescription = null, tint = AuraOrange, modifier = Modifier.size(24.dp))
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Column {
                                            Text(cat.name, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
                                            Text("${cat.files.size} items", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                        }
                                    }
                                    Text(if (cat.totalSize > 0) formatBytes(cat.totalSize) else "", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                                }
                                
                                if (expanded) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    cat.files.take(20).forEach { file ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(file.name, style = MaterialTheme.typography.bodySmall, color = TextSecondary, maxLines = 1, modifier = Modifier.weight(1f))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(formatBytes(file.length()), style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                                        }
                                    }
                                    if (cat.files.size > 20) {
                                        Text("... and ${cat.files.size - 20} more", style = MaterialTheme.typography.labelSmall, color = TextTertiary, modifier = Modifier.padding(top = 8.dp))
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

