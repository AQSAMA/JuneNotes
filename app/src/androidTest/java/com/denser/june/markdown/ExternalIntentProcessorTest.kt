package com.denser.june.markdown

import android.content.ClipData
import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ProviderInfo
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.denser.june.core.domain.markdown.MarkdownInput
import com.denser.june.core.domain.markdown.MarkdownTooLargeException
import com.denser.june.core.domain.repository.JournalRepository
import com.denser.june.presentation.utils.ExternalIntentProcessor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.UUID

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29) // ContentResolver.wrap is used only by this test fixture.
class ExternalIntentProcessorTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val repository get() = GlobalContext.get().get<JournalRepository>()
    private val uri = Uri.parse("content://june.markdown.test/source.md")
    private val ids = mutableListOf<String>()
    private lateinit var file: File
    private lateinit var processor: ExternalIntentProcessor
    private var streamMode = "readable"

    @Before fun prepare() {
        file = File(context.cacheDir, "${UUID.randomUUID()}.md")
        val provider = object : ContentProvider() {
            override fun onCreate() = true
            override fun getType(uri: Uri) = "text/markdown"
            override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
                               selectionArgs: Array<out String>?, sortOrder: String?): Cursor =
                MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME)).apply { addRow(arrayOf("source.md")) }
            override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor? = when (streamMode) {
                "null" -> null
                "missing" -> throw FileNotFoundException("Missing Markdown")
                "denied" -> throw SecurityException("Permission denied")
                "cancelled" -> throw CancellationException("Read cancelled")
                else -> AssetFileDescriptor(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY),
                    0, AssetFileDescriptor.UNKNOWN_LENGTH)
            }
            override fun insert(uri: Uri, values: ContentValues?): Uri? = null
            override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
            override fun update(uri: Uri, values: ContentValues?, selection: String?,
                                selectionArgs: Array<out String>?) = 0
        }
        provider.attachInfo(context, ProviderInfo().apply { authority = uri.authority })
        val resolver = ContentResolver.wrap(provider)
        processor = ExternalIntentProcessor(object : ContextWrapper(context) {
            override fun getContentResolver() = resolver
        }, repository)
    }

    @After fun cleanUp() = runBlocking {
        ids.forEach { repository.hardDeleteJournal(it) }
        file.delete()
        Unit
    }

    private fun share(text: String?, clipUri: Boolean = false) = Intent(Intent.ACTION_SEND).apply {
        type = "text/markdown"
        if (clipUri) clipData = ClipData.newRawUri("Markdown", uri)
        else putExtra(Intent.EXTRA_STREAM, uri)
        if (text != null) putExtra(Intent.EXTRA_TEXT, text)
    }

    private suspend fun assertSharedTextOpens(intent: Intent, expectedBody: String) {
        val route = processor.processIntent(intent).getOrThrow()!!
        ids.add(route.journalId)
        val journal = repository.getJournalById(route.journalId)!!
        assertEquals("Shared Note", journal.title)
        assertEquals(expectedBody, journal.content)
        assertTrue(journal.isDraft)
    }

    @Test fun unreadableOrNullExtraStreamFallsBackToSharedText() = runBlocking {
        for (mode in listOf("missing", "denied", "null")) {
            streamMode = mode
            val body = "Shared **$mode** ${UUID.randomUUID()}"
            assertSharedTextOpens(share(body), body)
        }
    }

    @Test fun unreadableOrNullClipDataUriFallsBackToSharedText() = runBlocking {
        for (mode in listOf("missing", "denied", "null")) {
            streamMode = mode
            val body = "Clip URI fallback ${UUID.randomUUID()}"
            assertSharedTextOpens(share(body, clipUri = true), body)
        }
    }

    @Test fun blankUriFallsBackToSharedText() = runBlocking {
        file.writeText("\uFEFF \n\t")
        val body = "Blank stream fallback ${UUID.randomUUID()}"
        assertSharedTextOpens(share(body), body)
    }

    @Test fun readableUriKeepsPrecedenceOverSharedText() = runBlocking {
        val title = "URI ${UUID.randomUUID()}"
        file.writeText("# $title\n\nURI body")
        // Even an oversized text alternative must not replace a usable URI.
        val route = processor.processIntent(share("x".repeat(MarkdownInput.MAX_BYTES + 1))).getOrThrow()!!
        ids.add(route.journalId)
        val journal = repository.getJournalById(route.journalId)!!
        assertEquals(title, journal.title)
        assertEquals("URI body", journal.content)
    }

    @Test fun uriFailureWithoutUsableSharedTextStillReturnsAnError() = runBlocking {
        for (mode in listOf("missing", "denied", "null", "readable")) {
            streamMode = mode
            file.writeText(" \n\t")
            for (text in listOf(null, " \n")) {
                val result = processor.processIntent(share(text))
                assertTrue("$mode with text $text", result.isFailure)
                assertTrue(result.exceptionOrNull() is IOException || result.exceptionOrNull() is SecurityException)
            }
        }
    }

    @Test fun fallbackStillEnforcesTheMarkdownSizeLimit() = runBlocking {
        file.writeText("x".repeat(MarkdownInput.MAX_BYTES + 1))
        val body = "Oversized URI fallback ${UUID.randomUUID()}"
        assertSharedTextOpens(share(body), body)
        streamMode = "missing"
        val result = processor.processIntent(share("x".repeat(MarkdownInput.MAX_BYTES + 1)))
        assertTrue(result.exceptionOrNull() is MarkdownTooLargeException)
    }

    @Test fun cancelledUriReadDoesNotProcessSharedText() {
        streamMode = "cancelled"
        val body = "Cancelled share ${UUID.randomUUID()}"
        try {
            runBlocking { processor.processIntent(share(body)) }
            fail("Cancellation must propagate")
        } catch (_: CancellationException) {
            assertNull(runBlocking { repository.findMatchingDraftId("Shared Note", body) })
        }
    }
}
