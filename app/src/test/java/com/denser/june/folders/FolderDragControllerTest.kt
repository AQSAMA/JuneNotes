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
            controller.register(first, Rect(0f, 0f, 100f, 100f), { true }, { destination = "first" }, null, null, 0)
            controller.begin(FolderDrag("note", false))
            controller.move(Offset(50f, 50f))
            controller.unregister(first)
            val newRow = Any()
            controller.register(newRow, Rect(0f, 0f, 100f, 100f), { true }, { destination = "new folder" }, null, null, 0)
            assertTrue(controller.drop { false })
            assertEquals("new folder", destination)
            controller.end()
            assertNull(controller.hovered)
        } finally { scope.cancel() }
    }

    @Test fun scrollZonesTakePriorityAndInvalidDescendantIsRejected() {
        val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        try {
            val controller = FolderDragController(scope) { }
            val invalid = Any()
            val arrow = Any()
            var scrolls = 0
            controller.register(invalid, Rect(0f, 0f, 100f, 100f), { false }, { fail("Invalid folder must not receive a drop") }, null, null, 0)
            controller.register(arrow, Rect(0f, 0f, 200f, 40f), { true }, { }, null, { scrolls++ }, 10)
            controller.begin(FolderDrag("parent", true))
            controller.move(Offset(50f, 20f))
            assertEquals(arrow, controller.hovered)
            assertTrue(scrolls > 0)
            controller.move(Offset(50f, 80f))
            assertNull(controller.hovered)
            assertFalse(controller.drop { fail("An invalid descendant must not fall back to the current folder"); true })
            controller.end()
        } finally { scope.cancel() }
    }
}
