package org.noblecow.hrservice.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes

/**
 * Platform-specific color scheme provider.
 *
 * Returns the appropriate color scheme for the current platform:
 * - Android: Material Design 3 colors (Purple/Pink palette)
 * - iOS: iOS system colors (Blue/Gray palette)
 *
 * @param darkTheme Whether to use dark theme colors
 * @return Platform-appropriate ColorScheme
 */
expect fun platformColorScheme(darkTheme: Boolean): ColorScheme

/**
 * Platform-specific shapes provider.
 *
 * Returns the appropriate component shapes for the current platform:
 * - Android: Material Design 3 shapes (larger corner radii)
 * - iOS: iOS-style shapes (standard 10pt/13pt corner radii)
 *
 * @return Platform-appropriate Shapes
 */
expect fun platformShapes(): Shapes
