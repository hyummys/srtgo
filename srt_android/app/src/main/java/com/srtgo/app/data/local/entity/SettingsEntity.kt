package com.srtgo.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "settings",
    indices = [Index(value = ["category", "key"], unique = true)]
)
data class SettingsEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val category: String,
    val key: String,
    val value: String,
    val encrypted: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)
