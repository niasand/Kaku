package com.timestampcam.app.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.timestampcam.app.Exporter
import com.timestampcam.app.MainViewModel
import com.timestampcam.app.R
import com.timestampcam.app.formatClock
import com.timestampcam.core.SubtitleTimeline
import kotlinx.coroutines.delay

@Composable
fun PlayerScreen(
    vm: MainViewModel,
    sessionId: String,
    onBack: () -> Unit,
) {
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val presets by vm.presets.collectAsStateWithLifecycle()
    val session = sessions.firstOrNull { it.id == sessionId }

    if (session == null) {
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = Color.White)
            }
            Text(stringResource(R.string.empty_library), color = Color.White)
        }
        return
    }

    LoadedPlayer(
        session = session,
        presets = presets,
        onBack = onBack,
        onAddCue = { text, atMs -> vm.addPlaybackCue(session.id, text, atMs) },
        onDeleteCue = { cueId -> vm.deleteCue(session.id, cueId) },
    )
}

@Composable
private fun LoadedPlayer(
    session: com.timestampcam.core.Session,
    presets: List<String>,
    onBack: () -> Unit,
    onAddCue: (String, Long) -> Unit,
    onDeleteCue: (String) -> Unit,
) {
    val context = LocalContext.current
    var banner by remember { mutableStateOf<String?>(null) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(false) }
    val player = remember(session.id) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(session.contentUri)))
            prepare()
        }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    LaunchedEffect(player) {
        while (true) {
            positionMs = player.currentPosition.coerceAtLeast(0L)
            delay(100)
        }
    }

    val duration = maxOf(session.durationMs, player.duration.takeIf { it > 0 } ?: 0L, positionMs)
    val overlayCues = SubtitleTimeline.activeAt(session.cues, positionMs).filter { !it.burned }

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = Color.White)
            }
            Text(
                session.displayName,
                color = Color.White,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            IconButton(onClick = { Exporter.shareVideo(context, Uri.parse(session.contentUri)) }) {
                Icon(Icons.Filled.Share, stringResource(R.string.share_video), tint = Color.White)
            }
            IconButton(onClick = { banner = Exporter.exportSrt(context, session) }) {
                Icon(Icons.Filled.Subtitles, stringResource(R.string.export_srt), tint = Color.White)
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.Black),
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = player
                        useController = false
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            if (overlayCues.isNotEmpty()) {
                Text(
                    overlayCues.joinToString("\n") { it.text },
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = 24.dp, end = 24.dp, bottom = 28.dp)
                        .background(Color.Black.copy(alpha = 0.62f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            banner?.let { Text(it, color = Color(0xFFFFC857), fontSize = 13.sp) }
            CueTimeline(
                durationMs = duration,
                positionMs = positionMs,
                cues = session.cues,
                onSeek = { ms ->
                    player.seekTo(ms)
                    positionMs = ms
                },
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { if (playing) player.pause() else player.play() }) {
                    Icon(
                        if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        if (playing) stringResource(R.string.pause) else stringResource(R.string.play),
                        tint = Color.White,
                    )
                }
                Text("${formatClock(positionMs)} / ${formatClock(duration)}", color = Color.White, fontSize = 13.sp)
            }
            Text(stringResource(R.string.playback_hint), color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp)
            Text(
                stringResource(R.string.legend),
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            PresetChips(
                presets = presets,
                active = null,
                enabled = true,
                onTap = { text ->
                    onAddCue(text, positionMs)
                    banner = context.getString(R.string.cue_added, formatClock(positionMs))
                },
                modifier = Modifier.fillMaxWidth(),
            )
            if (session.cues.isEmpty()) {
                Text(stringResource(R.string.no_cues), color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(top = 8.dp))
            } else {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 180.dp).padding(top = 6.dp)) {
                    items(session.cues, key = { it.id }) { cue ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(cue.text, color = Color.White)
                                Text(
                                    "${formatClock(cue.startMs)} – ${if (cue.endMs < 0) "…" else formatClock(cue.endMs)}  ·  " +
                                        if (cue.burned) stringResource(R.string.burned) else stringResource(R.string.timeline_only),
                                    color = if (cue.burned) Color(0xFFFFC857) else Color(0xFF7AD7F0),
                                    fontSize = 12.sp,
                                )
                            }
                            IconButton(onClick = { onDeleteCue(cue.id) }) {
                                Icon(Icons.Filled.Delete, stringResource(R.string.delete), tint = Color(0xFFFF8A80))
                            }
                        }
                    }
                }
            }
        }
    }
}
