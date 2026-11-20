package com.claude.messages.ui.money

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.claude.messages.R
import com.claude.messages.finance.MonthlyMoney
import com.claude.messages.finance.Transaction
import com.claude.messages.finance.TxnKind
import com.claude.messages.ui.compose.AppHeader
import com.claude.messages.ui.compose.EmptyState
import com.claude.messages.util.Formatting

/**
 * Salary and bank messages, read out of the inbox. Banks already send everything
 * needed to answer "what did I earn and spend this month" — it just arrives as
 * a few hundred unreadable texts.
 */
@Composable
fun MoneyScreen(
    viewModel: MoneyViewModel,
    contentPadding: PaddingValues,
    onOpenThread: (Long) -> Unit,
    onOpenRules: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var budgetDialog by remember { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    LaunchedEffect(state.justCreatedRule) {
        if (state.justCreatedRule) {
            viewModel.consumeRuleCreated()
            onOpenRules()
        }
    }

    Scaffold(
        modifier = Modifier.padding(contentPadding),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            AppHeader(
                title = stringResource(R.string.tab_money),
                subtitle = stringResource(R.string.money_subtitle),
                loading = state.loading,
            )
        },
    ) { padding ->
        val month = state.month

        when {
            !state.hasPermission -> EmptyState(
                icon = Icons.Default.AccountBalance,
                title = stringResource(R.string.money_needs_permission),
                subtitle = stringResource(R.string.money_needs_permission_hint),
                modifier = Modifier.padding(padding),
            )

            month == null && !state.loading -> EmptyState(
                icon = Icons.Default.AccountBalance,
                title = stringResource(R.string.money_empty),
                subtitle = stringResource(R.string.money_empty_hint),
                modifier = Modifier.padding(padding),
            )

            month == null -> Box(Modifier.padding(padding).fillMaxSize())

            else -> LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item("months") {
                    MonthPicker(
                        months = state.snapshot.months,
                        selected = state.selectedMonth,
                        onSelect = viewModel::selectMonth,
                    )
                }

                item("summary") { SummaryCard(month) }

                item("salary") {
                    SalaryCard(
                        month = month,
                        ruleExists = state.salaryRuleExists,
                        onCreateRule = viewModel::createSalaryRule,
                    )
                }

                item("budget") {
                    BudgetCard(
                        spending = month.spending,
                        currency = month.currency,
                        budget = state.budget,
                        left = state.budgetLeft,
                        daily = state.dailyAllowance,
                        onEdit = { budgetDialog = true },
                    )
                }

                if (state.snapshot.balances.isNotEmpty()) {
                    item("balances") {
                        BalancesCard(state.snapshot.balances, month.currency)
                    }
                }

                val top = month.topCounterparties.take(5)
                if (top.isNotEmpty()) {
                    item("top") { TopSpendCard(top, month.currency) }
                }

                item("txnHeader") {
                    SectionTitle(
                        stringResource(R.string.money_transactions),
                        "${month.transactions.size}",
                    )
                }

                items(month.transactions, key = { it.messageId }) { txn ->
                    TransactionRow(txn, onClick = { onOpenThread(txn.threadId) })
                }
            }
        }
    }

    if (budgetDialog) {
        BudgetDialog(
            current = state.budget,
            currency = state.snapshot.currency,
            onDismiss = { budgetDialog = false },
            onSave = {
                viewModel.setBudget(it)
                budgetDialog = false
            },
        )
    }
}

@Composable
private fun MonthPicker(months: List<MonthlyMoney>, selected: Int, onSelect: (Int) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(months) { index, month ->
            val active = index == selected
            Surface(
                shape = CircleShape,
                color = if (active) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = if (active) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable { onSelect(index) },
            ) {
                Text(
                    text = Formatting.shortMonthLabel(month.year, month.month),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                )
            }
        }
    }
}

