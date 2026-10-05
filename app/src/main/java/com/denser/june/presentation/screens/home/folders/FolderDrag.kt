package com.denser.june.presentation.screens.home.folders

import android.content.ClipData
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback

/** One persistent native target handles new lazy rows and folders composed mid-drag. */
@Stable
class FolderDrag {
    var item by mutableStateOf<FolderItem?>(null)
    var hovered by mutableStateOf<String?>(null)
    internal val regions = mutableMapOf<Any, FolderDropRegion>()
    internal fun regionAt(point: Offset, item: FolderItem) = regions.values
        .filter { it.bounds.contains(point) && it.accepts(item) }
        .maxWithOrNull(compareBy<FolderDropRegion> { it.priority }.thenBy { -it.bounds.width * it.bounds.height })
    fun end() { item = null; hovered = null }
}

internal class FolderDropRegion(
    val key: String, val accepts: (FolderItem) -> Boolean, val drop: (FolderItem) -> Unit,
    var bounds: Rect = Rect.Zero
) {
    val priority get() = when {
        key.startsWith("scroll:") -> 4
        key.startsWith("before:") -> 3
        key.startsWith("crumb:") -> 2
        key.startsWith("into:") -> 1
        else -> 0
    }
}

@Composable
fun Modifier.folderDragSource(item: FolderItem, drag: FolderDrag, enabled: Boolean): Modifier {
    val haptics = LocalHapticFeedback.current
    return if (!enabled) this else dragAndDropSource { _ ->
        drag.item = item
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        DragAndDropTransferData(ClipData.newPlainText("June folder item", item.id), localState = item)
    }
}

@Composable
fun Modifier.folderDragHost(drag: FolderDrag): Modifier {
    val haptics = LocalHapticFeedback.current
    val target = remember(drag) {
        object : DragAndDropTarget {
            override fun onMoved(event: DragAndDropEvent) {
                val native = event.toAndroidDragEvent()
                val item = native.localState as? FolderItem ?: return
                val key = drag.regionAt(Offset(native.x, native.y), item)?.key
                if (key != drag.hovered) {
                    drag.hovered = key
                    if (key != null) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            }
            override fun onExited(event: DragAndDropEvent) { drag.hovered = null }
            override fun onEnded(event: DragAndDropEvent) { drag.end() }
            override fun onDrop(event: DragAndDropEvent): Boolean {
                val native = event.toAndroidDragEvent()
                val item = native.localState as? FolderItem ?: return false
                val region = drag.regionAt(Offset(native.x, native.y), item) ?: return false
                // Scrolling zones guide the gesture; releasing there must not silently move an item.
                if (region.key.startsWith("scroll:")) return false
                region.drop(item)
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                drag.end()
                return true
            }
        }
    }
    return dragAndDropTarget(shouldStartDragAndDrop = { it.toAndroidDragEvent().localState is FolderItem }, target = target)
}

@Composable
fun Modifier.folderDropTarget(
    key: String,
    drag: FolderDrag,
    accepts: (FolderItem) -> Boolean,
    onDrop: (FolderItem) -> Unit
): Modifier {
    val currentAccepts by rememberUpdatedState(accepts)
    val currentDrop by rememberUpdatedState(onDrop)
    val token = remember { Any() }
    val region = remember(key, drag) { FolderDropRegion(key, { currentAccepts(it) }, { currentDrop(it) }) }
    DisposableEffect(drag, region) {
        drag.regions[token] = region
        onDispose { drag.regions.remove(token) }
    }
    return onGloballyPositioned { region.bounds = it.boundsInRoot() }
}
