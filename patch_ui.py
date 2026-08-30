import sys
import re

# 1. Remove from Screens.kt
content1 = open('app/src/main/java/com/example/ui/screens/Screens.kt').read()
content1 = re.sub(r'        // Deep Clean & Vault.*?Spacer\(modifier = Modifier\.height\(16\.dp\)\)\n\s*// Connectivity & Display', r'        // Connectivity & Display', content1, flags=re.DOTALL)
open('app/src/main/java/com/example/ui/screens/Screens.kt', 'w').write(content1)

# 2. Add to StorageAnalysisScreen.kt
content2 = open('app/src/main/java/com/example/ui/screens/StorageAnalysisScreen.kt').read()
injection = """                item {
                    Text("ADVANCED TOOLS", style = MaterialTheme.typography.labelSmall, color = TextTertiary, letterSpacing = 2.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        com.example.GlassCard(
                            modifier = Modifier.weight(1f).clickable {
                                context.startActivity(Intent(context, DuplicateFinderActivity::class.java))
                            }
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                                Icon(Icons.Default.FilterNone, contentDescription = "Duplicate Finder", tint = AuraCyan, modifier = Modifier.size(28.dp))
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("Duplicate\\nFinder", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp), color = TextPrimary, textAlign = TextAlign.Center)
                            }
                        }
                        
                        com.example.GlassCard(
                            modifier = Modifier.weight(1f).clickable {
                                context.startActivity(Intent(context, DeepCleanActivity::class.java))
                            }
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                                Icon(Icons.Default.CleaningServices, contentDescription = "Deep Clean", tint = AuraOrange, modifier = Modifier.size(28.dp))
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("Deep\\nClean", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp), color = TextPrimary, textAlign = TextAlign.Center)
                            }
                        }
                        
                        com.example.GlassCard(
                            modifier = Modifier.weight(1f).clickable {
                                context.startActivity(Intent(context, VaultActivity::class.java))
                            }
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                                Icon(Icons.Default.Lock, contentDescription = "Secure Vault", tint = AuraRose, modifier = Modifier.size(28.dp))
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("Secure\\nVault", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp), color = TextPrimary, textAlign = TextAlign.Center)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
                
                item {
                    Text("CATEGORIES","""
content2 = content2.replace('                item {\n                    Text("CATEGORIES",', injection)
imports2 = "import com.example.ui.screens.VaultActivity\nimport com.example.ui.screens.DuplicateFinderActivity\nimport com.example.ui.screens.DeepCleanActivity\nimport androidx.compose.material.icons.filled.FilterNone\nimport androidx.compose.material.icons.filled.CleaningServices\nimport androidx.compose.material.icons.filled.Lock\nimport androidx.compose.ui.text.style.TextAlign\n"
content2 = content2.replace("import androidx.compose.material.icons.filled.ArrowBack", imports2 + "import androidx.compose.material.icons.filled.ArrowBack")
open('app/src/main/java/com/example/ui/screens/StorageAnalysisScreen.kt', 'w').write(content2)

# 3. Modify DuplicateFinderActivity.kt
content3 = open('app/src/main/java/com/example/ui/screens/DuplicateFinderActivity.kt').read()
content3 = content3.replace('title = { Text("Deep Clean",', 'title = { Text("Duplicate Finder",')
content3 = content3.replace('import androidx.compose.material.icons.filled.ArrowBack', 'import androidx.compose.material.icons.filled.ArrowBack\nimport androidx.compose.material.icons.filled.FilterNone')
open('app/src/main/java/com/example/ui/screens/DuplicateFinderActivity.kt', 'w').write(content3)

print("UI Patched")
