package org.noblecow.hrservice

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import org.noblecow.hrservice.composables.HeartRateApp
import org.noblecow.hrservice.di.ActivityKey
import org.noblecow.hrservice.di.DefaultDispatcher
import org.noblecow.hrservice.ui.theme.HeartRateTheme

@ContributesIntoMap(AppScope::class, binding<Activity>())
@ActivityKey(MainActivity::class)
@Inject
internal class MainActivity(
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher,
    viewModelFactory: ViewModelProvider.Factory,
    val workManager: WorkManager
) : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val workRequest = OneTimeWorkRequestBuilder<MainWorker>().build()
        // val workManager = WorkManager.getInstance(this)

        val localScope = CoroutineScope(SupervisorJob() + dispatcher)
        val workState = workManager.getWorkInfosForUniqueWorkFlow(WORKER_NAME)
            .stateIn(
                scope = localScope,
                started = SharingStarted.WhileSubscribed(),
                initialValue = null
            )

        enableEdgeToEdge()
        setContent {
            HeartRateTheme {
                HeartRateApp(
                    workRequest = workRequest,
                    workState = workState,
                    workManager = workManager
                )
            }
        }
    }

    override val defaultViewModelProviderFactory: ViewModelProvider.Factory = viewModelFactory
}
