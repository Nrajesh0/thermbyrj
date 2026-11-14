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
        assertEquals("Therm by Rj", appName)
    }

    @Test
    fun `formatBytes formats sizes accurately`() {
        assertEquals("0 B", formatBytes(0L))
        assertEquals("512.0 B", formatBytes(512L))
        assertEquals("1.0 KB", formatBytes(1024L))
        assertEquals("1.5 MB", formatBytes(1572864L))
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
    fun `live guard monitor runs 8 point deep audit and logs events`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val monitor = LiveGuardMonitor.getInstance(context)
        monitor.startMonitoring()

        val vectors = monitor.runDeepSecurityAudit(includeSystemApps = false)
        assertEquals(8, vectors.size)

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
}
