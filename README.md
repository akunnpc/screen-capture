# Kirim Layar

Tangkap layar HP Android, encode (H.264 + AAC), lalu kirim lewat RTMP atau simpan ke file MP4.
Bisa dipasang dan diuji lewat kabel USB, tanpa internet dan tanpa akun platform.

> **Nama kerja.** "Kirim Layar" dan package `app.kirimlayar.capture` hanyalah nama sementara.
> Ganti sebelum dibagikan, dan lakukan pemeriksaan merek sendiri (jalankan `/replica-brand`).

## Status jujur

- Kode ini **belum pernah dikompilasi atau dijalankan di HP**. Lingkungan tempat ia ditulis tidak punya Android SDK.
- Yang sudah dicek: semua file XML valid, semua `R.string`, `R.id`, dan `R.drawable` yang dipakai Kotlin ada, YAML workflow valid, skrip shell lolos pemeriksaan sintaks, dan logika unit test sudah dicocokkan dengan padanannya.
- API pustaka encoder (RootEncoder) dicocokkan dengan contoh resmi di wiki-nya (Juni 2025), tetapi **versi yang dipakai (2.7.5) belum diuji** terhadap kode ini. Kemungkinan terbesar error kompilasi ada di sana. Lihat Pemecahan masalah.

## Isi

| Berkas | Fungsi |
| --- | --- |
| `app/.../StreamService.kt` | service latar depan: MediaProjection, encoder, RTMP, rekam MP4 |
| `app/.../MainActivity.kt` | layar setelan, izin, tombol mulai dan berhenti |
| `app/.../Presets.kt` | logika murni (kualitas, rotasi, validasi URL), punya unit test |
| `.github/workflows/android.yml` | GitHub Actions: unit test, build APK debug, unggah APK |
| `scripts/usb-stream.sh`, `.bat` | pasang APK lewat USB dan buka jalur RTMP lewat kabel |
| `replica/` | peta recon, arsitektur, matriks fitur, catatan build |

## 1. Membangun APK dengan GitHub Actions

1. Buat repositori baru di GitHub (boleh privat), lalu dari folder ini:
   ```bash
   git init && git add . && git commit -m "kirim layar awal"
   git branch -M main
   git remote add origin https://github.com/NAMA-ANDA/kirim-layar.git
   git push -u origin main
   ```
2. Buka tab **Actions**. Workflow "Build APK" berjalan otomatis (atau jalankan manual lewat *Run workflow*).
3. Setelah hijau, buka run-nya dan unduh artifact **kirim-layar-debug-apk** (berisi `app-debug.apk`).
4. Jika merah, buka langkah yang gagal, salin log error-nya, dan kirim ke saya.

Workflow memasang Gradle 8.11.1 langsung, jadi tidak perlu `gradle-wrapper.jar`.
APK ditandatangani dengan `app/debug.keystore` yang ikut di repo, supaya tanda tangan sama di setiap build. Itu kunci debug publik: hanya untuk uji pribadi, jangan untuk rilis.

## 2. Memasang lewat USB

Di HP: aktifkan **Opsi pengembang** lalu **Debug USB**, sambungkan kabel, dan setujui dialog di layar.

Di PC (perlu `adb` dari Android platform-tools):

```bash
./scripts/usb-stream.sh path/ke/app-debug.apk        # Linux/macOS
scripts\usb-stream.bat path\ke\app-debug.apk         # Windows
```

Skrip memasang APK (`adb install -r -g`, izin runtime ikut diberikan), menjalankan `adb reverse tcp:1935 tcp:1935`, lalu membuka aplikasinya.
Kalau muncul `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, jalankan `adb uninstall app.kirimlayar.capture` dan ulangi.

## 3. Uji tanpa internet: video lewat kabel USB

`adb reverse` membuat alamat `127.0.0.1:1935` **di HP** sampai ke `localhost:1935` **di PC** lewat kabel.

1. Jalankan server RTMP di PC pada port 1935. Contoh: MediaMTX (server RTMP/RTSP open source) atau nginx-rtmp. Baca dokumentasi servernya untuk aturan publish.
2. Jalankan skrip USB di atas.
3. Di aplikasi isi URL `rtmp://127.0.0.1:1935/live/test`, pilih 720p, 30 fps, sumber audio Mikrofon, lalu **Mulai**. Setujui dialog tangkap layar.
4. Di PC tonton dengan `ffplay rtmp://localhost:1935/live/test` atau VLC.

