package com.example.service

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import com.example.ui.theme.ThemeState
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object PreferencesBackupManager {

    private const val THERMAL_PREFS = "thermal_prefs"
    private const val GUARD_PREFS = "guard_events_prefs"
    private const val BOOKMARK_PREFS = "file_bookmarks_prefs"
    private const val KEY_INITIALIZED = "prefs_initialized"
    private const val KEY_AUTO_DOC_BACKUP = "auto_backup_documents"
    private const val INTERNAL_MIRROR_NAME = "dot_settings_mirror.json"

    fun markInitialized(context: Context) {
        val prefs = context.getSharedPreferences(THERMAL_PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_INITIALIZED, false)) {
            prefs.edit().putBoolean(KEY_INITIALIZED, true).commit()
        }
    }

    fun exportToJson(context: Context): String {
        val thermal = context.getSharedPreferences(THERMAL_PREFS, Context.MODE_PRIVATE)
        val guard = context.getSharedPreferences(GUARD_PREFS, Context.MODE_PRIVATE)
        val bookmarks = context.getSharedPreferences(BOOKMARK_PREFS, Context.MODE_PRIVATE)

        val root = JSONObject()
        root.put("schemaVersion", 1)
        root.put("exportedAt", System.currentTimeMillis())

        val thermalObj = JSONObject().apply {
            put("theme_accent", thermal.getString("theme_accent", "CYAN"))
            put("theme_surface", thermal.getString("theme_surface", "OBSIDIAN"))
            put("ultra_minimal_cards", thermal.getBoolean("ultra_minimal_cards", true))
            put("ambient_glow", thermal.getBoolean("ambient_glow", false))
            put("warning_temp", thermal.getFloat("warning_temp", 35f).toDouble())
            put("haptic_feedback", thermal.getBoolean("haptic_feedback", true))
            put("background_monitoring", thermal.getBoolean("background_monitoring", true))
            put("live_guard_alerts", thermal.getBoolean("live_guard_alerts", true))
            put(KEY_AUTO_DOC_BACKUP, thermal.getBoolean(KEY_AUTO_DOC_BACKUP, true))
        }
        root.put("thermal_prefs", thermalObj)

        val guardObj = JSONObject().apply {
            put("rules_alert_mode", guard.getString("rules_alert_mode", GuardAlertMode.SMART_COVERT.name))
            put("rules_debounce", guard.getBoolean("rules_debounce", true))
            put("rules_silence_calls", guard.getBoolean("rules_silence_calls", true))
            put("rules_quiet_fg", guard.getBoolean("rules_quiet_fg", true))
            put("rules_pause_screen_off", guard.getBoolean("rules_pause_screen_off", true))
            guard.getString("trusted_rules_json", null)?.let { put("trusted_rules_json", it) }
            val legacyIgnored = guard.getStringSet("ignored_packages", emptySet()) ?: emptySet()
            put("ignored_packages", JSONArray(legacyIgnored.toList()))
        }
        root.put("guard_events_prefs", guardObj)

        val starred = bookmarks.getStringSet("starred_paths", emptySet()) ?: emptySet()
        root.put("starred_paths", JSONArray(starred.toList()))

        return root.toString(2)
    }

    fun importFromJson(context: Context, jsonStr: String): Boolean {
        return try {
            val root = JSONObject(jsonStr)
            val thermalObj = root.optJSONObject("thermal_prefs")
            val guardObj = root.optJSONObject("guard_events_prefs")
            val starredArr = root.optJSONArray("starred_paths")

            if (thermalObj == null && guardObj == null && starredArr == null) {
                return false
            }

            if (thermalObj != null) {
                val tEdit = context.getSharedPreferences(THERMAL_PREFS, Context.MODE_PRIVATE).edit()
                if (thermalObj.has("theme_accent")) tEdit.putString("theme_accent", thermalObj.optString("theme_accent", "CYAN"))
                if (thermalObj.has("theme_surface")) tEdit.putString("theme_surface", thermalObj.optString("theme_surface", "OBSIDIAN"))
                if (thermalObj.has("ultra_minimal_cards")) tEdit.putBoolean("ultra_minimal_cards", thermalObj.optBoolean("ultra_minimal_cards", true))
                if (thermalObj.has("ambient_glow")) tEdit.putBoolean("ambient_glow", thermalObj.optBoolean("ambient_glow", false))
                if (thermalObj.has("warning_temp")) tEdit.putFloat("warning_temp", thermalObj.optDouble("warning_temp", 35.0).toFloat())
                if (thermalObj.has("haptic_feedback")) tEdit.putBoolean("haptic_feedback", thermalObj.optBoolean("haptic_feedback", true))
                if (thermalObj.has("background_monitoring")) tEdit.putBoolean("background_monitoring", thermalObj.optBoolean("background_monitoring", true))
                if (thermalObj.has("live_guard_alerts")) tEdit.putBoolean("live_guard_alerts", thermalObj.optBoolean("live_guard_alerts", true))
                if (thermalObj.has(KEY_AUTO_DOC_BACKUP)) tEdit.putBoolean(KEY_AUTO_DOC_BACKUP, thermalObj.optBoolean(KEY_AUTO_DOC_BACKUP, true))
                tEdit.putBoolean(KEY_INITIALIZED, true)
                tEdit.commit()
            }

            if (guardObj != null) {
                val gEdit = context.getSharedPreferences(GUARD_PREFS, Context.MODE_PRIVATE).edit()
                if (guardObj.has("rules_alert_mode")) gEdit.putString("rules_alert_mode", guardObj.optString("rules_alert_mode", GuardAlertMode.SMART_COVERT.name))
                if (guardObj.has("rules_debounce")) gEdit.putBoolean("rules_debounce", guardObj.optBoolean("rules_debounce", true))
                if (guardObj.has("rules_silence_calls")) gEdit.putBoolean("rules_silence_calls", guardObj.optBoolean("rules_silence_calls", true))
                if (guardObj.has("rules_quiet_fg")) gEdit.putBoolean("rules_quiet_fg", guardObj.optBoolean("rules_quiet_fg", true))
                if (guardObj.has("rules_pause_screen_off")) gEdit.putBoolean("rules_pause_screen_off", guardObj.optBoolean("rules_pause_screen_off", true))
                val trustedJson = guardObj.optString("trusted_rules_json", "")
                if (trustedJson.isNotBlank()) {
                    gEdit.putString("trusted_rules_json", trustedJson)
                }
                val ignoredArr = guardObj.optJSONArray("ignored_packages")
                if (ignoredArr != null) {
                    val set = mutableSetOf<String>()
                    for (i in 0 until ignoredArr.length()) {
                        val pkg = ignoredArr.optString(i)
                        if (pkg.isNotBlank()) set.add(pkg)
                    }
                    gEdit.putStringSet("ignored_packages", set)
                }
                gEdit.commit()
            }

            if (starredArr != null) {
                val set = mutableSetOf<String>()
                for (i in 0 until starredArr.length()) {
                    val path = starredArr.optString(i)
                    if (path.isNotBlank()) set.add(path)
                }
                context.getSharedPreferences(BOOKMARK_PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putStringSet("starred_paths", set)
                    .commit()
            }

            ThemeState.loadFromPrefs(context)
            LiveGuardMonitor.peekInstance()?.reloadPersistedState()
            try {
                ThermalWidgetProvider.updateAllWidgets(context)
            } catch (_: Exception) {
            }
            saveMirrorSnapshot(context)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun saveMirrorSnapshot(context: Context) {
        try {
            markInitialized(context)
            val json = exportToJson(context)
            val target = File(context.filesDir, INTERNAL_MIRROR_NAME)
            val temp = File(context.filesDir, "$INTERNAL_MIRROR_NAME.tmp")
            temp.writeText(json, Charsets.UTF_8)
            if (!temp.renameTo(target)) {
                target.writeText(json, Charsets.UTF_8)
                temp.delete()
            }

            val thermal = context.getSharedPreferences(THERMAL_PREFS, Context.MODE_PRIVATE)
            if (thermal.getBoolean(KEY_AUTO_DOC_BACKUP, true) && canAccessSharedDocuments()) {
                val backupFile = getSharedDocumentsBackupFile()
                backupFile.parentFile?.mkdirs()
                backupFile.writeText(json, Charsets.UTF_8)
            }
        } catch (_: Exception) {
        }
    }

    /**
     * Automatically restores settings if SharedPreferences were cleared (e.g. reinstall / prefs reset)
     * using either the internal atomic mirror or the user-permitted Documents/dot backup.
     */
    fun autoRestoreIfCleared(context: Context): Boolean {
        val prefs = context.getSharedPreferences(THERMAL_PREFS, Context.MODE_PRIVATE)
        val hasAnySavedPref = prefs.getBoolean(KEY_INITIALIZED, false) ||
            prefs.contains("theme_accent") ||
            prefs.contains("theme_surface") ||
            prefs.contains("warning_temp")
        if (hasAnySavedPref) {
            markInitialized(context)
            return false
        }

        val internalMirror = File(context.filesDir, INTERNAL_MIRROR_NAME)
        if (internalMirror.exists() && internalMirror.length() > 10L) {
            val ok = try {
                importFromJson(context, internalMirror.readText(Charsets.UTF_8))
            } catch (_: Exception) {
                false
            }
            if (ok) return true
        }

        if (canAccessSharedDocuments()) {
            val externalMirror = getSharedDocumentsBackupFile()
            if (externalMirror.exists() && externalMirror.length() > 10L) {
                val ok = try {
                    importFromJson(context, externalMirror.readText(Charsets.UTF_8))
                } catch (_: Exception) {
                    false
                }
                if (ok) return true
            }
        }

        markInitialized(context)
        return false
    }

    fun exportToUri(context: Context, uri: Uri): Boolean {
        return try {
            val json = exportToJson(context)
            context.contentResolver.openOutputStream(uri)?.use { os ->
                os.write(json.toByteArray(Charsets.UTF_8))
                os.flush()
            } ?: return false
            true
        } catch (_: Exception) {
            false
        }
    }

    fun importFromUri(context: Context, uri: Uri): Boolean {
        return try {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use {
                it.readText()
            } ?: return false
            importFromJson(context, text)
        } catch (_: Exception) {
            false
        }
    }

    private fun canAccessSharedDocuments(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.isExternalStorageManager()
            } else {
                false
            }
        } catch (_: Throwable) {
            false
        }
    }

    private fun getSharedDocumentsBackupFile(): File {
        val docs = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        return File(File(docs, "dot"), "dot_settings_backup.json")
    }
}
