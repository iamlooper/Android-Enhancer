package io.github.iamlooper.androidenhancer.ui.screens.settings

import androidx.compose.runtime.Immutable

enum class ThemeMode {
    FOLLOW_SYSTEM,
    LIGHT,
    DARK
}

enum class LanguageMode(val localeTag: String) {
    FOLLOW_SYSTEM(""),
    ENGLISH("en"),
    SPANISH("es"),
    CHINESE("zh-CN"),
    FRENCH("fr"),
    GERMAN("de"),
    JAPANESE("ja"),
    RUSSIAN("ru"),
    PORTUGUESE("pt"),
    ITALIAN("it"),
    KOREAN("ko");

    companion object {
        fun fromLocaleTag(tag: String): LanguageMode {
            // Empty tag means follow system
            if (tag.isBlank()) return FOLLOW_SYSTEM
            
            // Find exact match first
            entries.find { it.localeTag.equals(tag, ignoreCase = true) }?.let { return it }
            
            // Extract the language code (first part before any hyphen)
            val languageCode = tag.substringBefore("-").lowercase()
            
            // Match by language code - compare the language portion of each entry's localeTag
            return entries.find { entry ->
                entry.localeTag.isNotEmpty() && 
                entry.localeTag.substringBefore("-").lowercase() == languageCode
            } ?: FOLLOW_SYSTEM
        }
    }
}

@Immutable
data class SettingsState(
    val startOnBoot: Boolean = true,
    val touchBoostEnabled: Boolean = true,
    val languageMode: LanguageMode = LanguageMode.FOLLOW_SYSTEM,
    val themeMode: ThemeMode = ThemeMode.FOLLOW_SYSTEM,
    val pureBlackTheme: Boolean = false,
    val useDynamicTheme: Boolean = true
)
