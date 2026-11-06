package com.timestampcam.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

object Presets {
    const val MAX_LENGTH = 40
    val DEFAULT = listOf("开始作业", "检查完成", "发现异常", "复检通过", "现场备注")

    fun normalize(raw: String): String? {
        val text = raw.trim().replace(Regex("\\s+"), " ")
        if (text.isEmpty() || text.length > MAX_LENGTH) return null
        return text
    }

    fun add(current: List<String>, raw: String): List<String>? {
        val text = normalize(raw) ?: return null
        if (current.any { it == text }) return null
        return current + text
    }

    fun update(current: List<String>, index: Int, raw: String): List<String>? {
        if (index !in current.indices) return null
        val text = normalize(raw) ?: return null
        if (current.withIndex().any { it.index != index && it.value == text }) return null
        return current.toMutableList().also { it[index] = text }
    }

    fun removeAt(current: List<String>, index: Int): List<String> {
        if (index !in current.indices) return current
        return current.filterIndexed { i, _ -> i != index }
    }

    fun move(current: List<String>, from: Int, to: Int): List<String> {
        if (from !in current.indices || to !in current.indices || from == to) return current
        val next = current.toMutableList()
        val item = next.removeAt(from)
        next.add(to, item)
        return next
    }
}

class PresetStore(private val file: File) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    fun load(): List<String> {
        if (!file.exists()) return Presets.DEFAULT
        return try {
            val parsed = json.decodeFromString(PresetFile.serializer(), file.readText())
            parsed.presets.mapNotNull { Presets.normalize(it) }.distinct()
        } catch (_: Exception) {
            Presets.DEFAULT
        }
    }

    fun save(presets: List<String>) {
        file.parentFile?.mkdirs()
        val payload = PresetFile(presets.mapNotNull { Presets.normalize(it) }.distinct())
        file.writeText(json.encodeToString(PresetFile.serializer(), payload))
    }

    @Serializable
    private data class PresetFile(val presets: List<String> = emptyList())
}
