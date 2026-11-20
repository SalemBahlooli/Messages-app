package com.claude.messages.ui.thread

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.claude.messages.R
import com.claude.messages.data.db.NotificationRule
import com.claude.messages.data.model.Message
import com.claude.messages.data.repo.ContactsRepository
import com.claude.messages.ui.compose.Avatar
import com.claude.messages.ui.theme.BubbleTextStyle
import com.claude.messages.ui.theme.chatCanvas
import com.claude.messages.util.Formatting
import com.claude.messages.util.SmsLength

@Composable
fun ThreadScreen(
    viewModel: ThreadViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    var ruleDialogOpen by remember { mutableStateOf(false) }
    var actionsFor by remember { mutableStateOf<Message?>(null) }

    // The list uses reverseLayout, so item 0 is the newest message and the
    // conversation opens already pinned to the bottom — no scroll from the top.
    // Only when a new message arrives (and the user is near the bottom) do we
    // glide down to it.
    val newestId = state.messages.lastOrNull()?.id
    LaunchedEffect(newestId) {
        if (newestId != null && listState.firstVisibleItemIndex <= 1) {
            listState.animateScrollToItem(0)
        }
    }

    // Surface send failures instead of failing silently.
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeError()
        }
    }

    val leave = { viewModel.saveDraft(); onBack() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.chatCanvas,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            ThreadHeader(
                state = state,
                onBack = leave,
                onPickRule = { ruleDialogOpen = true },
                onToggleMute = viewModel::toggleMute,
                onDelete = { viewModel.deleteThread(onBack) },
            )
        },
        bottomBar = {
            Composer(
                draft = state.draft,
                canSend = state.canSend,
                onDraftChange = viewModel::onDraftChange,
                onSend = viewModel::send,
            )
        },
    ) { padding ->
        // Bubbles take a share of the width rather than a fixed 300dp, so they
        // stay proportionate on a small screen and on a tablet alike.
        BoxWithConstraints(Modifier.padding(padding).fillMaxSize()) {
            val maxBubbleWidth = maxWidth * 0.78f

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                reverseLayout = true,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            ) {
                // Newest first, so with reverseLayout the newest sits at the bottom.
                val messages = state.messages
                items(count = messages.size, key = { messages[messages.lastIndex - it].id }) { i ->
                    val index = messages.lastIndex - i
                    val message = messages[index]
                    val previous = messages.getOrNull(index - 1)
                    val next = messages.getOrNull(index + 1)
                    val newDay = previous == null || !Formatting.sameDay(previous.date, message.date)

                    Column(Modifier.fillMaxWidth()) {
                        if (newDay) DaySeparator(message.date)

                        // Consecutive messages from the same side are visually grouped.
                        val firstOfGroup = newDay ||
                            previous?.isOutgoing != message.isOutgoing ||
                            previous.address != message.address
                        val lastOfGroup = next == null ||
                            next.isOutgoing != message.isOutgoing ||
                            next.address != message.address ||
                            !Formatting.sameDay(next.date, message.date)

                        MessageBubble(
                            message = message,
                            maxWidth = maxBubbleWidth,
                            firstOfGroup = firstOfGroup,
                            lastOfGroup = lastOfGroup,
                            showSender = state.isGroup && firstOfGroup && message.isIncoming,
                            onLongClick = { actionsFor = message },
                            onRetry = { viewModel.resend(message) },
                        )
                    }
                }
            }
        }
    }

    if (ruleDialogOpen) {
        RulePickerDialog(
            rules = state.availableRules,
            selectedId = state.forcedRuleId,
            onSelect = { viewModel.setForcedRule(it) },
            onDismiss = { ruleDialogOpen = false },
        )
    }

    actionsFor?.let { message ->
        MessageActionsDialog(
            message = message,
            onCopy = {
                viewModel.copyText(message)
                actionsFor = null
            },
            onDelete = {
                viewModel.deleteMessage(message.id)
                actionsFor = null
            },
            onDismiss = { actionsFor = null },
        )
    }
}

/** Compact conversation header: avatar, name, state, actions — on one 52dp row. */
@Composable
private fun ThreadHeader(
    state: ThreadUiState,
    onBack: () -> Unit,
    onPickRule: () -> Unit,
    onToggleMute: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 2.dp, end = 4.dp)
                .heightIn(min = 54.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    stringResource(R.string.back),
                    Modifier.size(20.dp),
                )
            }
            Avatar(
                name = state.title,
                photoUri = state.photoUri,
                size = 34.dp,
                isGroup = state.isGroup,
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    state.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val subtitle = when {
                    state.muted -> stringResource(R.string.muted)
                    state.forcedRuleId != null -> state.availableRules
                        .firstOrNull { it.id == state.forcedRuleId }
                        ?.let { "${stringResource(R.string.rule_for_chat)}: ${it.name}" }

                    else -> null
                }
                subtitle?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(onClick = onPickRule, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.Tune, stringResource(R.string.rule_for_chat), Modifier.size(19.dp))
            }
            Box {
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.MoreVert, stringResource(R.string.more), Modifier.size(19.dp))
                }
                DropdownMenu(menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(
                                    if (state.muted) R.string.unmute
                                    else R.string.mute_notifications
                                )
                            )
                        },
                        leadingIcon = {
                            Icon(
                                if (state.muted) Icons.Default.NotificationsActive
                                else Icons.Default.NotificationsOff,
                                null,
                            )
                        },
                        onClick = { menuOpen = false; onToggleMute() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.rule_for_chat)) },
                        leadingIcon = { Icon(Icons.Default.Tune, null) },
                        onClick = { menuOpen = false; onPickRule() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete_conversation)) },
                        leadingIcon = { Icon(Icons.Default.Delete, null) },
                        onClick = { menuOpen = false; onDelete() },
                    )
                }
            }
        }
    }
}

