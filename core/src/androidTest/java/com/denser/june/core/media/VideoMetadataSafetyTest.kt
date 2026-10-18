package com.denser.june.core.media

import android.content.ContentValues
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.denser.june.core.media.exif.ExifUtils
import com.denser.june.core.media.exif.model.MediaExifInfo
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class VideoMetadataSafetyTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val fallbackDate = 1_700_000_000_000L
    private val corruptBytes = byteArrayOf(1, 2, 3, 4)

    @Test fun corruptLocalVideoReturnsFallbackMetadata() {
        val file = File(context.cacheDir, "corrupt-${UUID.randomUUID()}.mp4")
        try {
            file.writeBytes(corruptBytes)
            val info = ExifUtils.readMediaExif(context, file.path, fallbackDate, "Journal", 2, true)
            assertFallback(info)
            assertEquals(file.name, info.fileName)
            assertEquals(file.path, info.filePath)
            assertArrayEquals(corruptBytes, file.readBytes())
        } finally { file.delete() }
    }

    @Test @SdkSuppress(minSdkVersion = 29)
    fun corruptContentVideoReturnsFallbackMetadata() {
        val resolver = context.contentResolver
        val name = "corrupt-${UUID.randomUUID()}.mp4"
        val uri = checkNotNull(resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }))
        try {
            checkNotNull(resolver.openOutputStream(uri)).use { it.write(corruptBytes) }
            val info = ExifUtils.readMediaExif(context, uri.toString(), fallbackDate, "Journal", 2, true)
            assertFallback(info)
            assertEquals(name, info.fileName)
            assertEquals(uri.toString(), info.filePath)
        } finally { resolver.delete(uri, null, null) }
    }

    private fun assertFallback(info: MediaExifInfo) {
        assertTrue(info.isVideo)
        assertFalse(info.isHdr)
        assertTrue(info.isBackedUp)
        assertEquals(fallbackDate, info.dateTakenMillis ?: 0L)
        assertEquals("Journal", info.journalTitle)
        assertEquals(2, info.journalMediaCount)
        assertEquals("Video Details", info.deviceName)
        assertEquals(corruptBytes.size.toLong(), info.fileSizeBytes)
        assertEquals(0, info.width)
        assertEquals(0, info.height)
        assertNull(info.duration)
    }
}
