package com.denser.june.core.sync

import com.denser.june.core.domain.folder.Folder
import com.denser.june.core.domain.folder.FolderSnapshot
import com.denser.june.core.domain.sync.SyncManifest
import com.denser.june.core.domain.sync.serialize
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test

class SyncManifestSchemaTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test fun versionFiveIsExplicitInCloudAndBackupJsonEvenWithoutFolders() {
        for (folders in listOf(null, FolderSnapshot(folders = listOf(Folder("a", "Work"))))) {
            val manifest = SyncManifest(100, "device", 6, totalJournals = 0, folderData = folders)
            for (encoded in listOf(manifest.serialize(), Json.Default.encodeToString(SyncManifest.serializer(), manifest))) {
                assertEquals(5, json.parseToJsonElement(encoded).jsonObject.getValue("schemaVersion").jsonPrimitive.int)
                assertEquals(manifest, json.decodeFromString(SyncManifest.serializer(), encoded))
                // Match the old reader's default and SyncManager version gate: it must see 5,
                // rather than defaulting to 4 after ignoring the unknown folderData field.
                val legacy = json.decodeFromString(LegacyManifest.serializer(), encoded)
                assertTrue(legacy.schemaVersion > 4)
            }
        }
    }

    @Test fun schemasOneThroughFourAndUnversionedManifestsRemainReadable() {
        val fields = "\"lastSyncTime\":100,\"lastSyncDeviceId\":\"old\",\"databaseVersion\":5,\"totalJournals\":1"
        for (version in 1..4) {
            val manifest = json.decodeFromString(SyncManifest.serializer(), "{$fields,\"schemaVersion\":$version}")
            assertEquals(version, manifest.schemaVersion)
            assertEquals(1, manifest.totalJournals)
            assertNull(manifest.folderData)
        }
        assertNull(json.decodeFromString(SyncManifest.serializer(), "{$fields}").folderData)
    }
}

@Serializable
private data class LegacyManifest(
    val lastSyncTime: Long,
    val lastSyncDeviceId: String,
    val databaseVersion: Int,
    val schemaVersion: Int = 4,
    val totalJournals: Int
)
