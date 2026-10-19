package com.denser.june.core.media.storage

import java.io.File
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Bounded lock stripes serialize byte replacement plus its marker with sync publication. */
object MediaMutationLocks {
    private val locks = Array(64) { Mutex() }
    suspend fun <T> withFile(path: String, block: suspend () -> T): T {
        val index = (File(path).name.lowercase().hashCode() and Int.MAX_VALUE) % locks.size
        return locks[index].withLock { block() }
    }
}
