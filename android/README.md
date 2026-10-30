# ChatKu — Aplikasi Android (Kotlin + Jetpack Compose)

Klien native untuk backend chat di folder induk (Express + Socket.IO + PostgreSQL).
Tampilan bergaya aplikasi chat populer: tema hijau, gelembung chat, centang status, mode gelap.

## Fitur
Login/daftar (nomor E.164), daftar chat + badge unread + pencarian, chat 1-1 & grup, teks/foto/video/dokumen/voice note
(tahan tombol mic), balas (geser kanan), edit, hapus untuk semua, reaksi emoji, info baca per penerima, centang
sent/delivered/read, mengetik, online/terakhir dilihat, sinkronisasi kontak, info grup (anggota, admin, tambah/keluarkan,
keluar, bisukan), profil + foto, notifikasi lokal saat ada pesan (selama aplikasi/proses hidup).

## 1. Atur URL backend (Cloudflare)
Edit `gradle.properties`:
```
BASE_URL=https://chat.domainanda.com
DEFAULT_COUNTRY_CODE=62
```
atau saat build: `gradle assembleDebug -PBASE_URL=https://chat.domainanda.com`.
URL juga bisa diubah dari layar login → **Pengaturan server** (tanpa build ulang).

### Catatan Cloudflare
- Aplikasi hanya butuh URL **HTTPS** yang meneruskan REST **dan WebSocket** ke backend Node.js (kode di folder induk).
  Cloudflare proxy (awan oranye) dan Cloudflare Tunnel mendukung WebSocket secara bawaan.
- Kode backend memakai Node.js + PostgreSQL + Socket.IO sehingga **tidak bisa langsung dijalankan di Cloudflare Workers**.
  Jalankan di server/Heroku/VPS/Cloudflare Containers lalu arahkan domain Cloudflare ke sana (atau pakai Tunnel).
  Jika backend Anda sudah ditulis ulang untuk Workers/Durable Objects, kontraknya harus sama dengan `../README.md`.
- Bila memakai proxy di depan Heroku, set SSL/TLS mode **Full (strict)** dan jangan mengaktifkan aturan yang memblokir
  `User-Agent` okhttp. Batas upload Cloudflare (Free: 100 MB) lebih besar dari batas backend (16 MB).

## 2. Build
**Android Studio (disarankan):** File → Open → pilih folder `android/` → tunggu Gradle sync → Run ▶ (min. JDK 17, Android Studio Ladybug+).
Wrapper `gradle-wrapper.properties` sudah ada; Android Studio mengunduh Gradle 8.9 otomatis.

**Command line:** instal Gradle 8.9 lalu jalankan sekali `gradle wrapper`, kemudian:
```
./gradlew assembleDebug      # APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease    # APK release unsigned; gunakan keystore privat untuk distribusi
./gradlew test               # unit test
```
**GitHub Actions:** `.github/workflows/android.yml` (di root repo) membangun APK debug dan mengunggahnya sebagai artifact.
Set variabel repo `BASE_URL` agar URL ikut tertanam.

Untuk rilis Play Store, tandatangani APK/AAB dengan keystore privat di luar repository dan simpan kredensialnya
di secret CI. Build release tidak lagi memakai debug key.

## Struktur
```
app/src/main/java/com/chatku/app/
  data/   Models, Api (Retrofit), Net, Session, SocketManager (Socket.IO), Repo (state & logika)
  ui/     AppNav, Auth, ChatList, Chat, NewChat, NewGroup, GroupInfo, Profile, Theme, Components
  util/   Format, Notifier, VoiceRecorder, UriRequestBody
```

## Batasan yang diketahui
- Notifikasi hanya muncul saat proses aplikasi masih hidup. Notifikasi saat aplikasi benar-benar tertutup butuh FCM;
  backend saat ini hanya mengirim push ke token Expo, jadi perlu penambahan di backend.
- Pesan tidak di-cache ke database lokal (Room); daftar chat dan 50 pesan terakhir dimuat ulang dari server.
- Voice note: lepas tombol untuk mengirim (rekaman < 1 detik dibuang); belum ada geser-untuk-batal.
- Teks antarmuka ditulis langsung dalam Bahasa Indonesia (belum dipindah ke strings.xml).
- Kode ini belum pernah dikompilasi di lingkungan pembuatnya; bila Gradle melaporkan error, perbaiki sesuai pesan
  (atau jalankan OpenCode dengan `AGENTS.md`, langkah pertamanya memang memperbaiki build).
