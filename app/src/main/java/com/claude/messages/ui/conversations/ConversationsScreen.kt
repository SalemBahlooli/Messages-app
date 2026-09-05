package com.claude.messages.ui.conversations

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.claude.messages.data.model.Conversation
import com.claude.messages.ui.compose.Avatar
import com.claude.messages.ui.compose.EmptyState
import com.claude.messages.ui.compose.SetupCard
import com.claude.messages.util.Formatting

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationsScreen(
    viewModel: ConversationsViewModel,
    onOpenThread: (Long) -> Unit,
    onNewMessage: () -> Unit,
    onOpenRules: () -> Unit,
    onOpenSettings: () -> Unit,
    onRequestDefaultSms: () -> Unit,
    onGrantPermissions: () -> Unit,
    onOpenAppSettings: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var menuOpen by remember { mutableStateOf(false) }
    val setupNeeded = !state.hasSmsPermission || !state.isDefaultSmsApp

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            if (state.inSelectionMode) {
                SelectionTopBar(
                    count = state.selected.size,
                    showArchived = state.showArchived,
                    allPinned = state.selectedConversations.all { it.pinned },
                    allMuted = state.selectedConversations.all { it.muted },
                    onClose = viewModel::clearSelection,
                    onDelete = viewModel::deleteSelected,
                    onMarkRead = viewModel::markSelectedRead,
                    onArchive = viewModel::archiveSelected,
                    onPin = viewModel::togglePinSelected,
                    onMute = viewModel::toggleMuteSelected,
                )
            } else {
                TopAppBar(
                    title = {
                        Text(
                            text = if (state.showArchived) "Archived" else "Messages",
                            style = MaterialTheme.typography.headlineSmall,
                        )
                    },
                    navigationIcon = {
                        if (state.showArchived) {
                            IconButton(onClick = viewModel::toggleArchivedView) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = onOpenRules) {
                            Icon(Icons.Default.Tune, "Notification rules")
                        }
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Default.MoreVert, "More")
                            }
                            DropdownMenu(menuOpen, onDismissRequest = { menuOpen = false }) {
                                if (!state.showArchived) {
                                    DropdownMenuItem(
                                        text = { Text("Archived (${state.archivedCount})") },
                                        leadingIcon = { Icon(Icons.Default.Archive, null) },
                                        onClick = {
                                            menuOpen = false
                                            viewModel.toggleArchivedView()
                                        },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Notification rules") },
                                    leadingIcon = { Icon(Icons.Default.Tune, null) },
                                    onClick = { menuOpen = false; onOpenRules() },
                                )
                                DropdownMenuItem(
                                    text = { Text("Settings") },
                                    leadingIcon = { Icon(Icons.Default.Settings, null) },
                                    onClick = { menuOpen = false; onOpenSettings() },
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            }
        },
        floatingActionButton = {
            if (!state.inSelectionMode && !state.showArchived && !setupNeeded) {
                ExtendedFloatingActionButton(
                    onClick = onNewMessage,
                    icon = { Icon(Icons.Default.Edit, null) },
                    text = { Text("Start chat") },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {

            AnimatedVisibility(
                visible = setupNeeded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                SetupCard(
                    needsPermissions = !state.hasSmsPermission,
                    onGrantPermissions = onGrantPermissions,
                    onRequestDefaultSms = onRequestDefaultSms,
                    onOpenAppSettings = onOpenAppSettings,
                )
            }

            if (!state.showArchived) {
                SearchField(
                    query = state.searchQuery,
                    onQueryChange = viewModel::onSearchQueryChange,
                    onClear = viewModel::closeSearch,
                )
            }

            if (state.loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            when {
                state.searching -> SearchResults(state.searchResults, onOpenThread)

                state.conversations.isEmpty() && !state.loading && !setupNeeded -> EmptyState(
                    icon = Icons.AutoMirrored.Filled.Message,
                    title = if (state.showArchived) "Nothing archived" else "No conversations yet",
                    subtitle = if (state.showArchived) null
                    else "Tap Start chat to send your first message.",
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 96.dp, top = 4.dp),
                ) {
                    items(state.conversations, key = { it.threadId }) { conversation ->
                        ConversationRow(
                            conversation = conversation,
                            selected = conversation.threadId in state.selected,
                            onClick = {
                                if (state.inSelectionMode) {
                                    viewModel.toggleSelection(conversation.threadId)
                                } else {
                                    onOpenThread(conversation.threadId)
                                }
                            },
                            onLongClick = { viewModel.toggleSelection(conversation.threadId) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopBar(
    count: Int,
    showArchived: Boolean,
    allPinned: Boolean,
    allMuted: Boolean,
    onClose: () -> Unit,
    onDelete: () -> Unit,
    onMarkRead: () -> Unit,
    onArchive: () -> Unit,
    onPin: () -> Unit,
    onMute: () -> Unit,
) {
    TopAppBar(
        title = { Text("$count selected", style = MaterialTheme.typography.titleLarge) },
        navigationIcon = {
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Cancel") }
        },
        actions = {
            IconButton(onClick = onPin) {
                Icon(Icons.Default.PushPin, if (allPinned) "Unpin" else "Pin")
            }
            IconButton(onClick = onMute) {
                Icon(
                    if (allMuted) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff,
                    if (allMuted) "Unmute" else "Mute",
                )
            }
            IconButton(onClick = onMarkRead) { Icon(Icons.Default.DoneAll, "Mark read") }
            IconButton(onClick = onArchive) {
                Icon(
                    if (showArchived) Icons.Default.Unarchive else Icons.Default.Archive,
                    if (showArchived) "Unarchive" else "Archive",
                )
            }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Delete") }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, onClear: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        TextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search messages") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = onClear) { Icon(Icons.Default.Close, "Clear") }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
            ),
        )
    }
}

@Composable
private fun SearchResults(results: List<SearchHit>, onOpenThread: (Long) -> Unit) {
    if (results.isEmpty()) {
        EmptyState(icon = Icons.Default.Search, title = "No matching messages")
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
        items(results, key = { it.message.id }) { hit ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onOpenThread(hit.message.threadId) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(name = hit.displayName, photoUri = null, size = 44.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = hit.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = Formatting.listTimestamp(hit.message.date),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = hit.message.body,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ConversationRow(
    conversation: Conversation,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 1.dp)
            .clip(MaterialTheme.shapes.large)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(
                name = conversation.displayName,
                photoUri = conversation.photoUri,
                isGroup = conversation.isGroup,
                selected = selected,
            )

            Spacer(Modifier.width(14.dp))

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = conversation.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (conversation.hasUnread) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (conversation.pinned) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Default.PushPin,
                            "Pinned",
                            Modifier.size(13.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (conversation.muted) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Default.NotificationsOff,
                            "Muted",
                            Modifier.size(13.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = Formatting.listTimestamp(conversation.date),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (conversation.hasUnread) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (conversation.hasUnread) FontWeight.Bold
                        else FontWeight.Normal,
                    )
                }

                Spacer(Modifier.height(3.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = conversation.snippet.ifBlank { "No messages" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (conversation.hasUnread) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (conversation.hasUnread) FontWeight.Medium
                        else FontWeight.Normal,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    // A dot reads more calmly than a numeric badge in a dense list.
                    if (conversation.hasUnread) {
                        Spacer(Modifier.width(10.dp))
                        Box(
                            Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }

                conversation.ruleLabel?.let { label ->
                    Spacer(Modifier.height(5.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.NotificationsActive,
                            null,
                            Modifier.size(13.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
