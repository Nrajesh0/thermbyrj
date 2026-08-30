package com.example.data

import kotlinx.coroutines.flow.Flow

class ThermalRepository(private val thermalDao: ThermalDao) {
    fun getRecordsSince(since: Long): Flow<List<ThermalRecord>> = thermalDao.getRecordsSince(since)
    
    suspend fun getRecordsSinceSync(since: Long): List<ThermalRecord> = thermalDao.getRecordsSinceSync(since)
    val allRecords: Flow<List<ThermalRecord>> = thermalDao.getAllRecords()

    suspend fun insertRecord(record: ThermalRecord) = thermalDao.insertRecord(record)
    suspend fun insertRecords(records: List<ThermalRecord>) = thermalDao.insertRecords(records)
    suspend fun getRecordCount(): Int = thermalDao.getRecordCount()
}
