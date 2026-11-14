package com.example.service

import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
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
import android.provider.MediaStore
import android.provider.Settings
import android.view.Display
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
    private val bgExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
    private val prefs = context.getSharedPreferences("guard_events_prefs", Context.MODE_PRIVATE)

    private val _captureState = MutableStateFlow(LiveCaptureState())
    val captureState: StateFlow<LiveCaptureState> = _captureState.asStateFlow()

    private val _events = MutableStateFlow<List<GuardEvent>>(emptyList())
    val events: StateFlow<List<GuardEvent>> = _events.asStateFlow()

    @Volatile
    private var cachedAuditVectors: List<SecurityAuditVector>? = null

    fun peekCachedAuditVectors(): List<SecurityAuditVector>? = cachedAuditVectors

    private val busyCameras = mutableSetOf<String>()
    private var isMonitoring = false
    private var lastScreenCaptureLogged = false
    private var lastMicLogged = false
    private var lastCameraLogged = false
    private var lastObservedVideoCaptureMs = 0L
    @Volatile
    private var osScreenRecordingActive = false

    var onHighPriorityAlert: ((title: String, message: String) -> Unit)? = null

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
            super.onChange(selfChange)
            lastObservedVideoCaptureMs = System.currentTimeMillis()
            refreshDisplayCaptureState(triggerAlertIfNew = true)
        }
    }

    private val periodicMonitorRunnable = object : Runnable {
        override fun run() {
            if (!isMonitoring) return
            bgExecutor.execute {
                refreshDisplayCaptureState(triggerAlertIfNew = true)
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
                val micApps = if (isMicNow) {
                    resolveActiveSensorApps(
                        targetOpStr = "android:record_audio",
                        requiredPermission = android.Manifest.permission.RECORD_AUDIO,
                        audioConfigs = configs
                    )
                } else {
                    emptyList()
                }

                _captureState.value = _captureState.value.copy(
                    isMicActive = isMicNow,
                    activeMicSessions = activeCount,
                    activeMicApps = micApps,
                    lastUpdatedMs = System.currentTimeMillis()
                )
                // Re-evaluate screen capture in case the screen recorder is capturing internal/mic audio
                refreshDisplayCaptureState(triggerAlertIfNew = true)

                if (isMicNow && !lastMicLogged) {
                    lastMicLogged = true
                    val who = if (micApps.isNotEmpty()) micApps.joinToString(", ") else null
                    val title = if (who != null) "Mic Active: $who" else "Microphone Active"
                    val desc = if (who != null) {
                        "$who is recording audio ($activeCount active stream${if (activeCount == 1) "" else "s"})."
                    } else {
                        "$activeCount audio stream(s) recording."
                    }
                    recordEvent(
                        type = "MIC",
                        title = title,
                        description = desc,
                        severity = AuditSeverity.WARNING
                    )
                    onHighPriorityAlert?.invoke(title, desc)
                } else if (!isMicNow) {
                    lastMicLogged = false
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
        try {
            val configs = audioManager.activeRecordingConfigurations
            val isMicNow = configs.isNotEmpty()
            val micApps = if (isMicNow) {
                resolveActiveSensorApps(
                    targetOpStr = "android:record_audio",
                    requiredPermission = android.Manifest.permission.RECORD_AUDIO,
                    audioConfigs = configs
                )
            } else {
                emptyList()
            }
            _captureState.value = _captureState.value.copy(
                isMicActive = isMicNow,
                activeMicSessions = configs.size,
                activeMicApps = micApps,
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
            val camApps = if (isCamNow) {
                resolveActiveSensorApps(
                    targetOpStr = "android:camera",
                    requiredPermission = android.Manifest.permission.CAMERA
                )
            } else {
                emptyList()
            }
            _captureState.value = _captureState.value.copy(
                isCameraActive = isCamNow,
                busyCameraIds = currentBusy,
                activeCameraApps = camApps,
                lastUpdatedMs = System.currentTimeMillis()
            )
            if (isCamNow && !lastCameraLogged) {
                lastCameraLogged = true
                val who = if (camApps.isNotEmpty()) camApps.joinToString(", ") else null
                val title = if (who != null) "Camera Active: $who" else "Camera Active"
                val desc = if (who != null) {
                    "$who is using camera lens ${currentBusy.joinToString()}."
                } else {
                    "Lens ${currentBusy.joinToString()} in use."
                }
                recordEvent(
                    type = "CAMERA",
                    title = title,
                    description = desc,
                    severity = AuditSeverity.WARNING
                )
                onHighPriorityAlert?.invoke(title, desc)
            } else if (!isCamNow) {
                lastCameraLogged = false
            }
        }
    }

    private fun resolveActiveSensorApps(
        targetOpStr: String,
        requiredPermission: String,
        audioConfigs: List<AudioRecordingConfiguration> = emptyList()
    ): List<String> {
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
                            if (isRunning || (now - lastTime) in 0L..12_000L) {
                                foundPackages.add(pkgName)
                            }
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }

        // 3. UsageStatsManager fallback: most recent foreground app or active foreground service holding requiredPermission
        if (foundPackages.isEmpty()) {
            try {
                val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
                val events = usm.queryEvents(now - 180_000L, now)
                val ev = UsageEvents.Event()
                var lastFgPkg: String? = null
                var lastFgTs = 0L
                val activeSvcMap = mutableMapOf<String, Long>()
                val stoppedSvcMap = mutableMapOf<String, Long>()

                while (events != null && events.hasNextEvent()) {
                    events.getNextEvent(ev)
                    val pkg = ev.packageName ?: continue
                    if (pkg == context.packageName) continue
                    when (ev.eventType) {
                        UsageEvents.Event.ACTIVITY_RESUMED -> {
                            if (ev.timeStamp >= lastFgTs) {
                                lastFgTs = ev.timeStamp
                                lastFgPkg = pkg
                            }
                        }
                        UsageEvents.Event.FOREGROUND_SERVICE_START -> {
                            activeSvcMap[pkg] = maxOf(activeSvcMap[pkg] ?: 0L, ev.timeStamp)
                        }
                        UsageEvents.Event.FOREGROUND_SERVICE_STOP -> {
                            stoppedSvcMap[pkg] = maxOf(stoppedSvcMap[pkg] ?: 0L, ev.timeStamp)
                        }
                    }
                }

                // Prefer foreground app if it actually holds the sensor permission
                if (lastFgPkg != null && pm.checkPermission(requiredPermission, lastFgPkg) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    foundPackages.add(lastFgPkg)
                }

                // Also add any active foreground service holding the sensor permission
                activeSvcMap.forEach { (pkg, startTs) ->
                    val stopTs = stoppedSvcMap[pkg] ?: 0L
                    if (startTs > stopTs &&
                        pm.checkPermission(requiredPermission, pkg) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    ) {
                        foundPackages.add(pkg)
                    }
                }
            } catch (_: Exception) {
            }
        }

        return foundPackages.mapNotNull { pkg ->
            try {
                val ai = pm.getApplicationInfo(pkg, 0)
                pm.getApplicationLabel(ai).toString()
            } catch (_: Exception) {
                pkg
            }
        }.distinct().take(3)
    }

    private fun refreshDisplayCaptureState(triggerAlertIfNew: Boolean) {
        try {
            val captureIndicators = mutableListOf<String>()
            val suspectedPackages = mutableSetOf<String>()

            if (osScreenRecordingActive) {
                captureIndicators.add("OS Screen Recording Active")
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
            }

            // 2. Internal Audio Loopback (REMOTE_SUBMIX = 8) or Active Audio with Screen Recorder
            val audioConfigs = try {
                audioManager.activeRecordingConfigurations
            } catch (_: Exception) {
                emptyList()
            }
            val hasInternalAudioCapture = audioConfigs.any { cfg ->
                cfg.clientAudioSource == MediaRecorder.AudioSource.REMOTE_SUBMIX
            }
            if (hasInternalAudioCapture) {
                captureIndicators.add("System Audio Loopback (Screen Recorder)")
            }

            // 3. Active Screen Recorder Foreground Service / UsageEvents (MIUI, HyperOS, Samsung, OnePlus, 3rd-party)
            val activeRecorderPkgs = detectActiveScreenRecorderPackages(audioConfigs.isNotEmpty())
            if (activeRecorderPkgs.isNotEmpty()) {
                suspectedPackages.addAll(activeRecorderPkgs)
                val pm = context.packageManager
                activeRecorderPkgs.forEach { pkg ->
                    val label = try {
                        val ai = pm.getApplicationInfo(pkg, 0)
                        pm.getApplicationLabel(ai).toString()
                    } catch (_: Exception) {
                        pkg
                    }
                    captureIndicators.add("$label ($pkg)")
                }
            }

            // 4. Active ScreenRecording file write in DCIM/ScreenRecorder or Movies/ScreenRecorder or MediaStore
            val activeRecordingFile = detectActiveScreenRecordingFile()
            if (activeRecordingFile != null) {
                captureIndicators.add("Recording file: $activeRecordingFile")
            }

            // 5. Legacy running services check
            suspectedPackages.addAll(detectRunningCapturePackages())

            val distinctIndicators = captureIndicators.distinct()
            val isCapturing = distinctIndicators.isNotEmpty()

            _captureState.value = _captureState.value.copy(
                isScreenRecordingOrCasting = isCapturing,
                activeCaptureDisplays = distinctIndicators,
                suspectedRecorderPackages = suspectedPackages.toList(),
                lastUpdatedMs = System.currentTimeMillis()
            )

            if (isCapturing && !lastScreenCaptureLogged) {
                lastScreenCaptureLogged = true
                val detail = distinctIndicators.joinToString(", ")
                recordEvent(
                    type = "SCREEN",
                    title = "Screen Recording Detected",
                    description = detail,
                    severity = AuditSeverity.DANGER
                )
                if (triggerAlertIfNew) {
                    onHighPriorityAlert?.invoke(
                        "Screen Recording Detected!",
                        "Your screen is being recorded ($detail)."
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

    private fun detectActiveScreenRecorderPackages(isMicOrAudioActive: Boolean): List<String> {
        val activeRecorders = mutableSetOf<String>()
        val now = System.currentTimeMillis()

        // A. Check AppOpsManager for running OP_PROJECT_MEDIA or active screen recorder ops
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
                        }
                    }
                }
            }
        } catch (_: Exception) {
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

    private fun detectActiveScreenRecordingFile(): String? {
        val now = System.currentTimeMillis()

        // 1. Check MediaStore observer trigger or IS_PENDING video in ScreenRecorder folder
        if (now - lastObservedVideoCaptureMs in 1..8000L) {
            return "MediaStore Video Capture"
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val projection = arrayOf(
                    MediaStore.Video.Media.DISPLAY_NAME,
                    MediaStore.Video.Media.IS_PENDING,
                    MediaStore.Video.Media.DATE_MODIFIED
                )
                context.contentResolver.query(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    "${MediaStore.Video.Media.IS_PENDING} = 1",
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val name = cursor.getString(0) ?: "ScreenRecording.mp4"
                        return name
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
                    // A temp file (.tmp) or video file modified within the last 8 seconds indicates active recording
                    if ((nameLower.endsWith(".tmp") && ageMs in 0..120_000L) ||
                        ((nameLower.endsWith(".mp4") || nameLower.endsWith(".mkv")) && ageMs in 0..6_000L)
                    ) {
                        return f.name
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
            app.grantedPermissions.any { it.category == PermissionCategory.CAMERA_MIC }
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

        // 3. Screen Overlays
        val overlayApps = allApps.filter { it.hasOverlayPermission }
        val overlayVector = SecurityAuditVector(
            id = "screen_overlays",
            title = "Screen Overlays",
            plainSummary = if (overlayApps.isEmpty()) "No apps draw over your screen" else "${overlayApps.size} app(s) can draw over other apps",
            detailedAdvice = "Turn off 'Display over other apps' for apps that don't need floating bubbles.",
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
            .filter { it != context.packageName && !PrivacyAnalyzer.isBrandOrOemPackage(it) }
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

        // 7. Custom Third-Party Keyboards (Input Methods)
        val untrustedKeyboards = PrivacyAnalyzer.getEnabledInputMethods(context)
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

        // 8. Sideloaded Apps & USB Debugging (excludes MIUI / HyperOS / Brand apps)
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

    fun recordEvent(type: String, title: String, description: String, severity: AuditSeverity) {
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
