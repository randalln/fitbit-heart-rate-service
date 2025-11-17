package org.noblecow.hrservice.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * iOS system color equivalents for light mode.
 *
 * These colors approximate iOS system colors to provide a more
 * native feel on iOS while maintaining Material3 ColorScheme structure.
 *
 * Based on iOS Human Interface Guidelines:
 * - Blue is the primary iOS accent color
 * - Gray scales are system grays
 * - Colors adapt between light and dark mode
 */

// iOS System Blue (primary accent color)
private val IosBlueLight = Color(0xFF007AFF)
private val IosBlueDark = Color(0xFF0A84FF)

// iOS System Grays
private val IosGray = Color(0xFF8E8E93)
private val IosGray2Light = Color(0xFFAEAEB2)
private val IosGray2Dark = Color(0xFF636366)
private val IosGray3Light = Color(0xFFC7C7CC)
private val IosGray3Dark = Color(0xFF48484A)
private val IosGray4Light = Color(0xFFD1D1D6)
private val IosGray4Dark = Color(0xFF3A3A3C)
private val IosGray5Light = Color(0xFFE5E5EA)
private val IosGray5Dark = Color(0xFF2C2C2E)
private val IosGray6Light = Color(0xFFF2F2F7)
private val IosGray6Dark = Color(0xFF1C1C1E)

// iOS System Red (for errors)
private val IosRedLight = Color(0xFFFF3B30)
private val IosRedDark = Color(0xFFFF453A)

// iOS System Green (for success)
private val IosGreenLight = Color(0xFF34C759)
private val IosGreenDark = Color(0xFF32D74B)

// iOS Background colors
private val IosBackgroundLight = Color(0xFFFFFFFF)
private val IosBackgroundDark = Color(0xFF000000)
private val IosSecondaryBackgroundLight = Color(0xFFF2F2F7)
private val IosSecondaryBackgroundDark = Color(0xFF1C1C1E)

// iOS Label colors (text)
private val IosLabelLight = Color(0xFF000000)
private val IosLabelDark = Color(0xFFFFFFFF)
private val IosSecondaryLabelLight = Color(0x993C3C43) // 60% opacity
private val IosSecondaryLabelDark = Color(0x99EBEBF5) // 60% opacity

/**
 * iOS-style light color scheme.
 *
 * Maps Material3 semantic colors to iOS system colors for a native iOS feel.
 */
internal val IosLightColorScheme = lightColorScheme(
    // Primary colors - iOS Blue
    primary = IosBlueLight,
    onPrimary = Color.White,
    primaryContainer = IosBlueLight.copy(alpha = 0.2f),
    onPrimaryContainer = IosBlueLight,

    // Secondary colors - iOS Gray
    secondary = IosGray,
    onSecondary = Color.White,
    secondaryContainer = IosGray5Light,
    onSecondaryContainer = IosGray,

    // Tertiary colors
    tertiary = IosGray2Light,
    onTertiary = Color.White,
    tertiaryContainer = IosGray6Light,
    onTertiaryContainer = IosGray2Light,

    // Error colors - iOS Red
    error = IosRedLight,
    onError = Color.White,
    errorContainer = IosRedLight.copy(alpha = 0.1f),
    onErrorContainer = IosRedLight,

    // Background colors
    background = IosBackgroundLight,
    onBackground = IosLabelLight,

    // Surface colors
    surface = IosBackgroundLight,
    onSurface = IosLabelLight,
    surfaceVariant = IosSecondaryBackgroundLight,
    onSurfaceVariant = IosSecondaryLabelLight,

    // Outline colors
    outline = IosGray4Light,
    outlineVariant = IosGray5Light,

    // Scrim
    scrim = Color.Black.copy(alpha = 0.32f),

    // Inverse colors
    inverseSurface = IosGray6Dark,
    inverseOnSurface = IosLabelDark,
    inversePrimary = IosBlueDark
)

/**
 * iOS-style dark color scheme.
 *
 * Maps Material3 semantic colors to iOS dark mode system colors.
 */
internal val IosDarkColorScheme = darkColorScheme(
    // Primary colors - iOS Blue (dark mode)
    primary = IosBlueDark,
    onPrimary = Color.White,
    primaryContainer = IosBlueDark.copy(alpha = 0.3f),
    onPrimaryContainer = IosBlueDark,

    // Secondary colors - iOS Gray (dark mode)
    secondary = IosGray,
    onSecondary = Color.White,
    secondaryContainer = IosGray5Dark,
    onSecondaryContainer = IosGray2Dark,

    // Tertiary colors
    tertiary = IosGray2Dark,
    onTertiary = Color.White,
    tertiaryContainer = IosGray6Dark,
    onTertiaryContainer = IosGray2Dark,

    // Error colors - iOS Red (dark mode)
    error = IosRedDark,
    onError = Color.White,
    errorContainer = IosRedDark.copy(alpha = 0.2f),
    onErrorContainer = IosRedDark,

    // Background colors
    background = IosBackgroundDark,
    onBackground = IosLabelDark,

    // Surface colors
    surface = IosBackgroundDark,
    onSurface = IosLabelDark,
    surfaceVariant = IosSecondaryBackgroundDark,
    onSurfaceVariant = IosSecondaryLabelDark,

    // Outline colors
    outline = IosGray4Dark,
    outlineVariant = IosGray5Dark,

    // Scrim
    scrim = Color.Black.copy(alpha = 0.5f),

    // Inverse colors
    inverseSurface = IosGray6Light,
    inverseOnSurface = IosLabelLight,
    inversePrimary = IosBlueLight
)
