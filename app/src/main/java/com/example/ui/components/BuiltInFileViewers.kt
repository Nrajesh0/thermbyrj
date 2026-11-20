package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.example.InfoRow
import com.example.formatBytes
import com.example.service.FileManagerOps
import com.example.service.toSafeComposeBitmap
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
import java.util.zip.ZipInputStream

enum class InAppViewerType {
    IMAGE, TEXT, ZIP, APK
}

data class ActiveFilePreview(
    val file: File,
    val contentUri: Uri? = null,
    val type: InAppViewerType,
    val inMemoryBitmap: Bitmap? = null,
    val inMemoryText: String? = null,
    val displaySizeOverride: Long? = null
)

fun resolveInAppViewerType(file: File): InAppViewerType? {
    val ext = file.extension.lowercase()
    return when {
        ext in FileManagerOps.IMAGE_EXTENSIONS -> InAppViewerType.IMAGE
        ext in FileManagerOps.TEXT_VIEWABLE_EXTENSIONS -> InAppViewerType.TEXT
        ext == "zip" -> InAppViewerType.ZIP
        ext == "apk" -> InAppViewerType.APK
        else -> null
    }
}

fun openExternalWithProvider(context: Context, file: File?, contentUri: Uri? = null, mimeOverride: String? = null) {
    try {
        val uri = if (file != null && file.exists()) {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } else {
            contentUri ?: return
        }
        val ext = file?.extension?.lowercase() ?: ""
        val mime = mimeOverride ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Open with"))
    } catch (_: Exception) {
        Toast.makeText(context, "No application available to open this file", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun BuiltInFilePreviewHost(
    preview: ActiveFilePreview?,
    onDismiss: () -> Unit,
    onExtractZip: ((File) -> Unit)? = null,
    onShare: ((File, Uri?) -> Unit)? = null,
    onVault: ((File, Uri?) -> Unit)? = null,
    onDelete: ((File, Uri?) -> Unit)? = null,
    onShowDetails: ((File) -> Unit)? = null,
    onFileSaved: ((File) -> Unit)? = null
) {
    if (preview == null) return
    val context = LocalContext.current

    when (preview.type) {
        InAppViewerType.IMAGE -> InAppImageViewerDialog(
            file = preview.file,
            contentUri = preview.contentUri,
            inMemoryBitmap = preview.inMemoryBitmap,
            displaySizeOverride = preview.displaySizeOverride,
            onDismiss = onDismiss,
            onShare = onShare?.let { fn -> { fn(preview.file, preview.contentUri) } },
            onVault = onVault?.let { fn -> { fn(preview.file, preview.contentUri); onDismiss() } },
            onDelete = onDelete?.let { fn -> { fn(preview.file, preview.contentUri); onDismiss() } },
            onDetails = onShowDetails?.let { fn -> { fn(preview.file) } },
            onOpenExternal = if (preview.inMemoryBitmap != null && !preview.file.exists()) null else {
                { openExternalWithProvider(context, preview.file, preview.contentUri, "image/*") }
            }
        )

        InAppViewerType.TEXT -> InAppTextViewerDialog(
            file = preview.file,
            inMemoryText = preview.inMemoryText,
            displaySizeOverride = preview.displaySizeOverride,
            onDismiss = onDismiss,
            onOpenExternal = if (preview.inMemoryText != null && !preview.file.exists()) null else {
                { openExternalWithProvider(context, preview.file, preview.contentUri, "text/plain") }
            },
            onSaved = { onFileSaved?.invoke(preview.file) }
        )

        InAppViewerType.ZIP -> InAppZipViewerDialog(
            file = preview.file,
            onDismiss = onDismiss,
            onExtract = {
                onDismiss()
                onExtractZip?.invoke(preview.file)
            },
            onOpenExternal = { openExternalWithProvider(context, preview.file, preview.contentUri, "application/zip") }
        )

        InAppViewerType.APK -> InAppApkViewerSheet(
            file = preview.file,
            onDismiss = onDismiss,
            onInstall = {
                openExternalWithProvider(
                    context,
                    preview.file,
                    preview.contentUri,
                    "application/vnd.android.package-archive"
                )
            }
        )
    }
}

@Composable
private fun InAppImageViewerDialog(
    file: File,
    contentUri: Uri?,
    inMemoryBitmap: Bitmap? = null,
    displaySizeOverride: Long? = null,
    onDismiss: () -> Unit,
    onShare: (() -> Unit)?,
    onVault: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    onDetails: (() -> Unit)?,
    onOpenExternal: (() -> Unit)?
) {
    val context = LocalContext.current
    var bitmap by remember(file.absolutePath, contentUri, inMemoryBitmap) { mutableStateOf(inMemoryBitmap) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(file.absolutePath, contentUri, inMemoryBitmap) {
        if (inMemoryBitmap != null) {
            bitmap = inMemoryBitmap
            return@LaunchedEffect
        }
        bitmap = withContext(Dispatchers.IO) {
            try {
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                if (file.exists()) {
                    BitmapFactory.decodeFile(file.absolutePath, opts)
                } else if (contentUri != null) {
                    context.contentResolver.openInputStream(contentUri)?.use {
                        BitmapFactory.decodeStream(it, null, opts)
                    }
                }
                val maxDim = maxOf(opts.outWidth, opts.outHeight)
                val sample = if (maxDim > 2048) (maxDim / 1024).coerceAtLeast(2) else 1
                val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
                if (file.exists()) {
                    BitmapFactory.decodeFile(file.absolutePath, decodeOpts)
                } else if (contentUri != null) {
                    context.contentResolver.openInputStream(contentUri)?.use {
                        BitmapFactory.decodeStream(it, null, decodeOpts)
                    }
                } else null
            } catch (_: Exception) {
                null
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.96f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 72.dp, bottom = 112.dp)
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val newScale = (scale * zoom).coerceIn(1f, 4f)
                            scale = newScale
                            offset = if (newScale > 1f) offset + pan else Offset.Zero
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                val safeBmp = remember(bitmap) { bitmap.toSafeComposeBitmap() }
                if (safeBmp != null) {
                    Image(
                        bitmap = safeBmp.asImageBitmap(),
                        contentDescription = file.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offset.x,
                                translationY = offset.y
                            )
                    )
                } else {
                    Text("Unable to decode image", color = TextSecondary)
                }
            }

            // Top bar
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.72f))
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close Preview", tint = Color.White)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = file.name,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val shownSize = displaySizeOverride ?: if (file.exists()) file.length() else null
                    if (shownSize != null && shownSize > 0L) {
                        Text(formatBytes(shownSize), color = TextSecondary, style = MaterialTheme.typography.labelSmall)
                    }
                }
                if (onOpenExternal != null) {
                    IconButton(onClick = onOpenExternal) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open External", tint = AuraCyan)
                    }
                }
            }

            // Floating bottom quick action bar (elevated above gesture bar / rounded screen corners)
            val hasAnyActions = onShare != null || onVault != null || onDetails != null || onDelete != null
            if (hasAnyActions) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(start = 20.dp, end = 20.dp, bottom = 28.dp, top = 8.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(GlassPanel.copy(alpha = 0.94f))
                        .border(1.dp, GlassBorder, RoundedCornerShape(22.dp))
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    onShare?.let {
                        ViewerBottomAction(Modifier.weight(1f), Icons.Default.Share, "Share", AuraCyan, it)
                    }
                    onVault?.let {
                        ViewerBottomAction(Modifier.weight(1f), Icons.Default.Lock, "Vault", AuraPurple, it)
                    }
                    onDetails?.let {
                        ViewerBottomAction(Modifier.weight(1f), Icons.Default.Info, "Details", TextPrimary, it)
                    }
                    onDelete?.let {
                        ViewerBottomAction(Modifier.weight(1f), Icons.Default.Delete, "Delete", AuraRose, it)
                    }
                }
            }
        }
    }
}

