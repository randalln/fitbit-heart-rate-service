package org.noblecow.hrservice.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.unit.dp

/**
 * Android Material Design 3 dark color scheme.
 */
private val AndroidDarkColorScheme = darkColorScheme(
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

/**
 * Android Material Design 3 light color scheme.
 */
private val AndroidLightColorScheme = lightColorScheme(
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
 * Android Material Design 3 shapes (default Material3 corner radii).
 */
private val AndroidShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/**
 * Returns Android Material Design 3 color scheme.
 */
actual fun platformColorScheme(darkTheme: Boolean): ColorScheme =
    if (darkTheme) AndroidDarkColorScheme else AndroidLightColorScheme

/**
 * Returns Android Material Design 3 shapes.
 */
actual fun platformShapes(): Shapes = AndroidShapes
