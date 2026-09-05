package com.claude.messages.ui.thread

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.claude.messages.data.db.NotificationRule
import com.claude.messages.data.model.Message
import com.claude.messages.ui.compose.Avatar
import com.claude.messages.ui.theme.BubbleTextStyle
import com.claude.messages.util.Formatting
import com.claude.messages.util.SmsLength

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ThreadScreen(
    viewModel: ThreadViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    var menuOpen by remember { mutableStateOf(false) }
    var ruleDialogOpen by remember { mutableStateOf(false) }
    var actionsFor by remember { mutableStateOf<Message?>(null) }

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
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
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(
                            name = state.title,
                            photoUri = state.photoUri,
                            size = 38.dp,
                            isGroup = state.isGroup,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                state.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                            )
                            val subtitle = when {
                                state.muted -> "Muted"
                                state.forcedRuleId != null -> state.availableRules
                                    .firstOrNull { it.id == state.forcedRuleId }
                                    ?.let { "Rule: ${it.name}" }

                                else -> null
                            }
                            subtitle?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = leave) {
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
                            leadingIcon = {
                                Icon(
                                    if (state.muted) Icons.Default.NotificationsActive
                                    else Icons.Default.NotificationsOff,
                                    null,
                                )
                            },
                            onClick = { menuOpen = false; viewModel.toggleMute() },
                        )
                        DropdownMenuItem(
                            text = { Text("Notification rule") },
                            leadingIcon = { Icon(Icons.Default.Tune, null) },
                            onClick = { menuOpen = false; ruleDialogOpen = true },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete conversation") },
                            leadingIcon = { Icon(Icons.Default.Delete, null) },
                            onClick = { menuOpen = false; viewModel.deleteThread(onBack) },
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
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
        LazyColumn(
            state = listState,
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        ) {
            itemsIndexed(state.messages, key = { _, m -> m.id }) { index, message ->
                val previous = state.messages.getOrNull(index - 1)
                val next = state.messages.getOrNull(index + 1)
                val newDay = previous == null || !Formatting.sameDay(previous.date, message.date)

                if (newDay) DaySeparator(message.date)

                // Consecutive messages from the same side are visually grouped.
                val firstOfGroup = newDay || previous?.isOutgoing != message.isOutgoing
                val lastOfGroup = next == null ||
                    next.isOutgoing != message.isOutgoing ||
                    !Formatting.sameDay(next.date, message.date)

                MessageBubble(
                    message = message,
                    firstOfGroup = firstOfGroup,
                    lastOfGroup = lastOfGroup,
                    onLongClick = { actionsFor = message },
                    onRetry = { viewModel.resend(message) },
                )
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

@Composable
private fun DaySeparator(millis: Long) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Text(
                text = Formatting.daySeparator(millis),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: Message,
    firstOfGroup: Boolean,
    lastOfGroup: Boolean,
    onLongClick: () -> Unit,
    onRetry: () -> Unit,
) {
    val outgoing = message.isOutgoing
    var showDetail by remember { mutableStateOf(false) }

    val big = 20.dp
    val small = 6.dp
    val shape = RoundedCornerShape(
        topStart = if (outgoing || firstOfGroup) big else small,
        topEnd = if (!outgoing || firstOfGroup) big else small,
        bottomStart = if (outgoing || lastOfGroup) big else small,
        bottomEnd = if (!outgoing || lastOfGroup) big else small,
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (firstOfGroup) 6.dp else 2.dp),
        horizontalAlignment = if (outgoing) Alignment.End else Alignment.Start,
    ) {
        Surface(
            color = when {
                message.isFailed -> MaterialTheme.colorScheme.errorContainer
                outgoing -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.surfaceContainerHigh
            },
            contentColor = when {
                message.isFailed -> MaterialTheme.colorScheme.onErrorContainer
                outgoing -> MaterialTheme.colorScheme.onPrimary
                else -> MaterialTheme.colorScheme.onSurface
            },
            shape = shape,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .combinedClickable(
                    onClick = { showDetail = !showDetail },
                    onLongClick = onLongClick,
                ),
        ) {
            Text(
                text = message.body,
                modifier = Modifier.padding(horizontal = 15.dp, vertical = 9.dp),
                style = BubbleTextStyle,
            )
        }

        if (showDetail || message.isFailed || (lastOfGroup && outgoing)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            ) {
                if (showDetail || message.isFailed) {
                    Text(
                        text = Formatting.fullTimestamp(message.date),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(6.dp))
                }
                when {
                    message.isFailed -> {
                        Icon(
                            Icons.Default.ErrorOutline,
                            "Not sent",
                            Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "Not sent",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        TextButton(onClick = onRetry, modifier = Modifier.height(30.dp)) {
                            Icon(Icons.Default.Refresh, null, Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Retry", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    message.isSending -> StatusLabel(Icons.Default.Schedule, "Sending")

                    outgoing && message.status == Message.STATUS_COMPLETE ->
                        StatusLabel(Icons.Default.DoneAll, "Delivered", highlight = true)

                    outgoing -> StatusLabel(Icons.Default.Check, "Sent")
                }
            }
        }
    }
}

@Composable
private fun StatusLabel(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    highlight: Boolean = false,
) {
    val tint = if (highlight) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, label, Modifier.size(13.dp), tint = tint)
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Composer(
    draft: String,
    canSend: Boolean,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    val sendScale by animateFloatAsState(if (canSend) 1f else 0.85f, label = "sendScale")

    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(26.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Column {
                    TextField(
                        value = draft,
                        onValueChange = onDraftChange,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Text message") },
                        maxLines = 6,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                    )
                    // Only worth showing once a message will actually be split.
                    val info = SmsLength.describe(draft)
                    if (info != null) {
                        Text(
                            text = info,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.width(8.dp))

            FilledIconButton(
                onClick = onSend,
                enabled = canSend,
                modifier = Modifier.size(52.dp).scale(sendScale),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, "Send")
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
        title = { Text("Message") },
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
                Icon(Icons.Default.Delete, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onCopy) {
                Icon(Icons.Default.ContentCopy, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Copy")
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
        title = { Text("Notification rule") },
        text = {
            Column {
                Text(
                    "Pin a rule to this conversation, or let the conditions decide.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                RuleOption(
                    label = "Automatic",
                    detail = "Match by conditions",
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
                        "No rules yet — create one from the tuner icon on the conversation list.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
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
            .padding(vertical = 6.dp),
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
