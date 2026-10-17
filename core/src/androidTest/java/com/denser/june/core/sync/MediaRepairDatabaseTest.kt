package com.denser.june.core.sync

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.denser.june.core.data.database.journal.JournalDatabase
import com.denser.june.core.data.mappers.asEntity
import com.denser.june.core.domain.model.Journal
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaRepairDatabaseTest {
    @Test fun attachmentRepairRetainsCurrentFieldsAndRejectsAChangedAttachmentList() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, JournalDatabase::class.java).build()
        try {
            val dao = db.journalDao()
            val current = Journal("repair", "Edited title", "Edited text", images = listOf("old.jpg"), tags = listOf("#New"),
                createdAt = 1, updatedAt = 500, dateTime = 1, syncedAt = 1000).asEntity()
            dao.insertJournal(current)
            assertTrue(dao.repairMediaPaths(current.id, listOf("old.jpg"), listOf("verified.jpg"), 2, 2000))
            val repaired = dao.getJournalById(current.id)!!
            assertEquals(current.copy(images = listOf("verified.jpg"), updatedAt = 3001), repaired)
            assertFalse(dao.repairMediaPaths(current.id, listOf("old.jpg"), listOf("stale.jpg"), 4000, 2000))
            assertEquals(repaired, dao.getJournalById(current.id))
            assertFalse(dao.repairMediaPaths("missing", emptyList(), listOf("new.jpg"), 4000, 2000))
        } finally { db.close() }
    }
    @Test fun compressionUpdatesOnlySyncFieldsForEveryCurrentReference() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, JournalDatabase::class.java).build()
        try {
            val dao = db.journalDao()
            val edited = Journal("edited", "Concurrent title", "Concurrent content", images = listOf("/current/media_shared.jpg", "other.jpg"),
                tags = listOf("#Current"), createdAt = 1, updatedAt = 5000, dateTime = 2, syncedAt = 6000,
                cloudId = "cloud", isBookmarked = true, isArchived = true).asEntity()
            val sharedDeleted = edited.copy(id = "shared", images = listOf("media_shared.jpg"), deletedAt = 3)
            val removed = edited.copy(id = "removed", images = listOf("other.jpg"))
            dao.insertJournal(edited)
            dao.insertJournal(sharedDeleted)
            dao.insertJournal(removed)
            dao.insertTag(com.denser.june.core.data.database.journal.TagEntity("#Current"))
            dao.insertJournalTagCrossRef(com.denser.june.core.data.database.journal.JournalTagCrossRef(edited.id, "#Current"))

            assertEquals(2, dao.markMediaModified("/old/device/media_shared.jpg", 1000))
            assertEquals(edited.copy(syncedAt = null), dao.getJournalById(edited.id))
            assertEquals(sharedDeleted.copy(syncedAt = null), dao.getJournalById(sharedDeleted.id))
            assertEquals(removed, dao.getJournalById(removed.id))
            assertEquals(listOf(edited.id), dao.getJournalsByTagNameSync("#Current").map { it.id })
            assertEquals(0, dao.markMediaModified("missing.jpg", 9000))
            assertEquals(2, dao.markMediaModified("media_shared.jpg", 9000))
            assertEquals(9000L, dao.getJournalById(edited.id)!!.updatedAt)
        } finally { db.close() }
    }

}
