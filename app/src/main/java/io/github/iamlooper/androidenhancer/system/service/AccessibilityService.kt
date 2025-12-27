package io.github.iamlooper.androidenhancer.system.service

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import dagger.hilt.android.AndroidEntryPoint
import io.github.iamlooper.androidenhancer.data.repository.AppRepository
import io.github.iamlooper.androidenhancer.system.util.BatteryUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@SuppressLint("AccessibilityPolicy")
@AndroidEntryPoint
class AccessibilityService : AccessibilityService() {

    @Inject lateinit var repository: AppRepository
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var systemEventReceiver: BroadcastReceiver? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        scope.launch {
            repository.setAccessibilityEnabled(true)
        }
        registerSystemEventReceiver()
        checkInitialBatteryLevel()
        sendScreenInfo()
    }

    private fun sendScreenInfo() {
        val displayMetrics = resources.displayMetrics
        repository.onScreenInfoChanged(
            displayMetrics.densityDpi,
            displayMetrics.widthPixels,
            displayMetrics.heightPixels
        )
    }

    private fun registerSystemEventReceiver() {
        systemEventReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_ON -> {
                        repository.onScreenStateChanged(true)
                    }
                    Intent.ACTION_SCREEN_OFF -> {
                        repository.onScreenStateChanged(false)
                    }
                    Intent.ACTION_BATTERY_CHANGED -> {
                        val info = BatteryUtil.extractBatteryInfo(context, intent)
                        if (info != null) {
                            repository.onBatteryInfoChanged(info.level, info.capacityMah, info.isCharging)
                        }
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_BATTERY_CHANGED)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(systemEventReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(systemEventReceiver, filter)
        }
    }

    private fun checkInitialBatteryLevel() {
        val batteryStatus = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val info = BatteryUtil.extractBatteryInfo(this, batteryStatus)
        if (info != null) {
            repository.onBatteryInfoChanged(info.level, info.capacityMah, info.isCharging)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        repository.onForegroundApp(pkg)
    }

    override fun onInterrupt() {
        // no-op
    }

    override fun onUnbind(intent: Intent?): Boolean {
        systemEventReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (_: IllegalArgumentException) {
                // Already unregistered
            }
        }
        systemEventReceiver = null
        return super.onUnbind(intent)
    }
}
