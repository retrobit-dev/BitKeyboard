package dev.retrobit.bitkeyboard.core

/**
 * Satu penimpaan untuk sebuah tombol.
 *
 * @param corners empat nilai sudut (NW, NE, SW, SE). `null` berarti sudut itu
 *   dikosongkan, sedangkan daftar kosong berarti "ikut bawaan".
 * @param longPress daftar tekan-lama pengganti, atau `null` bila ikut bawaan.
 */
data class KeyOverride(
    val corners: List<String?>? = null,
    val longPress: List<String>? = null,
)

/**
 * Kustomisasi tombol yang disimpan pengguna.
 *
 * Kunci peta adalah [idFor] dari tombol: satu karakter dasar dalam huruf kecil
 * (`q`, `1`, `.`) atau label aksi (`shift`, `space`).
 *
 * Format serialisasi ringkas agar bisa disimpan di SharedPreferences:
 *
 * ```
 * q=1|!|`|~;e=3|#|+|=\u0001è,é,ê
 * ```
 *
 * Setiap entri: `id=c1|c2|c3|c4` (slot kosong berarti sudut kosong), lalu
 * opsional `\u0001` diikuti daftar tekan-lama yang dipisah `,`. Bila sebuah
 * entri hanya berisi `id=\u0001lp1,lp2` maka sudut tidak diubah.
 *
 * Pemisah sengaja memakai karakter kontrol agar tidak bentrok dengan karakter
 * yang boleh diketik pengguna (mis. `~` atau `|`).
 */
object KeyCustomization {

    const val MAX_CORNER_LEN = 4
    const val MAX_LONG_PRESS = 24

    /** Pemisah nilai tekan-lama dari daftar sudut. */
    private const val LONG_SEPARATOR = '\u0001'

    /** Id stabil untuk sebuah tombol; dipakai sebagai kunci penyimpanan. */
    fun idFor(spec: KeySpec): String = when (val value = spec.value) {
        is KeyValue.CharValue -> value.char.lowercaseChar().toString()
        is KeyValue.TextValue -> "t:" + value.text.lowercase()
        is KeyValue.EventValue -> "e:" + value.event.name.lowercase()
        is KeyValue.ActionValue -> "a:" + value.action.name.lowercase()
    }

    /** Label yang ditampilkan di editor. */
    fun displayName(spec: KeySpec): String = when (val value = spec.value) {
        is KeyValue.CharValue -> value.char.toString()
        is KeyValue.TextValue -> value.text
        is KeyValue.EventValue -> spec.label ?: value.event.name
        is KeyValue.ActionValue -> spec.label ?: value.action.name
    }

    /**
     * Terapkan penimpaan ke sebuah tombol. Tombol tanpa penimpaan
     * dikembalikan apa adanya.
     */
    fun apply(spec: KeySpec, overrides: Map<String, KeyOverride>): KeySpec {
        if (overrides.isEmpty()) return spec
        val override = overrides[idFor(spec)] ?: return spec

        val corners = override.corners?.let { values ->
            val out = ArrayList<KeyValue?>(4)
            for (i in 0 until 4) {
                val raw = values.getOrNull(i)?.trim().orEmpty()
                out.add(if (raw.isEmpty()) null else KeyValue.CharValue(raw[0]))
            }
            out
        } ?: spec.corners

        val longPress = override.longPress?.let { values ->
            values.mapNotNull { text ->
                val trimmed = text.trim()
                when {
                    trimmed.isEmpty() -> null
                    trimmed.length == 1 -> KeyValue.CharValue(trimmed[0])
                    else -> KeyValue.TextValue(trimmed)
                }
            }
        } ?: spec.longPress

        if (corners == spec.corners && longPress == spec.longPress) return spec
        return spec.copy(corners = corners, longPress = longPress)
    }

    /** Terapkan ke semua tombol pada layout. */
    fun apply(layout: KeyboardLayout, overrides: Map<String, KeyOverride>): KeyboardLayout {
        if (overrides.isEmpty()) return layout
        val rows = layout.rows.map { row ->
            row.copy(keys = row.keys.map { apply(it, overrides) })
        }
        return layout.copy(rows = rows)
    }

