package org.noblecow.hrservice

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import org.noblecow.hrservice.composables.HeartRateApp
import org.noblecow.hrservice.di.ActivityKey
import org.noblecow.hrservice.ui.theme.HeartRateTheme

@ContributesIntoMap(AppScope::class, binding<Activity>())
@ActivityKey(MainActivity::class)
@Inject
class MainActivity(viewModelFactory: ViewModelProvider.Factory) : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContent {
            HeartRateTheme {
                HeartRateApp()
            }
        }
    }

    override val defaultViewModelProviderFactory: ViewModelProvider.Factory = viewModelFactory
}
