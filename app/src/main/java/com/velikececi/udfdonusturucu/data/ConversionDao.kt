package com.velikececi.udfdonusturucu.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversionDao {
    @Query("SELECT * FROM conversion_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<ConversionHistory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(conversion: ConversionHistory): Long

    @Delete
    suspend fun delete(conversion: ConversionHistory)

    @Query("DELETE FROM conversion_history")
    suspend fun clearAll()
}
