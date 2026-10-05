package com.denser.june.core.folders

import com.denser.june.core.domain.folders.*
import com.denser.june.core.domain.sync.SyncManifest
import com.denser.june.core.sync.harness.SyncTestHarness
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import org.junit.*

class FolderSyncTest {
    private val harness = SyncTestHarness()
    private val folders = MemoryFolders()
    @Before fun setup() { harness.setUp(folders) }
    @After fun close() { harness.tearDown() }
    private fun state(id: String, deleted: Boolean = false) = FolderSnapshot(folders = listOf(
        NoteFolder(id, id, updatedAt = if (deleted) 2 else 1, deletedAt = if (deleted) 2 else null)
    ))

    @Test fun `folder-only changes upload and remote folders merge without uploading notes`() = runTest {
        folders.value.value = state("local")
        harness.cloud.manifest = SyncManifest(1, "remote", 5, totalJournals = 0, folders = state("remote"))
        Assert.assertTrue(harness.performAnalysis().getOrThrow().pendingFolderChanges)
        Assert.assertTrue(harness.sync().isSuccess)
        Assert.assertEquals(setOf("local", "remote"), folders.snapshot().folders.map { it.id }.toSet())
        Assert.assertEquals(folders.snapshot(), harness.cloud.manifest!!.folders)
        Assert.assertFalse(folders.observeDirty().first())
        Assert.assertEquals(0, harness.cloud.uploadJournalCallCount)
    }

    @Test fun `deleted remote folder does not resurrect and manifest failures retain dirty state`() = runTest {
        folders.value.value = state("same")
        harness.cloud.manifest = SyncManifest(1, "remote", 6, totalJournals = 0, folders = state("same", deleted = true))
        harness.cloud.failNextManifestWrite = true
        Assert.assertTrue(harness.sync().isFailure)
        Assert.assertTrue(folders.observeDirty().first())
        Assert.assertTrue(FolderTree(folders.snapshot()).folders.isEmpty())
        Assert.assertTrue(harness.sync().isSuccess)
        Assert.assertEquals(2L, harness.cloud.manifest!!.folders!!.folders.single().deletedAt)
    }

    @Test fun `failed manifest read cannot overwrite cloud folder data`() = runTest {
        folders.value.value = state("local")
        val remote = SyncManifest(1, "remote", 6, totalJournals = 0, folders = state("remote"))
        harness.cloud.manifest = remote
        harness.cloud.failNextManifestRead = true
        Assert.assertTrue(harness.sync().isFailure)
        Assert.assertEquals(remote, harness.cloud.manifest)
        Assert.assertTrue(folders.observeDirty().first())
    }

    private class MemoryFolders : FolderRepository {
        val value = MutableStateFlow(FolderSnapshot())
        private val synced = MutableStateFlow(FolderSnapshot())
        override fun observe(): Flow<FolderSnapshot> = value
        override fun observeDirty(): Flow<Boolean> = combine(value, synced) { a, b -> a != b }
        override suspend fun snapshot() = value.value
        override suspend fun merge(snapshot: FolderSnapshot) { value.value = value.value.merge(snapshot) }
        override suspend fun markSynced(snapshot: FolderSnapshot) { synced.value = snapshot }
        override suspend fun create(name: String, parentId: String?): String = error("Not used")
        override suspend fun rename(id: String, name: String): Unit = error("Not used")
        override suspend fun moveFolder(id: String, parentId: String?, beforeId: String?): Unit = error("Not used")
        override suspend fun moveNote(journalId: String, folderId: String?, beforeId: String?): Unit = error("Not used")
        override suspend fun remove(id: String): Unit = error("Not used")
    }
}
