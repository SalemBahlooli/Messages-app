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
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat

/** Why the device cannot send right now, in the user's words. */
enum class SendBlocker(val message: String) {
    NO_SEND_PERMISSION("Messages doesn't have permission to send SMS. Grant it in app settings."),
    NO_TELEPHONY("This device has no cellular radio, so it can't send SMS."),
    NO_SIM("No SIM card detected. Insert a SIM to send messages."),
    SIM_LOCKED("Your SIM is locked. Unlock it to send messages."),
}

/** Helpers for the default-SMS role, permissions, and send readiness. */
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
        context.packageName == defaultSmsPackage(context)

    /** The package the system currently treats as the SMS handler, if any. */
    fun defaultSmsPackage(context: Context): String? =
        runCatching { Telephony.Sms.getDefaultSmsPackage(context) }.getOrNull()

    fun hasPermission(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /** The permissions without which the app cannot show or send anything. */
    fun hasSmsPermissions(context: Context): Boolean =
        hasPermission(context, Manifest.permission.READ_SMS) &&
            hasPermission(context, Manifest.permission.SEND_SMS)

    fun missingPermissions(context: Context): List<String> =
        requiredPermissions.filterNot { hasPermission(context, it) }

    fun hasTelephony(context: Context): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)

    private fun simState(context: Context): Int =
        runCatching {
            (context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager).simState
        }.getOrDefault(TelephonyManager.SIM_STATE_UNKNOWN)

    /** Human-readable SIM state, for the diagnostics screen. */
    fun simStateLabel(context: Context): String = when (simState(context)) {
        TelephonyManager.SIM_STATE_READY -> "Ready"
        TelephonyManager.SIM_STATE_ABSENT -> "No SIM"
        TelephonyManager.SIM_STATE_PIN_REQUIRED -> "PIN required"
        TelephonyManager.SIM_STATE_PUK_REQUIRED -> "PUK required"
        TelephonyManager.SIM_STATE_NETWORK_LOCKED -> "Network locked"
        TelephonyManager.SIM_STATE_NOT_READY -> "Not ready"
        TelephonyManager.SIM_STATE_PERM_DISABLED -> "Disabled"
        TelephonyManager.SIM_STATE_CARD_IO_ERROR -> "Card error"
        else -> "Unknown"
    }

    /**
     * What stops a send from working, or null when the radio should accept one.
     *
     * Deliberately does **not** include "not the default SMS app": SEND_SMS alone
     * is enough for the platform to transmit. Being default only decides whether
     * the message can also be written into the system inbox.
     */
    fun sendBlocker(context: Context): SendBlocker? = when {
        !hasPermission(context, Manifest.permission.SEND_SMS) -> SendBlocker.NO_SEND_PERMISSION
        !hasTelephony(context) -> SendBlocker.NO_TELEPHONY
        simState(context) == TelephonyManager.SIM_STATE_ABSENT -> SendBlocker.NO_SIM
        simState(context) in setOf(
            TelephonyManager.SIM_STATE_PIN_REQUIRED,
            TelephonyManager.SIM_STATE_PUK_REQUIRED,
            TelephonyManager.SIM_STATE_NETWORK_LOCKED,
        ) -> SendBlocker.SIM_LOCKED

        else -> null
    }

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

    /** The system's own "default apps" screen, as a fallback route. */
    fun defaultAppsSettingsIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * Android 13+ blocks sideloaded apps from taking sensitive roles such as the
     * default SMS handler until the user allows restricted settings by hand.
     */
    val restrictedSettingsLikely: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
}
