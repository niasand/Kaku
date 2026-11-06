package com.zhiwei.shiyin.data

import android.content.SharedPreferences
import com.zhiwei.shiyin.core.DefaultPresets
import com.zhiwei.shiyin.core.PresetCodec
import com.zhiwei.shiyin.core.SubtitlePreset
import com.zhiwei.shiyin.core.SubtitleTimeline
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PresetRepository(private val prefs: SharedPreferences) {
    private val _presets = MutableStateFlow(read())
    val presets: StateFlow<List<SubtitlePreset>> = _presets.asStateFlow()

    fun add(text: String): String? {
        val cleaned = SubtitleTimeline.cleanText(text)
        val invalid = validate(cleaned, ignoreId = null) ?: run {
            persist(_presets.value + SubtitlePreset(UUID.randomUUID().toString(), cleaned))
            return null
        }
        return invalid
    }

    fun update(id: String, text: String): String? {
        val cleaned = SubtitleTimeline.cleanText(text)
        val invalid = validate(cleaned, ignoreId = id) ?: run {
            persist(_presets.value.map { if (it.id == id) it.copy(text = cleaned) else it })
            return null
        }
        return invalid
    }

    fun delete(id: String) {
        persist(_presets.value.filterNot { it.id == id })
    }

    fun move(id: String, delta: Int) {
        val list = _presets.value.toMutableList()
        val index = list.indexOfFirst { it.id == id }
        val target = index + delta
        if (index < 0 || target !in list.indices) return
        val item = list.removeAt(index)
        list.add(target, item)
        persist(list)
    }

    private fun validate(text: String, ignoreId: String?): String? {
        if (text.isEmpty()) return "文案不能为空"
        if (text.length > 60) return "文案最多 60 个字"
        if (_presets.value.any { it.id != ignoreId && it.text == text }) return "这条文案已经有了"
        return null
    }

    private fun read(): List<SubtitlePreset> {
        val raw = prefs.getString(KEY, null) ?: return defaults().also(::persist)
        return runCatching { PresetCodec.decode(raw) }.getOrElse { defaults() }
            .filter { it.text.isNotBlank() }
    }

    private fun defaults(): List<SubtitlePreset> =
        DefaultPresets.map { SubtitlePreset(UUID.randomUUID().toString(), it) }

    private fun persist(list: List<SubtitlePreset>) {
        _presets.value = list
        prefs.edit().putString(KEY, PresetCodec.encode(list)).apply()
    }

    private companion object {
        const val KEY = "presets"
    }
}
