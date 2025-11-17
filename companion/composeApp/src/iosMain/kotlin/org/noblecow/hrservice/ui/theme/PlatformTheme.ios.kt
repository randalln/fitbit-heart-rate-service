package org.noblecow.hrservice.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes

/**
 * iOS-style shapes using Apple Human Interface Guidelines corner radii.
 *
 * - extraSmall (4pt): Minimal rounding for compact elements
 * - small (8pt): Small controls and compact buttons
 * - medium (10pt): Standard iOS buttons and cards
 * - large (13pt): Prominent components and large buttons
 * - extraLarge (16pt): Sheets, modals, and large containers
 */
private val IosShapes = Shapes(
    extraSmall = RoundedCornerShape(Tokens.IosTokens.CornerRadius.Small / 2),
    small = RoundedCornerShape(Tokens.IosTokens.CornerRadius.Small),
    medium = RoundedCornerShape(Tokens.IosTokens.CornerRadius.Standard),
    large = RoundedCornerShape(Tokens.IosTokens.CornerRadius.Prominent),
    extraLarge = RoundedCornerShape(Tokens.IosTokens.CornerRadius.Large)
)

/**
 * Returns iOS system color scheme.
 */
actual fun platformColorScheme(darkTheme: Boolean): ColorScheme =
    if (darkTheme) IosDarkColorScheme else IosLightColorScheme

/**
 * Returns iOS-style shapes.
 */
actual fun platformShapes(): Shapes = IosShapes
