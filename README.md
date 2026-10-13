# GenKey 3.0

Keymapper Android dengan Shizuku dan editor overlay. Tidak memerlukan Accessibility, root, akun, atau langganan. Minimum Android 8 (API 26), target Android 15 (API 35). Shizuku melalui wireless debugging memerlukan Android 11+; versi lebih lama memerlukan aktivasi ADB dari komputer.

GenKey dikembangkan secara independen. [KeySync](https://github.com/aka-munan/keysync) menjadi referensi alur Shizuku, pemilihan game, dan overlay. UI dan implementasi baru tidak menyalin kode KeySync atau K2er. GenKey tidak mengklaim kesetaraan penuh dengan K2er Premium.

## Fitur

- Dashboard dengan tab Persiapan/Pengaturan, status perangkat input dan koneksi, pencarian game, ikon aplikasi sendiri, serta penanganan inset Android 15.
- Profil per game dan profil umum, nama dapat diganti, preset MMORPG/FPS, impor/ekspor JSON melalui pemilih file Android.
- Editor drag-and-drop: tap, hold, toggle hold, turbo, double tap, swipe dengan titik END, joystick WASD, kamera, dan mouse wheel.
- Durasi swipe, interval turbo, radius joystick, sensitivitas kamera, invert Y, serta dead zone analog dapat disesuaikan.
- Beberapa kontrol dapat memakai tombol yang sama untuk menekan beberapa titik sekaligus (maksimal 10 pointer pada backend).
- Analog kiri gamepad menggerakkan joystick pertama jika opsi gamepad diaktifkan. Tombol gamepad bisa direkam seperti keyboard.
- Mouse relatif menggunakan `requestPointerCapture()` dan callback `onCapturedPointerEvent`; tidak ada layer mouse fullscreen yang menelan sentuhan game.
- Backend shell memakai satu event loop terjadwal: tap dan swipe tidak menahan Binder atau mengantrekan seluruh gesture secara serial. Kamera memakai pointer berkelanjutan dengan recenter.
- Input dilepas saat fokus hilang, perangkat dicabut, rotasi, layar mati, berganti profil, pause, atau sesi berhenti. Watchdog shell membatalkan sentuhan ketika heartbeat berhenti selama >3 detik.
- Diagnostik penerimaan injeksi serta layar uji yang menghitung MotionEvent dan multitouch yang benar-benar diterima.

## Cara menggunakan

1. Install dan jalankan [Shizuku](https://shizuku.rikka.app/guide/setup/), lalu buka **Persiapan → Hubungkan Shizuku**.
2. Berikan izin tampil di atas aplikasi. Notifikasi sesi opsional pada Android 13+; tidak perlu mengaktifkan Accessibility.
3. Jalankan **Uji tap & multitouch**. Tekan tombol tes dan jangan menyentuh area tes selama tes berlangsung. Hasil ini hanya membuktikan injeksi ke layar tes, bukan kompatibilitas semua game.
4. Di Dashboard, tambah game dan gunakan **Tambah preset** jika diinginkan. Semua posisi preset adalah contoh; sesuaikan ke HUD game.
5. Tekan **Edit mapping**. Geser kontrol, ketuk untuk mengatur tombol/jenis aksi/durasi/duplikasi, lalu **Selesai**. Geser `END` untuk swipe. Joystick baru sudah berisi WASD.
6. Ketika bermain, ketuk **GEN → Fokus keyboard**. Tekan **F8** untuk menangkap mouse dan mengaktifkan kamera; **Esc** melepas mouse. **F9** menjeda/melanjutkan mapping. Ketiga tombol ini dicadangkan untuk kontrol sesi.
7. Selama capture, M1–M5 mengikuti mapping. Preset FPS memetakan M1 sebagai hold tembak dan M2 sebagai toggle bidik. Mouse wheel bisa dipetakan lewat editor. Tanpa capture, mouse dikendalikan Android seperti biasa.
8. Hentikan sesi melalui GEN atau tombol **Hentikan** di notifikasi.

Untuk HP saja: tambahkan kontrol dari toolbar, pilih opsi M1–M5 untuk mouse, dan gunakan keyboard/gamepad fisik untuk merekam tombol. Semua tombol/tampilan dapat disusun langsung di atas game.

## Build di GitHub Actions

Workflow **Build GenKey Android APK** berjalan pada push, pull request, dan `workflow_dispatch`.

1. Buka **Actions → Build GenKey Android APK → Run workflow**.
2. Workflow menjalankan unit test, Android Lint, build debug, serta build release.
3. Setelah sukses, unduh **GenKey-v3.0-Debug-APK**. Ekstrak ZIP artifact dan install `GenKey-v3.0-debug.apk`.
4. Artifact **GenKey-v3.0-Release-UNSIGNED** memerlukan penandatanganan sebelum bisa dipasang. Tidak diperlukan secret untuk build debug atau release unsigned.
5. **GenKey-validation-reports** memuat laporan test/lint. SHA-256 APK debug tersedia di artifact APK.

Build memakai JDK 17, Gradle Wrapper 8.11.1 dengan checksum distribusi, AGP 8.9.2, Kotlin 2.1.20, SDK/build-tools 35. Tidak memakai Gradle global.

```bash
chmod +x gradlew
./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease
```

Set `ANDROID_HOME` atau `sdk.dir` di `local.properties`. File konfigurasi SDK lokal, APK, dan keystore tidak disimpan ke Git. Debug APK dari runner berbeda bisa memakai kunci debug berbeda: jika Android menolak update karena tanda tangan, ekspor profil terlebih dahulu sebelum uninstall versi lama. Untuk distribusi rutin, tandatangani release dengan keystore tetap milik sendiri.

## Batasan yang harus dipahami

- Keyboard dan gamepad ditangkap melalui jendela overlay yang memiliki fokus, **bukan listener input global/native evdev**. Menyentuh game atau kebijakan firmware dapat memindahkan fokus. GEN → Fokus keyboard tersedia untuk mengambilnya kembali.
- Pointer capture baru berstatus aktif setelah callback Android menyatakan berhasil. ROM yang menolak capture pada overlay akan menampilkan pesan gagal. Belum ada jaminan kompatibilitas mouse untuk seluruh firmware.
- `injectInputEvent` adalah API internal Android. Izin shell, implementasi vendor, atau kebijakan game bisa menolak injeksi. Penghitung “Diterima” berarti InputManager menerima permintaan; layar uji dan pengujian game tetap diperlukan.
- Tidak ada deteksi game foreground otomatis. Pilih profil yang benar secara manual. Hindari multi-window; mapping memakai koordinat layar utama penuh.
- Tidak ada macro recorder, cloud sync, auto-switch profil, atau mouse capture native tanpa fokus. Fitur tersebut tidak ditampilkan sebagai fitur aktif.
- Shizuku perlu diaktifkan kembali setelah reboot. Game dapat membatasi overlay/input sintetis.
- Build dan unit test tidak menggantikan pengujian perangkat fisik. Daftar pemeriksaan ada di [docs/DEVICE_TESTING.md](docs/DEVICE_TESTING.md).

## Struktur

| File | Fungsi |
|---|---|
| `MainActivity.kt` | Dashboard, setup, pemilihan game, backup, pengaturan |
| `EditorOverlay.kt` | Editor drag/drop dan konfigurasi kontrol |
| `PlayOverlay.kt` | Bubble, fokus keyboard, relative pointer capture, preview |
| `MapperService.kt` | Routing input, kombinasi kontrol, lifecycle sesi |
| `ShizukuBridge.kt` | Koneksi shell, izin, IPC dan diagnostik |
| `ShellInputService.kt` | Event loop injeksi multitouch dan watchdog |
| `TouchState.kt` | Pointer ID/index dan vektor joystick yang diuji unit |
| `ProfileCodec.kt` / `MappingStore.kt` | Validasi JSON dan penyimpanan atomik |
| `ScreenSpace.kt` | Koordinat layar yang sama untuk editor/input |
| `InputTestActivity.kt` | Tes penerimaan MotionEvent di perangkat |
