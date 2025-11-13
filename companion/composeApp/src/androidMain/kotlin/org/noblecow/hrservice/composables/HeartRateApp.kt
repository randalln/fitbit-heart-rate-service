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
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import co.touchlab.kermit.Logger
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import com.mikepenz.aboutlibraries.ui.compose.produceLibraries
import heartratemonitor.composeapp.generated.resources.Res
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.noblecow.hrservice.data.repository.ServicesState
import org.noblecow.hrservice.data.util.ANIMATION_MILLIS
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
    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        viewModel.receivePermissions(permissions)
    }
    val enableBluetoothLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode != Activity.RESULT_OK) {
            viewModel.userDeclinedBluetoothEnable()
        } else {
            viewModel.start()
        }
    }

    // Side-effects
    uiState.permissionsRequested?.let {
        LaunchedEffect(uiState) {
            logger.d("Requesting permissions: $it")
            permissionsLauncher.launch(it.toTypedArray())
        }
    }

    uiState.bluetoothRequested?.let {
        if (it) {
            LaunchedEffect(uiState) {
                logger.d("Need to enable bluetooth")
                val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                enableBluetoothLauncher.launch(intent)
            }
        }
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
                modifier = Modifier
                    .fillMaxSize()
                    // .verticalScroll(rememberScrollState())
                    .padding(innerPadding)
            ) {
                val bpmJob = scope.launch(start = CoroutineStart.LAZY) {
                    delay(ANIMATION_MILLIS)
                    animationEnd = true
                }
                composable(route = HeartRateScreen.Home.name) {
                    HomeScreen(
                        onStartClick = { viewModel.start() },
                        onStopClick = { viewModel.stop() },
                        showAwaitingClient = uiState.servicesState == ServicesState.Started &&
                            !uiState.isClientConnected,
                        bpm = uiState.bpm,
                        animationEnd = animationEnd,
                        showStart = uiState.servicesState == ServicesState.Stopped
                    )
                    if (localBpmCount != 0 && localBpmCount != uiState.bpmCount) {
                        bpmJob.start()
                        animationEnd = false
                    }
                    localBpmCount = uiState.bpmCount
                }
                composable(route = HeartRateScreen.OpenSource.name) {
                    val libraries by produceLibraries {
                        Res.readBytes("files/aboutlibraries.json").decodeToString()
                    }
                    LibrariesContainer(libraries, Modifier.Companion.fillMaxSize())
                }
                uiState.userMessage?.let {
                    scope.launch {
                        snackbarHostState.showSnackbar(message = it)
                        viewModel.userMessageShown()
                    }
                }
            }
        }
    }
}
