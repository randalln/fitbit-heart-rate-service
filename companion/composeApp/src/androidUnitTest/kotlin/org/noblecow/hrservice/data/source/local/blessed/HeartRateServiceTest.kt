package org.noblecow.hrservice.data.source.local.blessed

import android.bluetooth.BluetoothGattCharacteristic
import com.welie.blessed.BluetoothCentral
import com.welie.blessed.BluetoothPeripheralManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.noblecow.hrservice.data.util.HRS_SERVICE_UUID_VAL
import org.noblecow.hrservice.data.util.HR_MEASUREMENT_CHAR_UUID_VAL

class HeartRateServiceTest {

    private lateinit var peripheralManager: BluetoothPeripheralManager
    private lateinit var heartRateService: HeartRateService
    private lateinit var mockCentral: BluetoothCentral

    @Before
    fun setup() {
        peripheralManager = mockk(relaxed = true) {
            every { connectedCentrals } returns emptySet()
        }
        heartRateService = HeartRateService(peripheralManager)
        mockCentral = mockk(relaxed = true)
    }

    @Test
    fun `service has correct UUID`() {
        assertEquals(
            UUID.fromString(HRS_SERVICE_UUID_VAL),
            HeartRateService.HRS_SERVICE_UUID
        )
    }

    @Test
    fun `characteristic has correct UUID`() {
        assertEquals(
            UUID.fromString(HR_MEASUREMENT_CHAR_UUID_VAL),
            HeartRateService.HEARTRATE_MEASUREMENT_CHARACTERISTIC_UUID
        )
    }

    @Test
    fun `onNotifyingEnabled enables notifications for correct characteristic`() {
        val characteristic = mockk<BluetoothGattCharacteristic> {
            every { uuid } returns HeartRateService.HEARTRATE_MEASUREMENT_CHARACTERISTIC_UUID
        }

        val result = heartRateService.onNotifyingEnabled(mockCentral, characteristic)

        assertTrue(result)
    }

    @Test
    fun `onNotifyingEnabled returns false for incorrect characteristic`() {
        val characteristic = mockk<BluetoothGattCharacteristic> {
            every { uuid } returns UUID.randomUUID()
        }

        val result = heartRateService.onNotifyingEnabled(mockCentral, characteristic)

        assertFalse(result)
    }

    @Test
    fun `onNotifyingDisabled disables notifications for correct characteristic`() {
        // First enable notifications
        val characteristic = mockk<BluetoothGattCharacteristic> {
            every { uuid } returns HeartRateService.HEARTRATE_MEASUREMENT_CHARACTERISTIC_UUID
        }
        heartRateService.onNotifyingEnabled(mockCentral, characteristic)

        // Then disable
        heartRateService.onNotifyingDisabled(mockCentral, characteristic)

        // Verify notifications are disabled by attempting to notify
        heartRateService.notifyHeartRate(75)

        // Should NOT notify because disabled
        verify(exactly = 0) {
            peripheralManager.notifyCharacteristicChanged(any(), any())
        }
    }

    @Test
    fun `onNotifyingDisabled ignores incorrect characteristic`() {
        // Enable for correct characteristic
        val correctCharacteristic = mockk<BluetoothGattCharacteristic> {
            every { uuid } returns HeartRateService.HEARTRATE_MEASUREMENT_CHARACTERISTIC_UUID
        }
        heartRateService.onNotifyingEnabled(mockCentral, correctCharacteristic)

        // Try to disable with wrong characteristic
        val wrongCharacteristic = mockk<BluetoothGattCharacteristic> {
            every { uuid } returns UUID.randomUUID()
        }
        heartRateService.onNotifyingDisabled(mockCentral, wrongCharacteristic)

        // Notifications should still be enabled
        heartRateService.notifyHeartRate(75)

        // Should notify because still enabled
        verify(exactly = 1) {
            peripheralManager.notifyCharacteristicChanged(any(), any())
        }
    }

    @Test
    fun `notifyHeartRate sends notification when enabled`() {
        // Enable notifications
        val characteristic = mockk<BluetoothGattCharacteristic> {
            every { uuid } returns HeartRateService.HEARTRATE_MEASUREMENT_CHARACTERISTIC_UUID
        }
        heartRateService.onNotifyingEnabled(mockCentral, characteristic)

        // Notify with BPM value
        heartRateService.notifyHeartRate(75)

        // Verify notification was sent with correct data
        val expectedValue = byteArrayOf(0x00, 75.toByte())
        verify {
            peripheralManager.notifyCharacteristicChanged(
                match { it.contentEquals(expectedValue) },
                any()
            )
        }
    }

