package dev.retrobit.bitkeyboard.settings

import android.content.Context
import android.content.SharedPreferences
import dev.retrobit.bitkeyboard.core.UserModel

/**
 * Penyimpanan preferensi. Semua data lokal; aplikasi tidak meminta izin internet.
 */
class SettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("bitkeyboard", Context.MODE_PRIVATE)

    // ------------------------- Tata letak -------------------------
    var glideEnabled: Boolean
        get() = prefs.getBoolean(KEY_GLIDE, true)
        set(v) = prefs.edit().putBoolean(KEY_GLIDE, v).apply()

    var suggestionsEnabled: Boolean
        get() = prefs.getBoolean(KEY_SUGGESTIONS, true)
        set(v) = prefs.edit().putBoolean(KEY_SUGGESTIONS, v).apply()

    var autocorrectEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTOCORRECT, true)
        set(v) = prefs.edit().putBoolean(KEY_AUTOCORRECT, v).apply()

    var nextWordPrediction: Boolean
        get() = prefs.getBoolean(KEY_NEXT_WORD, true)
        set(v) = prefs.edit().putBoolean(KEY_NEXT_WORD, v).apply()

    var showSuggestionBar: Boolean
        get() = prefs.getBoolean(KEY_SUGGESTION_BAR, true)
        set(v) = prefs.edit().putBoolean(KEY_SUGGESTION_BAR, v).apply()

    var numberRow: Boolean
        get() = prefs.getBoolean(KEY_NUMBER_ROW, false)
        set(v) = prefs.edit().putBoolean(KEY_NUMBER_ROW, v).apply()

    var functionRow: Boolean
        get() = prefs.getBoolean(KEY_FUNCTION_ROW, false)
        set(v) = prefs.edit().putBoolean(KEY_FUNCTION_ROW, v).apply()

    var cornerSymbols: Boolean
        get() = prefs.getBoolean(KEY_CORNER_SYMBOLS, true)
        set(v) = prefs.edit().putBoolean(KEY_CORNER_SYMBOLS, v).apply()

    var keyHeightDp: Int
        get() = prefs.getInt(KEY_KEY_HEIGHT, 52)
        set(v) = prefs.edit().putInt(KEY_KEY_HEIGHT, v.coerceIn(40, 68)).apply()

    // ------------------------- Perilaku -------------------------
    var soundEnabled: Boolean
        get() = prefs.getBoolean(KEY_SOUND, true)
        set(v) = prefs.edit().putBoolean(KEY_SOUND, v).apply()

    var vibrateEnabled: Boolean
        get() = prefs.getBoolean(KEY_VIBRATE, true)
        set(v) = prefs.edit().putBoolean(KEY_VIBRATE, v).apply()

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

    // ------------------------- Tampilan -------------------------
    var theme: String
        get() = prefs.getString(KEY_THEME, "system") ?: "system"
        set(v) = prefs.edit().putString(KEY_THEME, v).apply()

    // ------------------------- Suara -------------------------
    var voiceLanguage: String
        get() = prefs.getString(KEY_VOICE_LANGUAGE, "id-ID") ?: "id-ID"
        set(v) = prefs.edit().putString(KEY_VOICE_LANGUAGE, v).apply()

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

    // ------------------------- Data pengguna -------------------------
    var userWordsRaw: String
        get() = prefs.getString(KEY_USER_WORDS, "") ?: ""
        set(v) = prefs.edit().putString(KEY_USER_WORDS, v).apply()

    var userBigramsRaw: String
        get() = prefs.getString(KEY_USER_BIGRAMS, "") ?: ""
        set(v) = prefs.edit().putString(KEY_USER_BIGRAMS, v).apply()

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

    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    val all: SharedPreferences get() = prefs

    companion object {
        private const val KEY_GLIDE = "glide"
        private const val KEY_SUGGESTIONS = "suggestions"
        private const val KEY_AUTOCORRECT = "autocorrect"
        private const val KEY_NEXT_WORD = "next_word"
        private const val KEY_SUGGESTION_BAR = "suggestion_bar"
        private const val KEY_NUMBER_ROW = "number_row"
        private const val KEY_FUNCTION_ROW = "function_row"
        private const val KEY_CORNER_SYMBOLS = "corner_symbols"
        private const val KEY_KEY_HEIGHT = "key_height"
        private const val KEY_SOUND = "sound"
        private const val KEY_VIBRATE = "vibrate"
        private const val KEY_AUTO_CAPITALIZE = "auto_capitalize"
        private const val KEY_DOUBLE_SPACE = "double_space"
        private const val KEY_SPACE_AFTER_SUGGESTION = "space_after_suggestion"
        private const val KEY_UNDO_GLIDE = "undo_glide"
        private const val KEY_THEME = "theme"
        private const val KEY_VOICE_LANGUAGE = "voice_language"
        private const val KEY_EMOJI_TAB = "emoji_tab"
        private const val KEY_EMOJI_RECENT = "emoji_recent"
        private const val KEY_USER_WORDS = "user_words"
        private const val KEY_USER_BIGRAMS = "user_bigrams"
        private const val KEY_WELCOME = "welcome"
        private const val KEY_USER_VERSION = "user_data_version"
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
