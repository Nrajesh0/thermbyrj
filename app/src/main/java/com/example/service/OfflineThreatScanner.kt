package com.example.service

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap

enum class ThreatSignalCategory(val label: String) {
    STEALTH_STALKERWARE("Stealth / Stalkerware"),
    IMPERSONATION("System / Brand Spoofing"),
    CERT_ANOMALY("Certificate / Debug Key"),
    TROJAN_DROPPER("Trojan / Dropper Combo")
}

data class ThreatHeuristicSignal(
    val id: String,
    val title: String,
    val shortChip: String,
    val description: String,
    val category: ThreatSignalCategory,
    val weight: Int,
    val isCritical: Boolean
)

data class ApkCertificateInspection(
    val sha256Fingerprint: String,
    val issuerSummary: String,
    val subjectSummary: String,
    val isDebugOrTestCertificate: Boolean,
    val isDebuggableManifest: Boolean,
    val hasMultipleSigners: Boolean
)

data class AppThreatIntelReport(
    val packageName: String,
    val appName: String,
    val isSystem: Boolean,
    val isBrandOem: Boolean,
    val isSideloaded: Boolean,
    val hasLauncherIcon: Boolean,
    val isBlankOrInvisibleLabel: Boolean,
    val isImpersonatingSystemOrBrand: Boolean,
    val impersonationReason: String?,
    val certInspection: ApkCertificateInspection,
    val threatSignals: List<ThreatHeuristicSignal>,
    val threatScore: Int
)

object OfflineThreatScanner {

    /**
     * Well-known AOSP / Android SDK test & debug signing certificate SHA-256 fingerprints
     * (normalized uppercase hex without colons).
     * Apps signed with these public keys were built with default SDK debug keys or public AOSP testkeys
     * (common in repackaged/modded APKs, droppers, and unofficial builds).
     */
    private val knownPublicTestKeySha256 = setOf(
        // AOSP testkey
        "61ED377E85D386A8DFEE6B864BD85B0BFAA5AF81",
        "A40DA80A59D170CAA950CF15C18C454D47A39B26989D8B640ECD745BA71BF5DC",
        // AOSP platform test key
        "27196E386B875E76ADF700E7EA84E4C6EEE33DFA8F55086812394C9F1A0A3B02",
        // AOSP shared test key
        "5B368CFF2DA2686996BC9B2D36B83666748B9876E26B2D8C87A40A7E30C12C15",
        // AOSP media test key
        "B79DF4A82E90B578C6B00E6709A426063F7C1E0B68C58E43A96A9C5675A8B0E6"
    )

    /**
     * Companion / plugin / system-helper packages that legitimately do not have a launcher icon
     * or are verified Google/OEM services.
     */
    private val legitimateHeadlessPackagePrefixes = listOf(
        "com.google.",
        "com.android.",
        "android.",
        "androidx.",
        "org.chromium.",
        "com.qualcomm.",
        "com.mediatek.",
        "com.samsung.",
        "com.sec.",
        "com.miui.",
        "com.xiaomi.",
        "com.oplus.",
        "com.coloros.",
        "com.heytap.",
        "com.oppo.",
        "com.vivo.",
        "com.oneplus.",
        "com.realme.",
        "com.huawei.",
        "com.hihonor.",
        "com.motorola.",
        "com.asus.",
        "com.sony.",
        "com.nothing.",
        "com.transsion."
    )

    /**
     * Exact deceptive app labels frequently used by stalkerware, spyware, and trojans
     * to masquerade as core Android OS components when installed as a non-system user app.
     */
    private val deceptiveSystemLabels = setOf(
        "system update",
        "system service",
        "system services",
        "android system",
        "android service",
        "google play service",
        "google play services",
        "play services update",
        "system settings",
        "settings",
        "security update",
        "device security",
        "framework service",
        "kernel service",
        "wifi service",
        "wi-fi service",
        "battery service",
        "network service",
        "sim toolkit",
        "package installer"
    )

