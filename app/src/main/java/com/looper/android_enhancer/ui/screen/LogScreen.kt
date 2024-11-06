package com.looper.android_enhancer.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.looper.android_enhancer.R
import com.looper.android_enhancer.viewmodel.LogViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun LogScreen(
    viewModel: LogViewModel,
    coroutineScope: CoroutineScope
) {
    val logEntries by viewModel.logEntries.collectAsState()
    val isEmpty by viewModel.isEmpty.collectAsState()
    val listState = rememberLazyListState()

    // Show/hide FAB based on scroll position
    val showScrollToBottomButton by remember {
        derivedStateOf {
            val firstVisibleItem = listState.firstVisibleItemIndex
            val visibleItems = listState.layoutInfo.visibleItemsInfo.size
            val totalItems = logEntries.size

            firstVisibleItem > 0 && (firstVisibleItem + visibleItems) < totalItems
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        if (isEmpty) {
            Text(
                text = stringResource(R.string.no_log_found),
                modifier = Modifier.align(Alignment.Center),
                style = MaterialTheme.typography.bodyLarge
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                items(
                    items = logEntries,
                    key = { entry -> entry.id }
                ) { entry ->
                    Text(
                        text = entry.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                    )
                    if (entry != logEntries.last()) {
                        HorizontalDivider(
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }
            }

            if (showScrollToBottomButton) {
                FloatingActionButton(
                    onClick = {
                        coroutineScope.launch {
                            listState.animateScrollToItem(logEntries.size - 1)
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_keyboard_arrow_down),
                        contentDescription = "Scroll to bottom",
                        modifier = Modifier.padding(4.dp)
                    )
                }
            }
        }
    }
}