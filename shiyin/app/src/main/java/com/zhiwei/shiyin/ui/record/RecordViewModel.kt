package com.zhiwei.shiyin.ui.record

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zhiwei.shiyin.core.SubtitleCue
import com.zhiwei.shiyin.core.SubtitleTimeline
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class RecordViewModel : ViewModel() {
    var phase by mutableStateOf(Phase.Idle)
        private set
    var elapsedMs by mutableLongStateOf(0L)
        private set
    var cues by mutableStateOf<List<SubtitleCue>>(emptyList())
        private set
    var audioEnabled by mutableStateOf(true)
    var torchEnabled by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    val cuesRef = AtomicReference<List<SubtitleCue>>(emptyList())

    private var startedAt = 0L
    private var ticker: Job? = null

    fun onStarted() {
        startedAt = SystemClock.elapsedRealtime()
        elapsedMs = 0L
        updateCues(emptyList())
        phase = Phase.Recording
        error = null
        ticker?.cancel()
        ticker = viewModelScope.launch {
            while (isActive && phase == Phase.Recording) {
                elapsedMs = SystemClock.elapsedRealtime() - startedAt
                delay(100)
            }
        }
    }

    fun markSaving() {
        if (phase == Phase.Recording) phase = Phase.Saving
    }

    fun addPreset(text: String) {
        if (phase != Phase.Recording) return
        val at = SystemClock.elapsedRealtime() - startedAt
        updateCues(SubtitleTimeline.addCue(cues, at, text))
    }

    fun clearSubtitle() {
        if (phase != Phase.Recording) return
        val at = SystemClock.elapsedRealtime() - startedAt
        updateCues(SubtitleTimeline.clearAt(cues, at))
    }

    fun onFinished() {
        ticker?.cancel()
        phase = Phase.Idle
    }

    private fun updateCues(next: List<SubtitleCue>) {
        cues = next
        cuesRef.set(next)
    }

    enum class Phase { Idle, Recording, Saving }
}
