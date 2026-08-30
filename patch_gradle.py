import re

with open("app/build.gradle.kts", "r") as f:
    content = f.read()

content = content.replace(
    'keyAlias = "upload"',
    'keyAlias = System.getenv("KEY_ALIAS") ?: "upload"'
)

with open("app/build.gradle.kts", "w") as f:
    f.write(content)
print("Gradle patched")
