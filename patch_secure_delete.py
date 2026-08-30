import sys

content = open('app/src/main/java/com/example/ui/screens/FileCategoryActivity.kt').read()

old_code = """            val secureRandom = SecureRandom()
            raf.seek(0)
            for (i in 0 until length step bufferSize.toLong()) {
                val writeLen = minOf(bufferSize.toLong(), length - i).toInt()
                secureRandom.nextBytes(buffer)
                raf.write(buffer, 0, writeLen)
            }
            raf.close()
        }
        
        // Obfuscate the filename before deletion
        val dummyFile = File(file.parent, UUID.randomUUID().toString())
        file.renameTo(dummyFile)
        dummyFile.delete()"""

new_code = """            val secureRandom = SecureRandom()
            raf.seek(0)
            for (i in 0 until length step bufferSize.toLong()) {
                val writeLen = minOf(bufferSize.toLong(), length - i).toInt()
                secureRandom.nextBytes(buffer)
                raf.write(buffer, 0, writeLen)
            }
            
            // Force hardware sync barrier
            raf.fd.sync()
            
            // Truncate file to 0 bytes to destroy logical size metadata
            raf.setLength(0)
            raf.close()
        }
        
        // Obfuscate timestamps to Epoch 1970
        file.setLastModified(0L)
        
        // Obfuscate the filename before deletion
        val dummyFile = File(file.parent, UUID.randomUUID().toString())
        file.renameTo(dummyFile)
        dummyFile.delete()"""

if old_code in content:
    new_content = content.replace(old_code, new_code)
    open('app/src/main/java/com/example/ui/screens/FileCategoryActivity.kt', 'w').write(new_content)
    print("Patched secureDeleteFile successfully")
else:
    print("Could not find old_code")
