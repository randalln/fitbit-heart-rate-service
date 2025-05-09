package org.noblecow.hrservice.di

import kotlinx.coroutines.Dispatchers
import org.koin.androidx.workmanager.dsl.worker
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.noblecow.hrservice.MainWorker
import org.noblecow.hrservice.data.repository.MainRepository
import org.noblecow.hrservice.data.repository.MainRepositoryImpl
import org.noblecow.hrservice.data.source.local.BluetoothLocalDataSource
import org.noblecow.hrservice.data.source.local.BluetoothLocalDataSourceImpl
import org.noblecow.hrservice.data.source.local.FakeBpmLocalDataSource
import org.noblecow.hrservice.data.source.local.WebServerLocalDataSource
import org.noblecow.hrservice.data.util.PermissionsHelper
import org.noblecow.hrservice.ui.MainViewModel

const val DEFAULT_DISPATCHER = "DefaultDispatcher"
const val IO_DISPATCHER = "IoDispatcher"

val appModule = module {
    single(named(DEFAULT_DISPATCHER)) { Dispatchers.Default }
    single(named(IO_DISPATCHER)) { Dispatchers.IO }

    single<BluetoothLocalDataSource> {
        BluetoothLocalDataSourceImpl(get(), get(named(DEFAULT_DISPATCHER)))
    }
    single {
        FakeBpmLocalDataSource(get(named(IO_DISPATCHER)))
    }
    singleOf(::PermissionsHelper)
    singleOf(::WebServerLocalDataSource)
    single<MainRepository> {
        MainRepositoryImpl(get(), get(), get(), get(named(IO_DISPATCHER)))
    }

    viewModelOf(::MainViewModel)

    worker { params -> MainWorker(get(), params.get(), get(), get(named(IO_DISPATCHER))) }
}
