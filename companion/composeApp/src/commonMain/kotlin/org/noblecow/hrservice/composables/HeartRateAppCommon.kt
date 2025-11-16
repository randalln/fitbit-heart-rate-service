@file:OptIn(ExperimentalMaterial3Api::class)

package org.noblecow.hrservice.composables

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import co.touchlab.kermit.Logger
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import com.mikepenz.aboutlibraries.ui.compose.produceLibraries
import heartratemonitor.composeapp.generated.resources.Res
import kotlinx.coroutines.delay
import org.noblecow.hrservice.data.repository.ServicesState
import org.noblecow.hrservice.data.util.ANIMATION_MS
import org.noblecow.hrservice.viewmodel.MainUiState

/**
 * Remembers a pulse animation state that triggers on BPM count changes.
 *
 * Creates a brief animation pulse effect when a new heart rate reading arrives.
 * The animation completes after [ANIMATION_MS] milliseconds.
 *
 * @param bpmCount The sequence number of BPM readings - increments on each new reading
 * @return true when the animation has completed, false during the animation
 */
@Composable
internal fun rememberPulseAnimation(bpmCount: Int): Boolean {
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

/**
 * Main content container for the Heart Rate Monitor application.
 *
 * Provides the scaffold structure with app bar, navigation, and snackbar support.
 * This composable is platform-agnostic and used by both Android and iOS.
 *
 * @param navController Navigation controller for screen transitions
 * @param currentScreen The currently displayed screen
 * @param uiState The main UI state containing BPM, service state, etc.
 * @param onStartClick Callback when the start button is clicked
 * @param onStopClick Callback when the stop button is clicked
 * @param onFakeBpmClick Callback when the fake BPM toggle is clicked
 * @param onUserMessageShow Callback when a user message has been shown
 * @param logger Logger instance for debugging
 * @param modifier Optional modifier for this composable
 */
@Composable
internal fun HeartRateContent(
    navController: NavHostController,
    currentScreen: HeartRateScreen,
    uiState: MainUiState,
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

/**
 * Navigation host for the Heart Rate Monitor application.
 *
 * Defines the navigation graph with Home and OpenSource screens.
 * This composable is platform-agnostic.
 *
 * @param navController Navigation controller for screen transitions
 * @param bpmCount The sequence number of BPM readings for animation
 * @param servicesState Current state of the services (Started, Stopped, etc.)
 * @param isClientConnected Whether a BLE client is connected
 * @param bpm Current heart rate in beats per minute
 * @param onStartClick Callback when the start button is clicked
 * @param onStopClick Callback when the stop button is clicked
 * @param logger Logger instance for debugging
 * @param modifier Optional modifier for this composable
 */
@Composable
internal fun HeartRateNavigation(
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

/**
 * Handles displaying user messages via snackbar.
 *
 * When a user message is present, shows it in a snackbar and notifies
 * that the message has been shown. This composable is platform-agnostic.
 *
 * @param userMessage The message to display, or null if no message
 * @param snackbarHostState The snackbar host state for displaying the message
 * @param onShowMessage Callback invoked after the message has been shown
 */
@Composable
internal fun UserMessageHandler(
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

/**
 * Gets the current screen from the navigation back stack.
 *
 * Observes the navigation back stack and returns the current screen,
 * defaulting to Home if the route is not recognized.
 *
 * @param navController The navigation controller to observe
 * @return The current screen being displayed
 */
@Composable
internal fun getCurrentScreen(navController: NavHostController): HeartRateScreen {
    val backStackEntry by navController.currentBackStackEntryAsState()
    return HeartRateScreen.entries.find {
        it.name == backStackEntry?.destination?.route
    } ?: HeartRateScreen.Home
}
