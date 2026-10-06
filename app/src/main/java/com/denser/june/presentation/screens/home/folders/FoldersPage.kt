package com.denser.june.presentation.screens.home.folders

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import kotlinx.coroutines.launch
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.denser.june.core.R
import com.denser.june.core.domain.folder.Folder
import com.denser.june.core.domain.model.Journal
import com.denser.june.core.domain.model.enums.TimeFormat
import com.denser.june.presentation.components.*
import com.denser.june.presentation.navigation.*
import com.denser.june.presentation.screens.home.components.*
import com.denser.june.presentation.utils.UiUtils
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FoldersPage(viewModel: FoldersVM, isSelected: Boolean) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val time by viewModel.preferences.timeFormat().collectAsStateWithLifecycle(TimeFormat.TWELVE_HOUR)
    val navigator = koinInject<AppNavigator>()
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) { viewModel.errors.collect { snack.showSnackbar(it) } }
    BackHandler(isSelected && state.currentId != null) { viewModel.back() }

    var nameDialog by remember { mutableStateOf(false) }
    var renameFolder by remember { mutableStateOf<Folder?>(null) }
    var deleteFolder by remember { mutableStateOf<Folder?>(null) }
    var optionsFolder by remember { mutableStateOf<Folder?>(null) }
    var optionsNote by remember { mutableStateOf<Journal?>(null) }
    var deleteNote by remember { mutableStateOf<Journal?>(null) }
    var exportNote by remember { mutableStateOf<Journal?>(null) }
    var moveItem by remember { mutableStateOf<FolderDrag?>(null) }
    var addExisting by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    val pathScroll = rememberScrollState()
    LaunchedEffect(state.currentId, pathScroll.maxValue) { pathScroll.animateScrollTo(pathScroll.maxValue) }
    var addingNote by remember { mutableStateOf(false) }
    var addingError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val autoTime by viewModel.preferences.isAutoTimeEnabled().collectAsStateWithLifecycle(false)
    val addedMessage = stringResource(R.string.folder_note_added)
    val failureMessage = stringResource(R.string.folder_move_failed)
    val is24Hour = time == TimeFormat.TWENTY_FOUR_HOUR
    ExportJournalBottomSheet(exportNote, onDismiss = { exportNote = null })

    fun accepts(item: FolderDrag, parent: String?): Boolean = if (item.folder) state.snapshot.canMove(item.id, parent) else true

    FolderDragHost(
        accepts = { false }, onDrop = {},
        modifier = Modifier.fillMaxSize(), onDragActive = { dragging = it }
    ) {
    Box(Modifier.fillMaxSize()) {
        Column {
            // Keep the root heading uncluttered and the list stationary when a drag begins.
            AnimatedVisibility(state.currentId != null) { Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Breadcrumb(null, stringResource(R.string.folders), accepts = { accepts(it, null) },
                    onOpen = { viewModel.open(null) }, onDrop = { viewModel.move(it, null) })
                Row(Modifier.weight(1f).horizontalScroll(pathScroll), verticalAlignment = Alignment.CenterVertically) {
                state.path.forEach { folder ->
                    Icon(painterResource(R.drawable.chevron_right_24px), null, Modifier.size(16.dp))
                    Breadcrumb(folder.id, folder.name, accepts = { accepts(it, folder.id) },
                        onOpen = { viewModel.open(folder.id) }, onDrop = { viewModel.move(it, folder.id) })
                }
                }
            } }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(state.path.lastOrNull()?.name ?: stringResource(R.string.folders), style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = { nameDialog = true }) { Icon(painterResource(R.drawable.create_new_folder_24px), stringResource(R.string.new_folder)) }
                IconButton(onClick = { addingError = null; addExisting = true }) { Icon(painterResource(R.drawable.edit_note_24px), stringResource(R.string.folder_add_existing)) }
            }
            AnimatedContent(
                targetState = state.currentId,
                transitionSpec = {
                    if (dragging) fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                    else (fadeIn(tween(180)) + slideInVertically { it / 12 }) togetherWith (fadeOut(tween(100)) + slideOutVertically { -it / 12 })
                },
                label = "folder_navigation"
            ) { displayedId ->
                // Use the target ID for each animated pane, avoiding duplicate note IDs during transitions.
                val pane = remember(state, displayedId) { state.copy(currentId = displayedId) }
                val listState = rememberLazyListState()
                Box(Modifier.fillMaxSize()) {
                FolderDropSurface(
                    accepts = { displayedId == state.currentId && accepts(it, displayedId) },
                    onDrop = { viewModel.move(it, displayedId) },
                    modifier = Modifier.fillMaxSize(), priority = -10, enabled = displayedId == state.currentId
                ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = UiUtils.BOTTOM_BAR_PADDING),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (state.loading) {
                        item { JunePlaceholderPage(Modifier.fillParentMaxHeight(0.7f), isLoading = true) }
                    } else {
                        itemsIndexed(pane.children, key = { _, folder -> "folder_${folder.id}" }) { index, folder ->
                            val item = FolderDrag(folder.id, true, folder.name)
                            FolderDragItem(item, Modifier.animateItem().testTag("folder-row-${folder.id}")) {
                                Box {
                                    FolderDropSurface(
                                        accepts = { displayedId == state.currentId && accepts(it, folder.id) },
                                        onDrop = { viewModel.move(it, folder.id) },
                                        enabled = displayedId == state.currentId,
                                        onHoverOpen = { viewModel.open(folder.id) }
                                    ) {
                                        FolderRow(folder, state.folderCounts[folder.id] ?: 0, state.noteCounts[folder.id] ?: 0,
                                            onOpen = { viewModel.open(folder.id) }, onOptions = { optionsFolder = folder },
                                            onMove = { moveItem = item })
                                    }
                                    if (dragging && displayedId == state.currentId) {
                                        ReorderSlot(Modifier.align(Alignment.TopCenter).testTag("folder-before-${folder.id}"),
                                            accepts = { it.folder && it.id != folder.id && accepts(it, displayedId) },
                                            onDrop = { viewModel.move(it, displayedId, folder.id) })
                                        ReorderSlot(Modifier.align(Alignment.BottomCenter).testTag("folder-after-${folder.id}"),
                                            accepts = { it.folder && it.id != folder.id && accepts(it, displayedId) },
                                            onDrop = { viewModel.move(it, displayedId, pane.children.drop(index + 1).firstOrNull { next -> next.id != it.id }?.id) })
                                    }
                                }
                            }
                        }
                        itemsIndexed(pane.visibleNotes, key = { _, note -> "note_${note.id}" }) { index, note ->
                            val item = FolderDrag(note.id, false, note.title)
                            FolderDragItem(item, Modifier.animateItem().testTag("note-row-${note.id}")) {
                                Box {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        JournalCard(
                                            journal = note, is24Hour = is24Hour, showDate = true,
                                            modifier = Modifier.weight(1f),
                                            onToggleBookmark = { viewModel.bookmark(note.id) },
                                            onJournalClick = { navigator.navigateTo(Route.Editor(note.id), isSingleTop = true) },
                                            onLongClick = { optionsNote = note }
                                        )
                                        FolderDragHandle(item) { moveItem = item }
                                    }
                                    if (dragging && displayedId == state.currentId) {
                                        ReorderSlot(Modifier.align(Alignment.TopCenter).testTag("note-before-${note.id}"),
                                            accepts = { !it.folder && it.id != note.id },
                                            onDrop = { viewModel.move(it, displayedId, note.id) })
                                        ReorderSlot(Modifier.align(Alignment.BottomCenter).testTag("note-after-${note.id}"),
                                            accepts = { !it.folder && it.id != note.id },
                                            onDrop = { viewModel.move(it, displayedId, pane.visibleNotes.drop(index + 1).firstOrNull { next -> next.id != it.id }?.id) })
                                    }
                                }
                            }
                        }
                        item(key = "drop_end") {
                            FolderDropSurface(accepts = { displayedId == state.currentId && accepts(it, displayedId) }, onDrop = { viewModel.move(it, displayedId) }, enabled = displayedId == state.currentId) {
                                if (pane.children.isEmpty() && pane.visibleNotes.isEmpty()) {
                                    Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(painterResource(R.drawable.folder_open_24px), null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.height(16.dp))
                                        Text(stringResource(R.string.folder_empty), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Spacer(Modifier.height(12.dp))
                                        FilledTonalButton(onClick = { navigator.navigateTo(Route.Editor(initialFolderId = displayedId, initialDate = if (autoTime) System.currentTimeMillis() else null), isSingleTop = true) }) {
                                            Icon(painterResource(R.drawable.add_2_24px), null)
                                            Spacer(Modifier.width(8.dp))
                                            Text(stringResource(R.string.new_journal))
                                        }
                                    }
                                } else {
                                    Box(Modifier.fillMaxWidth().height(56.dp), contentAlignment = Alignment.Center) {
                                        if (dragging) Icon(painterResource(R.drawable.drive_folder_upload_24px), stringResource(R.string.folder_drop_here), tint = MaterialTheme.colorScheme.primary)
                                    }
                                }

                            }
                        }
                    }
                }
                }
                if (displayedId == state.currentId) {
                    FolderDragAutoScroll(listState, Modifier.fillMaxSize().padding(bottom = UiUtils.BOTTOM_BAR_PADDING)) { }
                }
                }
            }
        }
        SnackbarHost(snack, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = UiUtils.BOTTOM_BAR_PADDING))
    }

    }

    if (nameDialog || renameFolder != null) {
        FolderNameDialog(renameFolder?.name.orEmpty(), onDismiss = { nameDialog = false; renameFolder = null }) { name ->
            renameFolder?.let { viewModel.rename(it.id, name) } ?: viewModel.create(name)
            nameDialog = false; renameFolder = null
        }
    }
    optionsFolder?.let { folder ->
        ModalBottomSheet(onDismissRequest = { optionsFolder = null }) {
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
                Text(folder.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(12.dp))
                FolderAction(R.drawable.edit_24px, stringResource(R.string.rename)) { renameFolder = folder; optionsFolder = null }
                FolderAction(R.drawable.drive_folder_upload_24px, stringResource(R.string.folder_move)) { moveItem = FolderDrag(folder.id, true); optionsFolder = null }
                FolderAction(R.drawable.delete_24px, stringResource(R.string.delete)) { deleteFolder = folder; optionsFolder = null }
            }
        }
    }
    deleteFolder?.let { folder ->
        JuneConfirmationDialog(onDismiss = { deleteFolder = null }, onConfirm = { viewModel.delete(folder.id); deleteFolder = null },
            title = stringResource(R.string.folder_delete_title), description = stringResource(R.string.folder_delete_description), confirmButtonText = stringResource(R.string.delete))
    }
    optionsNote?.let { selected ->
        val note = state.notes.firstOrNull { it.id == selected.id }
        if (note != null) {
            ModalBottomSheet(onDismissRequest = { optionsNote = null }) {
                JournalOptionsSheet(note, is24Hour, onToggleBookmark = { viewModel.bookmark(note.id) },
                    onExportMarkdown = { exportNote = note; optionsNote = null }, onDeleteOrRestore = { deleteNote = note; optionsNote = null },
                    onMoveToFolder = { moveItem = FolderDrag(note.id, false); optionsNote = null })
            }
        }
    }
    deleteNote?.let { note ->
        DeleteConfirmationSheet(sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            onDismissRequest = { deleteNote = null }, onConfirm = { viewModel.deleteNote(note.id); deleteNote = null })
    }
    moveItem?.let { item -> FolderDestinationSheet(item, onDismiss = { moveItem = null }) }
    if (addExisting) {
        val visibleIds = state.visibleNotes.map { it.id }.toSet()
        ExistingFolderNoteSheet(
            notes = state.notes.filterNot { it.id in visibleIds },
            destination = state.path.lastOrNull()?.name ?: stringResource(R.string.folders),
            is24Hour = is24Hour, busy = addingNote, error = addingError,
            onDismiss = { addExisting = false },
            onChoose = { note ->
                if (!addingNote) {
                    val destination = state.currentId
                    addingNote = true
                    scope.launch {
                        try {
                            if (viewModel.moveAndReport(FolderDrag(note.id, false), destination)) {
                                addExisting = false
                                snack.showSnackbar(addedMessage)
                            } else addingError = failureMessage
                        } finally { addingNote = false }
                    }
                }
            }
        )
    }
}

