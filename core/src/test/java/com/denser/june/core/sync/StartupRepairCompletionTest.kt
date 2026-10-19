package com.denser.june.core.sync

import com.denser.june.core.domain.repository.JournalRepository
import com.denser.june.core.domain.sync.SyncManager
import com.denser.june.core.sync.fakes.*
import com.denser.june.core.sync.fixtures.SyncFixtures
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class StartupRepairCompletionTest {
    @get:Rule val files = TemporaryFolder()

    private fun manager(repository: JournalRepository, prefs: FakeSyncPreferences, scope: CoroutineScope) = SyncManager(
        repository, prefs, emptyMap(), files.root, FakeSyncScheduler(), scope, FakeSongLibraryDao(),
        File(files.root, "songs"), FakeFolderRepository()
    )

    @Test fun cleanupBarrierWaitsForTheRepairedDatabasePaths() = runBlocking<Unit> {
        val source = files.newFile("media_111_media_333.jpg").apply { writeText("original bytes") }
        val repo = FakeJournalRepository().apply { seed(SyncFixtures.newJournal("repair", images = listOf(source.path))) }
        val copyingFinished = CompletableDeferred<Unit>()
        val allowCommit = CompletableDeferred<Unit>()
        val blocked = object : JournalRepository by repo {
            override suspend fun repairMediaPaths(id: String, expected: List<String>, repaired: List<String>, modifiedAt: Long): Boolean {
                copyingFinished.complete(Unit)
                allowCommit.await()
                return repo.repairMediaPaths(id, expected, repaired, modifiedAt)
            }
        }
        val prefs = FakeSyncPreferences().apply { setLastCompletedDataRepairVersion(5) }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val sync = manager(blocked, prefs, scope)
            withTimeout(10000) { copyingFinished.await() }
            val cleanupMayStart = async { sync.awaitStartupRepair() }
            yield()
            assertFalse(cleanupMayStart.isCompleted)
            assertEquals(listOf(source.path), repo.getJournalById("repair")!!.images)
            allowCommit.complete(Unit)
            assertTrue(withTimeout(10000) { cleanupMayStart.await() })
            val repaired = repo.getJournalById("repair")!!.images.single()
            assertNotEquals(source.path, repaired)
            assertEquals("original bytes", File(repaired).readText())
            assertEquals(SyncManager.CURRENT_DATA_REPAIR_VERSION, prefs.lastCompletedDataRepairVersion)
        } finally { scope.cancel() }
    }

    @Test fun failedStartupRepairDoesNotAuthorizeCleanupOrAcknowledgeCompletion() = runBlocking<Unit> {
        val source = files.newFile("media_111_media_333.jpg").apply { writeText("original bytes") }
        val repo = FakeJournalRepository().apply { seed(SyncFixtures.newJournal("retry", images = listOf(source.path))) }
        val failing = object : JournalRepository by repo {
            override suspend fun repairMediaPaths(id: String, expected: List<String>, repaired: List<String>, modifiedAt: Long): Boolean =
                throw IOException("Database unavailable")
        }
        val prefs = FakeSyncPreferences().apply { setLastCompletedDataRepairVersion(5) }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            assertFalse(withTimeout(10000) { manager(failing, prefs, scope).awaitStartupRepair() })
            assertEquals(5, prefs.lastCompletedDataRepairVersion)
            assertEquals(listOf(source.path), repo.getJournalById("retry")!!.images)
            assertEquals("original bytes", source.readText())
        } finally { scope.cancel() }
    }
}
