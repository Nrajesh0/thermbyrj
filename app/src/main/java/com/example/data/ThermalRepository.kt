package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch

class ThermalRepository(private val thermalDao: ThermalDao) {
    fun getRecordsSince(since: Long): Flow<List<ThermalRecord>> =
        thermalDao.getRecordsSince(since).catch { emit(emptyList()) }

    suspend fun getRecordsSinceSync(since: Long): List<ThermalRecord> = try {
        thermalDao.getRecordsSinceSync(since)
    } catch (_: Throwable) {
        emptyList()
    }

    val allRecords: Flow<List<ThermalRecord>> =
        thermalDao.getAllRecords().catch { emit(emptyList()) }

    suspend fun insertRecord(record: ThermalRecord) {
        try {
            thermalDao.insertRecord(record)
        } catch (_: Throwable) {
        }
    }

    suspend fun insertRecords(records: List<ThermalRecord>) {
        try {
            thermalDao.insertRecords(records)
        } catch (_: Throwable) {
        }
    }

    suspend fun deleteRecordsOlderThan(cutoff: Long) {
        try {
            thermalDao.deleteRecordsOlderThan(cutoff)
        } catch (_: Throwable) {
        }
    }

    suspend fun getRecordCount(): Int = try {
        thermalDao.getRecordCount()
    } catch (_: Throwable) {
        0
    }
}
