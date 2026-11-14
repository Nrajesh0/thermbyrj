package com.example.service

import android.Manifest
import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.provider.Settings

enum class PermissionCategory(
    val label: String,
    val plainSummary: String
) {
    ALL("All", "Apps with sensitive access"),
    CAMERA_MIC("Cam & Mic", "Can take photos or listen to audio"),
    LOCATION("Location", "Can track your physical location"),
    CONTACTS_SMS("Texts & Contacts", "Can read SMS, calls, or contacts"),
    STORAGE_MEDIA("Files & Media", "Can view your photos or files"),
    SPECIAL_ACCESS("Special", "Overlays, screen readers & notifications")
}

enum class PermissionRiskLevel(val weight: Int, val label: String) {
    CRITICAL(22, "Critical"),
    HIGH(14, "High"),
    MEDIUM(7, "Medium"),
    LOW(3, "Low")
}

data class PlainPermissionInfo(
    val permissionName: String,
    val permissionGroup: String,
    val shortTag: String,
    val friendlyTitle: String,
    val simpleExplanation: String,
    val whyItMatters: String,
    val riskLevel: PermissionRiskLevel,
    val category: PermissionCategory
)

data class AppPrivacyReport(
    val appName: String,
    val packageName: String,
    val icon: Drawable,
    val isSystem: Boolean,
    val grantedPermissions: List<PlainPermissionInfo>,
    val riskScore: Int,
    val riskBadge: String,
    val lastTimeUsedMs: Long,
    val isSideloaded: Boolean,
    val installerName: String,
    val hasInternetAccess: Boolean,
    val canUploadSensitiveData: Boolean,
    val hasOverlayPermission: Boolean,
    val hasAccessibilityAccess: Boolean,
    val hasNotificationListener: Boolean,
    val isDeviceAdmin: Boolean
)

data class DevicePrivacySummary(
    val privacyScore: Int,
    val letterGrade: String,
    val statusHeadline: String,
    val statusExplanation: String,
    val totalScannedApps: Int,
    val highRiskAppCount: Int,
    val moderateRiskAppCount: Int,
    val safeAppCount: Int,
    val unusedRiskyAppCount: Int,
    val dataExfiltrationRiskCount: Int,
    val categoryCounts: Map<PermissionCategory, Int>,
    val topActionTips: List<String>,
    val appReports: List<AppPrivacyReport>
)

data class AppTelemetryReport(
    val appName: String,
    val packageName: String,
    val icon: Drawable,
    val isSystem: Boolean,
    val detectedAdNetworks: List<String>,
    val detectedAnalyticsSdks: List<String>,
    val telemetrySignals: List<String>,
    val usesAdId: Boolean,
    val runsAtBoot: Boolean,
    val usesWakeLock: Boolean,
    val uploadedBytes: Long,
    val downloadedBytes: Long,
    val telemetryScore: Int, // 0 - 100 (higher = worse collector)
    val severityBadge: String // "HEAVY", "MODERATE", "LIGHT", "CLEAN"
)

data class DeviceTelemetrySummary(
    val totalAppsScanned: Int,
    val heavyCollectorsCount: Int,
    val adIdAppsCount: Int,
    val totalTrackersDetected: Int,
    val bootTrackersCount: Int,
    val appReports: List<AppTelemetryReport>
)

enum class PrivacyChangeType(val label: String) {
    PERM_REVOKED("Revoked"),
    PERM_ADDED("Granted"),
    NEW_APP("New App"),
    APP_REMOVED("Removed")
}

data class PrivacyChangeEvent(
    val id: Long,
    val timestamp: Long,
    val packageName: String,
    val appName: String,
    val changeType: PrivacyChangeType,
    val permissionTitle: String,
    val detail: String
)

data class PermissionAccessEvent(
    val id: String,
    val packageName: String,
    val appName: String,
    val icon: Drawable,
    val permissionTitle: String,
    val shortTag: String,
    val category: PermissionCategory,
    val riskLevel: PermissionRiskLevel,
    val timestamp: Long,
    val accessContext: String, // e.g. "Active Now", "AppOps Verified", "Foreground Session", "Background Service"
    val permissionInfo: PlainPermissionInfo
)

object PrivacyAnalyzer {

