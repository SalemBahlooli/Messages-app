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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.claude.messages.data.db.ContentMatch
import com.claude.messages.data.db.RuleImportance
import com.claude.messages.data.db.SenderMatch
import com.claude.messages.data.db.VibrationPattern
import com.claude.messages.ui.compose.SectionHeader
import com.claude.messages.ui.theme.AvatarColors

/**
 * Editor for a single notification rule: the conditions that select a message,
 * and the sound / vibration / urgency it should get.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleEditorScreen(
    viewModel: RulesViewModel,
    onDone: () -> Unit,
) {
    val state by viewModel.editor.collectAsStateWithLifecycle()
    val rule = state.rule
    val context = LocalContext.current

    LaunchedEffect(state.saved) {
        if (state.saved) {
            viewModel.consumeSaved()
            onDone()
        }
    }

    // System ringtone picker — returns the chosen tone's content URI.
    val ringtonePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri: Uri? = result.data
                ?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            viewModel.setSound(uri)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isNew) "New rule" else "Edit rule") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    TextButton(onClick = viewModel::save) { Text("Save") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
        ) {
            state.error?.let { error ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                ) {
                    Text(error, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }

            OutlinedTextField(
                value = rule.name,
                onValueChange = { name -> viewModel.updateRule { it.copy(name = name) } },
                label = { Text("Rule name") },
                placeholder = { Text("e.g. Family, Work alerts, Bank codes") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            )

            // ------------------------------------------------------ WHO
            SectionHeader("When the sender is")
            ChipRow(
                options = SenderMatch.entries.map { it to it.label() },
                selected = rule.senderMatch,
                onSelect = { match -> viewModel.updateRule { it.copy(senderMatch = match) } },
            )
            if (rule.senderMatch == SenderMatch.NUMBERS) {
                OutlinedTextField(
                    value = rule.senderValues,
                    onValueChange = { v -> viewModel.updateRule { it.copy(senderValues = v) } },
                    label = { Text("Phone numbers") },
                    supportingText = { Text("Separate several numbers with commas") },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                )
                if (state.contacts.isNotEmpty()) {
                    Text(
                        "Tap a contact to add their number",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        state.contacts.take(30).forEach { contact ->
                            AssistChip(
                                onClick = {
                                    viewModel.updateRule {
                                        val existing = it.senderNumbers
                                        if (existing.any { n -> n == contact.number }) it
                                        else it.copy(
                                            senderValues = (existing + contact.number)
                                                .joinToString(", ")
                                        )
                                    }
                                },
                                label = { Text("${contact.name} · ${contact.number}") },
                                modifier = Modifier.padding(vertical = 2.dp),
                            )
                        }
                    }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            // ------------------------------------------------------ WHAT
            SectionHeader("And the message")
            ChipRow(
                options = ContentMatch.entries.map { it to it.label() },
                selected = rule.contentMatch,
                onSelect = { match -> viewModel.updateRule { it.copy(contentMatch = match) } },
            )
            if (rule.contentMatch != ContentMatch.ANY) {
                OutlinedTextField(
                    value = rule.contentValues,
                    onValueChange = { v -> viewModel.updateRule { it.copy(contentValues = v) } },
                    label = {
                        Text(
                            if (rule.contentMatch == ContentMatch.REGEX) "Regular expression"
                            else "Keywords"
                        )
                    },
                    supportingText = {
                        Text(
                            if (rule.contentMatch == ContentMatch.REGEX) {
                                "e.g. \\\\b\\\\d{4,8}\\\\b to match a numeric code"
                            } else {
                                "One keyword per line"
                            }
                        )
                    },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                )
                SwitchRow(
                    title = "Case sensitive",
                    checked = rule.caseSensitive,
                    onCheckedChange = { v -> viewModel.updateRule { it.copy(caseSensitive = v) } },
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            // ------------------------------------------------------ THEN
            SectionHeader("Alert with")

            RowItem(
                icon = Icons.Default.MusicNote,
                title = "Notification sound",
                subtitle = rule.soundLabel,
                onClick = {
                    val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                        putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
                        putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Sound for this rule")
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
                    ringtonePicker.launch(intent)
                },
            )

            Text(
                "Vibration",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp),
            )
            ChipRow(
                options = VibrationPattern.entries.map { it to it.label },
                selected = rule.vibration,
                onSelect = { pattern ->
                    viewModel.updateRule { it.copy(vibration = pattern) }
                    viewModel.previewVibration(pattern)
                },
            )
            Text(
                "Tap a pattern to feel it",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp),
            )

            Text(
                "Urgency",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp),
            )
            ChipRow(
                options = RuleImportance.entries.map { it to it.label },
                selected = rule.importance,
                onSelect = { imp -> viewModel.updateRule { it.copy(importance = imp) } },
            )

            Text(
                "Accent colour",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp),
            )
            ColorPicker(
                selected = rule.accentColor,
                onSelect = { color -> viewModel.updateRule { it.copy(accentColor = color) } },
            )

            OutlinedTextField(
                value = rule.titlePrefix,
                onValueChange = { v -> viewModel.updateRule { it.copy(titlePrefix = v) } },
                label = { Text("Title badge (optional)") },
                placeholder = { Text("e.g. 🔴 or ⭐") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            )

            SwitchRow(
                title = "Bypass Do Not Disturb",
                subtitle = "Alert even while DND is on",
                checked = rule.bypassDnd,
                onCheckedChange = { v -> viewModel.updateRule { it.copy(bypassDnd = v) } },
            )
            SwitchRow(
                title = "Hide text on lock screen",
                subtitle = "Show only the sender for messages matched by this rule",
                checked = rule.hideContentOnLockScreen,
                onCheckedChange = { v ->
                    viewModel.updateRule { it.copy(hideContentOnLockScreen = v) }
                },
            )
            SwitchRow(
                title = "Stop after this rule",
                subtitle = "Don't check later rules once this one matches",
                checked = rule.stopOnMatch,
                onCheckedChange = { v -> viewModel.updateRule { it.copy(stopOnMatch = v) } },
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            // ------------------------------------------------------ TEST
            SectionHeader("Try it out")
            Text(
                "Enter a sample message to check whether this rule would fire.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            OutlinedTextField(
                value = state.testSender,
                onValueChange = viewModel::onTestSenderChange,
                label = { Text("From (phone number)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
            )
            OutlinedTextField(
                value = state.testBody,
                onValueChange = viewModel::onTestBodyChange,
                label = { Text("Message text") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = viewModel::runTest) {
                    Icon(Icons.Default.Science, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Test rule")
                }
                Spacer(Modifier.width(12.dp))
                OutlinedButton(onClick = { viewModel.previewVibration(rule.vibration) }) {
                    Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Feel vibration")
                }
            }
            state.testResult?.let { result ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (result.matched) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                    ),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (result.matched) Icons.Default.Check else Icons.Default.Vibration,
                            null,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(result.explanation, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun <T> ChipRow(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
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

@Composable
private fun ColorPicker(selected: Int?, onSelect: (Int?) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
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
                    Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(20.dp))
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
            .padding(horizontal = 20.dp, vertical = 12.dp),
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
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun RowItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(20.dp))
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

private fun SenderMatch.label(): String = when (this) {
    SenderMatch.ANY -> "Anyone"
    SenderMatch.NUMBERS -> "Specific numbers"
    SenderMatch.IN_CONTACTS -> "In my contacts"
    SenderMatch.NOT_IN_CONTACTS -> "Unknown number"
}

private fun ContentMatch.label(): String = when (this) {
    ContentMatch.ANY -> "Any text"
    ContentMatch.CONTAINS_ANY -> "Contains any keyword"
    ContentMatch.CONTAINS_ALL -> "Contains all keywords"
    ContentMatch.STARTS_WITH -> "Starts with"
    ContentMatch.REGEX -> "Matches regex"
}
