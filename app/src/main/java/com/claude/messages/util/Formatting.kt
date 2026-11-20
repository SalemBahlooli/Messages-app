package com.claude.messages.util

import android.content.Context
import android.text.format.DateUtils
import com.claude.messages.R
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object Formatting {

    private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    private val weekdayFormat = SimpleDateFormat("EEE", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("MMM d", Locale.getDefault())
    private val fullFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    private val fullWithTime = SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault())
    private val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
    private val shortMonthFormat = SimpleDateFormat("MMM", Locale.getDefault())
    private val dayMonthFormat = SimpleDateFormat("d MMM", Locale.getDefault())

    /** Compact stamp for the conversation list: time today, weekday this week, else date. */
    fun listTimestamp(millis: Long): String {
        if (millis <= 0) return ""
        val now = Calendar.getInstance()
        val then = Calendar.getInstance().apply { timeInMillis = millis }
        return when {
            DateUtils.isToday(millis) -> timeFormat.format(Date(millis))
            now.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
                now.get(Calendar.DAY_OF_YEAR) - then.get(Calendar.DAY_OF_YEAR) in 1..6 ->
                weekdayFormat.format(Date(millis))

            now.get(Calendar.YEAR) == then.get(Calendar.YEAR) -> dateFormat.format(Date(millis))
            else -> fullFormat.format(Date(millis))
        }
    }

    /** "4 Nov" — a date without the year, for rows inside one month. */
    fun dayAndMonth(millis: Long): String =
        if (millis <= 0) "" else dayMonthFormat.format(Date(millis))

    fun messageTimestamp(millis: Long): String =
        if (millis <= 0) "" else timeFormat.format(Date(millis))

    fun fullTimestamp(millis: Long): String =
        if (millis <= 0) "" else fullWithTime.format(Date(millis))

    /**
     * Day separator text shown between groups of messages in a thread.
     * Takes a Context so "Today" and "Yesterday" follow the phone's language.
     */
    fun daySeparator(context: Context, millis: Long): String = when {
        DateUtils.isToday(millis) -> context.getString(R.string.today)
        DateUtils.isToday(millis + DateUtils.DAY_IN_MILLIS) ->
            context.getString(R.string.yesterday)

        else -> fullFormat.format(Date(millis))
    }

    /** Currencies whose minor unit is a thousandth rather than a hundredth. */
    private val threeDecimalCurrencies = setOf("OMR", "KWD", "BHD", "JOD", "TND", "LYD")

    /** "1,250.500 OMR" — the currency after the number, as bank SMS writes it. */
    fun money(amount: Double, currency: String): String {
        val digits = if (currency in threeDecimalCurrencies) 3 else 2
        val format = DecimalFormat().apply {
            minimumFractionDigits = digits
            maximumFractionDigits = digits
            isGroupingUsed = true
        }
        val number = format.format(amount)
        return if (currency.isEmpty()) number else "$number $currency"
    }

    /** "November 2025", in the phone's language. */
    fun monthLabel(year: Int, month: Int): String {
        val c = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        return monthFormat.format(c.time)
    }

    /** "Nov" — for the month picker, where space is tight. */
    fun shortMonthLabel(year: Int, month: Int): String {
        val c = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        return shortMonthFormat.format(c.time)
    }

    fun sameDay(a: Long, b: Long): Boolean {
        val ca = Calendar.getInstance().apply { timeInMillis = a }
        val cb = Calendar.getInstance().apply { timeInMillis = b }
        return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) &&
            ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
    }

    /** Two-letter monogram used by the avatar when there is no contact photo. */
    fun initials(name: String): String {
        val words = name.trim().split(" ", "-").filter { it.isNotBlank() }
        return when {
            words.isEmpty() -> "#"
            words.size == 1 -> words[0].take(1).uppercase()
            else -> (words[0].take(1) + words[1].take(1)).uppercase()
        }
    }
}
