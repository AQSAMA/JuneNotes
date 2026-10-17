package com.denser.june.core.backup

import com.denser.june.core.data.backup.extractBackupMedia
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.InputStream
import java.io.IOException
import java.util.concurrent.Executors

class BackupMediaExtractionTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun unusedNamesArePreservedAndIdenticalEntriesAreReused() {
        val bytes = byteArrayOf(1, 2, 3)
        val first = extractBackupMedia(bytes.inputStream(), temp.root, "media_one.jpg")
        val again = extractBackupMedia(bytes.inputStream(), temp.root, "media_one.jpg")
        assertEquals(File(temp.root, "media_one.jpg"), first)
        assertEquals(first, again)
        assertArrayEquals(bytes, first.readBytes())
        assertEquals(1, temp.root.listFiles()!!.size)
    }

    @Test fun sameSizeDifferentBytesNeverReplaceExistingMedia() {
        val existing = File(temp.root, "media_one.jpg").apply { writeText("OLD") }
        val imported = extractBackupMedia("NEW".byteInputStream(), temp.root, existing.name)
        assertNotEquals(existing, imported)
        assertEquals("OLD", existing.readText())
        assertEquals("NEW", imported.readText())
        assertTrue(imported.name.startsWith("media_"))
    }

    @Test fun failedStreamLeavesExistingMediaAndNoPartialDestination() {
        val existing = File(temp.root, "media_one.jpg").apply { writeText("OLD") }
        val broken = object : InputStream() { override fun read(): Int = throw IOException("Read failed") }
        try { extractBackupMedia(broken, temp.root, existing.name); fail("Expected read failure") }
        catch (_: IOException) { }
        assertEquals("OLD", existing.readText())
        assertEquals(listOf(existing), temp.root.listFiles()!!.toList())
    }

    @Test fun concurrentImportsCannotClaimAndOverwriteTheSameName() {
        val executor = Executors.newFixedThreadPool(2)
        try {
            val first = executor.submit<File> { extractBackupMedia("FIRST".byteInputStream(), temp.root, "media_shared.jpg") }
            val second = executor.submit<File> { extractBackupMedia("OTHER".byteInputStream(), temp.root, "media_shared.jpg") }
            val files = listOf(first.get(), second.get())
            assertEquals(2, files.toSet().size)
            assertEquals(setOf("FIRST", "OTHER"), files.map { it.readText() }.toSet())
        } finally { executor.shutdownNow() }
    }

    @Test fun emptyEntriesCannotBeMistakenForAnInFlightReservation() {
        try { extractBackupMedia(byteArrayOf().inputStream(), temp.root, "empty.jpg"); fail("Expected empty media rejection") }
        catch (_: IOException) { }
        assertTrue(temp.root.listFiles()!!.isEmpty())
    }
}
