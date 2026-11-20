package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.ThermalRecord
import com.example.data.ThermalRepository
import com.example.service.ThermalMonitor
import com.example.service.ThermalTrackerService
import com.example.service.safeCoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.abs

class ThermalViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val repository = ThermalRepository(db.thermalDao())
    val thermalMonitor = ThermalMonitor.getInstance(application)
    private val safeHandler = safeCoroutineExceptionHandler()

    val allRecords: StateFlow<List<ThermalRecord>> = repository.allRecords
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Rolling 24-hour slice derived reactively from allRecords so long-lived sessions never go stale
    val recentRecords24h: StateFlow<List<ThermalRecord>> = allRecords
        .map { list ->
            val cutoff = System.currentTimeMillis() - (24L * 60L * 60L * 1000L)
            list.filter { it.timestamp >= cutoff }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private var lastFgRecordedTimeMs: Long = 0L
    private var lastFgRecordedTemp: Float = -100f

    init {
        thermalMonitor.startMonitoring()
        // Collect live thermal readings to seed initial chart samples and keep recording while app is open
        // even if the user disabled the persistent background service.
        viewModelScope.launch(Dispatchers.IO + safeHandler) {
            thermalMonitor.liveData.collect { live ->
                if (live.batteryTemp <= 0f) return@collect
                try {
                    val now = System.currentTimeMillis()
                    val count = if (lastFgRecordedTimeMs == 0L) repository.getRecordCount() else 1
                    val shouldRecord = when {
                        count == 0 -> true
                        !ThermalTrackerService.isServiceRunning -> {
                            lastFgRecordedTimeMs == 0L ||
                                (now - lastFgRecordedTimeMs) >= 60_000L ||
                                (abs(live.batteryTemp - lastFgRecordedTemp) >= 0.3f && (now - lastFgRecordedTimeMs) >= 15_000L)
                        }
                        else -> false
                    }
                    if (shouldRecord) {
                        repository.insertRecord(
                            ThermalRecord(
                                timestamp = now,
                                batteryTemp = live.batteryTemp,
                                cpuTemp = live.cpuTemp,
                                batteryLevel = live.batteryLevel
                            )
                        )
                        lastFgRecordedTimeMs = now
                        lastFgRecordedTemp = live.batteryTemp
                    }
                } catch (_: Throwable) {
                }
            }
        }
    }

    fun recordCurrentSampleIfNeeded() {
        viewModelScope.launch(Dispatchers.IO + safeHandler) {
            try {
                val live = thermalMonitor.liveData.value
                if (live.batteryTemp > 0f) {
                    val now = System.currentTimeMillis()
                    repository.insertRecord(
                        ThermalRecord(
                            timestamp = now,
                            batteryTemp = live.batteryTemp,
                            cpuTemp = live.cpuTemp,
                            batteryLevel = live.batteryLevel
                        )
                    )
                    lastFgRecordedTimeMs = now
                    lastFgRecordedTemp = live.batteryTemp
                }
            } catch (_: Throwable) {
            }
        }
    }
}
