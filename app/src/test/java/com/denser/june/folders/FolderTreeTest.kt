package com.denser.june.folders

import com.denser.june.core.domain.folder.Folder
import com.denser.june.presentation.screens.home.folders.folderTreeRows
import com.denser.june.presentation.screens.home.folders.sidebarShouldOpen
import org.junit.Assert.*
import org.junit.Test

class FolderTreeTest {
    private val folders = listOf(
        Folder("b", "Second", position = 1), Folder("a", "First"),
        Folder("a2", "Second child", "a", 1), Folder("a1", "First child", "a"),
        Folder("deep", "Nested", "a1"), Folder("deleted", "Deleted", deleted = true)
    )
    @Test fun collapsedBranchesHideChildrenAndKeepManualRootOrder() {
        assertEquals(listOf("a", "b"), folderTreeRows(folders, emptySet()).map { it.folder.id })
    }
    @Test fun expansionUsesManualOrderAtEveryLevel() {
        val rows = folderTreeRows(folders, setOf("a", "a1"))
        assertEquals(listOf("a", "a1", "deep", "a2", "b"), rows.map { it.folder.id })
        assertEquals(listOf(0, 1, 2, 1, 0), rows.map { it.depth })
        assertEquals(listOf(true, true, false, false, false), rows.map { it.hasChildren })
    }
    @Test fun reparentingMovesTheWholeBranchToTheNewVisibleLocation() {
        val moved = folders.map { if (it.id == "a1") it.copy(parentId = "b") else it }
        assertEquals(listOf("a", "a2", "b", "a1", "deep"), folderTreeRows(moved, setOf("a", "b", "a1")).map { it.folder.id })
    }
    @Test fun slowReleaseUsesDistanceAndShortFlickUsesDirection() {
        assertFalse(sidebarShouldOpen(90f, 300f, 0f, 400f))
        assertTrue(sidebarShouldOpen(210f, 300f, 0f, 400f))
        assertTrue(sidebarShouldOpen(20f, 300f, 600f, 400f))
        assertFalse(sidebarShouldOpen(280f, 300f, -600f, 400f))
    }
}
