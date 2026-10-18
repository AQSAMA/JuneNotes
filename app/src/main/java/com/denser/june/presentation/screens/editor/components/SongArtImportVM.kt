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

    fun importArtwork(context: Context, uri: Uri) {
        importArtwork { SongMediaStorage.persistSongArt(context.applicationContext, uri) }
    }

    internal fun importArtwork(persist: () -> String?) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            var ownedPath: String? = null
            try {
                withContext(NonCancellable + Dispatchers.IO) { ownedPath = persist() }
                ensureActive()
                importedPath?.let { File(it).delete() }
                importedPath = ownedPath
                ownedPath = null
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Keep the current artwork when the import cannot be completed.
            } finally {
                ownedPath?.let { File(it).delete() }
                busy = false
            }
        }
    }

    fun acknowledge(path: String) {
        if (importedPath == path) importedPath = null
    }

    override fun onCleared() {
        importedPath?.let { File(it).delete() }
        importedPath = null
    }
}
