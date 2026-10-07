package com.windowhyun.health.di

import com.windowhyun.health.service.AndroidRunVoice
import com.windowhyun.health.service.RunVoice
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RunVoiceModule {
    @Binds
    @Singleton
    abstract fun bindRunVoice(impl: AndroidRunVoice): RunVoice
}
