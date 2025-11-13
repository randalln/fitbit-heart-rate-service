@file:OptIn(ExperimentalMaterial3Api::class)

package org.noblecow.hrservice.composables

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController

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
            canNavigateBack = canNavigateBack,
            navController = navController,
            onFakeBpmClick = onFakeBpmClick,
            modifier = modifier
        )
    }
}
