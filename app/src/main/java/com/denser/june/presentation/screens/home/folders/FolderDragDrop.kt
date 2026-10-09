package com.denser.june.presentation.screens.home.folders

import android.util.Log
import com.denser.june.BuildConfig
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import com.denser.june.core.R

private val LocalFolderDragController = staticCompositionLocalOf<FolderDragController> { error("FolderDragHost is required") }

/** Long-press the label to lift a folder; ordinary taps and vertical scroll stay native. */
@Composable
internal fun FolderDragSource(item: FolderDrag, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val controller = LocalFolderDragController.current
    val key = remember { Any() }
    DisposableEffect(controller, key) { onDispose { controller.unregisterSource(key) } }
    Box(modifier.onGloballyPositioned {
        controller.registerSource(key, item, it.boundsInRoot(), {}, immediate = false)
    }) { content() }
}

@Composable
fun FolderDragHandle(item: FolderDrag, onMove: () -> Unit) {
    val controller = LocalFolderDragController.current
    val key = remember { Any() }
    val currentMove by rememberUpdatedState(onMove)
    val description = stringResource(R.string.folder_drag_or_move)
    DisposableEffect(controller, key) { onDispose { controller.unregisterSource(key) } }
    Box(
        Modifier.size(48.dp).semantics {
            contentDescription = description
            onClick { currentMove(); true }
        }.onGloballyPositioned {
            controller.registerSource(key, item, it.boundsInRoot(), { currentMove() })
        }, contentAlignment = Alignment.Center
    ) { Icon(painterResource(R.drawable.drag_indicator_24px), null) }
}

/** Gesture ownership stays here when a hovered folder removes the originating row. */
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
    val controller = remember {
        FolderDragController(scope, debugTrace = if (BuildConfig.DEBUG) { { message ->
            if (Log.isLoggable("JuneFolderDrag", Log.DEBUG)) Log.d("JuneFolderDrag", message())
        } } else null) { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
    }
    val currentAccepts by rememberUpdatedState(accepts)
    val currentDrop by rememberUpdatedState(onDrop)
    val currentActive by rememberUpdatedState(onDragActive)
    var origin by remember { mutableStateOf(Offset.Zero) }
    DisposableEffect(controller) { onDispose { controller.end(); currentActive(false) } }
    CompositionLocalProvider(LocalFolderDragController provides controller) {
        Box(modifier.onGloballyPositioned {
            origin = it.positionInRoot()
            controller.viewport = Rect(origin, it.size.toSize())
        }.pointerInput(controller) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val source = controller.sourceAt(origin + down.position) ?: return@awaitEachGesture
                if (source.immediate) down.consume()
                var start = down.position
                var tapped = false
                var cancelled = false
                // A grip starts on touch slop OR a stationary long press; a tap still opens Move.
                withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                    while (true) {
                        val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                        if (change == null || change.isConsumed) { cancelled = true; break }
                        start = change.position
                        if (!change.pressed) { if (source.immediate) change.consume(); tapped = true; break }
                        if ((start - down.position).getDistance() >= viewConfiguration.touchSlop) {
                            if (source.immediate) change.consume() else cancelled = true
                            break
                        }
                    }
                }
                if (cancelled) return@awaitEachGesture
                if (tapped) { source.tap(); return@awaitEachGesture }
                controller.begin(source.item)
                controller.move(origin + start)
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                currentActive(true)
                try {
                    var released = false
                    while (true) {
                        // Keep the same pass for initiation and tracking. Switching to Main here
                        // would read the slop event we just consumed and cancel an immediate drag.
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.isConsumed) break
                        controller.move(origin + change.position)
                        event.changes.forEach { it.consume() }
                        if (!change.pressed) { released = true; break }
                    }
                    if (released) {
                        if (controller.drop { if (currentAccepts(it)) { currentDrop(it); true } else false })
                            haptic.performHapticFeedback(HapticFeedbackType.GestureEnd)
                    }
                } finally { controller.end(); currentActive(false) }
            }
        }) {
            content()
            val item = controller.item
            val pointer = controller.pointer
            if (item != null && pointer != null) {
                val density = LocalDensity.current
                Surface(
                    Modifier.align(AbsoluteAlignment.TopLeft).testTag("folder-drag-preview").width(180.dp).graphicsLayer {
                        translationX = (pointer.x - origin.x - size.width / 2).coerceIn(0f, ((controller.viewport?.width ?: size.width) - size.width).coerceAtLeast(0f))
                        translationY = (pointer.y - origin.y - with(density) { 76.dp.toPx() }).coerceIn(0f, ((controller.viewport?.height ?: size.height) - size.height).coerceAtLeast(0f))
                        rotationZ = -2f
                    },
                    shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.secondaryContainer,
                    shadowElevation = 8.dp
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(if (item.folder) R.drawable.folder_open_24px else R.drawable.edit_note_24px), null)
                        Spacer(Modifier.width(10.dp))
                        Text(item.label.ifBlank { stringResource(if (item.folder) R.string.folders else R.string.journals) }, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        }
    }
}

