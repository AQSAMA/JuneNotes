package com.denser.june.presentation.screens.home.folders

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.denser.june.core.R
import com.denser.june.core.domain.folder.Folder
import com.denser.june.presentation.components.JuneConfirmationDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FolderSidebar(
    viewModel: FoldersVM,
    folderSelected: Boolean,
    onAllNotes: () -> Unit,
    onFolder: (String?) -> Unit,
    onClose: () -> Unit,
    allNotesSelected: Boolean = !folderSelected
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var expandedIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var managing by rememberSaveable { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var createParent by remember { mutableStateOf<String?>(null) }
    var options by remember { mutableStateOf<Folder?>(null) }
    var renaming by remember { mutableStateOf<Folder?>(null) }
    var deleting by remember { mutableStateOf<Folder?>(null) }
    var moving by remember { mutableStateOf<FolderDrag?>(null) }
    val list = rememberLazyListState()
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) { viewModel.errors.collect { snack.showSnackbar(it) } }
    // Reveal the selected branch on entry or after a move, without resetting manual expansion.
    LaunchedEffect(state.path.map { it.id }) {
        expandedIds = (expandedIds + state.path.dropLast(1).map { it.id }).distinct()
    }
    val rows = remember(state.liveFolders, expandedIds) { folderTreeRows(state.liveFolders, expandedIds.toSet()) }
    val siblings = remember(state.liveFolders) {
        state.liveFolders.groupBy { it.parentId }.mapValues { (_, folders) -> folders.sortedWith(compareBy({ it.position }, { it.id })) }
    }
    fun accepts(item: FolderDrag, parent: String?) = item.folder && state.snapshot.canMove(item.id, parent)
    val moveLabel = stringResource(R.string.folder_move)
    val optionsLabel = stringResource(R.string.folder_options)

    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp, top = 8.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = onClose) { Icon(painterResource(R.drawable.close_24px), stringResource(R.string.folder_close_sidebar)) }
            }
            FolderDragHost({ false }, {}, { dragging = it }, Modifier.weight(1f)) {
                Box(Modifier.fillMaxSize()) {
                    LazyColumn(state = list, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp), modifier = Modifier.fillMaxSize()) {
                        item(key = "all-notes") {
                            SidebarDestination(stringResource(R.string.folder_all_notes), R.drawable.edit_note_24px,
                                state.notes.size, allNotesSelected, onAllNotes, Modifier.testTag("sidebar-all-notes"))
                        }
                        item(key = "unfiled") {
                            FolderDropSurface({ accepts(it, null) }, { viewModel.move(it, null) }) {
                                SidebarDestination(stringResource(R.string.folder_unfiled), R.drawable.folder_open_24px,
                                    state.noteCounts[null] ?: 0, folderSelected && state.currentId == null,
                                    { onFolder(null) }, Modifier.testTag("sidebar-unfiled"))
                            }
                        }
                        item(key = "folder-heading") {
                            HorizontalDivider(Modifier.padding(horizontal = 12.dp, vertical = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            Row(Modifier.fillMaxWidth().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.folders), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                                IconButton(onClick = { createParent = null; creating = true }) {
                                    Icon(painterResource(R.drawable.create_new_folder_24px), stringResource(R.string.new_folder))
                                }
                            }
                        }
                        items(rows, key = { "tree-${it.folder.id}" }) { row ->
                            val folder = row.folder
                            val item = FolderDrag(folder.id, true, folder.name)
                            val expanded = folder.id in expandedIds
                            val selectedFolder = folderSelected && folder.id == state.currentId
                            val indent = (row.depth.coerceAtMost(5) * 16).dp
                            FolderDragItem(item, Modifier.animateItem().testTag("sidebar-folder-${folder.id}")) {
                                Box(Modifier.padding(start = indent)) {
                                    FolderDropSurface(
                                        accepts = { accepts(it, folder.id) }, onDrop = {
                                            expandedIds = (expandedIds + folder.id).distinct()
                                            viewModel.move(it, folder.id)
                                        },
                                        onHoverOpen = if (row.hasChildren && !expanded) ({ expandedIds = (expandedIds + folder.id).distinct() }) else null
                                    ) {
                                        val color by animateColorAsState(if (selectedFolder) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow, label = "selected_folder")
                                        Surface(color = color, shape = RoundedCornerShape(16.dp)) {
                                            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                                                if (row.hasChildren) {
                                                    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "folder_expansion")
                                                    IconButton(onClick = { expandedIds = if (expanded) expandedIds - folder.id else expandedIds + folder.id }, modifier = Modifier.size(48.dp).testTag("sidebar-expand-${folder.id}")) {
                                                        Icon(painterResource(R.drawable.expand_more_24px), stringResource(if (expanded) R.string.folder_collapse else R.string.folder_expand), Modifier.size(20.dp).graphicsLayer { rotationZ = rotation })
                                                    }
                                                } else Spacer(Modifier.width(12.dp))
                                                FolderDragSource(item, Modifier.weight(1f)) {
                                                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { onFolder(folder.id) }.semantics {
                                                        selected = selectedFolder
                                                        customActions = listOf(
                                                            CustomAccessibilityAction(moveLabel) { moving = item; true },
                                                            CustomAccessibilityAction(optionsLabel) { options = folder; true }
                                                        )
                                                    }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(painterResource(R.drawable.folder_open_24px), null, Modifier.size(22.dp), tint = if (selectedFolder) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.primary)
                                                        Spacer(Modifier.width(12.dp))
                                                        Text(folder.name, style = MaterialTheme.typography.bodyLarge, fontWeight = if (selectedFolder) FontWeight.SemiBold else FontWeight.Normal,
                                                            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                                        if (!managing) Text((state.noteCounts[folder.id] ?: 0).toString(), style = MaterialTheme.typography.labelMedium,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp))
                                                    }
                                                }
                                                if (managing) FolderDragHandle(item) { moving = item }
                                                else IconButton(onClick = { options = folder }, modifier = Modifier.size(48.dp)) {
                                                    Icon(painterResource(R.drawable.more_vert_24px), optionsLabel, Modifier.size(20.dp))
                                                }
                                            }
                                        }
                                    }
                                    if (dragging) {
                                        SidebarInsertion(Modifier.align(Alignment.TopCenter).testTag("sidebar-before-${folder.id}"), Alignment.TopCenter,
                                            { it.id != folder.id && accepts(it, folder.parentId) }, { viewModel.move(it, folder.parentId, folder.id) })
                                        SidebarInsertion(Modifier.align(Alignment.BottomCenter).testTag("sidebar-after-${folder.id}"), Alignment.BottomCenter,
                                            { it.id != folder.id && accepts(it, folder.parentId) }, { drag ->
                                                val peers = siblings[folder.parentId].orEmpty()
                                                val next = peers.dropWhile { it.id != folder.id }.drop(1).firstOrNull { it.id != drag.id }?.id
                                                viewModel.move(drag, folder.parentId, next)
                                            })
                                    }
                                }
                            }
                        }
                        item(key = "tree-end") {
                            FolderDropSurface({ accepts(it, null) }, { viewModel.move(it, null) }) {
                                Box(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(12.dp), contentAlignment = Alignment.Center) {
                                    if (dragging) Text(stringResource(R.string.folder_drop_root), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                    else if (rows.isEmpty() && !state.loading) Text(stringResource(R.string.folder_sidebar_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    else if (state.loading) CircularProgressIndicator(Modifier.size(24.dp))
                                }
                            }
                        }
                    }
                    FolderDragAutoScroll(list, Modifier.fillMaxSize()) { }
                    SnackbarHost(snack, Modifier.align(Alignment.BottomCenter))
                }
            }
            // Management is optional. Whole labels can always be held to drag.
            TextButton(onClick = { managing = !managing }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag("sidebar-manage")) {
                Icon(painterResource(if (managing) R.drawable.check_24px else R.drawable.drag_indicator_24px), null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(if (managing) R.string.done else R.string.folder_organize))
            }
        }
    }
    if (creating || renaming != null) {
        FolderNameDialog(renaming?.name.orEmpty(), onDismiss = { creating = false; renaming = null }) { name ->
            renaming?.let { viewModel.rename(it.id, name) } ?: viewModel.create(name, createParent)
            createParent?.let { expandedIds = (expandedIds + it).distinct() }
            creating = false; renaming = null
        }
    }
    options?.let { folder ->
        ModalBottomSheet(onDismissRequest = { options = null }) {
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
                Text(folder.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp))
                SidebarAction(R.drawable.create_new_folder_24px, stringResource(R.string.folder_new_subfolder)) { createParent = folder.id; creating = true; options = null }
                SidebarAction(R.drawable.edit_24px, stringResource(R.string.rename)) { renaming = folder; options = null }
                SidebarAction(R.drawable.drive_folder_upload_24px, moveLabel) { moving = FolderDrag(folder.id, true, folder.name); options = null }
                SidebarAction(R.drawable.delete_24px, stringResource(R.string.delete)) { deleting = folder; options = null }
            }
        }
    }
    deleting?.let { folder ->
        JuneConfirmationDialog(onDismiss = { deleting = null }, onConfirm = { viewModel.delete(folder.id); deleting = null },
            title = stringResource(R.string.folder_delete_title), description = stringResource(R.string.folder_delete_description), confirmButtonText = stringResource(R.string.delete))
    }
    moving?.let { FolderDestinationSheet(it, onDismiss = { moving = null }) }
}

@Composable
private fun SidebarDestination(label: String, icon: Int, count: Int, active: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(color = if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(16.dp)) {
        Row(modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onClick).semantics { selected = active }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(icon), null, Modifier.size(22.dp))
            Spacer(Modifier.width(16.dp))
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
            Text(count.toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SidebarInsertion(modifier: Modifier, alignment: Alignment, accepts: (FolderDrag) -> Boolean, drop: (FolderDrag) -> Unit) {
    FolderDropSurface(accepts, drop, modifier.fillMaxWidth().height(12.dp), priority = 5, insertion = true, markerAlignment = alignment) { Box(Modifier.fillMaxSize()) }
}

@Composable
private fun SidebarAction(icon: Int, label: String, onClick: () -> Unit) {
    ListItem(headlineContent = { Text(label) }, leadingContent = { Icon(painterResource(icon), null) }, modifier = Modifier.clickable(onClick = onClick))
}
