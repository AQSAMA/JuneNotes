package com.denser.june.core.domain.folders

import kotlinx.coroutines.flow.Flow

interface FolderRepository {
    fun observe(): Flow<FolderSnapshot>
    fun observeDirty(): Flow<Boolean>
    suspend fun snapshot(): FolderSnapshot
    suspend fun merge(snapshot: FolderSnapshot)
    suspend fun markSynced(snapshot: FolderSnapshot)
    suspend fun create(name: String, parentId: String?): String
    suspend fun rename(id: String, name: String)
    suspend fun moveFolder(id: String, parentId: String?, beforeId: String? = null)
    suspend fun moveNote(journalId: String, folderId: String?, beforeId: String? = null)
    suspend fun remove(id: String)
}
