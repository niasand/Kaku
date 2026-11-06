package com.timestampcam.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleTimelineTest {
    @Test
    fun recordingTaps_closeThePreviousCue() {
        val (first, active1) = SubtitleTimeline.tapWhileRecording(emptyList(), "开始作业", 0L, "a")
        val (second, active2) = SubtitleTimeline.tapWhileRecording(first, "检查完成", 5_000L, "b")
        val closed = SubtitleTimeline.closeOpen(second, 8_000L)

        assertEquals("开始作业", active1)
        assertEquals("检查完成", active2)
        assertEquals(listOf(0L to 5_000L, 5_000L to 8_000L), closed.map { it.startMs to it.endMs })
        assertTrue(closed.all { it.burned })
    }

    @Test
    fun tappingTheOpenCue_endsIt() {
        val (started, _) = SubtitleTimeline.tapWhileRecording(emptyList(), "发现异常", 1_000L, "a")
        val (ended, active) = SubtitleTimeline.tapWhileRecording(started, "发现异常", 4_000L, "b")
        assertNull(active)
        assertEquals(1, ended.size)
        assertEquals(4_000L, ended.single().endMs)
    }

    @Test
    fun zeroLengthCue_isDropped() {
        val (started, _) = SubtitleTimeline.tapWhileRecording(emptyList(), "开始作业", 0L, "a")
        assertTrue(SubtitleTimeline.closeOpen(started, 0L).isEmpty())
    }

    @Test
    fun playbackInsert_splitsOnlyUnburnedCues() {
        val burned = Cue("burned", "已写入", 0L, 10_000L, burned = true)
        val note = Cue("note", "补充", 1_000L, 9_000L, burned = false)
        val next = SubtitleTimeline.insertPlaybackCue(
            cues = listOf(burned, note),
            text = "复检通过",
            atMs = 4_000L,
            durationMs = 3_000L,
            id = "new",
        )
        val burnedAfter = next.single { it.id == "burned" }
        assertEquals(0L, burnedAfter.startMs)
        assertEquals(10_000L, burnedAfter.endMs)
        assertEquals(listOf(1_000L to 4_000L, 7_000L to 9_000L), next.filter { it.text == "补充" }.map { it.startMs to it.endMs })
        val inserted = next.single { it.id == "new" }
        assertEquals(false, inserted.burned)
        assertEquals(4_000L, inserted.startMs)
        assertEquals(7_000L, inserted.endMs)
    }

    @Test
    fun activeAt_usesHalfOpenRange() {
        val cues = listOf(Cue("a", "甲", 0L, 3_000L, true))
        assertEquals("甲", SubtitleTimeline.activeAt(cues, 0L).single().text)
        assertEquals("甲", SubtitleTimeline.activeAt(cues, 2_999L).single().text)
        assertTrue(SubtitleTimeline.activeAt(cues, 3_000L).isEmpty())
    }
}
