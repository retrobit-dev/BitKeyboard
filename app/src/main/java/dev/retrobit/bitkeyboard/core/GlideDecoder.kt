package dev.retrobit.bitkeyboard.core

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.ln

/** Titik pada jalur geser, dalam satuan kolom/baris tombol (bukan piksel). */
data class GlidePoint(val x: Float, val y: Float)

/** Geometri QWERTY acuan yang dipakai decoder. */
object QwertyGeometry {
    const val COLUMNS = 10f
    const val ROWS = 3f

    private val ROW_LETTERS = arrayOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
    private val ROW_OFFSET = floatArrayOf(0f, 0.5f, 1.5f)

    val letters: List<Char> = ROW_LETTERS.flatMap { it.toList() }
    private val positions = HashMap<Char, GlidePoint>(26)

    init {
        for (r in ROW_LETTERS.indices) {
            val row = ROW_LETTERS[r]
            for (i in row.indices) {
                positions[row[i]] = GlidePoint(ROW_OFFSET[r] + i + 0.5f, r + 0.5f)
            }
        }
    }

    fun position(c: Char): GlidePoint? = positions[c]

    fun nearestLetters(p: GlidePoint, count: Int): List<Char> =
        letters.sortedBy { d2(p, positions.getValue(it)) }.take(count)

    fun d2(a: GlidePoint, b: GlidePoint): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return dx * dx + dy * dy
    }

    fun distance(a: GlidePoint, b: GlidePoint): Float = hypot(a.x - b.x, a.y - b.y)
}

/**
 * Decoder gesture typing (glide) berbasis bentuk jalur.
 *
 * Pendekatan: jalur pengguna di-resample berdasarkan panjang busur, lalu
 * dibandingkan dengan "template" tiap kata (garis yang melewati pusat tombol,
 * dengan pemotongan sudut seperti gerakan manusia). Pencocokan memakai DTW
 * (dynamic time warping) sehingga kecepatan jari tidak memengaruhi hasil.
 *
 * Tahapan agar tetap cepat di ponsel:
 *  1. kandidat dibatasi oleh huruf awal/akhir yang paling dekat,
 *  2. prefilter murah (12 titik) menyisakan ~180 kandidat,
 *  3. DTW penuh (24 titik) untuk peringkat akhir + prior frekuensi kata.
 */
class GlideDecoder(private val dictionary: Dictionary) {

    data class Result(
        val word: String,
        val score: Double,
        val frequency: Int,
    )

    private val templateCache = HashMap<Int, FloatArray>()
    private val candidateBuffer = GrowableInts(8192)
    private val preScores = ArrayList<ScoredWord>(2048)
    private val templateScratch = FloatArray(160)

    /**
     * @param points jalur jari minimal 3 titik, sudah dalam satuan tombol.
     */
    fun decode(points: List<GlidePoint>, limit: Int = 6): List<Result> {
        if (limit <= 0 || points.size < 2) return emptyList()
        val user = resample(points, INPUT_POINTS) ?: return emptyList()
        val userX = FloatArray(INPUT_POINTS)
        val userY = FloatArray(INPUT_POINTS)
        for (i in 0 until INPUT_POINTS) {
            userX[i] = user[i * 2]
            userY[i] = user[i * 2 + 1]
        }

        val startCandidates = QwertyGeometry.nearestLetters(GlidePoint(userX[0], userY[0]), EDGE_CANDIDATES)
        val endCandidates = QwertyGeometry.nearestLetters(
            GlidePoint(userX[INPUT_POINTS - 1], userY[INPUT_POINTS - 1]),
            EDGE_CANDIDATES,
        )

        // 1. Kumpulkan kandidat berdasarkan huruf awal & akhir.
        candidateBuffer.clear()
        for (first in startCandidates) {
            for (last in endCandidates) {
                dictionary.forEachWordByEnds(first, last, MAX_WORD_LENGTH) { index ->
                    if (dictionary.lengthAt(index) >= 2) candidateBuffer.add(index)
                }
            }
        }
        if (candidateBuffer.size == 0) return emptyList()

        // 2. Prefilter murah, lalu ambil N terbaik.
        preScores.clear()
        val userPre = FloatArray(PRE_POINTS * 2)
        val stride = (INPUT_POINTS - 1).toFloat() / (PRE_POINTS - 1)
        for (i in 0 until PRE_POINTS) {
            val src = (i * stride).toInt().coerceAtMost(INPUT_POINTS - 1)
            userPre[i * 2] = userX[src]
            userPre[i * 2 + 1] = userY[src]
        }
        var c = 0
        while (c < candidateBuffer.size) {
            val wi = candidateBuffer[c]
            val template = templateFor(wi)
            if (template != null) {
                val pre = prefilter(userPre, template)
                val freq = dictionary.frequencyAt(wi)
                preScores.add(ScoredWord(wi, pre - FREQ_ALPHA * ln(freq + 1.0)))
            }
            c++
        }
        if (preScores.isEmpty()) return emptyList()
        preScores.sortBy { it.score }
        val keep = minOf(PREFILTER_KEEP, preScores.size)

        // 3. DTW penuh untuk kandidat teratas.
        val results = ArrayList<Result>(keep)
        for (i in 0 until keep) {
            val sc = preScores[i]
            val wi = sc.index
            val template = templateFor(wi) ?: continue
            if (template.size < TEMPLATE_POINTS * 2) continue
            val dtw = dtw(userX, userY, template)
            val first = QwertyGeometry.position(dictionary.firstCharAt(wi)) ?: continue
            val last = QwertyGeometry.position(dictionary.lastCharAt(wi)) ?: continue
            val edgeDistance = QwertyGeometry.distance(GlidePoint(userX[0], userY[0]), first) +
                QwertyGeometry.distance(
                    GlidePoint(userX[INPUT_POINTS - 1], userY[INPUT_POINTS - 1]),
                    last,
                )
            val freq = dictionary.frequencyAt(wi)
            val score = dtw + EDGE_WEIGHT * edgeDistance - FREQ_ALPHA * ln(freq + 1.0)
            results.add(Result(dictionary.wordAt(wi), score, freq))
        }
        results.sortBy { it.score }
        return results.take(limit)
    }

