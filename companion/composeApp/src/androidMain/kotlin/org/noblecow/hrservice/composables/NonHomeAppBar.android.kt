@file:OptIn(ExperimentalMaterial3Api::class)

package org.noblecow.hrservice.composables

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation.NavHostController
import heartratemonitor.composeapp.generated.resources.Res
import heartratemonitor.composeapp.generated.resources.back_button
import org.jetbrains.compose.resources.stringResource

/**
 * Android implementation of non-home app bar using Material Design 3.
 *
 * Displays a standard Material 3 top app bar with:
 * - Left-aligned title
 * - Material back arrow for navigation
 *
 * Uses Material 3 components:
 * - TopAppBar for the app bar
 * - Material icon for back navigation (AutoMirrored.Filled.ArrowBack)
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
    TopAppBar(
        title = {
            Text(
                stringResource(currentScreen.titleRes),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        scrollBehavior = scrollBehavior,
        navigationIcon = {
            if (canNavigateBack) {
                IconButton(onClick = { navController.navigateUp() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(Res.string.back_button)
                    )
                }
            }
        },
        modifier = modifier
    )
}
