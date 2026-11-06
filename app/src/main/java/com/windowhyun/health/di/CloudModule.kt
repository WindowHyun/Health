package com.windowhyun.health.di

import com.windowhyun.health.data.cloud.FirebaseCloudAccount
import com.windowhyun.health.data.cloud.FirestoreCloudBackupStore
import com.windowhyun.health.domain.cloud.CloudAccount
import com.windowhyun.health.domain.cloud.CloudBackupStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CloudModule {
    @Binds
    @Singleton
    abstract fun bindAccount(impl: FirebaseCloudAccount): CloudAccount

    @Binds
    @Singleton
    abstract fun bindStore(impl: FirestoreCloudBackupStore): CloudBackupStore
}
