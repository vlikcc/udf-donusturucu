package com.velikececi.udfdonusturucu.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ConversionHistory::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun conversionDao(): ConversionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "udf_converter_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
