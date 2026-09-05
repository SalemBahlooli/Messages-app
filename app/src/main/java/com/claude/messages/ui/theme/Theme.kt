package com.claude.messages.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Blue = Color(0xFF1A73E8)
private val BlueDark = Color(0xFFA8C7FA)

private val LightColors = lightColorScheme(
    primary = Blue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E3FD),
    onPrimaryContainer = Color(0xFF041E49),
    secondary = Color(0xFF3B6939),
    surface = Color(0xFFFDFCFF),
    surfaceVariant = Color(0xFFE1E2EC),
    background = Color(0xFFFDFCFF),
    error = Color(0xFFB3261E),
)

private val DarkColors = darkColorScheme(
    primary = BlueDark,
    onPrimary = Color(0xFF062E6F),
    primaryContainer = Color(0xFF0842A0),
    onPrimaryContainer = Color(0xFFD3E3FD),
    secondary = Color(0xFF9FD49B),
    surface = Color(0xFF131316),
    surfaceVariant = Color(0xFF44464F),
    background = Color(0xFF131316),
    error = Color(0xFFF2B8B5),
)

/** Colours used to tint avatars, derived deterministically from the contact name. */
val AvatarColors = listOf(
    Color(0xFF1A73E8), Color(0xFFD93025), Color(0xFF188038), Color(0xFFE37400),
    Color(0xFF9334E6), Color(0xFF12B5CB), Color(0xFFC5221F), Color(0xFF7627BB),
)

fun avatarColorFor(key: String): Color =
    AvatarColors[(key.hashCode().let { if (it == Int.MIN_VALUE) 0 else kotlin.math.abs(it) }) % AvatarColors.size]

@Composable
fun MessagesTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content,
    )
}
