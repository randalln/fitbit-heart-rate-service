package org.noblecow.hrservice.data.di

import javax.inject.Qualifier

@Retention(AnnotationRetention.RUNTIME)
@Qualifier
internal annotation class DefaultDispatcher

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
internal annotation class IoDispatcher
