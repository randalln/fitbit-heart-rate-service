package org.noblecow.hrservice.composables

import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import heartratemonitor.composeapp.generated.resources.Res
import heartratemonitor.composeapp.generated.resources.start
import heartratemonitor.composeapp.generated.resources.stop
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.noblecow.hrservice.ui.theme.HeartRateTheme

/**
 * iOS implementation of home screen with iOS-native button styling.
 *
 * Uses IosButton component for Start/Stop actions, following Apple Human
 * Interface Guidelines (HIG) for iOS with:
 * - 44pt minimum touch targets
 * - iOS-appropriate corner radius and padding
 * - iOS-style button appearance
 *
 * @param onStartClick Callback when the start button is clicked.
 * @param onStopClick Callback when the stop button is clicked.
 * @param showAwaitingClient Whether to show the "Awaiting Client" message.
 * @param bpm Current heart rate in beats per minute.
 * @param isHeartBeatPulse Whether the heart animation should pulse.
 * @param showStart Whether to show the start button (true) or stop button (false).
 * @param modifier Optional modifier for this composable.
 */
@Composable
actual fun HomeScreen(
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
    showAwaitingClient: Boolean,
    bpm: Int,
    isHeartBeatPulse: Boolean,
    showStart: Boolean,
    modifier: Modifier
) {
    HomeScreenContent(
        showAwaitingClient = showAwaitingClient,
        bpm = bpm,
        isHeartBeatPulse = isHeartBeatPulse,
        modifier = modifier
    ) {
        if (showStart) {
            IosButton(
                onClick = onStartClick,
                modifier = Modifier.wrapContentSize()
            ) {
                Text(text = stringResource(Res.string.start))
            }
        } else {
            IosButton(
                onClick = onStopClick,
                modifier = Modifier.wrapContentSize()
            ) {
                Text(text = stringResource(Res.string.stop))
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
                showStart = true,
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
                showStart = true,
                showAwaitingClient = true,
                bpm = 128,
                isHeartBeatPulse = true
            )
        }
    }
}
