package com.denser.june.presentation.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat
import com.denser.june.core.domain.logging.AppLogger
import com.denser.june.core.domain.markdown.MarkdownEngine
import com.denser.june.core.domain.markdown.MarkdownInput
import com.denser.june.core.domain.repository.JournalRepository
import com.denser.june.core.utils.FileUtils
import com.denser.june.presentation.navigation.Route
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

class ExternalIntentProcessor(
    private val context: Context,
    private val journalRepo: JournalRepository
) {
    companion object {
        private const val TAG = "ExternalIntentProcessor"
    }

    suspend fun processIntent(intent: Intent?): Result<Route.Editor?> = withContext(Dispatchers.IO) {
        try {
            if (intent == null || intent.action !in setOf(Intent.ACTION_VIEW, Intent.ACTION_EDIT, Intent.ACTION_SEND)) {
                return@withContext Result.success(null)
            }
            val uri: Uri? = intent.data
                ?: IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
            val content: String
            val displayName: String?
            var uriFailure: Exception? = null
            val uriContent = if (uri != null) {
                try {
                    context.contentResolver.openInputStream(uri)?.use(MarkdownInput::read)
                        ?.takeUnless { it.isBlank() }
                        ?: throw IOException("Cannot read Markdown file or file is empty")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    uriFailure = e
                    null
                }
            } else null
            if (uriContent != null) {
                displayName = uri?.let { FileUtils.getDisplayName(context, it) }
                content = uriContent
            } else {
                // A share can carry both a URI and text. Try the text before reporting a URI error.
                displayName = "Shared Note"
                val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
                    ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
                    ?: run {
                        uriFailure?.let { throw it }
                        return@withContext Result.success(null)
                    }
                if (text.isBlank()) uriFailure?.let { throw it }
                content = MarkdownInput.validateText(text)
            }
            if (content.isBlank()) throw IOException("Markdown file is empty")

            val journal = MarkdownEngine.fromMarkdown(content, displayName, isDraft = true)
            // Query only an ID: loading every journal can exhaust memory on a large library.
            val targetId = journalRepo.getJournalById(journal.id)?.id
                ?: journalRepo.findMatchingDraftId(journal.title, journal.content)
                ?: journalRepo.insertJournal(journal)
            Result.success(Route.Editor(journalId = targetId))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.e(AppLogger.Category.DATABASE, TAG, "Failed to open external Markdown", e)
            Result.failure(e)
        } catch (e: StackOverflowError) {
            // Recursive third-party parsers can fail on deeply nested input, even when it is small.
            Result.failure(IOException("Markdown structure is too deeply nested", e))
        }
    }
}
