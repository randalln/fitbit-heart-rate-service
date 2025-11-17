@file:OptIn(ExperimentalMaterial3Api::class)

package org.noblecow.hrservice.composables

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController

/**
 * iOS implementation of non-home app bar with native iOS styling.
 *
 * Uses iOS-style navigation bar with:
 * - Left-aligned title (iOS convention)
 * - iOS-style back chevron (‹) instead of Material arrow
 * - SemiBold font weight for the title
 *
 * Follows Apple Human Interface Guidelines:
 * - No elevation/shadow on app bar
 * - iOS-appropriate typography and spacing
 * - Back chevron instead of arrow icon
 *
 * @param scrollBehavior Defines the scroll behavior for the app bar (e.g., pinned, collapsing).
 * @param currentScreen The current screen being displayed, used to show the appropriate title.
 * @param canNavigateBack Whether to show the back button. Only shown if there's a previous screen in the back stack.
 * @param navController Navigation controller used to navigate back when the back button is pressed.
 * @param modifier Optional modifier for this composable.
 *
 * @see HeartRateAppBar
 */
@Composable
actual fun NonHomeAppBar(
    scrollBehavior: TopAppBarScrollBehavior,
    currentScreen: HeartRateScreen,
    canNavigateBack: Boolean,
    navController: NavHostController,
    modifier: Modifier
) {
    IosNonHomeTopBar(
        scrollBehavior = scrollBehavior,
        currentScreen = currentScreen,
        canNavigateBack = canNavigateBack,
        navController = navController,
        modifier = modifier
    )
}
