package com.looper.android_enhancer

import android.app.Application

import com.topjohnwu.superuser.Shell

import com.google.android.material.color.DynamicColors

class App : Application() {

    override fun onCreate() {
        super.onCreate()

        // Apply dynamic colors.
        DynamicColors.applyToActivitiesIfAvailable(this)
                
        // Check if the main shell is already cached.
        if (Shell.getCachedShell() == null) {
            // If not cached, configure settings for the main shell.
            Shell.setDefaultBuilder(Shell.Builder.create()
                .setTimeout(60)
            )
            
            // Preheat the main shell.
            Shell.getShell()
        }
    }
}