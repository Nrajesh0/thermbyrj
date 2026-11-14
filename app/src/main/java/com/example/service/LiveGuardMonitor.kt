package com.example.service

import android.app.ActivityManager
import android.content.Context
import android.hardware.camera2.CameraManager
import android.hardware.display.DisplayManager
import android.media.AudioManager
import android.media.AudioRecordingConfiguration
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Display
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class LiveCaptureState(
    val isScreenRecordingOrCasting: Boolean = false,
    val activeCaptureDisplays: List<String> = emptyList(),
    val suspectedRecorderPackages: List<String> = emptyList(),
    val isMicActive: Boolean = false,
    val activeMicSessions: Int = 0,
    val isCameraActive: Boolean = false,
    val busyCameraIds: Set<String> = emptySet(),
    val isVpnOrProxyActive: Boolean = false,
    val lastUpdatedMs: Long = System.currentTimeMillis()
)

enum class AuditSeverity {
    SAFE, WARNING, DANGER
}

data class SecurityAuditVector(
    val id: String,
    val title: String,
    val plainSummary: String,
    val detailedAdvice: String,
    val severity: AuditSeverity,
    val statusBadge: String,
    val flaggedApps: List<AppPrivacyReport> = emptyList(),
    val extraDetails: List<String> = emptyList(),
    val settingsAction: String? = null,
    val actionButtonLabel: String = "Open Settings"
)

data class GuardEvent(
    val id: Long,
    val timestamp: Long,
    val type: String, // "SCREEN", "MIC", "CAMERA", "AUDIT"
    val title: String,
    val description: String,
    val severity: AuditSeverity
)

class LiveGuardMonitor private constructor(private val context: Context) {

    private val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val prefs = context.getSharedPreferences("guard_events_prefs", Context.MODE_PRIVATE)

    private val _captureState = MutableStateFlow(LiveCaptureState())
    val captureState: StateFlow<LiveCaptureState> = _captureState.asStateFlow()

    private val _events = MutableStateFlow<List<GuardEvent>>(emptyList())
    val events: StateFlow<List<GuardEvent>> = _events.asStateFlow()

    private val busyCameras = mutableSetOf<String>()
    private var isMonitoring = false
    private var lastScreenCaptureLogged = false
    private var lastMicLogged = false
    private var lastCameraLogged = false

    var onHighPriorityAlert: ((title: String, message: String) -> Unit)? = null

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {
            refreshDisplayCaptureState(triggerAlertIfNew = true)
        }

        override fun onDisplayRemoved(displayId: Int) {
            refreshDisplayCaptureState(triggerAlertIfNew = false)
        }

