package io.github.iamlooper.androidenhancer.data.repository

import android.content.Context
import android.os.FileObserver
import com.topjohnwu.superuser.Shell
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.iamlooper.androidenhancer.data.local.PreferencesSnapshot
import io.github.iamlooper.androidenhancer.data.local.appDataStore
import io.github.iamlooper.androidenhancer.data.local.appLogFile
import io.github.iamlooper.androidenhancer.data.local.sysfsBackupFile
import io.github.iamlooper.androidenhancer.data.local.snapshotFlow
import io.github.iamlooper.androidenhancer.data.local.updateSnapshot
import io.github.iamlooper.androidenhancer.system.jni.AndroidEnhancerMode
import io.github.iamlooper.androidenhancer.system.root.RootIpc
import io.github.iamlooper.androidenhancer.system.util.AppUtil
import io.github.iamlooper.androidenhancer.system.util.InstalledApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppRepository @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    private val dataStore = context.appDataStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val initializationMutex = Mutex()
    private val _isRunning = MutableStateFlow(false)

    val isRunning: StateFlow<Boolean> = _isRunning

    private val _installedApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val installedApps: StateFlow<List<InstalledApp>> = _installedApps

    val snapshot: StateFlow<PreferencesSnapshot> =
        dataStore.snapshotFlow()
            .stateIn(scope, SharingStarted.Eagerly, PreferencesSnapshot())

    val mode: StateFlow<AndroidEnhancerMode> = snapshot.map { it.mode }
        .stateIn(scope, SharingStarted.Eagerly, AndroidEnhancerMode.AUTO)

    private val logWatcher = LogFileWatcher.ensure(context, scope)

    val logStream: Flow<List<String>> = logWatcher.lines

    val logPreview: Flow<List<String>> = logStream.map { it.takeLast(6) }

    private var lastPushedApp: String? = null
    private var lastPushTime: Long = 0

    fun logFile(): File = appLogFile(context)

    fun refreshLog() {
        logWatcher.forceRefresh()
    }

    init {
        scope.launch {
            refreshInstalledApps()
        }
        scope.launch {
            try {
                ensureLogPath()
                synchronizeBridge()
            } catch (_: Exception) {
                // Silently ignore exceptions during init
            }
        }
    }

    fun refreshInstalledApps() {
        scope.launch(Dispatchers.Default) {
            val apps = AppUtil.installedLaunchableApps(context)
            _installedApps.value = apps
        }
    }


    private fun ensureLogPath(): String {
        val logFile = appLogFile(context)
        logFile.parentFile?.let { parent ->
            if (!parent.exists()) {
                parent.mkdirs()
            }
        }
        if (!logFile.exists()) {
            logFile.createNewFile()
        }
        return logFile.absolutePath
    }

    private fun ensureSysfsBackupPath(): String {
        val backupFile = sysfsBackupFile(context)
        backupFile.parentFile?.let { parent ->
            if (!parent.exists()) {
                parent.mkdirs()
            }
        }
        // Don't create empty file - Rust will create it when first backup is written
        return backupFile.absolutePath
    }

    private suspend fun synchronizeBridge() {
        val logPath = ensureLogPath()
        // Update DataStore with the log path
        dataStore.updateSnapshot { it.copy(logPath = logPath) }
    }

    private suspend fun ensureBridgeInitialized(logPath: String): Boolean {
        if (logPath.isEmpty()) return false
        if (Shell.isAppGrantedRoot() != true) return false
        
        return initializationMutex.withLock {
            if (_isRunning.value) {
                true
            } else {
                val sysfsBackupPath = ensureSysfsBackupPath()
                val running = RootIpc.invoke { svc ->
                    svc.isRunning || svc.start(logPath, sysfsBackupPath)
                } ?: false
                _isRunning.value = running
                running
            }
        }
    }

    private suspend fun setSystemAccessibilityEnabled(enabled: Boolean) {
        // Use secure settings to toggle our AccessibilityService using root shell.
        val component = "io.github.iamlooper.androidenhancer/io.github.iamlooper.androidenhancer.system.service.AccessibilityService"

        val script = if (enabled) {
            """
            service="$component"
            current=${'$'}(settings get secure enabled_accessibility_services || echo "")
            if [ "${'$'}current" = "null" ] || [ -z "${'$'}current" ]; then
                new="${'$'}service"
            elif echo "${'$'}current" | tr ':' '\n' | grep -qx "${'$'}service"; then
                new="${'$'}current"
            else
                new="${'$'}current:${'$'}service"
            fi
            settings put secure enabled_accessibility_services "${'$'}new"
            settings put secure accessibility_enabled 1
            """.trimIndent()
        } else {
            """
            service="$component"
            current=${'$'}(settings get secure enabled_accessibility_services || echo "")
            if [ "${'$'}current" = "null" ] || [ -z "${'$'}current" ]; then
                new=""
            else
                new=""
                OLD_IFS="${'$'}IFS"
                IFS=':'
                for entry in ${'$'}current; do
                    if [ "${'$'}entry" != "${'$'}service" ] && [ -n "${'$'}entry" ]; then
                        if [ -z "${'$'}new" ]; then
                            new="${'$'}entry"
                        else
                            new="${'$'}new:${'$'}entry"
                        fi
                    fi
                done
                IFS="${'$'}OLD_IFS"
            fi
            if [ -z "${'$'}new" ]; then
                settings put secure enabled_accessibility_services ""
                settings put secure accessibility_enabled 0
            else
                settings put secure enabled_accessibility_services "${'$'}new"
            fi
            """.trimIndent()
        }

        RootIpc.invoke { svc ->
            val commands = mutableListOf(script)
            svc.executeShellCommand(commands)
        }
    }

    suspend fun setMode(mode: AndroidEnhancerMode) {
        dataStore.updateSnapshot { it.copy(mode = mode) }
        val current = snapshot.value
        if (current.serviceEnabled) {
            val logPath = ensureLogPath()
            if (ensureBridgeInitialized(logPath)) {
                RootIpc.invoke { it.setMode(mode.code) }
            }
        }
    }

    suspend fun setServiceEnabled(enabled: Boolean) {
        dataStore.updateSnapshot { it.copy(serviceEnabled = enabled) }
        
        // Launch the shutdown/startup in background to avoid blocking UI
        scope.launch {
            if (!enabled) {
                // Explicitly shut down the service when disabled
                initializationMutex.withLock {
                    RootIpc.invoke { svc -> svc.stop() }
                    _isRunning.value = false
                }
                setSystemAccessibilityEnabled(false)
            } else {
                // Trigger re-initialization when enabled and sync state
                val logPath = ensureLogPath()
                val current = snapshot.value
                val initialized = ensureBridgeInitialized(logPath)
                if (initialized) {
                    // Sync current mode, app overrides, and touch boost to native
                    RootIpc.invoke { svc ->
                        svc.setMode(current.mode.code)
                        svc.setTouchBoostEnabled(current.touchBoostEnabled)
                        current.apps.forEach { (pkg, mode) ->
                            if (mode != AndroidEnhancerMode.AUTO) {
                                svc.setAppOverride(pkg, mode.code)
                            }
                        }
                    }
                    setSystemAccessibilityEnabled(true)
                }
            }
        }
    }

    suspend fun startService(): Boolean {
        val current = snapshot.value
        // Only start if explicitly enabled by user
        if (!current.serviceEnabled) {
            return false
        }

        val logPath = ensureLogPath()
        val initialized = ensureBridgeInitialized(logPath)
        if (initialized) {
            // Sync current mode, app overrides, and touch boost to native
            RootIpc.invoke { svc ->
                svc.setMode(current.mode.code)
                svc.setTouchBoostEnabled(current.touchBoostEnabled)
                current.apps.forEach { (pkg, mode) ->
                    if (mode != AndroidEnhancerMode.AUTO) {
                        svc.setAppOverride(pkg, mode.code)
                    }
                }
            }
            setSystemAccessibilityEnabled(true)
        }
        return initialized
    }

    suspend fun setAccessibilityEnabled(enabled: Boolean) {
        dataStore.updateSnapshot { it.copy(accessibilityEnabled = enabled) }
    }

    suspend fun notifyLogCleared() {
        // Delegate log clearing to the native service to avoid file descriptor conflicts
        if (_isRunning.value) {
            RootIpc.invoke { it.clearLog() }
        } else {
             // If service isn't running, we can safely clear it from here (or try to)
             // but better to just truncate it.
             val file = appLogFile(context)
             if (file.exists()) {
                 file.writeText("")
             }
        }
        logWatcher.forceRefresh()
    }

    suspend fun setAppMode(packageName: String, mode: AndroidEnhancerMode) {
        dataStore.updateSnapshot { prefs ->
            val updated = prefs.apps.toMutableMap()
            if (mode == AndroidEnhancerMode.AUTO) {
                updated.remove(packageName)
            } else {
                updated[packageName] = mode
            }
            prefs.copy(apps = updated)
        }
        // Event driven update
        val current = snapshot.value
        if (current.serviceEnabled) {
            val logPath = ensureLogPath()
            if (ensureBridgeInitialized(logPath)) {
                RootIpc.invoke { 
                    if (mode == AndroidEnhancerMode.AUTO) {
                        it.removeAppOverride(packageName)
                    } else {
                        it.setAppOverride(packageName, mode.code)
                    }
                }
            }
        }
    }

    suspend fun removeAppMode(packageName: String) {
        dataStore.updateSnapshot { prefs ->
            val updated = prefs.apps.toMutableMap()
            updated.remove(packageName)
            prefs.copy(apps = updated)
        }

        val current = snapshot.value
        if (current.serviceEnabled) {
            val logPath = ensureLogPath()
            if (ensureBridgeInitialized(logPath)) {
                RootIpc.invoke { it.removeAppOverride(packageName) }
            }
        }
    }
    
    fun onForegroundApp(packageName: String) {
        scope.launch {
            val current = snapshot.first()
            
            // Per-app overrides only work in Auto mode - skip processing otherwise
            if (current.mode != AndroidEnhancerMode.AUTO) {
                return@launch
            }
            
            val currentTime = System.currentTimeMillis()
            if (packageName == lastPushedApp && (currentTime - lastPushTime) < 2000) {
                return@launch
            }
            
            dataStore.updateSnapshot { prefs ->
                prefs.copy(currentApp = packageName)
            }

            // Only push foreground app if service is already running.
            // Don't auto-start - that should only happen via explicit startService() calls.
            if (current.serviceEnabled && _isRunning.value) {
                RootIpc.invoke { it.pushForegroundApp(packageName) }
                lastPushedApp = packageName
                lastPushTime = currentTime
            }
        }
    }

    fun onScreenStateChanged(isOn: Boolean) {
        scope.launch {
            val current = snapshot.first()
            // Only update screen state if service is already running.
            if (current.serviceEnabled && _isRunning.value) {
                RootIpc.invoke { it.setScreenState(isOn) }
            }
        }
    }

    fun onBatteryInfoChanged(level: Int, capacityMah: Int, isCharging: Boolean) {
        scope.launch {
            val current = snapshot.first()
            // Only update battery info if service is already running.
            if (current.serviceEnabled && _isRunning.value) {
                RootIpc.invoke { it.setBatteryInfo(level, capacityMah, isCharging) }
            }
        }
    }

    fun onScreenInfoChanged(dpi: Int, widthPx: Int, heightPx: Int) {
        scope.launch {
            val current = snapshot.first()
            // Only update screen info if service is already running.
            if (current.serviceEnabled && _isRunning.value) {
                RootIpc.invoke { it.setScreenInfo(dpi, widthPx, heightPx) }
            }
        }
    }
}

