package com.zhiwei.shiyin.ui.record

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.camera.core.CameraSelector
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zhiwei.shiyin.camera.VideoSession
import com.zhiwei.shiyin.core.RecordingMeta
import com.zhiwei.shiyin.core.SubtitleTimeline
import com.zhiwei.shiyin.core.formatElapsed
import com.zhiwei.shiyin.data.PresetRepository
import com.zhiwei.shiyin.data.RecordingRepository
import com.zhiwei.shiyin.ui.common.PresetChipRow
import com.zhiwei.shiyin.ui.presets.PresetEditor
import com.zhiwei.shiyin.ui.theme.Cream
import com.zhiwei.shiyin.ui.theme.Gold
import com.zhiwei.shiyin.ui.theme.Mist
import com.zhiwei.shiyin.ui.theme.RecordRed
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordScreen(
    recordings: RecordingRepository,
    presets: PresetRepository,
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
) {
    val context = LocalContext.current
    var cameraGranted by remember {
        mutableStateOf(granted(context, Manifest.permission.CAMERA))
    }
    var audioGranted by remember {
        mutableStateOf(granted(context, Manifest.permission.RECORD_AUDIO))
    }
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        cameraGranted = result[Manifest.permission.CAMERA] == true
        audioGranted = result[Manifest.permission.RECORD_AUDIO] == true
    }
    LaunchedEffect(Unit) {
        if (!cameraGranted || !audioGranted) {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO),
            )
        }
    }
    if (!cameraGranted) {
        PermissionGate(onBack = onBack, onSettings = {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null),
            )
            context.startActivity(intent)
        })
        return
    }

    val vm: RecordViewModel = viewModel()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val session = remember {
        VideoSession(context) { message -> scope.launch { snackbar.showSnackbar(message) } }
    }
    val alive = remember { AtomicBoolean(true) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val view = LocalView.current
    var facing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var cameraReady by remember { mutableStateOf(false) }
    var flashAvailable by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    var showPresets by remember { mutableStateOf(false) }
    val presetList by presets.presets.collectAsStateWithLifecycle()
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    DisposableEffect(session) {
        alive.set(true)
        view.keepScreenOn = true
        onDispose {
            alive.set(false)
            view.keepScreenOn = false
            session.close()
        }
    }
    DisposableEffect(lifecycleOwner, session) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && vm.phase == RecordViewModel.Phase.Recording) {
                vm.markSaving()
                session.stop()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(facing, lifecycleOwner) {
        session.onBound = {
            cameraReady = true
            flashAvailable = session.hasFlash
        }
        val bind = Runnable { session.bind(lifecycleOwner, previewView, facing) }
        previewView.post(bind)
        onDispose { previewView.removeCallbacks(bind) }
    }
    LaunchedEffect(vm.error) {
        val message = vm.error ?: return@LaunchedEffect
        snackbar.showSnackbar(message)
        vm.error = null
    }

    val recording = vm.phase != RecordViewModel.Phase.Idle
    BackHandler(enabled = recording) { confirmLeave = true }

    val active = SubtitleTimeline.subtitleAt(vm.cues, vm.elapsedMs)
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 56.dp),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { if (recording) confirmLeave = true else onBack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = Cream)
            }
            Text(
                if (vm.phase == RecordViewModel.Phase.Recording) formatElapsed(vm.elapsedMs) else "时印",
                color = Cream,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f),
                fontSize = 18.sp,
            )
            if (!audioGranted) {
                TextButton(onClick = {
                    permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                }) { Text("开启录音") }
            } else {
                IconButton(onClick = { vm.audioEnabled = !vm.audioEnabled }) {
                    Icon(
                        if (vm.audioEnabled) Icons.Filled.Mic else Icons.Filled.MicOff,
                        contentDescription = if (vm.audioEnabled) "关闭录音" else "打开录音",
                        tint = if (vm.audioEnabled) Gold else Mist,
                    )
                }
            }
            if (flashAvailable && facing == CameraSelector.LENS_FACING_BACK) {
                IconButton(onClick = {
                    vm.torchEnabled = !vm.torchEnabled
                    session.setTorch(vm.torchEnabled)
                }) {
                    Icon(
                        if (vm.torchEnabled) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                        contentDescription = "闪光灯",
                        tint = if (vm.torchEnabled) Gold else Cream,
                    )
                }
            }
            IconButton(onClick = { showPresets = true }) {
                Icon(Icons.Filled.TextFields, contentDescription = "编辑字幕文案", tint = Cream)
            }
            IconButton(
                onClick = {
                    if (vm.phase != RecordViewModel.Phase.Idle) return@IconButton
                    facing = if (facing == CameraSelector.LENS_FACING_BACK) {
                        CameraSelector.LENS_FACING_FRONT
                    } else {
                        CameraSelector.LENS_FACING_BACK
                    }
                    vm.torchEnabled = false
                    cameraReady = false
                },
                enabled = vm.phase == RecordViewModel.Phase.Idle,
            ) {
                Icon(Icons.Filled.Cameraswitch, contentDescription = "切换摄像头", tint = Cream)
            }
        }

        if (active != null) {
            Text(
                active,
                color = Color.White,
                fontSize = 28.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 210.dp, start = 24.dp, end = 24.dp)
                    .background(Color(0xCC101412), RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (vm.cues.any { it.text.isNotBlank() }) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(Color(0x99101412), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    vm.cues.filter { it.text.isNotBlank() }.takeLast(3).forEach { cue ->
                        Text(
                            "${formatElapsed(cue.startMs)}  ${cue.text}",
                            color = Cream,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
            Text(
                when (vm.phase) {
                    RecordViewModel.Phase.Idle -> "时间水印会写进每一帧。开录后点文案，字幕记到时间线。"
                    RecordViewModel.Phase.Recording -> "点一下文案，记在这一秒"
                    RecordViewModel.Phase.Saving -> "正在保存"
                },
                color = Mist,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
            if (presetList.isEmpty()) {
                TextButton(onClick = { showPresets = true }) { Text("先添加字幕文案") }
            } else {
                PresetChipRow(
                    presets = presetList,
                    enabled = vm.phase == RecordViewModel.Phase.Recording,
                    activeText = active,
                    onPick = vm::addPreset,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = vm::clearSubtitle,
                    enabled = vm.phase == RecordViewModel.Phase.Recording && active != null,
                ) { Text("清除字幕") }
            }
            RecordButton(
                phase = vm.phase,
                enabled = cameraReady && vm.phase != RecordViewModel.Phase.Saving,
                onClick = {
                    when (vm.phase) {
                        RecordViewModel.Phase.Idle -> startTake(
                            session = session,
                            vm = vm,
                            recordings = recordings,
                            fileAudio = audioGranted && vm.audioEnabled,
                            alive = alive,
                            onSaved = onSaved,
                        )
                        RecordViewModel.Phase.Recording -> {
                            vm.markSaving()
                            session.stop()
                        }
                        RecordViewModel.Phase.Saving -> Unit
                    }
                },
            )
        }
    }

    if (showPresets) {
        ModalBottomSheet(onDismissRequest = { showPresets = false }) {
            PresetEditor(
                repository = presets,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .padding(bottom = 24.dp),
            )
        }
    }
    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("停止并保存？") },
            text = { Text("当前录像和已经点过的字幕会保存下来。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmLeave = false
                    vm.markSaving()
                    session.stop()
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { confirmLeave = false }) { Text("继续拍") }
            },
        )
    }
}

