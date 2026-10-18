# Cara Jadiin APK - Aplikasi Sosial Chat

Bener, web app ini BISA dijadiin APK. Ini udah aku siapin project Android-nya.

## Isi Folder Ini
- `app/src/main/assets/index.html` = Aplikasi chat-nya (sudah jadi)
- `app/src/main/java/com/muse/chatapp/MainActivity.kt` = Pembungkus WebView
- `app/src/main/AndroidManifest.xml` = Izin kamera, galeri, internet, dll
- `app/build.gradle` + `build.gradle` + `settings.gradle` = Config build

## Cara 1: Paling Cepat (Tanpa Install Apa-apa) - 2 Menit
1. Buka https://webintoapp.com atau https://appsgeyser.com
2. Upload file `app/src/main/assets/index.html` (atau masukkan link web app)
3. Isi nama app: "Aplikasi Sosial Chat", upload icon
4. Klik Build / Download APK -> langsung dapat file .apk

## Cara 2: Via Android Studio (Hasil Paling Bagus)
1. Download & install Android Studio
2. Open Project -> pilih folder `aplikasi-sosial-chat-apk` ini
3. Tunggu Gradle sync selesai
4. Menu Build > Build APK(s) > Build APK(s)
5. File APK ada di `app/build/outputs/apk/debug/app-debug.apk`
6. Pindah ke HP, tap file-nya -> Install

## Catatan
- APK ini adalah WebView wrapper, jadi tampilannya 100% sama kayak web app-nya.
- Fitur kirim foto/video dari galeri sudah diizinkan di Manifest, tinggal di-test di HP.
- Kalau mau naik ke Play Store, perlu build Release + Signing (di Android Studio: Build > Generate Signed App Bundle).
