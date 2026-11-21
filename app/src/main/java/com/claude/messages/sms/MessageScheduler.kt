package com.claude.messages.sms

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.claude.messages.data.db.ScheduledMessage
import com.claude.messages.di.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Sends messages at a time the user picked.
 *
 * Each scheduled message gets its own alarm. Exact alarms are used when the
 * system allows them; otherwise the inexact variant still fires within a few
 * minutes even in Doze, which is acceptable for "send this tonight".
 */
object MessageScheduler {

    private const val TAG = "MessageScheduler"
    const val EXTRA_ID = "scheduled_id"

    suspend fun schedule(context: Context, message: ScheduledMessage): Long {
        val dao = ServiceLocator.database(context).scheduledDao()
        val id = dao.insert(message)
        arm(context, message.copy(id = id))
        ServiceLocator.notifyDataChanged()
        return id
    }

    suspend fun cancel(context: Context, id: Long) {
        alarmManager(context)?.cancel(pendingIntent(context, id))
        ServiceLocator.database(context).scheduledDao().delete(id)
        ServiceLocator.notifyDataChanged()
    }

    /** Sends a scheduled message right away and forgets its alarm. */
    suspend fun sendNow(context: Context, id: Long) {
        alarmManager(context)?.cancel(pendingIntent(context, id))
        deliver(context, id)
    }

    /** Alarms do not survive a reboot or an app update; re-arm everything. */
    suspend fun rearmAll(context: Context) {
        val all = runCatching { ServiceLocator.database(context).scheduledDao().getAll() }
            .getOrDefault(emptyList())
        all.forEach { arm(context, it) }
    }

    internal suspend fun deliver(context: Context, id: Long) {
        val dao = ServiceLocator.database(context).scheduledDao()
        val message = dao.get(id) ?: return
        // Remove first: a crash mid-send must not send the same text twice.
        dao.delete(id)
        val result = SmsSender(context).send(message.recipientList, message.body)
        if (result is SendResult.Failure) Log.w(TAG, "Scheduled send $id failed: ${result.reason}")
        ServiceLocator.notifyDataChanged()
    }

    private fun arm(context: Context, message: ScheduledMessage) {
        val manager = alarmManager(context) ?: return
        val pending = pendingIntent(context, message.id)
        val at = message.sendAt.coerceAtLeast(System.currentTimeMillis() + 1_000)
        val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()
        runCatching {
            if (exactAllowed) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            } else {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            }
        }.onFailure {
            Log.w(TAG, "Exact alarm refused, falling back", it)
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        }
    }

    private fun alarmManager(context: Context): AlarmManager? =
        context.getSystemService(AlarmManager::class.java)

    private fun pendingIntent(context: Context, id: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            ("scheduled$id").hashCode(),
            Intent(context, ScheduledSendReceiver::class.java).putExtra(EXTRA_ID, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}

/** Fires when a scheduled message is due. */
class ScheduledSendReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(MessageScheduler.EXTRA_ID, -1L)
        if (id <= 0) return
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                MessageScheduler.deliver(app, id)
            } finally {
                pending.finish()
            }
        }
    }
}
