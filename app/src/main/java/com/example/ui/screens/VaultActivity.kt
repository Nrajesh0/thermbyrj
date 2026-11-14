package com.example.ui.screens

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Base64
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
import androidx.compose.material.icons.filled.SelectAll
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.GlassCard
import com.example.formatBytes
import com.example.service.StorageScanCache
import com.example.ui.components.ActiveFilePreview
import com.example.ui.components.BuiltInFilePreviewHost
import com.example.ui.components.ExpandableSearchBar
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID

data class VaultEntry(
    val id: String,
    val originalName: String,
    val encryptedFile: File
)

object VaultManager {
    fun getMasterKey(context: Context): MasterKey {
        return MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    fun getRegistry(context: Context): SharedPreferences {
        return try {
            EncryptedSharedPreferences.create(
                context,
                "vault_registry_prefs",
                getMasterKey(context),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (_: Exception) {
            context.getSharedPreferences("vault_registry_fallback", Context.MODE_PRIVATE)
        }
    }

    fun getVaultDir(context: Context): File {
        return File(context.filesDir, "secure_vault").apply { if (!exists()) mkdirs() }
    }
}

class VaultActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Obsidian) {
                    VaultScreen(onBack = { finish() })
                }
            }
        }
    }
}

private fun getVaultEncryptedPrefs(context: Context): SharedPreferences = try {
    EncryptedSharedPreferences.create(
        context,
        "vault_secure_prefs",
        VaultManager.getMasterKey(context),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
} catch (_: Exception) {
    context.getSharedPreferences("vault_prefs_fallback", Context.MODE_PRIVATE)
}

private fun hashPinWithSalt(pin: String, saltBase64: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.update(Base64.decode(saltBase64, Base64.NO_WRAP))
    val hashBytes = digest.digest(pin.toByteArray(Charsets.UTF_8))
    return Base64.encodeToString(hashBytes, Base64.NO_WRAP)
}

private fun saveVaultPin(context: Context, pin: String) {
    val salt = ByteArray(16)
    SecureRandom().nextBytes(salt)
    val saltB64 = Base64.encodeToString(salt, Base64.NO_WRAP)
    val hashB64 = hashPinWithSalt(pin, saltB64)
    getVaultEncryptedPrefs(context).edit()
        .putString("pin_salt", saltB64)
        .putString("pin_hash", hashB64)
        .apply()
}

private fun isVaultPinConfigured(context: Context): Boolean {
    val prefs = getVaultEncryptedPrefs(context)
    return prefs.contains("pin_hash") && prefs.contains("pin_salt")
}

private fun verifyVaultPin(context: Context, candidatePin: String): Boolean {
    val prefs = getVaultEncryptedPrefs(context)
    val salt = prefs.getString("pin_salt", null) ?: return false
    val expectedHash = prefs.getString("pin_hash", null) ?: return false
    return hashPinWithSalt(candidatePin, salt) == expectedHash
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun VaultScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isUnlocked by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    var setupFirstPin by remember { mutableStateOf("") }
    var isPinConfigured by remember { mutableStateOf(isVaultPinConfigured(context)) }
    var vaultEntries by remember { mutableStateOf<List<VaultEntry>>(emptyList()) }
    var showChangePinDialog by remember { mutableStateOf(false) }
    var newPinValue by remember { mutableStateOf("") }

    var isSearchVisible by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var activePreview by remember { mutableStateOf<ActiveFilePreview?>(null) }

    val vaultDir = remember { VaultManager.getVaultDir(context) }
    val registry = remember { VaultManager.getRegistry(context) }
    val masterKey = remember { VaultManager.getMasterKey(context) }
    val previewCacheDir = remember {
        File(context.cacheDir, "vault_preview").apply { if (!exists()) mkdirs() }
    }

    val isSelectionMode = selectedIds.isNotEmpty()

    if (isSelectionMode) {
        BackHandler { selectedIds = emptySet() }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                previewCacheDir.deleteRecursively()
            } catch (_: Exception) {
            }
        }
    }

    fun refreshVault() {
        val files = vaultDir.listFiles()?.toList()?.sortedByDescending { it.lastModified() } ?: emptyList()
        vaultEntries = files.map { f ->
            val id = f.nameWithoutExtension
            val origName = registry.getString(id, null) ?: f.name
            VaultEntry(id = id, originalName = origName, encryptedFile = f)
        }
    }

    val filteredEntries = remember(vaultEntries, searchQuery) {
        if (searchQuery.isBlank()) vaultEntries
        else vaultEntries.filter { it.originalName.contains(searchQuery.trim(), ignoreCase = true) }
    }

    fun decryptEntryToDownloads(entry: VaultEntry): Boolean {
        return try {
            val encryptedFile = EncryptedFile.Builder(
                context,
                entry.encryptedFile,
                masterKey,
                EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
            ).build()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = android.content.ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, "Decrypted_${entry.originalName}")
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
                val outFile = File(downloads, "Decrypted_${entry.originalName}")
                if (outFile.exists()) outFile.delete()
                encryptedFile.openFileInput().use { input ->
                    FileOutputStream(outFile).use { output ->
                        input.copyTo(output)
                    }
                }
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    fun previewEncryptedEntry(entry: VaultEntry) {
        scope.launch(Dispatchers.IO) {
            try {
                previewCacheDir.mkdirs()
                val tempOut = File(previewCacheDir, entry.originalName)
                if (tempOut.exists()) tempOut.delete()
                val encryptedFile = EncryptedFile.Builder(
                    context,
                    entry.encryptedFile,
                    masterKey,
                    EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
                ).build()
                encryptedFile.openFileInput().use { input ->
                    FileOutputStream(tempOut).use { output ->
                        input.copyTo(output)
                    }
                }
                withContext(Dispatchers.Main) {
                    val viewerType = resolveInAppViewerType(tempOut)
                    if (viewerType != null) {
                        activePreview = ActiveFilePreview(file = tempOut, type = viewerType)
                    } else {
                        try {
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tempOut)
                            val ext = tempOut.extension.lowercase()
                            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, mime)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "Preview Secured File"))
                        } catch (_: Exception) {
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

    // Built-in viewer host for encrypted previews
    BuiltInFilePreviewHost(
        preview = activePreview,
        onDismiss = {
            activePreview?.file?.delete()
            activePreview = null
        }
    )

    val deviceCredentialLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            isUnlocked = true
            refreshVault()
        }
    }

    fun triggerDeviceCredential() {
        if (!isPinConfigured) return
        try {
            val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            if (km != null && km.isKeyguardSecure) {
                @Suppress("DEPRECATION")
                val intent = km.createConfirmDeviceCredentialIntent(
                    "Secure Vault",
                    "Authenticate with device credentials to unlock your vault"
                )
                if (intent != null) {
                    deviceCredentialLauncher.launch(intent)
                }
            } else {
                Toast.makeText(context, "Enter your 4-digit Vault PIN", Toast.LENGTH_SHORT).show()
            }
        } catch (_: Exception) {
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch(Dispatchers.IO) {
                try {
                    var fileName = "vault_file_${System.currentTimeMillis()}"
                    context.contentResolver.query(it, null, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (idx >= 0) fileName = cursor.getString(idx)
                        }
                    }
                    val uuid = UUID.randomUUID().toString()
                    val destFile = File(vaultDir, "$uuid.enc")
                    if (destFile.exists()) destFile.delete()

                    val encryptedFile = EncryptedFile.Builder(
                        context,
                        destFile,
                        masterKey,
                        EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
                    ).build()

                    context.contentResolver.openInputStream(it)?.use { input ->
                        encryptedFile.openFileOutput().use { output ->
                            input.copyTo(output)
                        }
                    }
                    registry.edit().putString(uuid, fileName).apply()
                    StorageScanCache.invalidate()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "File encrypted with AES-256 GCM!", Toast.LENGTH_SHORT).show()
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

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    if (isSelectionMode) {
                        Text("${selectedIds.size} Selected", color = AuraCyan, fontWeight = FontWeight.Bold)
                    } else {
                        Text("Secure Vault (AES-256)", color = TextPrimary, fontWeight = FontWeight.Bold)
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
                                        var count = 0
                                        targets.forEach { entry ->
                                            if (decryptEntryToDownloads(entry)) count++
                                        }
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, "Decrypted $count files to Downloads", Toast.LENGTH_LONG).show()
                                            selectedIds = emptySet()
                                        }
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Download, contentDescription = "Decrypt Selected", tint = AuraGreen)
                            }
                            IconButton(
                                onClick = {
                                    val targets = vaultEntries.filter { it.id in selectedIds }
                                    val editor = registry.edit()
                                    targets.forEach { entry ->
                                        entry.encryptedFile.delete()
                                        editor.remove(entry.id)
                                    }
                                    editor.apply()
                                    StorageScanCache.invalidate()
                                    selectedIds = emptySet()
                                    refreshVault()
                                    Toast.makeText(context, "Deleted ${targets.size} files from Vault", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Selected", tint = AuraRose)
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
                                Icon(Icons.Default.Key, contentDescription = "Change Vault PIN", tint = AuraCyan)
                            }
                            IconButton(onClick = {
                                isUnlocked = false
                                pinInput = ""
                                selectedIds = emptySet()
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
                    onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                    containerColor = AuraCyan,
                    contentColor = Color.Black
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Encrypt File")
                }
            }
        }
    ) { padding ->
        if (!isUnlocked) {
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
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(AuraRose.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = "Vault Locked", tint = AuraRose, modifier = Modifier.size(40.dp))
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    val titleText = when {
                        !isPinConfigured && setupFirstPin.isEmpty() -> "Create 4-Digit Vault PIN"
                        !isPinConfigured -> "Confirm 4-Digit Vault PIN"
                        else -> "Enter Vault PIN"
                    }
                    Text(titleText, style = MaterialTheme.typography.headlineSmall, color = TextPrimary, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        if (!isPinConfigured) "Files are encrypted with hardware-backed AES-256 GCM."
                        else "Enter your 4-digit PIN to unlock encrypted files.",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(24.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        repeat(4) { index ->
                            val filled = index < pinInput.length
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(if (filled) AuraCyan else GlassBorder)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(32.dp))

                    val keys = listOf(
                        listOf("1", "2", "3"),
                        listOf("4", "5", "6"),
                        listOf("7", "8", "9"),
                        listOf("BIO", "0", "DEL")
                    )

                    keys.forEach { row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(24.dp),
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            row.forEach { key ->
                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(CircleShape)
                                        .background(GlassBorder)
                                        .clickable {
                                            when (key) {
                                                "DEL" -> if (pinInput.isNotEmpty()) pinInput = pinInput.dropLast(1)
                                                "BIO" -> triggerDeviceCredential()
                                                else -> {
                                                    if (pinInput.length < 4) {
                                                        pinInput += key
                                                        if (pinInput.length == 4) {
                                                            if (!isPinConfigured) {
                                                                if (setupFirstPin.isEmpty()) {
                                                                    setupFirstPin = pinInput
                                                                    pinInput = ""
                                                                } else if (setupFirstPin == pinInput) {
                                                                    saveVaultPin(context, pinInput)
                                                                    isPinConfigured = true
                                                                    isUnlocked = true
                                                                    refreshVault()
                                                                } else {
                                                                    Toast.makeText(context, "PINs did not match. Try again.", Toast.LENGTH_SHORT).show()
                                                                    setupFirstPin = ""
                                                                    pinInput = ""
                                                                }
                                                            } else {
                                                                if (verifyVaultPin(context, pinInput)) {
                                                                    isUnlocked = true
                                                                    refreshVault()
                                                                } else {
                                                                    Toast.makeText(context, "Invalid PIN", Toast.LENGTH_SHORT).show()
                                                                    pinInput = ""
                                                                }
                                                            }
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
                                            tint = if (isPinConfigured) AuraCyan else TextTertiary
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
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                )

                if (filteredEntries.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.LockOpen, contentDescription = "Vault Unlocked", tint = AuraGreen, modifier = Modifier.size(64.dp))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                if (searchQuery.isNotEmpty()) "No matching encrypted files" else "Vault is Empty",
                                color = TextPrimary,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text("Tap + or lock files from Explorer to encrypt them.", color = TextSecondary)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filteredEntries, key = { it.id }) { entry ->
                            val isSelected = selectedIds.contains(entry.id)
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
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            entry.originalName,
                                            color = TextPrimary,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            "${formatBytes(entry.encryptedFile.length())} · Encrypted · Tap to preview",
                                            color = AuraGreen,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                    if (isSelectionMode) {
                                        Icon(
                                            imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                            contentDescription = "Select",
                                            tint = if (isSelected) AuraCyan else TextTertiary
                                        )
                                    } else {
                                        IconButton(onClick = { previewEncryptedEntry(entry) }) {
                                            Icon(Icons.Default.Visibility, contentDescription = "Preview File", tint = AuraCyan)
                                        }
                                        IconButton(onClick = {
                                            scope.launch(Dispatchers.IO) {
                                                val ok = decryptEntryToDownloads(entry)
                                                withContext(Dispatchers.Main) {
                                                    if (ok) {
                                                        Toast.makeText(context, "Decrypted to Downloads/Decrypted_${entry.originalName}", Toast.LENGTH_LONG).show()
                                                    } else {
                                                        Toast.makeText(context, "Decryption failed", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            }
                                        }) {
                                            Icon(Icons.Default.Download, contentDescription = "Decrypt and Export", tint = AuraGreen)
                                        }
                                        IconButton(onClick = {
                                            entry.encryptedFile.delete()
                                            registry.edit().remove(entry.id).apply()
                                            StorageScanCache.invalidate()
                                            refreshVault()
                                        }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete Permanently", tint = AuraRose)
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

    if (showChangePinDialog) {
        AlertDialog(
            onDismissRequest = { showChangePinDialog = false },
            containerColor = GlassPanel,
            title = { Text("Change Vault PIN", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Enter a new 4-digit PIN for your Secure Vault.", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newPinValue,
                        onValueChange = { input ->
                            if (input.length <= 4 && input.all { it.isDigit() }) {
                                newPinValue = input
                            }
                        },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        label = { Text("New 4-Digit PIN") },
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPinValue.length == 4) {
                            saveVaultPin(context, newPinValue)
                            showChangePinDialog = false
                            Toast.makeText(context, "Vault PIN updated", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "PIN must be 4 digits", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AuraCyan)
                ) {
                    Text("Save PIN", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showChangePinDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun VaultEntryThumbnail(
    entry: VaultEntry,
    masterKey: MasterKey,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val ext = remember(entry.originalName) {
        entry.originalName.substringAfterLast('.', "").lowercase()
    }
    var bitmap by remember(entry.id) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(entry.id) {
        if (ext in setOf("jpg", "jpeg", "png", "webp", "bmp")) {
            bitmap = withContext(Dispatchers.IO) {
                try {
                    val encryptedFile = EncryptedFile.Builder(
                        context,
                        entry.encryptedFile,
                        masterKey,
                        EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
                    ).build()
                    val bytes = encryptedFile.openFileInput().use { it.readBytes() }
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    var sample = 1
                    while (bounds.outWidth / sample > 200 && bounds.outHeight / sample > 200) {
                        sample *= 2
                    }
                    val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)
                } catch (_: Exception) {
                    null
                }
            }
        }
    }

    val (icon, tint) = when {
        ext in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp") -> Icons.Default.Image to AuraCyan
        ext in setOf("mp4", "mkv", "mov", "avi", "webm") -> Icons.Default.VideoFile to AuraRose
        ext in setOf("mp3", "wav", "flac", "m4a", "ogg") -> Icons.Default.AudioFile to AuraPurple
        ext in setOf("pdf", "txt", "doc", "docx", "json", "md", "log") -> Icons.Default.Description to AuraOrange
        else -> Icons.AutoMirrored.Filled.InsertDriveFile to AuraCyan
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = entry.originalName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        }
    }
}
