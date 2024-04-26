package org.noblecow.hrservice.data

import javax.inject.Inject
import org.noblecow.hrservice.data.blessed.BluetoothLocalDataSource

internal class BluetoothRepository @Inject constructor(
    private val dataSource: BluetoothLocalDataSource
) {
    fun getAdvertisingFlow() = dataSource.advertisingFlow
    fun getClientConnectionFlow() = dataSource.clientConnectionFlow
    fun getHardwareState() = dataSource.getHardwareState()
    fun getMissingPermissions() = dataSource.getMissingPermissions() ?: emptyArray()
    fun notifyHeartRate(bpm: Int) = dataSource.notifyHeartRate(bpm)
    fun permissionsGranted() = dataSource.permissionsGranted() ?: false
    fun startAdvertising() = dataSource.startAdvertising()
    fun stop() = dataSource.stop()
}
