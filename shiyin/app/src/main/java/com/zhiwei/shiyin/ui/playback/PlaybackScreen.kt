package com.zhiwei.shiyin.ui.playback

import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.zhiwei.shiyin.core.RecordingMeta
import com.zhiwei.shiyin.core.SubtitleTimeline
import com.zhiwei.shiyin.core.formatElapsed
import com.zhiwei.shiyin.core.formatWatermark
import com.zhiwei.shiyin.data.PresetRepository
import com.zhiwei.shiyin.data.RecordingRepository
import com.zhiwei.shiyin.ui.common.PresetChipRow
import com.zhiwei.shiyin.ui.common.shareFile
import com.zhiwei.shiyin.ui.theme.Gold
import com.zhiwei.shiyin.ui.theme.Ink
import com.zhiwei.shiyin.ui.theme.Mist
import com.zhiwei.shiyin.ui.theme.Panel
import java.time.ZoneId
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackScreen(
    recordings: RecordingRepository,
    presets: PresetRepository,
    id: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var meta by remember(id) { mutableStateOf(recordings.load(id)) }
    var confirmDelete by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    val presetList by presets.presets.collectAsStateWithLifecycle()
    val video = recordings.videoFile(id)
    val current = meta

    Scaffold(
        containerColor = Ink,
        topBar = {
            TopAppBar(
                title = { Text("回放") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (current != null) {
                        IconButton(onClick = {
                            val shared = shareFile(context, video, "video/mp4", "分享录像")
                            if (!shared) notice = "没有可以分享的应用"
                        }) {
                            Icon(Icons.Filled.Share, contentDescription = "分享视频")
                        }
                        TextButton(onClick = {
                            val srt = recordings.srtFile(id)
                            if (!srt.isFile || srt.length() == 0L) {
                                notice = "这条录像还没有字幕"
                            } else if (!shareFile(context, srt, "application/x-subrip", "分享字幕")) {
                                notice = "没有可以分享的应用"
                            }
                        }) { Text("SRT") }
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "删除")
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (current == null || !video.isFile) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("找不到这条录像", color = Mist)
            }
            return@Scaffold
        }
        PlaybackBody(
            meta = current,
            modifier = Modifier.fillMaxSize().padding(padding),
            presets = presetList.map { it },
            notice = notice,
            onAdd = { position, text ->
                val next = current.copy(cues = SubtitleTimeline.addCue(current.cues, position, text))
                recordings.save(next)
                meta = next
            },
            onClear = { position ->
                val next = current.copy(cues = SubtitleTimeline.clearAt(current.cues, position))
                recordings.save(next)
                meta = next
            },
            onRemove = { startMs ->
                val next = current.copy(cues = SubtitleTimeline.removeCue(current.cues, startMs))
                recordings.save(next)
                meta = next
            },
            videoPath = video.absolutePath,
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除这条录像？") },
            confirmButton = {
                TextButton(onClick = {
                    recordings.delete(id)
                    confirmDelete = false
                    onBack()
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun PlaybackBody(
    meta: RecordingMeta,
    videoPath: String,
    presets: List<com.zhiwei.shiyin.core.SubtitlePreset>,
    notice: String?,
    onAdd: (Long, String) -> Unit,
    onClear: (Long) -> Unit,
    onRemove: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val player = remember(videoPath) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(java.io.File(videoPath))))
            prepare()
            playWhenReady = false
        }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember(meta.durationMs) { mutableLongStateOf(meta.durationMs.coerceAtLeast(0L)) }
    var playing by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(player) {
        while (isActive) {
            if (!dragging) position = player.currentPosition.coerceAtLeast(0L)
            val reported = player.duration
            if (reported > 0) duration = reported
            playing = player.isPlaying
            delay(80)
        }
    }
    val active = SubtitleTimeline.subtitleAt(meta.cues, position)
    val span = duration.coerceAtLeast(1L)

    Column(modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            formatWatermark(meta.createdAtEpochMs, ZoneId.systemDefault()),
            color = Mist,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(Panel)
                .clickable {
                    if (player.isPlaying) player.pause() else player.play()
                },
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { viewContext ->
                    PlayerView(viewContext).apply {
                        this.player = player
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                },
                update = { it.player = player },
            )
            if (!playing) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = "播放",
                    tint = Gold,
                    modifier = Modifier.align(Alignment.Center).padding(8.dp),
                )
            }
            if (active != null) {
                Text(
                    active,
                    color = androidx.compose.ui.graphics.Color.White,
                    fontSize = 22.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(12.dp)
                        .background(androidx.compose.ui.graphics.Color(0xCC101412), RoundedCornerShape(10.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
        Canvas(Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(12.dp)) {
            drawLine(
                color = Mist.copy(alpha = 0.45f),
                start = Offset(0f, size.height / 2f),
                end = Offset(size.width, size.height / 2f),
                strokeWidth = 3f,
            )
            meta.cues.forEach { cue ->
                if (cue.text.isBlank()) return@forEach
                val x = size.width * cue.startMs.toFloat() / span.toFloat()
                drawCircle(color = Gold, radius = 7f, center = Offset(x.coerceIn(0f, size.width), size.height / 2f))
            }
        }
        Slider(
            value = position.coerceIn(0L, span).toFloat(),
            onValueChange = {
                dragging = true
                position = it.toLong()
            },
            onValueChangeFinished = {
                player.seekTo(position)
                dragging = false
            },
            valueRange = 0f..span.toFloat(),
            colors = SliderDefaults.colors(thumbColor = Gold, activeTrackColor = Gold),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (playing) player.pause() else player.play() }) {
                Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = "播放或暂停")
            }
            Text(
                "${formatElapsed(position)} / ${formatElapsed(duration)}",
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { onClear(position) }, enabled = active != null) { Text("清除字幕") }
        }
        Text("点按文案，记在当前播放时间", color = Mist, fontSize = 13.sp)
        if (notice != null) Text(notice, color = com.zhiwei.shiyin.ui.theme.RecordRed, fontSize = 13.sp)
        PresetChipRow(
            presets = presets,
            enabled = true,
            activeText = active,
            onPick = { onAdd(player.currentPosition.coerceAtLeast(0L), it) },
        )
        LazyColumn(modifier = Modifier.weight(0.7f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(meta.cues.filter { it.text.isNotBlank() }, key = { "${it.startMs}-${it.text}" }) { cue ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = {
                        player.seekTo(cue.startMs)
                        position = cue.startMs
                    }) {
                        Text(formatElapsed(cue.startMs), fontFamily = FontFamily.Monospace)
                    }
                    Text(cue.text, modifier = Modifier.weight(1f))
                    IconButton(onClick = { onRemove(cue.startMs) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "删除这条字幕")
                    }
                }
            }
        }
    }
}
