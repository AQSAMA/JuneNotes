package com.denser.june.core.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Base64
import androidx.exifinterface.media.ExifInterface
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

    @Test fun cachedPreviewCannotReplaceMediaWhenOriginalBackupFails() = runBlocking<Unit> {
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

    @Test fun compressionPreservesBothDiagonalExifOrientations() = runBlocking<Unit> {
        val bitmap = Bitmap.createBitmap(2400, 1200, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint()
        val sourceColors = listOf(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW)
        sourceColors.forEachIndexed { index, color ->
            paint.color = color
            val left = (index % 2) * 1200f
            val top = (index / 2) * 600f
            canvas.drawRect(left, top, left + 1200f, top + 600f, paint)
        }
        try {
            for (orientation in listOf(ExifInterface.ORIENTATION_TRANSPOSE, ExifInterface.ORIENTATION_TRANSVERSE)) {
                val file = File(context.cacheDir, "orientation-${UUID.randomUUID()}.jpg")
                try {
                    file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)) }
                    ExifInterface(file.path).apply {
                        setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
                        saveAttributes()
                    }
                    val original = file.readBytes()
                    assertTrue(PhotoCompressor.compressPhoto(context, file.path, CompressionPreset.LOW))
                    val compressed = BitmapFactory.decodeFile(file.path)!!
                    try {
                        assertTrue(compressed.width < compressed.height)
                        val expected = if (orientation == ExifInterface.ORIENTATION_TRANSPOSE) {
                            listOf(Color.RED, Color.BLUE, Color.GREEN, Color.YELLOW)
                        } else listOf(Color.YELLOW, Color.GREEN, Color.BLUE, Color.RED)
                        expected.forEachIndexed { index, color ->
                            val pixel = compressed.getPixel(compressed.width * (if (index % 2 == 0) 1 else 3) / 4,
                                compressed.height * (if (index / 2 == 0) 1 else 3) / 4)
                            assertTrue("Wrong red channel for orientation $orientation", kotlin.math.abs(Color.red(pixel) - Color.red(color)) < 40)
                            assertTrue("Wrong green channel for orientation $orientation", kotlin.math.abs(Color.green(pixel) - Color.green(color)) < 40)
                            assertTrue("Wrong blue channel for orientation $orientation", kotlin.math.abs(Color.blue(pixel) - Color.blue(color)) < 40)
                        }
                    } finally { compressed.recycle() }
                    assertEquals(ExifInterface.ORIENTATION_NORMAL, ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, 0))
                    assertTrue(CompressUtils.restoreUncompressedOriginalFile(context, file.path))
                    assertArrayEquals(original, file.readBytes())
                } finally { CompressUtils.cleanUncompressedOriginalFile(context, file.path); file.delete() }
            }
        } finally { bitmap.recycle() }
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
