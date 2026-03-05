package com.srtgo.app.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * All network classes (SessionManager, NetFunnelHelper, SrtClient, KtxClient)
 * use @Singleton + @Inject constructor, so Hilt provides them automatically.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule
