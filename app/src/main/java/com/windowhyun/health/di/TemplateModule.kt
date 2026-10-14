package com.windowhyun.health.di

import com.windowhyun.health.data.catalog.FileUserTemplateRepository
import com.windowhyun.health.data.catalog.UserTemplateRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class TemplateModule {
    @Binds
    @Singleton
    abstract fun bindUserTemplates(impl: FileUserTemplateRepository): UserTemplateRepository
}
