# BitKeyboard

Keyboard Android untuk Bahasa Indonesia yang cepat, ringan, dan sepenuhnya offline.
Menggabungkan tata letak bergaya *Unexpected Keyboard* (karakter ekstra lewat geseran
ke sudut tombol), **glide typing**, **saran kata + autocorrect**, **simbol matematika**,
**emoji**, dan **voice typing**.

- Tanpa iklan, tanpa pelacakan, **tanpa izin INTERNET**.
- Tanpa dependensi eksternal: hanya Android SDK + Kotlin stdlib.
- Kamus 38.990 kata Indonesia tertanam di APK (±464 KB).
- Mendukung Android 5.0 (API 21) hingga Android 15 (API 35).

---

## Fitur

### Tata letak & karakter ekstra

Setiap tombol huruf menyimpan hingga empat karakter tambahan di sudutnya.
Tekan tombol, geser sedikit ke sudut, lalu lepaskan:

```
 q        w        e        ...
1 ! ` ~   2 @ - _   3 # + =
```

- **Baris huruf QWERTY** dengan karakter angka, tanda baca, dan simbol matematika
  (`± √ π ∞ ÷ ≈ ≤ ∑ ∏ ∂ ∇`) langsung di sudut tombol.
- **Layer angka & tanda baca**, layer **simbol alternatif** (`=</#`), dan layer
  **ilmiah** (`α β γ … ∫ ∑ √ ∞ ≈ ≠ ≤ ≥ ∇`) untuk catatan kuliah atau skripsi.
- **Baris fungsi opsional**: `Esc`, `Tab`, `Ctrl`, `Alt`, `Fn`, tombol panah,
  dan `Home` — berguna untuk Termux, SSH, dan editor kode.
- **Aksen** lewat tekan lama: `e` → `è é ê ë`, `n` → `ñ`, `c` → `ç`, dan seterusnya.
- **Emoji** dengan tab kategori (wajah, tangan, hewan, makanan, aktivitas, objek,
  simbol) plus tab "sering dipakai" yang belajar dari pemakaian Anda.
- Shift sekali ketuk, caps lock lewat ketuk dua kali; auto-capital di awal kalimat.
- Geser pada tombol spasi untuk memindahkan kursor.

### Glide typing (geser untuk mengetik)

Geser jari melewati huruf-huruf sebuah kata, lalu lepaskan. Decoder mencocokkan
bentuk jalur dengan template tiap kata memakai **DTW (dynamic time warping)**,
sehingga kecepatan geser tidak memengaruhi hasil.

Hasil pada set uji sintetis (593 kata, jitter acak meniru gerakan jari):

| Metrik | Hasil |
| --- | --- |
| Top-1 akurasi | **94,8%** |
| Top-5 akurasi | **99,8%** |
| Waktu decode | ±1,9 ms/kata |
| Tanpa noise | 99,0% top-1 |

Tiga kandidat terbaik ditampilkan sebagai alternatif setelah kata dikenali.

### Saran kata, autocorrect, dan pembelajaran

- Pelengkapan awalan berbasis frekuensi (`pembel` → `pembelajaran`, `pembeli`, `pembelaan`).
- Koreksi ejaan Levenshtein untuk kata tak dikenal (`pemeblajaran` → `pembelajaran`,
  `tidk` → `tidak`) dengan ambang jarak yang menyesuaikan panjang kata.
- Prediksi kata berikutnya dari model bigram + riwayat pemakaian pribadi.
- Ketuk saran untuk memakai, **tekan lama saran untuk menambahkan kata ke kamus pribadi**.
- Kata yang Anda ketik dan terima akan menaikkan peringkatnya sendiri (tanpa jaringan).

### Voice typing

Tombol mikrofon membuka panel kecil "Mendengarkan…" dan menjalankan
`SpeechRecognizer` sistem dengan bahasa `id-ID` (bisa diganti di pengaturan).
Hasilnya langsung dimasukkan ke kolom teks yang sedang aktif — termasuk saat
panel suara sempat mengambil fokus, teks diantrekan lalu dimasukkan ketika
keyboard kembali aktif.

