package com.claude.messages.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.claude.messages.di.ServiceLocator

/**
 * Receives MMS WAP push notifications. Full MMS download requires a transaction
 * against the carrier's MMSC; this build registers for the broadcast (a hard
 * requirement for being installable as the default SMS app) and refreshes the
 * conversation list so a downloaded MMS shows up.
 */
class MmsDeliverReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ServiceLocator.notifyDataChanged()
    }
}
