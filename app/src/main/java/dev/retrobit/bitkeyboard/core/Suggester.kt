package dev.retrobit.bitkeyboard.core

import kotlin.math.ln

/**
 * Mesin saran kata: pelengkapan awalan, koreksi ejaan, prediksi kata berikutnya,
 * dan keputusan autocorrect.
 */
class Suggester(
    private val dictionary: Dictionary,
    private val bigrams: BigramModel,
    private val user: UserModel,
) {
    enum class Kind { COMPLETION, CORRECTION, PREDICTION, LEARNED }

    data class Suggestion(
        val word: String,
        val kind: Kind,
        val distance: Int = 0,
        val frequency: Int = 0,
    )

    /** Saran untuk kata yang sedang diketik. */
    fun onWord(composing: String, previousWord: String, limit: Int = 3): List<Suggestion> {
        if (limit <= 0) return emptyList()
        val query = composing.lowercase()
        if (query.isEmpty()) return nextWords(previousWord, limit)

        val scored = HashMap<String, ScoredSuggestion>()

        fun add(word: String, kind: Kind, distance: Int, frequency: Int, bonus: Double) {
            if (word.isEmpty()) return
            val key = word.lowercase()
            val score = ln((frequency + 1).toDouble()) + bonus - 2.2 * distance
            val existing = scored[key]
            if (existing == null || score > existing.score) {
                scored[key] = ScoredSuggestion(word, kind, distance, frequency, score)
            }
        }

        val exactFrequency = dictionary.frequency(query)

        // 1. Pelengkapan awalan dari kamus.
        for (r in dictionary.completions(query, 6)) {
            add(r.word, Kind.COMPLETION, 0, r.frequency, 3.0)
        }
        // 2. Kata yang pernah dipelajari pengguna.
        for ((w, f) in user.wordsWithPrefix(query, 4)) {
            add(w, Kind.LEARNED, 0, f * 60 + 400, 4.0)
        }
        // 3. Koreksi ejaan hanya bila kata belum dikenal. Kata yang sudah valid
        //    tidak boleh digeser oleh kandidat koreksi yang lebih populer.
        if (exactFrequency == 0 && query.length >= 3) {
            val maxDistance = if (query.length >= 7) 2 else 1
            for (r in dictionary.similar(query, maxDistance, 6)) {
                if (r.distance > 0) add(r.word, Kind.CORRECTION, r.distance, r.frequency, 1.0)
            }
        }

        val ranked = scored.values
            .sortedWith(
                compareByDescending<ScoredSuggestion> { it.score }
                    .thenByDescending { it.frequency }
                    .thenBy { it.word.length }
            )

        val result = ArrayList<Suggestion>(limit)
        if (exactFrequency > 0) {
            // Kata yang diketik persis selalu di posisi pertama.
            result += Suggestion(
                word = applyCaseIfNeeded(composing, query),
                kind = Kind.COMPLETION,
                frequency = exactFrequency,
            )
        }
        for (item in ranked) {
            if (result.size >= limit) break
            if (item.word == query && exactFrequency > 0) continue
            result += Suggestion(
                word = applyCaseIfNeeded(composing, item.word),
                kind = item.kind,
                distance = item.distance,
                frequency = item.frequency,
            )
        }
        return result
    }

    /** Prediksi kata berikutnya setelah [previousWord]. */
    fun nextWords(previousWord: String, limit: Int = 3): List<Suggestion> {
        if (limit <= 0) return emptyList()
        val prev = previousWord.lowercase().trim()
        if (prev.isEmpty()) return emptyList()
        val out = ArrayList<Suggestion>()
        val seen = HashSet<String>()

        for ((w, n) in user.topNextWords(prev, limit)) {
            if (seen.add(w)) out += Suggestion(w, Kind.PREDICTION, frequency = n)
        }
        for (e in bigrams.nextWords(prev, limit)) {
            if (seen.add(e.word)) out += Suggestion(e.word, Kind.PREDICTION, frequency = e.count)
        }
        if (out.size < limit) {
            // fallback: kata yang sangat umum tidak dipaksakan; cukup kembalikan
            // prediksi yang ada agar bilah saran tidak penuh dengan kata acak.
        }
        return out.take(limit)
    }

    /**
     * Menentukan kata pengganti otomatis. Mengembalikan null bila kata sudah
     * benar atau tidak ada kandidat yang cukup meyakinkan.
     */
    fun autocorrect(composing: String): String? {
        val query = composing.lowercase()
        if (query.length < 3) return null
        if (query.any { it < 'a' || it > 'z' }) return null
        if (user.isLearned(query)) return null
        val exactFreq = dictionary.frequency(query)
        if (exactFreq > 0) return null

        val maxDistance = if (query.length >= 7) 2 else 1
        val candidates = dictionary.similar(query, maxDistance, 8)
        val best = candidates.firstOrNull { r ->
            r.distance <= maxDistance &&
                r.frequency >= minFrequencyFor(query.length, r.distance)
        } ?: return null
        if (best.word == query) return null
        return TextUtils.applyCase(composing, best.word)
    }

    private fun minFrequencyFor(length: Int, distance: Int): Int {
        val base = when {
            length >= 9 -> 25
            length >= 6 -> 60
            else -> 250
        }
        return if (distance >= 2) base * 8 else base
    }

    /** Apakah kata layak ditambahkan ke kamus pengguna. */
    fun shouldLearn(word: String): Boolean {
        val w = word.lowercase()
        return w.length >= 3 && w.all { it in 'a'..'z' } && !dictionary.contains(w)
    }

    fun isKnown(word: String): Boolean =
        dictionary.contains(word.lowercase()) || user.isLearned(word)

    fun dictionarySize(): Int = dictionary.size

    private fun applyCaseIfNeeded(original: String, suggestion: String): String {
        if (original.isEmpty()) return suggestion
        return if (original[0].isUpperCase()) TextUtils.applyCase(original, suggestion) else suggestion
    }

    private data class ScoredSuggestion(
        val word: String,
        val kind: Kind,
        val distance: Int,
        val frequency: Int,
        val score: Double,
    )
}
