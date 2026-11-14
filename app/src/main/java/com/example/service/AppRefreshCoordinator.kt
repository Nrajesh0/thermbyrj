package com.example.service

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

object AppRefreshCoordinator {
    private val _refreshVersion = MutableStateFlow(0)
    val refreshVersion: StateFlow<Int> = _refreshVersion.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    suspend fun triggerRefreshAll(context: Context) {
        if (_isRefreshing.value) return
        _isRefreshing.value = true
        try {
            val appCtx = context.applicationContext
            withContext(Dispatchers.IO) {
                StorageScanCache.invalidate()
                PrivacyAnalyzer.invalidateAllCaches()
                val summary = PrivacyAnalyzer.analyzeDevicePrivacy(
                    appCtx,
                    includeSystemApps = false,
                    forceRefresh = true
                )
                PrivacyAnalyzer.getPrivacyChanges(
                    context = appCtx,
                    currentReports = summary.appReports,
                    dispatchNotifications = true
                )
                PrivacyAnalyzer.analyzeAppTelemetryAndTrackers(
                    appCtx,
                    includeSystemApps = false,
                    forceRefresh = true
                )
                val guard = LiveGuardMonitor.getInstance(appCtx)
                guard.refreshAllStates()
                guard.runDeepSecurityAudit(includeSystemApps = false, logAuditEvent = false)
                PrivacyAnalyzer.getPermissionAccessTimeline(
                    appCtx,
                    includeSystemApps = false,
                    forceRefresh = true
                )
                ThermalMonitor.getInstance(appCtx).forceUpdate()
            }
            _refreshVersion.value = _refreshVersion.value + 1
        } catch (_: Exception) {
            _refreshVersion.value = _refreshVersion.value + 1
        } finally {
            _isRefreshing.value = false
        }
    }
}
