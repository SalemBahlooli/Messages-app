package com.claude.messages.util

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.provider.Telephony
import androidx.core.content.ContextCompat

/** Helpers for the default-SMS role and the permissions the app needs. */
object DefaultSmsHelper {

    val requiredPermissions: List<String>
        get() = buildList {
            add(Manifest.permission.READ_SMS)
            add(Manifest.permission.RECEIVE_SMS)
            add(Manifest.permission.SEND_SMS)
            add(Manifest.permission.READ_CONTACTS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

    fun isDefault(context: Context): Boolean =
        context.packageName == Telephony.Sms.getDefaultSmsPackage(context)

    fun hasPermission(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /** The SMS permissions without which the app cannot show or send anything. */
    fun hasSmsPermissions(context: Context): Boolean =
        hasPermission(context, Manifest.permission.READ_SMS) &&
            hasPermission(context, Manifest.permission.SEND_SMS)

    fun missingPermissions(context: Context): List<String> =
        requiredPermissions.filterNot { hasPermission(context, it) }

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

    /** Opens this app's entry in system Settings, where restricted settings live. */
    fun appSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    /**
     * Android 13+ blocks sideloaded apps from taking sensitive roles such as the
     * default SMS handler until the user explicitly allows restricted settings.
     * Only relevant on those versions.
     */
    val restrictedSettingsLikely: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
}
