@file:OptIn(ExperimentalMaterial3Api::class)

package org.noblecow.hrservice

import android.app.Activity.RESULT_OK
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import kotlinx.coroutines.flow.StateFlow
import org.noblecow.hrservice.data.repository.ServicesState
import org.noblecow.hrservice.ui.HomeScreen
import org.noblecow.hrservice.ui.MainViewModel
import org.slf4j.LoggerFactory

enum class HeartRateScreen(@StringRes val title: Int) {
    Home(title = R.string.app_name),
    OpenSource(title = R.string.open_source)
}

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
                stringResource(currentScreen.title),
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
                        contentDescription = stringResource(R.string.back_button)
                    )
                }
            }
        },
        modifier = modifier
    )
}

@Composable
internal fun HomeAppBar(
    scrollBehavior: TopAppBarScrollBehavior,
    currentScreen: HeartRateScreen,
    canNavigateBack: Boolean,
    navController: NavHostController,
    onFakeBpmClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }

    CenterAlignedTopAppBar(
        title = {
            Text(
                stringResource(currentScreen.title),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        actions = {
            IconButton(onClick = { menuExpanded = !menuExpanded }) {
                Icon(
                    imageVector = Icons.Outlined.MoreVert,
                    contentDescription = stringResource(R.string.menu)
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
                        Text(stringResource(R.string.open_source))
                    }
                )
                DropdownMenuItem(
                    onClick = {
                        onFakeBpmClick.invoke()
                        menuExpanded = false
                    },
                    text = {
                        Text(stringResource(R.string.toggle_fake_bpm))
                    }
                )
            }
        },
        scrollBehavior = scrollBehavior,
        navigationIcon = {
            if (canNavigateBack) {
                IconButton(onClick = { navController.navigateUp() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back_button)
                    )
                }
            }
        },
        modifier = modifier
    )
}

@Composable
@Suppress("LongMethod")
internal fun HeartRateApp(
    workRequest: OneTimeWorkRequest,
    workState: StateFlow<MutableList<WorkInfo>?>,
    workManager: WorkManager,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    viewModel: MainViewModel = hiltViewModel()
) {
    val logger = LoggerFactory.getLogger("HeartRateApp")
    val uiState by viewModel.mainUiState.collectAsState()
    val workerState = workState.collectAsState()
    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        viewModel.receivePermissions(permissions)
    }
    val enableBluetoothLauncher = rememberLauncherForActivityResult(StartActivityForResult()) {
        if (it.resultCode != RESULT_OK) {
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
                composable(route = HeartRateScreen.Home.name) {
                    HomeScreen(
                        onStartClick = { viewModel.start() },
                        showAwaitingClient = uiState.servicesState == ServicesState.Started &&
                            !uiState.isClientConnected,
                        bpm = uiState.bpm,
                        showStart = uiState.servicesState == ServicesState.Stopped &&
                            !uiState.startAndroidService
                    )
                }
                composable(route = HeartRateScreen.OpenSource.name) {
                    LibrariesContainer()
                }
            }
        }
    }
}
