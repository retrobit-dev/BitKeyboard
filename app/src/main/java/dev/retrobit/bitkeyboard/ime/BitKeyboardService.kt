package dev.retrobit.bitkeyboard.ime

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.inputmethod.InputMethodManager
import android.inputmethodservice.InputMethodService
import android.widget.Toast
import dev.retrobit.bitkeyboard.core.BigramModel
import dev.retrobit.bitkeyboard.core.Dictionary
import dev.retrobit.bitkeyboard.core.GlideDecoder
import dev.retrobit.bitkeyboard.core.Layer
import dev.retrobit.bitkeyboard.core.Suggester
import dev.retrobit.bitkeyboard.settings.PrefsUserModel
import dev.retrobit.bitkeyboard.settings.SettingsActivity
import dev.retrobit.bitkeyboard.settings.SettingsStore
import java.lang.ref.WeakReference

/**
 * Input method service BitKeyboard.
 *
 * Kamus dimuat di thread latar agar keyboard langsung muncul; saran dan glide
 * aktif begitu kamus siap (biasanya < 1 detik).
 */
class BitKeyboardService : InputMethodService() {

    lateinit var settings: SettingsStore
        private set
    lateinit var editor: Editor
        private set
    lateinit var userModel: PrefsUserModel
        private set

    private var keyboardView: KeyboardView? = null
    private var dictionary: Dictionary? = null
    private var loading = false
    private var loadFailed = false
    private var pendingVoiceText: String? = null

