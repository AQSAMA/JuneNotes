package com.denser.june.presentation.components.lightbox

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** Once an in-place write starts, finish marking its current references even if the UI is disposed. */
internal suspend fun persistMediaCompression(
    compress: suspend () -> Boolean,
    markModified: suspend () -> Unit
): Boolean = withContext(NonCancellable) {
    compress().also { success -> if (success) markModified() }
}
