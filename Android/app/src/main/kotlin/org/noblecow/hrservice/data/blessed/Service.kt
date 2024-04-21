package org.noblecow.hrservice.data.blessed

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import com.welie.blessed.BluetoothCentral
import com.welie.blessed.GattStatus
import com.welie.blessed.ReadResponse

/**
 * Originally pulled from peripheral example in https://github.com/weliem/blessed-kotlin
 */
internal interface Service {
    val service: BluetoothGattService
    val serviceName: String

    fun onCharacteristicRead(
        central: BluetoothCentral,
        characteristic: BluetoothGattCharacteristic
    ): ReadResponse
    fun onCharacteristicWrite(
        central: BluetoothCentral,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray
    ): GattStatus
    fun onCharacteristicWriteCompleted(
        central: BluetoothCentral,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray
    )
    fun onDescriptorRead(
        central: BluetoothCentral,
        descriptor: BluetoothGattDescriptor
    ): ReadResponse
    fun onDescriptorWrite(
        central: BluetoothCentral,
        descriptor: BluetoothGattDescriptor,
        value: ByteArray
    ): GattStatus

    /**
     * @return whether this event should indicate that a client is "connected"
     */
    fun onNotifyingEnabled(
        central: BluetoothCentral,
        characteristic: BluetoothGattCharacteristic
    ): Boolean
    fun onNotifyingDisabled(central: BluetoothCentral, characteristic: BluetoothGattCharacteristic)
    fun onNotificationSent(
        central: BluetoothCentral,
        value: ByteArray,
        characteristic: BluetoothGattCharacteristic,
        status: GattStatus
    )
    fun onCentralConnected(central: BluetoothCentral)
    fun onCentralDisconnected(central: BluetoothCentral)
}
