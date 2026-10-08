package com.denser.june.core.folder

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.denser.june.core.data.backup.*
import com.denser.june.core.data.database.journal.JournalDatabase
import com.denser.june.core.data.repository.*
import com.denser.june.core.domain.folder.FolderBackupCodec
import com.denser.june.core.domain.model.Journal
import com.denser.june.core.domain.preferences.SyncPreferences
import com.denser.june.core.domain.sync.deserializeJournal
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.lang.reflect.Proxy
import java.util.zip.*

@RunWith(AndroidJUnit4::class)
class FolderBackupTest {
    @Test fun originalZipAndForkZipAndMarkdownRoundTrip() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = Room.inMemoryDatabaseBuilder(context, JournalDatabase::class.java).build()
        val destination = Room.inMemoryDatabaseBuilder(context, JournalDatabase::class.java).build()
        val preferences = Proxy.newProxyInstance(SyncPreferences::class.java.classLoader, arrayOf(SyncPreferences::class.java)) { _, method, _ ->
            error("Backup should not need preferences: ${method.name}")
        } as SyncPreferences
        val sourceNotes = JournalRepositoryImpl(source.journalDao(), preferences, context)
        val targetNotes = JournalRepositoryImpl(destination.journalDao(), preferences, context)
        val sourceFolders = FolderRepositoryImpl(source)
        val targetFolders = FolderRepositoryImpl(destination)
        val exporter = ExportImpl(sourceNotes, context, sourceFolders)
        val restore = RestoreImpl(targetNotes, destination.songLibraryDao(), context, targetFolders)
        val temporary = mutableListOf<File>()
        try {
            val note = Journal("original", "أدوية", "Original June text", tags = listOf("@Person", "#Topic", "Space"), createdAt = 1, updatedAt = 2, dateTime = 1, isDraft = false)
            sourceNotes.insertJournal(note)
            val parent = sourceFolders.create("Work", null)
            val child = sourceFolders.create("Medicine", parent)
            sourceFolders.moveJournal(note.id, child)
            val forkZip = exporter.exportData(false, false).getOrThrow().also { temporary.add(it) }
            val entries = ZipFile(forkZip).use { zip ->
                assertNotNull(zip.getEntry(FolderBackupCodec.ENTRY_NAME))
                // This is exactly the original June decoder for journal entries.
                val originalRead = zip.getInputStream(zip.getEntry("journals/original.json")).bufferedReader().readText().deserializeJournal()
                assertEquals(note, originalRead)
                zip.entries().asSequence().map { it.name }.toList()
            }
            restore.restoreData(forkZip.toURI().toString()).getOrThrow()
            assertEquals(child, targetFolders.snapshot().journals.single().folderId)
            assertEquals(note, targetNotes.getJournalById(note.id))

            // Removing the optional sidecar produces an original June archive, with identical journal/media entries.
            val originalZip = File(context.cacheDir, "original-june-fixture.zip").also { temporary.add(it) }
            ZipFile(forkZip).use { zip ->
                ZipOutputStream(originalZip.outputStream()).use { out ->
                    entries.filterNot { it == FolderBackupCodec.ENTRY_NAME }.forEach { name ->
                        out.putNextEntry(ZipEntry(name)); zip.getInputStream(zip.getEntry(name)).use { it.copyTo(out) }; out.closeEntry()
                    }
                }
            }
            restore.restoreData(originalZip.toURI().toString()).getOrThrow()
            assertEquals(note, targetNotes.getJournalById(note.id))
            assertEquals(child, targetFolders.snapshot().journals.single().folderId)

            val markdownZip = exporter.exportAsMarkdown(false).getOrThrow().also { temporary.add(it) }
            targetFolders.moveJournal(note.id, null)
            // Use a fresh empty folder repository to prove sidecar import, independent of existing data.
            val fresh = Room.inMemoryDatabaseBuilder(context, JournalDatabase::class.java).build()
            try {
                val freshNotes = JournalRepositoryImpl(fresh.journalDao(), preferences, context)
                val freshFolders = FolderRepositoryImpl(fresh)
                MarkdownImportImpl(freshNotes, context, freshFolders).importMarkdownZip(android.net.Uri.fromFile(markdownZip)).getOrThrow()
                assertEquals(child, freshFolders.snapshot().journals.single().folderId)
                assertEquals(note.title, freshNotes.getJournalById(note.id)?.title)
                assertEquals(note.tags.toSet(), freshNotes.getJournalById(note.id)?.tags?.toSet())
            } finally { fresh.close() }
        } finally { source.close(); destination.close(); temporary.forEach { it.delete() } }
    }
}
