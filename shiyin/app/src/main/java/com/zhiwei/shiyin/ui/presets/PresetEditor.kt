package com.zhiwei.shiyin.ui.presets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zhiwei.shiyin.data.PresetRepository
import com.zhiwei.shiyin.ui.theme.Ink
import com.zhiwei.shiyin.ui.theme.Mist
import com.zhiwei.shiyin.ui.theme.RecordRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresetScreen(repository: PresetRepository, onBack: () -> Unit) {
    Scaffold(
        containerColor = Ink,
        topBar = {
            TopAppBar(
                title = { Text("字幕文案") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        PresetEditor(
            repository = repository,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 8.dp),
        )
    }
}

@Composable
fun PresetEditor(repository: PresetRepository, modifier: Modifier = Modifier) {
    val presets by repository.presets.collectAsStateWithLifecycle()
    var draft by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("固定字幕文案", color = androidx.compose.ui.graphics.Color.White)
        Text("录像时或回放时点一下，这句话就记到当时的时间线上。", color = Mist)
        OutlinedTextField(
            value = draft,
            onValueChange = { if (it.length <= 60) draft = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("例如：门口检查完毕") },
            trailingIcon = {
                TextButton(onClick = {
                    val error = repository.add(draft)
                    message = error
                    if (error == null) draft = ""
                }) {
                    Text("添加")
                }
            },
        )
        if (message != null) {
            Text(message.orEmpty(), color = RecordRed)
        }
        presets.forEachIndexed { index, preset ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = preset.text,
                        onValueChange = { value ->
                            if (value != preset.text) message = repository.update(preset.id, value)
                        },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    IconButton(onClick = { repository.move(preset.id, -1) }, enabled = index > 0) {
                        Icon(Icons.Filled.ArrowUpward, contentDescription = "上移")
                    }
                    IconButton(
                        onClick = { repository.move(preset.id, 1) },
                        enabled = index < presets.lastIndex,
                    ) {
                        Icon(Icons.Filled.ArrowDownward, contentDescription = "下移")
                    }
                    IconButton(onClick = { repository.delete(preset.id) }) {
                    Icon(Icons.Filled.Delete, contentDescription = "删除")
                }
            }
        }
    }
}
