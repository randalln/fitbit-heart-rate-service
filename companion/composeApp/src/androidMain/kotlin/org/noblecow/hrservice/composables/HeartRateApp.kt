package org.noblecow.hrservice.composables

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import co.touchlab.kermit.Logger
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.metroViewModel
import org.noblecow.hrservice.viewmodel.MainViewModel

private const val TAG = "HeartRateApp"

/**
 * Main entry point for the Android Heart Rate Monitor application.
 *
 * Provides platform-specific permission and Bluetooth handling for Android,
 * then delegates to the common [HeartRateContent] composable for UI rendering.
 *
 * @param modifier Optional modifier for this composable
 * @param navController Navigation controller for screen transitions
 */
@Composable
internal fun HeartRateApp(
    viewModelFactory: MetroViewModelFactory,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    CompositionLocalProvider(LocalMetroViewModelFactory provides viewModelFactory) {
        val viewModel = metroViewModel<MainViewModel>()
        val logger = Logger.withTag(TAG)
        val uiState by viewModel.mainUiState.collectAsState()

        PermissionHandler(
            permissionsRequested = uiState.permissionsRequested,
            onPermissionsResult = { permissions ->
                viewModel.receivePermissions(permissions)
            },
            logger = logger
        )

        BluetoothHandler(
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
}

@Composable
private fun BluetoothHandler(
    bluetoothRequested: Boolean?,
    logger: Logger,
    onEnable: () -> Unit,
    onDecline: () -> Unit
) {
    val enableBluetoothLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode != Activity.RESULT_OK) {
            onDecline()
        } else {
            onEnable()
        }
    }

    if (bluetoothRequested == true) {
        LaunchedEffect(true) {
            logger.d("Need to enable bluetooth")
            val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBluetoothLauncher.launch(intent)
        }
    }
}

@Composable
private fun PermissionHandler(
    permissionsRequested: List<String>?,
    onPermissionsResult: (Map<String, Boolean>) -> Unit,
    logger: Logger
) {
    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        onPermissionsResult(permissions)
    }

    permissionsRequested?.let { permissions ->
        LaunchedEffect(permissions) {
            logger.d("Requesting permissions: $permissions")
            permissionsLauncher.launch(permissions.toTypedArray())
        }
    }
}
