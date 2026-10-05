package com.denser.june.presentation.screens.home.folders

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    val is24Hour = time == TimeFormat.TWENTY_FOUR_HOUR
    ExportJournalBottomSheet(exportNote, onDismiss = { exportNote = null })

    fun accepts(item: FolderDrag, parent: String?): Boolean = if (item.folder) state.snapshot.canMove(item.id, parent) else true

    FolderDragHost(
        accepts = { accepts(it, state.currentId) }, onDrop = { viewModel.move(it, state.currentId) },
        modifier = Modifier.fillMaxSize(), onDragActive = { dragging = it }
    ) {
    Box(Modifier.fillMaxSize()) {
        Column {
            // Every ancestor is both a navigation button and a drop target, including the root.
            Row(
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
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(state.path.lastOrNull()?.name ?: stringResource(R.string.folders), style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = { nameDialog = true }) { Icon(painterResource(R.drawable.create_new_folder_24px), stringResource(R.string.new_folder)) }
                IconButton(onClick = { addExisting = true }) { Icon(painterResource(R.drawable.edit_note_24px), stringResource(R.string.folder_add_existing)) }
            }
            AnimatedContent(
                targetState = state.currentId,
                transitionSpec = { (fadeIn(tween(180)) + slideInVertically { it / 12 }) togetherWith (fadeOut(tween(100)) + slideOutVertically { -it / 12 }) },
                label = "folder_navigation"
            ) { displayedId ->
                // Use the target ID for each animated pane, avoiding duplicate note IDs during transitions.
                val pane = remember(state, displayedId) { state.copy(currentId = displayedId) }
                val listState = rememberLazyListState()
                Box(Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = UiUtils.BOTTOM_BAR_PADDING),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (state.loading) {
                        item { JunePlaceholderPage(Modifier.fillParentMaxHeight(0.7f), isLoading = true) }
                    } else {
                        items(pane.children, key = { "folder_${it.id}" }) { folder ->
                            Column(Modifier.animateItem()) {
                                ReorderSlot(
                                    accepts = { it.folder && it.id != folder.id && accepts(it, displayedId) },
                                    onDrop = { viewModel.move(it, displayedId, folder.id) }
                                )
                                FolderDropSurface(
                                    accepts = { accepts(it, folder.id) },
                                    onDrop = { viewModel.move(it, folder.id) },
                                    onHoverOpen = { viewModel.open(folder.id) }
                                ) {
                                    FolderRow(folder, state.itemCounts[folder.id] ?: 0, onOpen = { viewModel.open(folder.id) },
                                        onOptions = { optionsFolder = folder }, onMove = { moveItem = FolderDrag(folder.id, true) })
                                }
                            }
                        }
                        if (pane.children.isNotEmpty()) {
                            item(key = "folder_end") { ReorderSlot(accepts = { it.folder && accepts(it, displayedId) }, onDrop = { viewModel.move(it, displayedId) }) }
                        }
                        items(pane.visibleNotes, key = { "note_${it.id}" }) { note ->
                            Column(Modifier.animateItem()) {
                                ReorderSlot(accepts = { !it.folder && it.id != note.id }, onDrop = { viewModel.move(it, displayedId, note.id) })
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    JournalCard(
                                        journal = note, is24Hour = is24Hour, showDate = true,
                                        modifier = Modifier.weight(1f),
                                        onToggleBookmark = { viewModel.bookmark(note.id) },
                                        onJournalClick = { navigator.navigateTo(Route.Editor(note.id), isSingleTop = true) },
                                        onLongClick = { optionsNote = note }
                                    )
                                    FolderDragHandle(FolderDrag(note.id, false)) { moveItem = FolderDrag(note.id, false) }
                                }
                            }
                        }
                        item(key = "drop_end") {
                            FolderDropSurface(accepts = { accepts(it, displayedId) }, onDrop = { viewModel.move(it, displayedId) }) {
                                if (pane.children.isEmpty() && pane.visibleNotes.isEmpty()) {
                                    Column(Modifier.fillMaxWidth().padding(vertical = 56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(painterResource(R.drawable.folder_open_24px), null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.height(16.dp))
                                        Text(stringResource(R.string.folder_empty), style = MaterialTheme.typography.titleMedium)
                                        Spacer(Modifier.height(12.dp))
                                        FilledTonalButton(onClick = { nameDialog = true }) { Text(stringResource(R.string.new_folder)) }
                                    }
                                } else {
                                    Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
                                        Text(stringResource(R.string.folder_drop_here), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
                if (dragging) {
                    FolderDropSurface(
                        accepts = { accepts(it, displayedId) }, onDrop = { viewModel.move(it, displayedId) },
                        modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().height(40.dp),
                        onHoverScroll = { listState.scrollBy(-24f) }
                    ) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(painterResource(R.drawable.expand_less_24px), null) } }
                    FolderDropSurface(
                        accepts = { accepts(it, displayedId) }, onDrop = { viewModel.move(it, displayedId) },
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = UiUtils.BOTTOM_BAR_PADDING).height(40.dp),
                        onHoverScroll = { listState.scrollBy(24f) }
                    ) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(painterResource(R.drawable.expand_more_24px), null) } }
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
                FolderAction(R.drawable.drive_folder_upload_24px, stringResource(R.string.folder_move)) { moveItem = FolderDrag(note.id, false); optionsNote = null }
                JournalOptionsSheet(note, is24Hour, onToggleBookmark = { viewModel.bookmark(note.id) },
                    onExportMarkdown = { exportNote = note; optionsNote = null }, onDeleteOrRestore = { deleteNote = note; optionsNote = null })
            }
        }
    }
    deleteNote?.let { note ->
        DeleteConfirmationSheet(sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            onDismissRequest = { deleteNote = null }, onConfirm = { viewModel.deleteNote(note.id); deleteNote = null })
    }
    moveItem?.let { item ->
        FolderMoveSheet(state, item, onDismiss = { moveItem = null }, onMove = { parent -> viewModel.move(item, parent); moveItem = null })
    }
    if (addExisting) {
        ModalBottomSheet(onDismissRequest = { addExisting = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Text(stringResource(R.string.folder_add_existing), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(24.dp))
            val visibleIds = state.visibleNotes.map { it.id }.toSet()
            val notes = state.notes.filterNot { it.id in visibleIds }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (notes.isEmpty()) item { Text(stringResource(R.string.folder_no_other_notes), modifier = Modifier.padding(16.dp)) }
                items(notes, key = { it.id }) { note ->
                    JournalCard(note, is24Hour = is24Hour, showDate = true, onJournalClick = {
                        viewModel.move(FolderDrag(note.id, false), state.currentId); addExisting = false
                    })
                }
            }
        }
    }
}

@Composable
private fun Breadcrumb(id: String?, label: String, accepts: (FolderDrag) -> Boolean, onOpen: () -> Unit, onDrop: (FolderDrag) -> Unit) {
    FolderDropSurface(accepts, onDrop, onHoverOpen = onOpen) {
        TextButton(onClick = onOpen) { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 160.dp)) }
    }
}

