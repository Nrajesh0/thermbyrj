package com.example

import android.app.KeyguardManager
import android.content.Context
import android.os.Environment
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.example.service.AuditSeverity
import com.example.service.GuardSensorType
import com.example.service.LiveGuardMonitor
import com.example.service.OfflineThreatScanner
import com.example.service.PrivacyAnalyzer
import com.example.service.SecurityAuditVector
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.io.File
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LiveGuardReliabilityTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        LiveGuardMonitor.resetInstanceForTests()
        PrivacyAnalyzer.invalidateAllCaches()
        context.getSharedPreferences("guard_events_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("thermal_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After
    fun tearDown() {
        LiveGuardMonitor.resetInstanceForTests()
        PrivacyAnalyzer.invalidateAllCaches()
    }

    @Test
    fun `package verifier check avoids deprecated false positive on Android 11+ when ADB is off`() {
        Settings.Global.putInt(context.contentResolver, "package_verifier_enable", 0)
        Settings.Global.putInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0)
        assertTrue(
            "Deprecated package_verifier_enable=0 must not trigger false DANGER on API 36 when ADB is off",
            OfflineThreatScanner.isPackageVerifierEnabled(context)
        )

        Settings.Global.putInt(context.contentResolver, Settings.Global.ADB_ENABLED, 1)
        Settings.Global.putInt(context.contentResolver, "verifier_verify_adb_installs", 0)
        assertFalse(
            "ADB install verifier explicitly disabled while ADB is ON should still be flagged",
            OfflineThreatScanner.isPackageVerifierEnabled(context)
        )
    }

    @Test
    fun `accessibility vector ignores stock TalkBack and OEM services while flagging third-party readers`() {
        val guard = LiveGuardMonitor.getInstance(context)

        // Stock Google TalkBack + OEM service should NOT trigger DANGER
        Settings.Secure.putString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            "com.google.android.marvin.talkback/com.google.android.marvin.talkback.TalkBackService:" +
                "com.miui.voiceassist/com.miui.voiceassist.AssistService"
        )
        val stockVectors = guard.runDeepSecurityAudit(includeSystemApps = false, logAuditEvent = true)
        val stockAcc = stockVectors.first { it.id == "accessibility_monitors" }
        assertEquals(AuditSeverity.SAFE, stockAcc.severity)
        assertTrue(stockAcc.extraDetails.isEmpty())

        // Genuine third-party accessibility service MUST trigger DANGER
        Settings.Secure.putString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            "com.google.android.marvin.talkback/com.google.android.marvin.talkback.TalkBackService:" +
                "com.unknown.spyware.reader/com.unknown.spyware.reader.KeyloggerService"
        )
        val riskyVectors = guard.runDeepSecurityAudit(includeSystemApps = false, logAuditEvent = true)
        val riskyAcc = riskyVectors.first { it.id == "accessibility_monitors" }
        assertEquals(AuditSeverity.DANGER, riskyAcc.severity)
        assertEquals(1, riskyAcc.extraDetails.size)
        assertTrue(riskyAcc.extraDetails.first().contains("com.unknown.spyware.reader"))
    }

    @Test
    fun `sideload vector uses contextual settings action based on ADB state`() {
        val guard = LiveGuardMonitor.getInstance(context)

        Settings.Global.putInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0)
        val adbOffVec = guard.runDeepSecurityAudit(includeSystemApps = false, logAuditEvent = true)
            .first { it.id == "sideload_adb" }
        assertEquals(Settings.ACTION_MANAGE_ALL_APPLICATIONS_SETTINGS, adbOffVec.settingsAction)

        Settings.Global.putInt(context.contentResolver, Settings.Global.ADB_ENABLED, 1)
        val adbOnVec = guard.runDeepSecurityAudit(includeSystemApps = false, logAuditEvent = true)
            .first { it.id == "sideload_adb" }
        assertEquals(AuditSeverity.DANGER, adbOnVec.severity)
        assertEquals(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS, adbOnVec.settingsAction)
    }

    @Test
    fun `screen recording file detector ignores completed mp4 files and stale tmp files`() {
        val guard = LiveGuardMonitor.getInstance(context)
        val recDir = File(Environment.getExternalStorageDirectory(), "DCIM/ScreenRecorder")
        recDir.mkdirs()

        val completedMp4 = File(recDir, "Screenrecorder-2026-test.mp4").apply {
            writeBytes(byteArrayOf(1, 2, 3, 4))
            setLastModified(System.currentTimeMillis() - 3_000L)
        }
        val staleTmp = File(recDir, "Screenrecorder-stale.tmp").apply {
            writeBytes(byteArrayOf(1, 2, 3))
            setLastModified(System.currentTimeMillis() - 60_000L)
        }

        try {
            // Neither a completed mp4 nor a 60s-old stale .tmp should falsely trigger active capture
            assertNull(guard.detectActiveScreenRecordingFile())

            // A freshly written active .tmp recording buffer SHOULD be detected
            val activeTmp = File(recDir, "Screenrecorder-live.tmp").apply {
                writeBytes(byteArrayOf(5, 6, 7, 8))
                setLastModified(System.currentTimeMillis())
            }
            try {
                val hit = guard.detectActiveScreenRecordingFile()
                assertNotNull(hit)
                assertEquals("Screenrecorder-live.tmp", hit?.fileName)
            } finally {
                activeTmp.delete()
            }
        } finally {
            completedMp4.delete()
            staleTmp.delete()
        }
    }

    @Test
    fun `snoozing SCREEN does not silence LOCATION audit checks and routine audit skips live sensor duplicates`() {
        val guard = LiveGuardMonitor.getInstance(context)
        // Initialize baseline first so subsequent vectors count as new changes
        guard.evaluateAndNotifyLiveGuardChecks(emptyList(), forceNotifyActive = false)
        guard.clearEvents()

        // Snooze only SCREEN sensor
        guard.snoozeTarget("SENSOR:SCREEN", "Screen", durationMs = 60_000L)

        val fakeVectors = listOf(
            SecurityAuditVector(
                id = "screen_recording",
                title = "Screen Recording / Mirroring",
                plainSummary = "Active",
                detailedAdvice = "Advice",
                severity = AuditSeverity.DANGER,
                statusBadge = "LIVE CAPTURE",
                extraDetails = listOf("OS Screen Recording")
            ),
            SecurityAuditVector(
                id = "live_mic_cam",
                title = "Mic & Camera Live",
                plainSummary = "Active",
                detailedAdvice = "Advice",
                severity = AuditSeverity.DANGER,
                statusBadge = "ACTIVE NOW",
                extraDetails = listOf("Microphone active (1 stream)")
            ),
            SecurityAuditVector(
                id = "bg_location",
                title = "Background Location",
                plainSummary = "1 app allowed 24/7 GPS",
                detailedAdvice = "Advice",
                severity = AuditSeverity.WARNING,
                statusBadge = "1 APPS",
                extraDetails = listOf("com.tracker.gps")
            )
        )

        guard.evaluateAndNotifyLiveGuardChecks(fakeVectors, forceNotifyActive = false)
        val recorded = guard.events.value

        // bg_location MUST still be recorded even though SCREEN is snoozed
        assertTrue(recorded.any { it.type == "LOCATION" })
        // screen_recording & live_mic_cam must NOT duplicate live callback alerts in routine audit runs
        assertFalse(recorded.any { it.type == "SCREEN" || it.type == "MIC" || it.type == "CAMERA" })

        // Calling evaluate again with the same vector must NOT spam duplicate events even without POST_NOTIFICATIONS
        val countBefore = guard.events.value.size
        guard.evaluateAndNotifyLiveGuardChecks(fakeVectors, forceNotifyActive = false)
        assertEquals(countBefore, guard.events.value.size)
    }

    @Test
    fun `trusting an app for MIC only does not suppress LOCATION or CAMERA checks`() {
        val guard = LiveGuardMonitor.getInstance(context)
        val pkg = "com.example.chatapp"
        guard.setAppTrust(pkg, setOf(GuardSensorType.MIC), source = "Test")

        assertTrue(guard.isSensorTrusted(pkg, GuardSensorType.MIC))
        assertFalse(guard.isSensorTrusted(pkg, GuardSensorType.CAMERA))
        assertFalse(guard.isSensorTrusted(pkg, GuardSensorType.LOCATION))
        assertFalse(guard.isSensorTrusted(pkg, GuardSensorType.CLIPBOARD))
        assertFalse(guard.isSensorTrusted(pkg, GuardSensorType.SCREEN))
    }

    @Test
    fun `camera activation on locked keyguard (Face Unlock) is suppressed`() {
        val guard = LiveGuardMonitor.getInstance(context)
        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        Shadows.shadowOf(km).setKeyguardLocked(true)

        // Simulate lens "1" becoming busy during Face Unlock while screen is locked
        val busyField = LiveGuardMonitor::class.java.getDeclaredField("busyCameras").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val busySet = busyField.get(guard) as MutableSet<String>
        synchronized(busySet) { busySet.add("1") }

        guard.updateCameraState()
        Thread.sleep(150L)
        ShadowLooper.idleMainLooper(250L, TimeUnit.MILLISECONDS)

        assertFalse(
            "Transient Face Unlock camera usage on locked screen must not mark camera active",
            guard.captureState.value.isCameraActive
        )

        // Unlock screen -> same busy lens should now be recognized as active camera usage
        Shadows.shadowOf(km).setKeyguardLocked(false)
        guard.updateCameraState()
        Thread.sleep(150L)
        ShadowLooper.idleMainLooper(250L, TimeUnit.MILLISECONDS)

        assertTrue(
            "Active camera lens after unlocking must be detected",
            guard.captureState.value.isCameraActive
        )
    }

    @Test
    fun `OS screen recording callback immediately flags and clears capture state`() {
        val guard = LiveGuardMonitor.getInstance(context)
        guard.startMonitoring()

        guard.onOsScreenRecordingStateChanged(true)
        ShadowLooper.idleMainLooper(200L, TimeUnit.MILLISECONDS)
        Thread.sleep(150L)

        assertTrue(guard.captureState.value.isScreenRecordingOrCasting)
        assertTrue(guard.captureState.value.activeCaptureDisplays.any { it.contains("OS Screen Recording") })

        guard.onOsScreenRecordingStateChanged(false)
        ShadowLooper.idleMainLooper(200L, TimeUnit.MILLISECONDS)
        Thread.sleep(150L)

        assertFalse(guard.captureState.value.isScreenRecordingOrCasting)
        assertTrue(guard.captureState.value.activeCaptureDisplays.isEmpty())
    }
}
