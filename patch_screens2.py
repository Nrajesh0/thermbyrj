import sys

content = open('app/src/main/java/com/example/ui/screens/Screens.kt').read()

import_statement = "import com.example.ui.screens.VaultActivity\nimport com.example.ui.screens.DuplicateFinderActivity\nimport androidx.compose.material.icons.filled.Lock\nimport androidx.compose.material.icons.filled.CleaningServices\n"
if "VaultActivity" not in content:
    content = content.replace("package com.example.ui.screens", "package com.example.ui.screens\n" + import_statement)

injection = """
        // Deep Clean & Vault
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            com.example.GlassCard(
                modifier = Modifier.weight(1f).clickable {
                    context.startActivity(android.content.Intent(context, DuplicateFinderActivity::class.java))
                }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                    Icon(Icons.Default.CleaningServices, contentDescription = "Deep Clean", tint = AuraCyan, modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Deep Clean", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp), color = TextPrimary)
                }
            }
            
            com.example.GlassCard(
                modifier = Modifier.weight(1f).clickable {
                    context.startActivity(android.content.Intent(context, VaultActivity::class.java))
                }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                    Icon(Icons.Default.Lock, contentDescription = "Secure Vault", tint = AuraRose, modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Secure Vault", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp), color = TextPrimary)
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
"""

if "Deep Clean & Vault" not in content:
    parts = content.split('        // Connectivity & Display')
    if len(parts) > 1:
        new_content = parts[0] + injection + '        // Connectivity & Display\n' + parts[1]
        open('app/src/main/java/com/example/ui/screens/Screens.kt', 'w').write(new_content)
        print("Injected successfully")
    else:
        print("Failed to find Connectivity")
else:
    print("Already injected")
