# Prompt OpenCode: Aplikasi Android Native (Kotlin + Jetpack Compose)

> **Update:** source code awal aplikasi sudah tersedia di `android/` (lihat `android/README.md`). Prompt di bawah
> sekarang berfungsi sebagai spesifikasi target. Perintah singkat untuk OpenCode:
> `Baca AGENTS.md, buat android/ berhasil di-build (assembleDebug), lalu lengkapi fitur dari PROMPT_ANDROID_KOTLIN.md yang belum ada.`

Repo ini berisi backend (root) dan folder `android/` kosong tempat aplikasi dibuat.
Jalankan OpenCode di root repo, lalu tempel perintah singkat ini:

```text
Baca AGENTS.md dan PROMPT_ANDROID_KOTLIN.md, lalu kerjakan seluruh tugas di dalamnya sampai selesai.
```

Isi tugas lengkap (ganti `BASE_URL` dengan URL Heroku Anda):

````text
Buatkan aplikasi Android NATIVE (Kotlin) untuk Android Studio, berupa aplikasi chat dengan
fitur dan tampilan menyerupai WhatsApp, terhubung ke backend yang sudah jadi.
Kontrak API REST dan Socket.IO ada di file README.md di ROOT repo ini (dari folder android/ berarti ../README.md,
bagian "API Reference" dan "Socket.IO"). BACA README.md SEPENUHNYA dulu dan patuhi persis (snake_case, nama event,
bentuk payload). Jangan mengubah backend (folder src/, Procfile, package.json) atau membuat endpoint baru.
Seluruh proyek Android dibuat di dalam folder android/ (buka folder itu di Android Studio).
BASE_URL = https://NAMA-APP-ANDA.herokuapp.com (simpan di BuildConfig field, bisa beda untuk debug/release).

## Teknologi
- Kotlin 2.x, Jetpack Compose + Material 3, minSdk 26, targetSdk 35, Gradle Kotlin DSL + version catalog (libs.versions.toml)
- Arsitektur MVVM + Repository (UI -> ViewModel -> Repository -> Remote/Local), StateFlow, Coroutines
- Hilt (DI), Navigation Compose
- Retrofit + OkHttp + kotlinx.serialization (snake_case via @SerialName), interceptor Authorization Bearer
- Socket.IO: io.socket:socket.io-client (Java). Koneksi dengan options.auth = mapOf("token" to jwt),
  transports websocket, reconnect otomatis
- Room (cache lokal chat & pesan agar bisa dibuka offline), DataStore + EncryptedSharedPreferences/Tink untuk token
- Coil (gambar, termasuk URL /api/media/:id?token=JWT), CameraX/ActivityResult untuk kamera & galeri,
  MediaRecorder untuk voice note, ExoPlayer (Media3) untuk audio/video
- WorkManager untuk upload media yang tahan terputus

## Fitur wajib (semua fitur backend)
1. Auth: layar Register (nomor internasional +62..., nama, password) & Login, validasi form,
   token tersimpan aman, auto-login, logout (hapus token & data lokal). 401 dari API -> logout otomatis.
2. Daftar chat: urut aktivitas terbaru, avatar (inisial jika tidak ada foto), nama (direct = peer.display_name),
   pesan terakhir dengan ikon tipe (📷 Foto, 🎤 Pesan suara, 📎 Dokumen), jam/hari, badge unread hijau bulat,
   ikon mute, indikator "mengetik...". Update real-time dari socket, pull-to-refresh, pencarian chat.
3. Layar chat:
   - Gelembung kiri/kanan dengan ekor, pemisah tanggal ("Hari ini", "Kemarin"), daftar terbalik (reverse layout)
     dengan paginasi ke atas memakai before_id.
   - Optimistic send dengan client_id (UUID); cocokkan saat message:new/ack; status gagal + ketuk untuk kirim ulang.
   - Centang: jam + ✓ (sent), ✓✓ abu (delivered), ✓✓ biru (read) dari event message:status.
   - Kirim teks, foto, video, dokumen, voice note (tahan tombol mic, geser ke kiri untuk batal, kunci ke atas).
     Alur media: POST /api/media (multipart) lalu kirim pesan dengan media_id; body = caption.
   - Sheet lampiran (Dokumen, Kamera, Galeri, Audio) berbentuk grid ikon bulat berwarna.
   - Pratinjau gambar layar penuh (zoom), pemutar voice note dengan waveform/seek, pemutar video, buka dokumen via intent.
   - Geser pesan ke kanan untuk membalas; bar balasan di atas input; kutipan di dalam gelembung; ketuk kutipan
     melompat ke pesan asal.
   - Tekan lama pesan -> top bar kontekstual: Balas, Reaksi emoji (baris 6 emoji + tombol lainnya), Salin,
     Edit (pesan sendiri, teks), Hapus untuk semua (pesan sendiri), Info (GET /api/messages/:id/receipts).
     Pesan terhapus tampil "🚫 Pesan ini telah dihapus"; pesan diedit diberi label "diedit".
   - Header: avatar, nama, subjudul "online" / "terakhir dilihat ..." / "sedang mengetik..." (grup: "Nama sedang mengetik...").
   - Kirim event typing dengan debounce; panggil message:read saat layar fokus dan ada pesan baru masuk.
