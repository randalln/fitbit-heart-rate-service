package org.noblecow.hrservice.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import org.noblecow.hrservice.data.source.local.BluetoothLocalDataSource
import org.noblecow.hrservice.data.source.local.BluetoothLocalDataSourceImpl

@Module
@InstallIn(SingletonComponent::class)
internal interface DataSourceModule {
    @Singleton
    @Binds
    fun bindBluetoothLocalDataSource(
        dataSource: BluetoothLocalDataSourceImpl
    ): BluetoothLocalDataSource
}
