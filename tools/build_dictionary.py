#!/usr/bin/env python3
"""Membangun aset kamus Bahasa Indonesia untuk BitKeyboard.

Sumber data
-----------
1. FrequencyWords (hermitdave/FrequencyWords) - frekuensi kata dari subtitle
   OpenSubtitles 2018. Konten: CC-BY-SA 4.0, kode: MIT.
2. Sastrawi (sastrawi/sastrawi) - daftar kata dasar Bahasa Indonesia (MIT).
3. Daftar kata kurasi BitKeyboard (tools/data/seed_words.txt).
4. Daftar frasa untuk bigram (tools/data/seed_bigrams.txt).

Pendekatan
----------
Korpus subtitle dipakai HANYA untuk frekuensi. Keanggotaan kata divalidasi
dengan kata dasar Sastrawi + pembentukan kata berimbuhan (meN-, peN-, ber-,
ter-, di-, ke-...-an, peN-...-an, dan seterusnya). Hasilnya jauh lebih bersih
daripada memakai korpus mentah (tanpa nama tokoh / kata Inggris).

Keluaran
--------
- app/src/main/assets/dictionary_id.txt  ("kata<TAB>frekuensi")
- app/src/main/assets/bigrams_id.txt     ("kata1<TAB>kata2<TAB>bobot")
- build/dictionary_stats.json

Pemakaian
---------
    python3 tools/build_dictionary.py             # unduh bila perlu, lalu bangun
    python3 tools/build_dictionary.py --offline   # hanya pakai tools/.cache
"""

from __future__ import annotations

import argparse
import json
import re
import sys
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CACHE = ROOT / "tools" / ".cache"
ASSETS = ROOT / "app" / "src" / "main" / "assets"
BUILD = ROOT / "build"

ID_URL = "https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/content/2018/id/id_50k.txt"
EN_URL = "https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/content/2018/en/en_50k.txt"
ROOT_URL = "https://raw.githubusercontent.com/sastrawi/sastrawi/master/data/kata-dasar.txt"

WORD_RE = re.compile(r"^[a-z]{2,20}$")
VOWELS = "aeiou"

# Kata Inggris yang sering muncul di subtitle Indonesia.
ENGLISH_STOPWORDS = set("""
the and you that was for are with his they this have from one had word but not what all were when
your can said there use an each which she how their will other about out many then them these so
some her would make like him into time has look two more write see number way could people my than
first water been call who oil its now find long down day did get come made may part over new sound
take only little work know place year live me back give most very after thing our just name good
sentence man think say great where help through much before line right too mean old any same tell
boy follow came want show also around form three small set put end does another well large must big
even such because turn here why ask went men read need land different home us move try kind hand
picture again change off play spell air away animal house point page letter mother answer found
study still learn should world high every near add food between own below country plant last school
father keep tree never start city earth eye light thought head under story saw left few while along
might close something seem next hard open example begin life always those both paper together got
group often run important until children side feet car mile night walk white sea began grow took
river four carry state once book hear stop without second later miss idea enough eat face watch far
really almost let above girl sometimes mountain cut young talk soon list song being leave family
hello yeah okay ok hey hi oh ah uh um gonna wanna gotta
""".split())

# Kata Indonesia yang kebetulan sama dengan kata Inggris.
INDONESIAN_KEEP = set("""
air main mata hari bulan bumi api batu susu kopi teh roti gula garam daging ikan ayam sapi kuda
kucing anjing burung ular semut lebah pohon bunga daun akar buah mangga pisang apel jeruk anggur
semangka nanas durian rambutan salak duku markisa alpukat tomat kentang wortel bayam kangkung
jagung beras nasi mie bakso sate rendang sambal kecap bawang cabai jahe kunyit kencur serai pandan
kelapa sawit karet kayu besi baja tembaga emas perak intan mutiara pasir tanah lumpur gunung lembah
sungai laut pantai pulau hutan sawah ladang kota desa jalan jembatan rumah pintu jendela atap lantai
dinding meja kursi lemari kasur bantal selimut lampu kipas kulkas kompor pisau sendok garpu piring
gelas botol ember sapu kain baju celana rok topi sepatu sandal tas dompet kunci jam cincin kalung
gelang kaca cermin sabun sampo sikat handuk biru merah hijau kuning hitam putih cokelat ungu jingga
muda tua baru lama besar kecil tinggi rendah panjang pendek lebar sempit berat ringan cepat lambat
panas dingin hangat sejuk kering basah bersih kotor terang gelap ramai sepi
satu dua tiga empat lima enam tujuh delapan sembilan sepuluh seratus seribu juta miliar
""".split())

