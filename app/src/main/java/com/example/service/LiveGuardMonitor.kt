package com.example.service

import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.KeyguardManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.hardware.camera2.CameraManager
import android.hardware.display.DisplayManager
import android.media.AudioManager
import android.media.AudioRecordingConfiguration
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.MediaStore
import android.provider.Settings
import android.view.Display
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class LiveCaptureState(
    val isScreenRecordingOrCasting: Boolean = false,
    val activeCaptureDisplays: List<String> = emptyList(),
    val suspectedRecorderPackages: List<String> = emptyList(),
    val isMicActive: Boolean = false,
    val activeMicSessions: Int = 0,
    val activeMicApps: List<String> = emptyList(),
    val isCameraActive: Boolean = false,
    val busyCameraIds: Set<String> = emptySet(),
    val activeCameraApps: List<String> = emptyList(),
    val isBackgroundLocationActive: Boolean = false,
    val activeLocationApps: List<String> = emptyList(),
    val isClipboardAccessedRecently: Boolean = false,
    val recentClipboardApps: List<String> = emptyList(),
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
    val type: String, // "SCREEN", "MIC", "CAMERA", "LOCATION", "CLIPBOARD", "AUDIT"
    val title: String,
    val description: String,
    val severity: AuditSeverity,
    val packageName: String? = null
)

class LiveGuardMonitor private constructor(private val context: Context) {

    private val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val bgExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
    private val prefs = context.getSharedPreferences("guard_events_prefs", Context.MODE_PRIVATE)

    private val _captureState = MutableStateFlow(LiveCaptureState())
    val captureState: StateFlow<LiveCaptureState> = _captureState.asStateFlow()

    private val _events = MutableStateFlow<List<GuardEvent>>(emptyList())
    val events: StateFlow<List<GuardEvent>> = _events.asStateFlow()

    private val _ignoredPackages = MutableStateFlow<Set<String>>(emptySet())
    val ignoredPackages: StateFlow<Set<String>> = _ignoredPackages.asStateFlow()

    private val _rulesConfig = MutableStateFlow(GuardRulesConfig())
    val rulesConfig: StateFlow<GuardRulesConfig> = _rulesConfig.asStateFlow()

    private val _trustedRules = MutableStateFlow<Map<String, TrustedAppRule>>(emptyMap())
    val trustedRules: StateFlow<Map<String, TrustedAppRule>> = _trustedRules.asStateFlow()

    private val _activeSnoozes = MutableStateFlow<List<SnoozeRule>>(emptyList())
    val activeSnoozes: StateFlow<List<SnoozeRule>> = _activeSnoozes.asStateFlow()

    @Volatile
    private var cachedAuditVectors: List<SecurityAuditVector>? = null

    fun peekCachedAuditVectors(): List<SecurityAuditVector>? = cachedAuditVectors

    private val busyCameras = mutableSetOf<String>()
    private var isMonitoring = false
    private var lastScreenCaptureLogged = false
    private var lastMicLogged = false
    private var lastCameraLogged = false
    private val lastLocationAlertMs = mutableMapOf<String, Long>()
    private val lastClipboardAlertMs = mutableMapOf<String, Long>()
    @Volatile
    private var osScreenRecordingActive = false
    @Volatile
    private var isScreenInteractive = true
    private var screenReceiverRegistered = false

    private var pendingMicAlertRunnable: Runnable? = null
    private var pendingCamAlertRunnable: Runnable? = null

    var onHighPriorityAlert: ((title: String, message: String) -> Unit)? = null
    var onGuardAlert: ((GuardAlertPayload) -> Unit)? = null

    private val coreLocationProviders = setOf(
        "android",
        "com.android.location.fused",
        "com.google.android.gms",
        "com.android.phone",
        "com.android.systemui"
    )

    private val knownScreenRecorders = setOf(
        "com.miui.screenrecorder",
        "com.samsung.android.app.smartcapture",
        "com.oneplus.screenrecord",
        "com.coloros.screenrecorder",
        "com.oplus.screenrecorder",
        "com.vivo.screenrecord",
        "com.asus.screenrecorder",
        "com.huawei.screenrecorder",
        "com.hecorat.screenrecorder.free",
        "videoeditor.videorecorder.screenrecorder",
        "com.duapps.recorder",
        "com.kimcy929.screenrecorder",
        "com.nll.screenrecorder",
        "com.mobizen.mirroring.uimode"
    )

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

    private val videoMediaObserver = object : ContentObserver(mainHandler) {
        override fun onChange(selfChange: Boolean) {
            onChange(selfChange, null)
        }

        override fun onChange(selfChange: Boolean, uri: android.net.Uri?) {
            super.onChange(selfChange, uri)
            bgExecutor.execute {
                refreshDisplayCaptureState(triggerAlertIfNew = true)
            }
        }
    }

