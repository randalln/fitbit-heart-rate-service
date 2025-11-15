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
 * Top app bar for non-home screens with back navigation support.
 *
 * Displays the current screen title and a back arrow button (if navigation
 * is available) that allows users to navigate up in the navigation hierarchy.
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
fun NonHomeAppBar(
    scrollBehavior: TopAppBarScrollBehavior,
    currentScreen: HeartRateScreen,
    canNavigateBack: Boolean,
    navController: NavHostController,
    modifier: Modifier = Modifier
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
