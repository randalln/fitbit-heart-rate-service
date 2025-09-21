package org.noblecow.hrservice.di

import kotlinx.coroutines.Dispatchers
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Named
import org.koin.core.annotation.Qualifier
import org.koin.core.annotation.Single

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class DefaultDispatcher

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class IoDispatcher

@Module
@ComponentScan("org.noblecow.hrservice")
@Suppress("InjectDispatcher")
class AppModule {
    @Single
    @DefaultDispatcher
    fun dispatcherDefault() = Dispatchers.Default

    @Single
    @IoDispatcher
    fun dispatcherIo() = Dispatchers.IO
}