@Composable
private fun ReorderSlot(accepts: (FolderDrag) -> Boolean, onDrop: (FolderDrag) -> Unit) {
    FolderDropSurface(accepts, onDrop, modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(16.dp))
    }
}

@Composable
private fun FolderRow(folder: Folder, count: Int, onOpen: () -> Unit, onOptions: () -> Unit, onMove: () -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(start = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.folder_open_24px), null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(folder.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(count.toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onOptions) { Icon(painterResource(R.drawable.more_vert_24px), stringResource(R.string.folder_options)) }
            FolderDragHandle(FolderDrag(folder.id, true), onMove)
        }
    }
}

@Composable
private fun FolderAction(icon: Int, label: String, onClick: () -> Unit) {
    ListItem(headlineContent = { Text(label) }, leadingContent = { Icon(painterResource(icon), null) }, modifier = Modifier.clickable(onClick = onClick))
}

@Composable
private fun FolderNameDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by rememberSaveable(initial) { mutableStateOf(initial) }
    AlertDialog(onDismissRequest = onDismiss, icon = { Icon(painterResource(R.drawable.folder_open_24px), null) },
        title = { Text(stringResource(if (initial.isEmpty()) R.string.new_folder else R.string.rename)) },
        text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.folder_name)) }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onSave(name.trim()) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolderMoveSheet(state: FoldersState, item: FolderDrag, onDismiss: () -> Unit, onMove: (String?) -> Unit) {
    var parent by rememberSaveable(item.id) { mutableStateOf<String?>(null) }
    val folders = state.liveFolders.filter { it.parentId == parent && (!item.folder || state.snapshot.canMove(item.id, it.id)) }.sortedBy { it.position }
    val current = state.liveFolders.firstOrNull { it.id == parent }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (parent != null) IconButton(onClick = { parent = current?.parentId }) { Icon(painterResource(R.drawable.arrow_back_24px), stringResource(R.string.back)) }
                Text(current?.name ?: stringResource(R.string.folders), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                items(folders, key = { it.id }) { folder ->
                    ListItem(headlineContent = { Text(folder.name) }, leadingContent = { Icon(painterResource(R.drawable.folder_open_24px), null) },
                        trailingContent = { Icon(painterResource(R.drawable.chevron_right_24px), null) }, modifier = Modifier.clickable { parent = folder.id })
                }
            }
            Button(onClick = { onMove(parent) }, enabled = !item.folder || state.snapshot.canMove(item.id, parent), modifier = Modifier.fillMaxWidth()) {
                Icon(painterResource(R.drawable.drive_folder_upload_24px), null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.folder_move_here))
            }
        }
    }
}
