package org.noblecow.hrservice.di

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.Qualifier
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class DefaultDispatcher

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class IoDispatcher

@BindingContainer
object CoroutineProviders {
    @DefaultDispatcher
    @Provides
    @SingleIn(AppScope::class)
    fun dispatcherDefault(): CoroutineDispatcher = Dispatchers.Default

    @IoDispatcher
    @Provides
    @SingleIn(AppScope::class)
    fun dispatcherIo(): CoroutineDispatcher = Dispatchers.IO
}
