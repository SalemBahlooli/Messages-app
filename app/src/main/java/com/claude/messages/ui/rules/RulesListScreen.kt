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
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PriorityHigh
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
    val accent = rule.accentColor?.let { Color(it) } ?: MaterialTheme.colorScheme.primary
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (rule.enabled) MaterialTheme.colorScheme.surfaceContainerHigh
            else MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Tonal badge carries the rule's own accent colour and badge text.
                Surface(
                    shape = CircleShape,
                    color = accent.copy(alpha = if (rule.enabled) 0.18f else 0.08f),
                    modifier = Modifier.size(42.dp),
                ) {
                    Column(
                        Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (rule.titlePrefix.isNotBlank()) {
                            Text(rule.titlePrefix.take(2))
                        } else {
                            Icon(
                                Icons.Default.NotificationsActive,
                                null,
                                Modifier.size(20.dp),
                                tint = accent,
                            )
                        }
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        rule.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Switch(checked = rule.enabled, onCheckedChange = onToggle)
            }

            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Attribute(Icons.Default.MusicNote, rule.soundLabel)
                Spacer(Modifier.width(12.dp))
                Attribute(Icons.Default.Vibration, rule.vibration.label)
                Spacer(Modifier.width(12.dp))
                Attribute(
                    Icons.Default.PriorityHigh,
                    when (rule.importance) {
                        RuleImportance.SILENT -> "Silent"
                        RuleImportance.LOW -> "Quiet"
                        RuleImportance.NORMAL -> "Normal"
                        RuleImportance.HIGH -> "Urgent"
                    },
                    highlight = rule.importance == RuleImportance.HIGH,
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

/** One sound/vibration/urgency fact on a rule card. */
@Composable
private fun Attribute(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    highlight: Boolean = false,
) {
    val tint = if (highlight) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(15.dp), tint = tint)
        Spacer(Modifier.width(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
