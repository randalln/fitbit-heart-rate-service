@file:OptIn(ExperimentalMaterial3Api::class)

package org.noblecow.hrservice.composables

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch

/**
 * iOS implementation of home app bar with native iOS styling.
 *
 * Uses iOS-style navigation bar with:
 * - Left-aligned bold title (iOS convention)
 * - Text-based menu button (⋯) instead of Material icon
 * - Action sheet menu (ModalBottomSheet) instead of dropdown
 *
 * The action sheet includes:
 * - Navigate to Open Source licenses screen
 * - Toggle fake BPM generation
 *
 * Follows Apple Human Interface Guidelines:
 * - No elevation/shadow on app bar
 * - iOS-appropriate typography and spacing
 * - Action sheet pattern for menus (not dropdown)
 *
 * @param scrollBehavior Defines the scroll behavior for the app bar.
 * @param currentScreen The current screen being displayed.
 * @param navController Navigation controller for screen navigation.
 * @param onFakeBpmClick Callback invoked when the "Toggle Fake BPM" menu item is selected.
 * @param modifier Optional modifier for this composable.
 */
@Composable
actual fun HomeAppBar(
    scrollBehavior: TopAppBarScrollBehavior,
    currentScreen: HeartRateScreen,
    navController: NavHostController,
    onFakeBpmClick: () -> Unit,
    modifier: Modifier
) {
    var showActionSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    IosHomeTopBar(
        scrollBehavior = scrollBehavior,
        currentScreen = currentScreen,
        navController = navController,
        onMenuClick = {
            showActionSheet = true
        },
        modifier = modifier
    )

    // iOS action sheet for menu items
    IosActionMenu(
        visible = showActionSheet,
        onOpenSourceClick = {
            navController.navigate(HeartRateScreen.OpenSource.name)
        },
        onFakeBpmClick = onFakeBpmClick,
        onDismiss = {
            scope.launch {
                sheetState.hide()
                showActionSheet = false
            }
        },
        sheetState = sheetState
    )
}
