package org.noblecow.hrservice

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
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.binding
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import org.noblecow.hrservice.data.repository.AppState
import org.noblecow.hrservice.data.repository.MainRepository
import org.noblecow.hrservice.data.repository.ServicesState
import org.noblecow.hrservice.data.util.DEFAULT_BPM
import org.noblecow.hrservice.di.IoDispatcher
import org.noblecow.hrservice.di.MetroWorkerFactory
import org.noblecow.hrservice.di.WorkerKey
import org.slf4j.LoggerFactory

private const val CHANNEL_ID = "channel_id_1"
private const val NOTIFICATION_ID = 0
private const val TAG = "MainWorker"
internal const val WORKER_NAME = "mainWorker"

@AssistedInject
internal class MainWorker(
    context: Context,
    @Assisted params: WorkerParameters,
    private val mainRepository: MainRepository,
    @IoDispatcher dispatcher: CoroutineDispatcher
) : CoroutineWorker(context, params) {

    private val notificationManager = context.getSystemService(
        Context.NOTIFICATION_SERVICE
    ) as NotificationManager

    private val localScope: CoroutineScope = CoroutineScope(SupervisorJob() + dispatcher)
    private val logger = LoggerFactory.getLogger(TAG)
    private lateinit var notificationBuilder: NotificationCompat.Builder

    @SuppressLint("MissingPermission")
    @Suppress("SwallowedException", "TooGenericExceptionCaught")
    override suspend fun doWork(): Result {
        return try {
            setForeground(createForegroundInfo())

            val notificationManager = NotificationManagerCompat.from(applicationContext)
            var previousState: AppState? = null
            val job = localScope.launch {
                mainRepository.getAppStateStream()
                    .takeWhile {
                        previousState == null || it.servicesState != ServicesState.Stopped
                    }
                    .collect {
                        logger.debug("$it")
                        val updateNotification = previousState?.let { previousState ->
                            previousState.bpm != it.bpm ||
                                previousState.isClientConnected != it.isClientConnected
                        } != false
                        if (updateNotification) {
                            updateNotification(it)
                            notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())
                        }
                        previousState = it
                    }
            }
            mainRepository.startServices()
            job.join()

            return Result.success()
        } catch (error: Throwable) {
            logger.error(error.localizedMessage, error)
            Result.failure()
        } finally {
            notificationManager.cancelAll()
        }
    }

    private fun createForegroundInfo(): ForegroundInfo {
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
                    applicationContext.getString(R.string.stop),
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

    private fun createNotificationChannel() {
        val name = applicationContext.getString(R.string.channel_1_name)
        val descriptionText = applicationContext.getString(R.string.channel_1_description)
        val importance = NotificationManager.IMPORTANCE_LOW
        val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
            description = descriptionText
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun updateNotification(appState: AppState) {
        notificationBuilder.apply {
            setContentText(
                applicationContext.getString(
                    R.string.bpm,
                    appState.bpm.toString()
                )
            )
            setContentTitle(
                applicationContext.getString(
                    if (appState.isClientConnected) {
                        R.string.client_connected
                    } else {
                        R.string.awaiting_client
                    }
                )
            )
        }
    }

    @WorkerKey(MainWorker::class)
    @ContributesIntoMap(
        AppScope::class,
        binding = binding<MetroWorkerFactory.WorkerInstanceFactory<*>>()
    )
    @AssistedFactory
    abstract class Factory : MetroWorkerFactory.WorkerInstanceFactory<MainWorker>
}
