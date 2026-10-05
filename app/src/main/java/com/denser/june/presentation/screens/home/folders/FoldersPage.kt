package com.denser.june.presentation.screens.home.folders

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.denser.june.core.R
import com.denser.june.core.domain.folders.NoteFolder
import com.denser.june.core.domain.model.Journal
import com.denser.june.core.domain.model.enums.TimeFormat
import com.denser.june.presentation.components.*
import com.denser.june.presentation.navigation.AppNavigator
import com.denser.june.presentation.navigation.Route
import com.denser.june.presentation.screens.home.components.*
import com.denser.june.presentation.utils.UiUtils
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FoldersPage(isSelected: Boolean, viewModel: FoldersVM = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val timeFormat by viewModel.timeFormat.collectAsStateWithLifecycle()
    val navigator = koinInject<AppNavigator>()
    val drag = remember { FolderDrag() }
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1 else 1
    var newFolder by rememberSaveable { mutableStateOf(false) }
    var renameFolder by remember { mutableStateOf<NoteFolder?>(null) }
    var deleteFolder by remember { mutableStateOf<NoteFolder?>(null) }
    var selectedFolder by remember { mutableStateOf<NoteFolder?>(null) }
    var selectedNote by remember { mutableStateOf<String?>(null) }
    var deleteNote by remember { mutableStateOf<String?>(null) }
    var moveItem by remember { mutableStateOf<FolderItem?>(null) }
    var exportNote by remember { mutableStateOf<Journal?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val resources = androidx.compose.ui.platform.LocalContext.current.resources

    BackHandler(enabled = isSelected && state.currentId != null) { viewModel.back() }
    LaunchedEffect(viewModel) { viewModel.events.collect { snackbar.showSnackbar(resources.getString(it)) } }
    LaunchedEffect(isSelected) { if (!isSelected) drag.end() }

    // Spring-open a hovered folder/ancestor without ending the native drag session.
    LaunchedEffect(drag.hovered, drag.item) {
        val key = drag.hovered ?: return@LaunchedEffect
        val item = drag.item ?: return@LaunchedEffect
        if (key.startsWith("into:") || key.startsWith("crumb:")) {
            val destination = key.substringAfter(':').takeUnless { it == "root" }
            if (state.accepts(item, destination) && destination != state.currentId) {
                delay(850)
                if (drag.hovered == key) { drag.hovered = null; viewModel.open(destination) }
            }
        }
    }

    Box(Modifier.fillMaxSize().folderDragHost(drag)) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                if (state.currentId != null) IconButton(onClick = viewModel::back) {
                    Icon(painterResource(R.drawable.arrow_back_24px), stringResource(R.string.back))
                }
                Text(state.currentFolder?.name ?: stringResource(R.string.folders),
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                FilledTonalIconButton(onClick = { newFolder = true }, enabled = state.loaded && !state.busy) {
                    Icon(painterResource(R.drawable.create_new_folder_24px), stringResource(R.string.new_folder))
                }
                if (state.currentFolder != null) IconButton(onClick = { selectedFolder = state.currentFolder }) {
                    Icon(painterResource(R.drawable.more_vert_24px), stringResource(R.string.folder_options))
                }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                FolderCrumb(null, stringResource(R.string.folders), state, drag, viewModel)
                state.path.forEach { folder ->
                    Icon(painterResource(R.drawable.chevron_right_24px), null, Modifier.size(16.dp))
                    FolderCrumb(folder.id, folder.name, state, drag, viewModel)
                }
            }
            AnimatedVisibility(drag.item != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Text(stringResource(R.string.folder_drag_hint), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
            }
            AnimatedContent(
                targetState = state.currentId,
                modifier = Modifier.weight(1f),
                transitionSpec = {
                    val forward = state.tree.path(targetState).size > state.tree.path(initialState).size
                    val sign = (if (forward) 1 else -1) * direction
                    (fadeIn(spring()) + slideInHorizontally { it / 8 * sign }) togetherWith
                        (fadeOut(spring()) + slideOutHorizontally { -it / 8 * sign })
                }, label = "folder_navigation"
            ) { folderId ->
                // Outgoing content keeps its own parent during the animated transition.
                val page = state.copy(currentId = folderId)
                FolderContents(page, drag, viewModel, timeFormat == TimeFormat.TWENTY_FOUR_HOUR,
                    onOpenNote = { navigator.navigateTo(Route.Editor(it.id), isSingleTop = true) },
                    onNoteOptions = { selectedNote = it.id }, onFolderOptions = { selectedFolder = it },
                    onMove = { moveItem = it }, onNewFolder = { newFolder = true },
                    onNewNote = { navigator.navigateTo(Route.Editor(initialFolderId = page.currentId)) })
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = UiUtils.BOTTOM_BAR_PADDING))
    }

    if (newFolder) FolderNameDialog(null, onDismiss = { newFolder = false }) { viewModel.create(it); newFolder = false }
    renameFolder?.let { folder -> FolderNameDialog(folder.name, onDismiss = { renameFolder = null }) {
        viewModel.rename(folder.id, it); renameFolder = null
    } }
    deleteFolder?.let { folder -> JuneConfirmationDialog(
        onDismiss = { deleteFolder = null }, onConfirm = { viewModel.remove(folder.id); deleteFolder = null },
        title = stringResource(R.string.delete_folder), description = stringResource(R.string.delete_folder_description, folder.name),
        confirmButtonText = stringResource(R.string.delete)) }
    selectedFolder?.let { folder ->
        ModalBottomSheet(onDismissRequest = { selectedFolder = null }, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
            Text(folder.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FolderAction(R.drawable.edit_24px, R.string.rename_folder, Modifier.weight(1f)) { renameFolder = folder; selectedFolder = null }
                FolderAction(R.drawable.drive_file_move_24px, R.string.move, Modifier.weight(1f)) { moveItem = FolderItem.Folder(folder.id); selectedFolder = null }
                FolderAction(R.drawable.delete_24px, R.string.delete, Modifier.weight(1f)) { deleteFolder = folder; selectedFolder = null }
            }
            Spacer(Modifier.navigationBarsPadding())
        }
    }
    val note = state.journals.firstOrNull { it.id == selectedNote }
    if (note != null) ModalBottomSheet(onDismissRequest = { selectedNote = null },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FolderAction(R.drawable.edit_24px, R.string.edit, Modifier.weight(1f)) { navigator.navigateTo(Route.Editor(note.id)); selectedNote = null }
            FolderAction(R.drawable.drive_file_move_24px, R.string.move, Modifier.weight(1f)) { moveItem = FolderItem.Note(note.id); selectedNote = null }
        }
        JournalOptionsSheet(journal = note, is24Hour = timeFormat == TimeFormat.TWENTY_FOUR_HOUR,
            onToggleBookmark = { viewModel.bookmark(note.id) },
            onDeleteOrRestore = { deleteNote = note.id; selectedNote = null },
            onExportMarkdown = { exportNote = note; selectedNote = null })
    }
    if (deleteNote != null) DeleteConfirmationSheet(sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        onDismissRequest = { deleteNote = null }, onConfirm = { deleteNote?.let(viewModel::deleteNote); deleteNote = null })
    ExportJournalBottomSheet(journal = exportNote, onDismiss = { exportNote = null })
    moveItem?.let { item -> FolderMoveSheet(item, state, onDismiss = { moveItem = null }) {
        viewModel.move(item, it); moveItem = null
    } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolderCrumb(id: String?, label: String, state: FoldersState, drag: FolderDrag, viewModel: FoldersVM) {
    val key = "crumb:${id ?: "root"}"
    val hovered = drag.hovered == key
    val color by animateColorAsState(if (hovered) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer, label = "breadcrumb_drop")
    Surface(onClick = { viewModel.open(id) }, color = color, shape = CircleShape,
        modifier = Modifier.folderDropTarget(key, drag, { state.accepts(it, id) }, { viewModel.move(it, id) })) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (id == null) Icon(painterResource(R.drawable.folder_open_24px), null, Modifier.size(18.dp))
            if (id == null) Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 160.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FolderContents(
    state: FoldersState, drag: FolderDrag, viewModel: FoldersVM, is24Hour: Boolean,
    onOpenNote: (Journal) -> Unit, onNoteOptions: (Journal) -> Unit,
    onFolderOptions: (NoteFolder) -> Unit, onMove: (FolderItem) -> Unit,
    onNewFolder: () -> Unit, onNewNote: () -> Unit
) {
    val list = rememberLazyListState()
    val scrollDirection = when (drag.hovered) { "scroll:-1" -> -1; "scroll:1" -> 1; else -> 0 }
    val scrollStep = with(androidx.compose.ui.platform.LocalDensity.current) { 10.dp.toPx() }
    LaunchedEffect(scrollDirection, drag.item) {
        while (drag.item != null && scrollDirection != 0) { list.scrollBy(scrollStep * scrollDirection); delay(16) }
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(state = list, modifier = Modifier.fillMaxSize()
            .folderDropTarget("page:${state.currentId}", drag, { state.accepts(it, state.currentId) }, { viewModel.move(it, state.currentId) }), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!state.loaded) item { JunePlaceholderPage(isLoading = true, modifier = Modifier.fillParentMaxHeight(0.7f)) }
            if (state.loaded && state.children.isEmpty() && state.notes.isEmpty()) item {
                Column(Modifier.fillParentMaxHeight(0.7f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(painterResource(R.drawable.folder_open_24px), null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.folder_empty), style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = onNewFolder) { Icon(painterResource(R.drawable.create_new_folder_24px), null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.new_folder)) }
                        Button(onClick = onNewNote) { Icon(painterResource(R.drawable.add_2_24px), null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.new_journal)) }
                    }
                }
            }
            items(state.children, key = { "folder:${it.id}" }) { folder ->
                Column(Modifier.animateItem()) {
                    FolderInsertion(FolderItem.Folder(folder.id), state, drag, viewModel)
                    FolderTile(folder, state, drag, viewModel, onFolderOptions, onMove)
                }
            }
            if (state.children.isNotEmpty()) item(key = "folder_end") { FolderInsertion(null, state, drag, viewModel, folders = true) }
            if (state.notes.isNotEmpty()) item(key = "notes_header") {
                Text(stringResource(if (state.currentId == null) R.string.unfiled_notes else R.string.journals),
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp))
            }
            items(state.notes, key = { "note:${it.id}" }) { note ->
                Column(Modifier.animateItem()) {
                    FolderInsertion(FolderItem.Note(note.id), state, drag, viewModel)
                    JournalCard(journal = note, is24Hour = is24Hour, showDate = true,
                        onToggleBookmark = { viewModel.bookmark(note.id) }, onJournalClick = { onOpenNote(note) },
                        onLongClick = { onNoteOptions(note) }, trailingContent = {
                            IconButton(onClick = { onMove(FolderItem.Note(note.id)) }, enabled = !state.busy,
                                modifier = Modifier.folderDragSource(FolderItem.Note(note.id), drag, !state.busy)) {
                                Icon(painterResource(R.drawable.drag_indicator_24px), stringResource(R.string.move_note))
                            }
                        })
                }
            }
            item(key = "note_end") { FolderInsertion(null, state, drag, viewModel, folders = false) }
            item {
                Box(Modifier.fillMaxWidth().height(64.dp)
                    .folderDropTarget("current:${state.currentId}", drag, { state.accepts(it, state.currentId) }, { viewModel.move(it, state.currentId) }),
                    contentAlignment = Alignment.Center) {
                    if (drag.item != null) Text(stringResource(R.string.drop_here), color = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(UiUtils.BOTTOM_BAR_PADDING))
            }
        }
        if (drag.item != null) {
            DragScrollEdge(-1, drag, Modifier.align(Alignment.TopCenter))
            DragScrollEdge(1, drag, Modifier.align(Alignment.BottomCenter).padding(bottom = UiUtils.BOTTOM_BAR_PADDING))
        }
    }
}

