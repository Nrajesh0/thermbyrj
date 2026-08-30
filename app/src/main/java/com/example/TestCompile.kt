package com.example

import android.content.Context
import androidx.security.crypto.MasterKey

fun testMasterKey(context: Context) {
    MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .setUserAuthenticationRequired(true, 120)
        .build()
}