4. Chat baru: layar pilih kontak, pencarian pengguna (GET /api/users/search) dan sinkronisasi kontak telepon
   (izin READ_CONTACTS -> normalisasi ke E.164 -> POST /api/contacts/match, maks 500 per request).
   Ketuk pengguna -> POST /api/chats/direct -> buka chat.
5. Grup: buat grup (pilih anggota -> nama & deskripsi), layar info grup (daftar anggota, badge admin,
   tambah/keluarkan anggota, ubah nama/deskripsi, bisukan, keluar grup). Di grup tampilkan nama pengirim
   berwarna berbeda per orang di atas pesan.
6. Profil & Pengaturan: ubah foto (upload lalu PATCH /api/me), nama, "Info"; layar pengaturan sederhana
   (notifikasi, tema, logout).
7. Notifikasi: saat app berjalan dan socket tersambung, tampilkan notifikasi lokal untuk pesan baru dari chat
   lain (hormati status mute), dengan aksi Balas langsung & Tandai dibaca; ketuk membuka chat (deep link).
   Buat ForegroundService opsional (dapat dimatikan di Pengaturan) agar socket tetap hidup di latar belakang.
   Siapkan antarmuka PushTokenProvider terpisah sehingga FCM bisa ditambahkan nanti TANPA mengubah kode lain
   (backend saat ini hanya mendukung token Expo, jadi JANGAN memanggil /api/me/push-token dengan token FCM).
8. Real-time: satu SocketManager singleton (Flow untuk tiap event). Saat reconnect atau app kembali ke
   foreground, muat ulang daftar chat dan chat yang terbuka, lalu gabungkan ke Room (sumber kebenaran = Room).
9. Mode offline: chat & pesan terakhir tetap tampil dari Room; pesan yang belum terkirim masuk antrean dan
   dikirim saat online kembali.

## Tampilan (mirip WhatsApp)
- Warna: primary hijau tua #075E54 (top bar terang) / #1F2C34 (gelap), aksen hijau #25D366 untuk FAB & badge;
  gelembung keluar #D9FDD3 (terang) / #005C4B (gelap); gelembung masuk putih / #202C33 (gelap);
  latar chat bermotif halus (#EFEAE2 terang / #0B141A gelap). Dukung tema terang, gelap, dan dynamic color OFF secara default.
- Navigasi utama: top app bar dengan judul aplikasi, ikon cari & menu (overflow); FAB hijau "chat baru";
  tab/daftar Chat (cukup satu tab; tanpa Status/Panggilan).
- Input pesan berbentuk kapsul dengan ikon emoji, kolom teks multi-baris, ikon klip & kamera; tombol bulat hijau
  di kanan berubah dari mic menjadi kirim saat ada teks.
- Animasi halus: transisi layar, muncul/hilang bar balasan, swipe-to-reply dengan ikon panah.
- Gunakan font & ikon Material (jangan memakai logo, nama, atau aset resmi WhatsApp). Nama aplikasi: "ChatKu"
  (mudah diganti di strings.xml). Bahasa antarmuka: Indonesia (siapkan strings.xml, bukan hardcode).

## Struktur & aturan kode
- Package com.example.chatku, modul tunggal :app dengan paket: data (remote, local, repository), domain (model),
  di (Hilt), ui (auth, chats, chat, newchat, group, profile, settings, components, theme), service, util.
- Model sesuai README (User, Chat, Message, Reaction, Receipt, dst.) dengan mapper DTO <-> Entity <-> UI model.
- Penanganan error terpusat: tampilkan field "error" dari API via Snackbar; state Loading/Empty/Error di tiap layar.
- Tidak ada rahasia di kode. Izin di manifest: INTERNET, POST_NOTIFICATIONS, RECORD_AUDIO, CAMERA,
  READ_CONTACTS, READ_MEDIA_IMAGES/VIDEO/AUDIO (dengan alur izin runtime yang benar), FOREGROUND_SERVICE.
- ProGuard/R8 rules untuk kotlinx.serialization, Retrofit, Socket.IO. Sertakan .gitignore.
- Tulis unit test untuk mapper, normalisasi nomor telepon E.164, dan logika status centang/ViewModel utama.

## Selesai ketika
- `./gradlew assembleDebug` dan `./gradlew lintDebug` sukses tanpa error; perbaiki semua warning penting.
- Buat README.md aplikasi: cara membuka di Android Studio, cara mengganti BASE_URL, cara build APK/AAB.
- Di akhir, ringkas fitur yang selesai dan yang belum, serta apa yang perlu diuji manual di dua perangkat/emulator.
````
