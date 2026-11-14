package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.ThermalRecord
import com.example.data.ThermalRepository
import com.example.service.ThermalMonitor
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ThermalViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val repository = ThermalRepository(db.thermalDao())
    val thermalMonitor = ThermalMonitor.getInstance(application)

    val allRecords: StateFlow<List<ThermalRecord>> = repository.allRecords
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val recentRecords24h: StateFlow<List<ThermalRecord>> =
        repository.getRecordsSince(System.currentTimeMillis() - (24L * 60L * 60L * 1000L))
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    init {
        thermalMonitor.startMonitoring()
        viewModelScope.launch {
            val live = thermalMonitor.liveData.value
            if (live.batteryTemp > 0f && repository.getRecordCount() == 0) {
                repository.insertRecord(
                    ThermalRecord(
                        timestamp = System.currentTimeMillis(),
                        batteryTemp = live.batteryTemp,
                        cpuTemp = live.cpuTemp,
                        batteryLevel = live.batteryLevel
                    )
                )
            }
        }
    }

    fun recordCurrentSampleIfNeeded() {
        viewModelScope.launch {
            val live = thermalMonitor.liveData.value
            if (live.batteryTemp > 0f) {
                repository.insertRecord(
                    ThermalRecord(
                        timestamp = System.currentTimeMillis(),
                        batteryTemp = live.batteryTemp,
                        cpuTemp = live.cpuTemp,
                        batteryLevel = live.batteryLevel
                    )
                )
            }
        }
    }
}