@Composable
private fun MoneyCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun SummaryCard(month: MonthlyMoney) {
    MoneyCard {
        Text(
            Formatting.monthLabel(month.year, month.month),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Figure(
                label = stringResource(R.string.money_in),
                value = Formatting.money(month.income, month.currency),
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.weight(1f),
            )
            Figure(
                label = stringResource(R.string.money_out),
                value = Formatting.money(month.spending, month.currency),
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(12.dp))
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.money_net),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    Formatting.money(month.net, month.currency),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (month.net >= 0) MaterialTheme.colorScheme.tertiary
                    else MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun Figure(label: String, value: String, tint: Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SalaryCard(month: MonthlyMoney, ruleExists: Boolean, onCreateRule: () -> Unit) {
    MoneyCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CardIcon(Icons.Default.Payments, MaterialTheme.colorScheme.tertiary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.money_salary),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val salary = month.salary
                if (salary != null) {
                    Text(
                        Formatting.money(salary.amount, salary.currency.ifEmpty { month.currency }),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        Formatting.dayAndMonth(salary.date),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        stringResource(R.string.money_no_salary),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        if (!ruleExists) {
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onCreateRule, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.NotificationsActive, null, Modifier.size(17.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.money_create_salary_rule))
            }
        }
    }
}

@Composable
private fun BudgetCard(
    spending: Double,
    currency: String,
    budget: Double,
    left: Double?,
    daily: Double?,
    onEdit: () -> Unit,
) {
    MoneyCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CardIcon(Icons.Default.Savings, MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.money_budget),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    if (budget > 0) Formatting.money(budget, currency)
                    else stringResource(R.string.money_no_budget),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            TextButton(onClick = onEdit) {
                Text(stringResource(if (budget > 0) R.string.edit else R.string.money_set_budget))
            }
        }

        if (budget > 0) {
            Spacer(Modifier.height(10.dp))
            val fraction = (spending / budget).coerceIn(0.0, 1.0).toFloat()
            val over = (left ?: 0.0) < 0
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = if (over) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (over) {
                    stringResource(
                        R.string.money_over_budget,
                        Formatting.money(-(left ?: 0.0), currency),
                    )
                } else {
                    stringResource(
                        R.string.money_budget_left,
                        Formatting.money(left ?: 0.0, currency),
                    )
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (over) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurface,
            )
            daily?.takeIf { !over }?.let {
                Text(
                    stringResource(R.string.money_daily_allowance, Formatting.money(it, currency)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun BalancesCard(balances: List<Pair<String, Double>>, currency: String) {
    MoneyCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CardIcon(Icons.Default.AccountBalanceWallet, MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.width(12.dp))
            Text(
                stringResource(R.string.money_balances),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
        balances.forEach { (source, balance) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                Text(
                    source,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    Formatting.money(balance, currency),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.money_balance_caveat),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TopSpendCard(top: List<Pair<String, Double>>, currency: String) {
    val largest = top.firstOrNull()?.second ?: 1.0
    MoneyCard {
        Text(
            stringResource(R.string.money_top_spend),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        top.forEach { (name, amount) ->
            Column(Modifier.padding(bottom = 10.dp)) {
                Row {
                    Text(
                        name,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        Formatting.money(amount, currency),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { (amount / largest).coerceIn(0.0, 1.0).toFloat() },
                    modifier = Modifier.fillMaxWidth().height(5.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, trailing: String?) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        trailing?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TransactionRow(txn: Transaction, onClick: () -> Unit) {
    val tint = when (txn.kind) {
        TxnKind.DEBIT -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.tertiary
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(shape = CircleShape, color = tint.copy(alpha = 0.14f)) {
            Icon(
                imageVector = when (txn.kind) {
                    TxnKind.SALARY -> Icons.Default.Payments
                    TxnKind.CREDIT -> Icons.AutoMirrored.Filled.CallReceived
                    TxnKind.DEBIT -> Icons.AutoMirrored.Filled.CallMade
                },
                contentDescription = null,
                modifier = Modifier.padding(7.dp).size(16.dp),
                tint = tint,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = txn.counterparty ?: txn.source,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = Formatting.dayAndMonth(txn.date) + " · " + txn.source,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = (if (txn.kind == TxnKind.DEBIT) "−" else "+") +
                Formatting.money(txn.amount, txn.currency),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = tint,
        )
    }
}

@Composable
private fun BudgetDialog(
    current: Double,
    currency: String,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit,
) {
    var text by remember {
        mutableStateOf(if (current > 0) current.toString().trimEnd('0').trimEnd('.') else "")
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.money_budget)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.money_budget_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { input ->
                        text = input.filter { it.isDigit() || it == '.' }
                    },
                    singleLine = true,
                    suffix = { Text(currency) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(text.toDoubleOrNull() ?: 0.0) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun CardIcon(icon: ImageVector, tint: Color) {
    Surface(shape = CircleShape, color = tint.copy(alpha = 0.14f)) {
        Icon(icon, null, Modifier.padding(8.dp).size(18.dp), tint = tint)
    }
}
