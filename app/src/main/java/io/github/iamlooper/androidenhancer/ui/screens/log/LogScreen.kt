package io.github.iamlooper.androidenhancer.ui.screens.log

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.FloatingToolbarDefaults.floatingToolbarVerticalNestedScroll
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.github.iamlooper.androidenhancer.R
import io.github.iamlooper.androidenhancer.system.util.Constants.LOG_BATCH_SIZE
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LogScreen(
    state: LogState,
    onShare: (Context) -> Unit,
    onClear: () -> Unit
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var expanded by rememberSaveable { mutableStateOf(true) }

    val hasEntries = state.logEntries.isNotEmpty()
    val entryCount = state.logEntries.size
    
    // Stable batch count calculation
    val batchCount = remember(entryCount) {
        if (entryCount > 0) (entryCount + LOG_BATCH_SIZE - 1) / LOG_BATCH_SIZE else 0
    }

    val canScrollUp by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
        }
    }
    
    val canScrollDown by remember {
        derivedStateOf {
            val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
            val totalItems = listState.layoutInfo.totalItemsCount
            lastVisibleIndex != null && totalItems > 0 && lastVisibleIndex < totalItems - 1
        }
    }

    // Initial scroll to bottom when entries change
    LaunchedEffect(batchCount) {
        if (batchCount > 0) {
            listState.scrollToItem(batchCount)
        }
    }

    val toolbarColors = FloatingToolbarDefaults.vibrantFloatingToolbarColors()

    Scaffold { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            HorizontalFloatingToolbar(
                expanded = expanded,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = -FloatingToolbarDefaults.ScreenOffset)
                    .zIndex(1f),
                colors = toolbarColors,
                content = {
                    IconButton(
                        onClick = onClear,
                        enabled = hasEntries
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_delete),
                            contentDescription = stringResource(R.string.delete_log)
                        )
                    }
                    IconButton(
                        onClick = {
                            if (hasEntries) {
                                onShare(context)
                            }
                        },
                        enabled = hasEntries
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_share),
                            contentDescription = stringResource(R.string.share_log)
                        )
                    }
                    IconButton(
                        onClick = {
                            scope.launch {
                                listState.animateScrollToItem(
                                    index = 0,
                                    scrollOffset = 0
                                )
                            }
                        },
                        enabled = hasEntries && canScrollUp
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_upward),
                            contentDescription = stringResource(R.string.scroll_to_top)
                        )
                    }
                    IconButton(
                        onClick = {
                            scope.launch {
                                listState.animateScrollToItem(
                                    index = batchCount,
                                    scrollOffset = 0
                                )
                            }
                        },
                        enabled = hasEntries && canScrollDown
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_downward),
                            contentDescription = stringResource(R.string.scroll_to_bottom)
                        )
                    }
                }
            )

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .floatingToolbarVerticalNestedScroll(
                        expanded = expanded,
                        onExpand = { expanded = true },
                        onCollapse = { expanded = false }
                    ),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 16.dp,
                    bottom = 0.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!hasEntries) {
                    item(key = "empty_placeholder", contentType = "placeholder") { 
                        LogEmptyPlaceholder() 
                    }
                } else {
                    // Batch log entries for better performance
                    logEntriesBatched(state.logEntries)
                    
                    item(key = "bottom_spacer", contentType = "spacer") {
                        Spacer(Modifier.size(100.dp))
                    }
                }
            }
        }
    }
}

// Batch log entries to reduce composable count and improve scroll performance
private fun LazyListScope.logEntriesBatched(entries: List<String>) {
    val batchCount = (entries.size + LOG_BATCH_SIZE - 1) / LOG_BATCH_SIZE
    
    items(
        count = batchCount,
        key = { batchIndex -> "batch_$batchIndex" },
        contentType = { "log_batch" }
    ) { batchIndex ->
        val startIdx = batchIndex * LOG_BATCH_SIZE
        val endIdx = minOf(startIdx + LOG_BATCH_SIZE, entries.size)
        val batchEntries = entries.subList(startIdx, endIdx)
        
        LogEntryBatch(entries = batchEntries, startIndex = startIdx)
    }
}

@Composable
private fun LogEntryBatch(entries: List<String>, startIndex: Int) {
    // Cache colors once per batch to avoid repeated lookups
    val containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    val textColor = MaterialTheme.colorScheme.onSurface
    val shape = MaterialTheme.shapes.medium
    
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        entries.forEachIndexed { localIndex, line ->
            LogEntryItem(
                line = line,
                containerColor = containerColor,
                textColor = textColor,
                shape = shape
            )
        }
    }
}

@Composable
private fun LogEntryItem(
    line: String,
    containerColor: androidx.compose.ui.graphics.Color,
    textColor: androidx.compose.ui.graphics.Color,
    shape: androidx.compose.ui.graphics.Shape
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        color = containerColor,
        tonalElevation = 1.dp
    ) {
        Text(
            text = line,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = textColor,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun LogEmptyPlaceholder() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_description),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(36.dp)
            )
            Text(
                text = stringResource(R.string.log_empty_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.log_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

