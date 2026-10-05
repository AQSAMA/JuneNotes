package com.denser.june.presentation.screens.home.folders

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.denser.june.core.R
import com.denser.june.core.domain.folder.*
import com.denser.june.core.domain.model.Journal
import com.denser.june.presentation.screens.home.components.JournalCard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

internal fun matchingFolderNotes(notes: List<Journal>, query: String): List<Journal> {
    val terms = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return if (terms.isEmpty()) notes else notes.filter { note ->
        terms.all { term -> note.title.contains(term, true) || note.content.contains(term, true) || note.tags.any { it.contains(term, true) } }
    }
}

/** Search and selection reuse June's original cards, including their media thumbnails. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExistingFolderNoteSheet(
    notes: List<Journal>,
    destination: String,
    is24Hour: Boolean,
    busy: Boolean,
    onDismiss: () -> Unit,
    onChoose: (Journal) -> Unit,
    error: String? = null
) {
    var query by rememberSaveable { mutableStateOf("") }
    val matching = remember(notes, query) { matchingFolderNotes(notes, query) }
    val list = rememberLazyListState()
    LaunchedEffect(query) { list.scrollToItem(0) }
    ModalBottomSheet(
        onDismissRequest = { if (!busy) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(Modifier.fillMaxHeight(0.88f).imePadding().padding(horizontal = 16.dp)) {
            SheetHeading(stringResource(R.string.folder_add_existing), destination, busy, onDismiss)
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(bottom = 8.dp))
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp)) }
            FolderSearchField(query, { query = it }, stringResource(R.string.folder_search_notes))
            Spacer(Modifier.height(12.dp))
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                state = list,
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (matching.isEmpty()) item {
                    Text(stringResource(if (query.isBlank()) R.string.folder_no_other_notes else R.string.no_matches_found),
                        Modifier.padding(vertical = 32.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(matching, key = { it.id }) { note ->
                    JournalCard(
                        note, is24Hour = is24Hour, showDate = true,
                        actionIcon = R.drawable.add_2_24px,
                        actionContentDescription = stringResource(R.string.folder_add_existing),
                        onActionClick = { if (!busy) onChoose(note) },
                        onJournalClick = { if (!busy) onChoose(note) }
                    )
                }
            }
        }
    }
}

/** Shared by every note menu and by folder move controls; placement is saved before dismissal. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderDestinationSheet(item: FolderDrag, onDismiss: () -> Unit) {
    val repository = koinInject<FolderRepository>()
    val snapshot by repository.observe().collectAsStateWithLifecycle(FolderSnapshot())
    var parent by rememberSaveable(item.id) { mutableStateOf<String?>(null) }
    var query by rememberSaveable(item.id) { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var createFolder by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val failureMessage = stringResource(R.string.folder_move_failed)
    val list = rememberLazyListState()
    LaunchedEffect(query, parent) { list.scrollToItem(0) }
    val live = remember(snapshot) { snapshot.folders.filterNot { it.deleted } }
    val byId = remember(live) { live.associateBy { it.id } }
    val current = byId[parent]
    val validParent = parent == null || current != null
    val folders = remember(snapshot, item, parent, query) {
        live.filter { folder ->
            (!item.folder || snapshot.canMove(item.id, folder.id)) &&
                if (query.isBlank()) folder.parentId == parent else folder.name.contains(query.trim(), true)
        }.sortedWith(compareBy({ it.position }, { it.id }))
    }
    LaunchedEffect(parent, validParent) { if (!validParent) { parent = null; query = "" } }
    fun open(id: String?) { parent = id; query = ""; error = null }
    ModalBottomSheet(
        onDismissRequest = { if (!busy) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(Modifier.fillMaxHeight(0.78f).imePadding().padding(horizontal = 16.dp)) {
            SheetHeading(stringResource(R.string.folder_move), "", busy, onDismiss)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (parent != null) {
                    IconButton(onClick = { open(null) }, enabled = !busy) {
                        Icon(painterResource(R.drawable.folder_open_24px), stringResource(R.string.folders))
                    }
                    IconButton(onClick = { open(current?.parentId) }, enabled = !busy) {
                        Icon(painterResource(R.drawable.arrow_back_24px), stringResource(R.string.back))
                    }
                }
                Text(current?.name ?: stringResource(R.string.folders), Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = { createFolder = true }, enabled = !busy) {
                    Icon(painterResource(R.drawable.create_new_folder_24px), stringResource(R.string.new_folder))
                }
            }
            FolderSearchField(query, { query = it }, stringResource(R.string.folder_search_folders))
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, contentPadding = PaddingValues(vertical = 8.dp)) {
                if (folders.isEmpty()) item {
                    Text(stringResource(if (query.isBlank()) R.string.folder_empty else R.string.no_matches_found),
                        Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(folders, key = { it.id }) { folder ->
                    ListItem(
                        headlineContent = { Text(folder.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        supportingContent = if (query.isNotBlank()) { { Text(byId[folder.parentId]?.name ?: stringResource(R.string.folders)) } } else null,
                        leadingContent = { Icon(painterResource(R.drawable.folder_open_24px), null, tint = MaterialTheme.colorScheme.primary) },
                        trailingContent = { Icon(painterResource(R.drawable.chevron_right_24px), null) },
                        modifier = Modifier.clickable(enabled = !busy) { open(folder.id) }
                    )
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp)) }
            Button(
                enabled = !busy && validParent && (!item.folder || snapshot.canMove(item.id, parent)),
                onClick = {
                    busy = true
                    scope.launch {
                        try {
                            if (item.folder) repository.moveFolder(item.id, parent) else repository.moveJournal(item.id, parent)
                            onDismiss()
                        } catch (e: CancellationException) { throw e }
                        catch (e: Exception) { error = e.message ?: failureMessage }
                        finally { busy = false }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Icon(painterResource(R.drawable.drive_folder_upload_24px), null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.folder_move_here))
            }
        }
    }
    if (createFolder) FolderNameDialog("", onDismiss = { createFolder = false }) { name ->
        createFolder = false
        busy = true
        scope.launch {
            try { open(repository.create(name, parent)) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: failureMessage }
            finally { busy = false }
        }
    }
}

@Composable
private fun SheetHeading(title: String, destination: String, busy: Boolean, onDismiss: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            if (destination.isNotEmpty()) Text(destination, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onDismiss, enabled = !busy) {
            Icon(painterResource(R.drawable.close_24px), stringResource(R.string.close))
        }
    }
}

@Composable
internal fun FolderSearchField(query: String, onQuery: (String) -> Unit, placeholder: String) {
    val focus = LocalFocusManager.current
    OutlinedTextField(
        value = query, onValueChange = onQuery, singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(painterResource(R.drawable.search_24px), null) },
        trailingIcon = if (query.isNotEmpty()) { {
            IconButton(onClick = { onQuery("") }) { Icon(painterResource(R.drawable.close_24px), stringResource(R.string.clear)) }
        } } else null,
        shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()
    )
}
