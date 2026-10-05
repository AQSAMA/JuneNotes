package com.denser.june.core.domain.folders

import kotlinx.serialization.Serializable

@Serializable
data class NoteFolder(
    val id: String,
    val name: String,
    val parentId: String? = null,
    val position: Long = 0,
    val updatedAt: Long,
    val deletedAt: Long? = null
)

@Serializable
data class NotePlacement(
    val journalId: String,
    val folderId: String? = null,
    val position: Long = 0,
    val updatedAt: Long
)

/** An additive extension: Journal's schema and content hash remain unchanged. */
@Serializable
data class FolderSnapshot(
    val version: Int = 1,
    val folders: List<NoteFolder> = emptyList(),
    val placements: List<NotePlacement> = emptyList()
) {
    fun validate() {
        require(version == 1) { "Unsupported folder backup version" }
        require(folders.all { it.id.isNotBlank() && it.name.isNotBlank() && it.name.length <= 120 && it.updatedAt in 0 until Long.MAX_VALUE && it.position >= 0 && (it.deletedAt == null || it.deletedAt >= 0) })
        require(placements.all { it.journalId.isNotBlank() && it.updatedAt in 0 until Long.MAX_VALUE && it.position >= 0 })
        require(folders.map { it.id }.distinct().size == folders.size)
        require(placements.map { it.journalId }.distinct().size == placements.size)
    }

    /** Last writer wins per record, with a stable tie break for clock collisions. */
    fun merge(other: FolderSnapshot): FolderSnapshot {
        validate()
        other.validate()
        return FolderSnapshot(
            folders = (folders + other.folders).groupBy { it.id }.map { (_, records) ->
                records.maxWith(compareBy<NoteFolder> { it.updatedAt }
                    .thenBy { it.deletedAt ?: Long.MIN_VALUE }
                    .thenBy { it.parentId.orEmpty() }.thenBy { it.position }.thenBy { it.name })
            }.sortedBy { it.id },
            placements = (placements + other.placements).groupBy { it.journalId }.map { (_, records) ->
                records.maxWith(compareBy<NotePlacement> { it.updatedAt }
                    .thenBy { it.folderId.orEmpty() }.thenBy { it.position })
            }.sortedBy { it.journalId }
        )
    }
}

/** Deterministically repairs dangling parents and concurrent cyclic moves for presentation. */
class FolderTree(snapshot: FolderSnapshot) {
    val folders = snapshot.folders.filter { it.deletedAt == null }.associateBy { it.id }
    private val parents = folders.mapValues { (id, folder) ->
        folder.parentId?.takeIf { it != id && it in folders }
    }.toMutableMap()

    init {
        folders.keys.sorted().forEach { start ->
            val path = mutableListOf<String>()
            var cursor: String? = start
            while (cursor != null) {
                val cycleStart = path.indexOf(cursor)
                if (cycleStart >= 0) {
                    parents[path.drop(cycleStart).min()] = null
                    break
                }
                path.add(cursor)
                cursor = parents[cursor]
            }
        }
    }

    private val childrenByParent = folders.values.groupBy { parents[it.id] }.mapValues { (_, children) ->
        children.sortedWith(compareBy<NoteFolder> { it.position }.thenBy { it.id })
    }
    fun parentOf(id: String): String? = parents[id]
    fun children(parentId: String?): List<NoteFolder> = childrenByParent[parentId].orEmpty()

    fun path(id: String?): List<NoteFolder> {
        val result = mutableListOf<NoteFolder>()
        var cursor = id
        while (cursor != null) {
            val folder = folders[cursor] ?: break
            result.add(folder)
            cursor = parents[cursor]
        }
        return result.reversed()
    }

    fun descendants(id: String): Set<String> {
        val result = mutableSetOf(id)
        val queue = ArrayDeque<String>().apply { add(id) }
        while (queue.isNotEmpty()) children(queue.removeFirst()).forEach {
            if (result.add(it.id)) queue.add(it.id)
        }
        return result
    }

    fun canMove(id: String, destination: String?): Boolean = id in folders &&
        (destination == null || (destination in folders && destination !in descendants(id)))

    fun folderFor(placement: NotePlacement?): String? = placement?.folderId?.takeIf { it in folders }
}
