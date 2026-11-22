package com.example.service

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.security.SecureRandom

enum class ShredStandard(
    val title: String,
    val subtitle: String,
    val passes: Int
) {
    DOD_5220_22_M(
        title = "DoD 5220.22-M (3-Pass)",
        subtitle = "Zeroes (0x00) -> Ones (0xFF) -> Cryptographic Random + fsync + filename scramble",
        passes = 3
    ),
    NIST_SP_800_88(
        title = "NIST SP 800-88 (1-Pass)",
        subtitle = "Fast cryptographic random overwrite + fsync + filename scramble",
        passes = 1
    )
}

data class ShredTarget(
    val file: File,
    val contentUri: Uri? = null
)

data class ShredResult(
    val shreddedCount: Int,
    val totalBytesWiped: Long,
    val blockedForOverwrite: List<ShredTarget>,
    val failedTargets: List<ShredTarget>,
    val wasCancelled: Boolean = false
)

data class ShredJobState(
    val isRunning: Boolean = false,
    val progress: Float = 0f,
    val completedFiles: Int = 0,
    val totalFiles: Int = 0,
    val statusText: String = "",
    val completionId: Long = 0L,
    val lastResult: ShredResult? = null,
    val attemptedTargetPaths: Set<String> = emptySet(),
    val attemptedTargetUris: Set<String> = emptySet()
)

object ShredJobCoordinator {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + safeCoroutineExceptionHandler())
    private val _state = MutableStateFlow(ShredJobState())
    val state: StateFlow<ShredJobState> = _state.asStateFlow()

    @Volatile
    private var activeJob: Job? = null

    @Volatile
    private var cancelRequested: Boolean = false

    fun cancelCurrentShred() {
        cancelRequested = true
        if (_state.value.isRunning) {
            _state.value = _state.value.copy(statusText = "Cancelling shredder...")
        }
    }

    fun startBackgroundShred(
        context: Context,
        targets: List<ShredTarget>,
        standard: ShredStandard
    ): Boolean {
        if (targets.isEmpty() || _state.value.isRunning) return false
        val appContext = context.applicationContext
        cancelRequested = false

        val initialTotal = targets.size.coerceAtLeast(1)
        // Set running state BEFORE starting FileShredService so onStartCommand never sees isRunning == false
        _state.value = ShredJobState(
            isRunning = true,
            progress = 0f,
            completedFiles = 0,
            totalFiles = initialTotal,
            statusText = "Starting ${standard.title}...",
            completionId = _state.value.completionId,
            lastResult = _state.value.lastResult,
            attemptedTargetPaths = targets.mapNotNull { it.file.absolutePath.takeIf(String::isNotBlank) }.toSet(),
            attemptedTargetUris = targets.mapNotNull { it.contentUri?.toString() }.toSet()
        )

        try {
            val svcIntent = Intent(appContext, FileShredService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appContext.startForegroundService(svcIntent)
            } else {
                appContext.startService(svcIntent)
            }
        } catch (_: Throwable) {
        }

        activeJob = scope.launch {
            val result = try {
                FileShredder.shredTargets(
                    context = appContext,
                    targets = targets,
                    standard = standard,
                    isCancelled = { cancelRequested },
                    onDetailedProgress = { fraction, done, total, label ->
                        if (!cancelRequested) {
                            _state.value = _state.value.copy(
                                isRunning = true,
                                progress = fraction.coerceIn(0f, 1f),
                                completedFiles = done,
                                totalFiles = total,
                                statusText = label
                            )
                        }
                    }
                )
            } catch (_: Throwable) {
                ShredResult(
                    shreddedCount = 0,
                    totalBytesWiped = 0L,
                    blockedForOverwrite = emptyList(),
                    failedTargets = targets,
                    wasCancelled = cancelRequested
                )
            }

            if (result.shreddedCount > 0) {
                StorageScanCache.invalidate()
            }

            cancelRequested = false
            _state.value = _state.value.copy(
                isRunning = false,
                progress = 1f,
                completionId = System.currentTimeMillis(),
                lastResult = result,
                statusText = when {
                    result.wasCancelled -> "Cancelled (${result.shreddedCount} shredded)"
                    result.blockedForOverwrite.isNotEmpty() ->
                        "Shredded ${result.shreddedCount}; ${result.blockedForOverwrite.size} blocked"
                    else -> "Shredded ${result.shreddedCount} file(s)"
                }
            )
        }
        return true
    }
}

object FileShredder {

    private const val SHRED_PREFS = "shred_state_prefs"
    private const val KEY_INTERRUPTED_PATH = "interrupted_shred_path"
    private val random = SecureRandom()

