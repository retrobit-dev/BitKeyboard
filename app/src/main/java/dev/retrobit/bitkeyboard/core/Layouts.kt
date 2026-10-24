package dev.retrobit.bitkeyboard.core

/**
 * Definisi semua layout. Karakter di sudut tombol (corner) bisa diketik dengan
 * menggeser jari dari tengah tombol ke arah sudut, seperti Unexpected Keyboard.
 * Urutan corner: NW (kiri atas), NE (kanan atas), SW (kiri bawah), SE (kanan bawah).
 */
object Layouts {

    private fun char(
        c: Char,
        corners: List<Char?> = emptyList(),
        longPress: String = "",
        width: Float = 1f,
        style: KeyStyle = KeyStyle.NORMAL,
        repeat: Boolean = false,
        label: String? = null,
    ) = KeySpec(
        value = KeyValue.CharValue(c),
        label = label,
        corners = corners.map { it?.let { ch -> KeyValue.CharValue(ch) } },
        longPress = longPress.map { KeyValue.CharValue(it) },
        width = width,
        style = style,
        repeat = repeat,
    )

    private fun action(
        a: KeyboardAction,
        label: String,
        payload: String? = null,
        width: Float = 1f,
        style: KeyStyle = KeyStyle.ACTION,
        corners: List<Char?> = emptyList(),
        repeat: Boolean = false,
    ) = KeySpec(
        value = KeyValue.ActionValue(a, payload),
        label = label,
        corners = corners.map { it?.let { ch -> KeyValue.CharValue(ch) } },
        width = width,
        style = style,
        repeat = repeat,
    )

    private fun layer(target: Layer, label: String, width: Float = 1f) =
        KeySpec(
            value = KeyValue.ActionValue(KeyboardAction.SWITCH_LAYER, target.id),
            label = label,
            width = width,
            style = KeyStyle.FUNCTION,
        )

    private fun event(e: KeyEvent, label: String, width: Float = 1f, repeat: Boolean = false) =
        KeySpec(
            value = KeyValue.EventValue(e),
            label = label,
            width = width,
            style = KeyStyle.FUNCTION,
            repeat = repeat,
        )

    private val ACCENTS_A = "àáâäãåāăąæ"
    private val ACCENTS_E = "èéêëēėęě"
    private val ACCENTS_I = "ìíîïīįı"
    private val ACCENTS_O = "òóôöõøōœ"
    private val ACCENTS_U = "ùúûüūůű"
    private val ACCENTS_N = "ñńň"
    private val ACCENTS_C = "çćčĉ"
    private val ACCENTS_S = "śšşß"
    private val ACCENTS_Y = "ýÿŷ"
    private val ACCENTS_Z = "źžż"
    private val ACCENTS_G = "ğĝ"
    private val ACCENTS_L = "łľĺ"
    private val ACCENTS_T = "ťţ"
    private val ACCENTS_R = "řŕ"

    private fun letter(c: Char, corners: List<Char?>, accents: String = "", longPress: String = "") =
        char(c, corners = corners, longPress = accents + longPress)

