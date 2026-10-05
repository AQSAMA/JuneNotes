package com.denser.june.core.data.database.folders

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "note_folders")
data class FolderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val parentId: String?,
    val position: Long,
    val updatedAt: Long,
    val deletedAt: Long?
)

// No journal FK: June uses INSERT OR REPLACE for restore/sync, which must not erase placements.
@Entity(tableName = "note_placements")
data class PlacementEntity(
    @PrimaryKey val journalId: String,
    val folderId: String?,
    val position: Long,
    val updatedAt: Long
)

@Entity(tableName = "folder_sync_state")
data class FolderSyncState(@PrimaryKey val id: Int = 0, val snapshot: String)

@Dao
interface FolderDao {
    @Query("SELECT * FROM note_folders ORDER BY id")
    fun observeFolders(): Flow<List<FolderEntity>>
    @Query("SELECT * FROM note_placements ORDER BY journalId")
    fun observePlacements(): Flow<List<PlacementEntity>>
    @Query("SELECT snapshot FROM folder_sync_state WHERE id = 0")
    fun observeSynced(): Flow<String?>
    @Query("SELECT * FROM note_folders ORDER BY id")
    suspend fun folders(): List<FolderEntity>
    @Query("SELECT * FROM note_placements ORDER BY journalId")
    suspend fun placements(): List<PlacementEntity>
    @Upsert suspend fun putFolders(folders: List<FolderEntity>)
    @Upsert suspend fun putPlacements(placements: List<PlacementEntity>)
    @Upsert suspend fun putSynced(state: FolderSyncState)
}
