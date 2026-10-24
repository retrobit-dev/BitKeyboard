package dev.retrobit.bitkeyboard.settings

import android.app.Activity
import android.app.AlertDialog
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
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/**
 * Pengaturan BitKeyboard. Dibangun sepenuhnya dengan kode agar tidak
 * membutuhkan pustaka tambahan dan tetap ringan.
 */
class SettingsActivity : Activity() {

    private lateinit var settings: SettingsStore
    private lateinit var statusText: TextView
    private lateinit var activateButton: Button

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
    private val colorAccent: Int get() = 0xFF2D6CDF.toInt()
    private val colorDivider: Int get() = if (darkTheme) 0xFF263140.toInt() else 0xFFDDE4EE.toInt()

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
    // UI
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
            setPadding(dp(16), dp(14), dp(16), dp(28))
        }
        scroll.addView(content)

        // Status aktivasi
        content.addView(sectionTitle("Status keyboard"))
        val statusCard = card()
        statusText = TextView(this).apply {
            setTextColor(colorText)
            textSize = 15f
            setLineSpacing(dp(2).toFloat(), 1f)
        }
        activateButton = primaryButton("Aktifkan BitKeyboard") {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            toast("Pilih BitKeyboard lalu aktifkan")
        }
        val pickButton = outlineButton("Pilih keyboard sekarang") {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
        }
        statusCard.addView(statusText)
        statusCard.addView(activateButton)
        statusCard.addView(pickButton)
        content.addView(statusCard)

        // Mengetik
        content.addView(sectionTitle("Mengetik"))
        val typing = card()
        typing.addView(switchRow(
            "Glide typing",
            "Geser jari dari huruf ke huruf untuk mengetik satu kata",
            settings.glideEnabled,
        ) { settings.glideEnabled = it })
        typing.addView(divider())
        typing.addView(switchRow(
            "Saran kata",
            "Tampilkan pelengkapan dan koreksi di bilah atas",
            settings.suggestionsEnabled,
        ) { settings.suggestionsEnabled = it })
        typing.addView(divider())
        typing.addView(switchRow(
            "Autocorrect",
            "Perbaiki ejaan otomatis saat spasi ditekan (bisa dibatalkan dengan backspace)",
            settings.autocorrectEnabled,
        ) { settings.autocorrectEnabled = it })
        typing.addView(divider())
        typing.addView(switchRow(
            "Prediksi kata berikutnya",
            "Saran berdasarkan kata yang baru saja diketik",
            settings.nextWordPrediction,
        ) { settings.nextWordPrediction = it })
        typing.addView(divider())
        typing.addView(switchRow(
            "Kapital otomatis",
            "Huruf besar di awal kalimat",
            settings.autoCapitalize,
        ) { settings.autoCapitalize = it })
        typing.addView(divider())
        typing.addView(switchRow(
            "Spasi ganda menjadi titik",
            "Tekan spasi dua kali untuk mengakhiri kalimat",
            settings.doubleSpacePeriod,
        ) { settings.doubleSpacePeriod = it })
        content.addView(typing)

        // Tampilan
        content.addView(sectionTitle("Tampilan & tata letak"))
        val look = card()
        look.addView(switchRow(
            "Baris angka",
            "Tambahkan baris 1–0 di atas huruf",
            settings.numberRow,
        ) { settings.numberRow = it })
        look.addView(divider())
        look.addView(switchRow(
            "Baris fungsi",
            "Esc, Tab, Ctrl, Alt, panah, Home untuk terminal dan editor",
            settings.functionRow,
        ) { settings.functionRow = it })
        look.addView(divider())
        look.addView(switchRow(
            "Simbol di sudut tombol",
            "Geser ke sudut untuk mengetik simbol tanpa ganti layer",
            settings.cornerSymbols,
        ) { settings.cornerSymbols = it })
        look.addView(divider())
        look.addView(seekRow())
        look.addView(divider())
        look.addView(themeRow())
        look.addView(divider())
        look.addView(switchRow(
            "Bilah saran",
            "Sembunyikan untuk keyboard yang lebih ringkas",
            settings.showSuggestionBar,
        ) { settings.showSuggestionBar = it })
        content.addView(look)

        // Suara
        content.addView(sectionTitle("Suara & umpan balik"))
        val sound = card()
        sound.addView(switchRow(
            "Suara klik",
            "Bunyi halus saat tombol ditekan",
            settings.soundEnabled,
        ) { settings.soundEnabled = it })
        sound.addView(divider())
        sound.addView(switchRow(
            "Getaran",
            "Umpan balik haptik",
            settings.vibrateEnabled,
        ) { settings.vibrateEnabled = it })
        sound.addView(divider())
        sound.addView(voiceRow())
        content.addView(sound)

        // Data
        content.addView(sectionTitle("Data belajar"))
        val data = card()
        val learned = PrefsUserModel(settings).learnedCount()
        val dataInfo = TextView(this).apply {
            text = "Kata yang dipelajari: $learned\nEmoji terbaru: ${settings.emojiRecent.size}"
            setTextColor(colorSecondary)
            textSize = 14f
        }
        data.addView(dataInfo)
        data.addView(outlineButton("Hapus kata yang dipelajari") {
            AlertDialog.Builder(this)
                .setTitle("Hapus data belajar?")
                .setMessage("Kata dan pasangan kata yang dipelajari akan dihapus. Kamus bawaan tetap ada.")
                .setPositiveButton("Hapus") { _, _ ->
                    PrefsUserModel(settings).forgetAll()
                    toast("Data belajar dihapus")
                    recreate()
                }
                .setNegativeButton("Batal", null)
                .show()
        })
        data.addView(outlineButton("Hapus emoji terbaru") {
            settings.emojiRecent = emptyList()
            toast("Emoji terbaru dihapus")
            recreate()
        })
        content.addView(data)

        // Tentang
        content.addView(sectionTitle("Tentang"))
        val about = card()
        about.addView(infoBlock())
        about.addView(outlineButton("Buka repositori GitHub") {
            try {
                startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/retrobit-dev/BitKeyboard")),
                )
            } catch (e: Exception) {
                toast("Tidak ada aplikasi peramban")
            }
        })
        content.addView(about)

        root.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f,
        ))
        return root
    }

    private fun buildHeader(): View {
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(0xFF0F172A.toInt())
            setPadding(dp(8), dp(10), dp(16), dp(10))
        }
        val back = TextView(this).apply {
            text = "‹"
            setTextColor(Color.WHITE)
            textSize = 30f
            gravity = Gravity.CENTER
            setPadding(dp(10), 0, dp(10), dp(4))
            setOnClickListener { finish() }
        }
        val title = TextView(this).apply {
            text = "BitKeyboard"
            setTextColor(Color.WHITE)
            textSize = 20f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        val spacer = View(this)
        val version = TextView(this).apply {
            text = "v${appVersion()}"
            setTextColor(0xFF94A6BA.toInt())
            textSize = 12f
        }
        header.addView(back)
        header.addView(title)
        header.addView(spacer, LinearLayout.LayoutParams(0, 1, 1f))
        header.addView(version)
        return header
    }

    private fun switchRow(
        title: String,
        summary: String,
        initial: Boolean,
        onChange: (Boolean) -> Unit,
    ): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, dp(12))
        }
        val textBlock = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        textBlock.addView(TextView(this).apply {
            text = title
            setTextColor(colorText)
            textSize = 16f
        })
        textBlock.addView(TextView(this).apply {
            text = summary
            setTextColor(colorSecondary)
            textSize = 12f
            setPadding(0, dp(2), dp(12), 0)
        })
        val toggle = Switch(this).apply {
            isChecked = initial
            setOnCheckedChangeListener { _, value -> onChange(value) }
        }
        row.addView(textBlock, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(toggle)
        return row
    }

    private fun seekRow(): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(12), 0, dp(12))
        }
        val title = TextView(this).apply {
            text = "Tinggi tombol"
            setTextColor(colorText)
            textSize = 16f
        }
        val value = TextView(this).apply {
            text = "${settings.keyHeightDp} dp"
            setTextColor(colorAccent)
            textSize = 13f
        }
        val bar = SeekBar(this).apply {
            max = 28
            progress = settings.keyHeightDp - 40
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val dp = 40 + progress
                    value.text = "$dp dp"
                    if (fromUser) settings.keyHeightDp = dp
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(value)
        container.addView(header)
        container.addView(bar)
        return container
    }

    private fun themeRow(): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(12), 0, dp(12))
        }
        container.addView(TextView(this).apply {
            text = "Tema"
            setTextColor(colorText)
            textSize = 16f
        })
        val spinner = Spinner(this)
        val labels = listOf("Ikuti sistem", "Terang", "Gelap", "AMOLED")
        val values = listOf("system", "light", "dark", "amoled")
        spinner.adapter = simpleAdapter(labels)
        spinner.setSelection(values.indexOf(settings.theme).coerceAtLeast(0))
        var first = true
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (first) { first = false; return }
                settings.theme = values[position]
                recreate()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        container.addView(spinner)
        return container
    }

    private fun voiceRow(): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(12), 0, dp(12))
        }
        container.addView(TextView(this).apply {
            text = "Bahasa pengenalan suara"
            setTextColor(colorText)
            textSize = 16f
        })
        container.addView(TextView(this).apply {
            text = "Dipakai saat tombol mikrofon ditekan (layanan suara sistem)."
            setTextColor(colorSecondary)
            textSize = 12f
        })
        val spinner = Spinner(this)
        val labels = listOf("Indonesia (id-ID)", "English (en-US)", "Ikuti sistem")
        val values = listOf("id-ID", "en-US", "")
        spinner.adapter = simpleAdapter(labels)
        spinner.setSelection(values.indexOf(settings.voiceLanguage).coerceAtLeast(0))
        var first = true
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (first) { first = false; return }
                settings.voiceLanguage = values[position]
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        container.addView(spinner)
        return container
    }

    private fun infoBlock(): View {
        val text = TextView(this).apply {
            text = buildString {
                append("BitKeyboard 1.0 — keyboard Bahasa Indonesia yang ringan dan privat.\n\n")
                append("• Kamus ±39.000 kata (bentuk dasar dan berimbuhan)\n")
                append("• Glide typing, saran kata, autocorrect, prediksi bigram\n")
                append("• Simbol matematika, ilmiah, dan emoji tanpa unduhan\n")
                append("• Tanpa iklan, tanpa pelacakan, tanpa izin internet\n")
                append("• Mikrofon hanya aktif saat tombol suara ditekan\n\n")
                append("Data kamus: FrequencyWords (CC BY-SA 4.0) untuk frekuensi kata, ")
                append("Sastrawi (MIT) untuk kata dasar. Kode aplikasi: MIT.\n\n")
                append("Pengenalan suara dijalankan oleh aplikasi pengenalan suara di perangkat; ")
                append("BitKeyboard tidak menyimpan rekaman audio.")
            }
            setTextColor(colorSecondary)
            textSize = 13f
            setLineSpacing(dp(3).toFloat(), 1f)
            setPadding(0, dp(4), 0, dp(10))
        }
        return text
    }

    // ------------------------------------------------------------------ //
    // Status
    // ------------------------------------------------------------------ //
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
    // Komponen kecil
    // ------------------------------------------------------------------ //
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

    private fun divider(): View = View(this).apply {
        setBackgroundColor(colorDivider)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)
    }

    private fun primaryButton(text: String, onClick: () -> Unit): Button = Button(this).apply {
        this.text = text
        textSize = 15f
        setTextColor(Color.WHITE)
        background = rounded(colorAccent, dp(10).toFloat())
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).also { it.topMargin = dp(10) }
    }

    private fun outlineButton(text: String, onClick: () -> Unit): Button = Button(this).apply {
        this.text = text
        textSize = 14f
        setTextColor(colorAccent)
        background = rounded(if (darkTheme) 0xFF1F2933.toInt() else 0xFFEAF0F8.toInt(), dp(10).toFloat())
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).also { it.topMargin = dp(8) }
    }

    private fun simpleAdapter(items: List<String>): ArrayAdapter<String> =
        ArrayAdapter(this, android.R.layout.simple_spinner_item, items).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

    private fun rounded(color: Int, radius: Float): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = radius
        setColor(color)
    }

    private fun appVersion(): String = try {
        packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0"
    } catch (e: Exception) {
        "1.0"
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics)
            .toInt()
}
