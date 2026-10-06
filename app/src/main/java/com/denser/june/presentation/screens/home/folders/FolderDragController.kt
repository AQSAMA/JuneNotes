package com.denser.june.presentation.screens.home.folders

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.*

/** A persistent native session with live hit regions, including newly composed lazy-list rows. */
internal class FolderDragController(private val scope: CoroutineScope, private val onHover: () -> Unit) {
    private data class Region(
        val bounds: Rect,
        val accepts: (FolderDrag) -> Boolean,
        val drop: (FolderDrag) -> Unit,
        val open: (() -> Unit)?,
        val scroll: (suspend () -> Unit)?,
        val priority: Int
    )
    private val regions = linkedMapOf<Any, Region>()
    private var item: FolderDrag? = null
    private var pointer: Offset? = null
    private var hoverJob: Job? = null
    var hovered by mutableStateOf<Any?>(null)
        private set

    fun register(key: Any, bounds: Rect, accepts: (FolderDrag) -> Boolean, drop: (FolderDrag) -> Unit,
                 open: (() -> Unit)?, scroll: (suspend () -> Unit)?, priority: Int) {
        regions[key] = Region(bounds, accepts, drop, open, scroll, priority)
        refresh()
    }
    fun unregister(key: Any) { regions.remove(key); refresh() }
    fun begin(value: FolderDrag) { item = value }
    fun move(position: Offset) { pointer = position; refresh() }
    fun leave() { pointer = null; select(null) }
    fun end() { item = null; pointer = null; select(null) }
    fun drop(fallback: (FolderDrag) -> Boolean): Boolean {
        refresh()
        hoverJob?.cancel()
        val value = item ?: return false
        val region = regions[hovered]
        if (region != null && region.accepts(value)) { region.drop(value); return true }
        // An invalid folder under the pointer must reject the drop, rather than file it in the background.
        val position = pointer
        if (position != null && regions.values.any { it.bounds.contains(position) }) return false
        return fallback(value)
    }
    private fun refresh() {
        val value = item
        val position = pointer
        val key = if (value == null || position == null) null else regions.entries
            .filter { it.value.bounds.width > 0 && it.value.bounds.height > 0 && it.value.bounds.contains(position) && it.value.accepts(value) }
            .sortedWith(compareByDescending<Map.Entry<Any, Region>> { it.value.priority }
                .thenBy { it.value.bounds.width * it.value.bounds.height })
            .firstOrNull()?.key
        select(key)
    }
    private fun select(key: Any?) {
        if (key == hovered) return
        hovered = key
        hoverJob?.cancel()
        val region = regions[key] ?: return
        onHover()
        hoverJob = scope.launch {
            if (region.scroll != null) {
                while (hovered == key) { region.scroll.invoke(); delay(40) }
            } else if (region.open != null) {
                delay(800)
                if (hovered == key) region.open.invoke()
            }
        }
    }
}
