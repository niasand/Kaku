package com.timestampcam.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.timestampcam.app.formatClock
import com.timestampcam.core.Session
import com.timestampcam.core.TimeWatermark
import java.time.ZoneId

@Composable
fun LibraryScreen(
    vm: MainViewModel,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
) {
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<Session?>(null) }
    Column(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = Color.White)
            }
            Text(stringResource(R.string.library), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }
        if (sessions.isEmpty()) {
            Text(
                stringResource(R.string.empty_library),
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.padding(20.dp),
            )
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                items(sessions, key = { it.id }) { session ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpen(session.id) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(session.displayName, color = Color.White, fontWeight = FontWeight.Medium)
                            Text(
                                "${TimeWatermark.format(session.createdAtEpochMs, ZoneId.systemDefault())}  ·  ${formatClock(session.durationMs)}  ·  ${session.cues.size} 条字幕",
                                color = Color.White.copy(alpha = 0.65f),
                                fontSize = 13.sp,
                            )
                        }
                        IconButton(onClick = { pendingDelete = session }) {
                            Icon(Icons.Filled.Delete, stringResource(R.string.delete), tint = Color(0xFFFF8A80))
                        }
                    }
                }
            }
        }
    }
    pendingDelete?.let { session ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.delete_video_title)) },
            text = { Text(stringResource(R.string.delete_video_body)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteSession(session.id)
                    pendingDelete = null
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}
