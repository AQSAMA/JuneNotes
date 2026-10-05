package com.denser.june.core.domain.folder

import kotlinx.coroutines.flow.Flow

interface FolderRepository {
    fun observe(): Flow<FolderSnapshot>
    fun observePendingSync(): Flow<Boolean>
    suspend fun hasPendingSync(): Boolean
    suspend fun markSynced(snapshot: FolderSnapshot)
    suspend fun snapshot(): FolderSnapshot
    suspend fun merge(snapshot: FolderSnapshot)
    suspend fun create(name: String, parentId: String?): String
    suspend fun rename(id: String, name: String)
    suspend fun moveFolder(id: String, parentId: String?, beforeId: String? = null)
    suspend fun moveJournal(id: String, folderId: String?, beforeId: String? = null)
    /** Remove the container, keeping its notes and children in its parent. */
    suspend fun delete(id: String)
}