    private val permissionCatalog: Map<String, PlainPermissionInfo> = listOf(
        PlainPermissionInfo(
            permissionName = "SPECIAL_ACCESSIBILITY_SERVICE",
            permissionGroup = "SPECIAL_ACCESSIBILITY",
            shortTag = "Screen Reader",
            friendlyTitle = "Read All Screen Text",
            simpleExplanation = "Can read everything on your screen and tap buttons.",
            whyItMatters = "Keep off unless it is a trusted accessibility tool.",
            riskLevel = PermissionRiskLevel.CRITICAL,
            category = PermissionCategory.SPECIAL_ACCESS
        ),
        PlainPermissionInfo(
            permissionName = "SPECIAL_NOTIFICATION_LISTENER",
            permissionGroup = "SPECIAL_NOTIFICATION",
            shortTag = "Notifications",
            friendlyTitle = "Read All Notifications",
            simpleExplanation = "Can read incoming texts, chats, and alerts silently.",
            whyItMatters = "Keep only for smartwatches or trusted automation apps.",
            riskLevel = PermissionRiskLevel.CRITICAL,
            category = PermissionCategory.SPECIAL_ACCESS
        ),
        PlainPermissionInfo(
            permissionName = "SPECIAL_DEVICE_ADMIN",
            permissionGroup = "SPECIAL_ADMIN",
            shortTag = "Device Admin",
            friendlyTitle = "Device Admin Control",
            simpleExplanation = "Can lock your phone and block normal uninstallation.",
            whyItMatters = "Keep only for work security or Find My Device apps.",
            riskLevel = PermissionRiskLevel.CRITICAL,
            category = PermissionCategory.SPECIAL_ACCESS
        ),
        PlainPermissionInfo(
            permissionName = Manifest.permission.SYSTEM_ALERT_WINDOW,
            permissionGroup = "SPECIAL_OVERLAY",
            shortTag = "Overlay",
            friendlyTitle = "Draw Over Other Apps",
            simpleExplanation = "Can float windows on top of other apps you use.",
            whyItMatters = "Revoke if the app doesn't need floating bubbles.",
            riskLevel = PermissionRiskLevel.HIGH,
            category = PermissionCategory.SPECIAL_ACCESS
        ),
        PlainPermissionInfo(
            permissionName = Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            permissionGroup = Manifest.permission_group.LOCATION,
            shortTag = "24/7 GPS",
            friendlyTitle = "Always-On Location",
            simpleExplanation = "Tracks where you go even when the app is closed.",
            whyItMatters = "Change to 'Only while using the app'.",
            riskLevel = PermissionRiskLevel.CRITICAL,
            category = PermissionCategory.LOCATION
        ),
        PlainPermissionInfo(
            permissionName = Manifest.permission.ACCESS_FINE_LOCATION,
            permissionGroup = Manifest.permission_group.LOCATION,
            shortTag = "Exact GPS",
            friendlyTitle = "Precise Street Location",
            simpleExplanation = "Pinpoints your exact GPS coordinates.",
            whyItMatters = "Needed for maps & rides; revoke for games/tools.",
            riskLevel = PermissionRiskLevel.HIGH,
            category = PermissionCategory.LOCATION
        ),
        PlainPermissionInfo(
            permissionName = Manifest.permission.ACCESS_COARSE_LOCATION,
            permissionGroup = Manifest.permission_group.LOCATION,
            shortTag = "City GPS",
            friendlyTitle = "Approximate Location",
            simpleExplanation = "Sees your general city or neighborhood.",
            whyItMatters = "Good for weather; unnecessary for offline tools.",
            riskLevel = PermissionRiskLevel.MEDIUM,
            category = PermissionCategory.LOCATION
        ),
        PlainPermissionInfo(
            permissionName = Manifest.permission.RECORD_AUDIO,
            permissionGroup = Manifest.permission_group.MICROPHONE,
            shortTag = "Mic",
            friendlyTitle = "Microphone Access",
            simpleExplanation = "Can listen to and record audio from your mic.",
            whyItMatters = "Keep only for calls, voice notes, or voice search.",
            riskLevel = PermissionRiskLevel.HIGH,
            category = PermissionCategory.CAMERA_MIC
        ),
        PlainPermissionInfo(
            permissionName = Manifest.permission.CAMERA,
            permissionGroup = Manifest.permission_group.CAMERA,
            shortTag = "Camera",
            friendlyTitle = "Camera Access",
            simpleExplanation = "Can take photos and record video.",
            whyItMatters = "Revoke if you never take photos or scan QR codes in it.",
            riskLevel = PermissionRiskLevel.HIGH,
            category = PermissionCategory.CAMERA_MIC
        ),
        PlainPermissionInfo(
            permissionName = Manifest.permission.READ_SMS,
            permissionGroup = Manifest.permission_group.SMS,
            shortTag = "Read SMS",
            friendlyTitle = "Read Text Messages",
            simpleExplanation = "Can read all private texts and login codes.",
            whyItMatters = "Only your main SMS app should have this.",
            riskLevel = PermissionRiskLevel.CRITICAL,
            category = PermissionCategory.CONTACTS_SMS
        ),
        PlainPermissionInfo(
            permissionName = Manifest.permission.RECEIVE_SMS,
            permissionGroup = Manifest.permission_group.SMS,
            shortTag = "Intercept SMS",
            friendlyTitle = "Intercept New Texts",
            simpleExplanation = "Reads incoming texts the instant they arrive.",
            whyItMatters = "Revoke for non-messaging apps.",
            riskLevel = PermissionRiskLevel.CRITICAL,
            category = PermissionCategory.CONTACTS_SMS
        ),
        PlainPermissionInfo(
            permissionName = Manifest.permission.SEND_SMS,
            permissionGroup = Manifest.permission_group.SMS,
            shortTag = "Send SMS",
            friendlyTitle = "Send Text Messages",
            simpleExplanation = "Can send SMS texts from your number automatically.",
            whyItMatters = "Keep only for your default messaging app.",
            riskLevel = PermissionRiskLevel.CRITICAL,
            category = PermissionCategory.CONTACTS_SMS
        ),
        PlainPermissionInfo(
            permissionName = Manifest.permission.READ_CALL_LOG,
            permissionGroup = Manifest.permission_group.CALL_LOG,
            shortTag = "Call Log",
            friendlyTitle = "Read Call History",
            simpleExplanation = "Sees everyone you called and when.",
            whyItMatters = "Only dialer or spam-blocker apps need this.",
            riskLevel = PermissionRiskLevel.HIGH,
            category = PermissionCategory.CONTACTS_SMS
        ),
        PlainPermissionInfo(
            permissionName = Manifest.permission.READ_CONTACTS,
            permissionGroup = Manifest.permission_group.CONTACTS,
            shortTag = "Contacts",
            friendlyTitle = "Read Address Book",
            simpleExplanation = "Sees all names, phone numbers, and emails.",
            whyItMatters = "Revoke if the app doesn't need to find friends.",
            riskLevel = PermissionRiskLevel.MEDIUM,
            category = PermissionCategory.CONTACTS_SMS
        ),
        PlainPermissionInfo(
            permissionName = Manifest.permission.READ_PHONE_STATE,
            permissionGroup = Manifest.permission_group.PHONE,
            shortTag = "Phone ID",
            friendlyTitle = "Phone & Network Info",
            simpleExplanation = "Sees your carrier, call status, and device info.",
            whyItMatters = "Often used by ad trackers.",
            riskLevel = PermissionRiskLevel.LOW,
            category = PermissionCategory.CONTACTS_SMS
        ),
        PlainPermissionInfo(
            permissionName = Manifest.permission.MANAGE_EXTERNAL_STORAGE,
            permissionGroup = "SPECIAL_ALL_FILES",
            shortTag = "All Files",
            friendlyTitle = "All Files Access",
            simpleExplanation = "Can read, change, or delete any file on your phone.",
            whyItMatters = "Grant only to trusted file managers or backup apps.",
            riskLevel = PermissionRiskLevel.CRITICAL,
            category = PermissionCategory.STORAGE_MEDIA
        ),
        PlainPermissionInfo(
            permissionName = Manifest.permission.READ_MEDIA_IMAGES,
            permissionGroup = Manifest.permission_group.READ_MEDIA_VISUAL,
            shortTag = "Photos",
            friendlyTitle = "View All Photos",
            simpleExplanation = "Can browse your entire photo gallery.",
            whyItMatters = "Revoke if the app rarely needs your photos.",
            riskLevel = PermissionRiskLevel.MEDIUM,
            category = PermissionCategory.STORAGE_MEDIA
        ),
        PlainPermissionInfo(
            permissionName = Manifest.permission.READ_MEDIA_VIDEO,
            permissionGroup = Manifest.permission_group.READ_MEDIA_VISUAL,
            shortTag = "Videos",
            friendlyTitle = "View All Videos",
            simpleExplanation = "Can browse all videos saved on your phone.",
            whyItMatters = "Revoke if the app doesn't play or edit videos.",
            riskLevel = PermissionRiskLevel.MEDIUM,
            category = PermissionCategory.STORAGE_MEDIA
        ),
        PlainPermissionInfo(
            permissionName = Manifest.permission.READ_EXTERNAL_STORAGE,
            permissionGroup = Manifest.permission_group.STORAGE,
            shortTag = "Read Files",
            friendlyTitle = "Read Shared Storage",
            simpleExplanation = "Can read media and documents in shared folders.",
            whyItMatters = "Revoke if the app doesn't open local files.",
            riskLevel = PermissionRiskLevel.MEDIUM,
            category = PermissionCategory.STORAGE_MEDIA
        ),
        PlainPermissionInfo(
            permissionName = Manifest.permission.WRITE_EXTERNAL_STORAGE,
            permissionGroup = Manifest.permission_group.STORAGE,
            shortTag = "Write Files",
            friendlyTitle = "Modify Shared Storage",
            simpleExplanation = "Can save or delete files in shared folders.",
            whyItMatters = "Revoke if the app doesn't save downloads.",
            riskLevel = PermissionRiskLevel.MEDIUM,
            category = PermissionCategory.STORAGE_MEDIA
        ),
        PlainPermissionInfo(
            permissionName = Manifest.permission.REQUEST_INSTALL_PACKAGES,
            permissionGroup = "SPECIAL_INSTALL",
            shortTag = "Install APKs",
            friendlyTitle = "Install Unknown Apps",
            simpleExplanation = "Can prompt to install outside APK app files.",
            whyItMatters = "Turn off for browsers after installing an app.",
            riskLevel = PermissionRiskLevel.HIGH,
            category = PermissionCategory.SPECIAL_ACCESS
        ),
        PlainPermissionInfo(
            permissionName = Manifest.permission.PACKAGE_USAGE_STATS,
            permissionGroup = "SPECIAL_USAGE",
            shortTag = "App Usage",
            friendlyTitle = "Track App Usage",
            simpleExplanation = "Sees which other apps you open and for how long.",
            whyItMatters = "Only screen-time or system tools need this.",
            riskLevel = PermissionRiskLevel.MEDIUM,
            category = PermissionCategory.SPECIAL_ACCESS
        )
    ).associateBy { it.permissionName }