    // ------------------------------------------------------------------ //
    // Layer huruf
    // ------------------------------------------------------------------ //
    fun letters(numberRow: Boolean, functionRow: Boolean): KeyboardLayout {
        val rows = mutableListOf<KeyRow>()

        if (functionRow) {
            rows += KeyRow(
                listOf(
                    event(KeyEvent.ESCAPE, "Esc"),
                    event(KeyEvent.TAB, "Tab"),
                    action(KeyboardAction.CTRL, "Ctrl"),
                    action(KeyboardAction.ALT, "Alt"),
                    action(KeyboardAction.FN, "Fn"),
                    event(KeyEvent.DPAD_LEFT, "←", repeat = true),
                    event(KeyEvent.DPAD_UP, "↑", repeat = true),
                    event(KeyEvent.DPAD_DOWN, "↓", repeat = true),
                    event(KeyEvent.DPAD_RIGHT, "→", repeat = true),
                    event(KeyEvent.MOVE_HOME, "Home"),
                ),
                height = 0.85f,
            )
        }

        if (numberRow) {
            rows += KeyRow(
                listOf(
                    char('1', listOf('`', '~', '!', '¡')),
                    char('2', listOf(null, null, '@', '€')),
                    char('3', listOf(null, null, '#', '£')),
                    char('4', listOf(null, null, '$', '¥')),
                    char('5', listOf(null, null, '%', '‰')),
                    char('6', listOf(null, null, '^', '°')),
                    char('7', listOf(null, null, '&', '§')),
                    char('8', listOf(null, null, '*', '×')),
                    char('9', listOf(null, null, '(', '÷')),
                    char('0', listOf(null, null, ')', '=')),
                ),
                height = 0.9f,
            )
        }

        rows += KeyRow(
            listOf(
                letter('q', listOf('1', '!', '`', '~')),
                letter('w', listOf('2', '@', '-', '_')),
                letter('e', listOf('3', '#', '+', '='), ACCENTS_E),
                letter('r', listOf('4', '$', '[', '{')),
                letter('t', listOf('5', '%', ']', '}')),
                letter('y', listOf('6', '^', '\\', '|'), ACCENTS_Y),
                letter('u', listOf('7', '&', ';', ':'), ACCENTS_U),
                letter('i', listOf('8', '*', '\'', '"'), ACCENTS_I),
                letter('o', listOf('9', '(', ',', '<'), ACCENTS_O),
                letter('p', listOf('0', ')', '.', '>')),
            ),
        )

        rows += KeyRow(
            listOf(
                letter('a', listOf('±', '∓', '(', ')'), ACCENTS_A),
                letter('s', listOf('√', '∛', '[', ']'), ACCENTS_S),
                letter('d', listOf('π', 'ℯ', '{', '}')),
                letter('f', listOf('∞', '°', '<', '>')),
                letter('g', listOf('÷', '×', '/', '\\'), ACCENTS_G),
                letter('h', listOf('≈', '≠', '|', '&')),
                letter('j', listOf('≤', '≥', ';', ':')),
                letter('k', listOf('µ', 'Ω', '"', '\'')),
                letter('l', listOf('∑', '∏', '?', '!'), ACCENTS_L),
            ),
            offset = 0.5f,
        )

        rows += KeyRow(
            listOf(
                KeySpec(
                    value = KeyValue.ActionValue(KeyboardAction.SHIFT),
                    label = "⇧",
                    width = 1.5f,
                    style = KeyStyle.FUNCTION,
                    repeat = false,
                ),
                letter('z', listOf('α', 'β', '•', '·'), ACCENTS_Z),
                letter('x', listOf('γ', 'δ', '…', '—')),
                letter('c', listOf('θ', 'λ', '©', '®'), ACCENTS_C),
                letter('v', listOf('σ', 'φ', '™', '§')),
                letter('b', listOf('ω', '∆', '½', '¼')),
                letter('n', listOf('∈', '∉', '°', '′'), ACCENTS_N),
                letter('m', listOf('∂', '∇', '€', '¢')),
                KeySpec(
                    value = KeyValue.ActionValue(KeyboardAction.BACKSPACE),
                    label = "⌫",
                    width = 1.5f,
                    style = KeyStyle.FUNCTION,
                    repeat = true,
                ),
            ),
        )

        rows += KeyRow(
            listOf(
                layer(Layer.SYMBOLS, "?123", 1.5f),
                action(KeyboardAction.OPEN_SETTINGS, "⚙", width = 0.9f),
                KeySpec(
                    value = KeyValue.ActionValue(KeyboardAction.SPACE),
                    label = "spasi",
                    width = 4.35f,
                    style = KeyStyle.SPACE,
                ),
                char(',', longPress = ";،", width = 0.75f),
                char('.', longPress = "…?!", width = 0.75f),
                KeySpec(
                    value = KeyValue.ActionValue(KeyboardAction.ENTER),
                    label = "⏎",
                    width = 1.75f,
                    style = KeyStyle.ACTION,
                ),
            ),
        )

        return KeyboardLayout(
            layer = Layer.LETTERS,
            rows = rows,
            columns = 10f,
            letterRowRange = (rows.size - 4)..rows.size - 2,
        )
    }

