package org.noblecow.hrservice.data

import com.welie.blessed.AdvertiseError

sealed class AdvertisingState {
    data object Started : AdvertisingState()
    data object Stopped : AdvertisingState()
    data class Failure(val error: AdvertiseError) : AdvertisingState()
}
