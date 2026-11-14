package com.example.service

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.provider.MediaStore
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
    val failedTargets: List<ShredTarget>
)

object FileShredder {

    private val random = SecureRandom()

    fun shredTargets(
        context: Context,
        targets: List<ShredTarget>,
        standard: ShredStandard = ShredStandard.DOD_5220_22_M,
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
        val blocked = mutableListOf<ShredTarget>()
        val failed = mutableListOf<ShredTarget>()
        val buffer = ByteArray(65536)

        expandedFiles.forEachIndexed { idx, target ->
            val file = target.file
            val fileName = file.name.ifBlank { "File ${idx + 1}" }
            onProgress(idx, total, "Shredding $fileName...")

            var wipedSuccessfully = false
            val fileLen = if (file.exists()) file.length() else 0L

            if (file.exists() && file.isFile && file.canWrite()) {
                wipedSuccessfully = overwriteAndDestroyLocalFile(
                    context = context,
                    file = file,
                    contentUri = target.contentUri,
                    standard = standard,
                    buffer = buffer
                )
            } else if (target.contentUri != null) {
                wipedSuccessfully = overwriteViaContentUri(
                    context = context,
                    uri = target.contentUri,
                    fileLengthHint = fileLen,
                    standard = standard,
                    buffer = buffer
                )
            }

            if (wipedSuccessfully) {
                shreddedCount++
                totalBytesWiped += fileLen
            } else if (file.exists() && !file.canWrite()) {
                blocked.add(target)
            } else if (target.contentUri != null) {
                // Scoped storage blocked direct write; do NOT falsely delete normally and claim "shredded"
                blocked.add(target)
            } else {
                failed.add(target)
            }

            onProgress(idx + 1, total, "Completed ${idx + 1} / $total")
        }

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

        return ShredResult(
            shreddedCount = shreddedCount,
            totalBytesWiped = totalBytesWiped,
            blockedForOverwrite = blocked,
            failedTargets = failed
        )
    }

    private fun overwriteAndDestroyLocalFile(
        context: Context,
        file: File,
        contentUri: Uri?,
        standard: ShredStandard,
        buffer: ByteArray
    ): Boolean {
        val originalPath = file.absolutePath
        return try {
            val length = file.length()
            if (length > 0L) {
                when (standard) {
                    ShredStandard.DOD_5220_22_M -> {
                        overwritePass(file, length, mode = 0, buffer = buffer) // 0x00
                        overwritePass(file, length, mode = 1, buffer = buffer) // 0xFF
                        overwritePass(file, length, mode = 2, buffer = buffer) // Random
                    }
                    ShredStandard.NIST_SP_800_88 -> {
                        overwritePass(file, length, mode = 2, buffer = buffer) // Random
                    }
                }
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

    private fun overwritePass(file: File, length: Long, mode: Int, buffer: ByteArray) {
        RandomAccessFile(file, "rws").use { raf ->
            raf.seek(0L)
            var written = 0L
            when (mode) {
                0 -> java.util.Arrays.fill(buffer, 0x00.toByte())
                1 -> java.util.Arrays.fill(buffer, 0xFF.toByte())
            }
            while (written < length) {
                if (mode == 2) {
                    random.nextBytes(buffer)
                }
                val toWrite = minOf(buffer.size.toLong(), length - written).toInt()
                raf.write(buffer, 0, toWrite)
                written += toWrite
            }
            raf.fd.sync()
        }
    }

    private fun overwriteViaContentUri(
        context: Context,
        uri: Uri,
        fileLengthHint: Long,
        standard: ShredStandard,
        buffer: ByteArray
    ): Boolean {
        return try {
            val pfd = context.contentResolver.openFileDescriptor(uri, "rw") ?: return false
            pfd.use { descriptor ->
                val actualLen = descriptor.statSize.takeIf { it > 0L } ?: fileLengthHint
                val passes = if (standard == ShredStandard.DOD_5220_22_M) listOf(0, 1, 2) else listOf(2)
                if (actualLen > 0L) {
                    FileOutputStream(descriptor.fileDescriptor).use { fos ->
                        val ch = fos.channel
                        passes.forEach { mode ->
                            ch.position(0L)
                            when (mode) {
                                0 -> java.util.Arrays.fill(buffer, 0x00.toByte())
                                1 -> java.util.Arrays.fill(buffer, 0xFF.toByte())
                            }
                            var written = 0L
                            while (written < actualLen) {
                                if (mode == 2) random.nextBytes(buffer)
                                val toWrite = minOf(buffer.size.toLong(), actualLen - written).toInt()
                                ch.write(java.nio.ByteBuffer.wrap(buffer, 0, toWrite))
                                written += toWrite
                            }
                            ch.force(true)
                        }
                        ch.truncate(0L)
                        ch.force(true)
                    }
                }
            }
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
        if (contentUri != null) {
            try {
                val rows = context.contentResolver.delete(contentUri, null, null)
                if (rows > 0) deleted = true
            } catch (_: Exception) {
            }
        } else if (deleted) {
            cleanupMediaStoreEntry(context, file.absolutePath, null)
        }
        return deleted || !file.exists()
    }
}
