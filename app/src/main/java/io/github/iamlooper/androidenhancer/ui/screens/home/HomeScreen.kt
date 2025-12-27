package io.github.iamlooper.androidenhancer.ui.screens.home

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.iamlooper.androidenhancer.R
import io.github.iamlooper.androidenhancer.system.jni.AndroidEnhancerMode
import io.github.iamlooper.androidenhancer.ui.components.LoadingIndicatorDialog
import kotlinx.coroutines.delay

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    state: HomeState,
    onModeSelected: (AndroidEnhancerMode) -> Unit,
    onOpenLog: () -> Unit,
    onOpenPerAppMode: () -> Unit
) {
    val perAppModeEnabled = state.mode == AndroidEnhancerMode.AUTO && state.serviceEnabled

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {


        item {
            GlobalModeCard(
                currentMode = state.mode,
                serviceEnabled = state.serviceEnabled,
                onModeSelected = onModeSelected
            )
        }

        item {
            PerAppModeCard(
                modeOverrideCount = state.apps.size,
                enabled = perAppModeEnabled,
                serviceEnabled = state.serviceEnabled,
                onViewProfileOverrides = onOpenPerAppMode
            )
        }

        item {
            LogPreviewCard(
                lines = state.logPreview,
                onOpenLog = onOpenLog
            )
        }
    }
}



@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun GlobalModeCard(
    currentMode: AndroidEnhancerMode,
    serviceEnabled: Boolean,
    onModeSelected: (AndroidEnhancerMode) -> Unit
) {
    var guardVisible by remember { mutableStateOf(false) }
    var pendingMode by remember { mutableStateOf<AndroidEnhancerMode?>(null) }

    LaunchedEffect(guardVisible) {
        if (!guardVisible && pendingMode != null) {
            pendingMode = null
        }
    }

    val showLoading = guardVisible
    val buttonsEnabled = !guardVisible && serviceEnabled
    val displayMode = pendingMode ?: currentMode
    val modes = AndroidEnhancerMode.entries

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = 3.dp,
            pressedElevation = 6.dp
        )
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .alpha(if (serviceEnabled) 1f else 0.78f),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(MaterialTheme.shapes.large)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_bolt),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = stringResource(R.string.global_mode_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Text(
                    text = displayMode.description(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    lineHeight = 20.sp
                )
            }

            // MD3 Expressive: InputChip group with spring physics
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                modes.forEach { mode ->
                    val selected = mode == displayMode
                    val scale by animateFloatAsState(
                        targetValue = if (selected) 1.03f else 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        ),
                        label = "mode_chip_scale"
                    )

                    InputChip(
                        selected = selected,
                        onClick = {
                            if (!selected && buttonsEnabled) {
                                pendingMode = mode
                                guardVisible = true
                                onModeSelected(mode)
                            }
                        },
                        label = {
                            Text(
                                text = mode.displayName(),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold
                            )
                        },
                        modifier = Modifier.scale(scale),
                        enabled = buttonsEnabled,
                        shape = MaterialTheme.shapes.large,
                        colors = InputChipDefaults.inputChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            labelColor = MaterialTheme.colorScheme.onSurface,
                            selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            disabledSelectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer
                        ),
                        border = InputChipDefaults.inputChipBorder(
                            enabled = buttonsEnabled,
                            selected = selected,
                            borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            selectedBorderColor = MaterialTheme.colorScheme.tertiary,
                            disabledBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            disabledSelectedBorderColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.7f),
                            borderWidth = 1.dp,
                            selectedBorderWidth = 1.5.dp
                        )
                    )
                }
            }
        }
    }

    LaunchedEffect(guardVisible) {
        if (guardVisible) {
            delay(3_000)
            guardVisible = false
        }
    }

    LoadingIndicatorDialog(visible = showLoading)
}

@Composable
private fun AndroidEnhancerMode.displayName(): String =
    when (this) {
        AndroidEnhancerMode.AUTO -> stringResource(R.string.mode_auto_label)
        AndroidEnhancerMode.POWERSAVER -> stringResource(R.string.mode_powersaver_label)
        AndroidEnhancerMode.BALANCED -> stringResource(R.string.mode_balanced_label)
        AndroidEnhancerMode.PERFORMANCE -> stringResource(R.string.mode_performance_label)
        AndroidEnhancerMode.GAMING -> stringResource(R.string.mode_gaming_label)
    }

@Composable
private fun AndroidEnhancerMode.description(): String =
    when (this) {
        AndroidEnhancerMode.AUTO -> stringResource(R.string.mode_selector_description_auto)
        AndroidEnhancerMode.POWERSAVER -> stringResource(R.string.mode_selector_description_powersaver)
        AndroidEnhancerMode.BALANCED -> stringResource(R.string.mode_selector_description_balanced)
        AndroidEnhancerMode.PERFORMANCE -> stringResource(R.string.mode_selector_description_performance)
        AndroidEnhancerMode.GAMING -> stringResource(R.string.mode_selector_description_gaming)
    }

@Composable
private fun LogPreviewCard(
    lines: List<String>,
    onOpenLog: () -> Unit
) {
    ElevatedCard(
        onClick = onOpenLog,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = 3.dp,
            pressedElevation = 6.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(MaterialTheme.shapes.large)
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_description),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = stringResource(R.string.log_preview_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.log_preview_hint),
                        style = MaterialTheme.typography.bodySmall,
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

            if (lines.isEmpty()) {
                Text(
                    text = stringResource(R.string.log_empty_title),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    lines.takeLast(3).forEachIndexed { index, line ->
                        // Spring scale animation for visual feedback
                        val scale by animateFloatAsState(
                            targetValue = 1f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                            ),
                            label = "log_line_scale_$index"
                        )
                        
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            ),
                            shape = MaterialTheme.shapes.medium,
                            elevation = CardDefaults.cardElevation(
                                defaultElevation = 1.dp
                            ),
                            modifier = Modifier
                                .scale(scale)
                                .fillMaxWidth()
                        ) {
                            Text(
                                text = line,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PerAppModeCard(
    modeOverrideCount: Int,
    enabled: Boolean,
    serviceEnabled: Boolean,
    onViewProfileOverrides: () -> Unit
) {
    val subtitle = when {
        !serviceEnabled -> stringResource(R.string.per_app_modes_hint)
        !enabled -> stringResource(R.string.per_app_modes_disabled)
        modeOverrideCount == 0 -> stringResource(R.string.per_app_modes_hint)
        else -> pluralStringResource(R.plurals.per_app_modes_count, modeOverrideCount, modeOverrideCount)
    }

    ElevatedCard(
        onClick = { if (enabled) onViewProfileOverrides() },
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = 3.dp,
            pressedElevation = 6.dp
        )
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .alpha(if (enabled) 1f else 0.78f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(MaterialTheme.shapes.large)
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_apps),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = stringResource(R.string.per_app_modes_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
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
}