@Composable
fun FolderDropSurface(
    accepts: (FolderDrag) -> Boolean,
    onDrop: (FolderDrag) -> Unit,
    modifier: Modifier = Modifier,
    onHoverOpen: (() -> Unit)? = null,
    priority: Int = 0,
    insertion: Boolean = false,
    markerAlignment: Alignment = Alignment.Center,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val controller = LocalFolderDragController.current
    val key = remember { Any() }
    val currentAccepts by rememberUpdatedState(accepts)
    val currentDrop by rememberUpdatedState(onDrop)
    val currentOpen by rememberUpdatedState(onHoverOpen)
    val hovered = controller.hovered == key && priority >= 0
    DisposableEffect(controller, key, enabled) { onDispose { controller.unregister(key) } }
    val color by animateColorAsState(if (hovered) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent, label = "folder_drop_color")
    Box(modifier.onGloballyPositioned {
        if (enabled) controller.register(key, it.boundsInRoot(), { currentAccepts(it) },
            { currentDrop(it) }, if (onHoverOpen == null) null else { { currentOpen?.invoke() } }, priority)
    }) {
        if (insertion) {
            content()
            if (hovered) HorizontalDivider(Modifier.align(markerAlignment).fillMaxWidth(), thickness = 3.dp, color = MaterialTheme.colorScheme.primary)
        } else Surface(color = color, border = if (hovered) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
            shape = RoundedCornerShape(24.dp)) { content() }
    }
}

@Composable
internal fun FolderDragItem(item: FolderDrag, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val controller = LocalFolderDragController.current
    val alpha by animateFloatAsState(if (controller.item?.id == item.id && controller.item?.folder == item.folder) 0.35f else 1f, label = "folder_drag_origin")
    Box(modifier.graphicsLayer { this.alpha = alpha }) { content() }
}

/** Scrolling and target selection are independent. Speed follows edge penetration in dp/second. */
@Composable
internal fun FolderDragAutoScroll(listState: LazyListState, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val controller = LocalFolderDragController.current
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val density = LocalDensity.current
    val point = controller.pointer
    val edge = with(density) { 64.dp.toPx() }.coerceAtMost(bounds.height / 3)
    val penetration = if (controller.item == null || point == null || edge <= 0 || point.x !in bounds.left..bounds.right) 0f else when {
        point.y in bounds.top..(bounds.top + edge) -> -((bounds.top + edge - point.y) / edge)
        point.y in (bounds.bottom - edge)..bounds.bottom -> (point.y - bounds.bottom + edge) / edge
        else -> 0f
    }
    val canScroll = if (penetration < 0f) listState.canScrollBackward else listState.canScrollForward
    val speed by rememberUpdatedState(if (canScroll) penetration * kotlin.math.abs(penetration) * with(density) { 900.dp.toPx() } else 0f)
    LaunchedEffect(listState, controller.item, penetration != 0f && canScroll) {
        if (penetration == 0f || !canScroll) return@LaunchedEffect
        controller.setScrolling(true)
        try {
            var previous = withFrameNanos { it }
            while (controller.item != null && speed != 0f) {
                val now = withFrameNanos { it }
                val elapsed = ((now - previous) / 1_000_000_000f).coerceAtMost(0.032f)
                previous = now
                listState.scrollBy(speed * elapsed)
            }
        } finally { controller.setScrolling(false) }
    }
    Box(modifier.onGloballyPositioned { bounds = Rect(it.positionInRoot(), it.size.toSize()) }) { content() }
}
