package com.denser.june.core.folder

import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.denser.june.core.data.backup.*
import com.denser.june.core.data.database.journal.JournalDatabase
import com.denser.june.core.data.repository.*
import com.denser.june.core.domain.markdown.MarkdownEngine
import com.denser.june.core.domain.model.Journal
import com.denser.june.core.domain.model.SongDetails
import com.denser.june.core.domain.preferences.SyncPreferences
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.lang.reflect.Proxy
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class BackupMediaCollisionTest {
    @Test fun everyBackupPathPreservesExistingAttachmentsAndRemapsImportedCollisions() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = Proxy.newProxyInstance(SyncPreferences::class.java.classLoader, arrayOf(SyncPreferences::class.java)) { _, method, _ ->
            error("Backup should not need preferences: ${method.name}")
        } as SyncPreferences
        val mediaDir = File(context.filesDir, "journal_media").apply { mkdirs() }
        val audioDir = File(context.filesDir, "song_media/library").apply { mkdirs() }
        val artDir = File(context.filesDir, "song_media/art").apply { mkdirs() }
        for (mode in listOf("markdown-import", "markdown-restore", "json-restore", "legacy-restore")) {
            val token = UUID.randomUUID().toString()
            val db = Room.inMemoryDatabaseBuilder(context, JournalDatabase::class.java).build()
            val zip = File(context.cacheDir, "$token.zip")
            val oldMedia = File(mediaDir, "media_$token.jpg").apply { writeText("OLD") }
            val oldAudio = File(audioDir, "$token.mp3").apply { writeText("OLD AUDIO") }
            val oldArt = File(artDir, "$token.jpg").apply { writeText("OLD ART") }
            try {
                val notes = JournalRepositoryImpl(db.journalDao(), preferences, context)
                val folders = FolderRepositoryImpl(db)
                val keep = Journal("keep-$token", "Keep", "Unrelated content", images = listOf(oldMedia.path),
                    songDetails = SongDetails("Keep song", "Artist", localPreviewPath = oldAudio.path, localThumbnailPath = oldArt.path),
                    createdAt = 1, updatedAt = 1, dateTime = 1)
                notes.insertJournal(keep)
                val incoming = Journal("new-$token", "Imported", "Backup text", images = listOf(oldMedia.name),
                    songDetails = if (mode == "json-restore") SongDetails("New song", "Artist", localPreviewPath = oldAudio.name, localThumbnailPath = oldArt.name) else null,
                    createdAt = 1, updatedAt = 1, dateTime = 1)
                ZipOutputStream(zip.outputStream()).use { out ->
                    fun entry(name: String, text: String) { out.putNextEntry(ZipEntry(name)); out.write(text.toByteArray()); out.closeEntry() }
                    when (mode) {
                        "json-restore" -> entry("journals/${incoming.id}.json", Json.encodeToString(Journal.serializer(), incoming))
                        "legacy-restore" -> entry("journal_data.json", Json.encodeToString(com.denser.june.core.domain.backup.ExportSchema.serializer(), com.denser.june.core.domain.backup.ExportSchema(journals = listOf(incoming))))
                        else -> entry("journals/${incoming.id}.md", MarkdownEngine.toMarkdown(incoming, "../media/"))
                    }
                    entry("media/${oldMedia.name}", "NEW")
                    if (mode == "json-restore") {
                        entry("song_media/library/${oldAudio.name}", "NEW AUDIO")
                        entry("song_media/art/${oldArt.name}", "NEW ART")
                    }
                }
                if (mode == "markdown-import") MarkdownImportImpl(notes, context, folders).importMarkdownZip(Uri.fromFile(zip)).getOrThrow()
                else RestoreImpl(notes, db.songLibraryDao(), context, folders).restoreData(Uri.fromFile(zip).toString()).getOrThrow()
                assertEquals(keep, notes.getJournalById(keep.id))
                assertEquals("OLD", oldMedia.readText())
                assertEquals("OLD AUDIO", oldAudio.readText())
                assertEquals("OLD ART", oldArt.readText())
                val imported = notes.getJournalById(incoming.id)!!
                assertNotEquals(oldMedia.path, imported.images.single())
                assertEquals("NEW", File(imported.images.single()).readText())
                if (mode == "json-restore") {
                    assertEquals("NEW AUDIO", File(imported.songDetails!!.localPreviewPath!!).readText())
                    assertEquals("NEW ART", File(imported.songDetails!!.localThumbnailPath!!).readText())
                }
            } finally {
                db.close(); zip.delete()
                listOf(mediaDir, audioDir, artDir).forEach { dir -> dir.listFiles()?.filter { it.name.contains(token) }?.forEach { it.delete() } }
            }
        }
    }
}
