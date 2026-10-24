package dev.retrobit.bitkeyboard.settings

import android.content.Context
import android.content.SharedPreferences
import dev.retrobit.bitkeyboard.core.KeyCustomization
import dev.retrobit.bitkeyboard.core.KeyOverride
import dev.retrobit.bitkeyboard.core.UserModel

/**
 * Penyimpanan preferensi. Semua data lokal; aplikasi tidak meminta izin internet.
 *
 * Setiap pengaturan punya nilai bawaan yang aman, dan seluruh isinya bisa
 * diekspor/diimpor lewat [exportText] dan [importText].
 */
class SettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("bitkeyboard", Context.MODE_PRIVATE)

    // ------------------------- Mengetik -------------------------
    var glideEnabled: Boolean
        get() = prefs.getBoolean(KEY_GLIDE, true)
        set(v) = prefs.edit().putBoolean(KEY_GLIDE, v).apply()

    var glideTrail: Boolean
        get() = prefs.getBoolean(KEY_GLIDE_TRAIL, true)
        set(v) = prefs.edit().putBoolean(KEY_GLIDE_TRAIL, v).apply()

    var suggestionsEnabled: Boolean
        get() = prefs.getBoolean(KEY_SUGGESTIONS, true)
        set(v) = prefs.edit().putBoolean(KEY_SUGGESTIONS, v).apply()

    /** Jumlah saran yang ditampilkan di bilah atas (1–6). */
    var suggestionCount: Int
        get() = prefs.getInt(KEY_SUGGESTION_COUNT, 3).coerceIn(1, 6)
        set(v) = prefs.edit().putInt(KEY_SUGGESTION_COUNT, v.coerceIn(1, 6)).apply()

    var autocorrectEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTOCORRECT, true)
        set(v) = prefs.edit().putBoolean(KEY_AUTOCORRECT, v).apply()

    var nextWordPrediction: Boolean
        get() = prefs.getBoolean(KEY_NEXT_WORD, true)
        set(v) = prefs.edit().putBoolean(KEY_NEXT_WORD, v).apply()

    var autoCapitalize: Boolean
        get() = prefs.getBoolean(KEY_AUTO_CAPITALIZE, true)
        set(v) = prefs.edit().putBoolean(KEY_AUTO_CAPITALIZE, v).apply()

    var doubleSpacePeriod: Boolean
        get() = prefs.getBoolean(KEY_DOUBLE_SPACE, true)
        set(v) = prefs.edit().putBoolean(KEY_DOUBLE_SPACE, v).apply()

    var addSpaceAfterSuggestion: Boolean
        get() = prefs.getBoolean(KEY_SPACE_AFTER_SUGGESTION, true)
        set(v) = prefs.edit().putBoolean(KEY_SPACE_AFTER_SUGGESTION, v).apply()

    var deleteWordOnBackspaceAfterGlide: Boolean
        get() = prefs.getBoolean(KEY_UNDO_GLIDE, true)
        set(v) = prefs.edit().putBoolean(KEY_UNDO_GLIDE, v).apply()

    var swipeCursorOnSpace: Boolean
        get() = prefs.getBoolean(KEY_SPACE_CURSOR, true)
        set(v) = prefs.edit().putBoolean(KEY_SPACE_CURSOR, v).apply()

    /** Lama tekan (ms) sebelum popup aksen muncul. */
    var longPressMs: Int
        get() = prefs.getInt(KEY_LONG_PRESS_MS, 400).coerceIn(200, 900)
        set(v) = prefs.edit().putInt(KEY_LONG_PRESS_MS, v.coerceIn(200, 900)).apply()

    // ------------------------- Tata letak -------------------------
    var numberRow: Boolean
        get() = prefs.getBoolean(KEY_NUMBER_ROW, false)
        set(v) = prefs.edit().putBoolean(KEY_NUMBER_ROW, v).apply()

    var functionRow: Boolean
        get() = prefs.getBoolean(KEY_FUNCTION_ROW, false)
        set(v) = prefs.edit().putBoolean(KEY_FUNCTION_ROW, v).apply()

    var cornerSymbols: Boolean
        get() = prefs.getBoolean(KEY_CORNER_SYMBOLS, true)
        set(v) = prefs.edit().putBoolean(KEY_CORNER_SYMBOLS, v).apply()

    var showSuggestionBar: Boolean
        get() = prefs.getBoolean(KEY_SUGGESTION_BAR, true)
        set(v) = prefs.edit().putBoolean(KEY_SUGGESTION_BAR, v).apply()

    var keyHeightDp: Int
        get() = prefs.getInt(KEY_KEY_HEIGHT, 52).coerceIn(36, 76)
        set(v) = prefs.edit().putInt(KEY_KEY_HEIGHT, v.coerceIn(36, 76)).apply()

    var suggestionBarHeightDp: Int
        get() = prefs.getInt(KEY_BAR_HEIGHT, 42).coerceIn(30, 60)
        set(v) = prefs.edit().putInt(KEY_BAR_HEIGHT, v.coerceIn(30, 60)).apply()

    /** Jarak antar tombol dalam dp (0–8). */
    var keyGapDp: Int
        get() = prefs.getInt(KEY_KEY_GAP, 3).coerceIn(0, 8)
        set(v) = prefs.edit().putInt(KEY_KEY_GAP, v.coerceIn(0, 8)).apply()

    /** Sudut lengkung tombol dalam dp (0–16). */
    var keyRadiusDp: Int
        get() = prefs.getInt(KEY_KEY_RADIUS, 7).coerceIn(0, 16)
        set(v) = prefs.edit().putInt(KEY_KEY_RADIUS, v.coerceIn(0, 16)).apply()

    /** Skala ukuran huruf tombol dalam persen (80–130). */
    var keyFontPercent: Int
        get() = prefs.getInt(KEY_KEY_FONT, 100).coerceIn(80, 130)
        set(v) = prefs.edit().putInt(KEY_KEY_FONT, v.coerceIn(80, 130)).apply()

    var keyPreview: Boolean
        get() = prefs.getBoolean(KEY_KEY_PREVIEW, true)
        set(v) = prefs.edit().putBoolean(KEY_KEY_PREVIEW, v).apply()

    // ------------------------- Tampilan -------------------------
    var theme: String
        get() = prefs.getString(KEY_THEME, "system") ?: "system"
        set(v) = prefs.edit().putString(KEY_THEME, v).apply()

    /** Warna aksen `#RRGGBB`, atau kosong berarti mengikuti tema. */
    var accentColor: String
        get() = prefs.getString(KEY_ACCENT, "") ?: ""
        set(v) = prefs.edit().putString(KEY_ACCENT, v).apply()

    // ------------------------- Suara & getaran -------------------------
    var soundEnabled: Boolean
        get() = prefs.getBoolean(KEY_SOUND, true)
        set(v) = prefs.edit().putBoolean(KEY_SOUND, v).apply()

    var vibrateEnabled: Boolean
        get() = prefs.getBoolean(KEY_VIBRATE, true)
        set(v) = prefs.edit().putBoolean(KEY_VIBRATE, v).apply()

    /** Kekuatan getaran 1–3 (1 halus, 3 kuat). */
    var hapticStrength: Int
        get() = prefs.getInt(KEY_HAPTIC_STRENGTH, 2).coerceIn(1, 3)
        set(v) = prefs.edit().putInt(KEY_HAPTIC_STRENGTH, v.coerceIn(1, 3)).apply()

    // ------------------------- Suara -------------------------
    var voiceLanguage: String
        get() = prefs.getString(KEY_VOICE_LANGUAGE, "id-ID") ?: "id-ID"
        set(v) = prefs.edit().putString(KEY_VOICE_LANGUAGE, v).apply()

    var voiceAutoSpace: Boolean
        get() = prefs.getBoolean(KEY_VOICE_AUTOSPACE, true)
        set(v) = prefs.edit().putBoolean(KEY_VOICE_AUTOSPACE, v).apply()

    // ------------------------- Emoji -------------------------
    var emojiTab: Int
        get() = prefs.getInt(KEY_EMOJI_TAB, 0)
        set(v) = prefs.edit().putInt(KEY_EMOJI_TAB, v).apply()

    var emojiRecent: List<String>
        get() = prefs.getString(KEY_EMOJI_RECENT, "")!!
            .split('\u0001')
            .filter { it.isNotBlank() }
        set(v) = prefs.edit().putString(KEY_EMOJI_RECENT, v.take(32).joinToString("\u0001")).apply()

    fun addRecentEmoji(emoji: String) {
        if (emoji.isBlank()) return
        val list = emojiRecent.toMutableList()
        list.remove(emoji)
        list.add(0, emoji)
        emojiRecent = list
    }

    // ------------------------- Kustomisasi tombol -------------------------
    /** Penimpaan karakter sudut / tekan-lama, lihat [KeyCustomization]. */
    var keyOverridesRaw: String
        get() = prefs.getString(KEY_KEY_OVERRIDES, "") ?: ""
        set(v) = prefs.edit().putString(KEY_KEY_OVERRIDES, v).apply()

    fun keyOverrides(): Map<String, KeyOverride> = KeyCustomization.parse(keyOverridesRaw)

    fun setKeyOverride(id: String, override: KeyOverride?) {
        keyOverridesRaw = KeyCustomization.serialize(
            KeyCustomization.withEntry(keyOverrides(), id, override),
        )
    }

    fun resetKeyOverrides() {
        keyOverridesRaw = ""
    }

    // ------------------------- Data pengguna -------------------------
    var userWordsRaw: String
        get() = prefs.getString(KEY_USER_WORDS, "") ?: ""
        set(v) = prefs.edit().putString(KEY_USER_WORDS, v).apply()

    var userBigramsRaw: String
        get() = prefs.getString(KEY_USER_BIGRAMS, "") ?: ""
        set(v) = prefs.edit().putString(KEY_USER_BIGRAMS, v).apply()

    /** Daftar kata di kamus pribadi, urut abjad. */
    fun userWords(): List<String> = userWordsRaw.split('|')
        .mapNotNull { part ->
            val i = part.indexOf(':')
            if (i <= 0) null else part.substring(0, i)
        }
        .filter { it.isNotBlank() }
        .sorted()

    fun addUserWord(word: String) {
        val clean = word.trim().lowercase()
        if (clean.length < 2 || clean.length > 24) return
        if (!clean.all { it.isLetter() || it == '\'' || it == '-' }) return
        if (userWords().any { it == clean }) return
        val entry = "$clean:${(userWordsRaw.split('|').firstOrNull { it.startsWith("$clean:") }?.substringAfter(':')?.toIntOrNull() ?: 0) + 1}"
        val parts = userWordsRaw.split('|').filter { it.isNotBlank() && !it.startsWith("$clean:") }
        userWordsRaw = (parts + entry).joinToString("|")
    }

    fun removeUserWord(word: String) {
        val parts = userWordsRaw.split('|').filter { it.isNotBlank() && !it.startsWith("$word:") }
        userWordsRaw = parts.joinToString("|")
        val bigramParts = userBigramsRaw.split('|')
            .filter { it.isNotBlank() && !it.substringBefore(':').split('\u0001').contains(word) }
        userBigramsRaw = bigramParts.joinToString("|")
    }

    fun clearUserData() {
        prefs.edit()
            .remove(KEY_USER_WORDS)
            .remove(KEY_USER_BIGRAMS)
            .remove(KEY_EMOJI_RECENT)
            .putInt(KEY_USER_VERSION, userDataVersion + 1)
            .apply()
    }

    var userDataVersion: Int
        get() = prefs.getInt(KEY_USER_VERSION, 0)
        private set(v) = prefs.edit().putInt(KEY_USER_VERSION, v).apply()

    // ------------------------- Bantuan -------------------------
    var shownWelcome: Boolean
        get() = prefs.getBoolean(KEY_WELCOME, false)
        set(v) = prefs.edit().putBoolean(KEY_WELCOME, v).apply()

    // ------------------------- Ekspor / impor -------------------------
    /** Semua pengaturan + data pengguna sebagai teks yang bisa disalin. */
    fun exportText(): String {
        val out = StringBuilder()
        out.append("# BitKeyboard backup v1\n")
        for (key in EXPORT_KEYS) {
            val raw = EXPORT_READERS[key]?.invoke(this)
            if (raw != null) out.append(key).append('=').append(raw.replace("\n", "\\n")).append('\n')
        }
        out.append("user_words=").append(userWordsRaw.replace("\n", "\\n")).append('\n')
        out.append("user_bigrams=").append(userBigramsRaw.replace("\n", "\\n")).append('\n')
        out.append("emoji_recent=").append(emojiRecent.joinToString(",")).append('\n')
        return out.toString()
    }

    /**
     * Impor hasil [exportText]. Nilai yang tidak dikenal diabaikan sehingga
     * berkas dari versi lebih baru tetap bisa dipakai.
     *
     * @return jumlah pengaturan yang diterapkan.
     */
    fun importText(text: String): Int {
        var applied = 0
        for (line in text.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
            val eq = trimmed.indexOf('=')
            if (eq <= 0) continue
            val key = trimmed.substring(0, eq).trim()
            val value = trimmed.substring(eq + 1).trim().replace("\\n", "\n")
            val writer = IMPORT_WRITERS[key] ?: continue
            if (writer(this, value)) applied++
        }
        return applied
    }

    /** Kembalikan semua pengaturan ke bawaan; data pengguna tidak dihapus. */
    fun resetSettings() {
        val editor = prefs.edit()
        for (key in EDITOR_KEYS) editor.remove(key)
        editor.apply()
    }

    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    val all: SharedPreferences get() = prefs

    companion object {
        private const val KEY_GLIDE = "glide"
        private const val KEY_GLIDE_TRAIL = "glide_trail"
        private const val KEY_SUGGESTIONS = "suggestions"
        private const val KEY_SUGGESTION_COUNT = "suggestion_count"
        private const val KEY_AUTOCORRECT = "autocorrect"
        private const val KEY_NEXT_WORD = "next_word"
        private const val KEY_SUGGESTION_BAR = "suggestion_bar"
        private const val KEY_BAR_HEIGHT = "suggestion_bar_height"
        private const val KEY_NUMBER_ROW = "number_row"
        private const val KEY_FUNCTION_ROW = "function_row"
        private const val KEY_CORNER_SYMBOLS = "corner_symbols"
        private const val KEY_KEY_HEIGHT = "key_height"
        private const val KEY_KEY_GAP = "key_gap"
        private const val KEY_KEY_RADIUS = "key_radius"
        private const val KEY_KEY_FONT = "key_font"
        private const val KEY_KEY_PREVIEW = "key_preview"
        private const val KEY_SOUND = "sound"
        private const val KEY_VIBRATE = "vibrate"
        private const val KEY_HAPTIC_STRENGTH = "haptic_strength"
        private const val KEY_AUTO_CAPITALIZE = "auto_capitalize"
        private const val KEY_DOUBLE_SPACE = "double_space"
        private const val KEY_SPACE_AFTER_SUGGESTION = "space_after_suggestion"
        private const val KEY_SPACE_CURSOR = "space_cursor"
        private const val KEY_UNDO_GLIDE = "undo_glide"
        private const val KEY_LONG_PRESS_MS = "long_press_ms"
        private const val KEY_THEME = "theme"
        private const val KEY_ACCENT = "accent_color"
        private const val KEY_VOICE_LANGUAGE = "voice_language"
        private const val KEY_VOICE_AUTOSPACE = "voice_auto_space"
        private const val KEY_EMOJI_TAB = "emoji_tab"
        private const val KEY_EMOJI_RECENT = "emoji_recent"
        private const val KEY_KEY_OVERRIDES = "key_overrides"
        private const val KEY_USER_WORDS = "user_words"
        private const val KEY_USER_BIGRAMS = "user_bigrams"
        private const val KEY_WELCOME = "welcome"
        private const val KEY_USER_VERSION = "user_data_version"

        /** Semua kunci pengaturan (tanpa data pengguna). */
        private val EDITOR_KEYS = listOf(
            KEY_GLIDE, KEY_GLIDE_TRAIL, KEY_SUGGESTIONS, KEY_SUGGESTION_COUNT,
            KEY_AUTOCORRECT, KEY_NEXT_WORD, KEY_SUGGESTION_BAR, KEY_BAR_HEIGHT,
            KEY_NUMBER_ROW, KEY_FUNCTION_ROW, KEY_CORNER_SYMBOLS, KEY_KEY_HEIGHT,
            KEY_KEY_GAP, KEY_KEY_RADIUS, KEY_KEY_FONT, KEY_KEY_PREVIEW, KEY_SOUND,
            KEY_VIBRATE, KEY_HAPTIC_STRENGTH, KEY_AUTO_CAPITALIZE, KEY_DOUBLE_SPACE,
            KEY_SPACE_AFTER_SUGGESTION, KEY_SPACE_CURSOR, KEY_UNDO_GLIDE,
            KEY_LONG_PRESS_MS, KEY_THEME, KEY_ACCENT, KEY_VOICE_LANGUAGE,
            KEY_VOICE_AUTOSPACE, KEY_EMOJI_TAB, KEY_KEY_OVERRIDES, KEY_WELCOME,
        )

        private val EXPORT_KEYS = EDITOR_KEYS.filter { it != KEY_WELCOME }

        private val EXPORT_READERS: Map<String, (SettingsStore) -> String> = mapOf(
            KEY_GLIDE to { s -> s.glideEnabled.toString() },
            KEY_GLIDE_TRAIL to { s -> s.glideTrail.toString() },
            KEY_SUGGESTIONS to { s -> s.suggestionsEnabled.toString() },
            KEY_SUGGESTION_COUNT to { s -> s.suggestionCount.toString() },
            KEY_AUTOCORRECT to { s -> s.autocorrectEnabled.toString() },
            KEY_NEXT_WORD to { s -> s.nextWordPrediction.toString() },
            KEY_SUGGESTION_BAR to { s -> s.showSuggestionBar.toString() },
            KEY_BAR_HEIGHT to { s -> s.suggestionBarHeightDp.toString() },
            KEY_NUMBER_ROW to { s -> s.numberRow.toString() },
            KEY_FUNCTION_ROW to { s -> s.functionRow.toString() },
            KEY_CORNER_SYMBOLS to { s -> s.cornerSymbols.toString() },
            KEY_KEY_HEIGHT to { s -> s.keyHeightDp.toString() },
            KEY_KEY_GAP to { s -> s.keyGapDp.toString() },
            KEY_KEY_RADIUS to { s -> s.keyRadiusDp.toString() },
            KEY_KEY_FONT to { s -> s.keyFontPercent.toString() },
            KEY_KEY_PREVIEW to { s -> s.keyPreview.toString() },
            KEY_SOUND to { s -> s.soundEnabled.toString() },
            KEY_VIBRATE to { s -> s.vibrateEnabled.toString() },
            KEY_HAPTIC_STRENGTH to { s -> s.hapticStrength.toString() },
            KEY_AUTO_CAPITALIZE to { s -> s.autoCapitalize.toString() },
            KEY_DOUBLE_SPACE to { s -> s.doubleSpacePeriod.toString() },
            KEY_SPACE_AFTER_SUGGESTION to { s -> s.addSpaceAfterSuggestion.toString() },
            KEY_SPACE_CURSOR to { s -> s.swipeCursorOnSpace.toString() },
            KEY_UNDO_GLIDE to { s -> s.deleteWordOnBackspaceAfterGlide.toString() },
            KEY_LONG_PRESS_MS to { s -> s.longPressMs.toString() },
            KEY_THEME to { s -> s.theme },
            KEY_ACCENT to { s -> s.accentColor },
            KEY_VOICE_LANGUAGE to { s -> s.voiceLanguage },
            KEY_VOICE_AUTOSPACE to { s -> s.voiceAutoSpace.toString() },
            KEY_EMOJI_TAB to { s -> s.emojiTab.toString() },
            KEY_KEY_OVERRIDES to { s -> s.keyOverridesRaw },
        )

        private val IMPORT_WRITERS: Map<String, (SettingsStore, String) -> Boolean> = buildMap {
            fun bool(key: String, set: (SettingsStore, Boolean) -> Unit) = put(key) { s, v ->
                val parsed = v.toBooleanStrictOrNull() ?: return@put false
                set(s, parsed); true
            }
            fun int(key: String, min: Int, max: Int, set: (SettingsStore, Int) -> Unit) = put(key) { s, v ->
                val parsed = v.toIntOrNull() ?: return@put false
                if (parsed < min || parsed > max) return@put false
                set(s, parsed); true
            }
            bool(KEY_GLIDE) { s, v -> s.glideEnabled = v }
            bool(KEY_GLIDE_TRAIL) { s, v -> s.glideTrail = v }
            bool(KEY_SUGGESTIONS) { s, v -> s.suggestionsEnabled = v }
            int(KEY_SUGGESTION_COUNT, 1, 6) { s, v -> s.suggestionCount = v }
            bool(KEY_AUTOCORRECT) { s, v -> s.autocorrectEnabled = v }
            bool(KEY_NEXT_WORD) { s, v -> s.nextWordPrediction = v }
            bool(KEY_SUGGESTION_BAR) { s, v -> s.showSuggestionBar = v }
            int(KEY_BAR_HEIGHT, 30, 60) { s, v -> s.suggestionBarHeightDp = v }
            bool(KEY_NUMBER_ROW) { s, v -> s.numberRow = v }
            bool(KEY_FUNCTION_ROW) { s, v -> s.functionRow = v }
            bool(KEY_CORNER_SYMBOLS) { s, v -> s.cornerSymbols = v }
            int(KEY_KEY_HEIGHT, 36, 76) { s, v -> s.keyHeightDp = v }
            int(KEY_KEY_GAP, 0, 8) { s, v -> s.keyGapDp = v }
            int(KEY_KEY_RADIUS, 0, 16) { s, v -> s.keyRadiusDp = v }
            int(KEY_KEY_FONT, 80, 130) { s, v -> s.keyFontPercent = v }
            bool(KEY_KEY_PREVIEW) { s, v -> s.keyPreview = v }
            bool(KEY_SOUND) { s, v -> s.soundEnabled = v }
            bool(KEY_VIBRATE) { s, v -> s.vibrateEnabled = v }
            int(KEY_HAPTIC_STRENGTH, 1, 3) { s, v -> s.hapticStrength = v }
            bool(KEY_AUTO_CAPITALIZE) { s, v -> s.autoCapitalize = v }
            bool(KEY_DOUBLE_SPACE) { s, v -> s.doubleSpacePeriod = v }
            bool(KEY_SPACE_AFTER_SUGGESTION) { s, v -> s.addSpaceAfterSuggestion = v }
            bool(KEY_SPACE_CURSOR) { s, v -> s.swipeCursorOnSpace = v }
            bool(KEY_UNDO_GLIDE) { s, v -> s.deleteWordOnBackspaceAfterGlide = v }
            int(KEY_LONG_PRESS_MS, 200, 900) { s, v -> s.longPressMs = v }
            put(KEY_THEME) { s, v ->
                if (v !in setOf("system", "light", "dark", "amoled")) return@put false
                s.theme = v; true
            }
            put(KEY_ACCENT) { s, v ->
                if (v.isNotEmpty() && !Regex("^#[0-9A-Fa-f]{6}$").matches(v)) return@put false
                s.accentColor = v; true
            }
            put(KEY_VOICE_LANGUAGE) { s, v -> s.voiceLanguage = v.take(16); true }
            bool(KEY_VOICE_AUTOSPACE) { s, v -> s.voiceAutoSpace = v }
            int(KEY_EMOJI_TAB, 0, 8) { s, v -> s.emojiTab = v }
            put(KEY_KEY_OVERRIDES) { s, v ->
                if (v.length > 4000) return@put false
                s.keyOverridesRaw = v; true
            }
            put("user_words") { s, v -> s.userWordsRaw = v.take(200_000); true }
            put("user_bigrams") { s, v -> s.userBigramsRaw = v.take(200_000); true }
            put("emoji_recent") { s, v ->
                s.emojiRecent = v.split(',').map { it.trim() }.filter { it.isNotEmpty() }.take(32)
                true
            }
        }
    }
}

