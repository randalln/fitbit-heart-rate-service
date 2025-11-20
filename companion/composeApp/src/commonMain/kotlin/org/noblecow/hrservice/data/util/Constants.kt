package org.noblecow.hrservice.data.util

internal const val ANIMATION_MS = 250L
internal const val DEFAULT_BPM = 0
internal const val FAKE_BPM_END = 110
internal const val FAKE_BPM_INTERVAL_MS = 1000L
internal const val FAKE_BPM_START = 90
internal const val MAX_BPM = 255 // Determine how arbitrary this is
internal const val PORT_LISTEN = 12345

// Bluetooth Heart Rate Service constants
// Standard Bluetooth Heart Rate Service UUID
// See: https://www.bluetooth.com/specifications/specs/heart-rate-service-1-0/
internal const val HRS_SERVICE_UUID_VAL = "0000180D-0000-1000-8000-00805f9b34fb"

// Heart Rate Measurement characteristic UUID
// See: https://www.bluetooth.com/specifications/specs/heart-rate-measurement-characteristic/
internal const val HR_MEASUREMENT_CHAR_UUID_VAL = "00002A37-0000-1000-8000-00805f9b34fb"
