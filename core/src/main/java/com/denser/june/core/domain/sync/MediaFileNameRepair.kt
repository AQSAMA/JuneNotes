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
    copy: (File, File) -> Unit = { source, target -> Files.copy(source.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING); Unit }
): String {
    val source = File(path)
    if (!source.name.startsWith("media_") || !source.name.contains("_media_")) return path
    val parent = source.parentFile ?: mediaDir
    val target = File(parent, "media_" + source.name.substringAfterLast("_media_"))
    // Missing files and permanent different-content collisions cannot be safely repaired.
    if (!source.isFile || source.length() == 0L) return path
    val sourceHash = source.computeSHA256()
    if (sourceHash.isEmpty()) throw IOException("Cannot verify media source")
    fun matches(file: File): Boolean = file.isFile && file.length() == source.length() && file.computeSHA256() == sourceHash
    if (target.exists()) return if (matches(target)) target.absolutePath else path

    // Never leave a partial cleaned-name file that would look like a permanent collision.
    val staging = Files.createTempFile(parent.toPath(), ".june-repair-", ".tmp").toFile()
    try {
        copy(source, staging)
        if (!matches(staging)) throw IOException("Media copy verification failed")
        try {
            Files.move(staging.toPath(), target.toPath())
        } catch (_: java.nio.file.FileAlreadyExistsException) {
            return if (matches(target)) target.absolutePath else path
        }
        return target.absolutePath
    } finally {
        Files.deleteIfExists(staging.toPath())
    }
}

/** Shared attachments resolve consistently; failed database writes leave their sources intact. */
internal suspend fun repairRepeatedMediaPaths(
    repository: JournalRepository,
    mediaDir: File,
    repairPath: (String, File) -> String = { path, directory -> repairRepeatedMediaPath(path, directory) }
): Int {
    val repairedPaths = mutableMapOf<String, String>()
    var repairedCount = 0
    repository.getAllJournalsIncludeDeletedSync().forEach { journal ->
        val images = journal.images.map { path -> repairedPaths.getOrPut(path) { repairPath(path, mediaDir) } }
        if (images != journal.images) {
            if (repository.repairMediaPaths(journal.id, journal.images, images, System.currentTimeMillis())) {
                repairedCount++
            } else {
                val stillPending = journal.images.zip(images).filter { (before, after) -> before != after }.map { it.first }.toSet()
                if (repository.getJournalById(journal.id)?.images?.any { it in stillPending } == true) {
                    throw IOException("Attachment list changed during repair; retry against current data")
                }
            }
        }
    }
    return repairedCount
}