        override fun onDisplayChanged(displayId: Int) {
            refreshDisplayCaptureState(triggerAlertIfNew = true)
        }
    }

    private val audioRecordingCallback = object : AudioManager.AudioRecordingCallback() {
        override fun onRecordingConfigChanged(configs: List<AudioRecordingConfiguration>) {
            super.onRecordingConfigChanged(configs)
            val activeCount = configs.size
            val isMicNow = activeCount > 0
            _captureState.value = _captureState.value.copy(
                isMicActive = isMicNow,
                activeMicSessions = activeCount,
                lastUpdatedMs = System.currentTimeMillis()
            )
            if (isMicNow && !lastMicLogged) {
                lastMicLogged = true
                recordEvent(
                    type = "MIC",
                    title = "Microphone Active",
                    description = "$activeCount audio stream(s) recording.",
                    severity = AuditSeverity.WARNING
                )
                onHighPriorityAlert?.invoke(
                    "Microphone Active",
                    "An app is currently recording audio."
                )
            } else if (!isMicNow) {
                lastMicLogged = false
            }
        }
    }

    private val cameraCallback = object : CameraManager.AvailabilityCallback() {
        override fun onCameraAvailable(cameraId: String) {
            super.onCameraAvailable(cameraId)
            synchronized(busyCameras) {
                busyCameras.remove(cameraId)
            }
            updateCameraState()
        }

        override fun onCameraUnavailable(cameraId: String) {
            super.onCameraUnavailable(cameraId)
            synchronized(busyCameras) {
                busyCameras.add(cameraId)
            }
            updateCameraState()
        }
    }

    init {
        loadSavedEvents()
    }

    fun startMonitoring() {
        if (isMonitoring) {
            refreshAllStates()
            return
        }
        isMonitoring = true
        try {
            displayManager.registerDisplayListener(displayListener, mainHandler)
        } catch (_: Exception) {
        }
        try {
            audioManager.registerAudioRecordingCallback(audioRecordingCallback, mainHandler)
        } catch (_: Exception) {
        }
        try {
            cameraManager.registerAvailabilityCallback(cameraCallback, mainHandler)
        } catch (_: Exception) {
        }
        refreshAllStates()
    }

    fun refreshAllStates() {
        refreshDisplayCaptureState(triggerAlertIfNew = false)
        try {
            val configs = audioManager.activeRecordingConfigurations
            val vpnActive = checkActiveVpnOrProxy()
            _captureState.value = _captureState.value.copy(
                isMicActive = configs.isNotEmpty(),
                activeMicSessions = configs.size,
                isVpnOrProxyActive = vpnActive,
                lastUpdatedMs = System.currentTimeMillis()
            )
        } catch (_: Exception) {
        }
    }

    private fun checkActiveVpnOrProxy(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val net = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(net) ?: return false
            val hasVpn = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
            val proxy = cm.defaultProxy != null
            hasVpn || proxy
        } catch (_: Exception) {
            false
        }
    }

    private fun updateCameraState() {
        val currentBusy = synchronized(busyCameras) { busyCameras.toSet() }
        val isCamNow = currentBusy.isNotEmpty()
        _captureState.value = _captureState.value.copy(
            isCameraActive = isCamNow,
            busyCameraIds = currentBusy,
            lastUpdatedMs = System.currentTimeMillis()
        )
        if (isCamNow && !lastCameraLogged) {
            lastCameraLogged = true
            recordEvent(
                type = "CAMERA",
                title = "Camera Active",
                description = "Lens ${currentBusy.joinToString()} in use.",
                severity = AuditSeverity.WARNING
            )
        } else if (!isCamNow) {
            lastCameraLogged = false
        }
    }

    private fun refreshDisplayCaptureState(triggerAlertIfNew: Boolean) {
        try {
            val allDisplays = displayManager.displays ?: emptyArray()
            val virtualOrCaptureDisplays = allDisplays.filter { display ->
                display.displayId != Display.DEFAULT_DISPLAY && display.state != Display.STATE_OFF
            }.map { display ->
                display.name ?: "Virtual Display #${display.displayId}"
            }

            val suspectedPackages = detectRunningCapturePackages()
            val isCapturing = virtualOrCaptureDisplays.isNotEmpty()

            _captureState.value = _captureState.value.copy(
                isScreenRecordingOrCasting = isCapturing,
                activeCaptureDisplays = virtualOrCaptureDisplays,
                suspectedRecorderPackages = suspectedPackages,
                lastUpdatedMs = System.currentTimeMillis()
            )

            if (isCapturing && !lastScreenCaptureLogged) {
                lastScreenCaptureLogged = true
                val detail = virtualOrCaptureDisplays.joinToString(", ")
                recordEvent(
                    type = "SCREEN",
                    title = "Screen Capture Detected",
                    description = "Active display: $detail",
                    severity = AuditSeverity.DANGER
                )
                if (triggerAlertIfNew) {
                    onHighPriorityAlert?.invoke(
                        "Screen Recording Detected!",
                        "Your screen is being captured ($detail)."
                    )
                }
            } else if (!isCapturing) {
                lastScreenCaptureLogged = false
            }
        } catch (_: Exception) {
        }
    }

    @Suppress("DEPRECATION")
    private fun detectRunningCapturePackages(): List<String> {
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val services = am.getRunningServices(100) ?: emptyList()
            services.mapNotNull { srv ->
                val cls = srv.service.className.lowercase()
                val pkg = srv.service.packageName
                if (pkg != context.packageName &&
                    (cls.contains("record") || cls.contains("projection") || cls.contains("capture") || cls.contains("cast"))
                ) {
                    pkg
                } else {
                    null
                }
            }.distinct()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun triggerSimulatedGuardTest() {
        recordEvent(
            type = "SCREEN",
            title = "Guard Test Passed",
            description = "Real-time screen & mic alerts are working.",
            severity = AuditSeverity.SAFE
        )
        onHighPriorityAlert?.invoke(
            "Guard Test Passed",
            "Screen & Microphone background alerts are active."
        )
    }

    fun runDeepSecurityAudit(includeSystemApps: Boolean = false): List<SecurityAuditVector> {
        refreshAllStates()
        val live = _captureState.value
        val privacySummary = PrivacyAnalyzer.analyzeDevicePrivacy(context, includeSystemApps = includeSystemApps)
        val allApps = privacySummary.appReports

        // 1. Screen Recording / Mirroring
        val screenVector = if (live.isScreenRecordingOrCasting) {
            SecurityAuditVector(
                id = "screen_recording",
                title = "Screen Recording",
                plainSummary = "Screen is being recorded or mirrored now",
                detailedAdvice = "Stop unknown screen recordings or TV casts from your notification shade immediately.",
                severity = AuditSeverity.DANGER,
                statusBadge = "RECORDING",
                extraDetails = live.activeCaptureDisplays + live.suspectedRecorderPackages.map { "Service: $it" },
                settingsAction = Settings.ACTION_CAST_SETTINGS,
                actionButtonLabel = "Cast Settings"
            )
        } else {
            SecurityAuditVector(
                id = "screen_recording",
                title = "Screen Recording",
                plainSummary = "No active screen capture or mirroring",
                detailedAdvice = "Monitors DisplayManager for hidden virtual displays used by screen recorders.",
                severity = AuditSeverity.SAFE,
                statusBadge = "SAFE"
            )
        }

        // 2. Live Mic & Camera
        val micCamActive = live.isMicActive || live.isCameraActive
        val micCamApps = allApps.filter { app ->
            app.grantedPermissions.any { it.category == PermissionCategory.CAMERA_MIC }
        }
        val micCamVector = if (micCamActive) {
            SecurityAuditVector(
                id = "live_mic_cam",
                title = "Mic & Camera Live",
                plainSummary = "Sensor is actively recording right now",
                detailedAdvice = "If you aren't on a call, an app is using your mic or camera in the background.",
                severity = AuditSeverity.DANGER,
                statusBadge = "ACTIVE NOW",
                flaggedApps = micCamApps.take(8),
                settingsAction = Settings.ACTION_PRIVACY_SETTINGS,
                actionButtonLabel = "Privacy Controls"
            )
        } else {
            SecurityAuditVector(
                id = "live_mic_cam",
                title = "Mic & Camera Live",
                plainSummary = "Idle • ${micCamApps.size} apps have permission",
                detailedAdvice = "Revoke Camera or Mic access for apps that don't need voice or video.",
                severity = AuditSeverity.SAFE,
                statusBadge = "IDLE",
                flaggedApps = micCamApps.take(6)
            )
        }

        // 3. Screen Overlays
        val overlayApps = allApps.filter { it.hasOverlayPermission }
        val overlayVector = SecurityAuditVector(
            id = "screen_overlays",
            title = "Screen Overlays",
            plainSummary = if (overlayApps.isEmpty()) "No apps draw over your screen" else "${overlayApps.size} app(s) can draw over other apps",
            detailedAdvice = "Turn off 'Display over other apps' for apps that don't need floating windows.",
            severity = if (overlayApps.isNotEmpty()) AuditSeverity.WARNING else AuditSeverity.SAFE,
            statusBadge = if (overlayApps.isEmpty()) "SAFE" else "${overlayApps.size} APPS",
            flaggedApps = overlayApps,
            settingsAction = Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            actionButtonLabel = "Overlay Settings"
        )

        // 4. Accessibility Screen-Readers
        val accessibilityApps = allApps.filter { it.hasAccessibilityAccess }
        val enabledRawServices = PrivacyAnalyzer.getEnabledAccessibilityPackages(context)
            .filter { it != context.packageName }
        val accessCount = maxOf(accessibilityApps.size, enabledRawServices.size)
        val accessibilityVector = SecurityAuditVector(
            id = "accessibility_monitors",
            title = "Screen Readers",
            plainSummary = if (accessCount == 0) "No apps are reading screen text" else "$accessCount service(s) can read all screen text",
            detailedAdvice = "Spyware often uses Accessibility to read chats and passwords. Disable unknown services.",
            severity = if (accessCount > 0) AuditSeverity.DANGER else AuditSeverity.SAFE,
            statusBadge = if (accessCount == 0) "SAFE" else "$accessCount ACTIVE",
            flaggedApps = accessibilityApps,
            extraDetails = enabledRawServices.map { "Service: $it" },
            settingsAction = Settings.ACTION_ACCESSIBILITY_SETTINGS,
            actionButtonLabel = "Accessibility"
        )

        // 5. Notification Spy Listeners
        val notifApps = allApps.filter { it.hasNotificationListener }
        val rawNotifListeners = PrivacyAnalyzer.getEnabledNotificationListeners(context)
            .filter { it != context.packageName && !it.startsWith("com.android.") && !it.startsWith("com.google.android.") }
        val notifCount = maxOf(notifApps.size, rawNotifListeners.size)
        val notifVector = SecurityAuditVector(
            id = "notification_listeners",
            title = "Notification Readers",
            plainSummary = if (notifCount == 0) "No apps intercepting notifications" else "$notifCount app(s) can read all incoming messages",
            detailedAdvice = "Notification Listeners can silently read incoming WhatsApp, SMS, and 2FA codes.",
            severity = if (notifCount > 0) AuditSeverity.WARNING else AuditSeverity.SAFE,
            statusBadge = if (notifCount == 0) "SAFE" else "$notifCount APPS",
            flaggedApps = notifApps,
            extraDetails = rawNotifListeners.map { "Listener: $it" },
            settingsAction = "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS",
            actionButtonLabel = "Notification Access"
        )

        // 6. Device Admins
        val adminApps = allApps.filter { it.isDeviceAdmin }
        val adminVector = SecurityAuditVector(
            id = "device_admins",
            title = "Device Admins",
            plainSummary = if (adminApps.isEmpty()) "No third-party admin apps" else "${adminApps.size} app(s) have Admin lock control",
            detailedAdvice = "Device Admins can lock your phone and block uninstallation until revoked.",
            severity = if (adminApps.isNotEmpty()) AuditSeverity.WARNING else AuditSeverity.SAFE,
            statusBadge = if (adminApps.isEmpty()) "SAFE" else "${adminApps.size} ADMIN",
            flaggedApps = adminApps,
            settingsAction = Settings.ACTION_SECURITY_SETTINGS,
            actionButtonLabel = "Security Settings"
        )

        // 7. Custom Keyboards & VPN / Proxy
        val untrustedKeyboards = PrivacyAnalyzer.getEnabledInputMethods(context)
        val vpnActive = live.isVpnOrProxyActive
        val inputFlags = buildList {
            if (untrustedKeyboards.isNotEmpty()) add("${untrustedKeyboards.size} third-party keyboard(s) enabled")
            if (vpnActive) add("Active VPN or Network Proxy tunnel detected")
        }
        val inputVector = SecurityAuditVector(
            id = "keyboards_vpn",
            title = "Keyboards & Network",
            plainSummary = if (inputFlags.isEmpty()) "System keyboard & direct network" else inputFlags.joinToString(" • "),
            detailedAdvice = "Untrusted keyboards can log keystrokes, and unknown VPNs can inspect traffic.",
            severity = if (untrustedKeyboards.isNotEmpty()) AuditSeverity.WARNING else AuditSeverity.SAFE,
            statusBadge = if (inputFlags.isEmpty()) "SAFE" else "REVIEW",
            extraDetails = untrustedKeyboards.map { "Keyboard: $it" } + if (vpnActive) listOf("VPN / Proxy active") else emptyList(),
            settingsAction = Settings.ACTION_INPUT_METHOD_SETTINGS,
            actionButtonLabel = "Keyboard Settings"
        )

        // 8. Sideloaded Apps & USB Debugging
        val sideloadedApps = allApps.filter { it.isSideloaded }
        val adbEnabled = try {
            Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1
        } catch (_: Exception) {
            false
        }
        val sysFlags = buildList {
            if (adbEnabled) add("USB Debugging (ADB) is ON")
            if (sideloadedApps.isNotEmpty()) add("${sideloadedApps.size} sideloaded APK(s)")
        }
        val sideloadVector = SecurityAuditVector(
            id = "sideload_adb",
            title = "Sideloads & USB Debug",
            plainSummary = if (sysFlags.isEmpty()) "Verified store apps & ADB off" else sysFlags.joinToString(" • "),
            detailedAdvice = "Turn off USB Debugging when not needed and review apps installed outside Play Store.",
            severity = if (adbEnabled) AuditSeverity.DANGER else if (sideloadedApps.isNotEmpty()) AuditSeverity.WARNING else AuditSeverity.SAFE,
            statusBadge = if (sysFlags.isEmpty()) "SAFE" else "REVIEW",
            flaggedApps = sideloadedApps,
            extraDetails = sysFlags,
            settingsAction = Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS,
            actionButtonLabel = "Developer Options"
        )

        val allVectors = listOf(
            screenVector,
            micCamVector,
            overlayVector,
            accessibilityVector,
            notifVector,
            adminVector,
            inputVector,
            sideloadVector
        )

        recordEvent(
            type = "AUDIT",
            title = "Security Scan Finished",
            description = "${allVectors.count { it.severity != AuditSeverity.SAFE }} item(s) flagged for review.",
            severity = AuditSeverity.SAFE
        )

        return allVectors
    }

    fun recordEvent(type: String, title: String, description: String, severity: AuditSeverity) {
        val current = _events.value.toMutableList()
        val newEvent = GuardEvent(
            id = System.currentTimeMillis(),
            timestamp = System.currentTimeMillis(),
            type = type,
            title = title,
            description = description,
            severity = severity
        )
        current.add(0, newEvent)
        val trimmed = current.take(40)
        _events.value = trimmed
        saveEvents(trimmed)
    }

    fun clearEvents() {
        _events.value = emptyList()
        prefs.edit().remove("saved_events_json").apply()
    }

    private fun saveEvents(list: List<GuardEvent>) {
        try {
            val arr = JSONArray()
            for (ev in list) {
                val obj = JSONObject().apply {
                    put("id", ev.id)
                    put("timestamp", ev.timestamp)
                    put("type", ev.type)
                    put("title", ev.title)
                    put("description", ev.description)
                    put("severity", ev.severity.name)
                }
                arr.put(obj)
            }
            prefs.edit().putString("saved_events_json", arr.toString()).apply()
        } catch (_: Exception) {
        }
    }

    private fun loadSavedEvents() {
        try {
            val raw = prefs.getString("saved_events_json", null)
            if (raw.isNullOrBlank()) {
                val initial = GuardEvent(
                    id = System.currentTimeMillis(),
                    timestamp = System.currentTimeMillis(),
                    type = "AUDIT",
                    title = "Guard Active",
                    description = "Screen, Mic & Camera monitors ready.",
                    severity = AuditSeverity.SAFE
                )
                _events.value = listOf(initial)
                return
            }
            val arr = JSONArray(raw)
            val loaded = mutableListOf<GuardEvent>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val sev = try {
                    AuditSeverity.valueOf(obj.optString("severity", "SAFE"))
                } catch (_: Exception) {
                    AuditSeverity.SAFE
                }
                loaded.add(
                    GuardEvent(
                        id = obj.optLong("id"),
                        timestamp = obj.optLong("timestamp"),
                        type = obj.optString("type", "AUDIT"),
                        title = obj.optString("title", "Guard Event"),
                        description = obj.optString("description", ""),
                        severity = sev
                    )
                )
            }
            _events.value = loaded
        } catch (_: Exception) {
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: LiveGuardMonitor? = null

        fun getInstance(context: Context): LiveGuardMonitor {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: LiveGuardMonitor(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
