package io.github.iamlooper.androidenhancer.ui.screens.per_app_mode

import androidx.compose.runtime.Immutable
import io.github.iamlooper.androidenhancer.system.jni.AndroidEnhancerMode
import io.github.iamlooper.androidenhancer.system.util.InstalledApp

@Immutable
data class PerAppModeState(
    val apps: Map<String, AndroidEnhancerMode> = emptyMap(),
    val installedApps: List<InstalledApp> = emptyList(),
    val isLoading: Boolean = true,
    val updatingPackage: String? = null
)
