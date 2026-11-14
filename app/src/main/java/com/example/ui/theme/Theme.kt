package com.example.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
fun AppTheme(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    androidx.compose.runtime.remember(context) {
        ThemeState.loadFromPrefs(context)
        true
    }
    LaunchedEffect(Unit) {
        ThemeState.loadFromPrefs(context)
    }

    val isLight = ThemeState.surfacePreset.isLight

    val colorScheme = if (isLight) {
        lightColorScheme(
            primary = AuraCyan,
            secondary = AuraPurple,
            tertiary = AuraRose,
            background = Obsidian,
            surface = GlassPanel,
            surfaceVariant = GlassPanel,
            onPrimary = Obsidian,
            onSecondary = Obsidian,
            onTertiary = Obsidian,
            onBackground = TextPrimary,
            onSurface = TextPrimary,
            onSurfaceVariant = TextSecondary
        )
    } else {
        darkColorScheme(
            primary = AuraCyan,
            secondary = AuraPurple,
            tertiary = AuraRose,
            background = Obsidian,
            surface = GlassPanel,
            surfaceVariant = GlassPanel,
            onPrimary = Obsidian,
            onSecondary = Obsidian,
            onTertiary = Obsidian,
            onBackground = TextPrimary,
            onSurface = TextPrimary,
            onSurfaceVariant = TextSecondary
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            @Suppress("DEPRECATION")
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            @Suppress("DEPRECATION")
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = isLight
            controller.isAppearanceLightNavigationBars = isLight
            WindowCompat.setDecorFitsSystemWindows(window, false)
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
