package dev.retrobit.bitkeyboard.ime

import android.content.res.Configuration
import android.graphics.Color
import dev.retrobit.bitkeyboard.settings.SettingsStore

/** Warna dan ukuran keyboard. */
class KeyboardTheme(
    val background: Int,
    val suggestionBackground: Int,
    val key: Int,
    val keyPressed: Int,
    val functionKey: Int,
    val actionKey: Int,
    val spaceKey: Int,
    val text: Int,
    val hint: Int,
    val accent: Int,
    val accentText: Int,
    val divider: Int,
    val trail: Int,
    val popupBackground: Int,
    val isDark: Boolean,
) {
    val actionText: Int get() = accentText

    /** Salinan tema dengan warna aksen pilihan pengguna. */
    fun withAccent(color: Int?): KeyboardTheme {
        if (color == null) return this
        val onAccent = if (luminance(color) > 0.62) Color.BLACK else Color.WHITE
        return KeyboardTheme(
            background = background,
            suggestionBackground = suggestionBackground,
            key = key,
            keyPressed = keyPressed,
            functionKey = functionKey,
            actionKey = color,
            spaceKey = spaceKey,
            text = text,
            hint = hint,
            accent = color,
            accentText = onAccent,
            divider = divider,
            trail = blend(color, if (isDark) Color.WHITE else Color.BLACK, 0.25f),
            popupBackground = popupBackground,
            isDark = isDark,
        )
    }

    companion object {
        /** Terang/dark-nya sebuah warna (0..1) untuk memilih warna teks. */
        fun luminance(color: Int): Double {
            val r = Color.red(color) / 255.0
            val g = Color.green(color) / 255.0
            val b = Color.blue(color) / 255.0
            return 0.2126 * r + 0.7152 * g + 0.0722 * b
        }

        fun blend(base: Int, other: Int, ratio: Float): Int {
            val r = (Color.red(base) * (1 - ratio) + Color.red(other) * ratio).toInt()
            val g = (Color.green(base) * (1 - ratio) + Color.green(other) * ratio).toInt()
            val b = (Color.blue(base) * (1 - ratio) + Color.blue(other) * ratio).toInt()
            return Color.rgb(r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
        }

        /** Palet aksen yang ditawarkan di pengaturan. */
        val ACCENT_CHOICES: List<Pair<String, Int>> = listOf(
            "Biru" to 0xFF2D6CDF.toInt(),
            "Tosca" to 0xFF0FA3A3.toInt(),
            "Hijau" to 0xFF17A673.toInt(),
            "Ungu" to 0xFF7C5CFF.toInt(),
            "Oranye" to 0xFFF17C3A.toInt(),
            "Merah" to 0xFFE0464B.toInt(),
            "Pink" to 0xFFE8467C.toInt(),
            "Abu" to 0xFF64748B.toInt(),
        )

        val DARK = KeyboardTheme(
            background = Color.parseColor("#0F141A"),
            suggestionBackground = Color.parseColor("#151C24"),
            key = Color.parseColor("#232C36"),
            keyPressed = Color.parseColor("#3B4A5C"),
            functionKey = Color.parseColor("#1A222B"),
            actionKey = Color.parseColor("#2D6CDF"),
            spaceKey = Color.parseColor("#2B3642"),
            text = Color.parseColor("#EAF1FB"),
            hint = Color.parseColor("#94A6BA"),
            accent = Color.parseColor("#4C8DFF"),
            accentText = Color.parseColor("#FFFFFF"),
            divider = Color.parseColor("#263140"),
            trail = Color.parseColor("#66A9FF"),
            popupBackground = Color.parseColor("#2A3542"),
            isDark = true,
        )

        val LIGHT = KeyboardTheme(
            background = Color.parseColor("#DDE4EE"),
            suggestionBackground = Color.parseColor("#EEF2F8"),
            key = Color.parseColor("#FFFFFF"),
            keyPressed = Color.parseColor("#C7D4E5"),
            functionKey = Color.parseColor("#D3DDEA"),
            actionKey = Color.parseColor("#2D6CDF"),
            spaceKey = Color.parseColor("#F1F5FB"),
            text = Color.parseColor("#16202C"),
            hint = Color.parseColor("#5E7086"),
            accent = Color.parseColor("#2D6CDF"),
            accentText = Color.parseColor("#FFFFFF"),
            divider = Color.parseColor("#C4D0E0"),
            trail = Color.parseColor("#2D6CDF"),
            popupBackground = Color.parseColor("#FFFFFF"),
            isDark = false,
        )

        val AMOLED = KeyboardTheme(
            background = Color.parseColor("#000000"),
            suggestionBackground = Color.parseColor("#090C0F"),
            key = Color.parseColor("#14181D"),
            keyPressed = Color.parseColor("#2C333C"),
            functionKey = Color.parseColor("#0D1013"),
            actionKey = Color.parseColor("#2D6CDF"),
            spaceKey = Color.parseColor("#1A2027"),
            text = Color.parseColor("#F2F6FC"),
            hint = Color.parseColor("#7E8B99"),
            accent = Color.parseColor("#5B9BFF"),
            accentText = Color.parseColor("#FFFFFF"),
            divider = Color.parseColor("#1E242B"),
            trail = Color.parseColor("#5B9BFF"),
            popupBackground = Color.parseColor("#1B222A"),
            isDark = true,
        )

        fun resolve(settings: SettingsStore, nightMode: Boolean): KeyboardTheme {
            val base = when (settings.theme) {
                "light" -> LIGHT
                "dark" -> DARK
                "amoled" -> AMOLED
                else -> if (nightMode) DARK else LIGHT
            }
            val accent = parseColor(settings.accentColor)
            return base.withAccent(accent)
        }

        /** `#RRGGBB` → warna, atau `null` bila kosong/tidak valid. */
        fun parseColor(text: String): Int? {
            if (text.length != 7 || !text.startsWith("#")) return null
            return try {
                Color.parseColor(text)
            } catch (e: IllegalArgumentException) {
                null
            }
        }
    }
}

fun isNightMode(configuration: Configuration): Boolean =
    (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
