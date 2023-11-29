package com.looper.android_enhancer.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

import com.looper.android_enhancer.service.BootService

import com.looper.android.support.util.SharedPreferenceUtils

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_LOCKED_BOOT_COMPLETED || intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Initialize SharedPreferenceUtils instance.
            val sharedPreferenceUtils = SharedPreferenceUtils.getInstance(context)
            
            // Retrieve selected tweaks as a set of strings from shared preferences.
            val selectedTweaks = sharedPreferenceUtils.getStringSet("list_pref_execute_tweaks_on_boot", emptySet())
            
            // Reset memory tweak on boot.
            sharedPreferenceUtils.saveBoolean("switch_pref_memory_tweak", false)
            
            // Check if there are selected tweaks to start boot service.
            if (selectedTweaks.isNotEmpty()) { 
                val serviceIntent = Intent(context, BootService::class.java)
                context.startForegroundService(serviceIntent)
            }
        }
    }
}