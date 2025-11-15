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
import androidx.compose.runtime.rememberUpdatedState
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
private fun rememberPulseAnimation(bpmCount: Int): Boolean {
    var animationEnd by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(bpmCount) {
        if (bpmCount != 0) {
            animationEnd = false
            delay(ANIMATION_MS)
            animationEnd = true
        }
    }
    return animationEnd
}

@Composable
private fun HeartRateContent(
    navController: NavHostController,
    currentScreen: HeartRateScreen,
    uiState: org.noblecow.hrservice.viewmodel.MainUiState,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
    onFakeBpmClick: () -> Unit,
    onUserMessageShow: () -> Unit,
    logger: Logger,
    modifier: Modifier = Modifier
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(
        rememberTopAppBarState()
    )
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            HeartRateAppBar(
                scrollBehavior = scrollBehavior,
                currentScreen = currentScreen,
                canNavigateBack = navController.previousBackStackEntry != null,
                navController = navController,
                onFakeBpmClick = onFakeBpmClick
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        }
    ) { innerPadding ->
        HeartRateNavigation(
            navController = navController,
            bpmCount = uiState.bpmCount,
            servicesState = uiState.servicesState,
            isClientConnected = uiState.isClientConnected,
            bpm = uiState.bpm,
            onStartClick = onStartClick,
            onStopClick = onStopClick,
            logger = logger,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        )

        UserMessageHandler(
            userMessage = uiState.userMessage,
            snackbarHostState = snackbarHostState,
            onShowMessage = onUserMessageShow
        )
    }
}

@Composable
private fun HeartRateNavigation(
    navController: NavHostController,
    bpmCount: Int,
    servicesState: ServicesState,
    isClientConnected: Boolean,
    bpm: Int,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
    logger: Logger,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = HeartRateScreen.Home.name,
        modifier = modifier
    ) {
        composable(route = HeartRateScreen.Home.name) {
            val isPulseAnimationComplete = rememberPulseAnimation(bpmCount)

            HomeScreen(
                onStartClick = onStartClick,
                onStopClick = onStopClick,
                showAwaitingClient = servicesState == ServicesState.Started &&
                    !isClientConnected,
                bpm = bpm,
                isHeartBeatPulse = isPulseAnimationComplete,
                showStart = servicesState == ServicesState.Stopped
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
}

@Composable
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

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentScreen = HeartRateScreen.entries.find {
        it.name == backStackEntry?.destination?.route
    } ?: HeartRateScreen.Home

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

@Composable
private fun UserMessageHandler(
    userMessage: String?,
    snackbarHostState: SnackbarHostState,
    onShowMessage: () -> Unit
) {
    val currentOnShowMessage by rememberUpdatedState(onShowMessage)

    LaunchedEffect(userMessage) {
        userMessage?.let { message ->
            snackbarHostState.showSnackbar(message = message)
            currentOnShowMessage()
        }
    }
}
