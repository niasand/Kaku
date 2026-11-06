package com.zhiwei.shiyin.core

import org.junit.Assert.assertEquals
import org.junit.Test

class RecordingMetaCodecTest {
    @Test
    fun metaRoundTripsChineseCues() {
        val meta = RecordingMeta(
            id = "20261106_224105",
            createdAtEpochMs = 1_762_441_265_000,
            durationMs = 12_000,
            hasAudio = true,
            cues = listOf(SubtitleCue(1_200, "检查正常")),
        )
        val decoded = RecordingMetaCodec.decode(RecordingMetaCodec.encode(meta))
        assertEquals(meta, decoded)
    }

    @Test
    fun presetRoundTrip() {
        val presets = listOf(SubtitlePreset("a", "发现异常"))
        assertEquals(presets, PresetCodec.decode(PresetCodec.encode(presets)))
    }
}
