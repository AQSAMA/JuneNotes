package com.denser.june.presentation.screens.home.folders

import android.content.ClipData
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draganddrop.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.denser.june.core.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val FOLDER_MIME = "application/vnd.june.folder-item"

@Composable
fun FolderDragHandle(item: FolderDrag, onMove: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    IconButton(
        onClick = onMove,
        modifier = Modifier.dragAndDropSource { _ ->
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            DragAndDropTransferData(
                clipData = ClipData("June folder item", arrayOf(FOLDER_MIME), ClipData.Item(item.id)),
                localState = item
            )
        }
    ) {
        Icon(painterResource(R.drawable.drag_indicator_24px), stringResource(R.string.folder_drag_or_move))
    }
}

/** Android's native drag shadow survives navigation into another folder during a drag. */
@Composable
fun FolderDropSurface(
    accepts: (FolderDrag) -> Boolean,
    onDrop: (FolderDrag) -> Unit,
    modifier: Modifier = Modifier,
    onHoverOpen: (() -> Unit)? = null,
    onDragActive: ((Boolean) -> Unit)? = null,
    onHoverScroll: (suspend () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    var hovered by remember { mutableStateOf(false) }
    val currentAccepts by rememberUpdatedState(accepts)
    val currentDrop by rememberUpdatedState(onDrop)
    val currentOpen by rememberUpdatedState(onHoverOpen)
    val currentActive by rememberUpdatedState(onDragActive)
    val currentScroll by rememberUpdatedState(onHoverScroll)
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val target = remember {
        object : DragAndDropTarget {
            var openJob: Job? = null
            fun item(event: DragAndDropEvent) = event.toAndroidDragEvent().localState as? FolderDrag
            override fun onStarted(event: DragAndDropEvent) { currentActive?.invoke(true) }
            override fun onEntered(event: DragAndDropEvent) {
                hovered = item(event)?.let(currentAccepts) == true
                if (hovered) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    openJob?.cancel()
                    openJob = scope.launch {
                        if (currentScroll != null) {
                            while (hovered) { currentScroll?.invoke(); delay(40) }
                        } else { delay(800); currentOpen?.invoke() }
                    }
                }
            }
            override fun onExited(event: DragAndDropEvent) { hovered = false; openJob?.cancel() }
            override fun onEnded(event: DragAndDropEvent) { hovered = false; openJob?.cancel(); currentActive?.invoke(false) }
            override fun onDrop(event: DragAndDropEvent): Boolean {
                openJob?.cancel()
                hovered = false
                val item = item(event) ?: return false
                if (!currentAccepts(item)) return false
                currentDrop(item)
                return true
            }
        }
    }
    DisposableEffect(target) { onDispose { target.openJob?.cancel() } }
    val scale by animateFloatAsState(if (hovered) 1.025f else 1f, label = "folder_drop_scale")
    val color by animateColorAsState(if (hovered) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface, label = "folder_drop_color")
    Surface(
        color = color,
        border = if (hovered) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        shape = RoundedCornerShape(24.dp),
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .dragAndDropTarget(
                shouldStartDragAndDrop = { event ->
                    event.mimeTypes().contains(FOLDER_MIME) &&
                        (event.toAndroidDragEvent().localState as? FolderDrag)?.let(currentAccepts) == true
                },
                target = target
            )
    ) { content() }
}
