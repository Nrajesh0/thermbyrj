package com.example.service

import android.Manifest
import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.app.usage.UsageEvents
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
    val isDeviceAdmin: Boolean,
    val firstInstallTimeMs: Long = 0L,
    val lastUpdateTimeMs: Long = 0L,
    val hasLauncherIcon: Boolean = true,
    val isHiddenStealthApp: Boolean = false,
    val isImpersonatingSystemOrBrand: Boolean = false,
    val impersonationReason: String? = null,
    val certSha256: String = "",
    val certSignerSummary: String = "",
    val hasUntrustedOrDebugCert: Boolean = false,
    val isDebuggableApk: Boolean = false,
    val threatSignals: List<ThreatHeuristicSignal> = emptyList(),
    val threatScore: Int = 0
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
    val threatFlaggedAppCount: Int = 0,
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
            Manifest.permission.REQUEST_INSTALL_PACKAGES, "SPECIAL_SIDELOADED_APP" ->
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
            "SPECIAL_SCREEN_CAPTURE", "SPECIAL_SCREEN_MIRROR" ->
                Intent(Settings.ACTION_CAST_SETTINGS)
            "SPECIAL_CUSTOM_KEYBOARD" ->
                Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
            "SPECIAL_USB_DEBUGGING" ->
                Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
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

    private val appIconDrawableCache = java.util.concurrent.ConcurrentHashMap<String, Drawable>()

    fun peekCachedPrivacySummary(includeSystemApps: Boolean = false): DevicePrivacySummary? =
        if (includeSystemApps) cachedPrivacySystem else cachedPrivacyUser

    fun peekCachedTelemetry(includeSystemApps: Boolean = false): DeviceTelemetrySummary? =
        if (includeSystemApps) cachedTelemetrySystem else cachedTelemetryUser

    fun peekCachedTimeline(includeSystemApps: Boolean = false): List<PermissionAccessEvent>? =
        if (includeSystemApps) cachedTimelineSystem else cachedTimelineUser

    fun invalidateTimelineCache() {
        cachedTimelineUser = null
        cachedTimelineSystem = null
        cachedTimelineTimeMs = 0L
    }

    fun invalidateAllCaches() {
        cachedPrivacyUser = null
        cachedPrivacySystem = null
        cachedPrivacyUserTimeMs = 0L
        cachedPrivacySystemTimeMs = 0L
        cachedTelemetryUser = null
        cachedTelemetrySystem = null
        cachedTelemetryUserTimeMs = 0L
        cachedTelemetrySystemTimeMs = 0L
        invalidateTimelineCache()
    }

    private val privacyScanLock = Any()
    private val telemetryScanLock = Any()
    private val timelineLock = Any()

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
            if (cached != null && (nowCheck - cachedTs) < 60_000L) {
                return cached
            }
        }
        synchronized(privacyScanLock) {
            val nowLocked = System.currentTimeMillis()
            if (!forceRefresh) {
                val cached = if (includeSystemApps) cachedPrivacySystem else cachedPrivacyUser
                val cachedTs = if (includeSystemApps) cachedPrivacySystemTimeMs else cachedPrivacyUserTimeMs
                if (cached != null && (nowLocked - cachedTs) < 60_000L) {
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
            } catch (_: Throwable) {
                emptySet()
            }

            val usageMap = getRecentUsageMap(context)
            val launcherPackages = OfflineThreatScanner.getPackagesWithLauncherActivities(context, forceRefresh = forceRefresh)
            // Query ONLY GET_PERMISSIONS in the bulk call to keep the Binder parcel small (<100 KB).
            // Certificates and manifest metadata are fetched lazily per user package only on cache miss.
            val packages = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
                } else {
                    pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
                }
            } catch (_: Throwable) {
                try {
                    pm.getInstalledPackages(0)
                } catch (_: Throwable) {
                    emptyList()
                }
            }

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

            // Special AppOp permissions MUST strictly equal MODE_ALLOWED (MODE_DEFAULT means denied for non-system apps)
            val hasOverlay = isSpecialAppOpAllowed(
                appOps = appOps,
                op = AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW,
                uid = appInfo.uid,
                packageName = pkgInfo.packageName
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
                    val isGrantedFlag = (reqFlags[i] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
                    if (permName == Manifest.permission.INTERNET && isGrantedFlag) {
                        hasInternet = true
                    }
                    // Handle Special AppOps permissions strictly via AppOpsManager.MODE_ALLOWED
                    when (permName) {
                        Manifest.permission.SYSTEM_ALERT_WINDOW -> continue
                        Manifest.permission.REQUEST_INSTALL_PACKAGES -> {
                            if (isSpecialAppOpAllowed(appOps, "android:request_install_packages", appInfo.uid, pkgInfo.packageName)) {
                                permissionCatalog[permName]?.let { info ->
                                    if (grantedList.none { it.permissionName == info.permissionName }) {
                                        grantedList.add(info)
                                    }
                                }
                            }
                            continue
                        }
                        Manifest.permission.MANAGE_EXTERNAL_STORAGE -> {
                            val allowedAllFiles = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                isSpecialAppOpAllowed(appOps, "android:manage_external_storage", appInfo.uid, pkgInfo.packageName)
                            } else {
                                false
                            }
                            if (allowedAllFiles) {
                                permissionCatalog[permName]?.let { info ->
                                    if (grantedList.none { it.permissionName == info.permissionName }) {
                                        grantedList.add(info)
                                    }
                                }
                            }
                            continue
                        }
                        Manifest.permission.PACKAGE_USAGE_STATS -> {
                            if (isSpecialAppOpAllowed(appOps, AppOpsManager.OPSTR_GET_USAGE_STATS, appInfo.uid, pkgInfo.packageName)) {
                                permissionCatalog[permName]?.let { info ->
                                    if (grantedList.none { it.permissionName == info.permissionName }) {
                                        grantedList.add(info)
                                    }
                                }
                            }
                            continue
                        }
                    }

                    // On Android 13+ (API 33+), apps targeting SDK >= 33 use granular media permissions instead of legacy storage
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        appInfo.targetSdkVersion >= Build.VERSION_CODES.TIRAMISU &&
                        (permName == Manifest.permission.READ_EXTERNAL_STORAGE ||
                            permName == Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    ) {
                        continue
                    }

                    if (isGrantedFlag && isRuntimePermissionAppOpAllowed(appOps, permName, appInfo.uid, pkgInfo.packageName)) {
                        permissionCatalog[permName]?.let { info ->
                            if (grantedList.none { it.permissionName == info.permissionName }) {
                                grantedList.add(info)
                            }
                        }
                    }
                }
            }

            val installDetails = getInstallOriginDetails(pm, pkgInfo)
            val installer = installDetails.installerPackage
            val isSideloaded = !isSystem && !isBrandOem && installDetails.isSideloaded

            val lastUsed = usageMap[pkgInfo.packageName] ?: 0L
            val isUnusedRecently = lastUsed in 1L until (now - sevenDaysMs)

            val canUpload = hasInternet && grantedList.any {
                it.riskLevel == PermissionRiskLevel.CRITICAL || it.riskLevel == PermissionRiskLevel.HIGH
            }

            val appName = try {
                pm.getApplicationLabel(appInfo).toString()
            } catch (_: Exception) {
                pkgInfo.packageName
            }
            val icon = appIconDrawableCache[pkgInfo.packageName] ?: try {
                pm.getApplicationIcon(appInfo).also { loaded ->
                    if (appIconDrawableCache.size < 350) {
                        appIconDrawableCache[pkgInfo.packageName] = loaded
                    }
                }
            } catch (_: Exception) {
                context.applicationInfo.loadIcon(pm)
            }

            val threatIntel = OfflineThreatScanner.inspectAppThreatHeuristics(
                context = context,
                pkgInfo = pkgInfo,
                appName = appName,
                isSystem = isSystem,
                isBrandOem = isBrandOem,
                isSideloaded = isSideloaded,
                grantedPermissions = grantedList,
                hasAccessibility = hasAccessibility,
                hasNotifListener = hasNotifListener,
                hasOverlay = hasOverlay,
                isDeviceAdmin = isAdmin,
                hasInternet = hasInternet,
                launcherPackages = launcherPackages
            )

            var rawRisk = grantedList.sumOf { it.riskLevel.weight }
            if (canUpload) rawRisk += 6
            if (isSideloaded && grantedList.isNotEmpty()) rawRisk += 10
            if (isUnusedRecently && rawRisk >= 14) rawRisk += 6
            rawRisk += (threatIntel.threatScore / 2)

            val cappedRisk = rawRisk.coerceIn(0, 100)
            val badge = when {
                cappedRisk >= 35 || threatIntel.threatSignals.any { it.isCritical } -> "HIGH RISK"
                cappedRisk >= 14 || threatIntel.threatSignals.isNotEmpty() -> "MODERATE"
                grantedList.isNotEmpty() -> "LOW RISK"
                else -> "SAFE"
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
                    isDeviceAdmin = isAdmin,
                    firstInstallTimeMs = pkgInfo.firstInstallTime,
                    lastUpdateTimeMs = pkgInfo.lastUpdateTime,
                    hasLauncherIcon = threatIntel.hasLauncherIcon,
                    isHiddenStealthApp = (!threatIntel.hasLauncherIcon && grantedList.isNotEmpty()) || threatIntel.isBlankOrInvisibleLabel,
                    isImpersonatingSystemOrBrand = threatIntel.isImpersonatingSystemOrBrand,
                    impersonationReason = threatIntel.impersonationReason,
                    certSha256 = threatIntel.certInspection.sha256Fingerprint,
                    certSignerSummary = threatIntel.certInspection.issuerSummary,
                    hasUntrustedOrDebugCert = threatIntel.certInspection.isDebugOrTestCertificate,
                    isDebuggableApk = threatIntel.certInspection.isDebuggableManifest,
                    threatSignals = threatIntel.threatSignals,
                    threatScore = threatIntel.threatScore
                )
            )
        }

        val sortedReports = reports.sortedWith(
            compareByDescending<AppPrivacyReport> { it.riskScore }
                .thenByDescending { it.threatScore }
                .thenByDescending { it.grantedPermissions.size }
                .thenBy { it.appName.lowercase() }
        )

        val highRiskCount = sortedReports.count { it.riskScore >= 35 || it.threatSignals.any { s -> s.isCritical } }
        val moderateCount = sortedReports.count { it.riskScore in 14..34 && it.threatSignals.none { s -> s.isCritical } }
        val safeCount = sortedReports.count { it.riskScore < 14 && it.threatSignals.isEmpty() }
        val unusedRiskyCount = sortedReports.count {
            it.riskScore >= 14 && it.lastTimeUsedMs > 0L && (now - it.lastTimeUsedMs) > sevenDaysMs
        }
        val exfilCount = sortedReports.count { it.canUploadSensitiveData }
        val threatFlaggedCount = sortedReports.count { it.threatSignals.isNotEmpty() }

        val penaltyFromHigh = highRiskCount * 9
        val penaltyFromModerate = moderateCount * 3
        val penaltyFromUnused = unusedRiskyCount * 4
        val penaltyFromThreats = threatFlaggedCount * 8
        val totalPenalty = (penaltyFromHigh + penaltyFromModerate + penaltyFromUnused + penaltyFromThreats).coerceAtMost(88)
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
            threatFlaggedAppCount = threatFlaggedCount,
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
        synchronized(telemetryScanLock) {
            val nowLocked = System.currentTimeMillis()
            if (!forceRefresh) {
                val cached = if (includeSystemApps) cachedTelemetrySystem else cachedTelemetryUser
                val cachedTs = if (includeSystemApps) cachedTelemetrySystemTimeMs else cachedTelemetryUserTimeMs
                if (cached != null && (nowLocked - cachedTs) < 30_000L) {
                    return cached
                }
            }
            val pm = context.packageManager
            val detailFlags = PackageManager.GET_PERMISSIONS or
                PackageManager.GET_SERVICES or
                PackageManager.GET_RECEIVERS or
                PackageManager.GET_PROVIDERS or
                PackageManager.GET_ACTIVITIES

            // Fetch lightweight application list first to avoid a 1MB+ Binder TransactionTooLargeException
            val installedApps = try {
                pm.getInstalledApplications(0)
            } catch (_: Throwable) {
                emptyList()
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

            for (appInfo in installedApps) {
                if (appInfo.packageName == context.packageName) continue

                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                    (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                if (isSystem && !includeSystemApps) continue

                val pkgInfo = try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        pm.getPackageInfo(appInfo.packageName, PackageManager.PackageInfoFlags.of(detailFlags.toLong()))
                    } else {
                        pm.getPackageInfo(appInfo.packageName, detailFlags)
                    }
                } catch (_: Throwable) {
                    try {
                        pm.getPackageInfo(appInfo.packageName, PackageManager.GET_PERMISSIONS)
                    } catch (_: Throwable) {
                        continue
                    }
                }

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

    /**
     * Strictly checks special `signature|appop` permissions (Overlay, Install Unknown Apps,
     * All Files Access, Usage Access). For these special permissions, `AppOpsManager.MODE_DEFAULT`
     * means DENIED for third-party apps (the OS only flips them to `MODE_ALLOWED` when the user
     * explicitly turns on the toggle in Special App Access).
     */
    @Suppress("DEPRECATION")
    private fun isSpecialAppOpAllowed(
        appOps: AppOpsManager,
        op: String,
        uid: Int,
        packageName: String
    ): Boolean {
        return try {
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(op, uid, packageName)
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(op, uid, packageName)
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Verifies that a runtime permission with `PROTECTION_FLAG_APPOP` has not been revoked
     * via AppOps (`MODE_IGNORED` or `MODE_ERRORED`).
     */
    @Suppress("DEPRECATION")
    private fun isRuntimePermissionAppOpAllowed(
        appOps: AppOpsManager,
        permName: String,
        uid: Int,
        packageName: String
    ): Boolean {
        return try {
            val op = AppOpsManager.permissionToOp(permName) ?: return true
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(op, uid, packageName)
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(op, uid, packageName)
            }
            mode == AppOpsManager.MODE_ALLOWED ||
                mode == AppOpsManager.MODE_DEFAULT ||
                mode == 4 /* AppOpsManager.MODE_FOREGROUND */
        } catch (_: Exception) {
            true
        }
    }

    private fun hasRequestedPermission(pkgInfo: PackageInfo, perm: String): Boolean {
        val reqPerms = pkgInfo.requestedPermissions ?: return false
        return perm in reqPerms
    }

    private data class InstallOriginDetails(
        val installerPackage: String?,
        val isSideloaded: Boolean
    )

    private val trustedStoreInstallers = setOf(
        "com.android.vending",
        "com.google.android.feedback",
        "com.google.android.gms",
        "com.google.android.apps.restore",
        "com.google.android.apps.work.clouddpc",
        "com.microsoft.windowsintune.companyportal",
        "com.sec.android.app.samsungapps",
        "com.samsung.android.app.omcagent",
        "com.sec.android.easyMover",
        "com.xiaomi.market",
        "com.xiaomi.discover",
        "com.xiaomi.mipicks",
        "com.miui.huanji",
        "com.android.updater",
        "com.heytap.market",
        "com.oppo.market",
        "com.coloros.market",
        "com.oneplus.market",
        "com.realme.market",
        "com.nothing.appstore",
        "com.oneplus.backuprestore",
        "com.coloros.backuprestore",
        "com.heytap.backuprestore",
        "com.vivo.appstore",
        "com.bbk.appstore",
        "com.vivo.easyshare",
        "com.huawei.appmarket",
        "com.amazon.venezia",
        "org.fdroid.fdroid",
        "com.aurora.store"
    )

    private val explicitSideloadInitiatorHints = listOf(
        "chrome",
        "browser",
        "firefox",
        "brave",
        "opera",
        "ucmobile",
        "filemanager",
        "documentsui",
        "whatsapp",
        "telegram",
        "discord",
        "downloads"
    )

    /**
     * Accurately resolves whether an app came from an official app store (Google Play Store,
     * Galaxy Store, Xiaomi GetApps, etc.) vs. being sideloaded from an APK file.
     *
     * Why previous detection had false positives (e.g. Pokémon GO from Play Store):
     * 1. On Xiaomi/MIUI/HyperOS, Oppo/ColorOS, Vivo, and Samsung, when Play Store installs an app
     *    (especially split APKs like Pokémon GO), `initiatingPackageName` is `com.android.vending`,
     *    while the OEM security scanner re-commits the session so `installingPackageName` becomes
     *    `com.miui.packageinstaller` or `com.google.android.packageinstaller`.
     * 2. Apps distributed by Google Play Store carry Play Store stamp/split metadata inside
     *    `ApplicationInfo.metaData` (`com.android.vending.derived.apk.id`, `com.android.vending.splits`,
     *    `com.android.stamp.source`, `com.android.stamp.type`).
     * 3. On Android 13+ (API 33+), `InstallSourceInfo.packageSource` explicitly reports
     *    `PACKAGE_SOURCE_STORE` vs `PACKAGE_SOURCE_LOCAL_FILE` / `PACKAGE_SOURCE_DOWNLOADED_FILE`.
     */
    private fun getInstallOriginDetails(pm: PackageManager, pkgInfo: PackageInfo): InstallOriginDetails {
        val packageName = pkgInfo.packageName
        val appInfo = pkgInfo.applicationInfo
        val isSys = appInfo != null &&
            ((appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0)
        val isBrand = isBrandOrOemPackage(packageName)

        if (isSys || isBrand || packageName == "com.example" || packageName.startsWith("com.aistudio.")) {
            return InstallOriginDetails(installerPackage = "system", isSideloaded = false)
        }

        return try {
            val candidates = mutableListOf<String>()
            var packageSource = 0

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val info = pm.getInstallSourceInfo(packageName)
                info.initiatingPackageName?.takeIf { it.isNotBlank() }?.let { candidates.add(it) }
                info.originatingPackageName?.takeIf { it.isNotBlank() }?.let { candidates.add(it) }
                info.installingPackageName?.takeIf { it.isNotBlank() }?.let { candidates.add(it) }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    packageSource = info.packageSource
                }
            }
            @Suppress("DEPRECATION")
            pm.getInstallerPackageName(packageName)?.takeIf { it.isNotBlank() }?.let { candidates.add(it) }

            // 1. If ANY of initiating, originating, installing, or legacy installer is a trusted store, it is NOT sideloaded!
            val matchedStore = candidates.firstOrNull { it in trustedStoreInstallers }
            if (matchedStore != null) {
                return InstallOriginDetails(installerPackage = matchedStore, isSideloaded = false)
            }

            // 2. Check Android 13+ explicit PACKAGE_SOURCE_STORE
            val isExplicitStoreSource = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                packageSource == android.content.pm.PackageInstaller.PACKAGE_SOURCE_STORE
            if (isExplicitStoreSource) {
                return InstallOriginDetails(
                    installerPackage = candidates.firstOrNull() ?: "com.android.vending",
                    isSideloaded = false
                )
            }

            val isExplicitLocalOrDownloaded = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && (
                packageSource == android.content.pm.PackageInstaller.PACKAGE_SOURCE_LOCAL_FILE ||
                    packageSource == android.content.pm.PackageInstaller.PACKAGE_SOURCE_DOWNLOADED_FILE
                )

            val initiatedByBrowserOrFileMgr = candidates.any { pkg ->
                val lower = pkg.lowercase()
                explicitSideloadInitiatorHints.any { hint -> lower.contains(hint) }
            }

            // 3. Check Google Play Store stamp & split APK metadata in ApplicationInfo.metaData (lazily fetched if needed)
            val meta = appInfo?.metaData ?: try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getApplicationInfo(
                        packageName,
                        PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong())
                    ).metaData
                } else {
                    pm.getApplicationInfo(packageName, PackageManager.GET_META_DATA).metaData
                }
            } catch (_: Throwable) {
                null
            }
            val hasPlayStoreStamp = meta != null && (
                meta.containsKey("com.android.vending.derived.apk.id") ||
                    meta.containsKey("com.android.vending.splits") ||
                    meta.containsKey("com.android.stamp.source") ||
                    meta.containsKey("com.android.stamp.type")
                )
            val hasSplitApks = !appInfo?.splitSourceDirs.isNullOrEmpty()

            if ((hasPlayStoreStamp || hasSplitApks) && !isExplicitLocalOrDownloaded && !initiatedByBrowserOrFileMgr) {
                return InstallOriginDetails(installerPackage = "com.android.vending", isSideloaded = false)
            }

            val primaryInstaller = candidates.firstOrNull()
            val isManualPackageInstaller = primaryInstaller != null && (
                primaryInstaller.contains("packageinstaller", ignoreCase = true) ||
                    primaryInstaller == "com.oplus.appdetail" ||
                    primaryInstaller == "com.google.android.FileExplorer"
                )

            val isSideloaded = isExplicitLocalOrDownloaded ||
                initiatedByBrowserOrFileMgr ||
                isManualPackageInstaller ||
                primaryInstaller == "com.android.shell" ||
                (primaryInstaller == null && !hasPlayStoreStamp)

            InstallOriginDetails(installerPackage = primaryInstaller, isSideloaded = isSideloaded)
        } catch (_: Exception) {
            InstallOriginDetails(installerPackage = null, isSideloaded = false)
        }
    }

    private fun formatInstallerName(installerPkg: String?, isSystem: Boolean, isBrandOem: Boolean = false): String {
        if (isSystem) return "System App"
        if (isBrandOem) return "Brand App"
        return when (installerPkg) {
            "com.android.vending", "com.google.android.feedback", "com.google.android.gms" -> "Play Store"
            "com.google.android.apps.restore",
            "com.sec.android.easyMover",
            "com.miui.huanji",
            "com.oneplus.backuprestore",
            "com.coloros.backuprestore",
            "com.heytap.backuprestore",
            "com.vivo.easyshare" -> "Device Restore"
            "com.google.android.apps.work.clouddpc",
            "com.microsoft.windowsintune.companyportal" -> "Work / MDM"
            "com.sec.android.app.samsungapps", "com.samsung.android.app.omcagent" -> "Galaxy Store"
            "com.xiaomi.market", "com.xiaomi.discover", "com.xiaomi.mipicks", "com.android.updater" -> "Xiaomi Store"
            "com.heytap.market", "com.oppo.market", "com.coloros.market", "com.oneplus.market", "com.realme.market" -> "App Market"
            "com.nothing.appstore" -> "Nothing Store"
            "com.vivo.appstore", "com.bbk.appstore" -> "V-Appstore"
            "com.huawei.appmarket" -> "AppGallery"
            "com.amazon.venezia" -> "Amazon Appstore"
            "org.fdroid.fdroid" -> "F-Droid"
            "com.aurora.store" -> "Aurora Store"
            "com.google.android.packageinstaller",
            "com.android.packageinstaller",
            "com.miui.packageinstaller",
            "com.samsung.android.packageinstaller",
            "com.oplus.appdetail" -> "APK Installer"
            "com.android.shell" -> "ADB / Shell"
            null -> "Sideloaded APK"
            else -> installerPkg
        }
    }

    @Suppress("DEPRECATION")
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

    private val clipboardTimelineInfo = PlainPermissionInfo(
        permissionName = "SPECIAL_CLIPBOARD_READ",
        permissionGroup = "SPECIAL_CLIPBOARD",
        shortTag = "Clipboard",
        friendlyTitle = "Read Clipboard",
        simpleExplanation = "Read text or links copied to your clipboard.",
        whyItMatters = "Background clipboard reads can capture copied passwords or 2FA codes.",
        riskLevel = PermissionRiskLevel.HIGH,
        category = PermissionCategory.SPECIAL_ACCESS
    )

    private val screenCaptureTimelineInfo = PlainPermissionInfo(
        permissionName = "SPECIAL_SCREEN_CAPTURE",
        permissionGroup = "SPECIAL_SCREEN",
        shortTag = "Screen Rec",
        friendlyTitle = "Screen Recording",
        simpleExplanation = "Captured or recorded your live screen display.",
        whyItMatters = "Verify that screen recording was started by you.",
        riskLevel = PermissionRiskLevel.CRITICAL,
        category = PermissionCategory.SPECIAL_ACCESS
    )

    private val screenMirrorTimelineInfo = PlainPermissionInfo(
        permissionName = "SPECIAL_SCREEN_MIRROR",
        permissionGroup = "SPECIAL_SCREEN",
        shortTag = "Mirroring",
        friendlyTitle = "Screen Mirroring / Cast",
        simpleExplanation = "Mirrored or cast your screen to an external or virtual display.",
        whyItMatters = "Ensure you recognize any connected cast or mirroring display.",
        riskLevel = PermissionRiskLevel.CRITICAL,
        category = PermissionCategory.SPECIAL_ACCESS
    )

    private val customKeyboardTimelineInfo = PlainPermissionInfo(
        permissionName = "SPECIAL_CUSTOM_KEYBOARD",
        permissionGroup = "SPECIAL_KEYBOARD",
        shortTag = "Keyboard",
        friendlyTitle = "Custom Third-Party Keyboard",
        simpleExplanation = "Enabled third-party input method can see everything you type.",
        whyItMatters = "Keep only keyboards from developers you fully trust.",
        riskLevel = PermissionRiskLevel.HIGH,
        category = PermissionCategory.SPECIAL_ACCESS
    )

    private val usbDebuggingTimelineInfo = PlainPermissionInfo(
        permissionName = "SPECIAL_USB_DEBUGGING",
        permissionGroup = "SPECIAL_ADB",
        shortTag = "USB Debug",
        friendlyTitle = "USB Debugging (ADB)",
        simpleExplanation = "Allows a connected computer to run commands and inspect apps via ADB.",
        whyItMatters = "Turn off USB Debugging in Developer Options when not actively debugging.",
        riskLevel = PermissionRiskLevel.HIGH,
        category = PermissionCategory.SPECIAL_ACCESS
    )

    private val sideloadTimelineInfo = PlainPermissionInfo(
        permissionName = "SPECIAL_SIDELOADED_APP",
        permissionGroup = "SPECIAL_INSTALL",
        shortTag = "Sideloaded",
        friendlyTitle = "Sideloaded APK App",
        simpleExplanation = "Installed directly from an APK outside the official app store.",
        whyItMatters = "Review permissions granted to sideloaded apps carefully.",
        riskLevel = PermissionRiskLevel.HIGH,
        category = PermissionCategory.SPECIAL_ACCESS
    )

    private val stealthStalkerwareTimelineInfo = PlainPermissionInfo(
        permissionName = "SPECIAL_STEALTH_STALKERWARE",
        permissionGroup = "SPECIAL_SECURITY",
        shortTag = "Stealth Risk",
        friendlyTitle = "Stealth / Impersonation Detection",
        simpleExplanation = "App hides its launcher icon, uses a blank name, or impersonates a system utility.",
        whyItMatters = "Uninstall unverified hidden or impersonating apps immediately.",
        riskLevel = PermissionRiskLevel.CRITICAL,
        category = PermissionCategory.SPECIAL_ACCESS
    )

    private val apkCertTrojanTimelineInfo = PlainPermissionInfo(
        permissionName = "SPECIAL_APK_CERT_TROJAN",
        permissionGroup = "SPECIAL_SECURITY",
        shortTag = "APK / Cert",
        friendlyTitle = "APK Certificate & Trojan Heuristics",
        simpleExplanation = "Signed with a debug/test key or combines overlay, dropper, and interception permissions.",
        whyItMatters = "Verify the developer or uninstall untrusted modded/dropper APKs.",
        riskLevel = PermissionRiskLevel.CRITICAL,
        category = PermissionCategory.SPECIAL_ACCESS
    )

    fun getPermissionAccessTimeline(
        context: Context,
        includeSystemApps: Boolean = false,
        forceRefresh: Boolean = false
    ): List<PermissionAccessEvent> {
        val nowCheck = System.currentTimeMillis()
        if (!forceRefresh) {
            val cached = if (includeSystemApps) cachedTimelineSystem else cachedTimelineUser
            if (cached != null && (nowCheck - cachedTimelineTimeMs) < 10_000L) {
                return cached
            }
        }
        synchronized(timelineLock) {
            val nowLocked = System.currentTimeMillis()
            if (!forceRefresh) {
                val cached = if (includeSystemApps) cachedTimelineSystem else cachedTimelineUser
                if (cached != null && (nowLocked - cachedTimelineTimeMs) < 10_000L) {
                    return cached
                }
            }
            val hasCachedSummary = peekCachedPrivacySummary(includeSystemApps) != null
            val summary = analyzeDevicePrivacy(
                context,
                includeSystemApps = includeSystemApps,
                forceRefresh = forceRefresh && !hasCachedSummary
            )
            val reportsByPkg = summary.appReports.associateBy { it.packageName }
            val appOpsTimes = queryAppOpsLastAccessMap(context)
        val pm = context.packageManager
        val guardMonitor = LiveGuardMonitor.getInstance(context)
        val ignored = guardMonitor.ignoredPackages.value
        val liveCapture = guardMonitor.captureState.value
        val now = System.currentTimeMillis()
        val defaultSysIcon: Drawable = context.applicationInfo.loadIcon(pm)

        val timeline = mutableListOf<PermissionAccessEvent>()

        fun resolvePkgMeta(pkg: String?): Triple<String, String, Drawable> {
            if (pkg.isNullOrBlank() || pkg == "system.sensor") {
                return Triple("system.sensor", "System Hardware", defaultSysIcon)
            }
            val cachedReport = reportsByPkg[pkg]
            if (cachedReport != null) {
                return Triple(pkg, cachedReport.appName, cachedReport.icon)
            }
            val appInfo = try {
                pm.getApplicationInfo(pkg, 0)
            } catch (_: Exception) {
                null
            }
            if (appInfo != null) {
                val label = try {
                    pm.getApplicationLabel(appInfo).toString()
                } catch (_: Exception) {
                    pkg
                }
                val icon = try {
                    pm.getApplicationIcon(appInfo)
                } catch (_: Exception) {
                    defaultSysIcon
                }
                return Triple(pkg, label, icon)
            }
            return Triple(pkg, pkg, defaultSysIcon)
        }

        // 0. Live Ongoing Hardware Sessions ("LIVE NOW") — ensures active calls, mic, camera, or screen recording show immediately while happening
        if (liveCapture.isScreenRecordingOrCasting) {
            val recPkg = liveCapture.suspectedRecorderPackages.firstOrNull { it !in ignored }
            val (rPkg, rName, rIcon) = resolvePkgMeta(recPkg)
            val isMirror = liveCapture.activeCaptureDisplays.any {
                it.contains("Mirror", ignoreCase = true) || it.contains("Cast", ignoreCase = true)
            }
            val pInfo = if (isMirror) screenMirrorTimelineInfo else screenCaptureTimelineInfo
            val displaySummary = liveCapture.activeCaptureDisplays.firstOrNull() ?: pInfo.friendlyTitle
            timeline.add(
                PermissionAccessEvent(
                    id = "live_now_screen",
                    packageName = rPkg,
                    appName = if (rPkg == "system.sensor") "Screen Capture" else rName,
                    icon = rIcon,
                    permissionTitle = displaySummary,
                    shortTag = "LIVE NOW",
                    category = pInfo.category,
                    riskLevel = PermissionRiskLevel.CRITICAL,
                    timestamp = now + 3L,
                    accessContext = "LIVE NOW • Active Capture",
                    permissionInfo = pInfo
                )
            )
        }

        if (liveCapture.isMicActive) {
            val micLabel = liveCapture.activeMicApps.firstOrNull()
            val matchedApp = summary.appReports.firstOrNull {
                it.appName.equals(micLabel, ignoreCase = true) || it.packageName == micLabel
            }
            val rPkg = matchedApp?.packageName ?: "system.sensor"
            if (rPkg !in ignored) {
                val rName = matchedApp?.appName ?: micLabel ?: "Call / Microphone"
                val rIcon = matchedApp?.icon ?: defaultSysIcon
                val pInfo = permissionCatalog[Manifest.permission.RECORD_AUDIO] ?: clipboardTimelineInfo
                timeline.add(
                    PermissionAccessEvent(
                        id = "live_now_mic",
                        packageName = rPkg,
                        appName = rName,
                        icon = rIcon,
                        permissionTitle = "Call / Microphone Active Right Now",
                        shortTag = "LIVE NOW",
                        category = PermissionCategory.CAMERA_MIC,
                        riskLevel = PermissionRiskLevel.CRITICAL,
                        timestamp = now + 2L,
                        accessContext = "LIVE NOW • Active Audio",
                        permissionInfo = pInfo
                    )
                )
            }
        }

        if (liveCapture.isCameraActive) {
            val camLabel = liveCapture.activeCameraApps.firstOrNull()
            val matchedApp = summary.appReports.firstOrNull {
                it.appName.equals(camLabel, ignoreCase = true) || it.packageName == camLabel
            }
            val rPkg = matchedApp?.packageName ?: "system.sensor"
            if (rPkg !in ignored) {
                val rName = matchedApp?.appName ?: camLabel ?: "Camera Sensor"
                val rIcon = matchedApp?.icon ?: defaultSysIcon
                val pInfo = permissionCatalog[Manifest.permission.CAMERA] ?: clipboardTimelineInfo
                timeline.add(
                    PermissionAccessEvent(
                        id = "live_now_camera",
                        packageName = rPkg,
                        appName = rName,
                        icon = rIcon,
                        permissionTitle = "Camera Active Right Now",
                        shortTag = "LIVE NOW",
                        category = PermissionCategory.CAMERA_MIC,
                        riskLevel = PermissionRiskLevel.CRITICAL,
                        timestamp = now + 1L,
                        accessContext = "LIVE NOW • Active Camera",
                        permissionInfo = pInfo
                    )
                )
            }
        }

        // 1. Verified OS AppOps last-access timestamps (only real per-permission timestamps, never fabricated from app launch times)
        for (app in summary.appReports) {
            if (app.packageName in ignored) continue
            val pkgOps = appOpsTimes[app.packageName]
            if (!pkgOps.isNullOrEmpty()) {
                val candidates = buildList {
                    addAll(app.grantedPermissions)
                    if (pkgOps.containsKey("SPECIAL_CLIPBOARD_READ")) add(clipboardTimelineInfo)
                    if (pkgOps.containsKey("SPECIAL_SCREEN_CAPTURE")) add(screenCaptureTimelineInfo)
                }

                for (perm in candidates) {
                    val appOpTime = pkgOps[perm.permissionName] ?: 0L
                    if (appOpTime in 1L..now) {
                        timeline.add(
                            PermissionAccessEvent(
                                id = "appops_${app.packageName}_${perm.permissionName}_$appOpTime",
                                packageName = app.packageName,
                                appName = app.appName,
                                icon = app.icon,
                                permissionTitle = perm.friendlyTitle,
                                shortTag = perm.shortTag,
                                category = perm.category,
                                riskLevel = perm.riskLevel,
                                timestamp = appOpTime,
                                accessContext = "Verified AppOps",
                                permissionInfo = perm
                            )
                        )
                    }
                }
            }
        }

        // 2. Real-time hardware & state-transition events observed by LiveGuardMonitor (unified Event Log + Timeline)
        val sensorEvents = guardMonitor.events.value.filter { ev ->
            if (ev.type == "AUDIT" || ev.title.contains("Guard Test", ignoreCase = true)) return@filter false
            // Guard against any historical false-positive SCREEN event on non-screen-recorder apps
            if (ev.type == "SCREEN" && !ev.packageName.isNullOrBlank()) {
                val pLower = ev.packageName.lowercase()
                val isDedicatedScreenApp = pLower.contains("screenrecord") ||
                    pLower.contains("screencast") ||
                    pLower.contains("azscreen") ||
                    pLower.contains("du.recorder") ||
                    pLower.contains("mobizen") ||
                    pLower.contains("xrecorder")
                val hasHardCaptureEvidence = ev.description.contains("Display:", ignoreCase = true) ||
                    ev.description.contains("OS ScreenRecordingCallback", ignoreCase = true) ||
                    ev.description.contains("File:", ignoreCase = true) ||
                    ev.description.contains("AppOps:", ignoreCase = true) ||
                    ev.description.contains("Route:", ignoreCase = true) ||
                    ev.description.contains("OS Screen Recording", ignoreCase = true) ||
                    ev.description.contains("Virtual Display", ignoreCase = true)
                if (!isDedicatedScreenApp && !hasHardCaptureEvidence) {
                    return@filter false
                }
            }
            true
        }

        for (ev in sensorEvents) {
            val pkg = ev.packageName
            if (pkg != null && pkg in ignored) continue

            val isMirrorEvent = ev.type == "SCREEN" && (
                ev.title.contains("Mirror", ignoreCase = true) ||
                    ev.title.contains("Cast", ignoreCase = true) ||
                    ev.description.contains("mirror", ignoreCase = true) ||
                    ev.description.contains("cast", ignoreCase = true)
                )

            val permInfo = when (ev.type) {
                "MIC" -> permissionCatalog[Manifest.permission.RECORD_AUDIO]
                "CAMERA" -> permissionCatalog[Manifest.permission.CAMERA]
                "LOCATION" -> permissionCatalog[Manifest.permission.ACCESS_FINE_LOCATION]
                "CLIPBOARD" -> clipboardTimelineInfo
                "SCREEN" -> if (isMirrorEvent) screenMirrorTimelineInfo else screenCaptureTimelineInfo
                "ACCESSIBILITY" -> permissionCatalog["SPECIAL_ACCESSIBILITY_SERVICE"]
                "NOTIFICATION_LISTENER" -> permissionCatalog["SPECIAL_NOTIFICATION_LISTENER"]
                "OVERLAY" -> permissionCatalog[Manifest.permission.SYSTEM_ALERT_WINDOW]
                "DEVICE_ADMIN" -> permissionCatalog["SPECIAL_DEVICE_ADMIN"]
                "KEYBOARD" -> customKeyboardTimelineInfo
                "USB_ADB" -> if (ev.title.contains("USB", ignoreCase = true) || ev.description.contains("USB", ignoreCase = true)) {
                    usbDebuggingTimelineInfo
                } else {
                    sideloadTimelineInfo
                }
                "STEALTH_STALKERWARE" -> stealthStalkerwareTimelineInfo
                "APK_CERT_TROJAN" -> apkCertTrojanTimelineInfo
                else -> null
            } ?: continue

            val (resolvedPkg, baseName, resolvedIcon) = resolvePkgMeta(pkg)
            val resolvedName = if (resolvedPkg != "system.sensor") {
                baseName
            } else {
                when (ev.type) {
                    "MIC" -> "Microphone / Call Audio"
                    "CAMERA" -> "Camera Sensor"
                    "SCREEN" -> if (isMirrorEvent) "Screen Mirroring / Cast" else "Screen Recording"
                    "ACCESSIBILITY" -> "Screen Reader Service"
                    "NOTIFICATION_LISTENER" -> "Notification Reader"
                    "OVERLAY" -> "Screen Overlay"
                    "DEVICE_ADMIN" -> "Device Admin"
                    "KEYBOARD" -> "Custom Keyboard"
                    "USB_ADB" -> "Developer / Sideload Check"
                    "STEALTH_STALKERWARE" -> "Stealth / Stalkerware Check"
                    "APK_CERT_TROJAN" -> "APK / Trojan Heuristic Check"
                    else -> ev.title
                }
            }

            // Preserve the specific event title & description from LiveGuardMonitor
            val richTitle = if (ev.description.isNotBlank() && !ev.description.equals(ev.title, ignoreCase = true)) {
                "${ev.title} — ${ev.description}"
            } else {
                ev.title.ifBlank { permInfo.friendlyTitle }
            }

            val duplicateNearby = timeline.any { existing ->
                existing.packageName == resolvedPkg &&
                    existing.permissionInfo.permissionName == permInfo.permissionName &&
                    kotlin.math.abs(existing.timestamp - ev.timestamp) < 5_000L &&
                    !existing.id.startsWith("live_now_")
            }
            if (!duplicateNearby) {
                timeline.add(
                    PermissionAccessEvent(
                        id = "guard_${ev.id}_${permInfo.permissionName}",
                        packageName = resolvedPkg,
                        appName = resolvedName,
                        icon = resolvedIcon,
                        permissionTitle = richTitle,
                        shortTag = permInfo.shortTag,
                        category = permInfo.category,
                        riskLevel = permInfo.riskLevel,
                        timestamp = ev.timestamp,
                        accessContext = when (ev.type) {
                            "MIC", "CAMERA", "SCREEN", "CLIPBOARD", "LOCATION" -> "Hardware / Sensor Event"
                            else -> "State Change Alert"
                        },
                        permissionInfo = permInfo
                    )
                )
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
    }

    /**
     * Queries real-time UsageEvents over the last 15 minutes so apps that are currently
     * in an active phone call, foreground session, or running a foreground service
     * appear in the Timeline immediately while the activity/service is ongoing
     * (without waiting for the activity to pause/finish).
     */
    private fun queryLiveUsageEventsTimestamps(context: Context): Map<String, Long> {
        if (!hasUsageStatsPermission(context)) return emptyMap()
        val result = HashMap<String, Long>()
        try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: return emptyMap()
            val now = System.currentTimeMillis()
            val events = usm.queryEvents(now - 15 * 60_000L, now) ?: return emptyMap()
            val ev = UsageEvents.Event()
            val selfPkg = context.packageName
            while (events.hasNextEvent()) {
                events.getNextEvent(ev)
                val pkg = ev.packageName ?: continue
                if (pkg == selfPkg || pkg == "android") continue
                val isLiveEvent = ev.eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                        ev.eventType == UsageEvents.Event.FOREGROUND_SERVICE_START)
                if (isLiveEvent && ev.timeStamp in 1L..now) {
                    val prev = result[pkg] ?: 0L
                    if (ev.timeStamp > prev) {
                        result[pkg] = ev.timeStamp
                    }
                }
            }
        } catch (_: Throwable) {
            // Ignore
        }
        return result
    }

    @Volatile
    private var appOpsGetPackagesForOpsMethod: java.lang.reflect.Method? = null
    @Volatile
    private var pkgOpsGetPackageNameMethod: java.lang.reflect.Method? = null
    @Volatile
    private var pkgOpsGetOpsMethod: java.lang.reflect.Method? = null
    @Volatile
    private var opEntryGetOpStrMethod: java.lang.reflect.Method? = null
    @Volatile
    private var opEntryGetModeMethod: java.lang.reflect.Method? = null
    @Volatile
    private var opEntryGetLastAccessTimeMethod: java.lang.reflect.Method? = null

    /**
     * Queries OS AppOps last-access timestamps ONLY for real sensor/data reads where the op mode
     * is actually ALLOWED (`MODE_ALLOWED` or `MODE_FOREGROUND`).
     *
     * Why this is critical:
     * Android stamps `lastAccessTime` on `android:request_install_packages`, `android:system_alert_window`,
     * and `android:get_usage_stats` whenever an app merely calls `canRequestPackageInstalls()` or
     * `Settings.canDrawOverlays()` to check if it has permission—even when the permission is DENIED!
     * Excluding those passive capability-check ops and requiring `mode == MODE_ALLOWED || MODE_FOREGROUND`
     * completely eliminates false-positive Timeline entries for apps like PhonePe and Uber.
     */
    private fun queryAppOpsLastAccessMap(context: Context): Map<String, Map<String, Long>> {
        val map = mutableMapOf<String, MutableMap<String, Long>>()
        val now = System.currentTimeMillis()
        try {
            // Reuse LiveGuardMonitor's synchronized, targeted AppOps query cache to avoid duplicate large Binder transactions
            val pkgOpsList = LiveGuardMonitor.getInstance(context).getCachedOrFetchPackageOps(forceFresh = false)
                ?: return emptyMap()

            for (pkgOps in pkgOpsList) {
                if (pkgOps == null) continue
                val pkgNameMethod = pkgOpsGetPackageNameMethod
                    ?: pkgOps.javaClass.getMethod("getPackageName").also { pkgOpsGetPackageNameMethod = it }
                val opsMethod = pkgOpsGetOpsMethod
                    ?: pkgOps.javaClass.getMethod("getOps").also { pkgOpsGetOpsMethod = it }
                val pkgName = pkgNameMethod.invoke(pkgOps) as? String ?: continue
                val ops = opsMethod.invoke(pkgOps) as? List<*> ?: continue
                val permTimes = map.getOrPut(pkgName) { mutableMapOf() }

                for (opEntry in ops) {
                    if (opEntry == null) continue
                    val opStr = try {
                        val opStrMethod = opEntryGetOpStrMethod
                            ?: opEntry.javaClass.getMethod("getOpStr").also { opEntryGetOpStrMethod = it }
                        opStrMethod.invoke(opEntry) as? String
                    } catch (_: Exception) {
                        null
                    } ?: continue

                    val opMode = try {
                        val modeMethod = opEntryGetModeMethod
                            ?: opEntry.javaClass.getMethod("getMode").also { opEntryGetModeMethod = it }
                        (modeMethod.invoke(opEntry) as? Int) ?: AppOpsManager.MODE_ALLOWED
                    } catch (_: Exception) {
                        AppOpsManager.MODE_ALLOWED
                    }
                    // Skip any op that is currently ignored/denied or in ungranted default state for special ops
                    if (opMode != AppOpsManager.MODE_ALLOWED && opMode != 4 /* MODE_FOREGROUND */) {
                        continue
                    }

                    // Use getLastAccessTime ONLY (never getTime(), which can reflect op-grant/mode-change time)
                    // Flag 0x15 = OP_FLAG_SELF (0x1) | OP_FLAG_TRUSTED_PROXIED (0x4) | OP_FLAG_TRUSTED_PROXY (0x10)
                    val lastTime = try {
                        val m = opEntryGetLastAccessTimeMethod
                            ?: opEntry.javaClass.getMethod("getLastAccessTime", Int::class.javaPrimitiveType).also {
                                opEntryGetLastAccessTimeMethod = it
                            }
                        (m.invoke(opEntry, 0x15) as? Long) ?: 0L
                    } catch (_: Exception) {
                        0L
                    }

                    val recentCutoff = now - (7L * 86_400_000L)
                    if (lastTime in recentCutoff..(now + 60_000L)) {
                        val mappedPerm = when (opStr) {
                            AppOpsManager.OPSTR_CAMERA, "android:camera" -> Manifest.permission.CAMERA
                            AppOpsManager.OPSTR_RECORD_AUDIO, "android:record_audio" -> Manifest.permission.RECORD_AUDIO
                            AppOpsManager.OPSTR_FINE_LOCATION,
                            "android:fine_location",
                            "android:monitor_location",
                            "android:monitor_location_high_power" -> Manifest.permission.ACCESS_FINE_LOCATION
                            AppOpsManager.OPSTR_COARSE_LOCATION, "android:coarse_location" -> Manifest.permission.ACCESS_COARSE_LOCATION
                            AppOpsManager.OPSTR_READ_CONTACTS, "android:read_contacts" -> Manifest.permission.READ_CONTACTS
                            AppOpsManager.OPSTR_READ_SMS, "android:read_sms" -> Manifest.permission.READ_SMS
                            "android:receive_sms" -> Manifest.permission.RECEIVE_SMS
                            "android:send_sms" -> Manifest.permission.SEND_SMS
                            AppOpsManager.OPSTR_READ_CALL_LOG, "android:read_call_log" -> Manifest.permission.READ_CALL_LOG
                            "android:read_media_images" -> Manifest.permission.READ_MEDIA_IMAGES
                            "android:read_media_video" -> Manifest.permission.READ_MEDIA_VIDEO
                            "android:project_media" -> "SPECIAL_SCREEN_CAPTURE"
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
    private const val KEY_HAS_BASELINE = "has_baseline_v2"
    private const val KEY_SNAPSHOT = "baseline_snapshot_v2"
    private const val KEY_CHANGE_LOG = "change_log_v2"

    fun openOsPrivacyDashboard(context: Context): Boolean {
        val actions = listOf(
            Settings.ACTION_PRIVACY_SETTINGS,
            Settings.ACTION_SECURITY_SETTINGS,
            Settings.ACTION_SETTINGS
        )
        for (action in actions) {
            val intent = Intent(action)
            if (context !is android.app.Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                if (context.packageManager.resolveActivity(intent, 0) != null) {
                    context.startActivity(intent)
                    return true
                }
            } catch (_: Exception) {
            }
        }
        return false
    }

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
    fun getPrivacyChanges(
        context: Context,
        currentReports: List<AppPrivacyReport>,
        dispatchNotifications: Boolean = true
    ): List<PrivacyChangeEvent> {
        val prefs = context.getSharedPreferences(PREFS_PRIVACY_DIFF, Context.MODE_PRIVATE)
        val pm = context.packageManager
        val now = System.currentTimeMillis()

        // Always compare against non-system + current reports so toggling "System" doesn't create false diffs
        val userReports = currentReports.filter { !it.isSystem }
        val hasBaseline = prefs.getBoolean(KEY_HAS_BASELINE, false)
        val existingLog = decodeChangeLog(prefs.getString(KEY_CHANGE_LOG, "") ?: "").toMutableList()

        if (!hasBaseline) {
            val snapshotStr = encodeSnapshot(userReports)
            prefs.edit()
                .putBoolean(KEY_HAS_BASELINE, true)
                .putString(KEY_SNAPSHOT, snapshotStr)
                .putString(KEY_CHANGE_LOG, "")
                .apply()
            cachedChangeEvents = emptyList()
            return emptyList()
        }

        val oldSnapshot = decodeSnapshot(prefs.getString(KEY_SNAPSHOT, "") ?: "")
        val newEvents = mutableListOf<PrivacyChangeEvent>()
        var nextId = (existingLog.maxOfOrNull { it.id } ?: now).coerceAtLeast(now) + 1L

        val currentMap = userReports.associateBy { it.packageName }
        val newlyInstalledApps = mutableListOf<AppPrivacyReport>()
        val newlyGrantedPermsByApp = linkedMapOf<AppPrivacyReport, MutableList<PlainPermissionInfo>>()

        // Check for newly installed apps or permission additions/revocations
        for (app in userReports) {
            val prevEntry = oldSnapshot[app.packageName]
            val currentPermSet = app.grantedPermissions.map { it.permissionName }.toSet()
            if (prevEntry == null) {
                newlyInstalledApps.add(app)
                val permSummaryTitle = if (app.grantedPermissions.isNotEmpty()) {
                    "${app.grantedPermissions.size} permission(s) granted"
                } else {
                    "Installed • No sensitive permissions"
                }
                val permDetail = if (app.grantedPermissions.isNotEmpty()) {
                    app.grantedPermissions.take(4).joinToString(", ") { it.shortTag }
                } else {
                    app.installerName
                }
                newEvents.add(
                    PrivacyChangeEvent(
                        id = nextId++,
                        timestamp = now,
                        packageName = app.packageName,
                        appName = app.appName,
                        changeType = PrivacyChangeType.NEW_APP,
                        permissionTitle = permSummaryTitle,
                        detail = permDetail
                    )
                )
            } else {
                val prevPerms = prevEntry.second
                // Newly added permissions
                for (perm in app.grantedPermissions) {
                    if (perm.permissionName !in prevPerms) {
                        newlyGrantedPermsByApp.getOrPut(app) { mutableListOf() }.add(perm)
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

        if (newEvents.isNotEmpty() || oldSnapshot.keys != currentMap.keys) {
            prefs.edit()
                .putString(KEY_SNAPSHOT, encodeSnapshot(userReports))
                .putString(KEY_CHANGE_LOG, encodeChangeLog(updatedLog))
                .apply()
        }

        if (dispatchNotifications && (newlyInstalledApps.isNotEmpty() || newlyGrantedPermsByApp.isNotEmpty())) {
            dispatchInstallAndPermissionNotifications(
                context = context,
                newlyInstalledApps = newlyInstalledApps,
                newlyGrantedPermsByApp = newlyGrantedPermsByApp
            )
        }

        cachedChangeEvents = updatedLog
        return updatedLog
    }

    private fun dispatchInstallAndPermissionNotifications(
        context: Context,
        newlyInstalledApps: List<AppPrivacyReport>,
        newlyGrantedPermsByApp: Map<AppPrivacyReport, List<PlainPermissionInfo>>
    ) {
        val guardMonitor = LiveGuardMonitor.peekInstance()
        for (app in newlyInstalledApps) {
            val permsAddedText = if (app.grantedPermissions.isNotEmpty()) {
                "Added permissions: ${app.grantedPermissions.joinToString(", ") { it.friendlyTitle }}"
            } else {
                "No sensitive permissions granted (${app.installerName})"
            }
            val title = "New App Installed: ${app.appName}"
            val message = "${app.appName} installed • $permsAddedText"
            guardMonitor?.recordEvent(
                type = "INSTALL",
                title = title,
                description = message,
                severity = if (app.grantedPermissions.isNotEmpty()) AuditSeverity.WARNING else AuditSeverity.SAFE,
                packageName = app.packageName
            )
            sendPrivacyChangeNotification(
                context = context,
                notificationId = 4100 + (app.packageName.hashCode() and 0x7FFF),
                title = title,
                message = message,
                packageName = app.packageName
            )
        }

        for ((app, addedPerms) in newlyGrantedPermsByApp) {
            if (addedPerms.isEmpty()) continue
            val permNames = addedPerms.joinToString(", ") { it.friendlyTitle }
            val title = "New Permission Added: ${app.appName}"
            val message = "${app.appName} was granted: $permNames"
            guardMonitor?.recordEvent(
                type = "PERMISSION",
                title = title,
                description = message,
                severity = if (addedPerms.any { it.riskLevel == PermissionRiskLevel.CRITICAL || it.riskLevel == PermissionRiskLevel.HIGH }) {
                    AuditSeverity.WARNING
                } else {
                    AuditSeverity.SAFE
                },
                packageName = app.packageName
            )
            sendPrivacyChangeNotification(
                context = context,
                notificationId = 4500 + (app.packageName.hashCode() and 0x7FFF),
                title = title,
                message = message,
                packageName = app.packageName
            )
        }
    }

    fun sendPrivacyChangeNotification(
        context: Context,
        notificationId: Int,
        title: String,
        message: String,
        packageName: String? = null
    ) {
        val prefs = context.getSharedPreferences("thermal_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("live_guard_alerts", true)) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        try {
            NotificationChannels.ensureCreated(context)
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager ?: return
            val targetIntent = if (!packageName.isNullOrBlank()) {
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", packageName, null)
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
            } else {
                Intent(context, com.example.MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
            }
            val contentPi = android.app.PendingIntent.getActivity(
                context,
                notificationId,
                targetIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            val notification = androidx.core.app.NotificationCompat.Builder(
                context,
                NotificationChannels.GUARD_ALERT_CHANNEL_ID
            )
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(contentPi)
                .build()
            nm.notify(notificationId, notification)
        } catch (_: Exception) {
        }
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
