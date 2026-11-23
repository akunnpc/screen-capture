# Catatan build

Irisan: inti tangkap layar, encoding, dan pengiriman RTMP (M0 di `architecture.md`), dengan jalur USB lewat `adb reverse`.

| ID | tanggal | status | yang belum | catatan |
| --- | --- | --- | --- | --- |
| S01 | 2026-11-23 | sebagian | halaman pengantar | izin diminta saat tombol Mulai, tanpa layar pengantar |
| S02 | 2026-11-23 | tidak dibuat | semuanya | bukan bagian irisan encoder |
| S10 | 2026-11-23 | sebagian | banyak tujuan, kunci terenkripsi, cek upload | satu kolom URL, kualitas, fps, bitrate, sumber audio |
| S11 | 2026-11-23 | sebagian | tombol mengambang, ganti scene | tombol Mulai dan Berhenti, notifikasi dengan aksi Berhenti |
| S12 | 2026-11-23 | tidak dibuat | semuanya | belum ada event |

## Lebih sulit dari perkiraan

- Versi pustaka encoder: API berubah antar versi dan tidak bisa dicoba di lingkungan penulisan. Versi 2.7.5 dipilih karena ada di changelog yang terlihat, bukan karena sudah diuji.
- Urutan Android 14: izin tangkap layar harus sudah diberikan sebelum `startForeground` bertipe `mediaProjection`, dan callback harus didaftarkan sebelum layar virtual dibuat. Kode mengikuti urutan itu, tetapi belum dibuktikan di perangkat.

## Belum diuji sama sekali

Kompilasi, jalan di HP, kodek per chipset, panas, dan baterai.
