package org.noblecow.hrservice.data.util

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import dev.zacsweers.metro.Inject

@Inject
internal class PermissionsHelper(
    val context: Context
) {
    fun getMissingNotificationsPermissions(): Array<String> {
        var missingPermissions = emptyArray<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val notificationManager = context.getSystemService(
                Context.NOTIFICATION_SERVICE
            ) as NotificationManager
            if (!notificationManager.areNotificationsEnabled()) {
                missingPermissions = arrayOf(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        return missingPermissions
    }
}
