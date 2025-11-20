package org.noblecow.hrservice.data.source.local.blessed

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothGattService.SERVICE_TYPE_PRIMARY
import com.welie.blessed.BluetoothCentral
import com.welie.blessed.BluetoothPeripheralManager
import java.util.UUID
import org.noblecow.hrservice.data.util.HRS_SERVICE_UUID_VAL
import org.noblecow.hrservice.data.util.HR_MEASUREMENT_CHAR_UUID_VAL

/**
 * Originally pulled from peripheral example in https://github.com/weliem/blessed-kotlin
 */
internal class HeartRateService(peripheralManager: BluetoothPeripheralManager) :
    BaseService(
        peripheralManager,
        BluetoothGattService(HRS_SERVICE_UUID, SERVICE_TYPE_PRIMARY),
        "HeartRate Service"
    ) {

    private val measurement = BluetoothGattCharacteristic(
        HEARTRATE_MEASUREMENT_CHARACTERISTIC_UUID,
        BluetoothGattCharacteristic.PROPERTY_NOTIFY,
        0
    )

    private var notifyEnabled = false

    init {
        service.addCharacteristic(measurement)
        measurement.addDescriptor(cccDescriptor)
    }

    override fun onCentralDisconnected(central: BluetoothCentral) {
        if (noCentralsConnected()) {
            notifyEnabled = false
        }
    }

    override fun onNotifyingEnabled(
        central: BluetoothCentral,
        characteristic: BluetoothGattCharacteristic
    ): Boolean = if (characteristic.uuid == HEARTRATE_MEASUREMENT_CHARACTERISTIC_UUID) {
        notifyEnabled = true
        true
    } else {
        false
    }

    override fun onNotifyingDisabled(
        central: BluetoothCentral,
        characteristic: BluetoothGattCharacteristic
    ) {
        if (characteristic.uuid == HEARTRATE_MEASUREMENT_CHARACTERISTIC_UUID) {
            notifyEnabled = false
        }
    }

    fun notifyHeartRate(bpm: Int) {
        if (notifyEnabled) {
            val value = byteArrayOf(0x00, bpm.toByte())
            notifyCharacteristicChanged(value, measurement)
        }
    }

    internal companion object {
        val HRS_SERVICE_UUID: UUID = UUID.fromString(HRS_SERVICE_UUID_VAL)
        val HEARTRATE_MEASUREMENT_CHARACTERISTIC_UUID: UUID = UUID.fromString(HR_MEASUREMENT_CHAR_UUID_VAL)
    }
}
