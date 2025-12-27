package io.github.iamlooper.androidenhancer.ui.screens.log

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.iamlooper.androidenhancer.BuildConfig
import io.github.iamlooper.androidenhancer.R
import io.github.iamlooper.androidenhancer.data.repository.AppRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class LogViewModel @Inject constructor(
    private val repository: AppRepository,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    private val logFile: File = repository.logFile()
    private val _isLoading = MutableStateFlow(false)
    val state: StateFlow<LogState> = combine(repository.logStream, _isLoading) { entries, loading ->
        LogState(entries, entries.isEmpty(), loading)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, LogState())

    init {
        // Initial refresh to populate log entries
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            try {
                repository.refreshLog()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearLog(onMessage: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            try {
                if (!logFile.exists()) {
                    onMessage(context.getString(R.string.no_log_exists_to_clear))
                    return@launch
                }
                // Delete and recreate to ensure clean state and trigger watchers reliably
                if (logFile.delete()) {
                    logFile.createNewFile()
                } else {
                    // Fallback to truncation if delete fails
                    logFile.writeText("")
                }
                repository.notifyLogCleared()
                onMessage(context.getString(R.string.log_cleared_successfully))
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun shareLog(context: Context, onMessage: (String) -> Unit) {
        if (!logFile.exists() || logFile.length() == 0L) {
            onMessage(context.getString(R.string.no_log_exists_to_share))
            return
        }
        
        // Clean up old cached log share files to prevent unbounded cache growth
        context.cacheDir.listFiles()
            ?.filter { it.name.startsWith("androidenhancer_log_") && it.name.endsWith(".log") }
            ?.forEach { it.delete() }
        
        // Create a copy with dated filename for sharing
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US)
        val timestamp = dateFormat.format(Date())
        val shareFile = File(context.cacheDir, "androidenhancer_log_$timestamp.log")
        logFile.copyTo(shareFile, overwrite = true)
        
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${BuildConfig.APPLICATION_ID}.provider",
            shareFile
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share_file)))
    }
}
