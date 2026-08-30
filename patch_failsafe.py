import sys

with open('app/src/main/java/com/example/ui/screens/DeepCleanActivity.kt', 'r') as f:
    content = f.read()

old_delete = """                                    cat.files.toList().forEach { file ->
                                        try {
                                            if (file.isDirectory) file.deleteRecursively() else file.delete()
                                        } catch (e: Exception) {}
                                    }"""

new_delete = """                                    cat.files.toList().forEach { file ->
                                        try {
                                            val path = file.absolutePath
                                            // FINAL FAILSAFE: Physically prevent deletion of anything outside strictly defined disposable zones
                                            val isSafeZone = path.contains("/.thumbnails") || 
                                                             path.contains("/.cache") || 
                                                             path.contains("/Android/data/") || 
                                                             path.contains("/Android/obb/") || 
                                                             path.contains("/Android/media/") || 
                                                             path.contains("/LOST.DIR") || 
                                                             path.endsWith(".log", ignoreCase = true) || 
                                                             path.endsWith(".tmp", ignoreCase = true)
                                                             
                                            val isExternalStorage = path.startsWith(Environment.getExternalStorageDirectory().absolutePath)

                                            if (isSafeZone && isExternalStorage) {
                                                if (file.isDirectory) file.deleteRecursively() else file.delete()
                                            }
                                        } catch (e: Exception) {}
                                    }"""

if old_delete in content:
    content = content.replace(old_delete, new_delete)
    with open('app/src/main/java/com/example/ui/screens/DeepCleanActivity.kt', 'w') as f:
        f.write(content)
    print("Failsafe applied successfully.")
else:
    print("Could not find the target deletion block.")
