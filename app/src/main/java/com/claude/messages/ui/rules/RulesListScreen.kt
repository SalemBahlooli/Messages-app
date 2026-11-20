package com.claude.messages.ui.rules

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.claude.messages.R
import com.claude.messages.data.db.NotificationRule
import com.claude.messages.notifications.RuleTextFormatter
import com.claude.messages.ui.compose.AppHeader
import com.claude.messages.ui.compose.EmptyState

/**
 * The rules tab. Rules are listed in evaluation order; the first enabled rule
 * that matches an incoming message decides how it is announced.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesListScreen(
    viewModel: RulesViewModel,
    contentPadding: PaddingValues,
    onAddRule: () -> Unit,
    onEditRule: (Long) -> Unit,
) {
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val formatter = remember(context) { RuleTextFormatter(context) }

    Scaffold(
        modifier = Modifier.padding(contentPadding),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            AppHeader(
                title = stringResource(R.string.notification_rules),
                subtitle = stringResource(R.string.rules_subtitle),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddRule,
                icon = { Icon(Icons.Default.Add, null, Modifier.size(19.dp)) },
                text = { Text(stringResource(R.string.new_rule)) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    ) { padding ->
        if (rules.isEmpty()) {
            Column(Modifier.padding(padding)) {
                EmptyState(
                    icon = Icons.Default.Tune,
                    title = stringResource(R.string.no_rules_title),
                    subtitle = stringResource(R.string.no_rules_body),
                    actionLabel = stringResource(R.string.create_rule),
                    onAction = onAddRule,
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.rules_order_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            }
            itemsIndexed(rules, key = { _, r -> r.id }) { index, rule ->
                RuleCard(
                    rule = rule,
                    description = formatter.describe(rule),
                    position = index + 1,
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
    position: Int,
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
                // The badge doubles as the rule's position in evaluation order.
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
                            Text(
                                "$position",
                                style = MaterialTheme.typography.titleMedium,
                                color = accent,
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
                Attribute(Icons.Default.Vibration, stringResource(rule.vibration.labelRes))
                Spacer(Modifier.width(12.dp))
                Attribute(
                    Icons.Default.PriorityHigh,
                    stringResource(rule.importance.shortLabelRes),
                    highlight = rule.importance == com.claude.messages.data.db.RuleImportance.HIGH,
                )
            }

            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = onMoveUp, enabled = !isFirst) {
                    Icon(
                        Icons.Default.ArrowUpward,
                        stringResource(R.string.move_up),
                        Modifier.size(18.dp),
                    )
                }
                IconButton(onClick = onMoveDown, enabled = !isLast) {
                    Icon(
                        Icons.Default.ArrowDownward,
                        stringResource(R.string.move_down),
                        Modifier.size(18.dp),
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        stringResource(R.string.delete_rule),
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
private fun Attribute(icon: ImageVector, label: String, highlight: Boolean = false) {
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
