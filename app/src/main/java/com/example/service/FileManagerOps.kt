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

    internal const val MAX_ZIP_ENTRIES = 10_000
    internal const val MAX_ZIP_TOTAL_UNCOMPRESSED_BYTES = 2L * 1024L * 1024L * 1024L
    private const val MAX_DIRECTORY_WALK_DEPTH = 32

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

    internal fun buildUniqueDestination(targetDir: File, source: File, sameDirectoryCopy: Boolean): File {
        val base = if (source.isDirectory) source.name else source.nameWithoutExtension
        val ext = if (source.isDirectory) "" else source.extension
        val initialName = if (sameDirectoryCopy) {
            if (ext.isNotBlank()) "${base}_copy.$ext" else "${base}_copy"
        } else {
            source.name
        }
        var candidate = File(targetDir, initialName)
        if (!candidate.exists()) return candidate

        var index = 2
        while (candidate.exists()) {
            val suffix = if (sameDirectoryCopy) "_copy_$index" else "_$index"
            val name = if (ext.isNotBlank()) "$base$suffix.$ext" else "$base$suffix"
            candidate = File(targetDir, name)
            index++
        }
        return candidate
    }

    internal fun isDescendantOrSameDirectory(src: File, targetDir: File): Boolean {
        if (!src.isDirectory) return false
        return try {
            val srcCanon = src.canonicalPath
            val targetCanon = targetDir.canonicalPath
            val srcPrefix = if (srcCanon.endsWith(File.separator)) srcCanon else srcCanon + File.separator
            targetCanon == srcCanon || targetCanon.startsWith(srcPrefix)
        } catch (_: Exception) {
            false
        }
    }

    internal fun isValidFileName(name: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed == "." || trimmed == "..") return false
        if (trimmed.contains('/') || trimmed.contains('\\') || trimmed.contains('\u0000')) return false
        return true
    }

    fun executeClipboardPaste(context: Context, clip: SharedFileClipboard, targetDir: File): Int {
        if (!targetDir.exists() || !targetDir.isDirectory) return 0
        var successCount = 0
        for (srcPath in clip.paths) {
            val src = File(srcPath)
            if (!src.exists()) continue

            // Prevent pasting a directory into itself or one of its subdirectories
            if (isDescendantOrSameDirectory(src, targetDir)) continue

            val samePath = try {
                File(targetDir, src.name).canonicalPath == src.canonicalPath
            } catch (_: Exception) {
                File(targetDir, src.name).absolutePath == src.absolutePath
            }
            if (samePath && clip.operation == SharedClipboardOp.MOVE) continue

            val dest = buildUniqueDestination(targetDir, src, sameDirectoryCopy = samePath)
            try {
                if (clip.operation == SharedClipboardOp.COPY) {
                    val copied = src.copyRecursively(dest, overwrite = false)
                    if (copied && dest.exists()) {
                        MediaScannerConnection.scanFile(context, arrayOf(dest.absolutePath), null, null)
                        successCount++
                    } else if (dest.exists()) {
                        if (dest.isDirectory) dest.deleteRecursively() else dest.delete()
                    }
                } else {
                    if (src.renameTo(dest)) {
                        FileShredder.cleanupMediaStoreEntry(context, src.absolutePath, null)
                        MediaScannerConnection.scanFile(context, arrayOf(dest.absolutePath), null, null)
                        successCount++
                    } else {
                        val expectedSize = if (src.isFile) src.length() else computeRecursiveSize(src)
                        val copied = src.copyRecursively(dest, overwrite = false)
                        val verified = copied && dest.exists() && (
                            if (src.isFile) dest.length() == expectedSize
                            else computeRecursiveSize(dest) == expectedSize
                        )
                        if (verified) {
                            if (src.isDirectory) src.deleteRecursively() else src.delete()
                            FileShredder.cleanupMediaStoreEntry(context, src.absolutePath, null)
                            MediaScannerConnection.scanFile(context, arrayOf(dest.absolutePath), null, null)
                            successCount++
                        } else {
                            // Never delete source if cross-filesystem copy failed or was truncated
                            if (dest.exists()) {
                                if (dest.isDirectory) dest.deleteRecursively() else dest.delete()
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                try {
                    if (dest.exists() && src.exists()) {
                        if (dest.isDirectory) dest.deleteRecursively() else dest.delete()
                    }
                } catch (_: Exception) {
                }
            }
        }
        if (successCount > 0) {
            StorageScanCache.invalidate()
        }
        return successCount
    }

    fun renameFileSafely(context: Context, file: File, newName: String): Boolean {
        val cleanName = newName.trim()
        if (!isValidFileName(cleanName)) return false
        val parent = file.parentFile ?: return false
        val dest = File(parent, cleanName)
        if (dest.exists()) return false
        return try {
            val parentCanon = parent.canonicalPath
            val destParentCanon = dest.canonicalFile.parentFile?.canonicalPath
            if (destParentCanon != parentCanon) return false
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
            val destCanonical = destZip.canonicalPath
            ZipOutputStream(BufferedOutputStream(FileOutputStream(destZip))).use { zos ->
                for (src in sources) {
                    if (!src.exists()) continue
                    if (src.canonicalPath == destCanonical) continue
                    addFileToZip(zos, src, src.name, destCanonical)
                }
            }
            StorageScanCache.invalidate()
            true
        } catch (_: Exception) {
            try {
                if (destZip.exists()) destZip.delete()
            } catch (_: Exception) {
            }
            false
        }
    }

    private fun addFileToZip(zos: ZipOutputStream, file: File, entryName: String, destCanonical: String) {
        if (file.canonicalPath == destCanonical) return
        if (file.isDirectory) {
            val children = file.listFiles() ?: emptyArray()
            if (children.isEmpty()) {
                val dirEntryName = if (entryName.endsWith("/")) entryName else "$entryName/"
                zos.putNextEntry(ZipEntry(dirEntryName))
                zos.closeEntry()
            } else {
                for (child in children) {
                    addFileToZip(zos, child, "$entryName/${child.name}", destCanonical)
                }
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

    internal fun buildUniqueExtractDir(targetParentDir: File, baseName: String): File {
        val cleanBase = baseName.trim().ifBlank { "extracted" }
            .replace('/', '_')
            .replace('\\', '_')
        var candidate = File(targetParentDir, cleanBase)
        if (!candidate.exists()) return candidate
        var index = 2
        while (candidate.exists()) {
            candidate = File(targetParentDir, "${cleanBase}_$index")
            index++
        }
        return candidate
    }

    fun extractZipArchive(
        context: Context,
        zipFile: File,
        targetParentDir: File,
        maxEntries: Int = MAX_ZIP_ENTRIES,
        maxTotalBytes: Long = MAX_ZIP_TOTAL_UNCOMPRESSED_BYTES
    ): Boolean {
        var outDir: File? = null
        return try {
            val dir = buildUniqueExtractDir(targetParentDir, zipFile.nameWithoutExtension)
            if (!dir.mkdirs() && !dir.isDirectory) return false
            outDir = dir
            val canonicalOutDir = dir.canonicalPath
            val canonicalOutPrefix = if (canonicalOutDir.endsWith(File.separator)) {
                canonicalOutDir
            } else {
                canonicalOutDir + File.separator
            }
            var entryCount = 0
            var totalBytesWritten = 0L
            val copyBuffer = ByteArray(64 * 1024)
            ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    entryCount++
                    if (entryCount > maxEntries) {
                        throw SecurityException("ZIP archive exceeds maximum entry count ($maxEntries)")
                    }
                    val outFile = File(dir, entry.name)
                    val outCanonical = outFile.canonicalPath
                    if (outCanonical != canonicalOutDir && !outCanonical.startsWith(canonicalOutPrefix)) {
                        throw SecurityException("Blocked Zip-Slip path traversal: ${entry.name}")
                    }
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { fos ->
                            var read: Int
                            while (zis.read(copyBuffer).also { read = it } != -1) {
                                totalBytesWritten += read
                                if (totalBytesWritten > maxTotalBytes) {
                                    throw SecurityException("ZIP archive exceeds maximum uncompressed size ($maxTotalBytes bytes)")
                                }
                                fos.write(copyBuffer, 0, read)
                            }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            MediaScannerConnection.scanFile(context, arrayOf(dir.absolutePath), null, null)
            StorageScanCache.invalidate()
            true
        } catch (_: Exception) {
            try {
                outDir?.deleteRecursively()
            } catch (_: Exception) {
            }
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
        val visitedCanonicalDirs = HashSet<String>()
        val queue = ArrayDeque<Pair<File, Int>>()
        val rootCanon = try {
            rootDir.canonicalPath
        } catch (_: Exception) {
            rootDir.absolutePath
        }
        visitedCanonicalDirs.add(rootCanon)
        queue.add(rootDir to 0)

        while (queue.isNotEmpty() && results.size < maxResults) {
            val (dir, depth) = queue.removeFirst()
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
                if (child.isDirectory && results.size < maxResults && depth < MAX_DIRECTORY_WALK_DEPTH) {
                    val childCanon = try {
                        child.canonicalPath
                    } catch (_: Exception) {
                        child.absolutePath
                    }
                    if (visitedCanonicalDirs.add(childCanon)) {
                        queue.add(child to (depth + 1))
                    }
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
                if (drawable is BitmapDrawable && drawable.bitmap != null && !drawable.bitmap.isRecycled) {
                    val src = drawable.bitmap.toSafeComposeBitmap()
                    if (src != null) {
                        if (src.width <= 128 && src.height <= 128) src
                        else Bitmap.createScaledBitmap(src, 128, 128, true)
                    } else null
                } else {
                    val w = (if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 96).coerceIn(1, 128)
                    val h = (if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 96).coerceIn(1, 128)
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bmp)
                    drawable.setBounds(0, 0, canvas.width, canvas.height)
                    drawable.draw(canvas)
                    bmp
                }
            } catch (_: Throwable) {
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
