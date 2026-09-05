package com.claude.messages.ui.conversations

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.claude.messages.data.model.Conversation
import com.claude.messages.data.model.Message
import com.claude.messages.ui.compose.Avatar
import com.claude.messages.ui.compose.EmptyState
import com.claude.messages.util.Formatting

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun ConversationsScreen(
    viewModel: ConversationsViewModel,
    onOpenThread: (Long) -> Unit,
    onNewMessage: () -> Unit,
    onOpenRules: () -> Unit,
    onOpenSettings: () -> Unit,
    onRequestDefaultSms: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var menuOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            when {
                state.inSelectionMode -> SelectionTopBar(
                    count = state.selected.size,
                    showArchived = state.showArchived,
                    onClose = viewModel::clearSelection,
                    onDelete = viewModel::deleteSelected,
                    onMarkRead = viewModel::markSelectedRead,
                    onArchive = { viewModel.archiveSelected(!state.showArchived) },
                    onPin = { viewModel.pinSelected(true) },
                    onMute = { viewModel.muteSelected(true) },
                )

                else -> TopAppBar(
                    title = {
                        Text(if (state.showArchived) "Archived" else "Messages")
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
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreVert, "More")
                        }
                        DropdownMenu(menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(if (state.showArchived) "All messages" else "Archived") },
                                onClick = {
                                    menuOpen = false
                                    viewModel.toggleArchivedView()
                                },
                                leadingIcon = { Icon(Icons.Default.Archive, null) },
                            )
                            DropdownMenuItem(
                                text = { Text("Notification rules") },
                                onClick = { menuOpen = false; onOpenRules() },
                                leadingIcon = { Icon(Icons.Default.Tune, null) },
                            )
                            DropdownMenuItem(
                                text = { Text("Settings") },
                                onClick = { menuOpen = false; onOpenSettings() },
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            }
        },
        floatingActionButton = {
            if (!state.inSelectionMode && !state.showArchived) {
                ExtendedFloatingActionButton(
                    onClick = onNewMessage,
                    icon = { Icon(Icons.Default.Edit, null) },
                    text = { Text("Start chat") },
                )
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {

            if (!state.isDefaultSmsApp) {
                DefaultSmsBanner(onRequestDefaultSms)
            }

            SearchField(
                query = state.searchQuery,
                onQueryChange = viewModel::onSearchQueryChange,
                onClear = viewModel::closeSearch,
            )

            when {
                state.searching -> SearchResults(state.searchResults, onOpenThread)

                state.conversations.isEmpty() && !state.loading -> EmptyState(
                    icon = Icons.AutoMirrored.Filled.Message,
                    title = if (state.showArchived) "Nothing archived" else "No conversations yet",
                    subtitle = if (state.showArchived) null
                    else "Tap Start chat to send your first message.",
                )

                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(state.conversations, key = { it.threadId }) { conversation ->
                        ConversationRow(
                            conversation = conversation,
                            selected = conversation.threadId in state.selected,
                            selectionMode = state.inSelectionMode,
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
    onClose: () -> Unit,
    onDelete: () -> Unit,
    onMarkRead: () -> Unit,
    onArchive: () -> Unit,
    onPin: () -> Unit,
    onMute: () -> Unit,
) {
    TopAppBar(
        title = { Text("$count selected") },
        navigationIcon = {
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Cancel") }
        },
        actions = {
            IconButton(onClick = onArchive) {
                Icon(
                    if (showArchived) Icons.Default.Unarchive else Icons.Default.Archive,
                    if (showArchived) "Unarchive" else "Archive",
                )
            }
            IconButton(onClick = onPin) { Icon(Icons.Default.PushPin, "Pin") }
            IconButton(onClick = onMute) { Icon(Icons.Default.NotificationsOff, "Mute") }
            IconButton(onClick = onMarkRead) { Icon(Icons.Default.DoneAll, "Mark read") }
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
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        placeholder = { Text("Search messages") },
        leadingIcon = { Icon(Icons.Default.Search, null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear) { Icon(Icons.Default.Close, "Clear") }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
    )
}

@Composable
private fun SearchResults(results: List<Message>, onOpenThread: (Long) -> Unit) {
    if (results.isEmpty()) {
        EmptyState(icon = Icons.Default.Search, title = "No matching messages")
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(results, key = { it.id }) { message ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable { onOpenThread(message.threadId) }
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = message.address,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = Formatting.listTimestamp(message.date),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = message.body,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ConversationRow(
    conversation: Conversation,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            Avatar(
                name = conversation.displayName,
                photoUri = conversation.photoUri,
                isGroup = conversation.isGroup,
            )
            if (selected) {
                Box(
                    Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }

        Spacer(Modifier.width(16.dp))

        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = conversation.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (conversation.hasUnread) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (conversation.pinned) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Default.PushPin,
                        "Pinned",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (conversation.muted) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Default.NotificationsOff,
                        "Muted",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = Formatting.listTimestamp(conversation.date),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (conversation.hasUnread) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(2.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = conversation.snippet,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (conversation.hasUnread) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (conversation.hasUnread) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (conversation.unreadCount > 0) {
                    Spacer(Modifier.width(8.dp))
                    Badge { Text(conversation.unreadCount.toString()) }
                }
            }

            // Surfaces which custom notification rule this conversation triggers.
            conversation.ruleLabel?.let { label ->
                Spacer(Modifier.height(6.dp))
                AssistChip(
                    onClick = onClick,
                    label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.NotificationsActive,
                            null,
                            Modifier.size(AssistChipDefaults.IconSize),
                        )
                    },
                    modifier = Modifier.height(28.dp),
                )
            }
        }
    }
    HorizontalDivider(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.padding(start = 80.dp),
    )
}

@Composable
private fun DefaultSmsBanner(onRequest: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Messages isn't your default SMS app",
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Set it as the default to send and receive texts and to use notification rules.",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                AssistChip(onClick = onRequest, label = { Text("Set as default") })
            }
        }
    }
}
