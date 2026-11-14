package com.example.service

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import android.telecom.TelecomManager

enum class GuardAlertMode(val title: String, val subtitle: String) {
    SMART_COVERT(
        title = "Smart Guard",
        subtitle = "Alerts only for background access, locked screen, screen recording, or unknown callers. Quiet during calls & open apps."
    ),
    ALL_ACTIVITY(
        title = "All Activity",
        subtitle = "Alerts on every unignored Mic, Camera, Location, Clipboard, or Screen activation."
    ),
    SILENT_LOG(
        title = "Silent Log",
        subtitle = "No push notifications. Verified detections are recorded quietly in Timeline & Event Log."
    )
}

enum class GuardSensorType(val label: String) {
    MIC("Mic"),
    CAMERA("Camera"),
    LOCATION("Location"),
    CLIPBOARD("Clipboard"),
    SCREEN("Screen Rec")
}

data class GuardRulesConfig(
    val alertMode: GuardAlertMode = GuardAlertMode.SMART_COVERT,
    val debounceEnabled: Boolean = true,
    val silenceDuringCalls: Boolean = true,
    val quietForForegroundApps: Boolean = true,
    val pausePollingScreenOff: Boolean = true
)

data class TrustedAppRule(
    val packageName: String,
    val trustedSensors: Set<GuardSensorType>,
    val source: String = "Manual",
    val addedAtMs: Long = System.currentTimeMillis()
)

data class SnoozeRule(
    val targetKey: String, // packageName, "SENSOR:MIC", "SENSOR:CAMERA", or "ALL"
    val label: String,
    val expiresAtMs: Long
)

data class GuardAlertPayload(
    val title: String,
    val message: String,
    val packageName: String?,
    val sensorType: GuardSensorType
)

data class DiscoveredSystemSensorApp(
    val packageName: String,
    val appName: String,
    val roleLabel: String,
    val recommendedSensors: Set<GuardSensorType>
)

object GuardSystemAppDiscovery {

    /**
     * Dynamically discovers genuine built-in Phone Dialer, System Recorder, and System Camera
     * apps on any OEM phone using Android's TelecomManager + Intent resolution + FLAG_SYSTEM.
     * Avoids fragile hardcoded package name lists and rejects user-installed/sideloaded apps.
     */
    fun discoverSystemSensorApps(context: Context): List<DiscoveredSystemSensorApp> {
        val pm = context.packageManager
        val discovered = linkedMapOf<String, DiscoveredSystemSensorApp>()

        fun addIfSystem(pkg: String?, roleLabel: String, sensors: Set<GuardSensorType>, allowDefaultDialer: Boolean = false) {
            if (pkg.isNullOrBlank() || pkg == context.packageName) return
            try {
                val ai = pm.getApplicationInfo(pkg, 0)
                val isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                    (ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                if (!isSystem && !allowDefaultDialer) return
                val appName = pm.getApplicationLabel(ai).toString()
                val existing = discovered[pkg]
                if (existing == null) {
                    discovered[pkg] = DiscoveredSystemSensorApp(
                        packageName = pkg,
                        appName = appName,
                        roleLabel = roleLabel,
                        recommendedSensors = sensors
                    )
                } else {
                    discovered[pkg] = existing.copy(
                        recommendedSensors = existing.recommendedSensors + sensors
                    )
                }
            } catch (_: Exception) {
            }
        }

        // 1. Default Phone / Dialer (TelecomManager works on all OEMs & Android 6+)
        try {
            val tm = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            val defaultDialer = tm?.defaultDialerPackage
            addIfSystem(
                pkg = defaultDialer,
                roleLabel = "Default Phone Dialer",
                sensors = setOf(GuardSensorType.MIC),
                allowDefaultDialer = true
            )
        } catch (_: Exception) {
        }

        // 2. Built-in Sound / Voice Recorder (must carry OS FLAG_SYSTEM)
        try {
            val recordIntent = Intent(MediaStore.Audio.Media.RECORD_SOUND_ACTION)
            val handlers = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(recordIntent, PackageManager.ResolveInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(recordIntent, 0)
            }
            handlers.forEach { ri ->
                addIfSystem(
                    pkg = ri.activityInfo?.packageName,
                    roleLabel = "System Voice Recorder",
                    sensors = setOf(GuardSensorType.MIC)
                )
            }
        } catch (_: Exception) {
        }

        // 3. Built-in Camera App (must carry OS FLAG_SYSTEM)
        try {
            val camIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            val handlers = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(camIntent, PackageManager.ResolveInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(camIntent, 0)
            }
            handlers.forEach { ri ->
                addIfSystem(
                    pkg = ri.activityInfo?.packageName,
                    roleLabel = "System Camera",
                    sensors = setOf(GuardSensorType.CAMERA, GuardSensorType.MIC)
                )
            }
        } catch (_: Exception) {
        }

        return discovered.values.toList()
    }
}
