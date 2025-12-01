package org.noblecow.hrservice.composables

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import heartratemonitor.composeapp.generated.resources.Res
import heartratemonitor.composeapp.generated.resources.awaiting_client
import heartratemonitor.composeapp.generated.resources.ic_heart
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.noblecow.hrservice.data.repository.ServicesState
import org.noblecow.hrservice.ui.theme.Tokens

/**
 * Platform-specific home screen implementation.
 *
 * Displays heart rate monitoring interface with platform-appropriate button styling:
 * - Android: Material 3 Button with circular progress indicator during transitions
 * - iOS: iOS-style button (IosButton) with circular progress indicator during transitions
 *
 * Shows a circular progress indicator to the right of the button text during
 * Starting and Stopping transition states. The button is disabled during these states.
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
expect fun HomeScreen(
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
    showAwaitingClient: Boolean,
    bpm: Int,
    isHeartBeatPulse: Boolean,
    servicesState: ServicesState,
    modifier: Modifier = Modifier
)

/**
 * Shared home screen content layout.
 *
 * Displays the heart rate monitoring UI with animated heart icon and BPM display.
 * Platform-specific button implementations should be provided via the [actionButton] parameter.
 *
 * @param showAwaitingClient Whether to show the "Awaiting Client" message.
 * @param bpm Current heart rate in beats per minute.
 * @param isHeartBeatPulse Whether the heart animation should pulse.
 * @param modifier Optional modifier for this composable.
 * @param actionButton Platform-specific button composable (Start or Stop).
 */
@Composable
internal fun HomeScreenContent(
    showAwaitingClient: Boolean,
    bpm: Int,
    isHeartBeatPulse: Boolean,
    modifier: Modifier = Modifier,
    actionButton: @Composable ColumnScope.() -> Unit
) {
    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxSize()
            .padding(Tokens.Spacing.Small)
    ) {
        Text(
            text = stringResource(Res.string.awaiting_client),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.alpha(if (showAwaitingClient) 1f else 0f)
        )
        Box(
            modifier = Modifier.size(Tokens.Size.HeartContainer),
            contentAlignment = Alignment.Center
        ) {
            val heartScale by animateFloatAsState(
                targetValue = if (isHeartBeatPulse) 0.95f else 1.0f,
                label = "heartAnimation"
            )

            Image(
                painter = painterResource(Res.drawable.ic_heart),
                modifier = Modifier
                    .matchParentSize()
                    .scale(heartScale),
                contentDescription = null
            )
            Text(
                text = bpm.toString(),
                style = MaterialTheme.typography.displayLarge,
                color = Tokens.Colors.BPMText,
                modifier = Modifier.semantics {
                    contentDescription = if (bpm > 0) {
                        "Heart rate: $bpm beats per minute"
                    } else {
                        "Heart rate: not available"
                    }
                }
            )
        }
        actionButton()
    }
}
