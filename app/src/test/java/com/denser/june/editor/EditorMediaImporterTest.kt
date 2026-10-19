package com.denser.june.editor

import com.denser.june.presentation.screens.editor.EditorAction
import com.denser.june.presentation.screens.editor.importEditorMedia
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EditorMediaImporterTest {
    @Test fun retainedScopeKeepsImportRunningAcrossUiRecreation() = runTest {
        val retainedJob = SupervisorJob()
        val retainedScope = CoroutineScope(retainedJob + StandardTestDispatcher(testScheduler))
        val uiJob = Job()
        val copyFinished = CompletableDeferred<String?>()
        val compressionFinished = CompletableDeferred<Unit>()
        val actions = mutableListOf<EditorAction>()
        try {
            val import = retainedScope.importEditorMedia("content://photo", { copyFinished.await() }, { compressionFinished.await() }, actions::add)
            runCurrent()
            uiJob.cancel() // The destroyed composition does not own this operation.
            assertTrue(import.isActive)
            copyFinished.complete("/media/photo.jpg")
            runCurrent()
            assertTrue(actions.contains(EditorAction.ReplaceImage("content://photo", "/media/photo.jpg")))
            assertFalse(actions.contains(EditorAction.SetMediaProcessing("/media/photo.jpg", false)))
            compressionFinished.complete(Unit)
            import.join()
            assertTrue(actions.contains(EditorAction.SetMediaProcessing("content://photo", false)))
            assertTrue(actions.contains(EditorAction.SetMediaProcessing("/media/photo.jpg", false)))
        } finally { retainedJob.cancel() }
    }

    @Test fun cancelledCopyRemovesTheUriAndClearsItsProcessingMarker() = runTest {
        val actions = mutableListOf<EditorAction>()
        val import = backgroundScope.importEditorMedia("content://photo", { awaitCancellation() }, {}, actions::add)
        runCurrent()
        import.cancelAndJoin()
        assertTrue(actions.contains(EditorAction.RemoveImage("content://photo")))
        assertEquals(EditorAction.SetMediaProcessing("content://photo", false), actions.last())
    }

    @Test fun compressionFailureRetainsThePersistedAttachmentAndClearsBothMarkers() = runTest {
        val actions = mutableListOf<EditorAction>()
        var failed = false
        val import = backgroundScope.importEditorMedia("content://photo", { "/media/photo.jpg" },
            { throw java.io.IOException("Codec unavailable") }, actions::add, { failed = true })
        import.join()
        assertTrue(failed)
        assertFalse(actions.any { it is EditorAction.RemoveImage })
        assertEquals(EditorAction.SetMediaProcessing("/media/photo.jpg", false), actions.last())
        assertTrue(actions.contains(EditorAction.SetMediaProcessing("content://photo", false)))
    }
    @Test fun nullPersistenceReportsFailureAndRemovesItsUnresolvedPlaceholder() = runTest {
        val actions = mutableListOf<EditorAction>()
        var failures = 0
        val import = backgroundScope.importEditorMedia("content://unreadable", { null },
            { fail("No persisted file to compress") }, actions::add, { failures++ })
        import.join()
        assertEquals(1, failures)
        assertTrue(actions.contains(EditorAction.RemoveImage("content://unreadable")))
        assertEquals(EditorAction.SetMediaProcessing("content://unreadable", false), actions.last())
    }

}
