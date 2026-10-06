package com.denser.june.folders

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.denser.june.core.R
import com.denser.june.core.domain.folder.FolderRepository
import com.denser.june.core.domain.model.AppTheme
import com.denser.june.core.domain.model.Journal
import com.denser.june.core.domain.model.enums.ThemeMode
import com.denser.june.core.domain.repository.JournalRepository
import com.denser.june.core.domain.preferences.JournalPreferences
import androidx.lifecycle.SavedStateHandle
import com.denser.june.presentation.screens.home.components.JournalCard
import com.denser.june.presentation.screens.home.components.JournalOptionsSheet
import com.denser.june.presentation.screens.home.folders.*
import com.denser.june.presentation.theme.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class FolderInteractionTest {
    @get:Rule val ui = createComposeRule()
    @get:Rule(order = 1) val captureFailures = object : TestWatcher() {
        override fun failed(error: Throwable, description: Description) { screenshot("failed-${description.methodName}") }
    }
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private fun text(id: Int) = context.getString(id)

    @Composable private fun Theme(content: @Composable () -> Unit) {
        val theme = AppTheme(themeMode = ThemeMode.DARK, seedColor = 0xFF80CCD9.toInt())
        CompositionLocalProvider(LocalAppTheme provides theme, LocalInternetAllowed provides false) {
            JuneTheme(theme) { Surface(Modifier.fillMaxSize()) { content() } }
        }
    }

    @Test fun aRealTouchLongPressStartsDragAndDropsOnTheTarget() {
        var active = false
        var dropped = ""
        var tapped = false
        ui.setContent {
            Theme {
                FolderDragHost(accepts = { false }, onDrop = { fail("A region must receive this drop") }, onDragActive = { active = it }) {
                    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        FolderDropSurface(accepts = { true }, onDrop = { dropped = it.id }) {
                            Box(Modifier.fillMaxWidth().height(96.dp), contentAlignment = Alignment.Center) { Text("Destination") }
                        }
                        Spacer(Modifier.height(120.dp))
                        FolderDragHandle(FolderDrag("dragged-folder", true)) { tapped = true }
                    }
                }
            }
        }
        val source = screenCenter(ui.onNodeWithContentDescription(text(R.string.folder_drag_or_move)))
        val target = screenCenter(ui.onNodeWithText("Destination"))
        val downTime = SystemClock.uptimeMillis()
        inject(MotionEvent.ACTION_DOWN, source, downTime)
        try {
            // Inject Android MotionEvents through the actual input pipeline.
            SystemClock.sleep(750)
            ui.mainClock.advanceTimeBy(800)
            ui.waitForIdle()
            ui.waitUntil(3000) { active }
            ui.runOnIdle { assertFalse(tapped) }
            screenshot("native-drag-active")
            repeat(16) { index ->
                val fraction = (index + 1) / 16f
                inject(MotionEvent.ACTION_MOVE, source + (target - source) * fraction, downTime)
                SystemClock.sleep(20)
            }
        } finally { inject(MotionEvent.ACTION_UP, target, downTime) }
        ui.waitUntil(5000) { dropped == "dragged-folder" }
        ui.waitUntil(5000) { !active }
        screenshot("native-folder-drop")
    }

    @Test fun tappingTheGripStillOpensTheDestinationControl() {
        var tapped = false
        ui.setContent { Theme { FolderDragHost({ true }, {}, {}) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                FolderDragHandle(FolderDrag("tap", true)) { tapped = true }
            }
        } } }
        val source = screenCenter(ui.onNodeWithContentDescription(text(R.string.folder_drag_or_move)))
        val downTime = SystemClock.uptimeMillis()
        inject(MotionEvent.ACTION_DOWN, source, downTime)
        inject(MotionEvent.ACTION_UP, source, downTime)
        ui.waitUntil(3000) { tapped }
    }

    @Test fun actualFolderRowsMoveIntoAnotherFolderWithTouchDragging() {
        val folders = GlobalContext.get().get<FolderRepository>()
        val journals = GlobalContext.get().get<JournalRepository>()
        val preferences = GlobalContext.get().get<JournalPreferences>()
        val ids = runBlocking { folders.create("Projects", null) to folders.create("Personal", null) }
        val model = FoldersVM(folders, journals, preferences, SavedStateHandle())
        try {
            ui.setContent { Theme { FoldersPage(model, true) } }
            ui.waitUntil(5000) { ui.onAllNodesWithContentDescription(text(R.string.folder_drag_or_move)).fetchSemanticsNodes().size == 2 }
            ui.onNodeWithText(text(R.string.folder_drop_here)).assertDoesNotExist()
            screenshot("folders-refined-layout")
            val source = screenCenter(ui.onAllNodesWithContentDescription(text(R.string.folder_drag_or_move))[0])
            val target = screenCenter(ui.onNodeWithText("Personal"))
            val downTime = SystemClock.uptimeMillis()
            inject(MotionEvent.ACTION_DOWN, source, downTime)
            try {
                SystemClock.sleep(750)
                ui.mainClock.advanceTimeBy(800)
                ui.waitForIdle()
                ui.onNodeWithContentDescription(text(R.string.folder_drop_here)).assertExists()
                assertEquals(source, screenCenter(ui.onAllNodesWithContentDescription(text(R.string.folder_drag_or_move))[0]))
                screenshot("folders-drag-active")
                repeat(12) { index ->
                    inject(MotionEvent.ACTION_MOVE, source + (target - source) * ((index + 1) / 12f), downTime)
                    SystemClock.sleep(20)
                }
            } finally { inject(MotionEvent.ACTION_UP, target, downTime) }
            ui.waitUntil(5000) { runBlocking { folders.snapshot().folders.any { it.id == ids.first && it.parentId == ids.second } } }
        } finally { runBlocking { folders.delete(ids.first); folders.delete(ids.second) } }
    }

    @Test fun immediateGripMovementStartsWithoutLongPressAndCancelDoesNotDrop() {
        var active = false
        var drops = 0
        var taps = 0
        ui.setContent { Theme {
            FolderDragHost({ true }, { drops++ }, { active = it }, Modifier.fillMaxSize().padding(top = 72.dp)) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    FolderDragHandle(FolderDrag("cancel", true, "Cancel")) { taps++ }
                }
            }
        } }
        val source = screenCenter(ui.onNodeWithContentDescription(text(R.string.folder_drag_or_move)))
        val downTime = SystemClock.uptimeMillis()
        inject(MotionEvent.ACTION_DOWN, source, downTime)
        inject(MotionEvent.ACTION_MOVE, source + Offset(0f, 100f), downTime)
        ui.waitUntil(3000) { active }
        inject(MotionEvent.ACTION_CANCEL, source + Offset(0f, 100f), downTime)
        ui.waitUntil(3000) { !active }
        ui.runOnIdle { assertEquals(0, drops); assertEquals(0, taps) }
    }

    @Test fun hoverNavigationRemovesTheSourceButRetainsTheAndroidTouchStream() {
        var navigated by mutableStateOf(false)
        var active = false
        var destination = ""
        ui.setContent { Theme {
            FolderDragHost({ false }, { fail("Use a visible target") }, { active = it }, Modifier.fillMaxSize().padding(top = 60.dp)) {
                Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (!navigated) {
                        FolderDropSurface({ true }, { destination = "parent" }, onHoverOpen = { navigated = true }) {
                            Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) { Text("Open parent") }
                        }
                        Spacer(Modifier.height(80.dp))
                        FolderDragHandle(FolderDrag("removed-source", true, "Dragged")) { fail("Not a tap") }
                    } else {
                        FolderDropSurface({ true }, { destination = "child" }) {
                            Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) { Text("New child") }
                        }
                    }
                }
            }
        } }
        val source = screenCenter(ui.onNodeWithContentDescription(text(R.string.folder_drag_or_move)))
        val target = screenCenter(ui.onNodeWithText("Open parent"))
        val downTime = SystemClock.uptimeMillis()
        inject(MotionEvent.ACTION_DOWN, source, downTime)
        try {
            inject(MotionEvent.ACTION_MOVE, source + Offset(0f, -80f), downTime)
            ui.waitUntil(3000) { active }
            inject(MotionEvent.ACTION_MOVE, target, downTime)
            SystemClock.sleep(800)
            ui.mainClock.advanceTimeBy(800)
            ui.waitUntil(3000) { navigated }
            ui.onNodeWithText("New child").assertIsDisplayed()
            ui.onNodeWithContentDescription(text(R.string.folder_drag_or_move)).assertDoesNotExist()
            assertTrue(active)
            screenshot("hover-source-removed")
        } finally { inject(MotionEvent.ACTION_UP, target, downTime) }
        ui.waitUntil(3000) { destination == "child" && !active }
    }

    @Test fun rtlDragPreviewStaysInsideTheHostAndDropsAtTheFinger() {
        var dropped = false
        ui.setContent { Theme { CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            FolderDragHost({ false }, {}, {}) {
                Column(Modifier.fillMaxWidth().testTag("rtl-host")) {
                    FolderDropSurface({ true }, { dropped = true }) {
                        Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) { Text("وجهة") }
                    }
                    Spacer(Modifier.height(80.dp))
                    FolderDragHandle(FolderDrag("rtl", true, "مجلد عربي")) {}
                }
            }
        } } }
        val source = screenCenter(ui.onNodeWithContentDescription(text(R.string.folder_drag_or_move)))
        val target = screenCenter(ui.onNodeWithText("وجهة"))
        val downTime = SystemClock.uptimeMillis()
        inject(MotionEvent.ACTION_DOWN, source, downTime)
        try {
            inject(MotionEvent.ACTION_MOVE, target, downTime)
            ui.waitForIdle()
            val host = ui.onNodeWithTag("rtl-host").fetchSemanticsNode().boundsInRoot
            val preview = ui.onNodeWithTag("folder-drag-preview").fetchSemanticsNode().boundsInRoot
            assertTrue("RTL preview must stay within the host", preview.left >= host.left && preview.right <= host.right)
            screenshot("touch-rtl-preview")
        } finally { inject(MotionEvent.ACTION_UP, target, downTime) }
        ui.waitUntil(3000) { dropped }
    }

    @Test fun actualSiblingReorderingAndAdjacentNoOpRetainFolderParents() {
        val folders = GlobalContext.get().get<FolderRepository>()
        val model = FoldersVM(folders, GlobalContext.get().get(), GlobalContext.get().get(), SavedStateHandle())
        val ids = runBlocking { (1..4).map { folders.create("Sibling $it", null) } }
        try {
            ui.setContent { Theme { FoldersPage(model, true) } }
            ui.waitUntil(5000) { model.state.value.children.size == 4 }
            touchReorder(grip(ids[3]), "folder-before-${ids[0]}")
            ui.waitUntil(5000) { model.state.value.children.map { it.id } == listOf(ids[3], ids[0], ids[1], ids[2]) }
            // Dropping immediately after its existing predecessor must preserve order.
            touchReorder(grip(ids[1]), "folder-after-${ids[0]}")
            ui.runOnIdle { assertEquals(listOf(ids[3], ids[0], ids[1], ids[2]), model.state.value.children.map { it.id }) }
            runBlocking { assertTrue(folders.snapshot().folders.filter { it.id in ids }.all { it.parentId == null }) }
            screenshot("touch-sibling-reorder")
        } finally { runBlocking { ids.forEach { folders.delete(it) } } }
    }

    @Test fun actualTouchMovesAcrossTwoHoverLevelsAndBackToRoot() {
        val folders = GlobalContext.get().get<FolderRepository>()
        val model = FoldersVM(folders, GlobalContext.get().get(), GlobalContext.get().get(), SavedStateHandle())
        val ids = runBlocking {
            val parent = folders.create("Parent destination", null)
            val child = folders.create("Child destination", parent)
            val source = folders.create("Source subtree", null)
            val descendant = folders.create("Retained descendant", source)
            listOf(parent, child, source, descendant)
        }
        try {
            ui.setContent { Theme { FoldersPage(model, true) } }
            ui.waitUntil(5000) { model.state.value.children.size == 2 }
            val source = screenCenter(grip(ids[2]))
            val parent = screenCenter(ui.onNodeWithTag("folder-row-${ids[0]}"))
            val downTime = SystemClock.uptimeMillis()
            inject(MotionEvent.ACTION_DOWN, source, downTime)
            try {
                inject(MotionEvent.ACTION_MOVE, source + Offset(-80f, 0f), downTime)
                inject(MotionEvent.ACTION_MOVE, parent, downTime)
                SystemClock.sleep(750)
                ui.mainClock.advanceTimeBy(750)
                ui.waitUntil(5000) { model.state.value.currentId == ids[0] }
                val child = screenCenter(ui.onNodeWithTag("folder-row-${ids[1]}"))
                // Leave the previous position before intentionally hovering the next level.
                inject(MotionEvent.ACTION_MOVE, child + Offset(80f, 0f), downTime)
                inject(MotionEvent.ACTION_MOVE, child, downTime)
                SystemClock.sleep(750)
                ui.mainClock.advanceTimeBy(750)
                ui.waitUntil(5000) { model.state.value.currentId == ids[1] }
                val empty = screenCenter(ui.onNodeWithText(text(R.string.folder_empty)))
                inject(MotionEvent.ACTION_MOVE, empty, downTime)
                screenshot("touch-two-hover-levels")
                inject(MotionEvent.ACTION_UP, empty, downTime)
            } catch (error: Throwable) {
                inject(MotionEvent.ACTION_CANCEL, parent, downTime)
                throw error
            }
            ui.waitUntil(5000) { runBlocking { folders.snapshot().folders.any { it.id == ids[2] && it.parentId == ids[1] } } }
            ui.waitForIdle()
            val nestedSource = screenCenter(grip(ids[2]))
            val root = screenCenter(ui.onNodeWithText(text(R.string.folders)))
            val rootDown = SystemClock.uptimeMillis()
            inject(MotionEvent.ACTION_DOWN, nestedSource, rootDown)
            inject(MotionEvent.ACTION_MOVE, nestedSource + Offset(-80f, 0f), rootDown)
            inject(MotionEvent.ACTION_MOVE, root, rootDown)
            inject(MotionEvent.ACTION_UP, root, rootDown)
            ui.waitUntil(5000) { runBlocking { folders.snapshot().folders.any { it.id == ids[2] && it.parentId == null } } }
            runBlocking { assertEquals(ids[2], folders.snapshot().folders.first { it.id == ids[3] }.parentId) }
        } finally { runBlocking { ids.reversed().forEach { folders.delete(it) } } }
    }

    @Test fun actualNoteSiblingReorderingPreservesTheJournal() {
        val folders = GlobalContext.get().get<FolderRepository>()
        val journals = GlobalContext.get().get<JournalRepository>()
        val model = FoldersVM(folders, journals, GlobalContext.get().get(), SavedStateHandle())
        val folder = runBlocking { folders.create("Ordered notes", null) }
        val notes = (1..3).map { Journal("ordered-${UUID.randomUUID()}", "Note $it", "Content $it", tags = listOf("#Topic"), createdAt = it.toLong(), updatedAt = null, dateTime = it.toLong()) }
        runBlocking { notes.forEach { journals.insertJournal(it); folders.moveJournal(it.id, folder) } }
        model.open(folder)
        try {
            ui.setContent { Theme { FoldersPage(model, true) } }
            ui.waitUntil(5000) { model.state.value.visibleNotes.size == 3 }
            touchReorder(grip(notes[2].id, false), "note-before-${notes[0].id}")
            ui.waitUntil(5000) { model.state.value.visibleNotes.map { it.id } == listOf(notes[2].id, notes[0].id, notes[1].id) }
            runBlocking { notes.forEach { assertEquals(it, journals.getJournalById(it.id)) } }
            screenshot("touch-note-reorder")
        } finally { runBlocking { notes.forEach { journals.hardDeleteJournal(it.id) }; folders.delete(folder) } }
    }

    @Test fun edgeScrollingContinuesWithStationaryFingerAndNewLazyRowsReceiveDrop() {
        lateinit var list: LazyListState
        var dropped = -1
        ui.setContent { Theme {
            list = rememberLazyListState()
            FolderDragHost({ false }, { fail("Drop on a lazy row") }, {}) {
                Box(Modifier.fillMaxWidth().height(360.dp).padding(top = 24.dp).testTag("scroll-viewport")) {
                    LazyColumn(state = list, modifier = Modifier.fillMaxSize()) {
                        items(60) { index ->
                            FolderDropSurface({ true }, { dropped = index }) {
                                Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("Row $index", Modifier.weight(1f))
                                    FolderDragHandle(FolderDrag("row-$index", true, "Dragged row")) { fail("Not a tap") }
                                }
                            }
                        }
                    }
                    FolderDragAutoScroll(list, Modifier.fillMaxSize()) { }
                }
            }
        } }
        ui.waitForIdle()
        val source = screenCenter(ui.onAllNodesWithContentDescription(text(R.string.folder_drag_or_move))[0])
        val viewport = ui.onNodeWithTag("scroll-viewport").fetchSemanticsNode()
        val center = viewport.positionOnScreen + Offset(viewport.size.width / 2f, viewport.size.height / 2f)
        val bottom = viewport.positionOnScreen + Offset(viewport.size.width / 2f, viewport.size.height - 8f)
        val top = viewport.positionOnScreen + Offset(viewport.size.width / 2f, 40f)
        ui.mainClock.autoAdvance = false
        val downTime = SystemClock.uptimeMillis()
        inject(MotionEvent.ACTION_DOWN, source, downTime)
        try {
            inject(MotionEvent.ACTION_MOVE, bottom, downTime)
            ui.mainClock.advanceTimeBy(1800)
            val advanced = ui.runOnIdle { list.firstVisibleItemIndex }
            assertTrue("Stationary bottom-edge touch must scroll", advanced > 3)
            inject(MotionEvent.ACTION_MOVE, top, downTime)
            ui.mainClock.advanceTimeBy(500)
            assertTrue("Top-edge touch must reverse scrolling", ui.runOnIdle { list.firstVisibleItemIndex } < advanced)
            inject(MotionEvent.ACTION_MOVE, center, downTime)
            ui.mainClock.advanceTimeBy(100)
            screenshot("touch-auto-scroll-new-rows")
        } finally { inject(MotionEvent.ACTION_UP, center, downTime); ui.mainClock.autoAdvance = true }
        ui.waitUntil(5000) { dropped > 0 }
    }

    private fun grip(id: String, folder: Boolean = true): SemanticsNodeInteraction = ui.onNode(
        hasContentDescription(text(R.string.folder_drag_or_move)) and hasAnyAncestor(hasTestTag("${if (folder) "folder" else "note"}-row-$id"))
    )
    private fun touchReorder(sourceNode: SemanticsNodeInteraction, targetTag: String) {
        val source = screenCenter(sourceNode)
        val downTime = SystemClock.uptimeMillis()
        inject(MotionEvent.ACTION_DOWN, source, downTime)
        try {
            inject(MotionEvent.ACTION_MOVE, source + Offset(-80f, 0f), downTime)
            ui.waitForIdle()
            val target = screenCenter(ui.onNodeWithTag(targetTag))
            inject(MotionEvent.ACTION_MOVE, target, downTime)
            inject(MotionEvent.ACTION_UP, target, downTime)
        } catch (error: Throwable) { inject(MotionEvent.ACTION_CANCEL, source, downTime); throw error }
        ui.waitForIdle()
    }

    @Test fun existingNotePickerSearchesContentAndSelectsTheOriginalCard() {
        var selected = ""
        val notes = listOf(
            Journal("meeting", "Meeting", "Budget review", createdAt = 1, updatedAt = null, dateTime = 1),
            Journal("other", "Other note", "Unrelated", createdAt = 2, updatedAt = null, dateTime = 2)
        )
        ui.setContent { Theme { ExistingFolderNoteSheet(notes, "Work", false, false, {}, { selected = it.id }) } }
        ui.onNode(hasSetTextAction()).performTextInput("budget")
        ui.onNode(hasSetTextAction()).performImeAction()
        ui.onNodeWithText("Meeting").assertIsDisplayed()
        ui.onNodeWithText("Other note").assertDoesNotExist()
        screenshot("search-existing-notes")
        ui.onNodeWithText("Meeting").performClick()
        ui.runOnIdle { assertEquals("meeting", selected) }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test fun longPressNoteMenuFilesTheNoteAndPreservesTags() {
        val folders = GlobalContext.get().get<FolderRepository>()
        val journals = GlobalContext.get().get<JournalRepository>()
        val suffix = UUID.randomUUID().toString()
        val note = Journal("menu-$suffix", "Review notes", "Original content", tags = listOf("@Person", "#Topic", "Space"), createdAt = 1, updatedAt = null, dateTime = 1)
        val destination = "Work-${suffix.take(8)}"
        val folder = runBlocking { journals.insertJournal(note); folders.create(destination, null) }
        try {
            ui.setContent {
                var options by remember { mutableStateOf(false) }
                Theme {
                    Column(Modifier.padding(16.dp)) {
                        JournalCard(note, onJournalClick = {}, onLongClick = { options = true })
                    }
                    if (options) ModalBottomSheet(onDismissRequest = { options = false }) {
                        JournalOptionsSheet(note, onToggleBookmark = {}, onDeleteOrRestore = {})
                    }
                }
            }
            ui.onNodeWithText("Review notes").performTouchInput { longClick() }
            ui.onNodeWithContentDescription(text(R.string.folder_move)).performClick()
            ui.onNode(hasSetTextAction()).performTextInput(destination)
            ui.onNode(hasSetTextAction()).performImeAction()
            ui.onNode(hasText(destination) and !hasSetTextAction()).performClick()
            ui.waitForIdle()
            screenshot("note-folder-destination")
            ui.onNodeWithText(text(R.string.folder_move_here)).performClick()
            ui.waitUntil(5000) { runBlocking { folders.snapshot().journals.any { it.journalId == note.id && it.folderId == folder } } }
            runBlocking { assertEquals(note.tags, journals.getJournalById(note.id)?.tags) }
        } finally { runBlocking { journals.hardDeleteJournal(note.id); folders.delete(folder) } }
    }

    private fun screenCenter(node: SemanticsNodeInteraction): Offset {
        val semantics = node.fetchSemanticsNode()
        return ui.runOnIdle {
            // Use the node's actual screen position, including Compose view/window insets.
            semantics.positionOnScreen + Offset(semantics.size.width / 2f, semantics.size.height / 2f)
        }
    }
    private fun inject(action: Int, position: Offset, downTime: Long) {
        val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, position.x, position.y, 0)
        event.source = InputDevice.SOURCE_TOUCHSCREEN
        try { assertTrue(instrumentation.uiAutomation.injectInputEvent(event, true)) } finally { event.recycle() }
    }
    private fun screenshot(name: String) {
        // Shared output survives the connected-test runner uninstalling the app at teardown.
        instrumentation.uiAutomation.executeShellCommand("mkdir -p /sdcard/Download/june-ui").close()
        instrumentation.uiAutomation.executeShellCommand("screencap -p /sdcard/Download/june-ui/$name.png").use { descriptor ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
        }
    }
}
