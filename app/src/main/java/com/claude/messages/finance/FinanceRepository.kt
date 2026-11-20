package com.claude.messages.finance

import android.content.Context
import com.claude.messages.di.ServiceLocator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

/** One calendar month of money, built from the bank messages in the inbox. */
data class MonthlyMoney(
    val year: Int,
    /** 0-based, as [Calendar.MONTH] reports it. */
    val month: Int,
    val currency: String,
    val income: Double,
    val spending: Double,
    val salary: Transaction?,
    val transactions: List<Transaction>,
) {
    val net: Double get() = income - spending
    val startOfMonth: Long
        get() = Calendar.getInstance().apply {
            set(year, month, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    /** Spending per merchant, largest first. */
    val topCounterparties: List<Pair<String, Double>>
        get() = transactions
            .filter { it.kind == TxnKind.DEBIT && !it.counterparty.isNullOrBlank() }
            .groupBy { it.counterparty!! }
            .map { (name, list) -> name to list.sumOf { it.amount } }
            .sortedByDescending { it.second }
}

data class MoneySnapshot(
    val months: List<MonthlyMoney> = emptyList(),
    /** Latest balance each bank reported, newest message first. */
    val balances: List<Pair<String, Double>> = emptyList(),
    val currency: String = "",
    val scannedMessages: Int = 0,
) {
    val hasData: Boolean get() = months.any { it.transactions.isNotEmpty() }
}

/**
 * Turns the inbox into monthly totals. Nothing is stored: the SMS provider is
 * already the record, and a second copy of someone's bank history is a
 * liability rather than a feature.
 */
class FinanceRepository(context: Context) {

    private val sms = ServiceLocator.smsRepository(context)

    suspend fun load(monthsBack: Int = 12): MoneySnapshot = withContext(Dispatchers.IO) {
        val since = Calendar.getInstance().apply {
            add(Calendar.MONTH, -monthsBack)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }.timeInMillis

        val messages = sms.loadInboxSince(since)
        val transactions = messages.mapNotNull { message ->
            FinanceParser.parse(
                messageId = message.id,
                threadId = message.threadId,
                source = message.address,
                date = message.date,
                body = message.body,
            )
        }

        // The currency the user actually banks in: the one seen most often.
        val currency = transactions
            .filter { it.currency.isNotEmpty() }
            .groupingBy { it.currency }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
            .orEmpty()

        val months = transactions
            // A mixed-currency inbox (a foreign card alert, a holiday purchase)
            // would otherwise add unlike numbers together.
            .filter { currency.isEmpty() || it.currency.isEmpty() || it.currency == currency }
            .groupBy { monthKeyOf(it.date) }
            .map { (key, list) ->
                val (year, month) = key
                MonthlyMoney(
                    year = year,
                    month = month,
                    currency = currency,
                    income = list.filter { it.isIncome }.sumOf { it.amount },
                    spending = list.filter { it.kind == TxnKind.DEBIT }.sumOf { it.amount },
                    salary = list.filter { it.kind == TxnKind.SALARY }.maxByOrNull { it.amount },
                    transactions = list.sortedByDescending { it.date },
                )
            }
            .sortedWith(compareByDescending<MonthlyMoney> { it.year }.thenByDescending { it.month })

        val balances = transactions
            .filter { it.balance != null }
            .groupBy { it.source }
            .mapNotNull { (source, list) ->
                list.maxByOrNull { it.date }?.let { source to it.balance!! }
            }
            .sortedByDescending { it.second }

        MoneySnapshot(
            months = months,
            balances = balances,
            currency = currency,
            scannedMessages = messages.size,
        )
    }

    private fun monthKeyOf(millis: Long): Pair<Int, Int> {
        val c = Calendar.getInstance().apply { timeInMillis = millis }
        return c.get(Calendar.YEAR) to c.get(Calendar.MONTH)
    }
}
