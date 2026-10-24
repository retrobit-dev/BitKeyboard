package dev.retrobit.bitkeyboard.ime

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import dev.retrobit.bitkeyboard.settings.SettingsStore

/**
 * Aktivitas kecil untuk voice typing.
 *
 * IME tidak bisa menerima hasil `RecognizerIntent` karena tidak punya
 * `onActivityResult`, jadi pengenalan suara dijalankan di sini memakai
 * [SpeechRecognizer]. Hasilnya dikirim ke [BitKeyboardService] yang sedang aktif.
 *
 * Izin RECORD_AUDIO diminta di sini (hanya sekali, saat tombol mikrofon dipakai),
 * bukan saat aplikasi dipasang.
 */
class VoiceInputActivity : Activity(), RecognitionListener {

    private var recognizer: SpeechRecognizer? = null
    private var finished = false
    private lateinit var statusView: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val timeout = Runnable { fail("Tidak ada suara terdeteksi") }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            attributes = attributes.apply { dimAmount = 0.45f }
        }
        setContentView(buildUi())
        window?.setLayout(dp(300), ViewGroup.LayoutParams.WRAP_CONTENT)
        window?.setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_AUDIO)
        } else {
            startListening()
        }
    }

    // ------------------------------------------------------------------ //
    // Tampilan
    // ------------------------------------------------------------------ //

    private fun buildUi(): View {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(22), dp(20), dp(22), dp(16))
            background = GradientDrawable().apply {
                cornerRadius = dp(20).toFloat()
                setColor(if (isNightMode()) 0xFF18263A.toInt() else Color.WHITE)
                setStroke(dp(1), if (isNightMode()) 0xFF2A405C.toInt() else 0xFFE2E8F0.toInt())
            }
        }

        panel.addView(TextView(this).apply {
            text = "🎙"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 30f)
            gravity = Gravity.CENTER
        })

        panel.addView(TextView(this).apply {
            text = "Mendengarkan…"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            setTextColor(if (isNightMode()) Color.WHITE else 0xFF0F172A.toInt())
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, dp(2))
        })

        statusView = TextView(this).apply {
            text = "Ucapkan teks dalam Bahasa Indonesia"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            setTextColor(if (isNightMode()) 0xFF9FB4CA.toInt() else 0xFF64748B.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(14))
        }
        panel.addView(statusView)

        panel.addView(Button(this).apply {
            text = "Batal"
            isAllCaps = false
            setTextColor(if (isNightMode()) 0xFF9FB4CA.toInt() else 0xFF475569.toInt())
            background = GradientDrawable().apply {
                cornerRadius = dp(10).toFloat()
                setColor(if (isNightMode()) 0xFF101924.toInt() else 0xFFF1F5F9.toInt())
            }
            setOnClickListener { cancelListening() }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)))

        return panel
    }

    private fun isNightMode(): Boolean =
        (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES

    // ------------------------------------------------------------------ //
    // Pengenalan suara
    // ------------------------------------------------------------------ //

    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            fail("Layanan pengenalan suara tidak tersedia. Pasang Google Speech Services.")
            return
        }
        statusView.text = "Silakan bicara…"
        val instance = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer = instance
        instance.setRecognitionListener(this)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            val language = SettingsStore(this@VoiceInputActivity).voiceLanguage
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language.ifBlank { "id-ID" })
        }
        try {
            instance.startListening(intent)
        } catch (e: SecurityException) {
            fail("Izin mikrofon diperlukan untuk voice typing")
            return
        }
        handler.postDelayed(timeout, TIMEOUT_MS)
    }

    private fun cancelListening() {
        handler.removeCallbacks(timeout)
        recognizer?.cancel()
        finish()
    }

    private fun fail(message: String) {
        if (finished) return
        finished = true
        handler.removeCallbacks(timeout)
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        finish()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_AUDIO) return
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startListening()
        } else {
            fail("Izin mikrofon ditolak. Voice typing butuh akses mikrofon.")
        }
    }

    override fun onResults(results: Bundle?) {
        if (finished) return
        finished = true
        handler.removeCallbacks(timeout)
        val text = results
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            ?.trim()
        if (text.isNullOrEmpty()) {
            fail("Tidak ada suara yang dikenali")
            return
        }
        BitKeyboardService.deliverVoiceText(text)
        finish()
    }

    override fun onError(error: Int) {
        val message = when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "Masalah perekaman suara"
            SpeechRecognizer.ERROR_CLIENT -> "Pengenalan suara dibatalkan"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Izin mikrofon ditolak"
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
            -> "Jaringan bermasalah; layanan suara butuh koneksi"
            SpeechRecognizer.ERROR_NO_MATCH -> "Suara tidak dikenali, coba lagi"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Tidak ada suara terdeteksi"
            else -> "Pengenalan suara gagal (kode $error)"
        }
        fail(message)
    }

    override fun onReadyForSpeech(params: Bundle?) {
        statusView.text = "Silakan bicara…"
    }

    override fun onBeginningOfSpeech() {
        statusView.text = "Merekam…"
    }

    override fun onEndOfSpeech() {
        statusView.text = "Memproses…"
    }

    override fun onRmsChanged(rmsdB: Float) = Unit
    override fun onBufferReceived(buffer: ByteArray?) = Unit
    override fun onPartialResults(partialResults: Bundle?) = Unit
    override fun onEvent(eventType: Int, params: Bundle?) = Unit

    override fun onDestroy() {
        handler.removeCallbacks(timeout)
        recognizer?.destroy()
        recognizer = null
        super.onDestroy()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val REQUEST_AUDIO = 1041
        const val TIMEOUT_MS = 15_000L
    }
}
