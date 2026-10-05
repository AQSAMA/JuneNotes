package com.denser.june.presentation.screens.home.folders

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.denser.june.core.domain.folder.*
import com.denser.june.core.domain.model.Journal
import com.denser.june.core.domain.preferences.JournalPreferences
import com.denser.june.core.domain.repository.JournalRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class FoldersState(
    val snapshot: FolderSnapshot = FolderSnapshot(),
    val notes: List<Journal> = emptyList(),
    val currentId: String? = null,
    val loading: Boolean = true
) {
    val liveFolders get() = snapshot.folders.filterNot { it.deleted }
    val children get() = liveFolders.filter { it.parentId == currentId }.sortedWith(compareBy({ it.position }, { it.id }))
    val visibleNotes: List<Journal> get() {
        val membership = snapshot.journals.associateBy { it.journalId }
        return notes.filter { membership[it.id]?.folderId == currentId }.sortedWith(
            compareBy<Journal> { membership[it.id]?.position ?: Long.MAX_VALUE }.thenByDescending { it.dateTime }
        )
    }
    val path: List<Folder> get() {
        val byId = liveFolders.associateBy { it.id }
        val result = mutableListOf<Folder>()
        val seen = mutableSetOf<String>()
        var id = currentId
        while (id != null && seen.add(id)) {
            val folder = byId[id] ?: break
            result.add(folder)
            id = folder.parentId
        }
        return result.reversed()
    }
}

class FoldersVM(
    private val folderRepo: FolderRepository,
    private val journalRepo: JournalRepository,
    val preferences: JournalPreferences,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val current = savedStateHandle.getStateFlow<String?>("currentFolder", null)
    val state = combine(folderRepo.observe(), journalRepo.getJournals(), current) { snapshot, journals, id ->
        FoldersState(snapshot, journals, id.takeIf { key -> snapshot.folders.any { it.id == key && !it.deleted } }, false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FoldersState())
    private val messages = Channel<String>(Channel.BUFFERED)
    val errors = messages.receiveAsFlow()
    fun open(id: String?) { savedStateHandle["currentFolder"] = id }
    fun back() { open(state.value.path.lastOrNull()?.parentId) }
    private fun mutate(block: suspend () -> Unit) = viewModelScope.launch {
        try { block() } catch (e: CancellationException) { throw e } catch (e: Exception) { messages.send(e.message ?: "Unable to update folders") }
    }
    fun create(name: String) { val parent = state.value.currentId; mutate { folderRepo.create(name, parent) } }
    fun rename(id: String, name: String) { mutate { folderRepo.rename(id, name) } }
    fun delete(id: String) { mutate { folderRepo.delete(id) } }
    fun move(item: FolderDrag, parent: String?, beforeId: String? = null) {
        mutate { if (item.folder) folderRepo.moveFolder(item.id, parent, beforeId) else folderRepo.moveJournal(item.id, parent, beforeId) }
    }
    fun bookmark(id: String) { mutate { journalRepo.toggleBookmark(id) } }
    fun deleteNote(id: String) { mutate { journalRepo.softDeleteJournal(id) } }
}

data class FolderDrag(val id: String, val folder: Boolean)
