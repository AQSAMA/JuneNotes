package com.denser.june.core.folders

import com.denser.june.core.domain.folders.*
import com.denser.june.core.domain.model.Journal
import com.denser.june.core.domain.sync.SyncManifest
import com.denser.june.core.domain.sync.serialize
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class FolderTreeTest {
    private fun folder(id: String, parent: String? = null, time: Long = 1, deleted: Long? = null) =
        NoteFolder(id, id, parent, updatedAt = time, deletedAt = deleted)

    @Test fun `deep subtree can move to root and cannot move into itself`() {
        val tree = FolderTree(FolderSnapshot(folders = listOf(folder("a"), folder("b", "a"), folder("c", "b"), folder("d", "c"))))
        assertEquals(listOf("a", "b", "c", "d"), tree.path("d").map { it.id })
        assertTrue(tree.canMove("c", null))
        assertTrue(tree.canMove("c", "a"))
        assertFalse(tree.canMove("a", "d"))
        assertFalse(tree.canMove("c", "c"))
        assertEquals(setOf("c", "d"), tree.descendants("c"))
    }

    @Test fun `concurrent cycles repair identically regardless of input order`() {
        val folders = listOf(folder("a", "c"), folder("b", "a"), folder("c", "b"), folder("d", "missing"))
        val first = FolderTree(FolderSnapshot(folders = folders))
        val second = FolderTree(FolderSnapshot(folders = folders.reversed()))
        assertNull(first.parentOf("a"))
        assertNull(first.parentOf("d"))
        folders.forEach { assertEquals(first.path(it.id), second.path(it.id)) }
        assertEquals(3, first.path("c").size)
    }

    @Test fun `deletion wins over stale device and orphaned notes remain accessible`() {
        val old = FolderSnapshot(folders = listOf(folder("a"), folder("b", "a")), placements = listOf(NotePlacement("note", "b", updatedAt = 1)))
        val removed = FolderSnapshot(folders = listOf(folder("a", time = 2, deleted = 2), folder("b", "a", time = 2, deleted = 2)))
        val merged = old.merge(removed)
        assertTrue(FolderTree(merged).folders.isEmpty())
        assertNull(FolderTree(merged).folderFor(merged.placements.single()))
        assertEquals(merged, merged.merge(old))
        assertEquals(merged, removed.merge(old))
    }

    @Test fun `merge is commutative deterministic and retains explicit unfiling`() {
        val a = FolderSnapshot(folders = listOf(folder("a")), placements = listOf(NotePlacement("note", "a", updatedAt = 2)))
        val b = FolderSnapshot(folders = listOf(folder("a", time = 3)), placements = listOf(NotePlacement("note", null, updatedAt = 3)))
        assertEquals(a.merge(b), b.merge(a))
        assertNull(a.merge(b).placements.single().folderId)
        val tied = b.copy(folders = listOf(b.folders.single().copy(name = "renamed")))
        assertEquals(b.merge(tied), tied.merge(b))
    }

    @Test fun `old manifest decodes and original reader ignores folder extension`() {
        val original = """{"lastSyncTime":1,"lastSyncDeviceId":"original","databaseVersion":5,"totalJournals":0}"""
        assertNull(Json.decodeFromString<SyncManifest>(original).folders)
        val state = FolderSnapshot(folders = listOf(folder("a")))
        val encoded = SyncManifest(1, "preview", 6, totalJournals = 0, folders = state).serialize()
        assertTrue(encoded.contains("\"folders\""))
        assertEquals(state, Json.decodeFromString<SyncManifest>(encoded).folders)
        val originalReader = Json { ignoreUnknownKeys = true }.decodeFromString<OriginalManifest>(encoded)
        assertEquals(4, originalReader.schemaVersion)
        assertEquals(0, originalReader.totalJournals)
    }

    @Test fun `folder extension does not alter original journal JSON or hashes`() {
        val note = Journal("id", "Title", "Content", createdAt = 1, updatedAt = null, dateTime = 1)
        val hash = note.computeContentHash()
        val json = Json.encodeToString(Journal.serializer(), note)
        assertFalse(json.contains("folder"))
        assertEquals(note, Json.decodeFromString<Journal>(json))
        FolderSnapshot(folders = listOf(folder("a")), placements = listOf(NotePlacement(note.id, "a", updatedAt = 2)))
        assertEquals(hash, note.computeContentHash())
    }

    @Test(expected = IllegalArgumentException::class) fun `future extension is rejected before import`() {
        FolderSnapshot(version = 2).validate()
    }
}

// Same relevant fields/defaults and tolerant decoder used by the original provider.
@Serializable
private data class OriginalManifest(
    val lastSyncTime: Long, val lastSyncDeviceId: String, val databaseVersion: Int,
    val schemaVersion: Int = 4, val totalJournals: Int
)
