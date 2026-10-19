package com.denser.june.media

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.denser.june.core.domain.model.SongDetails
import com.denser.june.presentation.screens.editor.components.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class SongArtImportRetentionTest {
    @get:Rule val ui = createComposeRule()

    @Test fun artworkFinishesAfterDisposalAndIsDeliveredToTheRecreatedView() {
        val store = ViewModelStore()
        val owner = object : ViewModelStoreOwner { override val viewModelStore = store }
        val song = SongDetails("Retained artwork", "Test")
        val key = "song-art-${EditSongScope.Library}-${song.localPreviewPath}-${song.previewUrl}-${song.title}-${song.artistName}"
        val output = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "art-${UUID.randomUUID()}.jpg")
        val started = CountDownLatch(1)
        val finish = CountDownLatch(1)
        var shown by mutableStateOf(true)
        var received: String? = null
        lateinit var importer: SongArtImportVM
        try {
            ui.setContent {
                CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                    MaterialTheme {
                        if (shown) EditSongView(
                            song = song, title = song.title, onTitleChange = {}, artist = song.artistName,
                            onArtistChange = {}, album = "", onAlbumChange = {}, genre = "", onGenreChange = {},
                            artPath = null, isArtRemoved = false, onArtPathChange = { received = it }, onRemoveArt = {}
                        )
                    }
                }
            }
            ui.runOnIdle {
                importer = ViewModelProvider(owner, ViewModelProvider.NewInstanceFactory())[key, SongArtImportVM::class.java]
                importer.importArtwork {
                    started.countDown()
                    check(finish.await(10, TimeUnit.SECONDS))
                    output.writeText("artwork bytes")
                    output.path
                }
            }
            assertTrue(started.await(10, TimeUnit.SECONDS))
            ui.runOnIdle { shown = false }
            finish.countDown()
            ui.waitUntil(10000) { importer.importedPath != null }
            assertNull(received)
            ui.runOnIdle { shown = true }
            ui.waitUntil(10000) { received == output.path }
            assertEquals("artwork bytes", output.readText())
            ui.runOnIdle { assertNull(importer.importedPath) }
        } finally { finish.countDown(); ui.runOnIdle { store.clear() }; output.delete() }
    }
}
