package io.github.iamlooper.androidenhancer.ui.screens.settings

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.iamlooper.androidenhancer.data.local.appDataStore
import io.github.iamlooper.androidenhancer.data.local.snapshotFlow
import io.github.iamlooper.androidenhancer.data.local.updateSnapshot
import io.github.iamlooper.androidenhancer.system.root.RootIpc
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    private val dataStore = context.appDataStore

    // Flow to track the current app locale from AppCompat
    // Re-emit whenever locale changes by observing a trigger
    private val localeRefreshTrigger = MutableStateFlow(0)

    val state: StateFlow<SettingsState> = combine(
        dataStore.snapshotFlow(),
        localeRefreshTrigger
    ) { snapshot, _ ->
        // Read current locale directly from AppCompat (it handles persistence)
        val currentLocales = AppCompatDelegate.getApplicationLocales()
        val currentLocaleTag = if (currentLocales.isEmpty) {
            ""
        } else {
            currentLocales.get(0)?.toLanguageTag() ?: ""
        }
        val languageMode = LanguageMode.fromLocaleTag(currentLocaleTag)

        SettingsState(
            startOnBoot = snapshot.startOnBoot,
            touchBoostEnabled = snapshot.touchBoostEnabled,
            languageMode = languageMode,
            themeMode = when (snapshot.themeMode) {
                0 -> ThemeMode.FOLLOW_SYSTEM
                1 -> ThemeMode.LIGHT
                2 -> ThemeMode.DARK
                else -> ThemeMode.FOLLOW_SYSTEM
            },
            pureBlackTheme = snapshot.pureBlackTheme,
            useDynamicTheme = snapshot.useDynamicTheme
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        SettingsState()
    )

    fun setStartOnBoot(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.updateSnapshot { it.copy(startOnBoot = enabled) }
        }
    }

    fun setLanguageMode(mode: LanguageMode) {
        // Use AppCompat to set the locale - it handles persistence automatically
        // On Android 13+: syncs with system per-app language settings
        // On Android 12 and below: uses AppLocalesMetadataHolderService for auto-storage
        val appLocale = if (mode.localeTag.isEmpty()) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(mode.localeTag)
        }
        AppCompatDelegate.setApplicationLocales(appLocale)

        // Trigger UI refresh
        localeRefreshTrigger.value++
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            val modeCode = when (mode) {
                ThemeMode.FOLLOW_SYSTEM -> 0
                ThemeMode.LIGHT -> 1
                ThemeMode.DARK -> 2
            }
            dataStore.updateSnapshot { it.copy(themeMode = modeCode) }
        }
    }

    fun setPureBlackTheme(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.updateSnapshot { it.copy(pureBlackTheme = enabled) }
        }
    }

    fun setUseDynamicTheme(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.updateSnapshot { it.copy(useDynamicTheme = enabled) }
        }
    }

    fun setTouchBoostEnabled(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.updateSnapshot { it.copy(touchBoostEnabled = enabled) }
            RootIpc.invoke { it.setTouchBoostEnabled(enabled) }
        }
    }
}