    fun getPlainDictionary(): List<PlainPermissionInfo> = permissionCatalog.values.toList()

    /**
     * Jumps directly to the specific permission setting for [packageName] when supported by the OS,
     * falling back gracefully to the app's Permissions / App Info page.
     */
    fun openSpecificPermissionSettings(
        context: Context,
        packageName: String?,
        perm: PlainPermissionInfo
    ) {
        val pkgUri = packageName?.let { Uri.parse("package:$it") }

        // 1. Handle Special Access permissions that have dedicated Android Settings screens
        val specialIntent: Intent? = when (perm.permissionName) {
            Manifest.permission.SYSTEM_ALERT_WINDOW ->
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkgUri)
            Manifest.permission.MANAGE_EXTERNAL_STORAGE ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, pkgUri)
                } else null
            Manifest.permission.REQUEST_INSTALL_PACKAGES ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, pkgUri)
                } else null
            Manifest.permission.PACKAGE_USAGE_STATS ->
                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            "SPECIAL_ACCESSIBILITY_SERVICE" ->
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            "SPECIAL_NOTIFICATION_LISTENER" ->
                Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
            "SPECIAL_DEVICE_ADMIN" ->
                Intent(Settings.ACTION_SECURITY_SETTINGS)
            else -> null
        }

        if (specialIntent != null && tryStartActivity(context, specialIntent)) {
            return
        }

        if (packageName != null) {
            // 2. Try direct single-permission management screen (PermissionController)
            val directSinglePerm = Intent("android.intent.action.MANAGE_APP_PERMISSION").apply {
                putExtra(Intent.EXTRA_PACKAGE_NAME, packageName)
                putExtra("android.intent.extra.PERMISSION_NAME", perm.permissionName)
                putExtra("android.intent.extra.PERMISSION_GROUP_NAME", perm.permissionGroup)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (tryStartActivity(context, directSinglePerm)) return

            // 3. Try jumping directly to the App's Permissions list screen
            val appPermsScreen = Intent("android.intent.action.MANAGE_APP_PERMISSIONS").apply {
                putExtra(Intent.EXTRA_PACKAGE_NAME, packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (tryStartActivity(context, appPermsScreen)) return

            // 4. Standard fallback: App Details page
            val appDetails = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null)
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (tryStartActivity(context, appDetails)) return
        } else {
            // No specific app: try opening the system-wide Permission Group screen
            val groupScreen = Intent("android.intent.action.MANAGE_PERMISSION_APPS").apply {
                putExtra("android.intent.extra.PERMISSION_NAME", perm.permissionName)
                putExtra("android.intent.extra.PERMISSION_GROUP_NAME", perm.permissionGroup)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (tryStartActivity(context, groupScreen)) return

            tryStartActivity(context, Intent(Settings.ACTION_PRIVACY_SETTINGS))
        }
    }

    private fun tryStartActivity(context: Context, intent: Intent): Boolean {
        return try {
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    @Volatile
    private var cachedPrivacyUser: DevicePrivacySummary? = null
    @Volatile
    private var cachedPrivacySystem: DevicePrivacySummary? = null
    @Volatile
    private var cachedPrivacyUserTimeMs: Long = 0L
    @Volatile
    private var cachedPrivacySystemTimeMs: Long = 0L

    @Volatile
    private var cachedTelemetryUser: DeviceTelemetrySummary? = null
    @Volatile
    private var cachedTelemetrySystem: DeviceTelemetrySummary? = null
    @Volatile
    private var cachedTelemetryUserTimeMs: Long = 0L
    @Volatile
    private var cachedTelemetrySystemTimeMs: Long = 0L

    @Volatile
    private var cachedTimelineUser: List<PermissionAccessEvent>? = null
    @Volatile
    private var cachedTimelineSystem: List<PermissionAccessEvent>? = null
    @Volatile
    private var cachedTimelineTimeMs: Long = 0L

    @Volatile
    private var cachedChangeEvents: List<PrivacyChangeEvent>? = null

    fun peekCachedPrivacySummary(includeSystemApps: Boolean = false): DevicePrivacySummary? =
        if (includeSystemApps) cachedPrivacySystem else cachedPrivacyUser

    fun peekCachedTelemetry(includeSystemApps: Boolean = false): DeviceTelemetrySummary? =
        if (includeSystemApps) cachedTelemetrySystem else cachedTelemetryUser

    fun peekCachedTimeline(includeSystemApps: Boolean = false): List<PermissionAccessEvent>? =
        if (includeSystemApps) cachedTimelineSystem else cachedTimelineUser

    fun peekCachedChanges(): List<PrivacyChangeEvent>? = cachedChangeEvents

    fun analyzeDevicePrivacy(
        context: Context,
        includeSystemApps: Boolean = false,
        forceRefresh: Boolean = false
    ): DevicePrivacySummary {
        val nowCheck = System.currentTimeMillis()
        if (!forceRefresh) {
            val cached = if (includeSystemApps) cachedPrivacySystem else cachedPrivacyUser
            val cachedTs = if (includeSystemApps) cachedPrivacySystemTimeMs else cachedPrivacyUserTimeMs
            if (cached != null && (nowCheck - cachedTs) < 25_000L) {
                return cached
            }
        }
        val pm = context.packageManager
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

        val enabledAccessibilitySet = getEnabledAccessibilityPackages(context)
        val enabledNotifListeners = getEnabledNotificationListeners(context)
        val activeAdminSet = try {
            dpm.activeAdmins?.map { it.packageName }?.toSet() ?: emptySet()
        } catch (_: Exception) {
            emptySet()
        }

        val usageMap = getRecentUsageMap(context)
        val packages = try {
            pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
        } catch (_: Exception) {
            emptyList()
        }

        val trustedStoreInstallers = setOf(
            "com.android.vending",
            "com.google.android.feedback",
            "com.sec.android.app.samsungapps",
            "com.samsung.android.app.omcagent",
            "com.xiaomi.market",
            "com.xiaomi.discover",
            "com.miui.packageinstaller",
            "com.miui.securitycenter",
            "com.miui.cloudbackup",
            "com.miui.backup",
            "com.android.updater",
            "com.heytap.market",
            "com.oppo.market",
            "com.oneplus.backuprestore",
            "com.vivo.appstore",
            "com.bbk.appstore",
            "com.huawei.appmarket",
            "com.amazon.venezia"
        )

        val reports = mutableListOf<AppPrivacyReport>()
        val now = System.currentTimeMillis()
        val sevenDaysMs = 7L * 24 * 60 * 60 * 1000L

        for (pkgInfo in packages) {
            val appInfo = pkgInfo.applicationInfo ?: continue
            if (pkgInfo.packageName == context.packageName) continue

            val isBrandOem = isBrandOrOemPackage(pkgInfo.packageName)
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            if (isSystem && !includeSystemApps) continue

            val grantedList = mutableListOf<PlainPermissionInfo>()

            val hasAccessibility = pkgInfo.packageName in enabledAccessibilitySet
            if (hasAccessibility) {
                permissionCatalog["SPECIAL_ACCESSIBILITY_SERVICE"]?.let { grantedList.add(it) }
            }

            val hasNotifListener = pkgInfo.packageName in enabledNotifListeners
            if (hasNotifListener) {
                permissionCatalog["SPECIAL_NOTIFICATION_LISTENER"]?.let { grantedList.add(it) }
            }

            val isAdmin = pkgInfo.packageName in activeAdminSet
            if (isAdmin) {
                permissionCatalog["SPECIAL_DEVICE_ADMIN"]?.let { grantedList.add(it) }
            }

            val hasOverlay = checkAppOpAllowed(
                appOps = appOps,
                op = AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW,
                uid = appInfo.uid,
                packageName = pkgInfo.packageName,
                fallbackRequested = hasRequestedPermission(pkgInfo, Manifest.permission.SYSTEM_ALERT_WINDOW)
            )
            if (hasOverlay) {
                permissionCatalog[Manifest.permission.SYSTEM_ALERT_WINDOW]?.let { grantedList.add(it) }
            }

            var hasInternet = false
            val reqPerms = pkgInfo.requestedPermissions
            val reqFlags = pkgInfo.requestedPermissionsFlags
            if (reqPerms != null && reqFlags != null) {
                val count = minOf(reqPerms.size, reqFlags.size)
                for (i in 0 until count) {
                    val permName = reqPerms[i]
                    val isGranted = (reqFlags[i] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
                    if (permName == Manifest.permission.INTERNET && isGranted) {
                        hasInternet = true
                    }
                    if (permName == Manifest.permission.SYSTEM_ALERT_WINDOW) continue
                    if (isGranted) {
                        permissionCatalog[permName]?.let { info ->
                            if (grantedList.none { it.permissionName == info.permissionName }) {
                                grantedList.add(info)
                            }
                        }
                    }
                }
            }

            val installer = getInstallerPackage(pm, pkgInfo.packageName)
            val isSideloaded = !isSystem && !isBrandOem && (installer == null || installer !in trustedStoreInstallers)

            val lastUsed = usageMap[pkgInfo.packageName] ?: 0L
            val isUnusedRecently = lastUsed in 1L until (now - sevenDaysMs)

            val canUpload = hasInternet && grantedList.any {
                it.riskLevel == PermissionRiskLevel.CRITICAL || it.riskLevel == PermissionRiskLevel.HIGH
            }

            var rawRisk = grantedList.sumOf { it.riskLevel.weight }
            if (canUpload) rawRisk += 6
            if (isSideloaded && grantedList.isNotEmpty()) rawRisk += 10
            if (isUnusedRecently && rawRisk >= 14) rawRisk += 6

            val cappedRisk = rawRisk.coerceIn(0, 100)
            val badge = when {
                cappedRisk >= 35 -> "HIGH RISK"
                cappedRisk >= 14 -> "MODERATE"
                grantedList.isNotEmpty() -> "LOW RISK"
                else -> "SAFE"
            }

            val appName = try {
                pm.getApplicationLabel(appInfo).toString()
            } catch (_: Exception) {
                pkgInfo.packageName
            }
            val icon = try {
                pm.getApplicationIcon(appInfo)
            } catch (_: Exception) {
                context.applicationInfo.loadIcon(pm)
            }

            reports.add(
                AppPrivacyReport(
                    appName = appName,
                    packageName = pkgInfo.packageName,
                    icon = icon,
                    isSystem = isSystem || isBrandOem,
                    grantedPermissions = grantedList.sortedByDescending { it.riskLevel.weight },
                    riskScore = cappedRisk,
                    riskBadge = badge,
                    lastTimeUsedMs = lastUsed,
                    isSideloaded = isSideloaded,
                    installerName = formatInstallerName(installer, isSystem, isBrandOem),
                    hasInternetAccess = hasInternet,
                    canUploadSensitiveData = canUpload,
                    hasOverlayPermission = hasOverlay,
                    hasAccessibilityAccess = hasAccessibility,
                    hasNotificationListener = hasNotifListener,
                    isDeviceAdmin = isAdmin
                )
            )
        }

        val sortedReports = reports.sortedWith(
            compareByDescending<AppPrivacyReport> { it.riskScore }
                .thenByDescending { it.grantedPermissions.size }
                .thenBy { it.appName.lowercase() }
        )

        val highRiskCount = sortedReports.count { it.riskScore >= 35 }
        val moderateCount = sortedReports.count { it.riskScore in 14..34 }
        val safeCount = sortedReports.count { it.riskScore < 14 }
        val unusedRiskyCount = sortedReports.count {
            it.riskScore >= 14 && it.lastTimeUsedMs > 0L && (now - it.lastTimeUsedMs) > sevenDaysMs
        }
        val exfilCount = sortedReports.count { it.canUploadSensitiveData }

        val penaltyFromHigh = highRiskCount * 9
        val penaltyFromModerate = moderateCount * 3
        val penaltyFromUnused = unusedRiskyCount * 4
        val totalPenalty = (penaltyFromHigh + penaltyFromModerate + penaltyFromUnused).coerceAtMost(88)
        val privacyScore = (100 - totalPenalty).coerceIn(12, 100)

        val (grade, headline, explanation) = when {
            privacyScore >= 90 -> Triple("A+", "EXCELLENT PRIVACY", "Minimal sensor exposure across your apps.")
            privacyScore >= 80 -> Triple("A", "WELL PROTECTED", "Only a few apps hold sensitive access.")
            privacyScore >= 65 -> Triple("B", "MODERATE EXPOSURE", "$highRiskCount app(s) have high-risk sensor access.")
            privacyScore >= 50 -> Triple("C", "NEEDS ATTENTION", "Several apps can access private sensors + internet.")
            else -> Triple("D", "HIGH EXPOSURE", "Revoke Camera, Mic, or GPS from untrusted apps.")
        }

        val categoryCounts = PermissionCategory.entries.associateWith { cat ->
            when (cat) {
                PermissionCategory.ALL -> sortedReports.count { it.grantedPermissions.isNotEmpty() }
                else -> sortedReports.count { app -> app.grantedPermissions.any { it.category == cat } }
            }
        }

        val tips = buildShortActionTips(sortedReports.filter { !it.isSystem }.ifEmpty { sortedReports }, unusedRiskyCount)

        val result = DevicePrivacySummary(
            privacyScore = privacyScore,
            letterGrade = grade,
            statusHeadline = headline,
            statusExplanation = explanation,
            totalScannedApps = sortedReports.size,
            highRiskAppCount = highRiskCount,
            moderateRiskAppCount = moderateCount,
            safeAppCount = safeCount,
            unusedRiskyAppCount = unusedRiskyCount,
            dataExfiltrationRiskCount = exfilCount,
            categoryCounts = categoryCounts,
            topActionTips = tips,
            appReports = sortedReports
        )
        if (includeSystemApps) {
            cachedPrivacySystem = result
            cachedPrivacySystemTimeMs = System.currentTimeMillis()
        } else {
            cachedPrivacyUser = result
            cachedPrivacyUserTimeMs = System.currentTimeMillis()
        }
        return result
    }

    /**
     * Scans installed apps for embedded Advertising SDKs, Analytics/Telemetry services,
     * Advertising ID usage, Boot auto-start receivers, and network data usage.
     */
    fun analyzeAppTelemetryAndTrackers(
        context: Context,
        includeSystemApps: Boolean = false,
        forceRefresh: Boolean = false
    ): DeviceTelemetrySummary {
        val nowCheck = System.currentTimeMillis()
        if (!forceRefresh) {
            val cached = if (includeSystemApps) cachedTelemetrySystem else cachedTelemetryUser
            val cachedTs = if (includeSystemApps) cachedTelemetrySystemTimeMs else cachedTelemetryUserTimeMs
            if (cached != null && (nowCheck - cachedTs) < 30_000L) {
                return cached
            }
        }
        val pm = context.packageManager
        val flags = PackageManager.GET_PERMISSIONS or
            PackageManager.GET_SERVICES or
            PackageManager.GET_RECEIVERS or
            PackageManager.GET_PROVIDERS or
            PackageManager.GET_ACTIVITIES

        val packages = try {
            pm.getInstalledPackages(flags)
        } catch (_: Exception) {
            try {
                pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
            } catch (_: Exception) {
                emptyList()
            }
        }

        val adNetworkSignatures = listOf(
            "Google AdMob" to listOf("com.google.android.gms.ads", "com.google.ads"),
            "Meta Audience Ads" to listOf("com.facebook.ads", "com.facebook.appevents"),
            "AppLovin Ads" to listOf("com.applovin"),
            "Unity Ads" to listOf("com.unity3d.ads", "com.unity3d.services.ads"),
            "IronSource" to listOf("com.ironsource"),
            "Vungle / Liftoff" to listOf("com.vungle"),
            "InMobi" to listOf("com.inmobi"),
            "Pangle / TikTok Ads" to listOf("com.bytedance.sdk.openadsdk"),
            "Chartboost" to listOf("com.chartboost"),
            "Mintegral" to listOf("com.mbridge.msdk")
        )

        val analyticsSignatures = listOf(
            "Firebase Analytics" to listOf("com.google.firebase.analytics", "com.google.android.gms.measurement", "AppMeasurementService"),
            "Crashlytics" to listOf("com.google.firebase.crashlytics", "com.crashlytics"),
            "AppsFlyer Attribution" to listOf("com.appsflyer"),
            "Adjust Tracker" to listOf("com.adjust.sdk"),
            "Branch DeepLinks" to listOf("io.branch.referral"),
            "Mixpanel" to listOf("com.mixpanel"),
            "Amplitude" to listOf("com.amplitude"),
            "Segment" to listOf("com.segment.analytics"),
            "OneSignal Push" to listOf("com.onesignal"),
            "Braze / Appboy" to listOf("com.braze", "com.appboy"),
            "Flurry Analytics" to listOf("com.flurry"),
            "Sentry Telemetry" to listOf("io.sentry")
        )

        val reports = mutableListOf<AppTelemetryReport>()

        for (pkgInfo in packages) {
            val appInfo = pkgInfo.applicationInfo ?: continue
            if (pkgInfo.packageName == context.packageName) continue

            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            if (isSystem && !includeSystemApps) continue

            // Collect all component class names in this package
            val componentNames = buildList {
                pkgInfo.services?.forEach { add(it.name) }
                pkgInfo.receivers?.forEach { add(it.name) }
                pkgInfo.providers?.forEach { add(it.name) }
                pkgInfo.activities?.forEach { add(it.name) }
            }

            val detectedAds = adNetworkSignatures.mapNotNull { (label, prefixes) ->
                if (componentNames.any { cls -> prefixes.any { cls.contains(it, ignoreCase = true) } }) label else null
            }

            val detectedAnalytics = analyticsSignatures.mapNotNull { (label, prefixes) ->
                if (componentNames.any { cls -> prefixes.any { cls.contains(it, ignoreCase = true) } }) label else null
            }

            val reqPerms = pkgInfo.requestedPermissions?.toSet() ?: emptySet()
            val hasInternet = Manifest.permission.INTERNET in reqPerms
            val usesAdId = "com.google.android.gms.permission.AD_ID" in reqPerms || detectedAds.isNotEmpty()
            val runsAtBoot = Manifest.permission.RECEIVE_BOOT_COMPLETED in reqPerms
            val usesWakeLock = Manifest.permission.WAKE_LOCK in reqPerms
            val readsPhoneId = Manifest.permission.READ_PHONE_STATE in reqPerms
            val scansWifiOrBt = Manifest.permission.ACCESS_WIFI_STATE in reqPerms ||
                Manifest.permission.BLUETOOTH_CONNECT in reqPerms ||
                Manifest.permission.NEARBY_WIFI_DEVICES in reqPerms

            val signals = buildList {
                if (usesAdId) add("Reads Advertising ID")
                if (runsAtBoot && hasInternet) add("Starts on Phone Boot")
                if (readsPhoneId && hasInternet) add("Reads Device/Carrier ID")
                if (scansWifiOrBt && hasInternet) add("Scans Wi-Fi/BT Networks")
                if (usesWakeLock && hasInternet) add("Background WakeLock")
            }

            var score = 0
            if (hasInternet) {
                score += detectedAds.size * 16
                score += detectedAnalytics.size * 11
                if (usesAdId) score += 18
                if (runsAtBoot) score += 10
                if (readsPhoneId) score += 8
                if (scansWifiOrBt) score += 6
                if (usesWakeLock) score += 5
            }
            val finalScore = score.coerceIn(0, 100)

            val badge = when {
                finalScore >= 45 -> "HEAVY"
                finalScore >= 22 -> "MODERATE"
                finalScore > 0 -> "LIGHT"
                else -> "CLEAN"
            }

            val appName = try {
                pm.getApplicationLabel(appInfo).toString()
            } catch (_: Exception) {
                pkgInfo.packageName
            }
            val icon = try {
                pm.getApplicationIcon(appInfo)
            } catch (_: Exception) {
                context.applicationInfo.loadIcon(pm)
            }

            reports.add(
                AppTelemetryReport(
                    appName = appName,
                    packageName = pkgInfo.packageName,
                    icon = icon,
                    isSystem = isSystem,
                    detectedAdNetworks = detectedAds,
                    detectedAnalyticsSdks = detectedAnalytics,
                    telemetrySignals = signals,
                    usesAdId = usesAdId,
                    runsAtBoot = runsAtBoot,
                    usesWakeLock = usesWakeLock,
                    uploadedBytes = 0L,
                    downloadedBytes = 0L,
                    telemetryScore = finalScore,
                    severityBadge = badge
                )
            )
        }

        val sorted = reports.sortedWith(
            compareByDescending<AppTelemetryReport> { it.telemetryScore }
                .thenByDescending { it.detectedAdNetworks.size + it.detectedAnalyticsSdks.size }
                .thenBy { it.appName.lowercase() }
        )

        val result = DeviceTelemetrySummary(
            totalAppsScanned = sorted.size,
            heavyCollectorsCount = sorted.count { it.telemetryScore >= 45 },
            adIdAppsCount = sorted.count { it.usesAdId },
            totalTrackersDetected = sorted.sumOf { it.detectedAdNetworks.size + it.detectedAnalyticsSdks.size },
            bootTrackersCount = sorted.count { it.runsAtBoot && it.telemetryScore >= 22 },
            appReports = sorted
        )
        if (includeSystemApps) {
            cachedTelemetrySystem = result
            cachedTelemetrySystemTimeMs = System.currentTimeMillis()
        } else {
            cachedTelemetryUser = result
            cachedTelemetryUserTimeMs = System.currentTimeMillis()
        }
        return result
    }

    private fun buildShortActionTips(reports: List<AppPrivacyReport>, unusedRiskyCount: Int): List<String> {
        val tips = mutableListOf<String>()
        val bgLocCount = reports.count { app ->
            app.grantedPermissions.any { it.permissionName == Manifest.permission.ACCESS_BACKGROUND_LOCATION }
        }
        if (bgLocCount > 0) {
            tips.add("$bgLocCount app(s) track location 24/7 — switch to 'While using'.")
        }
        val uploadCount = reports.count { it.canUploadSensitiveData }
        if (uploadCount > 0) {
            tips.add("$uploadCount app(s) have both sensitive sensors + Internet access.")
        }
        if (unusedRiskyCount > 0) {
            tips.add("$unusedRiskyCount unused app(s) still hold risky permissions.")
        }
        if (tips.isEmpty()) {
            tips.add("Permissions look clean! Review new apps after installing.")
        }
        return tips.take(3)
    }

    fun getEnabledAccessibilityPackages(context: Context): Set<String> {
        return try {
            val settingValue = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return emptySet()
            settingValue.split(":")
                .mapNotNull { component -> component.substringBefore("/", "").takeIf { it.isNotBlank() } }
                .toSet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    fun getEnabledNotificationListeners(context: Context): Set<String> {
        return try {
            val settingValue = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            ) ?: return emptySet()
            settingValue.split(":")
                .mapNotNull { component -> component.substringBefore("/", "").takeIf { it.isNotBlank() } }
                .toSet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    fun isBrandOrOemPackage(packageName: String): Boolean {
        val pkg = packageName.lowercase()
        val brandPrefixes = listOf(
            "com.miui.",
            "com.xiaomi.",
            "com.mi.",
            "org.mipay.",
            "com.duokan.",
            "com.bsp.",
            "cn.wps.moffice_eng.xiaomi",
            "com.sohu.inputmethod.sogou.xiaomi",
            "com.baidu.input_mi",
            "com.facemoji.lite.xiaomi",
            "com.poco.",
            "com.blackshark.",
            "com.google.",
            "com.android.",
            "android.",
            "com.qualcomm.",
            "com.qti.",
            "com.mediatek.",
            "org.codeaurora.",
            "com.samsung.",
            "com.sec.",
            "com.oplus.",
            "com.coloros.",
            "com.heytap.",
            "com.oppo.",
            "com.oneplus.",
            "com.realme.",
            "com.nearme.",
            "com.vivo.",
            "com.bbk.",
            "com.iqoo.",
            "com.nothing.",
            "com.motorola.",
            "com.asus.",
            "com.sony.",
            "com.sonymobile.",
            "com.huawei.",
            "com.hihonor.",
            "com.transsion.",
            "com.infinix.",
            "com.tecno."
        )
        if (pkg == "android" || brandPrefixes.any { pkg.startsWith(it) }) return true
        val devBrand = Build.BRAND?.lowercase()?.takeIf { it.length >= 3 }
        val devMfr = Build.MANUFACTURER?.lowercase()?.takeIf { it.length >= 3 }
        if (devBrand != null && (pkg.startsWith("com.$devBrand.") || pkg.contains(".$devBrand."))) return true
        if (devMfr != null && (pkg.startsWith("com.$devMfr.") || pkg.contains(".$devMfr."))) return true
        return false
    }

    fun getEnabledInputMethods(context: Context): List<String> {
        return try {
            val raw = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_INPUT_METHODS
            ) ?: return emptyList()
            val trustedPrefixes = listOf(
                "com.google.android.inputmethod",
                "com.android.inputmethod",
                "com.samsung.android.honeyboard",
                "com.sec.android.inputmethod",
                "com.miui.",
                "com.xiaomi.",
                "com.sohu.inputmethod.sogou.xiaomi",
                "com.baidu.input_mi",
                "com.facemoji.lite.xiaomi",
                "com.oplus.",
                "com.coloros.",
                "com.vivo."
            )
            raw.split(":")
                .mapNotNull { it.substringBefore("/", "").takeIf { pkg -> pkg.isNotBlank() } }
                .distinct()
                .filter { pkg -> trustedPrefixes.none { pkg.startsWith(it) } && !isBrandOrOemPackage(pkg) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun checkAppOpAllowed(
        appOps: AppOpsManager,
        op: String,
        uid: Int,
        packageName: String,
        fallbackRequested: Boolean
    ): Boolean {
        return try {
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(op, uid, packageName)
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(op, uid, packageName)
            }
            when (mode) {
                AppOpsManager.MODE_ALLOWED -> true
                AppOpsManager.MODE_DEFAULT -> fallbackRequested
                else -> false
            }
        } catch (_: Exception) {
            fallbackRequested
        }
    }

    private fun hasRequestedPermission(pkgInfo: PackageInfo, perm: String): Boolean {
        val reqPerms = pkgInfo.requestedPermissions ?: return false
        return perm in reqPerms
    }

    private fun getInstallerPackage(pm: PackageManager, packageName: String): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                pm.getInstallSourceInfo(packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                pm.getInstallerPackageName(packageName)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun formatInstallerName(installerPkg: String?, isSystem: Boolean, isBrandOem: Boolean = false): String {
        if (isSystem) return "System App"
        if (isBrandOem) return "Brand App"
        return when (installerPkg) {
            "com.android.vending", "com.google.android.feedback" -> "Play Store"
            "com.sec.android.app.samsungapps", "com.samsung.android.app.omcagent" -> "Galaxy Store"
            "com.xiaomi.market", "com.xiaomi.discover", "com.miui.packageinstaller", "com.android.updater" -> "Xiaomi Store"
            "com.heytap.market", "com.oppo.market" -> "App Market"
            "com.vivo.appstore", "com.bbk.appstore" -> "V-Appstore"
            "com.huawei.appmarket" -> "AppGallery"
            "com.google.android.packageinstaller", "com.android.packageinstaller" -> "APK Installer"
            null -> "Sideloaded APK"
            else -> installerPkg
        }
    }

    fun hasUsageStatsPermission(context: Context): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) {
            false
        }
    }

    fun getPermissionAccessTimeline(
        context: Context,
        includeSystemApps: Boolean = false
    ): List<PermissionAccessEvent> {
        val summary = analyzeDevicePrivacy(context, includeSystemApps = includeSystemApps)
        val reports = summary.appReports.filter { it.grantedPermissions.isNotEmpty() }
        val appOpsTimes = queryAppOpsLastAccessMap(context)
        val sessionTimes = queryRecentUsageSessionsMap(context)
        val pm = context.packageManager

        val timeline = mutableListOf<PermissionAccessEvent>()

        for (app in reports) {
            val pkgOps = appOpsTimes[app.packageName] ?: emptyMap()
            val (lastFgTime, lastSvcTime) = sessionTimes[app.packageName] ?: (app.lastTimeUsedMs to 0L)
            val fallbackInstallTime = try {
                pm.getPackageInfo(app.packageName, 0).lastUpdateTime
            } catch (_: Exception) {
                0L
            }

            // Show up to the most important sensitive permissions per app with their exact access timestamps
            for (perm in app.grantedPermissions) {
                val appOpTime = pkgOps[perm.permissionName] ?: 0L
                val bestTimestamp: Long
                val contextLabel: String

                when {
                    appOpTime > 0L -> {
                        bestTimestamp = appOpTime
                        contextLabel = "System AppOps"
                    }
                    lastSvcTime > 0L && lastSvcTime >= lastFgTime -> {
                        bestTimestamp = lastSvcTime
                        contextLabel = "Background service"
                    }
                    lastFgTime > 0L -> {
                        bestTimestamp = lastFgTime
                        contextLabel = "Foreground session"
                    }
                    app.lastTimeUsedMs > 0L -> {
                        bestTimestamp = app.lastTimeUsedMs
                        contextLabel = "Recent session"
                    }
                    else -> {
                        bestTimestamp = fallbackInstallTime
                        contextLabel = "Granted on install"
                    }
                }

                if (bestTimestamp > 0L) {
                    timeline.add(
                        PermissionAccessEvent(
                            id = "${app.packageName}_${perm.permissionName}",
                            packageName = app.packageName,
                            appName = app.appName,
                            icon = app.icon,
                            permissionTitle = perm.friendlyTitle,
                            shortTag = perm.shortTag,
                            category = perm.category,
                            riskLevel = perm.riskLevel,
                            timestamp = bestTimestamp,
                            accessContext = contextLabel,
                            permissionInfo = perm
                        )
                    )
                }
            }
        }

        val sortedTimeline = timeline.sortedWith(
            compareByDescending<PermissionAccessEvent> { it.timestamp }
                .thenByDescending { it.riskLevel.weight }
        )
        if (includeSystemApps) {
            cachedTimelineSystem = sortedTimeline
        } else {
            cachedTimelineUser = sortedTimeline
        }
        cachedTimelineTimeMs = System.currentTimeMillis()
        return sortedTimeline
    }

    private fun queryRecentUsageSessionsMap(context: Context): Map<String, Pair<Long, Long>> {
        val result = mutableMapOf<String, Pair<Long, Long>>()
        try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val end = System.currentTimeMillis()
            val start = end - (7L * 24 * 60 * 60 * 1000L)

            // 1. Base usage stats (lastTimeUsed + lastTimeForegroundServiceUsed)
            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_WEEKLY, start, end)
            stats?.forEach { st ->
                val fg = st.lastTimeUsed
                val svc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    st.lastTimeForegroundServiceUsed
                } else 0L
                val existing = result[st.packageName]
                result[st.packageName] = maxOf(existing?.first ?: 0L, fg) to maxOf(existing?.second ?: 0L, svc)
            }

            // 2. Exact event stream from last 24 hours
            val events = usm.queryEvents(end - (24L * 60 * 60 * 1000L), end)
            val event = android.app.usage.UsageEvents.Event()
            while (events != null && events.hasNextEvent()) {
                events.getNextEvent(event)
                val pkg = event.packageName ?: continue
                val ts = event.timeStamp
                val cur = result[pkg] ?: (0L to 0L)
                when (event.eventType) {
                    android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED -> {
                        if (ts > cur.first) result[pkg] = ts to cur.second
                    }
                    android.app.usage.UsageEvents.Event.FOREGROUND_SERVICE_START -> {
                        if (ts > cur.second) result[pkg] = cur.first to ts
                    }
                }
            }
        } catch (_: Exception) {
        }
        return result
    }

    private fun queryAppOpsLastAccessMap(context: Context): Map<String, Map<String, Long>> {
        val map = mutableMapOf<String, MutableMap<String, Long>>()
        try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val getPackagesForOps = appOps.javaClass.getMethod("getPackagesForOps", IntArray::class.java)
            val pkgOpsList = getPackagesForOps.invoke(appOps, null) as? List<*> ?: return emptyMap()

            for (pkgOps in pkgOpsList) {
                if (pkgOps == null) continue
                val pkgName = pkgOps.javaClass.getMethod("getPackageName").invoke(pkgOps) as? String ?: continue
                val ops = pkgOps.javaClass.getMethod("getOps").invoke(pkgOps) as? List<*> ?: continue
                val permTimes = map.getOrPut(pkgName) { mutableMapOf() }

                for (opEntry in ops) {
                    if (opEntry == null) continue
                    val opStr = try {
                        opEntry.javaClass.getMethod("getOpStr").invoke(opEntry) as? String
                    } catch (_: Exception) {
                        null
                    } ?: continue

                    val lastTime = try {
                        val m = opEntry.javaClass.getMethod("getLastAccessTime", Int::class.javaPrimitiveType)
                        (m.invoke(opEntry, 0x1f) as? Long) ?: 0L
                    } catch (_: Exception) {
                        try {
                            val m2 = opEntry.javaClass.getMethod("getTime")
                            (m2.invoke(opEntry) as? Long) ?: 0L
                        } catch (_: Exception) {
                            0L
                        }
                    }

                    if (lastTime > 0L) {
                        val mappedPerm = when (opStr) {
                            AppOpsManager.OPSTR_CAMERA -> Manifest.permission.CAMERA
                            AppOpsManager.OPSTR_RECORD_AUDIO -> Manifest.permission.RECORD_AUDIO
                            AppOpsManager.OPSTR_FINE_LOCATION -> Manifest.permission.ACCESS_FINE_LOCATION
                            AppOpsManager.OPSTR_COARSE_LOCATION -> Manifest.permission.ACCESS_COARSE_LOCATION
                            AppOpsManager.OPSTR_READ_CONTACTS -> Manifest.permission.READ_CONTACTS
                            AppOpsManager.OPSTR_READ_SMS -> Manifest.permission.READ_SMS
                            AppOpsManager.OPSTR_READ_CALL_LOG -> Manifest.permission.READ_CALL_LOG
                            AppOpsManager.OPSTR_READ_PHONE_STATE -> Manifest.permission.READ_PHONE_STATE
                            AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW -> Manifest.permission.SYSTEM_ALERT_WINDOW
                            AppOpsManager.OPSTR_READ_EXTERNAL_STORAGE -> Manifest.permission.READ_EXTERNAL_STORAGE
                            else -> null
                        }
                        if (mappedPerm != null) {
                            permTimes[mappedPerm] = maxOf(permTimes[mappedPerm] ?: 0L, lastTime)
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }
        return map
    }

    private fun getRecentUsageMap(context: Context): Map<String, Long> {
        return try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val end = System.currentTimeMillis()
            val start = end - (30L * 24 * 60 * 60 * 1000L)
            usm.queryUsageStats(UsageStatsManager.INTERVAL_MONTHLY, start, end)
                ?.associate { it.packageName to it.lastTimeUsed }
                ?: emptyMap()
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private const val PREFS_PRIVACY_DIFF = "privacy_baseline_prefs"
    private const val KEY_HAS_BASELINE = "has_baseline_v1"
    private const val KEY_SNAPSHOT = "baseline_snapshot_v1"
    private const val KEY_CHANGE_LOG = "change_log_v1"

    fun openAppPermissionScreen(context: Context, packageName: String) {
        val appPermsIntent = Intent("android.intent.action.MANAGE_APP_PERMISSIONS").apply {
            putExtra(Intent.EXTRA_PACKAGE_NAME, packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(appPermsIntent)
        } catch (_: Exception) {
            try {
                context.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", packageName, null)
                    ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                )
            } catch (_: Exception) {}
        }
    }

    @Synchronized
    fun getPrivacyChanges(context: Context, currentReports: List<AppPrivacyReport>): List<PrivacyChangeEvent> {
        val prefs = context.getSharedPreferences(PREFS_PRIVACY_DIFF, Context.MODE_PRIVATE)
        val pm = context.packageManager
        val now = System.currentTimeMillis()

        // Always compare against non-system + current reports so toggling "System" doesn't create false diffs
        val userReports = currentReports.filter { !it.isSystem }
        val hasBaseline = prefs.getBoolean(KEY_HAS_BASELINE, false)
        val existingLog = decodeChangeLog(prefs.getString(KEY_CHANGE_LOG, "") ?: "").toMutableList()

        if (!hasBaseline) {
            // Seed initial recent activity from recently installed/updated user apps so CHANGES tab is immediately useful
            val seeded = mutableListOf<PrivacyChangeEvent>()
            var idSeq = now - 1000L
            val recentApps = userReports
                .filter { it.grantedPermissions.isNotEmpty() }
                .map { report ->
                    val installTime = try {
                        pm.getPackageInfo(report.packageName, 0).lastUpdateTime.takeIf { it > 0L } ?: now
                    } catch (_: Exception) {
                        now
                    }
                    report to installTime
                }
                .sortedByDescending { it.second }
                .take(6)

            for ((app, ts) in recentApps) {
                seeded.add(
                    PrivacyChangeEvent(
                        id = idSeq++,
                        timestamp = ts,
                        packageName = app.packageName,
                        appName = app.appName,
                        changeType = PrivacyChangeType.NEW_APP,
                        permissionTitle = "${app.grantedPermissions.size} permissions active",
                        detail = app.grantedPermissions.take(3).joinToString(", ") { it.shortTag }
                    )
                )
                app.grantedPermissions.take(2).forEach { perm ->
                    seeded.add(
                        PrivacyChangeEvent(
                            id = idSeq++,
                            timestamp = ts,
                            packageName = app.packageName,
                            appName = app.appName,
                            changeType = PrivacyChangeType.PERM_ADDED,
                            permissionTitle = perm.friendlyTitle,
                            detail = perm.shortTag
                        )
                    )
                }
            }

            val snapshotStr = encodeSnapshot(userReports)
            val combined = (seeded.sortedByDescending { it.timestamp } + existingLog).take(100)
            prefs.edit()
                .putBoolean(KEY_HAS_BASELINE, true)
                .putString(KEY_SNAPSHOT, snapshotStr)
                .putString(KEY_CHANGE_LOG, encodeChangeLog(combined))
                .apply()
            return combined
        }

        val oldSnapshot = decodeSnapshot(prefs.getString(KEY_SNAPSHOT, "") ?: "")
        val newEvents = mutableListOf<PrivacyChangeEvent>()
        var nextId = (existingLog.maxOfOrNull { it.id } ?: now).coerceAtLeast(now) + 1L

        val currentMap = userReports.associateBy { it.packageName }

        // Check for newly installed apps or permission additions/revocations
        for (app in userReports) {
            val prevEntry = oldSnapshot[app.packageName]
            val currentPermSet = app.grantedPermissions.map { it.permissionName }.toSet()
            if (prevEntry == null) {
                if (app.grantedPermissions.isNotEmpty()) {
                    newEvents.add(
                        PrivacyChangeEvent(
                            id = nextId++,
                            timestamp = now,
                            packageName = app.packageName,
                            appName = app.appName,
                            changeType = PrivacyChangeType.NEW_APP,
                            permissionTitle = "${app.grantedPermissions.size} permissions granted",
                            detail = app.grantedPermissions.take(3).joinToString(", ") { it.shortTag }
                        )
                    )
                }
            } else {
                val prevPerms = prevEntry.second
                // Newly added permissions
                for (perm in app.grantedPermissions) {
                    if (perm.permissionName !in prevPerms) {
                        newEvents.add(
                            PrivacyChangeEvent(
                                id = nextId++,
                                timestamp = now,
                                packageName = app.packageName,
                                appName = app.appName,
                                changeType = PrivacyChangeType.PERM_ADDED,
                                permissionTitle = perm.friendlyTitle,
                                detail = "Permission granted"
                            )
                        )
                    }
                }
                // Newly revoked permissions
                for (oldPermName in prevPerms) {
                    if (oldPermName !in currentPermSet) {
                        val info = permissionCatalog[oldPermName]
                        val title = info?.friendlyTitle ?: oldPermName.substringAfterLast('.')
                        newEvents.add(
                            PrivacyChangeEvent(
                                id = nextId++,
                                timestamp = now,
                                packageName = app.packageName,
                                appName = app.appName,
                                changeType = PrivacyChangeType.PERM_REVOKED,
                                permissionTitle = title,
                                detail = "Permission revoked"
                            )
                        )
                    }
                }
            }
        }

        // Check for apps that had all permissions revoked or were uninstalled
        for ((pkg, pair) in oldSnapshot) {
            if (pkg !in currentMap) {
                val oldAppName = pair.first
                val oldPerms = pair.second
                val isStillInstalled = try {
                    pm.getPackageInfo(pkg, 0)
                    true
                } catch (_: Exception) {
                    false
                }
                if (!isStillInstalled) {
                    newEvents.add(
                        PrivacyChangeEvent(
                            id = nextId++,
                            timestamp = now,
                            packageName = pkg,
                            appName = oldAppName,
                            changeType = PrivacyChangeType.APP_REMOVED,
                            permissionTitle = "App uninstalled",
                            detail = "${oldPerms.size} permissions removed"
                        )
                    )
                } else {
                    for (oldPermName in oldPerms) {
                        val info = permissionCatalog[oldPermName]
                        val title = info?.friendlyTitle ?: oldPermName.substringAfterLast('.')
                        newEvents.add(
                            PrivacyChangeEvent(
                                id = nextId++,
                                timestamp = now,
                                packageName = pkg,
                                appName = oldAppName,
                                changeType = PrivacyChangeType.PERM_REVOKED,
                                permissionTitle = title,
                                detail = "Permission revoked"
                            )
                        )
                    }
                }
            }
        }

        val updatedLog = if (newEvents.isNotEmpty()) {
            (newEvents + existingLog).take(100)
        } else {
            existingLog
        }

        prefs.edit()
            .putString(KEY_SNAPSHOT, encodeSnapshot(userReports))
            .putString(KEY_CHANGE_LOG, encodeChangeLog(updatedLog))
            .apply()

        cachedChangeEvents = updatedLog
        return updatedLog
    }

    @Synchronized
    fun clearPrivacyChangeLog(context: Context, currentReports: List<AppPrivacyReport>): List<PrivacyChangeEvent> {
        val prefs = context.getSharedPreferences(PREFS_PRIVACY_DIFF, Context.MODE_PRIVATE)
        val userReports = currentReports.filter { !it.isSystem }
        prefs.edit()
            .putBoolean(KEY_HAS_BASELINE, true)
            .putString(KEY_SNAPSHOT, encodeSnapshot(userReports))
            .putString(KEY_CHANGE_LOG, "")
            .apply()
        cachedChangeEvents = emptyList()
        return emptyList()
    }

    private fun encodeSnapshot(reports: List<AppPrivacyReport>): String {
        return reports.joinToString("\n") { app ->
            val cleanName = app.appName.replace("|", " ").replace("\n", " ")
            val perms = app.grantedPermissions.joinToString(",") { it.permissionName }
            "${app.packageName}|$cleanName|$perms"
        }
    }

    private fun decodeSnapshot(raw: String): Map<String, Pair<String, Set<String>>> {
        if (raw.isBlank()) return emptyMap()
        val map = mutableMapOf<String, Pair<String, Set<String>>>()
        raw.lineSequence().forEach { line ->
            val parts = line.split("|")
            if (parts.size >= 3) {
                val pkg = parts[0]
                val name = parts[1]
                val perms = parts[2].split(",").filter { it.isNotBlank() }.toSet()
                map[pkg] = name to perms
            }
        }
        return map
    }

    private fun encodeChangeLog(events: List<PrivacyChangeEvent>): String {
        return events.joinToString("\n") { ev ->
            val safeApp = ev.appName.replace("|", " ").replace("\n", " ")
            val safeTitle = ev.permissionTitle.replace("|", " ").replace("\n", " ")
            val safeDetail = ev.detail.replace("|", " ").replace("\n", " ")
            "${ev.id}|${ev.timestamp}|${ev.packageName}|$safeApp|${ev.changeType.name}|$safeTitle|$safeDetail"
        }
    }

    private fun decodeChangeLog(raw: String): List<PrivacyChangeEvent> {
        if (raw.isBlank()) return emptyList()
        return raw.lineSequence().mapNotNull { line ->
            val p = line.split("|")
            if (p.size >= 7) {
                val id = p[0].toLongOrNull() ?: return@mapNotNull null
                val ts = p[1].toLongOrNull() ?: 0L
                val type = try {
                    PrivacyChangeType.valueOf(p[4])
                } catch (_: Exception) {
                    PrivacyChangeType.PERM_ADDED
                }
                PrivacyChangeEvent(
                    id = id,
                    timestamp = ts,
                    packageName = p[2],
                    appName = p[3],
                    changeType = type,
                    permissionTitle = p[5],
                    detail = p[6]
                )
            } else null
        }.toList()
    }
}
