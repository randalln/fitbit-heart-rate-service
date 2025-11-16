package org.noblecow.hrservice.viewmodel

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import org.noblecow.hrservice.di.LocalIosAppGraph

/**
 * iOS-specific ViewModel provider using Metro DI.
 *
 * Unlike Android's ViewModelProvider, this function directly accesses ViewModels
 * from the DI graph and uses Compose's remember {} to maintain the instance
 * across recompositions.
 *
 * Currently only supports MainViewModel. To add more ViewModels, add them as
 * properties to IosAppGraph.
 *
 * @param VM The type of ViewModel to create (must be MainViewModel for now)
 * @return The ViewModel instance from the DI graph
 * @throws IllegalArgumentException if the ViewModel type is not supported
 *
 * Example usage:
 * ```kotlin
 * val viewModel = metroViewModel<MainViewModel>()
 * ```
 */
@Composable
internal inline fun <reified VM : ViewModel> metroViewModel(): VM {
    val appGraph = LocalIosAppGraph.current

    return remember {
        @Suppress("UNCHECKED_CAST")
        when (VM::class) {
            MainViewModel::class -> appGraph.mainViewModel as VM

            else -> error(
                "ViewModel ${VM::class.simpleName} not supported. " +
                    "Add it to IosAppGraph to make it available."
            )
        }
    }
}
