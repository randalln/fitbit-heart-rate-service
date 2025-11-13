package org.noblecow.hrservice

import android.app.Application
import androidx.work.Configuration
import dev.zacsweers.metro.createGraphFactory
import org.noblecow.hrservice.di.AndroidAppGraph

class HeartRateApplication :
    Application(),
    Configuration.Provider {

    internal val appGraph by lazy { createGraphFactory<AndroidAppGraph.Factory>().create(application = this) }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(appGraph.workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        // Initialize WorkerCoordinator to manage MainWorker lifecycle
        appGraph.workerCoordinator.start()
    }
}
