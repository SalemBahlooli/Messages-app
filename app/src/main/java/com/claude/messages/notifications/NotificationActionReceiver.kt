package com.claude.messages.notifications

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.claude.messages.di.ServiceLocator
import com.claude.messages.sms.SmsSender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Handles the notification's inline reply, mark-as-read and delete actions. */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val threadId = intent.getLongExtra(EXTRA_THREAD_ID, -1L)
        if (threadId <= 0) return

        val app = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = ServiceLocator.smsRepository(app)
                val notifier = MessageNotifier(app)
                when (intent.action) {
                    ACTION_REPLY -> {
                        val text = RemoteInput.getResultsFromIntent(intent)
                            ?.getCharSequence(KEY_REPLY_TEXT)?.toString().orEmpty().trim()
                        if (text.isNotEmpty()) {
                            val recipients = repo.recipientsForThread(threadId)
                            if (recipients.isNotEmpty()) SmsSender(app).send(recipients, text)
                        }
                        repo.markThreadRead(threadId)
                        notifier.cancel(threadId)
                    }

                    ACTION_MARK_READ -> {
                        repo.markThreadRead(threadId)
                        notifier.cancel(threadId)
                    }

                    ACTION_DELETE -> {
                        repo.deleteThread(threadId)
                        notifier.cancel(threadId)
                    }

                    ACTION_DISMISS -> notifier.cancel(threadId)
                }
                ServiceLocator.notifyDataChanged()
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val KEY_REPLY_TEXT = "key_reply_text"
        const val EXTRA_THREAD_ID = "thread_id"

        const val ACTION_REPLY = "com.claude.messages.action.REPLY"
        const val ACTION_MARK_READ = "com.claude.messages.action.MARK_READ"
        const val ACTION_DELETE = "com.claude.messages.action.DELETE"
        const val ACTION_DISMISS = "com.claude.messages.action.DISMISS"

        private fun intentFor(context: Context, action: String, threadId: Long): PendingIntent {
            val intent = Intent(context, NotificationActionReceiver::class.java).apply {
                this.action = action
                putExtra(EXTRA_THREAD_ID, threadId)
            }
            // Reply needs a mutable intent so RemoteInput can attach its results.
            val flags = if (action == ACTION_REPLY) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            }
            return PendingIntent.getBroadcast(
                context, (action + threadId).hashCode(), intent, flags,
            )
        }

        fun replyIntent(context: Context, threadId: Long) =
            intentFor(context, ACTION_REPLY, threadId)

        fun markReadIntent(context: Context, threadId: Long) =
            intentFor(context, ACTION_MARK_READ, threadId)

        fun dismissIntent(context: Context, threadId: Long) =
            intentFor(context, ACTION_DISMISS, threadId)
    }
}
