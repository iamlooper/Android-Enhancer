package com.looper.android_enhancer.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import com.looper.android_enhancer.R

@Composable
fun MultiSelectPreference(
    title: String,
    description: String,
    icon: ImageVector,
    entries: List<String>,
    entryValues: List<String>,
    selectedValues: Set<String>,
    onValuesChanged: (Set<String>) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        onClick = { showDialog = true }
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showDialog) {
        var tempSelectedValues by remember { mutableStateOf(selectedValues) }
        val scrollState = rememberScrollState()

        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(title) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(scrollState)
                ) {
                    entries.zip(entryValues).forEach { (entry, value) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    tempSelectedValues = if (tempSelectedValues.contains(value)) {
                                        tempSelectedValues - value
                                    } else {
                                        tempSelectedValues + value
                                    }
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = tempSelectedValues.contains(value),
                                onCheckedChange = { checked ->
                                    tempSelectedValues = if (checked) {
                                        tempSelectedValues + value
                                    } else {
                                        tempSelectedValues - value
                                    }
                                }
                            )
                            Text(
                                text = entry,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 16.dp, end = 16.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onValuesChanged(tempSelectedValues)
                        showDialog = false
                    }
                ) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDialog = false }
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}