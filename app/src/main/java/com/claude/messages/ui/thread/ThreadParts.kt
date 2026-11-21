package com.claude.messages.ui.thread

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.DateFormat
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.claude.messages.R
import com.claude.messages.data.db.NotificationRule
import com.claude.messages.data.db.ScheduledMessage
import com.claude.messages.data.model.Message
import com.claude.messages.data.repo.ContactsRepository
import com.claude.messages.ui.theme.BubbleTextStyle
import com.claude.messages.util.Formatting
import com.claude.messages.util.TextLink
import com.claude.messages.util.TextLinks
import java.util.Calendar

@Composable
internal fun DaySeparator(millis: Long) {
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

@Composable
internal fun MessageBubble(
    message: Message,
    maxWidth: Dp,
    firstOfGroup: Boolean,
    lastOfGroup: Boolean,
    showSender: Boolean,
    starred: Boolean,
    otpCode: String?,
    onCopyCode: (String) -> Unit,
    onLongClick: () -> Unit,
    onRetry: () -> Unit,
) {
    val outgoing = message.isOutgoing
    val context = LocalContext.current
    val longClick by rememberUpdatedState(onLongClick)

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

    // Links, numbers and addresses inside the text are tappable.
    val links = remember(message.body) { TextLinks.find(message.body) }
    val linkColor = if (outgoing) onContainer else MaterialTheme.colorScheme.primary
    val annotated = remember(message.body, links, linkColor) {
        buildAnnotatedString {
            append(message.body)
            links.forEach {
                addStyle(
                    SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
                    it.start,
                    it.end,
                )
            }
        }
    }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }

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
                .pointerInput(Unit) { detectTapGestures(onLongPress = { longClick() }) },
        ) {
            Column(Modifier.padding(start = 13.dp, end = 13.dp, top = 8.dp, bottom = 6.dp)) {
                Text(
                    text = annotated,
                    style = BubbleTextStyle,
                    onTextLayout = { layout = it },
                    modifier = Modifier.pointerInput(links) {
                        detectTapGestures(
                            onLongPress = { longClick() },
                            onTap = { position ->
                                val offset = layout?.getOffsetForPosition(position)
                                    ?: return@detectTapGestures
                                links.firstOrNull { offset in it.start until it.end }
                                    ?.let { openLink(context, it) }
                            },
                        )
                    },
                )
                // The timestamp lives inside the bubble, on every message: a
                // thread you have to tap to date is not a readable thread.
                Row(
                    modifier = Modifier.align(Alignment.End).padding(top = 1.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (starred) {
                        Icon(
                            Icons.Default.Star,
                            stringResource(R.string.starred),
                            Modifier.size(12.dp),
                            tint = onContainer.copy(alpha = 0.85f),
                        )
                        Spacer(Modifier.width(3.dp))
                    }
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

        if (otpCode != null) {
            AssistChip(
                onClick = { onCopyCode(otpCode) },
                label = {
                    Text(
                        stringResource(R.string.copy_code, otpCode),
                        style = MaterialTheme.typography.labelMedium,
                    )
                },
                leadingIcon = { Icon(Icons.Default.Key, null, Modifier.size(15.dp)) },
                shape = CircleShape,
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    leadingIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                border = null,
                modifier = Modifier.padding(top = 2.dp, start = 4.dp).height(32.dp),
            )
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

/** A message waiting for its time: outlined rather than filled, with a clock. */
@Composable
internal fun ScheduledBubble(item: ScheduledMessage, maxWidth: Dp, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalAlignment = Alignment.End,
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp, 20.dp, 7.dp, 20.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
            modifier = Modifier.widthIn(max = maxWidth).clickable(onClick = onClick),
        ) {
            Column(Modifier.padding(start = 13.dp, end = 13.dp, top = 8.dp, bottom = 6.dp)) {
                Text(item.body, style = BubbleTextStyle)
                Row(
                    Modifier.align(Alignment.End).padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Schedule,
                        null,
                        Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        stringResource(R.string.scheduled_for, Formatting.fullTimestamp(item.sendAt)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

/** Opens a tapped link, number or address in the app that handles it. */
internal fun openLink(context: Context, link: TextLink) {
    val uri = Uri.parse(link.target)
    val intent = when (link.kind) {
        TextLink.Kind.PHONE -> Intent(Intent.ACTION_DIAL, uri)
        TextLink.Kind.EMAIL -> Intent(Intent.ACTION_SENDTO, uri)
        TextLink.Kind.URL -> Intent(Intent.ACTION_VIEW, uri)
    }
    runCatching { context.startActivity(intent) }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 44.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(19.dp), tint = tint)
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = tint)
    }
}

@Composable
internal fun MessageActionsDialog(
    message: Message,
    starred: Boolean,
    otpCode: String?,
    onCopy: () -> Unit,
    onCopyCode: (String) -> Unit,
    onStar: () -> Unit,
    onForward: () -> Unit,
    onSpeak: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            Column {
                // Selectable, so part of a message can be copied.
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    Column(
                        Modifier
                            .heightIn(max = 220.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp)
                    ) {
                        SelectionContainer {
                            Text(message.body, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                Text(
                    Formatting.fullTimestamp(message.date),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp, start = 4.dp),
                )
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(4.dp))
                if (otpCode != null) {
                    ActionRow(
                        Icons.Default.Key,
                        stringResource(R.string.copy_code, otpCode),
                        { onCopyCode(otpCode) },
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                ActionRow(Icons.Default.ContentCopy, stringResource(R.string.copy), onCopy)
                ActionRow(
                    if (starred) Icons.Outlined.StarOutline else Icons.Default.Star,
                    stringResource(if (starred) R.string.unstar else R.string.star),
                    onStar,
                )
                ActionRow(Icons.AutoMirrored.Filled.Forward, stringResource(R.string.forward), onForward)
                ActionRow(Icons.AutoMirrored.Filled.VolumeUp, stringResource(R.string.read_aloud), onSpeak)
                ActionRow(
                    Icons.Default.Delete,
                    stringResource(R.string.delete),
                    onDelete,
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } },
    )
}

/** Quick presets for "send later", plus a full date and time picker. */
@Composable
internal fun ScheduleDialog(onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val presets = remember {
        val now = Calendar.getInstance()
        val inHour = (now.clone() as Calendar).apply { add(Calendar.HOUR_OF_DAY, 1) }
        val tonight = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 21); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val tomorrow = (now.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 8); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        buildList {
            add(R.string.schedule_in_1h to inHour.timeInMillis)
            // "Tonight" only makes sense while tonight is still ahead.
            if (tonight.timeInMillis - now.timeInMillis > 30 * 60_000L) {
                add(R.string.schedule_tonight to tonight.timeInMillis)
            }
            add(R.string.schedule_tomorrow to tomorrow.timeInMillis)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Schedule, null) },
        title = { Text(stringResource(R.string.schedule_send)) },
        text = {
            Column {
                presets.forEach { (label, at) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(at) }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.Schedule,
                            null,
                            Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(stringResource(label), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                Formatting.fullTimestamp(at),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { pickDateTime(context, onPick) }
                        .padding(vertical = 10.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Event,
                        null,
                        Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(stringResource(R.string.schedule_custom), style = MaterialTheme.typography.bodyLarge)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** The platform pickers: they follow the phone's calendar, locale and 12/24h setting. */
private fun pickDateTime(context: Context, onPick: (Long) -> Unit) {
    val now = Calendar.getInstance()
    DatePickerDialog(
        context,
        { _, year, month, day ->
            TimePickerDialog(
                context,
                { _, hour, minute ->
                    val at = Calendar.getInstance().apply {
                        set(year, month, day, hour, minute, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    onPick(at.timeInMillis)
                },
                (now.get(Calendar.HOUR_OF_DAY) + 1) % 24,
                0,
                DateFormat.is24HourFormat(context),
            ).show()
        },
        now.get(Calendar.YEAR),
        now.get(Calendar.MONTH),
        now.get(Calendar.DAY_OF_MONTH),
    ).apply { datePicker.minDate = now.timeInMillis - 1_000 }.show()
}

@Composable
internal fun ScheduledDetailDialog(
    item: ScheduledMessage,
    onSendNow: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Schedule, null) },
        title = { Text(stringResource(R.string.scheduled_title)) },
        text = {
            Column {
                Text(item.body, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.scheduled_for, Formatting.fullTimestamp(item.sendAt)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        confirmButton = { TextButton(onClick = onSendNow) { Text(stringResource(R.string.scheduled_send_now)) } },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.scheduled_cancel), color = MaterialTheme.colorScheme.error)
            }
        },
    )
}

@Composable
internal fun QuickRepliesDialog(
    replies: List<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.quick_replies)) },
        text = {
            Column {
                Column(
                    Modifier
                        .heightIn(max = 260.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    replies.forEach { reply ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                reply,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { onRemove(reply) }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.Close, stringResource(R.string.delete), Modifier.size(16.dp))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        placeholder = { Text(stringResource(R.string.quick_reply_hint)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        enabled = text.isNotBlank(),
                        onClick = {
                            onAdd(text)
                            text = ""
                        },
                    ) { Text(stringResource(R.string.quick_reply_add)) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } },
    )
}

@Composable
internal fun RulePickerDialog(
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