/** [UserModel] yang menyimpan pembelajaran ke [SettingsStore]. */
class PrefsUserModel(private val settings: SettingsStore) : UserModel {

    private data class State(val words: LinkedHashMap<String, Int>, val bigrams: LinkedHashMap<String, Int>)

    private val state: State by lazy { load() }
    private var loadedVersion: Int = settings.userDataVersion

    private fun load(): State {
        val words = LinkedHashMap<String, Int>()
        for (part in settings.userWordsRaw.split('|')) {
            if (part.isBlank()) continue
            val i = part.indexOf(':')
            if (i <= 0) continue
            val w = part.substring(0, i)
            val n = part.substring(i + 1).toIntOrNull() ?: continue
            if (w.isNotBlank()) words[w] = n
        }
        val bigrams = LinkedHashMap<String, Int>()
        for (part in settings.userBigramsRaw.split('|')) {
            if (part.isBlank()) continue
            val i = part.indexOf(':')
            if (i <= 0) continue
            val key = part.substring(0, i)
            val n = part.substring(i + 1).toIntOrNull() ?: continue
            if (key.contains('\u0001')) bigrams[key] = n
        }
        return State(words, bigrams)
    }

    private fun save() {
        settings.userWordsRaw = state.words.entries.joinToString("|") { "${it.key}:${it.value}" }
        settings.userBigramsRaw = state.bigrams.entries.joinToString("|") { "${it.key}:${it.value}" }
    }