    // ------------------------------------------------------------------ //
    // Angka & tanda baca
    // ------------------------------------------------------------------ //
    fun symbols(): KeyboardLayout = KeyboardLayout(
        layer = Layer.SYMBOLS,
        rows = listOf(
            KeyRow(
                "1234567890".map { char(it, longPress = if (it == '1') "¹½⅓" else "") }
            ),
            KeyRow(
                listOf(
                    char('@', listOf(null, null, '€', '£'), longPress = "@"),
                    char('#', longPress = "#№"),
                    char('$', longPress = "€£¥¢"),
                    char('_', listOf(null, null, '-', '–')),
                    char('&', longPress = "&§"),
                    char('-', listOf('−', '–', '_', '—')),
                    char('+', listOf('±', '∓', '=', '≠')),
                    char('(', listOf('[', '{', '<', '≤')),
                    char(')', listOf(']', '}', '>', '≥')),
                    char('/', listOf('\\', '|', '÷', '⁄')),
                ),
            ),
            KeyRow(
                listOf(
                    char('*', listOf('×', '·', '•', '∘')),
                    char('"', listOf('“', '”', '„', '‟')),
                    char('\'', listOf('‘', '’', '‚', '`')),
                    char(':', listOf(';', '…', '∶', '：')),
                    char(';', listOf(':', ',', '؛', '⁏')),
                    char('!', listOf('¡', '?', '‼', '⁉')),
                    char('?', listOf('¿', '!', '⁇', '⁈')),
                    char('=', listOf('≈', '≠', '≡', '≅')),
                    char('%', listOf('‰', '‱', '٪', '℅')),
                    char('~', listOf('≈', '˜', 'ˆ', '˘')),
                ),
            ),
            KeyRow(
                listOf(
                    layer(Layer.LETTERS, "ABC", 1.5f),
                    layer(Layer.SYMBOLS_ALT, "=<", 0.8f),
                    char(',', longPress = ";", width = 0.7f),
                    KeySpec(
                        value = KeyValue.ActionValue(KeyboardAction.SPACE),
                        label = "spasi",
                        width = 3.4f,
                        style = KeyStyle.SPACE,
                    ),
                    char('.', longPress = "…", width = 0.7f),
                    layer(Layer.SCIENTIFIC, "ƒx", 0.9f),
                    KeySpec(
                        value = KeyValue.ActionValue(KeyboardAction.BACKSPACE),
                        label = "⌫",
                        width = 1.0f,
                        style = KeyStyle.FUNCTION,
                        repeat = true,
                    ),
                    KeySpec(
                        value = KeyValue.ActionValue(KeyboardAction.ENTER),
                        label = "⏎",
                        width = 1.0f,
                        style = KeyStyle.ACTION,
                    ),
                ),
            ),
        ),
        letterRowRange = IntRange.EMPTY,
    )

    fun symbolsAlt(): KeyboardLayout = KeyboardLayout(
        layer = Layer.SYMBOLS_ALT,
        rows = listOf(
            KeyRow(
                listOf(
                    char('`', listOf('~', '^', '´', '`')),
                    char('|', listOf('\\', '/', '¦', '∣')),
                    char('\\', listOf('|', '/', '⧵', '⧸')),
                    char('{', listOf('[', '(', '⟨', '⦃')),
                    char('}', listOf(']', ')', '⟩', '⦄')),
                    char('[', listOf('{', '(', '⟦', '⌈')),
                    char(']', listOf('}', ')', '⟧', '⌉')),
                    char('«', listOf('»', '‹', '›', '⟨')),
                    char('»', listOf('«', '›', '‹', '⟩')),
                    char('°', listOf('℃', '℉', '′', '″')),
                ),
            ),
            KeyRow(
                listOf(
                    char('©', longPress = "®™"),
                    char('®', longPress = "©™"),
                    char('™', longPress = "®©"),
                    char('§', longPress = "¶†‡"),
                    char('€', longPress = "£¥$"),
                    char('£', longPress = "€¥$"),
                    char('¥', longPress = "€£$"),
                    char('¢', longPress = "€£$"),
                    char('₽', longPress = "₩₹₺"),
                    char('₩', longPress = "₹₽₺"),
                ),
            ),
            KeyRow(
                listOf(
                    char('←', listOf('↔', '⇐', '⟵', '↤')),
                    char('↑', listOf('↕', '⇑', '⟰', '↥')),
                    char('→', listOf('↔', '⇒', '⟶', '↦')),
                    char('↓', listOf('↕', '⇓', '⟱', '↧')),
                    char('↔', listOf('↕', '⇔', '⇄', '⇆')),
                    char('⇒', listOf('⇐', '⇔', '⟹', '⟸')),
                    char('⇔', listOf('⇒', '⇐', '⟺', '⇋')),
                    char('↺', listOf('↻', '⟲', '⟳', '⥀')),
                    char('↻', listOf('↺', '⟳', '⟲', '⥁')),
                    char('∴', longPress = "∵"),
                ),
            ),
            KeyRow(
                listOf(
                    layer(Layer.LETTERS, "ABC", 1.6f),
                    layer(Layer.SYMBOLS, "?123", 0.8f),
                    KeySpec(
                        value = KeyValue.ActionValue(KeyboardAction.SPACE),
                        label = "spasi",
                        width = 4.0f,
                        style = KeyStyle.SPACE,
                    ),
                    layer(Layer.EMOJI, "☺", 0.8f),
                    layer(Layer.SCIENTIFIC, "ƒx", 1.0f),
                    KeySpec(
                        value = KeyValue.ActionValue(KeyboardAction.BACKSPACE),
                        label = "⌫",
                        width = 1.0f,
                        style = KeyStyle.FUNCTION,
                        repeat = true,
                    ),
                    KeySpec(
                        value = KeyValue.ActionValue(KeyboardAction.ENTER),
                        label = "⏎",
                        width = 0.8f,
                        style = KeyStyle.ACTION,
                    ),
                ),
            ),
        ),
    )

