package org.noblecow.hrservice.composables

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import heartratemonitor.composeapp.generated.resources.start
import heartratemonitor.composeapp.generated.resources.stop
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.noblecow.hrservice.ui.theme.HeartRateTheme
import org.noblecow.hrservice.ui.theme.Tokens

@Composable
internal fun HomeScreen(
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
    showAwaitingClient: Boolean,
    bpm: Int,
    isHeartBeatPulse: Boolean,
    showStart: Boolean,
    modifier: Modifier = Modifier
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
        if (showStart) {
            Button(
                onClick = onStartClick,
                modifier = Modifier.wrapContentSize()
            ) {
                Text(text = stringResource(Res.string.start))
            }
        } else {
            Button(
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
            modifier = Modifier.fillMaxSize(),
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
            modifier = Modifier.fillMaxSize(),
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
