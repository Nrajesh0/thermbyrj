import sys

content = open('app/src/main/java/com/example/ui/screens/Screens.kt').read()

imports = """import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.CleaningServices
"""
content = content.replace("import androidx.compose.material.icons.outlined.Speed", imports + "import androidx.compose.material.icons.outlined.Speed")

vault_deep_clean = """        Spacer(modifier = Modifier.height(16.dp))
        
        // Deep Clean & Vault
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            GlassCard(
                modifier = Modifier.weight(1f).clickable {
                    // context.startActivity(Intent(context, DuplicateFinderActivity::class.java))
                }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.CleaningServices, contentDescription = "Deep Clean", tint = AuraCyan, modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Deep Clean", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp), color = TextPrimary)
                }
            }
            
            GlassCard(
                modifier = Modifier.weight(1f).clickable {
                    // context.startActivity(Intent(context, VaultActivity::class.java))
                }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Lock, contentDescription = "Secure Vault", tint = AuraRose, modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Secure Vault", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp), color = TextPrimary)
                }
            }
        }
"""
content = content.replace("        Spacer(modifier = Modifier.height(16.dp))\n        // Connectivity & Display", vault_deep_clean + "        Spacer(modifier = Modifier.height(16.dp))\n        // Connectivity & Display")

open('app/src/main/java/com/example/ui/screens/Screens.kt', 'w').write(content)
print("Screens.kt patched")
