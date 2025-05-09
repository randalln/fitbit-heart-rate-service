package org.noblecow.hrservice

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import org.noblecow.hrservice.ui.theme.HeartRateTheme

internal class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val workRequest = OneTimeWorkRequestBuilder<MainWorker>().build()
        val workManager = WorkManager.getInstance(this)

        val localScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
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
}
