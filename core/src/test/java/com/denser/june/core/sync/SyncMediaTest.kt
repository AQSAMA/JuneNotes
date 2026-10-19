package com.denser.june.core.sync

import com.denser.june.core.domain.sync.MediaSyncMeta
import com.denser.june.core.sync.fixtures.SyncFixtures
import com.denser.june.core.sync.fixtures.SyncFixtures.T1
import com.denser.june.core.sync.fixtures.SyncFixtures.T2
import com.denser.june.core.sync.harness.BaseSyncTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SyncMediaTest : BaseSyncTest() {

    @Test fun `late byte edit cannot be advertised as uploaded by a size-only manifest`() = runTest {
        val file = harness.givenMediaFileOnDisk("legacy-metadata.jpg", byteArrayOf(1))
        val note = harness.givenSyncedJournal("legacy-metadata", images = listOf(file.path))
        harness.givenCloudMedia(note.id, file.name, file.readBytes())
        harness.cloud.manifest = harness.cloud.manifest!!.copy(mediaMetadata = mapOf(file.name to MediaSyncMeta(1, "")))
        harness.givenLocalOnlyJournal("unrelated-upload")
        harness.cloud.beforeJournalUpload = {
            com.denser.june.core.media.storage.MediaMutationLocks.withFile(file.path) {
                file.writeBytes(byteArrayOf(2))
                harness.repo.markMediaModified(file.path, T2)
            }
        }
        assertTrue(harness.sync().isSuccess)
        assertTrue(harness.repo.getPendingMediaChanges().containsKey(file.name))
        assertEquals("", harness.cloud.manifest!!.mediaMetadata.getValue(file.name).hash)
        harness.cloud.beforeJournalUpload = null
        assertTrue(harness.sync().isSuccess)
        assertEquals(1, harness.cloud.uploadMediaCallCount)
        val verify = File(harness.mediaDir, "verify-legacy")
        harness.cloud.downloadMedia(note.id, file.name, verify).getOrThrow()
        org.junit.Assert.assertArrayEquals(byteArrayOf(2), verify.readBytes())
        assertTrue(harness.repo.getPendingMediaChanges().isEmpty())
    }

    @Test fun `remote note text can win without discarding an explicit local attachment edit`() = runTest {
        val file = harness.givenMediaFileOnDisk("independent-edit.jpg", byteArrayOf(1))
        val note = harness.givenSyncedJournal("independent-edit", images = listOf(file.path))
        harness.givenCloudMedia(note.id, file.name, file.readBytes())
        file.writeBytes(byteArrayOf(2))
        harness.repo.markMediaModified(file.path, T2)
        harness.remoteJournalUpdated(note.id, T2 + 10000) { it.copy(title = "Remote title", updatedAt = T2 + 10000) }
        assertTrue(harness.sync().isSuccess)
        assertEquals("Remote title", harness.repo.getJournalById(note.id)!!.title)
        assertTrue(harness.cloud.uploadMediaCallCount > 0)
        org.junit.Assert.assertArrayEquals(byteArrayOf(2), file.readBytes())
        assertTrue(harness.repo.getPendingMediaChanges().isEmpty())
    }

    @Test fun `text edit downloads remote media while only explicitly changed attachment uploads`() = runTest {
        val a = harness.givenMediaFileOnDisk("a.jpg", byteArrayOf(1))
        val b = harness.givenMediaFileOnDisk("b.jpg", byteArrayOf(2))
        val note = harness.givenSyncedJournal("mixed", images = listOf(a.path, b.path))
        harness.givenCloudMedia(note.id, a.name, a.readBytes())
        harness.givenCloudMedia(note.id, b.name, b.readBytes())
        harness.repo.seed(note.copy(content = "Local text edit", updatedAt = T2))
        a.writeBytes(byteArrayOf(3))
        harness.repo.markMediaModified(a.path, T2)
        harness.givenCloudMedia(note.id, b.name, byteArrayOf(4))
        val analysis = harness.performAnalysis().getOrThrow()
        assertEquals(1, analysis.pendingMediaUploadsCount)
        assertEquals(1, analysis.pendingMediaDownloadsCount)
        assertTrue(harness.sync().isSuccess)
        org.junit.Assert.assertArrayEquals(byteArrayOf(3), a.readBytes())
        org.junit.Assert.assertArrayEquals(byteArrayOf(4), b.readBytes())
        assertEquals(1, harness.cloud.uploadMediaCallCount)
        assertEquals("Local text edit", harness.cloud.getJournal(note.id)!!.content)
        assertTrue(harness.repo.getPendingMediaChanges().isEmpty())
    }

    @Test fun `text-only edit never uploads stale cached attachments`() = runTest {
        val file = harness.givenMediaFileOnDisk("text-only.jpg", byteArrayOf(1))
        val note = harness.givenSyncedJournal("text-only", images = listOf(file.path))
        harness.givenCloudMedia(note.id, file.name, byteArrayOf(2))
        harness.repo.seed(note.copy(content = "Local text edit", updatedAt = T2))
        assertTrue(harness.sync().isSuccess)
        assertEquals(0, harness.cloud.uploadMediaCallCount)
        org.junit.Assert.assertArrayEquals(byteArrayOf(2), file.readBytes())
        assertEquals("Local text edit", harness.cloud.getJournal(note.id)!!.content)
    }

    @Test fun `manifest acknowledgment never clears a newer media generation`() = runTest {
        val file = harness.givenMediaFileOnDisk("generation.jpg", byteArrayOf(1))
        val note = harness.givenSyncedJournal("generation", images = listOf(file.path))
        harness.givenCloudMedia(note.id, file.name, file.readBytes())
        file.writeBytes(byteArrayOf(2))
        harness.repo.markMediaModified(file.path, T2)
        harness.cloud.beforeManifestWrite = {
            file.writeBytes(byteArrayOf(3))
            harness.repo.markMediaModified(file.path, T2 + 1)
        }
        assertTrue(harness.sync().isSuccess)
        assertTrue(harness.repo.getPendingMediaChanges().containsKey(file.name))
        assertTrue(harness.syncManager.status.value is com.denser.june.core.domain.sync.SyncStatus.Dirty)
        harness.cloud.beforeManifestWrite = null
        assertTrue(harness.sync().isSuccess)
        assertTrue(harness.repo.getPendingMediaChanges().isEmpty())
        val verify = File(harness.mediaDir, "verify")
        harness.cloud.downloadMedia(note.id, file.name, verify).getOrThrow()
        org.junit.Assert.assertArrayEquals(byteArrayOf(3), verify.readBytes())
    }

    @Test fun `remote download cannot overwrite a newly marked local edit`() = runTest {
        val file = harness.givenMediaFileOnDisk("during-download.jpg", byteArrayOf(1))
        val note = harness.givenSyncedJournal("during-download", images = listOf(file.path))
        harness.givenCloudMedia(note.id, file.name, byteArrayOf(2))
        harness.cloud.beforeMediaDownload = {
            com.denser.june.core.media.storage.MediaMutationLocks.withFile(file.path) {
                file.writeBytes(byteArrayOf(3))
                harness.repo.markMediaModified(file.path, T2)
            }
        }
        assertTrue(harness.sync().isFailure)
        org.junit.Assert.assertArrayEquals(byteArrayOf(3), file.readBytes())
        harness.cloud.beforeMediaDownload = null
        assertTrue(harness.sync().isSuccess)
        org.junit.Assert.assertArrayEquals(byteArrayOf(3), file.readBytes())
    }

    @Test fun `unchanged marked bytes clear only after manifest verification`() = runTest {
        val file = harness.givenMediaFileOnDisk("restored.jpg")
        val note = harness.givenSyncedJournal("restored", images = listOf(file.path))
        harness.givenCloudMedia(note.id, file.name, file.readBytes())
        harness.repo.markMediaModified(file.path, T2)
        assertTrue(harness.sync().isSuccess)
        assertEquals(0, harness.cloud.uploadMediaCallCount)
        assertTrue(harness.repo.getPendingMediaChanges().isEmpty())
    }

    @Test fun `media-only change remains dirty if manifest commit fails and retries`() = runTest {
        val file = harness.givenMediaFileOnDisk("media-only.jpg")
        val original = harness.givenSyncedJournal("media-only", images = listOf(file.path))
        harness.givenCloudMedia(original.id, file.name, file.readBytes())
        val previousManifest = harness.cloud.manifest
        file.writeBytes(byteArrayOf(8, 9))
        harness.repo.markMediaModified(file.path, T2)
        harness.cloud.failNextManifestWrite = true

        assertTrue(harness.sync().isFailure)
        org.junit.Assert.assertNull(harness.repo.getJournalById(original.id)!!.syncedAt)
        assertEquals(previousManifest, harness.cloud.manifest)
        assertEquals(0, harness.cloud.uploadJournalCallCount)
        assertTrue(harness.sync().isSuccess)
        assertNotNull(harness.repo.getJournalById(original.id)!!.syncedAt)
        assertEquals(com.denser.june.core.utils.FileUtils.computeSHA256(file),
            harness.cloud.manifest!!.mediaMetadata[file.name]!!.hash)
    }

    @Test fun `manifest success cannot acknowledge a concurrent same-timestamp note edit`() = runTest {
        val file = harness.givenMediaFileOnDisk("concurrent.jpg")
        val original = harness.givenSyncedJournal("concurrent", images = listOf(file.path))
        harness.givenCloudMedia(original.id, file.name, file.readBytes())
        file.writeBytes(byteArrayOf(9))
        harness.repo.markMediaModified(file.path, T2)
        harness.cloud.beforeManifestWrite = {
            val current = harness.repo.getJournalById(original.id)!!
            harness.repo.seed(current.copy(content = "Written during manifest commit"))
        }

        assertTrue(harness.sync().isSuccess)
        val current = harness.repo.getJournalById(original.id)!!
        org.junit.Assert.assertNull(current.syncedAt)
        assertEquals("Written during manifest commit", current.content)
        assertTrue(harness.syncManager.status.value is com.denser.june.core.domain.sync.SyncStatus.Dirty)
        harness.cloud.beforeManifestWrite = null
        assertTrue(harness.sync().isSuccess)
        assertEquals(current.content, harness.cloud.getJournal(original.id)!!.content)
    }

    @Test fun `failed shared attachment blocks its notes while unrelated text still uploads`() = runTest {
        val file = harness.givenMediaFileOnDisk("failed-shared.jpg")
        harness.givenLocalOnlyJournal("dependent", images = listOf(file.path))
        harness.givenLocalOnlyJournal("unrelated")
        harness.cloud.failNextUploadMedia = true

        assertTrue(harness.sync().isFailure)
        org.junit.Assert.assertNull(harness.cloud.getJournal("dependent"))
        assertNotNull(harness.cloud.getJournal("unrelated"))
        org.junit.Assert.assertNull(harness.repo.getJournalById("unrelated")!!.syncedAt)
        assertTrue(harness.sync().isSuccess)
        assertNotNull(harness.cloud.getJournal("dependent"))
    }

    @Test fun `failed attachment upload retains text and media dirtiness for retry`() = runTest {
        val file = harness.givenMediaFileOnDisk("edited.jpg", byteArrayOf(1, 2, 3))
        val original = harness.givenSyncedJournal("retry", images = listOf(file.path))
        harness.givenCloudMedia(original.id, file.name, file.readBytes())
        val editedBytes = byteArrayOf(8, 9)
        file.writeBytes(editedBytes)
        harness.repo.seed(original.copy(content = "New text", updatedAt = T2))
        harness.repo.markMediaModified(file.path, T2)
        harness.cloud.failNextUploadMedia = true

        assertTrue(harness.sync().isFailure)
        org.junit.Assert.assertNull(harness.repo.getJournalById(original.id)!!.syncedAt)
        assertEquals(original.content, harness.cloud.getJournal(original.id)!!.content)
        org.junit.Assert.assertArrayEquals(editedBytes, file.readBytes())

        assertTrue(harness.sync().isSuccess)
        assertEquals("New text", harness.cloud.getJournal(original.id)!!.content)
        org.junit.Assert.assertArrayEquals(editedBytes, file.readBytes())
        val downloaded = File(harness.mediaDir, "verify-download")
        harness.cloud.downloadMedia(original.id, file.name, downloaded).getOrThrow()
        org.junit.Assert.assertArrayEquals(editedBytes, downloaded.readBytes())
        assertEquals(T2, harness.repo.getJournalById(original.id)!!.syncedAt)
    }

    @Test
    fun `journal upload includes referenced media file`() = runTest {
        val mediaFile = harness.givenMediaFileOnDisk("photo1.jpg", byteArrayOf(1, 2, 3))
        harness.givenLocalOnlyJournal("j1", images = listOf(mediaFile.absolutePath), updatedAt = T1)

        val result = harness.sync()

        assertTrue(result.isSuccess)
        harness.assertMediaExistsOnCloud("photo1.jpg")
    }

    @Test
    fun `journal download fetches missing media file`() = runTest {
        harness.cloud.putMedia("j1", "photo1.jpg", byteArrayOf(1, 2, 3))
        harness.givenCloudOnlyJournal("j1", images = listOf("photo1.jpg"), modifiedAt = T1)

        val result = harness.sync()

        assertTrue(result.isSuccess)
        harness.assertMediaExistsOnDisk("photo1.jpg")
    }

    @Test
    fun `shared media already on cloud is not re-uploaded by media phase`() = runTest {
        val mediaFile = harness.givenMediaFileOnDisk("photo.jpg", byteArrayOf(1, 2, 3))
        val journal = SyncFixtures.syncedJournal("j1", updatedAt = T1, images = listOf(mediaFile.absolutePath))
        harness.repo.seed(journal)
        harness.cloud.putJournal(journal, modifiedAt = T1)
        harness.cloud.putMedia("j1", "photo.jpg", byteArrayOf(1, 2, 3))
        harness.cloud.manifest = SyncFixtures.manifest(
            totalJournals = 1,
            mediaMetadata = mapOf("photo.jpg" to MediaSyncMeta(size = 3L, hash = com.denser.june.core.utils.FileUtils.computeSHA256(mediaFile)))
        )

        val result = harness.sync()

        assertTrue(result.isSuccess)
        assertEquals("Media must not be uploaded when already in cloud with matching hash", 0, harness.cloud.uploadMediaCallCount)
    }

    @Test
    fun `media hash mismatch triggers re-upload`() = runTest {
        // Local file has different bytes (modified on disk) than cloud
        val localMedia = harness.givenMediaFileOnDisk("photo.jpg", byteArrayOf(9, 9, 9))
        val journal = SyncFixtures.syncedJournal("j1", updatedAt = T2, images = listOf(localMedia.absolutePath)).copy(syncedAt = null)
        harness.repo.seed(journal)
        harness.cloud.putJournal(journal, modifiedAt = T1)
        harness.cloud.putMedia("j1", "photo.jpg", byteArrayOf(1, 2, 3))
        harness.cloud.manifest = SyncFixtures.manifest(
            totalJournals = 1,
            mediaMetadata = mapOf("photo.jpg" to MediaSyncMeta(size = 3L, hash = "old_stale_hash"))
        )
        harness.repo.markMediaModified(localMedia.path, T2)

        val result = harness.sync()

        assertTrue(result.isSuccess)
        assertEquals("Media must be re-uploaded on hash mismatch", 1, harness.cloud.uploadMediaCallCount)
    }

    @Test
    fun `analysis pendingMediaUploads detects compressed or modified media`() = runTest {
        val compressedFile = harness.givenMediaFileOnDisk("photo.jpg", byteArrayOf(1, 2))
        val journal = SyncFixtures.syncedJournal("j1", updatedAt = T2, images = listOf(compressedFile.absolutePath)).copy(syncedAt = null)
        harness.repo.seed(journal)
        harness.cloud.putJournal(journal, modifiedAt = T1)
        harness.cloud.putMedia("j1", "photo.jpg", byteArrayOf(1, 2, 3, 4, 5))
        harness.cloud.manifest = SyncFixtures.manifest(
            totalJournals = 1,
            mediaMetadata = mapOf("photo.jpg" to MediaSyncMeta(size = 5L, hash = "old_uncompressed_hash"))
        )

        harness.repo.markMediaModified(compressedFile.path, T2)
        val analysis = harness.performAnalysis().getOrThrow()
        assertEquals("Analysis should report 1 modified media file to upload", 1, analysis.pendingMediaUploadsCount)

        val result = harness.sync()
        assertTrue(result.isSuccess)
        assertEquals("Sync must re-upload the modified media", 1, harness.cloud.uploadMediaCallCount)
    }

    @Test
    fun `analysis pendingMediaUploads matches actual sync uploads`() = runTest {
        val f1 = harness.givenMediaFileOnDisk("new1.jpg", byteArrayOf(1))
        val f2 = harness.givenMediaFileOnDisk("new2.jpg", byteArrayOf(2))
        val fAlready = harness.givenMediaFileOnDisk("existing.jpg", byteArrayOf(3))

        val j1 = SyncFixtures.syncedJournal("j1", updatedAt = T1, images = listOf(f1.absolutePath, fAlready.absolutePath))
        val j2 = SyncFixtures.syncedJournal("j2", updatedAt = T1, images = listOf(f2.absolutePath))
        harness.repo.seed(j1); harness.repo.seed(j2)
        harness.cloud.putJournal(j1, modifiedAt = T1); harness.cloud.putJournal(j2, modifiedAt = T1)
        harness.cloud.putMedia("j1", "existing.jpg", byteArrayOf(3))
        harness.cloud.manifest = SyncFixtures.manifest(
            totalJournals = 2,
            mediaMetadata = mapOf("existing.jpg" to MediaSyncMeta(size = 1L, hash = com.denser.june.core.utils.FileUtils.computeSHA256(fAlready)))
        )

        val analysis = harness.performAnalysis().getOrThrow()
        assertEquals("Analysis should report 2 media files to upload", 2, analysis.pendingMediaUploadsCount)

        harness.sync()
        assertEquals("Sync must upload exactly the 2 media files analysis predicted", 2, harness.cloud.uploadMediaCallCount)
    }

    @Test
    fun `orphaned cloud media not referenced by any journal shows zero pending downloads`() = runTest {
        harness.givenSyncedJournal("j1", updatedAt = T1)

        // Cloud has 5 "orphan" media files from another device
        repeat(5) { i -> harness.cloud.putMedia("other-device", "orphan$i.jpg", byteArrayOf(i.toByte())) }

        val analysis = harness.performAnalysis().getOrThrow()

        assertEquals("Orphaned cloud-only files must not count as pending downloads", 0, analysis.pendingMediaDownloadsCount)
        assertTrue("pendingMediaDownloadsList must be empty", analysis.pendingMediaDownloadsList.isEmpty())
    }

    @Test
    fun `missing media for synced journal is downloaded on next sync with canonical path`() = runTest {
        val j1 = SyncFixtures.syncedJournal("j1", syncedAt = T1, updatedAt = T1).copy(
            images = listOf(File(harness.mediaDir, "missing.jpg").absolutePath)
        )
        harness.repo.seed(j1)
        harness.cloud.putJournal(j1, modifiedAt = T1)
        harness.cloud.putMedia("j1", "missing.jpg", byteArrayOf(0xAA.toByte(), 0xBB.toByte()))
        harness.cloud.manifest = SyncFixtures.manifest(
            totalJournals = 1,
            mediaMetadata = mapOf("missing.jpg" to MediaSyncMeta(size = 2L, hash = ""))
        )

        val analysis = harness.performAnalysis().getOrThrow()
        assertEquals("Analysis must report 1 pending media download", 1, analysis.pendingMediaDownloadsCount)

        val result = harness.sync()
        assertTrue("Sync succeeds", result.isSuccess)
        harness.assertMediaExistsOnDisk("missing.jpg")
    }

    @Test
    fun `media with case difference in manifest is not re-uploaded`() = runTest {
        val mediaFile = harness.givenMediaFileOnDisk("Photo.JPG", byteArrayOf(1, 2, 3))
        val journal = SyncFixtures.syncedJournal("j1", updatedAt = T1, images = listOf(mediaFile.absolutePath))
        harness.repo.seed(journal)
        harness.cloud.putJournal(journal, modifiedAt = T1)
        harness.cloud.putMedia("j1", "photo.jpg", byteArrayOf(1, 2, 3))
        harness.cloud.manifest = SyncFixtures.manifest(
            totalJournals = 1,
            mediaMetadata = mapOf("photo.jpg" to MediaSyncMeta(size = 3L, hash = com.denser.june.core.utils.FileUtils.computeSHA256(mediaFile)))
        )

        val result = harness.sync()

        assertTrue(result.isSuccess)
        assertEquals(0, harness.cloud.uploadMediaCallCount)
    }

    @Test
    fun `batch of media files is uploaded successfully`() = runTest {
        val imagePaths = (1..15).map { i ->
            harness.givenMediaFileOnDisk("photo_$i.jpg", byteArrayOf(i.toByte())).absolutePath
        }
        val journal = SyncFixtures.newJournal("j_batch", updatedAt = T1, images = imagePaths)
        harness.repo.seed(journal)

        val result = harness.sync()

        assertTrue(result.isSuccess)
        assertEquals(15, harness.cloud.uploadMediaCallCount)
        assertEquals(15, harness.cloud.manifest?.totalMedia)
    }
    @Test fun `remote journal winner downloads changed bytes without uploading stale media`() = runTest {
        val oldFile = harness.givenMediaFileOnDisk("photo.jpg", byteArrayOf(1, 2, 3))
        val local = harness.givenSyncedJournal("j1", images = listOf(oldFile.absolutePath))
        val remote = local.copy(content = "New remote text", updatedAt = T2)
        val remoteBytes = byteArrayOf(7, 8, 9)
        harness.cloud.putJournal(remote, modifiedAt = T2)
        harness.cloud.putMedia("j1", "photo.jpg", remoteBytes)
        val hash = java.security.MessageDigest.getInstance("SHA-256").digest(remoteBytes).joinToString("") { "%02x".format(it) }
        harness.cloud.manifest = harness.cloud.manifest!!.copy(
            journalMetadata = mapOf("j1" to com.denser.june.core.domain.sync.JournalSyncMeta(2, remote.computeContentHash())),
            mediaMetadata = mapOf("photo.jpg" to MediaSyncMeta(3, hash)))
        val analysis = harness.performAnalysis().getOrThrow()
        assertEquals(0, analysis.pendingMediaUploadsCount)
        assertEquals(1, analysis.pendingMediaDownloadsCount)
        assertTrue(harness.sync().isSuccess)
        assertEquals(0, harness.cloud.uploadMediaCallCount)
        org.junit.Assert.assertArrayEquals(remoteBytes, oldFile.readBytes())
        assertEquals("New remote text", harness.repo.getJournalById("j1")!!.content)
        assertEquals(hash, harness.cloud.manifest!!.mediaMetadata["photo.jpg"]!!.hash)
    }

    @Test fun `clean journal downloads remote-only media changes even with matching journal content`() = runTest {
        val localFile = harness.givenMediaFileOnDisk("photo.jpg", byteArrayOf(1, 2, 3))
        harness.givenSyncedJournal("j1", images = listOf(localFile.absolutePath))
        val remoteBytes = byteArrayOf(4, 5)
        harness.cloud.putMedia("j1", "photo.jpg", remoteBytes)
        val hash = java.security.MessageDigest.getInstance("SHA-256").digest(remoteBytes).joinToString("") { "%02x".format(it) }
        harness.cloud.manifest = harness.cloud.manifest!!.copy(mediaMetadata = mapOf("photo.jpg" to MediaSyncMeta(2, hash)))
        assertTrue(harness.sync().isSuccess)
        assertEquals(0, harness.cloud.uploadMediaCallCount)
        org.junit.Assert.assertArrayEquals(remoteBytes, localFile.readBytes())
        assertEquals(hash, harness.cloud.manifest!!.mediaMetadata["photo.jpg"]!!.hash)
    }

    @Test fun `failed remote journal pull never uploads its stale local attachment`() = runTest {
        val localFile = harness.givenMediaFileOnDisk("photo.jpg", byteArrayOf(1, 2, 3))
        val local = harness.givenSyncedJournal("j1", images = listOf(localFile.absolutePath))
        val remote = local.copy(content = "Remote edit", updatedAt = T2)
        harness.cloud.putJournal(remote, modifiedAt = T2)
        harness.cloud.putMedia("j1", "photo.jpg", byteArrayOf(4, 5, 6))
        harness.cloud.manifest = harness.cloud.manifest!!.copy(
            journalMetadata = mapOf("j1" to com.denser.june.core.domain.sync.JournalSyncMeta(2, remote.computeContentHash())),
            mediaMetadata = mapOf("photo.jpg" to MediaSyncMeta(3, "new_remote_hash")))
        harness.cloud.failNextDownload = true
        assertTrue(harness.sync().isFailure)
        assertEquals(0, harness.cloud.uploadMediaCallCount)
        assertEquals("Remote edit", harness.cloud.getJournal("j1")!!.content)
    }

}