Izin mikrofon diminta **hanya saat tombol pertama kali ditekan**, bukan saat
pemasangan. BitKeyboard tidak menyimpan rekaman; audio diproses oleh penyedia
pengenalan suara di perangkat (biasanya Google Speech Services), yang pada
beberapa perangkat memerlukan koneksi internet sendiri. Bila tidak ada penyedia,
muncul pesan yang jelas.

### Privasi

- Tidak ada izin `INTERNET` di manifest — aplikasi secara teknis tidak bisa
  mengirim data keluar.
- Izin `RECORD_AUDIO` hanya dipakai saat panel voice typing terbuka; tidak ada
  perekaman di latar belakang dan tidak ada audio yang disimpan.
- `allowBackup="false"`, tanpa SDK pihak ketiga, tanpa iklan.
- Semua kamus, bigram, dan statistik pemakaian disimpan lokal di perangkat.

---

## Instalasi

### Dari source

Prasyarat: JDK 17+ dan Android SDK (compileSdk 35). Gradle 8.9 diunduh otomatis lewat wrapper.

```bash
git clone https://github.com/retrobit-dev/BitKeyboard.git
cd BitKeyboard
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Pasang ke perangkat:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Aktifkan keyboard:

1. Buka **Pengaturan → Sistem → Bahasa & input → Keyboard di layar**.
2. Aktifkan **BitKeyboard**.
3. Pilih BitKeyboard sebagai keyboard aktif (tombol globe pada keyboard, atau
   notifikasi pemilih keyboard).
4. Buka aplikasi **BitKeyboard** untuk mengatur tema, tinggi tombol, bahasa suara,
   dan menghapus data belajar.

Android Studio akan mengunduh Android SDK yang diperlukan saat sinkronisasi
pertama. Tanpa Android Studio, set `ANDROID_HOME` ke SDK yang sudah terpasang.

---

## Demo web

`demo/` berisi versi browser dari keyboard yang sama. Lapisan bahasanya
(kamus, trie, suggester, decoder glide) adalah **kode Kotlin yang sama**
dikompilasi ke JavaScript; hanya tampilan yang ditulis ulang dengan HTML/CSS/JS.

```bash
tools/build_demo.sh          # butuh kotlinc-js (Kotlin 2.1+)
python3 -m http.server 8000
# buka http://localhost:8000/demo/
```

Yang bisa dicoba di demo: mengetik, geser ke sudut, glide manual, preset glide,
saran kata, autocorrect saat spasi, layer simbol/ilmiah, panel emoji, tekan lama
aksen, dan voice typing lewat Web Speech API (Chrome/Edge/Android).

Keluaran build demo ada di `demo/dist/` dan sengaja tidak masuk Git.

---

## Arsitektur

```
app/src/main/java/dev/retrobit/bitkeyboard/
├── core/                  # murni Kotlin, tanpa Android — bisa diuji di JVM & JS
│   ├── Keys.kt            # KeySpec, KeyValue, KeyboardAction, CornerDirection, Layer
│   ├── Layouts.kt         # huruf, simbol, simbol alt, ilmiah, ilmiah alt
│   ├── EmojiData.kt       # kategori emoji + pembuat layout panel emoji
│   ├── Dictionary.kt      # trie + daftar kata terurut frekuensi (format ringkas)
│   ├── BigramModel.kt     # prediksi kata berikutnya
│   ├── UserModel.kt       # kamus pribadi + riwayat bigram pengguna
│   ├── Suggester.kt       # pelengkapan, koreksi, prediksi, pembelajaran
│   ├── GlideDecoder.kt    # template jalur + prefilter + DTW
│   └── TextUtils.kt       # kapitalisasi, pemisah kata, deteksi huruf
├── ime/
│   ├── BitKeyboardService.kt  # InputMethodService, pemuatan kamus, voice, insets
│   ├── KeyboardView.kt        # gambar keyboard, sentuhan, sudut, glide, popup
│   ├── Editor.kt              # jembatan InputConnection + siklus kata
│   ├── VoiceInputActivity.kt  # panel SpeechRecognizer + izin mikrofon
│   └── Theme.kt               # palet terang/gelap
└── settings/
    ├── SettingsStore.kt    # SharedPreferences + PrefsUserModel
    └── SettingsActivity.kt # UI pengaturan tanpa AndroidX (programatik)
