package dev.retrobit.bitkeyboard.core

/** Prediksi kata berikutnya dari daftar bigram kurasi. */
class BigramModel private constructor(
    private val map: HashMap<String, List<Entry>>,
) {
    data class Entry(val word: String, val count: Int)

    fun nextWords(previous: String, limit: Int): List<Entry> {
        if (limit <= 0) return emptyList()
        val list = map[previous.lowercase()] ?: return emptyList()
        return list.take(limit)
    }

    fun isEmpty(): Boolean = map.isEmpty()

    companion object {
        val EMPTY = BigramModel(HashMap())

        fun fromText(text: String): BigramModel {
            val acc = HashMap<String, HashMap<String, Int>>()
            for (line in text.split('\n')) {
                if (line.isEmpty() || line[0] == '#') continue
                val parts = line.split('\t')
                if (parts.size < 3) continue
                val a = parts[0].lowercase()
                val b = parts[1].lowercase()
                val n = parts[2].toIntOrNull() ?: continue
                if (a.isEmpty() || b.isEmpty()) continue
                val bucket = acc.getOrPut(a) { HashMap() }
                bucket[b] = (bucket[b] ?: 0) + n
            }
            val map = HashMap<String, List<Entry>>(acc.size * 2)
            for ((prev, bucket) in acc) {
                map[prev] = bucket.entries
                    .sortedByDescending { it.value }
                    .map { Entry(it.key, it.value) }
            }
            return BigramModel(map)
        }
    }
}
