package com.example.ui.screens

import android.app.Activity
import android.app.KeyguardManager
import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Base64
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.GlassCard
import com.example.formatBytes
import com.example.ui.theme.AppTheme
import com.example.ui.theme.AuraCyan
import com.example.ui.theme.AuraRose
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.Obsidian
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class VaultActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
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

data class VaultFileEntry(
    val file: File,
    val uuid: String,
    val displayName: String,
    val sizeBytes: Long
)

object VaultManager {
    private const val REGISTRY_NAME = "secure_vault_registry_v2"
    private const val MASTER_KEY_ALIAS = "therm_vault_aes_key"
    private const val PIN_HASH_KEY = "__vault_pin_hash__"
    private const val PIN_SALT_KEY = "__vault_pin_salt__"

    fun getMasterKey(context: Context): MasterKey {
        return MasterKey.Builder(context, MASTER_KEY_ALIAS)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    fun getRegistry(context: Context): SharedPreferences {
        return EncryptedSharedPreferences.create(
            context,
            REGISTRY_NAME,
            getMasterKey(context),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun hasPinConfigured(context: Context): Boolean {
        return try {
            !getRegistry(context).getString(PIN_HASH_KEY, null).isNullOrEmpty()
        } catch (_: Exception) {
            false
        }
    }

    fun setPin(context: Context, pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = hashPin(pin, salt)
        getRegistry(context).edit()
            .putString(PIN_SALT_KEY, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(PIN_HASH_KEY, Base64.encodeToString(hash, Base64.NO_WRAP))
            .apply()
    }

    fun verifyPin(context: Context, pin: String): Boolean {
        return try {
            val prefs = getRegistry(context)
            val saltStr = prefs.getString(PIN_SALT_KEY, null) ?: return false
            val expectedHashStr = prefs.getString(PIN_HASH_KEY, null) ?: return false
            val salt = Base64.decode(saltStr, Base64.NO_WRAP)
            val expectedHash = Base64.decode(expectedHashStr, Base64.NO_WRAP)
            val actualHash = hashPin(pin, salt)
            actualHash.contentEquals(expectedHash)
        } catch (_: Exception) {
            false
        }
    }

    private fun hashPin(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 10_000, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    fun getVaultDir(context: Context): File {
        val dir = File(context.filesDir, "vault_files")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun wipeFileIfWritable(context: Context, uri: Uri) {
        try {
            context.contentResolver.openFileDescriptor(uri, "w")?.use { pfd ->
                FileOutputStream(pfd.fileDescriptor).use { fos ->
                    val size = pfd.statSize
                    if (size > 0) {
                        val zeroBuf = ByteArray(4096)
                        var written = 0L
                        while (written < size) {
                            val toWrite = minOf(4096L, size - written).toInt()
                            fos.write(zeroBuf, 0, toWrite)
                            written += toWrite
                        }
                        fos.fd.sync()
                    }
                }
            }
        } catch (_: Exception) {
            // Read-only providers do not permit overwriting; ignore gracefully
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val km = remember(context) { context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager }
    val isDeviceSecure = remember(km) { km.isDeviceSecure }

    var isUnlocked by remember { mutableStateOf(false) }
    var vaultEntries by remember { mutableStateOf<List<VaultFileEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var hasPin by remember { mutableStateOf(VaultManager.hasPinConfigured(context)) }
    var pinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }

    fun loadFiles() {
        coroutineScope.launch {
            vaultEntries = withContext(Dispatchers.IO) {
                val registry = try {
                    VaultManager.getRegistry(context)
                } catch (_: Exception) {
                    null
                }
                val files = VaultManager.getVaultDir(context).listFiles()?.toList().orEmpty()
                files.filter { it.isFile && it.name.endsWith(".enc") }.map { file ->
                    val uuid = file.name.removeSuffix(".enc")
                    val name = registry?.getString(uuid, null) ?: "Encrypted File"
                    VaultFileEntry(file, uuid, name, file.length())
                }
            }
        }
    }

    LaunchedEffect(isUnlocked) {
        if (isUnlocked) {
            loadFiles()
        }
    }

    val unlockLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            isUnlocked = true
        } else {
            Toast.makeText(context, "Authentication cancelled", Toast.LENGTH_SHORT).show()
        }
    }

    fun promptDeviceUnlock() {
        if (isDeviceSecure) {
            @Suppress("DEPRECATION")
            val intent = km.createConfirmDeviceCredentialIntent(
                "Unlock Secure Vault",
                "Verify your identity to access encrypted files."
            )
            if (intent != null) {
                unlockLauncher.launch(intent)
                return
            }
        }
        Toast.makeText(context, "Use your Vault PIN to unlock", Toast.LENGTH_SHORT).show()
    }

    LaunchedEffect(Unit) {
        if (!isUnlocked && isDeviceSecure) {
            promptDeviceUnlock()
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                isLoading = true
                val added = withContext(Dispatchers.IO) {
                    try {
                        var originalFileName = "encrypted_file_${System.currentTimeMillis()}"
                        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (cursor.moveToFirst() && nameIndex >= 0) {
                                originalFileName = cursor.getString(nameIndex) ?: originalFileName
                            }
                        }

                        val uuid = UUID.randomUUID().toString()
                        val vaultDir = VaultManager.getVaultDir(context)
                        val destFile = File(vaultDir, "$uuid.enc")

                        val encryptedFile = EncryptedFile.Builder(
                            context,
                            destFile,
                            VaultManager.getMasterKey(context),
                            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
                        ).build()

                        context.contentResolver.openInputStream(uri)?.use { inputStream ->
                            encryptedFile.openFileOutput().use { outputStream ->
                                inputStream.copyTo(outputStream)
                            }
                        } ?: return@withContext false

                        VaultManager.getRegistry(context).edit().putString(uuid, originalFileName).apply()

                        VaultManager.wipeFileIfWritable(context, uri)
                        try {
                            DocumentsContract.deleteDocument(context.contentResolver, uri)
                        } catch (_: Exception) {
                            try {
                                context.contentResolver.delete(uri, null, null)
                            } catch (_: Exception) {
                            }
                        }
                        true
                    } catch (e: Exception) {
                        e.printStackTrace()
                        false
                    }
                }
                Toast.makeText(
                    context,
                    if (added) "File encrypted in Vault" else "Failed to encrypt file",
                    Toast.LENGTH_SHORT
                ).show()
                loadFiles()
                isLoading = false
            }
        }
    }

    if (!isUnlocked) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.Security, contentDescription = "AES-256 Encrypted Vault", tint = AuraCyan, modifier = Modifier.size(64.dp))
            Spacer(modifier = Modifier.height(24.dp))
            Text("SECURE VAULT", style = MaterialTheme.typography.titleLarge, color = TextPrimary, letterSpacing = 4.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                if (isDeviceSecure) {
                    "Protected by Android device authentication & AES-256 encryption."
                } else if (hasPin) {
                    "Enter your Vault PIN to decrypt your files."
                } else {
                    "Create a 4+ digit Vault PIN to protect your encrypted files."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(36.dp))

            if (isDeviceSecure) {
                Button(
                    onClick = { promptDeviceUnlock() },
                    colors = ButtonDefaults.buttonColors(containerColor = AuraCyan),
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.LockOpen, contentDescription = null, tint = Color.Black)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Authenticate to Unlock", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            } else {
                OutlinedTextField(
                    value = pinInput,
                    onValueChange = { value ->
                        if (value.all { it.isDigit() } && value.length <= 8) {
                            pinInput = value
                            pinError = null
                        }
                    },
                    label = { Text(if (hasPin) "Vault PIN" else "New Vault PIN (4+ digits)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = AuraCyan,
                        unfocusedBorderColor = GlassBorder,
                        focusedLabelColor = AuraCyan,
                        unfocusedLabelColor = TextSecondary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                if (pinError != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(pinError!!, color = AuraRose, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = {
                        if (pinInput.length < 4) {
                            pinError = "PIN must be at least 4 digits"
                        } else if (!hasPin) {
                            VaultManager.setPin(context, pinInput)
                            hasPin = true
                            pinInput = ""
                            isUnlocked = true
                        } else if (VaultManager.verifyPin(context, pinInput)) {
                            pinInput = ""
                            isUnlocked = true
                        } else {
                            pinError = "Incorrect Vault PIN"
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AuraCyan),
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.LockOpen, contentDescription = null, tint = Color.Black)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        if (hasPin) "Unlock Vault" else "Set PIN & Unlock",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            TextButton(onClick = onBack) {
                Text("Cancel", color = TextSecondary)
            }
        }
    } else {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("Secure Vault", color = TextPrimary, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                        }
                    },
                    actions = {
                        IconButton(onClick = { isUnlocked = false }) {
                            Icon(Icons.Default.Lock, contentDescription = "Lock Vault", tint = AuraCyan)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    modifier = Modifier.navigationBarsPadding(),
                    onClick = { filePickerLauncher.launch("*/*") },
                    containerColor = AuraCyan
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add file to Vault", tint = Color.Black)
                }
            }
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AuraCyan)
                    }
                } else if (vaultEntries.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = TextSecondary.copy(alpha = 0.25f), modifier = Modifier.size(96.dp))
                            Spacer(modifier = Modifier.height(24.dp))
                            Text("Vault is Empty", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Files added here are encrypted with AES-256\nand stored privately inside app storage.",
                                textAlign = TextAlign.Center,
                                color = TextSecondary
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().navigationBarsPadding(),
                        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 100.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            Text("ENCRYPTED FILES", style = MaterialTheme.typography.labelSmall, color = TextTertiary, letterSpacing = 2.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        items(vaultEntries, key = { it.uuid }) { entry ->
                            GlassCard(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Icon(Icons.Default.InsertDriveFile, contentDescription = null, tint = AuraCyan, modifier = Modifier.size(28.dp))
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Column {
                                            Text(
                                                entry.displayName,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = TextPrimary,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(formatBytes(entry.sizeBytes), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                        }
                                    }

                                    Row {
                                        IconButton(onClick = {
                                            coroutineScope.launch {
                                                isLoading = true
                                                val exported = withContext(Dispatchers.IO) {
                                                    exportVaultFile(context, entry)
                                                }
                                                Toast.makeText(
                                                    context,
                                                    if (exported) "Exported to Downloads: ${entry.displayName}" else "Export failed",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                                isLoading = false
                                            }
                                        }) {
                                            Icon(Icons.Default.Download, contentDescription = "Export ${entry.displayName}", tint = TextPrimary)
                                        }
                                        IconButton(onClick = {
                                            coroutineScope.launch {
                                                withContext(Dispatchers.IO) {
                                                    entry.file.delete()
                                                    try {
                                                        VaultManager.getRegistry(context).edit().remove(entry.uuid).apply()
                                                    } catch (_: Exception) {
                                                    }
                                                }
                                                loadFiles()
                                            }
                                        }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete ${entry.displayName}", tint = AuraRose)
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

private fun exportVaultFile(context: Context, entry: VaultFileEntry): Boolean {
    return try {
        val encryptedFile = EncryptedFile.Builder(
            context,
            entry.file,
            VaultManager.getMasterKey(context),
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
        ).build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, entry.displayName)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val destUri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return false
            context.contentResolver.openOutputStream(destUri)?.use { outputStream ->
                encryptedFile.openFileInput().use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            } ?: return false
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            context.contentResolver.update(destUri, values, null, null)
            true
        } else {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            val destFile = File(downloadsDir, entry.displayName)
            encryptedFile.openFileInput().use { inputStream ->
                FileOutputStream(destFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            true
        }
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}
