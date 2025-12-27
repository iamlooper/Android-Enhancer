package io.github.iamlooper.androidenhancer.ui.screens.settings

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.iamlooper.androidenhancer.R

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
    state: SettingsState,
    onStartOnBootChanged: (Boolean) -> Unit,
    onTouchBoostEnabledChanged: (Boolean) -> Unit,
    onLanguageModeChanged: (LanguageMode) -> Unit,
    onThemeModeChanged: (ThemeMode) -> Unit,
    onPureBlackThemeChanged: (Boolean) -> Unit,
    onUseDynamicThemeChanged: (Boolean) -> Unit
) {
    var showLanguageDialog by rememberSaveable { mutableStateOf(false) }
    var showThemeDialog by rememberSaveable { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    // Reset dialog visibility when leaving the screen to prevent flash during navigation
    DisposableEffect(Unit) {
        onDispose {
            showLanguageDialog = false
            showThemeDialog = false
        }
    }

    val isDarkTheme = state.themeMode == ThemeMode.DARK || 
                     (state.themeMode == ThemeMode.FOLLOW_SYSTEM && isSystemInDarkTheme())
    val isDynamicColorSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SettingSwitchCard(
            iconRes = R.drawable.ic_power_settings_new,
            title = stringResource(R.string.settings_start_on_boot_title),
            subtitle = stringResource(R.string.settings_start_on_boot_subtitle),
            checked = state.startOnBoot,
            onCheckedChange = onStartOnBootChanged
        )

        SettingSwitchCard(
            iconRes = R.drawable.ic_touch_app,
            title = stringResource(R.string.settings_touch_boost_title),
            subtitle = stringResource(R.string.settings_touch_boost_subtitle),
            checked = state.touchBoostEnabled,
            onCheckedChange = onTouchBoostEnabledChanged
        )

        SettingClickableCard(
            iconRes = R.drawable.ic_language,
            title = stringResource(R.string.settings_language_title),
            subtitle = state.languageMode.displayName(),
            onClick = { showLanguageDialog = true }
        )

        SettingClickableCard(
            iconRes = R.drawable.ic_brightness_4,
            title = stringResource(R.string.settings_theme_title),
            subtitle = state.themeMode.displayName(),
            onClick = { showThemeDialog = true }
        )

        SettingSwitchCard(
            iconRes = R.drawable.ic_brightness_2,
            title = stringResource(R.string.settings_pure_black_title),
            subtitle = stringResource(R.string.settings_pure_black_subtitle),
            checked = state.pureBlackTheme,
            onCheckedChange = onPureBlackThemeChanged,
            enabled = isDarkTheme
        )

        SettingSwitchCard(
            iconRes = R.drawable.ic_palette,
            title = stringResource(R.string.settings_dynamic_theme_title),
            subtitle = stringResource(R.string.settings_dynamic_theme_subtitle),
            checked = state.useDynamicTheme,
            onCheckedChange = onUseDynamicThemeChanged,
            enabled = isDynamicColorSupported
        )

        Spacer(modifier = Modifier.height(8.dp))
    }

    if (showLanguageDialog) {
        LanguageSelectionDialog(
            currentMode = state.languageMode,
            onDismiss = { showLanguageDialog = false },
            onSelect = { mode ->
                onLanguageModeChanged(mode)
                showLanguageDialog = false
            }
        )
    }

    if (showThemeDialog) {
        ThemeSelectionDialog(
            currentMode = state.themeMode,
            onDismiss = { showThemeDialog = false },
            onSelect = { mode ->
                onThemeModeChanged(mode)
                showThemeDialog = false
            }
        )
    }
}

@Composable
private fun SettingSwitchCard(
    iconRes: Int,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (enabled) MaterialTheme.colorScheme.surfaceContainerLow
                           else MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = 2.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = if (enabled) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp)
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface
                           else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
                           else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled
            )
        }
    }
}

@Composable
private fun SettingClickableCard(
    iconRes: Int,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = 2.dp,
            pressedElevation = 6.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun LanguageSelectionDialog(
    currentMode: LanguageMode,
    onDismiss: () -> Unit,
    onSelect: (LanguageMode) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                painter = painterResource(R.drawable.ic_language),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = stringResource(R.string.settings_language_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                LanguageMode.entries.forEach { mode ->
                    ElevatedCard(
                        onClick = { onSelect(mode) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = if (mode == currentMode)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        elevation = CardDefaults.elevatedCardElevation(
                            defaultElevation = if (mode == currentMode) 2.dp else 0.dp
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = mode.displayName(),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (mode == currentMode) FontWeight.Bold else FontWeight.Normal,
                                color = if (mode == currentMode)
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else
                                    MaterialTheme.colorScheme.onSurface
                            )
                            if (mode == currentMode) {
                                RadioButton(
                                    selected = true,
                                    onClick = null
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {}
    )
}

@Composable
private fun ThemeSelectionDialog(
    currentMode: ThemeMode,
    onDismiss: () -> Unit,
    onSelect: (ThemeMode) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                painter = painterResource(R.drawable.ic_brightness_4),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = stringResource(R.string.settings_theme_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ThemeMode.entries.forEach { mode ->
                    ElevatedCard(
                        onClick = { onSelect(mode) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = if (mode == currentMode)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        elevation = CardDefaults.elevatedCardElevation(
                            defaultElevation = if (mode == currentMode) 2.dp else 0.dp
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = mode.displayName(),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (mode == currentMode) FontWeight.Bold else FontWeight.Normal,
                                color = if (mode == currentMode)
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else
                                    MaterialTheme.colorScheme.onSurface
                            )
                            if (mode == currentMode) {
                                RadioButton(
                                    selected = true,
                                    onClick = null
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {}
    )
}

@Composable
private fun ThemeMode.displayName(): String =
    when (this) {
        ThemeMode.FOLLOW_SYSTEM -> stringResource(R.string.settings_follow_system)
        ThemeMode.LIGHT -> stringResource(R.string.settings_light)
        ThemeMode.DARK -> stringResource(R.string.settings_dark)
    }

@Composable
private fun LanguageMode.displayName(): String =
    when (this) {
        LanguageMode.FOLLOW_SYSTEM -> stringResource(R.string.settings_follow_system)
        LanguageMode.ENGLISH -> stringResource(R.string.language_english)
        LanguageMode.SPANISH -> stringResource(R.string.language_spanish)
        LanguageMode.CHINESE -> stringResource(R.string.language_chinese)
        LanguageMode.FRENCH -> stringResource(R.string.language_french)
        LanguageMode.GERMAN -> stringResource(R.string.language_german)
        LanguageMode.JAPANESE -> stringResource(R.string.language_japanese)
        LanguageMode.RUSSIAN -> stringResource(R.string.language_russian)
        LanguageMode.PORTUGUESE -> stringResource(R.string.language_portuguese)
        LanguageMode.ITALIAN -> stringResource(R.string.language_italian)
        LanguageMode.KOREAN -> stringResource(R.string.language_korean)
    }
