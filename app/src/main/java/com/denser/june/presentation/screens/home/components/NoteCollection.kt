package com.denser.june.presentation.screens.home.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.denser.june.core.R
import com.denser.june.core.domain.model.Journal
import com.denser.june.core.utils.toFullDate
import com.denser.june.presentation.components.JunePlaceholderPage
import com.denser.june.presentation.screens.editor.components.JournalMosaicCard
import com.denser.june.presentation.screens.editor.components.MediaOperations
import com.denser.june.presentation.utils.UiUtils

/** One lazy layout for both modes, retaining the visible note when the column count changes. */
@Composable
internal fun NoteCollection(
    notes: List<Journal>,
    grid: Boolean,
    loading: Boolean,
    modifier: Modifier = Modifier,
    state: LazyStaggeredGridState = rememberLazyStaggeredGridState(),
    bottomPadding: androidx.compose.ui.unit.Dp = UiUtils.BOTTOM_BAR_PADDING,
    emptyContent: @Composable () -> Unit = {
        JunePlaceholderPage(
            modifier = Modifier.fillMaxWidth().heightIn(min = 320.dp),
            icon = R.drawable.edit_note_24px,
            title = stringResource(R.string.notes_empty_title),
            subtitle = stringResource(R.string.notes_empty_desc)
        )
    },
    noteContent: @Composable (Int, Journal) -> Unit
) {
    // Large accessibility fonts receive fewer, wider columns rather than cramped text.
    val minWidth = 156.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)
    LazyVerticalStaggeredGrid(
        columns = if (grid) StaggeredGridCells.Adaptive(minWidth) else StaggeredGridCells.Fixed(1),
        state = state,
        modifier = modifier.fillMaxSize().testTag(if (grid) "notes-grid" else "notes-list"),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = bottomPadding),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalItemSpacing = 10.dp
    ) {
        if (loading) {
            item(key = "loading", span = StaggeredGridItemSpan.FullLine) {
                JunePlaceholderPage(Modifier.fillMaxWidth().heightIn(min = 320.dp), isLoading = true)
            }
        } else if (notes.isEmpty()) {
            item(key = "empty", span = StaggeredGridItemSpan.FullLine) { emptyContent() }
        } else {
            itemsIndexed(notes, key = { _, note -> note.id }, contentType = { _, _ -> "note" }) { index, note ->
                noteContent(index, note)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun NotePreviewCard(
    journal: Journal,
    previewLines: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onBookmark: () -> Unit,
    modifier: Modifier = Modifier,
    trailingAction: (@Composable () -> Unit)? = null
) {
    val shape = RoundedCornerShape(18.dp)
    val body = remember(journal.content) { journal.content.trim().take(6000) }
    val title = journal.title.trim()
    val emoji = journal.emoji
    val song = journal.songDetails
    Card(
        modifier = modifier.fillMaxWidth().testTag("note-card-${journal.id}")
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, if (journal.isBookmarked) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
    ) {
        // There is deliberately no media box for a text-only note.
        if (journal.images.isNotEmpty()) {
            JournalMosaicCard(
                mediaList = listOf(journal.images.last()),
                enablePlayback = false,
                modifier = Modifier.fillMaxWidth().aspectRatio(1.65f).testTag("note-media-${journal.id}"),
                operations = remember { MediaOperations(onMediaClick = null) },
                roundedCornerShape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
            )
        }
        Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 14.dp)) {
            if (!emoji.isNullOrBlank()) {
                Text(emoji, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(6.dp))
            }
            if (title.isNotEmpty() || body.isEmpty()) {
                Text(title.ifEmpty { stringResource(R.string.untitled) },
                    style = MaterialTheme.typography.titleMedium.copy(textDirection = TextDirection.Content),
                    fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                if (body.isNotEmpty()) Spacer(Modifier.height(8.dp))
            }
            if (body.isNotEmpty()) {
                Text(body, modifier = Modifier.testTag("note-body-${journal.id}"),
                    style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content),
                    maxLines = previewLines.coerceIn(4, 14), overflow = TextOverflow.Ellipsis)
            }
            if (song != null) {
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.music_note_24px), null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(song.title, style = MaterialTheme.typography.labelMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(journal.dateTime.toFullDate(), modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (journal.isDraft) Icon(painterResource(R.drawable.edit_24px), stringResource(R.string.draft),
                Modifier.size(14.dp), tint = MaterialTheme.colorScheme.tertiary)
            IconButton(onClick = onBookmark) {
                Icon(painterResource(if (journal.isBookmarked) R.drawable.bookmark_added_24px_fill else R.drawable.bookmark_24px),
                    stringResource(R.string.bookmark), Modifier.size(20.dp),
                    tint = if (journal.isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            trailingAction?.invoke()
        }
    }
}
