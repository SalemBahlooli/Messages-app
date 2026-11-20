package com.claude.messages.ui.money

import android.Manifest
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.claude.messages.R
import com.claude.messages.data.db.ContentMatch
import com.claude.messages.data.db.NotificationRule
import com.claude.messages.data.db.RuleImportance
import com.claude.messages.data.db.VibrationPattern
import com.claude.messages.data.prefs.MoneyPrefs
import com.claude.messages.di.ServiceLocator
import com.claude.messages.finance.FinanceRepository
import com.claude.messages.finance.MonthlyMoney
import com.claude.messages.finance.MoneySnapshot
import com.claude.messages.util.DefaultSmsHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

data class MoneyUiState(
    val loading: Boolean = true,
    val hasPermission: Boolean = false,
    val snapshot: MoneySnapshot = MoneySnapshot(),
    /** Index into [MoneySnapshot.months]; 0 is the newest month with activity. */
    val selectedMonth: Int = 0,
    val budget: Double = 0.0,
    val salaryRuleExists: Boolean = false,
    val justCreatedRule: Boolean = false,
) {
    val month: MonthlyMoney? get() = snapshot.months.getOrNull(selectedMonth)

    /** Spending still inside the budget, null when no budget is set. */
    val budgetLeft: Double?
        get() = if (budget <= 0.0) null else budget - (month?.spending ?: 0.0)

    /** What's left per remaining day, for the current month only. */
    val dailyAllowance: Double?
        get() {
            val left = budgetLeft ?: return null
            val m = month ?: return null
            val now = Calendar.getInstance()
            if (now.get(Calendar.YEAR) != m.year || now.get(Calendar.MONTH) != m.month) return null
            val daysLeft = now.getActualMaximum(Calendar.DAY_OF_MONTH) -
                now.get(Calendar.DAY_OF_MONTH) + 1
            return if (daysLeft > 0) left / daysLeft else left
        }
}

class MoneyViewModel(app: Application) : AndroidViewModel(app) {

    private val finance = FinanceRepository(app)
    private val prefs = MoneyPrefs(app)
    private val ruleDao = ServiceLocator.database(app).ruleDao()

    private val _state = MutableStateFlow(MoneyUiState(budget = prefs.monthlyBudget))
    val state: StateFlow<MoneyUiState> = _state.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            ServiceLocator.dataChanged.collect { refresh() }
        }
    }

    fun refresh() {
        val app = getApplication<Application>()
        val granted = DefaultSmsHelper.hasPermission(app, Manifest.permission.READ_SMS)
        _state.value = _state.value.copy(hasPermission = granted, loading = granted)
        if (!granted) return

        viewModelScope.launch {
            val snapshot = runCatching { finance.load() }.getOrElse { MoneySnapshot() }
            val hasRule = runCatching {
                ruleDao.getAll().any { rule ->
                    rule.contentMatch != ContentMatch.ANY &&
                        rule.keywords.any {
                            it.equals(SALARY_AR, true) || it.equals("salary", true)
                        }
                }
            }.getOrDefault(false)

            _state.value = _state.value.copy(
                loading = false,
                snapshot = snapshot,
                // The newest month can change under us, so keep the index valid.
                selectedMonth = _state.value.selectedMonth
                    .coerceAtMost(maxOf(0, snapshot.months.lastIndex)),
                budget = prefs.monthlyBudget,
                salaryRuleExists = hasRule,
            )
        }
    }

    fun selectMonth(index: Int) {
        _state.value = _state.value.copy(selectedMonth = index)
    }

    fun setBudget(value: Double) {
        prefs.monthlyBudget = value.coerceAtLeast(0.0)
        _state.value = _state.value.copy(budget = prefs.monthlyBudget)
    }

    /**
     * Creates a notification rule that gives pay its own alert — the one thing
     * this app can do that the bank's own app can't.
     */
    fun createSalaryRule() {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val name = app.getString(R.string.money_salary_rule_name)
            if (ruleDao.getAll().any { it.name == name }) {
                _state.value = _state.value.copy(salaryRuleExists = true)
                return@launch
            }
            ruleDao.insert(
                NotificationRule(
                    name = name,
                    enabled = true,
                    order = ruleDao.nextOrder(),
                    contentMatch = ContentMatch.CONTAINS_ANY,
                    contentValues = listOf(SALARY_AR, "الراتب", "salary", "payroll")
                        .joinToString("\n"),
                    importance = RuleImportance.HIGH,
                    vibration = VibrationPattern.DOUBLE,
                    titlePrefix = "💰",
                    bypassDnd = true,
                )
            )
            _state.value = _state.value.copy(salaryRuleExists = true, justCreatedRule = true)
        }
    }

    fun consumeRuleCreated() {
        _state.value = _state.value.copy(justCreatedRule = false)
    }

    private companion object {
        const val SALARY_AR = "راتب"
    }
}
