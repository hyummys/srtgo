package com.srtgo.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "macro_history")
data class MacroHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val railType: String,
    val departure: String,
    val arrival: String,
    val date: String,
    val time: String,
    val passengers: String,
    val seatType: String,
    val autoPay: Boolean,
    val selectedTrains: String,
    val status: String,
    val attempts: Int = 0,
    val elapsedSeconds: Double = 0.0,
    val resultJson: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val finishedAt: Long? = null
)
