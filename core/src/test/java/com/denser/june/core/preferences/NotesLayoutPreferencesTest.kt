package com.denser.june.core.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.denser.june.core.data.preferences.JournalPreferencesImpl
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class NotesLayoutPreferencesTest {
    @Test fun defaultsPreserveOriginalLayoutAndChoicesSurviveReopening() = runBlocking {
        val directory = Files.createTempDirectory("notes-layout").toFile()
        val file = File(directory, "notes.preferences_pb")
        var job = SupervisorJob()
        fun preferences() = JournalPreferencesImpl(PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + job), produceFile = { file }))
        try {
            val first = preferences()
            assertFalse(first.compactNotesEnabled().first())
            assertTrue(first.notesGridEnabled().first())
            assertEquals(8, first.notePreviewLines().first())
            first.setCompactNotesEnabled(true)
            first.setNotesGridEnabled(false)
            first.setNotePreviewLines(14)
            job.cancelAndJoin()
            job = SupervisorJob()
            val reopened = preferences()
            assertTrue(reopened.compactNotesEnabled().first())
            assertFalse(reopened.notesGridEnabled().first())
            assertEquals(14, reopened.notePreviewLines().first())
            reopened.setCompactNotesEnabled(false)
            assertFalse(reopened.compactNotesEnabled().first())
            assertFalse(reopened.notesGridEnabled().first())
            reopened.setNotePreviewLines(Int.MAX_VALUE)
            assertEquals(14, reopened.notePreviewLines().first())
            reopened.setNotePreviewLines(-1)
            assertEquals(4, reopened.notePreviewLines().first())
        } finally {
            job.cancelAndJoin()
            directory.deleteRecursively()
        }
    }
}
