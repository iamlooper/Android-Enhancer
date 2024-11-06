package com.looper.android_enhancer.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.looper.android.support.util.SharedPreferencesUtil
import com.looper.android_enhancer.service.BootService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val sharedPreferenceUtils = SharedPreferencesUtil(context)
            val selectedTweaks = sharedPreferenceUtils.get("pref_tweaks_on_boot", emptySet<String>())

            if (selectedTweaks.isNotEmpty()) { 
                val serviceIntent = Intent(context, BootService::class.java)
                context.startForegroundService(serviceIntent)
            }
        }
    }
}