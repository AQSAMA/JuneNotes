package com.denser.june.media

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.denser.june.presentation.components.lightbox.CompressionBackHandler
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
}
