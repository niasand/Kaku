package com.zhiwei.shiyin.data

import com.zhiwei.shiyin.core.RecordingMeta
import com.zhiwei.shiyin.core.RecordingMetaCodec
import com.zhiwei.shiyin.core.SubtitleTimeline
import com.zhiwei.shiyin.core.recordingId
import java.io.File
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class RecordingRepository(private val dir: File) {
    private val _items = MutableStateFlow<List<RecordingMeta>>(emptyList())
    val items: StateFlow<List<RecordingMeta>> = _items.asStateFlow()

    init {
        dir.mkdirs()
        refresh()
    }

    fun refresh() {
        val files = dir.listFiles { file -> file.extension.equals("json", ignoreCase = true) }.orEmpty()
        _items.value = files.mapNotNull { file ->
            runCatching { RecordingMetaCodec.decode(file.readText()) }.getOrNull()
        }.filter { videoFile(it.id).isFile }
            .sortedByDescending { it.createdAtEpochMs }
    }

    fun newVideoFile(): File {
        val base = recordingId(System.currentTimeMillis(), ZoneId.systemDefault())
        var id = base
        var suffix = 2
        while (videoFile(id).exists() || jsonFile(id).exists()) {
            id = "${base}_$suffix"
            suffix += 1
        }
        return videoFile(id)
    }

    fun videoFile(id: String): File = File(dir, "$id.mp4")

    fun srtFile(id: String): File = File(dir, "$id.srt")

    fun load(id: String): RecordingMeta? {
        val file = jsonFile(id)
        if (!file.isFile) return null
        return runCatching { RecordingMetaCodec.decode(file.readText()) }.getOrNull()
    }

    fun save(meta: RecordingMeta) {
        jsonFile(meta.id).writeText(RecordingMetaCodec.encode(meta))
        srtFile(meta.id).writeText(SubtitleTimeline.toSrt(meta.cues, meta.durationMs))
        refresh()
    }

    fun delete(id: String) {
        videoFile(id).delete()
        srtFile(id).delete()
        jsonFile(id).delete()
        refresh()
    }

    private fun jsonFile(id: String): File = File(dir, "$id.json")
}
