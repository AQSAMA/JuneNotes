package com.denser.june.core.domain.markdown

import java.io.ByteArrayOutputStream
import java.io.InputStream

class MarkdownTooLargeException : Exception("Markdown files must be 1 MiB or smaller")

/** Limits external input before allocating a complete string or a SQLite row. */
object MarkdownInput {
    const val MAX_BYTES = 1024 * 1024
    const val RICH_TEXT_MAX_CHARS = 32 * 1024

    fun read(stream: InputStream): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            // Read at most one byte beyond the limit, including for providers without size metadata.
            val count = stream.read(buffer, 0, minOf(buffer.size, MAX_BYTES - total + 1))
            if (count < 0) break
            total += count
            if (total > MAX_BYTES) throw MarkdownTooLargeException()
            output.write(buffer, 0, count)
        }
        return output.toString(Charsets.UTF_8.name()).removePrefix("\uFEFF")
    }

    fun validateText(text: String): String {
        // Avoid allocating a second unbounded copy of a shared EXTRA_TEXT value.
        if (text.length > MAX_BYTES || text.toByteArray(Charsets.UTF_8).size > MAX_BYTES) {
            throw MarkdownTooLargeException()
        }
        return text.removePrefix("\uFEFF")
    }

    fun supportsRichText(text: String): Boolean = text.length <= RICH_TEXT_MAX_CHARS
}
