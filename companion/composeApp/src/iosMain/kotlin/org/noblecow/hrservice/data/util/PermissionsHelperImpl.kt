package org.noblecow.hrservice.data.util

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Mock implementation of PermissionsHelper for iOS.
 *
 * iOS handles permissions differently from Android. For the mock implementation,
 * we assume all permissions are granted.
 *
 * In a real implementation, this would check iOS notification permissions using
 * UNUserNotificationCenter.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
internal class PermissionsHelperImpl : PermissionsHelper {
    /**
     * Returns an empty array, indicating no missing permissions.
     * For iOS mock, we assume notifications are always enabled.
     */
    override fun getMissingNotificationsPermissions(): Array<String> = emptyArray()
}
