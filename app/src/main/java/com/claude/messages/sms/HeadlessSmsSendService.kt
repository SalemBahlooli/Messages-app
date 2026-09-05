package com.claude.messages.sms

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.telephony.TelephonyManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles "respond via message" quick replies from the incoming-call screen.
 * Required for the app to qualify as a default SMS handler.
 */
class HeadlessSmsSendService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == TelephonyManager.ACTION_RESPOND_VIA_MESSAGE) {
            val recipients = intent.data?.schemeSpecificPart
                ?.split(",", ";")
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                .orEmpty()
            val body = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
            if (recipients.isNotEmpty() && body.isNotEmpty()) {
                val app = applicationContext
                CoroutineScope(Dispatchers.IO).launch { SmsSender(app).send(recipients, body) }
            }
        }
        stopSelf(startId)
        return START_NOT_STICKY
    }
}
