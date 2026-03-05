package com.srtgo.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.srtgo.app.data.local.entity.MacroHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MacroHistoryDao {

    @Query("SELECT * FROM macro_history ORDER BY createdAt DESC")
    fun getAll(): Flow<List<MacroHistoryEntity>>

    @Query("SELECT * FROM macro_history WHERE id = :id LIMIT 1")
    suspend fun getById(id: Int): MacroHistoryEntity?

    @Query("SELECT * FROM macro_history WHERE status = :status ORDER BY createdAt DESC")
    suspend fun getByStatus(status: String): List<MacroHistoryEntity>

    @Insert
    suspend fun insert(entity: MacroHistoryEntity): Long

    @Update
    suspend fun update(entity: MacroHistoryEntity)

    @Query("""
        UPDATE macro_history
        SET status = :status, attempts = :attempts, elapsedSeconds = :elapsedSeconds,
            resultJson = :resultJson, finishedAt = :finishedAt
        WHERE id = :id
    """)
    suspend fun updateStatus(
        id: Int,
        status: String,
        attempts: Int,
        elapsedSeconds: Double,
        resultJson: String?,
        finishedAt: Long?
    )

    @Query("DELETE FROM macro_history WHERE createdAt < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)
}