```

Prinsip yang dipakai:

- **`core` bebas Android** sehingga bisa diuji cepat di JVM dan dipakai ulang di web.
- **Satu sumber kebenaran** untuk layout: `Layouts.kt` dipakai aplikasi dan demo.
- **Rendering manual** di `KeyboardView` (Canvas) agar tidak perlu AndroidX dan
  tetap ringan di perangkat lama.
- **Kamus dimuat di thread daemon** saat service dibuat, UI tidak pernah menunggu.
- **Tanpa state global yang bocor**: setiap `onStartInputView` mereset layer,
  shift, dan mode glide.

### Format kamus

`app/src/main/assets/dictionary_id.txt` menyimpan tiga kolom per baris:

```
kata<TAB>frekuensi<TAB>panjang
```

Kolom panjang memungkinkan decoder melewati kandidat yang mustahil tanpa
membaca seluruh kata. `Dictionary` membangun trie huruf-awal dan indeks
huruf-akhir (untuk glide), serta daftar terurut frekuensi (untuk saran).

`bigrams_id.txt`: `kata1 kata2<TAB>jumlah`.

---

## Membangun ulang kamus

```bash
python3 tools/build_dictionary.py            # dengan unduhan sumber
python3 tools/build_dictionary.py --offline  # hanya cache lokal
```

Sumber data:

| Sumber | Lisensi | Dipakai untuk |
| --- | --- | --- |
| [FrequencyWords](https://github.com/hermitdave/FrequencyWords) (id/en 50k) | CC BY-SA 4.0 | peringkat frekuensi |
| [Sastrawi](https://github.com/sastrawi/sastrawi) `kata-dasar.txt` | MIT | validasi kata dasar Indonesia |
| `tools/data/seed_words.txt`, `seed_bigrams.txt` | proyek ini | kata teknis, bigram umum |

Hasil: 38.990 kata, 366 bigram, ±130 ms waktu muat, 464 KB di APK.

---

## Pengujian

```bash
tools/jvmtest/run.sh
```

Menjalankan `tools/jvmtest/CoreTests.kt` di JVM tanpa Android SDK:

- 38.990 kata termuat, format kamus valid;
- saran: awalan, koreksi, prediksi, pembelajaran kata;
- decoder glide: akurasi top-1/top-5 dengan dan tanpa noise, batas waktu;
- layout: jumlah kolom, lebar baris, keberadaan simbol ilmiah dan emoji.

Hasil terakhir: **106/106 pemeriksaan lulus**.

---

## Lisensi & kredit

Kode BitKeyboard dilisensikan di bawah **MIT** (lihat `LICENSE`).
Data kamus mengikuti lisensi sumbernya dan **tidak** tercakup lisensi MIT:
daftar frekuensi kata dari FrequencyWords adalah CC BY-SA 4.0, dan daftar kata
dasar Sastrawi adalah MIT.

- Terinspirasi [Unexpected Keyboard](https://github.com/Julow/Unexpected-Keyboard)
  (GPL-3.0) untuk ide tata letak sudut tombol; **tidak ada kode GPL yang disalin**,
  implementasi ditulis dari nol.
- Data frekuensi kata: FrequencyWords, CC BY-SA 4.0.
- Daftar kata dasar: Sastrawi, MIT.
- Emoji: Unicode Emoji (Unicode Consortium).

Nama dan logo adalah milik proyek. Kontribusi diterima lewat pull request.
