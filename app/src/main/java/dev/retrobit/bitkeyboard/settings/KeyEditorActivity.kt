package dev.retrobit.bitkeyboard.settings

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import dev.retrobit.bitkeyboard.core.CornerDirection
import dev.retrobit.bitkeyboard.core.KeyCustomization
import dev.retrobit.bitkeyboard.core.KeyOverride
import dev.retrobit.bitkeyboard.core.KeySpec
import dev.retrobit.bitkeyboard.core.KeyValue

/**
 * Editor karakter tombol: empat sudut (geser) dan daftar tekan-lama.
 *
 * Setiap perubahan langsung disimpan ke [SettingsStore] dan dipakai keyboard
 * pada pembukaan berikutnya (atau seketika bila keyboard sedang terbuka).
 */
class KeyEditorActivity : Activity() {

    private lateinit var settings: SettingsStore
    private lateinit var list: LinearLayout
    private lateinit var summary: TextView

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
        refresh()
    }

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(colorBg)
        }
        root.addView(header())

        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(28))
        }
        scroll.addView(content)

        summary = TextView(this).apply {
            setTextColor(colorSecondary)
            textSize = 13f
            setLineSpacing(dp(2).toFloat(), 1f)
            setPadding(dp(2), dp(2), dp(2), dp(10))
        }
        content.addView(summary)

        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(list)

        content.addView(
            outlineButton("Kembalikan semua tombol ke bawaan") {
                AlertDialog.Builder(this)
                    .setTitle("Kembalikan semua tombol?")
                    .setMessage("Semua karakter sudut dan tekan-lama yang kamu ubah akan kembali ke bawaan.")
                    .setPositiveButton("Kembalikan") { _, _ ->
                        settings.resetKeyOverrides()
                        refresh()
                        toast("Semua tombol kembali ke bawaan")
                    }
                    .setNegativeButton("Batal", null)
                    .show()
            },
        )

        root.addView(
            scroll,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
        )
        return root
    }

    private fun header(): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(0xFF0F172A.toInt())
            setPadding(dp(8), dp(10), dp(16), dp(10))
        }
        bar.addView(TextView(this).apply {
            text = "‹"
            setTextColor(Color.WHITE)
            textSize = 30f
            gravity = Gravity.CENTER
            setPadding(dp(10), 0, dp(12), dp(4))
            setOnClickListener { finish() }
        })
        bar.addView(
            TextView(this).apply {
                text = "Karakter tombol"
                setTextColor(Color.WHITE)
                textSize = 19f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        return bar
    }

    // ------------------------------------------------------------------ //
    // Daftar tombol
    // ------------------------------------------------------------------ //

    private fun refresh() {
        val overrides = settings.keyOverrides()
        list.removeAllViews()
        val groups = KeyCustomization.editableKeyGroups()
        val total = groups.sumOf { it.second.size }
        summary.text =
            "Ketik satu karakter per kotak. Urutannya: kiri atas, kanan atas, kiri bawah, kanan bawah. " +
                "Tekan lama dipisah koma (mis. à,á,â). " +
                "Saat ini ${overrides.size} dari $total tombol diubah."
        for ((name, keys) in groups) {
            list.addView(TextView(this).apply {
                text = "${name.uppercase()} · ${keys.size} tombol"
                setTextColor(colorAccent)
                textSize = 11.5f
                letterSpacing = 0.06f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(dp(4), dp(16), 0, dp(6))
            })
            for (spec in keys) {
                val id = KeyCustomization.idFor(spec)
                list.addView(row(spec, id, overrides[id]))
            }
        }
    }

    private fun row(spec: KeySpec, id: String, override: KeyOverride?): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(10), dp(12))
            background = rounded(colorCard, dp(12).toFloat())
            setOnClickListener { edit(spec, id) }
        }
        val keyBadge = TextView(this).apply {
            text = KeyCustomization.displayName(spec)
            gravity = Gravity.CENTER
            setTextColor(colorText)
            textSize = 17f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            background = rounded(
                if (darkTheme) 0xFF232C36.toInt() else 0xFFE8EEF7.toInt(),
                dp(9).toFloat(),
            )
        }
        container.addView(
            keyBadge,
            LinearLayout.LayoutParams(dp(44), dp(44)),
        )

        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, dp(8), 0)
        }
        val corners: List<String?> = override?.corners
            ?: spec.corners.map { v -> v?.display() }
        val cornerText = corners.mapIndexed { index, value ->
            when {
                value != null -> value
                spec.corners.getOrNull(index) != null -> "·"
                else -> "–"
            }
        }
        info.addView(TextView(this).apply {
            text = cornerText.joinToString("   ")
            setTextColor(if (override != null) colorAccent else colorText)
            textSize = 15f
        })
        val longPress = override?.longPress ?: spec.longPress.map { it.display() }
        info.addView(TextView(this).apply {
            text = if (longPress.isEmpty()) {
                "tanpa tekan-lama"
            } else {
                "tekan lama: " + longPress.joinToString(" ")
            }
            setTextColor(colorSecondary)
            textSize = 11.5f
        })
        container.addView(info, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        container.addView(TextView(this).apply {
            text = "›"
            setTextColor(colorSecondary)
            textSize = 22f
            setPadding(dp(6), 0, dp(6), dp(2))
        })

        val wrapper = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        wrapper.addView(container)
        wrapper.addView(View(this).apply {
            setBackgroundColor(colorDivider)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(1),
            ).apply { topMargin = dp(6) }
        })
        return wrapper
    }

    private fun KeyValue.display(): String = when (this) {
        is KeyValue.CharValue -> char.toString()
        is KeyValue.TextValue -> text
        is KeyValue.EventValue -> event.name
        is KeyValue.ActionValue -> action.name
    }

    // ------------------------------------------------------------------ //
    // Dialog edit
    // ------------------------------------------------------------------ //

    private fun edit(spec: KeySpec, id: String) {
        val override = settings.keyOverrides()[id]
        val currentCorners = override?.corners
            ?: List(4) { i -> spec.corners.getOrNull(i)?.display() }
        val currentLong = (override?.longPress ?: spec.longPress.map { it.display() })
            .joinToString(", ")

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(6), dp(18), dp(4))
        }

        panel.addView(TextView(this).apply {
            text = "Karakter di sudut tombol “${KeyCustomization.displayName(spec)}”"
            setTextColor(colorSecondary)
            textSize = 12.5f
        })

        val fields = ArrayList<EditText>(4)
        val labels = listOf("Kiri atas (NW)", "Kanan atas (NE)", "Kiri bawah (SW)", "Kanan bawah (SE)")
        for (i in 0 until 4) {
            val field = EditText(this).apply {
                hint = labels[i]
                setText(currentCorners.getOrNull(i).orEmpty())
                setTextColor(colorText)
                setHintTextColor(colorSecondary)
                textSize = 16f
                inputType = InputType.TYPE_CLASS_TEXT
                maxLines = 1
            }
            panel.addView(
                field,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)),
            )
            fields.add(field)
        }

        panel.addView(TextView(this).apply {
            text = "Tekan lama (pisahkan dengan koma)"
            setTextColor(colorSecondary)
            textSize = 12.5f
            setPadding(0, dp(8), 0, 0)
        })
        val longField = EditText(this).apply {
            hint = "mis. à, á, â, ä"
            setText(currentLong)
            setTextColor(colorText)
            setHintTextColor(colorSecondary)
            textSize = 16f
            maxLines = 1
        }
        panel.addView(
            longField,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)),
        )

        val dialog = AlertDialog.Builder(this)
            .setTitle("Atur tombol “${KeyCustomization.displayName(spec)}”")
            .setView(panel)
            .setPositiveButton("Simpan", null)
            .setNeutralButton("Bawaan", null)
            .setNegativeButton("Batal", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val corners = fields.map { it.text.toString().trim() }
                val longPress = longField.text.toString()
                    .split(',')
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                val normalised = List(4) { i -> corners.getOrNull(i)?.takeIf { it.isNotEmpty() } }
                val unchangedCorners = normalised == List(4) { i ->
                    spec.corners.getOrNull(i)?.display()?.takeIf { it.isNotEmpty() }
                }
                val unchangedLong = longPress == spec.longPress.map { it.display() }
                val next = when {
                    unchangedCorners && unchangedLong -> null
                    else -> KeyOverride(
                        corners = normalised,
                        longPress = if (unchangedLong) null else longPress,
                    )
                }
                settings.setKeyOverride(id, next)
                dialog.dismiss()
                refresh()
                toast("Tombol disimpan")
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                settings.setKeyOverride(id, null)
                dialog.dismiss()
                refresh()
                toast("Tombol kembali ke bawaan")
            }
        }
        dialog.show()
    }

    // ------------------------------------------------------------------ //
    // Komponen
    // ------------------------------------------------------------------ //

    private fun outlineButton(text: String, onClick: () -> Unit): Button = Button(this).apply {
        this.text = text
        isAllCaps = false
        setTextColor(colorAccent)
        textSize = 14f
        background = rounded(if (darkTheme) 0xFF101924.toInt() else 0xFFF1F5F9.toInt(), dp(10).toFloat())
        setOnClickListener { onClick() }
    }

    private fun rounded(color: Int, radius: Float): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
