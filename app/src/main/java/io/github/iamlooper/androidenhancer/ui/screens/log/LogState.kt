package io.github.iamlooper.androidenhancer.ui.screens.log

import androidx.compose.runtime.Immutable

@Immutable
data class LogState(
    val logEntries: List<String> = emptyList(),
    val isEmpty: Boolean = true,
    val isLoading: Boolean = false
)
