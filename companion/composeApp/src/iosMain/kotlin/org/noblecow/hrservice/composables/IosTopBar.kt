@file:OptIn(ExperimentalMaterial3Api::class)

package org.noblecow.hrservice.composables

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation.NavHostController
import heartratemonitor.composeapp.generated.resources.Res
import heartratemonitor.composeapp.generated.resources.back_button
import org.jetbrains.compose.resources.stringResource
import org.noblecow.hrservice.ui.theme.Tokens

/**
 * iOS-style back chevron icon (text-based).
 *
 * Uses a simple chevron character instead of Material icons for iOS native feel.
 */
@Composable
private fun IosBackChevron(modifier: Modifier = Modifier) {
    Text(
        text = "‹",
        style = MaterialTheme.typography.headlineLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Normal,
        modifier = modifier
    )
}

/**
 * iOS-style top navigation bar for non-home screens.
 *
 * Follows Apple Human Interface Guidelines:
 * - Left-aligned title (not centered)
 * - iOS-style back chevron `<` instead of Material arrow
 * - No elevation/shadow
 * - Standard iOS navigation bar height (44pt)
 * - iOS typography and spacing
 *
 * @param scrollBehavior Defines the scroll behavior for the app bar.
 * @param currentScreen The current screen being displayed.
 * @param canNavigateBack Whether to show the back button.
 * @param navController Navigation controller used to navigate back.
 * @param modifier Optional modifier for this composable.
 */
@Composable
internal fun IosNonHomeTopBar(
    scrollBehavior: TopAppBarScrollBehavior,
    currentScreen: HeartRateScreen,
    canNavigateBack: Boolean,
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    TopAppBar(
        title = {
            Text(
                text = stringResource(currentScreen.titleRes),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        scrollBehavior = scrollBehavior,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surface,
            navigationIconContentColor = MaterialTheme.colorScheme.primary,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            actionIconContentColor = MaterialTheme.colorScheme.primary
        ),
        navigationIcon = {
            if (canNavigateBack) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { navController.navigateUp() }) {
                        IosBackChevron()
                    }
                }
            }
        },
        modifier = modifier
    )
}

/**
 * iOS-style top navigation bar for the home screen.
 *
 * Follows Apple Human Interface Guidelines:
 * - Large title centered (iOS home screen pattern)
 * - No elevation/shadow
 * - Action buttons on the right
 * - iOS typography and spacing
 *
 * @param scrollBehavior Defines the scroll behavior for the app bar.
 * @param currentScreen The current screen being displayed.
 * @param navController Navigation controller for screen navigation.
 * @param onMenuClick Callback when the menu button is clicked.
 * @param modifier Optional modifier for this composable.
 */
@Composable
internal fun IosHomeTopBar(
    scrollBehavior: TopAppBarScrollBehavior,
    currentScreen: HeartRateScreen,
    navController: NavHostController,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    TopAppBar(
        title = {
            Text(
                text = stringResource(currentScreen.titleRes),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        scrollBehavior = scrollBehavior,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            actionIconContentColor = MaterialTheme.colorScheme.primary
        ),
        actions = {
            // Menu button for iOS - will trigger action sheet
            IconButton(onClick = onMenuClick) {
                Text(
                    text = "⋯",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        modifier = modifier
    )
}
