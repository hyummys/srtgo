package com.srtgo.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.srtgo.app.data.local.entity.CredentialEntity

@Dao
interface CredentialDao {

    @Query("SELECT * FROM credentials WHERE railType = :railType LIMIT 1")
    suspend fun getByRailType(railType: String): CredentialEntity?

    @Upsert
    suspend fun upsert(entity: CredentialEntity)

    @Query("DELETE FROM credentials WHERE railType = :railType")
    suspend fun delete(railType: String)

    @Query("SELECT * FROM credentials")
    suspend fun getAll(): List<CredentialEntity>
}
