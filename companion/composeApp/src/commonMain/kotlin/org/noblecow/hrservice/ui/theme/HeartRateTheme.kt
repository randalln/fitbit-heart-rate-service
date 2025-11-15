package org.noblecow.hrservice.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    onPrimary = OnPurple80,
    primaryContainer = PurpleContainer80,
    onPrimaryContainer = OnPurple80,
    secondary = PurpleGrey80,
    onSecondary = OnPurpleGrey80,
    secondaryContainer = PurpleGreyContainer80,
    onSecondaryContainer = OnPurpleGrey80,
    tertiary = Pink80,
    onTertiary = OnPink80,
    tertiaryContainer = PinkContainer80,
    onTertiaryContainer = OnPink80,
    error = Error80,
    onError = OnError80,
    errorContainer = ErrorContainer80,
    onErrorContainer = OnErrorContainer80,
    background = Surface80,
    onBackground = OnSurface80,
    surface = Surface80,
    onSurface = OnSurface80,
    surfaceVariant = SurfaceVariant80,
    onSurfaceVariant = OnSurfaceVariant80,
    outline = Outline80,
    outlineVariant = OutlineVariant80,
    inverseSurface = InverseSurface80,
    inverseOnSurface = InverseOnSurface80,
    inversePrimary = InversePrimary80,
    scrim = Scrim
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    onPrimary = OnPurple40,
    primaryContainer = PurpleContainer40,
    onPrimaryContainer = OnPurple40,
    secondary = PurpleGrey40,
    onSecondary = OnPurpleGrey40,
    secondaryContainer = PurpleGreyContainer40,
    onSecondaryContainer = OnPurpleGrey40,
    tertiary = Pink40,
    onTertiary = OnPink40,
    tertiaryContainer = PinkContainer40,
    onTertiaryContainer = OnPink40,
    error = Error40,
    onError = OnError40,
    errorContainer = ErrorContainer40,
    onErrorContainer = OnErrorContainer40,
    background = Surface40,
    onBackground = OnSurface40,
    surface = Surface40,
    onSurface = OnSurface40,
    surfaceVariant = SurfaceVariant40,
    onSurfaceVariant = OnSurfaceVariant40,
    outline = Outline40,
    outlineVariant = OutlineVariant40,
    inverseSurface = InverseSurface40,
    inverseOnSurface = InverseOnSurface40,
    inversePrimary = InversePrimary40,
    scrim = Scrim
)

/**
 * Heart Rate Monitor theme wrapper that provides Material Design 3 theming.
 *
 * Automatically adapts to the system's dark/light theme preference using
 * [isSystemInDarkTheme]. Applies consistent color schemes and typography
 * throughout the application.
 *
 * @param darkTheme Whether to use dark theme. Defaults to system preference.
 * @param content The composable content to be themed.
 *
 * @see DarkColorScheme
 * @see LightColorScheme
 */
@Composable
fun HeartRateTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content
    )
}
