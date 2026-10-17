package com.denser.june.presentation.screens.home.folders

import com.denser.june.core.domain.folder.Folder

internal data class FolderTreeRow(val folder: Folder, val depth: Int, val hasChildren: Boolean)

/** Used by navigation and search results; safe even while imported metadata is being repaired. */
internal fun folderAncestorPath(folders: List<Folder>, folderId: String?): List<Folder> {
    val byId = folders.filterNot { it.deleted }.associateBy { it.id }
    val seen = mutableSetOf<String>()
    val path = mutableListOf<Folder>()
    var id = folderId
    while (id != null && seen.add(id)) {
        val folder = byId[id] ?: break
        path.add(folder)
        id = folder.parentId
    }
    return path.asReversed()
}

/** Stable sibling order, with only expanded branches in the lazy list. No recursive stack limit. */
internal fun folderTreeRows(folders: List<Folder>, expanded: Set<String>, query: String = ""): List<FolderTreeRow> {
    val live = folders.filterNot { it.deleted }
    val terms = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    val visible = if (terms.isEmpty()) null else buildSet {
        val byId = live.associateBy { it.id }
        live.filter { folder -> terms.all { folder.name.contains(it, ignoreCase = true) } }.forEach { match ->
            var id: String? = match.id
            while (id != null && add(id)) id = byId[id]?.parentId
        }
    }
    val children = live.filter { visible == null || it.id in visible }.groupBy { it.parentId }
        .mapValues { (_, items) -> items.sortedWith(compareBy({ it.position }, { it.id })) }
    val pending = ArrayDeque<Pair<Folder, Int>>()
    children[null].orEmpty().asReversed().forEach { pending.addLast(it to 0) }
    val seen = mutableSetOf<String>()
    return buildList {
        while (pending.isNotEmpty()) {
            val (folder, depth) = pending.removeLast()
            if (!seen.add(folder.id)) continue
            val nested = children[folder.id].orEmpty()
            add(FolderTreeRow(folder, depth, nested.isNotEmpty()))
            if (visible != null || folder.id in expanded) nested.asReversed().forEach { pending.addLast(it to depth + 1) }
        }
    }
}

/** A short deliberate flick has precedence over distance; slow releases use the midpoint. */
internal fun sidebarShouldOpen(offset: Float, width: Float, velocity: Float, flingThreshold: Float): Boolean =
    when {
        width <= 0f || !width.isFinite() -> false
        velocity > flingThreshold -> true
        velocity < -flingThreshold -> false
        else -> offset > width / 2f
    }
