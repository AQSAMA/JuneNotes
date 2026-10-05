package com.denser.june.core.folder

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.denser.june.core.data.database.DatabaseMigrations
import com.denser.june.core.data.database.journal.JournalDatabase
import com.denser.june.core.data.mappers.asEntity
import com.denser.june.core.data.repository.FolderRepositoryImpl
import com.denser.june.core.domain.model.Journal
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FolderDatabaseTest {
    @get:Rule val migrations = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(), JournalDatabase::class.java,
        emptyList(), FrameworkSQLiteOpenHelperFactory()
    )

    @Test fun migrationKeepsOriginalNotesAndValidatesNewTables() {
        migrations.createDatabase("folder-migration", 5).apply {
            execSQL("INSERT INTO journals (id,title,content,images,tags,createdAt,dateTime,isBookmarked,isArchived,isDraft) VALUES ('original','Original note','Text','[]','[]',1,1,0,0,0)")
            close()
        }
        migrations.runMigrationsAndValidate("folder-migration", 6, true, DatabaseMigrations.MIGRATION_5_6).use { db ->
            db.query("SELECT title FROM journals WHERE id = 'original'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Original note", cursor.getString(0))
            }
            db.query("SELECT COUNT(*) FROM folders").use { cursor -> cursor.moveToFirst(); assertEquals(0, cursor.getInt(0)) }
        }
    }

    @Test fun moveReorderDeleteAndTrashRestoreKeepNotesIntact() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, JournalDatabase::class.java).build()
        try {
            val repo = FolderRepositoryImpl(db)
            val top = repo.create("Top", null)
            val middle = repo.create("Middle", top)
            val deep = repo.create("Deep", middle)
            val other = repo.create("Other", null)
            val note = Journal("note", "Original", "Content", tags = listOf("@Person", "#Topic", "Space"), createdAt = 1, updatedAt = 1, dateTime = 1, isDraft = false)
            db.journalDao().insertJournal(note.asEntity())
            repo.moveJournal(note.id, deep)
            repo.moveFolder(deep, null, top)
            assertNull(repo.snapshot().folders.first { it.id == deep }.parentId)
            assertEquals(listOf(deep, top, other), repo.snapshot().folders.filter { it.parentId == null && !it.deleted }.sortedBy { it.position }.map { it.id })
            repo.moveFolder(deep, middle)
            try { repo.moveFolder(top, deep); fail("Cycle should be rejected") } catch (_: IllegalArgumentException) { }
            repo.delete(middle)
            assertEquals(top, repo.snapshot().folders.first { it.id == deep }.parentId)
            assertEquals(deep, repo.snapshot().journals.single().folderId)
            repo.delete(deep)
            assertEquals(top, repo.snapshot().journals.single().folderId)
            assertEquals(note.asEntity(), db.journalDao().getJournalById(note.id))
            db.journalDao().softDeleteJournal(note.id, 50)
            assertEquals(top, repo.snapshot().journals.single().folderId)
            db.journalDao().updateJournal(note.asEntity())
            assertEquals(note.asEntity(), db.journalDao().getJournalById(note.id))
            repo.moveJournal(note.id, null)
            assertNull(repo.snapshot().journals.single().folderId)
        } finally { db.close() }
    }
}
