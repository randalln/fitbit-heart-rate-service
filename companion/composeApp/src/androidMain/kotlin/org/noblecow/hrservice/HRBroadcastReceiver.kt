package org.noblecow.hrservice

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import kotlinx.coroutines.runBlocking
import org.noblecow.hrservice.data.repository.MainRepository
import org.noblecow.hrservice.di.ReceiverKey

@ContributesIntoMap(AppScope::class, binding<BroadcastReceiver>())
@ReceiverKey(HRBroadcastReceiver::class)
@Inject
class HRBroadcastReceiver(val mainRepository: MainRepository) : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        runBlocking {
            mainRepository.stopServices()
        }
    }
}
