# 💬 Chat Backend

Backend chat **real-time ala WhatsApp** — **Node.js 20 · Express · Socket.IO · PostgreSQL** — siap deploy ke Heroku dalam satu klik.

[![Deploy to Heroku](https://www.herokucdn.com/deploy/button.svg)](https://heroku.com/deploy?template=https://github.com/rhevaldyoctavianharis-netizen/ChatApp)

![Node](https://img.shields.io/badge/node-20.x-339933?logo=node.js&logoColor=white)
![License](https://img.shields.io/badge/license-MIT-blue)

Repository resmi: https://github.com/rhevaldyoctavianharis-netizen/ChatApp

## Daftar isi
- [Fitur](#fitur)
- [Deploy ke Heroku (1 klik)](#deploy-ke-heroku-1-klik)
- [Domain otomatis](#domain-otomatis)
- [Struktur proyek](#struktur-proyek)
- [Jalankan lokal](#jalankan-lokal)
- [Environment variables](#environment-variables)
- [Aplikasi Android](#aplikasi-android)
- [API Reference](#api-reference) · [Socket.IO](#socketio)

## Fitur
- Registrasi & login dengan nomor telepon (format internasional) + password (JWT, berlaku 30 hari)
- Chat 1-1 dan grup (admin/anggota, tambah/keluarkan anggota, keluar grup)
- Kirim teks, foto, video, voice note, dan dokumen
- Balas pesan, edit pesan, hapus untuk semua, reaksi emoji
- Status pesan `sent` ✓ → `delivered` ✓✓ → `read` ✓✓ biru (rincian per penerima di grup)
- Indikator mengetik, online/offline, *last seen*
- Jumlah pesan belum dibaca, bisukan chat
- Sinkronisasi kontak (cocokkan nomor telepon dengan pengguna terdaftar)
- Push notification untuk pengguna offline (Expo Push, opsional)
- Media disimpan di PostgreSQL — tanpa layanan tambahan (filesystem Heroku bersifat sementara)
- Rate limit pada login/registrasi, `helmet`, kompresi, dan Redis adapter opsional untuk multi-dyno

## Deploy ke Heroku (1 klik)

1. Gunakan repository ini di GitHub.
2. Klik tombol di bawah, isi nama app, lalu **Deploy app**:

   [![Deploy to Heroku](https://www.herokucdn.com/deploy/button.svg)](https://heroku.com/deploy?template=https://github.com/rhevaldyoctavianharis-netizen/ChatApp)

Yang dilakukan Heroku secara otomatis berdasarkan [`app.json`](app.json) dan [`Procfile`](Procfile):

| Langkah | Detail |
|---|---|
| Buildpack & stack | `heroku/nodejs`, `heroku-24` (Node 20 dari `package.json`) |
| Add-on | Heroku Postgres `essential-0` → `DATABASE_URL` terisi otomatis |
| Config vars | `JWT_SECRET` dibuat acak, `NODE_ENV=production`, `CORS_ORIGIN` perlu dibatasi, `MAX_MEDIA_MB=16` |
| Release phase | `node src/migrate.js` membuat semua tabel (idempoten) |
| Dyno | 1 × `web` ukuran `basic` (`node src/server.js`) |

> 💡 Dyno `basic` dan add-on Postgres berbayar. Heroku tidak menyediakan dyno gratis, tetapi dyno `basic` tidak tidur sehingga koneksi WebSocket tetap hidup.

### Deploy manual (CLI)
```bash
heroku login
heroku create nama-app-chat-anda
heroku addons:create heroku-postgresql:essential-0
heroku config:set JWT_SECRET=$(openssl rand -hex 32)
heroku config:set CORS_ORIGIN="https://domain-web-anda.example"
git push heroku HEAD:main
heroku logs --tail
```

### Lebih dari 1 dyno web?
Tambahkan Redis agar Socket.IO tersinkron antar dyno — `REDIS_URL` otomatis terbaca dan adapter aktif:
```bash
heroku addons:create heroku-redis:mini
heroku ps:scale web=2
```

## Domain otomatis

Anda **tidak perlu mengisi domain** di mana pun. Heroku memberi setiap app domain sendiri, `https://<nama-app>-<kode>.herokuapp.com`, dan backend membacanya langsung dari request yang masuk:

- Setelah deploy selesai, tombol **View** di Heroku membuka `/` (`success_url` di `app.json`) yang menampilkan domain app Anda:
  ```json
  {
    "name": "chat-backend",
    "status": "ok",
    "base_url": "https://nama-app-xxxx.herokuapp.com",
    "websocket_url": "wss://nama-app-xxxx.herokuapp.com",
    "health": "/health"
  }
  ```
- Endpoint yang sama tersedia di `GET /api/info` untuk dipakai klien.
- WebSocket memakai domain yang sama dengan REST API.
- Pakai domain kustom/Cloudflare? Tambahkan lewat `heroku domains:add chat.domainanda.com`; `base_url` otomatis mengikuti host yang dipakai pengakses.

Salin `base_url` tersebut sebagai `BASE_URL` di aplikasi klien.

## Struktur proyek
```
chat-backend/
├── src/
│   ├── server.js          # entry point: Express + Socket.IO (+ Redis adapter opsional)
│   ├── socket.js          # event real-time (pesan, typing, presence, read)
│   ├── migrate.js         # skema database (idempoten, jalan di release phase & saat start)
│   ├── config.js          # konfigurasi dari environment
│   ├── db.js              # pool PostgreSQL (SSL otomatis di luar localhost)
│   ├── middleware/auth.js # verifikasi JWT (header Bearer atau ?token=)
│   ├── routes/            # auth, users, chats, messages, media
│   ├── services/          # logika pesan, chat, push notification
│   └── utils/errors.js    # HttpError, wrapper async, normalisasi nomor telepon
├── android/               # klien Android (Kotlin + Jetpack Compose)
├── docs/                  # dokumen tambahan (prompt klien React Native)
├── .github/workflows/     # CI build APK Android
├── app.json               # konfigurasi tombol Deploy to Heroku
├── Procfile               # release + web process
├── docker-compose.yml     # PostgreSQL untuk pengembangan lokal
└── .env.example
```

Skema database: `users`, `media`, `chats`, `chat_members`, `messages`, `message_receipts`, `message_reactions`, `push_tokens`.

## Jalankan lokal
Butuh Node.js 20+ dan PostgreSQL (atau Docker).
```bash
cp .env.example .env
docker compose up -d        # PostgreSQL lokal
npm install
npm run dev                 # http://localhost:3000
```
Tes cepat:
```bash
curl localhost:3000/health
curl localhost:3000/api/info
```

## Environment variables
| Nama | Wajib | Default | Keterangan |
|---|---|---|---|
| `DATABASE_URL` | ya | `postgres://chat:chat@localhost:5432/chatapp` | Disediakan Heroku Postgres |
| `JWT_SECRET` | ya (production) | – | String acak panjang; server menolak start tanpa ini di production |
| `CORS_ORIGIN` | tidak | `*` | Daftar origin web yang dipercaya, dipisah koma; batasi pada production |
| `MAX_MEDIA_MB` | tidak | `16` | Batas ukuran upload |
| `REDIS_URL` | tidak | – | Wajib bila dyno web > 1 |
| `PORT` | tidak | `3000` | Diisi otomatis oleh Heroku |
| `DATABASE_SSL` | tidak | otomatis | Set `false` untuk mematikan SSL ke database |
| `PG_POOL_MAX` | tidak | `10` | Ukuran pool koneksi |

> Heroku memutus request yang tidak merespons dalam 30 detik; perhatikan ini untuk upload besar.

## Aplikasi Android
Klien native siap pakai ada di [`android/`](android/README.md) (Kotlin + Jetpack Compose). Atur `BASE_URL` di `android/gradle.properties` dengan domain Heroku Anda, atau ubah dari layar login → **Pengaturan server**. Workflow GitHub Actions membangun APK debug otomatis; set variabel repo `BASE_URL` agar URL ikut tertanam. Folder `android/` tidak ikut ke slug Heroku (lihat `.slugignore`).

Ingin klien lain (mis. React Native)? Pakai kontrak API di bawah dan prompt di [`docs/PROMPT_REACT_NATIVE.md`](docs/PROMPT_REACT_NATIVE.md).

## Lisensi
[MIT](LICENSE)

---

# API Reference

Semua JSON memakai **snake_case**. Header untuk endpoint terproteksi: `Authorization: Bearer <token>`.
Error selalu berbentuk `{ "error": "pesan" }`. Nomor telepon wajib format E.164, contoh `+628123456789`.

## Info server
| Method | Path | Keterangan |
|---|---|---|
| GET | `/` | `{name, status, base_url, websocket_url, health}` — domain terdeteksi otomatis |
| GET | `/api/info` | `{name, base_url, websocket_url}` |
| GET | `/health` | `{status, time}` |

## Auth
| Method | Path | Body | Respons |
|---|---|---|---|
| POST | `/api/auth/register` | `{phone, display_name, password}` | `201 {token, user}` |
| POST | `/api/auth/login` | `{phone, password}` | `{token, user}` |

## Profil & pengguna
| Method | Path | Keterangan |
|---|---|---|
| GET | `/api/me` | Profil sendiri |
| PATCH | `/api/me` | `{display_name?, about?, avatar_media_id?}` |
| POST | `/api/me/push-token` | `{token, platform}` (Expo push token) |
| DELETE | `/api/me/push-token` | `{token}` (panggil saat logout) |
| GET | `/api/users/search?q=` | Cari nama/nomor (min 3 huruf) |
| GET | `/api/users/:id` | Profil + `online` |
| POST | `/api/contacts/match` | `{phones: [...]}` → daftar pengguna terdaftar |

`user` = `{id, phone, display_name, about, avatar_media_id, last_seen, created_at}`

## Chat
| Method | Path | Keterangan |
|---|---|---|
| GET | `/api/chats` | Daftar chat, urut aktivitas terbaru |
| POST | `/api/chats/direct` | `{user_id}` → buat/ambil chat 1-1 |
| POST | `/api/chats/group` | `{name, description?, member_ids: []}` |
| GET | `/api/chats/:id` | Detail (grup menyertakan `members`) |
| PATCH | `/api/chats/:id` | `{name?, description?}` (admin grup) |
| PATCH | `/api/chats/:id/mute` | `{muted: bool}` |
| POST | `/api/chats/:id/members` | `{user_ids: []}` (admin) |
| DELETE | `/api/chats/:id/members/:userId` | Keluarkan anggota (admin) / keluar sendiri |
| GET | `/api/chats/:id/messages?before_id=&limit=50` | Riwayat **terbaru → lama**; `{messages, has_more}` |
| POST | `/api/chats/:id/messages` | `{body?, media_id?, reply_to?, client_id?}` |
| POST | `/api/chats/:id/read` | Tandai semua dibaca |

`chat` = `{id, type: "direct"|"group", name, description, role, muted, unread, peer (direct saja), last_message, last_activity, members? (grup)}`
Untuk chat `direct`, nama & foto diambil dari `peer`.

## Pesan
| Method | Path | Keterangan |
|---|---|---|
| PATCH | `/api/messages/:id` | `{body}` edit pesan sendiri |
| DELETE | `/api/messages/:id` | Hapus untuk semua orang |
| PUT | `/api/messages/:id/reactions` | `{emoji}` |
| DELETE | `/api/messages/:id/reactions` | Hapus reaksi sendiri |
| GET | `/api/messages/:id/receipts` | Rincian terkirim/dibaca per penerima (pengirim saja) |

`message` = `{id, chat_id, sender_id, type: "text"|"image"|"video"|"audio"|"file", body, media_id, media_mime, media_filename, media_size, created_at, edited_at, deleted, reply: {id, sender_id, type, body, deleted}|null, status: "sent"|"delivered"|"read"|null (hanya untuk pesan milik sendiri), reactions: [{user_id, emoji}], client_id?}`

Pesan dengan `deleted: true` tidak punya `body`/`media_id` → tampilkan "Pesan ini telah dihapus".

## Media
| Method | Path | Keterangan |
|---|---|---|
| POST | `/api/media` | `multipart/form-data`, field `file` → `{id, mime, filename, size, url}` |
| GET | `/api/media/:id` | Mengambil file. Header Bearer **atau** `?token=<jwt>` (agar bisa dipakai di `<Image source>` / `<img src>`) |

Alur kirim gambar/file: **upload ke `/api/media` → kirim pesan dengan `media_id`** (tipe pesan ditentukan server dari MIME; `body` dipakai sebagai caption).

---

# Socket.IO

Koneksi: `io(BASE_URL, { auth: { token }, transports: ["websocket"] })`. Server otomatis memasukkan klien ke room semua chat miliknya dan menandai pesan tertunda sebagai *delivered*.

## Client → Server (dengan acknowledgement `{ok, ...}` atau `{ok:false, error}`)
| Event | Payload | Ack |
|---|---|---|
| `message:send` | `{chat_id, body?, media_id?, reply_to?, client_id}` | `{ok, message}` |
| `message:read` | `{chat_id}` | `{ok, marked}` |
| `typing` | `{chat_id, is_typing}` | – |
| `presence:query` | `{user_ids: []}` | `{ok, online: [ids]}` |

## Server → Client
| Event | Payload |
|---|---|
| `message:new` | `message` (+ `client_id` untuk mencocokkan pesan optimistik). Dikirim juga ke pengirim. |
| `message:status` | `{chat_id, message_id, status}` → ubah centang pesan milik sendiri |
| `message:edited` | `message` |
| `message:deleted` | `{chat_id, message_id}` |
| `message:reaction` | `{chat_id, message_id, user_id, emoji\|null}` |
| `typing` | `{chat_id, user_id, is_typing}` |
| `presence` | `{user_id, online, last_seen}` |
| `chat:new` | `chat` (chat/grup baru, klien sudah otomatis join room) |
| `chat:updated` | `{chat_id}` → muat ulang detail chat |
| `chat:member_removed` | `{chat_id, user_id}` |
| `chat:read` | `{chat_id, user_id}` → sinkronkan unread di perangkat lain milik sendiri |

Catatan: saat aplikasi kembali ke foreground / reconnect, panggil `GET /api/chats` dan muat ulang pesan chat yang terbuka untuk menyinkronkan data yang terlewat.