@Composable
private fun DragScrollEdge(direction: Int, drag: FolderDrag, modifier: Modifier) {
    val key = "scroll:$direction"
    Box(modifier.fillMaxWidth().height(32.dp).folderDropTarget(key, drag, { true }, {}))
}

@Composable
private fun FolderInsertion(before: FolderItem?, state: FoldersState, drag: FolderDrag, viewModel: FoldersVM, folders: Boolean = before is FolderItem.Folder) {
    val item = drag.item
    if (item == null || (item is FolderItem.Folder) != folders) return
    val key = "before:${before?.id ?: if (folders) "folders_end" else "notes_end"}"
    val hovered = drag.hovered == key
    Surface(color = if (hovered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = CircleShape, modifier = Modifier.fillMaxWidth().height(if (hovered) 18.dp else 12.dp)
            .folderDropTarget(key, drag, { incoming -> incoming != before && state.accepts(incoming, state.currentId) },
                { viewModel.move(it, state.currentId, before?.id) })) {}
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun FolderTile(folder: NoteFolder, state: FoldersState, drag: FolderDrag, viewModel: FoldersVM,
    onOptions: (NoteFolder) -> Unit, onMove: (FolderItem) -> Unit) {
    val key = "into:${folder.id}"
    val hovered = drag.hovered == key
    val color by animateColorAsState(if (hovered) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow, label = "folder_drop_color")
    val scale by animateFloatAsState(if (hovered) 1.025f else 1f, spring(), label = "folder_drop_scale")
    val rotation by animateFloatAsState(if (hovered) -7f else 0f, spring(), label = "folder_icon_tilt")
    val childCount = state.tree.children(folder.id).size
    val noteCount = state.noteCounts[folder.id] ?: 0
    Surface(color = color, shape = RoundedCornerShape(24.dp),
        border = if (hovered) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier.fillMaxWidth().graphicsLayer { scaleX = scale; scaleY = scale }
            .folderDropTarget(key, drag, { state.accepts(it, folder.id) }, { viewModel.move(it, folder.id) })
            .clip(RoundedCornerShape(24.dp)).combinedClickable(onClick = { viewModel.open(folder.id) }, onLongClick = { onOptions(folder) })) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(16.dp), modifier = Modifier.size(52.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.folder_open_24px), null, Modifier.size(28.dp).graphicsLayer { rotationZ = rotation })
                }
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(folder.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(stringResource(R.string.folder_counts, childCount, noteCount), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { onMove(FolderItem.Folder(folder.id)) }, enabled = !state.busy,
                modifier = Modifier.folderDragSource(FolderItem.Folder(folder.id), drag, !state.busy)) {
                Icon(painterResource(R.drawable.drag_indicator_24px), stringResource(R.string.move_folder))
            }
        }
    }
}

