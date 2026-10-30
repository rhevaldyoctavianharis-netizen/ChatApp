# Prompt OpenCode: Klien React Native (opsional)

**Cara pakai:** buat folder kosong untuk aplikasi (mis. `chat-app/`), salin file `README.md` repo ini (bagian API Reference) ke dalamnya, buka OpenCode di folder itu, lalu tempel prompt di bawah. Ganti `BASE_URL` dengan URL Heroku Anda. Jika ingin teknologi lain (Flutter, React web, dsb.), ubah baris "Teknologi".

````text
Buatkan aplikasi chat lengkap ala WhatsApp yang terhubung ke backend yang sudah jadi.
Kontrak API REST dan Socket.IO ada di file README.md di folder ini (bagian "API Reference"
dan "Socket.IO"). BACA README.md SEPENUHNYA lebih dulu dan patuhi kontraknya persis
(snake_case, nama event, bentuk payload). Jangan mengubah atau membuat endpoint baru.

Teknologi: React Native + Expo (SDK terbaru), TypeScript, Expo Router, socket.io-client,
Zustand (state), expo-secure-store (simpan token), expo-image-picker, expo-document-picker,
expo-av (voice note), expo-notifications, expo-contacts.
BASE_URL = https://NAMA-APP-ANDA.herokuapp.com  (simpan di satu file config / env EXPO_PUBLIC_API_URL)

FITUR WAJIB
1. Auth: layar Register (nomor internasional +62..., nama, password) & Login. Token disimpan
   di SecureStore, auto-login saat app dibuka, logout menghapus token dan push token.
2. Daftar chat: urut terbaru, avatar, nama (direct = peer.display_name), pesan terakhir,
   jam, badge unread, ikon mute. Pull-to-refresh. Update real-time dari event socket.
3. Layar chat:
   - Gelembung pesan kiri/kanan, pemisah tanggal, FlatList inverted dengan paginasi
     (before_id) saat scroll ke atas.
   - Optimistic send: tampilkan pesan dengan client_id (uuid) lalu cocokkan saat
     message:new/ack datang; tampilkan status gagal + tombol kirim ulang.
   - Centang status: sent ✓, delivered ✓✓ abu-abu, read ✓✓ biru (event message:status).
   - Kirim teks, foto/video (galeri & kamera), dokumen, voice note (tahan tombol mic).
     Alur media: POST /api/media (multipart) lalu kirim pesan dengan media_id.
     Tampilkan media memakai URL /api/media/:id?token=JWT.
   - Reply (geser pesan ke kanan atau long-press → Balas) dengan kutipan; edit & hapus
     untuk semua orang (hanya pesan sendiri); reaksi emoji (long-press).
   - Indikator "sedang mengetik..." (kirim event typing dengan debounce), status
     "online"/"terakhir dilihat" di header (event presence + GET /api/users/:id).
   - Panggil message:read saat layar chat fokus dan ada pesan baru masuk.
4. Chat baru: cari pengguna (GET /api/users/search) dan sinkronisasi kontak
   (expo-contacts → normalisasi ke E.164 → POST /api/contacts/match). Tap pengguna →
   POST /api/chats/direct lalu buka chat.
5. Grup: buat grup (nama + pilih anggota), layar info grup (anggota, admin, tambah/keluarkan
   anggota, ubah nama, keluar grup). Pesan di grup menampilkan nama pengirim.
6. Profil: ubah nama, about, foto profil (upload media lalu PATCH /api/me).
7. Notifikasi push: minta izin, ambil Expo push token, kirim ke POST /api/me/push-token;
   tap notifikasi membuka chat sesuai data.chat_id.
8. Socket: satu koneksi global (auth: { token }), reconnect otomatis; saat reconnect atau
   app kembali foreground, muat ulang daftar chat dan chat yang sedang terbuka.
9. UX: tema terang/gelap mengikuti sistem, loading & error state yang jelas (tampilkan
   field "error" dari API), keyboard-avoiding, safe area, bahasa antarmuka Indonesia.

ATURAN KODE
- Struktur rapi: app/ (routes), src/api, src/socket, src/store, src/components, src/utils.
- Satu API client terpusat yang menambahkan header Authorization dan menangani 401
  (otomatis logout).
- Seluruh tipe (User, Chat, Message, dst.) didefinisikan di src/types.ts sesuai README.
- Jangan hardcode rahasia. Sertakan .env.example dan README.md aplikasi berisi cara
  menjalankan (npx expo start) dan build (eas build).
- Setelah selesai: jalankan type-check (tsc --noEmit) dan perbaiki semua error, lalu
  ringkas fitur yang sudah dibuat dan yang belum.
````
