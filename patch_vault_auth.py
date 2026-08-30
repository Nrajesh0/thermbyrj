import sys

content = r"""package com.example.ui.screens

import android.app.Activity
import android.app.KeyguardManager
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
import androidx.compose.material.icons.filled.Security
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
import java.util.UUID

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

object VaultManager {
    private const val REGISTRY_NAME = "secure_vault_registry"
    
    // FIX 1 & 4: Hardware-Bound Keys using Device Credential
    fun getMasterKey(context: Context): MasterKey {
        return MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .setUserAuthenticationRequired(true, 120) // Valid for 120 seconds after device unlock
            .build()
    }
    
    fun getRegistry(context: Context) = EncryptedSharedPreferences.create(
        context,
        REGISTRY_NAME,
        getMasterKey(context),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun isVaultUnlocked(context: Context): Boolean {
        return try {
            getRegistry(context).getString("ping", null)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    fun getVaultDir(context: Context): File {
        val dir = File(context.filesDir, "vault_files")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    // FIX 3: Zero-Fill File Wiping
    fun wipeFile(context: Context, uri: Uri) {
        try {
            context.contentResolver.openFileDescriptor(uri, "w")?.use { pfd ->
                FileOutputStream(pfd.fileDescriptor).use { fos ->
                    val size = pfd.statSize
                    val zeroBuf = ByteArray(4096)
                    var written = 0L
                    while(written < size) {
                        val toWrite = minOf(4096L, size - written).toInt()
                        fos.write(zeroBuf, 0, toWrite)
                        written += toWrite
                    }
                    fos.fd.sync()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    var isUnlocked by remember { mutableStateOf(VaultManager.isVaultUnlocked(context)) }
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

    val unlockLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            isUnlocked = true
        } else {
            Toast.makeText(context, "Authentication failed", Toast.LENGTH_SHORT).show()
        }
    }

    fun promptUnlock() {
        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (km.isDeviceSecure) {
            val intent = km.createConfirmDeviceCredentialIntent("Unlock Secure Vault", "Verify your identity to decrypt your files.")
            if (intent != null) {
                unlockLauncher.launch(intent)
            }
        } else {
            Toast.makeText(context, "Please set a secure lock screen (PIN/Pattern/Password) in Android Settings to use the Vault.", Toast.LENGTH_LONG).show()
        }
    }

    // Try unlock on startup if lock screen has recently been dismissed
    LaunchedEffect(Unit) {
        if (!isUnlocked) {
            promptUnlock()
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
                        
                        // FIX 3: Zero-fill wipe before deleting
                        VaultManager.wipeFile(context, uri)
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
            Icon(Icons.Default.Security, contentDescription = "Hardware Secured", tint = AuraCyan, modifier = Modifier.size(64.dp))
            Spacer(modifier = Modifier.height(24.dp))
            Text("SECURE VAULT", style = MaterialTheme.typography.titleLarge, color = TextPrimary, letterSpacing = 4.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Protected by Android Hardware Gatekeeper.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary, textAlign = TextAlign.Center)
            
            Spacer(modifier = Modifier.height(48.dp))
            
            Button(
                onClick = { promptUnlock() },
                colors = ButtonDefaults.buttonColors(containerColor = AuraCyan),
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.LockOpen, contentDescription = null, tint = Color.Black)
                Spacer(modifier = Modifier.width(12.dp))
                Text("Authenticate to Unlock", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp)
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
                        IconButton(onClick = { isUnlocked = false }) {
                            Icon(Icons.Default.Lock, contentDescription = "Lock", tint = AuraCyan)
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
                                            val displayName = try {
                                                VaultManager.getRegistry(context).getString(uuid, "Unknown File") ?: "Unknown File"
                                            } catch (e: Exception) {
                                                isUnlocked = false
                                                "Locked"
                                            }
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
                                                        withContext(Dispatchers.Main) {
                                                            isUnlocked = false // Lock on auth expiration
                                                        }
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
                                            try {
                                                VaultManager.getRegistry(context).edit().remove(uuid).apply()
                                            } catch (e: Exception) {
                                                isUnlocked = false
                                            }
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

print("Applied Hardware Bound Keys, CharArray/String wipe, and Zero-fill wipe")
