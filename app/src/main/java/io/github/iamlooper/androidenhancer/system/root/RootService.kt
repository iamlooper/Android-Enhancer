package io.github.iamlooper.androidenhancer.system.root

import android.content.Intent
import android.os.IBinder
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicBoolean
import com.topjohnwu.superuser.ipc.RootService as SuperUserRootService
import io.github.iamlooper.androidenhancer.system.jni.JniBridge

class RootService : SuperUserRootService() {

    private val binder by lazy { ServiceImpl() }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onDestroy() {
        binder.onServiceDestroyed()
        super.onDestroy()
    }

    private class ServiceImpl : IAndroidEnhancerService.Stub() {
        private val running = AtomicBoolean(false)

        override fun start(logPath: String?, sysfsBackupPath: String?): Boolean {
            val result = runCatching { JniBridge.start(logPath, sysfsBackupPath) }
            val success = result.getOrElse { throwable ->
                throwable.printStackTrace()
                false
            }
            if (success && JniBridge.isRunning()) {
                running.set(true)
            }
            return running.get()
        }

        override fun stop() {
            runCatching { JniBridge.stop() }
            running.set(false)
        }

        override fun isRunning(): Boolean = running.get()

        override fun setMode(modeCode: Int): Int {
            if (!running.get()) return -1
            return runCatching { JniBridge.setMode(modeCode) }
                .getOrElse { throwable ->
                    throwable.printStackTrace()
                    -1
                }
        }

        override fun getMode(): Int {
            return runCatching { JniBridge.getMode() }
                .getOrElse { throwable ->
                    throwable.printStackTrace()
                    -1
                }
        }

        override fun pushForegroundApp(packageName: String?) {
            if (!running.get() || packageName.isNullOrBlank()) return
            runCatching {
                JniBridge.pushForegroundApp(packageName)
            }.onFailure { throwable ->
                throwable.printStackTrace()
            }
        }

        override fun setAppOverride(packageName: String?, modeCode: Int) {
            if (!running.get() || packageName.isNullOrBlank()) return
            runCatching {
                JniBridge.setAppOverride(packageName, modeCode)
            }.onFailure { throwable ->
                throwable.printStackTrace()
            }
        }

        override fun removeAppOverride(packageName: String?) {
            if (!running.get() || packageName.isNullOrBlank()) return
            runCatching {
                JniBridge.removeAppOverride(packageName)
            }.onFailure { throwable ->
                throwable.printStackTrace()
            }
        }

        override fun clearLog() {
            if (!running.get()) return
            runCatching { JniBridge.clearLog() }
                .onFailure { throwable ->
                    throwable.printStackTrace()
                }
        }

        override fun setScreenState(isOn: Boolean) {
            if (!running.get()) return
            runCatching { JniBridge.setScreenState(isOn) }
                .onFailure { throwable ->
                    throwable.printStackTrace()
                }
        }

        override fun setBatteryInfo(level: Int, capacityMah: Int, isCharging: Boolean) {
            if (!running.get()) return
            runCatching { JniBridge.setBatteryInfo(level, capacityMah, isCharging) }
                .onFailure { throwable ->
                    throwable.printStackTrace()
                }
        }

        override fun setScreenInfo(dpi: Int, widthPx: Int, heightPx: Int) {
            if (!running.get()) return
            runCatching { JniBridge.setScreenInfo(dpi, widthPx, heightPx) }
                .onFailure { throwable ->
                    throwable.printStackTrace()
                }
        }

        override fun setTouchBoostEnabled(enabled: Boolean) {
            if (!running.get()) return
            runCatching { JniBridge.setTouchBoostEnabled(enabled) }
                .onFailure { throwable ->
                    throwable.printStackTrace()
                }
        }

        override fun isTouchBoostEnabled(): Boolean {
            if (!running.get()) return true
            return runCatching { JniBridge.isTouchBoostEnabled() }
                .getOrElse { throwable ->
                    throwable.printStackTrace()
                    true
                }
        }

        override fun executeShellCommand(commands: MutableList<String>?): String {
            if (commands.isNullOrEmpty()) {
                return ""
            }
            return try {
                // Join commands with " && " to stop on first failure; use /system/bin/sh
                val joined = commands.joinToString(" && ")
                val process = ProcessBuilder("/system/bin/sh", "-c", joined)
                    .redirectErrorStream(true)
                    .start()

                val output = StringBuilder()
                BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                    var line: String? = reader.readLine()
                    while (line != null) {
                        output.appendLine(line)
                        line = reader.readLine()
                    }
                }
                process.waitFor()
                output.toString().trimEnd()
            } catch (e: Exception) {
                e.printStackTrace()
                ""
            }
        }

        fun onServiceDestroyed() {
            runCatching { JniBridge.stop() }
            running.set(false)
        }
    }

}
