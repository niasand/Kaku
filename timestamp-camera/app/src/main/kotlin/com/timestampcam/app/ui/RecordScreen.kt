package com.timestampcam.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.view.View
import androidx.camera.core.CameraSelector
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.timestampcam.app.MainViewModel
import com.timestampcam.app.R
import com.timestampcam.app.camera.OverlayState
import com.timestampcam.app.camera.TimestampCamera
import com.timestampcam.app.formatClock
import kotlinx.coroutines.delay

private class CameraHandle {
    var camera: TimestampCamera? = null
}

@Composable
fun RecordScreen(
    vm: MainViewModel,
    onOpenLibrary: () -> Unit,
    onOpenPresets: () -> Unit,
    onRecorded: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val view = LocalView.current
    val ui by vm.record.collectAsStateWithLifecycle()
    val presets by vm.presets.collectAsStateWithLifecycle()
    var cameraGranted by remember { mutableStateOf(hasPermission(context, Manifest.permission.CAMERA)) }
    var audioGranted by remember { mutableStateOf(hasPermission(context, Manifest.permission.RECORD_AUDIO)) }
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        cameraGranted = result[Manifest.permission.CAMERA] == true
        audioGranted = result[Manifest.permission.RECORD_AUDIO] == true
        if (!audioGranted) vm.setAudioEnabled(false)
    }
    val overlayState = remember { OverlayState() }
    val cameraHandle = remember { CameraHandle() }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }

    SideEffect { overlayState.subtitle = ui.activeSubtitle.orEmpty() }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                cameraGranted = hasPermission(context, Manifest.permission.CAMERA)
                audioGranted = hasPermission(context, Manifest.permission.RECORD_AUDIO)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(cameraGranted, audioGranted) {
        if (!cameraGranted) {
            permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
        }
    }

    LaunchedEffect(ui.isRecording) {
        if (!ui.isRecording) return@LaunchedEffect
        while (true) {
            vm.refreshElapsed()
            delay(200)
        }
    }

    androidx.activity.compose.BackHandler(enabled = ui.isRecording) {
        cameraHandle.camera?.stopRecording()
    }

    LaunchedEffect(ui.openSessionId) {
        val id = ui.openSessionId ?: return@LaunchedEffect
        vm.consumeOpenSession()
        onRecorded(id)
    }

    DisposableEffect(ui.isRecording) {
        view.keepScreenOn = ui.isRecording
        onDispose { view.keepScreenOn = false }
    }

    val lens = ui.lensFacing
    val surface = previewView
    DisposableEffect(surface, lens, cameraGranted) {
        if (!cameraGranted || surface == null) return@DisposableEffect onDispose {}
        val camera = TimestampCamera(context, overlayState)
        cameraHandle.camera = camera
        var bound = false
        fun tryBind() {
            if (bound || surface.width == 0 || surface.height == 0) return
            bound = true
            camera.bind(surface, lifecycleOwner, lens) { ready, message ->
                vm.setCameraReady(ready, message)
            }
        }
        val listener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> tryBind() }
        surface.addOnLayoutChangeListener(listener)
        surface.post { tryBind() }
        onDispose {
            surface.removeOnLayoutChangeListener(listener)
            cameraHandle.camera = null
            camera.release()
            vm.setCameraReady(false, null)
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (cameraGranted) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    }.also { previewView = it }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onOpenLibrary, enabled = !ui.isRecording) {
                    Icon(Icons.Filled.PhotoLibrary, stringResource(R.string.library), tint = Color.White)
                }
                Row {
                    IconButton(
                        onClick = { vm.setAudioEnabled(!ui.audioEnabled) },
                        enabled = audioGranted && !ui.isRecording,
                    ) {
                        Icon(
                            if (ui.audioEnabled && audioGranted) Icons.Filled.Mic else Icons.Filled.MicOff,
                            if (ui.audioEnabled) stringResource(R.string.mic_on) else stringResource(R.string.mic_off),
                            tint = if (ui.audioEnabled && audioGranted) Color.White else Color(0xFFFF8A80),
                        )
                    }
                    IconButton(onClick = { vm.switchLens() }, enabled = !ui.isRecording && !ui.starting) {
                        Icon(Icons.Filled.Cameraswitch, stringResource(R.string.switch_camera), tint = Color.White)
                    }
                    IconButton(onClick = onOpenPresets, enabled = !ui.isRecording) {
                        Icon(Icons.Filled.Subtitles, stringResource(R.string.presets), tint = Color.White)
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f))),
                        RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                    )
                    .padding(top = 18.dp, bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ui.banner?.let { banner ->
                    TextButton(onClick = { vm.dismissBanner() }) {
                        Text(banner, color = Color(0xFFFFC857))
                    }
                }
                if (ui.isRecording) {
                    Text(
                        "● ${formatClock(ui.elapsedMs)}",
                        color = Color(0xFFFF3B30),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp,
                    )
                }
                if (ui.cues.isNotEmpty() || ui.isRecording) {
                    CueTimeline(
                        durationMs = maxOf(ui.elapsedMs, 1_000L),
                        positionMs = ui.elapsedMs,
                        cues = ui.cues,
                        onSeek = null,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                Text(
                    text = if (ui.isRecording) {
                        stringResource(R.string.record_hint)
                    } else {
                        stringResource(R.string.record_hint_idle)
                    },
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 13.sp,
                )
                if (!audioGranted) {
                    Text(
                        stringResource(R.string.audio_missing),
                        color = Color(0xFFFF8A80),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                PresetChips(
                    presets = presets,
                    active = ui.activeSubtitle,
                    enabled = ui.cameraReady || ui.isRecording,
                    onTap = vm::onPresetTapped,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                )
                Shutter(
                    recording = ui.isRecording,
                    enabled = ui.cameraReady && !ui.starting,
                    onClick = {
                        val camera = cameraHandle.camera ?: return@Shutter
                        if (ui.isRecording) {
                            camera.stopRecording()
                        } else {
                            vm.markStarting()
                            val result = camera.startRecording(ui.audioEnabled && audioGranted) { event ->
                                vm.onRecordEvent(event, camera.pendingDisplayName)
                            }
                            if (!result.started) vm.failStart(result.message) else result.message?.let(vm::showBanner)
                        }
                    },
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }

        if (!cameraGranted) {
            PermissionGate(
                onGrant = {
                    permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
                },
                onSettings = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                },
            )
        }
    }
}

@Composable
private fun Shutter(
    recording: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .size(78.dp)
            .border(4.dp, Color.White, CircleShape)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(if (recording) 32.dp else 58.dp)
                .clip(if (recording) RoundedCornerShape(8.dp) else CircleShape)
                .background(if (enabled) Color(0xFFFF3B30) else Color(0xFF7A403C)),
        )
    }
}

@Composable
private fun PermissionGate(onGrant: () -> Unit, onSettings: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.88f))
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.permission_title), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        Text(
            stringResource(R.string.permission_body),
            color = Color.White.copy(alpha = 0.8f),
            modifier = Modifier.padding(top = 12.dp, bottom = 20.dp),
        )
        TextButton(onClick = onGrant) { Text(stringResource(R.string.grant)) }
        TextButton(onClick = onSettings) { Text(stringResource(R.string.open_settings)) }
    }
}

private fun hasPermission(context: android.content.Context, permission: String): Boolean {
    return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