private fun startTake(
    session: VideoSession,
    vm: RecordViewModel,
    recordings: RecordingRepository,
    fileAudio: Boolean,
    alive: AtomicBoolean,
    onSaved: (String) -> Unit,
) {
    if (!session.isReady) {
        vm.error = "相机还在启动"
        return
    }
    val file = recordings.newVideoFile()
    try {
        session.start(file, withAudio = fileAudio) { event ->
            when (event) {
                is VideoRecordEvent.Start -> vm.onStarted()
                is VideoRecordEvent.Finalize -> {
                    val saved = persistTake(recordings, file, event, vm.cuesRef.get(), fileAudio)
                    vm.onFinished()
                    if (saved != null && alive.get()) onSaved(saved)
                    else if (saved == null) vm.error = "这次没有录下有效画面"
                }
                else -> Unit
            }
        }
    } catch (error: SecurityException) {
        file.delete()
        vm.error = error.message ?: "没有录音或相机权限"
    } catch (error: RuntimeException) {
        file.delete()
        vm.error = error.message ?: "无法开始录制"
    }
}

private fun persistTake(
    recordings: RecordingRepository,
    file: File,
    event: VideoRecordEvent.Finalize,
    cues: List<com.zhiwei.shiyin.core.SubtitleCue>,
    hasAudio: Boolean,
): String? {
    val recoverable = event.error == VideoRecordEvent.Finalize.ERROR_SOURCE_INACTIVE ||
        event.error == VideoRecordEvent.Finalize.ERROR_FILE_SIZE_LIMIT_REACHED ||
        event.error == VideoRecordEvent.Finalize.ERROR_DURATION_LIMIT_REACHED
    val usable = file.isFile && file.length() > 1024 && (!event.hasError() || recoverable)
    if (!usable) {
        file.delete()
        return null
    }
    val meta = RecordingMeta(
        id = file.nameWithoutExtension,
        createdAtEpochMs = System.currentTimeMillis(),
        durationMs = event.recordingStats.recordedDurationNanos / 1_000_000L,
        hasAudio = hasAudio,
        cues = SubtitleTimeline.sorted(cues),
    )
    recordings.save(meta)
    return meta.id
}

@Composable
private fun RecordButton(phase: RecordViewModel.Phase, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(78.dp)
            .clip(CircleShape)
            .background(if (enabled) Color.White.copy(alpha = 0.92f) else Color.White.copy(alpha = 0.35f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(if (phase == RecordViewModel.Phase.Recording) 30.dp else 58.dp)
                .clip(if (phase == RecordViewModel.Phase.Recording) RoundedCornerShape(8.dp) else CircleShape)
                .background(RecordRed),
        )
    }
}

@Composable
private fun PermissionGate(onBack: () -> Unit, onSettings: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(com.zhiwei.shiyin.ui.theme.Ink)
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("需要相机才能录像", color = Cream, fontSize = 24.sp)
        Text("时间水印会直接写进画面，所以要使用相机。录音用来给视频配上声音。", color = Mist, modifier = Modifier.padding(top = 12.dp))
        Row(Modifier.padding(top = 20.dp)) {
            TextButton(onClick = onBack) { Text("返回") }
            TextButton(onClick = onSettings) { Text("去设置里开启") }
        }
    }
}

private fun granted(context: android.content.Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
