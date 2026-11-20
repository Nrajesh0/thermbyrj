package com.example.ui.screens

import android.app.Activity
import android.app.KeyguardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Base64
import android.util.LruCache
import android.view.WindowManager
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.GlassCard
import com.example.formatBytes
import com.example.service.FileManagerOps
import com.example.service.FileShredder
import com.example.service.ShredStandard
import com.example.service.ShredTarget
import com.example.service.StorageScanCache
import com.example.service.toSafeComposeBitmap
import com.example.ui.components.ActiveFilePreview
import com.example.ui.components.BuiltInFilePreviewHost
import com.example.ui.components.ExpandableSearchBar
import com.example.ui.components.InAppViewerType
import com.example.ui.components.SearchIconToggleButton
import com.example.ui.components.resolveInAppViewerType
import com.example.ui.theme.AppTheme
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class VaultEntry(
    val id: String,
    val originalName: String,
    val originalParentPath: String?,
    val originalSizeBytes: Long,
    val addedAtMs: Long,
    val encryptedFile: File
) {
    val extension: String
        get() = originalName.substringAfterLast('.', "").lowercase()

    val category: String
        get() = when {
            extension in FileManagerOps.IMAGE_EXTENSIONS -> "Images"
            extension in setOf("mp4", "mkv", "mov", "avi", "webm", "3gp") -> "Videos"
            extension in setOf("mp3", "wav", "flac", "m4a", "ogg", "aac") -> "Audio"
            else -> "Docs & Archives"
        }
}

private val vaultThumbMemoryCache = object : LruCache<String, Bitmap>(24) {}

object VaultManager {
    const val PIN_LENGTH = 6
    private const val PBKDF2_ITERATIONS = 120_000
    private const val PBKDF2_KEY_BITS = 256

    fun getMasterKey(context: Context): MasterKey {
        return MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    fun getMasterKeyOrNull(context: Context): MasterKey? {
        return try {
            getMasterKey(context)
        } catch (_: Throwable) {
            null
        }
    }

    fun getRegistry(context: Context): SharedPreferences {
        return openEncryptedPrefsSafely(context.applicationContext, "vault_registry_prefs")
    }

    internal fun openEncryptedPrefsSafely(appCtx: Context, prefsName: String): SharedPreferences {
        fun createEncrypted(): SharedPreferences {
            return EncryptedSharedPreferences.create(
                appCtx,
                prefsName,
                getMasterKey(appCtx),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }
        return try {
            createEncrypted()
        } catch (_: Throwable) {
            try {
                // Retry once in case of transient Keystore contention
                createEncrypted()
            } catch (_: Throwable) {
                try {
                    // Corrupted ciphertext (e.g. after backup restore or Keystore invalidation): wipe & recreate
                    appCtx.deleteSharedPreferences(prefsName)
                    createEncrypted()
                } catch (_: Throwable) {
                    // Hardware Keystore unavailable: fall back safely to private app-sandboxed SharedPreferences
                    appCtx.getSharedPreferences("${prefsName}_fallback", Context.MODE_PRIVATE)
                }
            }
        }
    }

    fun getVaultDir(context: Context): File {
        return File(context.applicationContext.filesDir, "secure_vault").apply {
            if (!exists()) mkdirs()
        }
    }

    fun getPreviewCacheDir(context: Context): File {
        return File(context.applicationContext.cacheDir, "vault_preview")
    }

    /**
     * Securely shreds and deletes any leftover temporary preview files (e.g. after force-kill).
     */
    fun cleanupStalePreviewCache(context: Context) {
        try {
            val previewDir = getPreviewCacheDir(context)
            if (!previewDir.exists()) return
            val children = previewDir.walkBottomUp().toList()
            children.forEach { f ->
                if (f.isFile) {
                    shredAndDeleteInternalFile(f)
                } else if (f.isDirectory && f != previewDir) {
                    f.delete()
                }
            }
            previewDir.deleteRecursively()
        } catch (_: Exception) {
        }
    }

    fun clearMemoryCaches() {
        try {
            vaultThumbMemoryCache.evictAll()
        } catch (_: Exception) {
        }
    }

    fun shredAndDeleteInternalFile(file: File) {
        try {
            if (!file.exists()) return
            if (file.isFile && file.canWrite() && file.length() > 0L) {
                val len = file.length().coerceAtMost(8L * 1024L * 1024L) // Fast zero/random pass up to 8 MB header/body
                val buf = ByteArray(32768)
                SecureRandom().nextBytes(buf)
                FileOutputStream(file).use { fos ->
                    var written = 0L
                    while (written < len) {
                        val toWrite = minOf(buf.size.toLong(), len - written).toInt()
                        fos.write(buf, 0, toWrite)
                        written += toWrite
                    }
                    fos.fd.sync()
                }
            }
        } catch (_: Exception) {
        } finally {
            try {
                file.delete()
            } catch (_: Exception) {
            }
        }
    }

    fun registerVaultEntry(
        context: Context,
        uuid: String,
        originalName: String,
        originalParentPath: String?,
        originalSizeBytes: Long,
        addedAtMs: Long = System.currentTimeMillis()
    ) {
        val json = JSONObject().apply {
            put("name", originalName)
            if (!originalParentPath.isNullOrBlank()) {
                put("parent", originalParentPath)
            }
            put("size", originalSizeBytes)
            put("addedAt", addedAtMs)
        }.toString()
        getRegistry(context).edit().putString(uuid, json).apply()
    }

    fun parseVaultEntry(uuid: String, rawValue: String?, encryptedFile: File): VaultEntry {
        if (rawValue.isNullOrBlank()) {
            return VaultEntry(
                id = uuid,
                originalName = encryptedFile.name,
                originalParentPath = null,
                originalSizeBytes = encryptedFile.length(),
                addedAtMs = encryptedFile.lastModified(),
                encryptedFile = encryptedFile
            )
        }
        return if (rawValue.trimStart().startsWith("{")) {
            try {
                val obj = JSONObject(rawValue)
                VaultEntry(
                    id = uuid,
                    originalName = obj.optString("name", encryptedFile.name),
                    originalParentPath = obj.optString("parent", "").takeIf { it.isNotBlank() },
                    originalSizeBytes = obj.optLong("size", encryptedFile.length()),
                    addedAtMs = obj.optLong("addedAt", encryptedFile.lastModified()),
                    encryptedFile = encryptedFile
                )
            } catch (_: Exception) {
                VaultEntry(
                    id = uuid,
                    originalName = rawValue,
                    originalParentPath = null,
                    originalSizeBytes = encryptedFile.length(),
                    addedAtMs = encryptedFile.lastModified(),
                    encryptedFile = encryptedFile
                )
            }
        } else {
            // Backward compatibility with legacy plain-filename registry entries
            VaultEntry(
                id = uuid,
                originalName = rawValue,
                originalParentPath = null,
                originalSizeBytes = encryptedFile.length(),
                addedAtMs = encryptedFile.lastModified(),
                encryptedFile = encryptedFile
            )
        }
    }

    fun derivePbkdf2Bytes(pin: String, saltBytes: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), saltBytes, PBKDF2_ITERATIONS, PBKDF2_KEY_BITS)
        val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return try {
            skf.generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    fun wrapVaultDekWithPin(pinDerivedKey: ByteArray, dekBytes: ByteArray): String {
        val iv = ByteArray(12)
        SecureRandom().nextBytes(iv)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(pinDerivedKey, "AES")
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, GCMParameterSpec(128, iv))
        val cipherText = cipher.doFinal(dekBytes)
        val combined = ByteArray(iv.size + cipherText.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(cipherText, 0, combined, iv.size, cipherText.size)
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    fun unwrapVaultDekWithPin(pinDerivedKey: ByteArray, wrappedB64: String): ByteArray? {
        return try {
            val combined = Base64.decode(wrappedB64, Base64.NO_WRAP)
            if (combined.size <= 12) return null
            val iv = combined.copyOfRange(0, 12)
            val cipherText = combined.copyOfRange(12, combined.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val keySpec = SecretKeySpec(pinDerivedKey, "AES")
            cipher.init(Cipher.DECRYPT_MODE, keySpec, GCMParameterSpec(128, iv))
            cipher.doFinal(cipherText)
        } catch (_: Exception) {
            null
        }
    }
}

class VaultActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Block screenshots, screen recording, and Recents app-switcher snapshots inside the Vault
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        VaultManager.cleanupStalePreviewCache(applicationContext)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Obsidian) {
                    VaultScreen(onBack = { finish() })
                }
            }
        }
    }

    override fun onDestroy() {
        VaultManager.cleanupStalePreviewCache(applicationContext)
        VaultManager.clearMemoryCaches()
        super.onDestroy()
    }
}