# Bahasa sehari-hari / singkatan chat yang lazim dan tidak dihasilkan morfologi.
INFORMAL = set("""
gak nggak enggak aja ajah bgt banget kalo kalau gimana gimana2 gitu gitulah udah udahin
dapet dapetin bikin bikinin cuma cuman kayak kayaknya doang sih deh dong kok nih tuh
yah nah lho loh dong mah lah kah pun yg dgn sdh blm gpp makasih trims
mantap keren gabut mager santuy cuan hehe wkwk hmm hm oke ok ya iya
halo hai helo selamat pagi siang sore malam permisi tolong mohon maaf
dll dsb dkk spt misal misalnya contohnya kayak gini begitu begitulah
""".split())

# Kata fungsi & kata umum yang tidak selalu lolos validasi morfologi.
FUNCTION_WORDS = set("""
di ke dari dan yang untuk dengan pada dalam oleh ini itu adalah atau juga saja
sudah belum masih akan telah pernah sedang agar supaya karena sebab jika kalau bila
ketika saat setelah sebelum sampai hingga sejak meskipun walaupun namun tetapi tapi
melainkan bahkan hanya justru malah pula lagi sangat terlalu lebih paling cukup hampir
sekitar antara terhadap mengenai tentang bagi buat secara sebagai seperti bagai serta
yaitu yakni misalnya contohnya tak tidak bukan jangan harus bisa dapat mampu mau ingin
perlu wajib boleh mungkin barangkali tentu pasti segera selalu sering jarang kadang
terkadang kini sekarang dahulu dulu kemarin besok lusa nanti tadi kemudian lalu akhirnya
pertama kedua terakhir selanjutnya berikutnya sebelumnya kepada tanpa demi lewat melalui
atas bawah depan belakang samping seberang ujung tengah luar aku saya kamu engkau kau
anda dia ia kita kami kalian mereka beliau siapa apa kapan bagaimana mengapa kenapa
berapa mana bahwa pun lah kah dong sih deh ya yah nih tuh kok lho loh nah ayo mari tolong
mohon maaf silakan terima kasih halo hai para setiap semua beberapa banyak sedikit
""".split())

# Kata serapan / istilah teknologi yang lazim dipakai apa adanya.
LOANWORDS = set("""
online offline download upload update upgrade install uninstall setting settings
email password username login logout akun user admin server client browser website
file folder data database software hardware aplikasi program sistem memori prosesor
internet wifi bluetooth kabel charger baterai ponsel tablet laptop komputer keyboard
mouse monitor printer scanner webcam headset speaker mikrofon kamera video audio
link klik chat chatroom posting komentar status caption story feed timeline
startup startup mindset deadline meeting online shop marketplace ecommerce
smartphone android ios windows linux macos google youtube whatsapp telegram instagram
facebook twitter tiktok netflix spotify zoom discord github git coding ngoding
prompt chatbot kecerdasan buatan machine learning deep learning dataset
""".split())


# --------------------------------------------------------------------------- #
# Morfologi Bahasa Indonesia
# --------------------------------------------------------------------------- #
def vowel_groups(word: str) -> int:
    groups, prev = 0, False
    for ch in word:
        is_v = ch in VOWELS
        if is_v and not prev:
            groups += 1
        prev = is_v
    return max(1, groups)


