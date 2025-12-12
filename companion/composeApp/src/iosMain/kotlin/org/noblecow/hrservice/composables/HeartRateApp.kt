package org.noblecow.hrservice.composables

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import co.touchlab.kermit.Logger
import dev.zacsweers.metrox.viewmodel.metroViewModel
import org.noblecow.hrservice.viewmodel.MainViewModel

private const val TAG = "HeartRateApp"

/**
 * Main entry point for the iOS Heart Rate Monitor application.
 *
 * Provides iOS-specific permission and Bluetooth handling, then delegates to
 * the common [HeartRateContent] composable for UI rendering.
 *
 * Unlike Android which uses activity result launchers, iOS uses platform-specific
 * handlers that work with iOS's permission and Bluetooth APIs.
 *
 * @param modifier Optional modifier for this composable
 * @param navController Navigation controller for screen transitions
 */
@Composable
internal fun HeartRateApp(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = metroViewModel(),
    navController: NavHostController = rememberNavController()
) {
    val logger = Logger.withTag(TAG)
    val uiState by viewModel.mainUiState.collectAsState()

    IosPermissionHandler(
        permissionsRequested = uiState.permissionsRequested,
        onPermissionsResult = { permissions ->
            viewModel.receivePermissions(permissions)
        }
    )

    IosBluetoothHandler(
        bluetoothRequested = uiState.bluetoothRequested,
        logger = logger,
        onEnable = { viewModel.start() }
    ) {
        viewModel.userDeclinedBluetoothEnable()
    }

    val currentScreen = getCurrentScreen(navController)

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        HeartRateContent(
            navController = navController,
            currentScreen = currentScreen,
            uiState = uiState,
            onStartClick = { viewModel.start() },
            onStopClick = { viewModel.stop() },
            onFakeBpmClick = { viewModel.toggleFakeBPM() },
            onUserMessageShow = { viewModel.userMessageShown() },
            logger = logger
        )
    }
}
