package com.srtgo.app.di

import android.content.Context
import androidx.room.Room
import com.srtgo.app.data.local.AppDatabase
import com.srtgo.app.data.local.dao.CredentialDao
import com.srtgo.app.data.local.dao.MacroHistoryDao
import com.srtgo.app.data.local.dao.SettingsDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "srtgo.db"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @Singleton
    fun provideSettingsDao(database: AppDatabase): SettingsDao {
        return database.settingsDao()
    }

    @Provides
    @Singleton
    fun provideMacroHistoryDao(database: AppDatabase): MacroHistoryDao {
        return database.macroHistoryDao()
    }

    @Provides
    @Singleton
    fun provideCredentialDao(database: AppDatabase): CredentialDao {
        return database.credentialDao()
    }
}
