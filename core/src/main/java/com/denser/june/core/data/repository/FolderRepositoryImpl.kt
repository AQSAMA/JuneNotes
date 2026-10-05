package com.denser.june.core.data.repository

import androidx.room.withTransaction
import com.denser.june.core.data.database.folder.*
import com.denser.june.core.data.database.journal.JournalDatabase
import com.denser.june.core.domain.folder.*
import kotlinx.coroutines.flow.*
import java.util.UUID

class FolderRepositoryImpl(private val database: JournalDatabase) : FolderRepository {
    private val dao = database.folderDao()
    private fun List<FolderEntity>.domain() = map { Folder(it.id, it.name, it.parentId, it.position, it.updatedAt, it.deleted) }
    private fun List<FolderJournalEntity>.memberships() = map { FolderJournal(it.journalId, it.folderId, it.position, it.updatedAt) }
    override fun observe(): Flow<FolderSnapshot> = combine(dao.observeFolders(), dao.observeJournals()) { folders, journals ->
        FolderSnapshot(folders = folders.domain(), journals = journals.memberships()).normalized()
    }.distinctUntilChanged()
    override suspend fun snapshot(): FolderSnapshot = database.withTransaction { read() }
    private suspend fun read() = FolderSnapshot(folders = dao.folders().domain(), journals = dao.journals().memberships()).normalized()
    private suspend fun write(snapshot: FolderSnapshot) {
        dao.upsertFolders(snapshot.folders.map { FolderEntity(it.id, it.name, it.parentId, it.position, it.updatedAt, it.deleted) })
        dao.upsertJournals(snapshot.journals.map { FolderJournalEntity(it.journalId, it.folderId, it.position, it.updatedAt) })
    }
    private fun stamp(s: FolderSnapshot) = maxOf(System.currentTimeMillis(), (s.folders.map { it.updatedAt } + s.journals.map { it.updatedAt }).maxOrNull()?.plus(1) ?: 0)
    private fun validParent(s: FolderSnapshot, id: String?) = id == null || s.folders.any { it.id == id && !it.deleted }
    override suspend fun merge(snapshot: FolderSnapshot) = database.withTransaction { write(read().merge(snapshot)) }
    override suspend fun create(name: String, parentId: String?): String = database.withTransaction {
        val s = read()
        require(name.trim().isNotEmpty())
        require(validParent(s, parentId)) { "Folder no longer exists" }
        val id = UUID.randomUUID().toString()
        val position = (s.folders.filter { it.parentId == parentId && !it.deleted }.maxOfOrNull { it.position } ?: -1) + 1
        write(s.copy(folders = s.folders + Folder(id, name.trim(), parentId, position, stamp(s))))
        id
    }
    override suspend fun rename(id: String, name: String) = database.withTransaction {
        require(name.trim().isNotEmpty())
        val s = read()
        val time = stamp(s)
        write(s.copy(folders = s.folders.map { if (it.id == id && !it.deleted) it.copy(name = name.trim(), updatedAt = time) else it }))
    }
    override suspend fun moveFolder(id: String, parentId: String?, beforeId: String?) = database.withTransaction {
        val s = read()
        require(s.canMove(id, parentId)) { "A folder cannot be moved into itself or its children" }
        val ordered = s.folders.filter { !it.deleted && it.parentId == parentId && it.id != id }.sortedWith(compareBy({ it.position }, { it.id })).map { it.id }.toMutableList()
        ordered.add(ordered.indexOf(beforeId).takeIf { it >= 0 } ?: ordered.size, id)
        val positions = ordered.withIndex().associate { it.value to it.index.toLong() }
        val time = stamp(s)
        write(s.copy(folders = s.folders.map { f -> positions[f.id]?.let { f.copy(parentId = parentId, position = it, updatedAt = time) } ?: f }))
    }
    override suspend fun moveJournal(id: String, folderId: String?, beforeId: String?) = database.withTransaction {
        val s = read()
        require(validParent(s, folderId)) { "Folder no longer exists" }
        require(database.journalDao().getJournalById(id) != null) { "Note no longer exists" }
        val memberships = s.journals.associateBy { it.journalId }
        // Root notes imported from June have no membership row yet. Include them before ordering.
        val ordered = database.journalDao().getAllJournalsSync()
            .filter { it.id != id && memberships[it.id]?.folderId == folderId }
            .sortedWith(compareBy<com.denser.june.core.data.database.journal.JournalEntity> { memberships[it.id]?.position ?: Long.MAX_VALUE }
                .thenByDescending { it.dateTime }.thenByDescending { it.createdAt })
            .map { it.id }.toMutableList()
        ordered.add(ordered.indexOf(beforeId).takeIf { it >= 0 } ?: ordered.size, id)
        val time = stamp(s)
        val existing = s.journals.associateBy { it.journalId }.toMutableMap()
        ordered.forEachIndexed { index, key -> existing[key] = FolderJournal(key, folderId, index.toLong(), time) }
        write(s.copy(journals = existing.values.toList()))
    }
    override suspend fun delete(id: String) = database.withTransaction {
        val s = read()
        val folder = s.folders.firstOrNull { it.id == id && !it.deleted } ?: return@withTransaction
        val time = stamp(s)
        var folderPosition = (s.folders.filter { it.parentId == folder.parentId }.maxOfOrNull { it.position } ?: -1) + 1
        var notePosition = (s.journals.filter { it.folderId == folder.parentId }.maxOfOrNull { it.position } ?: -1) + 1
        write(s.copy(
            folders = s.folders.map {
                when {
                    it.id == id -> it.copy(deleted = true, updatedAt = time)
                    !it.deleted && it.parentId == id -> it.copy(parentId = folder.parentId, position = folderPosition++, updatedAt = time)
                    else -> it
                }
            },
            journals = s.journals.map { if (it.folderId == id) it.copy(folderId = folder.parentId, position = notePosition++, updatedAt = time) else it }
        ))
    }
}
