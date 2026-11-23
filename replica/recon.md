# Peta recon: StreamingIn (Android)

Cakupan: aplikasi Android StreamingIn (build "Full version (USB)"): desain overlay, siaran langsung dari HP, event live, dan akun Pro/Coin/Store.
Untuk: asumsi, produk milik pengguna sendiri untuk streamer Indonesia, Android lebih dulu. Ubah kalau salah.
Tanggal: 2026-11-22

## Cara kerja recon ini (dan batasnya)

- Hanya sumber publik. Tidak ada akun StreamingIn yang dipakai.
- APK yang diunggah **tidak dibongkar, tidak dibaca, dan isinya tidak disalin**. Aturan skill melarang dekompilasi. APK hanya dipakai untuk mengenali aplikasinya.
- Halaman Terms of Service (diperbarui 11 Nov 2026) dimuat lewat JavaScript, jadi isinya **tidak terbaca**. Klausul soal membangun produk pesaing belum diperiksa. Baca sendiri di https://streamingin.com/terms sebelum membangun.
- Tangkapan layar Android tidak ada. Layar Android di bawah adalah **inferensi** dari tur desktop dan deskripsi situs. Itu ditandai di kolom confidence.
- Halaman `/guide` bagian "Full Documentation" tampak seperti draf generik (misalnya dukungan Windows 10 "sampai 2025", dan halaman API dengan endpoint `api.streamingin.com`). Itu **tidak dijadikan bukti**. Bagian yang konsisten dengan halaman utama dan FAQ yang dipakai.

## Sumber

| # | sumber | URL | catatan |
| --- | --- | --- | --- |
| 1 | situs utama (fitur, harga, FAQ, unduhan) | https://streamingin.com/ | sumber terkuat; memuat deskripsi versi Android |
| 2 | bantuan dan FAQ | https://streamingin.com/support/ | 9 pertanyaan; Coin, bagi hasil 75 persen, Store |
| 3 | panduan dan tur interaktif | https://streamingin.com/guide | tur 20 langkah memperlihatkan UI desktop; sisanya draf |
| 4 | Terms of Service | https://streamingin.com/terms | tidak terbaca (dimuat JS) |
| 5 | video tutorial YouTube (hanya judul dan cuplikan) | https://www.youtube.com/watch?v=FAGZNYPqPtw | tidak ditonton; bisa dipakai pengguna untuk tangkapan layar Android |
| 6 | dokumentasi pihak ketiga soal API TikTok LIVE | https://github.com/EulerStream/TikTok-Live-Api | bukti bahwa TikTok tidak punya API publik resmi untuk chat/gift |
| 7 | halaman daftar rilis APK di GitHub | (tautan di situs utama) | sengaja tidak dibuka; isinya binary |

## Inti produk (core loop)

Streamer mendesain satu overlay yang bereaksi pada chat, gift, dan donasi, lalu menyiarkannya langsung dari HP ke satu atau banyak platform. Yang dibayar pengguna adalah Pro: scene tanpa batas, multi-siaran, dan multi-koneksi event.

## Layar (Android, inferensi)

| ID | layar | cara menuju | tujuan | komponen utama | status yang terlihat | confidence |
| --- | --- | --- | --- | --- | --- | --- |
| S01 | Pengantar dan izin | pertama dibuka | jelaskan mode, minta izin (tampil di atas app lain, tangkap layar, mikrofon, kamera, notifikasi) | stepper, tombol izin | belum diizinkan, ditolak | medium |
| S02 | Masuk dengan Google | S01 atau tombol Sign in | login satu-satunya | tombol Google | keluar, masuk, gagal | high (situs: "login dengan akun Google") |
| S03 | Beranda | setelah masuk | ringkasan, mulai cepat, status Pro | kartu aksi, banner tier | kosong, terisi | guess |
| S04 | Daftar scene | tab Scene | kelola scene dan preset | daftar, tombol New Scene, badge aktif | kosong, terisi, batas gratis tercapai | high (dari tur) |
| S05 | Editor kanvas | buka scene | susun elemen di kanvas | kanvas, toolbar, zoom, layer | kosong, terisi, memuat | high |
| S06 | Perpustakaan elemen | tombol tambah di S05 | pilih elemen statis atau dinamis | grid elemen, pencarian | gratis, terkunci Pro | high |
| S07 | Properti elemen | pilih elemen | ukuran, gaya, event binding, audio | panel, slider, pemilih warna | per jenis elemen | high |
| S08 | Template | sub-tab Templates | mulai dari desain jadi | kartu template, filter kategori | gratis, terkunci | high |
| S09 | Koneksi | tab Koneksi | hubungkan platform siaran dan event | tab per platform, status, tombol login | belum tersambung, tersambung, error, kuota habis | high |
| S10 | Setelan siaran | dari S09 atau Siaran | pilih tujuan, URL ingest, key, rasio, cek upload | daftar tujuan, input terenkripsi, peringatan bandwidth | kosong, valid, upload kurang | medium |
| S11 | Kontrol live | Mulai Live | kendali saat siaran (mulai/stop, mute, ganti scene) | tombol mengambang, timer, indikator | siap, live, menyambung ulang, error encoder | medium |
| S12 | Dock event | saat live | event feed dan statistik sesi | feed, penghitung chat/gift/follow/like | menunggu, mengalir, terputus | high |
| S13 | Rekaman dan analitik | tab Rekaman | riwayat sesi, puncak penonton, top pengirim | daftar sesi, grafik, peringkat | kosong, terisi | high |
| S14 | Donasi | tombol Donasi | hubungkan layanan donasi | daftar penyedia, URL webhook, tombol uji | belum diatur, aktif, gagal verifikasi | medium |
| S15 | Creator Store | tab Store | jelajah desain | grid, filter, harga SC | kosong, terisi | medium |
| S16 | Detail produk | S15 | lihat dan beli | pratinjau, harga, tombol beli | belum beli, sudah punya, SC kurang | medium |
| S17 | Terbitkan ke Store | dari scene | kirim desain untuk moderasi | form judul, harga, tag, pratinjau | draf, menunggu, ditolak, terbit | medium |
| S18 | Pro dan Coin | banner terkunci atau menu | beli Pro atau SC, masukkan voucher | kartu paket, input voucher, metode bayar | menunggu bayar, berhasil, gagal | high (harga di situs), medium (alur bayar) |
| S19 | Pengaturan | ikon gerigi | audio, TTS, bahasa, update | daftar setelan | per bagian | medium |
| S20 | Akun | S19 | profil, hapus akun | tombol keluar, hapus | masuk, konfirmasi hapus | medium |

