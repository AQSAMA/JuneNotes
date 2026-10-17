package com.denser.june.editor

import com.denser.june.presentation.components.lightbox.persistMediaCompression
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MediaCompressionSaveTest {
    @Test fun disposalAfterWriteStartsStillCommitsSyncMetadata() = runTest {
        val finishWrite = CompletableDeferred<Unit>()
        var marked = false
        val save = launch {
            persistMediaCompression(compress = { finishWrite.await(); true }, markModified = { marked = true })
        }
        runCurrent()
        save.cancel()
        runCurrent()
        assertFalse(marked)
        finishWrite.complete(Unit)
        save.join()
        assertTrue(marked)
    }

    @Test fun failedCompressionDoesNotMarkMediaModified() = runTest {
        var marked = false
        assertFalse(persistMediaCompression(compress = { false }, markModified = { marked = true }))
        assertFalse(marked)
    }
}
