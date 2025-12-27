package io.github.iamlooper.androidenhancer

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import com.topjohnwu.superuser.Shell
import dagger.hilt.android.HiltAndroidApp
import io.github.iamlooper.androidenhancer.data.repository.AppRepository
import io.github.iamlooper.androidenhancer.system.root.RootIpc
import io.github.iamlooper.androidenhancer.system.util.Constants
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.io.PrintWriter
import java.io.StringWriter
import javax.inject.Inject

@HiltAndroidApp
class App : Application() {

    @Inject lateinit var repository: AppRepository

    init {
        Shell.enableVerboseLogging = false
        Shell.setDefaultBuilder(Shell.Builder.create()
            .setFlags(Shell.FLAG_MOUNT_MASTER)
            .setTimeout(10))
    }

    override fun onCreate() {
        super.onCreate()

        // Bypass hidden API restrictions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            HiddenApiBypass.addHiddenApiExemptions("")
        }

        setupGlobalExceptionHandler()

        createNotificationChannel()

        RootIpc.init(this)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = Constants.BOOT_CHANNEL_ID
            val name = getString(R.string.notification_channel_boot_service_name)
            val descriptionText = getString(R.string.notification_channel_boot_service_description)
            val importance = NotificationManager.IMPORTANCE_HIGH // HIGH is crucial for heads-up
            
            val channel = NotificationChannel(channelId, name, importance).apply {
                description = descriptionText
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun setupGlobalExceptionHandler() {
        val defaultExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val stackTrace = StringWriter().apply {
                PrintWriter(this).use { throwable.printStackTrace(it) }
            }.toString()

            val errorLog = buildString {
                append("Thread: ${thread.name}\n")
                append("Exception: ${throwable.javaClass.simpleName}\n")
                append("Message: ${throwable.message}\n\n")
                append("Stack Trace:\n")
                append(stackTrace)
            }

            try {
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Crash Report", errorLog))
            } catch (e: Exception) {
                e.printStackTrace()
            }

            defaultExceptionHandler?.uncaughtException(thread, throwable)
        }
    }
}