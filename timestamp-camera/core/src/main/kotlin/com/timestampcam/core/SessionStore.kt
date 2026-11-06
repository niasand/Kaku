package com.timestampcam.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class Session(
    val id: String,
    val displayName: String,
    val contentUri: String,
    val createdAtEpochMs: Long,
    val durationMs: Long,
    val cues: List<Cue>,
)

class SessionStore(private val directory: File) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    fun loadAll(): List<Session> {
        if (!directory.exists()) return emptyList()
        return directory.listFiles { file -> file.extension == "json" }
            .orEmpty()
            .mapNotNull { file ->
                try {
                    json.decodeFromString(Session.serializer(), file.readText())
                } catch (_: Exception) {
                    null
                }
            }
            .sortedByDescending { it.createdAtEpochMs }
    }

    fun find(id: String): Session? = loadAll().firstOrNull { it.id == id }

    fun save(session: Session) {
        directory.mkdirs()
        val target = File(directory, "${session.id}.json")
        val tmp = File(directory, "${session.id}.json.tmp")
        tmp.writeText(json.encodeToString(Session.serializer(), session))
        if (target.exists() && !target.delete()) {
            error("Could not replace ${target.name}")
        }
        if (!tmp.renameTo(target)) {
            error("Could not write ${target.name}")
        }
    }

    fun delete(id: String): Session? {
        val existing = find(id) ?: return null
        File(directory, "$id.json").delete()
        return existing
    }
}