    fun peekInterruptedShredPath(context: Context): String? {
        return try {
            val path = context.applicationContext
                .getSharedPreferences(SHRED_PREFS, Context.MODE_PRIVATE)
                .getString(KEY_INTERRUPTED_PATH, null)
            if (!path.isNullOrBlank() && File(path).exists()) path else null
        } catch (_: Exception) {
            null
        }
    }

    fun clearInterruptedShredPath(context: Context) {
        try {
            context.applicationContext
                .getSharedPreferences(SHRED_PREFS, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_INTERRUPTED_PATH)
                .apply()
        } catch (_: Exception) {
        }
    }

    private fun markActiveShredPath(context: Context, path: String) {
        try {
            context.applicationContext
                .getSharedPreferences(SHRED_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_INTERRUPTED_PATH, path)
                .commit()
        } catch (_: Exception) {
        }
    }

    fun shredTargets(
        context: Context,
        targets: List<ShredTarget>,
        standard: ShredStandard = ShredStandard.DOD_5220_22_M,
        isCancelled: () -> Boolean = { false },
        onDetailedProgress: ((fraction: Float, completedFiles: Int, totalFiles: Int, statusLabel: String) -> Unit)? = null,
        onProgress: (completedFiles: Int, totalFiles: Int, statusLabel: String) -> Unit = { _, _, _ -> }
    ): ShredResult {
        val expandedFiles = mutableListOf<ShredTarget>()
        val directoriesToRemove = mutableListOf<File>()

        targets.forEach { target ->
            if (target.file.exists() && target.file.isDirectory) {
                val children = target.file.walkBottomUp().toList()
                children.forEach { child ->
                    if (child.isFile) {
                        expandedFiles.add(ShredTarget(child, null))
                    } else if (child.isDirectory) {
                        directoriesToRemove.add(child)
                    }
                }
            } else {
                expandedFiles.add(target)
            }
        }

        val total = expandedFiles.size.coerceAtLeast(1)
        var shreddedCount = 0
        var totalBytesWiped = 0L
        var cancelledEarly = false
        val blocked = mutableListOf<ShredTarget>()
        val failed = mutableListOf<ShredTarget>()
        val buffer = ByteArray(65536)

        for ((idx, target) in expandedFiles.withIndex()) {
            if (isCancelled()) {
                cancelledEarly = true
                break
            }
            val file = target.file
            val fileName = file.name.ifBlank { "File ${idx + 1}" }
            onProgress(idx, total, "Shredding $fileName...")
            onDetailedProgress?.invoke(idx.toFloat() / total.toFloat(), idx, total, "Shredding $fileName...")

            val perFileProgress: (Int, Int, Float) -> Unit = { passNum, totalPasses, passFraction ->
                val fileFraction = ((passNum - 1) + passFraction.coerceIn(0f, 1f)) / totalPasses.coerceAtLeast(1).toFloat()
                val overallFraction = ((idx + fileFraction) / total.toFloat()).coerceIn(0f, 1f)
                val pct = (passFraction.coerceIn(0f, 1f) * 100f).toInt()
                val label = "Pass $passNum/$totalPasses · $fileName ($pct%)"
                onDetailedProgress?.invoke(overallFraction, idx, total, label)
            }

            var wipedSuccessfully = false
            val fileLen = if (file.exists()) file.length() else 0L

            if (file.exists() && file.isFile && file.canWrite()) {
                markActiveShredPath(context, file.absolutePath)
                wipedSuccessfully = overwriteAndDestroyLocalFile(
                    context = context,
                    file = file,
                    contentUri = target.contentUri,
                    standard = standard,
                    buffer = buffer,
                    isCancelled = isCancelled,
                    onPassProgress = perFileProgress
                )
                clearInterruptedShredPath(context)
            } else if (target.contentUri != null) {
                if (file.absolutePath.isNotBlank() && file.exists()) {
                    markActiveShredPath(context, file.absolutePath)
                }
                wipedSuccessfully = overwriteViaContentUri(
                    context = context,
                    uri = target.contentUri,
                    fileLengthHint = fileLen,
                    standard = standard,
                    buffer = buffer,
                    isCancelled = isCancelled,
                    onPassProgress = perFileProgress
                )
                clearInterruptedShredPath(context)
            }

            if (wipedSuccessfully) {
                shreddedCount++
                totalBytesWiped += fileLen
            } else if (isCancelled()) {
                cancelledEarly = true
                failed.add(target)
                break
            } else if (file.exists() && !file.canWrite()) {
                blocked.add(target)
            } else if (target.contentUri != null) {
                // Scoped storage blocked direct write; do NOT falsely delete normally and claim "shredded"
                blocked.add(target)
            } else {
                failed.add(target)
            }

            val doneLabel = "Completed ${idx + 1} / $total"
            onProgress(idx + 1, total, doneLabel)
            onDetailedProgress?.invoke((idx + 1).toFloat() / total.toFloat(), idx + 1, total, doneLabel)
        }

        if (!cancelledEarly) {
            // Remove empty shredded subdirectories bottom-up
            directoriesToRemove
                .distinct()
                .sortedByDescending { it.absolutePath.length }
                .forEach { dir ->
                    try {
                        if (dir.exists() && (dir.listFiles()?.isEmpty() == true)) {
                            dir.delete()
                        }
                    } catch (_: Exception) {
                    }
                }
        }

        return ShredResult(
            shreddedCount = shreddedCount,
            totalBytesWiped = totalBytesWiped,
            blockedForOverwrite = blocked,
            failedTargets = failed,
            wasCancelled = cancelledEarly || isCancelled()
        )
    }

