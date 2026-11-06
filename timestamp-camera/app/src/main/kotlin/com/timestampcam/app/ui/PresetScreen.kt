package com.timestampcam.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.timestampcam.app.MainViewModel
import com.timestampcam.app.R

@Composable
fun PresetScreen(vm: MainViewModel, onBack: () -> Unit) {
    val presets by vm.presets.collectAsStateWithLifecycle()
    var draft by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<Int?>(null) }
    var editingText by remember { mutableStateOf("") }
    val invalid = stringResource(R.string.preset_invalid)

    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .padding(horizontal = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = Color.White)
            }
            Text(stringResource(R.string.presets), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(
            stringResource(R.string.preset_help),
            color = Color.White.copy(alpha = 0.75f),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        if (presets.isEmpty()) {
            Text(
                stringResource(R.string.empty_presets),
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        LazyColumn(Modifier.weight(1f)) {
            itemsIndexed(presets, key = { index, text -> "$index-$text" }) { index, text ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text,
                        color = Color.White,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    )
                    IconButton(onClick = {
                        editing = index
                        editingText = text
                    }) {
                        Text(stringResource(R.string.edit), color = Color(0xFFFFC857), fontSize = 13.sp)
                    }
                    IconButton(onClick = { vm.movePreset(index, index - 1) }, enabled = index > 0) {
                        Icon(Icons.Filled.KeyboardArrowUp, stringResource(R.string.move_up), tint = Color.White)
                    }
                    IconButton(onClick = { vm.movePreset(index, index + 1) }, enabled = index < presets.lastIndex) {
                        Icon(Icons.Filled.KeyboardArrowDown, stringResource(R.string.move_down), tint = Color.White)
                    }
                    IconButton(onClick = { vm.removePreset(index) }) {
                        Icon(Icons.Filled.Delete, stringResource(R.string.delete), tint = Color(0xFFFF8A80))
                    }
                }
            }
        }
        error?.let {
            Text(it, color = Color(0xFFFF8A80), modifier = Modifier.padding(horizontal = 16.dp))
        }
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.preset_placeholder)) },
                singleLine = true,
            )
            TextButton(onClick = {
                if (vm.addPreset(draft)) {
                    draft = ""
                    error = null
                } else {
                    error = invalid
                }
            }) { Text(stringResource(R.string.add)) }
        }
    }

    editing?.let { index ->
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(stringResource(R.string.edit)) },
            text = {
                OutlinedTextField(
                    value = editingText,
                    onValueChange = { editingText = it },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (vm.updatePreset(index, editingText)) {
                        editing = null
                        error = null
                    } else {
                        error = invalid
                    }
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { editing = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}
