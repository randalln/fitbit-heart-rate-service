package org.noblecow.hrservice.composables

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import co.touchlab.kermit.Logger

/**
 * iOS-specific permission handler composable.
 *
 * Unlike Android, iOS doesn't use a runtime permission request launcher pattern.
 * iOS permissions are typically requested when first needed or handled at the
 * app configuration level.
 *
 * This mock implementation immediately grants all requested permissions to allow
 * the app flow to continue without interruption.
 *
 * In a real implementation, this would:
 * - Check UNUserNotificationCenter authorization for notifications
 * - Request permissions using UNUserNotificationCenter.current().requestAuthorization()
 * - Handle the authorization status callback
 *
 * @param permissionsRequested List of permission names requested (currently ignored)
 * @param onPermissionsResult Callback invoked with permission results
 */
@Composable
internal fun IosPermissionHandler(
    permissionsRequested: List<String>?,
    onPermissionsResult: (Map<String, Boolean>) -> Unit
) {
    val currentOnPermissionsResult by rememberUpdatedState(onPermissionsResult)

    permissionsRequested?.let { permissions ->
        LaunchedEffect(permissions) {
            Logger.d("IosPermissionHandler") {
                "Mock: Auto-granting permissions: $permissions"
            }

            // Create a map of all requested permissions set to true (granted)
            val grantedPermissions = permissions.associateWith { true }
            currentOnPermissionsResult(grantedPermissions)
        }
    }
}
