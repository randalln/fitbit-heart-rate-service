package org.noblecow.hrservice.worker

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.CATEGORY_STATUS
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import co.touchlab.kermit.Logger
import co.touchlab.kermit.loggerConfigInit
import co.touchlab.kermit.platformLogWriter
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.binding
import heartratemonitor.composeapp.generated.resources.Res
import heartratemonitor.composeapp.generated.resources.awaiting_client
import heartratemonitor.composeapp.generated.resources.bpm
import heartratemonitor.composeapp.generated.resources.channel_1_description
import heartratemonitor.composeapp.generated.resources.channel_1_name
import heartratemonitor.composeapp.generated.resources.client_connected
import heartratemonitor.composeapp.generated.resources.stop
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.takeWhile
import org.noblecow.hrservice.HRBroadcastReceiver
import org.noblecow.hrservice.MainActivity
import org.noblecow.hrservice.R
import org.noblecow.hrservice.data.repository.AppState
import org.noblecow.hrservice.data.repository.MainRepository
import org.noblecow.hrservice.data.repository.ServicesState
import org.noblecow.hrservice.data.util.DEFAULT_BPM
import org.noblecow.hrservice.data.util.ResourceHelper
import org.noblecow.hrservice.di.MetroWorkerFactory
import org.noblecow.hrservice.di.WorkerKey

private const val CHANNEL_ID = "channel_id_1"
private const val NOTIFICATION_ID = 0
private const val TAG = "MainWorker"
internal const val WORKER_NAME = "mainWorker"

@AssistedInject
internal class MainWorker(
    context: Context,
    @Assisted params: WorkerParameters,
    private val mainRepository: MainRepository,
    private val resourceHelper: ResourceHelper,
    private val logger: Logger = Logger(loggerConfigInit(platformLogWriter()), TAG)
) : CoroutineWorker(context, params) {

    private val notificationManager = NotificationManagerCompat.from(applicationContext)
    private lateinit var notificationBuilder: NotificationCompat.Builder

    @SuppressLint("MissingPermission")
    @Suppress("SwallowedException", "TooGenericExceptionCaught")
    override suspend fun doWork(): Result {
        logger.d("MainWorker starting - runAttemptCount: $runAttemptCount")
        return try {
            logger.d("Creating foreground info...")
            val foregroundInfo = createForegroundInfo()
            logger.d("Setting foreground...")
            setForeground(foregroundInfo)
            logger.d("Foreground service started successfully")

            mainRepository.appStateFlow
                .takeWhile { it.servicesState != ServicesState.Stopped }
                .scan(null as AppState?) { previousState, currentState ->
                    // Now this only processes non-Stopped states
                    val updateNotification = previousState == null ||
                        previousState.bpm != currentState.bpm ||
                        previousState.isClientConnected != currentState.isClientConnected
                    if (updateNotification) {
                        updateNotification(currentState)
                        notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())
                    }
                    currentState
                }
                .collect { /* Work handled in the scan */ }

            logger.d("MainWorker completed successfully")
            return Result.success()
        } catch (error: Throwable) {
            logger.e("MainWorker failed: ${error.javaClass.simpleName} - ${error.message}", error)
            Result.failure()
        } finally {
            logger.d("MainWorker cleanup: cancelling notifications")
            notificationManager.cancel(NOTIFICATION_ID)
        }
    }

    private suspend fun createForegroundInfo(): ForegroundInfo {
        createNotificationChannel()

        val broadcastIntent = Intent(applicationContext, HRBroadcastReceiver::class.java)
        val stopPendingIntent = PendingIntent.getBroadcast(
            applicationContext,
            0,
            broadcastIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val contentIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        if (!this::notificationBuilder.isInitialized) {
            notificationBuilder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setContentText(DEFAULT_BPM.toString())
                .setSmallIcon(R.drawable.ic_heart)
                .setShowWhen(false)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(CATEGORY_STATUS)
                .setContentIntent(contentIntent)
                .addAction(
                    android.R.drawable.ic_delete,
                    resourceHelper.getString(Res.string.stop),
                    stopPendingIntent
                )
        }
        val notification = notificationBuilder.build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private suspend fun createNotificationChannel() {
        val name = resourceHelper.getString(Res.string.channel_1_name)
        val descriptionText = resourceHelper.getString(Res.string.channel_1_description)
        val importance = NotificationManager.IMPORTANCE_LOW
        val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
            description = descriptionText
        }
        notificationManager.createNotificationChannel(channel)
    }

    private suspend fun updateNotification(appState: AppState) {
        notificationBuilder.apply {
            setContentText(
                resourceHelper.getFormattedString(Res.string.bpm, appState.bpm.value)
            )
            setContentTitle(
                resourceHelper.getString(
                    if (appState.isClientConnected) {
                        Res.string.client_connected
                    } else {
                        Res.string.awaiting_client
                    }
                )
            )
        }
    }

    @Suppress("UnnecessaryAbstractClass")
    @WorkerKey(MainWorker::class)
    @ContributesIntoMap(
        AppScope::class,
        binding = binding<MetroWorkerFactory.WorkerInstanceFactory<*>>()
    )
    @AssistedFactory
    abstract class Factory : MetroWorkerFactory.WorkerInstanceFactory<MainWorker>
}
