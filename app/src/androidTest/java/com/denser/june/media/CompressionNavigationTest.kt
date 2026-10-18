package com.denser.june.media

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.denser.june.presentation.components.lightbox.CompressionBackHandler
import com.denser.june.presentation.components.lightbox.LightboxCompressionSliderCard
import com.denser.june.presentation.components.lightbox.LightboxCompressionState
import com.denser.june.core.media.compress.model.CompressionPreset
import androidx.compose.ui.geometry.Offset
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CompressionNavigationTest {
    @get:Rule val ui = createComposeRule()

    @Test fun repeatedBackCannotCancelOrLeaveACompressionSave() {
        var saving by mutableStateOf(true)
        var mode by mutableStateOf(true)
        var cancellations = 0
        var exits = 0
        lateinit var dispatcher: OnBackPressedDispatcher
        ui.setContent {
            dispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            BackHandler { exits++ }
            CompressionBackHandler(saving, mode, false, { cancellations++; mode = false }, {})
        }
        repeat(2) { ui.runOnIdle { dispatcher.onBackPressed() } }
        ui.runOnIdle {
            assertEquals(0, cancellations)
            assertEquals(0, exits)
            assertTrue(mode)
            saving = false
        }
        ui.waitForIdle()
        ui.runOnIdle { dispatcher.onBackPressed() }
        ui.runOnIdle { assertEquals(1, cancellations); assertEquals(0, exits) }
        ui.waitForIdle()
        ui.runOnIdle { dispatcher.onBackPressed() }
        ui.runOnIdle { assertEquals(1, exits) }
    }
    @Test fun loadedQualityPreferencesUpdateDefaultsWithoutResettingAnActiveChoice() {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val low = com.denser.june.core.media.compress.model.CompressionPreset.LOW
        val medium = com.denser.june.core.media.compress.model.CompressionPreset.MEDIUM
        val high = com.denser.june.core.media.compress.model.CompressionPreset.HIGH
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val photo = com.denser.june.presentation.components.lightbox.LightboxCompressionState("photo.jpg", context, 80, 1080) { false }
            photo.updateMediaPresets(null, setOf(low, medium, high))
            photo.startCompression()
            assertEquals(medium, photo.selectedPreset)
            photo.updateQualityPreferences(70, 1080)
            assertEquals(low, photo.selectedPreset)
            photo.selectPreset(high)
            photo.updateQualityPreferences(80, 1080)
            assertEquals(high, photo.selectedPreset)
            val explicit = com.denser.june.presentation.components.lightbox.LightboxCompressionState("explicit.jpg", context, 80, 1080) { false }
            explicit.updateMediaPresets(null, setOf(low, medium, high))
            explicit.startCompression()
            explicit.selectPreset(medium) // Choosing the displayed default is still an explicit choice.
            explicit.updateQualityPreferences(70, 1080)
            assertEquals(medium, explicit.selectedPreset)
            val video = com.denser.june.presentation.components.lightbox.LightboxCompressionState("video.mp4", context, 80, 1080) { false }
            video.updateMediaPresets(null, setOf(low, medium, high))
            video.updateQualityPreferences(80, 480)
            assertEquals(low, video.selectedPreset)
            video.updateQualityPreferences(80, 720)
            assertEquals(medium, video.selectedPreset)
        }
    }

    @Test fun retainedSaveDisablesTheOrdinaryOverflowMenuUntilCompletion() {
        var saving by mutableStateOf(true)
        var deletes = 0
        ui.setContent {
            androidx.compose.material3.MaterialTheme {
                com.denser.june.presentation.components.lightbox.LightboxTopBar(
                    isCompressMode = false, isInfoVisible = false, isDarkTheme = false,
                    isGeneratingPreview = false, isCompressing = saving,
                    selectedPreset = null, appliedPreset = null, availablePresets = emptySet(),
                    currentPath = "photo.jpg", exifInfo = null, dateTime = 1, parentJournal = null,
                    currentPreview = null, hasUncompressedOriginal = false, uncompressedOriginalBytes = null,
                    onBackOrCancel = {}, onSaveCompression = {}, onOpenInfo = {}, onStartCompression = {},
                    onDeleteMedia = { deletes++ }
                )
            }
        }
        ui.onNodeWithContentDescription("More options").assertIsNotEnabled()
        ui.runOnIdle { saving = false }
        ui.onNodeWithContentDescription("More options").performClick()
        ui.onNodeWithText(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.getString(com.denser.june.core.R.string.delete)).performClick()
        ui.runOnIdle { assertEquals(1, deletes) }
    }

    @Test fun tappingTheDisplayedSliderStepPreservesThatChoiceWhenPreferencesArrive() {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        lateinit var state: LightboxCompressionState
        ui.setContent {
            state = remember {
                LightboxCompressionState("photo.jpg", context, 80, 1080) { false }.apply {
                    updateMediaPresets(null, setOf(CompressionPreset.LOW, CompressionPreset.MEDIUM, CompressionPreset.HIGH))
                    startCompression()
                }
            }
            androidx.compose.material3.MaterialTheme {
                LightboxCompressionSliderCard(state.selectedPreset!!, false, state::selectPreset)
            }
        }
        ui.onNode(hasSetProgressAction()).performTouchInput {
            click(Offset(width / 3f, center.y))
        }
        ui.runOnIdle {
            state.updateQualityPreferences(70, 1080)
            assertEquals(CompressionPreset.MEDIUM, state.selectedPreset)
        }
    }

}