@Composable
private fun ViewerBottomAction(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(horizontal = 6.dp, vertical = 8.dp)
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = tint,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun InAppTextViewerDialog(
    file: File,
    inMemoryText: String? = null,
    displaySizeOverride: Long? = null,
    onDismiss: () -> Unit,
    onOpenExternal: (() -> Unit)?,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var content by remember(file.absolutePath, inMemoryText) { mutableStateOf(inMemoryText ?: "") }
    var isEditing by remember { mutableStateOf(false) }
    var truncated by remember { mutableStateOf(false) }
    var readError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(file.absolutePath, inMemoryText) {
        if (inMemoryText != null) {
            content = inMemoryText
            truncated = false
            readError = null
            return@LaunchedEffect
        }
        val result = withContext(Dispatchers.IO) {
            try {
                val maxRead = 512 * 1024
                if (file.length() > maxRead) {
                    val buf = ByteArray(maxRead)
                    val readCount = file.inputStream().use { it.read(buf) }.coerceAtLeast(0)
                    Triple(String(buf, 0, readCount), true, null as String?)
                } else {
                    Triple(file.readText(), false, null as String?)
                }
            } catch (e: Throwable) {
                Triple("", false, e.message ?: "Cannot read file")
            }
        }
        content = result.first
        truncated = result.second
        readError = result.third
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Obsidian)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(GlassPanel)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = TextPrimary)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        file.name,
                        color = TextPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val shownSize = displaySizeOverride ?: file.length()
                    Text(
                        "${formatBytes(shownSize)}${if (truncated) " • Preview truncated at 512 KB" else ""}",
                        color = TextTertiary,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                if (inMemoryText == null && !truncated && file.exists() && file.canWrite() && readError == null) {
                    IconButton(onClick = {
                        if (isEditing) {
                            scope.launch(Dispatchers.IO) {
                                val ok = try {
                                    file.writeText(content)
                                    true
                                } catch (_: Exception) {
                                    false
                                }
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(
                                        context,
                                        if (ok) "Saved changes" else "Write denied (grant All Files Access)",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    if (ok) {
                                        isEditing = false
                                        onSaved()
                                    }
                                }
                            }
                        } else {
                            isEditing = true
                        }
                    }) {
                        Icon(
                            imageVector = if (isEditing) Icons.Default.Save else Icons.Default.Edit,
                            contentDescription = if (isEditing) "Save File" else "Edit File",
                            tint = if (isEditing) AuraGreen else AuraCyan
                        )
                    }
                }
                if (onOpenExternal != null) {
                    IconButton(onClick = onOpenExternal) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open External", tint = TextSecondary)
                    }
                }
            }

            HorizontalDivider(color = GlassBorder)

            val currentReadError = readError
            if (currentReadError != null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(currentReadError, color = AuraRose)
                }
            } else if (isEditing) {
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        color = TextPrimary
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AuraCyan,
                        unfocusedBorderColor = GlassBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    Text(
                        text = content.ifEmpty { "(Empty file)" },
                        color = if (content.isEmpty()) TextTertiary else TextPrimary,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 19.sp
                        )
                    )
                }
            }
        }
    }
}

