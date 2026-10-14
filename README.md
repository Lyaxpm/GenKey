# GenKeymapper v3.0

Keyboard & mouse untuk game Android, **tanpa root, tanpa Accessibility, tanpa iklan, tanpa VIP, tanpa internet**.
Semua fitur gratis. Sentuhan disuntikkan lewat **Shizuku**.

## Fitur
- **Joystick WASD** (analog virtual, radius bisa diatur)
- **Kamera mouse-look relatif** (pointer capture): aktif di *Mode Bidik* atau *selama tombol ditahan* (mis. klik kanan untuk MMORPG)
- **Dua mode**: *Kursor* (kursor virtual, klik = ketuk, bisa seret) dan *Bidik* (FPS). Berpindah dengan klik tengah mouse, tombol lain, atau bubble
- **Aksi tombol**: Tap, Tahan, Toggle, Turbo (ketuk berulang), Geser (swipe)
- **Mouse penuh**: kiri/kanan/tengah/samping, scroll wheel atas/bawah bisa dipetakan
- **Lingkup per kontrol**: selalu / hanya Bidik / hanya Kursor (mis. klik kiri = tembak hanya saat Bidik)
- **Editor visual** drag & drop dengan panel properti, duplikat, hapus, batalkan perubahan
- **Preset** MMORPG dan FPS / Battle Royale, profil per game, ekspor/impor JSON (format v2 lama otomatis dikonversi)
- Bubble **GEN** yang bisa diseret: jeda, ganti mode, edit, sembunyikan petunjuk
- Tombol Kembali sistem tetap diteruskan ke game; tombol jeda (default F8)
- Uji injeksi sentuhan dan Diagnosa di dalam aplikasi

## Cara kerja
1. Satu jendela overlay layar penuh yang **fokus** tetapi `NOT_TOUCHABLE`: sentuhan layar tembus ke game, sedangkan keyboard dan mouse (pointer capture) diterima overlay.
2. `InputEngine` menerjemahkan tombol/mouse menjadi jari virtual.
3. `ShellInputService` (Shizuku UserService, UID shell) menyuntikkan `MotionEvent` multi-jari lewat `IInputManager`.

Pendekatan overlay-fokus + pointer capture terinspirasi proyek terbuka [aka-munan/keysync](https://github.com/aka-munan/keysync) (Apache-2.0). Kode ditulis ulang sendiri.

## Build
Tidak butuh Gradle wrapper. Push ke GitHub lalu jalankan **Actions → Build GenKeymapper Android APK**. APK ada di artifact `GenKeymapper-Android15-Debug-APK`. Jika gagal, artifact `build-reports` berisi laporan.
Lokal: JDK 17, Android SDK 35, Gradle 8.11.1 → `gradle :app:assembleDebug`.

## Keterbatasan (jujur)
- **Fokus**: overlay memegang fokus jendela. Sebagian game menjeda/membisukan diri saat kehilangan fokus.
- Game dapat memblokir overlay atau input sintetis. Pakai dengan bijak; mengubah input di game online bisa melanggar aturan game.
- Untuk melanjutkan setelah *Jeda*, ketuk bubble GEN (keyboard kembali ke game saat jeda).
- Gamepad: hanya tombol, belum stik analog.
- Setelah perangkat restart, Shizuku (Wireless debugging) harus dinyalakan lagi.
- Kode ini diverifikasi dengan kompilasi terhadap android.jar API 35, AIDL asli, dan 70+ uji perilaku engine/JSON di JVM. **Belum diuji di perangkat fisik**.
