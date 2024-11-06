package com.looper.android_enhancer.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.looper.android.support.util.AppUtil
import com.looper.android.support.util.SharedPreferencesUtil
import com.looper.android_enhancer.MyApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val sharedPreferencesUtils = SharedPreferencesUtil(getApplication())
    private val logFile = File(application.filesDir, "android_enhancer_log.txt")
    private val logPath = logFile.toString()
    private val libPath = AppUtil.getAppNativeLibraryPath(getApplication(), "android_enhancer")

    private val _selectedTweaks = MutableStateFlow<Set<String>>(emptySet())
    val selectedTweaks: StateFlow<Set<String>> = _selectedTweaks.asStateFlow()

    private val _focusedAppOptimizerEnabled = MutableStateFlow(false)
    val focusedAppOptimizerEnabled: StateFlow<Boolean> = _focusedAppOptimizerEnabled.asStateFlow()

    init {
        // Initialize selected tweaks from preferences
        _selectedTweaks.value = sharedPreferencesUtils.get(
            "pref_tweaks_on_boot",
            emptySet()
        )

        // Check if focused app optimizer is running
        viewModelScope.launch(Dispatchers.IO) {
            val isRunning =
                MyApp.shell?.run("$libPath -s")?.exitCode == 0
            _focusedAppOptimizerEnabled.value = isRunning
        }

        // Ensure log file exists
        if (!logFile.exists()) {
            logFile.createNewFile()
        }
    }

    fun checkRootAccess(onResult: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val hasRoot = MyApp.shell?.isAlive() == true
            withContext(Dispatchers.Main) {
                onResult(hasRoot)
            }
        }
    }

    fun executeAndroidEnhancer(
        flag: String,
        runInBg: Boolean = false,
        onComplete: () -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            if (MyApp.shell?.isAlive() == true) {
                withContext(Dispatchers.IO) {
                    if (runInBg) {
                        MyApp.shell?.run("$libPath -o $logPath $flag &")
                    } else {
                        MyApp.shell?.run("$libPath -o $logPath $flag")
                    }
                }
                withContext(Dispatchers.Main) {
                    onComplete()
                }
            }
        }
    }

    fun enableFocusedAppOptimizr() {
        viewModelScope.launch(Dispatchers.IO) {
            if (MyApp.shell?.isAlive() == true) {
                _focusedAppOptimizerEnabled.value = true
            }
        }
    }

    fun toggleFocusedAppOptimizer(enabled: Boolean, onComplete: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            if (MyApp.shell?.isAlive() == true) {
                executeAndroidEnhancer("-f") {
                    _focusedAppOptimizerEnabled.value = enabled
                    onComplete()
                }
            }
        }
    }

    fun updateSelectedTweaks(newValues: Set<String>) {
        viewModelScope.launch {
            _selectedTweaks.value = newValues
            sharedPreferencesUtils.put("pref_tweaks_on_boot", newValues)
        }
    }
}