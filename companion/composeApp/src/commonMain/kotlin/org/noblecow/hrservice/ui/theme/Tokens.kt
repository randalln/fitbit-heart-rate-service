package org.noblecow.hrservice.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Design tokens for the Heart Rate Monitor application.
 *
 * Centralizes spacing, sizing, typography scale, and custom color values
 * to ensure consistency across the UI and simplify theme customization.
 *
 * ## Color Usage Guidelines
 *
 * **Prefer [MaterialTheme.colorScheme] for semantic colors:**
 * - Text colors: Use `onSurface`, `onBackground`, `onPrimary`
 * - Backgrounds: Use `surface`, `background`, `primary`
 * - Interactive elements: Use `primary`, `secondary`, `tertiary`
 *
 * **Only use [Colors] for:**
 * - Brand-specific colors not in Material theme
 * - Custom accent colors unique to your app
 * - Special state colors (success, warning, info)
 */
object Tokens {
    /**
     * Custom colors for brand-specific or special use cases.
     *
     * Note: Most UI should use [MaterialTheme.colorScheme] instead.
     * Only define colors here if they don't fit the semantic color system.
     */
    object Colors {
        // Example custom colors (currently unused - prefer MaterialTheme.colorScheme)
        // val BrandRed: Color = Color(0xFFE53935)
        // val SuccessGreen: Color = Color(0xFF43A047)
        // val WarningOrange: Color = Color(0xFFFF9800)
        val BPMText: Color = Color.White
    }

    /**
     * Spacing values for padding, margins, and gaps.
     */
    object Spacing {
        /** Extra small spacing for tight layouts */
        val ExtraSmall: Dp = 4.dp

        /** Small spacing for compact elements */
        val Small: Dp = 8.dp

        /** Medium spacing for standard layouts */
        val Medium: Dp = 16.dp

        /** Large spacing for generous breathing room */
        val Large: Dp = 24.dp

        /** Extra large spacing for major sections */
        val ExtraLarge: Dp = 32.dp
    }

    /**
     * Size values for components and containers.
     */
    object Size {
        /** Size of the heart icon container on the home screen */
        val HeartContainer: Dp = 300.dp
    }
}