@Composable
private fun Breadcrumb(id: String?, label: String, accepts: (FolderDrag) -> Boolean, onOpen: () -> Unit, onDrop: (FolderDrag) -> Unit) {
    FolderDropSurface(accepts, onDrop, onHoverOpen = onOpen) {
        TextButton(onClick = onOpen) { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 160.dp)) }
    }
}

@Composable
private fun ReorderSlot(modifier: Modifier, accepts: (FolderDrag) -> Boolean, onDrop: (FolderDrag) -> Unit) {
    FolderDropSurface(accepts, onDrop, modifier.fillMaxWidth().height(22.dp), priority = 5, insertion = true) {
        Box(Modifier.fillMaxSize())
    }
}

@Composable
private fun FolderRow(folder: Folder, folders: Int, notes: Int, onOpen: () -> Unit, onOptions: () -> Unit, onMove: () -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        // The grip owns its touch stream. A clickable ancestor would consume its long press.
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).heightIn(min = 48.dp).clickable(onClick = onOpen).padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.folder_open_24px), null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(folder.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.folder_open_24px), stringResource(R.string.folders), Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(folders.toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(4.dp))
                        Icon(painterResource(R.drawable.edit_note_24px), stringResource(R.string.journals), Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(notes.toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            IconButton(onClick = onOptions) { Icon(painterResource(R.drawable.more_vert_24px), stringResource(R.string.folder_options)) }
            FolderDragHandle(FolderDrag(folder.id, true, folder.name), onMove)
        }
    }
}

@Composable
private fun FolderAction(icon: Int, label: String, onClick: () -> Unit) {
    ListItem(headlineContent = { Text(label) }, leadingContent = { Icon(painterResource(icon), null) }, modifier = Modifier.clickable(onClick = onClick))
}

@Composable
internal fun FolderNameDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by rememberSaveable(initial) { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(onDismissRequest = onDismiss, icon = { Icon(painterResource(R.drawable.folder_open_24px), null) },
        title = { Text(stringResource(if (initial.isEmpty()) R.string.new_folder else R.string.rename)) },
        text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.folder_name)) }, singleLine = true,
            modifier = Modifier.focusRequester(focus), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (name.isNotBlank()) onSave(name.trim()) })) },
        confirmButton = { TextButton(onClick = { onSave(name.trim()) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}
