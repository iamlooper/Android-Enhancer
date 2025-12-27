package io.github.iamlooper.androidenhancer.ui.screens.per_app_mode

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import io.github.iamlooper.androidenhancer.R
import io.github.iamlooper.androidenhancer.system.jni.AndroidEnhancerMode
import io.github.iamlooper.androidenhancer.system.util.InstalledApp
import io.github.iamlooper.androidenhancer.ui.components.LoadingIndicatorDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PerAppModeScreen(
    state: PerAppModeState,
    onSetModeOverride: (InstalledApp, AndroidEnhancerMode) -> Unit,
    onRemoveModeOverride: (String) -> Unit
) {
    PerAppModeList(
        installedApps = state.installedApps,
        modeOverrides = state.apps,
        onSetModeOverride = onSetModeOverride,
        onRemoveModeOverride = onRemoveModeOverride
    )
}

enum class AppFilter {
    ALL, OVERRIDDEN, NON_OVERRIDDEN
}

// Icon cache to avoid repeated loading during scrolling
@Stable
private class IconCache(private val context: Context) {
    private val cache = mutableMapOf<String, Drawable?>()
    
    suspend fun getIcon(packageName: String): Drawable? {
        return cache.getOrPut(packageName) {
            withContext(Dispatchers.IO) {
                runCatching { context.packageManager.getApplicationIcon(packageName) }.getOrNull()
            }
        }
    }
    
    fun getCached(packageName: String): Drawable? = cache[packageName]
}

