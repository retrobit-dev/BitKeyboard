@file:OptIn(kotlin.js.ExperimentalJsExport::class)

package demo

import dev.retrobit.bitkeyboard.core.BigramModel
import dev.retrobit.bitkeyboard.core.CornerDirection
import dev.retrobit.bitkeyboard.core.Dictionary
import dev.retrobit.bitkeyboard.core.EmojiData
import dev.retrobit.bitkeyboard.core.GlideDecoder
import dev.retrobit.bitkeyboard.core.GlidePoint
import dev.retrobit.bitkeyboard.core.InMemoryUserModel
import dev.retrobit.bitkeyboard.core.KeySpec
import dev.retrobit.bitkeyboard.core.KeyValue
import dev.retrobit.bitkeyboard.core.KeyboardLayout
import dev.retrobit.bitkeyboard.core.Layouts
import dev.retrobit.bitkeyboard.core.Suggester
import kotlin.js.JsExport
import kotlin.math.roundToInt

/**
 * Jembatan Kotlin/JS untuk demo web. Demo memakai kelas inti yang sama dengan
 * aplikasi Android: kamus, trie, suggester, dan decoder glide.
 */

private var dictionary: Dictionary = Dictionary.empty()
private var user: InMemoryUserModel = InMemoryUserModel()
private var suggester: Suggester = Suggester(dictionary, BigramModel.EMPTY, user)
private var decoder: GlideDecoder = GlideDecoder(dictionary)

@JsExport
fun loadDictionary(text: String): Int {
    dictionary = Dictionary.fromText(text)
    user = InMemoryUserModel()
    suggester = Suggester(dictionary, BigramModel.EMPTY, user)
    decoder = GlideDecoder(dictionary)
    return dictionary.size
}

@JsExport
fun dictionarySize(): Int = dictionary.size

@JsExport
fun learnWord(word: String) {
    user.learn(word)
}

@JsExport
fun isKnown(word: String): Boolean = suggester.isKnown(word)

@JsExport
fun frequency(word: String): Int = dictionary.frequency(word.lowercase())

/** Format: kata:jenis:jarak;... */
@JsExport
fun suggest(prefix: String, previous: String, limit: Int): String =
    suggester.onWord(prefix, previous, limit).joinToString(";") {
        "${it.word}:${it.kind.name}:${it.distance}"
    }

@JsExport
fun nextWords(previous: String, limit: Int): String =
    suggester.nextWords(previous, limit).joinToString(";") { "${it.word}:${it.frequency}" }

@JsExport
fun autocorrect(word: String): String = suggester.autocorrect(word) ?: ""

/** Format: kata:nilai;... Nilai dibulatkan 4 desimal. */
@JsExport
fun decodeGlide(pointsCsv: String, limit: Int): String {
    val points = ArrayList<GlidePoint>(64)
    for (token in pointsCsv.split(';')) {
        if (token.isBlank()) continue
        val comma = token.indexOf(',')
        if (comma <= 0) continue
        val x = token.substring(0, comma).toDoubleOrNull() ?: continue
        val y = token.substring(comma + 1).toDoubleOrNull() ?: continue
        points.add(GlidePoint(x.toFloat(), y.toFloat()))
    }
    return decoder.decode(points, limit).joinToString(";") {
        "${it.word}:${(it.score * 10000).roundToInt() / 10000.0}"
    }
}

/** Data layout untuk dirender oleh JS. */
@JsExport
fun layoutJson(layerId: String, emojiTab: Int, recentCsv: String): String {
    val layout: KeyboardLayout = when (layerId) {
        "symbols" -> Layouts.symbols()
        "symbols_alt" -> Layouts.symbolsAlt()
        "scientific" -> Layouts.scientific()
        "scientific_alt" -> Layouts.scientificAlt()
        "emoji" -> EmojiData.layout(
            emojiTab,
            recentCsv.split(',').map { it.trim() }.filter { it.isNotEmpty() },
        )
        else -> Layouts.letters(false, false)
    }
    return layoutToJson(layout)
}

@JsExport
fun version(): String = "1.0"

