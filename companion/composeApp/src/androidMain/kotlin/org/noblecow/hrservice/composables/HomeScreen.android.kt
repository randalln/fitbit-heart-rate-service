package org.noblecow.hrservice.composables

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import heartratemonitor.composeapp.generated.resources.Res
import heartratemonitor.composeapp.generated.resources.start
import heartratemonitor.composeapp.generated.resources.starting
import heartratemonitor.composeapp.generated.resources.stop
import heartratemonitor.composeapp.generated.resources.stopping
import org.jetbrains.compose.resources.stringResource
import org.noblecow.hrservice.data.repository.ServicesState
import org.noblecow.hrservice.ui.theme.HeartRateTheme

/**
 * Android implementation of home screen with Material 3 button styling.
 *
 * Uses Material 3 Button component for Start/Stop actions, following
 * Material Design 3 guidelines for Android. Shows a circular progress
 * indicator during Starting and Stopping transition states.
 *
 * @param onStartClick Callback when the start button is clicked.
 * @param onStopClick Callback when the stop button is clicked.
 * @param showAwaitingClient Whether to show the "Awaiting Client" message.
 * @param bpm Current heart rate in beats per minute.
 * @param isHeartBeatPulse Whether the heart animation should pulse.
 * @param servicesState Current state of the services (Starting, Started, Stopping, Stopped, Error).
 * @param modifier Optional modifier for this composable.
 */
@Composable
actual fun HomeScreen(
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
    showAwaitingClient: Boolean,
    bpm: Int,
    isHeartBeatPulse: Boolean,
    servicesState: ServicesState,
    modifier: Modifier
) {
    HomeScreenContent(
        showAwaitingClient = showAwaitingClient,
        bpm = bpm,
        isHeartBeatPulse = isHeartBeatPulse,
        modifier = modifier
    ) {
        when (servicesState) {
            ServicesState.Stopped, is ServicesState.Error -> {
                Button(
                    onClick = onStartClick,
                    modifier = Modifier.wrapContentSize()
                ) {
                    Text(text = stringResource(Res.string.start))
                }
            }

            ServicesState.Started -> {
                Button(
                    onClick = onStopClick,
                    modifier = Modifier.wrapContentSize()
                ) {
                    Text(text = stringResource(Res.string.stop))
                }
            }

            ServicesState.Starting -> {
                Button(
                    onClick = { },
                    enabled = false,
                    modifier = Modifier.wrapContentSize()
                ) {
                    Row {
                        Text(text = stringResource(Res.string.starting))
                        Spacer(modifier = Modifier.width(8.dp))
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
            }

            ServicesState.Stopping -> {
                Button(
                    onClick = { },
                    enabled = false,
                    modifier = Modifier.wrapContentSize()
                ) {
                    Row {
                        Text(text = stringResource(Res.string.stopping))
                        Spacer(modifier = Modifier.width(8.dp))
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun HomeScreenLightPreview() {
    HeartRateTheme(darkTheme = false) {
        Surface(
            color = MaterialTheme.colorScheme.background
        ) {
            HomeScreen(
                onStartClick = { },
                onStopClick = { },
                servicesState = ServicesState.Stopped,
                showAwaitingClient = true,
                bpm = 128,
                isHeartBeatPulse = true
            )
        }
    }
}

@Preview
@Composable
private fun HomeScreenDarkPreview() {
    HeartRateTheme(darkTheme = true) {
        Surface(
            color = MaterialTheme.colorScheme.background
        ) {
            HomeScreen(
                onStartClick = { },
                onStopClick = { },
                servicesState = ServicesState.Starting,
                showAwaitingClient = true,
                bpm = 128,
                isHeartBeatPulse = true
            )
        }
    }
}
