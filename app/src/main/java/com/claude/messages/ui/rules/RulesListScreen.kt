package com.claude.messages.ui.rules

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.claude.messages.data.db.NotificationRule
import com.claude.messages.data.db.RuleImportance
import com.claude.messages.ui.compose.EmptyState

/**
 * Lists the user's notification rules in evaluation order. The first enabled
 * rule that matches an incoming message decides how it is announced.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesListScreen(
    viewModel: RulesViewModel,
    onBack: () -> Unit,
    onAddRule: () -> Unit,
    onEditRule: (Long) -> Unit,
) {
    val rules by viewModel.rules.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notification rules") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddRule) { Icon(Icons.Default.Add, "New rule") }
        },
    ) { padding ->
        if (rules.isEmpty()) {
            Column(Modifier.padding(padding)) {
                EmptyState(
                    icon = Icons.Default.Tune,
                    title = "No rules yet",
                    subtitle = "Create a rule to give a specific contact — or messages " +
                        "containing certain words — their own ringtone, vibration and urgency.",
                    actionLabel = "Create a rule",
                    onAction = onAddRule,
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    "Rules are checked from top to bottom. The first match wins.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            itemsIndexed(rules, key = { _, r -> r.id }) { index, rule ->
                RuleCard(
                    rule = rule,
                    description = viewModel.describe(rule),
                    isFirst = index == 0,
                    isLast = index == rules.lastIndex,
                    onClick = { onEditRule(rule.id) },
                    onToggle = { viewModel.setEnabled(rule, it) },
                    onMoveUp = { viewModel.move(rule, up = true) },
                    onMoveDown = { viewModel.move(rule, up = false) },
                    onDelete = { viewModel.delete(rule) },
                )
            }
        }
    }
}

@Composable
private fun RuleCard(
    rule: NotificationRule,
    description: String,
    isFirst: Boolean,
    isLast: Boolean,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (rule.enabled) MaterialTheme.colorScheme.surfaceVariant
            else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (rule.titlePrefix.isNotBlank()) {
                            Text(rule.titlePrefix)
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(
                            rule.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = rule.enabled, onCheckedChange = onToggle)
            }

            Spacer(Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.MusicNote, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(rule.soundLabel, style = MaterialTheme.typography.labelMedium)

                Spacer(Modifier.width(14.dp))
                Icon(Icons.Default.Vibration, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(rule.vibration.label, style = MaterialTheme.typography.labelMedium)

                Spacer(Modifier.weight(1f))
                Text(
                    when (rule.importance) {
                        RuleImportance.SILENT -> "Silent"
                        RuleImportance.LOW -> "Quiet"
                        RuleImportance.NORMAL -> "Normal"
                        RuleImportance.HIGH -> "Urgent"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = onMoveUp, enabled = !isFirst) {
                    Icon(Icons.Default.ArrowUpward, "Move up", Modifier.size(18.dp))
                }
                IconButton(onClick = onMoveDown, enabled = !isLast) {
                    Icon(Icons.Default.ArrowDownward, "Move down", Modifier.size(18.dp))
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        "Delete rule",
                        Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}