@Composable
private fun DaySeparator(millis: Long) {
    Row(
        Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            Text(
                text = Formatting.daySeparator(LocalContext.current, millis),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            )
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: Message,
    maxWidth: androidx.compose.ui.unit.Dp,
    firstOfGroup: Boolean,
    lastOfGroup: Boolean,
    showSender: Boolean,
    onLongClick: () -> Unit,
    onRetry: () -> Unit,
) {
    val outgoing = message.isOutgoing

    // Round everywhere, with one tucked corner pointing at the sender's side on
    // the last bubble of a run. Nothing in the thread has a square corner.
    val round = 20.dp
    val tucked = 7.dp
    val shape = RoundedCornerShape(
        topStart = if (!outgoing && !firstOfGroup) tucked else round,
        topEnd = if (outgoing && !firstOfGroup) tucked else round,
        bottomStart = if (!outgoing && lastOfGroup) tucked else round,
        bottomEnd = if (outgoing && lastOfGroup) tucked else round,
    )

    val container = when {
        message.isFailed -> MaterialTheme.colorScheme.errorContainer
        outgoing -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val onContainer = when {
        message.isFailed -> MaterialTheme.colorScheme.onErrorContainer
        outgoing -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurface
    }
    val background by animateColorAsState(container, label = "bubbleColor")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (firstOfGroup) 8.dp else 2.dp),
        horizontalAlignment = if (outgoing) Alignment.End else Alignment.Start,
    ) {
        if (showSender) {
            Text(
                text = ContactsRepository.formatNumber(message.address),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 14.dp, bottom = 3.dp),
            )
        }

        Surface(
            color = background,
            contentColor = onContainer,
            shape = shape,
            modifier = Modifier
                .widthIn(max = maxWidth)
                .combinedClickable(onClick = {}, onLongClick = onLongClick),
        ) {
            Column(Modifier.padding(start = 13.dp, end = 13.dp, top = 8.dp, bottom = 6.dp)) {
                Text(
                    text = message.body,
                    style = BubbleTextStyle,
                )
                // The timestamp lives inside the bubble, on every message: a
                // thread you have to tap to date is not a readable thread.
                Row(
                    modifier = Modifier.align(Alignment.End).padding(top = 1.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = Formatting.messageTimestamp(message.date),
                        style = MaterialTheme.typography.labelSmall,
                        color = onContainer.copy(alpha = 0.7f),
                    )
                    if (outgoing) {
                        Spacer(Modifier.width(4.dp))
                        val (icon, label) = when {
                            message.isFailed ->
                                Icons.Default.ErrorOutline to stringResource(R.string.not_sent)

                            message.isSending ->
                                Icons.Default.Schedule to stringResource(R.string.sending)

                            message.status == Message.STATUS_COMPLETE ->
                                Icons.Default.DoneAll to stringResource(R.string.delivered)

                            else -> Icons.Default.Check to stringResource(R.string.sent)
                        }
                        Icon(
                            icon,
                            label,
                            Modifier.size(13.dp),
                            tint = onContainer.copy(alpha = 0.8f),
                        )
                    }
                }
            }
        }

        if (message.isFailed) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 4.dp),
            ) {
                Text(
                    stringResource(R.string.not_sent),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(onClick = onRetry, modifier = Modifier.height(30.dp)) {
                    Icon(Icons.Default.Refresh, null, Modifier.size(13.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.retry), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun Composer(
    draft: String,
    canSend: Boolean,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 9.dp)) {
                    Box {
                        if (draft.isEmpty()) {
                            Text(
                                stringResource(R.string.text_message),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        BasicTextField(
                            value = draft,
                            onValueChange = onDraftChange,
                            maxLines = 6,
                            textStyle = LocalTextStyle.current.merge(
                                MaterialTheme.typography.bodyLarge
                            ).copy(color = MaterialTheme.colorScheme.onSurface),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    // Only worth showing once a message will actually be split.
                    val info = SmsLength.describe(LocalContext.current, draft)
                    if (info != null) {
                        Text(
                            text = info,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.width(8.dp))

            val sendBackground by animateColorAsState(
                if (canSend) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceContainerHighest,
                label = "sendBackground",
            )
            Surface(
                shape = CircleShape,
                color = sendBackground,
                contentColor = if (canSend) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(44.dp),
            ) {
                Box(
                    Modifier.clickable(enabled = canSend, onClick = onSend),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        stringResource(R.string.send),
                        Modifier.size(19.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageActionsDialog(
    message: Message,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.message_label)) },
        text = {
            Column {
                Text(
                    message.body,
                    maxLines = 5,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    Formatting.fullTimestamp(message.date),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, null, Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onCopy) {
                Icon(Icons.Default.ContentCopy, null, Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.copy))
            }
        },
    )
}

@Composable
private fun RulePickerDialog(
    rules: List<NotificationRule>,
    selectedId: Long?,
    onSelect: (Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rule_for_chat)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.rule_for_chat_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                RuleOption(
                    label = stringResource(R.string.rule_automatic),
                    detail = stringResource(R.string.rule_automatic_hint),
                    selected = selectedId == null,
                    onClick = { onSelect(null) },
                )
                rules.forEach { rule ->
                    RuleOption(
                        label = rule.name,
                        detail = rule.soundLabel,
                        selected = selectedId == rule.id,
                        onClick = { onSelect(rule.id) },
                    )
                }
                if (rules.isEmpty()) {
                    Text(
                        stringResource(R.string.no_rules_inline),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } },
    )
}

@Composable
private fun RuleOption(
    label: String,
    detail: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(4.dp))
        Column {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
