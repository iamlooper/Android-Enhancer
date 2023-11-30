package com.looper.android_enhancer.service

import java.io.File

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.content.pm.ServiceInfo

import androidx.preference.PreferenceManager
import androidx.preference.MultiSelectListPreference
import androidx.preference.SwitchPreferenceCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

import com.topjohnwu.superuser.Shell

import com.looper.android.support.util.NotificationUtils
import com.looper.android.support.util.ShellUtils
import com.looper.android.support.util.AppUtils
import com.looper.android.support.util.SharedPreferenceUtils

import com.looper.android_enhancer.R

class BootService : Service() {

    override fun onCreate() {
        super.onCreate()
        
        // Build notification channel for tweaks.
        NotificationUtils.buildChannel(
            this,
            "tweaks_notification",
            getString(R.string.notification_channel_tweaks_name)
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Build foreground notification.
        val foregroundNotification = NotificationCompat.Builder(this, "tweaks_notification")
            .setSmallIcon(R.drawable.ic_bolt)
            .setContentTitle(getString(R.string.notification_title_tweaks))
            .build()

        // Start the foreground service.
        ServiceCompat.startForeground(this, 1, foregroundNotification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE)
    
        // Check if the app has root access.
        if (Shell.isAppGrantedRoot() == true) {
            // Initialize SharedPreferenceUtils instance.
            val sharedPreferenceUtils = SharedPreferenceUtils.getInstance(this)
               
            val androidEnhancerLibraryPath: String = AppUtils.getNativeLibraryPath(this, "android_enhancer")
            val vmTouchLibraryPath: String = AppUtils.getNativeLibraryPath(this, "vmtouch")
            val logFilePath: String = File(this.filesDir, "log.txt").toString()

            // Copy VMTouch library, set appropriate permissions and remove existing log file. 
            ShellUtils.execute("cp -f $vmTouchLibraryPath /data/local/tmp/vmtouch")
            ShellUtils.execute("chmod 0777 $androidEnhancerLibraryPath")
            ShellUtils.execute("chmod 0777 /data/local/tmp/vmtouch")
            ShellUtils.execute("rm -f $logFilePath")
            
            // Retrieve selected tweaks as a set of strings from shared preferences.
            val selectedTweaks: Set<String> = sharedPreferenceUtils.getStringSet("list_pref_execute_tweaks_on_boot", emptySet())            
            
            // Loop through selected tweaks and execute corresponding commands.
            for (selectedTweak in selectedTweaks) {
                // Execute commands based on the selected tweak.
                when (selectedTweak) {
                    "main_tweak" -> ShellUtils.execute("$androidEnhancerLibraryPath --main-tweak 1>> $logFilePath &")
                    "dalvik_tweak" -> ShellUtils.execute("$androidEnhancerLibraryPath --dalvik-tweak 1>> $logFilePath &")
                    "clean_junk" -> ShellUtils.execute("$androidEnhancerLibraryPath --clean-junk 1>> $logFilePath &")
                    else -> {
                        ShellUtils.execute("$androidEnhancerLibraryPath --enable-memory-tweak 1>> $logFilePath &")
                        sharedPreferenceUtils.saveBoolean("switch_pref_memory_tweak", true)
                    }
                }
            }
                    
            // Show notification for executed tweaks.
            NotificationUtils.build(
                this,
                "tweaks_notification",
                2,
                "",
                getString(R.string.notification_title_tweaks),
                getString(R.string.notification_content_tweaks_executed),
                R.drawable.ic_bolt
            )
        } else {
            // Show notification for failed tweaks execution.
            NotificationUtils.build(
                this,
                "tweaks_notification",
                2,
                "",
                getString(R.string.notification_title_tweaks),
                getString(R.string.notification_content_tweaks_failed),
                R.drawable.ic_bolt
            )
        }

        // Stop the foreground service.
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)

        // Stop the service completely.
        stopSelf()

        // Return START_NOT_STICKY to not restart the service if it gets terminated.
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        // Don't need to provide binding for this service, so return null.
        return null
    }
}