    /**
     * High-profile app package prefixes & names that trojans often spoof in sideloaded APKs.
     */
    private val protectedBrandClaims = listOf(
        "WhatsApp" to "com.whatsapp",
        "Telegram" to "org.telegram.",
        "Signal" to "org.thoughtcrime.securesms",
        "Google Chrome" to "com.android.chrome",
        "Google Play Store" to "com.android.vending",
        "Gmail" to "com.google.android.gm",
        "YouTube" to "com.google.android.youtube",
        "Instagram" to "com.instagram.",
        "Facebook" to "com.facebook."
    )

    /**
     * Known commercial stalkerware / spyware component or package substrings.
     */
    private val knownSpywareComponentSignatures = listOf(
        "com.mspy",
        "com.flexispy",
        "com.clevguard",
        "kidsguard",
        "hoverwatch",
        "cocospy",
        "spyic",
        "minspy",
        "spzie",
        "spyera",
        "thetruthspy",
        "xnspy",
        "eyezy",
        "umobix",
        "mobiletrackerfree",
        "cerberus"
    )

    private data class CertCacheKey(
        val packageName: String,
        val lastUpdateTime: Long
    )

    private val certCache = ConcurrentHashMap<CertCacheKey, ApkCertificateInspection>()

    @Volatile
    private var cachedLauncherPkgs: Set<String> = emptySet()
    @Volatile
    private var cachedLauncherPkgsTimestamp: Long = 0L

    /**
     * Checks offline if Android's built-in Package Verifier / Google Play Protect verification
     * is enabled in global system settings (`package_verifier_enable` / ` verifier_verify_adb_installs`).
     */
    fun isPackageVerifierEnabled(context: Context): Boolean {
        return try {
            val enabled = Settings.Global.getInt(context.contentResolver, "package_verifier_enable", 1)
            enabled != 0
        } catch (_: Exception) {
            true
        }
    }

