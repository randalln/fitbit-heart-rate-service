package org.noblecow.hrservice

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.ComposeUIViewController
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import org.noblecow.hrservice.composables.HomeScreen
import org.noblecow.hrservice.ui.theme.HeartRateTheme

private const val TAG = "MainViewController"

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
fun MainViewController() = ComposeUIViewController {
    HeartRateTheme {
        var showAwaitingClient by remember { mutableStateOf(true) }

        HomeScreen(
            onStartClick = {
                Logger.i(TAG) { "onStartClick" }
                showAwaitingClient = !showAwaitingClient
            },
            onStopClick = {
                Logger.i(TAG) { "onStopClick" }
                showAwaitingClient = !showAwaitingClient
            },
            showStart = true,
            showAwaitingClient = showAwaitingClient,
            bpm = 128,
            animationEnd = true
        )
    }
}
