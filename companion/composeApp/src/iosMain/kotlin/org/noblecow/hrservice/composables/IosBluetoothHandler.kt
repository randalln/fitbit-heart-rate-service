package org.noblecow.hrservice.composables

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import co.touchlab.kermit.Logger

/**
 * iOS-specific Bluetooth handler composable.
 *
 * Unlike Android which shows a system dialog to enable Bluetooth, iOS handles
 * Bluetooth differently:
 * - Bluetooth cannot be programmatically enabled/disabled
 * - Apps can only check Bluetooth authorization status
 * - Users must enable Bluetooth via Settings app
 *
 * This mock implementation immediately calls onEnable() to indicate Bluetooth
 * is ready, allowing the app flow to continue.
 *
 * In a real implementation, this would:
 * - Check CBCentralManager.authorization status
 * - Display an alert if Bluetooth is off, directing users to Settings
 * - Monitor CBCentralManager state changes
 *
 * @param bluetoothRequested Whether Bluetooth enable has been requested
 * @param logger Logger for debugging
 * @param onEnable Callback invoked when Bluetooth is ready (or assumed ready)
 * @param onDecline Callback invoked if user declines (not used in iOS mock)
 */
@Composable
internal fun IosBluetoothHandler(
    bluetoothRequested: Boolean?,
    logger: Logger,
    onEnable: () -> Unit,
    onDecline: () -> Unit
) {
    val currentOnEnable by rememberUpdatedState(onEnable)

    if (bluetoothRequested == true) {
        LaunchedEffect(true) {
            logger.d("IosBluetoothHandler: Mock - Bluetooth assumed enabled")
            // iOS doesn't have an enable Bluetooth dialog
            // In a real app, we'd check CBCentralManager.authorization
            // For mock, immediately proceed
            currentOnEnable()
        }
    }
}