def nasal(root: str, kind: str) -> str:
    """meN- / peN- dengan perubahan fonologis."""
    if root.startswith(("ny", "ng")):
        return kind + root
    if root.startswith("kh"):
        return kind + "ng" + root
    if vowel_groups(root) == 1:
        return kind + "nge" + root
    c = root[0]
    if c == "s":
        return kind + "ny" + root[1:]
    if c in "ptk":
        return kind + {"p": "m", "t": "n", "k": "ng"}[c] + root[1:]
    if c in VOWELS or c in "gh":
        return kind + "ng" + root
    if c in "bfv":
        return kind + "m" + root
    if c == "m":
        return kind + "m" + root
    if c == "n":
        return kind + "n" + root
    if c in "cdjz":
        return kind + "n" + root
    return kind + root  # l, r, w, y


def ber_form(root: str) -> str:
    if root.startswith("r"):
        return "be" + root
    if root == "ajar":
        return "belajar"
    return "ber" + root


def ter_form(root: str) -> str:
    return ("te" + root) if root.startswith("r") else ("ter" + root)


def per_form(root: str) -> str:
    return ("pe" + root) if root.startswith("r") else ("per" + root)


def derive(root: str) -> set[str]:
    me, pe = nasal(root, "me"), nasal(root, "pe")
    be, te, pr = ber_form(root), ter_form(root), per_form(root)
    out = {
        me, pe, be, te, pr,
        "di" + root, "ke" + root, "se" + root,
        root + "kan", root + "an",
        me + "kan", me + "i", pe + "an",
        "ke" + root + "an", be + "an", pr + "an",
        "di" + root + "kan", "di" + root + "i", te + "kan",
    }
    if not root.endswith("i"):
        out.add(root + "i")
    return {w for w in out if WORD_RE.fullmatch(w)}


# --------------------------------------------------------------------------- #
# I/O
# --------------------------------------------------------------------------- #
def download(url: str, dest: Path, offline: bool) -> Path:
    dest.parent.mkdir(parents=True, exist_ok=True)
    if dest.exists() and dest.stat().st_size > 0:
        return dest
    if offline:
        sys.exit(f"Cache tidak ditemukan: {dest}. Jalankan sekali tanpa --offline.")
    print(f"Mengunduh {url} ...")
    urllib.request.urlretrieve(url, dest.with_suffix(dest.suffix + ".part"))
    dest.with_suffix(dest.suffix + ".part").rename(dest)
    return dest


def read_freq(path: Path) -> dict[str, int]:
    out: dict[str, int] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        parts = line.rsplit(" ", 1)
        if len(parts) != 2:
            continue
        try:
            out[parts[0]] = int(parts[1])
        except ValueError:
            continue
    return out


def read_tokens(path: Path) -> list[str]:
    if not path.exists():
        return []
    out: list[str] = []
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if line and not line.startswith("#"):
            out.extend(line.split())
    return out


def read_phrases(path: Path) -> list[str]:
    if not path.exists():
        return []
    return [l.strip() for l in path.read_text(encoding="utf-8").splitlines()
            if l.strip() and not l.startswith("#")]


