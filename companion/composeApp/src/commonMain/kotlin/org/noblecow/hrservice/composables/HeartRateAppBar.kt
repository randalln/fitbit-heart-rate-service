@file:OptIn(ExperimentalMaterial3Api::class)

package org.noblecow.hrservice.composables

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController

/**
 * Top app bar that adapts based on the current navigation screen.
 *
 * Routes to either [HomeAppBar] for the home screen (with menu actions)
 * or [NonHomeAppBar] for other screens (with back navigation).
 *
 * Platform-specific implementations:
 * - Android: Material 3 TopAppBar with dropdown menu
 * - iOS: iOS-style navigation bar with action sheet menu
 *
 * @param scrollBehavior Defines the scroll behavior for the app bar (e.g., pinned, collapsing).
 * @param currentScreen The current screen being displayed, determines which app bar variant to show.
 * @param canNavigateBack Whether the back button should be shown (applies to non-home screens).
 * @param navController Navigation controller for handling screen navigation.
 * @param onFakeBpmClick Callback invoked when the "Toggle Fake BPM" menu item is selected (home screen only).
 * @param modifier Optional modifier for this composable.
 */
@Composable
fun HeartRateAppBar(
    scrollBehavior: TopAppBarScrollBehavior,
    currentScreen: HeartRateScreen,
    canNavigateBack: Boolean,
    navController: NavHostController,
    onFakeBpmClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (currentScreen != HeartRateScreen.Home) {
        NonHomeAppBar(
            scrollBehavior = scrollBehavior,
            currentScreen = currentScreen,
            canNavigateBack = canNavigateBack,
            navController = navController,
            modifier = modifier
        )
    } else {
        HomeAppBar(
            scrollBehavior = scrollBehavior,
            currentScreen = currentScreen,
            navController = navController,
            onFakeBpmClick = onFakeBpmClick,
            modifier = modifier
        )
    }
}

/**
 * Platform-specific home app bar implementation.
 *
 * - Android: Material 3 CenterAlignedTopAppBar with dropdown menu
 * - iOS: iOS-style TopAppBar with action sheet menu
 *
 * @param scrollBehavior Defines the scroll behavior for the app bar.
 * @param currentScreen The current screen being displayed.
 * @param navController Navigation controller for screen navigation.
 * @param onFakeBpmClick Callback invoked when the "Toggle Fake BPM" menu item is selected.
 * @param modifier Optional modifier for this composable.
 */
@Composable
expect fun HomeAppBar(
    scrollBehavior: TopAppBarScrollBehavior,
    currentScreen: HeartRateScreen,
    navController: NavHostController,
    onFakeBpmClick: () -> Unit,
    modifier: Modifier = Modifier
)

/**
 * Platform-specific non-home app bar implementation.
 *
 * - Android: Material 3 TopAppBar with Material back arrow
 * - iOS: iOS-style TopAppBar with iOS back chevron
 *
 * @param scrollBehavior Defines the scroll behavior for the app bar.
 * @param currentScreen The current screen being displayed.
 * @param canNavigateBack Whether the back button should be shown.
 * @param navController Navigation controller for navigation.
 * @param modifier Optional modifier for this composable.
 */
@Composable
expect fun NonHomeAppBar(
    scrollBehavior: TopAppBarScrollBehavior,
    currentScreen: HeartRateScreen,
    canNavigateBack: Boolean,
    navController: NavHostController,
    modifier: Modifier = Modifier
)
