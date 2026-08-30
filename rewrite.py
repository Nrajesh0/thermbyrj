import sys

content = r"""package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.OpenableColumns
import android.provider.DocumentsContract
import android.view.WindowManager
import android.widget.Toast
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import java.util.UUID

class VaultActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // PREDICTION 1: Screen Capture/Recent Apps Leak
        // Fix: FLAG_SECURE prevents screenshots and blanks the app in the recent apps switcher
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

object VaultManager {
    private const val PREFS_NAME = "secure_vault_prefs"
    private const val REGISTRY_NAME = "secure_vault_registry"
    private const val PIN_HASH_KEY = "vault_pin_hash"
    private const val PIN_SALT_KEY = "vault_pin_salt"
    private const val FAILED_ATTEMPTS_KEY = "vault_failed_attempts"
    private const val LOCKOUT_UNTIL_KEY = "vault_lockout_until"
    
    fun getMasterKey(context: Context): MasterKey {
        return MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }
    
    fun getEncryptedPrefs(context: Context) = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        getMasterKey(context),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun getRegistry(context: Context) = EncryptedSharedPreferences.create(
        context,
        REGISTRY_NAME,
        getMasterKey(context),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    
    // FIX 2: Upgraded from fast SHA-256 to slow PBKDF2 with 100,000 iterations to kill brute-force speed
    private fun hashPinPbkdf2(pin: String, salt: ByteArray): String {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 100000, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val hash = factory.generateSecret(spec).encoded
        return Base64.encodeToString(hash, Base64.NO_WRAP)
    }
    
    fun isPinSet(context: Context): Boolean {
        return getEncryptedPrefs(context).contains(PIN_HASH_KEY)
    }
    
    fun setPin(context: Context, pin: String) {
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)
        val saltBase64 = Base64.encodeToString(salt, Base64.NO_WRAP)
        val hash = hashPinPbkdf2(pin, salt)
        
        getEncryptedPrefs(context).edit()
            .putString(PIN_SALT_KEY, saltBase64)
            .putString(PIN_HASH_KEY, hash)
            .apply()
    }
    
    fun verifyPin(context: Context, pin: String): Boolean {
        val prefs = getEncryptedPrefs(context)
        val storedHash = prefs.getString(PIN_HASH_KEY, null) ?: return false
        val storedSaltBase64 = prefs.getString(PIN_SALT_KEY, null) ?: return false
        val salt = Base64.decode(storedSaltBase64, Base64.NO_WRAP)
        
        return storedHash == hashPinPbkdf2(pin, salt)
    }

    fun getLockoutTimeRemaining(context: Context): Long {
        val prefs = getEncryptedPrefs(context)
        val lockoutUntil = prefs.getLong(LOCKOUT_UNTIL_KEY, 0L)
        val remaining = lockoutUntil - System.currentTimeMillis()
        return if (remaining > 0) remaining else 0L
    }

    fun recordFailedAttempt(context: Context): Long {
        val prefs = getEncryptedPrefs(context)
        val attempts = prefs.getInt(FAILED_ATTEMPTS_KEY, 0) + 1
        prefs.edit().putInt(FAILED_ATTEMPTS_KEY, attempts).apply()
        
        if (attempts >= 5) {
            val minutes = when (attempts) {
                5 -> 1L
                6 -> 5L
                7 -> 15L
                8 -> 30L
                9 -> 60L
                else -> 60L * 24L // 24 hours for 10+ attempts
            }
            val penaltyMs = minutes * 60_000L
            val lockoutUntil = System.currentTimeMillis() + penaltyMs
            prefs.edit().putLong(LOCKOUT_UNTIL_KEY, lockoutUntil).apply()
            return penaltyMs
        }
        return 0L
    }

    fun resetFailedAttempts(context: Context) {
        getEncryptedPrefs(context).edit()
            .putInt(FAILED_ATTEMPTS_KEY, 0)
            .putLong(LOCKOUT_UNTIL_KEY, 0L)
            .apply()
    }
    
    fun getVaultDir(context: Context): File {
        val dir = File(context.filesDir, "vault_files")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    var isUnlocked by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf("") }
    
    var hasPinSet by remember { mutableStateOf(VaultManager.isPinSet(context)) }
    var isConfirmingPin by remember { mutableStateOf(false) }
    var firstPin by remember { mutableStateOf("") }
    
    var lockoutRemainingMs by remember { mutableStateOf(VaultManager.getLockoutTimeRemaining(context)) }
    
    var vaultFiles by remember { mutableStateOf(emptyList<File>()) }
    var isLoading by remember { mutableStateOf(false) }
    
    fun loadFiles() {
        vaultFiles = VaultManager.getVaultDir(context).listFiles()?.toList() ?: emptyList()
    }
    
    LaunchedEffect(isUnlocked) {
        if (isUnlocked) {
            loadFiles()
        }
    }

    LaunchedEffect(lockoutRemainingMs) {
        if (lockoutRemainingMs > 0) {
            while (lockoutRemainingMs > 0) {
                delay(1000)
                lockoutRemainingMs = VaultManager.getLockoutTimeRemaining(context)
            }
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                isLoading = true
                withContext(Dispatchers.IO) {
                    try {
                        val cursor = context.contentResolver.query(uri, null, null, null, null)
                        val nameIndex = cursor?.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        var originalFileName = "encrypted_file_${System.currentTimeMillis()}"
                        if (cursor != null && cursor.moveToFirst() && nameIndex != null && nameIndex >= 0) {
                            originalFileName = cursor.getString(nameIndex)
                        }
                        cursor?.close()

                        // FIX 3: Metadata Leakage. File names are now randomly generated UUIDs.
                        val uuid = UUID.randomUUID().toString()
                        VaultManager.getRegistry(context).edit().putString(uuid, originalFileName).apply()

                        val vaultDir = VaultManager.getVaultDir(context)
                        val destFile = File(vaultDir, "${uuid}.enc")
                        
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
                        }
                        
                        // PREDICTION 2: Source File Leakage
                        // Fix: Attempt to delete the original unencrypted file after copying it into the vault
                        try {
                            DocumentsContract.deleteDocument(context.contentResolver, uri)
                        } catch (e: Exception) {
                            try {
                                context.contentResolver.delete(uri, null, null)
                            } catch(e2: Exception) {}
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                loadFiles()
                isLoading = false
            }
        }
    }

    if (!isUnlocked) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.Lock, contentDescription = "Locked", tint = AuraRose, modifier = Modifier.size(64.dp))
            Spacer(modifier = Modifier.height(24.dp))
            Text("SECURE VAULT", style = MaterialTheme.typography.titleLarge, color = TextPrimary, letterSpacing = 4.sp)
            Spacer(modifier = Modifier.height(8.dp))
            
            val statusText = if (!hasPinSet) {
                if (isConfirmingPin) "Confirm your new PIN" else "Create a 6-digit PIN to secure the vault"
            } else {
                if (lockoutRemainingMs > 0) "Too many failed attempts." else "Enter PIN to decrypt vault"
            }
            
            Text(statusText, style = MaterialTheme.typography.bodyMedium, color = if (lockoutRemainingMs > 0) AuraRose else TextSecondary, textAlign = TextAlign.Center)
            
            Spacer(modifier = Modifier.height(48.dp))
            
            if (lockoutRemainingMs > 0) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Timer, contentDescription = "Locked Out", tint = AuraRose, modifier = Modifier.size(64.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("VAULT LOCKED", style = MaterialTheme.typography.titleMedium, color = AuraRose)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    val secondsTotal = lockoutRemainingMs / 1000
                    val minutes = secondsTotal / 60
                    val seconds = secondsTotal % 60
                    val timeStr = if (minutes > 0) "${minutes}m ${seconds}s" else "${seconds}s"
                    
                    Text("Try again in $timeStr", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                }
            } else {
                // PIN Dots
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    for (i in 0 until 6) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(if (i < pin.length) AuraCyan else GlassBorder)
                                .border(1.dp, if (i < pin.length) AuraCyan else TextSecondary.copy(alpha = 0.5f), CircleShape)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(48.dp))
                
                // Numpad
                val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "DEL")
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    for (row in keys.chunked(3)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            for (key in row) {
                                if (key.isEmpty()) {
                                    Spacer(modifier = Modifier.size(72.dp))
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(72.dp)
                                            .clip(CircleShape)
                                            .background(GlassPanel)
                                            .clickable {
                                                if (key == "DEL") {
                                                    if (pin.isNotEmpty()) pin = pin.dropLast(1)
                                                } else {
                                                    if (pin.length < 6) {
                                                        pin += key
                                                        if (pin.length == 6) {
                                                            if (!hasPinSet) {
                                                                if (!isConfirmingPin) {
                                                                    firstPin = pin
                                                                    pin = ""
                                                                    isConfirmingPin = true
                                                                } else {
                                                                    if (pin == firstPin) {
                                                                        VaultManager.setPin(context, pin)
                                                                        hasPinSet = true
                                                                        isUnlocked = true
                                                                    } else {
                                                                        pin = ""
                                                                        firstPin = ""
                                                                        isConfirmingPin = false
                                                                        Toast.makeText(context, "PINs did not match", Toast.LENGTH_SHORT).show()
                                                                    }
                                                                }
                                                            } else {
                                                                if (VaultManager.verifyPin(context, pin)) {
                                                                    VaultManager.resetFailedAttempts(context)
                                                                    isUnlocked = true
                                                                } else {
                                                                    pin = ""
                                                                    val penalty = VaultManager.recordFailedAttempt(context)
                                                                    if (penalty > 0) {
                                                                        lockoutRemainingMs = penalty
                                                                    } else {
                                                                        Toast.makeText(context, "Incorrect PIN", Toast.LENGTH_SHORT).show()
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(key, style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
                                    }
                                }
                            }
                        }
                    }
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
                        IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary) }
                    },
                    actions = {
                        IconButton(onClick = { isUnlocked = false; pin = "" }) {
                            Icon(Icons.Default.LockOpen, contentDescription = "Lock", tint = AuraCyan)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = { filePickerLauncher.launch("*/*") }, containerColor = AuraCyan) {
                    Icon(Icons.Default.Add, contentDescription = "Add", tint = Color.Black)
                }
            }
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AuraCyan)
                    }
                } else if (vaultFiles.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = TextSecondary.copy(alpha = 0.2f), modifier = Modifier.size(100.dp))
                            Spacer(modifier = Modifier.height(24.dp))
                            Text("Vault is Empty", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Files added here are encrypted with AES-256\nand hidden from other apps.", textAlign = TextAlign.Center, color = TextSecondary)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 100.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            Text("ENCRYPTED FILES", style = MaterialTheme.typography.labelSmall, color = TextTertiary, letterSpacing = 2.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        items(vaultFiles) { file ->
                            com.example.GlassCard(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Icon(Icons.Default.InsertDriveFile, contentDescription = null, tint = AuraCyan, modifier = Modifier.size(28.dp))
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Column {
                                            val uuid = file.name.removeSuffix(".enc")
                                            val displayName = VaultManager.getRegistry(context).getString(uuid, "Unknown File") ?: "Unknown File"
                                            Text(displayName, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, fontWeight = FontWeight.Bold, maxLines = 1)
                                            Text(com.example.formatBytes(file.length()), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                        }
                                    }
                                    
                                    Row {
                                        IconButton(onClick = {
                                            coroutineScope.launch {
                                                isLoading = true
                                                withContext(Dispatchers.IO) {
                                                    try {
                                                        val encryptedFile = EncryptedFile.Builder(
                                                            context,
                                                            file,
                                                            VaultManager.getMasterKey(context),
                                                            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
                                                        ).build()
                                                        
                                                        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                                                        val uuid = file.name.removeSuffix(".enc")
                                                        val originalName = VaultManager.getRegistry(context).getString(uuid, "DecryptedFile_${System.currentTimeMillis()}") ?: "DecryptedFile"
                                                        
                                                        val destFile = File(downloadsDir, originalName)
                                                        
                                                        encryptedFile.openFileInput().use { inputStream ->
                                                            FileOutputStream(destFile).use { outputStream ->
                                                                inputStream.copyTo(outputStream)
                                                            }
                                                        }
                                                        withContext(Dispatchers.Main) {
                                                            Toast.makeText(context, "Decrypted to Downloads!", Toast.LENGTH_SHORT).show()
                                                        }
                                                    } catch (e: Exception) {
                                                        e.printStackTrace()
                                                    }
                                                }
                                                isLoading = false
                                            }
                                        }) {
                                            Icon(Icons.Default.Download, contentDescription = "Export", tint = TextPrimary)
                                        }
                                        IconButton(onClick = {
                                            val uuid = file.name.removeSuffix(".enc")
                                            file.delete()
                                            VaultManager.getRegistry(context).edit().remove(uuid).apply()
                                            loadFiles()
                                        }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = AuraRose)
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
"""

with open('app/src/main/java/com/example/ui/screens/VaultActivity.kt', 'w') as f:
    f.write(content)

print("Rewritten VaultActivity.kt")
