package io.github.iamlooper.androidenhancer.ui.screens.about

import androidx.compose.runtime.Immutable

@Immutable
data class AboutAction(
    val titleRes: Int,
    val subtitleRes: Int,
    val uri: String,
    val type: AboutActionType
)

enum class AboutActionType {
    DEVELOPER,
    CHANNEL,
    CREDITS,
    SOURCE
}

@Immutable
data class AboutState(
    val actions: List<AboutAction> = emptyList()
)