    // ------------------------------------------------------------------ //
    // Simbol matematika & ilmiah
    // ------------------------------------------------------------------ //
    fun scientific(): KeyboardLayout = KeyboardLayout(
        layer = Layer.SCIENTIFIC,
        rows = listOf(
            KeyRow("αβγδεζηθικ".map { char(it) }),
            KeyRow("λμνξπρστφχ".map { char(it) }),
            KeyRow("ψω∆Ω∑∏∫√∞∂".map { char(it) }),
            KeyRow("≈≠≤≥±×÷∇°µ".map { char(it) }),
            KeyRow(
                listOf(
                    layer(Layer.LETTERS, "ABC", 1.6f),
                    layer(Layer.SYMBOLS, "?123", 0.8f),
                    layer(Layer.SCIENTIFIC_ALT, "αβ", 0.8f),
                    event(KeyEvent.DPAD_LEFT, "←", 0.7f, repeat = true),
                    event(KeyEvent.DPAD_UP, "↑", 0.7f, repeat = true),
                    event(KeyEvent.DPAD_DOWN, "↓", 0.7f, repeat = true),
                    event(KeyEvent.DPAD_RIGHT, "→", 0.7f, repeat = true),
                    KeySpec(
                        value = KeyValue.ActionValue(KeyboardAction.SPACE),
                        label = "spasi",
                        width = 1.8f,
                        style = KeyStyle.SPACE,
                    ),
                    KeySpec(
                        value = KeyValue.ActionValue(KeyboardAction.BACKSPACE),
                        label = "⌫",
                        width = 1.1f,
                        style = KeyStyle.FUNCTION,
                        repeat = true,
                    ),
                    KeySpec(
                        value = KeyValue.ActionValue(KeyboardAction.ENTER),
                        label = "⏎",
                        width = 1.1f,
                        style = KeyStyle.ACTION,
                    ),
                ),
            ),
        ),
    )

    fun scientificAlt(): KeyboardLayout = KeyboardLayout(
        layer = Layer.SCIENTIFIC_ALT,
        rows = listOf(
            KeyRow("⁰¹²³⁴⁵⁶⁷⁸⁹".map { char(it) }),
            KeyRow("₀₁₂₃₄₅₆₇₈₉".map { char(it) }),
            KeyRow("←→↑↓↔⇒⇔∈∉∅".map { char(it) }),
            KeyRow("∴∵⊥∥∠≅≡∝≪≫".map { char(it) }),
            KeyRow(
                listOf(
                    layer(Layer.LETTERS, "ABC", 1.5f),
                    layer(Layer.SCIENTIFIC, "ƒx", 0.9f),
                    KeySpec(
                        value = KeyValue.ActionValue(KeyboardAction.SPACE),
                        label = "spasi",
                        width = 4.1f,
                        style = KeyStyle.SPACE,
                    ),
                    event(KeyEvent.DPAD_LEFT, "←", 0.8f, repeat = true),
                    event(KeyEvent.DPAD_RIGHT, "→", 0.8f, repeat = true),
                    KeySpec(
                        value = KeyValue.ActionValue(KeyboardAction.BACKSPACE),
                        label = "⌫",
                        width = 1.1f,
                        style = KeyStyle.FUNCTION,
                        repeat = true,
                    ),
                    KeySpec(
                        value = KeyValue.ActionValue(KeyboardAction.ENTER),
                        label = "⏎",
                        width = 0.8f,
                        style = KeyStyle.ACTION,
                    ),
                ),
            ),
        ),
    )

    /** Layout huruf tanpa baris fungsi/angka untuk perhitungan tinggi. */
    fun lettersDefault(): KeyboardLayout = letters(numberRow = false, functionRow = false)
}
