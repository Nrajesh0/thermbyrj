package com.example.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [ThermalRecord::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun thermalDao(): ThermalDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: android.content.Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = androidx.room.Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "thermal_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
