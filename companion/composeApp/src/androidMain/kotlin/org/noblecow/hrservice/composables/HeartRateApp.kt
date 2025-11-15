@file:OptIn(ExperimentalMaterial3Api::class)

package org.noblecow.hrservice.composables

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import co.touchlab.kermit.Logger
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import com.mikepenz.aboutlibraries.ui.compose.produceLibraries
import heartratemonitor.composeapp.generated.resources.Res
import kotlinx.coroutines.delay
import org.noblecow.hrservice.data.repository.ServicesState
import org.noblecow.hrservice.data.util.ANIMATION_MS
import org.noblecow.hrservice.viewmodel.MainViewModel
import org.noblecow.hrservice.viewmodel.metroViewModel

private const val TAG = "HeartRateApp"

@Composable
@Suppress("LongMethod")
internal fun HeartRateApp(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
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

    // Get current back stack entry
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentScreen = HeartRateScreen.entries.find {
        it.name == backStackEntry?.destination?.route
    } ?: HeartRateScreen.Home
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(
            rememberTopAppBarState()
        )
        val snackbarHostState = remember { SnackbarHostState() }
        var animationEnd by rememberSaveable { mutableStateOf(false) }

        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                HeartRateAppBar(
                    scrollBehavior = scrollBehavior,
                    currentScreen = currentScreen,
                    canNavigateBack = navController.previousBackStackEntry != null,
                    navController = navController,
                    onFakeBpmClick = { viewModel.toggleFakeBPM() }
                )
            },
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState)
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = HeartRateScreen.Home.name,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                composable(route = HeartRateScreen.Home.name) {
                    LaunchedEffect(uiState.bpmCount) {
                        if (uiState.bpmCount != 0) {
                            animationEnd = false
                            delay(ANIMATION_MS)
                            animationEnd = true
                        }
                    }

                    HomeScreen(
                        onStartClick = { viewModel.start() },
                        onStopClick = { viewModel.stop() },
                        showAwaitingClient = uiState.servicesState == ServicesState.Started &&
                            !uiState.isClientConnected,
                        bpm = uiState.bpm,
                        isHeartBeatPulse = animationEnd,
                        showStart = uiState.servicesState == ServicesState.Stopped
                    )
                }
                composable(route = HeartRateScreen.OpenSource.name) {
                    val libraries by produceLibraries {
                        runCatching {
                            Res.readBytes("files/aboutlibraries.json").decodeToString()
                        }.getOrElse {
                            logger.e("Failed to load libraries", it)
                            "{}" // Empty JSON fallback
                        }
                    }
                    LibrariesContainer(libraries, Modifier.fillMaxSize())
                }
            }

            LaunchedEffect(uiState.userMessage) {
                uiState.userMessage?.let { message ->
                    snackbarHostState.showSnackbar(message = message)
                    viewModel.userMessageShown()
                }
            }
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
