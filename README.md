# GenKeymapper 2.1 — UI baru, Android 15, Shizuku, tanpa Accessibility

Modifikasi independen dari ZIP `GenKeymapper-v2_0-fixed-source.zip` milik pengguna. Desain dan sumber tetap GenKeymapper; [KeySync](https://github.com/aka-munan/keysync) digunakan sebagai referensi pola alur kerja **Shizuku → pemilihan game → overlay mapping**, tanpa menyalin UI maupun kode proyek itu.

## Perubahan utama

- **Menghilangkan ketergantungan Accessibility sepenuhnya**: komponen `BIND_ACCESSIBILITY_SERVICE`, deklarasi XML dan `GestureKit` dihapus. Backend injeksi sentuhan kini **Shizuku-only**.
- `MapperService` adalah foreground service normal (`specialUse`) dengan notifikasi aktif sesuai persyaratan target SDK 35. Hanya dimulai oleh tindakan pengguna dari Activity terlihat.
- Dashboard dark gaming baru: kontrol utama, indikator Shizuku/overlay, pemilihan aplikasi, profil permainan, impor/ekspor, pengaturan, kartu tindakan.
- Overlay `TYPE_APPLICATION_OVERLAY` untuk tombol GEN, menu cepat, pratinjau chip, dan editor drag-and-drop fullscreen. Tidak ada lagi `TYPE_ACCESSIBILITY_OVERLAY`.
- Editor menyimpan posisi **saat ACTION_UP** (bukan setiap layout-change), mendukung Tap/Hold/Swipe/Joystick/Kamera/Scroll, assign keyboard atau tombol mouse.
- Keyboard ditangkap melalui jendela overlay kecil yang memiliki fokus. Saat game mengambil kembali fokus, tekan tombol **GEN → Fokus ulang keyboard**. Ini *bukan* raw global input capture.
- Shizuku UserService mengirim perintah pointer multitouch memakai InputManager Android (reflection); keyboard/mouse mapping tetap tergantung kondisi window focus dan kompatibilitas firmware.
- Profil dipilih **secara eksplisit**, tanpa membaca aplikasi foreground lewat Accessibility. Profil baru langsung disimpan (memperbaiki bug `getOrPut` sebelum `save()`).
- Preset MMORPG: analog WASD, Q/E/R/F, Space, angka 1–4, area kamera. Profil juga dapat dibackup sebagai JSON.
- Mode mouse fullscreen masih **beta/eksperimental**. Saat input Shizuku dipicu, layer menangkap mouse dilepas sementara agar sentuhan sintetis tidak berulang masuk ke overlay. Mode ini tidak sama dengan *relative pointer capture* atau global mouse listener.

## Keterbatasan yang penting

- **Belum ada APK yang sukses dibangun/dijalankan di perangkat dalam lingkungan kerja ini**. UI dan backend perlu diuji pada Android 15 nyata sebelum dinyatakan stabil.
- Saat game mengambil fokus, window overlay kecil tidak selalu menerima keyboard fisik. Mengetuk **Fokus ulang keyboard** dapat membantu tetapi tidak menjamin kompatibilitas semua game atau ROM.
- Mouse-look absolut (hover) bisa mentok tepi layar; native *relative mouse capture* belum diimplementasikan.
- Mode mouse beta fullscreen menangkap gesture pada lapisannya; interaksi touchscreen langsung dengan game dapat terganggu. Matikan saat ingin bermain dengan sentuhan asli.
- Status Shizuku `Terhubung` hanya berarti UserService tersedia, **bukan bukti game menerima injeksi**.
- Shizuku yang dimulai lewat Wireless Debugging biasanya harus dinyalakan ulang setelah reboot.
- Game tertentu dapat memblokir overlay dan injeksi input; tidak ada fitur bypass anti-cheat, lisensi, atau perlindungan game.

## Cara memakai (Android 15)

1. Pasang Shizuku. Mulai via Wireless Debugging lalu izinkan akses ketika GenKeymapper meminta.
2. Buka GenKeymapper, aktifkan **Display over other apps**. **Tidak perlu Accessibility.**
3. Tambahkan game dari daftar, pilih profil; opsional terapkan **Preset MMORPG**.
4. Tekan **Pilih & edit** untuk membuka game dan menampilkan editor overlay, atau **Mainkan** untuk langsung menggunakan profil.
5. Geser chip ke tombol di HUD; ketuk chip untuk menetapkan keyboard/mouse. Swipe punya titik `END`. Joystick perlu 4 arah.
6. Tekan **Selesai**. Tombol **GEN** melayang membuka menu cepat untuk mengedit ulang, fokus keyboard, serta menampilkan/menyembunyikan indikator.
7. Uji mode tap/hold/WASD pada game yang mengizinkan mapping. Mode mouse diberi label beta sampai lolos pengujian perangkat.

## Build APK tanpa PC

Unggah **isi ZIP** ini ke root sebuah repo GitHub, termasuk `.github/workflows/build.yml`.
Buka **Actions → Build GenKeymapper Android APK → Run workflow**. Setelah workflow sukses, unduh artifact `GenKeymapper-Android15-Debug-APK`. Alur build menggunakan JDK 17, Gradle 8.11.1, SDK 35.

Secara lokal (bila Gradle dan Android SDK tersedia):

```bash
gradle --no-daemon :app:assembleDebug
```

> Pengujian build harus benar-benar berhasil sebelum APK dibagikan ke orang lain. Jangan menganggap ZIP source sebagai APK siap instal.

## Struktur

`MainActivity.kt`: dashboard; `MapperService.kt`: service notifikasi dan key routing; `EditorOverlay.kt`: drag & assign; `PlayOverlay.kt`: overlay control/pratinjau/keyboard focus; `ShizukuBridge.kt`: koneksi/izin; `ShellInputService.kt`: injeksi shell; `MappingStore.kt` + `Models.kt`: profil JSON; `.github/workflows/build.yml`: CI.
