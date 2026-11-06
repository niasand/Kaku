package com.zhiwei.shiyin.ui.library

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zhiwei.shiyin.core.formatElapsed
import com.zhiwei.shiyin.core.formatWatermark
import com.zhiwei.shiyin.data.RecordingRepository
import com.zhiwei.shiyin.ui.theme.Gold
import com.zhiwei.shiyin.ui.theme.Ink
import com.zhiwei.shiyin.ui.theme.Mist
import com.zhiwei.shiyin.ui.theme.Panel
import java.io.File
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    recordings: RecordingRepository,
    onRecord: () -> Unit,
    onPresets: () -> Unit,
    onOpen: (String) -> Unit,
) {
    val items by recordings.items.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { recordings.refresh() }

    Scaffold(
        containerColor = Ink,
        topBar = {
            TopAppBar(
                title = { Text("时印") },
                actions = {
                    TextButton(onClick = onPresets) { Text("字幕文案") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onRecord,
                icon = { Icon(Icons.Filled.Videocam, contentDescription = null) },
                text = { Text("开始录像") },
            )
        },
    ) { padding ->
        if (items.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 28.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text("时间写进每一帧", color = Gold, fontSize = 28.sp)
                Text(
                    "录像自带年月日时分秒水印，并收录声音。事先写好固定字幕，拍摄或回放时点一下，就记到那一秒。",
                    color = Mist,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(items, key = { it.id }) { item ->
                    val sound = if (item.hasAudio) "含录音" else "无录音"
                    val cueCount = item.cues.count { it.text.isNotBlank() }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Panel)
                            .clickable { onOpen(item.id) }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RecordingThumbnail(
                            file = recordings.videoFile(item.id),
                            modifier = Modifier
                                .size(84.dp)
                                .clip(RoundedCornerShape(12.dp)),
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp),
                        ) {
                            Text(
                                formatWatermark(item.createdAtEpochMs, ZoneId.systemDefault()),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                            )
                            Text(
                                "${formatElapsed(item.durationMs)} · $cueCount 条字幕 · $sound",
                                color = Mist,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        IconButton(onClick = { pendingDelete = item.id }) {
                            Icon(Icons.Filled.Delete, contentDescription = "删除")
                        }
                    }
                }
            }
        }
    }

    if (pendingDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除这条录像？") },
            text = { Text("视频和字幕时间线会一起删掉。") },
            confirmButton = {
                TextButton(onClick = {
                    recordings.delete(pendingDelete!!)
                    pendingDelete = null
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun RecordingThumbnail(file: File, modifier: Modifier = Modifier) {
    var image by remember(file.absolutePath) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(file.absolutePath) {
        val bitmap = withContext(Dispatchers.IO) { decodeFrame(file) }
        image = bitmap?.asImageBitmap()
    }
    val current = image
    if (current != null) {
        Image(
            bitmap = current,
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(modifier.background(Ink))
    }
}

private fun decodeFrame(file: File): Bitmap? {
    if (!file.isFile) return null
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(file.absolutePath)
        retriever.getFrameAtTime(500_000)
    } catch (_: RuntimeException) {
        null
    } finally {
        retriever.release()
    }
}
