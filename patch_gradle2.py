import re

with open("app/build.gradle.kts", "r") as f:
    content = f.read()

replacement = """    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      val releaseConfig = signingConfigs.getByName("release")
      if (releaseConfig.storeFile?.exists() == true) {
        signingConfig = releaseConfig
      } else {
        signingConfig = null
      }
    }"""

content = re.sub(r'    release \{.*?signingConfig = signingConfigs\.getByName\("release"\)\s*\}', replacement, content, flags=re.DOTALL)

with open("app/build.gradle.kts", "w") as f:
    f.write(content)

print("Gradle patched")