## Alur

```
F01 Pertama kali memakai
    S01 -> S02 -> S03 -> S08 (pilih template gratis) -> S05
    klik jalur normal: 6
    edge: izin ditolak, login Google dibatalkan, tidak ada internet

F02 Mendesain overlay
    S04 (New Scene) -> S05 -> S06 -> S07 (event binding) -> pratinjau mode uji
    klik jalur normal: 7
    edge: melewati 7 elemen di tier gratis, event belum tersambung

F03 Menghubungkan platform
    S09 -> (YouTube: OAuth Google) | (lainnya: tempel URL ingest dan key) -> S10
    klik jalur normal: 4
    edge: kunci TikTok/Instagram kedaluwarsa tiap siaran, kuota YouTube habis

F04 Siaran dari HP (Mode B)
    S10 -> cek upload -> S11 (Mulai Live) -> S12 -> stop -> S13
    klik jalur normal: 3 dari scene yang sudah siap
    edge: upload kurang, encoder gagal di perangkat tertentu, proses dimatikan sistem

F05 Overlay di atas game (Mode A)
    S01 izin tampil di atas app lain -> pilih scene -> tombol mengambang -> siaran lewat app platform
    edge: izin dicabut, game memblokir

F06 Beli Pro atau Coin
    S18 -> pilih paket -> (voucher opsional) -> bayar -> kembali ke app, status Pro aktif
    edge: bayar tertunda, webhook terlambat, voucher tidak valid

F07 Beli desain di Store
    S15 -> S16 -> beli (SC) -> desain masuk ke S04
    edge: SC kurang, sudah dimiliki

F08 Terbitkan desain
    S04 -> S17 -> kirim -> moderasi 1 sampai 3 hari kerja -> terbit
    edge: bukan Pro, belum terverifikasi, ditolak

F09 Hubungkan donasi
    S14 -> pilih penyedia -> salin URL webhook ke dasbor penyedia -> tombol uji
    edge: tanda tangan tidak cocok, event terkirim dua kali

F10 Hapus akun
    S19 -> S20 -> konfirmasi -> data dihapus
```

## Komponen

| komponen | varian | status | dipakai di |
| --- | --- | --- | --- |
| Tombol | primer, sekunder, ghost, bahaya | default, ditekan, fokus, nonaktif, memuat | semua |
| Kanvas dengan handle (pindah, ubah ukuran, putar) | portrait, landscape | dipilih, dikunci, tersembunyi | S05 |
| Kartu elemen | statis, dinamis, terkunci Pro | default, dipilih | S06 |
| Kartu template atau produk | gratis, berbayar, dimiliki | default, memuat | S08, S15 |
| Panel properti | per jenis elemen | terbuka, tertutup | S07 |
| Pemilih warna, slider, toggle | - | default, nonaktif | S07, S19 |
| Tab platform | TikTok, YouTube, Facebook, Kick | tersambung, putus | S09 |
| Input rahasia (stream key) | - | tersembunyi, tampil, tersalin | S10 |
| Indikator bandwidth | cukup, kurang | mengukur | S10 |
| Tombol live mengambang | idle, live | berkedip, error | S11 |
| Baris event | chat, gift, follow, like, donasi | masuk | S12 |
| Kartu statistik | angka, grafik | kosong, terisi | S12, S13 |
| Dialog (konfirmasi, paywall Pro) | - | terbuka | S04, S18, S20 |
| Toast | sukses, error | muncul | semua |

