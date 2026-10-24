import dev.retrobit.bitkeyboard.core.BigramModel
import dev.retrobit.bitkeyboard.core.Dictionary
import dev.retrobit.bitkeyboard.core.GlideDecoder
import dev.retrobit.bitkeyboard.core.GlidePoint
import dev.retrobit.bitkeyboard.core.InMemoryUserModel
import dev.retrobit.bitkeyboard.core.Layer
import dev.retrobit.bitkeyboard.core.Layouts
import dev.retrobit.bitkeyboard.core.QwertyGeometry
import dev.retrobit.bitkeyboard.core.Suggester
import java.io.File
import kotlin.math.hypot
import kotlin.random.Random
import kotlin.system.measureTimeMillis

private var failures = 0
private var checks = 0

private fun check(condition: Boolean, message: String) {
    checks++
    if (!condition) {
        failures++
        println("  GAGAL: $message")
    }
}

private fun checkEq(actual: Any?, expected: Any?, message: String) {
    check(actual == expected, "$message (dapat=$actual, harap=$expected)")
}

fun main() {
    val dictFile = File("app/src/main/assets/dictionary_id.txt")
    val bigramFile = File("app/src/main/assets/bigrams_id.txt")
    check(dictFile.exists(), "aset kamus ditemukan")
    val dictText = dictFile.readText()
    var dict: Dictionary? = null
    val loadMs = measureTimeMillis { dict = Dictionary.fromText(dictText) }
    val d = dict!!
    println("Kamus: ${d.size} kata, dimuat ${loadMs} ms, ${dictText.length / 1024} KB")
    check(d.size > 30_000, "jumlah kata > 30.000")

    // ------------------------- isi kamus -------------------------
    for (w in listOf("aku", "kamu", "yang", "pembelajaran", "menggunakan", "kesehatan", "keadaan", "di", "ke", "terima", "kasih", "algoritma", "kalkulus", "matriks", "integral", "fotosintesis", "gravitasi")) {
        check(d.contains(w), "kamus memuat '$w'")
    }
    check(!d.contains("the"), "kata Inggris 'the' tidak ada")
    check(!d.contains("charlie"), "nama tokoh 'charlie' tidak ada")

    // ------------------------- saran kata -------------------------
    val bigrams = BigramModel.fromText(bigramFile.readText())
    val user = InMemoryUserModel()
    val suggester = Suggester(d, bigrams, user)

    val completions = d.completions("pembel", 5)
    check(completions.any { it.word == "pembelajaran" }, "pelengkapan 'pembel' -> pembelajaran (${completions.map { it.word }})")

    val similar = d.similar("pemeblajaran", 2, 5)
    check(similar.any { it.word == "pembelajaran" }, "koreksi 'pemeblajaran' -> pembelajaran (${similar.map { it.word }})")

    checkEq(suggester.autocorrect("pemeblajaran"), "pembelajaran", "autocorrect typo transposisi")
    checkEq(suggester.autocorrect("saya"), null, "kata benar tidak dikoreksi")
    checkEq(suggester.autocorrect("Pemeblajaran"), "Pembelajaran", "autocorrect menjaga huruf kapital")
    checkEq(suggester.autocorrect("ak"), null, "kata pendek tidak dikoreksi")
    checkEq(suggester.autocorrect("zzzz"), null, "kata tak dikenal tanpa kandidat")

    for (prefix in listOf("mak", "bel", "pem", "sekol", "mat")) {
        val s = suggester.onWord(prefix, "", 3)
        check(s.isNotEmpty(), "saran untuk awalan '$prefix' tidak kosong")
        check(s.all { it.word.lowercase().startsWith(prefix.lowercase()) || it.kind == Suggester.Kind.CORRECTION },
            "saran '$prefix' relevan (${s.map { it.word }})")
        println("  '$prefix' -> ${s.map { it.word }}")
    }

    val next = suggester.nextWords("terima", 3)
    check(next.any { it.word == "kasih" }, "prediksi 'terima' -> kasih (${next.map { it.word }})")
    check(suggester.autocorrect("mnggunakan") == "menggunakan", "autocorrect satu huruf hilang")

    // pelajari kata baru
    user.learn("bitkeyboard")
    check(suggester.isKnown("bitkeyboard"), "kata pengguna dikenali")
    user.observeBigram("bit", "keyboard")
    check(user.topNextWords("bit", 3).any { it.first == "keyboard" }, "bigram pengguna tersimpan")

    // ------------------------- layout -------------------------
    val layouts = listOf(
        Layouts.letters(numberRow = false, functionRow = false),
        Layouts.letters(numberRow = true, functionRow = true),
        Layouts.symbols(),
        Layouts.symbolsAlt(),
        Layouts.scientific(),
        Layouts.scientificAlt(),
    )
    for (layout in layouts) {
        for ((i, row) in layout.rows.withIndex()) {
            val w = row.totalWidth
            check(w <= layout.columns + 0.001f, "lebar baris ${layout.layer}/$i = $w <= ${layout.columns}")
            check(w > layout.columns - 1.2f, "baris ${layout.layer}/$i tidak terlalu sempit ($w)")
        }
    }
    check(Layouts.letters(false, false).letterRowRange.first == 0, "letterRowRange mulai 0")
    check(Layouts.letters(false, false).letterRowRange.last == 2, "letterRowRange berakhir 2")
    check(Layouts.letters(true, true).letterRowRange.first == 2, "letterRowRange dengan baris tambahan")

    // ------------------------- geometri -------------------------
    checkEq(QwertyGeometry.nearestLetters(GlidePoint(0.5f, 0.5f), 1).first(), 'q', "nearest q")
    checkEq(QwertyGeometry.nearestLetters(GlidePoint(5.0f, 1.5f), 1).first(), 'g', "nearest g")

    // ------------------------- glide -------------------------
    val decoder = GlideDecoder(d)
    val topWords = (0 until 600).map { d.wordAt(it) }.filter { it.length >= 3 }
    val rand = Random(42)
    var top1 = 0
    var top5 = 0
    var total = 0
    var decodeMs = 0L
    val failuresList = ArrayList<Pair<String, List<String>>>()
    for (w in topWords) {
        val points = syntheticPath(w, rand, jitter = 0.15f, cornerCut = 0.5f)
        var results: List<GlideDecoder.Result> = emptyList()
        decodeMs += measureTimeMillis { results = decoder.decode(points, 5) }
        total++
        if (results.firstOrNull()?.word == w) top1++ else {
            if (results.any { it.word == w }) top5++
            if (failuresList.size < 8) failuresList += w to results.take(3).map { it.word }
        }
    }
    val acc1 = top1 * 100.0 / total
    val acc5 = (top1 + top5) * 100.0 / total
    println("Glide: top1=%.1f%% top5=%.1f%% (%d kata, rata-rata %.2f ms)".format(acc1, acc5, total, decodeMs.toDouble() / total))
    if (failuresList.isNotEmpty()) println("  contoh gagal: $failuresList")
    check(acc1 >= 85.0, "akurasi glide top-1 >= 85%")
    check(acc5 >= 97.0, "akurasi glide top-5 >= 97%")

    // glid dengan jalur "lurus" tanpa jitter juga harus tinggi
    var straight1 = 0
    for (w in topWords.take(200)) {
        val r = decoder.decode(syntheticPath(w, Random(1), jitter = 0.02f, cornerCut = 0.35f), 1)
        if (r.firstOrNull()?.word == w) straight1++
    }
    val straightAcc = straight1 * 100.0 / 200
    println("Glide tanpa noise: top1=%.1f%%".format(straightAcc))
    check(straightAcc >= 93.0, "akurasi glide jalur bersih >= 93%")

    // ------------------------- hasil -------------------------
    println()
    if (failures == 0) {
        println("SEMUA $checks PEMERIKSAAN LULUS")
    } else {
        println("$failures dari $checks pemeriksaan GAGAL")
        kotlin.system.exitProcess(1)
    }
}

