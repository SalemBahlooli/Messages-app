package com.claude.messages.ui.lock

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.SystemClock
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.claude.messages.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Locks the app behind the phone's own biometrics or screen lock.
 *
 * The lock engages on a cold start and whenever the app has been in the
 * background longer than a short grace period, so stepping out to the system
 * share sheet or a ringtone picker doesn't demand a fingerprint on return.
 */
object AppLock {

    private const val GRACE_MS = 10_000L

    private val _locked = MutableStateFlow(false)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    private var backgroundedAt = 0L
    private var coldStart = true
    private var prompting = false

    /** Whether this phone has something to unlock with at all. */
    fun isAvailable(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BiometricManager.from(context).canAuthenticate(BIOMETRIC_WEAK or DEVICE_CREDENTIAL) ==
                BiometricManager.BIOMETRIC_SUCCESS
        } else {
            context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true
        }

    fun onStart(context: Context, enabled: Boolean) {
        if (!enabled || !isAvailable(context)) {
            _locked.value = false
            coldStart = false
            return
        }
        val away = SystemClock.elapsedRealtime() - backgroundedAt
        if (coldStart || away > GRACE_MS) _locked.value = true
        coldStart = false
    }

    fun onStop() {
        backgroundedAt = SystemClock.elapsedRealtime()
    }

    /** Called when the setting is switched off, so the overlay goes away at once. */
    fun disable() {
        _locked.value = false
    }

    @Suppress("DEPRECATION")
    fun prompt(activity: FragmentActivity) {
        if (prompting || !_locked.value) return
        prompting = true
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    prompting = false
                    _locked.value = false
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    // Cancelled or locked out: stay locked; the button tries again.
                    prompting = false
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(activity.getString(R.string.locked_title))
            .setSubtitle(activity.getString(R.string.unlock_subtitle))
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
                } else {
                    // Before Android 11 this is how the PIN/pattern fallback is offered.
                    setDeviceCredentialAllowed(true)
                }
            }
            .build()
        runCatching { prompt.authenticate(info) }.onFailure { prompting = false }
    }
}

/** Covers the whole app while locked. */
@Composable
fun LockScreen(onUnlock: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(
                    Icons.Default.Lock,
                    null,
                    Modifier.padding(22.dp).size(36.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(
                stringResource(R.string.locked_title),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.unlock_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onUnlock) {
                Icon(Icons.Default.Fingerprint, null, Modifier.size(18.dp))
                Spacer(Modifier.padding(start = 8.dp))
                Text(stringResource(R.string.unlock))
            }
        }
    }
}
