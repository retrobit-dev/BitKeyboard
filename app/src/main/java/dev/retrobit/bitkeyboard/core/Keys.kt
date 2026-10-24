package dev.retrobit.bitkeyboard.core

/**
 * Aksi tingkat keyboard yang ditangani oleh lapisan UI.
 * Berbeda dengan [KeyEvent] yang dipetakan langsung ke kode tombol Android.
 */
enum class KeyboardAction {
    SHIFT,
    CAPS_LOCK,
    CTRL,
    ALT,
    FN,
    SWITCH_LAYER,
    EMOJI_TAB,
    BACKSPACE,
    DELETE_WORD,
    ENTER,
    SPACE,
    TAB,
    ESCAPE,
    ARROW_LEFT,
    ARROW_RIGHT,
    ARROW_UP,
    ARROW_DOWN,
    MOVE_WORD_LEFT,
    MOVE_WORD_RIGHT,
    SELECT_ALL,
    COPY,
    CUT,
    PASTE,
    UNDO,
    REDO,
    VOICE_INPUT,
    OPEN_SETTINGS,
    HIDE_KEYBOARD,
    SHOW_IME_PICKER,
    COMMIT_TEXT,
}

/** Tombol fisik yang bisa dikirim ke aplikasi lewat InputConnection. */
enum class KeyEvent(val androidKeyCode: Int) {
    BACKSPACE(67),
    FORWARD_DELETE(112),
    ENTER(66),
    TAB(61),
    ESCAPE(111),
    DPAD_LEFT(21),
    DPAD_RIGHT(22),
    DPAD_UP(19),
    DPAD_DOWN(20),
    MOVE_HOME(122),
    MOVE_END(123),
    PAGE_UP(92),
    PAGE_DOWN(93),
    INSERT(124),
    F1(131), F2(132), F3(133), F4(134), F5(135), F6(136),
    F7(137), F8(138), F9(139), F10(140), F11(141), F12(142),
}

/** Nilai sebuah tombol: karakter, teks, event, atau aksi UI. */
sealed class KeyValue {
    data class CharValue(val char: Char) : KeyValue()
    data class TextValue(val text: String) : KeyValue()
    data class EventValue(val event: KeyEvent) : KeyValue()
    data class ActionValue(val action: KeyboardAction, val payload: String? = null) : KeyValue()
}

/** Jenis visual tombol, memengaruhi warna dan bentuk. */
enum class KeyStyle { NORMAL, FUNCTION, ACTION, SPACE, MODIFIER }

/**
 * Satu tombol pada layout.
 *
 * @param corners nilai untuk geser ke sudut: barat laut, timur laut, barat daya,
 *   tenggara. `null` berarti sudut tersebut kosong.
 * @param longPress daftar karakter alternatif (mis. aksen) yang muncul saat
 *   tombol ditekan lama lalu digeser.
 * @param width lebar dalam satuan kolom (1.0 = satu tombol huruf).
 * @param repeat apakah tombol mengulang saat ditahan (backspace, panah).
 */
data class KeySpec(
    val value: KeyValue,
    val label: String? = null,
    val corners: List<KeyValue?> = emptyList(),
    val longPress: List<KeyValue> = emptyList(),
    val width: Float = 1f,
    val repeat: Boolean = false,
    val style: KeyStyle = KeyStyle.NORMAL,
) {
    fun corner(direction: CornerDirection): KeyValue? = when (direction) {
        CornerDirection.NW -> corners.getOrNull(0)
        CornerDirection.NE -> corners.getOrNull(1)
        CornerDirection.SW -> corners.getOrNull(2)
        CornerDirection.SE -> corners.getOrNull(3)
    }
}

enum class CornerDirection { NW, NE, SW, SE }

/** Satu baris tombol. [offset] adalah jarak dari tepi kiri dalam satuan kolom. */
data class KeyRow(
    val keys: List<KeySpec>,
    val offset: Float = 0f,
    val height: Float = 1f,
) {
    val totalWidth: Float get() = offset + keys.sumOf { it.width.toDouble() }.toFloat()
}

/** Layer logis; nilainya juga dipakai sebagai payload SWITCH_LAYER. */
enum class Layer(val id: String, val displayName: String) {
    LETTERS("letters", "Abc"),
    SYMBOLS("symbols", "?123"),
    SYMBOLS_ALT("symbols_alt", "=/#"),
    SCIENTIFIC("scientific", "ƒx"),
    SCIENTIFIC_ALT("scientific_alt", "αβ"),
    EMOJI("emoji", "☺"),
    ;

    companion object {
        fun fromId(id: String?): Layer = entries.firstOrNull { it.id == id } ?: LETTERS
    }
}

/** Definisi lengkap satu layer keyboard. */
data class KeyboardLayout(
    val layer: Layer,
    val rows: List<KeyRow>,
    val columns: Float = 10f,
    /** Baris (0-based) tempat tombol huruf QWERTY berada, dipakai untuk glide. */
    val letterRowRange: IntRange = IntRange.EMPTY,
) {
    val heightUnits: Float get() = rows.sumOf { it.height.toDouble() }.toFloat()
}

/** Utilitas label yang ditampilkan pada tombol. */
fun KeyValue.displayLabel(): String = when (this) {
    is KeyValue.CharValue -> char.toString()
    is KeyValue.TextValue -> text
    is KeyValue.EventValue -> event.name
    is KeyValue.ActionValue -> action.name
}
