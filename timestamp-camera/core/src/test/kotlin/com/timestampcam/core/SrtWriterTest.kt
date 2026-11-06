package com.timestampcam.core

import org.junit.Assert.assertEquals
import org.junit.Test

class SrtWriterTest {
    @Test
    fun formatsSrtTimestamp() {
        assertEquals("00:00:00,000", SrtWriter.formatTimestamp(0))
        assertEquals("00:00:01,500", SrtWriter.formatTimestamp(1_500))
        assertEquals("01:01:01,001", SrtWriter.formatTimestamp(3_661_001))
    }

    @Test
    fun writesCuesAndClosesOpenOnes() {
        val cues = listOf(
            Cue("b", "检查完成", 5_200L, 8_000L, true),
            Cue("a", "开始作业", 0L, 5_200L, true),
            Cue("c", "仍在继续", 8_000L, -1L, true),
        )
        val blocks = SrtWriter.write(cues, fallbackEndMs = 12_000L).trim().split("\n\n")
        assertEquals(
            listOf(
                "1\n00:00:00,000 --> 00:00:05,200\n开始作业",
                "2\n00:00:05,200 --> 00:00:08,000\n检查完成",
                "3\n00:00:08,000 --> 00:00:12,000\n仍在继续",
            ),
            blocks,
        )
    }
}
