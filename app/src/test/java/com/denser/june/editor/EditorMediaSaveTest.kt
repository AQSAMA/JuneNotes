package com.denser.june.editor

import com.denser.june.presentation.screens.editor.EditorState
import com.denser.june.presentation.screens.editor.awaitFinishedMedia
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EditorMediaSaveTest {
    @Test fun saveWaitsThroughUriReplacementAndAllConcurrentCompression() = runTest {
        val state = MutableStateFlow(EditorState(dateTime = 0, processingMediaPaths = setOf("content://one", "content://two")))
        val save = async { state.awaitFinishedMedia() }
        runCurrent()
        assertFalse(save.isCompleted)
        state.value = state.value.copy(images = listOf("/media/one.jpg", "/media/two.mp4"),
            processingMediaPaths = setOf("/media/one.jpg", "/media/two.mp4"))
        runCurrent()
        assertFalse(save.isCompleted)
        state.value = state.value.copy(processingMediaPaths = setOf("/media/two.mp4"))
        runCurrent()
        assertFalse(save.isCompleted)
        state.value = state.value.copy(title = "Latest edit", processingMediaPaths = emptySet())
        runCurrent()
        assertEquals(state.value, save.await())
        assertEquals("Latest edit", save.await().title)
    }

    @Test fun alreadyFinishedMediaDoesNotDelaySave() = runTest {
        val state = MutableStateFlow(EditorState(dateTime = 0, images = listOf("/media/one.jpg")))
        assertEquals(state.value, state.awaitFinishedMedia())
    }
}