    fun decode(pointsX: FloatArray, pointsY: FloatArray, limit: Int = 6): List<Result> {
        val n = minOf(pointsX.size, pointsY.size)
        val list = ArrayList<GlidePoint>(n)
        for (i in 0 until n) list.add(GlidePoint(pointsX[i], pointsY[i]))
        return decode(list, limit)
    }

    // ------------------------------------------------------------------ //

    private fun templateFor(wordIndex: Int): FloatArray? {
        templateCache[wordIndex]?.let { return it }
        val word = dictionary.wordAt(wordIndex)
        if (word.isEmpty()) return null
        var prev: GlidePoint? = null
        for (c in word) {
            var p = QwertyGeometry.position(c) ?: continue
            // Huruf ganda: beri sedikit simpangan agar jalur tidak degenerat.
            val a = prev
            if (a != null && abs(p.x - a.x) < 0.01f && abs(p.y - a.y) < 0.01f) {
                p = GlidePoint(p.x + 0.22f, p.y + 0.16f)
            }
            if (a != null) {
                val mid = GlidePoint((a.x + p.x) / 2f, (a.y + p.y) / 2f)
                addScratch(a.x + (mid.x - a.x) * CORNER_CUT, a.y + (mid.y - a.y) * CORNER_CUT)
                addScratch(p.x + (mid.x - p.x) * CORNER_CUT, p.y + (mid.y - p.y) * CORNER_CUT)
            } else {
                addScratch(p.x, p.y)
            }
            prev = p
        }
        prev?.let { addScratch(it.x, it.y) }
        val template = resampleScratch(TEMPLATE_POINTS) ?: return null
        if (templateCache.size > 8000) templateCache.clear()
        templateCache[wordIndex] = template
        return template
    }

    private var scratchSize = 0

    private fun addScratch(x: Float, y: Float) {
        if (scratchSize * 2 + 1 >= templateScratch.size) return
        templateScratch[scratchSize * 2] = x
        templateScratch[scratchSize * 2 + 1] = y
        scratchSize++
    }

    private fun resampleScratch(points: Int): FloatArray? {
        if (scratchSize < 2) {
            if (scratchSize == 1) {
                val out = FloatArray(points * 2)
                for (i in 0 until points) {
                    out[i * 2] = templateScratch[0]
                    out[i * 2 + 1] = templateScratch[1]
                }
                scratchSize = 0
                return out
            }
            scratchSize = 0
            return null
        }
        val out = resampleRaw(templateScratch, scratchSize, points)
        scratchSize = 0
        return out
    }

    private fun prefilter(user: FloatArray, template: FloatArray): Double {
        val n = user.size / 2
        val userLen = arcLength(user, n)
        val tmplLen = arcLength(template, template.size / 2)
        val scale = if (tmplLen > 1e-4f) (userLen / tmplLen).coerceIn(0.7f, 1.4f) else 1f
        val t0x = template[0]
        val t0y = template[1]
        val u0x = user[0]
        val u0y = user[1]
        var sum = 0.0
        val m = template.size / 2
        val stride = (m - 1).toFloat() / (n - 1)
        var i = 0
        while (i < n) {
            val ti = (i * stride).toInt().coerceAtMost(m - 1)
            val ux = user[i * 2]
            val uy = user[i * 2 + 1]
            val tx = t0x + (template[ti * 2] - t0x) * scale
            val ty = t0y + (template[ti * 2 + 1] - t0y) * scale
            val dx = (ux - u0x) - (tx - t0x)
            val dy = (uy - u0y) - (ty - t0y)
            sum += (dx * dx + dy * dy).toDouble()
            i++
        }
        return sum / n
    }

