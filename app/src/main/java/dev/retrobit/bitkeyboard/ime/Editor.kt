package dev.retrobit.bitkeyboard.ime

import android.os.SystemClock
import android.view.KeyEvent as AndroidKeyEvent
import android.view.inputmethod.InputConnection
import dev.retrobit.bitkeyboard.core.BigramModel
import dev.retrobit.bitkeyboard.core.GlideDecoder
import dev.retrobit.bitkeyboard.core.GlidePoint
import dev.retrobit.bitkeyboard.core.Suggester
import dev.retrobit.bitkeyboard.core.TextUtils
import dev.retrobit.bitkeyboard.core.UserModel
import dev.retrobit.bitkeyboard.settings.SettingsStore

/**
 * Menjembatani keyboard dengan [InputConnection]: menulis karakter, mengelola
 * autocorrect, saran kata, undo, dan navigasi kursor.
 *
 * BitKeyboard sengaja tidak memakai composing text agar kompatibel dengan
 * banyak aplikasi; kata yang sedang diketik dibaca kembali dari teks sebelum
 * kursor.
 */
class Editor(
    private val service: BitKeyboardService,
    private val settings: SettingsStore,
) {
    var lastEditUptime: Long = 0L
        private set

    interface Listener {
        fun onSuggestionsChanged(suggestions: List<Suggester.Suggestion>, composing: String)
        fun onShiftChanged(shift: Boolean)
        fun onToast(message: String)
    }

    var listener: Listener? = null
    var userModel: UserModel = UserModel.EMPTY
        private set
    private var suggester: Suggester? = null
    private var glideDecoder: GlideDecoder? = null

    private data class PendingUndo(val typed: String, val corrected: String, val time: Long)
    private data class LastGlide(val text: String, val time: Long)

    private var pendingUndo: PendingUndo? = null
    private var lastGlide: LastGlide? = null
    private var lastActionWasSpace = false

    private val conn: InputConnection? get() = service.currentInputConnection

    fun setSuggester(value: Suggester?) {
        suggester = value
        updateSuggestions()
    }

    fun setGlideDecoder(value: GlideDecoder?) {
        glideDecoder = value
    }

    fun isActiveInput(): Boolean = conn != null

    fun decodeGlide(points: List<GlidePoint>, limit: Int): List<GlideDecoder.Result> =
        glideDecoder?.decode(points, limit) ?: emptyList()

    fun setUserModel(value: UserModel) {
        userModel = value
    }

    fun reset() {
        pendingUndo = null
        lastGlide = null
        lastActionWasSpace = false
        updateSuggestions()
        listener?.onShiftChanged(shouldAutoShift())
    }

    fun onSelectionUpdated() {
        // Abaikan callback yang dipicu oleh edit kita sendiri.
        if (SystemClock.uptimeMillis() - lastEditUptime < 400) return
        pendingUndo = null
        lastGlide = null
        lastActionWasSpace = false
        updateSuggestions()
        listener?.onShiftChanged(shouldAutoShift())
    }

    // ------------------------------------------------------------------ //
    // Membaca teks
    // ------------------------------------------------------------------ //
    fun textBefore(max: Int = 128): String =
        conn?.getTextBeforeCursor(max, 0)?.toString() ?: ""

    fun currentWord(): String = TextUtils.lastWordBefore(textBefore(64))

    fun previousWord(): String {
        val before = textBefore(128)
        val current = TextUtils.lastWordBefore(before)
        if (current.isEmpty()) return ""
        val rest = before.dropLast(current.length).trimEnd()
        return TextUtils.lastWordBefore(rest)
    }

    fun shouldAutoShift(): Boolean {
        if (!settings.autoCapitalize) return false
        val before = textBefore(64)
        return before.isEmpty() || TextUtils.endsSentence(before)
    }

    fun hasTextBefore(): Boolean = textBefore(1).isNotEmpty()

    // ------------------------------------------------------------------ //
    // Menulis
    // ------------------------------------------------------------------ //
    fun commitChar(c: Char) {
        val ic = conn ?: return
        if (TextUtils.isWordChar(c)) {
            ic.commitText(c.toString(), 1)
            pendingUndo = null
            lastGlide = null
            lastActionWasSpace = false
        } else {
            finalizeWord(ic)
            ic.commitText(c.toString(), 1)
            pendingUndo = null
            lastGlide = null
            lastActionWasSpace = false
        }
        afterEdit()
    }

    fun commitSpace() {
        val ic = conn ?: return
        val word = currentWord()
        if (word.isNotEmpty()) {
            finalizeWord(ic)
            lastActionWasSpace = false
        }
        val before = textBefore(2)
        if (settings.doubleSpacePeriod && lastActionWasSpace && before == " ") {
            ic.deleteSurroundingText(1, 0)
            ic.commitText(". ", 1)
            lastActionWasSpace = false
        } else {
            ic.commitText(" ", 1)
            lastActionWasSpace = true
        }
        lastGlide = null
        afterEdit()
    }

    fun commitEnter() {
        val ic = conn ?: return
        if (currentWord().isNotEmpty()) finalizeWord(ic)
        val handled = try {
            service.sendDefaultEditorAction(true)
        } catch (e: Exception) {
            false
        }
        if (!handled) ic.commitText("\n", 1)
        pendingUndo = null
        lastGlide = null
        lastActionWasSpace = false
        afterEdit()
    }

    fun commitPlainText(text: String) {
        val ic = conn ?: return
        if (text.isEmpty()) return
        if (currentWord().isNotEmpty()) finalizeWord(ic)
        ic.commitText(text, 1)
        pendingUndo = null
        lastGlide = null
        lastActionWasSpace = text == " "
        afterEdit()
    }

    /** Kata hasil glide: langsung dikirim beserta spasi, bisa dihapus sekaligus. */
    fun commitGlideWord(word: String, autoCapitalize: Boolean) {
        val ic = conn ?: return
        if (currentWord().isNotEmpty()) finalizeWord(ic)
        val previous = previousWord()
        val text = if (autoCapitalize) TextUtils.applyCase("A", word) else word
        val before = textBefore(1)
        val needsLeadingSpace = before.isNotEmpty() && !before.endsWith(" ") && !before.endsWith("\n")
        val full = (if (needsLeadingSpace) " " else "") + text + " "
        ic.commitText(full, 1)
        userModel.observeBigram(previous, text)
        lastGlide = LastGlide(full, SystemClock.uptimeMillis())
        pendingUndo = null
        lastActionWasSpace = false
        afterEdit()
    }

    fun commitSuggestion(word: String) {
        val ic = conn ?: return
        val current = currentWord()
        if (current.isNotEmpty()) {
            ic.deleteSurroundingText(current.length, 0)
        }
        ic.commitText(word, 1)
        if (settings.addSpaceAfterSuggestion) ic.commitText(" ", 1)
        userModel.learn(word)
        lastGlide = null
        pendingUndo = null
        lastActionWasSpace = settings.addSpaceAfterSuggestion
        afterEdit()
    }

    private fun finalizeWord(ic: InputConnection) {
        val current = currentWord()
        if (current.isEmpty()) return
        val previous = previousWord()
        var finalWord = current
        if (settings.autocorrectEnabled && current.length >= 3) {
            val candidate = suggester?.autocorrect(current)
            if (candidate != null && candidate != current) {
                ic.deleteSurroundingText(current.length, 0)
                ic.commitText(candidate, 1)
                pendingUndo = PendingUndo(current, candidate, SystemClock.uptimeMillis())
                finalWord = candidate
            }
        }
        userModel.observeBigram(previous, finalWord)
        lastGlide = null
    }

    // ------------------------------------------------------------------ //
    // Menghapus & kursor
    // ------------------------------------------------------------------ //
    fun deleteBackward() {
        val ic = conn ?: return
        if (ic.getSelectedText(0)?.isNotEmpty() == true) {
            ic.commitText("", 1)
            afterEdit()
            return
        }
        val now = SystemClock.uptimeMillis()
        val undo = pendingUndo
        if (undo != null && now - undo.time < 6_000) {
            val before = textBefore(undo.corrected.length + 1)
            if (before == undo.corrected || before == undo.corrected + " ") {
                ic.deleteSurroundingText(before.length, 0)
                ic.commitText(undo.typed, 1)
                pendingUndo = null
                lastActionWasSpace = false
                afterEdit()
                return
            }
        }
        val glide = lastGlide
        if (glide != null && settings.deleteWordOnBackspaceAfterGlide && now - glide.time < 8_000) {
            val before = textBefore(glide.text.length)
            if (before.endsWith(glide.text)) {
                ic.deleteSurroundingText(glide.text.length, 0)
                lastGlide = null
                pendingUndo = null
                lastActionWasSpace = false
                afterEdit()
                return
            }
        }
        idDeleteOne(ic)
        pendingUndo = null
        lastGlide = null
        lastActionWasSpace = false
        afterEdit()
    }

    private fun idDeleteOne(ic: InputConnection) {
        val before = textBefore(1)
        if (before.isEmpty()) return
        ic.deleteSurroundingText(1, 0)
    }

    fun deleteWord() {
        val ic = conn ?: return
        val before = textBefore(64)
        if (before.isEmpty()) return
        var i = before.length
        while (i > 0 && before[i - 1] == ' ') i--
        while (i > 0 && TextUtils.isWordChar(before[i - 1])) i--
        val count = before.length - i
        if (count > 0) ic.deleteSurroundingText(count, 0)
        pendingUndo = null
        lastGlide = null
        afterEdit()
    }

    fun moveCursor(direction: Int, byWord: Boolean = false) {
        val ic = conn ?: return
        val code = if (direction < 0) AndroidKeyEvent.KEYCODE_DPAD_LEFT else AndroidKeyEvent.KEYCODE_DPAD_RIGHT
        val meta = if (byWord) AndroidKeyEvent.META_CTRL_ON else 0
        sendKeyCode(ic, code, meta)
    }

    // ------------------------------------------------------------------ //
    // Tombol khusus
    // ------------------------------------------------------------------ //
    fun sendKeyEvent(androidKeyCode: Int, metaState: Int = 0) {
        val ic = conn ?: return
        sendKeyCode(ic, androidKeyCode, metaState)
        pendingUndo = null
        lastGlide = null
        afterEdit()
    }

    fun sendCtrlLetter(c: Char, ctrl: Boolean, alt: Boolean) {
        val ic = conn ?: return
        if (c !in 'a'..'z') return
        var meta = 0
        if (ctrl) meta = meta or AndroidKeyEvent.META_CTRL_ON
        if (alt) meta = meta or AndroidKeyEvent.META_ALT_ON
        val code = AndroidKeyEvent.KEYCODE_A + (c - 'a')
        sendKeyCode(ic, code, meta)
        pendingUndo = null
        lastGlide = null
        afterEdit()
    }

    private fun sendKeyCode(ic: InputConnection, keyCode: Int, meta: Int) {
        val now = SystemClock.uptimeMillis()
        val down = AndroidKeyEvent(now, now, AndroidKeyEvent.ACTION_DOWN, keyCode, 0, meta)
        val up = AndroidKeyEvent(now, now, AndroidKeyEvent.ACTION_UP, keyCode, 0, meta)
        ic.sendKeyEvent(down)
        ic.sendKeyEvent(up)
    }

    fun performAction(action: Action) {
        val ic = conn ?: return
        when (action) {
            Action.COPY -> ic.performContextMenuAction(android.R.id.copy)
            Action.CUT -> ic.performContextMenuAction(android.R.id.cut)
            Action.PASTE -> ic.performContextMenuAction(android.R.id.paste)
            Action.SELECT_ALL -> ic.performContextMenuAction(android.R.id.selectAll)
            Action.UNDO -> sendCtrlLetter('z', ctrl = true, alt = false)
            Action.REDO -> sendCtrlLetter('y', ctrl = true, alt = false)
        }
        afterEdit()
    }

    enum class Action { COPY, CUT, PASTE, SELECT_ALL, UNDO, REDO }

    fun learnWord(word: String) {
        if (word.isBlank()) return
        userModel.learn(word)
        listener?.onToast("“$word” ditambahkan ke kamus")
        updateSuggestions()
    }

    // ------------------------------------------------------------------ //
    // Saran
    // ------------------------------------------------------------------ //
    fun updateSuggestions() {
        val l = listener ?: return
        val s = suggester
        if (!settings.suggestionsEnabled || s == null) {
            l.onSuggestionsChanged(emptyList(), "")
            return
        }
        val before = textBefore(96)
        val current = TextUtils.lastWordBefore(before)
        val rest = before.dropLast(current.length).trimEnd()
        val previous = TextUtils.lastWordBefore(rest)
        val limit = settings.suggestionCount
        val list = if (current.isEmpty()) {
            if (settings.nextWordPrediction) s.nextWords(previous, limit) else emptyList()
        } else {
            s.onWord(current, previous, limit)
        }
        l.onSuggestionsChanged(list, current)
    }

    private fun afterEdit() {
        lastEditUptime = SystemClock.uptimeMillis()
        listener?.onShiftChanged(shouldAutoShift())
        updateSuggestions()
    }

    fun dictionaryReady(): Boolean = suggester != null
}
