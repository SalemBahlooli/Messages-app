package com.claude.messages.ui.theme

import android.content.Context
import android.net.Uri
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * A typeface the user can pick. Either one of the families Android itself
 * provides, or a font file the user installed from their own storage.
 */
data class AppFont(
    /** Stable id stored in preferences. */
    val id: String,
    /** Shown in Settings; for an installed font this is the file's own name. */
    val displayName: String,
    val family: FontFamily,
    val installed: Boolean = false,
)

/**
 * The font catalogue: four built-in families, plus any .ttf/.otf the user has
 * added. Installing copies the file into the app's own storage, because the
 * document Uri the picker hands back is not readable after a reboot.
 *
 * No font is downloaded — the app holds no INTERNET permission and will not ask
 * for one for the sake of a typeface.
 */
object AppFonts {

    const val SYSTEM_ID = "system"

    private val builtIn = listOf(
        AppFont(SYSTEM_ID, "System", FontFamily.Default),
        AppFont("sans", "Sans", FontFamily.SansSerif),
        AppFont("serif", "Serif", FontFamily.Serif),
        AppFont("mono", "Mono", FontFamily.Monospace),
    )

    private val _fonts = MutableStateFlow(builtIn)

    /** Built-in families first, then installed files in the order they appear. */
    val fonts: StateFlow<List<AppFont>> = _fonts.asStateFlow()

    private var dir: File? = null

    fun init(context: Context) {
        dir = File(context.applicationContext.filesDir, "fonts").apply { mkdirs() }
        reload()
    }

    /** True when [id] names a font that is actually available right now. */
    fun has(id: String): Boolean = _fonts.value.any { it.id == id }

    fun familyFor(id: String): FontFamily =
        _fonts.value.firstOrNull { it.id == id }?.family ?: FontFamily.Default

    /**
     * Copies the picked document into app storage and adds it to the catalogue.
     * Returns the new font, or null when the file could not be read or is not a
     * font Android can load.
     */
    fun install(context: Context, uri: Uri): AppFont? {
        val target = dir ?: return null
        val name = fileNameOf(context, uri) ?: "font-${System.currentTimeMillis()}.ttf"
        if (!name.lowercase().endsWithFontExtension()) return null

        val file = File(target, sanitize(name))
        val copied = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
        }
        if (copied.isFailure || file.length() == 0L) {
            file.delete()
            return null
        }

        // Loading it once here is the only way to find out whether Android can
        // actually parse it; a bad file would otherwise crash every screen.
        val family = runCatching { FontFamily(Font(file)) }.getOrNull()
        if (family == null) {
            file.delete()
            return null
        }

        reload()
        return _fonts.value.firstOrNull { it.id == idOf(file) }
    }

    fun remove(id: String) {
        val target = dir ?: return
        target.listFiles()?.firstOrNull { idOf(it) == id }?.delete()
        reload()
    }

    private fun reload() {
        val files = dir?.listFiles()
            ?.filter { it.isFile && it.name.lowercase().endsWithFontExtension() }
            ?.sortedBy { it.name }
            .orEmpty()

        val loaded = files.mapNotNull { file ->
            runCatching {
                AppFont(
                    id = idOf(file),
                    displayName = file.nameWithoutExtension,
                    family = FontFamily(Font(file)),
                    installed = true,
                )
            }.getOrNull()
        }
        _fonts.value = builtIn + loaded
    }

    private fun idOf(file: File): String = "file:${file.name}"

    private fun sanitize(name: String): String =
        name.replace(Regex("""[^A-Za-z0-9._\-؀-ۿ ]"""), "_").take(64)

    private fun String.endsWithFontExtension(): Boolean =
        endsWith(".ttf") || endsWith(".otf")

    private fun fileNameOf(context: Context, uri: Uri): String? {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val index = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && c.moveToFirst()) return c.getString(index)
        }
        return uri.lastPathSegment?.substringAfterLast('/')
    }
}
