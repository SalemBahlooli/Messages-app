package com.claude.messages.ui.rules

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.claude.messages.R
import com.claude.messages.data.db.ContentMatch
import com.claude.messages.data.db.RuleImportance
import com.claude.messages.data.db.SenderMatch
import com.claude.messages.data.db.VibrationPattern
import com.claude.messages.ui.compose.AppHeader
import com.claude.messages.notifications.RuleTextFormatter
import com.claude.messages.ui.theme.AvatarColors

/**
 * Editor for one notification rule, laid out as the sentence it represents:
 * **When** a message matches these conditions, **alert me like this**. Each
 * half is its own card so the two halves never blur together, and detail fields
 * appear only once the choice above them needs them.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RuleEditorScreen(
    viewModel: RulesViewModel,
    onDone: () -> Unit,
) {
    val state by viewModel.editor.collectAsStateWithLifecycle()
    val rule = state.rule
    val context = LocalContext.current
    val formatter = remember(context) { RuleTextFormatter(context) }

    LaunchedEffect(state.saved) {
        if (state.saved) {
            viewModel.consumeSaved()
            onDone()
        }
    }

    val ringtonePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            @Suppress("DEPRECATION")
            val uri: Uri? = result.data
                ?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            viewModel.setSound(uri)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            AppHeader(
                title = stringResource(
                    if (state.isNew) R.string.new_rule else R.string.edit_rule
                ),
                onBack = onDone,
            ) {
                Button(
                    onClick = viewModel::save,
                    modifier = Modifier.padding(end = 8.dp),
                ) { Text(stringResource(R.string.save)) }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.problem?.let { problem ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        formatter.problemText(problem),
                        Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }

            OutlinedTextField(
                value = rule.name,
                onValueChange = { name -> viewModel.updateRule { it.copy(name = name) } },
                label = { Text(stringResource(R.string.rule_name)) },
                placeholder = { Text(stringResource(R.string.rule_name_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // ---------------------------------------------------------- WHEN
            SectionCard(
                title = stringResource(R.string.section_when),
                icon = Icons.Default.Tune,
            ) {
                FieldLabel(stringResource(R.string.from_sender))
                ChipRow(
                    options = SenderMatch.entries.map { it to formatter.label(it) },
                    selected = rule.senderMatch,
                    onSelect = { match -> viewModel.updateRule { it.copy(senderMatch = match) } },
                )
                if (rule.senderMatch == SenderMatch.NUMBERS) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = rule.senderValues,
                        onValueChange = { v -> viewModel.updateRule { it.copy(senderValues = v) } },
                        label = { Text(stringResource(R.string.phone_numbers)) },
                        supportingText = { Text(stringResource(R.string.phone_numbers_hint)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (state.contacts.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.tap_contact_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.contacts.take(24).forEach { contact ->
                                AssistChip(
                                    onClick = {
                                        viewModel.updateRule {
                                            val existing = it.senderNumbers
                                            if (existing.any { n -> n == contact.number }) it
                                            else it.copy(
                                                senderValues =
                                                    (existing + contact.number).joinToString(", ")
                                            )
                                        }
                                    },
                                    label = { Text(contact.name) },
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                FieldLabel(stringResource(R.string.and_message))
                ChipRow(
                    options = ContentMatch.entries.map { it to formatter.label(it) },
                    selected = rule.contentMatch,
                    onSelect = { match -> viewModel.updateRule { it.copy(contentMatch = match) } },
                )
                if (rule.contentMatch != ContentMatch.ANY) {
                    val isRegex = rule.contentMatch == ContentMatch.REGEX
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = rule.contentValues,
                        onValueChange = { v -> viewModel.updateRule { it.copy(contentValues = v) } },
                        label = {
                            Text(stringResource(if (isRegex) R.string.regex_label else R.string.keywords))
                        },
                        supportingText = {
                            Text(stringResource(if (isRegex) R.string.regex_hint else R.string.keywords_hint))
                        },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    SwitchRow(
                        title = stringResource(R.string.case_sensitive),
                        checked = rule.caseSensitive,
                        onCheckedChange = { v ->
                            viewModel.updateRule { it.copy(caseSensitive = v) }
                        },
                    )
                }
            }

            // ---------------------------------------------------------- THEN
            SectionCard(
                title = stringResource(R.string.section_then),
                icon = Icons.Default.NotificationsActive,
            ) {
                RowItem(
                    icon = Icons.Default.MusicNote,
                    title = stringResource(R.string.notification_sound),
                    subtitle = rule.soundLabel,
                    onClick = {
                        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                            putExtra(
                                RingtoneManager.EXTRA_RINGTONE_TYPE,
                                RingtoneManager.TYPE_NOTIFICATION,
                            )
                            putExtra(
                                RingtoneManager.EXTRA_RINGTONE_TITLE,
                                context.getString(R.string.sound_for_rule),
                            )
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                            putExtra(
                                RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                                rule.soundUri?.let(Uri::parse),
                            )
                            putExtra(
                                RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI,
                                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                            )
                        }
                        runCatching { ringtonePicker.launch(intent) }
                    },
                )

                Spacer(Modifier.height(12.dp))
                FieldLabel(stringResource(R.string.vibration))
                ChipRow(
                    options = VibrationPattern.entries.map { it to stringResource(it.labelRes) },
                    selected = rule.vibration,
                    onSelect = { pattern ->
                        viewModel.updateRule { it.copy(vibration = pattern) }
                        viewModel.previewVibration(pattern)
                    },
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.vibration_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(16.dp))
                FieldLabel(stringResource(R.string.urgency))
                ChipRow(
                    options = RuleImportance.entries.map { it to stringResource(it.labelRes) },
                    selected = rule.importance,
                    onSelect = { imp -> viewModel.updateRule { it.copy(importance = imp) } },
                )

                Spacer(Modifier.height(16.dp))
                FieldLabel(stringResource(R.string.accent_colour))
                ColorPicker(
                    selected = rule.accentColor,
                    onSelect = { color -> viewModel.updateRule { it.copy(accentColor = color) } },
                )

                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = rule.titlePrefix,
                    onValueChange = { v -> viewModel.updateRule { it.copy(titlePrefix = v) } },
                    label = { Text(stringResource(R.string.title_badge)) },
                    placeholder = { Text(stringResource(R.string.title_badge_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                SwitchRow(
                    title = stringResource(R.string.bypass_dnd),
                    subtitle = stringResource(R.string.bypass_dnd_body),
                    checked = rule.bypassDnd,
                    onCheckedChange = { v -> viewModel.updateRule { it.copy(bypassDnd = v) } },
                )
                SwitchRow(
                    title = stringResource(R.string.hide_lock_screen),
                    subtitle = stringResource(R.string.hide_lock_screen_body),
                    checked = rule.hideContentOnLockScreen,
                    onCheckedChange = { v ->
                        viewModel.updateRule { it.copy(hideContentOnLockScreen = v) }
                    },
                )
                SwitchRow(
                    title = stringResource(R.string.stop_after),
                    subtitle = stringResource(R.string.stop_after_body),
                    checked = rule.stopOnMatch,
                    onCheckedChange = { v -> viewModel.updateRule { it.copy(stopOnMatch = v) } },
                )
            }

            // ---------------------------------------------------------- TEST
            SectionCard(
                title = stringResource(R.string.section_test),
                icon = Icons.Default.Science,
            ) {
                Text(
                    stringResource(R.string.test_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = state.testSender,
                    onValueChange = viewModel::onTestSenderChange,
                    label = { Text(stringResource(R.string.test_from)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.testBody,
                    onValueChange = viewModel::onTestBodyChange,
                    label = { Text(stringResource(R.string.test_body)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = viewModel::runTest) {
                        Icon(Icons.Default.Science, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.test_rule))
                    }
                    OutlinedButton(onClick = { viewModel.previewVibration(rule.vibration) }) {
                        Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.feel_vibration))
                    }
                }
                state.testMatch?.let { outcome ->
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = if (outcome.matched) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (outcome.matched) Icons.Default.Check else Icons.Default.Science,
                                null,
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = outcome.match?.let {
                                    stringResource(R.string.rule_would_fire, formatter.explain(it))
                                } ?: stringResource(R.string.rule_would_not_fire),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A titled card grouping one half of the rule. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SectionCard(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit,
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChipRow(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (value, label) ->
            FilterChip(
                selected = value == selected,
                onClick = { onSelect(value) },
                label = { Text(label) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorPicker(selected: Int?, onSelect: (Int?) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        AvatarColors.forEach { color ->
            val argb = color.toArgb()
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(color)
                    .clickable { onSelect(if (selected == argb) null else argb) },
                contentAlignment = Alignment.Center,
            ) {
                if (selected == argb) {
                    Icon(Icons.Default.Check, null, Modifier.size(20.dp), tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun RowItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
