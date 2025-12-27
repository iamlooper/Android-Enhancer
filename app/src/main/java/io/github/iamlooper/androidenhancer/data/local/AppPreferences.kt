package io.github.iamlooper.androidenhancer.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.iamlooper.androidenhancer.system.jni.AndroidEnhancerMode
import io.github.iamlooper.androidenhancer.system.util.Constants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(name = Constants.STORE_NAME)

object PreferenceKeys {
    val MODE = intPreferencesKey("mode")
    val ACCESSIBILITY_ENABLED = booleanPreferencesKey("accessibility_enabled")
    val LOG_PATH = stringPreferencesKey("log_path")
    val APPS = stringPreferencesKey("apps")
    val CURRENT_APP = stringPreferencesKey("current_app")
    val START_ON_BOOT = booleanPreferencesKey("start_on_boot")
    val THEME_MODE = intPreferencesKey("theme_mode")
    val PURE_BLACK_THEME = booleanPreferencesKey("pure_black_theme")
    val USE_DYNAMIC_THEME = booleanPreferencesKey("use_dynamic_theme")
    val SERVICE_ENABLED = booleanPreferencesKey("service_enabled")
    val TOUCH_BOOST_ENABLED = booleanPreferencesKey("touch_boost_enabled")
}

@Serializable
data class PreferencesSnapshot(
    val mode: AndroidEnhancerMode = AndroidEnhancerMode.AUTO,
    val accessibilityEnabled: Boolean = false,
    val logPath: String = "",
    val apps: Map<String, AndroidEnhancerMode> = emptyMap(),
    val currentApp: String = "",
    val startOnBoot: Boolean = true,
    val themeMode: Int = 0,
    val pureBlackTheme: Boolean = false,
    val useDynamicTheme: Boolean = true,
    val serviceEnabled: Boolean = false,
    val touchBoostEnabled: Boolean = true
) {

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun fromPreferences(preferences: Preferences): PreferencesSnapshot {
            val appsJson = preferences[PreferenceKeys.APPS]
            return PreferencesSnapshot(
                mode = AndroidEnhancerMode.fromCode(preferences[PreferenceKeys.MODE] ?: 0),
                accessibilityEnabled = preferences[PreferenceKeys.ACCESSIBILITY_ENABLED] ?: false,
                logPath = preferences[PreferenceKeys.LOG_PATH] ?: "",
                apps = appsJson?.let { decodeApps(it) } ?: emptyMap(),
                currentApp = preferences[PreferenceKeys.CURRENT_APP] ?: "",
                startOnBoot = preferences[PreferenceKeys.START_ON_BOOT] ?: true,
                themeMode = preferences[PreferenceKeys.THEME_MODE] ?: 0,
                pureBlackTheme = preferences[PreferenceKeys.PURE_BLACK_THEME] ?: false,
                useDynamicTheme = preferences[PreferenceKeys.USE_DYNAMIC_THEME] ?: true,
                serviceEnabled = preferences[PreferenceKeys.SERVICE_ENABLED] ?: false,
                touchBoostEnabled = preferences[PreferenceKeys.TOUCH_BOOST_ENABLED] ?: true
            )
        }

        private fun decodeApps(payload: String): Map<String, AndroidEnhancerMode> {
            return runCatching {
                json.decodeFromString<Map<String, String>>(payload)
                    .mapValues { (_, value) -> AndroidEnhancerMode.fromCode(value.toIntOrNull() ?: 0) }
            }.getOrElse { emptyMap() }
        }

        fun encodeApps(apps: Map<String, AndroidEnhancerMode>): String {
            val serializable = apps.mapValues { it.value.code.toString() }
            return json.encodeToString(serializable)
        }
    }
}

fun DataStore<Preferences>.snapshotFlow(): Flow<PreferencesSnapshot> = data.map {
    PreferencesSnapshot.fromPreferences(it)
}

suspend fun DataStore<Preferences>.updateSnapshot(transform: (PreferencesSnapshot) -> PreferencesSnapshot) {
    edit { prefs ->
        val current = PreferencesSnapshot.fromPreferences(prefs)
        val updated = transform(current)
        prefs[PreferenceKeys.MODE] = updated.mode.code
        prefs[PreferenceKeys.ACCESSIBILITY_ENABLED] = updated.accessibilityEnabled
        prefs[PreferenceKeys.APPS] = PreferencesSnapshot.encodeApps(updated.apps)
        prefs[PreferenceKeys.LOG_PATH] = updated.logPath
        prefs[PreferenceKeys.CURRENT_APP] = updated.currentApp
        prefs[PreferenceKeys.START_ON_BOOT] = updated.startOnBoot
        prefs[PreferenceKeys.THEME_MODE] = updated.themeMode
        prefs[PreferenceKeys.PURE_BLACK_THEME] = updated.pureBlackTheme
        prefs[PreferenceKeys.USE_DYNAMIC_THEME] = updated.useDynamicTheme
        prefs[PreferenceKeys.SERVICE_ENABLED] = updated.serviceEnabled
        prefs[PreferenceKeys.TOUCH_BOOST_ENABLED] = updated.touchBoostEnabled
    }
}

fun appLogFile(context: Context): File =
    File(context.filesDir, "app.log")

fun sysfsBackupFile(context: Context): File =
    File(context.filesDir, "sysfs_backup.json")
