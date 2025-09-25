package org.noblecow.hrservice.data.source.local

import kotlinx.coroutines.flow.Flow

internal interface BluetoothLocalDataSource {
    val advertisingState: Flow<AdvertisingState>
    val clientConnectedState: Flow<Boolean>
    fun getHardwareState(): HardwareState
    fun getMissingPermissions(): Array<out String>
    fun startAdvertising()
    fun stop()
    fun notifyHeartRate(bpm: Int): Unit?
    fun permissionsGranted(): Boolean
}

internal sealed class AdvertisingState {
    data object Started : AdvertisingState()
    data object Stopped : AdvertisingState()
    data object Failure : AdvertisingState()
}

internal enum class HardwareState {
    DISABLED,
    HARDWARE_UNSUITABLE,
    READY
}

/*
internal class FakeBluetoothLocalDataSourceImpl : BluetoothLocalDataSource {
    override val advertisingState: Flow<AdvertisingState>
        get() = TODO("Not yet implemented")
    override val clientConnectedState: Flow<Boolean>
        get() = TODO("Not yet implemented")

    override fun getHardwareState(): HardwareState {
        TODO("Not yet implemented")
    }

    override fun getMissingPermissions(): Array<out String> {
        TODO("Not yet implemented")
    }

    override fun startAdvertising() {
        TODO("Not yet implemented")
    }

    override fun stop() {
        TODO("Not yet implemented")
    }

    override fun notifyHeartRate(bpm: Int) {
        TODO("Not yet implemented")
    }

    override fun permissionsGranted(): Boolean {
        TODO("Not yet implemented")
    }
}
*/
