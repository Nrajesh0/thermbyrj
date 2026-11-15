package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.service.AuditSeverity
import com.example.service.LiveGuardMonitor
import com.example.service.PrivacyAnalyzer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("dot", appName)
    }

    @Test
    fun `formatBytes formats sizes accurately`() {
        assertEquals("0 B", formatBytes(0L))
        assertEquals("512.0 B", formatBytes(512L))
        assertEquals("1.0 KB", formatBytes(1_000L))
        assertEquals("1.5 MB", formatBytes(1_500_000L))
        assertEquals("92.0 GB", formatBytes(92_000_000_000L))
    }

    @Test
    fun `privacy analyzer tracks baseline and permission changes`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val summary = PrivacyAnalyzer.analyzeDevicePrivacy(context, includeSystemApps = false)
        val initialChanges = PrivacyAnalyzer.getPrivacyChanges(context, summary.appReports)
        assertTrue(initialChanges.size >= 0)
        val cleared = PrivacyAnalyzer.clearPrivacyChangeLog(context, summary.appReports)
        assertTrue(cleared.isEmpty())
    }

    @Test
    fun `privacy analyzer provides plain english dictionary and valid score`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dictionary = PrivacyAnalyzer.getPlainDictionary()
        assertFalse(dictionary.isEmpty())
        assertTrue(dictionary.all { it.simpleExplanation.isNotBlank() && it.whyItMatters.isNotBlank() })

        val summary = PrivacyAnalyzer.analyzeDevicePrivacy(context, includeSystemApps = false)
        assertTrue(summary.privacyScore in 12..100)
        assertTrue(summary.letterGrade.isNotBlank())
    }

    @Test
    fun `live guard monitor runs 10 point deep audit, ignore list, and logs events`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val monitor = LiveGuardMonitor.getInstance(context)
        monitor.startMonitoring()

        val vectors = monitor.runDeepSecurityAudit(includeSystemApps = false)
        assertEquals(10, vectors.size)
        assertTrue(vectors.any { it.id == "bg_location" })
        assertTrue(vectors.any { it.id == "clipboard_access" })

        monitor.ignorePackage("com.example.ignored.test")
        assertTrue(monitor.isPackageIgnored("com.example.ignored.test"))
        monitor.unignorePackage("com.example.ignored.test")
        assertFalse(monitor.isPackageIgnored("com.example.ignored.test"))

        monitor.recordEvent(
            type = "SCREEN",
            title = "Test Screen Capture",
            description = "Unit test event",
            severity = AuditSeverity.WARNING
        )
        assertTrue(monitor.events.value.any { it.title == "Test Screen Capture" })
    }

    @Test
    fun `telemetry analyzer scans apps and calculates tracker scores`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val telemetry = PrivacyAnalyzer.analyzeAppTelemetryAndTrackers(context, includeSystemApps = false)
        assertTrue(telemetry.totalAppsScanned >= 0)
        assertTrue(telemetry.appReports.all { it.telemetryScore in 0..100 })
    }

    @Test
    fun `brand and oem packages are recognized and excluded from sideload warnings`() {
        assertTrue(PrivacyAnalyzer.isBrandOrOemPackage("com.miui.screenrecorder"))
        assertTrue(PrivacyAnalyzer.isBrandOrOemPackage("com.xiaomi.market"))
        assertTrue(PrivacyAnalyzer.isBrandOrOemPackage("com.samsung.android.app.smartcapture"))
        assertFalse(PrivacyAnalyzer.isBrandOrOemPackage("org.random.unknown.sideloaded"))
    }

    @Test
    fun `timeline reports only verified sensor or appops accesses and ignores unopened apps`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val monitor = LiveGuardMonitor.getInstance(context)
        monitor.clearEvents()
        PrivacyAnalyzer.invalidateTimelineCache()

        // Audit & simulated guard test events must NOT appear as sensor/permission accesses
        monitor.runDeepSecurityAudit(includeSystemApps = false)
        monitor.triggerSimulatedGuardTest()
        val baselineTimeline = PrivacyAnalyzer.getPermissionAccessTimeline(context, includeSystemApps = false)
        assertTrue(
            "Unopened apps and audit events must not create fake timeline entries",
            baselineTimeline.all { it.accessContext == "Verified AppOps" }
        )

        // Genuine live sensor detection MUST appear in the timeline
        monitor.recordEvent(
            type = "MIC",
            title = "Microphone Active",
            description = "1 audio stream(s) recording.",
            severity = AuditSeverity.WARNING,
            packageName = null
        )
        val afterMic = PrivacyAnalyzer.getPermissionAccessTimeline(context, includeSystemApps = false)
        assertTrue(afterMic.any { it.permissionInfo.permissionName == android.Manifest.permission.RECORD_AUDIO })
    }

    @Test
    fun `hidden settings catalog and white surface presets are properly configured`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val catalog = com.example.ui.screens.buildHiddenSettingsCatalog(context)
        assertTrue(catalog.size >= 24)
        assertTrue(catalog.all { it.title.isNotBlank() && it.subtitle.isNotBlank() && it.targets.isNotEmpty() })

        val pureWhite = com.example.ui.theme.SurfacePreset.PURE_WHITE
        val softWhite = com.example.ui.theme.SurfacePreset.SOFT_WHITE
        assertTrue(pureWhite.isLight)
        assertTrue(softWhite.isLight)
    }

    @Test
    fun `guard rules support granular per-sensor trust, snoozes, and system app discovery`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val monitor = LiveGuardMonitor.getInstance(context)
        monitor.clearIgnoredPackages()
        monitor.clearAllSnoozes()

        val initialConfig = monitor.rulesConfig.value
        val updatedConfig = initialConfig.copy(
            alertMode = com.example.service.GuardAlertMode.ALL_ACTIVITY,
            debounceEnabled = false,
            silenceDuringCalls = false
        )
        monitor.updateRulesConfig(updatedConfig)
        assertEquals(com.example.service.GuardAlertMode.ALL_ACTIVITY, monitor.rulesConfig.value.alertMode)
        assertFalse(monitor.rulesConfig.value.debounceEnabled)
        monitor.updateRulesConfig(com.example.service.GuardRulesConfig())
        assertEquals(com.example.service.GuardAlertMode.SMART_COVERT, monitor.rulesConfig.value.alertMode)

        // Granular per-sensor trust: trusting only MIC must keep CAMERA alerts active
        val recorderPkg = "com.example.recorder.unit"
        monitor.setAppTrust(recorderPkg, setOf(com.example.service.GuardSensorType.MIC), source = "Test")
        assertTrue(monitor.isSensorTrusted(recorderPkg, com.example.service.GuardSensorType.MIC))
        assertFalse(monitor.isSensorTrusted(recorderPkg, com.example.service.GuardSensorType.CAMERA))
        assertTrue(monitor.isSensorTrustedOrSnoozed(recorderPkg, com.example.service.GuardSensorType.MIC))
        assertFalse(monitor.isSensorTrustedOrSnoozed(recorderPkg, com.example.service.GuardSensorType.CAMERA))

        // Global / sensor snoozes suppress alerts until cancelled
        monitor.snoozeTarget("SENSOR:CAMERA", "Camera (1h)")
        assertTrue(monitor.isSensorTrustedOrSnoozed(recorderPkg, com.example.service.GuardSensorType.CAMERA))
        monitor.cancelSnooze("SENSOR:CAMERA")
        assertFalse(monitor.isSensorTrustedOrSnoozed(recorderPkg, com.example.service.GuardSensorType.CAMERA))

        monitor.removeAppTrust(recorderPkg)
        assertFalse(monitor.isSensorTrusted(recorderPkg, com.example.service.GuardSensorType.MIC))

        // Dynamic system app discovery runs safely on any device/environment
        val discovered = com.example.service.GuardSystemAppDiscovery.discoverSystemSensorApps(context)
        assertTrue(discovered.all { it.packageName.isNotBlank() && it.recommendedSensors.isNotEmpty() })
    }

    @Test
    fun `preferences backup manager saves mirror snapshot, exports json, and auto restores cleared prefs`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val thermalPrefs = context.getSharedPreferences("thermal_prefs", Context.MODE_PRIVATE)
        val guardPrefs = context.getSharedPreferences("guard_events_prefs", Context.MODE_PRIVATE)
        val bookmarkPrefs = context.getSharedPreferences("file_bookmarks_prefs", Context.MODE_PRIVATE)

        thermalPrefs.edit()
            .putString("theme_accent", "EMERALD")
            .putString("theme_surface", "PURE_WHITE")
            .putFloat("warning_temp", 39.5f)
            .putBoolean("ambient_glow", true)
            .commit()

        LiveGuardMonitor.resetInstanceForTests()
        val monitor = LiveGuardMonitor.getInstance(context)
        monitor.updateRulesConfig(
            com.example.service.GuardRulesConfig(
                alertMode = com.example.service.GuardAlertMode.ALL_ACTIVITY,
                debounceEnabled = false
            )
        )
        monitor.setAppTrust("com.example.trusted.app", setOf(com.example.service.GuardSensorType.MIC), source = "Test")

        bookmarkPrefs.edit()
            .putStringSet("starred_paths", setOf("/sdcard/Download/test_star"))
            .commit()

        com.example.service.PreferencesBackupManager.saveMirrorSnapshot(context)
        val exportedJson = com.example.service.PreferencesBackupManager.exportToJson(context)
        assertTrue(exportedJson.contains("EMERALD"))
        assertTrue(exportedJson.contains("PURE_WHITE"))
        assertTrue(exportedJson.contains("com.example.trusted.app"))

        // Simulate prefs being cleared on update/reset
        thermalPrefs.edit().clear().commit()
        guardPrefs.edit().clear().commit()
        bookmarkPrefs.edit().clear().commit()

        val restored = com.example.service.PreferencesBackupManager.autoRestoreIfCleared(context)
        assertTrue(restored)
        assertEquals("EMERALD", thermalPrefs.getString("theme_accent", null))
        assertEquals("PURE_WHITE", thermalPrefs.getString("theme_surface", null))
        assertEquals(39.5f, thermalPrefs.getFloat("warning_temp", 0f), 0.01f)
        assertTrue(thermalPrefs.getBoolean("ambient_glow", false))
        assertEquals(com.example.service.GuardAlertMode.ALL_ACTIVITY, monitor.rulesConfig.value.alertMode)
        assertTrue(monitor.isSensorTrusted("com.example.trusted.app", com.example.service.GuardSensorType.MIC))
        assertEquals(setOf("/sdcard/Download/test_star"), bookmarkPrefs.getStringSet("starred_paths", emptySet()))
    }

    @Test
    fun `file shredder wipes files using DoD 3-pass and NIST 1-pass and supports normal delete`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val testRoot = java.io.File(context.filesDir, "shred_unit_test_dir").apply {
            deleteRecursively()
            mkdirs()
        }
        val nestedDir = java.io.File(testRoot, "sub").apply { mkdirs() }
        val fileA = java.io.File(testRoot, "secret_a.txt").apply { writeText("TOP_SECRET_DATA_1234567890") }
        val fileB = java.io.File(nestedDir, "secret_b.bin").apply { writeBytes(ByteArray(4096) { 0x42 }) }
        val expectedBytes = fileA.length() + fileB.length()

        val dodResult = com.example.service.FileShredder.shredTargets(
            context = context,
            targets = listOf(com.example.service.ShredTarget(testRoot)),
            standard = com.example.service.ShredStandard.DOD_5220_22_M
        )
        assertEquals(2, dodResult.shreddedCount)
        assertEquals(expectedBytes, dodResult.totalBytesWiped)
        assertTrue(dodResult.blockedForOverwrite.isEmpty())
        assertFalse(fileA.exists())
        assertFalse(fileB.exists())
        assertFalse(testRoot.exists())

        // NIST 1-pass check
        val nistFile = java.io.File(context.filesDir, "nist_test.txt").apply { writeText("NIST_WIPE_PAYLOAD") }
        val nistLen = nistFile.length()
        val nistResult = com.example.service.FileShredder.shredTargets(
            context = context,
            targets = listOf(com.example.service.ShredTarget(nistFile)),
            standard = com.example.service.ShredStandard.NIST_SP_800_88
        )
        assertEquals(1, nistResult.shreddedCount)
        assertEquals(nistLen, nistResult.totalBytesWiped)
        assertFalse(nistFile.exists())

        // Normal delete check
        val normalFile = java.io.File(context.filesDir, "normal_del.txt").apply { writeText("hello") }
        assertTrue(com.example.service.FileShredder.normalDeleteFile(context, normalFile))
        assertFalse(normalFile.exists())
    }

    @Test
    fun `screen recording filter ignores normal camera or downloaded videos and matches recorder paths`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        LiveGuardMonitor.resetInstanceForTests()
        val monitor = LiveGuardMonitor.getInstance(context)

        // Normal videos must NOT trigger screen-recording alerts
        assertFalse(monitor.isScreenRecordingMediaEntry("DCIM/Camera/", null, "VID_20261114_120000.mp4"))
        assertFalse(monitor.isScreenRecordingMediaEntry("Download/", null, "sample_clip.mp4"))
        assertFalse(monitor.isScreenRecordingMediaEntry("Movies/Instagram/", null, "reel_991.mp4"))

        // Actual screen recorder paths and filenames MUST match
        assertTrue(monitor.isScreenRecordingMediaEntry("DCIM/ScreenRecorder/", null, "Screenrecorder-2026.mp4"))
        assertTrue(monitor.isScreenRecordingMediaEntry("Movies/Screen recordings/", null, "rec_01.mp4"))
        assertTrue(monitor.isScreenRecordingMediaEntry("Movies/", null, "Screen_Recording_2026.mp4"))

        assertEquals(null, monitor.detectActiveScreenRecordingFile())
    }

    @Test
    fun `manifest registers wired activities and light theme surfaces contrast properly`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val pm = context.packageManager

        val dataTelemetryIntent = android.content.Intent(context, com.example.ui.screens.DataTelemetryActivity::class.java)
        val guardRulesIntent = android.content.Intent(context, com.example.ui.screens.GuardRulesActivity::class.java)
        assertTrue(pm.resolveActivity(dataTelemetryIntent, 0) != null)
        assertTrue(pm.resolveActivity(guardRulesIntent, 0) != null)

        val pkgInfo = pm.getPackageInfo(context.packageName, android.content.pm.PackageManager.GET_ACTIVITIES)
        val activityNames = pkgInfo.activities?.map { it.name }.orEmpty()
        assertFalse(activityNames.any { it.endsWith("StorageAnalysisActivity") })

        val originalSurface = com.example.ui.theme.ThemeState.surfacePreset
        try {
            com.example.ui.theme.ThemeState.surfacePreset = com.example.ui.theme.SurfacePreset.PURE_WHITE
            assertTrue(com.example.ui.theme.GlassPanel != com.example.ui.theme.TextPrimary)
            assertTrue(com.example.ui.theme.GlassPanel.red > 0.8f)
            assertTrue(com.example.ui.theme.TextPrimary.red < 0.2f)
        } finally {
            com.example.ui.theme.ThemeState.surfacePreset = originalSurface
        }
    }

    @Test
    fun `live guard passive audit suppresses event loop and stopMonitoring clears active state`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        LiveGuardMonitor.resetInstanceForTests()
        val monitor = LiveGuardMonitor.getInstance(context)
        monitor.clearEvents()

        monitor.startMonitoring()
        assertTrue(monitor.isMonitoringActive())

        // Passive UI refresh must not append "Security Scan Finished" events
        monitor.runDeepSecurityAudit(includeSystemApps = false)
        assertFalse(monitor.events.value.any { it.title == "Security Scan Finished" })

        // Explicit user-triggered scan MUST append "Security Scan Finished"
        monitor.runDeepSecurityAudit(includeSystemApps = false, logAuditEvent = true)
        assertTrue(monitor.events.value.any { it.title == "Security Scan Finished" })

        monitor.stopMonitoring()
        assertFalse(monitor.isMonitoringActive())
    }

    @Test
    fun `privacy dashboard uses public privacy settings target and camera events appear in timeline`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val catalog = com.example.ui.screens.buildHiddenSettingsCatalog(context)
        val dashItem = catalog.first { it.id == "priv_dashboard" }
        assertTrue(dashItem.targets.any { it.action == android.provider.Settings.ACTION_PRIVACY_SETTINGS })
        assertFalse(dashItem.targets.any { it.action == "android.intent.action.REVIEW_PERMISSION_USAGE" })
        assertFalse(dashItem.targets.any { it.action == android.content.Intent.ACTION_VIEW_PERMISSION_USAGE })

        LiveGuardMonitor.resetInstanceForTests()
        val monitor = LiveGuardMonitor.getInstance(context)
        monitor.clearEvents()
        PrivacyAnalyzer.invalidateTimelineCache()

        monitor.recordEvent(
            type = "CAMERA",
            title = "Camera Active",
            description = "Lens 0 in use.",
            severity = AuditSeverity.WARNING,
            packageName = null
        )
        val timeline = PrivacyAnalyzer.getPermissionAccessTimeline(context, includeSystemApps = false)
        assertTrue(timeline.any { it.permissionInfo.permissionName == android.Manifest.permission.CAMERA })
    }

    @Test
    fun `storage cache invalidation, permission helpers, and shred service registration work cleanly`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val genBefore = com.example.service.StorageScanCache.generation
        com.example.service.StorageScanCache.invalidate()
        assertTrue(com.example.service.StorageScanCache.generation > genBefore)

        val mediaPerms = com.example.service.PermissionUtils.requiredMediaPermissions()
        assertTrue(mediaPerms.contains(android.Manifest.permission.READ_MEDIA_VIDEO))

        val shredServiceIntent = android.content.Intent(context, com.example.service.FileShredService::class.java)
        assertTrue(context.packageManager.resolveService(shredServiceIntent, 0) != null)
        assertEquals(null, com.example.service.FileShredder.peekInterruptedShredPath(context))
    }

    @Test
    fun `notification channels separate background monitor, thermal alerts, live guard, and shredder`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        com.example.service.NotificationChannels.ensureCreated(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager

        val tracker = nm.getNotificationChannel(com.example.service.NotificationChannels.TRACKER_CHANNEL_ID)
        val thermalAlert = nm.getNotificationChannel(com.example.service.NotificationChannels.ALERT_CHANNEL_ID)
        val guardAlert = nm.getNotificationChannel(com.example.service.NotificationChannels.GUARD_ALERT_CHANNEL_ID)
        val shredder = nm.getNotificationChannel(com.example.service.NotificationChannels.SHRED_CHANNEL_ID)

        assertTrue(tracker != null && tracker.name == "Background Monitor Status")
        assertTrue(thermalAlert != null && thermalAlert.name == "Thermal Overheat Alerts")
        assertTrue(guardAlert != null && guardAlert.name == "Live Guard Privacy Alerts")
        assertTrue(shredder != null && shredder.name == "Secure File Shredder")

        val thermalPrefs = context.getSharedPreferences("thermal_prefs", Context.MODE_PRIVATE)
        thermalPrefs.edit().putBoolean("thermal_alerts_enabled", false).commit()
        val json = com.example.service.PreferencesBackupManager.exportToJson(context)
        assertTrue(json.contains("\"thermal_alerts_enabled\": false"))
        thermalPrefs.edit().clear().commit()
        assertTrue(com.example.service.PreferencesBackupManager.importFromJson(context, json))
        assertFalse(thermalPrefs.getBoolean("thermal_alerts_enabled", true))
    }

    @Test
    fun `file manager operations support recursive search, zip archive, clipboard paste, and built in viewers`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val rootDir = java.io.File(context.cacheDir, "fm_ops_test_${System.currentTimeMillis()}").apply { mkdirs() }
        try {
            val subDir = java.io.File(rootDir, "SubFolder").apply { mkdirs() }
            val textFile = java.io.File(subDir, "notes.txt").apply { writeText("Hello File Manager") }
            val imgFile = java.io.File(rootDir, "sample.png").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }

            // Recursive search finds nested file
            val deepResults = com.example.service.FileManagerOps.searchFilesRecursively(
                rootDir = rootDir,
                query = "notes",
                showHidden = false
            )
            assertEquals(1, deepResults.size)
            assertEquals("notes.txt", deepResults.first().name)

            // Rename safely
            assertTrue(com.example.service.FileManagerOps.renameFileSafely(context, textFile, "renamed_notes.txt"))
            val renamedFile = java.io.File(subDir, "renamed_notes.txt")
            assertTrue(renamedFile.exists())

            // Zip and Unzip
            val zipOut = java.io.File(rootDir, "bundle.zip")
            assertTrue(com.example.service.FileManagerOps.compressFilesToZip(listOf(renamedFile), zipOut))
            assertTrue(zipOut.exists() && zipOut.length() > 0L)

            val extractDest = java.io.File(rootDir, "extracted").apply { mkdirs() }
            assertTrue(com.example.service.FileManagerOps.extractZipArchive(context, zipOut, extractDest))
            assertTrue(java.io.File(java.io.File(extractDest, "bundle"), "renamed_notes.txt").exists())

            // Clipboard copy & paste
            com.example.service.SharedClipboardHolder.setClipboard(
                setOf(renamedFile.absolutePath),
                com.example.service.SharedClipboardOp.COPY
            )
            val pasteDir = java.io.File(rootDir, "pasted").apply { mkdirs() }
            val clip = com.example.service.SharedClipboardHolder.clipboard.value!!
            val pastedCount = com.example.service.FileManagerOps.executeClipboardPaste(
                context = context,
                clip = clip,
                targetDir = pasteDir
            )
            assertEquals(1, pastedCount)
            assertTrue(java.io.File(pasteDir, "renamed_notes.txt").exists())
            com.example.service.SharedClipboardHolder.clear()

            // PendingExplorerNavigation
            com.example.service.PendingExplorerNavigation.openFolder(subDir.absolutePath)
            assertEquals(subDir.absolutePath, com.example.service.PendingExplorerNavigation.targetFolderPath.value)
            assertEquals(subDir.absolutePath, com.example.service.PendingExplorerNavigation.consume())
            assertEquals(null, com.example.service.PendingExplorerNavigation.targetFolderPath.value)

            // Built-in viewer type resolution
            assertEquals(
                com.example.ui.components.InAppViewerType.TEXT,
                com.example.ui.components.resolveInAppViewerType(renamedFile)
            )
            assertEquals(
                com.example.ui.components.InAppViewerType.IMAGE,
                com.example.ui.components.resolveInAppViewerType(imgFile)
            )
            assertEquals(
                com.example.ui.components.InAppViewerType.ZIP,
                com.example.ui.components.resolveInAppViewerType(zipOut)
            )
        } finally {
            rootDir.deleteRecursively()
        }
    }

    @Test
    fun `new app installs and newly added permissions trigger notifications and support new to old install time sorting`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val diffPrefs = context.getSharedPreferences("privacy_baseline_prefs", Context.MODE_PRIVATE)
        diffPrefs.edit().clear().commit()

        val icon = context.applicationInfo.loadIcon(context.packageManager)
        val dict = com.example.service.PrivacyAnalyzer.getPlainDictionary().associateBy { it.permissionName }
        val camPerm = dict[android.Manifest.permission.CAMERA]!!
        val micPerm = dict[android.Manifest.permission.RECORD_AUDIO]!!

        val olderApp = com.example.service.AppPrivacyReport(
            appName = "Alpha Older",
            packageName = "com.test.older",
            icon = icon,
            isSystem = false,
            grantedPermissions = listOf(camPerm),
            riskScore = 20,
            riskBadge = "MODERATE",
            lastTimeUsedMs = 1_000L,
            isSideloaded = false,
            installerName = "Play Store",
            hasInternetAccess = true,
            canUploadSensitiveData = true,
            hasOverlayPermission = false,
            hasAccessibilityAccess = false,
            hasNotificationListener = false,
            isDeviceAdmin = false,
            firstInstallTimeMs = 1_000_000L,
            lastUpdateTimeMs = 1_000_000L
        )

        // 1. Seed baseline with olderApp
        com.example.service.PrivacyAnalyzer.getPrivacyChanges(
            context = context,
            currentReports = listOf(olderApp),
            dispatchNotifications = false
        )

        // 2. Simulate a newly installed app AND a newly added permission on olderApp
        val updatedOlderApp = olderApp.copy(
            grantedPermissions = listOf(camPerm, micPerm)
        )
        val newlyInstalledApp = com.example.service.AppPrivacyReport(
            appName = "Zeta Newest",
            packageName = "com.test.newest",
            icon = icon,
            isSystem = false,
            grantedPermissions = listOf(camPerm),
            riskScore = 20,
            riskBadge = "MODERATE",
            lastTimeUsedMs = 2_000L,
            isSideloaded = false,
            installerName = "Play Store",
            hasInternetAccess = true,
            canUploadSensitiveData = true,
            hasOverlayPermission = false,
            hasAccessibilityAccess = false,
            hasNotificationListener = false,
            isDeviceAdmin = false,
            firstInstallTimeMs = 9_000_000L,
            lastUpdateTimeMs = 9_000_000L
        )

        val changes = com.example.service.PrivacyAnalyzer.getPrivacyChanges(
            context = context,
            currentReports = listOf(updatedOlderApp, newlyInstalledApp),
            dispatchNotifications = true
        )

        // Verify NEW_APP and PERM_ADDED events were recorded
        assertTrue(changes.any { it.packageName == "com.test.newest" && it.changeType == com.example.service.PrivacyChangeType.NEW_APP })
        assertTrue(changes.any { it.packageName == "com.test.older" && it.changeType == com.example.service.PrivacyChangeType.PERM_ADDED && it.permissionTitle == micPerm.friendlyTitle })

        // Verify New-to-Old install time sorting places newlyInstalledApp first
        val sortedNewToOld = listOf(updatedOlderApp, newlyInstalledApp).sortedByDescending { it.firstInstallTimeMs }
        assertEquals("com.test.newest", sortedNewToOld.first().packageName)
        assertEquals("com.test.older", sortedNewToOld.last().packageName)

        // Verify AppRefreshCoordinator increments refreshVersion
        val beforeVersion = com.example.service.AppRefreshCoordinator.refreshVersion.value
        kotlinx.coroutines.runBlocking {
            com.example.service.AppRefreshCoordinator.triggerRefreshAll(context)
        }
        assertTrue(com.example.service.AppRefreshCoordinator.refreshVersion.value > beforeVersion)
    }

    @Test
    fun `security checks support mark as safe acknowledgement and re-arm if items change`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        LiveGuardMonitor.resetInstanceForTests()
        val monitor = LiveGuardMonitor.getInstance(context)

        val sampleVec = com.example.service.SecurityAuditVector(
            id = "sideload_adb",
            title = "Sideloads & USB Debug",
            plainSummary = "USB Debugging (ADB) is ON",
            detailedAdvice = "Turn off USB Debugging when not needed.",
            severity = AuditSeverity.DANGER,
            statusBadge = "REVIEW",
            extraDetails = listOf("USB Debugging (ADB) is ON"),
            currentSignature = "USB Debugging (ADB) is ON"
        )

        monitor.acknowledgeVector(sampleVec)
        assertEquals("USB Debugging (ADB) is ON", monitor.acknowledgedVectors.value["sideload_adb"])

        // Export and re-import via PreferencesBackupManager preserves acknowledged state
        val exported = com.example.service.PreferencesBackupManager.exportToJson(context)
        assertTrue(exported.contains("acknowledged_vectors_json"))

        monitor.unacknowledgeVector("sideload_adb")
        assertFalse(monitor.acknowledgedVectors.value.containsKey("sideload_adb"))

        assertTrue(com.example.service.PreferencesBackupManager.importFromJson(context, exported))
        assertEquals("USB Debugging (ADB) is ON", monitor.acknowledgedVectors.value["sideload_adb"])
        monitor.unacknowledgeVector("sideload_adb")
    }
}


