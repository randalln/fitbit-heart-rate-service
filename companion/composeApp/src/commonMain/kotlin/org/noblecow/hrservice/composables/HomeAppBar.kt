@file:OptIn(ExperimentalMaterial3Api::class)

package org.noblecow.hrservice.composables

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation.NavHostController
import heartratemonitor.composeapp.generated.resources.Res
import heartratemonitor.composeapp.generated.resources.menu
import heartratemonitor.composeapp.generated.resources.open_source
import heartratemonitor.composeapp.generated.resources.toggle_fake_bpm
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun HomeAppBar(
    scrollBehavior: TopAppBarScrollBehavior,
    currentScreen: HeartRateScreen,
    navController: NavHostController,
    onFakeBpmClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }

    CenterAlignedTopAppBar(
        title = {
            Text(
                stringResource(currentScreen.titleRes),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        actions = {
            IconButton(onClick = { menuExpanded = !menuExpanded }) {
                Icon(
                    imageVector = Icons.Outlined.MoreVert,
                    contentDescription = stringResource(Res.string.menu)
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                DropdownMenuItem(
                    onClick = {
                        navController.navigate(HeartRateScreen.OpenSource.name)
                        menuExpanded = false
                    },
                    text = {
                        Text(stringResource(Res.string.open_source))
                    }
                )
                DropdownMenuItem(
                    onClick = {
                        onFakeBpmClick()
                        menuExpanded = false
                    },
                    text = {
                        Text(stringResource(Res.string.toggle_fake_bpm))
                    }
                )
            }
        },
        scrollBehavior = scrollBehavior,
        modifier = modifier
    )
}
