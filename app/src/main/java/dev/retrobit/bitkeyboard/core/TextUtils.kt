package dev.retrobit.bitkeyboard.core

object TextUtils {

    fun isWordChar(c: Char): Boolean =
        c.isLetter() || c == '\'' || c == '\u2019' || c == '-'

    fun isSentenceEnd(c: Char): Boolean = c == '.' || c == '!' || c == '?' || c == '\n'

    /** Menyesuaikan kapitalisasi kata pengganti dengan kata aslinya. */
    fun applyCase(original: String, replacement: String): String {
        if (original.isEmpty() || replacement.isEmpty()) return replacement
        if (original.length > 1 && original.uppercase() == original && original.lowercase() != original) {
            return replacement.uppercase()
        }
        val first = original[0]
        return if (first.isUpperCase()) {
            replacement[0].uppercaseChar() + replacement.substring(1)
        } else {
            replacement[0].lowercaseChar() + replacement.substring(1)
        }
    }

    /** Kata terakhir sebelum kursor, hanya huruf/aksen. */
    fun lastWordBefore(text: CharSequence): String {
        var i = text.length
        while (i > 0 && !isWordChar(text[i - 1])) i--
        val end = i
        while (i > 0 && isWordChar(text[i - 1])) i--
        return text.subSequence(i, end).toString()
    }

    /** Apakah teks diakhiri tanda baca akhir kalimat (untuk auto-shift). */
    fun endsSentence(text: CharSequence): Boolean {
        for (i in text.length - 1 downTo 0) {
            val c = text[i]
            if (c == ' ') continue
            return isSentenceEnd(c)
        }
        return true
    }

    fun isAllCaps(text: String): Boolean =
        text.length > 1 && text.uppercase() == text && text.lowercase() != text

    fun countWords(text: String): Int {
        var count = 0
        var inWord = false
        for (c in text) {
            if (isWordChar(c)) {
                if (!inWord) count++
                inWord = true
            } else {
                inWord = false
            }
        }
        return count
    }
}