private data class ZipEntryPreview(
    val name: String,
    val size: Long,
    val isDirectory: Boolean
)

@Composable
private fun InAppZipViewerDialog(
    file: File,
    onDismiss: () -> Unit,
    onExtract: () -> Unit,
    onOpenExternal: () -> Unit
) {
    var entries by remember(file.absolutePath) { mutableStateOf<List<ZipEntryPreview>>(emptyList()) }
    var loadError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(file.absolutePath) {
        val result = withContext(Dispatchers.IO) {
            try {
                val list = mutableListOf<ZipEntryPreview>()
                ZipInputStream(file.inputStream().buffered()).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null && list.size < 400) {
                        list.add(
                            ZipEntryPreview(
                                name = entry.name,
                                size = entry.size.coerceAtLeast(0L),
                                isDirectory = entry.isDirectory
                            )
                        )
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
                list to (null as String?)
            } catch (e: Throwable) {
                emptyList<ZipEntryPreview>() to (e.message ?: "Unable to read archive")
            }
        }
        entries = result.first
        loadError = result.second
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(GlassPanel)
                .border(1.dp, GlassBorder, RoundedCornerShape(22.dp))
                .padding(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.FolderZip, contentDescription = null, tint = AuraGreen)
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(file.name, color = TextPrimary, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${entries.size} entries • ${formatBytes(file.length())}", color = TextTertiary, style = MaterialTheme.typography.labelSmall)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            val currentLoadError = loadError
            if (currentLoadError != null) {
                Text(currentLoadError, color = AuraRose, style = MaterialTheme.typography.bodySmall)
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(entries) { entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Obsidian.copy(alpha = 0.45f))
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (entry.isDirectory) Icons.Default.Folder else Icons.AutoMirrored.Filled.InsertDriveFile,
                                contentDescription = null,
                                tint = if (entry.isDirectory) AuraCyan else TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = entry.name,
                                color = TextPrimary,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (!entry.isDirectory && entry.size > 0) {
                                Text(formatBytes(entry.size), color = TextTertiary, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onOpenExternal) {
                    Text("Open External", color = TextSecondary)
                }
                Button(
                    onClick = onExtract,
                    colors = ButtonDefaults.buttonColors(containerColor = AuraGreen)
                ) {
                    Icon(Icons.Default.Unarchive, contentDescription = null, tint = Obsidian, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Extract All", color = Obsidian, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private data class ApkMetadata(
    val appName: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val permissions: List<String>,
    val icon: Bitmap?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InAppApkViewerSheet(
    file: File,
    onDismiss: () -> Unit,
    onInstall: () -> Unit
) {
    val context = LocalContext.current
    var meta by remember(file.absolutePath) { mutableStateOf<ApkMetadata?>(null) }

    LaunchedEffect(file.absolutePath) {
        meta = withContext(Dispatchers.IO) {
            try {
                val pm = context.packageManager
                val flags = PackageManager.GET_PERMISSIONS
                val pkg = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageArchiveInfo(file.absolutePath, PackageManager.PackageInfoFlags.of(flags.toLong()))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageArchiveInfo(file.absolutePath, flags)
                } ?: return@withContext null

                val appInfo = pkg.applicationInfo
                appInfo?.sourceDir = file.absolutePath
                appInfo?.publicSourceDir = file.absolutePath
                val label = appInfo?.loadLabel(pm)?.toString() ?: file.nameWithoutExtension
                val verCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pkg.longVersionCode else {
                    @Suppress("DEPRECATION")
                    pkg.versionCode.toLong()
                }
                val iconBmp = try {
                    appInfo?.loadIcon(pm)?.let { drawable ->
                        if (drawable is BitmapDrawable && drawable.bitmap != null && !drawable.bitmap.isRecycled) {
                            drawable.bitmap.toSafeComposeBitmap()
                        } else {
                            val w = drawable.intrinsicWidth.coerceIn(64, 128)
                            val h = drawable.intrinsicHeight.coerceIn(64, 128)
                            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                            val canvas = Canvas(bmp)
                            drawable.setBounds(0, 0, canvas.width, canvas.height)
                            drawable.draw(canvas)
                            bmp
                        }
                    }
                } catch (_: Throwable) {
                    null
                }
                ApkMetadata(
                    appName = label,
                    packageName = pkg.packageName ?: "Unknown",
                    versionName = pkg.versionName ?: "1.0",
                    versionCode = verCode,
                    permissions = pkg.requestedPermissions?.map { it.substringAfterLast('.') }.orEmpty(),
                    icon = iconBmp
                )
            } catch (_: Throwable) {
                null
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = GlassPanel,
        contentColor = TextPrimary
    ) {
        val currentMeta = meta
        val safeIcon = remember(currentMeta?.icon) { currentMeta?.icon.toSafeComposeBitmap() }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (safeIcon != null && currentMeta != null) {
                    Image(
                        bitmap = safeIcon.asImageBitmap(),
                        contentDescription = currentMeta.appName,
                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier.size(48.dp).clip(CircleShape).background(AuraGreen.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Android, contentDescription = null, tint = AuraGreen, modifier = Modifier.size(26.dp))
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(currentMeta?.appName ?: file.name, color = TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(currentMeta?.packageName ?: "Android Package", color = TextTertiary, style = MaterialTheme.typography.labelSmall)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            InfoRow("FILE", file.name)
            InfoRow("SIZE", formatBytes(file.length()))
            if (currentMeta != null) {
                InfoRow("VERSION", "${currentMeta.versionName} (${currentMeta.versionCode})")
                InfoRow("PERMISSIONS", "${currentMeta.permissions.size} declared")
                if (currentMeta.permissions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        currentMeta.permissions.take(12).joinToString(" • "),
                        color = TextSecondary,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
            Button(
                onClick = onInstall,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = AuraGreen)
            ) {
                Icon(Icons.Default.Android, contentDescription = null, tint = Obsidian, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("INSTALL APK", color = Obsidian, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}
