package com.looper.android_enhancer

import com.google.android.material.color.DynamicColors
import com.jaredrummler.ktsh.Shell
import com.looper.android.support.App
import com.looper.android.support.util.NotificationUtil

class MyApp : App() {

    companion object {
        var shell: Shell? = null
    }

    override fun onCreate() {
        super.onCreate()

        // Apply dynamic colors
        DynamicColors.applyToActivitiesIfAvailable(this)

        // Build notification channel for boot service
        NotificationUtil.buildNotificationChannel(
            this,
            "boot_notification",
            getString(R.string.boot_notification)
        )
    }
}