## Model data (inferensi)

```
Pengguna        id, email, nama, bahasa, status_kreator_terverifikasi
                bukti: S02, S17 ("verified creator"), halaman Store guide
                confidence: medium

Langganan Pro   pengguna, paket (30 hari | 360 hari), mulai, berakhir, sumber
                bukti: S18, harga di situs, promo "Free Like a Pro" sampai akhir 2026
                confidence: high

Dompet Coin     pengguna, saldo SC (1 SC = 0,1 USD), riwayat transaksi
                bukti: FAQ "How to get Streamingin Coin"
                confidence: high

Scene/Preset    nama, ukuran kanvas, daftar elemen, transisi, hotkey, aktif
                bukti: S04, S05, tur
                confidence: high (disimpan LOKAL, kata situs)

Elemen          jenis (statis | dinamis), properti, event binding (sumber, event, filter, animasi masuk)
                bukti: S06, S07, tur
                confidence: high

Koneksi         platform, mode (siaran | event), status; stream key terenkripsi lokal
                bukti: S09, S10, FAQ "keys are encrypted on your own machine"
                confidence: high

Event live      jenis (chat | gift | follow | like | share | join | viewers | donasi), pengirim, nilai, waktu
                bukti: S12, tabel dukungan event per platform di situs
                confidence: high

Sesi live       judul, mulai, durasi, penonton puncak, koin gift, follower baru, jumlah chat
                bukti: S13 (tur)
                confidence: high

Produk Store    kreator, judul, deskripsi, kategori, tag, harga SC, status moderasi, snapshot scene
                bukti: S15 sampai S17, FAQ publish
                confidence: medium

Pembelian Store pembeli, produk, harga SC, bagian kreator
                bukti: FAQ (75 persen untuk kreator)
                confidence: medium

Voucher         kode, pemilik, jenis, masa berlaku
                bukti: harga Pro ("Free Referral Voucher as Pro Price for Commission Fee")
                confidence: guess

Media cloud     pemilik, jenis (sfx | gambar | video), ukuran
                bukti: "Cloud SFX & Media" di fitur Pro
                confidence: medium

Endpoint donasi pengguna, penyedia (7), token webhook, rahasia penanda tangan
                bukti: "7 donation services, by signed webhook"
                confidence: medium
```

Relasi: Pengguna 1-n Scene (lokal), Scene 1-n Elemen, Pengguna 1-n Sesi, Pengguna 1-n Langganan Pro, Pengguna 1-n entri Coin, Pengguna (kreator) 1-n Produk Store, Produk 1-n Pembelian.

Ketidakkonsistenan sumber: halaman bantuan menyebut kreator menerima 75 persen, sedangkan panduan Creator Store menyebut fee platform 30 persen (jumlahnya 105). Klon memakai satu angka (75/25) sebagai konstanta konfigurasi.

## Matriks fitur

Lihat `features.csv`. Must: 23, should: 24, could: 13, skip: 10 (total 70).

## Tidak bisa atau tidak boleh dikloning

- **Event TikTok (chat, gift, like, follow, share).** TikTok tidak punya API publik resmi untuk membaca interaksi live; semua pustaka yang ada non-resmi dan bergantung pada protokol internal. Klon hanya memakai API resmi, jadi ini `skip`. Situs asli mengklaim "login dengan TikTok via OAuth"; kami tidak bisa memverifikasi atau meniru klaim itu.
- Animasi dan katalog gift milik TikTok, serta lirik berlisensi.
- Komunitas kreator dan isi Store yang sudah ada (efek jaringan).
- Fitur khusus desktop: kamera virtual, window/game capture, hotkey, dan mirroring AirPlay (proyek GPL-3 terpisah).
- Instagram Live: Meta tidak membuka komentar ke aplikasi, jadi hanya siaran satu arah.

## Ukuran

Layar 20, alur 10, entitas 13. Bagian tersulit:

1. Komposit di perangkat: kamera atau tangkap layar ditambah overlay WebView, di-encode (H.264, opsional H.265) dan dikirim RTMP, di HP kelas bawah sampai atas. Panas, baterai, dan variasi kodek per perangkat.
2. Sumber event: tiap platform punya autentikasi dan kuota sendiri, TikTok tidak punya jalur resmi, dan donasi lewat webhook butuh relai server karena HP tidak bisa menerima webhook masuk.
3. Pembayaran, ledger Coin, dan kebijakan distribusi (APK di luar Play Store vs Play Billing).

Ukuran seluruh produk: **XL (rescope)**. Irisan yang disarankan: Mode B ke RTMP, overlay dengan sekitar 10 elemen, event YouTube dan Twitch, Pro. Itu **L (satu kuartal)**.
