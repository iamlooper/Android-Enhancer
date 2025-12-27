package io.github.iamlooper.androidenhancer.system.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.BatteryManager

object BatteryUtil {
    private const val POWER_PROFILE_CLASS = "com.android.internal.os.PowerProfile"
    
    private var cachedCapacity: Int = 0

    /**
     * Battery info data class.
     */
    data class BatteryInfo(
        val level: Int,
        val capacityMah: Int,
        val isCharging: Boolean
    )

    /**
     * Get battery design capacity in mAh using PowerProfile hidden API.
     * Returns cached value after first successful call.
     * Falls back to 4000mAh if reflection fails.
     */
    @SuppressLint("PrivateApi")
    fun getBatteryCapacity(context: Context): Int {
        if (cachedCapacity > 0) return cachedCapacity
        
        return try {
            val powerProfile = Class.forName(POWER_PROFILE_CLASS)
                .getConstructor(Context::class.java)
                .newInstance(context)
            
            val capacity = Class.forName(POWER_PROFILE_CLASS)
                .getMethod("getBatteryCapacity")
                .invoke(powerProfile) as Double
            
            cachedCapacity = capacity.toInt().coerceAtLeast(1000)
            cachedCapacity
        } catch (_: Exception) {
            // Fallback to reasonable default
            4000
        }
    }

    /**
     * Extract battery info from ACTION_BATTERY_CHANGED intent.
     */
    fun extractBatteryInfo(context: Context, intent: Intent?): BatteryInfo? {
        intent ?: return null
        
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level == -1 || scale == -1) return null
        
        val levelPct = (level * 100 / scale.toFloat()).toInt()
        val capacityMah = getBatteryCapacity(context)
        
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || 
                         status == BatteryManager.BATTERY_STATUS_FULL
        
        return BatteryInfo(levelPct, capacityMah, isCharging)
    }
}
