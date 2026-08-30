package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ThermalDao {
    @Query("SELECT * FROM thermal_records ORDER BY timestamp ASC")
    fun getAllRecords(): Flow<List<ThermalRecord>>

    @Query("SELECT * FROM thermal_records WHERE timestamp >= :since ORDER BY timestamp ASC")
    fun getRecordsSince(since: Long): Flow<List<ThermalRecord>>
    
    @Query("SELECT * FROM thermal_records WHERE timestamp >= :since ORDER BY timestamp ASC")
    suspend fun getRecordsSinceSync(since: Long): List<ThermalRecord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: ThermalRecord)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecords(records: List<ThermalRecord>)

    @Query("SELECT COUNT(*) FROM thermal_records")
    suspend fun getRecordCount(): Int
}
