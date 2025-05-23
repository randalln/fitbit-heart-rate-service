@file:Suppress("ktlint:standard:no-wildcard-imports", "WildcardImport")
package org.noblecow.hrservice

import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.androidx.workmanager.koin.workManagerFactory
import org.koin.core.context.startKoin
import org.koin.ksp.generated.*
import org.noblecow.hrservice.di.AppModule

internal class HeartRateApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Initialize Koin
        startKoin {
            androidLogger()
            androidContext(this@HeartRateApplication)
            workManagerFactory()
            modules(AppModule().module)
        }
    }
}
