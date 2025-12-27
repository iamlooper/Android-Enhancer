package io.github.iamlooper.androidenhancer.ui.screens.per_app_mode

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.iamlooper.androidenhancer.data.repository.AppRepository
import io.github.iamlooper.androidenhancer.system.jni.AndroidEnhancerMode
import io.github.iamlooper.androidenhancer.system.util.InstalledApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PerAppModeViewModel @Inject constructor(
    private val repository: AppRepository
) : ViewModel() {

    private val isLoading = MutableStateFlow(false)
    private val updatingPackage = MutableStateFlow<String?>(null)

    val state: StateFlow<PerAppModeState> = combine(
        repository.snapshot,
        repository.installedApps,
        isLoading,
        updatingPackage
    ) { snapshot, apps, loading, updating ->
        PerAppModeState(
            apps = snapshot.apps,
            installedApps = apps,
            isLoading = loading || apps.isEmpty(),
            updatingPackage = updating
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        PerAppModeState()
    )

    fun setModeOverride(app: InstalledApp, mode: AndroidEnhancerMode) {
        viewModelScope.launch {
            updatingPackage.value = app.packageName
            try {
                repository.setAppMode(app.packageName, mode)
            } finally {
                updatingPackage.value = null
            }
        }
    }

    fun removeModeOverride(packageName: String) {
        viewModelScope.launch {
            updatingPackage.value = packageName
            try {
                repository.removeAppMode(packageName)
            } finally {
                updatingPackage.value = null
            }
        }
    }
}
