package com.denser.june.core.domain.folder

import kotlinx.serialization.Serializable

@Serializable
data class Folder(
    val id: String,
    val name: String,
    val parentId: String? = null,
    val position: Long = 0,
    val updatedAt: Long = 0,
    val deleted: Boolean = false
)

@Serializable
data class FolderJournal(
    val journalId: String,
    val folderId: String? = null,
    val position: Long = 0,
    val updatedAt: Long = 0
)

/** Separate from Journal so every original June note format stays byte-for-byte compatible. */
@Serializable
data class FolderSnapshot(
    val version: Int = 1,
    val folders: List<Folder> = emptyList(),
    val journals: List<FolderJournal> = emptyList()
) {
    fun canMove(id: String, parentId: String?): Boolean {
        val live = folders.filterNot { it.deleted }.associateBy { it.id }
        if (id !in live || (parentId != null && parentId !in live)) return false
        var parent = parentId
        val visited = mutableSetOf<String>()
        while (parent != null) {
            if (parent == id || !visited.add(parent)) return false
            parent = live[parent]?.parentId
        }
        return true
    }

    fun forJournals(ids: Set<String>): FolderSnapshot {
        val selected = journals.filter { it.journalId in ids }
        val byId = folders.associateBy { it.id }
        val included = mutableSetOf<String>()
        selected.forEach { note ->
            var id = note.folderId
            while (id != null && included.add(id)) id = byId[id]?.parentId
        }
        return copy(folders = folders.filter { it.id in included }, journals = selected)
    }

    /** Per-record LWW with a deterministic tie-break; deletion/detachment records are retained. */
    fun merge(other: FolderSnapshot): FolderSnapshot {
        require(version == 1 && other.version == 1) { "Unsupported folder backup version" }
        val mergedFolders = (folders + other.folders).groupBy { it.id }.map { (_, candidates) ->
            candidates.maxWith(compareBy<Folder>({ it.updatedAt }, { it.deleted }, { it.parentId.orEmpty() }, { it.position }, { it.name }))
        }
        val mergedJournals = (journals + other.journals).groupBy { it.journalId }.map { (_, candidates) ->
            candidates.maxWith(compareBy<FolderJournal>({ it.updatedAt }, { it.folderId == null }, { it.folderId.orEmpty() }, { it.position }))
        }
        return FolderSnapshot(folders = mergedFolders, journals = mergedJournals).normalized()
    }

    /** Imported or concurrently moved parents may be missing, deleted, or cyclic. Repair identically on each device. */
    fun normalized(): FolderSnapshot {
        val live = folders.filterNot { it.deleted }.associateBy { it.id }.toMutableMap()
        live.keys.sorted().forEach { id ->
            val folder = live.getValue(id)
            var parent = folder.parentId
            val seen = mutableSetOf(id)
            while (parent != null && seen.add(parent)) parent = live[parent]?.parentId
            if (folder.parentId !in live || parent != null) live[id] = folder.copy(parentId = null)
        }
        return copy(
            folders = folders.map { live[it.id] ?: it }.sortedBy { it.id },
            journals = journals.map { if (it.folderId !in live) it.copy(folderId = null) else it }.sortedBy { it.journalId }
        )
    }
}
