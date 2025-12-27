package io.github.iamlooper.androidenhancer.ui.screens.home

import androidx.compose.runtime.Immutable
import io.github.iamlooper.androidenhancer.system.jni.AndroidEnhancerMode
import io.github.iamlooper.androidenhancer.system.util.InstalledApp

@Immutable
data class HomeState(
    val mode: AndroidEnhancerMode = AndroidEnhancerMode.AUTO,
    val apps: Map<String, AndroidEnhancerMode> = emptyMap(),
    val currentApp: String = "",
    val accessibilityEnabled: Boolean = false,
    val androidEnhancerRunning: Boolean = false,
    val logPreview: List<String> = emptyList(),
    val installedApps: List<InstalledApp> = emptyList(),
    val isModeUpdating: Boolean = false,
    val serviceEnabled: Boolean = true
)
