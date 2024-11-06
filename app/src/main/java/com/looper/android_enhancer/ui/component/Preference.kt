package com.looper.android_enhancer.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import com.looper.android_enhancer.R

@Composable
fun Preference(
    title: String,
    description: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    endItem: PreferenceEndItem? = null,
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        onClick = {
            when (endItem) {
                is PreferenceEndItem.Switch -> endItem.onCheckedChange(!endItem.checked)
                else -> onClick?.invoke()
            }
        }
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

            when (endItem) {
                is PreferenceEndItem.Switch -> {
                    Spacer(modifier = Modifier.width(16.dp))
                    Switch(
                        checked = endItem.checked,
                        onCheckedChange = endItem.onCheckedChange
                    )
                }

                is PreferenceEndItem.Arrow -> {
                    Icon(
                        imageVector = ImageVector.vectorResource(R.drawable.ic_chevron_right),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                null -> { /* No end item */
                }
            }
        }
    }
}

sealed class PreferenceEndItem {
    data class Switch(
        val checked: Boolean,
        val onCheckedChange: (Boolean) -> Unit
    ) : PreferenceEndItem()

    data object Arrow : PreferenceEndItem()
}