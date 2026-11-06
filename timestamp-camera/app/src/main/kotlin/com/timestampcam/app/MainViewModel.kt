package com.timestampcam.app

import android.app.Application
import android.net.Uri
import android.os.SystemClock
import androidx.camera.core.CameraSelector
import androidx.camera.video.VideoRecordEvent
import androidx.lifecycle.AndroidViewModel
import com.timestampcam.core.Cue
import com.timestampcam.core.PresetStore
import com.timestampcam.core.Presets
import com.timestampcam.core.Session
import com.timestampcam.core.SessionStore
import com.timestampcam.core.SubtitleTimeline
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File

data class RecordUiState(
    val cameraReady: Boolean = false,
    val isRecording: Boolean = false,
    val starting: Boolean = false,
    val audioEnabled: Boolean = true,
    val lensFacing: Int = CameraSelector.LENS_FACING_BACK,
    val activeSubtitle: String? = null,
    val cues: List<Cue> = emptyList(),
    val elapsedMs: Long = 0L,
    val banner: String? = null,
    val openSessionId: String? = null,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val presetStore = PresetStore(File(app.filesDir, "presets.json"))
    private val sessionStore = SessionStore(File(app.filesDir, "sessions"))

    private val _presets = MutableStateFlow(presetStore.load())
    val presets: StateFlow<List<String>> = _presets.asStateFlow()

    private val _sessions = MutableStateFlow(sessionStore.loadAll())
    val sessions: StateFlow<List<Session>> = _sessions.asStateFlow()

    private val _record = MutableStateFlow(RecordUiState())
    val record: StateFlow<RecordUiState> = _record.asStateFlow()

    private var recordAnchorElapsed = 0L

    fun setCameraReady(ready: Boolean, message: String?) {
        _record.update {
            it.copy(
                cameraReady = ready,
                banner = when {
                    message != null -> message
                    ready -> null
                    else -> it.banner
                },
            )
        }
    }

    fun setAudioEnabled(enabled: Boolean) {
        if (_record.value.isRecording || _record.value.starting) return
        _record.update { it.copy(audioEnabled = enabled) }
    }

    fun switchLens() {
        if (_record.value.isRecording || _record.value.starting) return
        _record.update {
            it.copy(
                lensFacing = if (it.lensFacing == CameraSelector.LENS_FACING_BACK) {
                    CameraSelector.LENS_FACING_FRONT
                } else {
                    CameraSelector.LENS_FACING_BACK
                },
                cameraReady = false,
            )
        }
    }

    fun onPresetTapped(text: String) {
        val state = _record.value
        if (!state.isRecording) {
            val next = if (state.activeSubtitle == text) null else text
            _record.update { it.copy(activeSubtitle = next, banner = null) }
            return
        }
        val at = recordedPositionMs()
        val (cues, active) = SubtitleTimeline.tapWhileRecording(
            state.cues,
            text,
            at,
            SubtitleTimeline.newId(),
        )
        _record.update {
            it.copy(
                cues = cues,
                activeSubtitle = active,
                elapsedMs = at,
                banner = if (active == null) "这条字幕已结束" else "已写入时间线 ${formatClock(at)}",
            )
        }
    }

    fun markStarting() {
        _record.update { it.copy(starting = true, banner = null) }
    }

    fun showBanner(message: String?) {
        _record.update { it.copy(banner = message) }
    }

    fun failStart(message: String?) {
        _record.update { it.copy(starting = false, banner = message) }
    }

    fun dismissBanner() {
        _record.update { it.copy(banner = null) }
    }

    fun refreshElapsed() {
        if (!_record.value.isRecording) return
        _record.update { it.copy(elapsedMs = recordedPositionMs()) }
    }

    fun onRecordEvent(event: VideoRecordEvent, displayName: String) {
        when (event) {
            is VideoRecordEvent.Start -> {
                recordAnchorElapsed = SystemClock.elapsedRealtime()
                val state = _record.value
                val active = state.activeSubtitle
                val cues = if (active != null && state.cues.none { it.endMs < 0L }) {
                    SubtitleTimeline.tapWhileRecording(state.cues, active, 0L, SubtitleTimeline.newId()).first
                } else {
                    state.cues
                }
                _record.update {
                    it.copy(isRecording = true, starting = false, cues = cues, elapsedMs = 0L, banner = null)
                }
            }
            is VideoRecordEvent.Status -> {
                val elapsed = event.recordingStats.recordedDurationNanos / 1_000_000L
                recordAnchorElapsed = SystemClock.elapsedRealtime() - elapsed
                _record.update { it.copy(elapsedMs = elapsed) }
            }
            is VideoRecordEvent.Finalize -> {
                if (event.hasError()) {
                    _record.update {
                        it.copy(
                            isRecording = false,
                            starting = false,
                            cues = emptyList(),
                            elapsedMs = 0L,
                            banner = "录像没有保存：${recordErrorMessage(event.error)}",
                        )
                    }
                    return
                }
                val duration = event.recordingStats.recordedDurationNanos / 1_000_000L
                val cues = SubtitleTimeline.closeOpen(_record.value.cues, duration)
                val session = Session(
                    id = SubtitleTimeline.newId(),
                    displayName = displayName,
                    contentUri = event.outputResults.outputUri.toString(),
                    createdAtEpochMs = System.currentTimeMillis(),
                    durationMs = duration,
                    cues = cues,
                )
                sessionStore.save(session)
                _sessions.value = sessionStore.loadAll()
                _record.update {
                    it.copy(
                        isRecording = false,
                        starting = false,
                        cues = emptyList(),
                        activeSubtitle = null,
                        elapsedMs = 0L,
                        banner = null,
                        openSessionId = session.id,
                    )
                }
            }
            else -> Unit
        }
    }

    fun consumeOpenSession() {
        _record.update { it.copy(openSessionId = null) }
    }

    fun addPreset(raw: String): Boolean {
        val next = Presets.add(_presets.value, raw) ?: return false
        persistPresets(next)
        return true
    }

    fun updatePreset(index: Int, raw: String): Boolean {
        val next = Presets.update(_presets.value, index, raw) ?: return false
        persistPresets(next)
        return true
    }

    fun removePreset(index: Int) {
        persistPresets(Presets.removeAt(_presets.value, index))
    }

    fun movePreset(from: Int, to: Int) {
        persistPresets(Presets.move(_presets.value, from, to))
    }

    fun addPlaybackCue(sessionId: String, text: String, atMs: Long) {
        val session = sessionStore.find(sessionId) ?: return
        val cues = SubtitleTimeline.insertPlaybackCue(
            cues = session.cues,
            text = text,
            atMs = atMs,
            id = SubtitleTimeline.newId(),
        )
        persistSession(session.copy(cues = cues))
    }

    fun deleteCue(sessionId: String, cueId: String) {
        val session = sessionStore.find(sessionId) ?: return
        persistSession(session.copy(cues = SubtitleTimeline.remove(session.cues, cueId)))
    }

    fun deleteSession(sessionId: String) {
        val removed = sessionStore.delete(sessionId) ?: return
        val uri = Uri.parse(removed.contentUri)
        when (uri.scheme) {
            "content" -> runCatching { getApplication<Application>().contentResolver.delete(uri, null, null) }
            "file" -> uri.path?.let { runCatching { File(it).delete() } }
        }
        _sessions.value = sessionStore.loadAll()
    }

    fun session(id: String): Session? = _sessions.value.firstOrNull { it.id == id }

    private fun persistPresets(presets: List<String>) {
        presetStore.save(presets)
        _presets.value = presets
    }

    private fun persistSession(session: Session) {
        sessionStore.save(session)
        _sessions.value = sessionStore.loadAll()
    }

    private fun recordedPositionMs(): Long {
        if (!_record.value.isRecording) return 0L
        return (SystemClock.elapsedRealtime() - recordAnchorElapsed).coerceAtLeast(0L)
    }
}

