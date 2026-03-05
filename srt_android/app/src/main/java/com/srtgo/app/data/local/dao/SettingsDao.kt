package com.srtgo.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.srtgo.app.data.local.entity.SettingsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SettingsDao {

    @Query("SELECT * FROM settings WHERE category = :category")
    fun getByCategory(category: String): Flow<List<SettingsEntity>>

    @Query("SELECT * FROM settings WHERE category = :category AND key = :key LIMIT 1")
    suspend fun getByCategoryAndKey(category: String, key: String): SettingsEntity?

    @Upsert
    suspend fun upsert(entity: SettingsEntity)

    @Query("DELETE FROM settings WHERE category = :category AND key = :key")
    suspend fun delete(category: String, key: String)

    @Query("DELETE FROM settings WHERE category = :category")
    suspend fun deleteByCategory(category: String)
}