# --------------------------------------------------------------------------- #
# Pembangunan kamus
# --------------------------------------------------------------------------- #
def build_words() -> tuple[list[tuple[str, int]], dict]:
    id_freq = read_freq(download(ID_URL, CACHE / "id_50k.txt", args.offline))
    read_freq(download(EN_URL, CACHE / "en_50k.txt", args.offline))  # validasi cache
    roots_file = download(ROOT_URL, CACHE / "kata-dasar.txt", args.offline)
    roots = {w for w in (l.strip().lower() for l in roots_file.read_text(encoding="utf-8").splitlines())
             if WORD_RE.fullmatch(w)}

    corpus = {w: f for w, f in id_freq.items() if WORD_RE.fullmatch(w)}

    # Kandidat bentuk berimbuhan: dari kata dasar + dari kata korpus yang umum.
    pseudo_roots = set(roots)
    for w, _ in sorted(corpus.items(), key=lambda p: -p[1])[:20000]:
        pseudo_roots.add(w)

    valid: set[str] = set(roots)
    for r in pseudo_roots:
        for form in derive(r):
            if form in corpus:
                valid.add(form)

    # Filter korpus yang lolos validasi morfologi.
    kept: dict[str, int] = {}
    for w, f in corpus.items():
        if w in valid:
            kept[w] = max(kept.get(w, 0), f)

    seeds = [w.strip().lower() for w in read_tokens(ROOT / "tools" / "data" / "seed_words.txt")]

    # Kata fungsi memakai frekuensi korpus asli agar peringkat sarannya benar.
    for w in FUNCTION_WORDS:
        if WORD_RE.fullmatch(w):
            kept[w] = max(kept.get(w, 0), corpus.get(w, 50_000))
    for w in (INFORMAL | LOANWORDS):
        if WORD_RE.fullmatch(w):
            kept[w] = max(kept.get(w, 0), corpus.get(w, 2_000), 2_000)

    # Kata dasar yang tidak muncul di korpus tetap disertakan dengan bobot kecil
    # supaya tersedia untuk pelengkapan kata.
    for w in roots:
        if w not in kept:
            kept[w] = 250
    for w in seeds:
        if WORD_RE.fullmatch(w):
            kept[w] = max(kept.get(w, 0), 2_000)

    words = sorted(kept.items(), key=lambda p: (-p[1], p[0]))
    stats = {
        "sumber_korpus": len(id_freq),
        "kata_dasar_sastrawi": len(roots),
        "kandidat_morfologi": len(pseudo_roots),
        "kata_valid_di_korpus": len(valid & set(corpus)),
        "kata_tambahan_kurasi": len([w for w in seeds + sorted(INFORMAL | LOANWORDS) if WORD_RE.fullmatch(w)]),
        "total_kata": len(words),
        "total_frekuensi": sum(f for _, f in words),
    }
    return words, stats


def build_bigrams() -> list[tuple[str, str, int]]:
    counts: dict[tuple[str, str], int] = {}
    for phrase in read_phrases(ROOT / "tools" / "data" / "seed_bigrams.txt"):
        parts = [w for w in phrase.lower().split() if re.fullmatch(r"[a-z-]{1,24}", w)]
        for a, b in zip(parts, parts[1:]):
            counts[(a, b)] = counts.get((a, b), 0) + 1
    return [(a, b, 40 * n + 20) for (a, b), n in sorted(counts.items())]


def write_assets(words, bigrams) -> None:
    ASSETS.mkdir(parents=True, exist_ok=True)
    head = (
        "# BitKeyboard - kamus Bahasa Indonesia\n"
        "# Sumber: FrequencyWords (CC-BY-SA 4.0) untuk frekuensi; Sastrawi (MIT) untuk kata dasar.\n"
        "# Format: kata<TAB>frekuensi. Dibangun ulang: python3 tools/build_dictionary.py\n"
    )
    body = "".join(f"{w}\t{f}\n" for w, f in words)
    (ASSETS / "dictionary_id.txt").write_text(head + body, encoding="utf-8")

    bhead = "# BitKeyboard - bigram Bahasa Indonesia (kurasi awal)\n# Format: kata1<TAB>kata2<TAB>bobot\n"
    (ASSETS / "bigrams_id.txt").write_text(
        bhead + "".join(f"{a}\t{b}\t{n}\n" for a, b, n in bigrams), encoding="utf-8"
    )


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--offline", action="store_true", help="jangan unduh, gunakan cache")
    args = ap.parse_args()

    words, stats = build_words()
    bigrams = build_bigrams()
    write_assets(words, bigrams)
    stats.update(bigrams=len(bigrams),
                 asset_bytes=(ASSETS / "dictionary_id.txt").stat().st_size)
    BUILD.mkdir(exist_ok=True)
    (BUILD / "dictionary_stats.json").write_text(json.dumps(stats, indent=2, ensure_ascii=False))
    print(json.dumps(stats, indent=2))
    print("Teratas:", ", ".join(w for w, _ in words[:30]))
