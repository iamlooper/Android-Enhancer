package io.github.iamlooper.androidenhancer.ui.screens.about

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.iamlooper.androidenhancer.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AboutViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(AboutState())
    val state: StateFlow<AboutState> = _state
        .stateIn(viewModelScope, SharingStarted.Eagerly, AboutState())

    init {
        viewModelScope.launch {
            _state.value = AboutState(
                actions = listOf(
                    AboutAction(
                        titleRes = R.string.developer,
                        subtitleRes = R.string.looper,
                        uri = "https://github.com/iamlooper",
                        type = AboutActionType.DEVELOPER
                    ),
                    AboutAction(
                        titleRes = R.string.release_channel,
                        subtitleRes = R.string.release_channel_desc,
                        uri = "https://t.me/loopprojects",
                        type = AboutActionType.CHANNEL
                    ),
                    AboutAction(
                        titleRes = R.string.credits,
                        subtitleRes = R.string.credits_desc,
                        uri = "https://github.com/iamlooper/Android-Enhancer/tree/main#credits-",
                        type = AboutActionType.CREDITS
                    ),
                    AboutAction(
                        titleRes = R.string.source_code,
                        subtitleRes = R.string.view_on_github,
                        uri = "https://github.com/iamlooper/Android-Enhancer",
                        type = AboutActionType.SOURCE
                    )
                )
            )
        }
    }
}
