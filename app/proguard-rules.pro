# Preserve clean line numbers for crash stack traces while hiding source filenames
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Room Database entities, DAOs, and generated implementations
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao interface *
-keepclassmembers class com.example.data.** { *; }

# Keep Android components declared in AndroidManifest.xml
-keep public class com.example.MainActivity
-keep public class com.example.service.ThermalTrackerService
-keep public class com.example.service.FileShredService
-keep public class com.example.service.ThermalWidgetProvider
-keep public class com.example.service.GuardAlertActionReceiver
-keep public class com.example.service.BootCompletedReceiver
-keep public class com.example.ui.screens.** extends android.app.Activity

# Keep reflective hidden-API / AppOps calls safe from aggressive stripping
-dontwarn android.app.AppOpsManager**
-dontwarn android.os.storage.**
-dontwarn android.hardware.camera2.**
-dontwarn android.media.**
-dontwarn org.slf4j.**
