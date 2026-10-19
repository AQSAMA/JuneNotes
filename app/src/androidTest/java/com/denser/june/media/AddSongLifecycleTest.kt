package com.denser.june.media

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.denser.june.core.domain.model.SongDetails
import com.denser.june.core.domain.repository.JournalRepository
import kotlinx.coroutines.runBlocking
import com.denser.june.presentation.navigation.JuneNavigator
import com.denser.june.presentation.screens.editor.*
import com.denser.june.presentation.screens.editor.components.SongArtImportVM
import com.denser.june.presentation.screens.editor.screens.AddSongScreen
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class AddSongLifecycleTest {
    @get:Rule val ui = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun withEditor(test: (EditorVM, ViewModelStoreOwner) -> Unit) {
        val owner = object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() }
        lateinit var vm: EditorVM
        ui.runOnIdle {
            val koin = GlobalContext.get()
            vm = ViewModelProvider(owner, object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = EditorVM(
                    SavedStateHandle(), context, koin.get(), koin.get(), koin.get(), JuneNavigator(), koin.get()
                ) as T
            })[EditorVM::class.java]
        }
        try { test(vm, owner) }
        finally {
            val draftId = vm.state.value.journalId
            ui.runOnIdle { owner.viewModelStore.clear() }
            if (draftId != null) runBlocking { GlobalContext.get().get<JournalRepository>().hardDeleteJournal(draftId) }
        }
    }

    @Test fun cancelRejectsArtworkFromTheAbandonedEdit() = cancelAndReopen { ui.onNodeWithText("Cancel").performClick() }
    @Test fun toolbarBackRejectsArtworkFromTheAbandonedEdit() = cancelAndReopen { ui.onNodeWithContentDescription("Back").performClick() }
    @Test fun systemBackRejectsArtworkFromTheAbandonedEdit() = cancelAndReopen { Espresso.pressBack() }

    private fun cancelAndReopen(cancel: () -> Unit) = withEditor { vm, owner ->
        val song = SongDetails("Cancel artwork ${UUID.randomUUID()}", "Test")
        val output = File(context.cacheDir, "cancel-art-${UUID.randomUUID()}.jpg")
        val started = CountDownLatch(1)
        val finish = CountDownLatch(1)
        val completed = CountDownLatch(1)
        lateinit var importer: SongArtImportVM
        try {
            ui.runOnIdle {
                vm.onAction(EditorAction.SaveJournalSongMeta(song))
                vm.onAction(EditorAction.OpenJournalSongEdit)
            }
            ui.setContent {
                CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                    MaterialTheme { AddSongScreen(vm, {}) }
                }
            }
            ui.onNodeWithText("Edit Song").assertIsDisplayed()
            ui.runOnIdle {
                importer = ViewModelProvider(owner, ViewModelProvider.NewInstanceFactory())["add-song-art-import", SongArtImportVM::class.java]
                importer.importArtwork {
                    started.countDown()
                    check(finish.await(10, TimeUnit.SECONDS))
                    output.writeText("abandoned artwork")
                    completed.countDown()
                    output.path
                }
            }
            assertTrue(started.await(10, TimeUnit.SECONDS))
            cancel()
            ui.onNodeWithText("Add Song").assertIsDisplayed()
            ui.runOnIdle { vm.onAction(EditorAction.OpenJournalSongEdit) }
            ui.onNodeWithText("Edit Song").assertIsDisplayed()
            finish.countDown()
            assertTrue(completed.await(10, TimeUnit.SECONDS))
            ui.waitUntil(10000) { !output.exists() }
            ui.runOnIdle { assertNull(importer.importedPath); assertFalse(importer.busy) }
            ui.onNodeWithText("Save").performClick()
            ui.runOnIdle { assertNull(vm.state.value.songDetails!!.localThumbnailPath) }
        } finally { finish.countDown(); output.delete() }
    }

    @Test fun trimmerRestoresItsSongWhenTheEditorsTransientRequestIsLost() = withEditor { vm, owner ->
        val song = SongDetails("Restored trimmer ${UUID.randomUUID()}", "Artist", clipStartMs = 1000, clipEndMs = 11000)
        val restoration = StateRestorationTester(ui)
        ui.runOnIdle { vm.onAction(EditorAction.OpenClipTrimmer(song)) }
        restoration.setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                // Simulate a new EditorVM losing its transient navigation request on process death.
                DisposableEffect(Unit) { onDispose { vm.onAction(EditorAction.DismissClipTrimmer) } }
                MaterialTheme { AddSongScreen(vm, {}) }
            }
        }
        ui.onNodeWithText("Trim Song").assertIsDisplayed()
        ui.onNodeWithText(song.title).assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        ui.onNodeWithText("Trim Song").assertIsDisplayed()
        ui.onNodeWithText(song.title).assertIsDisplayed()
        ui.onNodeWithText("Done").performClick()
        ui.runOnIdle { assertEquals(song.title, vm.state.value.songDetails!!.title) }
    }
}
