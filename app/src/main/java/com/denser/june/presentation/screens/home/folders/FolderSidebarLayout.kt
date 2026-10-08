package com.denser.june.presentation.screens.home.folders

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.denser.june.core.R

/** A reveal drawer: both surfaces follow one offset, read only in their graphics layers. */
@Composable
internal fun FolderSidebarLayout(
    open: Boolean,
    onOpenChange: (Boolean) -> Unit,
    sidebar: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    val savedSidebar = rememberSaveableStateHolder()
    val density = LocalDensity.current
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    val dismissLabel = stringResource(R.string.folder_close_sidebar)
    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds().testTag("folder-sidebar-layout")) {
        // Leave a reachable strip of the main pane on compact phones; avoid a huge tablet drawer.
        val drawerWidth = minOf(320.dp, maxWidth - 56.dp).coerceAtLeast(0.dp)
        val width = with(density) { drawerWidth.toPx() }
        val flingThreshold = with(density) { 400.dp.toPx() }
        var offset by remember { mutableFloatStateOf(if (open) width else 0f) }
        var dragging by remember { mutableStateOf(false) }
        var releaseVelocity by remember { mutableFloatStateOf(0f) }
        LaunchedEffect(open, width, dragging) {
            if (!dragging) animate(
                initialValue = offset.coerceIn(0f, width), targetValue = if (open) width else 0f,
                initialVelocity = releaseVelocity,
                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 500f)
            ) { value, _ -> offset = value.coerceIn(0f, width) }
        }
        val requestedOpen by rememberUpdatedState(open)
        val visible by remember { derivedStateOf { requestedOpen || dragging || offset > 0f } }
        BackHandler(open || dragging) { dragging = false; releaseVelocity = 0f; onOpenChange(false) }
        val dragState = rememberDraggableState { delta -> offset = (offset + delta * direction).coerceIn(0f, width) }
        val gesture = Modifier.draggable(
            state = dragState, orientation = Orientation.Horizontal,
            onDragStarted = { dragging = true; releaseVelocity = 0f },
            onDragStopped = { velocity ->
                releaseVelocity = velocity * direction
                onOpenChange(sidebarShouldOpen(offset, width, releaseVelocity, flingThreshold))
                dragging = false
            }
        )
        // Offscreen drawers do not keep scroll jobs, drop targets or accessibility nodes alive.
        if (visible) {
            Box(Modifier.align(Alignment.CenterStart).width(drawerWidth).fillMaxHeight()
                .graphicsLayer { translationX = direction * (offset - width) }
                .then(gesture).testTag("folder-sidebar")) { savedSidebar.SaveableStateProvider("folders") { sidebar() } }
        }
        Box(Modifier.fillMaxSize().graphicsLayer { translationX = direction * offset }
            .then(if (open || dragging) Modifier.clearAndSetSemantics { } else Modifier)) {
            content()
        }
        // This overlay blocks main-pane actions while exposing both swipe and tap dismissal.
        if (visible) {
            val scrim = MaterialTheme.colorScheme.scrim
            Box(Modifier.fillMaxSize().graphicsLayer { translationX = direction * offset; alpha = if (width > 0f) offset / width else 0f }
                .background(scrim.copy(alpha = 0.20f)).then(gesture)
                .clickable(onClickLabel = dismissLabel) { releaseVelocity = 0f; onOpenChange(false) }
                .testTag("folder-sidebar-scrim"))
        } else {
            Box(Modifier.align(Alignment.CenterStart).width(24.dp).fillMaxHeight().then(gesture).testTag("folder-sidebar-edge"))
        }
    }
}
