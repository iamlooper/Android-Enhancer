package com.looper.android_enhancer.viewmodel

import android.app.Application
import android.content.Context
import android.os.FileObserver
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.looper.android.support.util.FileUtil
import com.looper.android.support.util.IntentUtil
import com.looper.android_enhancer.R
import com.looper.android_enhancer.model.LogEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class LogViewModel(application: Application) : AndroidViewModel(application) {
    private val context = getApplication<Application>()
    private val logFile = File(application.filesDir, "android_enhancer_log.txt")
    private var fileObserver: FileObserver? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var currentId = 0L

    private val _logEntries = MutableStateFlow<List<LogEntry>>(emptyList())
    val logEntries: StateFlow<List<LogEntry>> = _logEntries.asStateFlow()

    private val _isEmpty = MutableStateFlow(true)
    val isEmpty: StateFlow<Boolean> = _isEmpty.asStateFlow()

    init {
        if (!logFile.exists()) {
            logFile.createNewFile()
        }
        startWatchingLogFile()
        updateLogContent()
    }

    @Suppress("DEPRECATION")
    private fun startWatchingLogFile() {
        val parentDir = logFile.parentFile?.absolutePath ?: return

        fileObserver = object : FileObserver(parentDir, MODIFY) {
            override fun onEvent(event: Int, path: String?) {
                if (path == null) return

                if (path == logFile.name) {
                    when (event) {
                        MODIFY -> {
                            updateLogContent()
                        }
                    }
                }
            }
        }
        fileObserver?.startWatching()
    }

    private fun stopWatchingLogFile() {
        fileObserver?.stopWatching()
        fileObserver = null
    }

    private fun updateLogContent() {
        viewModelScope.launch(Dispatchers.IO) {
            if (logFile.exists() && logFile.length() > 0) {
                val log = FileUtil.read(logFile)
                val logLines = log.split("\n")
                    .filter { it.isNotBlank() }
                val currentEntries = _logEntries.value

                if (logLines.size > currentEntries.size) {
                    // Only create new LogEntry objects for new lines
                    val newEntries = logLines.drop(currentEntries.size).map { line ->
                        LogEntry(currentId++, line)
                    }
                    _logEntries.value = currentEntries + newEntries
                    _isEmpty.value = false
                }
            } else {
                currentId = 0L
                _logEntries.value = emptyList()
                _isEmpty.value = true
            }
        }
    }

    fun clearLog(showSnackbar: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            if (logFile.exists()) {
                if (logFile.length() > 0) {
                    logFile.writeText("")
                    currentId = 0L
                    updateLogContent()
                    showSnackbar(context.getString(R.string.log_cleared_successfully))
                } else {
                    showSnackbar(context.getString(R.string.no_log_exists_to_clear))
                }
            } else {
                showSnackbar(context.getString(R.string.no_log_exists_to_clear))
            }
        }
    }

    fun shareLog(context: Context, showSnackbar: (String) -> Unit) {
        if (logFile.exists() && logFile.length() > 0) {
            IntentUtil.shareFile(context, logFile, "text/plain")
        } else {
            showSnackbar(context.getString(R.string.no_log_exists_to_share))
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopWatchingLogFile()
        mainHandler.removeCallbacksAndMessages(null)
    }
}