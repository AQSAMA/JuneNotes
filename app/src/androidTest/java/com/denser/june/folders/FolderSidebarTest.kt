package com.denser.june.folders

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.denser.june.core.domain.folder.FolderRepository
import com.denser.june.core.domain.model.AppTheme
import com.denser.june.core.domain.model.enums.ThemeMode
import com.denser.june.core.domain.repository.JournalRepository
import com.denser.june.presentation.screens.home.folders.*
import com.denser.june.presentation.screens.home.HomeScreen
import com.denser.june.core.domain.model.Journal
import java.util.UUID
import com.denser.june.presentation.theme.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

@RunWith(AndroidJUnit4::class)
class FolderSidebarTest {
    @get:Rule val ui = createComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val repository get() = GlobalContext.get().get<FolderRepository>()
    private fun model() = FoldersVM(repository, GlobalContext.get().get<JournalRepository>(), GlobalContext.get().get(), SavedStateHandle())

    @Composable private fun Theme(content: @Composable () -> Unit) {
        val theme = AppTheme(themeMode = ThemeMode.DARK, seedColor = 0xFF80CCD9.toInt())
        CompositionLocalProvider(LocalAppTheme provides theme, LocalInternetAllowed provides false) {
            JuneTheme(theme) { Surface(Modifier.fillMaxSize()) { content() } }
        }
    }

