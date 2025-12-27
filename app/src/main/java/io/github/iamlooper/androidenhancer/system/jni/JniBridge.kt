package io.github.iamlooper.androidenhancer.system.jni

object JniBridge {
    init {
        System.loadLibrary("androidenhancer")
    }

    external fun start(logPath: String?, sysfsBackupPath: String?): Boolean
    external fun stop()
    external fun isRunning(): Boolean
    external fun getMode(): Int
    external fun setMode(modeCode: Int): Int
    external fun pushForegroundApp(packageName: String)
    external fun setAppOverride(packageName: String, modeCode: Int)
    external fun removeAppOverride(packageName: String)
    external fun clearLog()
    external fun setScreenState(isOn: Boolean)
    external fun setBatteryInfo(level: Int, capacityMah: Int, isCharging: Boolean)
    external fun setScreenInfo(dpi: Int, widthPx: Int, heightPx: Int)
    external fun setTouchBoostEnabled(enabled: Boolean)
    external fun isTouchBoostEnabled(): Boolean
}