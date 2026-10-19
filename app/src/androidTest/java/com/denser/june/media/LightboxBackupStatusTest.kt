package com.denser.june.media

import androidx.compose.runtime.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.denser.june.core.domain.model.Journal
import com.denser.june.core.domain.preferences.SyncPreferences
import com.denser.june.core.domain.repository.JournalRepository
import com.denser.june.presentation.components.lightbox.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class LightboxBackupStatusTest {
    @get:Rule val ui = createComposeRule()

    @Test fun syncAcknowledgmentRefreshesAnOpenLightboxDespiteItsLocalCompressionMarker() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repo = GlobalContext.get().get<JournalRepository>()
        val prefs = object : SyncPreferences by GlobalContext.get().get<SyncPreferences>() {
            override fun getSyncEnabled() = flowOf(true)
            override fun getSelectedProvider() = flowOf("Test")
        }
        val id = UUID.randomUUID().toString()
        val file = File(context.cacheDir, "backup-status-$id.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val note = Journal(id, "Backup status", "", images = listOf(file.path), createdAt = 1, updatedAt = 2000, dateTime = 1, syncedAt = null, isDraft = false)
        val markers = setOf(file.path)
        var observed: LightboxMediaMetadataState? = null
        try {
            runBlocking { repo.insertJournal(note) }
            ui.setContent {
                val metadata = rememberLightboxMediaMetadataState(context, file.path, id, null, 0, markers, repo, prefs)
                SideEffect { observed = metadata }
            }
            ui.waitUntil(10000) { observed?.isPendingSync == true && observed?.parentJournal != null }
            assertFalse(observed!!.isMediaBackedUp)
            runBlocking { repo.updateSyncStatus(id, "$id.json", 3000) }
            ui.waitUntil(10000) { observed?.isMediaBackedUp == true && observed?.exifInfo?.isBackedUp == true }
            assertFalse(observed!!.isPendingSync)
            assertTrue(markers.contains(file.path))
        } finally { runBlocking { repo.hardDeleteJournal(id) }; file.delete() }
    }
}