    @Test fun treeExpandsSelectsAndRestoresExpansionAfterReopening() {
        val parent = runBlocking { repository.create("Sidebar parent", null) }
        val child = runBlocking { repository.create("Sidebar child", parent) }
        val vm = model()
        var open by mutableStateOf(true)
        var chosen: String? = null
        try {
            ui.setContent { Theme {
                FolderSidebarLayout(open, { open = it }, sidebar = {
                    FolderSidebar(vm, true, {}, { chosen = it; vm.open(it); open = false }, { open = false })
                }) { Button(onClick = { open = true }, Modifier.testTag("reopen")) { Text("Open") } }
            }
            ui.waitUntil(5000) { ui.onAllNodesWithTag("sidebar-expand-$parent").fetchSemanticsNodes().isNotEmpty() }
            ui.onNodeWithTag("sidebar-folder-$child").assertDoesNotExist()
            ui.onNodeWithTag("sidebar-expand-$parent").performClick()
            ui.onNodeWithText("Sidebar child").assertIsDisplayed().performClick()
            ui.waitForIdle()
            assertEquals(child, chosen)
            ui.onNodeWithTag("folder-sidebar").assertDoesNotExist()
            ui.onNodeWithTag("reopen").performClick()
            ui.onNodeWithText("Sidebar child").assertIsDisplayed()
            screenshot("sidebar-hierarchy")
        } finally { runBlocking { repository.delete(child); repository.delete(parent) } }
    }

    @Test fun realLabelLongPressNestsAndGripReordersWithoutMovingNotes() {
        val vm = model()
        val first = runBlocking { repository.create("Sidebar first", null) }
        val second = runBlocking { repository.create("Sidebar second", null) }
        val third = runBlocking { repository.create("Sidebar third", null) }
        try {
            ui.setContent { Theme { FolderSidebar(vm, true, {}, {}, {}) } }
            ui.waitUntil(5000) { ui.onAllNodesWithTag("sidebar-folder-$third").fetchSemanticsNodes().isNotEmpty() }
            // Whole-label long press, through Android's actual input pipeline.
            val from = center(ui.onNodeWithText("Sidebar first"))
            val to = center(ui.onNodeWithText("Sidebar second"))
            val down = SystemClock.uptimeMillis()
            inject(MotionEvent.ACTION_DOWN, from, down)
            try {
                SystemClock.sleep(750); ui.mainClock.advanceTimeBy(800); ui.waitForIdle()
                ui.onNodeWithTag("folder-drag-preview").assertExists()
                move(from, to, down)
            } finally { inject(MotionEvent.ACTION_UP, to, down) }
            ui.waitUntil(5000) { runBlocking { repository.snapshot().folders.any { it.id == first && it.parentId == second } } }
            ui.onNodeWithTag("sidebar-manage").performClick()
            val grip = ui.onNode(hasContentDescription(instrumentation.targetContext.getString(com.denser.june.core.R.string.folder_drag_or_move)) and hasAnyAncestor(hasTestTag("sidebar-folder-$third")))
            val source = center(grip)
            val reorderDown = SystemClock.uptimeMillis()
            inject(MotionEvent.ACTION_DOWN, source, reorderDown)
            try {
                move(source, source + Offset(-70f, 0f), reorderDown)
                val target = center(ui.onNodeWithTag("sidebar-before-$second"))
                move(source + Offset(-70f, 0f), target, reorderDown)
                inject(MotionEvent.ACTION_UP, target, reorderDown)
            } catch (e: Throwable) { inject(MotionEvent.ACTION_CANCEL, source, reorderDown); throw e }
            ui.waitUntil(5000) {
                runBlocking { repository.snapshot().folders.filter { !it.deleted && it.parentId == null }.sortedBy { it.position }.map { it.id }.let { it.indexOf(third) < it.indexOf(second) } }
            }
            screenshot("sidebar-manual-order")
        } finally { runBlocking { repository.delete(first); repository.delete(third); repository.delete(second) } }
    }

    @Test fun homeMenuSelectsFolderContentsAndBackClosesSidebarFirst() {
        val vmRepository = repository
        val journals = GlobalContext.get().get<JournalRepository>()
        val folder = runBlocking { vmRepository.create("Research", null) }
        val child = runBlocking { vmRepository.create("Reading", folder) }
        val journal = Journal("sidebar-home-${UUID.randomUUID()}", "Reading notes", "Original June note content", createdAt = 1, updatedAt = null, dateTime = 1)
        runBlocking { journals.insertJournal(journal); vmRepository.moveJournal(journal.id, child) }
        try {
            ui.setContent { Theme { HomeScreen() } }
            ui.onNodeWithContentDescription(instrumentation.targetContext.getString(com.denser.june.core.R.string.folder_open_sidebar)).performClick()
            ui.waitUntil(5000) { ui.onAllNodesWithTag("sidebar-expand-$folder").fetchSemanticsNodes().isNotEmpty() }
            ui.onNodeWithTag("sidebar-expand-$folder").performClick()
            screenshot("sidebar-in-june-home")
            ui.onNodeWithText("Reading").performClick()
            ui.onNodeWithText("Reading notes").assertIsDisplayed()
            ui.onNodeWithTag("folder-sidebar").assertDoesNotExist()
            ui.onAllNodesWithContentDescription(instrumentation.targetContext.getString(com.denser.june.core.R.string.folder_open_sidebar))[0].performClick()
            ui.waitForIdle()
            // System Back must dismiss the drawer, keeping the selected folder and note pane.
            instrumentation.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
            ui.waitForIdle()
            ui.onNodeWithTag("folder-sidebar").assertDoesNotExist()
            ui.onNodeWithText("Reading notes").assertIsDisplayed()
            screenshot("sidebar-selected-folder-notes")
        } finally { runBlocking { journals.hardDeleteJournal(journal.id); vmRepository.delete(child); vmRepository.delete(folder) } }
    }

    @Test fun nativeEdgeSwipeRevealsBothPanesAndSwipeClosesInLtr() = verifyReveal(LayoutDirection.Ltr)
    @Test fun nativeEdgeSwipeRevealsBothPanesAndSwipeClosesInRtl() = verifyReveal(LayoutDirection.Rtl)

    private fun verifyReveal(direction: LayoutDirection) {
        var open by mutableStateOf(false)
        ui.setContent { Theme { CompositionLocalProvider(LocalLayoutDirection provides direction) {
            FolderSidebarLayout(open, { open = it }, { Surface(Modifier.fillMaxSize()) { Text("Drawer content") } }) {
                Box(Modifier.fillMaxSize().testTag("main-pane")) { Text("Main content") }
            }
        } } }
        val start = center(ui.onNodeWithTag("folder-sidebar-edge"))
        val sign = if (direction == LayoutDirection.Ltr) 1f else -1f
        val down = SystemClock.uptimeMillis()
        inject(MotionEvent.ACTION_DOWN, start, down)
        move(start, start + Offset(sign * 600f, 0f), down)
        inject(MotionEvent.ACTION_UP, start + Offset(sign * 600f, 0f), down)
        ui.waitForIdle()
        assertTrue(open)
        ui.onNodeWithTag("folder-sidebar").assertIsDisplayed()
        screenshot("sidebar-reveal-${direction.name}")
        val closeStart = center(ui.onNodeWithTag("folder-sidebar"))
        val closeDown = SystemClock.uptimeMillis()
        inject(MotionEvent.ACTION_DOWN, closeStart, closeDown)
        move(closeStart, closeStart - Offset(sign * 600f, 0f), closeDown)
        inject(MotionEvent.ACTION_UP, closeStart - Offset(sign * 600f, 0f), closeDown)
        ui.waitForIdle()
        assertFalse(open)
        ui.onNodeWithTag("folder-sidebar").assertDoesNotExist()
        ui.onNodeWithText("Main content").assertIsDisplayed()
    }
    private fun center(node: SemanticsNodeInteraction): Offset {
        val root = ui.onRoot().fetchSemanticsNode()
        return root.positionOnScreen + node.fetchSemanticsNode().boundsInRoot.center - root.boundsInRoot.topLeft
    }
    private fun move(from: Offset, to: Offset, down: Long) {
        repeat(16) { i -> inject(MotionEvent.ACTION_MOVE, from + (to - from) * ((i + 1) / 16f), down); SystemClock.sleep(20) }
        repeat(2) { inject(MotionEvent.ACTION_MOVE, to, down); SystemClock.sleep(20) }
    }
    private fun inject(action: Int, at: Offset, down: Long) {
        val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, at.x, at.y, 0).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
        try { assertTrue(instrumentation.uiAutomation.injectInputEvent(event, true)) } finally { event.recycle() }
    }
    private fun screenshot(name: String) {
        instrumentation.uiAutomation.executeShellCommand("mkdir -p /sdcard/Download/june-ui").close()
        instrumentation.uiAutomation.executeShellCommand("screencap -p /sdcard/Download/june-ui/$name.png").use { fd ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).use { it.readBytes() }
        }
    }
}
