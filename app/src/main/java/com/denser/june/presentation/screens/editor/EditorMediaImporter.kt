package com.denser.june.presentation.screens.editor

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** The caller supplies a ViewModel scope, so configuration changes do not cancel imports. */
internal fun CoroutineScope.importEditorMedia(
    uri: String,
    persist: suspend () -> String?,
    compress: suspend (String) -> Unit,
    onAction: (EditorAction) -> Unit,
    onFailure: (Exception) -> Unit = {}
): Job {
    onAction(EditorAction.AddImage(uri))
    onAction(EditorAction.SetMediaProcessing(uri, true))
    return launch {
        var persistedPath: String? = null
        try {
            persistedPath = persist() ?: throw java.io.IOException("Media persistence returned no file")
            persistedPath?.let { path ->
                onAction(EditorAction.ReplaceImage(uri, path))
                compress(path)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            onFailure(error)
        } finally {
            if (persistedPath == null) onAction(EditorAction.RemoveImage(uri))
            onAction(EditorAction.SetMediaProcessing(uri, false))
            persistedPath?.let { onAction(EditorAction.SetMediaProcessing(it, false)) }
        }
    }
}