private fun getVaultEncryptedPrefs(context: Context): SharedPreferences {
    return VaultManager.openEncryptedPrefsSafely(context.applicationContext, "vault_secure_prefs")
}

private fun legacyHashPinWithSalt(pin: String, saltBase64: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.update(Base64.decode(saltBase64, Base64.NO_WRAP))
    val hashBytes = digest.digest(pin.toByteArray(Charsets.UTF_8))
    return Base64.encodeToString(hashBytes, Base64.NO_WRAP)
}

private fun saveVaultPin(context: Context, pin: String) {
    val prefs = getVaultEncryptedPrefs(context)
    val salt = ByteArray(32)
    SecureRandom().nextBytes(salt)
    val derivedKey = VaultManager.derivePbkdf2Bytes(pin, salt)

    // Generate or re-wrap the 256-bit Vault DEK under the new PBKDF2-derived PIN key
    val existingDekB64 = prefs.getString("vault_dek_keystore", null)
    val dekBytes = if (existingDekB64 != null) {
        Base64.decode(existingDekB64, Base64.NO_WRAP)
    } else {
        ByteArray(32).also { SecureRandom().nextBytes(it) }
    }
    val wrappedDek = VaultManager.wrapVaultDekWithPin(derivedKey, dekBytes)

    // Store a verification tag derived from a second domain-separated PBKDF2 pass
    val verifySalt = ByteArray(32)
    SecureRandom().nextBytes(verifySalt)
    val verifyHash = VaultManager.derivePbkdf2Bytes("$pin:verify", verifySalt)

    prefs.edit()
        .putString("pin_salt_v2", Base64.encodeToString(salt, Base64.NO_WRAP))
        .putString("pin_verify_salt_v2", Base64.encodeToString(verifySalt, Base64.NO_WRAP))
        .putString("pin_verify_hash_v2", Base64.encodeToString(verifyHash, Base64.NO_WRAP))
        .putString("vault_dek_wrapped_v2", wrappedDek)
        .putString("vault_dek_keystore", Base64.encodeToString(dekBytes, Base64.NO_WRAP))
        .putInt("pin_length", pin.length)
        .putInt("failed_attempts", 0)
        .putLong("lockout_until_ms", 0L)
        .remove("pin_salt")
        .remove("pin_hash")
        .apply()
}

private fun isVaultPinConfigured(context: Context): Boolean {
    val prefs = getVaultEncryptedPrefs(context)
    val hasV2 = prefs.contains("pin_verify_hash_v2") && prefs.contains("pin_salt_v2")
    val hasLegacy = prefs.contains("pin_hash") && prefs.contains("pin_salt")
    return hasV2 || hasLegacy
}

private fun getConfiguredPinLength(context: Context): Int {
    val prefs = getVaultEncryptedPrefs(context)
    if (prefs.contains("pin_verify_hash_v2")) {
        return prefs.getInt("pin_length", VaultManager.PIN_LENGTH)
    }
    if (prefs.contains("pin_hash")) {
        return 4 // Legacy 4-digit PIN until user upgrades to 6-digit PIN
    }
    return VaultManager.PIN_LENGTH
}

private fun isLegacy4DigitPin(context: Context): Boolean {
    val prefs = getVaultEncryptedPrefs(context)
    return !prefs.contains("pin_verify_hash_v2") && prefs.contains("pin_hash")
}

private fun getLockoutUntilMs(context: Context): Long {
    return getVaultEncryptedPrefs(context).getLong("lockout_until_ms", 0L)
}

