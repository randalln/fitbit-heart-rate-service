package org.noblecow.hrservice.utils

import android.content.Context
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class PermissionsHelper @Inject constructor(
    @ApplicationContext val context: Context
) {
    fun checkSelfPermission(permission: String): Int =
        ContextCompat.checkSelfPermission(context, permission)
}