@Composable
private fun PerAppModeList(
    installedApps: List<InstalledApp>,
    modeOverrides: Map<String, AndroidEnhancerMode>,
    onSetModeOverride: (InstalledApp, AndroidEnhancerMode) -> Unit,
    onRemoveModeOverride: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(AppFilter.ALL) }
    val listState = rememberLazyListState()
    val context = LocalContext.current
    
    // Shared icon cache to prevent repeated loading
    val iconCache = remember(context) { IconCache(context) }

    val filteredApps = remember(searchQuery, selectedFilter, installedApps, modeOverrides) {
        val query = searchQuery
        val filter = selectedFilter
        if (query.isEmpty() && filter == AppFilter.ALL) {
            installedApps
        } else {
            installedApps.filter { app ->
                val matchesSearch = query.isEmpty() ||
                    app.label.contains(query, ignoreCase = true) ||
                    app.packageName.contains(query, ignoreCase = true)
                val matchesFilter = when (filter) {
                    AppFilter.ALL -> true
                    AppFilter.OVERRIDDEN -> app.packageName in modeOverrides
                    AppFilter.NON_OVERRIDDEN -> app.packageName !in modeOverrides
                }
                matchesSearch && matchesFilter
            }
        }
    }

    // Pre-load icons for visible items + buffer
    LaunchedEffect(listState, filteredApps) {
        snapshotFlow {
            val visibleItems = listState.layoutInfo.visibleItemsInfo
            val firstVisible = visibleItems.firstOrNull()?.index ?: 0
            val lastVisible = visibleItems.lastOrNull()?.index ?: 0
            // Buffer: load icons for items before and after visible range
            val start = (firstVisible - 5).coerceAtLeast(0)
            val end = (lastVisible + 5).coerceAtMost(filteredApps.lastIndex)
            start to end
        }.distinctUntilChanged().collectLatest { (start, end) ->
            for (i in start..end) {
                if (i in filteredApps.indices) {
                    iconCache.getIcon(filteredApps[i].packageName)
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text(stringResource(R.string.search_apps_hint)) },
            leadingIcon = {
                Icon(
                    painter = painterResource(R.drawable.ic_search),
                    contentDescription = null
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_close),
                            contentDescription = stringResource(R.string.clear_search_desc)
                        )
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            shape = MaterialTheme.shapes.extraLarge,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                unfocusedIndicatorColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
            ),
            singleLine = true
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedFilter == AppFilter.ALL,
                onClick = { selectedFilter = AppFilter.ALL },
                label = { Text(stringResource(R.string.filter_all_format, installedApps.size), style = MaterialTheme.typography.labelLarge) },
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.padding(vertical = 2.dp)
            )
            FilterChip(
                selected = selectedFilter == AppFilter.OVERRIDDEN,
                onClick = { selectedFilter = AppFilter.OVERRIDDEN },
                label = { Text(stringResource(R.string.filter_overridden_format, modeOverrides.size), style = MaterialTheme.typography.labelLarge) },
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.padding(vertical = 2.dp)
            )
            FilterChip(
                selected = selectedFilter == AppFilter.NON_OVERRIDDEN,
                onClick = { selectedFilter = AppFilter.NON_OVERRIDDEN },
                label = { Text(stringResource(R.string.filter_default_format, installedApps.size - modeOverrides.size), style = MaterialTheme.typography.labelLarge) },
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(
                items = filteredApps,
                key = { it.packageName },
                contentType = { "app_item" }
            ) { app ->
                val currentMode = modeOverrides[app.packageName]
                PerAppModeItemCard(
                    app = app,
                    mode = currentMode ?: AndroidEnhancerMode.AUTO,
                    hasOverride = currentMode != null,
                    iconCache = iconCache,
                    onModeSelected = { newMode -> onSetModeOverride(app, newMode) },
                    onRemoveModeOverride = { onRemoveModeOverride(app.packageName) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PerAppModeItemCard(
    app: InstalledApp,
    mode: AndroidEnhancerMode,
    hasOverride: Boolean,
    iconCache: IconCache,
    onModeSelected: (AndroidEnhancerMode) -> Unit,
    onRemoveModeOverride: () -> Unit
) {
    var showModeSelection by remember { mutableStateOf(false) }
    
    // Use cached icon, trigger load if not available
    var appIcon by remember(app.packageName) { mutableStateOf(iconCache.getCached(app.packageName)) }
    
    LaunchedEffect(app.packageName) {
        if (appIcon == null) {
            appIcon = iconCache.getIcon(app.packageName)
        }
    }

    // Cache colors to avoid recomputation during scroll
    val containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    val iconContainerColor = MaterialTheme.colorScheme.primaryContainer
    val iconTint = MaterialTheme.colorScheme.onPrimaryContainer
    val textColor = MaterialTheme.colorScheme.onSurface
    val subtextColor = MaterialTheme.colorScheme.onSurfaceVariant

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(containerColor = containerColor),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = 2.dp,
            pressedElevation = 6.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(MaterialTheme.shapes.large)
                    .background(iconContainerColor),
                contentAlignment = Alignment.Center
            ) {
                val icon = appIcon
                if (icon != null) {
                    val painter = remember(icon) {
                        BitmapPainter(icon.toBitmap().asImageBitmap())
                    }
                    Image(
                        painter = painter,
                        contentDescription = app.label,
                        modifier = Modifier.size(48.dp),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.ic_apps),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = iconTint
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = subtextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            ModeIndicatorBadge(
                mode = mode,
                onClick = { showModeSelection = true }
            )

            if (hasOverride) {
                IconButton(
                    onClick = onRemoveModeOverride,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_delete),
                        contentDescription = stringResource(R.string.per_app_mode_remove_content_desc),
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }

    if (showModeSelection) {
        PerAppModeSelectionDialog(
            appName = app.label,
            currentMode = mode,
            onModeSelected = { selectedMode ->
                onModeSelected(selectedMode)
                showModeSelection = false
            },
            onDismiss = { showModeSelection = false }
        )
    }
}

@Composable
private fun ModeIndicatorBadge(
    mode: AndroidEnhancerMode,
    onClick: () -> Unit
) {
    val (backgroundColor, textColor) = when (mode) {
        AndroidEnhancerMode.AUTO -> {
            MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        }
        AndroidEnhancerMode.POWERSAVER -> {
            MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        }
        AndroidEnhancerMode.BALANCED -> {
            MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        }
        AndroidEnhancerMode.PERFORMANCE -> {
            MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        }
        AndroidEnhancerMode.GAMING -> {
            MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.onError
        }
    }

    val modeName = when (mode) {
        AndroidEnhancerMode.AUTO -> stringResource(R.string.mode_auto_label)
        AndroidEnhancerMode.POWERSAVER -> stringResource(R.string.mode_powersaver_label)
        AndroidEnhancerMode.BALANCED -> stringResource(R.string.mode_balanced_label)
        AndroidEnhancerMode.PERFORMANCE -> stringResource(R.string.mode_performance_label)
        AndroidEnhancerMode.GAMING -> stringResource(R.string.mode_gaming_label)
    }

    Box(
        modifier = Modifier
            .clip(MaterialTheme.shapes.medium)
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = modeName,
            style = MaterialTheme.typography.labelMedium,
            color = textColor,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun PerAppModeSelectionDialog(
    appName: String,
    currentMode: AndroidEnhancerMode,
    onModeSelected: (AndroidEnhancerMode) -> Unit,
    onDismiss: () -> Unit
) {
    var selectionGuardVisible by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.per_app_mode_dialog_title, appName),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.per_app_mode_dialog_body, appName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                AndroidEnhancerMode.entries.forEach { mode ->
                    val selected = mode == currentMode

                    // Spring scale animation for visual feedback
                    val scale by animateFloatAsState(
                        targetValue = if (selected) 1.02f else 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        ),
                        label = "dialog_mode_scale"
                    )
                    
                    if (selected) {
                        ElevatedCard(
                            onClick = { 
                                selectionGuardVisible = true
                                onModeSelected(mode) 
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .scale(scale),
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.elevatedCardColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer
                            ),
                            elevation = CardDefaults.elevatedCardElevation(
                                defaultElevation = 3.dp,
                                pressedElevation = 6.dp
                            )
                        ) {
                            Text(
                                text = mode.displayName(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)
                            )
                        }
                    } else {
                        OutlinedCard(
                            onClick = { 
                                selectionGuardVisible = true
                                onModeSelected(mode) 
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .scale(scale),
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.outlinedCardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            border = CardDefaults.outlinedCardBorder().copy(
                                width = 1.5.dp
                            )
                        ) {
                            Text(
                                text = mode.displayName(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)
                            )
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.per_app_mode_dialog_secondary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dismiss))
            }
        },
        modifier = Modifier.widthIn(min = 280.dp, max = 400.dp)
    )

    LaunchedEffect(selectionGuardVisible) {
        if (selectionGuardVisible) {
            delay(3_000)
            selectionGuardVisible = false
        }
    }

    LoadingIndicatorDialog(visible = selectionGuardVisible)
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

