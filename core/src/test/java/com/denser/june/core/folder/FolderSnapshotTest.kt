package com.denser.june.core.folder

import com.denser.june.core.domain.folder.*
import com.denser.june.core.domain.model.Journal
import com.denser.june.core.domain.sync.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class FolderSnapshotTest {
    private val tree = FolderSnapshot(folders = listOf(Folder("a", "Work"), Folder("b", "Project", "a"), Folder("c", "Deep", "b")))

    @Test fun moveFromThreeLevelsToRootAndBack() {
        assertTrue(tree.canMove("c", null))
        assertTrue(tree.canMove("c", "a"))
        assertFalse(tree.canMove("a", "c"))
        assertFalse(tree.canMove("a", "a"))
        assertFalse(tree.canMove("c", "missing"))
        val lifted = tree.copy(folders = tree.folders.map { if (it.id == "c") it.copy(parentId = null) else it })
        assertTrue(lifted.canMove("a", "c"))
    }

    @Test fun concurrentOppositeMovesCannotLeaveCycle() {
        val left = FolderSnapshot(folders = listOf(Folder("a", "A", "b", updatedAt = 10), Folder("b", "B", updatedAt = 1)))
        val right = FolderSnapshot(folders = listOf(Folder("a", "A", updatedAt = 1), Folder("b", "B", "a", updatedAt = 10)))
        val merged = left.merge(right)
        assertEquals(merged, right.merge(left))
        assertEquals(merged, merged.normalized())
        assertNull(merged.folders.first { it.id == "a" }.parentId)
        assertTrue(merged.canMove("b", null))
    }

    @Test fun deletionAndNoteRemovalSurviveStaleCloudSnapshot() {
        val stale = tree.copy(journals = listOf(FolderJournal("note", "c", updatedAt = 2)))
        val deleted = FolderSnapshot(folders = listOf(Folder("c", "Deep", "b", updatedAt = 20, deleted = true)), journals = listOf(FolderJournal("note", null, updatedAt = 20)))
        val merged = stale.merge(deleted).merge(stale)
        assertTrue(merged.folders.first { it.id == "c" }.deleted)
        assertNull(merged.journals.single().folderId)
    }

    @Test fun malformedParentsAreRepairedAndDeepTreesAreIterative() {
        val folders = (0..2000).map { Folder("$it", "Folder $it", if (it == 0) null else "${it - 1}") }
        val deep = FolderSnapshot(folders = folders)
        assertFalse(deep.canMove("0", "2000"))
        assertTrue(deep.canMove("2000", null))
        val repaired = FolderSnapshot(folders = listOf(Folder("a", "A", "missing")), journals = listOf(FolderJournal("n", "missing"))).normalized()
        assertNull(repaired.folders.single().parentId)
        assertNull(repaired.journals.single().folderId)
    }

    @Test fun sidecarRoundTripPreservesUnicodeAndOrdering() {
        val snapshot = tree.copy(folders = tree.folders + Folder("d", "أدوية 💊", "c", 42, 100), journals = listOf(FolderJournal("note", "d", 3, 100)))
        assertEquals(snapshot.normalized(), FolderBackupCodec.read(FolderBackupCodec.encode(snapshot).byteInputStream()))
    }

    @Test(expected = IllegalArgumentException::class) fun duplicateIdsRejected() {
        FolderBackupCodec.decode(FolderBackupCodec.encode(tree.copy(folders = tree.folders + tree.folders.first())))
    }

    @Test(expected = IllegalArgumentException::class) fun unknownFolderVersionRejected() {
        FolderBackupCodec.decode("{\"version\":2}")
    }

    @Test fun originalManifestAndJournalFormatsRemainReadable() {
        val manifest = SyncManifest(100, "device", 5, totalJournals = 1, folderData = tree)
        val json = Json { ignoreUnknownKeys = true }
        val encoded = manifest.serialize()
        val old = json.decodeFromString(OriginalManifest.serializer(), encoded)
        assertEquals(1, old.totalJournals)
        assertEquals(5, old.databaseVersion)
        val oldEncoded = json.encodeToString(OriginalManifest.serializer(), old)
        assertNull(json.decodeFromString(SyncManifest.serializer(), oldEncoded).folderData)
        val note = Journal("n", "Title", "Text", tags = listOf("@Person", "#Topic", "Space"), createdAt = 1, updatedAt = 2, dateTime = 1)
        val fields = json.parseToJsonElement(note.serialize()).jsonObject
        assertFalse(fields.containsKey("folderId"))
        assertEquals(note, note.serialize().deserializeJournal())
    }
}

@Serializable
private data class OriginalManifest(val lastSyncTime: Long, val lastSyncDeviceId: String, val databaseVersion: Int, val totalJournals: Int)
