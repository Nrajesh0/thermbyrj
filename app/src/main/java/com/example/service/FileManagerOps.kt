package com.example.service

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.MediaStore
import android.util.Size
import androidx.core.content.ContextCompat
import androidx.security.crypto.EncryptedFile
import com.example.ui.screens.VaultManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.math.max

enum class SharedClipboardOp {
    COPY, MOVE
}

data class SharedFileClipboard(
    val paths: Set<String>,
    val operation: SharedClipboardOp
)

object SharedClipboardHolder {
    private val _clipboard = MutableStateFlow<SharedFileClipboard?>(null)
    val clipboard: StateFlow<SharedFileClipboard?> = _clipboard.asStateFlow()

    fun setClipboard(paths: Set<String>, operation: SharedClipboardOp) {
        if (paths.isEmpty()) {
            _clipboard.value = null
        } else {
            _clipboard.value = SharedFileClipboard(paths, operation)
        }
    }

    fun clear() {
        _clipboard.value = null
    }
}

object PendingExplorerNavigation {
    private val _targetFolderPath = MutableStateFlow<String?>(null)
    val targetFolderPath: StateFlow<String?> = _targetFolderPath.asStateFlow()

    fun openFolder(folderPath: String) {
        _targetFolderPath.value = folderPath
    }

    fun consume(): String? {
        val v = _targetFolderPath.value
        _targetFolderPath.value = null
        return v
    }
}

data class StorageVolumeRoot(
    val label: String,
    val rootDir: File,
    val isPrimary: Boolean
)

data class RecentMediaFile(
    val name: String,
    val path: String,
    val size: Long,
    val dateModifiedSec: Long,
    val mimeType: String,
    val contentUri: Uri
)

object FileManagerOps {

    val DOC_EXTENSIONS = setOf(
        "pdf", "txt", "doc", "docx", "ppt", "pptx", "xls", "xlsx",
        "csv", "rtf", "zip", "rar", "7z", "tar", "gz", "json", "xml", "md", "epub", "log"
    )

    val TEXT_VIEWABLE_EXTENSIONS = setOf(
        "txt", "md", "json", "xml", "log", "csv", "ini", "cfg", "conf",
        "yml", "yaml", "properties", "kt", "java", "py", "sh", "html", "css", "js", "sql", "env"
    )