    private val clipChangedListener = ClipboardManager.OnPrimaryClipChangedListener {
        // Re-check clipboard access shortly after clip changes so background snooping apps are caught immediately
        mainHandler.postDelayed({
            bgExecutor.execute {
                refreshLocationAndClipboardState(triggerAlertIfNew = true)
            }
        }, 650L)
    }

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    isScreenInteractive = false
                    if (_rulesConfig.value.pausePollingScreenOff) {
                        mainHandler.removeCallbacks(periodicMonitorRunnable)
                    }
                }
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                    isScreenInteractive = true
                    if (isMonitoring) {
                        mainHandler.removeCallbacks(periodicMonitorRunnable)
                        mainHandler.postDelayed(periodicMonitorRunnable, 1500L)
                        bgExecutor.execute { refreshAllStates() }
                    }
                }
            }
        }
    }

    private val periodicMonitorRunnable = object : Runnable {
        override fun run() {
            if (!isMonitoring) return
            if (_rulesConfig.value.pausePollingScreenOff && !isDeviceScreenInteractive()) {
                return
            }
            bgExecutor.execute {
                refreshDisplayCaptureState(triggerAlertIfNew = true)
                refreshLocationAndClipboardState(triggerAlertIfNew = true)
            }
            mainHandler.postDelayed(this, 3500L)
        }
    }

    private val audioRecordingCallback = object : AudioManager.AudioRecordingCallback() {
        override fun onRecordingConfigChanged(configs: List<AudioRecordingConfiguration>) {
            super.onRecordingConfigChanged(configs)
            val activeCount = configs.size
            val isMicNow = activeCount > 0
            bgExecutor.execute {
                val resolvedMic = if (isMicNow) {
                    resolveActiveSensorApps(
                        targetOpStr = "android:record_audio",
                        requiredPermission = android.Manifest.permission.RECORD_AUDIO,
                        audioConfigs = configs
                    )
                } else {
                    emptyList()
                }
                val unignoredMic = resolvedMic.filterNot { isSensorTrustedOrSnoozed(it.first, GuardSensorType.MIC) }
                val micLabels = unignoredMic.map { it.second }
                val shouldSuppressAll = (resolvedMic.isNotEmpty() && unignoredMic.isEmpty()) ||
                    (resolvedMic.isEmpty() && isGlobalSensorSnoozed(GuardSensorType.MIC))

                _captureState.value = _captureState.value.copy(
                    isMicActive = isMicNow && !shouldSuppressAll,
                    activeMicSessions = if (shouldSuppressAll) 0 else activeCount,
                    activeMicApps = micLabels,
                    lastUpdatedMs = System.currentTimeMillis()
                )
                // Re-evaluate screen capture in case the screen recorder is capturing internal/mic audio
                refreshDisplayCaptureState(triggerAlertIfNew = true)

                if (!isMicNow || shouldSuppressAll) {
                    pendingMicAlertRunnable?.let { mainHandler.removeCallbacks(it) }
                    pendingMicAlertRunnable = null
                    if (!isMicNow) lastMicLogged = false
                    return@execute
                }

                if (!lastMicLogged && pendingMicAlertRunnable == null) {
                    val delayMs = if (_rulesConfig.value.debounceEnabled) 1800L else 0L
                    val runnable = Runnable {
                        pendingMicAlertRunnable = null
                        bgExecutor.execute {
                            val currentConfigs = try {
                                audioManager.activeRecordingConfigurations
                            } catch (_: Exception) {
                                emptyList()
                            }
                            if (currentConfigs.isEmpty()) return@execute

                            val freshResolved = resolveActiveSensorApps(
                                targetOpStr = "android:record_audio",
                                requiredPermission = android.Manifest.permission.RECORD_AUDIO,
                                audioConfigs = currentConfigs
                            )
                            val freshUnignored = freshResolved.filterNot { isSensorTrustedOrSnoozed(it.first, GuardSensorType.MIC) }
                            val freshSuppress = (freshResolved.isNotEmpty() && freshUnignored.isEmpty()) ||
                                (freshResolved.isEmpty() && isGlobalSensorSnoozed(GuardSensorType.MIC))
                            if (freshSuppress || lastMicLogged) return@execute

                            lastMicLogged = true
                            val who = if (freshUnignored.isNotEmpty()) freshUnignored.joinToString(", ") { it.second } else null
                            val primaryPkg = freshUnignored.firstOrNull()?.first
                            val title = if (who != null) "Mic Active: $who" else "Microphone Active"
                            val countNow = currentConfigs.size
                            val desc = if (who != null) {
                                "$who is recording audio ($countNow active stream${if (countNow == 1) "" else "s"})."
                            } else {
                                "$countNow audio stream(s) recording."
                            }
                            recordEvent(
                                type = "MIC",
                                title = title,
                                description = desc,
                                severity = AuditSeverity.WARNING,
                                packageName = primaryPkg
                            )
                            maybeDispatchGuardNotification(
                                payload = GuardAlertPayload(title, desc, primaryPkg, GuardSensorType.MIC),
                                audioConfigs = currentConfigs
                            )
                        }
                    }
                    pendingMicAlertRunnable = runnable
                    if (delayMs == 0L) {
                        runnable.run()
                    } else {
                        mainHandler.postDelayed(runnable, delayMs)
                    }
                }
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
        PreferencesBackupManager.autoRestoreIfCleared(context)
        loadSavedEvents()
        loadRulesConfig()
        loadTrustedRules()
        loadSnoozes()
    }

    fun reloadPersistedState() {
        loadRulesConfig()
        loadTrustedRules()
        loadSnoozes()
    }

    fun isPackageIgnored(packageName: String): Boolean {
        val rule = _trustedRules.value[packageName] ?: return false
        return rule.trustedSensors.isNotEmpty()
    }

    fun isSensorTrusted(packageName: String?, sensorType: GuardSensorType): Boolean {
        if (packageName.isNullOrBlank()) return false
        val rule = _trustedRules.value[packageName] ?: return false
        return sensorType in rule.trustedSensors
    }

    private fun isGlobalSensorSnoozed(sensorType: GuardSensorType): Boolean {
        val now = System.currentTimeMillis()
        val active = pruneExpiredSnoozes(now)
        val sensorKey = "SENSOR:${sensorType.name}"
        return active.any { it.targetKey == "ALL" || it.targetKey == sensorKey }
    }

    fun isSensorTrustedOrSnoozed(packageName: String?, sensorType: GuardSensorType): Boolean {
        if (isGlobalSensorSnoozed(sensorType)) return true
        if (packageName.isNullOrBlank()) return false
        if (isSensorTrusted(packageName, sensorType)) return true
        val now = System.currentTimeMillis()
        return pruneExpiredSnoozes(now).any { it.targetKey == packageName }
    }

    fun updateRulesConfig(config: GuardRulesConfig) {
        _rulesConfig.value = config
        try {
            prefs.edit()
                .putString("rules_alert_mode", config.alertMode.name)
                .putBoolean("rules_debounce", config.debounceEnabled)
                .putBoolean("rules_silence_calls", config.silenceDuringCalls)
                .putBoolean("rules_quiet_fg", config.quietForForegroundApps)
                .putBoolean("rules_pause_screen_off", config.pausePollingScreenOff)
                .commit()
            bgExecutor.execute { PreferencesBackupManager.saveMirrorSnapshot(context) }
        } catch (_: Exception) {
        }
        if (isMonitoring) {
            mainHandler.removeCallbacks(periodicMonitorRunnable)
            if (!config.pausePollingScreenOff || isDeviceScreenInteractive()) {
                mainHandler.postDelayed(periodicMonitorRunnable, 1500L)
            }
        }
    }

    fun setAppTrust(
        packageName: String,
        sensors: Set<GuardSensorType>,
        source: String = "Manual"
    ) {
        if (packageName.isBlank()) return
        val updatedMap = _trustedRules.value.toMutableMap()
        if (sensors.isEmpty()) {
            updatedMap.remove(packageName)
        } else {
            val existing = updatedMap[packageName]
            updatedMap[packageName] = TrustedAppRule(
                packageName = packageName,
                trustedSensors = sensors,
                source = existing?.source?.takeIf { source == "Manual" && it != "Manual" } ?: source,
                addedAtMs = existing?.addedAtMs ?: System.currentTimeMillis()
            )
        }
        persistTrustedRules(updatedMap)
    }

    fun removeAppTrust(packageName: String) {
        val updatedMap = _trustedRules.value.toMutableMap()
        updatedMap.remove(packageName)
        persistTrustedRules(updatedMap)
    }

    fun snoozeTarget(targetKey: String, label: String, durationMs: Long = 3_600_000L) {
        val now = System.currentTimeMillis()
        val active = pruneExpiredSnoozes(now).filterNot { it.targetKey == targetKey }.toMutableList()
        active.add(SnoozeRule(targetKey = targetKey, label = label, expiresAtMs = now + durationMs))
        persistSnoozes(active)
        bgExecutor.execute { refreshAllStates() }
    }

    fun cancelSnooze(targetKey: String) {
        val updated = pruneExpiredSnoozes(System.currentTimeMillis()).filterNot { it.targetKey == targetKey }
        persistSnoozes(updated)
        bgExecutor.execute { refreshAllStates() }
    }

    fun clearAllSnoozes() {
        persistSnoozes(emptyList())
        bgExecutor.execute { refreshAllStates() }
    }

    fun ignorePackage(packageName: String) {
        setAppTrust(packageName, GuardSensorType.entries.toSet(), source = "Live Guard")
    }

    fun unignorePackage(packageName: String) {
        removeAppTrust(packageName)
    }

    fun clearIgnoredPackages() {
        persistTrustedRules(emptyMap())
    }

    private fun persistTrustedRules(map: Map<String, TrustedAppRule>) {
        _trustedRules.value = map
        val pkgSet = map.keys.toSet()
        _ignoredPackages.value = pkgSet
        try {
            val arr = JSONArray()
            map.values.forEach { rule ->
                arr.put(JSONObject().apply {
                    put("pkg", rule.packageName)
                    put("source", rule.source)
                    put("addedAt", rule.addedAtMs)
                    put("sensors", JSONArray(rule.trustedSensors.map { it.name }))
                })
            }
            prefs.edit()
                .putString("trusted_rules_json", arr.toString())
                .putStringSet("ignored_packages", pkgSet)
                .commit()
            bgExecutor.execute { PreferencesBackupManager.saveMirrorSnapshot(context) }
        } catch (_: Exception) {
        }
        cachedAuditVectors = null
        PrivacyAnalyzer.invalidateTimelineCache()
        bgExecutor.execute { refreshAllStates() }
    }

    private fun loadRulesConfig() {
        try {
            val modeName = prefs.getString("rules_alert_mode", GuardAlertMode.SMART_COVERT.name)
            val mode = try {
                GuardAlertMode.valueOf(modeName ?: GuardAlertMode.SMART_COVERT.name)
            } catch (_: Exception) {
                GuardAlertMode.SMART_COVERT
            }
            _rulesConfig.value = GuardRulesConfig(
                alertMode = mode,
                debounceEnabled = prefs.getBoolean("rules_debounce", true),
                silenceDuringCalls = prefs.getBoolean("rules_silence_calls", true),
                quietForForegroundApps = prefs.getBoolean("rules_quiet_fg", true),
                pausePollingScreenOff = prefs.getBoolean("rules_pause_screen_off", true)
            )
        } catch (_: Exception) {
        }
    }

    private fun loadTrustedRules() {
        try {
            val rawJson = prefs.getString("trusted_rules_json", null)
            val map = linkedMapOf<String, TrustedAppRule>()
            if (!rawJson.isNullOrBlank()) {
                val arr = JSONArray(rawJson)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val pkg = obj.optString("pkg")
                    if (pkg.isBlank()) continue
                    val sensorArr = obj.optJSONArray("sensors")
                    val sensors = mutableSetOf<GuardSensorType>()
                    if (sensorArr != null) {
                        for (j in 0 until sensorArr.length()) {
                            try {
                                sensors.add(GuardSensorType.valueOf(sensorArr.getString(j)))
                            } catch (_: Exception) {
                            }
                        }
                    }
                    if (sensors.isNotEmpty()) {
                        map[pkg] = TrustedAppRule(
                            packageName = pkg,
                            trustedSensors = sensors,
                            source = obj.optString("source", "Manual"),
                            addedAtMs = obj.optLong("addedAt", System.currentTimeMillis())
                        )
                    }
                }
            } else {
                // Migrate legacy ignored_packages set
                val legacy = prefs.getStringSet("ignored_packages", emptySet()) ?: emptySet()
                legacy.forEach { pkg ->
                    if (pkg.isNotBlank()) {
                        map[pkg] = TrustedAppRule(
                            packageName = pkg,
                            trustedSensors = GuardSensorType.entries.toSet(),
                            source = "Legacy Ignore"
                        )
                    }
                }
            }
            _trustedRules.value = map
            _ignoredPackages.value = map.keys.toSet()
        } catch (_: Exception) {
        }
    }

    private fun pruneExpiredSnoozes(now: Long): List<SnoozeRule> {
        val current = _activeSnoozes.value
        val active = current.filter { it.expiresAtMs > now }
        if (active.size != current.size) {
            persistSnoozes(active)
        }
        return active
    }

    private fun persistSnoozes(list: List<SnoozeRule>) {
        _activeSnoozes.value = list
        try {
            val arr = JSONArray()
            list.forEach { s ->
                arr.put(JSONObject().apply {
                    put("key", s.targetKey)
                    put("label", s.label)
                    put("expiresAt", s.expiresAtMs)
                })
            }
            prefs.edit().putString("snooze_rules_json", arr.toString()).apply()
        } catch (_: Exception) {
        }
    }

    private fun loadSnoozes() {
        try {
            val raw = prefs.getString("snooze_rules_json", null) ?: return
            val arr = JSONArray(raw)
            val now = System.currentTimeMillis()
            val list = mutableListOf<SnoozeRule>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val exp = obj.optLong("expiresAt", 0L)
                val key = obj.optString("key")
                if (key.isNotBlank() && exp > now) {
                    list.add(SnoozeRule(key, obj.optString("label", key), exp))
                }
            }
            _activeSnoozes.value = list
        } catch (_: Exception) {
        }
    }

    private fun isDeviceScreenInteractive(): Boolean {
        return try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            pm?.isInteractive ?: isScreenInteractive
        } catch (_: Exception) {
            isScreenInteractive
        }
    }

    private fun isScreenInteractiveAndUnlocked(): Boolean {
        if (!isDeviceScreenInteractive()) return false
        return try {
            val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            km?.isKeyguardLocked != true
        } catch (_: Exception) {
            true
        }
    }

    private fun isActivePhoneOrVoipCall(audioConfigs: List<AudioRecordingConfiguration>): Boolean {
        val mode = try {
            audioManager.mode
        } catch (_: Exception) {
            AudioManager.MODE_NORMAL
        }
        if (mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION) {
            return true
        }
        return audioConfigs.any { cfg ->
            try {
                cfg.clientAudioSource == MediaRecorder.AudioSource.VOICE_COMMUNICATION
            } catch (_: Exception) {
                false
            }
        }
    }

    private fun maybeDispatchGuardNotification(
        payload: GuardAlertPayload,
        audioConfigs: List<AudioRecordingConfiguration> = emptyList()
    ) {
        val cfg = _rulesConfig.value
        if (cfg.alertMode == GuardAlertMode.SILENT_LOG) return
        if (isSensorTrustedOrSnoozed(payload.packageName, payload.sensorType)) return

        if (cfg.silenceDuringCalls &&
            (payload.sensorType == GuardSensorType.MIC || payload.sensorType == GuardSensorType.CAMERA)
        ) {
            val configsToCheck = audioConfigs.ifEmpty {
                try {
                    audioManager.activeRecordingConfigurations
                } catch (_: Exception) {
                    emptyList()
                }
            }
            if (isActivePhoneOrVoipCall(configsToCheck)) return
        }

        if (cfg.alertMode == GuardAlertMode.SMART_COVERT &&
            cfg.quietForForegroundApps &&
            payload.sensorType != GuardSensorType.SCREEN &&
            payload.packageName != null &&
            isScreenInteractiveAndUnlocked()
        ) {
            val fgSnapshot = queryForegroundUsageState()
            if (fgSnapshot.usageAccessGranted && fgSnapshot.activeForegroundPackage == payload.packageName) {
                return
            }
        }

        onGuardAlert?.invoke(payload)
        onHighPriorityAlert?.invoke(payload.title, payload.message)
    }

    fun startMonitoring() {
        if (isMonitoring) {
            refreshAllStates()
            return
        }
        isMonitoring = true
        isScreenInteractive = isDeviceScreenInteractive()
        if (!screenReceiverRegistered) {
            try {
                val filter = IntentFilter().apply {
                    addAction(Intent.ACTION_SCREEN_OFF)
                    addAction(Intent.ACTION_SCREEN_ON)
                    addAction(Intent.ACTION_USER_PRESENT)
                }
                ContextCompat.registerReceiver(
                    context,
                    screenStateReceiver,
                    filter,
                    ContextCompat.RECEIVER_NOT_EXPORTED
                )
                screenReceiverRegistered = true
            } catch (_: Exception) {
            }
        }
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
        try {
            clipboardManager?.addPrimaryClipChangedListener(clipChangedListener)
        } catch (_: Exception) {
        }
        try {
            context.contentResolver.registerContentObserver(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                true,
                videoMediaObserver
            )
        } catch (_: Exception) {
        }
        if (Build.VERSION.SDK_INT >= 35) {
            try {
                val wm = context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
                val consumer = java.util.function.Consumer<Int> { state ->
                    osScreenRecordingActive = (state == 1) // SCREEN_RECORDING_STATE_VISIBLE
                    refreshDisplayCaptureState(triggerAlertIfNew = true)
                }
                val initialState = wm.addScreenRecordingCallback(context.mainExecutor, consumer)
                osScreenRecordingActive = (initialState == 1)
            } catch (_: Exception) {
            }
        }
        mainHandler.removeCallbacks(periodicMonitorRunnable)
        mainHandler.postDelayed(periodicMonitorRunnable, 2500L)
        refreshAllStates()
    }

    fun refreshAllStates() {
        refreshDisplayCaptureState(triggerAlertIfNew = false)
        refreshLocationAndClipboardState(triggerAlertIfNew = false)
        try {
            val configs = audioManager.activeRecordingConfigurations
            val isMicNow = configs.isNotEmpty()
            val resolvedMic = if (isMicNow) {
                resolveActiveSensorApps(
                    targetOpStr = "android:record_audio",
                    requiredPermission = android.Manifest.permission.RECORD_AUDIO,
                    audioConfigs = configs
                )
            } else {
                emptyList()
            }
            val unignoredMic = resolvedMic.filterNot { isSensorTrustedOrSnoozed(it.first, GuardSensorType.MIC) }
            val micSuppressed = (resolvedMic.isNotEmpty() && unignoredMic.isEmpty()) ||
                (resolvedMic.isEmpty() && isGlobalSensorSnoozed(GuardSensorType.MIC))
            _captureState.value = _captureState.value.copy(
                isMicActive = isMicNow && !micSuppressed,
                activeMicSessions = if (micSuppressed) 0 else configs.size,
                activeMicApps = unignoredMic.map { it.second },
                isVpnOrProxyActive = false,
                lastUpdatedMs = System.currentTimeMillis()
            )
        } catch (_: Exception) {
        }
    }

    private fun updateCameraState() {
        val currentBusy = synchronized(busyCameras) { busyCameras.toSet() }
        val isCamNow = currentBusy.isNotEmpty()
        bgExecutor.execute {
            val resolvedCam = if (isCamNow) {
                resolveActiveSensorApps(
                    targetOpStr = "android:camera",
                    requiredPermission = android.Manifest.permission.CAMERA
                )
            } else {
                emptyList()
            }
            val unignoredCam = resolvedCam.filterNot { isSensorTrustedOrSnoozed(it.first, GuardSensorType.CAMERA) }
            val shouldSuppressAlert = (resolvedCam.isNotEmpty() && unignoredCam.isEmpty()) ||
                (resolvedCam.isEmpty() && isGlobalSensorSnoozed(GuardSensorType.CAMERA))
            _captureState.value = _captureState.value.copy(
                isCameraActive = isCamNow && !shouldSuppressAlert,
                busyCameraIds = if (shouldSuppressAlert) emptySet() else currentBusy,
                activeCameraApps = unignoredCam.map { it.second },
                lastUpdatedMs = System.currentTimeMillis()
            )
            if (!isCamNow || shouldSuppressAlert) {
                pendingCamAlertRunnable?.let { mainHandler.removeCallbacks(it) }
                pendingCamAlertRunnable = null
                if (!isCamNow) lastCameraLogged = false
                return@execute
            }
            if (!lastCameraLogged && pendingCamAlertRunnable == null) {
                val delayMs = if (_rulesConfig.value.debounceEnabled) 1800L else 0L
                val runnable = Runnable {
                    pendingCamAlertRunnable = null
                    bgExecutor.execute {
                        val busyNow = synchronized(busyCameras) { busyCameras.toSet() }
                        if (busyNow.isEmpty() || lastCameraLogged) return@execute

                        val freshResolved = resolveActiveSensorApps(
                            targetOpStr = "android:camera",
                            requiredPermission = android.Manifest.permission.CAMERA
                        )
                        val freshUnignored = freshResolved.filterNot { isSensorTrustedOrSnoozed(it.first, GuardSensorType.CAMERA) }
                        val freshSuppress = (freshResolved.isNotEmpty() && freshUnignored.isEmpty()) ||
                            (freshResolved.isEmpty() && isGlobalSensorSnoozed(GuardSensorType.CAMERA))
                        if (freshSuppress) return@execute

                        lastCameraLogged = true
                        val who = if (freshUnignored.isNotEmpty()) freshUnignored.joinToString(", ") { it.second } else null
                        val primaryPkg = freshUnignored.firstOrNull()?.first
                        val title = if (who != null) "Camera Active: $who" else "Camera Active"
                        val desc = if (who != null) {
                            "$who is using camera lens ${busyNow.joinToString()}."
                        } else {
                            "Lens ${busyNow.joinToString()} in use."
                        }
                        recordEvent(
                            type = "CAMERA",
                            title = title,
                            description = desc,
                            severity = AuditSeverity.WARNING,
                            packageName = primaryPkg
                        )
                        maybeDispatchGuardNotification(
                            payload = GuardAlertPayload(title, desc, primaryPkg, GuardSensorType.CAMERA)
                        )
                    }
                }
                pendingCamAlertRunnable = runnable
                if (delayMs == 0L) {
                    runnable.run()
                } else {
                    mainHandler.postDelayed(runnable, delayMs)
                }
            }
        }
    }

    private fun resolveActiveSensorApps(
        targetOpStr: String,
        requiredPermission: String,
        audioConfigs: List<AudioRecordingConfiguration> = emptyList()
    ): List<Pair<String, String>> {
        val pm = context.packageManager
        val now = System.currentTimeMillis()
        val foundPackages = linkedSetOf<String>()

        // 1. Try AudioRecordingConfiguration client UID / package via reflection
        for (cfg in audioConfigs) {
            try {
                val clientPkg = cfg.javaClass.getMethod("getClientPackageName").invoke(cfg) as? String
                if (!clientPkg.isNullOrBlank() && clientPkg != context.packageName) {
                    foundPackages.add(clientPkg)
                }
            } catch (_: Exception) {
            }
            try {
                val uid = cfg.javaClass.getMethod("getClientUid").invoke(cfg) as? Int
                if (uid != null && uid >= 10000) {
                    pm.getPackagesForUid(uid)?.forEach { pkg ->
                        if (pkg != context.packageName) foundPackages.add(pkg)
                    }
                }
            } catch (_: Exception) {
            }
        }

        // 2. Check AppOpsManager for running or just-started ops matching targetOpStr
        if (foundPackages.isEmpty()) {
            try {
                val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
                val getPackagesForOps = appOps.javaClass.getMethod("getPackagesForOps", IntArray::class.java)
                val pkgOpsList = getPackagesForOps.invoke(appOps, null) as? List<*>
                if (pkgOpsList != null) {
                    for (pkgOps in pkgOpsList) {
                        if (pkgOps == null) continue
                        val pkgName = pkgOps.javaClass.getMethod("getPackageName").invoke(pkgOps) as? String ?: continue
                        if (pkgName == context.packageName) continue
                        val ops = pkgOps.javaClass.getMethod("getOps").invoke(pkgOps) as? List<*> ?: continue
                        for (opEntry in ops) {
                            if (opEntry == null) continue
                            val opStr = try {
                                opEntry.javaClass.getMethod("getOpStr").invoke(opEntry) as? String
                            } catch (_: Exception) {
                                null
                            } ?: continue
                            if (opStr != targetOpStr) continue

                            val isRunning = try {
                                (opEntry.javaClass.getMethod("isRunning").invoke(opEntry) as? Boolean) == true
                            } catch (_: Exception) {
                                false
                            }
                            val lastTime = try {
                                val m = opEntry.javaClass.getMethod("getLastAccessTime", Int::class.javaPrimitiveType)
                                (m.invoke(opEntry, 0x1f) as? Long) ?: 0L
                            } catch (_: Exception) {
                                0L
                            }
                            if (isRunning || (now - lastTime) in 0L..5_000L) {
                                foundPackages.add(pkgName)
                            }
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }

        // 3. UsageStatsManager strict foreground-only fallback:
        // Only attribute to the currently RESUMED (not paused/closed) non-launcher foreground app holding the permission.
        // Never blame background foreground-services or recently closed apps (prevents false positives on WhatsApp etc.).
        if (foundPackages.isEmpty()) {
            val fgSnapshot = queryForegroundUsageState()
            val fgPkg = fgSnapshot.activeForegroundPackage
            if (fgPkg != null &&
                fgPkg != context.packageName &&
                fgPkg !in getLauncherPackages() &&
                pm.checkPermission(requiredPermission, fgPkg) == android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                foundPackages.add(fgPkg)
            }
        }

        return foundPackages.map { pkg ->
            val label = try {
                val ai = pm.getApplicationInfo(pkg, 0)
                pm.getApplicationLabel(ai).toString()
            } catch (_: Exception) {
                pkg
            }
            pkg to label
        }.distinctBy { it.first }.take(3)
    }

    private data class ForegroundUsageSnapshot(
        val usageAccessGranted: Boolean,
        val activeForegroundPackage: String?,
        val lastLeftForegroundMs: Map<String, Long>
    )

    private fun getLauncherPackages(): Set<String> {
        return try {
            val intent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
                addCategory(android.content.Intent.CATEGORY_HOME)
            }
            context.packageManager.queryIntentActivities(intent, 0)
                .mapNotNull { it.activityInfo?.packageName }
                .toSet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    private fun queryForegroundUsageState(lookbackMs: Long = 15L * 60_000L): ForegroundUsageSnapshot {
        if (!PrivacyAnalyzer.hasUsageStatsPermission(context)) {
            return ForegroundUsageSnapshot(
                usageAccessGranted = false,
                activeForegroundPackage = null,
                lastLeftForegroundMs = emptyMap()
            )
        }
        return try {
            val now = System.currentTimeMillis()
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val events = usm.queryEvents(now - lookbackMs, now)
            val ev = UsageEvents.Event()
            var activeFgPkg: String? = null
            var activeFgResumeTs = 0L
            val lastLeftMap = mutableMapOf<String, Long>()

            while (events != null && events.hasNextEvent()) {
                events.getNextEvent(ev)
                val pkg = ev.packageName ?: continue
                when (ev.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> {
                        if (ev.timeStamp >= activeFgResumeTs) {
                            activeFgResumeTs = ev.timeStamp
                            activeFgPkg = pkg
                        }
                    }
                    UsageEvents.Event.ACTIVITY_PAUSED,
                    UsageEvents.Event.ACTIVITY_STOPPED -> {
                        lastLeftMap[pkg] = maxOf(lastLeftMap[pkg] ?: 0L, ev.timeStamp)
                        if (pkg == activeFgPkg && ev.timeStamp >= activeFgResumeTs) {
                            activeFgPkg = null
                        }
                    }
                    16 /* UsageEvents.Event.SCREEN_NON_INTERACTIVE */ -> {
                        if (ev.timeStamp >= activeFgResumeTs) {
                            activeFgPkg?.let { lastLeftMap[it] = maxOf(lastLeftMap[it] ?: 0L, ev.timeStamp) }
                            activeFgPkg = null
                        }
                    }
                }
            }
            ForegroundUsageSnapshot(
                usageAccessGranted = true,
                activeForegroundPackage = activeFgPkg,
                lastLeftForegroundMs = lastLeftMap
            )
        } catch (_: Exception) {
            ForegroundUsageSnapshot(
                usageAccessGranted = false,
                activeForegroundPackage = null,
                lastLeftForegroundMs = emptyMap()
            )
        }
    }

    private fun refreshLocationAndClipboardState(triggerAlertIfNew: Boolean) {
        try {
            val now = System.currentTimeMillis()
            val pm = context.packageManager
            val fgSnapshot = queryForegroundUsageState()
            if (!fgSnapshot.usageAccessGranted) {
                // Without Usage Access, we cannot verify whether an app is foreground or background—never guess.
                _captureState.value = _captureState.value.copy(
                    isBackgroundLocationActive = false,
                    activeLocationApps = emptyList(),
                    isClipboardAccessedRecently = false,
                    recentClipboardApps = emptyList(),
                    lastUpdatedMs = now
                )
                return
            }
            val fgPkg = fgSnapshot.activeForegroundPackage
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val getPackagesForOps = appOps.javaClass.getMethod("getPackagesForOps", IntArray::class.java)
            val pkgOpsList = getPackagesForOps.invoke(appOps, null) as? List<*> ?: return

            val locPackages = linkedMapOf<String, String>()
            val clipPackages = linkedMapOf<String, String>()

            for (pkgOps in pkgOpsList) {
                if (pkgOps == null) continue
                val pkgName = pkgOps.javaClass.getMethod("getPackageName").invoke(pkgOps) as? String ?: continue
                if (pkgName == context.packageName || isPackageIgnored(pkgName)) continue
                if (pkgName == fgPkg) continue

                val lastLeftFg = fgSnapshot.lastLeftForegroundMs[pkgName] ?: 0L

                val ops = pkgOps.javaClass.getMethod("getOps").invoke(pkgOps) as? List<*> ?: continue
                for (opEntry in ops) {
                    if (opEntry == null) continue
                    val opStr = try {
                        opEntry.javaClass.getMethod("getOpStr").invoke(opEntry) as? String
                    } catch (_: Exception) {
                        null
                    } ?: continue

                    val isRunning = try {
                        (opEntry.javaClass.getMethod("isRunning").invoke(opEntry) as? Boolean) == true
                    } catch (_: Exception) {
                        false
                    }
                    val lastTime = try {
                        val m = opEntry.javaClass.getMethod("getLastAccessTime", Int::class.javaPrimitiveType)
                        (m.invoke(opEntry, 0x1f) as? Long) ?: 0L
                    } catch (_: Exception) {
                        0L
                    }

                    // Ensure the access actually happened while out of foreground (not right before closing the app)
                    val happenedAfterLeavingFg = lastLeftFg == 0L || lastTime > (lastLeftFg + 2_500L)

                    val isBgLocationOp = opStr == "android:fine_location" ||
                        opStr == "android:coarse_location" ||
                        opStr == "android:monitor_location" ||
                        opStr == "android:monitor_location_high_power"

                    if (isBgLocationOp &&
                        !isSensorTrustedOrSnoozed(pkgName, GuardSensorType.LOCATION) &&
                        (isRunning || ((now - lastTime) in 0L..10_000L && happenedAfterLeavingFg)) &&
                        pkgName !in coreLocationProviders &&
                        !PrivacyAnalyzer.isBrandOrOemPackage(pkgName)
                    ) {
                        // Also make sure ongoing ops aren't lingering < 15s from a foreground close
                        if (!isRunning || (now - lastLeftFg) > 15_000L || lastLeftFg == 0L) {
                            val label = try {
                                pm.getApplicationLabel(pm.getApplicationInfo(pkgName, 0)).toString()
                            } catch (_: Exception) {
                                pkgName
                            }
                            locPackages[pkgName] = label
                        }
                    }

                    if (opStr == "android:read_clipboard" &&
                        !isSensorTrustedOrSnoozed(pkgName, GuardSensorType.CLIPBOARD) &&
                        (now - lastTime) in 0L..8_000L &&
                        happenedAfterLeavingFg &&
                        !PrivacyAnalyzer.isBrandOrOemPackage(pkgName) &&
                        pkgName != "com.android.systemui"
                    ) {
                        val label = try {
                            pm.getApplicationLabel(pm.getApplicationInfo(pkgName, 0)).toString()
                        } catch (_: Exception) {
                            pkgName
                        }
                        clipPackages[pkgName] = label
                    }
                }
            }

            _captureState.value = _captureState.value.copy(
                isBackgroundLocationActive = locPackages.isNotEmpty(),
                activeLocationApps = locPackages.values.toList(),
                isClipboardAccessedRecently = clipPackages.isNotEmpty(),
                recentClipboardApps = clipPackages.values.toList(),
                lastUpdatedMs = now
            )

            if (triggerAlertIfNew) {
                for ((pkg, label) in locPackages) {
                    val lastAlert = lastLocationAlertMs[pkg] ?: 0L
                    if (now - lastAlert > 60_000L) {
                        lastLocationAlertMs[pkg] = now
                        val title = "Background Location: $label"
                        val desc = "$label accessed GPS location while not in the foreground."
                        recordEvent(
                            type = "LOCATION",
                            title = title,
                            description = desc,
                            severity = AuditSeverity.WARNING,
                            packageName = pkg
                        )
                        maybeDispatchGuardNotification(
                            GuardAlertPayload(title, desc, pkg, GuardSensorType.LOCATION)
                        )
                    }
                }

                for ((pkg, label) in clipPackages) {
                    val lastAlert = lastClipboardAlertMs[pkg] ?: 0L
                    if (now - lastAlert > 30_000L) {
                        lastClipboardAlertMs[pkg] = now
                        val title = "Clipboard Read: $label"
                        val desc = "$label silently read your clipboard in the background."
                        recordEvent(
                            type = "CLIPBOARD",
                            title = title,
                            description = desc,
                            severity = AuditSeverity.WARNING,
                            packageName = pkg
                        )
                        maybeDispatchGuardNotification(
                            GuardAlertPayload(title, desc, pkg, GuardSensorType.CLIPBOARD)
                        )
                    }
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun resolveAppLabel(pkg: String): String {
        return try {
            val pm = context.packageManager
            val ai = pm.getApplicationInfo(pkg, 0)
            pm.getApplicationLabel(ai).toString()
        } catch (_: Exception) {
            pkg
        }
    }

    private fun refreshDisplayCaptureState(triggerAlertIfNew: Boolean) {
        try {
            val captureIndicators = mutableListOf<String>()
            val allCandidatePackages = linkedSetOf<String>()

            if (osScreenRecordingActive) {
                captureIndicators.add("OS Screen Recording")
            }

            // 1. Standard + Presentation + Hidden Virtual Displays (DisplayManager)
            val allDisplays = mutableMapOf<Int, Display>()
            displayManager.displays?.forEach { allDisplays[it.displayId] = it }
            displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)?.forEach {
                allDisplays[it.displayId] = it
            }
            try {
                val getHiddenMethod = displayManager.javaClass.getMethod("getDisplays", String::class.java)
                val allInc = getHiddenMethod.invoke(
                    displayManager,
                    "android.hardware.display.category.ALL_INCLUDING_DISABLED"
                ) as? Array<*>
                allInc?.filterIsInstance<Display>()?.forEach { allDisplays[it.displayId] = it }
            } catch (_: Exception) {
            }

            allDisplays.values.filter { display ->
                display.displayId != Display.DEFAULT_DISPLAY && display.state != Display.STATE_OFF
            }.forEach { display ->
                captureIndicators.add(display.name ?: "Virtual Display #${display.displayId}")
                try {
                    val ownerPkg = display.javaClass.getMethod("getOwnerPackageName").invoke(display) as? String
                    if (!ownerPkg.isNullOrBlank() && ownerPkg != context.packageName) {
                        allCandidatePackages.add(ownerPkg)
                    }
                } catch (_: Exception) {
                }
            }

            // 2. Internal Audio Loopback (REMOTE_SUBMIX = 8) & client UID attribution
            val audioConfigs = try {
                audioManager.activeRecordingConfigurations
            } catch (_: Exception) {
                emptyList()
            }
            val remoteSubmixConfigs = audioConfigs.filter { cfg ->
                cfg.clientAudioSource == MediaRecorder.AudioSource.REMOTE_SUBMIX
            }
            if (remoteSubmixConfigs.isNotEmpty()) {
                captureIndicators.add("Internal Audio Loopback")
                val pm = context.packageManager
                for (cfg in remoteSubmixConfigs) {
                    try {
                        val clientPkg = cfg.javaClass.getMethod("getClientPackageName").invoke(cfg) as? String
                        if (!clientPkg.isNullOrBlank() && clientPkg != context.packageName) {
                            allCandidatePackages.add(clientPkg)
                        }
                    } catch (_: Exception) {
                    }
                    try {
                        val uid = cfg.javaClass.getMethod("getClientUid").invoke(cfg) as? Int
                        if (uid != null && uid >= 10000) {
                            pm.getPackagesForUid(uid)?.forEach { pkg ->
                                if (pkg != context.packageName) allCandidatePackages.add(pkg)
                            }
                        }
                    } catch (_: Exception) {
                    }
                }
            }

            // 3. Active ScreenRecording file write in DCIM/ScreenRecorder or Movies/ScreenRecorder (filtered strictly)
            val activeRecordingHit = detectActiveScreenRecordingFile()
            if (activeRecordingHit != null) {
                captureIndicators.add("File: ${activeRecordingHit.fileName}")
                activeRecordingHit.ownerPackage
                    ?.takeIf { it.isNotBlank() && it != context.packageName }
                    ?.let { allCandidatePackages.add(it) }
            }

            val hasHardwareOrFileSignal = captureIndicators.isNotEmpty()

            // 4. Active Screen Recorder / MediaProjection AppOps & UsageEvents
            val activeRecorderPkgs = detectActiveScreenRecorderPackages(
                isMicOrAudioActive = audioConfigs.isNotEmpty(),
                hasOtherCaptureSignal = hasHardwareOrFileSignal
            )
            allCandidatePackages.addAll(activeRecorderPkgs)

            // 5. Legacy running services check
            allCandidatePackages.addAll(detectRunningCapturePackages())

            val unignoredPackages = allCandidatePackages
                .filterNot { isSensorTrustedOrSnoozed(it, GuardSensorType.SCREEN) }
                .distinct()

            val suppressedByTrustedOnly = allCandidatePackages.isNotEmpty() && unignoredPackages.isEmpty()

            unignoredPackages.forEach { pkg ->
                val label = resolveAppLabel(pkg)
                if (captureIndicators.none { it.contains(label, ignoreCase = true) || it.contains(pkg, ignoreCase = true) }) {
                    captureIndicators.add(label)
                }
            }

            val distinctIndicators = captureIndicators.distinct()
            val isCapturing = distinctIndicators.isNotEmpty() &&
                !suppressedByTrustedOnly &&
                !isGlobalSensorSnoozed(GuardSensorType.SCREEN)

            _captureState.value = _captureState.value.copy(
                isScreenRecordingOrCasting = isCapturing,
                activeCaptureDisplays = if (isCapturing) distinctIndicators else emptyList(),
                suspectedRecorderPackages = if (isCapturing) unignoredPackages else emptyList(),
                lastUpdatedMs = System.currentTimeMillis()
            )

            if (isCapturing && !lastScreenCaptureLogged) {
                lastScreenCaptureLogged = true
                val detail = distinctIndicators.joinToString(", ")
                val primaryPkg = unignoredPackages.firstOrNull()
                val who = unignoredPackages
                    .map { resolveAppLabel(it) }
                    .distinct()
                    .takeIf { it.isNotEmpty() }
                    ?.joinToString(", ")

                val eventTitle = if (who != null) "Screen Recording: $who" else "Screen Recording Detected"
                val alertMsg = if (who != null) {
                    "$who is recording or casting your screen ($detail)."
                } else {
                    "Screen capture active ($detail)."
                }

                recordEvent(
                    type = "SCREEN",
                    title = eventTitle,
                    description = alertMsg,
                    severity = AuditSeverity.DANGER,
                    packageName = primaryPkg
                )
                if (triggerAlertIfNew) {
                    maybeDispatchGuardNotification(
                        GuardAlertPayload(
                            title = eventTitle,
                            message = alertMsg,
                            packageName = primaryPkg,
                            sensorType = GuardSensorType.SCREEN
                        )
                    )
                }
            } else if (!isCapturing) {
                lastScreenCaptureLogged = false
            }
        } catch (_: Exception) {
        }
    }

    private fun isScreenRecorderPackage(pkg: String): Boolean {
        val lower = pkg.lowercase()
        return lower in knownScreenRecorders ||
            lower.contains("screenrecorder") ||
            lower.contains("screenrecord") ||
            lower.contains("screencapture")
    }

    private fun detectActiveScreenRecorderPackages(
        isMicOrAudioActive: Boolean,
        hasOtherCaptureSignal: Boolean = false
    ): List<String> {
        val activeRecorders = mutableSetOf<String>()
        val recentProjectionCandidates = mutableListOf<Pair<String, Long>>()
        val now = System.currentTimeMillis()

        // A. Check AppOpsManager for running or just-accessed OP_PROJECT_MEDIA or active screen recorder ops
        try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val getPackagesForOps = appOps.javaClass.getMethod("getPackagesForOps", IntArray::class.java)
            val pkgOpsList = getPackagesForOps.invoke(appOps, null) as? List<*>
            if (pkgOpsList != null) {
                for (pkgOps in pkgOpsList) {
                    if (pkgOps == null) continue
                    val pkgName = pkgOps.javaClass.getMethod("getPackageName").invoke(pkgOps) as? String ?: continue
                    if (pkgName == context.packageName) continue
                    val ops = pkgOps.javaClass.getMethod("getOps").invoke(pkgOps) as? List<*> ?: continue
                    for (opEntry in ops) {
                        if (opEntry == null) continue
                        val opStr = try {
                            opEntry.javaClass.getMethod("getOpStr").invoke(opEntry) as? String
                        } catch (_: Exception) {
                            null
                        } ?: continue
                        val isRunning = try {
                            (opEntry.javaClass.getMethod("isRunning").invoke(opEntry) as? Boolean) == true
                        } catch (_: Exception) {
                            false
                        }
                        if (isRunning && (opStr == "android:project_media" || isScreenRecorderPackage(pkgName))) {
                            activeRecorders.add(pkgName)
                        } else if (hasOtherCaptureSignal && opStr == "android:project_media") {
                            val lastTime = try {
                                val m = opEntry.javaClass.getMethod("getLastAccessTime", Int::class.javaPrimitiveType)
                                (m.invoke(opEntry, 0x1f) as? Long) ?: 0L
                            } catch (_: Exception) {
                                0L
                            }
                            if (lastTime > 0L && (now - lastTime) in 0L..12_000L) {
                                recentProjectionCandidates.add(pkgName to lastTime)
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }

        if (activeRecorders.isEmpty() && recentProjectionCandidates.isNotEmpty()) {
            recentProjectionCandidates
                .sortedByDescending { it.second }
                .firstOrNull()
                ?.let { activeRecorders.add(it.first) }
        }

        // B. Check UsageStatsManager for active FOREGROUND_SERVICE_START or recent screen recorder session
        try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val events = usm.queryEvents(now - 30L * 60L * 1000L, now)
            val event = UsageEvents.Event()
            val serviceStartMap = mutableMapOf<String, Long>()
            val serviceStopMap = mutableMapOf<String, Long>()
            val lastResumeMap = mutableMapOf<String, Long>()

            while (events != null && events.hasNextEvent()) {
                events.getNextEvent(event)
                val pkg = event.packageName ?: continue
                if (!isScreenRecorderPackage(pkg)) continue

                when (event.eventType) {
                    UsageEvents.Event.FOREGROUND_SERVICE_START -> {
                        serviceStartMap[pkg] = maxOf(serviceStartMap[pkg] ?: 0L, event.timeStamp)
                    }
                    UsageEvents.Event.FOREGROUND_SERVICE_STOP -> {
                        serviceStopMap[pkg] = maxOf(serviceStopMap[pkg] ?: 0L, event.timeStamp)
                    }
                    UsageEvents.Event.ACTIVITY_RESUMED -> {
                        lastResumeMap[pkg] = maxOf(lastResumeMap[pkg] ?: 0L, event.timeStamp)
                    }
                }
            }

            // If a screen recorder started a foreground service and hasn't stopped it yet
            for ((pkg, startTs) in serviceStartMap) {
                val stopTs = serviceStopMap[pkg] ?: 0L
                if (startTs > stopTs && (now - startTs) < 30L * 60L * 1000L) {
                    activeRecorders.add(pkg)
                }
            }

            // If audio/mic recording is active right now AND a screen recorder was opened within the last 5 minutes
            if (isMicOrAudioActive) {
                for ((pkg, resumeTs) in lastResumeMap) {
                    if ((now - resumeTs) < 5L * 60L * 1000L) {
                        activeRecorders.add(pkg)
                    }
                }
            }
        } catch (_: Exception) {
        }

        return activeRecorders.toList()
    }

    internal data class ScreenRecordingFileHit(
        val fileName: String,
        val ownerPackage: String? = null
    )

    internal fun isScreenRecordingMediaEntry(
        relativePath: String?,
        dataPath: String?,
        displayName: String?
    ): Boolean {
        val combinedPath = "${relativePath.orEmpty()}/${dataPath.orEmpty()}".lowercase()
        val nameLower = displayName.orEmpty().lowercase()

        val pathMatches = combinedPath.contains("screenrecorder") ||
            combinedPath.contains("screen_recorder") ||
            combinedPath.contains("screen recordings") ||
            combinedPath.contains("screen_recordings") ||
            combinedPath.contains("screenrecord") ||
            combinedPath.contains("screencapture") ||
            combinedPath.contains("screen_capture") ||
            combinedPath.contains("screencast")

        val nameMatches = nameLower.startsWith("screenrecord") ||
            nameLower.startsWith("screen_record") ||
            nameLower.startsWith("screen-record") ||
            nameLower.startsWith("screen recording") ||
            nameLower.startsWith("srecorder") ||
            nameLower.contains("screenrecord")

        return pathMatches || nameMatches
    }

    internal fun detectActiveScreenRecordingFile(): ScreenRecordingFileHit? {
        val now = System.currentTimeMillis()

        // 1. Check MediaStore ONLY for videos in ScreenRecorder paths or screen-recording filenames
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val projection = arrayOf(
                    MediaStore.Video.Media.DISPLAY_NAME,
                    MediaStore.Video.Media.RELATIVE_PATH,
                    MediaStore.Video.Media.OWNER_PACKAGE_NAME,
                    MediaStore.Video.Media.DATE_MODIFIED
                )
                val cutoffSec = (now - 8_000L) / 1000L
                context.contentResolver.query(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    "${MediaStore.Video.Media.IS_PENDING} = 1 OR ${MediaStore.Video.Media.DATE_MODIFIED} >= ?",
                    arrayOf(cutoffSec.toString()),
                    "${MediaStore.Video.Media.DATE_MODIFIED} DESC"
                )?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val name = cursor.getString(0) ?: ""
                        val relPath = cursor.getString(1) ?: ""
                        val ownerPkg = cursor.getString(2)?.takeIf { it.isNotBlank() }
                        if (isScreenRecordingMediaEntry(relPath, null, name) ||
                            (ownerPkg != null && isScreenRecorderPackage(ownerPkg))
                        ) {
                            return ScreenRecordingFileHit(
                                fileName = name.ifBlank { "ScreenRecording.mp4" },
                                ownerPackage = ownerPkg
                            )
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }

        // 2. Check standard MIUI / HyperOS / Android ScreenRecorder directories for files actively being written
        try {
            val root = Environment.getExternalStorageDirectory()
            val candidateDirs = listOf(
                File(root, "DCIM/ScreenRecorder"),
                File(root, "Movies/ScreenRecorder"),
                File(root, "DCIM/Screen recordings"),
                File(root, "Movies/Screen recordings")
            )
            for (dir in candidateDirs) {
                if (!dir.exists() || !dir.isDirectory) continue
                val files = dir.listFiles() ?: continue
                for (f in files) {
                    if (!f.isFile) continue
                    val ageMs = now - f.lastModified()
                    val nameLower = f.name.lowercase()
                    if ((nameLower.endsWith(".tmp") && ageMs in 0..120_000L) ||
                        ((nameLower.endsWith(".mp4") || nameLower.endsWith(".mkv")) && ageMs in 0..6_000L)
                    ) {
                        return ScreenRecordingFileHit(fileName = f.name, ownerPackage = null)
                    }
                }
            }
        } catch (_: Exception) {
        }

        return null
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
                    (cls.contains("record") || cls.contains("projection") || cls.contains("capture") || cls.contains("cast") || isScreenRecorderPackage(pkg))
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
        val title = "Guard Test Passed"
        val desc = "Screen & Microphone background alerts are active."
        recordEvent(
            type = "SCREEN",
            title = title,
            description = "Real-time screen & mic alerts are working.",
            severity = AuditSeverity.SAFE
        )
        onGuardAlert?.invoke(GuardAlertPayload(title, desc, null, GuardSensorType.SCREEN))
        onHighPriorityAlert?.invoke(title, desc)
    }

    fun runDeepSecurityAudit(includeSystemApps: Boolean = false): List<SecurityAuditVector> {
        refreshAllStates()
        val live = _captureState.value
        val privacySummary = PrivacyAnalyzer.analyzeDevicePrivacy(context, includeSystemApps = includeSystemApps)
        val ignored = _ignoredPackages.value
        val rules = _trustedRules.value
        val allApps = privacySummary.appReports.filterNot { app ->
            val r = rules[app.packageName]
            r != null && r.trustedSensors.size >= GuardSensorType.entries.size
        }

        // 1. Screen Recording / Mirroring
        val screenVector = if (live.isScreenRecordingOrCasting) {
            SecurityAuditVector(
                id = "screen_recording",
                title = "Screen Recording",
                plainSummary = "Screen is being recorded or mirrored now",
                detailedAdvice = "Active screen capture detected. Stop unknown recordings from your notification shade.",
                severity = AuditSeverity.DANGER,
                statusBadge = "RECORDING",
                extraDetails = live.activeCaptureDisplays,
                settingsAction = Settings.ACTION_CAST_SETTINGS,
                actionButtonLabel = "Cast Settings"
            )
        } else {
            SecurityAuditVector(
                id = "screen_recording",
                title = "Screen Recording",
                plainSummary = "No active screen capture or mirroring",
                detailedAdvice = "Monitors DisplayManager, audio loopback, AppOps, and ScreenRecorder services in real time.",
                severity = AuditSeverity.SAFE,
                statusBadge = "SAFE"
            )
        }

        // 2. Live Mic & Camera
        val micCamActive = live.isMicActive || live.isCameraActive
        val micCamApps = allApps.filter { app ->
            val r = rules[app.packageName]?.trustedSensors ?: emptySet()
            app.grantedPermissions.any { perm ->
                perm.category == PermissionCategory.CAMERA_MIC &&
                    !(perm.permissionName == android.Manifest.permission.RECORD_AUDIO && GuardSensorType.MIC in r) &&
                    !(perm.permissionName == android.Manifest.permission.CAMERA && GuardSensorType.CAMERA in r)
            }
        }
        val micCamVector = if (micCamActive) {
            SecurityAuditVector(
                id = "live_mic_cam",
                title = "Mic & Camera Live",
                plainSummary = "Sensor is actively recording right now",
                detailedAdvice = "If you aren't on a call or recording video, an app is using your mic or camera.",
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

        // 3. Background Location Access
        val bgLocationApps = allApps.filter { app ->
            !isSensorTrusted(app.packageName, GuardSensorType.LOCATION) &&
                app.grantedPermissions.any { it.permissionName == android.Manifest.permission.ACCESS_BACKGROUND_LOCATION }
        }
        val bgLocationVector = SecurityAuditVector(
            id = "bg_location",
            title = "Background Location",
            plainSummary = when {
                live.isBackgroundLocationActive -> "Active now: ${live.activeLocationApps.joinToString(", ")}"
                bgLocationApps.isEmpty() -> "No apps have Always-Allow GPS"
                else -> "${bgLocationApps.size} app(s) allowed 24/7 background GPS"
            },
            detailedAdvice = "Apps with Always-Allow location can track your movement even when closed. Change to 'Only while using' or ignore trusted apps.",
            severity = when {
                live.isBackgroundLocationActive -> AuditSeverity.DANGER
                bgLocationApps.isNotEmpty() -> AuditSeverity.WARNING
                else -> AuditSeverity.SAFE
            },
            statusBadge = when {
                live.isBackgroundLocationActive -> "GPS LIVE"
                bgLocationApps.isEmpty() -> "SAFE"
                else -> "${bgLocationApps.size} APPS"
            },
            flaggedApps = bgLocationApps,
            settingsAction = Settings.ACTION_LOCATION_SOURCE_SETTINGS,
            actionButtonLabel = "Location Settings"
        )

        // 4. Clipboard Snooping Monitor
        val clipboardTimelineApps = try {
            queryRecentClipboardReaderApps(allApps)
                .filterNot { isSensorTrusted(it.packageName, GuardSensorType.CLIPBOARD) }
        } catch (_: Exception) {
            emptyList()
        }
        val clipboardVector = SecurityAuditVector(
            id = "clipboard_access",
            title = "Clipboard Snooping",
            plainSummary = when {
                live.isClipboardAccessedRecently -> "Read recently by ${live.recentClipboardApps.joinToString(", ")}"
                clipboardTimelineApps.isEmpty() -> "No background clipboard reads detected"
                else -> "${clipboardTimelineApps.size} app(s) read clipboard recently"
            },
            detailedAdvice = "Monitors AppOps clipboard reads in the background to catch silent password, OTP, or link snooping.",
            severity = when {
                live.isClipboardAccessedRecently -> AuditSeverity.WARNING
                clipboardTimelineApps.isNotEmpty() -> AuditSeverity.WARNING
                else -> AuditSeverity.SAFE
            },
            statusBadge = when {
                live.isClipboardAccessedRecently -> "READ NOW"
                clipboardTimelineApps.isEmpty() -> "SAFE"
                else -> "${clipboardTimelineApps.size} RECENT"
            },
            flaggedApps = clipboardTimelineApps,
            settingsAction = Settings.ACTION_PRIVACY_SETTINGS,
            actionButtonLabel = "Privacy Controls"
        )

        val fullIgnored = rules.filterValues { it.trustedSensors.size >= GuardSensorType.entries.size }.keys

        // 5. Screen Overlays
        val overlayApps = allApps.filter { it.hasOverlayPermission }
        val overlayVector = SecurityAuditVector(
            id = "screen_overlays",
            title = "Screen Overlays",
            plainSummary = if (overlayApps.isEmpty()) "No apps draw over your screen" else "${overlayApps.size} app(s) can draw over other apps",
            detailedAdvice = "Turn off 'Display over other apps' for apps that don't need floating bubbles, or ignore trusted apps.",
            severity = if (overlayApps.isNotEmpty()) AuditSeverity.WARNING else AuditSeverity.SAFE,
            statusBadge = if (overlayApps.isEmpty()) "SAFE" else "${overlayApps.size} APPS",
            flaggedApps = overlayApps,
            settingsAction = Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            actionButtonLabel = "Overlay Settings"
        )

        // 6. Accessibility Screen-Readers
        val accessibilityApps = allApps.filter { it.hasAccessibilityAccess }
        val enabledRawServices = PrivacyAnalyzer.getEnabledAccessibilityPackages(context)
            .filter { it != context.packageName && it !in fullIgnored }
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

        // 7. Notification Spy Listeners
        val notifApps = allApps.filter { it.hasNotificationListener }
        val rawNotifListeners = PrivacyAnalyzer.getEnabledNotificationListeners(context)
            .filter { it != context.packageName && !PrivacyAnalyzer.isBrandOrOemPackage(it) && it !in fullIgnored }
        val notifCount = maxOf(notifApps.size, rawNotifListeners.size)
        val notifVector = SecurityAuditVector(
            id = "notification_listeners",
            title = "Notification Readers",
            plainSummary = if (notifCount == 0) "No apps intercepting notifications" else "$notifCount app(s) can read all incoming messages",
            detailedAdvice = "Notification Listeners can silently read incoming chats, SMS, and 2FA codes.",
            severity = if (notifCount > 0) AuditSeverity.WARNING else AuditSeverity.SAFE,
            statusBadge = if (notifCount == 0) "SAFE" else "$notifCount APPS",
            flaggedApps = notifApps,
            extraDetails = rawNotifListeners.map { "Listener: $it" },
            settingsAction = "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS",
            actionButtonLabel = "Notification Access"
        )

        // 8. Device Admins
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

        // 9. Custom Third-Party Keyboards (Input Methods)
        val untrustedKeyboards = PrivacyAnalyzer.getEnabledInputMethods(context)
            .filterNot { it in fullIgnored }
        val inputVector = SecurityAuditVector(
            id = "keyboards_vpn",
            title = "Custom Keyboards",
            plainSummary = if (untrustedKeyboards.isEmpty()) "Only trusted system/brand keyboard enabled" else "${untrustedKeyboards.size} third-party keyboard(s) enabled",
            detailedAdvice = "Untrusted third-party keyboards can record everything you type, including passwords.",
            severity = if (untrustedKeyboards.isNotEmpty()) AuditSeverity.WARNING else AuditSeverity.SAFE,
            statusBadge = if (untrustedKeyboards.isEmpty()) "SAFE" else "${untrustedKeyboards.size} ACTIVE",
            extraDetails = untrustedKeyboards.map { "Keyboard: $it" },
            settingsAction = Settings.ACTION_INPUT_METHOD_SETTINGS,
            actionButtonLabel = "Keyboard Settings"
        )

        // 10. Sideloaded Apps & USB Debugging (excludes MIUI / HyperOS / Brand apps)
        val sideloadedApps = allApps.filter { it.isSideloaded && !PrivacyAnalyzer.isBrandOrOemPackage(it.packageName) }
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
            plainSummary = if (sysFlags.isEmpty()) "Verified store/brand apps & ADB off" else sysFlags.joinToString(" • "),
            detailedAdvice = "Turn off USB Debugging when not needed and review third-party APKs installed outside official stores.",
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
            bgLocationVector,
            clipboardVector,
            overlayVector,
            accessibilityVector,
            notifVector,
            adminVector,
            inputVector,
            sideloadVector
        )

        cachedAuditVectors = allVectors

        recordEvent(
            type = "AUDIT",
            title = "Security Scan Finished",
            description = "${allVectors.count { it.severity != AuditSeverity.SAFE }} item(s) flagged for review.",
            severity = AuditSeverity.SAFE
        )

        return allVectors
    }

    private fun queryRecentClipboardReaderApps(apps: List<AppPrivacyReport>): List<AppPrivacyReport> {
        val now = System.currentTimeMillis()
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val getPackagesForOps = appOps.javaClass.getMethod("getPackagesForOps", IntArray::class.java)
        val pkgOpsList = getPackagesForOps.invoke(appOps, null) as? List<*> ?: return emptyList()
        val recentPkgs = mutableSetOf<String>()
        for (pkgOps in pkgOpsList) {
            if (pkgOps == null) continue
            val pkgName = pkgOps.javaClass.getMethod("getPackageName").invoke(pkgOps) as? String ?: continue
            val ops = pkgOps.javaClass.getMethod("getOps").invoke(pkgOps) as? List<*> ?: continue
            for (opEntry in ops) {
                if (opEntry == null) continue
                val opStr = try {
                    opEntry.javaClass.getMethod("getOpStr").invoke(opEntry) as? String
                } catch (_: Exception) {
                    null
                } ?: continue
                if (opStr != "android:read_clipboard") continue
                val lastTime = try {
                    val m = opEntry.javaClass.getMethod("getLastAccessTime", Int::class.javaPrimitiveType)
                    (m.invoke(opEntry, 0x1f) as? Long) ?: 0L
                } catch (_: Exception) {
                    0L
                }
                if (lastTime > 0L && (now - lastTime) in 0L..86_400_000L) {
                    recentPkgs.add(pkgName)
                }
            }
        }
        return apps.filter { it.packageName in recentPkgs && !PrivacyAnalyzer.isBrandOrOemPackage(it.packageName) }
    }

    fun recordEvent(
        type: String,
        title: String,
        description: String,
        severity: AuditSeverity,
        packageName: String? = null
    ) {
        val current = _events.value.toMutableList()
        val now = System.currentTimeMillis()
        val maxExistingId = current.maxOfOrNull { it.id } ?: 0L
        val uniqueId = if (now > maxExistingId) now else maxExistingId + 1L
        val newEvent = GuardEvent(
            id = uniqueId,
            timestamp = now,
            type = type,
            title = title,
            description = description,
            severity = severity,
            packageName = packageName
        )
        current.add(0, newEvent)
        val trimmed = current.take(40)
        _events.value = trimmed
        saveEvents(trimmed)
        if (type in setOf("MIC", "CAMERA", "LOCATION", "CLIPBOARD", "SCREEN")) {
            PrivacyAnalyzer.invalidateTimelineCache()
        }
    }

    fun clearEvents() {
        _events.value = emptyList()
        prefs.edit().remove("saved_events_json").apply()
        PrivacyAnalyzer.invalidateTimelineCache()
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
                    if (ev.packageName != null) put("packageName", ev.packageName)
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
            val usedIds = mutableSetOf<Long>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val sev = try {
                    AuditSeverity.valueOf(obj.optString("severity", "SAFE"))
                } catch (_: Exception) {
                    AuditSeverity.SAFE
                }
                var candidateId = obj.optLong("id", System.currentTimeMillis() + i)
                while (!usedIds.add(candidateId)) {
                    candidateId++
                }
                loaded.add(
                    GuardEvent(
                        id = candidateId,
                        timestamp = obj.optLong("timestamp", candidateId),
                        type = obj.optString("type", "AUDIT"),
                        title = obj.optString("title", "Guard Event"),
                        description = obj.optString("description", ""),
                        severity = sev,
                        packageName = obj.optString("packageName").takeIf { it.isNotBlank() }
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

        fun peekInstance(): LiveGuardMonitor? = INSTANCE

        internal fun resetInstanceForTests() {
            INSTANCE = null
        }

        fun getInstance(context: Context): LiveGuardMonitor {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: LiveGuardMonitor(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
