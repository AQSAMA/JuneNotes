package com.denser.june.media

import android.os.SystemClock
import androidx.lifecycle.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.denser.june.core.domain.repository.JournalRepository
import com.denser.june.presentation.navigation.*
import com.denser.june.presentation.screens.editor.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class EditorMediaPersistenceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val repository get() = GlobalContext.get().get<JournalRepository>()

    private fun withEditor(test: (EditorVM, AtomicInteger, String) -> Unit) {
        val store = ViewModelStore()
        val exits = AtomicInteger()
        val navigator = object : AppNavigator by JuneNavigator() {
            override fun navigateBack() { exits.incrementAndGet() }
        }
        val title = "Media save ${UUID.randomUUID()}"
        lateinit var vm: EditorVM
        instrumentation.runOnMainSync {
            val koin = GlobalContext.get()
            vm = ViewModelProvider(store, object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = EditorVM(
                    SavedStateHandle(), context, repository, koin.get(), koin.get(), navigator, koin.get()
                ) as T
            })[EditorVM::class.java]
            vm.onAction(EditorAction.ChangeTitle(title))
        }
        try { test(vm, exits, title) }
        finally {
            instrumentation.runOnMainSync { store.clear() }
            runBlocking { repository.getAllJournals().filter { it.title == title }.forEach { repository.hardDeleteJournal(it.id) } }
        }
    }

    private fun waitUntil(timeout: Long = 10000, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + timeout
        while (!condition()) {
            if (SystemClock.uptimeMillis() >= deadline) fail("Timed out waiting for media persistence")
            SystemClock.sleep(25)
        }
    }

    @Test fun draftBackWaitsForCompressionAndDatabasePersistenceBeforeLeaving() = withEditor { vm, exits, title ->
        val file = File(context.cacheDir, "${UUID.randomUUID()}.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        try {
            instrumentation.runOnMainSync {
                vm.onAction(EditorAction.AddImage(file.path))
                vm.onAction(EditorAction.SetMediaProcessing(file.path, true))
                vm.onAction(EditorAction.NavigateBack)
            }
            assertTrue(vm.state.value.isSaving)
            assertEquals(0, exits.get())
            instrumentation.runOnMainSync { vm.onAction(EditorAction.SetMediaProcessing(file.path, false)) }
            waitUntil { exits.get() == 1 }
            val saved = runBlocking { repository.getJournalById(vm.state.value.journalId!!) }!!
            assertEquals(title, saved.title)
            assertTrue(saved.isDraft)
            assertEquals(listOf(file.path), saved.images)
        } finally { file.delete() }
    }

    @Test fun completingImportRearmsAnAutosaveThatElapsedWhileProcessing() = withEditor { vm, _, title ->
        val file = File(context.cacheDir, "${UUID.randomUUID()}.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        try {
            instrumentation.runOnMainSync {
                vm.onAction(EditorAction.AddImage(file.path))
                vm.onAction(EditorAction.SetMediaProcessing(file.path, true))
            }
            SystemClock.sleep(5500) // Let the original five-second debounce expire during processing.
            assertNull(vm.state.value.journalId)
            instrumentation.runOnMainSync { vm.onAction(EditorAction.SetMediaProcessing(file.path, false)) }
            waitUntil { vm.state.value.journalId != null }
            val saved = runBlocking { repository.getJournalById(vm.state.value.journalId!!) }!!
            assertEquals(title, saved.title)
            assertEquals(listOf(file.path), saved.images)
        } finally { file.delete() }
    }
}