    val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "heic", "bmp")

    fun getAvailableStorageVolumes(context: Context): List<StorageVolumeRoot> {
        val result = mutableListOf<StorageVolumeRoot>()
        val primary = Environment.getExternalStorageDirectory()
        if (primary != null && primary.exists()) {
            result.add(StorageVolumeRoot("Internal", primary, isPrimary = true))
        } else {
            result.add(StorageVolumeRoot("App Storage", context.filesDir, isPrimary = true))
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val sm = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
                sm?.storageVolumes?.forEach { vol ->
                    val dir = vol.directory
                    if (!vol.isPrimary && dir != null && dir.exists()) {
                        val label = vol.getDescription(context)?.takeIf { it.isNotBlank() } ?: "External"
                        if (result.none { it.rootDir.absolutePath == dir.absolutePath }) {
                            result.add(StorageVolumeRoot(label, dir, isPrimary = false))
                        }
                    }
                }
            } else {
                ContextCompat.getExternalFilesDirs(context, null).forEachIndexed { idx, appDir ->
                    if (idx > 0 && appDir != null) {
                        val root = appDir.parentFile?.parentFile?.parentFile?.parentFile
                        if (root != null && root.exists() && result.none { it.rootDir.absolutePath == root.absolutePath }) {
                            result.add(StorageVolumeRoot("SD / USB $idx", root, isPrimary = false))
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }
        return result
    }

    fun executeClipboardPaste(context: Context, clip: SharedFileClipboard, targetDir: File): Int {
        var successCount = 0
        for (srcPath in clip.paths) {
            val src = File(srcPath)
            if (!src.exists()) continue
            var dest = File(targetDir, src.name)
            if (dest.absolutePath == src.absolutePath) {
                if (clip.operation == SharedClipboardOp.MOVE) continue
                val suffix = if (src.extension.isNotBlank()) "_copy.${src.extension}" else "_copy"
                dest = File(targetDir, "${src.nameWithoutExtension}$suffix")
            } else if (dest.exists()) {
                val stamp = System.currentTimeMillis() % 10000
                val suffix = if (src.extension.isNotBlank()) "_$stamp.${src.extension}" else "_$stamp"
                dest = File(targetDir, "${src.nameWithoutExtension}$suffix")
            }
            try {
                if (clip.operation == SharedClipboardOp.COPY) {
                    src.copyRecursively(dest, overwrite = true)
                    MediaScannerConnection.scanFile(context, arrayOf(dest.absolutePath), null, null)
                    successCount++
                } else {
                    if (src.renameTo(dest)) {
                        FileShredder.cleanupMediaStoreEntry(context, src.absolutePath, null)
                        MediaScannerConnection.scanFile(context, arrayOf(dest.absolutePath), null, null)
                        successCount++
                    } else {
                        src.copyRecursively(dest, overwrite = true)
                        if (src.isDirectory) src.deleteRecursively() else src.delete()
                        FileShredder.cleanupMediaStoreEntry(context, src.absolutePath, null)
                        MediaScannerConnection.scanFile(context, arrayOf(dest.absolutePath), null, null)
                        successCount++
                    }
                }
            } catch (_: Exception) {
            }
        }
        StorageScanCache.invalidate()
        return successCount
    }

    fun renameFileSafely(context: Context, file: File, newName: String): Boolean {
        val parent = file.parentFile ?: return false
        val dest = File(parent, newName)
        if (dest.exists()) return false
        return try {
            val ok = file.renameTo(dest)
            if (ok) {
                FileShredder.cleanupMediaStoreEntry(context, file.absolutePath, null)
                MediaScannerConnection.scanFile(context, arrayOf(dest.absolutePath), null, null)
                StorageScanCache.invalidate()
            }
            ok
        } catch (_: Exception) {
            false
        }
    }

    fun compressFilesToZip(sources: List<File>, destZip: File): Boolean {
        return try {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(destZip))).use { zos ->
                for (src in sources) {
                    addFileToZip(zos, src, src.name)
                }
            }
            StorageScanCache.invalidate()
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun addFileToZip(zos: ZipOutputStream, file: File, entryName: String) {
        if (file.isDirectory) {
            val children = file.listFiles() ?: return
            for (child in children) {
                addFileToZip(zos, child, "$entryName/${child.name}")
            }
        } else {
            FileInputStream(file).use { fis ->
                BufferedInputStream(fis).use { origin ->
                    val entry = ZipEntry(entryName)
                    zos.putNextEntry(entry)
                    origin.copyTo(zos)
                    zos.closeEntry()
                }
            }
        }
    }

    fun extractZipArchive(context: Context, zipFile: File, targetParentDir: File): Boolean {
        return try {
            val outDir = File(targetParentDir, zipFile.nameWithoutExtension)
            if (!outDir.exists()) outDir.mkdirs()
            val canonicalOutDir = outDir.canonicalPath
            ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    val outFile = File(outDir, entry.name)
                    if (!outFile.canonicalPath.startsWith(canonicalOutDir)) {
                        break // Zip-slip protection
                    }
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { fos ->
                            zis.copyTo(fos)
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            MediaScannerConnection.scanFile(context, arrayOf(outDir.absolutePath), null, null)
            StorageScanCache.invalidate()
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Encrypts a file into the AES-256-GCM Vault, records original path metadata,
     * and securely shreds/cleans up both disk plaintext and MediaStore index.
     */
    fun encryptFileToVault(context: Context, sourceFile: File, contentUri: Uri? = null): Boolean {
        return try {
            if (!sourceFile.exists() || !sourceFile.isFile) return false
            val originalName = sourceFile.name
            val originalParent = sourceFile.parentFile?.absolutePath
            val originalSize = sourceFile.length()
            val uuid = UUID.randomUUID().toString()
            val vaultDir = VaultManager.getVaultDir(context)
            val destFile = File(vaultDir, "$uuid.enc")
            if (destFile.exists()) destFile.delete()

            val encryptedFile = EncryptedFile.Builder(
                context,
                destFile,
                VaultManager.getMasterKey(context),
                EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
            ).build()

            sourceFile.inputStream().use { input ->
                encryptedFile.openFileOutput().use { output ->
                    input.copyTo(output)
                }
            }

            if (!destFile.exists() || (originalSize > 0L && destFile.length() == 0L)) {
                destFile.delete()
                return false
            }

            VaultManager.registerVaultEntry(
                context = context,
                uuid = uuid,
                originalName = originalName,
                originalParentPath = originalParent,
                originalSizeBytes = originalSize
            )

            // Securely shred plaintext blocks (NIST 1-Pass) + clean up MediaStore index
            val targetUri = contentUri ?: FileShredder.findMediaContentUriForFile(context, sourceFile)
            val shredResult = FileShredder.shredTargets(
                context = context,
                targets = listOf(ShredTarget(sourceFile, targetUri)),
                standard = ShredStandard.NIST_SP_800_88
            )
            if (shredResult.shreddedCount == 0 && sourceFile.exists()) {
                val deleted = sourceFile.delete()
                if (deleted) {
                    FileShredder.cleanupMediaStoreEntry(context, sourceFile.absolutePath, targetUri)
                } else if (targetUri != null) {
                    try {
                        context.contentResolver.delete(targetUri, null, null)
                    } catch (_: Exception) {
                    }
                }
            }
            StorageScanCache.invalidate()
            true
        } catch (_: Exception) {
            false
        }
    }

    fun computeRecursiveSize(file: File): Long {
        return try {
            if (file.isFile) file.length()
            else file.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        } catch (_: Exception) {
            0L
        }
    }

    fun computeSha256Short(file: File): String? {
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buf = ByteArray(8192)
                var read: Int
                while (input.read(buf).also { read = it } != -1) {
                    md.update(buf, 0, read)
                }
            }
            md.digest().take(8).joinToString("") { "%02x".format(it) } + "..."
        } catch (_: Exception) {
            null
        }
    }

    fun searchFilesRecursively(
        rootDir: File,
        query: String,
        showHidden: Boolean,
        maxResults: Int = 250
    ): List<File> {
        val q = query.trim().lowercase()
        if (q.isBlank() || !rootDir.exists() || !rootDir.isDirectory) return emptyList()
        val results = mutableListOf<File>()
        val queue = ArrayDeque<File>()
        queue.add(rootDir)

        while (queue.isNotEmpty() && results.size < maxResults) {
            val dir = queue.removeFirst()
            val children = try {
                dir.listFiles()
            } catch (_: Exception) {
                null
            } ?: continue

            for (child in children) {
                val name = child.name
                if (!showHidden && name.startsWith(".")) continue
                // Skip giant Android/data cache trees unless specifically inside them
                if (child.isDirectory && child.parentFile?.name == "Android" && name in setOf("data", "obb")) {
                    continue
                }
                if (name.lowercase().contains(q)) {
                    results.add(child)
                    if (results.size >= maxResults) break
                }
                if (child.isDirectory && results.size < maxResults) {
                    queue.add(child)
                }
            }
        }
        return results
    }

    fun queryDownloadsSize(context: Context): Long {
        var size = 0L
        val dlDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (dlDir != null && dlDir.exists() && PermissionUtils.hasAllFilesPermission()) {
            return computeRecursiveSize(dlDir)
        }
        try {
            val uri = MediaStore.Files.getContentUri("external")
            val projection = arrayOf(MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.DATA)
            context.contentResolver.query(uri, projection, "${MediaStore.MediaColumns.SIZE} > 0", null, null)?.use { c ->
                val sizeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val dataCol = c.getColumnIndex(MediaStore.MediaColumns.DATA)
                while (c.moveToNext()) {
                    val path = if (dataCol >= 0) c.getString(dataCol) ?: "" else ""
                    if (path.contains("/Download/", ignoreCase = true) || path.contains("/Downloads/", ignoreCase = true)) {
                        size += max(0L, c.getLong(sizeCol))
                    }
                }
            }
        } catch (_: Exception) {
        }
        return size
    }

    fun queryApksSize(context: Context): Long {
        var size = 0L
        try {
            val uri = MediaStore.Files.getContentUri("external")
            val projection = arrayOf(MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.DATA)
            context.contentResolver.query(uri, projection, "${MediaStore.MediaColumns.SIZE} > 0", null, null)?.use { c ->
                val sizeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val nameCol = c.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                val dataCol = c.getColumnIndex(MediaStore.MediaColumns.DATA)
                while (c.moveToNext()) {
                    val path = if (dataCol >= 0) c.getString(dataCol) ?: "" else ""
                    val name = if (nameCol >= 0) c.getString(nameCol) ?: "" else ""
                    val candidate = path.ifBlank { name }
                    if (candidate.endsWith(".apk", ignoreCase = true)) {
                        size += max(0L, c.getLong(sizeCol))
                    }
                }
            }
        } catch (_: Exception) {
        }
        return size
    }

    fun queryRecentFiles(context: Context, limit: Int = 12): List<RecentMediaFile> {
        val results = mutableListOf<RecentMediaFile>()
        try {
            val uri = MediaStore.Files.getContentUri("external")
            val projection = arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.DATE_MODIFIED,
                MediaStore.MediaColumns.DATA,
                MediaStore.MediaColumns.MIME_TYPE,
                MediaStore.Files.FileColumns.MEDIA_TYPE
            )
            val selection = "${MediaStore.MediaColumns.SIZE} > 0 AND ${MediaStore.Files.FileColumns.MEDIA_TYPE} != ${MediaStore.Files.FileColumns.MEDIA_TYPE_NONE}"
            val sortOrder = "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
            context.contentResolver.query(uri, projection, selection, null, sortOrder)?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameCol = c.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                val sizeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val dateCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
                val dataCol = c.getColumnIndex(MediaStore.MediaColumns.DATA)
                val mimeCol = c.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)

                while (c.moveToNext() && results.size < limit) {
                    val id = c.getLong(idCol)
                    val path = if (dataCol >= 0) c.getString(dataCol) ?: "" else ""
                    if (path.contains("/.")) continue
                    val rawName = if (nameCol >= 0) c.getString(nameCol) ?: "" else ""
                    val name = rawName.ifBlank { File(path).name.ifBlank { "File_$id" } }
                    val size = c.getLong(sizeCol)
                    val date = c.getLong(dateCol)
                    val mime = (if (mimeCol >= 0) c.getString(mimeCol) else null) ?: "*/*"
                    val contentUri = ContentUris.withAppendedId(uri, id)
                    results.add(RecentMediaFile(name, path, size, date, mime, contentUri))
                }
            }
        } catch (_: Exception) {
        }
        return results
    }

    fun loadFileThumbnailBitmap(context: Context, file: File): Bitmap? {
        val ext = file.extension.lowercase()
        if (ext == "apk") {
            return try {
                val pm = context.packageManager
                val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageArchiveInfo(file.absolutePath, PackageManager.PackageInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageArchiveInfo(file.absolutePath, 0)
                }
                val appInfo = pkgInfo?.applicationInfo ?: return null
                appInfo.sourceDir = file.absolutePath
                appInfo.publicSourceDir = file.absolutePath
                val drawable = appInfo.loadIcon(pm)
                if (drawable is BitmapDrawable && drawable.bitmap != null) {
                    drawable.bitmap
                } else {
                    val w = drawable.intrinsicWidth.coerceAtLeast(64)
                    val h = drawable.intrinsicHeight.coerceAtLeast(64)
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bmp)
                    drawable.setBounds(0, 0, canvas.width, canvas.height)
                    drawable.draw(canvas)
                    bmp
                }
            } catch (_: Exception) {
                null
            }
        }

        if (ext !in IMAGE_EXTENSIONS && ext !in setOf("mp4", "mkv", "mov", "avi", "webm")) {
            return null
        }

        val contentUri = FileShredder.findMediaContentUriForFile(context, file)
        if (contentUri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                return context.contentResolver.loadThumbnail(contentUri, Size(192, 192), CancellationSignal())
            } catch (_: Exception) {
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                if (ext in IMAGE_EXTENSIONS) {
                    return android.media.ThumbnailUtils.createImageThumbnail(file, Size(192, 192), null)
                } else {
                    return android.media.ThumbnailUtils.createVideoThumbnail(file, Size(192, 192), null)
                }
            } catch (_: Exception) {
            }
        }
        return null
    }
}
