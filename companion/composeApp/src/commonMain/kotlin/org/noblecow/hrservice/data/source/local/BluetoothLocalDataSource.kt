package org.noblecow.hrservice.data.source.local

import kotlinx.coroutines.flow.Flow

internal interface BluetoothLocalDataSource {
    val advertisingState: Flow<AdvertisingState>
    val clientConnectedState: Flow<Boolean>
    fun getHardwareState(): HardwareState
    fun getMissingPermissions(): Array<out String>
    fun startAdvertising()
    fun stopAdvertising()
    fun notifyHeartRate(bpm: Int): Boolean
    fun permissionsGranted(): Boolean
}

sealed class AdvertisingState {
    data object Started : AdvertisingState()
    data object Stopped : AdvertisingState()
    data object Failure : AdvertisingState()
}

enum class HardwareState {
    DISABLED,
    HARDWARE_UNSUITABLE,
    READY
}
