package com.denser.june.editor

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.denser.june.presentation.components.lightbox.MediaCompressionSaveVM
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MediaCompressionSaveVMTest {
    @Test fun recreatedOwnerRetainsBusyStateAndRejectsConcurrentFileWrites() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val first = ViewModelProvider(store, ViewModelProvider.NewInstanceFactory())[MediaCompressionSaveVM::class.java]
            val finishWrite = CompletableDeferred<Unit>()
            var writes = 0
            var marks = 0
            var cleanups = 0
            assertTrue(first.startSave("photo.jpg", { writes++; finishWrite.await(); true }, { marks++ }, { cleanups++ }))
            runCurrent()
            val recreated = ViewModelProvider(store, ViewModelProvider.NewInstanceFactory())[MediaCompressionSaveVM::class.java]
            assertSame(first, recreated)
            assertTrue(recreated.status("photo.jpg").busy)
            assertFalse(recreated.startSave("photo.jpg", { writes++; true }, { marks++ }))
            finishWrite.complete(Unit)
            runCurrent()
            assertFalse(recreated.status("photo.jpg").busy)
            assertTrue(recreated.status("photo.jpg").succeeded)
            assertEquals(1, writes)
            assertEquals(1, marks)
            assertEquals(1, cleanups)
        } finally { store.clear(); Dispatchers.resetMain() }
    }
}
