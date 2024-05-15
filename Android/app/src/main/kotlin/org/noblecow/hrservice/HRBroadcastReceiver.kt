package org.noblecow.hrservice

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.noblecow.hrservice.data.repository.MainRepository

@AndroidEntryPoint
class HRBroadcastReceiver : BroadcastReceiver() {
    @Inject
    internal lateinit var mainRepository: MainRepository

    override fun onReceive(context: Context?, intent: Intent?) {
        runBlocking {
            mainRepository.stopServices()
        }
    }
}
