package com.timestampcam.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class StoreTest {
    @Rule
    @JvmField
    val folder = TemporaryFolder()

    @Test
    fun presets_rejectBlankDuplicateAndOverlongText() {
        assertNull(Presets.add(listOf("检查完成"), "  "))
        assertNull(Presets.add(listOf("检查完成"), "检查完成"))
        assertNull(Presets.add(emptyList(), "字".repeat(41)))
        assertEquals(listOf("检查完成", "复检通过"), Presets.add(listOf("检查完成"), " 复检通过 ") )
        assertEquals(listOf("复检通过", "检查完成"), Presets.move(listOf("检查完成", "复检通过"), 0, 1))
    }

    @Test
    fun presetStore_roundTripsAndFallsBackWhenCorrupt() {
        val file = folder.newFile("presets.json")
        val store = PresetStore(file)
        store.save(listOf("开始作业", "发现异常"))
        assertEquals(listOf("开始作业", "发现异常"), PresetStore(file).load())
        store.save(emptyList())
        assertEquals(emptyList<String>(), PresetStore(file).load())
        file.writeText("{not json")
        assertEquals(Presets.DEFAULT, PresetStore(file).load())
    }

    @Test
    fun sessionStore_roundTripsCueWithQuotes() {
        val store = SessionStore(folder.newFolder("sessions"))
        val session = Session(
            id = "s1",
            displayName = "时间记录_20261106.mp4",
            contentUri = "content://media/1",
            createdAtEpochMs = 10L,
            durationMs = 4_000L,
            cues = listOf(Cue("c1", "他说\"通过\"", 0L, 3_000L, burned = false)),
        )
        store.save(session)
        store.save(session.copy(durationMs = 5_000L))
        assertEquals(5_000L, store.find("s1")?.durationMs)
        assertEquals("他说\"通过\"", store.find("s1")?.cues?.single()?.text)
        assertEquals(session.id, store.delete("s1")?.id)
        assertNull(store.find("s1"))
    }
}
