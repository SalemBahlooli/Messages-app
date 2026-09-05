package com.claude.messages.util

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony

/** Helpers for checking and requesting the default-SMS-app role. */
object DefaultSmsHelper {

    fun isDefault(context: Context): Boolean =
        context.packageName == Telephony.Sms.getDefaultSmsPackage(context)

    /**
     * Returns the intent that asks the user to make this app the default SMS
     * handler — RoleManager on Android 10+, the legacy chooser before that.
     */
    fun requestIntent(context: Context): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            if (roleManager?.isRoleAvailable(RoleManager.ROLE_SMS) == true) {
                roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
            } else null
        } else {
            @Suppress("DEPRECATION")
            Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).apply {
                putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.packageName)
            }
        }

    fun request(activity: Activity, requestCode: Int = 42) {
        requestIntent(activity)?.let { activity.startActivityForResult(it, requestCode) }
    }
}