fun formatClock(ms: Long): String {
    val total = ms.coerceAtLeast(0L) / 1000L
    val minutes = (total / 60L) % 60L
    val seconds = total % 60L
    val hours = total / 3600L
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%02d:%02d".format(minutes, seconds)
}

fun recordErrorMessage(error: Int): String = when (error) {
    VideoRecordEvent.Finalize.ERROR_ENCODING_FAILED -> "编码失败"
    VideoRecordEvent.Finalize.ERROR_FILE_SIZE_LIMIT_REACHED -> "文件太大"
    VideoRecordEvent.Finalize.ERROR_INSUFFICIENT_STORAGE -> "存储空间不足"
    VideoRecordEvent.Finalize.ERROR_INVALID_OUTPUT_OPTIONS -> "保存位置无效"
    VideoRecordEvent.Finalize.ERROR_NO_VALID_DATA -> "没有录到有效画面"
    VideoRecordEvent.Finalize.ERROR_RECORDER_ERROR -> "录制器出错"
    VideoRecordEvent.Finalize.ERROR_SOURCE_INACTIVE -> "相机中断了录像"
    VideoRecordEvent.Finalize.ERROR_DURATION_LIMIT_REACHED -> "达到时长上限"
    VideoRecordEvent.Finalize.ERROR_RECORDING_GARBAGE_COLLECTED -> "录像被系统回收"
    else -> "未知错误"
}
