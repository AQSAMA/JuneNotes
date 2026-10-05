package com.denser.june.core.folders

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import com.denser.june.core.data.backup.ExportImpl
import com.denser.june.core.data.backup.RestoreImpl
import com.denser.june.core.data.database.DatabaseMigrations
import com.denser.june.core.data.database.journal.JournalDatabase
import com.denser.june.core.data.repository.FolderRepositoryImpl
import com.denser.june.core.data.repository.JournalRepositoryImpl
import com.denser.june.core.domain.folders.*
import com.denser.june.core.domain.model.Journal
import com.denser.june.core.sync.fakes.FakeSyncPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class FolderStorageTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private lateinit var db: JournalDatabase
    private lateinit var folders: FolderRepositoryImpl
    private lateinit var notes: JournalRepositoryImpl

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(context, JournalDatabase::class.java).build()
        folders = FolderRepositoryImpl(db)
        notes = JournalRepositoryImpl(db.journalDao(), FakeSyncPreferences(), context, db, folders)
    }
    @After fun close() { db.close() }
    private fun note(id: String) = Journal(id, "Note $id", "Body", createdAt = 1, updatedAt = 1, dateTime = 1, isDraft = false)

    @Test fun `folder and note moves reorder and survive note replacement`() = runBlocking<Unit> {
        val a = folders.create("A", null)
        val b = folders.create("B", a)
        val c = folders.create("C", b)
        val d = folders.create("D", c)
        notes.insertJournalInFolder(note("one"), d)
        notes.insertJournalInFolder(note("two"), d)
        folders.moveNote("two", d, "one")
        folders.moveFolder(c, null, a)
        val state = folders.snapshot()
        Assert.assertEquals(listOf(c, a), FolderTree(state).children(null).map { it.id })
        Assert.assertEquals(listOf(c, d), FolderTree(state).path(d).map { it.id })
        Assert.assertEquals(listOf("two", "one"), state.placements.sortedBy { it.position }.map { it.journalId })
        notes.insertJournal(note("one").copy(title = "Edited by restore"))
        Assert.assertEquals(d, folders.snapshot().placements.first { it.journalId == "one" }.folderId)
        try { folders.moveFolder(c, d); Assert.fail("Cycle must be rejected") } catch (_: IllegalArgumentException) { }
    }

    @Test fun `new folders append after sibling deletion and retain requested ordering`() = runBlocking<Unit> {
        val a = folders.create("A", null)
        val b = folders.create("B", null)
        folders.remove(a)
        val c = folders.create("C", null)
        Assert.assertEquals(listOf(b, c), FolderTree(folders.snapshot()).children(null).map { it.id })
    }

    @Test fun `unfiled original notes can be reordered before any placement exists`() = runBlocking<Unit> {
        notes.insertJournal(note("one")); notes.insertJournal(note("two"))
        folders.moveNote("two", null, "one")
        Assert.assertEquals(listOf("two", "one"), folders.snapshot().placements.sortedBy { it.position }.map { it.journalId })
    }

    @Test fun `subtree deletion preserves notes and a removed editor destination falls back to root`() = runBlocking<Unit> {
        val a = folders.create("A", null)
        val b = folders.create("B", a)
        notes.insertJournalInFolder(note("one"), b)
        folders.remove(a)
        Assert.assertNotNull(notes.getJournalById("one"))
        Assert.assertTrue(FolderTree(folders.snapshot()).folders.isEmpty())
        Assert.assertNull(folders.snapshot().placements.single().folderId)
        notes.insertJournalInFolder(note("two"), b)
        Assert.assertNotNull(notes.getJournalById("two"))
        Assert.assertNull(FolderTree(folders.snapshot()).folderFor(folders.snapshot().placements.firstOrNull { it.journalId == "two" }))
    }

    @Test fun `restoring an older backup recovers a deleted folder without deleting newer notes`() = runBlocking<Unit> {
        val a = folders.create("A", null)
        notes.insertJournalInFolder(note("old"), a)
        val backup = folders.snapshot()
        folders.remove(a)
        notes.insertJournal(note("new"))
        folders.restore(backup)
        Assert.assertEquals("A", FolderTree(folders.snapshot()).folders[a]!!.name)
        Assert.assertEquals(a, folders.snapshot().placements.first { it.journalId == "old" }.folderId)
        Assert.assertNotNull(notes.getJournalById("new"))
        Assert.assertTrue(folders.observeDirty().first())
    }

    @Test fun `sync acknowledges only the uploaded snapshot and retains a concurrent edit`() = runBlocking<Unit> {
        val a = folders.create("A", null)
        Assert.assertTrue(folders.observeDirty().first())
        val uploaded = folders.snapshot()
        folders.rename(a, "Changed during upload")
        folders.markSynced(uploaded)
        Assert.assertTrue(folders.observeDirty().first())
        folders.markSynced(folders.snapshot())
        Assert.assertFalse(folders.observeDirty().first())
    }

    @Test fun `ZIP and Markdown backups round trip folders notes and media without changing original records`() = runBlocking<Unit> {
        val a = folders.create("Parent", null)
        val b = folders.create("Child", a)
        val media = File(context.filesDir, "journal_media/sample.jpg").apply { parentFile!!.mkdirs(); writeBytes(byteArrayOf(1, 2, 3)) }
        val originalNote = note("one").copy(images = listOf(media.absolutePath), tags = listOf("Space", "@Person", "#Topic"))
        notes.insertJournalInFolder(originalNote, b)
        val expected = folders.snapshot()
        val exporter = ExportImpl(notes, context, folders)
        for (backup in listOf(exporter.exportData(true, false).getOrThrow(), exporter.exportAsMarkdown(true).getOrThrow())) {
            val restoredDb = Room.inMemoryDatabaseBuilder(context, JournalDatabase::class.java).build()
            try {
                val restoredFolders = FolderRepositoryImpl(restoredDb)
                val restoredNotes = JournalRepositoryImpl(restoredDb.journalDao(), FakeSyncPreferences(), context, restoredDb, restoredFolders)
                RestoreImpl(restoredNotes, restoredDb.songLibraryDao(), context, restoredFolders, restoredDb)
                    .restoreData(backup.toURI().toString()).getOrThrow()
                Assert.assertEquals(expected, restoredFolders.snapshot())
                val restored = restoredNotes.getJournalById("one")!!
                Assert.assertEquals(originalNote.title, restored.title)
                Assert.assertEquals(originalNote.tags, restored.tags)
                Assert.assertArrayEquals(byteArrayOf(1, 2, 3), File(restored.images.single()).readBytes())
                ZipFile(backup).use { zip ->
                    Assert.assertNotNull(zip.getEntry("folders.json"))
                    zip.getEntry("journals/one.json")?.let { entry ->
                        val decodedByOriginal = Json.decodeFromString<Journal>(zip.getInputStream(entry).reader().readText())
                        Assert.assertEquals(originalNote.title, decodedByOriginal.title)
                    }
                }
            } finally { restoredDb.close() }
        }
    }

    @Test fun `original ZIP import and empty folder backup import both work`() = runBlocking<Unit> {
        val originalZip = File(context.cacheDir, "original.zip")
        ZipOutputStream(originalZip.outputStream()).use { out ->
            out.putNextEntry(ZipEntry("journals/one.json"))
            out.write(Json.encodeToString(Journal.serializer(), note("one")).toByteArray()); out.closeEntry()
        }
        val restore = RestoreImpl(notes, db.songLibraryDao(), context, folders, db)
        restore.restoreData(originalZip.toURI().toString()).getOrThrow()
        Assert.assertEquals(note("one"), notes.getJournalById("one"))
        val emptyZip = File(context.cacheDir, "empty-folders.zip")
        val snapshot = FolderSnapshot(folders = listOf(NoteFolder("empty", "Empty", updatedAt = 1)))
        ZipOutputStream(emptyZip.outputStream()).use { out ->
            out.putNextEntry(ZipEntry("folders.json"))
            out.write(Json.encodeToString(FolderSnapshot.serializer(), snapshot).toByteArray()); out.closeEntry()
        }
        restore.restoreData(emptyZip.toURI().toString()).getOrThrow()
        Assert.assertEquals(snapshot, folders.snapshot())
    }

    @Test fun `migration from original version 5 preserves notes and creates validated folder tables`() = runBlocking<Unit> {
        val name = "migration-test.db"
        context.deleteDatabase(name)
        val schemaPath = File(System.getProperty("june.schemaDir"), "com.denser.june.core.data.database.journal.JournalDatabase/5.json")
        val schema = Json.parseToJsonElement(schemaPath.readText()).jsonObject.getValue("database").jsonObject
        val file = context.getDatabasePath(name).apply { parentFile!!.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { sqlite ->
            schema.getValue("entities").jsonArray.forEach { value ->
                val entity = value.jsonObject
                val table = entity.getValue("tableName").jsonPrimitive.content
                sqlite.execSQL(entity.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                entity["indices"]?.jsonArray?.forEach { sqlite.execSQL(it.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table)) }
            }
            sqlite.execSQL("INSERT INTO journals (id, title, content, images, tags, createdAt, dateTime, isBookmarked, isArchived, isDraft) VALUES ('original', 'Preserved', 'Body', '[]', '[]', 1, 1, 0, 0, 0)")
            sqlite.version = 5
        }
        val migrated = Room.databaseBuilder(context, JournalDatabase::class.java, name).addMigrations(DatabaseMigrations.MIGRATION_5_6).build()
        try {
            Assert.assertEquals("Preserved", migrated.journalDao().getJournalById("original")!!.title)
            Assert.assertTrue(migrated.folderDao().folders().isEmpty())
            FolderRepositoryImpl(migrated).create("New folder", null)
        } finally { migrated.close(); context.deleteDatabase(name) }
    }
}
