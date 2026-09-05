package com.claude.messages.data.prefs

import android.content.Context

/** Small persisted UI choices that don't belong in the message database. */
class UiPrefs(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("ui_prefs", Context.MODE_PRIVATE)

    /**
     * Whether the user dismissed the setup banner. Honoured even while setup is
     * incomplete — being nagged by a card you cannot act on is worse than the
     * missing feature, and Settings still shows the real status.
     */
    var setupBannerDismissed: Boolean
        get() = prefs.getBoolean(KEY_SETUP_DISMISSED, false)
        set(value) = prefs.edit().putBoolean(KEY_SETUP_DISMISSED, value).apply()

    private companion object {
        const val KEY_SETUP_DISMISSED = "setup_banner_dismissed"
    }
}
