package io.github.iamlooper.androidenhancer.system.service

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import io.github.iamlooper.androidenhancer.R
import io.github.iamlooper.androidenhancer.data.repository.AppRepository
import io.github.iamlooper.androidenhancer.system.util.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class BootService : Service() {

    @Inject lateinit var repository: AppRepository

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val channelId = Constants.BOOT_CHANNEL_ID

        val builder = NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.notification_title_boot))
            .setContentText(getString(R.string.notification_text_boot_init))
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setOnlyAlertOnce(true)

        // SDK 31+ immediate notification flag
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }

        try {
            val notification = builder.build()

            // SDK 34+ startForeground check
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                try {
                    startForeground(
                        Constants.NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                    )
                } catch (_: Exception) {
                    // Just in case
                    startForeground(1, notification)
                }
            } else {
                startForeground(Constants.NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        serviceScope.launch {
            try {
                performTask()
                delay(5000)
            } finally {
                withContext(Dispatchers.Main) {
                    stopServiceAndNotification()
                }
            }
        }

        return START_NOT_STICKY
    }

    private fun stopServiceAndNotification() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private suspend fun performTask() {
        try {
            // Force-enable the service on boot since startOnBoot is true.
            // This ensures the service starts regardless of previous serviceEnabled state.
            repository.setServiceEnabled(true)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
