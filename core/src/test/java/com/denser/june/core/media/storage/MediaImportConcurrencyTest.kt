package com.denser.june.core.media.storage

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MediaImportConcurrencyTest {
    @get:Rule val files = TemporaryFolder()

    @Test fun parallelImportsReserveDifferentDestinationsAndKeepEveryPayload() {
        val pool = Executors.newFixedThreadPool(8)
        try {
            val results = pool.invokeAll((0 until 128).map { id -> Callable {
                val bytes = ByteArray(2048) { id.toByte() }
                copyMediaImport(files.root, "jpg", ByteArrayInputStream(bytes)) to bytes
            } }).map { it.get() }
            assertEquals(results.size, results.map { it.first.path }.toSet().size)
            results.forEach { (file, expected) -> assertArrayEquals(expected, file.readBytes()) }
        } finally { pool.shutdownNow() }
    }

    @Test fun failedImportRemovesItsPartialReservedFile() {
        val broken = object : InputStream() {
            private var reads = 0
            override fun read(): Int {
                if (++reads > 10) throw IOException("Read failed")
                return 1
            }
        }
        try { copyMediaImport(files.root, "mp4", broken); fail("Import should fail") }
        catch (_: IOException) { }
        assertEquals(0, files.root.listFiles()!!.size)
    }
}
