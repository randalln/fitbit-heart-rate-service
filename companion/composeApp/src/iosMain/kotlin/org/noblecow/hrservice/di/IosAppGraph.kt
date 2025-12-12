package org.noblecow.hrservice.di

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metrox.viewmodel.ViewModelGraph
import org.noblecow.hrservice.viewmodel.MainViewModel

/**
 * iOS-specific dependency injection graph.
 */
@DependencyGraph(
    AppScope::class,
    bindingContainers = [CoroutineProviders::class, LoggerProviders::class]
)
internal interface IosAppGraph :
    AppGraph,
    ViewModelGraph
