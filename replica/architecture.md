# Arsitektur: klon StreamingIn Android (versi sendiri dari fitur inti StreamingIn)

Masukan: `recon.md`, `features.csv`. Tanggal: 2026-11-22.

## Keputusan terbesar: local-first

Situs asli menyatakan konfigurasi tersimpan lokal, stream key terenkripsi di perangkat, dan tidak ada data sensitif ke cloud. Klon mengikuti pola itu. Akibatnya backend **kecil**: hanya akun, Pro, Coin, Store, media cloud, relai donasi, dan rilis app. Scene, elemen, koneksi, dan log event ada di HP. Ini mengurangi biaya dan risiko privasi, dan memang yang dijanjikan ke pengguna.

## Stack

| lapisan | pilihan | alasan |
| --- | --- | --- |
| app Android | Kotlin + Jetpack Compose | inti produk (tangkap layar, encoder, jendela mengambang, audio, service latar depan) semuanya API native. Expo yang jadi default skill akan butuh modul native untuk hampir semuanya. |
| mesin siaran | pustaka open source RTMP/encoder (kandidat: RootEncoder) | jangan menulis RTMP dan GL dari nol. Periksa lisensi, perawatan, dan dukungan H.265 sebelum dipilih. |
| render overlay | WebView di luar layar, widget HTML/CSS/JS yang dibundel di app | situs asli juga merender HTML/CSS/JS; widget yang sama bisa dipakai ulang di pendamping desktop nanti |
| data lokal | Room (SQLite) + DataStore | scene, elemen (JSON), log event, ringkasan sesi |
| rahasia lokal | Android Keystore | stream key dan token OAuth terenkripsi, tidak pernah dikirim ke server |
| database awan | Postgres di Supabase, region Singapura | dekat pengguna Indonesia; RLS bawaan; satu database, tanpa microservice |
| auth | Supabase Auth, penyedia Google | situs asli hanya login Google |
| pembayaran | Midtrans atau Xendit (QRIS, e-wallet, VA) | pengganti Stripe default: pasar Rupiah. Jika app dirilis di Play Store, pembelian digital dalam app umumnya wajib Google Play Billing; cek kebijakan terbaru sebelum memilih jalur distribusi. |
| email | Resend | tanda terima dan pengingat Pro berakhir |
| jobs | pg_cron + Supabase Edge Functions | tanpa server tambahan |
| file | Supabase Storage (bucket privat + URL bertanda tangan) | snapshot scene Store, media cloud, APK rilis |
| relai donasi | Supabase Realtime (channel privat per pengguna) | HP tidak bisa menerima webhook masuk, jadi server menerima lalu meneruskan |
| hosting | Supabase (backend); Vercel + Next.js (landing dan Store web, tahap akhir) | semua terkelola |

## Skema

Tabel: 17 (lihat `schema.sql`). Aturan akses: **RLS** di semua tabel. Pengguna hanya membaca barisnya sendiri; menulis ke uang, Pro, dan webhook hanya lewat service role atau fungsi `security definer` (`buy_listing`). Ledger Coin hanya-tambah; saldo adalah jumlah delta. Pembelian Store atomik, dikunci per pembeli, tidak bisa minus dan tidak bisa dibeli dua kali (`unique(buyer_id, listing_id)`).

Batasan keras dari recon:
- webhook pembayaran dua kali tidak memberi Pro dua kali: `pro_entitlements.unique(order_id)` dan `orders.unique(provider, provider_ref)`
- donasi dan pembayaran idempoten lewat `webhook_events.unique(source, event_id)`
- bagi hasil kreator 75 persen disimpan di `app_config`, karena sumber asli bertentangan (75 persen vs fee 30 persen)

## API

Notasi: REST = PostgREST di bawah RLS; RPC = fungsi Postgres; FN = Edge Function.

