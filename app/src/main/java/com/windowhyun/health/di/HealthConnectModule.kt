package com.windowhyun.health.di

import com.windowhyun.health.data.healthconnect.AndroidHealthConnectGateway
import com.windowhyun.health.data.healthconnect.DataStoreHealthConnectLedger
import com.windowhyun.health.domain.model.HealthConnectGateway
import com.windowhyun.health.domain.model.HealthConnectLedger
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class HealthConnectModule {
    @Binds
    @Singleton
    abstract fun bindGateway(impl: AndroidHealthConnectGateway): HealthConnectGateway

    @Binds
    @Singleton
    abstract fun bindLedger(impl: DataStoreHealthConnectLedger): HealthConnectLedger
}
