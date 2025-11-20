package org.noblecow.hrservice.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/**
 * Heart Rate Monitor theme wrapper that provides platform-aware Material Design 3 theming.
 *
 * Automatically adapts to:
 * - System's dark/light theme preference using [isSystemInDarkTheme]
 * - Platform-specific colors (Material on Android, iOS system colors on iOS)
 * - Platform-specific shapes (Material corner radii on Android, iOS corner radii on iOS)
 *
 * @param darkTheme Whether to use dark theme. Defaults to system preference.
 * @param content The composable content to be themed.
 *
 * @see platformColorScheme
 * @see platformShapes
 */
@Composable
fun HeartRateTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = platformColorScheme(darkTheme),
        typography = Typography,
        shapes = platformShapes(),
        content = content
    )
}
