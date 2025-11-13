// Copyright (C) 2025 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
package org.noblecow.hrservice.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provider
import kotlin.reflect.KClass
import org.noblecow.hrservice.di.AndroidAppGraph

/**
 * A [ViewModelProvider.Factory] that uses an injected map of [KClass] to [Provider] of [ViewModel]
 * to create ViewModels.
 */
@ContributesBinding(AppScope::class)
@Inject
internal class MetroViewModelFactory(val appGraph: AndroidAppGraph) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        val viewModelGraph = viewModelGraph(extras)

        println(viewModelGraph.viewModelProviders)

        val provider =
            viewModelGraph.viewModelProviders[modelClass.kotlin]
                ?: throw kotlin.IllegalArgumentException("Unknown model class $modelClass")

        @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
        return modelClass.cast(provider())
    }

    fun viewModelGraph(extras: CreationExtras): ViewModelGraph = appGraph.createViewModelGraph(extras)
}
