package com.claude.messages

import android.app.Application
import com.claude.messages.data.db.NotificationRule
import com.claude.messages.data.db.ContentMatch
import com.claude.messages.data.db.RuleImportance
import com.claude.messages.data.db.SenderMatch
import com.claude.messages.data.db.VibrationPattern
import com.claude.messages.di.ServiceLocator
import com.claude.messages.notifications.ChannelManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MessagesApp : Application() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        ChannelManager(this).ensureBaseChannels()
        scope.launch { seedStarterRules() }
    }

    /**
     * First launch ships two disabled example rules so the feature is
     * discoverable rather than an empty screen.
     */
    private suspend fun seedStarterRules() {
        val dao = ServiceLocator.database(this).ruleDao()
        if (dao.getAll().isNotEmpty()) return

        dao.insert(
            NotificationRule(
                name = "Verification codes",
                enabled = false,
                order = 0,
                senderMatch = SenderMatch.ANY,
                contentMatch = ContentMatch.CONTAINS_ANY,
                contentValues = "code\nOTP\nverification\nرمز\nتحقق",
                importance = RuleImportance.HIGH,
                vibration = VibrationPattern.DOUBLE,
                titlePrefix = "🔐",
                hideContentOnLockScreen = true,
            )
        )
        dao.insert(
            NotificationRule(
                name = "Unknown senders stay quiet",
                enabled = false,
                order = 1,
                senderMatch = SenderMatch.NOT_IN_CONTACTS,
                contentMatch = ContentMatch.ANY,
                importance = RuleImportance.LOW,
                vibration = VibrationPattern.NONE,
            )
        )
    }
}
