# AGENTS.md

Repo ini monorepo:
- **Root** = backend chat (Node.js, Express, Socket.IO, PostgreSQL). SUDAH JADI. Jangan diubah kecuali diminta eksplisit.
- **android/** = aplikasi Android native (Kotlin + Jetpack Compose). Source code SUDAH ADA tetapi belum pernah dikompilasi.

## Langkah pertama (wajib)
1. Buka `android/`, jalankan `gradle wrapper` (Gradle 8.9) bila `gradlew` belum ada, lalu `./gradlew assembleDebug`.
2. Perbaiki SEMUA error kompilasi/resource/dependensi sampai build sukses, tanpa mengubah perilaku fitur.
3. Jalankan `./gradlew test lintDebug` dan perbaiki masalah penting.

## Setelah build sukses
Rujuk `PROMPT_ANDROID_KOTLIN.md` untuk daftar fitur & tampilan target; lengkapi yang belum ada
(mis. cache Room, FCM jika backend sudah mendukung, geser-untuk-batal voice note, strings.xml).

## Aturan
1. Kontrak API/Socket.IO ada di `README.md` (root) dan satu-satunya sumber kebenaran (snake_case, nama event, payload).
2. Jangan mengubah backend; bila kontrak kurang, catat di `android/NOTES.md` dan buat solusi sisi klien.
3. URL backend lewat `BASE_URL` di `android/gradle.properties` (backend di belakang Cloudflare, HTTPS + WebSocket).
4. Jangan menaruh rahasia di kode.
5. Folder `android/` tidak ikut ke slug Heroku (lihat `.slugignore`).
