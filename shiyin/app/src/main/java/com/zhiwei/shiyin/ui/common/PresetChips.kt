package com.zhiwei.shiyin.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.zhiwei.shiyin.core.SubtitlePreset

@Composable
fun PresetChipRow(
    presets: List<SubtitlePreset>,
    enabled: Boolean,
    activeText: String?,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    LazyRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(presets, key = { it.id }) { preset ->
            FilterChip(
                selected = preset.text == activeText,
                enabled = enabled,
                onClick = {
                    if (!enabled) return@FilterChip
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onPick(preset.text)
                },
                label = { Text(preset.text) },
            )
        }
    }
}
