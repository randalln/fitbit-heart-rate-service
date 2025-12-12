package org.noblecow.hrservice.di

import android.app.Activity
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkManager
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Multibinds
import dev.zacsweers.metro.Provider
import dev.zacsweers.metro.Provides
import dev.zacsweers.metrox.android.MetroAppComponentProviders
import dev.zacsweers.metrox.viewmodel.ViewModelGraph
import kotlin.reflect.KClass
import org.noblecow.hrservice.worker.WorkerCoordinator

@DependencyGraph(
    AppScope::class,
    bindingContainers = [CoroutineProviders::class, LoggerProviders::class]
)
internal interface AndroidAppGraph :
    AppGraph,
    MetroAppComponentProviders,
    ViewModelGraph {

    val workerCoordinator: WorkerCoordinator

    @Provides
    fun providesWorkManager(application: Context): WorkManager = WorkManager.getInstance(application)

    @Multibinds
    val workerProviders:
        Map<KClass<out ListenableWorker>, Provider<MetroWorkerFactory.WorkerInstanceFactory<*>>>

    val workerFactory: MetroWorkerFactory

    @Provides
    fun provideApplicationContext(application: Application): Context = application

    @DependencyGraph.Factory
    interface Factory {
        fun create(
            @Provides application: Application
        ): AndroidAppGraph
    }
}
