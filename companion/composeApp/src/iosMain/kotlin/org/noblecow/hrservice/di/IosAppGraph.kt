package org.noblecow.hrservice.di

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import org.noblecow.hrservice.viewmodel.MainViewModel

/**
 * iOS-specific dependency injection graph.
 *
 * Provides MainViewModel directly as a property for easy access from composables.
 * Unlike Android, iOS doesn't use ViewModelProvider, so ViewModels are accessed
 * directly from the graph.
 *
 * @see LocalIosAppGraph for accessing the graph in composables
 */
@DependencyGraph(
    AppScope::class,
    bindingContainers = [CoroutineProviders::class, LoggerProviders::class]
)
internal interface IosAppGraph : AppGraph {
    /**
     * Provides the MainViewModel instance.
     * This is a singleton in AppScope, so the same instance is reused.
     */
    val mainViewModel: MainViewModel

    /**
     * Factory for creating IosAppGraph instances.
     */
    @DependencyGraph.Factory
    interface Factory {
        /**
         * Creates an IosAppGraph instance.
         *
         * @return A configured IosAppGraph with all dependencies
         */
        fun create(): IosAppGraph
    }
}