    /**
     * Queries all packages on the device that declare a legitimate user-facing entry point:
     * - `ACTION_MAIN` + `CATEGORY_LAUNCHER`
     * - `ACTION_MAIN` + `CATEGORY_INFO`
     * - `ACTION_MAIN` + `CATEGORY_HOME` (custom home launchers)
     * - Installed Input Methods (`InputMethodManager`)
     * - Installed Live Wallpaper services (`android.service.wallpaper.WallpaperService`)
     * - Installed AppWidget providers (`AppWidgetManager`)
     * This prevents false-positive "Ghost App" flags on legitimate widgets, keyboards, wallpapers, and themes.
     */
    fun getPackagesWithLauncherActivities(context: Context, forceRefresh: Boolean = false): Set<String> {
        val now = System.currentTimeMillis()
        if (!forceRefresh && cachedLauncherPkgs.isNotEmpty() && (now - cachedLauncherPkgsTimestamp) < 60_000L) {
            return cachedLauncherPkgs
        }
        val pm = context.packageManager
        val result = mutableSetOf<String>()
        try {
            val categories = listOf(
                Intent.CATEGORY_LAUNCHER,
                Intent.CATEGORY_INFO,
                Intent.CATEGORY_HOME
            )
            for (cat in categories) {
                val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                    addCategory(cat)
                }
                val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.queryIntentActivities(
                        mainIntent,
                        PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DISABLED_COMPONENTS.toLong())
                    )
                } else {
                    @Suppress("DEPRECATION")
                    pm.queryIntentActivities(mainIntent, PackageManager.MATCH_DISABLED_COMPONENTS)
                }
                for (ri in resolveInfos) {
                    val pkg = ri.activityInfo?.packageName
                    if (!pkg.isNullOrBlank()) {
                        result.add(pkg)
                    }
                }
            }

            // Include legitimate Input Methods (keyboards)
            try {
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.inputMethodList?.forEach { imi ->
                    if (!imi.packageName.isNullOrBlank()) {
                        result.add(imi.packageName)
                    }
                }
            } catch (_: Exception) {
            }

            // Include legitimate AppWidget providers (home-screen widget-only apps)
            try {
                val awm = AppWidgetManager.getInstance(context)
                awm?.installedProviders?.forEach { info ->
                    val pkg = info.provider?.packageName
                    if (!pkg.isNullOrBlank()) {
                        result.add(pkg)
                    }
                }
            } catch (_: Exception) {
            }

            // Include legitimate Live Wallpapers
            try {
                val wpIntent = Intent("android.service.wallpaper.WallpaperService")
                val wpServices = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.queryIntentServices(wpIntent, PackageManager.ResolveInfoFlags.of(0L))
                } else {
                    @Suppress("DEPRECATION")
                    pm.queryIntentServices(wpIntent, 0)
                }
                wpServices.forEach { ri ->
                    val pkg = ri.serviceInfo?.packageName
                    if (!pkg.isNullOrBlank()) {
                        result.add(pkg)
                    }
                }
            } catch (_: Exception) {
            }
        } catch (_: Exception) {
        }
        cachedLauncherPkgs = result
        cachedLauncherPkgsTimestamp = now
        return result
    }

    /**
     * Inspects the APK's X.509 signing certificate(s) and manifest debuggable flag 100% offline.
     */
    @Suppress("DEPRECATION")
    fun inspectPackageCertificate(
        pm: PackageManager,
        pkgInfo: PackageInfo
    ): ApkCertificateInspection {
        val appInfo = pkgInfo.applicationInfo
        val isDebuggable = appInfo != null && (appInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val cacheKey = CertCacheKey(pkgInfo.packageName, pkgInfo.lastUpdateTime)
        certCache[cacheKey]?.let { return it }

        var sha256Hex = ""
        var issuer = "Unknown Signer"
        var subject = "Unknown"
        var isTestOrDebugCert = false
        var multiSigners = false

        try {
            val signatures: Array<Signature>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val sigInfo = pkgInfo.signingInfo ?: try {
                    val fetched = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        pm.getPackageInfo(
                            pkgInfo.packageName,
                            PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
                        )
                    } else {
                        pm.getPackageInfo(pkgInfo.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                    }
                    fetched.signingInfo
                } catch (_: Exception) {
                    null
                }
                if (sigInfo != null) {
                    multiSigners = sigInfo.hasMultipleSigners()
                    if (sigInfo.hasMultipleSigners()) {
                        sigInfo.apkContentsSigners
                    } else {
                        sigInfo.signingCertificateHistory
                    }
                } else {
                    null
                }
            } else {
                pkgInfo.signatures ?: try {
                    pm.getPackageInfo(pkgInfo.packageName, PackageManager.GET_SIGNATURES).signatures
                } catch (_: Exception) {
                    null
                }
            }

            val primarySig = signatures?.firstOrNull()
            if (primarySig != null) {
                val rawBytes = primarySig.toByteArray()
                val md = MessageDigest.getInstance("SHA-256")
                val digest = md.digest(rawBytes)
                val rawHex = digest.joinToString("") { "%02X".format(it) }
                sha256Hex = digest.joinToString(":") { "%02X".format(it) }

                if (rawHex in knownPublicTestKeySha256) {
                    isTestOrDebugCert = true
                }

                try {
                    val cf = CertificateFactory.getInstance("X.509")
                    val cert = cf.generateCertificate(ByteArrayInputStream(rawBytes)) as? X509Certificate
                    if (cert != null) {
                        issuer = cert.issuerX500Principal?.name ?: "Unknown"
                        subject = cert.subjectX500Principal?.name ?: "Unknown"
                        val combinedDn = "$issuer | $subject".lowercase()
                        if (combinedDn.contains("cn=android debug") ||
                            (combinedDn.contains("o=android,c=us") && combinedDn.contains("android@android.com")) ||
                            combinedDn.contains("ou=android debug") ||
                            combinedDn.contains("testkey")
                        ) {
                            isTestOrDebugCert = true
                        }
                    }
                } catch (_: Exception) {
                }
            }
        } catch (_: Exception) {
        }

        val result = ApkCertificateInspection(
            sha256Fingerprint = sha256Hex.ifBlank { "Unavailable" },
            issuerSummary = formatShortDistinguishedName(issuer),
            subjectSummary = formatShortDistinguishedName(subject),
            isDebugOrTestCertificate = isTestOrDebugCert,
            isDebuggableManifest = isDebuggable,
            hasMultipleSigners = multiSigners
        )
        certCache[cacheKey] = result
        return result
    }

    private fun formatShortDistinguishedName(dn: String): String {
        if (dn.isBlank() || dn == "Unknown" || dn == "Unavailable") return dn
        val parts = dn.split(",").map { it.trim() }
        val cn = parts.firstOrNull { it.startsWith("CN=", ignoreCase = true) }?.substringAfter("=")
        val o = parts.firstOrNull { it.startsWith("O=", ignoreCase = true) }?.substringAfter("=")
        return when {
            !cn.isNullOrBlank() && !o.isNullOrBlank() && !cn.equals(o, ignoreCase = true) -> "$cn ($o)"
            !cn.isNullOrBlank() -> cn
            !o.isNullOrBlank() -> o
            else -> dn.take(40)
        }
    }

    /**
     * Evaluates a package for offline Stalkerware, Stealth/Ghost App, Impersonation,
     * Certificate/Debug-Key anomalies, and Banking-Trojan / Dropper permission combos.
     */
    fun inspectAppThreatHeuristics(
        context: Context,
        pkgInfo: PackageInfo,
        appName: String,
        isSystem: Boolean,
        isBrandOem: Boolean,
        isSideloaded: Boolean,
        grantedPermissions: List<PlainPermissionInfo>,
        hasAccessibility: Boolean,
        hasNotifListener: Boolean,
        hasOverlay: Boolean,
        isDeviceAdmin: Boolean,
        hasInternet: Boolean,
        launcherPackages: Set<String>
    ): AppThreatIntelReport {
        val pm = context.packageManager
        val pkgName = pkgInfo.packageName
        val pkgLower = pkgName.lowercase()
        val labelTrimmed = appName.trim()
        val labelLower = labelTrimmed.lowercase()

        val certInfo = inspectPackageCertificate(pm, pkgInfo)
        val signals = mutableListOf<ThreatHeuristicSignal>()

        // Check if app has a visible launcher icon or is a legitimate keyboard / widget / wallpaper / OEM service
        val isLegitHeadlessPrefix = legitimateHeadlessPackagePrefixes.any { pkgLower.startsWith(it) }
        val hasDirectLaunchIntent = try {
            pm.getLaunchIntentForPackage(pkgName) != null ||
                pm.getLeanbackLaunchIntentForPackage(pkgName) != null
        } catch (_: Exception) {
            false
        }
        val hasLauncher = pkgName in launcherPackages || hasDirectLaunchIntent || isSystem || isBrandOem || isLegitHeadlessPrefix

        // Check for truly blank / invisible char labels (zero-width space, Braille blank, Hangul filler, empty)
        val strippedVisibleChars = labelTrimmed.filter { ch ->
            !ch.isWhitespace() &&
                ch != '\u200B' &&
                ch != '\u200C' &&
                ch != '\u200D' &&
                ch != '\uFEFF' &&
                ch != '\u2800' &&
                ch != '\u3164'
        }
        val isBlankLabel = !isSystem && !isBrandOem && strippedVisibleChars.isEmpty()

        val grantedPermNames = grantedPermissions.map { it.permissionName }.toSet()
        val reqPerms = pkgInfo.requestedPermissions?.toSet() ?: emptySet()
        val runsAtBoot = Manifest.permission.RECEIVE_BOOT_COMPLETED in reqPerms
        val canInstallPackages = Manifest.permission.REQUEST_INSTALL_PACKAGES in grantedPermNames
        val canDeletePackages = "android.permission.REQUEST_DELETE_PACKAGES" in reqPerms
        val hasSmsOrCallAccess = grantedPermissions.any {
            it.permissionName == Manifest.permission.READ_SMS ||
                it.permissionName == Manifest.permission.RECEIVE_SMS ||
                it.permissionName == Manifest.permission.SEND_SMS ||
                it.permissionName == Manifest.permission.READ_CALL_LOG
        }
        val hasHighRiskSpySensors = grantedPermissions.any {
            it.permissionName == Manifest.permission.RECORD_AUDIO ||
                it.permissionName == Manifest.permission.CAMERA ||
                it.permissionName == Manifest.permission.ACCESS_BACKGROUND_LOCATION ||
                it.permissionName == Manifest.permission.ACCESS_FINE_LOCATION ||
                it.category == PermissionCategory.CONTACTS_SMS
        }

        // 1. Stealth / Iconless "Ghost App" Detection (only flags sideloaded headless apps holding real surveillance / admin hooks)
        if (isSideloaded && !hasLauncher && (hasHighRiskSpySensors || hasAccessibility || hasNotifListener || isDeviceAdmin || hasOverlay)) {
            signals.add(
                ThreatHeuristicSignal(
                    id = "ghost_hidden_launcher",
                    title = "Hidden Icon (Ghost App)",
                    shortChip = "Ghost App",
                    description = "Sideloaded app with no launcher/widget entry point while holding active surveillance or admin permissions.",
                    category = ThreatSignalCategory.STEALTH_STALKERWARE,
                    weight = 28,
                    isCritical = true
                )
            )
        }

        if (isBlankLabel) {
            signals.add(
                ThreatHeuristicSignal(
                    id = "invisible_app_name",
                    title = "Blank / Invisible App Name",
                    shortChip = "Blank Name",
                    description = "Uses blank or invisible Unicode characters for its app name to hide in settings.",
                    category = ThreatSignalCategory.STEALTH_STALKERWARE,
                    weight = 26,
                    isCritical = true
                )
            )
        }

        // 2. System / Brand Impersonation Detection
        var impersonationReason: String? = null
        if (!isSystem && !isBrandOem) {
            if (labelLower in deceptiveSystemLabels) {
                impersonationReason = "Non-system app masquerading as OS utility \"$labelTrimmed\""
            } else {
                for ((brandTitle, expectedPrefix) in protectedBrandClaims) {
                    val claimsBrandName = labelLower == brandTitle.lowercase()
                    val matchesOfficialPkg = pkgLower == expectedPrefix || pkgLower.startsWith(expectedPrefix)
                    if (claimsBrandName && !matchesOfficialPkg) {
                        impersonationReason = "Impersonates \"$brandTitle\" with unofficial package ($pkgName)"
                        break
                    }
                    if (matchesOfficialPkg && isSideloaded && (certInfo.isDebugOrTestCertificate || certInfo.isDebuggableManifest)) {
                        impersonationReason = "Modded/repackaged \"$brandTitle\" signed with a debug/test key"
                        break
                    }
                }
            }
        }

        if (impersonationReason != null) {
            signals.add(
                ThreatHeuristicSignal(
                    id = "system_brand_impersonation",
                    title = "System / Brand Impersonation",
                    shortChip = "Spoofed App",
                    description = impersonationReason,
                    category = ThreatSignalCategory.IMPERSONATION,
                    weight = 30,
                    isCritical = true
                )
            )
        }

        // 3. Stalkerware Behavioral Combo Detection (restricted to sideloaded or hidden-icon apps with active spy hooks)
        val hasSpyMonitorHook = hasAccessibility || hasNotifListener
        if (!isSystem && !isBrandOem && (isSideloaded || !hasLauncher) && hasSpyMonitorHook && hasInternet && (runsAtBoot || hasHighRiskSpySensors)) {
            val hookName = if (hasAccessibility && hasNotifListener) {
                "Screen Reader + Notification Listener"
            } else if (hasAccessibility) {
                "Screen Reader (Accessibility)"
            } else {
                "Notification Listener"
            }
            signals.add(
                ThreatHeuristicSignal(
                    id = "stalkerware_combo",
                    title = "Stalkerware Surveillance Combo",
                    shortChip = "Spy Combo",
                    description = "Sideloaded/hidden app combines $hookName with Internet & background persistence.",
                    category = ThreatSignalCategory.STEALTH_STALKERWARE,
                    weight = 32,
                    isCritical = true
                )
            )
        }

        // Check known commercial stalkerware component names
        val matchedSpySignature = knownSpywareComponentSignatures.firstOrNull { sig ->
            pkgLower.contains(sig.trim().lowercase())
        }
        if (matchedSpySignature != null && !isSystem) {
            signals.add(
                ThreatHeuristicSignal(
                    id = "known_stalkerware_signature",
                    title = "Known Spyware Package Signature",
                    shortChip = "Spyware SDK",
                    description = "Matches known monitoring/stalkerware signature ($matchedSpySignature).",
                    category = ThreatSignalCategory.STEALTH_STALKERWARE,
                    weight = 40,
                    isCritical = true
                )
            )
        }

        // 4. Banking Trojan / Overlay + 2FA Interception Combo (only for sideloaded apps)
        if (!isSystem && !isBrandOem && isSideloaded && hasOverlay && (hasAccessibility || hasNotifListener || hasSmsOrCallAccess) && hasInternet) {
            signals.add(
                ThreatHeuristicSignal(
                    id = "banking_trojan_overlay_combo",
                    title = "Overlay + OTP/Screen Interception",
                    shortChip = "Trojan Overlay",
                    description = "Sideloaded app can draw fake windows over apps while reading screen text, SMS, or 2FA notifications.",
                    category = ThreatSignalCategory.TROJAN_DROPPER,
                    weight = 26,
                    isCritical = true
                )
            )
        }

        // 5. Silent APK Dropper / Auto-Installer Combo (only when REQUEST_INSTALL_PACKAGES is actually granted via AppOps and app is sideloaded or has Accessibility)
        if (!isSystem && !isBrandOem && canInstallPackages && (hasAccessibility || isSideloaded)) {
            val detail = if (hasAccessibility) {
                "Can install outside APKs and use Accessibility to auto-confirm installation prompts."
            } else {
                "Sideloaded app granted permission to stage and install additional APK packages."
            }
            signals.add(
                ThreatHeuristicSignal(
                    id = "apk_dropper_installer",
                    title = "APK Dropper / Installer Risk",
                    shortChip = "APK Dropper",
                    description = detail,
                    category = ThreatSignalCategory.TROJAN_DROPPER,
                    weight = if (hasAccessibility) 28 else 16,
                    isCritical = hasAccessibility
                )
            )
        }

        // 6. Locker / Ransomware Pre-Condition Combo (Device Admin + Overlay / All-Files)
        if (!isSystem && !isBrandOem && isDeviceAdmin && (hasOverlay || Manifest.permission.MANAGE_EXTERNAL_STORAGE in grantedPermNames)) {
            signals.add(
                ThreatHeuristicSignal(
                    id = "locker_admin_combo",
                    title = "Screen-Locker / Admin Persistence",
                    shortChip = "Admin Locker",
                    description = "Combines Device Admin lock privileges with screen overlays or full storage access.",
                    category = ThreatSignalCategory.TROJAN_DROPPER,
                    weight = 28,
                    isCritical = true
                )
            )
        }

        // 7. APK Signing Certificate & Debuggable Anomaly Checks
        if (!isSystem && !isBrandOem) {
            if (certInfo.isDebugOrTestCertificate && (grantedPermissions.isNotEmpty() || isSideloaded)) {
                signals.add(
                    ThreatHeuristicSignal(
                        id = "untrusted_debug_test_cert",
                        title = "Signed with Debug / Public Test Key",
                        shortChip = "Test Key",
                        description = "APK is signed with a non-production debug or public test certificate (${certInfo.issuerSummary}).",
                        category = ThreatSignalCategory.CERT_ANOMALY,
                        weight = 20,
                        isCritical = isSideloaded && grantedPermissions.isNotEmpty()
                    )
                )
            }
            if (certInfo.isDebuggableManifest && (grantedPermissions.isNotEmpty() || isSideloaded)) {
                signals.add(
                    ThreatHeuristicSignal(
                        id = "manifest_debuggable_flag",
                        title = "Debuggable APK Build",
                        shortChip = "Debug Build",
                        description = "Manifest has android:debuggable=true, exposing internal app data and memory hooks.",
                        category = ThreatSignalCategory.CERT_ANOMALY,
                        weight = 14,
                        isCritical = false
                    )
                )
            }
        }

        val totalScore = signals.sumOf { it.weight }.coerceIn(0, 100)

        return AppThreatIntelReport(
            packageName = pkgName,
            appName = appName,
            isSystem = isSystem,
            isBrandOem = isBrandOem,
            isSideloaded = isSideloaded,
            hasLauncherIcon = hasLauncher,
            isBlankOrInvisibleLabel = isBlankLabel,
            isImpersonatingSystemOrBrand = impersonationReason != null,
            impersonationReason = impersonationReason,
            certInspection = certInfo,
            threatSignals = signals,
            threatScore = totalScore
        )
    }
}
