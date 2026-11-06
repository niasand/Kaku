package com.zhiwei.shiyin.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class RecordingMeta(
    val id: String,
    val createdAtEpochMs: Long,
    val durationMs: Long,
    val hasAudio: Boolean,
    val cues: List<SubtitleCue> = emptyList(),
)

object RecordingMetaCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    fun encode(meta: RecordingMeta): String = json.encodeToString(meta)

    fun decode(raw: String): RecordingMeta = json.decodeFromString(raw)
}

object PresetCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    fun encode(presets: List<SubtitlePreset>): String = json.encodeToString(presets)

    fun decode(raw: String): List<SubtitlePreset> = json.decodeFromString(raw)
}
