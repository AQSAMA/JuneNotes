package com.denser.june.core.data.database.folder

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "folders", indices = [Index("parentId")])
data class FolderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val parentId: String?,
    val position: Long,
    val updatedAt: Long,
    val deleted: Boolean
)

// No foreign keys: cloud metadata can arrive before the corresponding journals.
@Entity(tableName = "folder_journals", indices = [Index("folderId")])
data class FolderJournalEntity(
    @PrimaryKey val journalId: String,
    val folderId: String?,
    val position: Long,
    val updatedAt: Long
)

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders") fun observeFolders(): Flow<List<FolderEntity>>
    @Query("SELECT * FROM folder_journals") fun observeJournals(): Flow<List<FolderJournalEntity>>
    @Query("SELECT * FROM folders") suspend fun folders(): List<FolderEntity>
    @Query("SELECT * FROM folder_journals") suspend fun journals(): List<FolderJournalEntity>
    @Upsert suspend fun upsertFolders(folders: List<FolderEntity>)
    @Upsert suspend fun upsertJournals(journals: List<FolderJournalEntity>)
}