    /**
     * Perubahan di layar pengaturan langsung dipakai keyboard yang terbuka.
     *
     * Hanya kunci yang memengaruhi tampilan/saran yang diproses; perubahan
     * kamus pribadi (yang ditulis setiap kali belajar kata) tidak memicu
     * pembangunan ulang layout.
     */
    private val settingsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key in DISPLAY_KEYS) {
            mainHandler.post {
                keyboardView?.syncSettings()
                editor.updateSuggestions()
            }
        }
    }
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore(this)
        userModel = PrefsUserModel(settings)
        editor = Editor(this, settings)
        editor.setUserModel(userModel)
        active = WeakReference(this)
        settings.registerListener(settingsListener)
        loadDictionary()
    }

    override fun onCreateInputView(): android.view.View {
        if (keyboardView == null) {
            keyboardView = KeyboardView(this, this, settings, editor)
        }
        return keyboardView!!
    }

    override fun onStartInputView(info: android.view.inputmethod.EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        keyboardView?.resetForNewInput()
        keyboardView?.syncSettings()
        editor.reset()
        flushPendingVoiceText()
        if (dictionary != null) keyboardView?.onDictionaryReady()
        if (loadFailed) toast("Kamus gagal dimuat. Coba pasang ulang aplikasi.")
    }

    override fun onFinishInput() {
        super.onFinishInput()
        saveState()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        saveState()
    }

    private fun saveState() {
        // Data pengguna sudah disimpan setiap perubahan; tidak ada yang perlu
        // dilakukan di sini selain memastikan referensi view tetap hidup.
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        keyboardView?.syncSettings()
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onComputeInsets(outInsets: Insets) {
        super.onComputeInsets(outInsets)
        val view = keyboardView ?: return
        if (!isInputViewShown || view.height == 0) return
        val decor = window?.window?.decorView
        val w = decor?.width ?: resources.displayMetrics.widthPixels
        val h = decor?.height ?: resources.displayMetrics.heightPixels
        val top = Math.max(0, h - view.height)
        // Aplikasi di atas keyboard harus tahu area yang tertutup agar kursor
        // tidak tersembunyi di belakang keyboard.
        outInsets.contentTopInsets = top
        outInsets.visibleTopInsets = top
        outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_REGION
        outInsets.touchableRegion.set(0, top, w, h)
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int,
    ) {
        super.onUpdateSelection(
            oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd,
        )
        if (oldSelStart != newSelStart || oldSelEnd != newSelEnd) {
            editor.onSelectionUpdated()
        }
    }

    // ------------------------------------------------------------------ //
    // Kamus
    // ------------------------------------------------------------------ //
    private fun loadDictionary() {
        if (dictionary != null || loading) return
        loading = true
        Thread {
            val started = SystemClock.uptimeMillis()
            try {
                val dictText = readAsset("dictionary_id.txt")
                val dict = Dictionary.fromText(dictText)
                val bigrams = try {
                    BigramModel.fromText(readAsset("bigrams_id.txt"))
                } catch (e: Exception) {
                    BigramModel.EMPTY
                }
                val suggester = Suggester(dict, bigrams, userModel)
                val decoder = GlideDecoder(dict)
                mainHandler.post {
                    dictionary = dict
                    editor.setSuggester(suggester)
                    editor.setGlideDecoder(decoder)
                    keyboardView?.onDictionaryReady()
                    Log.i(TAG, "Kamus siap: ${dict.size} kata dalam ${SystemClock.uptimeMillis() - started} ms")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Gagal memuat kamus", e)
                loadFailed = true
                mainHandler.post {
                    keyboardView?.onDictionaryReady()
                    toast("Kamus Bahasa Indonesia gagal dimuat")
                }
            } finally {
                loading = false
            }
        }.apply { name = "bitkeyboard-dictionary"; isDaemon = true }.start()
    }

    private fun readAsset(name: String): String =
        assets.open(name).bufferedReader(Charsets.UTF_8).use { it.readText() }

    // ------------------------------------------------------------------ //
    // Aksi dari tampilan
    // ------------------------------------------------------------------ //
    fun openSettings() {
        try {
            val intent = Intent(this, SettingsActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            toast("Pengaturan tidak dapat dibuka")
        }
    }

    fun showImePicker() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager ?: return
        imm.showInputMethodPicker()
    }

    fun startVoiceInput() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            toast("Layanan suara tidak ditemukan. Pasang aplikasi pengenalan suara.")
            return
        }
        try {
            startActivity(
                Intent(this, VoiceInputActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (e: ActivityNotFoundException) {
            toast("Pengenalan suara tidak tersedia di perangkat ini")
        }
    }

    /**
     * Dipanggil [VoiceInputActivity] setelah pengenalan suara selesai.
     *
     * Saat aktivitas suara tampil, target aplikasi kehilangan fokus sehingga
     * koneksi input bisa dilepas. Teks disimpan dulu lalu dimasukkan begitu
     * keyboard menerima input lagi.
     */
    private fun onVoiceText(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        val suffix = if (settings.voiceAutoSpace) " " else ""
        if (editor.isActiveInput()) {
            editor.commitPlainText(clean + suffix)
        } else {
            pendingVoiceText = clean
        }
    }

    private fun flushPendingVoiceText() {
        val text = pendingVoiceText ?: return
        pendingVoiceText = null
        if (text.isEmpty()) return
        val suffix = if (settings.voiceAutoSpace) " " else ""
        mainHandler.postDelayed({
            if (editor.isActiveInput()) editor.commitPlainText(text + suffix)
        }, 80)
    }

    fun toast(message: String) {
        mainHandler.post {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        settings.unregisterListener(settingsListener)
        editor.listener = null
        keyboardView = null
        if (active?.get() === this) active = null
        super.onDestroy()
    }

    /** Layer yang terakhir dipakai, untuk reset saat keyboard dibuka lagi. */
    fun resetLayer() {
        keyboardView?.setLayer(Layer.LETTERS)
    }

    companion object {
        private const val TAG = "BitKeyboard"

        /** Kunci preferensi yang mengubah tampilan keyboard. */
        private val DISPLAY_KEYS = setOf(
            "glide", "glide_trail", "suggestions", "suggestion_count", "autocorrect",
            "next_word", "suggestion_bar", "suggestion_bar_height", "number_row",
            "function_row", "corner_symbols", "key_height", "key_gap", "key_radius",
            "key_font", "key_preview", "theme", "accent_color", "long_press_ms",
            "space_cursor", "emoji_tab",
        )

        /** IME aktif saat ini; voice typing berjalan di aktivitas terpisah. */
        private var active: WeakReference<BitKeyboardService>? = null

        fun deliverVoiceText(text: String) {
            val service = active?.get() ?: return
            service.mainHandler.post { service.onVoiceText(text) }
        }
    }
}