@Composable
private fun FolderAction(icon: Int, label: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, modifier = modifier, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(painterResource(icon), null); Spacer(Modifier.height(4.dp)); Text(stringResource(label), maxLines = 1)
        }
    }
}

@Composable
private fun FolderNameDialog(initial: String?, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by rememberSaveable(initial) { mutableStateOf(initial.orEmpty()) }
    val focus = remember { FocusRequester() }
    val valid = name.trim().isNotBlank() && name.length <= 120
    LaunchedEffect(Unit) { focus.requestFocus() }
    JuneDialog(onDismissRequest = onDismiss, title = stringResource(if (initial == null) R.string.new_folder else R.string.rename_folder),
        icon = R.drawable.folder_open_24px,
        text = { OutlinedTextField(value = name, onValueChange = { if (it.length <= 120) name = it }, singleLine = true,
            label = { Text(stringResource(R.string.name)) }, modifier = Modifier.fillMaxWidth().focusRequester(focus),
            shape = RoundedCornerShape(16.dp), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (valid) onSave(name.trim()) })) },
        confirmButton = { Button(onClick = { onSave(name.trim()) }, enabled = valid) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderMoveSheet(item: FolderItem, state: FoldersState, onDismiss: () -> Unit, onMove: (String?) -> Unit) {
    var destination by rememberSaveable(item.id) { mutableStateOf(state.currentId) }
    val tree = state.tree
    LaunchedEffect(tree.folders.keys) { if (destination != null && destination !in tree.folders) destination = null }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text(stringResource(R.string.move), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { destination = tree.parentOf(destination ?: return@IconButton) }, enabled = destination != null) {
                    Icon(painterResource(R.drawable.arrow_back_24px), stringResource(R.string.back))
                }
                Text(tree.folders[destination]?.name ?: stringResource(R.string.folders), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { destination = null }) { Text(stringResource(R.string.folders)) }
            }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(tree.children(destination).filter { state.accepts(item, it.id) }, key = { it.id }) { folder ->
                    Surface(onClick = { destination = folder.id }, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(painterResource(R.drawable.folder_open_24px), null)
                            Text(folder.name, Modifier.weight(1f).padding(horizontal = 12.dp))
                            Icon(painterResource(R.drawable.chevron_right_24px), null)
                        }
                    }
                }
            }
            Button(onClick = { onMove(destination) }, enabled = state.accepts(item, destination), modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
                Icon(painterResource(R.drawable.drive_file_move_24px), null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.move_here))
            }
        }
        Spacer(Modifier.navigationBarsPadding())
    }
}
