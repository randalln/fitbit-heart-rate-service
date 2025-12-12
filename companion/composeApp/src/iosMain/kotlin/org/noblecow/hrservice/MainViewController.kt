package org.noblecow.hrservice

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.window.ComposeUIViewController
import dev.zacsweers.metro.createGraph
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory
import org.noblecow.hrservice.composables.HeartRateApp
import org.noblecow.hrservice.di.IosAppGraph
import org.noblecow.hrservice.ui.theme.HeartRateTheme

/**
 * Main view controller for the iOS Heart Rate Monitor application.
 *
 * @return A ComposeUIViewController containing the full app UI
 */
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
fun MainViewController() = ComposeUIViewController {
    val appGraph = createGraph<IosAppGraph>()

    HeartRateTheme {
        CompositionLocalProvider(LocalMetroViewModelFactory provides appGraph.metroViewModelFactory) {
            HeartRateApp()
        }
    }
}
