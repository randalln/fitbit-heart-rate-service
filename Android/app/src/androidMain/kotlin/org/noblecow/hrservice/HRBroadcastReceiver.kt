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
internal class HRBroadcastReceiver(
    val mainRepository: MainRepository
) : BroadcastReceiver() {
    // KoinComponent {
    // internal val mainRepository: MainRepository by inject()

    // @Inject
    // internal lateinit var mainRepository: MainRepository

    override fun onReceive(context: Context?, intent: Intent?) {
        runBlocking {
            mainRepository.stopServices()
        }
    }

    /*
    @ReceiverKey(HRBroadcastReceiver::class)
    @ContributesIntoMap(
        AppScope::class,
        binding = binding<MetroReceiverFactory.ReceiverInstanceFactory<*>>()
    )
    @AssistedFactory
    abstract class Factory : MetroReceiverFactory.ReceiverInstanceFactory<HRBroadcastReceiver>
     */
}
