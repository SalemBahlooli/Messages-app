package com.claude.messages.ui.settings

import android.Manifest
import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.claude.messages.R
import com.claude.messages.ui.compose.SectionHeader
import com.claude.messages.ui.compose.SettingRow
import com.claude.messages.util.DefaultSmsHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    onOpenRules: () -> Unit,
    onRequestDefaultSms: () -> Unit,
    onOpenSystemNotifications: () -> Unit,
) {
    val context = LocalContext.current
    // Recomputed whenever the screen comes forward, so a change made in system
    // Settings is reflected the moment the user returns.
    var probe by remember { mutableStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { probe++ }

    val isDefault = remember(probe) { DefaultSmsHelper.isDefault(context) }
    val defaultPackage = remember(probe) { DefaultSmsHelper.defaultSmsPackage(context) }
    val simState = remember(probe) { DefaultSmsHelper.simStateLabel(context) }
    val hasTelephony = remember(probe) { DefaultSmsHelper.hasTelephony(context) }
    val blocker = remember(probe) { DefaultSmsHelper.sendBlocker(context) }

    Scaffold(
        modifier = Modifier.padding(contentPadding),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.tab_settings),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            SectionHeader(stringResource(R.string.settings_notifications))
            SettingRow(
                title = stringResource(R.string.notification_rules),
                subtitle = stringResource(R.string.rules_subtitle),
                leading = Icons.Default.Tune,
                onClick = onOpenRules,
            )
            SettingRow(
                title = stringResource(R.string.system_notification_settings),
                subtitle = stringResource(R.string.system_notification_body),
                leading = Icons.Default.Notifications,
                onClick = onOpenSystemNotifications,
            )

            SectionHeader(stringResource(R.string.settings_sms))
            SettingRow(
                title = stringResource(R.string.default_sms_app),
                subtitle = stringResource(
                    if (isDefault) R.string.is_default_yes else R.string.is_default_no
                ),
                leading = if (isDefault) Icons.Default.CheckCircle else Icons.Default.Sms,
                onClick = if (isDefault) null else onRequestDefaultSms,
            )

            // Ground truth for "why can't I send?" — what the system actually
            // reports, rather than what the app assumes.
            SectionHeader(stringResource(R.string.settings_diagnostics))
            Column(Modifier.padding(horizontal = 20.dp)) {
                StatusLine(stringResource(R.string.diag_default), isDefault)
                StatusLine(stringResource(R.string.diag_can_send), blocker == null)
                blocker?.let {
                    Text(
                        stringResource(it.messageRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                StatusLine(stringResource(R.string.diag_radio), hasTelephony)
                StatusLine(
                    stringResource(R.string.diag_read),
                    DefaultSmsHelper.hasPermission(context, Manifest.permission.READ_SMS),
                )
                StatusLine(
                    stringResource(R.string.diag_send),
                    DefaultSmsHelper.hasPermission(context, Manifest.permission.SEND_SMS),
                )
                StatusLine(
                    stringResource(R.string.diag_receive),
                    DefaultSmsHelper.hasPermission(context, Manifest.permission.RECEIVE_SMS),
                )
                StatusLine(
                    stringResource(R.string.diag_contacts),
                    DefaultSmsHelper.hasPermission(context, Manifest.permission.READ_CONTACTS),
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    StatusLine(
                        stringResource(R.string.diag_notifications),
                        DefaultSmsHelper.hasPermission(
                            context, Manifest.permission.POST_NOTIFICATIONS,
                        ),
                    )
                }

                Spacer(Modifier.height(12.dp))
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        DetailLine(stringResource(R.string.diag_sim), simState)
                        DetailLine(
                            stringResource(R.string.diag_system_sms),
                            defaultPackage ?: stringResource(R.string.none),
                        )
                        DetailLine(stringResource(R.string.diag_this_app), context.packageName)
                        DetailLine(
                            stringResource(R.string.diag_android),
                            "API ${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})",
                        )
                        DetailLine(
                            stringResource(R.string.diag_device),
                            "${Build.MANUFACTURER} ${Build.MODEL}",
                        )
                    }
                }
            }

            SectionHeader(stringResource(R.string.settings_about))
            SettingRow(
                title = stringResource(R.string.app_name),
                subtitle = stringResource(R.string.about_body),
                leading = Icons.Default.Info,
            )
        }
    }
}

@Composable
private fun StatusLine(label: String, ok: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (ok) Icons.Default.CheckCircle else Icons.Default.Cancel,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            stringResource(if (ok) R.string.yes else R.string.no),
            style = MaterialTheme.typography.labelMedium,
            color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(140.dp),
        )
        Text(
            value,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f),
        )
    }
}
