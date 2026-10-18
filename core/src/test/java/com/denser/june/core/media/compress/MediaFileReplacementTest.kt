package com.denser.june.core.media.compress

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class MediaFileReplacementTest {
    @get:Rule val files = TemporaryFolder()

    @Test fun failedPartialWriteLeavesAttachmentAndTimestampUntouched() {
        val target = files.newFile("attachment.jpg").apply { writeText("original attachment") }
        val timestamp = 1_700_000_000_000L
        assertTrue(target.setLastModified(timestamp))

        assertFalse(writeMediaFileAtomically(target, timestamp) { staged ->
            staged.writeText("partial")
            throw IOException("No space left on device")
        })

        assertEquals("original attachment", target.readText())
        assertEquals(timestamp, target.lastModified())
        assertEquals(listOf(target.name), files.root.list()!!.toList())
    }

    @Test fun replacementKeepsSourceAndPreservesAttachmentTimestamp() {
        val source = files.newFile("preview.jpg").apply { writeText("compressed attachment") }
        val target = files.newFile("attachment.jpg").apply { writeText("original attachment") }
        val timestamp = 1_700_000_000_000L
        assertTrue(target.setLastModified(timestamp))

        assertTrue(replaceMediaFile(source, target))

        assertEquals("compressed attachment", target.readText())
        assertEquals("compressed attachment", source.readText())
        assertEquals(timestamp, target.lastModified())
        assertEquals(setOf(source.name, target.name), files.root.list()!!.toSet())
    }

    @Test fun emptyReplacementCannotEraseAttachment() {
        val source = files.newFile("preview.jpg")
        val target = files.newFile("attachment.jpg").apply { writeText("original attachment") }
        assertFalse(replaceMediaFile(source, target))
        assertEquals("original attachment", target.readText())
    }

    @Test fun invalidDestinationKeepsSourceAndDirectoryContents() {
        val source = files.newFile("backup.jpg").apply { writeText("original attachment") }
        val target = files.newFolder("attachment.jpg")
        val child = File(target, "obstruction").apply { writeText("keep") }
        assertFalse(replaceMediaFile(source, target))
        assertEquals("original attachment", source.readText())
        assertEquals("keep", child.readText())
    }
}
