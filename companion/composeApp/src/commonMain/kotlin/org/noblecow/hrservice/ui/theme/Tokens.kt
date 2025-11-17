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

    /**
     * iOS-specific design tokens following Apple Human Interface Guidelines.
     *
     * These values align with iOS design conventions:
     * - Spacing based on iOS 4pt/8pt grid system
     * - Corner radius matching iOS standard values
     * - Navigation bar heights per iOS specifications
     * - Typography scale aligned with San Francisco font conventions
     */
    object IosTokens {
        /**
         * iOS spacing values (4pt base grid).
         */
        object Spacing {
            /** Extra small spacing (4pt) */
            val ExtraSmall: Dp = 4.dp

            /** Small spacing (8pt) */
            val Small: Dp = 8.dp

            /** Compact spacing (12pt) */
            val Compact: Dp = 12.dp

            /** Medium spacing (16pt) - iOS standard */
            val Medium: Dp = 16.dp

            /** Large spacing (20pt) */
            val Large: Dp = 20.dp

            /** Extra large spacing (24pt) */
            val ExtraLarge: Dp = 24.dp

            /** Section spacing (32pt) */
            val Section: Dp = 32.dp
        }

        /**
         * iOS corner radius values.
         */
        object CornerRadius {
            /** Standard corner radius for buttons and cards (10pt) */
            val Standard: Dp = 10.dp

            /** Prominent corner radius for large components (13pt) */
            val Prominent: Dp = 13.dp

            /** Small corner radius for compact elements (8pt) */
            val Small: Dp = 8.dp

            /** Large corner radius for sheets and modals (16pt) */
            val Large: Dp = 16.dp
        }

        /**
         * iOS navigation bar specifications.
         */
        object NavigationBar {
            /** Standard iOS navigation bar height (44pt) */
            val Height: Dp = 44.dp

            /** Large title navigation bar height (96pt) */
            val LargeTitleHeight: Dp = 96.dp

            /** Horizontal content padding (16pt) */
            val HorizontalPadding: Dp = 16.dp
        }

        /**
         * iOS typography scale adjustments.
         */
        object Typography {
            /** Large title size (34pt) */
            val LargeTitleSize: TextUnit = 34.sp

            /** Title 1 size (28pt) */
            val Title1Size: TextUnit = 28.sp

            /** Title 2 size (22pt) */
            val Title2Size: TextUnit = 22.sp

            /** Title 3 size (20pt) */
            val Title3Size: TextUnit = 20.sp

            /** Headline size (17pt semi-bold) */
            val HeadlineSize: TextUnit = 17.sp

            /** Body size (17pt regular) */
            val BodySize: TextUnit = 17.sp

            /** Callout size (16pt) */
            val CalloutSize: TextUnit = 16.sp

            /** Subheadline size (15pt) */
            val SubheadlineSize: TextUnit = 15.sp

            /** Footnote size (13pt) */
            val FootnoteSize: TextUnit = 13.sp

            /** Caption 1 size (12pt) */
            val Caption1Size: TextUnit = 12.sp

            /** Caption 2 size (11pt) */
            val Caption2Size: TextUnit = 11.sp
        }

        /**
         * iOS animation durations (in milliseconds).
         */
        object Animation {
            /** Quick animation (0.2s) */
            const val QUICK: Long = 200L

            /** Standard animation (0.3s) */
            const val STANDARD: Long = 300L

            /** Emphasized animation (0.4s) */
            const val EMPHASIZED: Long = 400L
        }
    }
}