    fun parse(text: String): Map<String, KeyOverride> {
        if (text.isBlank()) return emptyMap()
        val result = LinkedHashMap<String, KeyOverride>()
        for (entry in text.split(';')) {
            val trimmed = entry.trim()
            if (trimmed.isEmpty()) continue
            val eq = trimmed.indexOf('=')
            if (eq <= 0) continue
            val id = trimmed.substring(0, eq).trim().lowercase()
            if (id.isEmpty()) continue
            val body = trimmed.substring(eq + 1)
            val marker = body.indexOf(LONG_SEPARATOR)
            val cornerPart = if (marker >= 0) body.substring(0, marker) else body
            val longPart = if (marker >= 0) body.substring(marker + 1) else null

            val corners: List<String?>? = if (cornerPart.isEmpty()) {
                null
            } else {
                val pieces = cornerPart.split('|')
                List(4) { index -> pieces.getOrNull(index)?.takeIf { it.isNotEmpty() } }
            }
            val longPress: List<String>? = longPart?.split(',')
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?.take(MAX_LONG_PRESS)

            if (corners == null && longPress == null) continue
            result[id] = KeyOverride(corners = corners, longPress = longPress)
        }
        return result
    }

    fun serialize(overrides: Map<String, KeyOverride>): String {
        if (overrides.isEmpty()) return ""
        val sb = StringBuilder()
        for ((id, override) in overrides) {
            if (override.corners == null && override.longPress == null) continue
            if (sb.isNotEmpty()) sb.append(';')
            sb.append(id).append('=')
            override.corners?.let { values ->
                for (i in 0 until 4) {
                    if (i > 0) sb.append('|')
                    // Sudut hanya menampung satu karakter.
                    sb.append(values.getOrNull(i)?.trim()?.take(1).orEmpty())
                }
            }
            override.longPress?.let { values ->
                sb.append(LONG_SEPARATOR)
                sb.append(
                    values.map { it.trim().take(MAX_CORNER_LEN) }
                        .filter { it.isNotEmpty() }
                        .take(MAX_LONG_PRESS)
                        .joinToString(","),
                )
            }
        }
        return sb.toString()
    }

    /** Setel satu tombol tanpa menghapus penimpaan tombol lain. */
    fun withEntry(
        overrides: Map<String, KeyOverride>,
        id: String,
        override: KeyOverride?,
    ): Map<String, KeyOverride> {
        val next = LinkedHashMap(overrides)
        if (override == null) next.remove(id) else next[id] = override
        return next
    }

    /**
     * Semua tombol yang bisa dikustomisasi, dikelompokkan per layer agar editor
     * mudah dibaca. Tombol dengan id sama hanya muncul sekali.
     */
    fun editableKeyGroups(): List<Pair<String, List<KeySpec>>> {
        val seen = HashSet<String>()
        val groups = ArrayList<Pair<String, List<KeySpec>>>()
        val sources = listOf(
            "Huruf" to Layouts.letters(numberRow = false, functionRow = false),
            "Angka & tanda baca" to Layouts.symbols(),
            "Simbol lain" to Layouts.symbolsAlt(),
            "Ilmiah" to Layouts.scientific(),
            "Ilmiah lanjutan" to Layouts.scientificAlt(),
        )
        for ((name, layout) in sources) {
            val keys = layout.rows
                .flatMap { row -> row.keys }
                .filter { it.style == KeyStyle.NORMAL || it.style == KeyStyle.SPACE }
                .filter { it.value is KeyValue.CharValue }
                .filter { seen.add(idFor(it)) }
            if (keys.isNotEmpty()) groups.add(name to keys)
        }
        return groups
    }

    /** Rata semua tombol yang bisa dikustomisasi. */
    fun editableKeys(): List<KeySpec> = editableKeyGroups().flatMap { it.second }
}
