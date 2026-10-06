package com.denser.june.folders

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.denser.june.presentation.screens.home.folders.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class FolderDragControllerTest {
    @Test fun newlyComposedFolderAndScrolledRowCanReceiveExistingDrag() {
        val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        try {
            val controller = FolderDragController(scope) { }
            val first = Any()
            var destination = ""
            controller.register(first, Rect(0f, 0f, 100f, 100f), { true }, { destination = "first" }, null, 0)
            controller.begin(FolderDrag("note", false))
            controller.move(Offset(50f, 50f))
            controller.unregister(first)
            val newRow = Any()
            controller.register(newRow, Rect(0f, 0f, 100f, 100f), { true }, { destination = "new folder" }, null, 0)
            assertTrue(controller.drop { false })
            assertEquals("new folder", destination)
            controller.end()
            assertNull(controller.hovered)
            assertNull(controller.item)
        } finally { scope.cancel() }
    }

    @Test fun rejectedForegroundRegionCannotFallThroughAndOffscreenRegionsCannotDrop() {
        val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        try {
            val controller = FolderDragController(scope) { }
            controller.viewport = Rect(0f, 0f, 100f, 100f)
            controller.register("background", Rect(0f, 0f, 100f, 100f), { true }, { fail("Invalid target cannot fall through") }, null, -1)
            controller.register("descendant", Rect(0f, 0f, 100f, 60f), { false }, { fail("Cycle") }, null, 0)
            controller.begin(FolderDrag("parent", true))
            controller.move(Offset(50f, 20f))
            assertNull(controller.hovered)
            assertFalse(controller.drop { fail("Invalid descendant must not use the fallback"); true })
            controller.move(Offset(50f, 80f))
            assertEquals("background", controller.hovered)
            controller.register("offscreen", Rect(0f, 101f, 100f, 200f), { true }, { fail("Clipped row") }, null, 0)
            controller.move(Offset(50f, 150f))
            assertFalse(controller.drop { true })
        } finally { scope.cancel() }
    }

    @Test fun insertionZoneWinsOverFolderCenterAndDropUsesFinalPosition() {
        val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        try {
            val controller = FolderDragController(scope) { }
            var result = ""
            controller.register("folder", Rect(0f, 0f, 100f, 100f), { true }, { result = "nest" }, null, 0)
            controller.register("before", Rect(0f, 0f, 100f, 25f), { true }, { result = "reorder" }, null, 5)
            controller.begin(FolderDrag("sibling", true))
            controller.move(Offset(50f, 50f))
            assertEquals("folder", controller.hovered)
            controller.move(Offset(50f, 10f))
            assertTrue(controller.drop { false })
            assertEquals("reorder", result)
        } finally { scope.cancel() }
    }
}
