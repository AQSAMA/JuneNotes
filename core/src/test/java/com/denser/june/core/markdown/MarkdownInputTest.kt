package com.denser.june.core.markdown

import com.denser.june.core.domain.markdown.MarkdownInput
import com.denser.june.core.domain.markdown.MarkdownTooLargeException
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

class MarkdownInputTest {
    @Test fun exactLimitIsAcceptedWithoutTruncation() {
        val text = "a".repeat(MarkdownInput.MAX_BYTES)
        assertEquals(text, MarkdownInput.read(ByteArrayInputStream(text.toByteArray())))
    }

    @Test fun oversizedUnknownLengthStreamStopsAtLimitPlusOne() {
        var bytesRead = 0
        val stream = object : InputStream() {
            override fun read(): Int { bytesRead++; return 'a'.code }
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                buffer.fill('a'.code.toByte(), offset, offset + length)
                bytesRead += length
                return length
            }
        }
        assertThrows(MarkdownTooLargeException::class.java) { MarkdownInput.read(stream) }
        assertEquals(MarkdownInput.MAX_BYTES + 1, bytesRead)
    }

    @Test fun multibyteSharedTextIsLimitedByBytesRatherThanCharacters() {
        val text = "ع".repeat(MarkdownInput.MAX_BYTES / 2)
        assertEquals(text, MarkdownInput.validateText(text))
        assertThrows(MarkdownTooLargeException::class.java) { MarkdownInput.validateText(text + "ع") }
    }

    @Test fun utf8BomAndShortReadsPreserveUnicode() {
        val text = "# عنوان\nمرحبا 😀"
        val source = ByteArrayInputStream(("\uFEFF" + text).toByteArray())
        val stream = object : InputStream() {
            override fun read() = source.read()
            override fun read(buffer: ByteArray, offset: Int, length: Int) = source.read(buffer, offset, minOf(length, 3))
        }
        assertEquals(text, MarkdownInput.read(stream))
    }

    @Test fun largeNotesBypassRichParser() {
        assertTrue(MarkdownInput.supportsRichText("a".repeat(MarkdownInput.RICH_TEXT_MAX_CHARS)))
        assertFalse(MarkdownInput.supportsRichText("a".repeat(MarkdownInput.RICH_TEXT_MAX_CHARS + 1)))
    }
}
