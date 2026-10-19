package com.denser.june.media

import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.denser.june.core.media.compress.model.CompressionPreset
import com.denser.june.presentation.components.lightbox.LightboxCompressionState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class CompressionPreviewFailureTest {
    @Test fun previewWriteFailureIsRecoverableAndRetryProducesACompleteImage() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = File(context.cacheDir, "preview-failure-${UUID.randomUUID()}.jpg")
        val obstruction = File(context.cacheDir, "cache-obstruction-${UUID.randomUUID()}").apply { writeText("not a directory") }
        val bitmap = Bitmap.createBitmap(2300, 2300, Bitmap.Config.ARGB_8888)
        var failCache = true
        val wrapper = object : ContextWrapper(context) {
            override fun getCacheDir(): File = if (failCache) obstruction else context.cacheDir
        }
        val state = LightboxCompressionState(source.path, wrapper, 68, 720) { false }
        try {
            source.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)) }
            state.selectPreset(CompressionPreset.LOW)
            state.generatePreview(CompressionPreset.LOW)
            assertTrue(state.previewFailed)
            assertFalse(state.isGeneratingPreview)
            assertNull(state.currentPreview)

            failCache = false
            state.retryPreview()
            state.generatePreview(CompressionPreset.LOW)
            assertFalse(state.previewFailed)
            assertFalse(state.isGeneratingPreview)
            val preview = state.currentPreview!!
            assertNotEquals(source.path, preview.previewPath)
            val decoded = BitmapFactory.decodeFile(preview.previewPath)
            assertNotNull(decoded)
            decoded?.recycle()
        } finally { state.cleanup(); bitmap.recycle(); source.delete(); obstruction.delete() }
    }
}
