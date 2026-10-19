package com.denser.june.core.media.compress

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal fun replaceMediaFile(
    source: File,
    target: File,
    lastModified: Long = target.lastModified()
): Boolean {
    if (!source.isFile || source.length() == 0L) return false
    val expectedSize = source.length()
    return writeMediaFileAtomically(target, lastModified) { staged ->
        source.copyTo(staged, overwrite = true)
        if (staged.length() != expectedSize || source.length() != expectedSize) {
            throw IOException("Incomplete media copy")
        }
    }
}

/** A failed write leaves the attachment untouched, including when storage is full. */
internal fun writeMediaFileAtomically(target: File, lastModified: Long, write: (File) -> Unit): Boolean {
    var staged: File? = null
    return try {
        if (target.exists() && !target.isFile) return false
        // Stage beside the destination so the final rename stays on the same filesystem.
        staged = Files.createTempFile(target.absoluteFile.parentFile!!.toPath(), ".media-", ".tmp").toFile()
        write(staged)
        if (!staged.isFile || staged.length() == 0L) return false
        RandomAccessFile(staged, "rw").use { it.fd.sync() }
        if (lastModified > 0L && !staged.setLastModified(lastModified)) return false
        Files.move(staged.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        true
    } catch (_: Exception) {
        // Never fall back to truncating the attachment if atomic replacement is unavailable.
        false
    } finally {
        staged?.delete()
    }
}
