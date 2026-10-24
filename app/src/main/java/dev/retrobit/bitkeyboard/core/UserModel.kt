package dev.retrobit.bitkeyboard.core

/**
 * Model bahasa personal: kata dan pasangan kata yang dipelajari dari kebiasaan
 * pengguna. Implementasi Android menyimpannya di SharedPreferences; versi
 * in-memory dipakai untuk pengujian dan demo.
 */
interface UserModel {
    fun frequency(word: String): Int
    fun isLearned(word: String): Boolean
    fun learn(word: String)
    fun forgetAll()
    fun bigramCount(previous: String, next: String): Int
    fun observeBigram(previous: String, next: String)
    fun topNextWords(previous: String, limit: Int): List<Pair<String, Int>>
    fun wordsWithPrefix(prefix: String, limit: Int): List<Pair<String, Int>>

    companion object {
        val EMPTY: UserModel = InMemoryUserModel()
    }
}

/** Implementasi sederhana dengan batas ukuran agar tidak tumbuh tanpa kendali. */
class InMemoryUserModel(
    private val maxWords: Int = 500,
    private val maxBigrams: Int = 500,
) : UserModel {
    private val words = LinkedHashMap<String, Int>()
    private val bigrams = LinkedHashMap<String, Int>()

    override fun frequency(word: String): Int {
        val key = word.lowercase()
        return words[key] ?: 0
    }

    override fun isLearned(word: String): Boolean = frequency(word) > 0

    override fun learn(word: String) {
        val key = word.lowercase()
        if (key.length < 2) return
        words[key] = (words[key] ?: 0) + 1
        trim(words, maxWords)
    }

    override fun forgetAll() {
        words.clear()
        bigrams.clear()
    }

    override fun bigramCount(previous: String, next: String): Int =
        bigrams[bigramKey(previous, next)] ?: 0

    override fun observeBigram(previous: String, next: String) {
        val p = previous.lowercase()
        val n = next.lowercase()
        if (p.isEmpty() || n.isEmpty()) return
        val key = bigramKey(p, n)
        bigrams[key] = (bigrams[key] ?: 0) + 1
        trim(bigrams, maxBigrams)
    }

    override fun topNextWords(previous: String, limit: Int): List<Pair<String, Int>> {
        val p = previous.lowercase()
        return bigrams.entries
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
        return words.entries
            .asSequence()
            .filter { it.key.startsWith(p) }
            .sortedByDescending { it.value }
            .take(limit)
            .map { it.key to it.value }
            .toList()
    }

    private fun bigramKey(a: String, b: String) = "$a\u0001$b"

    private fun trim(map: LinkedHashMap<String, Int>, max: Int) {
        if (map.size <= max) return
        val keep = map.entries.sortedByDescending { it.value }.take(max)
        map.clear()
        for ((k, v) in keep) map[k] = v
    }

    /** Untuk penyimpanan: semua kata yang dipelajari, dipisah koma. */
    fun exportWords(): Map<String, Int> = words.toMap()
    fun exportBigrams(): Map<String, Int> = bigrams.toMap()
    fun import(words: Map<String, Int>, bigrams: Map<String, Int>) {
        this.words.clear()
        this.words.putAll(words)
        this.bigrams.clear()
        this.bigrams.putAll(bigrams)
    }
}
