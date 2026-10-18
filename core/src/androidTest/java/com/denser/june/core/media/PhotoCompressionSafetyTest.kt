package com.denser.june.core.media

import android.graphics.Bitmap
import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.denser.june.core.media.compress.CompressUtils
import com.denser.june.core.media.compress.PhotoCompressor
import com.denser.june.core.media.compress.model.CompressionPreset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PhotoCompressionSafetyTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun transparentPngAndWebpKeepTheirOriginalEncodingAndAlpha() = runBlocking {
        val bitmap = Bitmap.createBitmap(2300, 2, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(0x4000FF00)
        try {
            for (format in listOf(Bitmap.CompressFormat.PNG, Bitmap.CompressFormat.WEBP_LOSSLESS)) {
                // A misleading JPEG extension must not bypass content-based format detection.
                val file = File(context.cacheDir, "safety-${format.name}.jpg")
                file.outputStream().use { assertTrue(bitmap.compress(format, 100, it)) }
                assertPreserved(file)
            }
        } finally { bitmap.recycle() }
    }

    @Test fun animatedGifIsExcludedFromAutomaticAndManualCompression() = runBlocking {
        val file = File(context.cacheDir, "safety-animation.jpg")
        file.writeBytes(Base64.decode("R0lGODlh/AgCAIEAAP8AAAAAAAAAAAAAACH/C05FVFNDQVBFMi4wAwEAAAAh+QQACgAAACwAAAAA/AgCAAAIbwABCBxIsKDBgwgTKlzIsKHDhxAjSpxIsaLFixgzatzIsaPHjyBDihxJsqTJkyhTqlzJsqXLlzBjypxJs6bNmzhz6tzJs6fPn0CDCh1KtKjRo0iTKl3KtKnTp1CjSp1KtarVq1izat3KtavXrygDAgAh+QQBCgABACwAAAAA/AgCAIEAAP8AAAAAAAAAAAAIbwABCBxIsKDBgwgTKlzIsKHDhxAjSpxIsaLFixgzatzIsaPHjyBDihxJsqTJkyhTqlzJsqXLlzBjypxJs6bNmzhz6tzJs6fPn0CDCh1KtKjRo0iTKl3KtKnTp1CjSp1KtarVq1izat3KtavXrygDAgA7", Base64.DEFAULT))
        assertPreserved(file)
    }

    @Test fun jpegStillOffersCompression() {
        val bitmap = Bitmap.createBitmap(2300, 2, Bitmap.Config.ARGB_8888)
        val file = File(context.cacheDir, "safety-photo.jpg")
        try {
            file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)) }
            assertTrue(PhotoCompressor.shouldCompressPhoto(file))
            assertTrue(CompressUtils.getAvailablePresets(file).contains(CompressionPreset.HIGH))
        } finally { bitmap.recycle(); file.delete() }
    }

    @Test fun cachedPreviewCannotReplaceMediaWhenOriginalBackupFails() = runBlocking {
        val bitmap = Bitmap.createBitmap(2300, 2300, Bitmap.Config.ARGB_8888)
        val file = File(context.cacheDir, "safety-backup-${UUID.randomUUID()}.jpg")
        val backup = File(context.cacheDir, "uncompressed_${file.name}")
        try {
            bitmap.eraseColor(0xFF55AA33.toInt())
            file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)) }
            val original = file.readBytes()
            val preview = PhotoCompressor.generatePhotoPreview(context, file.path, CompressionPreset.LOW)
            assertNotEquals(file.path, preview.previewPath)
            // An unusable destination makes saving the only original fail.
            assertTrue(backup.createNewFile())
            assertFalse(PhotoCompressor.compressPhoto(context, file.path, CompressionPreset.LOW))
            assertArrayEquals(original, file.readBytes())
            File(preview.previewPath).delete()
        } finally { bitmap.recycle(); file.delete(); backup.delete() }
    }

    @Test fun failedRestoreKeepsTheOriginalAvailableForRetry() {
        val file = File(context.cacheDir, "safety-restore-${UUID.randomUUID()}.jpg")
        val original = byteArrayOf(1, 2, 3)
        val backup = File(context.cacheDir, "uncompressed_${file.name}")
        val obstruction = File(file, "obstruction")
        try {
            file.writeBytes(original)
            assertNotNull(CompressUtils.saveUncompressedOriginalFile(context, file.path))
            assertTrue(file.delete())
            assertTrue(file.mkdir())
            obstruction.writeText("Prevent replacement")
            assertFalse(CompressUtils.restoreUncompressedOriginalFile(context, file.path))
            assertArrayEquals(original, backup.readBytes())
        } finally { obstruction.delete(); file.delete(); backup.delete() }
    }

    private suspend fun assertPreserved(file: File) {
        val bytes = file.readBytes()
        try {
            assertFalse(PhotoCompressor.shouldCompressPhoto(file))
            assertEquals(setOf(CompressionPreset.ORIGINAL), CompressUtils.getAvailablePresets(file))
            val preview = PhotoCompressor.generatePhotoPreview(context, file.absolutePath, CompressionPreset.LOW)
            assertTrue(preview.isAlreadyOptimal)
            assertEquals(file.absolutePath, preview.previewPath)
            assertFalse(PhotoCompressor.compressPhoto(context, file.absolutePath, CompressionPreset.LOW))
            assertArrayEquals(bytes, file.readBytes())
            assertNull(CompressUtils.getUncompressedOriginalFile(context, file.absolutePath))
        } finally { file.delete() }
    }
}