### Uji tanpa server sama sekali

Kosongkan URL, centang **Rekam juga ke file MP4**, lalu Mulai. Layar direkam ke file lokal. Ambil lewat USB:

```bash
adb pull /sdcard/Android/data/app.kirimlayar.capture/files/Movies/ ./rekaman
```

Ini cara tercepat membuktikan tangkap layar dan encoder jalan, sebelum mencoba jaringan.

## Sumber audio

- **Mikrofon**: berjalan di Android 8 ke atas.
- **Suara sistem atau game**: Android 10 ke atas. Aplikasi yang melarang penangkapan audio akan tetap senyap.
- **Mikrofon + suara sistem**: dicampur. Ini yang paling dekat dengan "mic bersama suara game".
  Catatan dari wiki RootEncoder: agar mic bisa dipakai bersamaan dengan aplikasi lain yang juga memakai mic (misalnya voice chat game), diperlukan izin Aksesibilitas (Android 10 ke atas). Izin itu sensitif dan **belum** ada di proyek ini.

## Pemecahan masalah

**Error kompilasi yang menyebut `com.pedro...`** (kelas atau parameter tidak ditemukan).
API RootEncoder berubah antar versi. Di `app/build.gradle.kts` ubah satu baris:

```kotlin
val rootEncoderVersion = "2.7.5"
```

Coba berurutan: `2.7.5`, lalu `2.5.5`, lalu `2.4.4`. Versi 2.4.4 tercantum di daftar rilis resmi. Sertakan log error saat meminta bantuan.

**Dependensi `jitpack.io` tidak terunduh.** Coba jalankan ulang workflow; JitPack kadang lambat membangun versi yang belum pernah diminta.

**Layar hitam atau video tidak bergerak.** MediaProjection hanya menghasilkan frame saat layar berubah. Kode sudah memaksa frame rate tetap, tapi lapor kalau tetap terjadi.

**Siaran berhenti sendiri di HP tertentu.** Banyak vendor mematikan proses latar secara agresif. Matikan optimasi baterai untuk aplikasi ini.

**Dialog tangkap layar muncul tiap kali.** Itu perilaku Android, bukan bug. Versi baru meminta persetujuan setiap sesi.

## Yang belum ada

- Sambung ulang otomatis saat jaringan putus (sekarang siaran berhenti dan menampilkan galat).
- Stream key terenkripsi (sekarang disimpan teks biasa di penyimpanan privat, `allowBackup` mati). Rencana: Android Keystore.
- Bitrate adaptif, dua jalur encode (H.265 + H.264), multi-tujuan.
- Overlay (widget chat, gift) di atas siaran. Itu bagian lain dari rencana di `replica/architecture.md`.
- Video lewat USB sebagai kabel data langsung (hanya lewat `adb reverse` ke server RTMP di PC).

## Hal yang perlu diuji manual di HP

| Skenario | Hasil yang diharapkan |
| --- | --- |
| Rekam lokal 720p30, 1 menit | berkas MP4 bisa diputar, video dan audio sinkron |
| Siaran ke server di PC lewat USB | `ffplay` menampilkan layar HP |
| Layar tegak lalu mendatar sebelum Mulai | rasio video mengikuti orientasi |
| Audio Mikrofon, Sistem, Campuran | masing-masing terdengar sesuai pilihan |
| Tekan Berhenti dari notifikasi | siaran berhenti, notifikasi hilang |
| Cabut kabel atau matikan server saat siaran | status galat, tidak crash |
| Android 10, 12, 14 | tidak crash saat mulai |

## Lisensi

Kode di folder ini ditulis baru dan tidak berasal dari aplikasi mana pun. Pustaka pihak ketiga ada di `THIRD_PARTY.md`.