| method path | melakukan | siapa | masukan | keluaran | alur |
| --- | --- | --- | --- | --- | --- |
| (SDK) Auth Google | login | publik | token Google | sesi | F01 |
| GET /rest/v1/profiles | profil sendiri | pengguna | - | profil | F01 |
| PATCH /rest/v1/profiles | ubah nama, bahasa | pengguna | field | profil | F01 |
| POST /rest/v1/rpc/me_status | status Pro, saldo SC, batas | pengguna | - | {is_pro, ends_at, coins, limits} | F01, F06 |
| GET /functions/v1/app-latest | cek update | publik | channel, version_code | rilis atau 204 | F01 |
| GET /rest/v1/products | daftar paket | publik | - | produk | F06 |
| POST /functions/v1/voucher-validate | cek voucher | pengguna | kode, sku | harga akhir | F06 |
| POST /functions/v1/checkout | buat pesanan dan tagihan | pengguna | sku, kode voucher | order_id, QR atau tautan bayar | F06 |
| GET /rest/v1/orders | polling status | pengguna | id | status | F06 |
| GET /rest/v1/coin_ledger | riwayat SC | pengguna | - | baris | F06 |
| GET /rest/v1/store_listings | jelajah Store | pengguna | filter | daftar | F07 |
| GET /rest/v1/store_listing_media | media produk | pengguna | listing_id | media | F07 |
| POST /rest/v1/rpc/buy_listing | beli desain | pengguna | listing_id | purchase_id | F07 |
| GET /rest/v1/store_purchases | pustaka beli | pengguna | - | baris | F07 |
| POST /functions/v1/store-download | URL snapshot scene | pemilik | listing_id | URL berumur pendek | F07 |
| POST /rest/v1/store_reviews | beri ulasan | pembeli | rating, isi | ulasan | F07 |
| POST /functions/v1/store-publish | buat produk dari scene | Pro dan kreator terverifikasi | scene JSON, meta | listing (in_review) | F08 |
| PATCH /rest/v1/store_listings | ubah draf atau ditolak | kreator | field | listing | F08 |
| POST /functions/v1/admin-review-listing | setujui atau tolak | admin | id, keputusan, catatan | listing | F08 |
| POST /functions/v1/payout-request | minta pencairan (min 100 SC) | kreator | metode | payout | F08 |
| POST /functions/v1/media-upload-url | URL unggah bertanda tangan | Pro | jenis, ukuran, mime | URL | F02 |
| GET /rest/v1/cloud_media | pustaka media | pengguna (Pro untuk yang terkunci) | jenis | daftar | F02 |
| DELETE /rest/v1/cloud_media | hapus media sendiri | pemilik | id | - | F02 |
| POST /functions/v1/donation-endpoint | buat endpoint dan rahasia | pengguna | penyedia | URL webhook (tampil sekali) | F09 |
| DELETE /rest/v1/donation_endpoints | cabut endpoint | pemilik | id | - | F09 |
| WS Realtime channel `donations:{user_id}` | terima donasi yang diteruskan | pemilik (channel privat) | - | event | F09 |
| POST /functions/v1/account-delete | hapus akun dan data | pengguna (login baru) | konfirmasi | 202 | F10 |

Jumlah rute: 27 (belum termasuk 2 webhook masuk di bawah).

**Webhook masuk**
- `POST /functions/v1/webhook-payment/{provider}` (Midtrans, Xendit): verifikasi tanda tangan, idempoten, tandai pesanan lunas, beri Pro atau Coin dalam satu transaksi.
- `POST /functions/v1/webhook-donation/{public_token}` (7 penyedia donasi): verifikasi tanda tangan per penyedia, dedup, teruskan ke Realtime, **jangan simpan isi donasi**. Format tanda tangan tiap penyedia berbeda dan belum diverifikasi; baca dokumentasi resmi masing-masing. Mulai dari Saweria dan Trakteer.

**Webhook keluar**: tidak ada di MVP.

**Integrasi di perangkat (bukan di server), hanya API resmi dengan token pengguna sendiri**
- YouTube Data API (siaran, chat live). Kuota harian terbatas; ukur penggunaan.
- Twitch EventSub (transport WebSocket bisa dipakai dari klien). Verifikasi saat build.
- Kick dan Facebook: verifikasi API resmi dan cakupan izin saat build; tandanya `could`.
- **TikTok: tidak ada integrasi event.** Tidak ada API publik resmi. Siaran ke TikTok tetap bisa lewat URL ingest dan key yang ditempel pengguna.
- Instagram: hanya siaran, tanpa event.

**Jobs**
| nama | jadwal | melakukan |
| --- | --- | --- |
| expire_pending_orders | tiap 15 menit | pesanan `pending` lewat `expires_at` jadi `expired` |
| pro_expiry_reminder | harian 09:00 WIB | email pengingat 3 hari sebelum Pro berakhir |
| payout_batch | tanggal 1, 02:00 WIB | hitung kreator yang memenuhi minimum, buat baris `requested` untuk disetujui admin |
| cleanup_webhook_events | harian | hapus `webhook_events` lebih dari 30 hari |

