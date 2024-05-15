package org.noblecow.hrservice.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import org.noblecow.hrservice.data.repository.MainRepository
import org.noblecow.hrservice.data.repository.MainRepositoryImpl

@Module
@InstallIn(SingletonComponent::class)
internal interface RepositoryModule {
    @Singleton
    @Binds
    fun bindMainRepository(repository: MainRepositoryImpl): MainRepository
}