    private fun overwriteAndDestroyLocalFile(
        context: Context,
        file: File,
        contentUri: Uri?,
        standard: ShredStandard,
        buffer: ByteArray,
        isCancelled: () -> Boolean,
        onPassProgress: (passNum: Int, totalPasses: Int, passFraction: Float) -> Unit
    ): Boolean {
        val originalPath = file.absolutePath
        return try {
            val length = file.length()
            if (length > 0L) {
                val ok = when (standard) {
                    ShredStandard.DOD_5220_22_M -> {
                        overwritePass(file, length, mode = 0, buffer = buffer, isCancelled = isCancelled) { f -> onPassProgress(1, 3, f) } &&
                            overwritePass(file, length, mode = 1, buffer = buffer, isCancelled = isCancelled) { f -> onPassProgress(2, 3, f) } &&
                            overwritePass(file, length, mode = 2, buffer = buffer, isCancelled = isCancelled) { f -> onPassProgress(3, 3, f) }
                    }
                    ShredStandard.NIST_SP_800_88 -> {
                        overwritePass(file, length, mode = 2, buffer = buffer, isCancelled = isCancelled) { f -> onPassProgress(1, 1, f) }
                    }
                }
                if (!ok || isCancelled()) return false
            }

            // Truncate to 0 bytes and force metadata sync before rename/delete
            RandomAccessFile(file, "rws").use { raf ->
                raf.setLength(0L)
                raf.fd.sync()
            }

            // Rename to randomized name to obfuscate directory entry filename
            val obfuscatedFile = renameToRandomName(file)
            val deleted = obfuscatedFile.delete() || !obfuscatedFile.exists()

            // Remove MediaStore index entry if present
            cleanupMediaStoreEntry(context, originalPath, contentUri)
            deleted
        } catch (_: Exception) {
            false
        }
    }

    private fun overwritePass(
        file: File,
        length: Long,
        mode: Int,
        buffer: ByteArray,
        isCancelled: () -> Boolean,
        onFraction: (Float) -> Unit
    ): Boolean {
        RandomAccessFile(file, "rws").use { raf ->
            raf.seek(0L)
            var written = 0L
            var lastEmitMs = 0L
            when (mode) {
                0 -> java.util.Arrays.fill(buffer, 0x00.toByte())
                1 -> java.util.Arrays.fill(buffer, 0xFF.toByte())
            }
            onFraction(0f)
            while (written < length) {
                if (isCancelled()) return false
                if (mode == 2) {
                    random.nextBytes(buffer)
                }
                val toWrite = minOf(buffer.size.toLong(), length - written).toInt()
                raf.write(buffer, 0, toWrite)
                written += toWrite
                val now = System.currentTimeMillis()
                if (now - lastEmitMs >= 150L || written >= length) {
                    lastEmitMs = now
                    onFraction((written.toFloat() / length.toFloat()).coerceIn(0f, 1f))
                }
            }
            raf.fd.sync()
        }
        return true
    }

