package com.denser.june.core.sync

import com.denser.june.core.domain.folder.*
import com.denser.june.core.domain.sync.SyncManifest
import com.denser.june.core.sync.harness.BaseSyncTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class SyncFolderTest : BaseSyncTest() {
    @Test fun folderOnlyBackupUploadsAndDownloadsThroughManifest() = runTest(harness.testDispatcher) {
        val local = Folder("a", "Local", updatedAt = 10)
        val remote = Folder("b", "Remote", updatedAt = 20)
        harness.folders.data.value = FolderSnapshot(folders = listOf(local))
        harness.cloud.manifest = SyncManifest(0, "other", 5, totalJournals = 0, folderData = FolderSnapshot(folders = listOf(remote)))
        harness.syncManager.sync().getOrThrow()
        val result = harness.folders.snapshot()
        assertEquals(setOf("a", "b"), result.folders.map { it.id }.toSet())
        assertEquals(result, harness.cloud.manifest!!.folderData)
    }

    @Test fun originalJuneManifestDoesNotEraseLocalFolders() = runTest(harness.testDispatcher) {
        harness.folders.data.value = FolderSnapshot(folders = listOf(Folder("a", "Local", updatedAt = 10)))
        harness.cloud.manifest = SyncManifest(0, "original", 5, schemaVersion = 4, totalJournals = 0)
        harness.syncManager.sync().getOrThrow()
        assertEquals("a", harness.cloud.manifest!!.folderData!!.folders.single().id)
        assertEquals(5, harness.cloud.manifest!!.schemaVersion)
    }
    @Test fun versionFourFolderManifestIsUpgradedWithoutLosingRemoteFolders() = runTest(harness.testDispatcher) {
        val remote = FolderSnapshot(folders = listOf(Folder("remote", "Remote", updatedAt = 20)))
        harness.cloud.manifest = SyncManifest(0, "older-fork", 6, schemaVersion = 4,
            totalJournals = 0, folderData = remote)
        harness.syncManager.sync().getOrThrow()
        assertEquals(remote, harness.folders.snapshot())
        assertEquals(remote, harness.cloud.manifest!!.folderData)
        assertEquals(5, harness.cloud.manifest!!.schemaVersion)
    }

    @Test fun manifestReadFailureNeverOverwritesRemoteFolders() = runTest(harness.testDispatcher) {
        val remote = SyncManifest(0, "other", 5, totalJournals = 0,
            folderData = FolderSnapshot(folders = listOf(Folder("remote", "Remote", updatedAt = 20))))
        harness.cloud.manifest = remote
        harness.cloud.failNextManifestRead = true
        assertTrue(harness.syncManager.sync().isFailure)
        assertEquals(remote, harness.cloud.manifest)
    }

    @Test fun failedManifestWriteKeepsFolderChangesPending() = runTest(harness.testDispatcher) {
        harness.folders.data.value = FolderSnapshot(folders = listOf(Folder("a", "Local", updatedAt = 10)))
        harness.cloud.failNextManifestWrite = true
        assertTrue(harness.syncManager.sync().isFailure)
        assertTrue(harness.folders.hasPendingSync())
        harness.syncManager.sync().getOrThrow()
        assertFalse(harness.folders.hasPendingSync())
    }
}
