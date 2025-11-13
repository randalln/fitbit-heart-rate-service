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
import kotlin.reflect.KClass
import org.noblecow.hrservice.viewmodel.ViewModelGraph
import org.noblecow.hrservice.worker.WorkerCoordinator

@DependencyGraph(
    AppScope::class,
    bindingContainers = [CoroutineProviders::class]
)
internal interface AndroidAppGraph :
    AppGraph,
    ViewModelGraph.Factory {

    /**
     * A multibinding map of activity classes to their providers accessible for
     * [MetroAppComponentFactory].
     */
    @Multibinds
    val activityProviders: Map<KClass<out Activity>, Provider<Activity>>

    val workerCoordinator: WorkerCoordinator

    @Provides
    fun providesWorkManager(application: Context): WorkManager = WorkManager.getInstance(application)

    @Multibinds
    val workerProviders:
        Map<KClass<out ListenableWorker>, Provider<MetroWorkerFactory.WorkerInstanceFactory<*>>>

    val workerFactory: MetroWorkerFactory

    @Multibinds
    val receiverProviders: Map<KClass<out BroadcastReceiver>, Provider<BroadcastReceiver>>

    @Provides
    fun provideApplicationContext(application: Application): Context = application

    @DependencyGraph.Factory
    interface Factory {
        fun create(
            @Provides application: Application
        ): AndroidAppGraph
    }
}
