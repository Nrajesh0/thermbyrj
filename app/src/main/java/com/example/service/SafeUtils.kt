package com.example.service

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.widget.Toast
import kotlinx.coroutines.CoroutineExceptionHandler

/**
 * Safely unwraps any [ContextWrapper] chain (such as [android.view.ContextThemeWrapper],
 * Compose modal/dialog wrappers, or OEM freeform/floating-window contexts) to find the
 * backing [Activity] without ever throwing [ClassCastException].
 */
tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext?.findActivity()
    else -> null
}

/**
 * Launches [intent] safely without crashing on [android.content.ActivityNotFoundException],
 * [SecurityException], or OEM-specific binder/window failures.
 * Automatically adds [Intent.FLAG_ACTIVITY_NEW_TASK] when called from a non-Activity context.
 */
fun Context.safeStartActivity(
    intent: Intent,
    fallbackToast: String? = null
): Boolean {
    return try {
        val launchIntent = Intent(intent).apply {
            if (findActivity() == null) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        startActivity(launchIntent)
        true
    } catch (_: Throwable) {
        if (!fallbackToast.isNullOrBlank()) {
            try {
                Toast.makeText(this, fallbackToast, Toast.LENGTH_SHORT).show()
            } catch (_: Throwable) {
            }
        }
        false
    }
}

/**
 * Ensures any [Bitmap] passed to Compose `asImageBitmap()` or a software [android.graphics.Canvas]
 * is non-null, not recycled, and backed by software memory (never [Bitmap.Config.HARDWARE]).
 */
fun Bitmap?.toSafeComposeBitmap(): Bitmap? {
    if (this == null || isRecycled || width <= 0 || height <= 0) return null
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && config == Bitmap.Config.HARDWARE) {
            copy(Bitmap.Config.ARGB_8888, false)
        } else {
            this
        }
    } catch (_: Throwable) {
        null
    }
}

/**
 * Process-safe [CoroutineExceptionHandler] so uncaught exceptions inside background services,
 * monitors, or coordinators never terminate the app process.
 */
fun safeCoroutineExceptionHandler(): CoroutineExceptionHandler =
    CoroutineExceptionHandler { _, _ -> }
