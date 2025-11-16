package org.noblecow.hrservice

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import dev.zacsweers.metro.createGraphFactory
import org.noblecow.hrservice.composables.HeartRateApp
import org.noblecow.hrservice.di.IosAppGraph
import org.noblecow.hrservice.di.LocalIosAppGraph
import org.noblecow.hrservice.ui.theme.HeartRateTheme

/**
 * Main view controller for the iOS Heart Rate Monitor application.
 *
 * Initializes the dependency injection graph and provides it to the
 * Compose UI tree via CompositionLocal. The HeartRateApp composable
 * then uses this graph to access ViewModels and other dependencies.
 *
 * @return A ComposeUIViewController containing the full app UI
 */
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
fun MainViewController() = ComposeUIViewController {
    // Create the DI graph once and remember it across recompositions
    val appGraph = remember { createGraphFactory<IosAppGraph.Factory>().create() }

    // Provide the graph to all composables in the tree
    CompositionLocalProvider(LocalIosAppGraph provides appGraph) {
        HeartRateTheme {
            HeartRateApp()
        }
    }
}
