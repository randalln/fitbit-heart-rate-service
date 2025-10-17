package org.noblecow.hrservice.composables

import androidx.annotation.StringRes
import org.noblecow.hrservice.R

enum class HeartRateScreen(
    @param:StringRes val title: Int
) {
    Home(title = R.string.app_name),
    OpenSource(title = R.string.open_source)
}
