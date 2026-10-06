package com.denser.june.core.domain.folder

import kotlinx.serialization.json.Json

object FolderBackupCodec {
    const val ENTRY_NAME = "folders.json"
    const val MAX_BYTES = 8 * 1024 * 1024
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    fun encode(snapshot: FolderSnapshot): String = json.encodeToString(FolderSnapshot.serializer(), snapshot)
    fun read(stream: java.io.InputStream): FolderSnapshot {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val size = stream.read(buffer)
            if (size < 0) break
            require(output.size() + size <= MAX_BYTES) { "Folder backup is too large" }
            output.write(buffer, 0, size)
        }
        return decode(output.toString("UTF-8"))
    }
    fun decode(value: String): FolderSnapshot {
        require(value.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Folder backup is too large" }
        val snapshot = json.decodeFromString(FolderSnapshot.serializer(), value)
        require(snapshot.version == 1) { "Unsupported folder backup version" }
        require(snapshot.folders.map { it.id }.distinct().size == snapshot.folders.size)
        require(snapshot.journals.map { it.journalId }.distinct().size == snapshot.journals.size)
        require(snapshot.folders.all { it.id.isNotBlank() && it.name.isNotBlank() && it.updatedAt >= 0 })
        require(snapshot.journals.all { it.journalId.isNotBlank() && it.updatedAt >= 0 })
        return snapshot.normalized()
    }
}
