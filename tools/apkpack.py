#!/usr/bin/env python3
"""Bungkus APK hasil aapt2 + classes.dex menjadi APK selaras (zipalign 4 byte).

APK dari `aapt2 link` belum berisi kode, dan `classes.dex` harus ditambahkan
sebagai entri root. Skrip ini menulis ulang arsip zip sehingga entri yang
disimpan tanpa kompresi (resources.arsc, .dex, .arsc) berada pada batas 4 byte,
seperti hasil `zipalign -p 4`.

Pemakaian:
    python3 tools/apkpack.py BASE_APK OUT_APK ENTRY:FILE [ENTRY:FILE ...]
"""
import sys
import zipfile
import zlib
import struct

ALIGN = 4

def pack(base_apk, out_apk, extras):
    with zipfile.ZipFile(base_apk) as src:
        existing = {info.filename: src.read(info.filename)
                    for info in src.infolist()}
    for entry, path in extras:
        with open(path, 'rb') as handle:
            existing[entry] = handle.read()

    # Urutan: resources.arsc dulu (direkomendasikan), lalu AndroidManifest.xml,
    # lalu sisanya. Semua entri .dex dan .arsc disimpan tanpa kompresi.
    def priority(name):
        if name == 'resources.arsc':
            return (0, name)
        if name == 'AndroidManifest.xml':
            return (1, name)
        if name.startswith('classes'):
            return (2, name)
        return (3, name)

    names = sorted(existing, key=priority)

    with open(out_apk, 'wb') as out:
        with zipfile.ZipFile(out, 'w') as dst:
            for name in names:
                data = existing[name]
                stored = name.endswith('.dex') or name in ('resources.arsc', 'AndroidManifest.xml')
                info = zipfile.ZipInfo(name, date_time=(2026, 1, 1, 0, 0, 0))
                info.compress_type = zipfile.ZIP_STORED if stored else zipfile.ZIP_DEFLATED
                if stored:
                    # Header lokal 30 byte + nama + extra 4 byte; tambah padding
                    # agar offset data kelipatan ALIGN.
                    base = dst.fp.tell() + 30 + len(name.encode()) + 4
                    pad = (-base) % ALIGN
                    info.extra = struct.pack('<HH', 0xD935, pad) + b'\x00' * pad
                dst.writestr(info, data, compresslevel=9)

if __name__ == '__main__':
    if len(sys.argv) < 4:
        print(__doc__)
        raise SystemExit(2)
    extras = []
    for arg in sys.argv[3:]:
        entry, path = arg.split(':', 1)
        extras.append((entry, path))
    pack(sys.argv[1], sys.argv[2], extras)
    print('APK ditulis:', sys.argv[2])
