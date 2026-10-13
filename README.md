# GenKeymapper v2.0 — Android 15 + Shizuku

Aplikasi keymapper **independen**, tidak memakai kode/fitur premium ataupun lisensi K2er.
Versi ini memodifikasi source `GenKeymapper-v1.0-source.zip` yang diberikan pemilik proyek.
Semua fitur aplikasi ini gratis; tidak ada akun, billing, internet, analytics, atau VIP.

## Fitur

- Target SDK 35 (Android 15), minimum SDK 26 (Android 8.0).
- Memperbaiki `canPerformGestures=true` dan implementasi stroke kontinu Accessiblity.
- Pemrosesan keyboard/gamepad per-key, mode tap/hold/swipe, mapping WASD joystick 8 arah.
- Overlay chip permainan bertipe `TYPE_ACCESSIBILITY_OVERLAY` (tidak bisa disentuh), editor chip drag-and-drop, endpoint swipe `END` yang dapat digeser, dan assignment hardware key.
- Overlay mouse untuk klik, tap, drag, scroll wheel ke mapping; Kamera Mouse (hover-based, eksperimental).
- Shizuku v13 `UserService` yang menyuntikkan `MotionEvent` multi-pointer dengan InputManager.
- Profil per package game, templat MMORPG (WASD, Q/E/R/F/Space/1–4, kamera).
- Pengaturan durasi dan sensitivitas, ekspor/impor profil JSON.
- Fallback Accessibility apabila Shizuku tidak aktif.

## Penting: cakupan dan keterbatasan

**Source belum diuji di perangkat Android 15 sebenarnya, dan APK belum dikompilasi di lingkungan pengembangan ini.**
Hasil build CI + pengujian perangkat diperlukan sebelum menyatakan fitur *working*.

- `UserService` Shizuku menggunakan Android hidden API (reflection `InputManagerGlobal` / `InputManager`); dukungan bergantung versi firmware dan izin shell perangkat. UI `Terhubung` belum membuktikan gesture sudah berhasil diterima game.
- Beberapa game MMO melarang input sintetis/overlay atau memblokir touch ketika input berasal dari Accessibility/shell.
- `dispatchGesture` Accessibility dapat membatalkan gesture lain. Shizuku disarankan untuk multitouch, hold dan gerakan simultan.
- `Kamera Mouse` memakai **hover mouse absolut**, bukan hardware pointer capture relatif penuh. Kursor bisa mentok tepi layar; karakteristik swipe kamera perlu dites per game. Kecepatan/latensi tidak dijanjikan identik K2er.
- Overlay Mode Mouse fullscreen akan mencegat sentuhan asli dan meneruskannya ulang; mode ini bersifat opsional.
- Shizuku yang berjalan melalui Wireless Debugging perlu diaktifkan lagi setelah boot ulang (non-root).
- Saat instalasi sideload, Android mungkin mengunci akses Accessibility melalui **Restricted settings**. Jika opsi tidak dapat diaktifkan, buka Settings > Apps > Gen Keymapper > menu tiga titik > Allow restricted settings (bila tersedia), lalu aktifkan service Accessibility.

## Build APK melalui GitHub Actions — tanpa PC

1. Buat repository GitHub baru, masukkan **isi ZIP** ini ke root repository (termasuk folder tersembunyi `.github/workflows/`).
2. Buka tab **Actions** > **Build GenKeymapper Android APK** > **Run workflow**.
3. Setelah workflow berhasil, unduh artifact `GenKeymapper-Android15-Debug-APK` dan pasang `app-debug.apk` di Android.
4. Ini APK debug untuk uji coba. Untuk distribusi gunakan keystore release milikmu sendiri, jangan berbagi keystore.

Pada sistem yang sudah memiliki Android SDK 35, JDK 17 dan Gradle 8.11.1:

```bash
gradle --no-daemon :app:assembleDebug
```

## Menjalankan

1. Pasang dan mulai **Shizuku** dari https://shizuku.rikka.app/ (pilih Wireless Debugging untuk Android 15 tanpa root).
2. Buka Gen Keymapper, aktifkan izin overlay dan layanan Accessibility.
3. Pilih **Hubungkan / Minta Izin Shizuku**, setujui izin di aplikasi Shizuku.
4. Pastikan status `Terhubung` dan backend utama `Shizuku` (bisa diganti ke Accessibility).
5. Sambungkan keyboard/mouse OTG/Bluetooth.
6. Tambah profil game lewat daftar aplikasi, lalu pilih **MMORPG** untuk membuat preset.
7. Jalankan game, ketuk ikon overlay 🎮, pilih **Edit Mapping** dan pindahkan chip ke posisi kontrol HUD yang benar. Untuk swipe, geser titik **END** ke titik tujuan. Simpan.
8. Untuk kontrol kamera, tambahkan chip **Kamera**, geser ke area kamera game; aktifkan **Mode Mouse** dan **Kamera Mouse**.
9. Coba di game offline/ruang latihan dulu, lalu sesuaikan sensitivitas.

## Struktur kode

- `app/src/main/java/com/gen/keymapper/MapperService.kt`: key routing, pengelolaan joystick, status game, overlay.
- `ShellInputService.kt`: proses privileged Shizuku, injeksi multi-pointer.
- `ShizukuBridge.kt`: izin Shizuku, bind UserService, perintah IPC dan fallback.
- `GestureKit.kt`: fallback gesture Accessibility API 26+.
- `PlayOverlay.kt`: tombol floating, petunjuk chip saat bermain, kontrol mouse.
- `EditorOverlay.kt`: editor visual drag-drop chip dan area kamera.
- `MappingStore.kt` + `Models.kt`: profil, konfigurasi, JSON impor/ekspor.
- `.github/workflows/build.yml`: build APK debug dalam GitHub Actions.

## Catatan keamanan dan kompatibilitas

Tidak mencoba mem-bypass pemeriksaan anti-cheat, pembatasan OEM, atau sistem lisensi pihak ketiga.
Gunakan mapping hanya ketika diizinkan oleh ketentuan game.
