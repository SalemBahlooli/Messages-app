package com.claude.messages.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.claude.messages.util.DefaultSmsHelper

/**
 * Shown until the app is usable: it needs the SMS permissions and the default
 * SMS role. Includes help for the case where Android blocks the role because
 * the app was installed as an APK rather than from a store.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupCard(
    needsPermissions: Boolean,
    onGrantPermissions: () -> Unit,
    onRequestDefaultSms: () -> Unit,
    onOpenAppSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var helpOpen by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Sms,
                    null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = if (needsPermissions) "Almost ready" else "One last step",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (needsPermissions) {
                    "Messages needs access to your texts and contacts to show your conversations."
                } else {
                    "Set Messages as your default SMS app to send and receive texts and to use notification rules."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!needsPermissions && DefaultSmsHelper.restrictedSettingsLikely) {
                    TextButton(onClick = { helpOpen = true }) { Text("It was blocked") }
                    Spacer(Modifier.width(8.dp))
                }
                Button(
                    onClick = if (needsPermissions) onGrantPermissions else onRequestDefaultSms
                ) {
                    Text(if (needsPermissions) "Continue" else "Set as default")
                }
            }
        }
    }

    if (helpOpen) {
        ModalBottomSheet(
            onDismissRequest = { helpOpen = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            RestrictedSettingsHelp(
                onOpenAppSettings = {
                    helpOpen = false
                    onOpenAppSettings()
                },
            )
        }
    }
}

/**
 * Explains Android's "restricted settings" guard, which blocks sideloaded apps
 * from becoming the default SMS handler until the user allows it by hand.
 */
@Composable
fun RestrictedSettingsHelp(onOpenAppSettings: () -> Unit) {
    Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 32.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Text(
                "\"Denied access to be default SMS app\"",
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Android blocks apps installed from an APK — rather than from a store — " +
                "from taking sensitive roles like the SMS handler. It isn't a fault in " +
                "this app, and you can allow it in two taps.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        listOf(
            "Open this app's page in system Settings.",
            "Tap the ⋮ menu at the top right.",
            "Choose \"Allow restricted settings\".",
            "Come back here and tap Set as default.",
        ).forEachIndexed { index, step ->
            Row(Modifier.padding(bottom = 14.dp)) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(28.dp),
                ) {
                    Column(
                        Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("${index + 1}", style = MaterialTheme.typography.labelLarge)
                    }
                }
                Spacer(Modifier.width(14.dp))
                Text(
                    step,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "The menu item only appears after the app has been denied once — which has " +
                "already happened, so it should be there now.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onOpenAppSettings, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Open app settings")
        }
    }
}
