@file:Suppress("compose:compositionlocal-allowlist")

package org.noblecow.hrservice.di

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * CompositionLocal for providing the iOS DI graph to composables.
 *
 * This allows composables to access the dependency injection graph for
 * creating ViewModels and other dependencies.
 *
 * Must be provided at the root of the composition tree using
 * `CompositionLocalProvider(LocalIosAppGraph provides appGraph) { ... }`
 *
 * Note: CompositionLocals should generally be avoided, but for dependency injection
 * this is the recommended pattern in Compose Multiplatform.
 *
 * @throws IllegalStateException if accessed before being provided
 */
internal val LocalIosAppGraph = staticCompositionLocalOf<IosAppGraph> {
    error("No IosAppGraph provided. Make sure to wrap your UI with CompositionLocalProvider.")
}