    @Test
    fun `notifyHeartRate does not send notification when disabled`() {
        // Don't enable notifications
        heartRateService.notifyHeartRate(75)

        // Verify notification was NOT sent
        verify(exactly = 0) {
            peripheralManager.notifyCharacteristicChanged(any(), any())
        }
    }

    @Test
    fun `notifyHeartRate sends multiple notifications with different BPM values`() {
        // Enable notifications
        val characteristic = mockk<BluetoothGattCharacteristic> {
            every { uuid } returns HeartRateService.HEARTRATE_MEASUREMENT_CHARACTERISTIC_UUID
        }
        heartRateService.onNotifyingEnabled(mockCentral, characteristic)

        // Send multiple notifications
        heartRateService.notifyHeartRate(60)
        heartRateService.notifyHeartRate(75)
        heartRateService.notifyHeartRate(90)

        // Verify all were sent
        verify(exactly = 3) {
            peripheralManager.notifyCharacteristicChanged(any(), any())
        }

        // Verify correct values
        verify {
            peripheralManager.notifyCharacteristicChanged(
                match { it.contentEquals(byteArrayOf(0x00, 60.toByte())) },
                any()
            )
        }
        verify {
            peripheralManager.notifyCharacteristicChanged(
                match { it.contentEquals(byteArrayOf(0x00, 75.toByte())) },
                any()
            )
        }
        verify {
            peripheralManager.notifyCharacteristicChanged(
                match { it.contentEquals(byteArrayOf(0x00, 90.toByte())) },
                any()
            )
        }
    }

    @Test
    fun `notifyHeartRate formats BPM correctly in byte array`() {
        val characteristic = mockk<BluetoothGattCharacteristic> {
            every { uuid } returns HeartRateService.HEARTRATE_MEASUREMENT_CHARACTERISTIC_UUID
        }
        heartRateService.onNotifyingEnabled(mockCentral, characteristic)

        // Test edge cases
        heartRateService.notifyHeartRate(0)
        verify {
            peripheralManager.notifyCharacteristicChanged(
                match { it.contentEquals(byteArrayOf(0x00, 0.toByte())) },
                any()
            )
        }

        heartRateService.notifyHeartRate(255)
        verify {
            peripheralManager.notifyCharacteristicChanged(
                match { it.contentEquals(byteArrayOf(0x00, 255.toByte())) },
                any()
            )
        }
    }

    @Test
    fun `onCentralDisconnected disables notifications when no centrals connected`() {
        // Setup: one central connected with notifications enabled
        every { peripheralManager.connectedCentrals } returns setOf(mockCentral)

        val characteristic = mockk<BluetoothGattCharacteristic> {
            every { uuid } returns HeartRateService.HEARTRATE_MEASUREMENT_CHARACTERISTIC_UUID
        }
        heartRateService.onNotifyingEnabled(mockCentral, characteristic)

        // Central disconnects
        every { peripheralManager.connectedCentrals } returns emptySet()
        heartRateService.onCentralDisconnected(mockCentral)

        // Try to notify - should not send
        heartRateService.notifyHeartRate(75)

        verify(exactly = 0) {
            peripheralManager.notifyCharacteristicChanged(any(), any())
        }
    }

    @Test
    fun `onCentralDisconnected keeps notifications enabled when other centrals connected`() {
        val mockCentral1 = mockk<BluetoothCentral>(relaxed = true)
        val mockCentral2 = mockk<BluetoothCentral>(relaxed = true)

        // Setup: two centrals connected
        every { peripheralManager.connectedCentrals } returns setOf(mockCentral1, mockCentral2)

        val characteristic = mockk<BluetoothGattCharacteristic> {
            every { uuid } returns HeartRateService.HEARTRATE_MEASUREMENT_CHARACTERISTIC_UUID
        }
        heartRateService.onNotifyingEnabled(mockCentral1, characteristic)

        // One central disconnects, but another remains
        every { peripheralManager.connectedCentrals } returns setOf(mockCentral2)
        heartRateService.onCentralDisconnected(mockCentral1)

        // Notifications should still work
        heartRateService.notifyHeartRate(75)

        verify(exactly = 1) {
            peripheralManager.notifyCharacteristicChanged(any(), any())
        }
    }

    @Test
    fun `service name is correct`() {
        assertEquals("HeartRate Service", heartRateService.serviceName)
    }
}
