package com.claude.messages.ui.thread

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.claude.messages.R
import com.claude.messages.data.db.ScheduledMessage
import com.claude.messages.data.model.Message
import com.claude.messages.data.prefs.FeaturePrefs
import com.claude.messages.ui.compose.Avatar
import com.claude.messages.ui.theme.chatCanvas
import com.claude.messages.util.Formatting
import com.claude.messages.util.OtpDetector
import com.claude.messages.util.SmsLength
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.ceil

@Composable
fun ThreadScreen(
    viewModel: ThreadViewModel,
    onBack: () -> Unit,
    onForward: (String) -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val features by FeaturePrefs.settings.collectAsState()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var ruleDialogOpen by remember { mutableStateOf(false) }
    var actionsFor by remember { mutableStateOf<Message?>(null) }
    var scheduleOpen by remember { mutableStateOf(false) }
    var scheduledDetail by remember { mutableStateOf<ScheduledMessage?>(null) }
    var manageReplies by remember { mutableStateOf(false) }

    fun toast(text: String) {
        scope.launch { snackbarHostState.showSnackbar(text) }
    }

    fun copyCode(code: String) {
        context.getSystemService(ClipboardManager::class.java)
            ?.setPrimaryClip(ClipData.newPlainText("code", code))
        toast(context.getString(R.string.code_copied, code))
    }

    // The list is laid out bottom-up (reverseLayout), so it opens already at the
    // newest message. Index 0 is the bottom; only scroll when a new one arrives.
    LaunchedEffect(state.messages.lastOrNull()?.id) {
        if (state.messages.isNotEmpty() && listState.firstVisibleItemIndex > 0) {
            listState.animateScrollToItem(0)
        }
    }

    // Older messages load as the user nears the top of what is loaded.
    val nearOldest by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 8
        }
    }
    LaunchedEffect(nearOldest, state.messages.size) {
        if (nearOldest && state.hasMore) viewModel.loadMore()
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ThreadEvent.Share -> runCatching { context.startActivity(event.intent) }
                is ThreadEvent.Toast -> toast(event.text)
            }
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
                onToggleBlock = viewModel::toggleBlock,
                onExport = viewModel::exportConversation,
                onDelete = { viewModel.deleteThread(onBack) },
            )
        },
        bottomBar = {
            Composer(
                state = state,
                quickReplies = features.quickReplies,
                onDraftChange = viewModel::onDraftChange,
                onSend = viewModel::send,
                onSchedule = { scheduleOpen = true },
                onQuickReply = { reply ->
                    viewModel.onDraftChange(
                        if (state.draft.isBlank()) reply else state.draft.trimEnd() + " " + reply
                    )
                },
                onManageReplies = { manageReplies = true },
                onUndo = viewModel::undoSend,
            )
        },
    ) { padding ->
        // Bubbles take a share of the width rather than a fixed 300dp, so they
        // stay proportionate on a small screen and on a tablet alike.
        BoxWithConstraints(Modifier.padding(padding).fillMaxSize()) {
            val maxBubbleWidth = maxWidth * 0.78f
            val messages = state.messages
            // Bottom-up: the latest scheduled message sits lowest.
            val scheduled = remember(state.scheduled) { state.scheduled.sortedByDescending { it.sendAt } }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                reverseLayout = true,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            ) {
                items(scheduled, key = { "s${it.id}" }) { item ->
                    ScheduledBubble(
                        item = item,
                        maxWidth = maxBubbleWidth,
                        onClick = { scheduledDetail = item },
                    )
                }

                items(
                    count = messages.size,
                    key = { i -> "m${messages[messages.lastIndex - i].id}" },
                ) { i ->
                    val index = messages.lastIndex - i
                    val message = messages[index]
                    val previous = messages.getOrNull(index - 1)
                    val next = messages.getOrNull(index + 1)
                    val newDay = previous == null || !Formatting.sameDay(previous.date, message.date)

                    if (newDay) DaySeparator(message.date)

                    // Consecutive messages from the same side are visually grouped.
                    val firstOfGroup = newDay ||
                        previous?.isOutgoing != message.isOutgoing ||
                        previous.address != message.address
                    val lastOfGroup = next == null ||
                        next.isOutgoing != message.isOutgoing ||
                        next.address != message.address ||
                        !Formatting.sameDay(next.date, message.date)

                    val code = if (features.otpHelper && message.isIncoming) {
                        remember(message.body) { OtpDetector.find(message.body) }
                    } else {
                        null
                    }

                    MessageBubble(
                        message = message,
                        maxWidth = maxBubbleWidth,
                        firstOfGroup = firstOfGroup,
                        lastOfGroup = lastOfGroup,
                        showSender = state.isGroup && firstOfGroup && message.isIncoming,
                        starred = message.id in state.starredIds,
                        otpCode = code,
                        onCopyCode = ::copyCode,
                        onLongClick = { actionsFor = message },
                        onRetry = { viewModel.resend(message) },
                    )
                }
            }

            val showJump by remember { derivedStateOf { listState.firstVisibleItemIndex > 3 } }
            AnimatedVisibility(
                visible = showJump,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut(),
                modifier = Modifier.align(Alignment.BottomEnd).padding(14.dp),
            ) {
                SmallFloatingActionButton(
                    onClick = { scope.launch { listState.animateScrollToItem(0) } },
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.primary,
                ) {
                    Icon(Icons.Default.KeyboardArrowDown, stringResource(R.string.scroll_to_latest))
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
        val code = remember(message.body) {
            if (message.isIncoming) OtpDetector.find(message.body) else null
        }
        MessageActionsDialog(
            message = message,
            starred = message.id in state.starredIds,
            otpCode = code,
            onCopy = {
                viewModel.copyText(message)
                actionsFor = null
            },
            onCopyCode = { c ->
                copyCode(c)
                actionsFor = null
            },
            onStar = {
                viewModel.toggleStar(message)
                actionsFor = null
            },
            onForward = {
                actionsFor = null
                viewModel.saveDraft()
                onForward(message.body)
            },
            onSpeak = {
                viewModel.speak(message)
                actionsFor = null
            },
            onDelete = {
                viewModel.deleteMessage(message.id)
                actionsFor = null
            },
            onDismiss = { actionsFor = null },
        )
    }

    if (scheduleOpen) {
        ScheduleDialog(
            onPick = { at ->
                scheduleOpen = false
                viewModel.schedule(at)
            },
            onDismiss = { scheduleOpen = false },
        )
    }

    scheduledDetail?.let { item ->
        ScheduledDetailDialog(
            item = item,
            onSendNow = {
                viewModel.sendScheduledNow(item.id)
                scheduledDetail = null
            },
            onCancel = {
                viewModel.cancelScheduled(item.id)
                scheduledDetail = null
            },
            onDismiss = { scheduledDetail = null },
        )
    }

    if (manageReplies) {
        QuickRepliesDialog(
            replies = features.quickReplies,
            onAdd = FeaturePrefs::addQuickReply,
            onRemove = FeaturePrefs::removeQuickReply,
            onDismiss = { manageReplies = false },
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
    onToggleBlock: () -> Unit,
    onExport: () -> Unit,
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
                    state.blocked -> stringResource(R.string.blocked_label)
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
                        color = if (state.blocked) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary,
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
                    if (state.threadId > 0) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.export_chat)) },
                            leadingIcon = { Icon(Icons.Default.Share, null) },
                            onClick = { menuOpen = false; onExport() },
                        )
                    }
                    if (state.recipients.size == 1) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(
                                        if (state.blocked) R.string.unblock_number
                                        else R.string.block_number
                                    )
                                )
                            },
                            leadingIcon = { Icon(Icons.Default.Block, null) },
                            onClick = { menuOpen = false; onToggleBlock() },
                        )
                    }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Composer(
    state: ThreadUiState,
    quickReplies: List<String>,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onSchedule: () -> Unit,
    onQuickReply: (String) -> Unit,
    onManageReplies: () -> Unit,
    onUndo: () -> Unit,
) {
    val draft = state.draft
    val canSend = state.canSend

    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding(),
        ) {
            AnimatedVisibility(
                visible = state.pendingSend != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                state.pendingSend?.let { PendingSendBar(it, onUndo) }
            }

            AnimatedVisibility(
                visible = draft.isEmpty() && state.pendingSend == null &&
                    quickReplies.isNotEmpty() && state.recipients.isNotEmpty(),
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                QuickReplyRow(quickReplies, onQuickReply, onManageReplies)
            }

            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Column(
                            Modifier
                                .weight(1f)
                                .padding(start = 14.dp, end = 6.dp, top = 9.dp, bottom = 9.dp)
                        ) {
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
                        // Scheduling is offered as soon as there is something to send.
                        AnimatedVisibility(visible = canSend) {
                            IconButton(onClick = onSchedule, modifier = Modifier.size(40.dp)) {
                                Icon(
                                    Icons.Outlined.Schedule,
                                    stringResource(R.string.schedule_send),
                                    Modifier.size(19.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
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
                        // Long-press is a shortcut to scheduling.
                        Modifier.combinedClickable(
                            enabled = canSend,
                            onClick = onSend,
                            onLongClick = onSchedule,
                        ),
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
}

/** Countdown with an Undo button while a just-sent message can still be taken back. */
@Composable
private fun PendingSendBar(pending: PendingSend, onUndo: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(pending) {
        while (true) {
            now = System.currentTimeMillis()
            delay(100)
        }
    }
    val totalMs = pending.seconds * 1000f
    val leftMs = (pending.sendAt - now).coerceAtLeast(0L)
    val secondsLeft = ceil(leftMs / 1000.0).toInt().coerceAtLeast(1)

    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.Schedule,
                null,
                Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.sending_in, secondsLeft),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    pending.body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(onClick = onUndo) { Text(stringResource(R.string.undo)) }
        }
        LinearProgressIndicator(
            progress = { (leftMs / totalMs).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickReplyRow(
    replies: List<String>,
    onPick: (String) -> Unit,
    onManage: () -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
    ) {
        items(replies, key = { it }) { reply ->
            SuggestionChip(
                onClick = { onPick(reply) },
                label = {
                    Text(
                        reply,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                shape = CircleShape,
            )
        }
        item(key = "__manage") {
            IconButton(onClick = onManage, modifier = Modifier.size(32.dp).padding(top = 4.dp)) {
                Icon(
                    Icons.Default.Edit,
                    stringResource(R.string.quick_replies_manage),
                    Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
