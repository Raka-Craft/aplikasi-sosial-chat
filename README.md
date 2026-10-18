# Aplikasi Sosial Chat

Aplikasi chat LAN (satu WiFi, tanpa internet) untuk Android.

## Fitur
- Chat teks dan foto antar HP dalam satu WiFi yang sama
- Satu HP jadi Host (Buat Ruangan), HP lain Join pakai IP Host
- Tanpa login, tanpa server luar, tanpa internet
- Nama hanya panggilan, riwayat hanya selama sesi (in-memory)

## Cara Pakai
1. Install APK di kedua HP (uninstall versi lama dulu jika ada konflik signature).
2. Hubungkan kedua HP ke WiFi yang sama. Matikan VPN.
3. HP 1 (Host): Buka app, isi Nama, Buat Ruangan, catat IP (contoh 192.168.1.8:8080).
4. HP 2 (Tamu): Buka app, isi Nama, Gabung Ruangan, isi IP dan Port (default 8080), Gabung.
5. Kirim teks dan foto. Pesan muncul dalam ±2 detik (polling).

## Build
Project Android (WebView + embedded HTTP server, port 8080).
- `MainActivity.java` - Activity + ServerSocket HTTP server (GET /, /messages, /info, POST /send)
- `app/src/main/assets/index.html` - UI chat
- `app/src/main/AndroidManifest.xml` - izin INTERNET, WiFi, dll.

Build manual (tanpa Gradle, karena Gradle daemon gagal di sandbox):
```bash
aapt2 compile + link
javac
d8
zipalign
apksigner sign
```

Versi terbaru: v2.2 (versionCode 5) - WhatsApp theme overhaul (Dark/Light, split IP/Port, toast, emoji, bubbles WA).

## Catatan
- Host harus tetap membuka aplikasi. Jika Host tutup, ruangan hilang.
- Harus satu WiFi/subnet yang sama. Router dengan Client Isolation akan memblokir.
- Tanpa enkripsi, jangan kirim data sensitif.
- APK di repo ini ditandatangani debug key untuk sideload. Untuk Play Store perlu build release + signing release key.
- File biner (ikon PNG, debug.keystore, gradle-wrapper.jar) tidak ikut di push ini dan perlu ditambahkan via upload web GitHub jika diperlukan. Lihat .gitignore.
