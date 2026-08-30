package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "thermal_records")
data class ThermalRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long,
    val batteryTemp: Float,
    val cpuTemp: Float,
    val batteryLevel: Int
)
