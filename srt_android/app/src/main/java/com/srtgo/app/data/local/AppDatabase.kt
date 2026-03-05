package com.srtgo.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.srtgo.app.data.local.dao.CredentialDao
import com.srtgo.app.data.local.dao.MacroHistoryDao
import com.srtgo.app.data.local.dao.SettingsDao
import com.srtgo.app.data.local.entity.CredentialEntity
import com.srtgo.app.data.local.entity.MacroHistoryEntity
import com.srtgo.app.data.local.entity.SettingsEntity

@Database(
    entities = [SettingsEntity::class, MacroHistoryEntity::class, CredentialEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun settingsDao(): SettingsDao
    abstract fun macroHistoryDao(): MacroHistoryDao
    abstract fun credentialDao(): CredentialDao
}
