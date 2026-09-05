package com.claude.messages.ui.settings

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
    val isDefault = DefaultSmsHelper.isDefault(context)

    Scaffold(
        topBar = {
            TopAppBar(
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
                    context.startActivity(intent)
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

            SectionHeader("About")
            SettingRow(
                title = "Messages",
                subtitle = "A full SMS client with per-sender and per-keyword notification rules",
                leading = Icons.Default.Info,
            )
        }
    }
}
