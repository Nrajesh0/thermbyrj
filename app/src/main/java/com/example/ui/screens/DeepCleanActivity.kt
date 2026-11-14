package com.example.ui.screens

import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.GlassCard
import com.example.formatBytes
import com.example.ui.theme.AppTheme
import com.example.ui.theme.AuraGreen
import com.example.ui.theme.AuraOrange
import com.example.ui.theme.Obsidian
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
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

data class JunkEntry(
    val file: File,
    val sizeBytes: Long
)

data class JunkCategory(
    val name: String,
    val description: String,
    val icon: ImageVector,
    val entries: MutableList<JunkEntry> = mutableListOf(),
    var totalSize: Long = 0L
)

private fun calculateEntrySize(file: File): Long {
    if (file.isFile) return file.length()
    var length = 0L
    file.listFiles()?.forEach { child ->
        length += calculateEntrySize(child)
    }
    return length
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeepCleanScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var categories by remember { mutableStateOf<List<JunkCategory>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var totalJunkSize by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        val result = withContext(Dispatchers.IO) {
            val logCat = JunkCategory("Log Files", "System and app log outputs", Icons.Default.Article)
            val thumbCat = JunkCategory("Thumbnail Caches", "Leftover image previews", Icons.Default.Image)
            val corpseCat = JunkCategory("Corpse Data", "Leftovers from uninstalled apps", Icons.Default.DeleteSweep)
            val tempCat = JunkCategory("Temp & Backup", "Temporary and fsck fragments", Icons.Default.Restore)

            val installedPackages = try {
                context.packageManager.getInstalledPackages(0).map { it.packageName }.toSet()
            } catch (_: Exception) {
                emptySet()
            }

            try {
                val root = Environment.getExternalStorageDirectory()

                fun scanDirectory(dir: File, depth: Int = 0) {
                    if (depth > 8) return
                    val files = dir.listFiles() ?: return

                    if (depth == 2 && dir.parentFile?.name == "Android" && (dir.name == "data" || dir.name == "obb" || dir.name == "media")) {
                        for (pkgFolder in files) {
                            if (pkgFolder.isDirectory && pkgFolder.name.contains(".") && !installedPackages.contains(pkgFolder.name)) {
                                val size = calculateEntrySize(pkgFolder)
                                corpseCat.entries.add(JunkEntry(pkgFolder, size))
                                corpseCat.totalSize += size
                            } else {
                                scanDirectory(pkgFolder, depth + 1)
                            }
                        }
                        return
                    }

                    if (depth == 1 && dir.name == "LOST.DIR") {
                        for (f in files) {
                            val size = calculateEntrySize(f)
                            tempCat.entries.add(JunkEntry(f, size))
                            tempCat.totalSize += size
                        }
                        return
                    }

                    val protectedDirs = setOf("DCIM", "Pictures", "Music", "Movies", "Documents", "Download", "Podcasts", "Audiobooks")
                    if (depth == 1 && protectedDirs.contains(dir.name)) {
                        val thumbDir = File(dir, ".thumbnails")
                        if (thumbDir.exists() && thumbDir.isDirectory) {
                            thumbDir.walkTopDown().filter { it.isFile }.forEach {
                                val len = it.length()
                                thumbCat.entries.add(JunkEntry(it, len))
                                thumbCat.totalSize += len
                            }
                        }
                        return
                    }

                    for (file in files) {
                        if (file.isDirectory) {
                            if (file.name == ".thumbnails" || file.name == ".cache") {
                                file.walkTopDown().filter { it.isFile }.forEach {
                                    val len = it.length()
                                    thumbCat.entries.add(JunkEntry(it, len))
                                    thumbCat.totalSize += len
                                }
                            } else {
                                scanDirectory(file, depth + 1)
                            }
                        } else {
                            val lower = file.name.lowercase()
                            if (lower.endsWith(".log")) {
                                val len = file.length()
                                logCat.entries.add(JunkEntry(file, len))
                                logCat.totalSize += len
                            } else if (lower.endsWith(".tmp")) {
                                val len = file.length()
                                tempCat.entries.add(JunkEntry(file, len))
                                tempCat.totalSize += len
                            }
                        }
                    }
                }

                scanDirectory(root)
            } catch (_: Exception) {
            }

            listOf(thumbCat, logCat, corpseCat, tempCat).filter { it.entries.isNotEmpty() }
        }
        categories = result
        totalJunkSize = result.sumOf { it.totalSize }
        isLoading = false
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Deep Clean", color = TextPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        floatingActionButton = {
            if (!isLoading && categories.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    modifier = Modifier.navigationBarsPadding(),
                    onClick = {
                        coroutineScope.launch {
                            val (deletedCount, freedBytes, remainingCategories) = withContext(Dispatchers.IO) {
                                var count = 0
                                var freed = 0L
                                val remaining = mutableListOf<JunkCategory>()
                                val extRoot = Environment.getExternalStorageDirectory().absolutePath

                                categories.forEach { cat ->
                                    val failedEntries = mutableListOf<JunkEntry>()
                                    var remainingSize = 0L

                                    cat.entries.forEach { entry ->
                                        val file = entry.file
                                        val path = file.absolutePath
                                        val isSafeZone = path.contains("/.thumbnails") ||
                                            path.contains("/.cache") ||
                                            path.contains("/Android/data/") ||
                                            path.contains("/Android/obb/") ||
                                            path.contains("/Android/media/") ||
                                            path.contains("/LOST.DIR") ||
                                            path.endsWith(".log", ignoreCase = true) ||
                                            path.endsWith(".tmp", ignoreCase = true)

                                        val deleted = if (isSafeZone && path.startsWith(extRoot)) {
                                            try {
                                                if (file.isDirectory) file.deleteRecursively() else file.delete()
                                            } catch (_: Exception) {
                                                false
                                            }
                                        } else {
                                            false
                                        }

                                        if (deleted) {
                                            count++
                                            freed += entry.sizeBytes
                                        } else {
                                            failedEntries.add(entry)
                                            remainingSize += entry.sizeBytes
                                        }
                                    }

                                    if (failedEntries.isNotEmpty()) {
                                        remaining.add(cat.copy(entries = failedEntries, totalSize = remainingSize))
                                    }
                                }
                                Triple(count, freed, remaining)
                            }
                            categories = remainingCategories
                            totalJunkSize = remainingCategories.sumOf { it.totalSize }
                            val msg = if (deletedCount > 0) {
                                "Cleaned $deletedCount items (${formatBytes(freedBytes)})"
                            } else {
                                "Permission required: grant All Files Access to clean protected folders"
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
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
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = AuraOrange)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Scanning for junk files...", color = TextSecondary)
                }
            }
        } else if (categories.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
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
                    modifier = Modifier.fillMaxSize().navigationBarsPadding(),
                    contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 100.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(categories) { cat ->
                        var expanded by remember { mutableStateOf(false) }
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Icon(cat.icon, contentDescription = null, tint = AuraOrange, modifier = Modifier.size(24.dp))
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Column {
                                            Text(cat.name, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
                                            Text("${cat.entries.size} items", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                        }
                                    }
                                    Text(if (cat.totalSize > 0) formatBytes(cat.totalSize) else "0 B", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                                }

                                if (expanded) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    cat.entries.take(20).forEach { entry ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(entry.file.name, style = MaterialTheme.typography.bodySmall, color = TextSecondary, maxLines = 1, modifier = Modifier.weight(1f))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(formatBytes(entry.sizeBytes), style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                                        }
                                    }
                                    if (cat.entries.size > 20) {
                                        Text("... and ${cat.entries.size - 20} more", style = MaterialTheme.typography.labelSmall, color = TextTertiary, modifier = Modifier.padding(top = 8.dp))
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
