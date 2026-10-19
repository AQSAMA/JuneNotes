package com.denser.june.notes

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.test.platform.app.InstrumentationRegistry
import com.denser.june.core.R
import com.denser.june.core.domain.folder.FolderRepository
import com.denser.june.core.domain.model.AppTheme
import com.denser.june.core.domain.model.Journal
import com.denser.june.core.domain.model.enums.ThemeMode
import com.denser.june.core.domain.preferences.JournalPreferences
import com.denser.june.core.domain.repository.JournalRepository
import com.denser.june.presentation.screens.home.HomeScreen
import com.denser.june.presentation.screens.home.components.*
import com.denser.june.presentation.screens.home.folders.*
import com.denser.june.presentation.screens.settings.screens.AppearanceSettingsScreen
import com.denser.june.presentation.theme.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class CompactNotesTest {
    @get:Rule val ui = createComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val preferences get() = org.koin.core.context.GlobalContext.get().get<JournalPreferences>()
    private fun note(id: String, title: String, body: String) = Journal(id, title, body,
        createdAt = 1792425600000L, updatedAt = null, dateTime = 1792425600000L, isDraft = false)

    @Composable private fun Theme(light: Boolean = false, content: @Composable () -> Unit) {
        val theme = AppTheme(themeMode = if (light) ThemeMode.LIGHT else ThemeMode.DARK, seedColor = 0xFF80CCD9.toInt())
        CompositionLocalProvider(LocalAppTheme provides theme, LocalInternetAllowed provides false) {
            JuneTheme(theme) { Surface(Modifier.fillMaxSize()) { content() } }
        }
    }

    @Test fun variableHeightCardsSwitchToListAndKeepActionsSeparate() {
        var grid by mutableStateOf(true)
        var opened = false
        var bookmarked = false
        var options = false
        val notes = listOf(note("short", "Shopping", "Coffee and notebooks"),
            note("long", "Reading notes", "Ideas worth keeping\n\nA longer note with enough detail to read from the collection.\nAdd the next step.\nKeep the useful parts visible."),
            note("arabic", "ملاحظات سريعة", "الأفكار المهمة تبقى واضحة أثناء التصفح.\nأضف التفاصيل لاحقًا."))
        ui.setContent { Theme {
            Box(Modifier.width(360.dp).fillMaxHeight()) {
                NoteCollection(notes, grid, false) { _, item ->
                    NotePreviewCard(item, 8, onClick = { opened = true }, onLongClick = { options = true }, onBookmark = { bookmarked = true })
                }
            }
        } }
        val short = ui.onNodeWithTag("note-card-short").fetchSemanticsNode().boundsInRoot
        val long = ui.onNodeWithTag("note-card-long").fetchSemanticsNode().boundsInRoot
        assertTrue("Two columns at phone width", long.left > short.left)
        assertTrue("Content determines height", long.height > short.height)
        ui.onNodeWithTag("note-media-short", useUnmergedTree = true).assertDoesNotExist()
        ui.onNodeWithTag("note-body-short", useUnmergedTree = true).assertTextEquals("Coffee and notebooks")
        screenshot("compact-grid-dark")
        ui.onNode(hasContentDescription(context.getString(R.string.bookmark)) and hasAnyAncestor(hasTestTag("note-card-short"))).performClick()
        ui.runOnIdle { assertTrue(bookmarked); assertFalse(opened); grid = false }
        ui.onNodeWithTag("notes-list").assertExists()
        val first = ui.onNodeWithTag("note-card-short").fetchSemanticsNode().boundsInRoot
        val second = ui.onNodeWithTag("note-card-long").fetchSemanticsNode().boundsInRoot
        assertEquals(first.left, second.left, 1f)
        assertTrue(second.top >= first.bottom)
        screenshot("compact-list-dark")
        ui.onNodeWithTag("note-card-short").performTouchInput { longClick() }
        ui.runOnIdle { assertTrue(options); assertFalse(opened) }
        ui.onNodeWithTag("note-card-short").performClick()
        ui.runOnIdle { assertTrue(opened) }
    }

    @Test fun largeArabicTextGetsAReadableSingleColumnInLightMode() {
        val notes = listOf(note("ar1", "ملاحظات العمل", "ملاحظة قصيرة قابلة للقراءة."),
            note("ar2", "أفكار للقراءة", "تجربة نص عربي طويل مع تفاصيل تساعد على التصفح.\nكل ملاحظة تأخذ المساحة المناسبة لمحتواها."))
        ui.setContent { Theme(light = true) {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl,
                LocalDensity provides Density(density.density, 1.5f)) {
                Box(Modifier.width(360.dp).fillMaxHeight()) {
                    NoteCollection(notes, true, false) { _, item -> NotePreviewCard(item, 8, {}, {}, {}) }
                }
            }
        } }
        val first = ui.onNodeWithTag("note-card-ar1").fetchSemanticsNode().boundsInRoot
        val second = ui.onNodeWithTag("note-card-ar2").fetchSemanticsNode().boundsInRoot
        assertEquals(first.left, second.left, 1f)
        assertTrue(second.top >= first.bottom)
        screenshot("compact-arabic-large-light")
    }

    @Test fun appearanceOptInUpdatesHomeAndFolderSidebarAndCanBeReversed() {
        val old = runBlocking { Triple(preferences.compactNotesEnabled().first(), preferences.notesGridEnabled().first(), preferences.notePreviewLines().first()) }
        val koin = org.koin.core.context.GlobalContext.get()
        val folders = koin.get<FolderRepository>()
        val journals = koin.get<JournalRepository>()
        val id = UUID.randomUUID().toString()
        val folder = runBlocking { folders.create("Project notes", null) }
        val child = runBlocking { folders.create("Ideas", folder) }
        val vm = FoldersVM(folders, journals, preferences, SavedStateHandle())
        var page by mutableIntStateOf(0)
        try {
            runBlocking {
                preferences.setCompactNotesEnabled(false)
                preferences.setNotesGridEnabled(true)
                journals.insertJournal(note(id, "A readable note", "The note preview is visible directly in the collection."))
            }
            ui.setContent { Theme {
                when (page) {
                    0 -> AppearanceSettingsScreen()
                    1 -> HomeScreen()
                    else -> FolderSidebar(vm, false, {}, {}, {}, compact = true)
                }
            } }
            ui.onNodeWithText(context.getString(R.string.compact_notes)).performClick()
            ui.waitUntil(5000) { runBlocking { preferences.compactNotesEnabled().first() } }
            screenshot("compact-appearance-settings")
            ui.runOnIdle { page = 1 }
            ui.waitUntil(5000) { ui.onAllNodesWithTag("notes-grid").fetchSemanticsNodes().isNotEmpty() }
            ui.onNodeWithTag("note-card-$id").assertExists()
            screenshot("compact-home")
            ui.onNodeWithTag("notes-layout-toggle").performClick()
            ui.waitUntil(5000) { !runBlocking { preferences.notesGridEnabled().first() } }
            ui.onNodeWithTag("notes-list").assertExists()
            ui.runOnIdle { page = 2 }
            ui.waitUntil(5000) { ui.onAllNodesWithTag("sidebar-expand-$folder").fetchSemanticsNodes().isNotEmpty() }
            ui.onNodeWithTag("sidebar-expand-$folder").performClick()
            ui.onNodeWithText("Ideas").assertIsDisplayed()
            screenshot("compact-folder-sidebar")
            runBlocking { preferences.setCompactNotesEnabled(false) }
            ui.runOnIdle { page = 1 }
            ui.waitUntil(5000) { ui.onAllNodesWithTag("compact-search-entry").fetchSemanticsNodes().isEmpty() }
            ui.onNodeWithTag("notes-list").assertDoesNotExist()
        } finally {
            runBlocking {
                preferences.setCompactNotesEnabled(old.first)
                preferences.setNotesGridEnabled(old.second)
                preferences.setNotePreviewLines(old.third)
                journals.hardDeleteJournal(id)
                folders.delete(child); folders.delete(folder)
            }
        }
    }

    private fun screenshot(name: String) {
        instrumentation.uiAutomation.executeShellCommand("mkdir -p /sdcard/Download/june-ui").close()
        instrumentation.uiAutomation.executeShellCommand("screencap -p /sdcard/Download/june-ui/$name.png").use { fd ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).use { it.readBytes() }
        }
    }
}
