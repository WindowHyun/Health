package com.windowhyun.health.di

import android.content.Context
import com.windowhyun.health.wear.PlayServicesWatchTransport
import com.windowhyun.health.wear.WatchTransport
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object WearModule {
    @Provides
    @Singleton
    fun provideWatchTransport(@ApplicationContext context: Context): WatchTransport =
        PlayServicesWatchTransport(context)
}