private class LogFileWatcher private constructor(
    private val file: File,
    private val scope: CoroutineScope
) {
    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines
    private val observer = @Suppress("DEPRECATION") object : FileObserver(
        file.parentFile?.absolutePath ?: file.absolutePath,
        MODIFY or CLOSE_WRITE or CREATE or DELETE
    ) {
        override fun onEvent(event: Int, path: String?) {
            if (path == null) return
            if (path == file.name) {
                scheduleRefresh()
            }
        }
    }

    init {
        observer.startWatching()
        scope.launch {
            refreshImmediate()
        }
    }

    fun forceRefresh() {
        scope.launch { refreshImmediate() }
    }

    private var refreshJob: Job? = null
    private var lastRefreshTime = 0L

    private fun scheduleRefresh() {
        val now = System.currentTimeMillis()
        // Throttle updates to max once per 1.5s during continuous writes
        if (now - lastRefreshTime < 1500) {
            refreshJob?.cancel()
            refreshJob = scope.launch {
                delay(1500)
                refreshImmediate()
            }
        } else {
            scope.launch { refreshImmediate() }
        }
    }

    private fun refreshImmediate() {
        lastRefreshTime = System.currentTimeMillis()
        runCatching {
            if (!file.exists()) {
                file.parentFile?.mkdirs()
                file.createNewFile()
                _lines.value = emptyList()
                return
            }
            // Read only the last 1000 lines for performance
            val allLines = file.useLines { lines ->
                val buffer = ArrayDeque<String>(MAX_LOG_LINES + 1)
                for (line in lines) {
                    if (buffer.size >= MAX_LOG_LINES) {
                        buffer.removeFirst()
                    }
                    buffer.addLast(line)
                }
                buffer.toList()
            }
            _lines.value = allLines
        }
    }

    companion object {
        private const val MAX_LOG_LINES = 1000
        private var watcher: LogFileWatcher? = null

        fun ensure(context: Context, scope: CoroutineScope): LogFileWatcher {
            return watcher ?: synchronized(this) {
                watcher ?: LogFileWatcher(appLogFile(context), scope).also { watcher = it }
            }
        }
    }
}
