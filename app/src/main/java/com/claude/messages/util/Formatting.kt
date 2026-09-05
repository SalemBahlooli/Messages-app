package com.claude.messages.util

import android.text.format.DateUtils
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

    fun messageTimestamp(millis: Long): String =
        if (millis <= 0) "" else timeFormat.format(Date(millis))

    fun fullTimestamp(millis: Long): String =
        if (millis <= 0) "" else fullWithTime.format(Date(millis))

    /** Day separator text shown between groups of messages in a thread. */
    fun daySeparator(millis: Long): String = when {
        DateUtils.isToday(millis) -> "Today"
        DateUtils.isToday(millis + DateUtils.DAY_IN_MILLIS) -> "Yesterday"
        else -> fullFormat.format(Date(millis))
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
