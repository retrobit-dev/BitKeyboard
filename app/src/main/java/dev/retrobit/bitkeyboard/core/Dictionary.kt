package dev.retrobit.bitkeyboard.core

/**
 * Kamus kata Bahasa Indonesia berbasis trie yang dioptimalkan untuk perangkat.
 *
 * Data dimuat dari aset teks ("kata<TAB>frekuensi"). Struktur internal memakai
 * array primitif agar hemat memori dan cepat. Tidak ada ketergantungan Android
 * sehingga kelas ini juga bisa dikompilasi ke JavaScript untuk demo.
 */
class Dictionary private constructor(
    private val words: Array<String>,
    private val freqs: IntArray,
    private val lengths: IntArray,
    private val firstChar: CharArray,
    private val lastChar: CharArray,
    // Trie
    private val firstEdge: IntArray,
    private val termWord: IntArray,
    private val bestFreq: IntArray,
    // Edge
    private val edgeLetter: CharArray,
    private val edgeTarget: IntArray,
    private val nextEdge: IntArray,
    // Bucket kata berdasarkan huruf awal & akhir
    private val bucketStart: IntArray,
    private val bucketWords: IntArray,
) {

    val size: Int get() = words.size

    fun wordAt(index: Int): String = words[index]
    fun frequencyAt(index: Int): Int = freqs[index]

    fun frequency(word: String): Int {
        val node = nodeFor(word) ?: return 0
        val w = termWord[node]
        return if (w >= 0) freqs[w] else 0
    }

    fun contains(word: String): Boolean {
        val node = nodeFor(word) ?: return false
        return termWord[node] >= 0
    }

    private fun nodeFor(word: String): Int? {
        var node = 0
        for (c in word) {
            node = child(node, c)
            if (node < 0) return null
        }
        return node
    }

    private fun child(node: Int, c: Char): Int {
        var e = firstEdge[node]
        while (e >= 0) {
            if (edgeLetter[e] == c) return edgeTarget[e]
            e = nextEdge[e]
        }
        return -1
    }

    /**
     * Kata-kata yang berawalan [prefix], diurutkan berdasarkan frekuensi menurun.
     */
    fun completions(prefix: String, limit: Int): List<WordResult> {
        if (limit <= 0) return emptyList()
        val sink = CandidateSink(limit)
        var node = 0
        for (c in prefix.lowercase()) {
            node = child(node, c)
            if (node < 0) return emptyList()
        }
        collect(node, sink)
        return sink.toList(words, freqs)
    }

    private fun collect(node: Int, sink: CandidateSink) {
        val w = termWord[node]
        if (w >= 0) sink.offer(w, 0, freqs[w])
        var e = firstEdge[node]
        while (e >= 0) {
            val target = edgeTarget[e]
            if (!sink.isFull || bestFreq[target] >= sink.minFrequency) {
                collect(target, sink)
            }
            e = nextEdge[e]
        }
    }

    /**
     * Kata yang mirip (jarak Levenshtein <= [maxDistance]). Traversal trie dengan
     * pemangkasan baris DP; hasil diurutkan jarak terkecil lalu frekuensi terbesar.
     */
    fun similar(word: String, maxDistance: Int, limit: Int): List<WordResult> {
        if (limit <= 0 || maxDistance < 0) return emptyList()
        val query = word.lowercase()
        if (query.length > MAX_WORD_LENGTH) return emptyList()
        val sink = CandidateSink(limit)
        val rows = Array(MAX_WORD_LENGTH + 2) { IntArray(query.length + 1) }
        for (i in 0..query.length) rows[0][i] = i
        var e = firstEdge[0]
        while (e >= 0) {
            similarDfs(e, query, rows, maxDistance, 0, sink)
            e = nextEdge[e]
        }
        return sink.toList(words, freqs)
    }

    private fun similarDfs(
        edge: Int,
        query: String,
        rows: Array<IntArray>,
        maxDistance: Int,
        depth: Int,
        sink: CandidateSink,
    ) {
        if (depth >= MAX_WORD_LENGTH) return
        val c = edgeLetter[edge]
        val node = edgeTarget[edge]
        val prevRow = rows[depth]
        val row = rows[depth + 1]
        row[0] = depth + 1
        var rowMin = row[0]
        var j = 1
        while (j <= query.length) {
            val insert = row[j - 1] + 1
            val delete = prevRow[j] + 1
            val replace = prevRow[j - 1] + if (query[j - 1] == c) 0 else 1
            val v = minOf(insert, delete, replace)
            row[j] = v
            if (v < rowMin) rowMin = v
            j++
        }
        if (rowMin > maxDistance) return

        val w = termWord[node]
        if (w >= 0 && row[query.length] <= maxDistance) {
            sink.offer(w, row[query.length], freqs[w])
        }
        if (depth + 1 < MAX_WORD_LENGTH) {
            var e = firstEdge[node]
            while (e >= 0) {
                similarDfs(e, query, rows, maxDistance, depth + 1, sink)
                e = nextEdge[e]
            }
        }
    }

    /**
     * Iterasi indeks kata berdasarkan huruf awal, huruf akhir, dan panjang
     * maksimum. Dipakai glide decoder untuk menyusun kandidat awal.
     */
    fun forEachWordByEnds(first: Char, last: Char, maxLength: Int, action: (Int) -> Unit) {
        if (first < 'a' || first > 'z' || last < 'a' || last > 'z') return
        val bucket = (first - 'a') * 26 + (last - 'a')
        var i = bucketStart[bucket]
        val end = bucketStart[bucket + 1]
        while (i < end) {
            val w = bucketWords[i]
            if (lengths[w] <= maxLength) action(w)
            i++
        }
    }

    fun lengthAt(index: Int): Int = lengths[index]
    fun firstCharAt(index: Int): Char = firstChar[index]
    fun lastCharAt(index: Int): Char = lastChar[index]

    /** Hasil pencarian kata. [distance] 0 berarti persis atau awalan. */
    data class WordResult(val word: String, val frequency: Int, val distance: Int)

    /**
     * Penampung kandidat berukuran tetap: menyimpan [limit] kata terbaik.
     * Perbandingan: jarak edit lebih kecil, lalu frekuensi lebih besar.
     */
    class CandidateSink(private val limit: Int) {
        private val idx = IntArray(limit)
        private val dist = IntArray(limit)
        private val freq = IntArray(limit)
        private var count = 0

        val isFull: Boolean get() = count >= limit
        val minFrequency: Int get() = if (count >= limit) freq[worstIndex()] else 0

        private fun worstIndex(): Int {
            var worst = 0
            for (i in 1 until count) {
                if (dist[i] > dist[worst] || (dist[i] == dist[worst] && freq[i] < freq[worst])) {
                    worst = i
                }
            }
            return worst
        }

        private fun isBetter(distance: Int, frequency: Int, than: Int): Boolean =
            distance < dist[than] || (distance == dist[than] && frequency > freq[than])

        fun offer(index: Int, distance: Int, frequency: Int) {
            if (count < limit) {
                idx[count] = index
                dist[count] = distance
                freq[count] = frequency
                count++
                return
            }
            val worst = worstIndex()
            if (isBetter(distance, frequency, worst)) {
                idx[worst] = index
                dist[worst] = distance
                freq[worst] = frequency
            }
        }

        fun toList(words: Array<String>, freqs: IntArray): List<WordResult> {
            val list = ArrayList<WordResult>(count)
            for (i in 0 until count) {
                list += WordResult(words[idx[i]], freqs[idx[i]], dist[i])
            }
            list.sortWith(compareBy<WordResult> { it.distance }.thenByDescending { it.frequency })
            return list
        }
    }

    companion object {
        const val MAX_WORD_LENGTH = 20

        fun empty(): Dictionary = fromText("")

        fun fromText(text: String): Dictionary {
            val parsed = ArrayList<Pair<String, Int>>(48_000)
            for (line in text.split('\n')) {
                if (line.isEmpty() || line[0] == '#') continue
                val tab = line.indexOf('\t')
                if (tab <= 0) continue
                val w = line.substring(0, tab)
                if (w.length < 2 || w.length > MAX_WORD_LENGTH) continue
                var ok = true
                for (c in w) {
                    if (c < 'a' || c > 'z') { ok = false; break }
                }
                if (!ok) continue
                val f = line.substring(tab + 1).trim().toIntOrNull() ?: continue
                parsed += w to f
            }
            val unique = LinkedHashMap<String, Int>(parsed.size * 2)
            for ((w, f) in parsed) {
                val old = unique[w]
                if (old == null || f > old) unique[w] = f
            }
            val count = unique.size

            val wordArr = arrayOfNulls<String>(count)
            val freqs = IntArray(count)
            val lengths = IntArray(count)
            val firstChar = CharArray(count)
            val lastChar = CharArray(count)
            var i = 0
            for ((w, f) in unique) {
                wordArr[i] = w
                freqs[i] = f
                lengths[i] = w.length
                firstChar[i] = w[0]
                lastChar[i] = w[w.length - 1]
                i++
            }

            // --- Trie dengan adjacency linked-list ---
            val firstEdge = GrowableInt(4096)
            val termWord = GrowableInt(4096)
            val edgeLetter = GrowableChar(4096)
            val edgeTarget = GrowableInt(4096)
            val nextEdge = GrowableInt(4096)
            fun newNode(): Int {
                termWord.add(-1)
                firstEdge.add(-1)
                return termWord.size - 1
            }
            newNode() // root = 0
            for (wi in 0 until count) {
                val w = wordArr[wi]!!
                var node = 0
                for (c in w) {
                    var e = firstEdge[node]
                    var found = -1
                    while (e >= 0) {
                        if (edgeLetter[e] == c) { found = edgeTarget[e]; break }
                        e = nextEdge[e]
                    }
                    if (found < 0) {
                        val target = newNode()
                        edgeLetter.add(c)
                        edgeTarget.add(target)
                        nextEdge.add(firstEdge[node])
                        firstEdge[node] = edgeLetter.size - 1
                        found = target
                    }
                    node = found
                }
                termWord[node] = wi
            }

            // Frekuensi maksimum per subtree (post-order rekursif, kedalaman <= 20).
            val nodeCount = termWord.size
            val bestFreq = IntArray(nodeCount)
            fun computeBest(node: Int): Int {
                val w = termWord[node]
                var best = if (w >= 0) freqs[w] else 0
                var e = firstEdge[node]
                while (e >= 0) {
                    val childBest = computeBest(edgeTarget[e])
                    if (childBest > best) best = childBest
                    e = nextEdge[e]
                }
                bestFreq[node] = best
                return best
            }
            computeBest(0)

            // --- Bucket huruf awal + huruf akhir ---
            val bucketCounts = IntArray(26 * 26 + 1)
            for (wi in 0 until count) {
                bucketCounts[(firstChar[wi] - 'a') * 26 + (lastChar[wi] - 'a') + 1]++
            }
            for (b in 1..26 * 26) bucketCounts[b] += bucketCounts[b - 1]
            val bucketStart = bucketCounts.copyOf()
            val bucketWords = IntArray(count)
            val cursor = bucketCounts.copyOf()
            for (wi in 0 until count) {
                val b = (firstChar[wi] - 'a') * 26 + (lastChar[wi] - 'a')
                bucketWords[cursor[b]++] = wi
            }

            return Dictionary(
                words = Array(count) { wordArr[it]!! },
                freqs = freqs,
                lengths = lengths,
                firstChar = firstChar,
                lastChar = lastChar,
                firstEdge = firstEdge.toArray(),
                termWord = termWord.toArray(),
                bestFreq = bestFreq,
                edgeLetter = edgeLetter.toArray(),
                edgeTarget = edgeTarget.toArray(),
                nextEdge = nextEdge.toArray(),
                bucketStart = bucketStart,
                bucketWords = bucketWords,
            )
        }
    }
}

private class GrowableInt(initial: Int) {
    private var data = IntArray(initial)
    var size = 0
        private set

    fun add(v: Int) {
        if (size == data.size) data = data.copyOf(data.size * 2)
        data[size++] = v
    }

    operator fun get(i: Int): Int = data[i]
    operator fun set(i: Int, v: Int) { data[i] = v }

    fun toArray(): IntArray = data.copyOf(size)
}

private class GrowableChar(initial: Int) {
    private var data = CharArray(initial)
    var size = 0
        private set

    fun add(v: Char) {
        if (size == data.size) data = data.copyOf(data.size * 2)
        data[size++] = v
    }

    operator fun get(i: Int): Char = data[i]

    fun toArray(): CharArray = data.copyOf(size)
}
