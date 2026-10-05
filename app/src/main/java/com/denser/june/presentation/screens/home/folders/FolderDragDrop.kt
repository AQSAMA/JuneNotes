package com.denser.june.presentation.screens.home.folders

import android.content.ClipData
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.denser.june.core.R

private const val FOLDER_MIME = "application/vnd.june.folder-item"
private val LocalFolderDragController = staticCompositionLocalOf<FolderDragController> { error("FolderDragHost is required") }

@Composable
@OptIn(ExperimentalFoundationApi::class)
@Suppress("DEPRECATION")
fun FolderDragHandle(item: FolderDrag, onMove: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val currentMove by rememberUpdatedState(onMove)
    val description = stringResource(R.string.folder_drag_or_move)
    // One gesture detector owns both tap and long press. IconButton's clickable detector
    // consumes the down event needed by the default native drag source on touch devices.
    Box(
        modifier = Modifier.size(48.dp).semantics {
            contentDescription = description
            onClick { currentMove(); true }
        }.dragAndDropSource(block = {
            detectTapGestures(
                onTap = { currentMove() },
                onLongPress = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    startTransfer(DragAndDropTransferData(
                        clipData = ClipData("June folder item", arrayOf(FOLDER_MIME), ClipData.Item(item.id)),
                        localState = item
                    ))
                }
            )
        }),
        contentAlignment = Alignment.Center
    ) {
        Icon(painterResource(R.drawable.drag_indicator_24px), null)
    }
}

/** Compose registers native targets only at drag start; retain one host while rows and folders change. */
@Composable
fun FolderDragHost(
    accepts: (FolderDrag) -> Boolean,
    onDrop: (FolderDrag) -> Unit,
    onDragActive: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val controller = remember { FolderDragController(scope) { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) } }
    val currentAccepts by rememberUpdatedState(accepts)
    val currentDrop by rememberUpdatedState(onDrop)
    val currentActive by rememberUpdatedState(onDragActive)
    val target = remember {
        object : DragAndDropTarget {
            private fun move(event: DragAndDropEvent) {
                val native = event.toAndroidDragEvent()
                controller.move(Offset(native.x, native.y))
            }
            override fun onStarted(event: DragAndDropEvent) {
                val item = event.toAndroidDragEvent().localState as? FolderDrag ?: return
                controller.begin(item)
                currentActive(true)
            }
            override fun onEntered(event: DragAndDropEvent) = move(event)
            override fun onMoved(event: DragAndDropEvent) = move(event)
            override fun onExited(event: DragAndDropEvent) { controller.leave() }
            override fun onEnded(event: DragAndDropEvent) { controller.end(); currentActive(false) }
            override fun onDrop(event: DragAndDropEvent): Boolean {
                move(event)
                return controller.drop { item ->
                    if (currentAccepts(item)) { currentDrop(item); true } else false
                }
            }
        }
    }
    DisposableEffect(controller) { onDispose { controller.end() } }
    CompositionLocalProvider(LocalFolderDragController provides controller) {
        Box(modifier.dragAndDropTarget(
            shouldStartDragAndDrop = { event -> event.mimeTypes().contains(FOLDER_MIME) && event.toAndroidDragEvent().localState is FolderDrag },
            target = target
        )) { content() }
    }
}

@Composable
fun FolderDropSurface(
    accepts: (FolderDrag) -> Boolean,
    onDrop: (FolderDrag) -> Unit,
    modifier: Modifier = Modifier,
    onHoverOpen: (() -> Unit)? = null,
    onHoverScroll: (suspend () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val controller = LocalFolderDragController.current
    val key = remember { Any() }
    val currentAccepts by rememberUpdatedState(accepts)
    val currentDrop by rememberUpdatedState(onDrop)
    val currentOpen by rememberUpdatedState(onHoverOpen)
    val currentScroll by rememberUpdatedState(onHoverScroll)
    val hovered = controller.hovered == key
    DisposableEffect(controller, key) { onDispose { controller.unregister(key) } }
    val scale by animateFloatAsState(if (hovered) 1.025f else 1f, label = "folder_drop_scale")
    val color by animateColorAsState(if (hovered) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface, label = "folder_drop_color")
    Surface(
        color = color,
        border = if (hovered) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        shape = RoundedCornerShape(24.dp),
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .onGloballyPositioned { coordinates ->
                controller.register(
                    key, coordinates.boundsInRoot(), { currentAccepts(it) }, { currentDrop(it) },
                    if (onHoverOpen == null) null else { { currentOpen?.invoke() } },
                    if (onHoverScroll == null) null else { { currentScroll?.invoke(); Unit } },
                    if (onHoverScroll == null) 0 else 10
                )
            }
    ) { content() }
}