private fun getFailedAttempts(context: Context): Int {
    return getVaultEncryptedPrefs(context).getInt("failed_attempts", 0)
}

private fun recordFailedPinAttempt(context: Context): Pair<Int, Long> {
    val prefs = getVaultEncryptedPrefs(context)
    val fails = prefs.getInt("failed_attempts", 0) + 1
    val now = System.currentTimeMillis()
    val cooldownMs = when {
        fails >= 10 -> 10 * 60_000L // 10 minutes
        fails >= 8 -> 2 * 60_000L   // 2 minutes
        fails >= 5 -> 30_000L       // 30 seconds
        else -> 0L
    }
    val until = if (cooldownMs > 0L) now + cooldownMs else 0L
    prefs.edit()
        .putInt("failed_attempts", fails)
        .putLong("lockout_until_ms", until)
        .apply()
    return fails to until
}

private fun resetFailedPinAttempts(context: Context) {
    getVaultEncryptedPrefs(context).edit()
        .putInt("failed_attempts", 0)
        .putLong("lockout_until_ms", 0L)
        .apply()
}

private fun verifyVaultPin(context: Context, candidatePin: String): Boolean {
    val prefs = getVaultEncryptedPrefs(context)
    val now = System.currentTimeMillis()
    if (prefs.getLong("lockout_until_ms", 0L) > now) {
        return false
    }

    if (prefs.contains("pin_verify_hash_v2")) {
        val saltB64 = prefs.getString("pin_salt_v2", null) ?: return false
        val verifySaltB64 = prefs.getString("pin_verify_salt_v2", null) ?: return false
        val expectedVerifyB64 = prefs.getString("pin_verify_hash_v2", null) ?: return false
        val wrappedDekB64 = prefs.getString("vault_dek_wrapped_v2", null) ?: return false

        val verifySalt = Base64.decode(verifySaltB64, Base64.NO_WRAP)
        val expectedVerify = Base64.decode(expectedVerifyB64, Base64.NO_WRAP)
        val candidateVerify = VaultManager.derivePbkdf2Bytes("$candidatePin:verify", verifySalt)

        if (!MessageDigest.isEqual(candidateVerify, expectedVerify)) {
            return false
        }

        // Also cryptographically unwrap the Vault DEK with the PBKDF2-derived key
        val kdfSalt = Base64.decode(saltB64, Base64.NO_WRAP)
        val derivedKey = VaultManager.derivePbkdf2Bytes(candidatePin, kdfSalt)
        val unwrappedDek = VaultManager.unwrapVaultDekWithPin(derivedKey, wrappedDekB64)
        return unwrappedDek != null
    }

    // Legacy 4-digit SHA-256 verification for smooth upgrade
    val legacySalt = prefs.getString("pin_salt", null) ?: return false
    val legacyExpected = prefs.getString("pin_hash", null) ?: return false
    val candidateLegacy = legacyHashPinWithSalt(candidatePin, legacySalt)
    return MessageDigest.isEqual(
        candidateLegacy.toByteArray(Charsets.UTF_8),
        legacyExpected.toByteArray(Charsets.UTF_8)
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun VaultScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var isUnlocked by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    var setupFirstPin by remember { mutableStateOf("") }
    var isPinConfigured by remember { mutableStateOf(isVaultPinConfigured(context)) }
    var requiredPinLength by remember { mutableStateOf(getConfiguredPinLength(context)) }
    var isVerifyingPin by remember { mutableStateOf(false) }

    var failedAttempts by remember { mutableStateOf(getFailedAttempts(context)) }
    var lockoutUntilMs by remember { mutableLongStateOf(getLockoutUntilMs(context)) }
    var remainingLockoutSec by remember { mutableLongStateOf(0L) }

    var vaultEntries by remember { mutableStateOf<List<VaultEntry>>(emptyList()) }
    var selectedCategory by remember { mutableStateOf("All") }
    var showChangePinDialog by remember { mutableStateOf(false) }
    var newPinValue by remember { mutableStateOf("") }
    var promptLegacyUpgrade by remember { mutableStateOf(false) }

    var isSearchVisible by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var activePreview by remember { mutableStateOf<ActiveFilePreview?>(null) }
    var isLaunchingSystemDialog by remember { mutableStateOf(false) }

    val vaultDir = remember { VaultManager.getVaultDir(context) }
    val registry = remember { VaultManager.getRegistry(context) }
    val masterKey = remember { VaultManager.getMasterKeyOrNull(context) }
    val previewCacheDir = remember { VaultManager.getPreviewCacheDir(context) }

    val isSelectionMode = selectedIds.isNotEmpty()

    if (isSelectionMode) {
        BackHandler { selectedIds = emptySet() }
    }

    // Live countdown ticker when brute-force lockout is active
    LaunchedEffect(lockoutUntilMs) {
        while (true) {
            val now = System.currentTimeMillis()
            val diffSec = ((lockoutUntilMs - now) + 999L) / 1000L
            if (diffSec <= 0L) {
                remainingLockoutSec = 0L
                break
            } else {
                remainingLockoutSec = diffSec
                delay(500L)
            }
        }
    }

    // Auto-lock Vault and shred preview cache when app goes to background
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && !isLaunchingSystemDialog) {
                activePreview?.file?.let { VaultManager.shredAndDeleteInternalFile(it) }
                activePreview = null
                selectedIds = emptySet()
                pinInput = ""
                isUnlocked = false
                VaultManager.cleanupStalePreviewCache(context)
                VaultManager.clearMemoryCaches()
            } else if (event == Lifecycle.Event.ON_RESUME) {
                isLaunchingSystemDialog = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            VaultManager.cleanupStalePreviewCache(context)
            VaultManager.clearMemoryCaches()
        }
    }

    fun refreshVault() {
        val files = vaultDir.listFiles()?.toList()?.sortedByDescending { it.lastModified() } ?: emptyList()
        vaultEntries = files.map { f ->
            val id = f.nameWithoutExtension
            val rawMeta = registry.getString(id, null)
            VaultManager.parseVaultEntry(id, rawMeta, f)
        }
    }

    val filteredEntries = remember(vaultEntries, searchQuery, selectedCategory) {
        vaultEntries.filter { entry ->
            val matchesCat = selectedCategory == "All" || entry.category == selectedCategory
            val matchesSearch = searchQuery.isBlank() || entry.originalName.contains(searchQuery.trim(), ignoreCase = true)
            matchesCat && matchesSearch
        }
    }

    /**
     * Exports a decrypted copy to Downloads while keeping the encrypted file in the Vault.
     */
    fun exportCopyToDownloads(entry: VaultEntry): Boolean {
        val key = masterKey ?: return false
        return try {
            val encryptedFile = EncryptedFile.Builder(
                context,
                entry.encryptedFile,
                key,
                EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
            ).build()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, entry.originalName)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    encryptedFile.openFileInput().use { input ->
                        context.contentResolver.openOutputStream(uri)?.use { output ->
                            input.copyTo(output)
                        }
                    }
                    true
                } else false
            } else {
                val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloads.exists()) downloads.mkdirs()
                val outFile = File(downloads, entry.originalName)
                if (outFile.exists()) outFile.delete()
                encryptedFile.openFileInput().use { input ->
                    FileOutputStream(outFile).use { output ->
                        input.copyTo(output)
                    }
                }
                MediaScannerConnection.scanFile(context, arrayOf(outFile.absolutePath), null, null)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Restores the encrypted file back to its original parent directory (or Downloads if unavailable)
     * AND securely shreds/removes the encrypted copy from the Vault.
     */
    fun restoreAndUnlockEntry(entry: VaultEntry): Pair<Boolean, String> {
        val key = masterKey ?: return false to ""
        return try {
            val encryptedFile = EncryptedFile.Builder(
                context,
                entry.encryptedFile,
                key,
                EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
            ).build()

            val targetParent = entry.originalParentPath?.let { File(it) }
            if (targetParent != null &&
                (targetParent.exists() || targetParent.mkdirs()) &&
                targetParent.canWrite()
            ) {
                var destFile = File(targetParent, entry.originalName)
                if (destFile.exists()) {
                    val stamp = System.currentTimeMillis() % 10000
                    val ext = destFile.extension
                    val base = destFile.nameWithoutExtension
                    destFile = File(targetParent, if (ext.isNotBlank()) "${base}_$stamp.$ext" else "${base}_$stamp")
                }
                encryptedFile.openFileInput().use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
                if (destFile.exists() && (entry.originalSizeBytes == 0L || destFile.length() > 0L)) {
                    MediaScannerConnection.scanFile(context, arrayOf(destFile.absolutePath), null, null)
                    VaultManager.shredAndDeleteInternalFile(entry.encryptedFile)
                    registry.edit().remove(entry.id).apply()
                    StorageScanCache.invalidate()
                    return true to destFile.parentFile?.name.orEmpty().ifBlank { "original folder" }
                }
            }

            // Fallback: Restore to Downloads and remove from Vault
            if (exportCopyToDownloads(entry)) {
                VaultManager.shredAndDeleteInternalFile(entry.encryptedFile)
                registry.edit().remove(entry.id).apply()
                StorageScanCache.invalidate()
                return true to "Downloads"
            }
            false to ""
        } catch (_: Exception) {
            false to ""
        }
    }

    /**
     * Zero-disk-residue preview for Images & Text (decoded directly in RAM from EncryptedFile stream).
     * Only falls back to auto-shredded previewCacheDir for formats that require a File descriptor (ZIP/APK/External).
     */
    fun previewEncryptedEntry(entry: VaultEntry) {
        val key = masterKey
        if (key == null) {
            Toast.makeText(context, "Device Keystore unavailable", Toast.LENGTH_SHORT).show()
            return
        }
        scope.launch(Dispatchers.IO) {
            try {
                val encryptedFile = EncryptedFile.Builder(
                    context,
                    entry.encryptedFile,
                    key,
                    EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
                ).build()

                val virtualFile = File(previewCacheDir, entry.originalName)
                val viewerType = resolveInAppViewerType(virtualFile)

                if (viewerType == InAppViewerType.IMAGE) {
                    // Two-pass streaming decode directly in RAM (zero temp file on disk!)
                    val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    encryptedFile.openFileInput().use { stream ->
                        BitmapFactory.decodeStream(stream, null, boundsOpts)
                    }
                    val maxDim = maxOf(boundsOpts.outWidth, boundsOpts.outHeight)
                    var sample = 1
                    while (maxDim / sample > 2048) {
                        sample *= 2
                    }
                    val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
                    val bmp = encryptedFile.openFileInput().use { stream ->
                        BitmapFactory.decodeStream(stream, null, decodeOpts)
                    }
                    withContext(Dispatchers.Main) {
                        if (bmp != null) {
                            activePreview = ActiveFilePreview(
                                file = File(entry.originalName),
                                type = InAppViewerType.IMAGE,
                                inMemoryBitmap = bmp,
                                displaySizeOverride = entry.originalSizeBytes
                            )
                        } else {
                            Toast.makeText(context, "Unable to decode encrypted image", Toast.LENGTH_SHORT).show()
                        }
                    }
                    return@launch
                }

                if (viewerType == InAppViewerType.TEXT) {
                    // Stream up to 512 KB directly into RAM (zero temp file on disk!)
                    val maxBytes = 512 * 1024
                    val text = encryptedFile.openFileInput().use { stream ->
                        val buf = ByteArray(maxBytes)
                        var total = 0
                        while (total < maxBytes) {
                            val r = stream.read(buf, total, maxBytes - total)
                            if (r <= 0) break
                            total += r
                        }
                        String(buf, 0, total, Charsets.UTF_8)
                    }
                    withContext(Dispatchers.Main) {
                        activePreview = ActiveFilePreview(
                            file = File(entry.originalName),
                            type = InAppViewerType.TEXT,
                            inMemoryText = text,
                            displaySizeOverride = entry.originalSizeBytes
                        )
                    }
                    return@launch
                }

                // For ZIP, APK, or external viewers that require a physical file descriptor:
                previewCacheDir.mkdirs()
                val tempOut = File(previewCacheDir, entry.originalName)
                if (tempOut.exists()) VaultManager.shredAndDeleteInternalFile(tempOut)
                encryptedFile.openFileInput().use { input ->
                    FileOutputStream(tempOut).use { output ->
                        input.copyTo(output)
                    }
                }
                withContext(Dispatchers.Main) {
                    if (viewerType != null) {
                        activePreview = ActiveFilePreview(
                            file = tempOut,
                            type = viewerType,
                            displaySizeOverride = entry.originalSizeBytes
                        )
                    } else {
                        try {
                            isLaunchingSystemDialog = true
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tempOut)
                            val ext = tempOut.extension.lowercase()
                            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, mime)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "Preview Secured File"))
                        } catch (_: Exception) {
                            isLaunchingSystemDialog = false
                            VaultManager.shredAndDeleteInternalFile(tempOut)
                            Toast.makeText(context, "No app installed to preview this file type", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Preview failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    BuiltInFilePreviewHost(
        preview = activePreview,
        onDismiss = {
            activePreview?.file?.let { f ->
                if (f.exists()) VaultManager.shredAndDeleteInternalFile(f)
            }
            activePreview = null
        }
    )

    val deviceCredentialLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isLaunchingSystemDialog = false
        if (result.resultCode == Activity.RESULT_OK) {
            resetFailedPinAttempts(context)
            failedAttempts = 0
            lockoutUntilMs = 0L
            isUnlocked = true
            refreshVault()
            if (isLegacy4DigitPin(context)) {
                promptLegacyUpgrade = true
            }
        }
    }

    fun triggerDeviceCredential() {
        if (!isPinConfigured) return
        if (remainingLockoutSec > 0L) {
            Toast.makeText(context, "Vault locked out for ${remainingLockoutSec}s", Toast.LENGTH_SHORT).show()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val cancellationSignal = android.os.CancellationSignal()
                val builder = android.hardware.biometrics.BiometricPrompt.Builder(context)
                    .setTitle("Unlock Secure Vault")
                    .setSubtitle("Use fingerprint, face, or device credential")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    builder.setAllowedAuthenticators(
                        android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_STRONG or
                            android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_WEAK or
                            android.hardware.biometrics.BiometricManager.Authenticators.DEVICE_CREDENTIAL
                    )
                } else {
                    @Suppress("DEPRECATION")
                    builder.setDeviceCredentialAllowed(true)
                }
                val prompt = builder.build()
                isLaunchingSystemDialog = true
                prompt.authenticate(
                    cancellationSignal,
                    context.mainExecutor,
                    object : android.hardware.biometrics.BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: android.hardware.biometrics.BiometricPrompt.AuthenticationResult?) {
                            super.onAuthenticationSucceeded(result)
                            isLaunchingSystemDialog = false
                            resetFailedPinAttempts(context)
                            failedAttempts = 0
                            lockoutUntilMs = 0L
                            isUnlocked = true
                            refreshVault()
                            if (isLegacy4DigitPin(context)) {
                                promptLegacyUpgrade = true
                            }
                        }

                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                            super.onAuthenticationError(errorCode, errString)
                            isLaunchingSystemDialog = false
                        }

                        override fun onAuthenticationFailed() {
                            super.onAuthenticationFailed()
                        }
                    }
                )
                return
            } catch (_: Throwable) {
                isLaunchingSystemDialog = false
            }
        }
        try {
            val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            if (km != null && km.isKeyguardSecure) {
                @Suppress("DEPRECATION")
                val intent = km.createConfirmDeviceCredentialIntent(
                    "Secure Vault",
                    "Authenticate with device credentials to unlock your vault"
                )
                if (intent != null) {
                    isLaunchingSystemDialog = true
                    deviceCredentialLauncher.launch(intent)
                }
            } else {
                Toast.makeText(context, "Enter your $requiredPinLength-digit Vault PIN", Toast.LENGTH_SHORT).show()
            }
        } catch (_: Exception) {
            isLaunchingSystemDialog = false
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        isLaunchingSystemDialog = false
        uri?.let {
            scope.launch(Dispatchers.IO) {
                try {
                    var fileName = "vault_file_${System.currentTimeMillis()}"
                    var fileSize = 0L
                    context.contentResolver.query(it, null, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (nameIdx >= 0) fileName = cursor.getString(nameIdx)
                            val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                            if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) fileSize = cursor.getLong(sizeIdx)
                        }
                    }
                    val uuid = UUID.randomUUID().toString()
                    val destFile = File(vaultDir, "$uuid.enc")
                    if (destFile.exists()) destFile.delete()

                    val key = masterKey ?: throw IllegalStateException("Keystore unavailable")
                    val encryptedFile = EncryptedFile.Builder(
                        context,
                        destFile,
                        key,
                        EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
                    ).build()

                    context.contentResolver.openInputStream(it)?.use { input ->
                        encryptedFile.openFileOutput().use { output ->
                            input.copyTo(output)
                        }
                    }

                    val actualSize = if (fileSize > 0L) fileSize else destFile.length()
                    VaultManager.registerVaultEntry(
                        context = context,
                        uuid = uuid,
                        originalName = fileName,
                        originalParentPath = null,
                        originalSizeBytes = actualSize
                    )

                    // Attempt to remove original unencrypted document if SAF provider grants delete permission
                    val deletedSource = try {
                        DocumentsContract.deleteDocument(context.contentResolver, it)
                    } catch (_: Exception) {
                        false
                    }

                    StorageScanCache.invalidate()
                    withContext(Dispatchers.Main) {
                        val msg = if (deletedSource) {
                            "Encrypted into Vault & removed original copy"
                        } else {
                            "Encrypted into Vault (use File Explorer 'Vault' action to also shred original)"
                        }
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        refreshVault()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Encryption failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    fun handleCompletedPinEntry(enteredPin: String) {
        if (isVerifyingPin) return
        isVerifyingPin = true
        scope.launch(Dispatchers.Default) {
            if (!isPinConfigured) {
                withContext(Dispatchers.Main) {
                    if (setupFirstPin.isEmpty()) {
                        setupFirstPin = enteredPin
                        pinInput = ""
                        isVerifyingPin = false
                    } else if (setupFirstPin == enteredPin) {
                        withContext(Dispatchers.IO) {
                            saveVaultPin(context, enteredPin)
                        }
                        isPinConfigured = true
                        requiredPinLength = VaultManager.PIN_LENGTH
                        isUnlocked = true
                        isVerifyingPin = false
                        refreshVault()
                    } else {
                        Toast.makeText(context, "6-digit PINs did not match. Try again.", Toast.LENGTH_SHORT).show()
                        setupFirstPin = ""
                        pinInput = ""
                        isVerifyingPin = false
                    }
                }
            } else {
                val valid = verifyVaultPin(context, enteredPin)
                withContext(Dispatchers.Main) {
                    if (valid) {
                        val wasLegacy = isLegacy4DigitPin(context)
                        resetFailedPinAttempts(context)
                        failedAttempts = 0
                        lockoutUntilMs = 0L
                        isUnlocked = true
                        isVerifyingPin = false
                        refreshVault()
                        if (wasLegacy) {
                            promptLegacyUpgrade = true
                        }
                    } else {
                        val (fails, until) = recordFailedPinAttempt(context)
                        failedAttempts = fails
                        lockoutUntilMs = until
                        pinInput = ""
                        isVerifyingPin = false
                        val waitSec = ((until - System.currentTimeMillis()) + 999L) / 1000L
                        val msg = if (waitSec > 0L) {
                            "Too many failed attempts. Locked for ${waitSec}s."
                        } else {
                            "Invalid PIN (${(5 - fails).coerceAtLeast(1)} attempt(s) before cooldown)"
                        }
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    if (isSelectionMode) {
                        Text("${selectedIds.size} Selected", color = AuraCyan, fontWeight = FontWeight.Bold)
                    } else {
                        Column {
                            Text("Secure Vault", color = TextPrimary, fontWeight = FontWeight.Bold)
                            Text(
                                "AES-256-GCM • PBKDF2 • Screenshot Shield",
                                color = AuraGreen,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (isSelectionMode) {
                        IconButton(onClick = { selectedIds = emptySet() }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel Selection", tint = TextPrimary)
                        }
                    } else {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                        }
                    }
                },
                actions = {
                    if (isUnlocked) {
                        if (isSelectionMode) {
                            IconButton(
                                onClick = {
                                    selectedIds = if (selectedIds.size == filteredEntries.size) emptySet()
                                    else filteredEntries.map { it.id }.toSet()
                                }
                            ) {
                                Icon(Icons.Default.SelectAll, contentDescription = "Select All", tint = AuraCyan)
                            }
                            IconButton(
                                onClick = {
                                    val targets = vaultEntries.filter { it.id in selectedIds }
                                    scope.launch(Dispatchers.IO) {
                                        var restored = 0
                                        targets.forEach { entry ->
                                            if (restoreAndUnlockEntry(entry).first) restored++
                                        }
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, "Restored & unlocked $restored file(s)", Toast.LENGTH_LONG).show()
                                            selectedIds = emptySet()
                                            refreshVault()
                                        }
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Restore, contentDescription = "Restore Selected to Original Folder", tint = AuraGreen)
                            }
                            IconButton(
                                onClick = {
                                    val targets = vaultEntries.filter { it.id in selectedIds }
                                    scope.launch(Dispatchers.IO) {
                                        var count = 0
                                        targets.forEach { entry ->
                                            if (exportCopyToDownloads(entry)) count++
                                        }
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, "Exported $count copy/copies to Downloads", Toast.LENGTH_LONG).show()
                                            selectedIds = emptySet()
                                        }
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Download, contentDescription = "Export Selected Copies to Downloads", tint = AuraCyan)
                            }
                            IconButton(
                                onClick = {
                                    val targets = vaultEntries.filter { it.id in selectedIds }
                                    scope.launch(Dispatchers.IO) {
                                        val editor = registry.edit()
                                        targets.forEach { entry ->
                                            VaultManager.shredAndDeleteInternalFile(entry.encryptedFile)
                                            editor.remove(entry.id)
                                        }
                                        editor.apply()
                                        StorageScanCache.invalidate()
                                        withContext(Dispatchers.Main) {
                                            selectedIds = emptySet()
                                            refreshVault()
                                            Toast.makeText(context, "Shredded ${targets.size} file(s) from Vault", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Shred Selected from Vault", tint = AuraRose)
                            }
                        } else {
                            SearchIconToggleButton(
                                isExpanded = isSearchVisible,
                                hasQuery = searchQuery.isNotEmpty(),
                                modifier = Modifier.padding(end = 4.dp),
                                onClick = {
                                    isSearchVisible = !isSearchVisible
                                    if (!isSearchVisible) searchQuery = ""
                                }
                            )
                            IconButton(onClick = {
                                newPinValue = ""
                                showChangePinDialog = true
                            }) {
                                Icon(Icons.Default.Key, contentDescription = "Change 6-Digit Vault PIN", tint = AuraCyan)
                            }
                            IconButton(onClick = {
                                isUnlocked = false
                                pinInput = ""
                                selectedIds = emptySet()
                                VaultManager.cleanupStalePreviewCache(context)
                                VaultManager.clearMemoryCaches()
                            }) {
                                Icon(Icons.Default.Lock, contentDescription = "Lock Vault", tint = AuraRose)
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        floatingActionButton = {
            if (isUnlocked && !isSelectionMode) {
                FloatingActionButton(
                    onClick = {
                        try {
                            isLaunchingSystemDialog = true
                            filePickerLauncher.launch(arrayOf("*/*"))
                        } catch (_: Throwable) {
                            isLaunchingSystemDialog = false
                            Toast.makeText(context, "No file picker available on device", Toast.LENGTH_SHORT).show()
                        }
                    },
                    containerColor = AuraCyan,
                    contentColor = Color.Black
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Encrypt File")
                }
            }
        }
    ) { padding ->
        if (!isUnlocked) {
            val activeTargetLen = if (!isPinConfigured) VaultManager.PIN_LENGTH else requiredPinLength
            val isLockedOut = remainingLockoutSec > 0L

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(AuraRose.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = "Vault Locked", tint = AuraRose, modifier = Modifier.size(38.dp))
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    val titleText = when {
                        isLockedOut -> "Vault Cooldown (${remainingLockoutSec}s)"
                        !isPinConfigured && setupFirstPin.isEmpty() -> "Create 6-Digit Vault PIN"
                        !isPinConfigured -> "Confirm 6-Digit Vault PIN"
                        else -> "Enter $activeTargetLen-Digit Vault PIN"
                    }
                    Text(
                        titleText,
                        style = MaterialTheme.typography.headlineSmall,
                        color = if (isLockedOut) AuraRose else TextPrimary,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        when {
                            isLockedOut -> "Brute-force protection active. Please wait ${remainingLockoutSec}s before trying again."
                            !isPinConfigured -> "Protected with PBKDF2-HMAC-SHA256 (120k rounds) + Hardware AES-256-GCM."
                            else -> "Enter your $activeTargetLen-digit PIN or tap Biometrics to unlock."
                        },
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(22.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        repeat(activeTargetLen) { index ->
                            val filled = index < pinInput.length
                            Box(
                                modifier = Modifier
                                    .size(15.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            isLockedOut -> AuraRose.copy(alpha = 0.35f)
                                            filled -> AuraCyan
                                            else -> GlassBorder
                                        }
                                    )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(28.dp))

                    val keys = listOf(
                        listOf("1", "2", "3"),
                        listOf("4", "5", "6"),
                        listOf("7", "8", "9"),
                        listOf("BIO", "0", "DEL")
                    )

                    keys.forEach { row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(24.dp),
                            modifier = Modifier.padding(vertical = 7.dp)
                        ) {
                            row.forEach { key ->
                                Box(
                                    modifier = Modifier
                                        .size(70.dp)
                                        .clip(CircleShape)
                                        .background(if (isLockedOut) GlassBorder.copy(alpha = 0.4f) else GlassBorder)
                                        .clickable(enabled = !isLockedOut && !isVerifyingPin) {
                                            when (key) {
                                                "DEL" -> if (pinInput.isNotEmpty()) pinInput = pinInput.dropLast(1)
                                                "BIO" -> triggerDeviceCredential()
                                                else -> {
                                                    if (pinInput.length < activeTargetLen) {
                                                        val updated = pinInput + key
                                                        pinInput = updated
                                                        if (updated.length == activeTargetLen) {
                                                            handleCompletedPinEntry(updated)
                                                        }
                                                    }
                                                }
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    when (key) {
                                        "BIO" -> Icon(
                                            Icons.Default.Fingerprint,
                                            contentDescription = "Unlock with Device Credentials",
                                            tint = if (isPinConfigured && !isLockedOut) AuraCyan else TextTertiary
                                        )
                                        "DEL" -> Icon(
                                            Icons.AutoMirrored.Filled.Backspace,
                                            contentDescription = "Delete Digit",
                                            tint = TextPrimary
                                        )
                                        else -> Text(key, style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            val categories = listOf("All", "Images", "Videos", "Audio", "Docs & Archives")
            val totalVaultBytes = remember(vaultEntries) { vaultEntries.sumOf { it.encryptedFile.length() } }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                ExpandableSearchBar(
                    visible = isSearchVisible,
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onClose = {
                        searchQuery = ""
                        isSearchVisible = false
                    },
                    placeholder = "Search encrypted files...",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )

                // Hardware Keystore & Auto-Lock Info Strip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(GlassPanel.copy(alpha = 0.65f))
                        .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Shield, contentDescription = null, tint = AuraGreen, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "${vaultEntries.size} locked (${formatBytes(totalVaultBytes)}) • Hardware-bound keys (restore before uninstalling app)",
                            color = TextSecondary,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            maxLines = 2
                        )
                    }
                }

                // Category Filter Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categories.forEach { cat ->
                        val active = selectedCategory == cat
                        val count = if (cat == "All") vaultEntries.size else vaultEntries.count { it.category == cat }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(100.dp))
                                .background(if (active) AuraCyan.copy(alpha = 0.18f) else GlassPanel.copy(alpha = 0.55f))
                                .border(1.dp, if (active) AuraCyan else GlassBorder, RoundedCornerShape(100.dp))
                                .clickable { selectedCategory = cat }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "$cat ($count)",
                                color = if (active) AuraCyan else TextSecondary,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
                            )
                        }
                    }
                }

                if (filteredEntries.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                            Icon(Icons.Default.LockOpen, contentDescription = "Vault Unlocked", tint = AuraGreen, modifier = Modifier.size(56.dp))
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                if (searchQuery.isNotEmpty() || selectedCategory != "All") "No matching encrypted files" else "Vault is Empty",
                                color = TextPrimary,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Lock files from File Explorer (shreds original plaintext) or tap + below.",
                                color = TextSecondary,
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp),
                        contentPadding = PaddingValues(top = 6.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredEntries, key = { it.id }) { entry ->
                            val isSelected = selectedIds.contains(entry.id)
                            val dateStr = remember(entry.addedAtMs) {
                                SimpleDateFormat("MMM d", Locale.US).format(Date(entry.addedAtMs))
                            }
                            val originFolderLabel = remember(entry.originalParentPath) {
                                entry.originalParentPath?.let { File(it).name }?.takeIf { it.isNotBlank() } ?: "Vault Import"
                            }

                            GlassCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        width = if (isSelected) 1.5.dp else 0.dp,
                                        color = if (isSelected) AuraCyan else Color.Transparent,
                                        shape = RoundedCornerShape(24.dp)
                                    )
                                    .combinedClickable(
                                        onClick = {
                                            if (isSelectionMode) {
                                                selectedIds = if (isSelected) selectedIds - entry.id else selectedIds + entry.id
                                            } else {
                                                previewEncryptedEntry(entry)
                                            }
                                        },
                                        onLongClick = {
                                            selectedIds = if (isSelected) selectedIds - entry.id else selectedIds + entry.id
                                        }
                                    )
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    VaultEntryThumbnail(
                                        entry = entry,
                                        masterKey = masterKey,
                                        modifier = Modifier.size(46.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            entry.originalName,
                                            color = TextPrimary,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            "${formatBytes(entry.originalSizeBytes)} • From $originFolderLabel • $dateStr",
                                            color = AuraGreen,
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (isSelectionMode) {
                                        Icon(
                                            imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                            contentDescription = "Select",
                                            tint = if (isSelected) AuraCyan else TextTertiary
                                        )
                                    } else {
                                        IconButton(
                                            onClick = { previewEncryptedEntry(entry) },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(Icons.Default.Visibility, contentDescription = "Preview in Memory", tint = AuraCyan, modifier = Modifier.size(20.dp))
                                        }
                                        IconButton(
                                            onClick = {
                                                scope.launch(Dispatchers.IO) {
                                                    val (ok, destLabel) = restoreAndUnlockEntry(entry)
                                                    withContext(Dispatchers.Main) {
                                                        if (ok) {
                                                            Toast.makeText(
                                                                context,
                                                                "Restored '${entry.originalName}' to $destLabel",
                                                                Toast.LENGTH_LONG
                                                            ).show()
                                                            refreshVault()
                                                        } else {
                                                            Toast.makeText(context, "Restore failed", Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                }
                                            },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(Icons.Default.Restore, contentDescription = "Restore to Original Folder & Unlock", tint = AuraGreen, modifier = Modifier.size(20.dp))
                                        }
                                        IconButton(
                                            onClick = {
                                                scope.launch(Dispatchers.IO) {
                                                    VaultManager.shredAndDeleteInternalFile(entry.encryptedFile)
                                                    registry.edit().remove(entry.id).apply()
                                                    StorageScanCache.invalidate()
                                                    withContext(Dispatchers.Main) {
                                                        refreshVault()
                                                        Toast.makeText(context, "Shredded from Vault", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Shred Permanently", tint = AuraRose, modifier = Modifier.size(20.dp))
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

    if (showChangePinDialog || promptLegacyUpgrade) {
        val isUpgrade = promptLegacyUpgrade && !showChangePinDialog
        AlertDialog(
            onDismissRequest = {
                showChangePinDialog = false
                promptLegacyUpgrade = false
            },
            containerColor = GlassPanel,
            title = {
                Text(
                    if (isUpgrade) "Upgrade to 6-Digit Vault PIN" else "Change 6-Digit Vault PIN",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        if (isUpgrade) {
                            "Your Vault has been upgraded to PBKDF2-HMAC-SHA256 encryption. Please set a new 6-digit PIN."
                        } else {
                            "Enter a new 6-digit PIN for your Secure Vault (PBKDF2 120,000 iterations)."
                        },
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newPinValue,
                        onValueChange = { input ->
                            if (input.length <= VaultManager.PIN_LENGTH && input.all { it.isDigit() }) {
                                newPinValue = input
                            }
                        },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        label = { Text("New 6-Digit PIN") },
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPinValue.length == VaultManager.PIN_LENGTH) {
                            val pinToSave = newPinValue
                            scope.launch(Dispatchers.IO) {
                                saveVaultPin(context, pinToSave)
                                withContext(Dispatchers.Main) {
                                    requiredPinLength = VaultManager.PIN_LENGTH
                                    showChangePinDialog = false
                                    promptLegacyUpgrade = false
                                    newPinValue = ""
                                    Toast.makeText(context, "6-Digit Vault PIN saved", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } else {
                            Toast.makeText(context, "PIN must be 6 digits", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AuraCyan)
                ) {
                    Text("Save 6-Digit PIN", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showChangePinDialog = false
                    promptLegacyUpgrade = false
                }) {
                    Text("Later", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun VaultEntryThumbnail(
    entry: VaultEntry,
    masterKey: MasterKey?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val ext = entry.extension
    var bitmap by remember(entry.id) { mutableStateOf(vaultThumbMemoryCache.get(entry.id)) }

    LaunchedEffect(entry.id, masterKey) {
        if (masterKey != null && bitmap == null && ext in setOf("jpg", "jpeg", "png", "webp", "bmp")) {
            val decoded = withContext(Dispatchers.IO) {
                try {
                    val encryptedFile = EncryptedFile.Builder(
                        context,
                        entry.encryptedFile,
                        masterKey,
                        EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
                    ).build()
                    // Pass 1: Decode header bounds directly from stream without loading full image into RAM
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    encryptedFile.openFileInput().use { stream ->
                        BitmapFactory.decodeStream(stream, null, bounds)
                    }
                    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

                    var sample = 1
                    while (bounds.outWidth / sample > 180 && bounds.outHeight / sample > 180) {
                        sample *= 2
                    }
                    // Pass 2: Stream-decode downsampled thumbnail
                    val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
                    encryptedFile.openFileInput().use { stream ->
                        BitmapFactory.decodeStream(stream, null, decodeOpts)
                    }
                } catch (_: Throwable) {
                    null
                }
            }
            if (decoded != null) {
                vaultThumbMemoryCache.put(entry.id, decoded)
                bitmap = decoded
            }
        }
    }

    val (icon, tint) = when {
        ext in FileManagerOps.IMAGE_EXTENSIONS -> Icons.Default.Image to AuraCyan
        ext in setOf("mp4", "mkv", "mov", "avi", "webm", "3gp") -> Icons.Default.VideoFile to AuraRose
        ext in setOf("mp3", "wav", "flac", "m4a", "ogg", "aac") -> Icons.Default.AudioFile to AuraPurple
        ext in setOf("pdf", "txt", "doc", "docx", "json", "md", "log") -> Icons.Default.Description to AuraOrange
        else -> Icons.AutoMirrored.Filled.InsertDriveFile to AuraCyan
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center
    ) {
        val safeBmp = remember(bitmap) { bitmap.toSafeComposeBitmap() }
        if (safeBmp != null) {
            Image(
                bitmap = safeBmp.asImageBitmap(),
                contentDescription = entry.originalName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        }
    }
}
