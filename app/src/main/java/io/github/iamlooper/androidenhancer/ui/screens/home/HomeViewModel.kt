package io.github.iamlooper.androidenhancer.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.iamlooper.androidenhancer.data.repository.AppRepository
import io.github.iamlooper.androidenhancer.system.jni.AndroidEnhancerMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: AppRepository
) : ViewModel() {

    private val logPreviewFlow = repository.logPreview.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        emptyList()
    )
    private val modeUpdating = MutableStateFlow(false)

    val uiState: StateFlow<HomeState> = combine(
        repository.snapshot,
        repository.isRunning,
        logPreviewFlow,
        repository.installedApps,
        modeUpdating
    ) { snapshot, running, logs, apps, isUpdating ->
        HomeState(
            mode = snapshot.mode,
            apps = snapshot.apps,
            currentApp = snapshot.currentApp,
            accessibilityEnabled = snapshot.accessibilityEnabled,
            androidEnhancerRunning = running,
            logPreview = logs,
            installedApps = apps,
            isModeUpdating = isUpdating,
            serviceEnabled = snapshot.serviceEnabled
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        HomeState()
    )

    fun setMode(mode: AndroidEnhancerMode) {
        viewModelScope.launch {
            modeUpdating.value = true
            try {
                repository.setMode(mode)
            } finally {
                modeUpdating.value = false
            }
        }
    }

    fun toggleService(enabled: Boolean) {
        viewModelScope.launch {
            repository.setServiceEnabled(enabled)
        }
    }

}
