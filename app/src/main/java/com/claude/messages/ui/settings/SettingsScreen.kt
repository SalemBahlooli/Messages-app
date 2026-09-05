package com.claude.messages.ui.settings

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.claude.messages.ui.compose.SectionHeader
import com.claude.messages.ui.compose.SettingRow
import com.claude.messages.util.DefaultSmsHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenRules: () -> Unit,
    onRequestDefaultSms: () -> Unit,
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
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            SectionHeader("Notifications")
            SettingRow(
                title = "Notification rules",
                subtitle = "Custom sounds and vibration per contact or keyword",
                leading = Icons.Default.Tune,
                onClick = onOpenRules,
            )
            SettingRow(
                title = "System notification settings",
                subtitle = "Channel volumes, badges and Do Not Disturb",
                leading = Icons.Default.Notifications,
                onClick = {
                    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    } else {
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            .setData(android.net.Uri.parse("package:${context.packageName}"))
                    }
                    runCatching { context.startActivity(intent) }
                },
            )

            HorizontalDivider()

            SectionHeader("SMS")
            SettingRow(
                title = "Default SMS app",
                subtitle = if (isDefault) "Messages is your default SMS app"
                else "Tap to make Messages the default",
                leading = if (isDefault) Icons.Default.CheckCircle else Icons.Default.Sms,
                onClick = if (isDefault) null else onRequestDefaultSms,
            )

            HorizontalDivider()

            // Ground truth for "why can't I send?" — what the system actually
            // reports, rather than what the app assumes.
            SectionHeader("Diagnostics")
            Column(Modifier.padding(horizontal = 20.dp)) {
                StatusLine("Default SMS app", isDefault)
                StatusLine("Can send right now", blocker == null)
                blocker?.let {
                    Text(
                        it.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                StatusLine("Cellular radio", hasTelephony)
                StatusLine("Read messages", DefaultSmsHelper.hasPermission(context, Manifest.permission.READ_SMS))
                StatusLine("Send messages", DefaultSmsHelper.hasPermission(context, Manifest.permission.SEND_SMS))
                StatusLine("Receive messages", DefaultSmsHelper.hasPermission(context, Manifest.permission.RECEIVE_SMS))
                StatusLine("Contacts", DefaultSmsHelper.hasPermission(context, Manifest.permission.READ_CONTACTS))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    StatusLine(
                        "Notifications",
                        DefaultSmsHelper.hasPermission(context, Manifest.permission.POST_NOTIFICATIONS),
                    )
                }

                Spacer(Modifier.padding(top = 8.dp))
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        DetailLine("SIM state", simState)
                        DetailLine("System's SMS app", defaultPackage ?: "none")
                        DetailLine("This app", context.packageName)
                        DetailLine("Android", "API ${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})")
                        DetailLine("Device", "${Build.MANUFACTURER} ${Build.MODEL}")
                    }
                }
            }

            HorizontalDivider(Modifier.padding(top = 16.dp))

            SectionHeader("About")
            SettingRow(
                title = "Messages",
                subtitle = "A full SMS client with per-sender and per-keyword notification rules",
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
            contentDescription = if (ok) "Yes" else "No",
            modifier = Modifier.size(18.dp),
            tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            if (ok) "Yes" else "No",
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
            modifier = Modifier.width(130.dp),
        )
        Text(
            value,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f),
        )
    }
}
