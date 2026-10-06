package com.denser.june.presentation.screens.home.folders

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.*

/** One session outlives lazy rows and folder navigation. All geometry is in Compose root pixels. */
internal class FolderDragController(private val scope: CoroutineScope, private val onHover: () -> Unit) {
    private data class Region(
        val bounds: Rect,
        val accepts: (FolderDrag) -> Boolean,
        val drop: (FolderDrag) -> Unit,
        val open: (() -> Unit)?,
        val priority: Int
    )
    internal data class Source(val item: FolderDrag, val bounds: Rect, val tap: () -> Unit)
    private val regions = linkedMapOf<Any, Region>()
    private val sources = linkedMapOf<Any, Source>()
    private var hoverJob: Job? = null
    private var scrolling = false
    private var navigationArmed = true
    private var navigationAnchor: Offset? = null
    var item by mutableStateOf<FolderDrag?>(null)
        private set
    var pointer by mutableStateOf<Offset?>(null)
        private set
    var hovered by mutableStateOf<Any?>(null)
        private set
    var viewport: Rect? = null
        set(value) { field = value; refresh() }

    fun registerSource(key: Any, item: FolderDrag, bounds: Rect, tap: () -> Unit) {
        sources[key] = Source(item, bounds, tap)
    }
    fun unregisterSource(key: Any) { sources.remove(key) }
    fun sourceAt(position: Offset): Source? = sources.values.lastOrNull { it.bounds.contains(position) }

    fun register(key: Any, bounds: Rect, accepts: (FolderDrag) -> Boolean, drop: (FolderDrag) -> Unit,
                 open: (() -> Unit)?, priority: Int) {
        regions[key] = Region(bounds, accepts, drop, open, priority)
        refresh()
    }
    fun unregister(key: Any) { regions.remove(key); refresh() }
    fun begin(value: FolderDrag) { end(); item = value; navigationArmed = true }
    fun move(position: Offset) {
        // Navigation is deliberately re-armed by finger movement, never by new rows appearing.
        val rearmed = !navigationArmed && navigationAnchor?.let { (it - position).getDistance() > 8f } == true
        if (rearmed) navigationArmed = true
        pointer = position
        refresh(rearmed)
    }
    fun setScrolling(active: Boolean) {
        if (scrolling == active) return
        scrolling = active
        if (active) hoverJob?.cancel() else select(hovered, restartHover = true)
    }
    fun leave() { pointer = null; select(null) }
    fun end() { item = null; pointer = null; navigationAnchor = null; scrolling = false; select(null); hoverJob?.cancel() }
    fun drop(fallback: (FolderDrag) -> Boolean): Boolean {
        refresh()
        hoverJob?.cancel()
        val value = item ?: return false
        val position = pointer ?: return false
        if (viewport?.contains(position) == false) return false
        val region = regions[hovered]
        if (region != null && region.accepts(value)) { region.drop(value); return true }
        // Reject self/descendant targets rather than silently moving into the background.
        if (regions.values.any { it.bounds.contains(position) }) return false
        return fallback(value)
    }
    private fun refresh(restartHover: Boolean = false) {
        val position = pointer
        val value = item
        val region = if (position == null || value == null || viewport?.contains(position) == false) null
        else regions.entries.filter { it.value.bounds.contains(position) && it.value.bounds.width > 0 && it.value.bounds.height > 0 }
            .maxWithOrNull(compareBy<Map.Entry<Any, Region>> { it.value.priority }
                .thenBy { -it.value.bounds.width * it.value.bounds.height })
        select(region?.takeIf { it.value.accepts(value!!) }?.key, restartHover)
    }
    private fun select(key: Any?, restartHover: Boolean = false) {
        if (key == hovered && !restartHover) return
        val changed = key != hovered
        hovered = key
        hoverJob?.cancel()
        val region = regions[key] ?: return
        if (changed) onHover()
        if (region.open != null && navigationArmed && !scrolling) hoverJob = scope.launch {
            delay(650)
            if (hovered == key && item != null) {
                navigationArmed = false
                navigationAnchor = pointer
                region.open.invoke()
            }
        }
    }
}
