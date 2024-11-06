package com.looper.android_enhancer.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.jaredrummler.ktsh.Shell
import com.looper.android.support.util.AppUtil
import com.looper.android.support.util.NotificationUtil
import com.looper.android.support.util.SharedPreferencesUtil
import com.looper.android_enhancer.MyApp
import com.looper.android_enhancer.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class BootService : Service() {

    private lateinit var sharedPreferenceUtils: SharedPreferencesUtil
    private lateinit var libPath: String
    private lateinit var logFile: File
    private lateinit var logPath: String
    private lateinit var selectedTweaks: Set<String>

    override fun onCreate() {
        super.onCreate()

        try {
            MyApp.shell = Shell.SU
        } catch (e: Exception) {
            e.printStackTrace()
        }

        sharedPreferenceUtils = SharedPreferencesUtil(this)
        libPath = AppUtil.getAppNativeLibraryPath(this, "android_enhancer")
        logFile = File(filesDir, "android_enhancer_log.txt")
        logPath = logFile.toString()
        selectedTweaks =
            sharedPreferenceUtils.get("pref_tweaks_on_boot", emptySet())
    }

    @SuppressLint("InlinedApi")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Build foreground notification
        val foregroundNotification = NotificationUtil.createNotificationBuilder(
            this,
            "boot_notification",
            R.drawable.ic_rocket_launch
        )
            .setContentTitle(getString(R.string.boot_service))
            .setContentText(getString(R.string.tweaks_are_running))
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

        // Start the foreground service
        ServiceCompat.startForeground(
            this,
            1,
            foregroundNotification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )

        CoroutineScope(Dispatchers.Default).launch {
            // Check if the app has root access
            if (MyApp.shell?.isAlive() == true) {
                withContext(Dispatchers.IO) {
                    logFile.delete()
                    logFile.createNewFile()

                    for (selectedTweak in selectedTweaks) {
                        when (selectedTweak) {
                            "main_tweak" -> MyApp.shell?.run("$libPath -o $logPath -m")
                            "priority_tweak" -> MyApp.shell?.run("$libPath -o $logPath -p")
                            "art_tweak" -> MyApp.shell?.run("$libPath -o $logPath -r &")
                            "focused_app_optimizer" -> MyApp.shell?.run("$libPath -o $logPath -f")
                        }
                    }
                }
            }

            // Stop the service
            ServiceCompat.stopForeground(this@BootService, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }

        // Not restart the service if it gets terminated
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        // Don't need to provide binding for this service, so return null
        return null
    }
}