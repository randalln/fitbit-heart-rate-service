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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.mikepenz.aboutlibraries.ui.compose.android.rememberLibraries
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.noblecow.hrservice.R
import org.noblecow.hrservice.WORKER_NAME
import org.noblecow.hrservice.data.repository.ServicesState
import org.noblecow.hrservice.data.util.ANIMATION_MILLIS
import org.noblecow.hrservice.data.util.DEFAULT_BPM
import org.noblecow.hrservice.ui.MainViewModel
import org.slf4j.LoggerFactory

@Composable
@Suppress("LongMethod", "CyclomaticComplexMethod")
internal fun HeartRateApp(
    workRequest: OneTimeWorkRequest,
    workState: StateFlow<List<WorkInfo>?>,
    workManager: WorkManager,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    viewModel: MainViewModel = koinViewModel()
) {
    val logger = LoggerFactory.getLogger("HeartRateApp")
    val uiState by viewModel.mainUiState.collectAsState()
    val workerState = workState.collectAsState()
    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        viewModel.receivePermissions(permissions)
    }
    val enableBluetoothLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode != Activity.RESULT_OK) {
            viewModel.userDeclinedBluetoothEnable()
        } else {
            viewModel.start()
        }
    }
    logger.debug(uiState.toString())

    // Side-effects
    uiState.permissionsRequested?.let {
        LaunchedEffect(uiState) {
            logger.debug("Requesting permissions: $it")
            permissionsLauncher.launch(it.toTypedArray())
        }
    }

    uiState.bluetoothRequested?.let {
        if (it) {
            LaunchedEffect(uiState) {
                logger.debug("Need to enable bluetooth")
                val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                enableBluetoothLauncher.launch(intent)
            }
        }
    }

    // (re-)start the foreground service via WorkManager
    val serviceState: WorkInfo.State? = workerState.value?.find {
        WorkInfo.State.SUCCEEDED == it.state
    }?.state
    workerState.value?.let {
        if (uiState.startAndroidService &&
            (serviceState == null || serviceState == WorkInfo.State.SUCCEEDED)
        ) {
            logger.debug("Starting Foreground Service")
            workManager.enqueueUniqueWork(WORKER_NAME, ExistingWorkPolicy.KEEP, workRequest)
            viewModel.androidServiceStarted()
        }
    }

    val userMessage = uiState.userMessage?.let {
        stringResource(id = it)
    }

    // Get current back stack entry
    val backStackEntry by navController.currentBackStackEntryAsState()
    // Get the name of the current screen
    val currentScreen = HeartRateScreen.valueOf(
        backStackEntry?.destination?.route ?: HeartRateScreen.Home.name
    )
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(
            rememberTopAppBarState()
        )
        val scope = rememberCoroutineScope()
        val snackbarHostState = remember { SnackbarHostState() }
        var animationEnd by remember { mutableStateOf(false) }
        var localBpmCount by remember { mutableIntStateOf(uiState.bpmCount) }

        Scaffold(
            modifier = Modifier.Companion
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
                modifier = Modifier.Companion
                    .fillMaxSize()
                    // .verticalScroll(rememberScrollState())
                    .padding(innerPadding)
            ) {
                val bpmJob = scope.launch(start = CoroutineStart.LAZY) {
                    delay(ANIMATION_MILLIS)
                    animationEnd = true
                }
                composable(route = HeartRateScreen.Home.name) {
                    val showStart = uiState.servicesState == ServicesState.Stopped ||
                        uiState.servicesState == ServicesState.Starting
                    val startStopEnabled = uiState.servicesState == ServicesState.Stopped ||
                        uiState.servicesState == ServicesState.Started
                    HomeScreen(
                        onStartClick = { if (showStart) viewModel.start() else viewModel.stop() },
                        showAwaitingClient = uiState.servicesState == ServicesState.Started &&
                            !uiState.isClientConnected,
                        bpm = uiState.bpm,
                        animationEnd = animationEnd,
                        showStart = showStart,
                        startStopEnabled = startStopEnabled
                    )
                    if (localBpmCount != DEFAULT_BPM && localBpmCount != uiState.bpmCount) {
                        bpmJob.start()
                        animationEnd = false
                    }
                    localBpmCount = uiState.bpmCount
                }
                composable(route = HeartRateScreen.OpenSource.name) {
                    val libraries by rememberLibraries(R.raw.aboutlibraries)
                    LibrariesContainer(libraries, Modifier.Companion.fillMaxSize())
                }
                userMessage?.let {
                    scope.launch {
                        snackbarHostState.showSnackbar(message = userMessage)
                        viewModel.userMessageShown()
                    }
                }
            }
        }
    }
}