    private fun overwriteViaContentUri(
        context: Context,
        uri: Uri,
        fileLengthHint: Long,
        standard: ShredStandard,
        buffer: ByteArray,
        isCancelled: () -> Boolean,
        onPassProgress: (passNum: Int, totalPasses: Int, passFraction: Float) -> Unit
    ): Boolean {
        return try {
            val pfd = context.contentResolver.openFileDescriptor(uri, "rw") ?: return false
            var completedAllPasses = true
            pfd.use { descriptor ->
                val actualLen = descriptor.statSize.takeIf { it > 0L } ?: fileLengthHint
                val passes = if (standard == ShredStandard.DOD_5220_22_M) listOf(0, 1, 2) else listOf(2)
                if (actualLen > 0L) {
                    FileOutputStream(descriptor.fileDescriptor).use { fos ->
                        val ch = fos.channel
                        for ((passIdx, mode) in passes.withIndex()) {
                            if (isCancelled()) {
                                completedAllPasses = false
                                break
                            }
                            ch.position(0L)
                            when (mode) {
                                0 -> java.util.Arrays.fill(buffer, 0x00.toByte())
                                1 -> java.util.Arrays.fill(buffer, 0xFF.toByte())
                            }
                            var written = 0L
                            var lastEmitMs = 0L
                            onPassProgress(passIdx + 1, passes.size, 0f)
                            while (written < actualLen) {
                                if (isCancelled()) {
                                    completedAllPasses = false
                                    break
                                }
                                if (mode == 2) random.nextBytes(buffer)
                                val toWrite = minOf(buffer.size.toLong(), actualLen - written).toInt()
                                ch.write(java.nio.ByteBuffer.wrap(buffer, 0, toWrite))
                                written += toWrite
                                val now = System.currentTimeMillis()
                                if (now - lastEmitMs >= 150L || written >= actualLen) {
                                    lastEmitMs = now
                                    onPassProgress(passIdx + 1, passes.size, (written.toFloat() / actualLen.toFloat()).coerceIn(0f, 1f))
                                }
                            }
                            if (!completedAllPasses) break
                            ch.force(true)
                        }
                        if (completedAllPasses) {
                            ch.truncate(0L)
                            ch.force(true)
                        }
                    }
                }
            }
            if (!completedAllPasses || isCancelled()) return false
            val rows = context.contentResolver.delete(uri, null, null)
            rows > 0
        } catch (_: Exception) {
            false
        }
    }

    private fun renameToRandomName(file: File): File {
        val parent = file.parentFile ?: return file
        val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
        val sb = StringBuilder(16)
        repeat(16) {
            sb.append(chars[random.nextInt(chars.length)])
        }
        val target = File(parent, sb.toString())
        return if (file.renameTo(target)) target else file
    }

    fun findMediaContentUriForFile(context: Context, file: File): Uri? {
        val path = file.absolutePath
        if (path.isBlank()) return null
        val ext = file.extension.lowercase()
        val imagesUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val videoUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val audioUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val filesUri = MediaStore.Files.getContentUri("external")

        val uris = when (ext) {
            "jpg", "jpeg", "png", "webp", "gif", "heic", "heif", "bmp", "dng" ->
                listOf(imagesUri, filesUri)
            "mp4", "mkv", "mov", "avi", "webm", "3gp", "m4v" ->
                listOf(videoUri, filesUri)
            "mp3", "wav", "ogg", "m4a", "flac", "aac", "opus" ->
                listOf(audioUri, filesUri)
            else ->
                listOf(filesUri, imagesUri, videoUri, audioUri)
        }
        for (baseUri in uris) {
            try {
                context.contentResolver.query(
                    baseUri,
                    arrayOf(MediaStore.MediaColumns._ID),
                    "${MediaStore.MediaColumns.DATA} = ?",
                    arrayOf(path),
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val id = cursor.getLong(0)
                        return ContentUris.withAppendedId(baseUri, id)
                    }
                }
            } catch (_: Exception) {
            }
        }
        return null
    }

    fun cleanupMediaStoreEntry(context: Context, path: String?, contentUri: Uri?) {
        if (contentUri != null) {
            try {
                context.contentResolver.delete(contentUri, null, null)
            } catch (_: Exception) {
            }
        }
        if (!path.isNullOrBlank()) {
            try {
                context.contentResolver.delete(
                    MediaStore.Files.getContentUri("external"),
                    "${MediaStore.MediaColumns.DATA} = ?",
                    arrayOf(path)
                )
            } catch (_: Exception) {
            }
            try {
                MediaScannerConnection.scanFile(context, arrayOf(path), null, null)
            } catch (_: Exception) {
            }
        }
    }

    fun normalDeleteFile(context: Context, file: File, contentUri: Uri? = null): Boolean {
        var deleted = false
        try {
            if (file.exists()) {
                deleted = if (file.isDirectory) file.deleteRecursively() else file.delete()
            }
        } catch (_: Exception) {
        }
        if (deleted) {
            cleanupMediaStoreEntry(context, file.absolutePath, contentUri)
        } else if (contentUri != null) {
            try {
                val rows = context.contentResolver.delete(contentUri, null, null)
                if (rows > 0) deleted = true
            } catch (_: Exception) {
            }
        }
        return deleted || !file.exists()
    }
}
