package com.claude.messages.data.prefs

import android.content.Context
import androidx.annotation.StringRes
import com.claude.messages.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * How large the interface draws. Android's own "display size" setting scales
 * every app at once; this scales only this app, so a phone configured for large
 * system text can still show a dense message list.
 */
enum class UiScale(@StringRes val labelRes: Int, val factor: Float) {
    COMPACT(R.string.scale_compact, 0.82f),
    COSY(R.string.scale_cosy, 0.90f),
    STANDARD(R.string.scale_standard, 1.00f),
    LARGE(R.string.scale_large, 1.12f),
}

/** The colour source for the theme. */
enum class ThemeMode(@StringRes val labelRes: Int) {
    /** The app's own palette — identical on every device. */
    BRAND(R.string.theme_brand),

    /** Material You: colours derived from the system wallpaper (Android 12+). */
    SYSTEM(R.string.theme_system),
}

data class AppearanceSettings(
    val scale: UiScale = UiScale.COSY,
    val themeMode: ThemeMode = ThemeMode.BRAND,
)

/**
 * Appearance is read by the theme, which sits above every view model, so it
 * lives in a process-wide holder rather than in one screen's state.
 */
object Appearance {

    private val _settings = MutableStateFlow(AppearanceSettings())
    val settings: StateFlow<AppearanceSettings> = _settings.asStateFlow()

    private var prefs: android.content.SharedPreferences? = null

    fun init(context: Context) {
        val store = context.applicationContext
            .getSharedPreferences("appearance", Context.MODE_PRIVATE)
        prefs = store
        _settings.value = AppearanceSettings(
            scale = store.getString(KEY_SCALE, null).toScale(),
            themeMode = store.getString(KEY_THEME, null).toThemeMode(),
        )
    }

    fun setScale(scale: UiScale) {
        prefs?.edit()?.putString(KEY_SCALE, scale.name)?.apply()
        _settings.value = _settings.value.copy(scale = scale)
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs?.edit()?.putString(KEY_THEME, mode.name)?.apply()
        _settings.value = _settings.value.copy(themeMode = mode)
    }

    private fun String?.toScale(): UiScale =
        UiScale.entries.firstOrNull { it.name == this } ?: UiScale.COSY

    private fun String?.toThemeMode(): ThemeMode =
        ThemeMode.entries.firstOrNull { it.name == this } ?: ThemeMode.BRAND

    private const val KEY_SCALE = "ui_scale"
    private const val KEY_THEME = "theme_mode"
}
