package com.denser.june.presentation.screens.home.folders

import com.denser.june.core.domain.folder.Folder

internal data class FolderTreeRow(val folder: Folder, val depth: Int, val hasChildren: Boolean)

/** Stable sibling order, with only expanded branches in the lazy list. No recursive stack limit. */
internal fun folderTreeRows(folders: List<Folder>, expanded: Set<String>): List<FolderTreeRow> {
    val children = folders.filterNot { it.deleted }.groupBy { it.parentId }
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
            if (folder.id in expanded) nested.asReversed().forEach { pending.addLast(it to depth + 1) }
        }
    }
}

/** A short deliberate flick has precedence over distance; slow releases use the midpoint. */
internal fun sidebarShouldOpen(offset: Float, width: Float, velocity: Float, flingThreshold: Float): Boolean =
    when {
        velocity > flingThreshold -> true
        velocity < -flingThreshold -> false
        else -> offset > width / 2f
    }
