package com.denser.june.presentation.components.lightbox

import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal data class MediaCompressionSaveStatus(val busy: Boolean = false, val generation: Int = 0, val succeeded: Boolean = false, val handledGeneration: Int = 0)

class MediaCompressionSaveVM : ViewModel() {
    private val saves = mutableStateMapOf<String, MediaCompressionSaveStatus>()

    internal fun status(path: String?): MediaCompressionSaveStatus = path?.let { saves[it] } ?: MediaCompressionSaveStatus()

    internal fun acknowledge(path: String, generation: Int) {
        val current = status(path)
        if (!current.busy && current.generation == generation) {
            saves[path] = current.copy(handledGeneration = generation)
        }
    }

    internal fun startSave(
        path: String,
        compress: suspend () -> Boolean,
        markModified: suspend () -> Unit,
        cleanup: () -> Unit = {}
    ): Boolean {
        val before = status(path)
        if (before.busy) return false
        saves[path] = before.copy(busy = true)
        viewModelScope.launch {
            var succeeded = false
            try {
                succeeded = com.denser.june.core.media.storage.MediaMutationLocks.withFile(path) {
                    persistMediaCompression(compress, markModified, cleanup)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Keep the original and offer the same save again after a failed attempt.
            } finally {
                saves[path] = MediaCompressionSaveStatus(false, before.generation + 1, succeeded, before.handledGeneration)
            }
        }
        return true
    }
}
