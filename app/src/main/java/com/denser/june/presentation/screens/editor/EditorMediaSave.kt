package com.denser.june.presentation.screens.editor

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first

/** Imported files keep processing after their content URI becomes a local path. */
internal suspend fun StateFlow<EditorState>.awaitFinishedMedia(): EditorState =
    first { it.processingMediaPaths.isEmpty() }
