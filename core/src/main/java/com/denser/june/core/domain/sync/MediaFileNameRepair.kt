package com.denser.june.core.domain.sync

import com.denser.june.core.domain.repository.JournalRepository
import com.denser.june.core.utils.computeSHA256
import java.io.File
import java.io.IOException
import java.nio.file.Files

/** Keep the source until journal writes succeed; normal orphan cleanup can reclaim it later. */
internal fun repairRepeatedMediaPath(
    path: String,
    mediaDir: File,
    copy: (File, File) -> Unit = { source, target -> Files.copy(source.toPath(), target.toPath()); Unit }
): String {
    val source = File(path)
    if (!source.name.startsWith("media_") || !source.name.contains("_media_")) return path
    val target = File(source.parentFile ?: mediaDir, "media_" + source.name.substringAfterLast("_media_"))
    return try {
        // A missing source cannot prove that an existing destination is this attachment.
        if (!source.isFile || source.length() == 0L) return path
        if (!target.exists()) copy(source, target) // CREATE_NEW semantics: never overwrite a collision.
        val sourceHash = source.computeSHA256()
        if (sourceHash.isNotEmpty() && target.isFile && target.length() == source.length() && sourceHash == target.computeSHA256())
            target.absolutePath else path
    } catch (_: IOException) {
        path
    } catch (_: SecurityException) {
        path
    }
}

/** Shared attachments resolve consistently; failed database writes leave their sources intact. */
internal suspend fun repairRepeatedMediaPaths(repository: JournalRepository, mediaDir: File): Int {
    val repairedPaths = mutableMapOf<String, String>()
    var repairedCount = 0
    repository.getAllJournalsIncludeDeletedSync().forEach { journal ->
        val images = journal.images.map { path -> repairedPaths.getOrPut(path) { repairRepeatedMediaPath(path, mediaDir) } }
        if (images != journal.images) {
            if (repository.repairMediaPaths(journal.id, journal.images, images, System.currentTimeMillis())) repairedCount++
        }
    }
    return repairedCount
}
