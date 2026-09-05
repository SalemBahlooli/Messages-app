package com.claude.messages.ui.thread

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.claude.messages.data.model.Message
import com.claude.messages.ui.compose.Avatar
import com.claude.messages.util.Formatting

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ThreadScreen(
    viewModel: ThreadViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var menuOpen by remember { mutableStateOf(false) }
    var ruleDialogOpen by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Message?>(null) }

    // Keep the newest message in view as the thread grows.
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(
                            name = state.title,
                            photoUri = state.photoUri,
                            size = 36.dp,
                            isGroup = state.isGroup,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(state.title, style = MaterialTheme.typography.titleMedium)
                            state.forcedRuleId?.let { id ->
                                val name = state.availableRules.firstOrNull { it.id == id }?.name
                                if (name != null) {
                                    Text(
                                        "Rule: $name",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.saveDraft(); onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { ruleDialogOpen = true }) {
                        Icon(Icons.Default.Tune, "Notification rule")
                    }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, "More")
                    }
                    DropdownMenu(menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(if (state.muted) "Unmute" else "Mute notifications") },
                            leadingIcon = { Icon(Icons.Default.NotificationsOff, null) },
                            onClick = { menuOpen = false; viewModel.toggleMute() },
                        )
                        DropdownMenuItem(
                            text = { Text("Notification rule for this chat") },
                            leadingIcon = { Icon(Icons.Default.Tune, null) },
                            onClick = { menuOpen = false; ruleDialogOpen = true },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete conversation") },
                            onClick = {
                                menuOpen = false
                                viewModel.deleteThread(onBack)
                            },
                        )
                    }
                },
            )
        },
        bottomBar = {
            Composer(
                draft = state.draft,
                sending = state.sending,
                onDraftChange = viewModel::onDraftChange,
                onSend = viewModel::send,
            )
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 12.dp,
                vertical = 8.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            itemsIndexed(state.messages, key = { _, m -> m.id }) { index, message ->
                val previous = state.messages.getOrNull(index - 1)
                if (previous == null || !Formatting.sameDay(previous.date, message.date)) {
                    DaySeparator(message.date)
                }
                MessageBubble(
                    message = message,
                    onLongClick = { pendingDelete = message },
                    onRetry = { viewModel.resend(message) },
                )
            }
        }
    }

    if (ruleDialogOpen) {
        RulePickerDialog(
            rules = state.availableRules,
            selectedId = state.forcedRuleId,
            onSelect = {
                viewModel.setForcedRule(it)
                ruleDialogOpen = false
            },
            onDismiss = { ruleDialogOpen = false },
        )
    }

    pendingDelete?.let { message ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete message?") },
            text = { Text(message.body, maxLines = 4) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteMessage(message.id)
                    pendingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun DaySeparator(millis: Long) {
    Text(
        text = Formatting.daySeparator(millis),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
    )
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: Message,
    onLongClick: () -> Unit,
    onRetry: () -> Unit,
) {
    val outgoing = message.isOutgoing
    var showDetail by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (outgoing) Alignment.End else Alignment.Start,
    ) {
        Surface(
            color = if (outgoing) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (outgoing) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurface,
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (outgoing) 18.dp else 4.dp,
                bottomEnd = if (outgoing) 4.dp else 18.dp,
            ),
            modifier = Modifier
                .widthIn(max = 300.dp)
                .combinedClickable(
                    onClick = { showDetail = !showDetail },
                    onLongClick = onLongClick,
                ),
        ) {
            Text(
                text = message.body,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        ) {
            if (showDetail || message.isFailed) {
                Text(
                    text = Formatting.fullTimestamp(message.date),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(4.dp))
            }
            when {
                message.isFailed -> {
                    Icon(
                        Icons.Default.ErrorOutline,
                        "Not sent",
                        Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                    TextButton(onClick = onRetry) { Text("Retry") }
                }

                message.isSending -> Icon(
                    Icons.Default.Schedule,
                    "Sending",
                    Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                outgoing && message.status == Message.STATUS_COMPLETE -> Icon(
                    Icons.Default.DoneAll,
                    "Delivered",
                    Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )

                outgoing -> Icon(
                    Icons.Default.Check,
                    "Sent",
                    Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Composer(
    draft: String,
    sending: Boolean,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Text message") },
                shape = RoundedCornerShape(24.dp),
                maxLines = 5,
                supportingText = {
                    if (draft.length > 140) {
                        val parts = (draft.length / 153) + 1
                        Text("${draft.length} characters · $parts SMS")
                    }
                },
            )
            Spacer(Modifier.width(8.dp))
            FilledIconButton(
                onClick = onSend,
                enabled = draft.isNotBlank() && !sending,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, "Send")
            }
        }
    }
}

@Composable
private fun RulePickerDialog(
    rules: List<com.claude.messages.data.db.NotificationRule>,
    selectedId: Long?,
    onSelect: (Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Notification rule") },
        text = {
            Column {
                Text(
                    "Pin a rule to this conversation, or let conditions decide.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(null) }
                        .padding(vertical = 8.dp),
                ) {
                    RadioButton(selected = selectedId == null, onClick = { onSelect(null) })
                    Text("Automatic (match by conditions)")
                }
                rules.forEach { rule ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(rule.id) }
                            .padding(vertical = 8.dp),
                    ) {
                        RadioButton(
                            selected = selectedId == rule.id,
                            onClick = { onSelect(rule.id) },
                        )
                        Column {
                            Text(rule.name)
                            Text(
                                rule.soundLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}