    override fun frequency(word: String): Int = state.words[word.lowercase()] ?: 0

    override fun isLearned(word: String): Boolean = frequency(word) > 0

    private fun syncIfCleared() {
        if (loadedVersion != settings.userDataVersion) {
            state.words.clear()
            state.bigrams.clear()
            loadedVersion = settings.userDataVersion
        }
    }

    override fun learn(word: String) {
        syncIfCleared()
        val key = word.lowercase()
        if (key.length < 2 || key.length > 24) return
        state.words[key] = (state.words[key] ?: 0) + 1
        trim(state.words, 800)
        save()
    }

    override fun forgetAll() {
        state.words.clear()
        state.bigrams.clear()
        settings.clearUserData()
        loadedVersion = settings.userDataVersion
    }

    override fun bigramCount(previous: String, next: String): Int =
        state.bigrams[bigramKey(previous, next)] ?: 0

    override fun observeBigram(previous: String, next: String) {
        syncIfCleared()
        val p = previous.lowercase()
        val n = next.lowercase()
        if (p.isEmpty() || n.isEmpty()) return
        val key = bigramKey(p, n)
        state.bigrams[key] = (state.bigrams[key] ?: 0) + 1
        trim(state.bigrams, 800)
        save()
    }

    override fun topNextWords(previous: String, limit: Int): List<Pair<String, Int>> {
        val p = previous.lowercase()
        return state.bigrams.entries
            .asSequence()
            .filter { it.key.startsWith("$p\u0001") }
            .map { it.key.substringAfter('\u0001') to it.value }
            .sortedByDescending { it.second }
            .take(limit)
            .toList()
    }

    override fun wordsWithPrefix(prefix: String, limit: Int): List<Pair<String, Int>> {
        val p = prefix.lowercase()
        if (p.isEmpty()) return emptyList()
        return state.words.entries
            .asSequence()
            .filter { it.key.startsWith(p) }
            .sortedByDescending { it.value }
            .take(limit)
            .map { it.key to it.value }
            .toList()
    }

    fun learnedCount(): Int = state.words.size

    private fun bigramKey(a: String, b: String) = "$a\u0001$b"

    private fun trim(map: LinkedHashMap<String, Int>, max: Int) {
        if (map.size <= max) return
        val keep = map.entries.sortedByDescending { it.value }.take(max)
        map.clear()
        for ((k, v) in keep) map[k] = v
    }
}
