package com.denser.june.folders

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

    @Test fun aRealTouchLongPressStartsNativeDragAndDropsOnTheTarget() {
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
            // Native input keeps its real down time; Compose long-press timeouts use the test clock.
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
        ui.setContent { Theme { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            FolderDragHandle(FolderDrag("tap", true)) { tapped = true }
        } } }
        ui.onNodeWithContentDescription(text(R.string.folder_drag_or_move)).performTouchInput { click() }
        ui.runOnIdle { assertTrue(tapped) }
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
                repeat(12) { index ->
                    inject(MotionEvent.ACTION_MOVE, source + (target - source) * ((index + 1) / 12f), downTime)
                    SystemClock.sleep(20)
                }
            } finally { inject(MotionEvent.ACTION_UP, target, downTime) }
            ui.waitUntil(5000) { runBlocking { folders.snapshot().folders.any { it.id == ids.first && it.parentId == ids.second } } }
        } finally { runBlocking { folders.delete(ids.first); folders.delete(ids.second) } }
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
        val destination = "Work-$suffix"
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
            screenshot("note-folder-destination")
            ui.onNodeWithText(text(R.string.folder_move_here)).performClick()
            ui.waitUntil(5000) { runBlocking { folders.snapshot().journals.any { it.journalId == note.id && it.folderId == folder } } }
            runBlocking { assertEquals(note.tags, journals.getJournalById(note.id)?.tags) }
        } finally { runBlocking { journals.hardDeleteJournal(note.id); folders.delete(folder) } }
    }

    private fun screenCenter(node: SemanticsNodeInteraction): Offset {
        val center = node.fetchSemanticsNode().boundsInRoot.center
        val location = IntArray(2)
        instrumentation.runOnMainSync {
            val activity = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).single()
            activity.findViewById<View>(android.R.id.content).getLocationOnScreen(location)
        }
        return center + Offset(location[0].toFloat(), location[1].toFloat())
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
