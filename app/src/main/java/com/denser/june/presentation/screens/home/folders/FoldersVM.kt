package com.denser.june.presentation.screens.home.folders

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.denser.june.core.domain.folders.*
import com.denser.june.core.domain.model.Journal
import com.denser.june.core.domain.model.enums.TimeFormat
import com.denser.june.core.domain.preferences.JournalPreferences
import com.denser.june.core.domain.repository.JournalRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed interface FolderItem {
    val id: String
    data class Folder(override val id: String) : FolderItem
    data class Note(override val id: String) : FolderItem
}

data class FoldersState(
    val loaded: Boolean = false,
    val snapshot: FolderSnapshot = FolderSnapshot(),
    val journals: List<Journal> = emptyList(),
    val currentId: String? = null,
    val busy: Boolean = false
) {
    val tree = FolderTree(snapshot)
    val currentFolder get() = currentId?.let { tree.folders[it] }
    val path get() = tree.path(currentId)
    val children get() = tree.children(currentId)
    val placements = snapshot.placements.associateBy { it.journalId }
    val noteCounts = journals.groupingBy { tree.folderFor(placements[it.id]) }.eachCount()
    val notes get() = journals.filter { tree.folderFor(placements[it.id]) == currentId }
        .sortedWith(compareBy<Journal> { placements[it.id]?.position ?: Long.MAX_VALUE }
            .thenByDescending { it.dateTime }.thenByDescending { it.createdAt }.thenBy { it.id })
    fun accepts(item: FolderItem, destination: String?) = !busy && when (item) {
        is FolderItem.Folder -> tree.canMove(item.id, destination)
        is FolderItem.Note -> journals.any { it.id == item.id } && (destination == null || destination in tree.folders)
    }
}

class FoldersVM(
    private val savedState: SavedStateHandle,
    private val folders: FolderRepository,
    private val journals: JournalRepository,
    preferences: JournalPreferences
) : ViewModel() {
    val currentId = savedState.getStateFlow<String?>("folder", null)
    private val busy = MutableStateFlow(false)
    val state = combine(folders.observe(), journals.getJournals(), currentId, busy) { snapshot, notes, id, working ->
        val validId = id?.takeIf { it in FolderTree(snapshot).folders }
        FoldersState(true, snapshot, notes, validId, working)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FoldersState())
    val timeFormat = preferences.timeFormat().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TimeFormat.TWELVE_HOUR)
    private val messages = Channel<Int>(Channel.BUFFERED)
    val events = messages.receiveAsFlow()

    init {
        viewModelScope.launch {
            folders.observe().collect { snapshot ->
                val id = currentId.value
                if (id != null && id !in FolderTree(snapshot).folders) open(null)
            }
        }
    }

    fun open(id: String?) { savedState["folder"] = id }
    fun back() { open(state.value.tree.parentOf(state.value.currentId ?: return)) }
    fun create(name: String) = mutate { folders.create(name, state.value.currentId) }
    fun rename(id: String, name: String) = mutate { folders.rename(id, name) }
    fun remove(id: String) = mutate { folders.remove(id) }
    fun move(item: FolderItem, destination: String?, beforeId: String? = null) = mutate {
        when (item) {
            is FolderItem.Folder -> folders.moveFolder(item.id, destination, beforeId)
            is FolderItem.Note -> folders.moveNote(item.id, destination, beforeId)
        }
        messages.send(com.denser.june.core.R.string.folder_moved)
    }
    fun bookmark(id: String) = mutate { journals.toggleBookmark(id) }
    fun deleteNote(id: String) = mutate { journals.softDeleteJournal(id) }

    private fun mutate(block: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            try { block() }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { messages.send(com.denser.june.core.R.string.folder_operation_failed) }
            finally { busy.value = false }
        }
    }
}
