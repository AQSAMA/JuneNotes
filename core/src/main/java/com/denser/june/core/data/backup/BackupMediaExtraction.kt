package com.denser.june.core.data.backup

import com.denser.june.core.utils.computeSHA256
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.util.UUID

/** Stage each ZIP entry; existing attachments are only reused after byte verification. */
internal fun extractBackupMedia(input: InputStream, directory: File, preferredName: String): File {
    val name = File(preferredName).name
    require(name.isNotBlank() && name != "." && name != "..") { "Invalid media filename" }
    val staging = Files.createTempFile(directory.toPath(), ".june-import-", ".tmp").toFile()
    try {
        staging.outputStream().use { input.copyTo(it) }
        if (staging.length() == 0L) throw java.io.IOException("Empty media entry")
        val hash = staging.computeSHA256()
        var target = File(directory, name)
        while (true) {
            if (target.exists()) {
                if (target.isFile && target.length() == staging.length() && target.computeSHA256() == hash) return target
                val uniqueName = if (name.startsWith("media_")) "media_${UUID.randomUUID()}_${name.removePrefix("media_")}"
                    else "${UUID.randomUUID()}_$name"
                target = File(directory, uniqueName)
            }
            // Reserve a new destination atomically; only replace the empty file we own.
            if (!target.createNewFile()) continue
            try {
                Files.move(staging.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                return target
            } catch (error: Exception) {
                target.delete()
                throw error
            }
        }
    } finally {
        Files.deleteIfExists(staging.toPath())
    }
}
