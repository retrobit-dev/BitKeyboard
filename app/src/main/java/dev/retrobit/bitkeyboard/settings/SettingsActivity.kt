package dev.retrobit.bitkeyboard.settings

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import dev.retrobit.bitkeyboard.ime.KeyboardTheme

/**
 * Pengaturan BitKeyboard. Dibangun sepenuhnya dengan kode agar tidak
 * membutuhkan pustaka tambahan dan tetap ringan.
 *
 * Semua yang memengaruhi keyboard bisa diatur di sini: perilaku mengetik,
 * ukuran dan bentuk tombol, kustomisasi karakter sudut per tombol, tema dan
 * warna aksen, getaran, dikte suara, kamus pribadi, sampai cadangan pengaturan.
 * Perubahan langsung dipakai keyboard yang sedang terbuka.
 */
class SettingsActivity : Activity() {

    private lateinit var settings: SettingsStore
    private lateinit var statusText: TextView
    private lateinit var activateButton: Button
    private lateinit var preview: LinearLayout
    private lateinit var previewCaption: TextView

    private val darkTheme: Boolean by lazy {
        when (settings.theme) {
            "light" -> false
            "dark", "amoled" -> true
            else -> (resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
        }
    }

    private val colorBg: Int get() = if (darkTheme) 0xFF0F141A.toInt() else 0xFFF2F5F9.toInt()
    private val colorCard: Int get() = if (darkTheme) 0xFF171E26.toInt() else Color.WHITE
    private val colorText: Int get() = if (darkTheme) 0xFFEAF1FB.toInt() else 0xFF16202C.toInt()
    private val colorSecondary: Int get() = if (darkTheme) 0xFF94A6BA.toInt() else 0xFF5E7086.toInt()
    private val colorAccent: Int get() = KeyboardTheme.parseColor(settings.accentColor) ?: 0xFF2D6CDF.toInt()
    private val colorDivider: Int get() = if (darkTheme) 0xFF263140.toInt() else 0xFFDDE4EE.toInt()
    private val colorKey: Int get() = if (darkTheme) 0xFF232C36.toInt() else 0xFFFFFFFF.toInt()
    private val colorKeyText: Int get() = if (darkTheme) 0xFFEAF1FB.toInt() else 0xFF16202C.toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = SettingsStore(this)
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(colorBg))
        setContentView(buildUi())
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    // ------------------------------------------------------------------ //
    // Kerangka
    // ------------------------------------------------------------------ //
    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(colorBg)
        }
        root.addView(buildHeader())

        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(28))
        }
        scroll.addView(content)

        content.addView(buildStatusCard())
        content.addView(sectionTitle("Mengetik"))
        content.addView(buildTypingCard())
        content.addView(sectionTitle("Tombol & tata letak"))
        content.addView(buildLayoutCard())
        content.addView(sectionTitle("Tampilan"))
        content.addView(buildAppearanceCard())
        content.addView(sectionTitle("Suara & getaran"))
        content.addView(buildFeedbackCard())
        content.addView(sectionTitle("Dikte suara"))
        content.addView(buildVoiceCard())
        content.addView(sectionTitle("Kamus pribadi"))
        content.addView(buildDictionaryCard())
        content.addView(sectionTitle("Emoji"))
        content.addView(buildEmojiCard())
        content.addView(sectionTitle("Cadangan & reset"))
        content.addView(buildBackupCard())
        content.addView(sectionTitle("Bantuan"))
        content.addView(buildHelpCard())
        content.addView(sectionTitle("Tentang"))
        content.addView(buildAboutCard())

        root.addView(
            scroll,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
        )
        return root
    }

    private fun buildHeader(): View {
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(0xFF0F172A.toInt())
            setPadding(dp(8), dp(10), dp(16), dp(10))
        }
        header.addView(TextView(this).apply {
            text = "‹"
            setTextColor(Color.WHITE)
            textSize = 30f
            gravity = Gravity.CENTER
            setPadding(dp(10), 0, dp(10), dp(4))
            setOnClickListener { finish() }
        })
        val titles = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), 0, 0, 0)
        }
        titles.addView(TextView(this).apply {
            text = "BitKeyboard"
            setTextColor(Color.WHITE)
            textSize = 20f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        titles.addView(TextView(this).apply {
            text = "Semua pengaturan ada di sini"
            setTextColor(0xFF94A6BA.toInt())
            textSize = 11.5f
        })
        header.addView(titles, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(TextView(this).apply {
            text = "v${appVersion()}"
            setTextColor(0xFF94A6BA.toInt())
            textSize = 12f
        })
        return header
    }

    // ------------------------------------------------------------------ //
    // Status
    // ------------------------------------------------------------------ //
    private fun buildStatusCard(): View {
        val card = card()
        statusText = TextView(this).apply {
            setTextColor(colorText)
            textSize = 14.5f
            setLineSpacing(dp(3).toFloat(), 1f)
        }
        card.addView(statusText)
        activateButton = primaryButton("Aktifkan BitKeyboard") {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            toast("Pilih BitKeyboard lalu aktifkan")
        }
        card.addView(activateButton)
        card.addView(
            outlineButton("Pilih keyboard sekarang") {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showInputMethodPicker()
            },
        )
        return card
    }

    private fun updateStatus() {
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_INPUT_METHODS)
            ?.contains(packageName) == true
        val active = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.startsWith(packageName) == true
        statusText.text = when {
            active -> "BitKeyboard sedang aktif. Kamu bisa langsung mengetik di aplikasi mana pun."
            enabled -> "BitKeyboard sudah diaktifkan, tetapi belum dipilih. Buka pemilih keyboard lalu pilih BitKeyboard."
            else -> "BitKeyboard belum diaktifkan. Aktifkan dulu di pengaturan sistem, lalu pilih sebagai keyboard."
        }
        activateButton.text = if (enabled) "Buka pengaturan keyboard" else "Aktifkan BitKeyboard"
    }

    // ------------------------------------------------------------------ //
    // Mengetik
    // ------------------------------------------------------------------ //
    private fun buildTypingCard(): View {
        val card = card()
        card.addRow(
            switchRow(
                "Glide typing",
                "Geser jari dari huruf ke huruf untuk mengetik satu kata",
                settings.glideEnabled,
            ) { settings.glideEnabled = it },
        )
        card.addDivider()
        card.addRow(
            switchRow(
                "Tampilkan jejak geser",
                "Garis biru mengikuti jalur jari saat glide",
                settings.glideTrail,
            ) { settings.glideTrail = it; refreshPreview() },
        )
        card.addDivider()
        card.addRow(
            switchRow(
                "Saran kata",
                "Pelengkapan, koreksi ejaan, dan prediksi di bilah atas",
                settings.suggestionsEnabled,
            ) { settings.suggestionsEnabled = it },
        )
        card.addDivider()
        card.addRow(
            sliderRow(
                "Jumlah saran",
                "Berapa kata yang ditampilkan di bilah saran",
                1, 6, settings.suggestionCount,
            ) { value ->
                settings.suggestionCount = value
                "${value} kata"
            },
        )
        card.addDivider()
        card.addRow(
            switchRow(
                "Bilah saran",
                "Sembunyikan untuk keyboard yang lebih ringkas",
                settings.showSuggestionBar,
            ) { settings.showSuggestionBar = it },
        )
        card.addDivider()
        card.addRow(
            switchRow(
                "Autocorrect",
                "Perbaiki ejaan otomatis saat spasi ditekan",
                settings.autocorrectEnabled,
            ) { settings.autocorrectEnabled = it },
        )
        card.addDivider()
        card.addRow(
            switchRow(
                "Prediksi kata berikutnya",
                "Saran berdasarkan kata yang baru diketik",
                settings.nextWordPrediction,
            ) { settings.nextWordPrediction = it },
        )
        card.addDivider()
        card.addRow(
            switchRow(
                "Kapital otomatis",
                "Huruf besar di awal kalimat",
                settings.autoCapitalize,
            ) { settings.autoCapitalize = it },
        )
        card.addDivider()
        card.addRow(
            switchRow(
                "Spasi ganda menjadi titik",
                "Tekan spasi dua kali untuk mengakhiri kalimat",
                settings.doubleSpacePeriod,
            ) { settings.doubleSpacePeriod = it },
        )
        card.addDivider()
        card.addRow(
            switchRow(
                "Spasi setelah memilih saran",
                "Tambahkan spasi otomatis setelah kata dari bilah saran",
                settings.addSpaceAfterSuggestion,
            ) { settings.addSpaceAfterSuggestion = it },
        )
        card.addDivider()
        card.addRow(
            switchRow(
                "Backspace menghapus kata hasil glide",
                "Sekali tekan menghapus seluruh kata yang baru digeser",
                settings.deleteWordOnBackspaceAfterGlide,
            ) { settings.deleteWordOnBackspaceAfterGlide = it },
        )
        card.addDivider()
        card.addRow(
            switchRow(
                "Geser spasi untuk pindah kursor",
                "Geser kiri/kanan di tombol spasi memindahkan kursor",
                settings.swipeCursorOnSpace,
            ) { settings.swipeCursorOnSpace = it },
        )
        card.addDivider()
        card.addRow(
            sliderRow(
                "Lama tekan popup",
                "Berapa lama menahan tombol sebelum pilihan aksen muncul",
                200, 900, settings.longPressMs,
            ) { value ->
                settings.longPressMs = value
                "$value ms"
            },
        )
        return card
    }

    // ------------------------------------------------------------------ //
    // Tombol & tata letak
    // ------------------------------------------------------------------ //
    private fun buildLayoutCard(): View {
        val card = card()
        card.addRow(
            switchRow(
                "Baris angka",
                "Tambahkan baris 1–0 di atas huruf",
                settings.numberRow,
            ) { settings.numberRow = it; refreshPreview() },
        )
        card.addDivider()
        card.addRow(
            switchRow(
                "Baris fungsi",
                "Esc, Tab, Ctrl, Alt, panah, Home untuk terminal dan editor",
                settings.functionRow,
            ) { settings.functionRow = it; refreshPreview() },
        )
        card.addDivider()
        card.addRow(
            switchRow(
                "Karakter sudut tombol",
                "Geser ke sudut untuk mengetik simbol tanpa ganti layer",
                settings.cornerSymbols,
            ) { settings.cornerSymbols = it; refreshPreview() },
        )
        card.addDivider()
        card.addRow(
            sliderRow(
                "Tinggi tombol",
                "Tinggi tiap baris tombol",
                36, 76, settings.keyHeightDp,
            ) { value ->
                settings.keyHeightDp = value
                "$value dp"
            },
        )
        card.addDivider()
        card.addRow(
            sliderRow(
                "Tinggi bilah saran",
                "Berlaku bila bilah saran ditampilkan",
                30, 60, settings.suggestionBarHeightDp,
            ) { value ->
                settings.suggestionBarHeightDp = value
                "$value dp"
            },
        )
        card.addDivider()
        card.addRow(
            sliderRow(
                "Jarak antar tombol",
                "Celah di antara tombol",
                0, 8, settings.keyGapDp,
            ) { value ->
                settings.keyGapDp = value
                "$value dp"
            },
        )
        card.addDivider()
        card.addRow(
            sliderRow(
                "Sudut lengkung tombol",
                "0 = kotak, 16 = sangat bulat",
                0, 16, settings.keyRadiusDp,
            ) { value ->
                settings.keyRadiusDp = value
                "$value dp"
            },
        )
        card.addDivider()
        card.addRow(
            sliderRow(
                "Ukuran huruf tombol",
                "Perbesar atau perkecil label tombol",
                80, 130, settings.keyFontPercent,
            ) { value ->
                settings.keyFontPercent = value
                "$value%"
            },
        )
        card.addDivider()
        card.addRow(
            switchRow(
                "Pratinjau tombol saat ditekan",
                "Kotak besar berisi karakter di atas jari",
                settings.keyPreview,
            ) { settings.keyPreview = it },
        )
        card.addDivider()
        card.addRow(
            actionRow(
                "Karakter tiap tombol",
                "Ubah sendiri empat sudut dan daftar tekan-lama",
            ) {
                startActivity(Intent(this, KeyEditorActivity::class.java))
            },
        )
        card.addDivider()
        card.addRow(
            actionRow(
                "Kembalikan tombol ke bawaan",
                "Hapus semua perubahan karakter sudut dan tekan-lama",
            ) {
                confirm(
                    "Kembalikan semua tombol?",
                    "Karakter sudut dan tekan-lama kembali ke bawaan pabrik.",
                ) {
                    settings.resetKeyOverrides()
                    toast("Tombol dikembalikan ke bawaan")
                }
            },
        )
        return card
    }

    // ------------------------------------------------------------------ //
    // Tampilan
    // ------------------------------------------------------------------ //
    private fun buildAppearanceCard(): View {
        val card = card()
        card.addRow(
            choiceRow(
                "Tema keyboard",
                "Warna dasar tombol dan latar",
                listOf("Ikuti sistem", "Terang", "Gelap", "AMOLED"),
                listOf("system", "light", "dark", "amoled"),
                settings.theme,
            ) { value ->
                settings.theme = value
                recreate()
            },
        )
        card.addDivider()
        card.addRow(accentRow())
        card.addDivider()
        previewCaption = TextView(this).apply {
            text = "Pratinjau"
            setTextColor(colorSecondary)
            textSize = 12f
            setPadding(0, dp(12), 0, dp(6))
        }
        card.addView(previewCaption)
        preview = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, dp(8))
        }
        card.addView(preview)
        refreshPreview()
        return card
    }

    private fun accentRow(): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(12), 0, dp(12))
        }
        container.addView(TextView(this).apply {
            text = "Warna aksen"
            setTextColor(colorText)
            textSize = 16f
        })
        container.addView(TextView(this).apply {
            text = "Tombol aksi, kursor saran, dan jejak glide"
            setTextColor(colorSecondary)
            textSize = 12f
            setPadding(0, dp(2), 0, dp(8))
        })
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val current = settings.accentColor
        val choices = ArrayList<Pair<String, Int?>>()
        choices.add("Bawaan" to null)
        for ((name, color) in KeyboardTheme.ACCENT_CHOICES) choices.add(name to color)

        for ((name, color) in choices) {
            val selected = if (color == null) {
                current.isEmpty()
            } else {
                current.equals(String.format("#%06X", 0xFFFFFF and color), ignoreCase = true)
            }
            val chip = TextView(this).apply {
                text = ""
                background = GradientDrawable().apply {
                    cornerRadius = dp(15).toFloat()
                    setColor(color ?: if (darkTheme) 0xFF3B4A5C.toInt() else 0xFFCBD5E1.toInt())
                    if (selected) setStroke(dp(3), colorAccent) else setStroke(dp(1), colorDivider)
                }
                setOnClickListener {
                    settings.accentColor = if (color == null) "" else String.format("#%06X", 0xFFFFFF and color)
                    recreate()
                }
            }
            row.addView(
                chip,
                LinearLayout.LayoutParams(dp(30), dp(30)).apply {
                    rightMargin = dp(7)
                    topMargin = dp(2)
                    bottomMargin = dp(2)
                },
            )
        }
        container.addView(row)
        return container
    }

    private fun refreshPreview() {
        if (!::preview.isInitialized) return
        preview.removeAllViews()
        val radius = settings.keyRadiusDp
        val font = settings.keyFontPercent / 100f
        val samples = listOf("a", "s", "d", "f", "␣", "⌫")
        val cornerHint = settings.cornerSymbols
        for ((index, label) in samples.withIndex()) {
            val isSpace = label == "␣"
            val isBackspace = label == "⌫"
            val key = TextView(this).apply {
                text = if (isSpace) "spasi" else label
                gravity = Gravity.CENTER
                setTextColor(if (isSpace || isBackspace) colorSecondary else colorKeyText)
                textSize = (if (isSpace || isBackspace) 10f else 17f) * font
                background = GradientDrawable().apply {
                    cornerRadius = dp(radius).toFloat()
                    setColor(
                        when {
                            isSpace -> if (darkTheme) 0xFF2B3642.toInt() else 0xFFF1F5FB.toInt()
                            isBackspace -> if (darkTheme) 0xFF1A222B.toInt() else 0xFFD3DDEA.toInt()
                            else -> colorKey
                        },
                    )
                }
            }
            val widthUnits = when {
                isSpace -> 1.9f
                isBackspace -> 1.2f
                else -> 1f
            }
            preview.addView(
                key,
                LinearLayout.LayoutParams(0, dp(settings.keyHeightDp.coerceIn(36, 56))).apply {
                    weight = widthUnits
                    rightMargin = dp(settings.keyGapDp + 1)
                },
            )
            if (cornerHint && !isSpace && !isBackspace && index == 0) {
                key.text = "a"
            }
        }
        previewCaption.text = "Pratinjau · tinggi ${settings.keyHeightDp} dp · lengkung ${settings.keyRadiusDp} dp · " +
            "jarak ${settings.keyGapDp} dp · huruf ${settings.keyFontPercent}%"
    }

    // ------------------------------------------------------------------ //
    // Suara & getaran
    // ------------------------------------------------------------------ //
    private fun buildFeedbackCard(): View {
        val card = card()
        card.addRow(
            switchRow(
                "Suara klik",
                "Bunyi halus saat tombol ditekan",
                settings.soundEnabled,
            ) { settings.soundEnabled = it },
        )
        card.addDivider()
        card.addRow(
            switchRow(
                "Getaran",
                "Umpan balik haptik saat mengetik",
                settings.vibrateEnabled,
            ) { settings.vibrateEnabled = it },
        )
        card.addDivider()
        card.addRow(
            sliderRow(
                "Kekuatan getaran",
                "1 halus, 2 sedang, 3 kuat",
                1, 3, settings.hapticStrength,
            ) { value ->
                settings.hapticStrength = value
                when (value) {
                    1 -> "halus"
                    2 -> "sedang"
                    else -> "kuat"
                }
            },
        )
        return card
    }

    // ------------------------------------------------------------------ //
    // Dikte suara
    // ------------------------------------------------------------------ //
    private fun buildVoiceCard(): View {
        val card = card()
        card.addRow(
            choiceRow(
                "Bahasa pengenalan suara",
                "Dipakai saat tombol mikrofon ditekan",
                listOf("Indonesia (id-ID)", "English (en-US)", "Ikuti sistem"),
                listOf("id-ID", "en-US", ""),
                settings.voiceLanguage,
            ) { settings.voiceLanguage = it },
        )
        card.addDivider()
        card.addRow(
            switchRow(
                "Spasi otomatis setelah dikte",
                "Tambahkan spasi di akhir teks hasil suara",
                settings.voiceAutoSpace,
            ) { settings.voiceAutoSpace = it },
        )
        card.addDivider()
        card.addRow(
            actionRow(
                "Coba dikte suara",
                "Buka panel mikrofon sekarang",
            ) {
                startVoiceTest()
            },
        )
        card.addDivider()
        card.addRow(
            infoText(
                "Izin mikrofon hanya diminta saat pertama kali tombol suara dipakai. " +
                    "BitKeyboard tidak menyimpan rekaman, dan tidak punya izin internet.",
            ),
        )
        return card
    }

    private fun startVoiceTest() {
        val available = android.speech.SpeechRecognizer.isRecognitionAvailable(this)
        if (!available) {
            toast("Layanan pengenalan suara tidak tersedia di perangkat ini")
            return
        }
        toast("Buka kolom teks di aplikasi lain, lalu tekan tombol 🎙 di keyboard")
    }

    // ------------------------------------------------------------------ //
    // Kamus pribadi
    // ------------------------------------------------------------------ //
    private fun buildDictionaryCard(): View {
        val card = card()
        val learned = PrefsUserModel(settings).learnedCount()
        card.addRow(
            infoText("$learned kata tersimpan di kamus pribadi. Kata ini diprioritaskan di saran."),
        )
        card.addDivider()
        card.addRow(
            actionRow("Tambah kata", "Masukkan kata yang sering kamu pakai") { addWordDialog() },
        )
        card.addDivider()
        card.addRow(
            actionRow("Kelola kata tersimpan", "Lihat dan hapus kata satu per satu") { manageWordsDialog() },
        )
        card.addDivider()
        card.addRow(
            actionRow("Hapus semua kata yang dipelajari", "Kamus bawaan tetap ada") {
                confirm(
                    "Hapus data belajar?",
                    "Kata dan pasangan kata yang dipelajari akan dihapus. Kamus bawaan tetap ada.",
                ) {
                    PrefsUserModel(settings).forgetAll()
                    toast("Data belajar dihapus")
                    recreate()
                }
            },
        )
        return card
    }

    private fun addWordDialog() {
        val input = EditText(this).apply {
            hint = "mis. ngoding"
            setTextColor(colorText)
            setHintTextColor(colorSecondary)
            inputType = InputType.TYPE_CLASS_TEXT
            maxLines = 1
            setPadding(dp(20), dp(12), dp(20), dp(4))
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Tambah kata ke kamus")
            .setView(input)
            .setPositiveButton("Tambah", null)
            .setNegativeButton("Batal", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val word = input.text.toString().trim()
                if (word.length < 2) {
                    toast("Kata minimal 2 huruf")
                    return@setOnClickListener
                }
                settings.addUserWord(word)
                val model = PrefsUserModel(settings)
                model.learn(word)
                dialog.dismiss()
                toast("“$word” ditambahkan")
                recreate()
            }
        }
        dialog.show()
    }

    private fun manageWordsDialog() {
        val words = settings.userWords()
        if (words.isEmpty()) {
            toast("Belum ada kata tersimpan")
            return
        }
        val listView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        lateinit var dialog: AlertDialog
        for (word in words) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(8), dp(8), dp(8))
            }
            row.addView(
                TextView(this).apply {
                    text = word
                    setTextColor(colorText)
                    textSize = 15f
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
            )
            row.addView(TextView(this).apply {
                text = "Hapus"
                setTextColor(colorAccent)
                textSize = 13f
                setPadding(dp(10), dp(6), dp(10), dp(6))
                setOnClickListener {
                    settings.removeUserWord(word)
                    toast("“$word” dihapus")
                    dialog.dismiss()
                    recreate()
                }
            })
            listView.addView(row)
        }
        val scroll = ScrollView(this).apply { addView(listView) }
        dialog = AlertDialog.Builder(this)
            .setTitle("Kata tersimpan (${words.size})")
            .setView(scroll)
            .setPositiveButton("Tutup", null)
            .show()
    }

    // ------------------------------------------------------------------ //
    // Emoji
    // ------------------------------------------------------------------ //
    private fun buildEmojiCard(): View {
        val card = card()
        card.addRow(
            infoText("${settings.emojiRecent.size} emoji terakhir tercatat dan muncul di tab pertama panel emoji."),
        )
        card.addDivider()
        card.addRow(
            actionRow("Hapus riwayat emoji", "Kosongkan tab “sering dipakai”") {
                settings.emojiRecent = emptyList()
                toast("Riwayat emoji dihapus")
                recreate()
            },
        )
        return card
    }

    // ------------------------------------------------------------------ //
    // Cadangan
    // ------------------------------------------------------------------ //
    private fun buildBackupCard(): View {
        val card = card()
        card.addRow(
            actionRow("Ekspor pengaturan", "Salin semua pengaturan sebagai teks") { exportDialog() },
        )
        card.addDivider()
        card.addRow(
            actionRow("Impor pengaturan", "Tempel teks hasil ekspor") { importDialog() },
        )
        card.addDivider()
        card.addRow(
            actionRow("Reset semua pengaturan", "Kembali ke bawaan; kata pribadi tetap disimpan") {
                confirm(
                    "Reset semua pengaturan?",
                    "Tema, ukuran tombol, karakter sudut, dan preferensi lain kembali ke bawaan.",
                ) {
                    settings.resetSettings()
                    toast("Pengaturan dikembalikan ke bawaan")
                    recreate()
                }
            },
        )
        card.addDivider()
        card.addRow(
            actionRow("Hapus semua data belajar", "Kata pribadi, riwayat emoji, dan statistik") {
                confirm(
                    "Hapus semua data?",
                    "Kata pribadi, pasangan kata, dan riwayat emoji dihapus permanen.",
                ) {
                    PrefsUserModel(settings).forgetAll()
                    settings.emojiRecent = emptyList()
                    toast("Data belajar dihapus")
                    recreate()
                }
            },
        )
        return card
    }

    private fun exportDialog() {
        val text = settings.exportText()
        val view = EditText(this).apply {
            setText(text)
            setTextColor(colorText)
            textSize = 12f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            maxLines = 14
            setPadding(dp(16), dp(10), dp(16), dp(10))
        }
        AlertDialog.Builder(this)
            .setTitle("Ekspor pengaturan")
            .setView(view)
            .setPositiveButton("Salin") { _, _ ->
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("BitKeyboard", text))
                toast("Pengaturan disalin ke papan klip")
            }
            .setNeutralButton("Bagikan") { _, _ ->
                try {
                    startActivity(
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "BitKeyboard — pengaturan")
                            putExtra(Intent.EXTRA_TEXT, text)
                        },
                    )
                } catch (e: Exception) {
                    toast("Tidak ada aplikasi untuk membagikan")
                }
            }
            .setNegativeButton("Tutup", null)
            .show()
    }

    private fun importDialog() {
        val input = EditText(this).apply {
            hint = "Tempel teks hasil ekspor di sini"
            setHintTextColor(colorSecondary)
            setTextColor(colorText)
            textSize = 12f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            maxLines = 10
            setPadding(dp(16), dp(10), dp(16), dp(10))
        }
        AlertDialog.Builder(this)
            .setTitle("Impor pengaturan")
            .setView(input)
            .setPositiveButton("Impor") { _, _ ->
                val count = settings.importText(input.text.toString())
                if (count == 0) {
                    toast("Tidak ada pengaturan yang dikenali")
                } else {
                    toast("$count pengaturan diterapkan")
                    recreate()
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    // ------------------------------------------------------------------ //
    // Bantuan & tentang
    // ------------------------------------------------------------------ //
    private fun buildHelpCard(): View {
        val card = card()
        card.addRow(
            infoText(
                """
                Cara pakai cepat:
                • Geser dari huruf ke huruf lalu lepaskan untuk glide typing.
                • Tekan tombol lalu geser sedikit ke sudut untuk karakter ekstra (mis. q ke kiri atas = 1).
                • Tahan tombol huruf untuk memilih aksen.
                • Geser kiri/kanan pada tombol spasi untuk memindahkan kursor.
                • Tekan 🎙 untuk dikte suara, ⚙ untuk kembali ke pengaturan ini.
                """.trimIndent(),
            ),
        )
        card.addDivider()
        card.addRow(
            actionRow("Buka panduan lengkap", "README proyek di GitHub") {
                openUrl("https://github.com/retrobit-dev/BitKeyboard#readme")
            },
        )
        card.addDivider()
        card.addRow(
            actionRow("Cek izin aplikasi", "BitKeyboard hanya meminta mikrofon") {
                try {
                    startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:$packageName"),
                        ),
                    )
                } catch (e: Exception) {
                    toast("Pengaturan izin tidak bisa dibuka")
                }
            },
        )
        return card
    }

    private fun buildAboutCard(): View {
        val card = card()
        card.addRow(
            infoText(
                """
                BitKeyboard ${appVersion()} — keyboard Bahasa Indonesia yang ringan dan privat.

                • Kamus ±39.000 kata, saran, autocorrect, prediksi bigram
                • Glide typing, karakter sudut, simbol ilmiah, emoji
                • Tanpa iklan, pelacakan, maupun izin internet
                • Mikrofon hanya aktif saat tombol suara ditekan

                Data kamus: FrequencyWords (CC BY-SA 4.0) untuk frekuensi kata,
                Sastrawi (MIT) untuk kata dasar. Kode aplikasi: MIT.
                """.trimIndent(),
            ),
        )
        card.addDivider()
        card.addRow(
            actionRow("Buka repositori GitHub", "github.com/retrobit-dev/BitKeyboard") {
                openUrl("https://github.com/retrobit-dev/BitKeyboard")
            },
        )
        card.addDivider()
        card.addRow(
            actionRow("Lisensi & kredit", "MIT untuk kode, CC BY-SA untuk data kamus") {
                openUrl("https://github.com/retrobit-dev/BitKeyboard/blob/main/LICENSE")
            },
        )
        return card
    }

    // ------------------------------------------------------------------ //
    // Komponen baris
    // ------------------------------------------------------------------ //
    private fun switchRow(
        title: String,
        summary: String,
        initial: Boolean,
        onChange: (Boolean) -> Unit,
    ): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(10), 0, dp(10))
            minimumHeight = dp(48)
        }
        val textBlock = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        textBlock.addView(TextView(this).apply {
            text = title
            setTextColor(colorText)
            textSize = 15.5f
        })
        textBlock.addView(TextView(this).apply {
            text = summary
            setTextColor(colorSecondary)
            textSize = 12f
            setPadding(0, dp(2), dp(12), 0)
            setLineSpacing(dp(1).toFloat(), 1f)
        })
        val toggle = Switch(this).apply {
            isChecked = initial
            setOnCheckedChangeListener { _, value -> onChange(value) }
        }
        row.addView(textBlock, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(toggle)
        return row
    }

    private fun sliderRow(
        title: String,
        summary: String,
        min: Int,
        max: Int,
        initial: Int,
        onChange: (Int) -> String,
    ): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(12), 0, dp(6))
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val value = TextView(this).apply {
            text = onChange(initial)
            setTextColor(colorAccent)
            textSize = 13f
        }
        header.addView(
            TextView(this).apply {
                text = title
                setTextColor(colorText)
                textSize = 15.5f
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        header.addView(value)
        container.addView(header)
        container.addView(TextView(this).apply {
            text = summary
            setTextColor(colorSecondary)
            textSize = 12f
            setPadding(0, dp(2), 0, dp(2))
        })
        val bar = SeekBar(this).apply {
            this.max = max - min
            progress = (initial - min).coerceIn(0, max - min)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val current = min + progress
                    value.text = onChange(current)
                    if (fromUser) {
                        // Terapkan langsung agar pratinjau ikut berubah.
                        onChange(current)
                        refreshPreview()
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    refreshPreview()
                }
            })
        }
        container.addView(bar)
        return container
    }

    private fun choiceRow(
        title: String,
        summary: String,
        labels: List<String>,
        values: List<String>,
        current: String,
        onChange: (String) -> Unit,
    ): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(12), 0, dp(8))
        }
        container.addView(TextView(this).apply {
            text = title
            setTextColor(colorText)
            textSize = 15.5f
        })
        container.addView(TextView(this).apply {
            text = summary
            setTextColor(colorSecondary)
            textSize = 12f
            setPadding(0, dp(2), 0, dp(4))
        })
        val spinner = Spinner(this)
        spinner.adapter = simpleAdapter(labels)
        spinner.setSelection(values.indexOf(current).coerceAtLeast(0))
        var first = true
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (first) {
                    first = false
                    return
                }
                onChange(values[position])
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        container.addView(spinner)
        return container
    }

    private fun actionRow(title: String, summary: String, onClick: () -> Unit): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, dp(12))
            minimumHeight = dp(52)
            isClickable = true
            setOnClickListener { onClick() }
        }
        val textBlock = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        textBlock.addView(TextView(this).apply {
            text = title
            setTextColor(colorText)
            textSize = 15.5f
        })
        textBlock.addView(TextView(this).apply {
            text = summary
            setTextColor(colorSecondary)
            textSize = 12f
            setPadding(0, dp(2), dp(12), 0)
        })
        row.addView(textBlock, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(TextView(this).apply {
            text = "›"
            setTextColor(colorSecondary)
            textSize = 22f
            setPadding(0, 0, dp(4), dp(2))
        })
        return row
    }

    private fun infoText(text: String): TextView = TextView(this).apply {
        this.text = text
        setTextColor(colorSecondary)
        textSize = 12.5f
        setLineSpacing(dp(3).toFloat(), 1f)
        setPadding(0, dp(8), 0, dp(8))
    }

    private fun sectionTitle(text: String): TextView = TextView(this).apply {
        this.text = text.uppercase()
        setTextColor(colorAccent)
        textSize = 12f
        letterSpacing = 0.08f
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        setPadding(dp(4), dp(18), 0, dp(6))
    }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(4), dp(16), dp(8))
        background = rounded(colorCard, dp(14).toFloat())
    }

    private fun LinearLayout.addRow(view: View) {
        addView(view, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun LinearLayout.addDivider() {
        addView(View(this@SettingsActivity).apply {
            setBackgroundColor(colorDivider)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1))
    }

    private fun primaryButton(text: String, onClick: () -> Unit): Button = Button(this).apply {
        this.text = text
        isAllCaps = false
        setTextColor(Color.WHITE)
        textSize = 15f
        background = rounded(colorAccent, dp(11).toFloat())
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(46),
        ).apply { topMargin = dp(10); bottomMargin = dp(6) }
    }

    private fun outlineButton(text: String, onClick: () -> Unit): Button = Button(this).apply {
        this.text = text
        isAllCaps = false
        setTextColor(colorText)
        textSize = 14f
        background = GradientDrawable().apply {
            cornerRadius = dp(11).toFloat()
            setColor(if (darkTheme) 0xFF101924.toInt() else 0xFFF1F5F9.toInt())
            setStroke(dp(1), colorDivider)
        }
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(44),
        ).apply { bottomMargin = dp(6) }
    }

    private fun simpleAdapter(labels: List<String>): ArrayAdapter<String> =
        object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, labels) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent) as TextView
                view.setTextColor(colorText)
                view.textSize = 15f
                view.setPadding(dp(6), dp(8), dp(6), dp(8))
                return view
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getDropDownView(position, convertView, parent) as TextView
                view.setTextColor(colorText)
                view.setBackgroundColor(colorCard)
                view.textSize = 15f
                view.setPadding(dp(10), dp(10), dp(10), dp(10))
                return view
            }
        }

    private fun rounded(color: Int, radius: Float): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius
    }

    private fun confirm(title: String, message: String, onConfirm: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Lanjut") { _, _ -> onConfirm() }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            toast("Tidak ada aplikasi peramban")
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun appVersion(): String = try {
        packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0"
    } catch (e: Exception) {
        "1.0"
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
