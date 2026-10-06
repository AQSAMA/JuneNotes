package com.denser.june.core.sync.fakes

import com.denser.june.core.domain.folder.*
import kotlinx.coroutines.flow.*

class FakeFolderRepository : FolderRepository {
    val data = MutableStateFlow(FolderSnapshot())
    private val acknowledged = MutableStateFlow(FolderSnapshot())
    override fun observePendingSync() = combine(data, acknowledged) { snapshot, ack -> snapshot.normalized() != ack }
    override suspend fun hasPendingSync() = data.value.normalized() != acknowledged.value
    override suspend fun markSynced(snapshot: FolderSnapshot) { acknowledged.value = snapshot.normalized() }
    override fun observe() = data
    override suspend fun snapshot() = data.value
    override suspend fun merge(snapshot: FolderSnapshot) { data.value = data.value.merge(snapshot) }
    override suspend fun create(name: String, parentId: String?): String = error("Not needed by sync tests")
    override suspend fun rename(id: String, name: String) = error("Not needed by sync tests")
    override suspend fun moveFolder(id: String, parentId: String?, beforeId: String?) = error("Not needed by sync tests")
    override suspend fun moveJournal(id: String, folderId: String?, beforeId: String?) = error("Not needed by sync tests")
    override suspend fun delete(id: String) = error("Not needed by sync tests")
}
