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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/** Native Android drag session survives paging into a folder while holding an item. */
@Stable
class FolderDrag {
    var item by mutableStateOf<FolderItem?>(null)
    var hovered by mutableStateOf<String?>(null)
    fun end() { item = null; hovered = null }
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
fun Modifier.folderDropTarget(
    key: String,
    drag: FolderDrag,
    accepts: (FolderItem) -> Boolean,
    onDrop: (FolderItem) -> Unit
): Modifier {
    val currentAccepts by rememberUpdatedState(accepts)
    val currentDrop by rememberUpdatedState(onDrop)
    val haptics = LocalHapticFeedback.current
    val target = remember(key, drag) {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) {
                val item = event.toAndroidDragEvent().localState as? FolderItem ?: return
                if (currentAccepts(item)) {
                    drag.hovered = key
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            }
            override fun onExited(event: DragAndDropEvent) { if (drag.hovered == key) drag.hovered = null }
            override fun onEnded(event: DragAndDropEvent) { drag.end() }
            override fun onDrop(event: DragAndDropEvent): Boolean {
                val item = event.toAndroidDragEvent().localState as? FolderItem ?: return false
                if (!currentAccepts(item)) return false
                currentDrop(item)
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                drag.end()
                return true
            }
        }
    }
    return dragAndDropTarget(
        shouldStartDragAndDrop = { event ->
            (event.toAndroidDragEvent().localState as? FolderItem)?.let(currentAccepts) == true
        }, target = target
    )
}
