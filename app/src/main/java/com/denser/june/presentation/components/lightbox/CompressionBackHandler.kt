package com.denser.june.presentation.components.lightbox

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable

@Composable
internal fun CompressionBackHandler(
    isCompressing: Boolean,
    isCompressMode: Boolean,
    isInfoVisible: Boolean,
    onCancel: () -> Unit,
    onHideInfo: () -> Unit
) {
    BackHandler(enabled = isCompressing || isCompressMode || isInfoVisible) {
        if (!isCompressing) {
            if (isCompressMode) onCancel() else if (isInfoVisible) onHideInfo()
        }
    }
}
