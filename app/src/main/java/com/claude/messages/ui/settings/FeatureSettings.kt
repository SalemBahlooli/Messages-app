package com.claude.messages.ui.settings

import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Quickreply
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.claude.messages.R
import com.claude.messages.data.prefs.FeaturePrefs
import com.claude.messages.ui.compose.SectionHeader
import com.claude.messages.ui.compose.SegmentedChoice
import com.claude.messages.ui.compose.SettingRow
import com.claude.messages.ui.lock.AppLock
import com.claude.messages.ui.thread.QuickRepliesDialog
import java.util.Calendar

/** Undo send, the verification-code helper and quick replies. */
@Composable
internal fun SmartFeatureSettings() {
    val settings by FeaturePrefs.settings.collectAsState()
    var editingReplies by remember { mutableStateOf(false) }

    SectionHeader(stringResource(R.string.settings_smart))

    SettingRow(
        title = stringResource(R.string.undo_send),
        subtitle = stringResource(R.string.undo_send_hint),
        leading = Icons.AutoMirrored.Filled.Undo,
    )
    SegmentedChoice(
        options = FeaturePrefs.UNDO_CHOICES,
        selected = settings.undoSeconds,
        label = { s ->
            if (s == 0) stringResource(R.string.undo_off) else stringResource(R.string.seconds_short, s)
        },
        onSelect = FeaturePrefs::setUndoSeconds,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
    )
    Spacer(Modifier.height(6.dp))

    SettingRow(
        title = stringResource(R.string.otp_helper),
        subtitle = stringResource(R.string.otp_helper_hint),
        leading = Icons.Default.Key,
        trailing = { Switch(checked = settings.otpHelper, onCheckedChange = FeaturePrefs::setOtpHelper) },
        onClick = { FeaturePrefs.setOtpHelper(!settings.otpHelper) },
    )

    SettingRow(
        title = stringResource(R.string.quick_replies),
        subtitle = settings.quickReplies.joinToString(" · ").ifBlank { null },
        leading = Icons.Default.Quickreply,
        onClick = { editingReplies = true },
    )

    if (editingReplies) {
        QuickRepliesDialog(
            replies = settings.quickReplies,
            onAdd = FeaturePrefs::addQuickReply,
            onRemove = FeaturePrefs::removeQuickReply,
            onDismiss = { editingReplies = false },
        )
    }
}

/** App lock, quiet hours and the block list. */
@Composable
internal fun PrivacySettings(onOpenBlocked: () -> Unit) {
    val context = LocalContext.current
    val settings by FeaturePrefs.settings.collectAsState()
    val lockAvailable = remember { AppLock.isAvailable(context) }

    SectionHeader(stringResource(R.string.settings_privacy))

    SettingRow(
        title = stringResource(R.string.app_lock),
        subtitle = stringResource(
            if (lockAvailable) R.string.app_lock_hint else R.string.app_lock_unavailable
        ),
        leading = Icons.Default.Lock,
        trailing = {
            Switch(
                checked = settings.appLockEnabled && lockAvailable,
                enabled = lockAvailable,
                onCheckedChange = { on ->
                    FeaturePrefs.setAppLock(on)
                    if (!on) AppLock.disable()
                },
            )
        },
    )

    SettingRow(
        title = stringResource(R.string.quiet_hours),
        subtitle = stringResource(R.string.quiet_hours_hint),
        leading = Icons.Default.Bedtime,
        trailing = {
            Switch(checked = settings.quietHoursEnabled, onCheckedChange = FeaturePrefs::setQuietHours)
        },
        onClick = { FeaturePrefs.setQuietHours(!settings.quietHoursEnabled) },
    )
    if (settings.quietHoursEnabled) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TimeButton(
                label = { stringResource(R.string.quiet_from, it) },
                minutes = settings.quietStart,
                onPick = FeaturePrefs::setQuietStart,
                modifier = Modifier.weight(1f),
            )
            TimeButton(
                label = { stringResource(R.string.quiet_to, it) },
                minutes = settings.quietEnd,
                onPick = FeaturePrefs::setQuietEnd,
                modifier = Modifier.weight(1f),
            )
        }
    }

    SettingRow(
        title = stringResource(R.string.blocked_numbers),
        subtitle = stringResource(R.string.blocked_numbers_hint),
        leading = Icons.Default.Block,
        onClick = onOpenBlocked,
    )
}

@Composable
internal fun LibrarySettings(onOpenStarred: () -> Unit, onOpenInsights: () -> Unit) {
    SectionHeader(stringResource(R.string.settings_library))
    SettingRow(
        title = stringResource(R.string.starred_messages),
        subtitle = stringResource(R.string.starred_messages_hint),
        leading = Icons.Default.Star,
        onClick = onOpenStarred,
    )
    SettingRow(
        title = stringResource(R.string.insights),
        subtitle = stringResource(R.string.insights_hint),
        leading = Icons.Default.Insights,
        onClick = onOpenInsights,
    )
}

@Composable
private fun TimeButton(
    label: @Composable (String) -> String,
    minutes: Int,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val formatted = remember(minutes) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, minutes / 60)
            set(Calendar.MINUTE, minutes % 60)
        }
        DateFormat.getTimeFormat(context).format(cal.time)
    }
    OutlinedButton(
        onClick = {
            TimePickerDialog(
                context,
                { _, h, m -> onPick(h * 60 + m) },
                minutes / 60,
                minutes % 60,
                DateFormat.is24HourFormat(context),
            ).show()
        },
        modifier = modifier,
    ) {
        Text(label(formatted), style = MaterialTheme.typography.labelLarge)
    }
}
