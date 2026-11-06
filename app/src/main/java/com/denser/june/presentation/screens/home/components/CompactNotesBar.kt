package com.denser.june.presentation.screens.home.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.denser.june.core.R
import com.denser.june.presentation.utils.UiUtils

@Composable
internal fun CompactNotesBar(
    searching: Boolean,
    query: String,
    grid: Boolean,
    showLayoutToggle: Boolean,
    focusRequester: FocusRequester,
    onQuery: (String) -> Unit,
    onNavigation: () -> Unit,
    onSearch: () -> Unit,
    onToggleLayout: () -> Unit,
    onSettings: () -> Unit,
    syncIndicator: @Composable () -> Unit
) {
    val focusManager = LocalFocusManager.current
    Surface(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 6.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(20.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onNavigation) {
                Icon(painterResource(if (searching) R.drawable.arrow_back_24px else R.drawable.menu_24px),
                    stringResource(if (searching) R.string.back else R.string.folder_open_sidebar))
            }
            if (searching) {
                TextField(value = query, onValueChange = onQuery, singleLine = true,
                    modifier = Modifier.weight(1f).focusRequester(focusRequester).testTag("compact-note-search"),
                    placeholder = { Text(stringResource(R.string.notes_search)) },
                    colors = UiUtils.getTransparentTextFieldColors(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                    trailingIcon = if (query.isNotEmpty()) { {
                        IconButton(onClick = { onQuery("") }) {
                            Icon(painterResource(R.drawable.close_24px), stringResource(R.string.clear))
                        }
                    } } else null)
            } else {
                Row(Modifier.weight(1f).heightIn(min = 48.dp).clickable(onClick = onSearch)
                    .testTag("compact-search-entry"), verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.search_24px), null, Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.notes_search), style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (showLayoutToggle) IconButton(onClick = onToggleLayout, modifier = Modifier.testTag("notes-layout-toggle")) {
                    Icon(painterResource(if (grid) R.drawable.list_24px else R.drawable.view_cozy_24px),
                        stringResource(if (grid) R.string.notes_show_list else R.string.notes_show_grid))
                }
                syncIndicator()
                IconButton(onClick = onSettings) {
                    Icon(painterResource(R.drawable.settings_24px), stringResource(R.string.settings))
                }
            }
        }
    }
}
