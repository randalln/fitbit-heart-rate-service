package org.noblecow.hrservice

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.runBlocking
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.noblecow.hrservice.data.repository.MainRepository

class HRBroadcastReceiver :
    BroadcastReceiver(),
    KoinComponent {
    internal val mainRepository: MainRepository by inject()

    override fun onReceive(context: Context?, intent: Intent?) {
        runBlocking {
            mainRepository.stopServices()
        }
    }
}