## Bagian yang menggigit

- **Zona waktu:** semua `timestamptz` UTC. Jadwal bulanan dan pengingat dihitung dalam WIB (Asia/Jakarta, tanpa DST), tapi pengguna bisa di WITA atau WIT; tampilkan sesuai perangkat.
- **Idempotensi:** webhook pembayaran dan donasi datang dua kali. Kunci unik di `webhook_events`, `orders`, `pro_entitlements`, dan `coin_ledger`.
- **Balapan:** dua tap "beli" bersamaan. `buy_listing` memakai advisory lock per pembeli plus kunci unik. Saldo SC tidak boleh minus.
- **Encoder di perangkat:** dukungan kodek dan performa berbeda per chipset. Perlu uji di minimal HP kelas bawah, menengah, atas. Panas dan baterai membatasi 1080p60; sediakan preset turun otomatis.
- **Proses dimatikan sistem:** banyak HP Android di Indonesia memakai kustomisasi vendor yang agresif mematikan proses latar. Wajib service latar depan dengan jenis yang benar, panduan whitelist baterai, dan penyambungan ulang.
- **Izin Android:** tangkap layar butuh persetujuan pengguna (versi baru memintanya per sesi), tampil di atas app lain butuh izin khusus, penangkapan audio game dibatasi versi dan app tertentu. Fitur "mic bersama suara game" di versi penuh kemungkinan butuh izin yang diberikan lewat USB (tebakan, belum terverifikasi).
- **Batas laju:** kuota YouTube (polling chat), dan batas masing-masing platform. Polling adaptif.
- **Pencairan dan pajak:** pencairan ke kreator membawa kewajiban perpajakan dan KYC; putuskan sebelum membuka Store.
- **Distribusi:** APK di luar Play Store (seperti aslinya, lewat GitHub dan USB) mengurangi kepercayaan dan memaksa pengguna melewati peringatan. Jika masuk Play Store, aturan pembayaran dan izin sensitif berlaku.
- **Privasi dan hapus akun:** `orders`, `coin_ledger`, dan `store_purchases` memakai `on delete restrict` agar catatan keuangan tidak hilang. Fungsi hapus akun harus menganonimkan (bukan cascade) lalu menghapus profil, media, dan endpoint. Sesuaikan dengan UU PDP.
- **Relai donasi vs janji "tidak ada data ke cloud":** relai lewat server memang mengirim nama dan nominal donatur. Jangan simpan isinya, TTL pendek, dan beri tahu pengguna di kebijakan privasi.
- **Pencarian, offline:** pencarian Store cukup `ilike` dan GIN pada tag. App harus berfungsi penuh offline kecuali Store, Pro, dan donasi.

## Urutan pembangunan

1. **Irisan vertikal (jelek tapi ujung ke ujung):** S01, S02, S03 (minimal), S05 dengan satu elemen teks dan satu widget chat, S09 (login YouTube plus tempel RTMP), S10, S11, S12. Tabel: `profiles`, `app_config`. Rute: login, `me_status`. Di perangkat: tangkap layar, encoder, komposit overlay WebView, kirim RTMP ke ingest uji, chat YouTube menggerakkan widget. Tujuan: membuktikan komposit dan encode di tiga kelas HP sebelum apa pun dibangun di atasnya.
2. **Must-have** (23 baris di `features.csv`), per area:
   - Overlay: S04 sampai S08, elemen statis dan dinamis inti, event binding, template resmi gratis.
   - Siaran: Mode A (jendela mengambang), RTMP kustom, stream key terenkripsi.
   - Akun: batas tier gratis, hapus akun (S20).
   - Pro: `products`, `orders`, `pro_entitlements`, `checkout`, `webhook-payment`, `me_status`, S18.
3. **Should-have:** rekaman dan S13, multistream dan cek upload, event Twitch, donasi (S14, relai), ekspor log event, Coin dan Store (S15 sampai S17, `buy_listing`, ulasan, publish, payout), media cloud, mic bersama suara game, update dalam app.
4. **Could-have:** Kick, Facebook, dua jalur encode H.265 plus H.264, transisi morph, filter wajah, URL browser source dan pendamping desktop, voucher referral.
5. **Perbaikan** yang ditemukan `/replica-entrepreneur` setelah dijalankan.

Setiap milestone memakai S-ID, tabel, dan rute di atas sebagai daftar kerja.
