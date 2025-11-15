package org.noblecow.hrservice.composables

import heartratemonitor.composeapp.generated.resources.Res
import heartratemonitor.composeapp.generated.resources.app_name
import heartratemonitor.composeapp.generated.resources.open_source
import org.jetbrains.compose.resources.StringResource

/**
 * Defines the available screens in the Heart Rate Monitor application.
 *
 * This enum is used for type-safe navigation and provides localized titles
 * for the app bar. The enum name (e.g., "Home") is used as the route identifier
 * in the NavHost.
 *
 * @property titleRes The string resource for the screen's title in the app bar
 */
enum class HeartRateScreen(val titleRes: StringResource) {
    /** Main screen displaying heart rate monitoring controls and current BPM */
    Home(titleRes = Res.string.app_name),

    /** Screen displaying open source licenses for third-party libraries */
    OpenSource(titleRes = Res.string.open_source)
}
