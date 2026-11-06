package com.timestampcam.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun PresetChips(
    presets: List<String>,
    active: String?,
    enabled: Boolean,
    onTap: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        presets.forEach { text ->
            FilterChip(
                selected = text == active,
                onClick = { onTap(text) },
                enabled = enabled,
                label = { Text(text, maxLines = 1) },
                modifier = Modifier.padding(vertical = 2.dp),
            )
        }
    }
}
