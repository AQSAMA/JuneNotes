package com.denser.june.presentation.screens.editor.components

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.denser.june.core.media.storage.SongMediaStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Retains import work and its unconsumed result across composition/activity recreation. */
class SongArtImportVM : ViewModel() {
    var busy by mutableStateOf(false)
        private set
    var importedPath by mutableStateOf<String?>(null)
        private set

    private var generation = 0L
    private val ownedPaths = mutableSetOf<String>()

    fun importArtwork(context: Context, uri: Uri) {
        importArtwork { SongMediaStorage.persistSongArt(context.applicationContext, uri) }
    }

    internal fun importArtwork(persist: () -> String?) {
        if (busy) return
        val requestGeneration = generation
        busy = true
        viewModelScope.launch {
            var ownedPath: String? = null
            try {
                withContext(NonCancellable + Dispatchers.IO) { ownedPath = persist() }
                ensureActive()
                if (requestGeneration != generation) return@launch
                importedPath?.let { ownedPaths.remove(it); File(it).delete() }
                ownedPath?.let { ownedPaths.add(it) }
                importedPath = ownedPath
                ownedPath = null
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Keep the current artwork when the import cannot be completed.
            } finally {
                ownedPath?.let { File(it).delete() }
                if (requestGeneration == generation) busy = false
            }
        }
    }

    fun acknowledge(path: String) {
        if (importedPath == path) importedPath = null
    }

    /** Explicit Cancel/Back ends an edit; recreation deliberately does not call this. */
    fun discard() {
        generation++
        busy = false
        importedPath = null
        ownedPaths.forEach { File(it).delete() }
        ownedPaths.clear()
    }

    /** Transfer only the selected artwork to the saved song; remove superseded imports. */
    fun commit(selectedPath: String?) {
        selectedPath?.let { ownedPaths.remove(it) }
        discard()
    }

    override fun onCleared() = discard()
}
