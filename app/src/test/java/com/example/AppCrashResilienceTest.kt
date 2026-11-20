package com.example

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.ContextThemeWrapper
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.data.ThermalRecord
import com.example.service.FileManagerOps
import com.example.service.FileShredService
import com.example.service.GuardAlertActionReceiver
import com.example.service.LiveGuardMonitor
import com.example.service.SharedClipboardOp
import com.example.service.SharedFileClipboard
import com.example.service.ThermalMonitor
import com.example.service.ThermalTrackerService
import com.example.service.ThermalWidgetProvider
import com.example.service.findActivity
import com.example.service.safeStartActivity
import com.example.service.toSafeComposeBitmap
import com.example.ui.screens.AppListActivity
import com.example.ui.screens.DataTelemetryActivity
import com.example.ui.screens.DeepCleanActivity
import com.example.ui.screens.DuplicateFinderActivity
import com.example.ui.screens.DuplicateHashVerifier
import com.example.ui.screens.FileCategoryActivity
import com.example.ui.screens.GuardRulesActivity
import com.example.ui.screens.VaultActivity
import com.example.ui.screens.VaultManager
import com.example.ui.theme.AppTheme
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppCrashResilienceTest {

    @Test
    fun `MainActivity survives full lifecycle, state save, and restart without crashing`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        controller.create().start().resume()

        val savedState = Bundle()
        controller.pause().saveInstanceState(savedState).stop().destroy()

        // Recreate from saved state (simulates OEM sidebar / multi-window / process recreation)
        val recreated = Robolectric.buildActivity(MainActivity::class.java)
        recreated.create(savedState).start().resume().pause().stop().destroy()
    }

    @Test
    fun `all secondary activities launch and tear down cleanly`() {
        val activities = listOf(
            AppListActivity::class.java,
            DataTelemetryActivity::class.java,
            DeepCleanActivity::class.java,
            DuplicateFinderActivity::class.java,
            GuardRulesActivity::class.java,
            VaultActivity::class.java
        )
        for (cls in activities) {
            val ctrl = Robolectric.buildActivity(cls)
            ctrl.create().start().resume().pause().stop().destroy()
        }

        // FileCategoryActivity with valid category extra
        val app = ApplicationProvider.getApplicationContext<Application>()
        val catIntent = Intent(app, FileCategoryActivity::class.java).apply {
            putExtra("CATEGORY_NAME", "Images")
        }
        Robolectric.buildActivity(FileCategoryActivity::class.java, catIntent)
            .create().start().resume().pause().stop().destroy()

        // FileCategoryActivity with missing extras (must not crash)
        Robolectric.buildActivity(FileCategoryActivity::class.java)
            .create().start().resume().pause().stop().destroy()
    }

    @Test
    fun `VaultManager recovers gracefully when Keystore is unavailable`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val registry = VaultManager.getRegistry(context)
        assertNotNull(registry)
        registry.edit().putString("test_key", "test_value").commit()
        assertEquals("test_value", registry.getString("test_key", null))

        // Must never throw even if Keystore is unavailable
        VaultManager.getMasterKeyOrNull(context)
    }

    @Test
    fun `SafeUtils guards unresolvable intents, wrapped contexts, and recycled bitmaps`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        assertNull(app.findActivity())

        val activity = Robolectric.buildActivity(GuardRulesActivity::class.java).create().get()
        val wrapped = ContextWrapper(ContextThemeWrapper(activity, android.R.style.Theme_Material))
        assertEquals(activity, wrapped.findActivity())

        // Context throwing ActivityNotFoundException must be caught and return false
        val throwingContext = object : ContextWrapper(app) {
            override fun startActivity(intent: Intent?) {
                throw android.content.ActivityNotFoundException("No handler for intent")
            }
        }
        val bogusIntent = Intent("com.example.nonexistent.ACTION_DOES_NOT_EXIST_XYZ")
        val launched = throwingContext.safeStartActivity(bogusIntent)
        assertFalse(launched)

        // Bitmap safety: null, valid, and recycled bitmaps
        assertNull((null as Bitmap?).toSafeComposeBitmap())

        val validBmp = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        val safeValid = validBmp.toSafeComposeBitmap()
        assertNotNull(safeValid)

        validBmp.recycle()
        assertNull(validBmp.toSafeComposeBitmap())
    }

    @Test
    fun `background services and broadcast receiver handle malformed intents safely`() {
        val app = ApplicationProvider.getApplicationContext<Application>()

        // ThermalTrackerService lifecycle
        val trackerCtrl = Robolectric.buildService(ThermalTrackerService::class.java)
        trackerCtrl.create().startCommand(0, 1).destroy()

        // FileShredService with empty/null intents must not crash
        val shredCtrl = Robolectric.buildService(FileShredService::class.java)
        shredCtrl.create().startCommand(0, 1).destroy()

        // GuardAlertActionReceiver with empty, unknown, and valid actions
        val receiver = GuardAlertActionReceiver()
        receiver.onReceive(app, Intent())
        receiver.onReceive(app, Intent("com.example.UNKNOWN_ACTION"))
        receiver.onReceive(
            app,
            Intent(GuardAlertActionReceiver.ACTION_SNOOZE_1H).apply {
                putExtra(GuardAlertActionReceiver.EXTRA_PACKAGE_NAME, "com.test.sample")
                putExtra(GuardAlertActionReceiver.EXTRA_SENSOR_TYPE, "INVALID_ENUM_VALUE")
            }
        )
    }

    @Test
    fun `ThermalMonitor, LiveGuardMonitor, and ThermalWidgetProvider handle edge cases safely`() {
        val app = ApplicationProvider.getApplicationContext<Application>()

        val thermalMonitor = ThermalMonitor.getInstance(app)
        thermalMonitor.startMonitoring()
        thermalMonitor.forceUpdate()
        thermalMonitor.stopMonitoring()

        LiveGuardMonitor.resetInstanceForTests()
        val guardMonitor = LiveGuardMonitor.getInstance(app)
        guardMonitor.startMonitoring()
        guardMonitor.refreshAllStates()
        guardMonitor.stopMonitoring()

        // Widget update & sparkline edge cases (empty, single point, identical timestamps, NaN/Inf)
        val widgetProvider = ThermalWidgetProvider()
        widgetProvider.onUpdate(app, android.appwidget.AppWidgetManager.getInstance(app), intArrayOf())

        val companionObj = ThermalWidgetProvider.Companion
        val method = companionObj::class.java.getDeclaredMethod(
            "renderThermalSparklineBitmap",
            List::class.java,
            Int::class.javaPrimitiveType
        )
        method.isAccessible = true

        assertNotNull(method.invoke(companionObj, emptyList<ThermalRecord>(), 0xFF00E5FF.toInt()))
        val single = listOf(ThermalRecord(timestamp = 1000L, batteryTemp = 35.0f, cpuTemp = 36f, batteryLevel = 80))
        assertNotNull(method.invoke(companionObj, single, 0xFF00E5FF.toInt()))

        val sameTs = listOf(
            ThermalRecord(timestamp = 1000L, batteryTemp = 35.0f, cpuTemp = 36f, batteryLevel = 80),
            ThermalRecord(timestamp = 1000L, batteryTemp = 35.0f, cpuTemp = 36f, batteryLevel = 80)
        )
        assertNotNull(method.invoke(companionObj, sameTs, 0xFF00E5FF.toInt()))

        val nonFinite = listOf(
            ThermalRecord(timestamp = 1000L, batteryTemp = Float.NaN, cpuTemp = 0f, batteryLevel = 80),
            ThermalRecord(timestamp = 2000L, batteryTemp = Float.POSITIVE_INFINITY, cpuTemp = 0f, batteryLevel = 80)
        )
        assertNotNull(method.invoke(companionObj, nonFinite, 0xFF00E5FF.toInt()))
        assertTrue(thermalMonitor.liveData.value.thermalStatusLabel.isNotBlank())
    }

    @Test
    fun `DuplicateHashVerifier eliminates false positives when headers match but content differs`() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "dot_dupe_test_${System.nanoTime()}")
        tempDir.mkdirs()
        try {
            val sharedHeader = ByteArray(16 * 1024) { 0x42 }
            val tailA = ByteArray(24 * 1024) { 0x11 }
            val tailB = ByteArray(24 * 1024) { 0x22 }

            // Two files with IDENTICAL size (40 KB) and IDENTICAL first 16 KB, but different tail
            val fileA = File(tempDir, "photo_a.bin").apply { writeBytes(sharedHeader + tailA) }
            val fileB = File(tempDir, "photo_b.bin").apply { writeBytes(sharedHeader + tailB) }
            // True exact copy of fileA
            val fileACopy = File(tempDir, "photo_a_copy.bin").apply { writeBytes(sharedHeader + tailA) }

            val groups = DuplicateHashVerifier.findExactDuplicates(listOf(fileA, fileB, fileACopy))
            assertEquals("Only true identical copies should form a duplicate group", 1, groups.size)
            val matchedNames = groups.first().files.map { it.name }.toSet()
            assertEquals(setOf("photo_a.bin", "photo_a_copy.bin"), matchedNames)
            assertFalse("Distinct file with matching 8KB header must NOT be flagged", "photo_b.bin" in matchedNames)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `FileManagerOps blocks self-recursive paste, Zip-Slip sibling traversal, and invalid renames`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val root = File(System.getProperty("java.io.tmpdir"), "dot_fmops_test_${System.nanoTime()}")
        root.mkdirs()
        try {
            // 1. Self-recursion protection
            val parentFolder = File(root, "docs").apply { mkdirs() }
            val subFolder = File(parentFolder, "nested").apply { mkdirs() }
            File(parentFolder, "note.txt").writeText("hello")
            assertTrue(FileManagerOps.isDescendantOrSameDirectory(parentFolder, parentFolder))
            assertTrue(FileManagerOps.isDescendantOrSameDirectory(parentFolder, subFolder))
            val pastedSelf = FileManagerOps.executeClipboardPaste(
                app,
                SharedFileClipboard(setOf(parentFolder.absolutePath), SharedClipboardOp.COPY),
                subFolder
            )
            assertEquals("Pasting a folder into its own descendant must be blocked", 0, pastedSelf)

            // 2. Incrementing unique copy names
            val srcFile = File(root, "report.txt").apply { writeText("v1") }
            val copy1 = FileManagerOps.buildUniqueDestination(root, srcFile, sameDirectoryCopy = true)
            assertEquals("report_copy.txt", copy1.name)
            copy1.writeText("v1")
            val copy2 = FileManagerOps.buildUniqueDestination(root, srcFile, sameDirectoryCopy = true)
            assertEquals("report_copy_2.txt", copy2.name)

            // 3. Invalid filename validation
            assertFalse(FileManagerOps.isValidFileName(""))
            assertFalse(FileManagerOps.isValidFileName(".."))
            assertFalse(FileManagerOps.isValidFileName("foo/bar.txt"))
            assertFalse(FileManagerOps.isValidFileName("foo\\bar.txt"))
            assertTrue(FileManagerOps.isValidFileName("clean_name.pdf"))
            assertFalse(FileManagerOps.renameFileSafely(app, srcFile, "../escape.txt"))

            // 4. Zip-Slip sibling prefix bypass protection (extracting archive.zip -> outDir "archive", malicious entry "../archive_evil/pwn.txt")
            val maliciousZip = File(root, "archive.zip")
            ZipOutputStream(FileOutputStream(maliciousZip)).use { zos ->
                zos.putNextEntry(ZipEntry("../archive_evil/pwn.txt"))
                zos.write("malicious".toByteArray())
                zos.closeEntry()
            }
            val extracted = FileManagerOps.extractZipArchive(app, maliciousZip, root)
            assertFalse("Zip-Slip sibling prefix traversal must fail", extracted)
            assertFalse("Malicious sibling file must not be created", File(root, "archive_evil/pwn.txt").exists())

            // 5. Empty directory preserved in ZIP compression
            val emptyDir = File(root, "empty_folder").apply { mkdirs() }
            val goodZip = File(root, "good.zip")
            assertTrue(FileManagerOps.compressFilesToZip(listOf(emptyDir, srcFile), goodZip))
            assertTrue(goodZip.exists() && goodZip.length() > 0L)
        } finally {
            root.deleteRecursively()
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MainActivityUiResilienceTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @org.junit.After
    fun tearDownMonitors() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        LiveGuardMonitor.getInstance(app).stopMonitoring()
        ThermalMonitor.getInstance(app).stopMonitoring()
    }

    @Test
    fun `switching all 4 tabs and toggling settings orb works without crashing`() {
        composeRule.mainClock.autoAdvance = false
        composeRule.mainClock.advanceTimeBy(500L)

        // Navigate through all 4 tabs via testTags
        composeRule.onNodeWithTag("nav_tab_files").performClick()
        composeRule.mainClock.advanceTimeBy(500L)

        composeRule.onNodeWithTag("nav_tab_privacy").performClick()
        composeRule.mainClock.advanceTimeBy(500L)

        composeRule.onNodeWithTag("nav_tab_guard").performClick()
        composeRule.mainClock.advanceTimeBy(500L)

        composeRule.onNodeWithTag("nav_tab_tools").performClick()
        composeRule.mainClock.advanceTimeBy(500L)

        // Open and close Settings Orb
        composeRule.onNodeWithTag("settings_orb_button").performClick()
        composeRule.mainClock.advanceTimeBy(500L)

        composeRule.onNodeWithTag("settings_close_button").performClick()
        composeRule.mainClock.advanceTimeBy(500L)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ThemeWrapperResilienceTest {

    @get:Rule
    val composeRule = createComposeRule()

    @org.junit.Before
    fun stopLingeringMonitors() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        LiveGuardMonitor.getInstance(app).stopMonitoring()
        ThermalMonitor.getInstance(app).stopMonitoring()
    }

    @Test
    fun `AppTheme composes cleanly without casting errors`() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            AppTheme {
                Text("Theme Safety OK")
            }
        }
        composeRule.mainClock.advanceTimeBy(500L)
    }
}
