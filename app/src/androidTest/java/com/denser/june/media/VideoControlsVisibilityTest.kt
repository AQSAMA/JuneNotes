package com.denser.june.media

import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import coil.ImageLoader
import com.denser.june.presentation.components.lightbox.LightboxCompressionState
import com.denser.june.presentation.components.lightbox.LightboxMediaPager
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class VideoControlsVisibilityTest {
    @get:Rule val ui = createComposeRule()

    @Test fun playAndMuteKeepAlreadyVisibleControlsVisible() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "controls-${UUID.randomUUID()}.mp4").apply { writeBytes(byteArrayOf(1)) }
        var visible by mutableStateOf(true)
        try {
            ui.setContent {
                val loader = remember { ImageLoader.Builder(context).build() }
                DisposableEffect(loader) { onDispose { loader.shutdown() } }
                val compression = remember { LightboxCompressionState(file.path, context, 80, 1080) { false } }
                MaterialTheme {
                    LightboxMediaPager(
                        pagerState = rememberPagerState { 1 }, mediaPaths = listOf(file.path),
                        context = context, imageLoader = loader, compressionState = compression,
                        showUI = visible, isInfoVisible = false, isVideoZoomed = false,
                        isDarkTheme = false, refreshTrigger = 0,
                        onUIVisibilityChange = { visible = it }, onShowInfo = {}, onHideInfo = {},
                        onVideoZoomChanged = {}
                    )
                }
            }
            ui.onNodeWithContentDescription("Unmute").performClick()
            ui.onNodeWithContentDescription("Mute").assertIsDisplayed()
            ui.runOnIdle { assertTrue(visible) }
            ui.onNodeWithContentDescription("Play").performClick()
            ui.onNodeWithContentDescription("Mute").assertIsDisplayed()
            ui.runOnIdle { assertTrue(visible) }
        } finally { file.delete() }
    }
}
