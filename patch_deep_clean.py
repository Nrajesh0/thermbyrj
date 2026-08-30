import sys

with open('app/src/main/java/com/example/ui/screens/DeepCleanActivity.kt', 'r') as f:
    content = f.read()

# We need to replace the category initializations
old_cats = """                val apkCat = JunkCategory("APK Files", "Obsolete app installers", Icons.Default.Android)
                val logCat = JunkCategory("Log Files", "System and app log outputs", Icons.Default.Article)
                val thumbCat = JunkCategory("Thumbnail Caches", "Leftover image previews", Icons.Default.Image)
                val emptyDirCat = JunkCategory("Empty Folders", "Leftover empty directories", Icons.Default.FolderOpen)
                val corpseCat = JunkCategory("Corpse Data", "Leftovers from uninstalled apps", Icons.Default.DeleteSweep)
                val tempCat = JunkCategory("Temp & Backup", "Temporary, .bak, and .old fragments", Icons.Default.Restore)"""

new_cats = """                // AUDIT FIX: Removed APKs, Empty Folders, and User Backups from auto-deletion for strict safety.
                val logCat = JunkCategory("Log Files", "System and app log outputs", Icons.AutoMirrored.Filled.Article)
                val thumbCat = JunkCategory("Thumbnail Caches", "Leftover image previews", Icons.Default.Image)
                val corpseCat = JunkCategory("Corpse Data", "Leftovers from uninstalled apps", Icons.Default.DeleteSweep)
                val tempCat = JunkCategory("Temp Fragments", "Temporary system fragments", Icons.Default.Restore)"""
content = content.replace(old_cats, new_cats)

# The list assignment
old_list = "categories = listOf(apkCat, thumbCat, logCat, corpseCat, tempCat, emptyDirCat).filter { it.files.isNotEmpty() }"
new_list = "categories = listOf(thumbCat, logCat, corpseCat, tempCat).filter { it.files.isNotEmpty() }"
content = content.replace(old_list, new_list)

# The scanDirectory logic
old_scan = """                        if (depth == 1 && dir.name == "LOST.DIR") {
                            for (f in files) {
                                tempCat.files.add(f)
                                tempCat.totalSize += if (f.isFile) f.length() else getFolderSize(f)
                            }
                            return
                        }
                        
                        if (files.isEmpty() && dir.absolutePath != root.absolutePath) {
                            emptyDirCat.files.add(dir)
                            return
                        }
                        
                        for (file in files) {
                            if (file.isDirectory) {
                                if (file.name == ".thumbnails" || file.name == ".cache") {
                                    file.walkTopDown().filter { it.isFile }.forEach {
                                        thumbCat.files.add(it)
                                        thumbCat.totalSize += it.length()
                                    }
                                } else {
                                    scanDirectory(file, depth + 1)
                                }
                            } else {
                                val lower = file.name.lowercase()
                                if (lower.endsWith(".apk")) {
                                    apkCat.files.add(file)
                                    apkCat.totalSize += file.length()
                                } else if (lower.endsWith(".log")) {
                                    logCat.files.add(file)
                                    logCat.totalSize += file.length()
                                } else if (lower.endsWith(".tmp") || lower.endsWith(".bak") || lower.endsWith(".old")) {
                                    tempCat.files.add(file)
                                    tempCat.totalSize += file.length()
                                }
                            }
                        }"""

new_scan = """                        // Check for LOST.DIR (Fsck fragments)
                        if (depth == 1 && dir.name == "LOST.DIR") {
                            for (f in files) {
                                tempCat.files.add(f)
                                tempCat.totalSize += if (f.isFile) f.length() else getFolderSize(f)
                            }
                            return
                        }
                        
                        // AUDIT FIX: Skip user document and download folders entirely to prevent accidental deletions
                        val protectedDirs = setOf("DCIM", "Pictures", "Music", "Movies", "Documents", "Download", "Podcasts", "Audiobooks")
                        if (depth == 1 && protectedDirs.contains(dir.name)) {
                            // Only safely scan inside them if we're looking for thumbnails, skip general temp/log scans
                            val thumbDir = File(dir, ".thumbnails")
                            if (thumbDir.exists() && thumbDir.isDirectory) {
                                thumbDir.walkTopDown().filter { it.isFile }.forEach {
                                    thumbCat.files.add(it)
                                    thumbCat.totalSize += it.length()
                                }
                            }
                            return // Do not drill deeper into protected folders
                        }
                        
                        // AUDIT FIX: Removed Empty Folder auto-deletion to preserve user folder structures.
                        
                        for (file in files) {
                            if (file.isDirectory) {
                                if (file.name == ".thumbnails" || file.name == ".cache") {
                                    file.walkTopDown().filter { it.isFile }.forEach {
                                        thumbCat.files.add(it)
                                        thumbCat.totalSize += it.length()
                                    }
                                } else {
                                    scanDirectory(file, depth + 1)
                                }
                            } else {
                                val lower = file.name.lowercase()
                                // AUDIT FIX: Removed .apk, .bak, and .old from junk targeting. 
                                // These are often legitimate user backups or downloaded installers.
                                if (lower.endsWith(".log")) {
                                    logCat.files.add(file)
                                    logCat.totalSize += file.length()
                                } else if (lower.endsWith(".tmp")) {
                                    tempCat.files.add(file)
                                    tempCat.totalSize += file.length()
                                }
                            }
                        }"""

content = content.replace(old_scan, new_scan)

# Also fix the deprecated icons while we are at it
content = content.replace("Icons.Default.Article", "Icons.AutoMirrored.Filled.Article")
content = content.replace("Icons.Default.ArrowBack", "Icons.AutoMirrored.Filled.ArrowBack")

with open('app/src/main/java/com/example/ui/screens/DeepCleanActivity.kt', 'w') as f:
    f.write(content)

print("Patch applied.")
