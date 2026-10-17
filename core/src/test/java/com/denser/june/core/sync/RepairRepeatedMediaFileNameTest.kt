package com.denser.june.core.sync

import com.denser.june.core.domain.repository.JournalRepository
import com.denser.june.core.domain.sync.SyncManager
import com.denser.june.core.domain.sync.repairRepeatedMediaPath
import com.denser.june.core.domain.sync.repairRepeatedMediaPaths
import com.denser.june.core.sync.fakes.FakeJournalRepository
import com.denser.june.core.sync.fixtures.SyncFixtures
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class RepairRepeatedMediaFileNameTest {
    @get:Rule val temp = TemporaryFolder()
    private fun file(name: String, content: String = "image bytes") = File(temp.root, name).apply { writeText(content) }

    @Test fun repeatedNameIsCopiedAndVerifiedWithoutRemovingTheSource() {
        val source = file("media_111_media_222_photo.jpg")
        val destination = File(temp.root, "media_222_photo.jpg")
        assertEquals(destination.absolutePath, repairRepeatedMediaPath(source.path, temp.root))
        assertTrue(source.isFile)
        assertArrayEquals(source.readBytes(), destination.readBytes())
    }

    @Test fun multiplyRepeatedNameUsesTheLastPrefix() {
        val source = file("media_111_media_222_media_333.mp4")
        assertEquals(File(temp.root, "media_333.mp4").absolutePath, repairRepeatedMediaPath(source.path, temp.root))
        assertTrue(source.isFile)
    }

    @Test fun normalAndUnrelatedNamesStayUnchanged() {
        listOf("media_123.jpg", "my_special_media_file.jpg").forEach { name ->
            val source = file(name)
            assertEquals(source.path, repairRepeatedMediaPath(source.path, temp.root) { _, _ -> fail("No copy needed") })
        }
    }

    @Test fun distinctAttachmentsWithTheSameCleanNameNeverReplaceEachOther() {
        val first = file("media_111_media_333.jpg", "AAAA")
        val second = file("media_222_media_333.jpg", "BBBB")
        val destination = File(temp.root, "media_333.jpg")
        assertEquals(destination.absolutePath, repairRepeatedMediaPath(first.path, temp.root))
        assertEquals(second.path, repairRepeatedMediaPath(second.path, temp.root))
        assertEquals("AAAA", destination.readText())
        assertEquals("AAAA", first.readText())
        assertEquals("BBBB", second.readText())
    }

    @Test fun anExistingDestinationIsReusedOnlyAfterContentVerification() {
        val source = file("media_111_media_333.jpg")
        val destination = file("media_333.jpg")
        assertEquals(destination.path, repairRepeatedMediaPath(source.path, temp.root) { _, _ -> fail("Do not overwrite") })
        assertTrue(source.exists())
    }

    @Test fun missingSourceCannotBeRedirectedToAnUnverifiableExistingDestination() {
        val source = File(temp.root, "media_111_media_333.jpg")
        file("media_333.jpg")
        assertEquals(source.path, repairRepeatedMediaPath(source.path, temp.root))
    }

    @Test fun aFailedOrIncompleteCopyLeavesTheOriginalPathAndBytesIntact() {
        val source = file("media_111_media_333.jpg")
        val result = repairRepeatedMediaPath(source.path, temp.root) { _, target ->
            target.writeText("partial")
            throw IOException("Disk full")
        }
        assertEquals(source.path, result)
        assertEquals("image bytes", source.readText())
    }

    @Test fun aSuccessfulCopyCallStillRequiresVerifiedDestinationBytes() {
        val source = file("media_111_media_333.jpg", "AAAA")
        assertEquals(source.path, repairRepeatedMediaPath(source.path, temp.root) { _, target -> target.writeText("BBBB") })
        assertEquals("AAAA", source.readText())
    }

    @Test fun directoriesAndEmptySourcesAreNotTreatedAsVerifiedMedia() {
        val directory = File(temp.root, "media_111_media_333.jpg").apply { mkdir() }
        val empty = file("media_444_media_555.jpg", "")
        assertEquals(directory.path, repairRepeatedMediaPath(directory.path, temp.root))
        assertEquals(empty.path, repairRepeatedMediaPath(empty.path, temp.root))
    }

    @Test fun sharedAttachmentsAndTrashedNotesRetainTheirContentAndRepairIsIdempotent() = runTest {
        val source = file("media_111_media_333.jpg", "AAAA")
        val collision = file("media_222_media_333.jpg", "BBBB")
        val repo = FakeJournalRepository()
        val live = SyncFixtures.syncedJournal("live", images = listOf(source.path, collision.path)).copy(tags = listOf("#Keep"))
        val deleted = SyncFixtures.syncedJournal("deleted", images = listOf(source.path)).copy(deletedAt = 1L)
        repo.seed(live); repo.seed(deleted)
        assertEquals(2, repairRepeatedMediaPaths(repo, temp.root))
        val destination = File(temp.root, "media_333.jpg").path
        val repaired = repo.getJournalById(live.id)!!
        assertEquals(listOf(destination, collision.path), repaired.images)
        assertEquals(live.tags, repaired.tags)
        assertEquals(live.content, repaired.content)
        assertTrue(repaired.updatedAt!! > live.syncedAt!! + SyncManager.SYNC_THRESHOLD_MS)
        assertEquals(listOf(destination), repo.getJournalById(deleted.id)!!.images)
        assertEquals(deleted.deletedAt, repo.getJournalById(deleted.id)!!.deletedAt)
        assertEquals(0, repairRepeatedMediaPaths(repo, temp.root))
        assertEquals("AAAA", source.readText())
        assertEquals("BBBB", collision.readText())
    }

    @Test fun databaseFailureLeavesTheOriginalAttachmentAvailableForRetry() = runTest {
        val source = file("media_111_media_333.jpg")
        val repo = FakeJournalRepository()
        val note = SyncFixtures.newJournal("retry", images = listOf(source.path))
        repo.seed(note)
        val failing = object : JournalRepository by repo {
            override suspend fun repairMediaPaths(id: String, expected: List<String>, repaired: List<String>, modifiedAt: Long): Boolean =
                throw IOException("Database unavailable")
        }
        try { repairRepeatedMediaPaths(failing, temp.root); fail("Expected database failure") }
        catch (_: IOException) { }
        assertEquals(note, repo.getJournalById(note.id))
        assertTrue(source.isFile)
        assertEquals(1, repairRepeatedMediaPaths(repo, temp.root))
        assertTrue(File(repo.getJournalById(note.id)!!.images.single()).isFile)
    }

    @Test fun repairRetainsEditsMadeWhileAttachmentBytesWereBeingChecked() = runTest {
        val source = file("media_111_media_333.jpg")
        val repo = FakeJournalRepository()
        val note = SyncFixtures.newJournal("edited", images = listOf(source.path))
        repo.seed(note)
        val editing = object : JournalRepository by repo {
            override suspend fun repairMediaPaths(id: String, expected: List<String>, repaired: List<String>, modifiedAt: Long): Boolean {
                repo.seed(repo.getJournalById(id)!!.copy(title = "Edited during repair", tags = listOf("#New")))
                return repo.repairMediaPaths(id, expected, repaired, modifiedAt)
            }
        }
        assertEquals(1, repairRepeatedMediaPaths(editing, temp.root))
        assertEquals("Edited during repair", repo.getJournalById(note.id)!!.title)
        assertEquals(listOf("#New"), repo.getJournalById(note.id)!!.tags)
    }

    @Test fun repairDoesNotReinsertAnAttachmentRemovedDuringFileWork() = runTest {
        val source = file("media_111_media_333.jpg")
        val repo = FakeJournalRepository()
        val note = SyncFixtures.newJournal("removed", images = listOf(source.path))
        repo.seed(note)
        val editing = object : JournalRepository by repo {
            override suspend fun repairMediaPaths(id: String, expected: List<String>, repaired: List<String>, modifiedAt: Long): Boolean {
                repo.seed(repo.getJournalById(id)!!.copy(images = emptyList()))
                return repo.repairMediaPaths(id, expected, repaired, modifiedAt)
            }
        }
        assertEquals(0, repairRepeatedMediaPaths(editing, temp.root))
        assertTrue(repo.getJournalById(note.id)!!.images.isEmpty())
        assertTrue(source.isFile)
    }
}
