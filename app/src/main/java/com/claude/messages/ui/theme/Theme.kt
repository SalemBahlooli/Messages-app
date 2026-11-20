package com.claude.messages.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.claude.messages.data.prefs.Appearance
import com.claude.messages.data.prefs.ThemeMode

/*
 * The palette is the app's own rather than the wallpaper's by default. Material
 * You made every build look identical on a device whose system accent never
 * changed, which hid the redesign completely; Settings can still switch to it.
 */

private val LightColors = lightColorScheme(
    primary = Color(0xFF4B49D6),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE2E0FF),
    onPrimaryContainer = Color(0xFF0E0066),
    inversePrimary = Color(0xFFC0C0FF),
    secondary = Color(0xFF5C5B70),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE3E0F2),
    onSecondaryContainer = Color(0xFF191826),
    tertiary = Color(0xFF0D7A6E),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFC6F1E9),
    onTertiaryContainer = Color(0xFF00201C),
    background = Color(0xFFF9F8FD),
    onBackground = Color(0xFF17161C),
    surface = Color(0xFFF9F8FD),
    onSurface = Color(0xFF17161C),
    surfaceVariant = Color(0xFFE4E1EF),
    onSurfaceVariant = Color(0xFF4A4858),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF3F1FB),
    surfaceContainer = Color(0xFFEDEBF7),
    surfaceContainerHigh = Color(0xFFE7E4F3),
    surfaceContainerHighest = Color(0xFFE0DDEE),
    outline = Color(0xFF7A7889),
    outlineVariant = Color(0xFFCBC7DA),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFC1C0FF),
    onPrimary = Color(0xFF1C1A8C),
    primaryContainer = Color(0xFF3836AE),
    onPrimaryContainer = Color(0xFFE2E0FF),
    inversePrimary = Color(0xFF4B49D6),
    secondary = Color(0xFFC6C4DC),
    onSecondary = Color(0xFF2E2D3F),
    secondaryContainer = Color(0xFF444356),
    onSecondaryContainer = Color(0xFFE3E0F2),
    tertiary = Color(0xFF8FD6C9),
    onTertiary = Color(0xFF003731),
    tertiaryContainer = Color(0xFF005048),
    onTertiaryContainer = Color(0xFFC6F1E9),
    background = Color(0xFF111017),
    onBackground = Color(0xFFE5E1F0),
    surface = Color(0xFF111017),
    onSurface = Color(0xFFE5E1F0),
    surfaceVariant = Color(0xFF474554),
    onSurfaceVariant = Color(0xFFC9C5D8),
    surfaceContainerLowest = Color(0xFF0B0A10),
    surfaceContainerLow = Color(0xFF17161F),
    surfaceContainer = Color(0xFF1C1B25),
    surfaceContainerHigh = Color(0xFF262430),
    surfaceContainerHighest = Color(0xFF312F3C),
    outline = Color(0xFF938FA2),
    outlineVariant = Color(0xFF474554),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

/**
 * Nothing in the app has a hard corner: the smallest radius is still clearly
 * round, and the large shapes are nearly pill-shaped.
 */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(34.dp),
)

/**
 * Deliberately a step tighter than the Material defaults. The stock scale reads
 * as magnified on a phone list that should fit eight conversations on screen.
 */
private val AppTypography = Typography(
    headlineMedium = TextStyle(
        fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold,
    ),
    headlineSmall = TextStyle(
        fontSize = 21.sp, lineHeight = 27.sp, fontWeight = FontWeight.SemiBold,
    ),
    titleLarge = TextStyle(
        fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold,
    ),
    titleMedium = TextStyle(
        fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold,
    ),
    titleSmall = TextStyle(
        fontSize = 13.5.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium,
    ),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 21.sp),
    bodyMedium = TextStyle(fontSize = 13.5.sp, lineHeight = 19.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(
        fontSize = 13.5.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium,
    ),
    labelMedium = TextStyle(
        fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium,
    ),
    labelSmall = TextStyle(
        fontSize = 10.5.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium,
    ),
)

/** Applies the chosen typeface to every text style in one go. */
private fun Typography.withFamily(family: FontFamily): Typography = Typography(
    displayLarge = displayLarge.copy(fontFamily = family),
    displayMedium = displayMedium.copy(fontFamily = family),
    displaySmall = displaySmall.copy(fontFamily = family),
    headlineLarge = headlineLarge.copy(fontFamily = family),
    headlineMedium = headlineMedium.copy(fontFamily = family),
    headlineSmall = headlineSmall.copy(fontFamily = family),
    titleLarge = titleLarge.copy(fontFamily = family),
    titleMedium = titleMedium.copy(fontFamily = family),
    titleSmall = titleSmall.copy(fontFamily = family),
    bodyLarge = bodyLarge.copy(fontFamily = family),
    bodyMedium = bodyMedium.copy(fontFamily = family),
    bodySmall = bodySmall.copy(fontFamily = family),
    labelLarge = labelLarge.copy(fontFamily = family),
    labelMedium = labelMedium.copy(fontFamily = family),
    labelSmall = labelSmall.copy(fontFamily = family),
)

/** Palette used to tint avatars, chosen deterministically from the contact name. */
val AvatarColors = listOf(
    Color(0xFF4B49D6), Color(0xFFC2410C), Color(0xFF0D7A6E), Color(0xFF9333EA),
    Color(0xFF0E7490), Color(0xFFBE123C), Color(0xFF854D0E), Color(0xFF3F6212),
)

fun avatarColorFor(key: String): Color {
    val hash = key.hashCode()
    val index = (if (hash == Int.MIN_VALUE) 0 else kotlin.math.abs(hash)) % AvatarColors.size
    return AvatarColors[index]
}

/** The canvas a conversation is drawn on: a shade apart from the bubbles. */
val ColorScheme.chatCanvas: Color get() = surfaceContainerLow

@Composable
fun MessagesTheme(content: @Composable () -> Unit) {
    val settings by Appearance.settings.collectAsStateWithLifecycle()
    val darkTheme = isSystemInDarkTheme()
    val context = LocalContext.current

    val colorScheme = when {
        settings.themeMode == ThemeMode.SYSTEM && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        darkTheme -> DarkColors
        else -> LightColors
    }

    // Both the device density and the device font scale are folded into one
    // factor here, so "everything is zoomed in" is something the user can fix
    // inside the app instead of in Android's display settings.
    val systemDensity = LocalDensity.current
    val density = remember(systemDensity, settings.scale) {
        val factor = settings.scale.factor
        Density(
            density = systemDensity.density * factor,
            fontScale = systemDensity.fontScale.coerceIn(0.85f, 1.15f) * factor,
        )
    }

    val fonts by AppFonts.fonts.collectAsStateWithLifecycle()
    val typography = remember(settings.fontId, fonts) {
        when (val family = AppFonts.familyFor(settings.fontId)) {
            FontFamily.Default -> AppTypography
            else -> AppTypography.withFamily(family)
        }
    }

    CompositionLocalProvider(LocalDensity provides density) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = AppShapes,
            content = content,
        )
    }
}

/** Message-bubble text style: the body size with a little more air between lines. */
val BubbleTextStyle: TextStyle
    @Composable get() = MaterialTheme.typography.bodyLarge.copy(lineHeight = 21.sp)