    private fun dtw(userX: FloatArray, userY: FloatArray, template: FloatArray): Double {
        val n = userX.size
        val m = template.size / 2
        var prev = FloatArray(m + 1) { Float.POSITIVE_INFINITY }
        var cur = FloatArray(m + 1) { Float.POSITIVE_INFINITY }
        prev[0] = 0f
        for (i in 1..n) {
            var clearIdx = 0
            while (clearIdx <= m) {
                cur[clearIdx] = Float.POSITIVE_INFINITY
                clearIdx++
            }
            val lo = maxOf(1, i - BAND)
            val hi = minOf(m, i + BAND)
            val ux = userX[i - 1]
            val uy = userY[i - 1]
            var j = lo
            while (j <= hi) {
                val dx = ux - template[(j - 1) * 2]
                val dy = uy - template[(j - 1) * 2 + 1]
                val cost = dx * dx + dy * dy
                var best = prev[j]
                if (prev[j - 1] < best) best = prev[j - 1]
                if (cur[j - 1] < best) best = cur[j - 1]
                cur[j] = cost + best
                j++
            }
            val tmp = prev
            prev = cur
            cur = tmp
        }
        return (prev[m] / (n + m).toFloat()).toDouble()
    }

    private data class ScoredWord(val index: Int, val score: Double)

    private class GrowableInts(initial: Int) {
        private var data = IntArray(initial)
        var size = 0
            private set
        fun clear() { size = 0 }
        fun add(v: Int) {
            if (size == data.size) data = data.copyOf(data.size * 2)
            data[size++] = v
        }
        operator fun get(i: Int): Int = data[i]
    }

    companion object {
        const val INPUT_POINTS = 24
        const val TEMPLATE_POINTS = 24
        const val PRE_POINTS = 12
        const val EDGE_CANDIDATES = 3
        const val PREFILTER_KEEP = 180
        const val MAX_WORD_LENGTH = 16
        const val BAND = 5
        const val CORNER_CUT = 0.4f
        const val FREQ_ALPHA = 0.02
        const val EDGE_WEIGHT = 0.06

        /** Resample polyline (x,y interleaved) ke [count] titik berdasarkan panjang busur. */
        fun resampleRaw(points: FloatArray, pointCount: Int, count: Int): FloatArray {
            if (pointCount <= 0) return FloatArray(0)
            val out = FloatArray(count * 2)
            if (pointCount == 1) {
                for (i in 0 until count) {
                    out[i * 2] = points[0]
                    out[i * 2 + 1] = points[1]
                }
                return out
            }
            var total = 0.0
            for (i in 1 until pointCount) {
                total += hypot(
                    (points[i * 2] - points[i * 2 - 2]).toDouble(),
                    (points[i * 2 + 1] - points[i * 2 - 1]).toDouble(),
                )
            }
            if (total < 1e-6) {
                for (i in 0 until count) {
                    out[i * 2] = points[0]
                    out[i * 2 + 1] = points[1]
                }
                return out
            }
            var seg = 1
            var segStart = 0.0
            var segLen = hypot(
                (points[2] - points[0]).toDouble(),
                (points[3] - points[1]).toDouble(),
            )
            var k = 0
            while (k < count) {
                val target = total * k / (count - 1)
                while (seg < pointCount - 1 && segStart + segLen < target) {
                    segStart += segLen
                    seg++
                    segLen = hypot(
                        (points[seg * 2] - points[seg * 2 - 2]).toDouble(),
                        (points[seg * 2 + 1] - points[seg * 2 - 1]).toDouble(),
                    )
                }
                val t = if (segLen > 1e-9) ((target - segStart) / segLen).coerceIn(0.0, 1.0) else 0.0
                val ax = points[(seg - 1) * 2]
                val ay = points[(seg - 1) * 2 + 1]
                val bx = points[seg * 2]
                val by = points[seg * 2 + 1]
                out[k * 2] = (ax + (bx - ax) * t).toFloat()
                out[k * 2 + 1] = (ay + (by - ay) * t).toFloat()
                k++
            }
            return out
        }

        private fun resample(points: List<GlidePoint>, count: Int): FloatArray? {
            if (points.isEmpty()) return null
            if (points.size == 1) return null
            val flat = FloatArray(points.size * 2)
            for (i in points.indices) {
                flat[i * 2] = points[i].x
                flat[i * 2 + 1] = points[i].y
            }
            return resampleRaw(flat, points.size, count)
        }

        private fun arcLength(points: FloatArray, count: Int): Float {
            if (count < 2) return 0f
            var total = 0.0
            for (i in 1 until count) {
                total += hypot(
                    (points[i * 2] - points[i * 2 - 2]).toDouble(),
                    (points[i * 2 + 1] - points[i * 2 - 1]).toDouble(),
                )
            }
            return total.toFloat()
        }
    }
}
