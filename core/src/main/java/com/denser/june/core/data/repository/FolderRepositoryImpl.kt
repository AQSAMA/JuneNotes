package com.denser.june.core.data.repository

import androidx.room.withTransaction
import com.denser.june.core.data.database.folders.*
import com.denser.june.core.data.database.journal.JournalDatabase
import com.denser.june.core.domain.folders.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.json.Json
import java.util.UUID

class FolderRepositoryImpl(private val database: JournalDatabase) : FolderRepository {
    private val dao = database.folderDao()
    override fun observe(): Flow<FolderSnapshot> = combine(dao.observeFolders(), dao.observePlacements()) { folders, placements ->
        domain(folders, placements)
    }
    override fun observeDirty(): Flow<Boolean> = combine(observe(), dao.observeSynced()) { state, synced ->
        encode(state) != (synced ?: encode(FolderSnapshot()))
    }
    override suspend fun snapshot(): FolderSnapshot = database.withTransaction { read() }
    private suspend fun read() = domain(dao.folders(), dao.placements())
    private fun encode(state: FolderSnapshot) = Json.encodeToString(FolderSnapshot.serializer(), state)
    override suspend fun markSynced(snapshot: FolderSnapshot) { dao.putSynced(FolderSyncState(snapshot = encode(snapshot))) }
    override suspend fun merge(snapshot: FolderSnapshot) = database.withTransaction { write(read().merge(snapshot)) }

    override suspend fun restore(snapshot: FolderSnapshot) = database.withTransaction {
        snapshot.validate()
        val local = read()
        val time = stamp(local)
        val existingFolders = local.folders.associateBy { it.id }
        val existingPlacements = local.placements.associateBy { it.journalId }
        val importedFolders = snapshot.folders.map { folder ->
            val existing = existingFolders[folder.id]
            if (existing != null && existing != folder) folder.copy(updatedAt = time, deletedAt = folder.deletedAt?.let { time }) else folder
        }
        val importedPlacements = snapshot.placements.map { placement ->
            val existing = existingPlacements[placement.journalId]
            if (existing != null && existing != placement) placement.copy(updatedAt = time) else placement
        }
        val folderIds = importedFolders.map { it.id }.toSet()
        val noteIds = importedPlacements.map { it.journalId }.toSet()
        write(FolderSnapshot(
            folders = local.folders.filter { it.id !in folderIds } + importedFolders,
            placements = local.placements.filter { it.journalId !in noteIds } + importedPlacements
        ))
    }

    override suspend fun create(name: String, parentId: String?): String = database.withTransaction {
        val state = read()
        val tree = FolderTree(state)
        require(parentId == null || parentId in tree.folders) { "Folder no longer exists" }
        val id = UUID.randomUUID().toString()
        val folder = NoteFolder(id, clean(name), parentId, tree.children(parentId).size.toLong(), stamp(state))
        write(state.copy(folders = state.folders + folder))
        id
    }

    override suspend fun rename(id: String, name: String) = database.withTransaction {
        val state = read()
        require(id in FolderTree(state).folders) { "Folder no longer exists" }
        val time = stamp(state)
        write(state.copy(folders = state.folders.map { if (it.id == id) it.copy(name = clean(name), updatedAt = time) else it }))
    }

    override suspend fun moveFolder(id: String, parentId: String?, beforeId: String?) = database.withTransaction {
        val state = read()
        val tree = FolderTree(state)
        require(tree.canMove(id, parentId)) { "A folder cannot contain itself" }
        val siblings = tree.children(parentId).map { it.id }.filter { it != id }.toMutableList()
        require(beforeId == null || beforeId in siblings) { "Destination changed" }
        siblings.add(if (beforeId == null) siblings.size else siblings.indexOf(beforeId), id)
        val positions = siblings.withIndex().associate { it.value to it.index.toLong() }
        val time = stamp(state)
        write(state.copy(folders = state.folders.map { folder ->
            positions[folder.id]?.let { position -> folder.copy(parentId = parentId, position = position, updatedAt = time) } ?: folder
        }))
    }

    override suspend fun moveNote(journalId: String, folderId: String?, beforeId: String?) = database.withTransaction {
        val state = read()
        val tree = FolderTree(state)
        require(folderId == null || folderId in tree.folders) { "Folder no longer exists" }
        val note = database.journalDao().getJournalById(journalId)
        require(note != null && note.deletedAt == null) { "Note no longer exists" }
        val placements = state.placements.associateBy { it.journalId }
        val siblings = database.journalDao().getAllJournalsSync()
            .filter { tree.folderFor(placements[it.id]) == folderId && it.id != journalId }
            .sortedWith(compareBy<com.denser.june.core.data.database.journal.JournalEntity> { placements[it.id]?.position ?: Long.MAX_VALUE }
                .thenByDescending { it.dateTime }.thenByDescending { it.createdAt }.thenBy { it.id })
            .map { it.id }.toMutableList()
        require(beforeId == null || beforeId in siblings) { "Destination changed" }
        siblings.add(if (beforeId == null) siblings.size else siblings.indexOf(beforeId), journalId)
        val positions = siblings.withIndex().associate { it.value to it.index.toLong() }
        val time = stamp(state)
        val remaining = state.placements.filter { it.journalId !in positions }
        write(state.copy(placements = remaining + positions.map { (id, position) -> NotePlacement(id, folderId, position, time) }))
    }

    override suspend fun remove(id: String) = database.withTransaction {
        val state = read()
        val tree = FolderTree(state)
        require(id in tree.folders) { "Folder no longer exists" }
        val removed = tree.descendants(id)
        val time = stamp(state)
        // Keep tombstones so another device cannot resurrect a deleted subtree.
        write(state.copy(
            folders = state.folders.map { if (it.id in removed) it.copy(deletedAt = time, updatedAt = time) else it },
            placements = state.placements.map { if (it.folderId in removed) it.copy(folderId = null, updatedAt = time) else it }
        ))
    }

    private fun clean(name: String): String = name.trim().also { require(it.isNotEmpty() && it.length <= 120) { "Enter a folder name (1–120 characters)" } }
    private fun stamp(state: FolderSnapshot) = maxOf(System.currentTimeMillis(),
        (state.folders.maxOfOrNull { it.updatedAt } ?: 0) + 1,
        (state.placements.maxOfOrNull { it.updatedAt } ?: 0) + 1)
    private suspend fun write(state: FolderSnapshot) {
        dao.putFolders(state.folders.map { FolderEntity(it.id, it.name, it.parentId, it.position, it.updatedAt, it.deletedAt) })
        dao.putPlacements(state.placements.map { PlacementEntity(it.journalId, it.folderId, it.position, it.updatedAt) })
    }
    private fun domain(folders: List<FolderEntity>, placements: List<PlacementEntity>) = FolderSnapshot(
        folders = folders.map { NoteFolder(it.id, it.name, it.parentId, it.position, it.updatedAt, it.deletedAt) },
        placements = placements.map { NotePlacement(it.journalId, it.folderId, it.position, it.updatedAt) }
    )
}
