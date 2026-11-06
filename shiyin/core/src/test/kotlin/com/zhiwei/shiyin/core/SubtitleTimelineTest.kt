package com.zhiwei.shiyin.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubtitleTimelineTest {
    @Test
    fun subtitleShowsUntilTheNextCueAndBlankClearsIt() {
        val cues = listOf(
            SubtitleCue(0, "开始"),
            SubtitleCue(1_500, "发现异常"),
            SubtitleCue(3_000, ""),
        )
        assertEquals("开始", SubtitleTimeline.subtitleAt(cues, 0))
        assertEquals("开始", SubtitleTimeline.subtitleAt(cues, 1_499))
        assertEquals("发现异常", SubtitleTimeline.subtitleAt(cues, 1_500))
        assertNull(SubtitleTimeline.subtitleAt(cues, 3_000))
        assertNull(SubtitleTimeline.subtitleAt(cues, -10))
    }

    @Test
    fun sameMomentKeepsTheLaterCue() {
        val cues = SubtitleTimeline.addCue(emptyList(), 1_000, "开始", replaceWindowMs = 0)
            .let { SubtitleTimeline.addCue(it, 1_000, "", replaceWindowMs = 0) }
        assertNull(SubtitleTimeline.subtitleAt(cues, 1_000))
    }

    @Test
    fun nearbyTapReplacesInsteadOfStacking() {
        val once = SubtitleTimeline.addCue(emptyList(), 2_000, "开始")
        val twice = SubtitleTimeline.addCue(once, 2_100, "结束")
        assertEquals(listOf(SubtitleCue(2_000, "结束")), twice)
    }

    @Test
    fun srtEndsEachCueAtTheNextOneAndSkipsBlanks() {
        val cues = listOf(
            SubtitleCue(0, "开始"),
            SubtitleCue(1_500, "发现异常\n请复查"),
            SubtitleCue(3_000, " "),
        )
        val srt = SubtitleTimeline.toSrt(cues, 5_000)
        assertEquals(
            """
            1
            00:00:00,000 --> 00:00:01,500
            开始

            2
            00:00:01,500 --> 00:00:03,000
            发现异常 请复查
            """.trimIndent() + "\n",
            srt,
        )
    }

    @Test
    fun lastCueRunsUntilTheRecordingEnds() {
        val srt = SubtitleTimeline.toSrt(listOf(SubtitleCue(500, "结束")), 4_000)
        assertEquals(
            """
            1
            00:00:00,500 --> 00:00:04,000
            结束
            """.trimIndent() + "\n",
            srt,
        )
    }

    @Test
    fun clearDoesNothingWhenNothingIsShowing() {
        val cues = listOf(SubtitleCue(0, "开始"), SubtitleCue(1_000, ""))
        assertEquals(cues, SubtitleTimeline.clearAt(cues, 1_500))
    }
}