/** Meniru gerakan jari manusia: memotong sudut dan sedikit gemetar. */
private fun syntheticPath(word: String, rand: Random, jitter: Float, cornerCut: Float): List<GlidePoint> {
    val centers = word.mapNotNull { QwertyGeometry.position(it) }
    if (centers.isEmpty()) return emptyList()
    val flat = ArrayList<Float>()
    flat += centers[0].x
    flat += centers[0].y
    for (i in 1 until centers.size) {
        val a = centers[i - 1]
        val b = centers[i]
        val mx = (a.x + b.x) / 2f
        val my = (a.y + b.y) / 2f
        flat += a.x + (mx - a.x) * cornerCut
        flat += a.y + (my - a.y) * cornerCut
        flat += b.x + (mx - b.x) * cornerCut
        flat += b.y + (my - b.y) * cornerCut
    }
    flat += centers.last().x
    flat += centers.last().y
    val resampled = GlideDecoder.resampleRaw(flat.toFloatArray(), flat.size / 2, 28)
    val points = ArrayList<GlidePoint>(28)
    for (i in 0 until 28) {
        val jx = (rand.nextFloat() * 2f - 1f) * jitter
        val jy = (rand.nextFloat() * 2f - 1f) * jitter
        points += GlidePoint(resampled[i * 2] + jx, resampled[i * 2 + 1] + jy)
    }
    return points
}
