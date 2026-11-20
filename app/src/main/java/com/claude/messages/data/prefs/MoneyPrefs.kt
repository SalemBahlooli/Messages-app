package com.claude.messages.data.prefs

import android.content.Context

/** The handful of money settings the user sets by hand. */
class MoneyPrefs(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("money_prefs", Context.MODE_PRIVATE)

    /** Monthly spending limit, or 0 when the user hasn't set one. */
    var monthlyBudget: Double
        get() = prefs.getFloat(KEY_BUDGET, 0f).toDouble()
        set(value) = prefs.edit().putFloat(KEY_BUDGET, value.toFloat()).apply()

    /** Day of the month pay normally lands, or 0 when not known yet. */
    var payday: Int
        get() = prefs.getInt(KEY_PAYDAY, 0)
        set(value) = prefs.edit().putInt(KEY_PAYDAY, value).apply()

    private companion object {
        const val KEY_BUDGET = "monthly_budget"
        const val KEY_PAYDAY = "payday"
    }
}
