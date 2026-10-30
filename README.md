# ChatKu

![Node.js](https://img.shields.io/badge/Node.js-22.x-339933?logo=node.js&logoColor=white)
![Android](https://img.shields.io/badge/Android-Kotlin%20%2B%20Compose-3DDC84?logo=android&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?logo=postgresql&logoColor=white)
![License](https://img.shields.io/badge/license-MIT-blue)

ChatKu adalah aplikasi chat real-time dengan backend **Node.js, Express, Socket.IO,
PostgreSQL** dan klien Android native **Kotlin + Jetpack Compose**.

> Proyek ini menggunakan pola desain aplikasi chat modern tanpa menyalin kode, logo,
> nama, atau aset proprietary aplikasi pihak lain.

**Repository:** <https://github.com/rhevaldyoctavianharis-netizen/ChatApp>

## Daftar isi

- [Fitur](#fitur)
- [Arsitektur](#arsitektur)
- [Prasyarat](#prasyarat)
- [Instalasi backend lokal](#instalasi-backend-lokal)
- [Menjalankan backend](#menjalankan-backend)
- [Deploy ke Heroku](#deploy-ke-heroku)
- [Hosting mandiri atau VPS](#hosting-mandiri-atau-vps)
- [Android: membuka source dan build APK](#android-membuka-source-dan-build-apk)
- [Konfigurasi environment](#konfigurasi-environment)
- [Troubleshooting](#troubleshooting)
- [API dan Socket.IO](#api-dan-socketio)
- [Struktur repository](#struktur-repository)
- [Lisensi](#lisensi)

## Fitur

- Registrasi dan login dengan nomor telepon format E.164.
- Chat langsung dan grup.
- Pesan teks, foto, video, audio, dan dokumen.
- Balas, edit, hapus untuk semua, reaksi emoji, dan status terkirim/dibaca.
- Indikator mengetik, online/offline, last seen, unread badge, dan mute chat.
- Sinkronisasi kontak dan pencarian pengguna.
- Upload media ke PostgreSQL.
- Socket.IO untuk pembaruan real-time.
- Rate limit autentikasi, Helmet, kompresi, validasi akses media, dan koneksi TLS.
- Android native dengan tema terang/gelap, voice note, kamera, galeri, dan notifikasi lokal.

## Arsitektur

```text
Android app (Kotlin + Compose)
        │ HTTPS + WebSocket
        ▼
Node.js + Express + Socket.IO
        │
        ├── PostgreSQL 16 (data, pesan, media)
        └── Redis opsional (Socket.IO multi-instance)
```

Backend tidak menyimpan file media di filesystem lokal. Media disimpan sebagai
`BYTEA` di PostgreSQL sehingga tetap tersedia setelah restart atau deploy ulang.

## Prasyarat

| Komponen | Versi minimum | Keterangan |
|---|---:|---|
| Node.js | 22.x | Backend dan migrasi database |
| npm | ikut Node.js | Instalasi dependency |
| PostgreSQL | 16 | Database lokal atau managed database |
| Docker | versi terbaru | Opsional, untuk PostgreSQL lokal |
| JDK | 17 | Build Android |
| Android Studio | Ladybug atau lebih baru | Build dan menjalankan APK |
| Android SDK | API 35 | Compile/target SDK aplikasi |

### Linux (Ubuntu/Debian)

```bash
sudo apt update
sudo apt install -y git curl unzip openjdk-17-jdk
```

Instal Node.js 22 dengan `nvm`:

```bash
curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.40.1/install.sh | bash
source ~/.bashrc
nvm install 22
nvm use 22
```

Untuk database, gunakan Docker (cara paling konsisten):

```bash
sudo apt install -y docker.io docker-compose-plugin
sudo systemctl enable --now docker
```

### macOS

Instal Homebrew dari <https://brew.sh>, lalu:

```bash
brew install git node@20 openjdk@17
brew install --cask android-studio
```

Jika diperlukan, tambahkan JDK ke environment:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
```

Docker Desktop dapat diunduh dari <https://www.docker.com/products/docker-desktop/>.

### Windows

Pilihan termudah adalah **Windows 11 + PowerShell + Docker Desktop**.

1. Instal Git dari <https://git-scm.com/download/win>.
2. Instal Node.js 22 LTS dari <https://nodejs.org/>.
3. Instal JDK 17 (Temurin) dari <https://adoptium.net/>.
4. Instal Android Studio dari <https://developer.android.com/studio>.
5. Instal Docker Desktop dari <https://www.docker.com/products/docker-desktop/>.
6. Buka Android Studio dan pasang Android SDK Platform 35 serta Android SDK Build-Tools.

Perintah berikut dapat dijalankan di PowerShell:

```powershell
node --version
npm --version
java -version
docker --version
```

## Instalasi backend lokal

Clone repository dan masuk ke root proyek:

```bash
git clone https://github.com/rhevaldyoctavianharis-netizen/ChatApp.git
cd ChatApp
```

Salin konfigurasi contoh:

```bash
# Linux/macOS/Git Bash
cp .env.example .env

# PowerShell
Copy-Item .env.example .env
```

Edit `.env`. Untuk pengembangan lokal, gunakan password database yang tidak kosong:

```dotenv
DATABASE_URL=******localhost:5432/chatapp
POSTGRES_USER=chat
POSTGRES_PASSWORD=ganti-password-lokal
POSTGRES_DB=chatapp
JWT_SECRET=ganti-dengan-string-acak-minimal-32-karakter
NODE_ENV=development
PORT=3000
```

Jalankan PostgreSQL dengan Docker:

```bash
docker compose up -d
```

Instal dependency Node.js:

```bash
npm install
```

Migrasi dijalankan otomatis saat server mulai. Migrasi manual:

```bash
npm run migrate
```

## Menjalankan backend

Mode pengembangan:

```bash
npm run dev
```

Mode normal:

```bash
npm start
```

Endpoint pemeriksaan:

```bash
curl http://localhost:3000/health
curl http://localhost:3000/api/info
```

PowerShell:

```powershell
Invoke-WebRequest http://localhost:3000/health
Invoke-WebRequest http://localhost:3000/api/info
```

Server dianggap siap jika `/health` mengembalikan JSON dengan `"status":"ok"`.

## Deploy ke Heroku

Heroku memerlukan kartu pembayaran dan add-on PostgreSQL berbayar. Gunakan
**salah satu** metode berikut.

### Opsi A: Deploy button

Klik tombol berikut, pilih nama aplikasi, lalu tekan **Deploy app**:

[![Deploy to Heroku](https://www.herokucdn.com/deploy/button.svg)](https://heroku.com/deploy?template=https://github.com/rhevaldyoctavianharis-netizen/ChatApp/tree/heroku-ssl-release-fix)

`app.json` akan mengatur Node.js, PostgreSQL, `NODE_ENV=production`, dan
`JWT_SECRET` acak. Setelah deployment selesai:

```text
https://NAMA-APP.herokuapp.com/health
https://NAMA-APP.herokuapp.com/api/info
```

### Opsi B: Heroku CLI

Instal Heroku CLI dari <https://devcenter.heroku.com/articles/heroku-cli>,
login, lalu jalankan dari root repository:

```bash
heroku login
heroku create nama-app-chatku
heroku addons:create heroku-postgresql:essential-0
heroku config:set NODE_ENV=production
heroku config:set JWT_SECRET="$(openssl rand -hex 32)"
heroku config:set CORS_ORIGIN="https://domain-web-anda.example"
git push heroku main
heroku logs --tail
```

Pada Windows PowerShell, buat secret dengan:

```powershell
$secret = -join ((1..64) | ForEach-Object { '{0:x}' -f (Get-Random -Maximum 16) })
heroku config:set NODE_ENV=production JWT_SECRET=$secret
```

Periksa status:

```bash
heroku ps
heroku config
heroku open
```

`DATABASE_URL` disediakan otomatis oleh Heroku Postgres. `Procfile` menjalankan
migrasi pada release phase dan server pada dyno web.

### Heroku dengan domain kustom dan WebSocket

```bash
heroku domains:add chat.example.com
```

Pastikan DNS mengarah ke target Heroku. Gunakan HTTPS; Socket.IO akan memakai
WebSocket pada domain yang sama. Jika menggunakan Cloudflare, gunakan mode
TLS **Full (strict)** dan aktifkan dukungan WebSocket.

Untuk lebih dari satu dyno web, tambahkan Redis:

```bash
heroku addons:create heroku-redis:mini
heroku ps:scale web=2
```

## Hosting mandiri atau VPS

Gunakan Ubuntu 22.04/24.04, Debian, atau server Linux setara.

### Metode Docker Compose

1. Instal Docker Engine dan Compose plugin.
2. Clone repository.
3. Buat `.env` dengan `DATABASE_URL` PostgreSQL managed atau gunakan service
   database terpisah.
4. Jalankan backend dengan process manager atau container.

Contoh menjalankan setelah PostgreSQL tersedia:

```bash
npm ci
NODE_ENV=production npm run migrate
NODE_ENV=production npm start
```

Untuk proses yang tetap hidup, gunakan systemd, PM2, atau supervisor. Contoh
PM2:

```bash
npm install --global pm2
pm2 start src/server.js --name chatku
pm2 save
pm2 startup
```

Pasang reverse proxy HTTPS (Nginx/Caddy) di depan Node.js. Backend mendengarkan
port internal, misalnya `3000`; reverse proxy meneruskan HTTP dan WebSocket.

Contoh Nginx minimal:

```nginx
server {
    listen 443 ssl http2;
    server_name chat.example.com;

    location / {
        proxy_pass http://127.0.0.1:3000;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-Proto https;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
    }
}
```

Gunakan sertifikat Let's Encrypt, firewall, backup PostgreSQL, dan secret
environment yang tidak disimpan di Git. Untuk database PostgreSQL non-lokal,
aktifkan validasi TLS dan sediakan `DATABASE_SSL_CA` jika provider memerlukannya.

### Menjalankan sepenuhnya di komputer sendiri

Untuk pengujian LAN, backend dapat diakses melalui IP komputer:

```bash
hostname -I        # Linux
ipconfig           # Windows
```

Atur `BASE_URL` Android ke `http://IP-KOMPUTER:3000` hanya untuk debug. Jangan
menggunakan HTTP biasa untuk production. Pastikan firewall mengizinkan port
yang diperlukan dan jangan expose PostgreSQL ke jaringan publik.

## Android: membuka source dan build APK

Source Android berada di folder [`android/`](android/). Buka **folder `android/`**,
bukan root repository, di Android Studio.

### Konfigurasi URL backend

Edit `android/gradle.properties`:

```properties
BASE_URL=https://nama-app.herokuapp.com
DEFAULT_COUNTRY_CODE=62
```

Atau berikan saat build:

```bash
./gradlew assembleDebug -PBASE_URL=https://nama-app.herokuapp.com
```

URL juga dapat diubah dari layar login melalui **Pengaturan server**.

### Build APK debug dari source

Linux/macOS/Git Bash:

```bash
cd android
./gradlew assembleDebug
```

Windows PowerShell:

```powershell
cd android
.\gradlew.bat assembleDebug
```

APK tersedia di:

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

Instal ke perangkat dengan Android Debug Bridge:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Pastikan USB debugging aktif atau gunakan emulator Android Studio.

### Build release APK/AAB

Build release tidak memakai debug key. Buat keystore privat di luar repository:

```bash
keytool -genkeypair -v \
  -keystore chatku-release.jks \
  -alias chatku \
  -keyalg RSA -keysize 4096 -validity 10000
```

Konfigurasikan signing release melalui `signingConfigs` lokal atau secret CI.
Jangan commit `.jks`, password, atau file `keystore.properties`.

Perintah build:

```bash
cd android
./gradlew assembleRelease
./gradlew bundleRelease
```

Output umumnya berada di:

```text
app/build/outputs/apk/release/
app/build/outputs/bundle/release/
```

### Build melalui GitHub Actions

Workflow `.github/workflows/android.yml` membangun debug APK pada branch `main`.
Tambahkan repository variable `BASE_URL` pada **Settings → Secrets and variables →
Actions → Variables**, lalu jalankan workflow manual atau push ke `main`.

Artifact bernama `ChatKu-debug-apk` dapat diunduh dari halaman workflow.

### Uji dan lint Android

```bash
cd android
./gradlew test lintDebug
```

## Konfigurasi environment

| Variabel | Wajib | Keterangan |
|---|---:|---|
| `DATABASE_URL` | Ya | Connection string PostgreSQL |
| `JWT_SECRET` | Ya | Minimal 32 karakter; wajib di semua environment |
| `NODE_ENV` | Disarankan | Gunakan `production` pada server publik |
| `PORT` | Tidak | Default `3000`; Heroku mengisi otomatis |
| `CORS_ORIGIN` | Disarankan | Daftar origin tepercaya, dipisah koma |
| `MAX_MEDIA_MB` | Tidak | Default `16` |
| `REDIS_URL` | Tidak | Diperlukan untuk Socket.IO multi-instance |
| `DATABASE_SSL` | Tidak | Hanya set `false` untuk database lokal |
| `DATABASE_SSL_CA` | Tidak | CA PEM untuk PostgreSQL non-lokal |
| `DATABASE_SSL_REJECT_UNAUTHORIZED` | Tidak | Set `true` jika CA PostgreSQL tersedia; Heroku memakai TLS tanpa CA aplikasi secara default |
| `REDIS_TLS_CA` | Tidak | CA PEM untuk Redis TLS bila diperlukan |
| `PG_POOL_MAX` | Tidak | Default `10` |

Jangan memasukkan JWT secret, password database, token push, atau keystore ke
Git. Gunakan Heroku Config Vars, GitHub Actions Secrets, atau secret manager
hosting Anda.

## Troubleshooting

### `JWT_SECRET wajib diisi dan minimal 32 karakter`

Buat secret baru dan masukkan ke `.env` atau environment server:

```bash
openssl rand -hex 32
```

### PostgreSQL tidak dapat terhubung

```bash
docker compose ps
docker compose logs db
docker compose up -d
```

Pastikan `.env` memiliki `POSTGRES_PASSWORD` dan `DATABASE_URL` menggunakan
host `localhost` saat backend berjalan di host machine.

### Android gagal sync atau `JAVA_HOME` salah

Pastikan JDK 17 aktif:

```bash
java -version
```

Di Android Studio, pilih **Settings/Preferences → Build Tools → Gradle →
Gradle JDK → JDK 17**.

### WebSocket tidak tersambung

Pastikan reverse proxy meneruskan header `Upgrade` dan `Connection`, domain
menggunakan HTTPS, dan Cloudflare mengizinkan WebSocket.

### Upload media gagal

Batas default adalah 16 MB. Naikkan `MAX_MEDIA_MB` bila diperlukan, dan periksa
batas upload reverse proxy/Cloudflare serta batas provider hosting.

## API dan Socket.IO

Kontrak lengkap API REST dan event Socket.IO ada di bagian berikut. Semua JSON
menggunakan `snake_case`; error berbentuk `{ "error": "pesan" }`.

### Endpoint utama

| Method | Path | Keterangan |
|---|---|---|
| GET | `/health` | Status server |
| GET | `/api/info` | URL REST dan WebSocket |
| POST | `/api/auth/register` | Registrasi |
| POST | `/api/auth/login` | Login |
| GET | `/api/chats` | Daftar chat |
| POST | `/api/chats/direct` | Chat langsung |
| POST | `/api/chats/group` | Grup |
| GET | `/api/chats/:id/messages` | Riwayat pesan |
| POST | `/api/chats/:id/messages` | Kirim pesan via REST |
| POST | `/api/media` | Upload media |
| GET | `/api/media/:id` | Ambil media yang berhak diakses |

Endpoint terproteksi memakai:

```http
Authorization: Bearer <JWT>
```

Koneksi Socket.IO:

```javascript
io(BASE_URL, {
  auth: { token: jwt },
  transports: ['websocket']
});
```

Event utama client ke server adalah `message:send`, `message:read`, `typing`,
dan `presence:query`. Event server ke client mencakup `message:new`,
`message:status`, `typing`, `presence`, dan pembaruan chat.

## Struktur repository

```text
ChatApp/
├── src/                    # Backend Express + Socket.IO
│   ├── routes/             # REST API
│   ├── services/           # Logika chat, pesan, push
│   ├── middleware/         # Auth
│   ├── migrate.js          # Skema PostgreSQL
│   └── server.js           # Entry point
├── android/                # Klien Kotlin + Jetpack Compose
├── .github/workflows/      # CI build APK
├── app.json                # Metadata Deploy button Heroku
├── Procfile                # Release dan web process Heroku
├── docker-compose.yml      # PostgreSQL development
├── .env.example            # Template konfigurasi
└── package.json
```

## Lisensi

Proyek ini menggunakan [MIT License](LICENSE).
