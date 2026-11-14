package com.example.ui.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

enum class AccentPreset(val key: String, val label: String, val color: Color) {
    CYAN("CYAN", "Cyber Cyan", Color(0xFF06B6D4)),
    GREEN("GREEN", "Emerald", Color(0xFF10B981)),
    PURPLE("PURPLE", "Amethyst", Color(0xFF8B5CF6)),
    ROSE("ROSE", "Crimson", Color(0xFFF43F5E)),
    AMBER("AMBER", "Solar Amber", Color(0xFFF59E0B)),
    MONO("MONO", "Monochrome", Color(0xFFE2E8F0))
}

enum class SurfacePreset(
    val key: String,
    val label: String,
    val bg: Color,
    val panel: Color,
    val border: Color,
    val isLight: Boolean = false
) {
    PURE_WHITE("PURE_WHITE", "Pure White", Color(0xFFFFFFFF), Color(0xFFF1F5F9), Color(0x1E0F172A), isLight = true),
    SOFT_WHITE("SOFT_WHITE", "Soft White", Color(0xFFF1F5F9), Color(0xFFFFFFFF), Color(0x1A0F172A), isLight = true),
    OLED("OLED", "OLED Black", Color(0xFF000000), Color(0xFF090A0F), Color(0x16FFFFFF), isLight = false),
    OBSIDIAN("OBSIDIAN", "Obsidian", Color(0xFF030305), Color(0xFF0D0F17), Color(0x1AFFFFFF), isLight = false),
    SLATE("SLATE", "Dark Slate", Color(0xFF0B0F19), Color(0xFF151C2C), Color(0x22FFFFFF), isLight = false)
}

object ThemeState {
    var accentPreset by mutableStateOf(AccentPreset.CYAN)
    var surfacePreset by mutableStateOf(SurfacePreset.OBSIDIAN)
    var ultraMinimalCards by mutableStateOf(true)
    var showAmbientGlow by mutableStateOf(false)

    fun loadFromPrefs(context: Context) {
        val prefs = context.getSharedPreferences("thermal_prefs", Context.MODE_PRIVATE)
        val accentKey = prefs.getString("theme_accent", "CYAN") ?: "CYAN"
        val surfaceKey = prefs.getString("theme_surface", "OBSIDIAN") ?: "OBSIDIAN"
        accentPreset = AccentPreset.entries.find { it.key == accentKey } ?: AccentPreset.CYAN
        surfacePreset = SurfacePreset.entries.find { it.key == surfaceKey } ?: SurfacePreset.OBSIDIAN
        ultraMinimalCards = prefs.getBoolean("ultra_minimal_cards", true)
        showAmbientGlow = prefs.getBoolean("ambient_glow", false)
    }
}

val Obsidian: Color
    get() = ThemeState.surfacePreset.bg

val GlassPanel: Color
    get() = ThemeState.surfacePreset.panel

val GlassBorder: Color
    get() = ThemeState.surfacePreset.border

val AuraCyan: Color
    get() = if (ThemeState.accentPreset == AccentPreset.MONO && ThemeState.surfacePreset.isLight) {
        Color(0xFF0F172A)
    } else if (ThemeState.surfacePreset.isLight && ThemeState.accentPreset == AccentPreset.CYAN) {
        Color(0xFF0891B2)
    } else {
        ThemeState.accentPreset.color
    }

val AuraPurple = Color(0xFF8B5CF6)
val AuraGreen = Color(0xFF10B981)
val AuraOrange = Color(0xFFF97316)
val AuraRose = Color(0xFFF43F5E)

val TextPrimary: Color
    get() = if (ThemeState.surfacePreset.isLight) Color(0xFF0F172A) else Color(0xFFF8FAFC)

val TextSecondary: Color
    get() = if (ThemeState.surfacePreset.isLight) Color(0xFF475569) else Color(0xFF94A3B8)

val TextTertiary: Color
    get() = if (ThemeState.surfacePreset.isLight) Color(0xFF64748B) else Color(0xFF475569)