@JsExport
fun qwertyLetters(): String = "qwertyuiop|asdfghjkl|zxcvbnm"

// ---------------------------------------------------------------------------

private fun layoutToJson(layout: KeyboardLayout): String {
    val sb = StringBuilder(4096)
    sb.append("{\"columns\":").append(layout.columns).append(",\"rows\":[")
    for ((rowIndex, row) in layout.rows.withIndex()) {
        if (rowIndex > 0) sb.append(',')
        sb.append("{\"offset\":").append(row.offset)
        sb.append(",\"height\":").append(row.height)
        sb.append(",\"keys\":[")
        for ((keyIndex, key) in row.keys.withIndex()) {
            if (keyIndex > 0) sb.append(',')
            sb.append(keyToJson(key))
        }
        sb.append("]}")
    }
    sb.append("]}")
    return sb.toString()
}

private fun keyToJson(key: KeySpec): String {
    val sb = StringBuilder(160)
    sb.append('{')
    var kind = "action"
    var value = ""
    var action = ""
    var payload = ""
    var event = ""
    when (val v = key.value) {
        is KeyValue.CharValue -> { kind = "char"; value = v.char.toString() }
        is KeyValue.TextValue -> { kind = "text"; value = v.text }
        is KeyValue.EventValue -> { kind = "event"; event = v.event.name }
        is KeyValue.ActionValue -> { action = v.action.name; payload = v.payload ?: "" }
    }
    sb.append("\"kind\":\"").append(kind).append('"')
    sb.append(",\"value\":").append(quote(value))
    sb.append(",\"label\":").append(quote(key.label ?: defaultLabel(key)))
    sb.append(",\"action\":").append(quote(action))
    sb.append(",\"payload\":").append(quote(payload))
    sb.append(",\"event\":").append(quote(event))
    sb.append(",\"width\":").append(key.width)
    sb.append(",\"style\":\"").append(key.style.name.lowercase()).append('"')
    sb.append(",\"repeat\":").append(key.repeat)
    sb.append(",\"corners\":[")
    for (i in 0 until 4) {
        if (i > 0) sb.append(',')
        val corner = key.corner(CornerDirection.entries[i])
        sb.append(quote(corner?.let { cornerLabel(it) } ?: ""))
    }
    sb.append(']')
    sb.append(",\"longpress\":[")
    for ((i, lp) in key.longPress.withIndex()) {
        if (i > 0) sb.append(',')
        sb.append(quote(cornerLabel(lp)))
    }
    sb.append(']')
    sb.append('}')
    return sb.toString()
}

private fun defaultLabel(key: KeySpec): String = when (val v = key.value) {
    is KeyValue.CharValue -> v.char.toString()
    is KeyValue.TextValue -> v.text
    is KeyValue.EventValue -> v.event.name
    is KeyValue.ActionValue -> when (v.action) {
        dev.retrobit.bitkeyboard.core.KeyboardAction.SHIFT -> "⇧"
        dev.retrobit.bitkeyboard.core.KeyboardAction.BACKSPACE -> "⌫"
        dev.retrobit.bitkeyboard.core.KeyboardAction.ENTER -> "⏎"
        dev.retrobit.bitkeyboard.core.KeyboardAction.SPACE -> "spasi"
        else -> v.action.name
    }
}

private fun cornerLabel(value: KeyValue): String = when (value) {
    is KeyValue.CharValue -> value.char.toString()
    is KeyValue.TextValue -> value.text
    is KeyValue.EventValue -> value.event.name
    is KeyValue.ActionValue -> value.action.name
}

private fun quote(text: String): String {
    val sb = StringBuilder(text.length + 2)
    sb.append('"')
    for (c in text) {
        when (c) {
            '"' -> sb.append("\\\"")
            '\\' -> sb.append("\\\\")
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            '\t' -> sb.append("\\t")
            else -> {
                if (c.code < 0x20) {
                    sb.append("\\u")
                    val hex = c.code.toString(16)
                    repeat(4 - hex.length) { sb.append('0') }
                    sb.append(hex)
                } else {
                    sb.append(c)
                }
            }
        }
    }
    sb.append('"')
    return sb.toString()
}
