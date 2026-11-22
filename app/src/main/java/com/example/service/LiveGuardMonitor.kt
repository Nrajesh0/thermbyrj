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
    val isAppOpsIntrospectionAvailable: Boolean = false,
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
    val actionButtonLabel: String = "Open Settings",
    val isAcknowledged: Boolean = false,
    val currentSignature: String = ""
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

    private val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
    private val mediaRouter = try {
        context.getSystemService(Context.MEDIA_ROUTER_SERVICE) as? android.media.MediaRouter
    } catch (_: Throwable) {
        null
    }
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    private val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val bgExecutor = java.util.concurrent.Executors.newSingleThreadExecutor { runnable ->
        Thread({
            try {
                runnable.run()
            } catch (_: Throwable) {
            }
        }, "LiveGuardBgThread").apply {
            isDaemon = true
            uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { _, _ -> }
        }
    }
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

    private val _acknowledgedVectors = MutableStateFlow<Map<String, String>>(emptyMap())
    val acknowledgedVectors: StateFlow<Map<String, String>> = _acknowledgedVectors.asStateFlow()

    @Volatile
    private var cachedAuditVectors: List<SecurityAuditVector>? = null

    fun peekCachedAuditVectors(): List<SecurityAuditVector>? = cachedAuditVectors

    private val busyCameras = mutableSetOf<String>()
    @Volatile
    private var isMonitoring = false
    @Volatile
    private var lastScreenCaptureLogged = false
    @Volatile
    private var lastScreenCaptureAlertDispatched = false
    @Volatile
    private var lastMicLogged = false
    @Volatile
    private var lastMicAlertDispatched = false
    @Volatile
    private var lastCameraLogged = false
    @Volatile
    private var lastCameraAlertDispatched = false
    private val lastLocationAlertMs = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val lastClipboardAlertMs = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val knownVectorItemsMap = java.util.concurrent.ConcurrentHashMap<String, Set<String>>()
    @Volatile
    private var vectorBaselineInitialized = false
    @Volatile
    private var osScreenRecordingActive = false
    @Volatile
    private var isScreenInteractive = true
    private var screenReceiverRegistered = false
    private var displayListenerRegistered = false
    private var mediaRouterCallbackRegistered = false
    private var audioCallbackRegistered = false
    private var cameraCallbackRegistered = false
    private var clipboardListenerRegistered = false
    private var videoObserverRegistered = false
    private var settingsObserverRegistered = false
    @Volatile
    private var activeMediaRouterMirrorRoute: String? = null
    @Volatile
    private var appOpsIntrospectionAvailable: Boolean = false

    @Volatile
    private var pendingMicLogRunnable: Runnable? = null
    @Volatile
    private var pendingMicAlertRunnable: Runnable? = null
    @Volatile
    private var pendingCamLogRunnable: Runnable? = null
    @Volatile
    private var pendingCamAlertRunnable: Runnable? = null

    // Bounded label cache & launcher cache to avoid repeated PackageManager binder IPC
    private val appLabelCache = java.util.concurrent.ConcurrentHashMap<String, String>()
    @Volatile
    private var cachedLauncherPackages: Set<String> = emptySet()
    @Volatile
    private var cachedLauncherPackagesTimeMs: Long = 0L

    // Incremental UsageEvents state lock + cursors (avoids re-scanning 15-30m of events every tick)
    private val usageStatsLock = Any()
    private var lastUsageQueryEndMs: Long = 0L
    private var lastUsagePruneMs: Long = 0L
    private var cachedActiveFgPkg: String? = null
    private var cachedActiveFgClass: String? = null
    private var cachedActiveFgResumeTs: Long = 0L
    private var cachedLastNonSelfFgPkg: String? = null
    private var cachedLastNonSelfFgTs: Long = 0L
    private val cachedLastLeftMap = mutableMapOf<String, Long>()
    private val cachedActiveFgServiceStartMap = mutableMapOf<String, Long>()
    private val cachedActiveFgServiceStopMap = mutableMapOf<String, Long>()
    private val cachedRecServiceStartMap = mutableMapOf<String, Long>()
    private val cachedRecServiceStopMap = mutableMapOf<String, Long>()
    private val cachedRecLastResumeMap = mutableMapOf<String, Long>()

    private val appOpsLock = Any()
    private val auditLock = Any()
    @Volatile
    private var lastRefreshAllStatesMs: Long = 0L

    // Cached reflection Method handles to avoid lookup overhead inside nested loops
    private val appOpsGetPackagesForOpsMethod: java.lang.reflect.Method? by lazy {
        try {
            AppOpsManager::class.java.getMethod("getPackagesForOps", IntArray::class.java)
        } catch (_: Exception) {
            null
        }
    }
    private val targetedAppOpIds: IntArray? by lazy {
        try {
            val strOpToOp = AppOpsManager::class.java.getMethod("strOpToOp", String::class.java)
            val opNames = listOf(
                "android:camera",
                "android:record_audio",
                "android:fine_location",
                "android:coarse_location",
                "android:monitor_location",
                "android:monitor_location_high_power",
                "android:read_clipboard",
                "android:project_media",
                "android:read_contacts",
                "android:read_sms",
                "android:receive_sms",
                "android:send_sms",
                "android:read_call_log",
                "android:read_media_images",
                "android:read_media_video",
                "android:read_external_storage",
                "android:write_external_storage",
                "android:phone_call_microphone",
                "android:phone_call_camera",
                "android:receive_ambient_trigger_audio"
            )
            val resolved = opNames.mapNotNull { name ->
                try {
                    (strOpToOp.invoke(null, name) as? Int)?.takeIf { it >= 0 }
                } catch (_: Throwable) {
                    null
                }
            }.distinct().toIntArray()
            if (resolved.isNotEmpty()) resolved else null
        } catch (_: Throwable) {
            null
        }
    }
    @Volatile
    private var pkgOpsGetPackageNameMethod: java.lang.reflect.Method? = null
    @Volatile
    private var pkgOpsGetOpsMethod: java.lang.reflect.Method? = null
    @Volatile
    private var opEntryGetOpStrMethod: java.lang.reflect.Method? = null
    @Volatile
    private var opEntryGetModeMethod: java.lang.reflect.Method? = null
    @Volatile
    private var opEntryIsRunningMethod: java.lang.reflect.Method? = null
    @Volatile
    private var opEntryGetLastAccessTimeMethod: java.lang.reflect.Method? = null

    private val displayManagerGetHiddenMethod: java.lang.reflect.Method? by lazy {
        try {
            DisplayManager::class.java.getMethod("getDisplays", String::class.java)
        } catch (_: Exception) {
            null
        }
    }
    private val displayGetOwnerPkgMethod: java.lang.reflect.Method? by lazy {
        try {
            Display::class.java.getMethod("getOwnerPackageName")
        } catch (_: Exception) {
            null
        }
    }
    private val audioCfgGetClientPkgMethod: java.lang.reflect.Method? by lazy {
        try {
            AudioRecordingConfiguration::class.java.getMethod("getClientPackageName")
        } catch (_: Exception) {
            null
        }
    }
    private val audioCfgGetClientUidMethod: java.lang.reflect.Method? by lazy {
        try {
            AudioRecordingConfiguration::class.java.getMethod("getClientUid")
        } catch (_: Exception) {
            null
        }
    }

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
        "com.samsung.android.smartmirroring",
        "com.oneplus.screenrecord",
        "com.oneplus.asti",
        "com.coloros.screenrecorder",
        "com.coloros.oshare",
        "com.oplus.screenrecorder",
        "com.oplus.cast",
        "com.heytap.cast",
        "com.vivo.screenrecord",
        "com.vivo.upnpserver",
        "com.asus.screenrecorder",
        "com.huawei.screenrecorder",
        "com.hisilicon.miracast",
        "com.miui.misight",
        "com.milink.service",
        "com.xiaomi.mirror",
        "com.google.android.apps.chromecast.app",
        "com.hecorat.screenrecorder.free",
        "videoeditor.videorecorder.screenrecorder",
        "com.duapps.recorder",
        "com.kimcy929.screenrecorder",
        "com.nll.screenrecorder",
        "com.mobizen.mirroring.uimode"
    )

    private val debouncedDisplayAlertRunnable = Runnable {
        if (isMonitoring) {
            bgExecutor.execute {
                try {
                    val recentCachedOps = cachedPkgOpsList?.takeIf {
                        (System.currentTimeMillis() - cachedPkgOpsTimeMs) in 0L..10_000L
                    }
                    refreshDisplayCaptureState(
                        triggerAlertIfNew = true,
                        preloadedPkgOpsList = recentCachedOps
                    )
                } catch (_: Throwable) {
                }
            }
        }
    }

    private val debouncedDisplaySilentRunnable = Runnable {
        if (isMonitoring) {
            bgExecutor.execute {
                try {
                    val recentCachedOps = cachedPkgOpsList?.takeIf {
                        (System.currentTimeMillis() - cachedPkgOpsTimeMs) in 0L..10_000L
                    }
                    refreshDisplayCaptureState(
                        triggerAlertIfNew = false,
                        preloadedPkgOpsList = recentCachedOps
                    )
                } catch (_: Throwable) {
                }
            }
        }
    }

    private fun scheduleDisplayCaptureRefresh(triggerAlertIfNew: Boolean, delayMs: Long = 500L) {
        if (triggerAlertIfNew) {
            mainHandler.removeCallbacks(debouncedDisplayAlertRunnable)
            mainHandler.removeCallbacks(debouncedDisplaySilentRunnable)
            mainHandler.postDelayed(debouncedDisplayAlertRunnable, delayMs)
        } else {
            mainHandler.removeCallbacks(debouncedDisplaySilentRunnable)
            mainHandler.postDelayed(debouncedDisplaySilentRunnable, delayMs)
        }
    }

    private val debouncedClipboardRunnable = Runnable {
        if (isMonitoring) {
            bgExecutor.execute {
                try {
                    refreshLocationAndClipboardState(triggerAlertIfNew = true)
                } catch (_: Throwable) {
                }
            }
        }
    }

    private val debouncedSettingsAuditRunnable = Runnable {
        if (isMonitoring) {
            bgExecutor.execute {
                try {
                    cachedAuditVectors = null
                    cachedAuditVectorsTimeMs = 0L
                    runDeepSecurityAudit(includeSystemApps = false, logAuditEvent = false)
                } catch (_: Throwable) {
                }
            }
        }
    }

    private val settingsSecurityObserver = object : ContentObserver(mainHandler) {
        override fun onChange(selfChange: Boolean) {
            onChange(selfChange, null)
        }

        override fun onChange(selfChange: Boolean, uri: android.net.Uri?) {
            try {
                super.onChange(selfChange, uri)
                mainHandler.removeCallbacks(debouncedSettingsAuditRunnable)
                mainHandler.postDelayed(debouncedSettingsAuditRunnable, 400L)
            } catch (_: Throwable) {
            }
        }
    }

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {
            try {
                // Allow OEM Smart Sidebar / freeform window transition to finish before checking displays
                scheduleDisplayCaptureRefresh(triggerAlertIfNew = true, delayMs = 750L)
            } catch (_: Throwable) {
            }
        }

        override fun onDisplayRemoved(displayId: Int) {
            try {
                scheduleDisplayCaptureRefresh(triggerAlertIfNew = false, delayMs = 600L)
            } catch (_: Throwable) {
            }
        }

        override fun onDisplayChanged(displayId: Int) {
            try {
                // Coalesce frequent refresh-rate/brightness/PiP/sidebar onDisplayChanged bursts off the main thread
                scheduleDisplayCaptureRefresh(triggerAlertIfNew = true, delayMs = 800L)
            } catch (_: Throwable) {
            }
        }
    }

    private fun updateMediaRouterMirrorStateOnMain(triggerRefreshOnChange: Boolean) {
        try {
            val router = mediaRouter ?: return
            val liveVideoRoute = router.getSelectedRoute(android.media.MediaRouter.ROUTE_TYPE_LIVE_VIDEO)
            val detectedRoute: String? = if (liveVideoRoute != null) {
                val presDisplay = liveVideoRoute.presentationDisplay
                val routeName = liveVideoRoute.name?.toString().orEmpty()
                val isNonDefaultVideoRoute = presDisplay != null ||
                    (liveVideoRoute != router.defaultRoute &&
                        (routeName.contains("cast", ignoreCase = true) ||
                            routeName.contains("mirror", ignoreCase = true) ||
                            routeName.contains("tv", ignoreCase = true) ||
                            routeName.contains("display", ignoreCase = true) ||
                            routeName.contains("wireless", ignoreCase = true)))
                if (isNonDefaultVideoRoute) {
                    if (routeName.isNotBlank()) "Screen Mirroring ($routeName)" else "Screen Mirroring Route"
                } else {
                    null
                }
            } else {
                null
            }
            val changed = activeMediaRouterMirrorRoute != detectedRoute
            activeMediaRouterMirrorRoute = detectedRoute
            if (triggerRefreshOnChange && changed) {
                scheduleDisplayCaptureRefresh(triggerAlertIfNew = detectedRoute != null, delayMs = 250L)
            }
        } catch (_: Throwable) {
        }
    }

    private val mediaRouterCallback = object : android.media.MediaRouter.SimpleCallback() {
        override fun onRouteSelected(router: android.media.MediaRouter?, type: Int, info: android.media.MediaRouter.RouteInfo?) {
            updateMediaRouterMirrorStateOnMain(triggerRefreshOnChange = true)
        }

        override fun onRouteUnselected(router: android.media.MediaRouter?, type: Int, info: android.media.MediaRouter.RouteInfo?) {
            updateMediaRouterMirrorStateOnMain(triggerRefreshOnChange = true)
        }

        override fun onRoutePresentationDisplayChanged(router: android.media.MediaRouter?, info: android.media.MediaRouter.RouteInfo?) {
            updateMediaRouterMirrorStateOnMain(triggerRefreshOnChange = true)
        }

        override fun onRouteChanged(router: android.media.MediaRouter?, info: android.media.MediaRouter.RouteInfo?) {
            updateMediaRouterMirrorStateOnMain(triggerRefreshOnChange = true)
        }
    }

    private val videoMediaObserver = object : ContentObserver(mainHandler) {
        override fun onChange(selfChange: Boolean) {
            onChange(selfChange, null)
        }

        override fun onChange(selfChange: Boolean, uri: android.net.Uri?) {
            try {
                super.onChange(selfChange, uri)
                scheduleDisplayCaptureRefresh(triggerAlertIfNew = true, delayMs = 450L)
            } catch (_: Throwable) {
            }
        }
    }

    private val clipChangedListener = ClipboardManager.OnPrimaryClipChangedListener {
        try {
            // Re-check clipboard access shortly after clip changes so background snooping apps are caught immediately
            mainHandler.removeCallbacks(debouncedClipboardRunnable)
            mainHandler.postDelayed(debouncedClipboardRunnable, 650L)
        } catch (_: Throwable) {
        }
    }

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            try {
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
                            if (intent.action == Intent.ACTION_USER_PRESENT && synchronized(busyCameras) { busyCameras.isNotEmpty() }) {
                                updateCameraState()
                            }
                        }
                    }
                }
            } catch (_: Throwable) {
            }
        }
    }

    private var packageReceiverRegistered = false
    @Volatile
    private var lastPermissionDiffCheckMs: Long = 0L

    private val packageChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            try {
                val action = intent?.action ?: return
                if (action == Intent.ACTION_PACKAGE_ADDED ||
                    action == Intent.ACTION_PACKAGE_REPLACED ||
                    action == Intent.ACTION_PACKAGE_REMOVED
                ) {
                    bgExecutor.execute {
                        try {
                            PrivacyAnalyzer.invalidateAllCaches()
                            cachedAuditVectors = null
                            cachedAuditVectorsTimeMs = 0L
                            val summary = PrivacyAnalyzer.analyzeDevicePrivacy(
                                context,
                                includeSystemApps = false,
                                forceRefresh = true
                            )
                            PrivacyAnalyzer.getPrivacyChanges(
                                context = context,
                                currentReports = summary.appReports,
                                dispatchNotifications = true
                            )
                            val vectors = runDeepSecurityAudit(includeSystemApps = false, logAuditEvent = false)
                            evaluateAndNotifyLiveGuardChecks(vectors)
                            lastPermissionDiffCheckMs = System.currentTimeMillis()
                        } catch (_: Throwable) {
                        }
                    }
                }
            } catch (_: Throwable) {
            }
        }
    }

    private val periodicMonitorRunnable = object : Runnable {
        override fun run() {
            if (!isMonitoring) return
            val screenInteractive = isDeviceScreenInteractive()
            if (!_rulesConfig.value.pausePollingScreenOff || screenInteractive) {
                bgExecutor.execute {
                    try {
                        val pkgOps = fetchAllPackageOps()
                        val usageSnapshot = queryCombinedUsageState()
                        refreshMicAndCallState(
                            triggerAlertIfNew = true,
                            preloadedPkgOpsList = pkgOps,
                            preloadedUsageSnapshot = usageSnapshot
                        )
                        refreshDisplayCaptureState(
                            triggerAlertIfNew = true,
                            preloadedPkgOpsList = pkgOps,
                            preloadedUsageSnapshot = usageSnapshot
                        )
                        refreshLocationAndClipboardState(
                            triggerAlertIfNew = true,
                            preloadedPkgOpsList = pkgOps,
                            preloadedUsageSnapshot = usageSnapshot
                        )
                        val now = System.currentTimeMillis()
                        // Package installs/removals and system settings changes are already handled
                        // in real time by packageChangeReceiver and settingsSecurityObserver;
                        // only run the fallback background diff check every 15 minutes.
                        if (now - lastPermissionDiffCheckMs >= 15 * 60_000L) {
                            lastPermissionDiffCheckMs = now
                            try {
                                val summary = PrivacyAnalyzer.analyzeDevicePrivacy(
                                    context,
                                    includeSystemApps = false,
                                    forceRefresh = false
                                )
                                PrivacyAnalyzer.getPrivacyChanges(
                                    context = context,
                                    currentReports = summary.appReports,
                                    dispatchNotifications = true
                                )
                                val vectors = runDeepSecurityAudit(includeSystemApps = false, logAuditEvent = false)
                                evaluateAndNotifyLiveGuardChecks(vectors)
                            } catch (_: Throwable) {
                            }
                        }
                    } catch (_: Throwable) {
                    }
                }
            }
            // Adaptive polling: Mic, Camera, Screen & Clipboard are instant via OS callbacks;
            // polling only supplements background GPS & AppOps fallback (8s foreground, 25s background).
            val nextDelayMs = when {
                !screenInteractive -> 30_000L
                com.example.MainActivity.isUiForeground -> 8_000L
                else -> 25_000L
            }
            mainHandler.postDelayed(this, nextDelayMs)
        }
    }

    private val audioRecordingCallback = object : AudioManager.AudioRecordingCallback() {
        override fun onRecordingConfigChanged(configs: List<AudioRecordingConfiguration>) {
            super.onRecordingConfigChanged(configs)
            val configsSnapshot = try {
                configs.toList()
            } catch (_: Throwable) {
                emptyList()
            }
            val activeCount = configsSnapshot.size
            val isMicNow = activeCount > 0
            bgExecutor.execute {
                try {
                    val resolvedMic = if (isMicNow) {
                        resolveActiveSensorApps(
                            targetOpStr = "android:record_audio",
                            requiredPermission = android.Manifest.permission.RECORD_AUDIO,
                            audioConfigs = configsSnapshot
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
                        pendingMicLogRunnable?.let { mainHandler.removeCallbacks(it) }
                        pendingMicLogRunnable = null
                        pendingMicAlertRunnable?.let { mainHandler.removeCallbacks(it) }
                        pendingMicAlertRunnable = null
                        if (!isMicNow) {
                            lastMicLogged = false
                            lastMicAlertDispatched = false
                        }
                        return@execute
                    }

                    if (!lastMicLogged && pendingMicLogRunnable == null) {
                        val capturedResolved = unignoredMic
                        val logRunnable = Runnable {
                            pendingMicLogRunnable = null
                            bgExecutor.execute {
                                try {
                                    val currentConfigs = try {
                                        audioManager?.activeRecordingConfigurations?.toList() ?: emptyList()
                                    } catch (_: Throwable) {
                                        emptyList()
                                    }
                                    if (currentConfigs.isEmpty() || lastMicLogged) return@execute

                                    val freshResolved = resolveActiveSensorApps(
                                        targetOpStr = "android:record_audio",
                                        requiredPermission = android.Manifest.permission.RECORD_AUDIO,
                                        audioConfigs = currentConfigs
                                    )
                                    val freshUnignored = freshResolved.filterNot { isSensorTrustedOrSnoozed(it.first, GuardSensorType.MIC) }
                                    val freshSuppress = (freshResolved.isNotEmpty() && freshUnignored.isEmpty()) ||
                                        (freshResolved.isEmpty() && isGlobalSensorSnoozed(GuardSensorType.MIC))
                                    if (freshSuppress) return@execute

                                    val effectiveResolved = freshUnignored.ifEmpty { capturedResolved }
                                    lastMicLogged = true
                                    val who = if (effectiveResolved.isNotEmpty()) effectiveResolved.joinToString(", ") { it.second } else null
                                    val primaryPkg = effectiveResolved.firstOrNull()?.first
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
                                } catch (_: Throwable) {
                                }
                            }
                        }
                        pendingMicLogRunnable = logRunnable
                        mainHandler.postDelayed(logRunnable, 150L)
                    }

                    if (!lastMicAlertDispatched && pendingMicAlertRunnable == null) {
                        val delayMs = if (_rulesConfig.value.debounceEnabled) 1800L else 0L
                        val alertRunnable = Runnable {
                            pendingMicAlertRunnable = null
                            bgExecutor.execute {
                                try {
                                    val currentConfigs = try {
                                        audioManager?.activeRecordingConfigurations?.toList() ?: emptyList()
                                    } catch (_: Throwable) {
                                        emptyList()
                                    }
                                    if (currentConfigs.isEmpty() || lastMicAlertDispatched) return@execute

                                    val freshResolved = resolveActiveSensorApps(
                                        targetOpStr = "android:record_audio",
                                        requiredPermission = android.Manifest.permission.RECORD_AUDIO,
                                        audioConfigs = currentConfigs
                                    )
                                    val freshUnignored = freshResolved.filterNot { isSensorTrustedOrSnoozed(it.first, GuardSensorType.MIC) }
                                    val freshSuppress = (freshResolved.isNotEmpty() && freshUnignored.isEmpty()) ||
                                        (freshResolved.isEmpty() && isGlobalSensorSnoozed(GuardSensorType.MIC))
                                    if (freshSuppress) return@execute

                                    lastMicAlertDispatched = true
                                    val who = if (freshUnignored.isNotEmpty()) freshUnignored.joinToString(", ") { it.second } else null
                                    val primaryPkg = freshUnignored.firstOrNull()?.first
                                    val title = if (who != null) "Mic Active: $who" else "Microphone Active"
                                    val countNow = currentConfigs.size
                                    val desc = if (who != null) {
                                        "$who is recording audio ($countNow active stream${if (countNow == 1) "" else "s"})."
                                    } else {
                                        "$countNow audio stream(s) recording."
                                    }
                                    maybeDispatchGuardNotification(
                                        payload = GuardAlertPayload(title, desc, primaryPkg, GuardSensorType.MIC),
                                        audioConfigs = currentConfigs
                                    )
                                } catch (_: Throwable) {
                                }
                            }
                        }
                        pendingMicAlertRunnable = alertRunnable
                        if (delayMs == 0L) {
                            alertRunnable.run()
                        } else {
                            mainHandler.postDelayed(alertRunnable, delayMs)
                        }
                    }
                } catch (_: Throwable) {
                }
            }
        }
    }

    private val cameraCallback = object : CameraManager.AvailabilityCallback() {
        override fun onCameraAvailable(cameraId: String) {
            try {
                super.onCameraAvailable(cameraId)
                synchronized(busyCameras) {
                    busyCameras.remove(cameraId)
                }
                updateCameraState()
            } catch (_: Throwable) {
            }
        }

        override fun onCameraUnavailable(cameraId: String) {
            try {
                super.onCameraUnavailable(cameraId)
                synchronized(busyCameras) {
                    busyCameras.add(cameraId)
                }
                updateCameraState()
            } catch (_: Throwable) {
            }
        }
    }

    init {
        try {
            PreferencesBackupManager.autoRestoreIfCleared(context)
        } catch (_: Throwable) {
        }
        try {
            loadSavedEvents()
            loadRulesConfig()
            loadTrustedRules()
            loadSnoozes()
            loadAcknowledgedVectors()
            loadKnownVectorBaseline()
        } catch (_: Throwable) {
        }
    }

    fun reloadPersistedState() {
        loadRulesConfig()
        loadTrustedRules()
        loadSnoozes()
        loadAcknowledgedVectors()
        loadKnownVectorBaseline()
    }

    fun acknowledgeVector(vector: SecurityAuditVector) {
        val sig = vector.currentSignature.ifBlank {
            computeVectorSignature(vector.flaggedApps, vector.extraDetails)
        }
        if (sig.isBlank()) return
        val updated = _acknowledgedVectors.value.toMutableMap()
        updated[vector.id] = sig
        persistAcknowledgedVectors(updated)
    }

    fun unacknowledgeVector(vectorId: String) {
        val updated = _acknowledgedVectors.value.toMutableMap()
        updated.remove(vectorId)
        persistAcknowledgedVectors(updated)
    }

    private fun persistAcknowledgedVectors(map: Map<String, String>) {
        _acknowledgedVectors.value = map
        try {
            val obj = JSONObject()
            for ((k, v) in map) {
                obj.put(k, v)
            }
            prefs.edit().putString("acknowledged_vectors_json", obj.toString()).apply()
        } catch (_: Exception) {
        }
        cachedAuditVectors = null
        cachedAuditVectorsTimeMs = 0L
        PrivacyAnalyzer.invalidateTimelineCache()
    }

    private fun loadAcknowledgedVectors() {
        try {
            val raw = prefs.getString("acknowledged_vectors_json", null) ?: return
            val obj = JSONObject(raw)
            val map = mutableMapOf<String, String>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val v = obj.optString(k, "")
                if (k.isNotBlank() && v.isNotBlank()) {
                    map[k] = v
                }
            }
            _acknowledgedVectors.value = map
        } catch (_: Exception) {
        }
    }

    private fun loadKnownVectorBaseline() {
        try {
            vectorBaselineInitialized = prefs.getBoolean("vector_baseline_initialized_v3", false)
            val raw = prefs.getString("known_vector_items_v3_json", null) ?: return
            val obj = JSONObject(raw)
            knownVectorItemsMap.clear()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val arr = obj.optJSONArray(k) ?: continue
                val set = mutableSetOf<String>()
                for (i in 0 until arr.length()) {
                    val item = arr.optString(i)
                    if (item.isNotBlank()) set.add(item)
                }
                knownVectorItemsMap[k] = set
            }
        } catch (_: Exception) {
        }
    }

    private fun saveKnownVectorBaseline() {
        try {
            val obj = JSONObject()
            for ((k, set) in knownVectorItemsMap) {
                obj.put(k, JSONArray(set.toList()))
            }
            prefs.edit()
                .putBoolean("vector_baseline_initialized_v3", vectorBaselineInitialized)
                .putString("known_vector_items_v3_json", obj.toString())
                .apply()
        } catch (_: Exception) {
        }
    }

    private fun computeVectorSignature(
        flaggedApps: List<AppPrivacyReport>,
        extraDetails: List<String>
    ): String {
        val pkgs = flaggedApps.map { it.packageName }.sorted()
        val details = extraDetails.sorted()
        return (pkgs + details).joinToString("|")
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
        cachedAuditVectorsTimeMs = 0L
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
        cachedAuditVectorsTimeMs = 0L
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

    private fun isDeviceKeyguardLocked(): Boolean {
        return try {
            val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            km?.isKeyguardLocked == true
        } catch (_: Exception) {
            false
        }
    }

    private fun isScreenInteractiveAndUnlocked(): Boolean {
        if (!isDeviceScreenInteractive()) return false
        return !isDeviceKeyguardLocked()
    }

    private fun isActivePhoneOrVoipCall(audioConfigs: List<AudioRecordingConfiguration>): Boolean {
        val mode = try {
            audioManager?.mode ?: AudioManager.MODE_NORMAL
        } catch (_: Throwable) {
            AudioManager.MODE_NORMAL
        }
        if (mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION) {
            return true
        }
        return audioConfigs.any { cfg ->
            try {
                cfg.clientAudioSource == MediaRecorder.AudioSource.VOICE_COMMUNICATION
            } catch (_: Throwable) {
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
                    audioManager?.activeRecordingConfigurations ?: emptyList()
                } catch (_: Throwable) {
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

        val callback = onGuardAlert
        if (callback != null) {
            callback.invoke(payload)
        } else {
            val thermalPrefs = context.getSharedPreferences("thermal_prefs", Context.MODE_PRIVATE)
            if (thermalPrefs.getBoolean("live_guard_alerts", true)) {
                sendGuardNotificationDirect(payload)
            }
        }
        onHighPriorityAlert?.invoke(payload.title, payload.message)
    }

    fun sendGuardNotificationDirect(
        payload: GuardAlertPayload,
        customNotificationId: Int? = null,
        settingsAction: String? = null
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        try {
            NotificationChannels.ensureCreated(context)
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager ?: return
            val notifId = customNotificationId ?: (1003 + payload.sensorType.ordinal)

            val contentIntent = if (!settingsAction.isNullOrBlank()) {
                Intent(settingsAction).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
            } else {
                Intent(context, com.example.ui.screens.GuardRulesActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
            }
            val contentPi = android.app.PendingIntent.getActivity(
                context,
                3001 + notifId,
                contentIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )

            val snoozeIntent = Intent(context, GuardAlertActionReceiver::class.java).apply {
                action = GuardAlertActionReceiver.ACTION_SNOOZE_1H
                putExtra(GuardAlertActionReceiver.EXTRA_PACKAGE_NAME, payload.packageName)
                putExtra(GuardAlertActionReceiver.EXTRA_SENSOR_TYPE, payload.sensorType.name)
                putExtra(GuardAlertActionReceiver.EXTRA_NOTIFICATION_ID, notifId)
            }
            val snoozePi = android.app.PendingIntent.getBroadcast(
                context,
                3002 + (payload.packageName?.hashCode() ?: payload.sensorType.ordinal),
                snoozeIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )

            val builder = androidx.core.app.NotificationCompat.Builder(
                context,
                NotificationChannels.GUARD_ALERT_CHANNEL_ID
            )
                .setContentTitle(payload.title)
                .setContentText(payload.message)
                .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(payload.message))
                .setSmallIcon(com.example.R.drawable.ic_stat_dot)
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(contentPi)
                .addAction(0, "Snooze 1h", snoozePi)

            if (!payload.packageName.isNullOrBlank()) {
                val trustIntent = Intent(context, GuardAlertActionReceiver::class.java).apply {
                    action = GuardAlertActionReceiver.ACTION_TRUST_APP
                    putExtra(GuardAlertActionReceiver.EXTRA_PACKAGE_NAME, payload.packageName)
                    putExtra(GuardAlertActionReceiver.EXTRA_SENSOR_TYPE, payload.sensorType.name)
                    putExtra(GuardAlertActionReceiver.EXTRA_NOTIFICATION_ID, notifId)
                }
                val trustPi = android.app.PendingIntent.getBroadcast(
                    context,
                    3100 + payload.packageName.hashCode(),
                    trustIntent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                )
                builder.addAction(0, "Trust App", trustPi)
            }

            notificationManager.notify(notifId, builder.build())
        } catch (_: Throwable) {
        }
    }

    /**
     * Called by MainActivity's WindowManager screen recording callback (API 35+) safely from an Activity context.
     */
    fun onOsScreenRecordingStateChanged(isRecordingVisible: Boolean) {
        val changed = osScreenRecordingActive != isRecordingVisible
        osScreenRecordingActive = isRecordingVisible
        if (isRecordingVisible || changed) {
            scheduleDisplayCaptureRefresh(triggerAlertIfNew = true, delayMs = 80L)
        }
    }

    fun isMonitoringActive(): Boolean = isMonitoring

    fun startMonitoring() {
        if (isMonitoring) {
            val now = System.currentTimeMillis()
            if ((now - lastRefreshAllStatesMs) > 5_000L) {
                bgExecutor.execute {
                    try {
                        refreshAllStates()
                    } catch (_: Throwable) {
                    }
                }
            }
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
            } catch (_: Throwable) {
            }
        }
        if (!packageReceiverRegistered) {
            try {
                val pkgFilter = IntentFilter().apply {
                    addAction(Intent.ACTION_PACKAGE_ADDED)
                    addAction(Intent.ACTION_PACKAGE_REPLACED)
                    addAction(Intent.ACTION_PACKAGE_REMOVED)
                    addDataScheme("package")
                }
                ContextCompat.registerReceiver(
                    context,
                    packageChangeReceiver,
                    pkgFilter,
                    ContextCompat.RECEIVER_NOT_EXPORTED
                )
                packageReceiverRegistered = true
            } catch (_: Throwable) {
            }
        }
        if (!displayListenerRegistered) {
            try {
                val dm = displayManager
                if (dm != null) {
                    dm.registerDisplayListener(displayListener, mainHandler)
                    displayListenerRegistered = true
                }
            } catch (_: Throwable) {
            }
        }
        if (!mediaRouterCallbackRegistered && mediaRouter != null) {
            val registerRouter = {
                if (isMonitoring && !mediaRouterCallbackRegistered) {
                    try {
                        mediaRouter.addCallback(
                            android.media.MediaRouter.ROUTE_TYPE_LIVE_VIDEO,
                            mediaRouterCallback,
                            android.media.MediaRouter.CALLBACK_FLAG_UNFILTERED_EVENTS
                        )
                        mediaRouterCallbackRegistered = true
                        updateMediaRouterMirrorStateOnMain(triggerRefreshOnChange = false)
                    } catch (_: Throwable) {
                    }
                }
            }
            if (Looper.myLooper() == Looper.getMainLooper()) {
                registerRouter()
            } else {
                mainHandler.post(registerRouter)
            }
        }
        if (!audioCallbackRegistered) {
            try {
                val am = audioManager
                if (am != null) {
                    am.registerAudioRecordingCallback(audioRecordingCallback, mainHandler)
                    audioCallbackRegistered = true
                }
            } catch (_: Throwable) {
            }
        }
        if (!cameraCallbackRegistered) {
            try {
                val cm = cameraManager
                if (cm != null) {
                    cm.registerAvailabilityCallback(cameraCallback, mainHandler)
                    cameraCallbackRegistered = true
                }
            } catch (_: Throwable) {
            }
        }
        if (!clipboardListenerRegistered) {
            try {
                clipboardManager?.addPrimaryClipChangedListener(clipChangedListener)
                clipboardListenerRegistered = clipboardManager != null
            } catch (_: Throwable) {
            }
        }
        if (!videoObserverRegistered) {
            try {
                context.contentResolver.registerContentObserver(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    true,
                    videoMediaObserver
                )
                videoObserverRegistered = true
            } catch (_: Throwable) {
            }
        }
        if (!settingsObserverRegistered) {
            try {
                context.contentResolver.registerContentObserver(
                    Settings.Secure.CONTENT_URI,
                    true,
                    settingsSecurityObserver
                )
                context.contentResolver.registerContentObserver(
                    Settings.Global.CONTENT_URI,
                    true,
                    settingsSecurityObserver
                )
                settingsObserverRegistered = true
            } catch (_: Throwable) {
            }
        }
        mainHandler.removeCallbacks(periodicMonitorRunnable)
        val initialDelayMs = if (com.example.MainActivity.isUiForeground) 10_000L else 30_000L
        mainHandler.postDelayed(periodicMonitorRunnable, initialDelayMs)
        bgExecutor.execute {
            try {
                refreshAllStates()
                runDeepSecurityAudit(includeSystemApps = false, logAuditEvent = false)
            } catch (_: Throwable) {
            }
        }
    }

    fun stopMonitoring() {
        isMonitoring = false
        mainHandler.removeCallbacks(periodicMonitorRunnable)
        mainHandler.removeCallbacks(debouncedDisplayAlertRunnable)
        mainHandler.removeCallbacks(debouncedDisplaySilentRunnable)
        mainHandler.removeCallbacks(debouncedClipboardRunnable)
        mainHandler.removeCallbacks(debouncedSettingsAuditRunnable)
        pendingMicLogRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingMicLogRunnable = null
        pendingMicAlertRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingMicAlertRunnable = null
        pendingCamLogRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingCamLogRunnable = null
        pendingCamAlertRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingCamAlertRunnable = null

        if (screenReceiverRegistered) {
            try {
                context.unregisterReceiver(screenStateReceiver)
            } catch (_: Throwable) {
            }
            screenReceiverRegistered = false
        }
        if (packageReceiverRegistered) {
            try {
                context.unregisterReceiver(packageChangeReceiver)
            } catch (_: Throwable) {
            }
            packageReceiverRegistered = false
        }
        if (displayListenerRegistered) {
            try {
                displayManager?.unregisterDisplayListener(displayListener)
            } catch (_: Throwable) {
            }
            displayListenerRegistered = false
        }
        if (mediaRouterCallbackRegistered) {
            val unregisterRouter = {
                try {
                    mediaRouter?.removeCallback(mediaRouterCallback)
                } catch (_: Throwable) {
                }
                mediaRouterCallbackRegistered = false
                activeMediaRouterMirrorRoute = null
            }
            if (Looper.myLooper() == Looper.getMainLooper()) {
                unregisterRouter()
            } else {
                mainHandler.post(unregisterRouter)
            }
        }
        if (audioCallbackRegistered) {
            try {
                audioManager?.unregisterAudioRecordingCallback(audioRecordingCallback)
            } catch (_: Throwable) {
            }
            audioCallbackRegistered = false
        }
        if (cameraCallbackRegistered) {
            try {
                cameraManager?.unregisterAvailabilityCallback(cameraCallback)
            } catch (_: Throwable) {
            }
            cameraCallbackRegistered = false
        }
        if (clipboardListenerRegistered) {
            try {
                clipboardManager?.removePrimaryClipChangedListener(clipChangedListener)
            } catch (_: Throwable) {
            }
            clipboardListenerRegistered = false
        }
        if (videoObserverRegistered) {
            try {
                context.contentResolver.unregisterContentObserver(videoMediaObserver)
            } catch (_: Throwable) {
            }
            videoObserverRegistered = false
        }
        if (settingsObserverRegistered) {
            try {
                context.contentResolver.unregisterContentObserver(settingsSecurityObserver)
            } catch (_: Throwable) {
            }
            settingsObserverRegistered = false
        }
    }

    fun refreshAllStates() {
        try {
            lastRefreshAllStatesMs = System.currentTimeMillis()
            val pkgOps = fetchAllPackageOps()
            val usageSnapshot = queryCombinedUsageState()
            refreshMicAndCallState(
                triggerAlertIfNew = true,
                preloadedPkgOpsList = pkgOps,
                preloadedUsageSnapshot = usageSnapshot
            )
            refreshDisplayCaptureState(
                triggerAlertIfNew = false,
                preloadedPkgOpsList = pkgOps,
                preloadedUsageSnapshot = usageSnapshot
            )
            refreshLocationAndClipboardState(
                triggerAlertIfNew = false,
                preloadedPkgOpsList = pkgOps,
                preloadedUsageSnapshot = usageSnapshot
            )
        } catch (_: Throwable) {
        }
    }

    private fun refreshMicAndCallState(
        triggerAlertIfNew: Boolean,
        preloadedPkgOpsList: List<*>? = null,
        preloadedUsageSnapshot: ForegroundUsageSnapshot? = null
    ) {
        try {
            val configs = try {
                audioManager?.activeRecordingConfigurations?.toList() ?: emptyList()
            } catch (_: Throwable) {
                emptyList()
            }
            val audioMode = try {
                audioManager?.mode ?: AudioManager.MODE_NORMAL
            } catch (_: Throwable) {
                AudioManager.MODE_NORMAL
            }
            val isInCallOrComm = audioMode == AudioManager.MODE_IN_CALL ||
                audioMode == AudioManager.MODE_IN_COMMUNICATION
            val isMicNow = configs.isNotEmpty() || isInCallOrComm

            val usageSnapshot = preloadedUsageSnapshot ?: queryCombinedUsageState()
            val resolvedMic = if (isMicNow) {
                val base = resolveActiveSensorApps(
                    targetOpStr = "android:record_audio",
                    requiredPermission = android.Manifest.permission.RECORD_AUDIO,
                    audioConfigs = configs,
                    preloadedPkgOpsList = preloadedPkgOpsList,
                    preloadedUsageSnapshot = usageSnapshot
                ).toMutableList()
                if (base.isEmpty() && isInCallOrComm && isScreenInteractiveAndUnlocked()) {
                    // Only attribute to the currently active unlocked foreground app (never guess from unrelated background services)
                    val fgPkg = usageSnapshot.activeForegroundPackage
                    val pm = context.packageManager
                    if (fgPkg != null && fgPkg != context.packageName && fgPkg !in getLauncherPackages()) {
                        val hasCallOrMicPerm = pm.checkPermission(android.Manifest.permission.RECORD_AUDIO, fgPkg) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
                            pm.checkPermission(android.Manifest.permission.CALL_PHONE, fgPkg) == android.content.pm.PackageManager.PERMISSION_GRANTED
                        if (hasCallOrMicPerm) {
                            base.add(fgPkg to resolveAppLabel(fgPkg))
                        }
                    }
                }
                base
            } else {
                emptyList()
            }

            val unignoredMic = resolvedMic.filterNot { isSensorTrustedOrSnoozed(it.first, GuardSensorType.MIC) }
            val micSuppressed = (resolvedMic.isNotEmpty() && unignoredMic.isEmpty()) ||
                (resolvedMic.isEmpty() && isGlobalSensorSnoozed(GuardSensorType.MIC))
            val effectiveSessions = if (micSuppressed) 0 else maxOf(configs.size, if (isInCallOrComm) 1 else 0)

            _captureState.value = _captureState.value.copy(
                isMicActive = isMicNow && !micSuppressed,
                activeMicSessions = effectiveSessions,
                activeMicApps = unignoredMic.map { it.second },
                isVpnOrProxyActive = false,
                lastUpdatedMs = System.currentTimeMillis()
            )

            if (!isMicNow || micSuppressed) {
                if (!isMicNow) {
                    lastMicLogged = false
                    lastMicAlertDispatched = false
                }
                return
            }

            if (!lastMicLogged) {
                lastMicLogged = true
                val who = if (unignoredMic.isNotEmpty()) unignoredMic.joinToString(", ") { it.second } else null
                val primaryPkg = unignoredMic.firstOrNull()?.first
                val title = when {
                    isInCallOrComm && who != null -> "Call / Mic Active: $who"
                    isInCallOrComm -> "Phone Call / Mic Active"
                    who != null -> "Mic Active: $who"
                    else -> "Microphone Active"
                }
                val desc = when {
                    isInCallOrComm && who != null -> "$who is actively using call & microphone audio right now."
                    isInCallOrComm -> "Voice call audio & microphone are active right now."
                    who != null -> "$who is recording audio ($effectiveSessions active stream${if (effectiveSessions == 1) "" else "s"})."
                    else -> "$effectiveSessions audio stream(s) recording."
                }
                recordEvent(
                    type = "MIC",
                    title = title,
                    description = desc,
                    severity = AuditSeverity.WARNING,
                    packageName = primaryPkg
                )
            }

            if (triggerAlertIfNew && !lastMicAlertDispatched) {
                lastMicAlertDispatched = true
                val who = if (unignoredMic.isNotEmpty()) unignoredMic.joinToString(", ") { it.second } else null
                val primaryPkg = unignoredMic.firstOrNull()?.first
                val title = if (who != null) "Mic Active: $who" else "Microphone Active"
                val desc = if (who != null) {
                    "$who is actively using the microphone right now."
                } else {
                    "Microphone is actively recording audio right now."
                }
                maybeDispatchGuardNotification(
                    payload = GuardAlertPayload(title, desc, primaryPkg, GuardSensorType.MIC),
                    audioConfigs = configs
                )
            }
        } catch (_: Throwable) {
        }
    }

    internal fun updateCameraState() {
        val currentBusy = synchronized(busyCameras) { busyCameras.toSet() }
        // Ignore transient lock-screen camera activations (Face Unlock / biometric camera warm-up)
        val isKeyguardLocked = isDeviceKeyguardLocked()
        val isCamNow = currentBusy.isNotEmpty() && !isKeyguardLocked
        bgExecutor.execute {
            try {
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
                    busyCameraIds = if (isCamNow && !shouldSuppressAlert) currentBusy else emptySet(),
                    activeCameraApps = if (isCamNow && !shouldSuppressAlert) unignoredCam.map { it.second } else emptyList(),
                    lastUpdatedMs = System.currentTimeMillis()
                )
                if (!isCamNow || shouldSuppressAlert) {
                    pendingCamLogRunnable?.let { mainHandler.removeCallbacks(it) }
                    pendingCamLogRunnable = null
                    pendingCamAlertRunnable?.let { mainHandler.removeCallbacks(it) }
                    pendingCamAlertRunnable = null
                    if (!isCamNow) {
                        lastCameraLogged = false
                        lastCameraAlertDispatched = false
                    }
                    return@execute
                }

                // Log verified camera usage quickly (150ms settle) so quick snaps (e.g. WhatsApp camera)
                // appear in the Timeline even when notification debouncing (1800ms) is enabled.
                if (!lastCameraLogged && pendingCamLogRunnable == null) {
                    val capturedIds = currentBusy
                    val capturedResolved = unignoredCam
                    val logRunnable = Runnable {
                        pendingCamLogRunnable = null
                        bgExecutor.execute {
                            try {
                                val busyNow = synchronized(busyCameras) { busyCameras.toSet() }
                                if (busyNow.isEmpty() || isDeviceKeyguardLocked() || lastCameraLogged) return@execute

                                val freshResolved = resolveActiveSensorApps(
                                    targetOpStr = "android:camera",
                                    requiredPermission = android.Manifest.permission.CAMERA
                                )
                                val freshUnignored = freshResolved.filterNot { isSensorTrustedOrSnoozed(it.first, GuardSensorType.CAMERA) }
                                val freshSuppress = (freshResolved.isNotEmpty() && freshUnignored.isEmpty()) ||
                                    (freshResolved.isEmpty() && isGlobalSensorSnoozed(GuardSensorType.CAMERA))
                                if (freshSuppress) return@execute

                                val effectiveResolved = freshUnignored.ifEmpty { capturedResolved }
                                lastCameraLogged = true
                                val who = if (effectiveResolved.isNotEmpty()) effectiveResolved.joinToString(", ") { it.second } else null
                                val primaryPkg = effectiveResolved.firstOrNull()?.first
                                val idsText = (busyNow.ifEmpty { capturedIds }).joinToString()
                                val title = if (who != null) "Camera Active: $who" else "Camera Active"
                                val desc = if (who != null) {
                                    "$who is using camera lens $idsText."
                                } else {
                                    "Lens $idsText in use."
                                }
                                recordEvent(
                                    type = "CAMERA",
                                    title = title,
                                    description = desc,
                                    severity = AuditSeverity.WARNING,
                                    packageName = primaryPkg
                                )
                            } catch (_: Throwable) {
                            }
                        }
                    }
                    pendingCamLogRunnable = logRunnable
                    mainHandler.postDelayed(logRunnable, 150L)
                }

                if (!lastCameraAlertDispatched && pendingCamAlertRunnable == null) {
                    val delayMs = if (_rulesConfig.value.debounceEnabled) 1800L else 0L
                    val alertRunnable = Runnable {
                        pendingCamAlertRunnable = null
                        bgExecutor.execute {
                            try {
                                val busyNow = synchronized(busyCameras) { busyCameras.toSet() }
                                if (busyNow.isEmpty() || isDeviceKeyguardLocked() || lastCameraAlertDispatched) return@execute

                                val freshResolved = resolveActiveSensorApps(
                                    targetOpStr = "android:camera",
                                    requiredPermission = android.Manifest.permission.CAMERA
                                )
                                val freshUnignored = freshResolved.filterNot { isSensorTrustedOrSnoozed(it.first, GuardSensorType.CAMERA) }
                                val freshSuppress = (freshResolved.isNotEmpty() && freshUnignored.isEmpty()) ||
                                    (freshResolved.isEmpty() && isGlobalSensorSnoozed(GuardSensorType.CAMERA))
                                if (freshSuppress) return@execute

                                lastCameraAlertDispatched = true
                                val who = if (freshUnignored.isNotEmpty()) freshUnignored.joinToString(", ") { it.second } else null
                                val primaryPkg = freshUnignored.firstOrNull()?.first
                                val title = if (who != null) "Camera Active: $who" else "Camera Active"
                                val desc = if (who != null) {
                                    "$who is using camera lens ${busyNow.joinToString()}."
                                } else {
                                    "Lens ${busyNow.joinToString()} in use."
                                }
                                maybeDispatchGuardNotification(
                                    payload = GuardAlertPayload(title, desc, primaryPkg, GuardSensorType.CAMERA)
                                )
                            } catch (_: Throwable) {
                            }
                        }
                    }
                    pendingCamAlertRunnable = alertRunnable
                    if (delayMs == 0L) {
                        alertRunnable.run()
                    } else {
                        mainHandler.postDelayed(alertRunnable, delayMs)
                    }
                }
            } catch (_: Throwable) {
            }
        }
    }

    @Volatile
    private var cachedPkgOpsList: List<*>? = null
    @Volatile
    private var cachedPkgOpsTimeMs: Long = 0L

    fun getCachedOrFetchPackageOps(forceFresh: Boolean = false): List<*>? =
        fetchAllPackageOps(forceFresh = forceFresh)

    private fun fetchAllPackageOps(forceFresh: Boolean = false): List<*>? {
        val now = System.currentTimeMillis()
        if (!forceFresh) {
            val cached = cachedPkgOpsList
            if (cached != null && (now - cachedPkgOpsTimeMs) in 0L..4_500L) {
                return cached
            }
        }
        synchronized(appOpsLock) {
            val nowLocked = System.currentTimeMillis()
            if (!forceFresh) {
                val cached = cachedPkgOpsList
                if (cached != null && (nowLocked - cachedPkgOpsTimeMs) in 0L..4_500L) {
                    return cached
                }
            }
            return try {
                val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return cachedPkgOpsList
                val method = appOpsGetPackagesForOpsMethod ?: return cachedPkgOpsList
                // Pass targetedAppOpIds instead of null so AppOpsService filters in system_server
                // and returns a ~15KB Binder parcel instead of a 700KB+ parcel of all 140 ops across 300 packages.
                val opIds = targetedAppOpIds
                val result = (try {
                    method.invoke(appOps, opIds) as? List<*>
                } catch (_: Throwable) {
                    null
                }) ?: (try {
                    method.invoke(appOps, null) as? List<*>
                } catch (_: Throwable) {
                    null
                })
                if (result != null) {
                    appOpsIntrospectionAvailable = true
                    cachedPkgOpsList = result
                    cachedPkgOpsTimeMs = System.currentTimeMillis()
                    result
                } else {
                    if (cachedPkgOpsList == null) {
                        appOpsIntrospectionAvailable = false
                    }
                    cachedPkgOpsList
                }
            } catch (_: Throwable) {
                if (cachedPkgOpsList == null) {
                    appOpsIntrospectionAvailable = false
                }
                cachedPkgOpsList
            }
        }
    }

    private fun extractPkgOpsNameAndOps(pkgOps: Any): Pair<String, List<*>>? {
        return try {
            val nameMethod = pkgOpsGetPackageNameMethod ?: pkgOps.javaClass.getMethod("getPackageName").also {
                pkgOpsGetPackageNameMethod = it
            }
            val opsMethod = pkgOpsGetOpsMethod ?: pkgOps.javaClass.getMethod("getOps").also {
                pkgOpsGetOpsMethod = it
            }
            val pkgName = nameMethod.invoke(pkgOps) as? String ?: return null
            val ops = opsMethod.invoke(pkgOps) as? List<*> ?: return null
            pkgName to ops
        } catch (_: Exception) {
            null
        }
    }

    private fun extractOpStr(opEntry: Any): String? {
        return try {
            val m = opEntryGetOpStrMethod ?: opEntry.javaClass.getMethod("getOpStr").also {
                opEntryGetOpStrMethod = it
            }
            m.invoke(opEntry) as? String
        } catch (_: Exception) {
            null
        }
    }

    private fun extractOpIsRunning(opEntry: Any): Boolean {
        return try {
            val m = opEntryIsRunningMethod ?: opEntry.javaClass.getMethod("isRunning").also {
                opEntryIsRunningMethod = it
            }
            (m.invoke(opEntry) as? Boolean) == true
        } catch (_: Exception) {
            false
        }
    }

    private fun extractOpMode(opEntry: Any): Int {
        return try {
            val m = opEntryGetModeMethod ?: opEntry.javaClass.getMethod("getMode").also {
                opEntryGetModeMethod = it
            }
            (m.invoke(opEntry) as? Int) ?: AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) {
            AppOpsManager.MODE_ALLOWED
        }
    }

    private fun extractOpLastAccessTime(opEntry: Any): Long {
        return try {
            val m = opEntryGetLastAccessTimeMethod ?: opEntry.javaClass.getMethod(
                "getLastAccessTime",
                Int::class.javaPrimitiveType
            ).also {
                opEntryGetLastAccessTimeMethod = it
            }
            (m.invoke(opEntry, 0x15) as? Long) ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    private fun resolveActiveSensorApps(
        targetOpStr: String,
        requiredPermission: String,
        audioConfigs: List<AudioRecordingConfiguration> = emptyList(),
        preloadedPkgOpsList: List<*>? = null,
        preloadedUsageSnapshot: ForegroundUsageSnapshot? = null
    ): List<Pair<String, String>> {
        val pm = context.packageManager
        val now = System.currentTimeMillis()
        val foundPackages = linkedSetOf<String>()

        // 1. Try AudioRecordingConfiguration client UID / package via cached reflection
        for (cfg in audioConfigs) {
            try {
                val clientPkg = audioCfgGetClientPkgMethod?.invoke(cfg) as? String
                if (!clientPkg.isNullOrBlank() && clientPkg != context.packageName) {
                    foundPackages.add(clientPkg)
                }
            } catch (_: Exception) {
            }
            try {
                val uid = audioCfgGetClientUidMethod?.invoke(cfg) as? Int
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
            val pkgOpsList = preloadedPkgOpsList ?: fetchAllPackageOps()
            if (pkgOpsList != null) {
                for (pkgOps in pkgOpsList) {
                    if (pkgOps == null) continue
                    val (pkgName, ops) = extractPkgOpsNameAndOps(pkgOps) ?: continue
                    if (pkgName == context.packageName) continue
                    for (opEntry in ops) {
                        if (opEntry == null) continue
                        val opStr = extractOpStr(opEntry) ?: continue
                        if (opStr != targetOpStr) continue

                        val isRunning = extractOpIsRunning(opEntry)
                        val lastTime = extractOpLastAccessTime(opEntry)
                        if (isRunning || (now - lastTime) in 0L..5_000L) {
                            foundPackages.add(pkgName)
                        }
                    }
                }
            }
        }

        // 3. Conservative UsageStatsManager fallback:
        // Only attribute to the currently RESUMED foreground app while the screen is unlocked.
        // Never blindly blame unrelated background foreground-service apps merely because they hold a permission;
        // when Android hides the active sensor client UID, leave the caller unnamed rather than accusing the wrong app.
        if (foundPackages.isEmpty() && isScreenInteractiveAndUnlocked()) {
            val fgSnapshot = preloadedUsageSnapshot ?: queryCombinedUsageState()
            val launchers = getLauncherPackages()
            val fgPkg = fgSnapshot.activeForegroundPackage
            if (fgPkg != null &&
                fgPkg != context.packageName &&
                fgPkg !in launchers &&
                pm.checkPermission(requiredPermission, fgPkg) == android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                foundPackages.add(fgPkg)
            }
        }

        return foundPackages.map { pkg ->
            pkg to resolveAppLabel(pkg)
        }.distinctBy { it.first }.take(3)
    }

    private data class ForegroundUsageSnapshot(
        val usageAccessGranted: Boolean,
        val activeForegroundPackage: String?,
        val lastLeftForegroundMs: Map<String, Long>,
        val lastNonSelfForegroundPackage: String? = null,
        val activeForegroundServicePackages: List<String> = emptyList(),
        val recorderServiceStarts: Map<String, Long> = emptyMap(),
        val recorderServiceStops: Map<String, Long> = emptyMap(),
        val recorderLastResumes: Map<String, Long> = emptyMap()
    )

    private fun getLauncherPackages(): Set<String> {
        val now = System.currentTimeMillis()
        if (cachedLauncherPackages.isNotEmpty() && (now - cachedLauncherPackagesTimeMs) < 5L * 60_000L) {
            return cachedLauncherPackages
        }
        val resolved = try {
            val intent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
                addCategory(android.content.Intent.CATEGORY_HOME)
            }
            context.packageManager.queryIntentActivities(intent, 0)
                .mapNotNull { it.activityInfo?.packageName }
                .toSet()
        } catch (_: Exception) {
            emptySet()
        }
        cachedLauncherPackages = resolved
        cachedLauncherPackagesTimeMs = now
        return resolved
    }

    private fun queryForegroundUsageState(lookbackMs: Long = 15L * 60_000L): ForegroundUsageSnapshot {
        return queryCombinedUsageState(lookbackMs)
    }

    private fun queryCombinedUsageState(initialLookbackMs: Long = 30L * 60_000L): ForegroundUsageSnapshot {
        if (!PrivacyAnalyzer.hasUsageStatsPermission(context)) {
            return ForegroundUsageSnapshot(
                usageAccessGranted = false,
                activeForegroundPackage = null,
                lastLeftForegroundMs = emptyMap()
            )
        }
        return try {
            val now = System.currentTimeMillis()
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: return ForegroundUsageSnapshot(usageAccessGranted = false, activeForegroundPackage = null, lastLeftForegroundMs = emptyMap())
            val launchers = getLauncherPackages()
            synchronized(usageStatsLock) {
                val queryStart = if (lastUsageQueryEndMs == 0L || now < lastUsageQueryEndMs || (now - lastUsageQueryEndMs) > 10L * 60_000L) {
                    cachedActiveFgPkg = null
                    cachedActiveFgClass = null
                    cachedActiveFgResumeTs = 0L
                    cachedLastNonSelfFgPkg = null
                    cachedLastNonSelfFgTs = 0L
                    cachedLastLeftMap.clear()
                    cachedActiveFgServiceStartMap.clear()
                    cachedActiveFgServiceStopMap.clear()
                    cachedRecServiceStartMap.clear()
                    cachedRecServiceStopMap.clear()
                    cachedRecLastResumeMap.clear()
                    now - initialLookbackMs
                } else {
                    (lastUsageQueryEndMs - 1_000L).coerceAtLeast(now - initialLookbackMs)
                }

                val events = usm.queryEvents(queryStart, now)
                val ev = UsageEvents.Event()

                while (events != null && events.hasNextEvent()) {
                    events.getNextEvent(ev)
                    val pkg = ev.packageName ?: continue
                    val clsLower = ev.className?.lowercase().orEmpty()
                    // Strictly match explicit screen recording / projection components only (never generic "recordingservice" which matches call recorders like Truecaller!)
                    val isCaptureComponent = clsLower.contains("screenrecord") ||
                        clsLower.contains("screen_record") ||
                        clsLower.contains("screencapture") ||
                        clsLower.contains("screen_capture") ||
                        clsLower.contains("mediaprojection") ||
                        clsLower.contains("smartmirroring") ||
                        clsLower.contains("casttile")
                    val isRecorderPkg = isScreenRecorderPackage(pkg) || isCaptureComponent
                    when (ev.eventType) {
                        UsageEvents.Event.ACTIVITY_RESUMED -> {
                            if (ev.timeStamp >= cachedActiveFgResumeTs) {
                                cachedActiveFgResumeTs = ev.timeStamp
                                cachedActiveFgPkg = pkg
                                cachedActiveFgClass = ev.className
                            }
                            if (pkg != context.packageName && pkg !in launchers && ev.timeStamp >= cachedLastNonSelfFgTs) {
                                cachedLastNonSelfFgTs = ev.timeStamp
                                cachedLastNonSelfFgPkg = pkg
                            }
                            if (isRecorderPkg) {
                                cachedRecLastResumeMap[pkg] = maxOf(cachedRecLastResumeMap[pkg] ?: 0L, ev.timeStamp)
                            }
                        }
                        UsageEvents.Event.ACTIVITY_PAUSED,
                        UsageEvents.Event.ACTIVITY_STOPPED -> {
                            cachedLastLeftMap[pkg] = maxOf(cachedLastLeftMap[pkg] ?: 0L, ev.timeStamp)
                            val sameActivity = cachedActiveFgClass == null || ev.className == null || ev.className == cachedActiveFgClass
                            if (pkg == cachedActiveFgPkg && sameActivity && ev.timeStamp >= cachedActiveFgResumeTs) {
                                cachedActiveFgPkg = null
                                cachedActiveFgClass = null
                            }
                        }
                        16 /* UsageEvents.Event.SCREEN_NON_INTERACTIVE */ -> {
                            if (ev.timeStamp >= cachedActiveFgResumeTs) {
                                cachedActiveFgPkg?.let { cachedLastLeftMap[it] = maxOf(cachedLastLeftMap[it] ?: 0L, ev.timeStamp) }
                                cachedActiveFgPkg = null
                                cachedActiveFgClass = null
                            }
                        }
                        UsageEvents.Event.FOREGROUND_SERVICE_START -> {
                            if (pkg != context.packageName) {
                                cachedActiveFgServiceStartMap[pkg] = maxOf(cachedActiveFgServiceStartMap[pkg] ?: 0L, ev.timeStamp)
                            }
                            if (isRecorderPkg) {
                                cachedRecServiceStartMap[pkg] = maxOf(cachedRecServiceStartMap[pkg] ?: 0L, ev.timeStamp)
                            }
                        }
                        UsageEvents.Event.FOREGROUND_SERVICE_STOP -> {
                            if (pkg in cachedActiveFgServiceStartMap) {
                                cachedActiveFgServiceStopMap[pkg] = maxOf(cachedActiveFgServiceStopMap[pkg] ?: 0L, ev.timeStamp)
                            }
                            if (isRecorderPkg || pkg in cachedRecServiceStartMap) {
                                cachedRecServiceStopMap[pkg] = maxOf(cachedRecServiceStopMap[pkg] ?: 0L, ev.timeStamp)
                            }
                        }
                    }
                }
                lastUsageQueryEndMs = now

                if (now - lastUsagePruneMs > 5L * 60_000L) {
                    val staleCutoff = now - 35L * 60_000L
                    cachedLastLeftMap.entries.removeAll { it.value < staleCutoff }
                    cachedActiveFgServiceStartMap.entries.removeAll { it.value < staleCutoff }
                    cachedActiveFgServiceStopMap.entries.removeAll { it.value < staleCutoff }
                    cachedRecServiceStartMap.entries.removeAll { it.value < staleCutoff }
                    cachedRecServiceStopMap.entries.removeAll { it.value < staleCutoff }
                    cachedRecLastResumeMap.entries.removeAll { it.value < staleCutoff }
                    lastUsagePruneMs = now
                }

                val activeFgServices = cachedActiveFgServiceStartMap.entries
                    .filter { (pkg, startTs) ->
                        val stopTs = cachedActiveFgServiceStopMap[pkg] ?: 0L
                        startTs > stopTs && (now - startTs) < 30L * 60_000L
                    }
                    .sortedByDescending { it.value }
                    .map { it.key }

                val recentNonSelfFg = cachedLastNonSelfFgPkg?.takeIf { (now - cachedLastNonSelfFgTs) < 10L * 60_000L }

                ForegroundUsageSnapshot(
                    usageAccessGranted = true,
                    activeForegroundPackage = cachedActiveFgPkg,
                    lastLeftForegroundMs = HashMap(cachedLastLeftMap),
                    lastNonSelfForegroundPackage = recentNonSelfFg,
                    activeForegroundServicePackages = activeFgServices,
                    recorderServiceStarts = HashMap(cachedRecServiceStartMap),
                    recorderServiceStops = HashMap(cachedRecServiceStopMap),
                    recorderLastResumes = HashMap(cachedRecLastResumeMap)
                )
            }
        } catch (_: Exception) {
            ForegroundUsageSnapshot(
                usageAccessGranted = false,
                activeForegroundPackage = null,
                lastLeftForegroundMs = emptyMap()
            )
        }
    }

    private fun refreshLocationAndClipboardState(
        triggerAlertIfNew: Boolean,
        preloadedPkgOpsList: List<*>? = null,
        preloadedUsageSnapshot: ForegroundUsageSnapshot? = null
    ) {
        try {
            val now = System.currentTimeMillis()
            val fgSnapshot = preloadedUsageSnapshot ?: queryCombinedUsageState()
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
            val pkgOpsList = preloadedPkgOpsList ?: fetchAllPackageOps()
            if (pkgOpsList == null) {
                _captureState.value = _captureState.value.copy(
                    isBackgroundLocationActive = false,
                    activeLocationApps = emptyList(),
                    isClipboardAccessedRecently = false,
                    recentClipboardApps = emptyList(),
                    isAppOpsIntrospectionAvailable = false,
                    lastUpdatedMs = now
                )
                return
            }

            val locPackages = linkedMapOf<String, String>()
            val clipPackages = linkedMapOf<String, String>()

            for (pkgOps in pkgOpsList) {
                if (pkgOps == null) continue
                val (pkgName, ops) = extractPkgOpsNameAndOps(pkgOps) ?: continue
                if (pkgName == context.packageName) continue
                if (pkgName == fgPkg) continue

                val lastLeftFg = fgSnapshot.lastLeftForegroundMs[pkgName] ?: 0L

                for (opEntry in ops) {
                    if (opEntry == null) continue
                    val opStr = extractOpStr(opEntry) ?: continue
                    val opMode = extractOpMode(opEntry)
                    if (opMode != AppOpsManager.MODE_ALLOWED && opMode != 4 /* MODE_FOREGROUND */) {
                        continue
                    }

                    val isRunning = extractOpIsRunning(opEntry)
                    val lastTime = extractOpLastAccessTime(opEntry)

                    // Ensure the access actually happened while out of foreground (not right before closing the app)
                    val happenedAfterLeavingFg = lastLeftFg == 0L || lastTime > (lastLeftFg + 2_500L)
                    // For clipboard, strictly require verified prior foreground exit (> 5s ago) so normal user pastes are never flagged
                    val verifiedBackgroundClipboardRead = lastLeftFg > 0L && lastTime > (lastLeftFg + 5_000L)

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
                            locPackages[pkgName] = resolveAppLabel(pkgName)
                        }
                    }

                    if (opStr == "android:read_clipboard" &&
                        !isSensorTrustedOrSnoozed(pkgName, GuardSensorType.CLIPBOARD) &&
                        (now - lastTime) in 0L..8_000L &&
                        verifiedBackgroundClipboardRead &&
                        !PrivacyAnalyzer.isBrandOrOemPackage(pkgName) &&
                        pkgName != "com.android.systemui"
                    ) {
                        clipPackages[pkgName] = resolveAppLabel(pkgName)
                    }
                }
            }

            _captureState.value = _captureState.value.copy(
                isBackgroundLocationActive = locPackages.isNotEmpty(),
                activeLocationApps = locPackages.values.toList(),
                isClipboardAccessedRecently = clipPackages.isNotEmpty(),
                recentClipboardApps = clipPackages.values.toList(),
                isAppOpsIntrospectionAvailable = true,
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
        appLabelCache[pkg]?.let { return it }
        val resolved = try {
            val pm = context.packageManager
            val ai = pm.getApplicationInfo(pkg, 0)
            pm.getApplicationLabel(ai).toString()
        } catch (_: Exception) {
            pkg
        }
        if (appLabelCache.size > 300) {
            appLabelCache.clear()
        }
        appLabelCache[pkg] = resolved
        return resolved
    }

    private fun refreshDisplayCaptureState(
        triggerAlertIfNew: Boolean,
        preloadedPkgOpsList: List<*>? = null,
        preloadedUsageSnapshot: ForegroundUsageSnapshot? = null
    ) {
        try {
            val captureIndicators = mutableListOf<String>()
            val allCandidatePackages = linkedSetOf<String>()
            var hasMirrorOrCastDisplay = false

            if (osScreenRecordingActive) {
                captureIndicators.add("OS Screen Recording")
            }

            // 1. Standard + Presentation + Hidden Virtual Displays (DisplayManager)
            val allDisplays = mutableMapOf<Int, Display>()
            try {
                displayManager?.displays?.forEach { allDisplays[it.displayId] = it }
            } catch (_: Throwable) {
            }
            try {
                displayManager?.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)?.forEach {
                    allDisplays[it.displayId] = it
                }
            } catch (_: Throwable) {
            }
            try {
                val dm = displayManager
                val allInc = if (dm != null) {
                    displayManagerGetHiddenMethod?.invoke(
                        dm,
                        "android.hardware.display.category.ALL_INCLUDING_DISABLED"
                    ) as? Array<*>
                } else {
                    null
                }
                allInc?.filterIsInstance<Display>()?.forEach { allDisplays[it.displayId] = it }
            } catch (_: Throwable) {
            }

            allDisplays.values.filter { display ->
                display.displayId != Display.DEFAULT_DISPLAY && display.state != Display.STATE_OFF
            }.forEach { display ->
                val dispName = display.name ?: "Virtual Display #${display.displayId}"
                val dispLower = dispName.lowercase()
                val isPresentation = (display.flags and Display.FLAG_PRESENTATION) != 0
                val ownerPkg = try {
                    displayGetOwnerPkgMethod?.invoke(display) as? String
                } catch (_: Throwable) {
                    null
                }
                val ownerLower = ownerPkg?.lowercase().orEmpty()
                val isOemFreeformOrSidebarDisplay = dispLower.contains("freeform") ||
                    dispLower.contains("miniwindow") ||
                    dispLower.contains("multiwindow") ||
                    dispLower.contains("smartsidebar") ||
                    dispLower.contains("sidebar") ||
                    dispLower.contains("edgepanel") ||
                    dispLower.contains("pip") ||
                    dispLower.contains("floating") ||
                    dispLower.contains("popup") ||
                    dispLower.contains("split") ||
                    dispLower.contains("activityview") ||
                    dispLower.contains("taskvirtualdisplay") ||
                    ownerLower.contains("freeform") ||
                    ownerLower.contains("smartsidebar") ||
                    ownerLower.contains("sidebar") ||
                    ownerLower.contains("cocktailbar") ||
                    ownerLower.contains("multiwindow") ||
                    ownerLower.contains("floating") ||
                    (ownerPkg != null && !isScreenRecorderPackage(ownerPkg) && (
                        ownerLower.startsWith("com.miui.") ||
                            ownerLower.startsWith("com.xiaomi.") ||
                            ownerLower.startsWith("com.oplus.") ||
                            ownerLower.startsWith("com.coloros.") ||
                            ownerLower.startsWith("com.heytap.") ||
                            ownerLower.startsWith("com.vivo.") ||
                            ownerLower.startsWith("com.samsung.android.app.cocktailbarservice")
                        ))
                val isCaptureOrMirrorDisplay = !isOemFreeformOrSidebarDisplay && (
                    isPresentation ||
                        dispLower.contains("mirror") ||
                        dispLower.contains("cast") ||
                        dispLower.contains("wireless") ||
                        dispLower.contains("smart view") ||
                        dispLower.contains("screenrecord") ||
                        dispLower.contains("screencapture") ||
                        dispLower.contains("wfd") ||
                        dispLower.contains("scrcpy") ||
                        (ownerPkg != null && isScreenRecorderPackage(ownerPkg))
                    )
                if (isCaptureOrMirrorDisplay) {
                    if (isPresentation ||
                        dispLower.contains("mirror") ||
                        dispLower.contains("cast") ||
                        dispLower.contains("wireless") ||
                        dispLower.contains("smart view") ||
                        dispLower.contains("wfd")
                    ) {
                        hasMirrorOrCastDisplay = true
                    }
                    captureIndicators.add(dispName)
                    if (!ownerPkg.isNullOrBlank() && ownerPkg != context.packageName) {
                        allCandidatePackages.add(ownerPkg)
                    }
                }
            }

            // 1b. Live MediaRouter Wireless Display / Screen Mirroring route (kept updated on mainHandler)
            val mirrorRouteIndicator = activeMediaRouterMirrorRoute
            if (!mirrorRouteIndicator.isNullOrBlank()) {
                hasMirrorOrCastDisplay = true
                if (captureIndicators.none { it.equals(mirrorRouteIndicator, ignoreCase = true) }) {
                    captureIndicators.add(mirrorRouteIndicator)
                }
            }

            // 2. Internal Audio Loopback (REMOTE_SUBMIX = 8) & client UID attribution
            val audioConfigs = try {
                audioManager?.activeRecordingConfigurations?.toList() ?: emptyList()
            } catch (_: Throwable) {
                emptyList()
            }
            val remoteSubmixConfigs = audioConfigs.filter { cfg ->
                try {
                    cfg.clientAudioSource == MediaRecorder.AudioSource.REMOTE_SUBMIX
                } catch (_: Throwable) {
                    false
                }
            }
            if (remoteSubmixConfigs.isNotEmpty()) {
                captureIndicators.add("Internal Audio Loopback")
                val pm = context.packageManager
                for (cfg in remoteSubmixConfigs) {
                    try {
                        val clientPkg = audioCfgGetClientPkgMethod?.invoke(cfg) as? String
                        if (!clientPkg.isNullOrBlank() && clientPkg != context.packageName) {
                            allCandidatePackages.add(clientPkg)
                        }
                    } catch (_: Throwable) {
                    }
                    try {
                        val uid = audioCfgGetClientUidMethod?.invoke(cfg) as? Int
                        if (uid != null && uid >= 10000) {
                            pm.getPackagesForUid(uid)?.forEach { pkg ->
                                if (pkg != context.packageName) allCandidatePackages.add(pkg)
                            }
                        }
                    } catch (_: Throwable) {
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
                hasOtherCaptureSignal = hasHardwareOrFileSignal,
                preloadedPkgOpsList = preloadedPkgOpsList,
                preloadedUsageSnapshot = preloadedUsageSnapshot
            )
            allCandidatePackages.addAll(activeRecorderPkgs)

            // 5. Legacy running services check ONLY when a hardware/display/file signal is already present
            if (hasHardwareOrFileSignal) {
                allCandidatePackages.addAll(detectRunningCapturePackages())
            }

            val unignoredPackages = allCandidatePackages
                .filterNot { isSensorTrustedOrSnoozed(it, GuardSensorType.SCREEN) }
                .distinct()

            val suppressedByTrustedOnly = allCandidatePackages.isNotEmpty() && unignoredPackages.isEmpty()

            unignoredPackages.forEach { pkg ->
                val label = resolveAppLabel(pkg)
                if (pkg.contains("mirror", ignoreCase = true) ||
                    pkg.contains("cast", ignoreCase = true) ||
                    pkg.contains("milink", ignoreCase = true) ||
                    pkg.contains("oshare", ignoreCase = true)
                ) {
                    hasMirrorOrCastDisplay = true
                }
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

            if (isCapturing) {
                val detail = distinctIndicators.joinToString(", ")
                val primaryPkg = unignoredPackages.firstOrNull()
                val who = unignoredPackages
                    .map { resolveAppLabel(it) }
                    .distinct()
                    .takeIf { it.isNotEmpty() }
                    ?.joinToString(", ")

                val actionNoun = if (hasMirrorOrCastDisplay && activeRecordingHit == null && !osScreenRecordingActive) {
                    "Screen Mirroring"
                } else if (hasMirrorOrCastDisplay) {
                    "Screen Recording / Mirroring"
                } else {
                    "Screen Recording"
                }
                val eventTitle = if (who != null) "$actionNoun: $who" else "$actionNoun Detected"
                val alertMsg = if (who != null) {
                    "$who is recording or mirroring your screen ($detail)."
                } else {
                    "$actionNoun active ($detail)."
                }

                if (!lastScreenCaptureLogged) {
                    lastScreenCaptureLogged = true
                    recordEvent(
                        type = "SCREEN",
                        title = eventTitle,
                        description = alertMsg,
                        severity = AuditSeverity.DANGER,
                        packageName = primaryPkg
                    )
                }
                if (triggerAlertIfNew && !lastScreenCaptureAlertDispatched) {
                    lastScreenCaptureAlertDispatched = true
                    maybeDispatchGuardNotification(
                        GuardAlertPayload(
                            title = eventTitle,
                            message = alertMsg,
                            packageName = primaryPkg,
                            sensorType = GuardSensorType.SCREEN
                        )
                    )
                }
            } else {
                lastScreenCaptureLogged = false
                lastScreenCaptureAlertDispatched = false
            }
        } catch (_: Throwable) {
        }
    }

    private fun isScreenRecorderPackage(pkg: String): Boolean {
        val lower = pkg.lowercase()
        return lower in knownScreenRecorders ||
            lower.contains("screenrecorder") ||
            lower.contains("screenrecord") ||
            lower.contains("screencapture") ||
            lower.contains("smartmirroring") ||
            lower.contains("screencast")
    }

    private fun detectActiveScreenRecorderPackages(
        isMicOrAudioActive: Boolean,
        hasOtherCaptureSignal: Boolean = false,
        preloadedPkgOpsList: List<*>? = null,
        preloadedUsageSnapshot: ForegroundUsageSnapshot? = null
    ): List<String> {
        val activeRecorders = mutableSetOf<String>()
        val recentProjectionCandidates = mutableListOf<Pair<String, Long>>()
        val now = System.currentTimeMillis()

        // A. Check AppOpsManager for running or just-accessed OP_PROJECT_MEDIA or active screen recorder ops
        val pkgOpsList = preloadedPkgOpsList ?: fetchAllPackageOps()
        if (pkgOpsList != null) {
            for (pkgOps in pkgOpsList) {
                if (pkgOps == null) continue
                val (pkgName, ops) = extractPkgOpsNameAndOps(pkgOps) ?: continue
                if (pkgName == context.packageName) continue
                for (opEntry in ops) {
                    if (opEntry == null) continue
                    val opStr = extractOpStr(opEntry) ?: continue
                    val isRunning = extractOpIsRunning(opEntry)
                    if (isRunning && (opStr == "android:project_media" || isScreenRecorderPackage(pkgName))) {
                        activeRecorders.add(pkgName)
                    } else if (hasOtherCaptureSignal && opStr == "android:project_media") {
                        val lastTime = extractOpLastAccessTime(opEntry)
                        if (lastTime > 0L && (now - lastTime) in 0L..12_000L) {
                            recentProjectionCandidates.add(pkgName to lastTime)
                        }
                    }
                }
            }
        }

        if (activeRecorders.isEmpty() && recentProjectionCandidates.isNotEmpty()) {
            recentProjectionCandidates
                .sortedByDescending { it.second }
                .firstOrNull()
                ?.let { activeRecorders.add(it.first) }
        }

        // B. Use UsageStats recorder hints ONLY to attribute a package name when a real OS/display/file/audio
        // capture signal is already active. Never treat historical recorder service starts or app resumes as
        // standalone live recording triggers (OEM recorders often omit FOREGROUND_SERVICE_STOP cleanly).
        if (hasOtherCaptureSignal) {
            val usageSnap = preloadedUsageSnapshot ?: queryCombinedUsageState()
            if (usageSnap.usageAccessGranted) {
                for ((pkg, startTs) in usageSnap.recorderServiceStarts) {
                    val stopTs = usageSnap.recorderServiceStops[pkg] ?: 0L
                    if (startTs > stopTs && (now - startTs) < 10L * 60L * 1000L) {
                        activeRecorders.add(pkg)
                    }
                }
                if (activeRecorders.isEmpty() && isMicOrAudioActive) {
                    for ((pkg, resumeTs) in usageSnap.recorderLastResumes) {
                        if ((now - resumeTs) < 2L * 60L * 1000L) {
                            activeRecorders.add(pkg)
                        }
                    }
                }
            }
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

        // 1. Check MediaStore STRICTLY for IS_PENDING = 1 (actively open for write right now).
        // Completed videos (even recent ones) are NOT active recordings and must not cause false alerts.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val projection = arrayOf(
                    MediaStore.Video.Media.DISPLAY_NAME,
                    MediaStore.Video.Media.RELATIVE_PATH,
                    MediaStore.Video.Media.OWNER_PACKAGE_NAME,
                    MediaStore.Video.Media.DATE_MODIFIED
                )
                val staleCutoffSec = (now - 120_000L) / 1000L
                context.contentResolver.query(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    "${MediaStore.Video.Media.IS_PENDING} = 1 AND ${MediaStore.Video.Media.DATE_MODIFIED} >= ?",
                    arrayOf(staleCutoffSec.toString()),
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

        // 2. Check dedicated ScreenRecorder directories strictly for active temp recording buffers (.tmp / .part)
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
                    if ((nameLower.endsWith(".tmp") || nameLower.endsWith(".part") || nameLower.endsWith(".recording")) &&
                        ageMs in 0..15_000L
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
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return emptyList()
            val services = am.getRunningServices(100) ?: emptyList()
            services.mapNotNull { srv ->
                val cls = srv.service.className.lowercase()
                val pkg = srv.service.packageName
                if (pkg != context.packageName &&
                    (cls.contains("screenrecord") ||
                        cls.contains("screen_record") ||
                        cls.contains("screencapture") ||
                        cls.contains("screen_capture") ||
                        cls.contains("mediaprojection") ||
                        isScreenRecorderPackage(pkg))
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
        val desc = "Screen, Mirroring, Screen Reader & Mic alerts are active."
        recordEvent(
            type = "SCREEN",
            title = title,
            description = "Real-time screen, mirroring, accessibility & mic alerts are working.",
            severity = AuditSeverity.SAFE
        )
        val payload = GuardAlertPayload(title, desc, null, GuardSensorType.SCREEN)
        val callback = onGuardAlert
        if (callback != null) {
            callback.invoke(payload)
        } else {
            sendGuardNotificationDirect(payload)
        }
        onHighPriorityAlert?.invoke(title, desc)
        notifyActiveChecksNow()
    }

    fun notifyActiveChecksNow() {
        bgExecutor.execute {
            try {
                val vectors = runDeepSecurityAudit(includeSystemApps = false, logAuditEvent = false)
                evaluateAndNotifyLiveGuardChecks(vectors, forceNotifyActive = true)
            } catch (_: Throwable) {
            }
        }
    }

    @Volatile
    private var cachedAuditVectorsTimeMs: Long = 0L
    @Volatile
    private var cachedAuditIncludeSystem: Boolean = false

    fun runDeepSecurityAudit(
        includeSystemApps: Boolean = false,
        logAuditEvent: Boolean = false
    ): List<SecurityAuditVector> {
        val nowMs = System.currentTimeMillis()
        if (!logAuditEvent) {
            val existing = cachedAuditVectors
            if (existing != null &&
                cachedAuditIncludeSystem == includeSystemApps &&
                (nowMs - cachedAuditVectorsTimeMs) in 0L..15_000L &&
                (nowMs - _captureState.value.lastUpdatedMs) in 0L..10_000L
            ) {
                evaluateAndNotifyLiveGuardChecks(existing, forceNotifyActive = false)
                return existing
            }
        }
        synchronized(auditLock) {
            val nowLocked = System.currentTimeMillis()
            if (!logAuditEvent) {
                val existing = cachedAuditVectors
                if (existing != null &&
                    cachedAuditIncludeSystem == includeSystemApps &&
                    (nowLocked - cachedAuditVectorsTimeMs) in 0L..15_000L &&
                    (nowLocked - _captureState.value.lastUpdatedMs) in 0L..10_000L
                ) {
                    evaluateAndNotifyLiveGuardChecks(existing, forceNotifyActive = false)
                    return existing
                }
            }
            val pkgOps = fetchAllPackageOps(forceFresh = logAuditEvent)
        if (logAuditEvent || (!isMonitoring && (nowMs - _captureState.value.lastUpdatedMs) > 5_000L)) {
            val usageSnap = queryCombinedUsageState()
            refreshMicAndCallState(
                triggerAlertIfNew = logAuditEvent,
                preloadedPkgOpsList = pkgOps,
                preloadedUsageSnapshot = usageSnap
            )
            refreshDisplayCaptureState(
                triggerAlertIfNew = logAuditEvent,
                preloadedPkgOpsList = pkgOps,
                preloadedUsageSnapshot = usageSnap
            )
            refreshLocationAndClipboardState(
                triggerAlertIfNew = logAuditEvent,
                preloadedPkgOpsList = pkgOps,
                preloadedUsageSnapshot = usageSnap
            )
        }
        val live = _captureState.value
        val privacySummary = PrivacyAnalyzer.analyzeDevicePrivacy(context, includeSystemApps = includeSystemApps)
        val rules = _trustedRules.value
        val allApps = privacySummary.appReports.filterNot { app ->
            val r = rules[app.packageName]
            r != null && r.trustedSensors.size >= GuardSensorType.entries.size
        }

        // 1. Screen Recording / Mirroring
        val screenVector = if (live.isScreenRecordingOrCasting) {
            SecurityAuditVector(
                id = "screen_recording",
                title = "Screen Recording / Mirroring",
                plainSummary = "Screen is being recorded or mirrored now",
                detailedAdvice = "Active screen capture or wireless mirroring detected. Stop unknown recordings or casts from your notification shade.",
                severity = AuditSeverity.DANGER,
                statusBadge = "LIVE CAPTURE",
                extraDetails = live.activeCaptureDisplays,
                settingsAction = Settings.ACTION_CAST_SETTINGS,
                actionButtonLabel = "Cast Settings"
            )
        } else {
            SecurityAuditVector(
                id = "screen_recording",
                title = "Screen Recording / Mirroring",
                plainSummary = "No active screen capture or mirroring",
                detailedAdvice = "Monitors DisplayManager, MediaRouter casts, audio loopback, AppOps, and ScreenRecorder services in real time.",
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
        val resolvedActiveNames = (live.activeMicApps + live.activeCameraApps).toSet()
        val activeMicCamMatchedApps = if (micCamActive && resolvedActiveNames.isNotEmpty()) {
            micCamApps.filter { app ->
                app.appName in resolvedActiveNames || app.packageName in resolvedActiveNames
            }
        } else {
            emptyList()
        }
        val activeMicCamDetails = buildList {
            if (live.isMicActive) {
                val micWho = live.activeMicApps.takeIf { it.isNotEmpty() }?.joinToString(", ")
                    ?: "App hidden by Android OS"
                add("Microphone active (${live.activeMicSessions.coerceAtLeast(1)} stream): $micWho")
            }
            if (live.isCameraActive) {
                val camWho = live.activeCameraApps.takeIf { it.isNotEmpty() }?.joinToString(", ")
                    ?: "App hidden by Android OS"
                add("Camera active (Lens ${live.busyCameraIds.joinToString().ifBlank { "?" }}): $camWho")
            }
        }
        val micCamVector = if (micCamActive) {
            SecurityAuditVector(
                id = "live_mic_cam",
                title = "Mic & Camera Live",
                plainSummary = activeMicCamDetails.firstOrNull() ?: "Sensor is actively recording right now",
                detailedAdvice = "Hardware sensor is active right now. If Android hides the exact app name, check your green status-bar privacy indicator or OS Privacy Dashboard.",
                severity = AuditSeverity.DANGER,
                statusBadge = "ACTIVE NOW",
                flaggedApps = activeMicCamMatchedApps,
                extraDetails = activeMicCamDetails,
                settingsAction = Settings.ACTION_PRIVACY_SETTINGS,
                actionButtonLabel = "Privacy Controls"
            )
        } else {
            SecurityAuditVector(
                id = "live_mic_cam",
                title = "Mic & Camera Live",
                plainSummary = "Idle • ${micCamApps.size} apps have permission",
                detailedAdvice = "Monitors live OS AudioRecordingCallback & CameraManager callbacks. Revoke Camera or Mic access for apps that don't need voice or video.",
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
            detailedAdvice = if (live.isAppOpsIntrospectionAvailable) {
                "Apps with Always-Allow location can track your movement even when closed. Change to 'Only while using' or ignore trusted apps."
            } else {
                "Audits every app granted 24/7 Always-Allow GPS permission. Android blocks third-party apps from reading live background GPS AppOps logs — check OS Privacy Dashboard for per-minute GPS history."
            },
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
            queryRecentClipboardReaderApps(allApps, preloadedPkgOpsList = pkgOps)
                .filterNot { isSensorTrusted(it.packageName, GuardSensorType.CLIPBOARD) }
        } catch (_: Exception) {
            emptyList()
        }
        val clipboardVector = SecurityAuditVector(
            id = "clipboard_access",
            title = "Clipboard Protection",
            plainSummary = when {
                live.isClipboardAccessedRecently -> "Read recently by ${live.recentClipboardApps.joinToString(", ")}"
                clipboardTimelineApps.isNotEmpty() -> "${clipboardTimelineApps.size} app(s) read clipboard recently"
                !live.isAppOpsIntrospectionAvailable -> "Protected by Android 10+ (OS Privacy Controls)"
                else -> "No background clipboard reads detected"
            },
            detailedAdvice = if (live.isAppOpsIntrospectionAvailable) {
                "Monitors AppOps clipboard reads in the background to catch silent password, OTP, or link snooping."
            } else {
                "Android 10+ automatically blocks background apps from reading your clipboard and shows a toast when any foreground app pastes. Android restricts raw clipboard AppOps logs to the OS Privacy Dashboard."
            },
            severity = when {
                live.isClipboardAccessedRecently -> AuditSeverity.WARNING
                clipboardTimelineApps.isNotEmpty() -> AuditSeverity.WARNING
                else -> AuditSeverity.SAFE
            },
            statusBadge = when {
                live.isClipboardAccessedRecently -> "READ NOW"
                clipboardTimelineApps.isNotEmpty() -> "${clipboardTimelineApps.size} RECENT"
                !live.isAppOpsIntrospectionAvailable -> "OS GUARDED"
                else -> "SAFE"
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

        // 6. Accessibility Screen-Readers (exclude stock OS / OEM accessibility helpers unless system apps requested)
        val accessibilityApps = allApps.filter { it.hasAccessibilityAccess }
        val stockAccessibilityAllowlist = setOf(
            "com.google.android.marvin.talkback",
            "com.google.android.accessibility.switchaccess",
            "com.samsung.accessibility"
        )
        val enabledRawServices = PrivacyAnalyzer.getEnabledAccessibilityPackages(context)
            .filter { pkg ->
                pkg != context.packageName &&
                    pkg !in fullIgnored &&
                    (includeSystemApps || (!PrivacyAnalyzer.isBrandOrOemPackage(pkg) && pkg !in stockAccessibilityAllowlist))
            }
        val accessCount = maxOf(accessibilityApps.size, enabledRawServices.size)
        val accessibilityVector = SecurityAuditVector(
            id = "accessibility_monitors",
            title = "Screen Readers",
            plainSummary = if (accessCount == 0) "No third-party apps reading screen text" else "$accessCount service(s) can read all screen text",
            detailedAdvice = "Spyware often uses Accessibility to read chats and passwords. Disable unknown third-party services.",
            severity = if (accessCount > 0) AuditSeverity.DANGER else AuditSeverity.SAFE,
            statusBadge = if (accessCount == 0) "SAFE" else "$accessCount ACTIVE",
            flaggedApps = accessibilityApps,
            extraDetails = enabledRawServices.map { "Service: ${resolveAppLabel(it)} ($it)" },
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
            extraDetails = rawNotifListeners.map { "Listener: ${resolveAppLabel(it)} ($it)" },
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
            extraDetails = untrustedKeyboards.map { "Keyboard: ${resolveAppLabel(it)} ($it)" },
            settingsAction = Settings.ACTION_INPUT_METHOD_SETTINGS,
            actionButtonLabel = "Keyboard Settings"
        )

        // 10. Sideloaded Apps, Package Verifier & USB Debugging (excludes MIUI / HyperOS / Brand apps)
        val sideloadedApps = allApps.filter { it.isSideloaded && !PrivacyAnalyzer.isBrandOrOemPackage(it.packageName) }
        val adbEnabled = try {
            Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1
        } catch (_: Exception) {
            false
        }
        val verifierEnabled = OfflineThreatScanner.isPackageVerifierEnabled(context)
        val sysFlags = buildList {
            if (adbEnabled) add("USB Debugging (ADB) is ON")
            if (!verifierEnabled) add("OS Package Verifier is OFF")
            if (sideloadedApps.isNotEmpty()) add("${sideloadedApps.size} sideloaded APK(s)")
        }
        val sideloadVector = SecurityAuditVector(
            id = "sideload_adb",
            title = "Sideloads & USB Debug",
            plainSummary = if (sysFlags.isEmpty()) "Verified store/brand apps & ADB off" else sysFlags.joinToString(" • "),
            detailedAdvice = "Turn off USB Debugging when not needed and review third-party APKs installed outside official app stores.",
            severity = if (adbEnabled || !verifierEnabled) AuditSeverity.DANGER else if (sideloadedApps.isNotEmpty()) AuditSeverity.WARNING else AuditSeverity.SAFE,
            statusBadge = if (sysFlags.isEmpty()) "SAFE" else "REVIEW",
            flaggedApps = sideloadedApps,
            extraDetails = sysFlags,
            settingsAction = if (adbEnabled) Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS else Settings.ACTION_MANAGE_ALL_APPLICATIONS_SETTINGS,
            actionButtonLabel = if (adbEnabled) "Developer Options" else "Review Apps"
        )

        // 11. Offline Stalkerware, Ghost Apps & System Impersonation
        val stealthAndImpersonatingApps = allApps.filter { app ->
            !app.isSystem && (
                app.isHiddenStealthApp ||
                    app.isImpersonatingSystemOrBrand ||
                    app.threatSignals.any {
                        it.category == ThreatSignalCategory.STEALTH_STALKERWARE ||
                            it.category == ThreatSignalCategory.IMPERSONATION
                    }
                )
        }
        val stealthExtraDetails = stealthAndImpersonatingApps.flatMap { app ->
            app.threatSignals
                .filter {
                    it.category == ThreatSignalCategory.STEALTH_STALKERWARE ||
                        it.category == ThreatSignalCategory.IMPERSONATION
                }
                .map { "${app.appName}: ${it.title} — ${it.description}" }
        }
        val stealthVector = SecurityAuditVector(
            id = "stealth_stalkerware",
            title = "Stealth & Stalkerware Check",
            plainSummary = if (stealthAndImpersonatingApps.isEmpty()) {
                "No hidden-icon ghost apps or system impersonators"
            } else {
                "${stealthAndImpersonatingApps.size} app(s) exhibit stealth or impersonation behavior"
            },
            detailedAdvice = "Scans 100% offline for non-system apps that hide their launcher icon (Ghost Apps), use blank/invisible names, masquerade as OS utilities, or combine Accessibility/Notification spying with background persistence.",
            severity = when {
                stealthAndImpersonatingApps.any { app -> app.threatSignals.any { it.isCritical } } -> AuditSeverity.DANGER
                stealthAndImpersonatingApps.isNotEmpty() -> AuditSeverity.WARNING
                else -> AuditSeverity.SAFE
            },
            statusBadge = if (stealthAndImpersonatingApps.isEmpty()) "SAFE" else "${stealthAndImpersonatingApps.size} FLAGGED",
            flaggedApps = stealthAndImpersonatingApps,
            extraDetails = stealthExtraDetails,
            settingsAction = Settings.ACTION_MANAGE_ALL_APPLICATIONS_SETTINGS,
            actionButtonLabel = "Manage Apps"
        )

        // 12. Offline APK Certificate, Debug Key & Banking Trojan / Dropper Heuristics
        val certAndTrojanApps = allApps.filter { app ->
            !app.isSystem && (
                app.hasUntrustedOrDebugCert ||
                    app.isDebuggableApk ||
                    app.threatSignals.any {
                        it.category == ThreatSignalCategory.CERT_ANOMALY ||
                            it.category == ThreatSignalCategory.TROJAN_DROPPER
                    }
                )
        }
        val certTrojanDetails = certAndTrojanApps.flatMap { app ->
            app.threatSignals
                .filter {
                    it.category == ThreatSignalCategory.CERT_ANOMALY ||
                        it.category == ThreatSignalCategory.TROJAN_DROPPER
                }
                .map { "${app.appName}: ${it.title} (${app.certSignerSummary.ifBlank { "Unverified Signer" }})" }
        }
        val certTrojanVector = SecurityAuditVector(
            id = "apk_cert_trojan",
            title = "APK Signatures & Trojan Combos",
            plainSummary = if (certAndTrojanApps.isEmpty()) {
                "All user APKs have production certs & no trojan combos"
            } else {
                "${certAndTrojanApps.size} APK(s) flagged for cert anomaly or trojan/dropper combo"
            },
            detailedAdvice = "Inspects X.509 signing certificates (SHA-256) offline for public AOSP test/debug keys, debuggable builds, Overlay + OTP interception (banking trojan pattern), and Accessibility + APK auto-installer droppers.",
            severity = when {
                certAndTrojanApps.any { app -> app.threatSignals.any { it.isCritical } } -> AuditSeverity.DANGER
                certAndTrojanApps.isNotEmpty() -> AuditSeverity.WARNING
                else -> AuditSeverity.SAFE
            },
            statusBadge = if (certAndTrojanApps.isEmpty()) "VERIFIED" else "${certAndTrojanApps.size} REVIEW",
            flaggedApps = certAndTrojanApps,
            extraDetails = certTrojanDetails,
            settingsAction = Settings.ACTION_MANAGE_ALL_APPLICATIONS_SETTINGS,
            actionButtonLabel = "Inspect Apps"
        )

        val rawVectors = listOf(
            screenVector,
            micCamVector,
            stealthVector,
            certTrojanVector,
            bgLocationVector,
            clipboardVector,
            overlayVector,
            accessibilityVector,
            notifVector,
            adminVector,
            inputVector,
            sideloadVector
        )

        val ackMap = _acknowledgedVectors.value
        val allVectors = rawVectors.map { vec ->
            val sig = computeVectorSignature(vec.flaggedApps, vec.extraDetails)
            val isLiveVector = vec.id == "screen_recording" ||
                vec.id == "live_mic_cam" ||
                (vec.id == "bg_location" && live.isBackgroundLocationActive) ||
                (vec.id == "clipboard_access" && live.isClipboardAccessedRecently)
            if (!isLiveVector && vec.severity != AuditSeverity.SAFE && sig.isNotEmpty() && ackMap[vec.id] == sig) {
                vec.copy(
                    severity = AuditSeverity.SAFE,
                    statusBadge = "EXPECTED",
                    plainSummary = "Marked as expected • ${vec.plainSummary}",
                    isAcknowledged = true,
                    currentSignature = sig
                )
            } else {
                vec.copy(
                    isAcknowledged = false,
                    currentSignature = sig
                )
            }
        }

        cachedAuditVectors = allVectors
        cachedAuditVectorsTimeMs = System.currentTimeMillis()
        cachedAuditIncludeSystem = includeSystemApps

        if (logAuditEvent) {
            recordEvent(
                type = "AUDIT",
                title = "Security Scan Finished",
                description = "${allVectors.count { it.severity != AuditSeverity.SAFE }} item(s) flagged across 12 offline checks.",
                severity = AuditSeverity.SAFE
            )
        }
        evaluateAndNotifyLiveGuardChecks(allVectors, forceNotifyActive = false)

        return allVectors
        }
    }

    /**
     * Evaluates all 12 Live Guard security checks using persisted baseline diffing.
     * Fires push notifications and records Timeline events ONLY when:
     * 1. A live threat is active or newly triggered, OR
     * 2. A new app/setting item is added to a check vector after the baseline was initialized, OR
     * 3. forceNotifyActive is explicitly requested (e.g., user tapped Test Alert).
     */
    fun evaluateAndNotifyLiveGuardChecks(
        vectors: List<SecurityAuditVector>,
        forceNotifyActive: Boolean = false
    ) {
        try {
            val cfg = _rulesConfig.value
            if (cfg.alertMode == GuardAlertMode.SILENT_LOG) return
            val thermalPrefs = context.getSharedPreferences("thermal_prefs", Context.MODE_PRIVATE)
            if (!thermalPrefs.getBoolean("live_guard_alerts", true)) return

            val canPostNotifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            } else {
                true
            }

            data class PendingCheckAlert(
                val notifId: Int,
                val eventType: String,
                val title: String,
                val message: String,
                val severity: AuditSeverity,
                val primaryPkg: String?,
                val sensorType: GuardSensorType,
                val settingsAction: String?
            )

            val isFirstBaselineRun = !vectorBaselineInitialized
            var baselineDirty = false
            val pendingAlerts = mutableListOf<PendingCheckAlert>()

            for (vector in vectors) {
                val isLiveCaptureOrSensor = vector.id == "screen_recording" || vector.id == "live_mic_cam"
                // Live hardware vectors are already notified in real time by OS callbacks (avoid duplicate alerts)
                if (isLiveCaptureOrSensor && !forceNotifyActive) {
                    continue
                }

                val currentItems: Set<String> = buildSet {
                    vector.flaggedApps.forEach { add("app:${it.packageName}") }
                    vector.extraDetails.forEach { add("detail:$it") }
                    if (isEmpty() && vector.severity != AuditSeverity.SAFE) {
                        add("state:${vector.statusBadge}")
                    }
                }

                if (vector.severity == AuditSeverity.SAFE && !vector.isAcknowledged) {
                    if (knownVectorItemsMap.remove(vector.id) != null) {
                        baselineDirty = true
                    }
                    continue
                }

                if (vector.isAcknowledged) {
                    if (knownVectorItemsMap[vector.id] != currentItems) {
                        knownVectorItemsMap[vector.id] = currentItems
                        baselineDirty = true
                    }
                    continue
                }

                val previousItems = knownVectorItemsMap[vector.id] ?: emptySet()
                val newlyAddedItems = currentItems - previousItems

                // On first launch, seed static posture checks into the baseline silently without blasting notifications
                if (isFirstBaselineRun && !isLiveCaptureOrSensor && !forceNotifyActive) {
                    knownVectorItemsMap[vector.id] = currentItems
                    baselineDirty = true
                    continue
                }

                if (!forceNotifyActive && newlyAddedItems.isEmpty()) {
                    continue
                }

                val vectorSensorType = when (vector.id) {
                    "bg_location" -> GuardSensorType.LOCATION
                    "clipboard_access" -> GuardSensorType.CLIPBOARD
                    "live_mic_cam" -> if (_captureState.value.isCameraActive) GuardSensorType.CAMERA else GuardSensorType.MIC
                    else -> GuardSensorType.SCREEN
                }
                if (!forceNotifyActive && isGlobalSensorSnoozed(vectorSensorType)) {
                    continue
                }

                // Always advance baseline when processed so denied notification permission never causes duplicate event spam
                knownVectorItemsMap[vector.id] = currentItems
                baselineDirty = true

                val appNames = vector.flaggedApps.map { it.appName }.distinct()
                val details = vector.extraDetails.sorted()
                val primaryPkg = vector.flaggedApps.firstOrNull()?.packageName
                val whoSummary = when {
                    appNames.isNotEmpty() -> appNames.take(3).joinToString(", ") +
                        if (appNames.size > 3) " +${appNames.size - 3} more" else ""
                    details.isNotEmpty() -> details.take(2).joinToString(" • ")
                    else -> vector.plainSummary
                }

                val alertSpec: PendingCheckAlert = when (vector.id) {
                    "screen_recording" -> PendingCheckAlert(
                        notifId = 2100,
                        eventType = "SCREEN",
                        title = "Screen Recording / Mirroring Active",
                        message = "${vector.plainSummary} ($whoSummary).",
                        severity = vector.severity,
                        primaryPkg = primaryPkg,
                        sensorType = GuardSensorType.SCREEN,
                        settingsAction = vector.settingsAction
                    )
                    "accessibility_monitors" -> PendingCheckAlert(
                        notifId = 2101,
                        eventType = "ACCESSIBILITY",
                        title = "Screen Reader Enabled: ${appNames.firstOrNull() ?: "Accessibility"}",
                        message = "${vector.plainSummary} ($whoSummary). Screen readers can read all on-screen text and passwords.",
                        severity = vector.severity,
                        primaryPkg = primaryPkg,
                        sensorType = GuardSensorType.SCREEN,
                        settingsAction = vector.settingsAction
                    )
                    "notification_listeners" -> PendingCheckAlert(
                        notifId = 2102,
                        eventType = "NOTIFICATION_LISTENER",
                        title = "Notification Reader Enabled: ${appNames.firstOrNull() ?: "Listener"}",
                        message = "${vector.plainSummary} ($whoSummary). Notification listeners can read incoming chats and 2FA codes.",
                        severity = vector.severity,
                        primaryPkg = primaryPkg,
                        sensorType = GuardSensorType.SCREEN,
                        settingsAction = vector.settingsAction
                    )
                    "screen_overlays" -> PendingCheckAlert(
                        notifId = 2103,
                        eventType = "OVERLAY",
                        title = "Screen Overlay Enabled (${vector.flaggedApps.size} App${if (vector.flaggedApps.size == 1) "" else "s"})",
                        message = "Apps allowed to draw over your screen: $whoSummary.",
                        severity = vector.severity,
                        primaryPkg = primaryPkg,
                        sensorType = GuardSensorType.SCREEN,
                        settingsAction = vector.settingsAction
                    )
                    "device_admins" -> PendingCheckAlert(
                        notifId = 2104,
                        eventType = "DEVICE_ADMIN",
                        title = "Device Admin Enabled: ${appNames.firstOrNull() ?: "Admin"}",
                        message = "${vector.plainSummary} ($whoSummary).",
                        severity = vector.severity,
                        primaryPkg = primaryPkg,
                        sensorType = GuardSensorType.SCREEN,
                        settingsAction = vector.settingsAction
                    )
                    "keyboards_vpn" -> PendingCheckAlert(
                        notifId = 2105,
                        eventType = "KEYBOARD",
                        title = "Custom Keyboard Enabled",
                        message = "${vector.plainSummary} ($whoSummary). Third-party keyboards can log keystrokes.",
                        severity = vector.severity,
                        primaryPkg = primaryPkg,
                        sensorType = GuardSensorType.SCREEN,
                        settingsAction = vector.settingsAction
                    )
                    "sideload_adb" -> PendingCheckAlert(
                        notifId = 2106,
                        eventType = "USB_ADB",
                        title = if (details.any { it.contains("ADB", ignoreCase = true) }) "USB Debugging (ADB) Turned ON" else "New Sideloaded App Detected",
                        message = "${vector.plainSummary}${if (appNames.isNotEmpty()) " ($whoSummary)" else ""}.",
                        severity = vector.severity,
                        primaryPkg = primaryPkg,
                        sensorType = GuardSensorType.SCREEN,
                        settingsAction = vector.settingsAction
                    )
                    "bg_location" -> PendingCheckAlert(
                        notifId = 2107,
                        eventType = "LOCATION",
                        title = "Background Location Granted (${vector.statusBadge})",
                        message = "${vector.plainSummary} ($whoSummary).",
                        severity = vector.severity,
                        primaryPkg = primaryPkg,
                        sensorType = GuardSensorType.LOCATION,
                        settingsAction = vector.settingsAction
                    )
                    "clipboard_access" -> PendingCheckAlert(
                        notifId = 2108,
                        eventType = "CLIPBOARD",
                        title = "Clipboard Access Detected",
                        message = "${vector.plainSummary} ($whoSummary).",
                        severity = vector.severity,
                        primaryPkg = primaryPkg,
                        sensorType = GuardSensorType.CLIPBOARD,
                        settingsAction = vector.settingsAction
                    )
                    "live_mic_cam" -> PendingCheckAlert(
                        notifId = 2109,
                        eventType = if (_captureState.value.isCameraActive) "CAMERA" else "MIC",
                        title = "Mic / Camera Active Now",
                        message = "${vector.plainSummary}.",
                        severity = vector.severity,
                        primaryPkg = primaryPkg,
                        sensorType = if (_captureState.value.isCameraActive) GuardSensorType.CAMERA else GuardSensorType.MIC,
                        settingsAction = vector.settingsAction
                    )
                    "stealth_stalkerware" -> PendingCheckAlert(
                        notifId = 2111,
                        eventType = "STEALTH_STALKERWARE",
                        title = "Stealth / Impersonation Risk: ${appNames.firstOrNull() ?: "Flagged App"}",
                        message = "${vector.plainSummary} ($whoSummary).",
                        severity = vector.severity,
                        primaryPkg = primaryPkg,
                        sensorType = GuardSensorType.SCREEN,
                        settingsAction = vector.settingsAction
                    )
                    "apk_cert_trojan" -> PendingCheckAlert(
                        notifId = 2112,
                        eventType = "APK_CERT_TROJAN",
                        title = "APK Signature / Trojan Risk: ${appNames.firstOrNull() ?: "Flagged APK"}",
                        message = "${vector.plainSummary} ($whoSummary).",
                        severity = vector.severity,
                        primaryPkg = primaryPkg,
                        sensorType = GuardSensorType.SCREEN,
                        settingsAction = vector.settingsAction
                    )
                    else -> PendingCheckAlert(
                        notifId = 2110,
                        eventType = "SCREEN",
                        title = vector.title,
                        message = vector.plainSummary,
                        severity = vector.severity,
                        primaryPkg = primaryPkg,
                        sensorType = GuardSensorType.SCREEN,
                        settingsAction = vector.settingsAction
                    )
                }

                // Deduplicate in _events if the exact same title was logged within the last 60s
                val alreadyInEvents = _events.value.any { ev ->
                    ev.title == alertSpec.title && (System.currentTimeMillis() - ev.timestamp) < 60_000L
                }
                if (!alreadyInEvents) {
                    recordEvent(
                        type = alertSpec.eventType,
                        title = alertSpec.title,
                        description = alertSpec.message,
                        severity = alertSpec.severity,
                        packageName = alertSpec.primaryPkg
                    )
                }
                pendingAlerts.add(alertSpec)
            }

            if (isFirstBaselineRun) {
                vectorBaselineInitialized = true
                baselineDirty = true
            }
            if (baselineDirty) {
                saveKnownVectorBaseline()
            }

            if (pendingAlerts.isEmpty() || !canPostNotifications) return

            // If multiple checks are flagged at once, post a high-priority summary notification first
            // so Android's per-package burst rate limiter never drops any flagged check!
            if (pendingAlerts.size > 1) {
                val summaryLines = pendingAlerts.map { "• ${it.title}" }
                val summaryBody = summaryLines.joinToString("\n")
                sendGuardNotificationDirect(
                    payload = GuardAlertPayload(
                        title = "Live Guard: ${pendingAlerts.size} Checks Flagged",
                        message = summaryBody,
                        packageName = null,
                        sensorType = GuardSensorType.SCREEN
                    ),
                    customNotificationId = 2099,
                    settingsAction = null
                )
            }

            // Stagger individual check notifications by 220ms so Android NotificationManagerService displays all of them
            pendingAlerts.forEachIndexed { idx, alert ->
                val delayMs = idx * 220L
                if (delayMs == 0L) {
                    sendGuardNotificationDirect(
                        payload = GuardAlertPayload(
                            title = alert.title,
                            message = alert.message,
                            packageName = alert.primaryPkg,
                            sensorType = alert.sensorType
                        ),
                        customNotificationId = alert.notifId,
                        settingsAction = alert.settingsAction
                    )
                } else {
                    mainHandler.postDelayed({
                        sendGuardNotificationDirect(
                            payload = GuardAlertPayload(
                                title = alert.title,
                                message = alert.message,
                                packageName = alert.primaryPkg,
                                sensorType = alert.sensorType
                            ),
                            customNotificationId = alert.notifId,
                            settingsAction = alert.settingsAction
                        )
                    }, delayMs)
                }
            }
        } catch (_: Throwable) {
        }
    }

    private fun queryRecentClipboardReaderApps(
        apps: List<AppPrivacyReport>,
        preloadedPkgOpsList: List<*>? = null
    ): List<AppPrivacyReport> {
        val now = System.currentTimeMillis()
        // Only flag apps that were verified to read the clipboard in the background (never normal foreground user pastes)
        val verifiedBgClipPkgs = _events.value
            .asSequence()
            .filter { it.type == "CLIPBOARD" && !it.packageName.isNullOrBlank() && (now - it.timestamp) in 0L..86_400_000L }
            .mapNotNull { it.packageName }
            .toSet()
        return apps.filter { it.packageName in verifiedBgClipPkgs && !PrivacyAnalyzer.isBrandOrOemPackage(it.packageName) }
    }

    @Synchronized
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
        if (type != "AUDIT") {
            PrivacyAnalyzer.invalidateTimelineCache()
        }
    }

    fun clearEvents() {
        _events.value = emptyList()
        prefs.edit().remove("saved_events_v3_json").remove("saved_events_json").apply()
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
            prefs.edit().putString("saved_events_v3_json", arr.toString()).apply()
        } catch (_: Exception) {
        }
    }

    private fun loadSavedEvents() {
        try {
            val raw = prefs.getString("saved_events_v3_json", null)
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
                val evType = obj.optString("type", "AUDIT")
                val evPkg = obj.optString("packageName").takeIf { it.isNotBlank() }
                val evDesc = obj.optString("description", "")
                // Clean up any historical false-positive Screen Recording, Install, or Permission events
                if (evType == "INSTALL" || evType == "PERMISSION") {
                    continue
                }
                if (evType == "SCREEN" &&
                    evPkg != null &&
                    !isScreenRecorderPackage(evPkg) &&
                    evPkg != "com.android.systemui" &&
                    !evDesc.contains("OS Screen Recording", ignoreCase = true) &&
                    !evDesc.contains("Virtual Display", ignoreCase = true) &&
                    !evDesc.contains("File:", ignoreCase = true)
                ) {
                    continue
                }
                loaded.add(
                    GuardEvent(
                        id = candidateId,
                        timestamp = obj.optLong("timestamp", candidateId),
                        type = evType,
                        title = obj.optString("title", "Guard Event"),
                        description = evDesc,
                        severity = sev,
                        packageName = evPkg
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
            INSTANCE?.stopMonitoring()
            INSTANCE = null
        }

        fun getInstance(context: Context): LiveGuardMonitor {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: LiveGuardMonitor(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
