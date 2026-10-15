# GenKeymapper v3.2

Keyboard & mouse untuk game Android, **tanpa root, tanpa iklan, tanpa VIP**.
Pilih **OTG offline (Aksesibilitas)** untuk bermain dengan kuota atau tanpa jaringan.
Mode **Shizuku** dan **Pairing mandiri (ADB Wireless)** tetap tersedia.

## OTG + kuota di HyperOS
1. Di Persiapan pilih **OTG offline · Aksesibilitas**.
2. Buka pengaturan dan aktifkan **GenKey · OTG offline**. Layanan tidak membaca isi layar.
3. Izinkan overlay; pada HyperOS izinkan autostart dan baterai Tanpa batasan bila tersedia.
4. Colok keyboard/mouse OTG, jalankan **Uji sentuhan**, lalu buka mapping game.
5. Wi-Fi dan Wireless debugging boleh mati untuk mode offline ini. Tidak ada pairing.

Izin Aksesibilitas harus dinyalakan oleh pengguna. Bila pemasangan APK memicu
Setelan terbatas, periksa Info aplikasi > Izinkan setelan terbatas (jika tersedia),
hanya untuk APK yang dipercaya. Aplikasi tidak melewati pengamanan ini secara otomatis.

Mode offline memakai gesture 32 ms yang disambung, termasuk beberapa jari dalam satu frame.
Latensi nyata termasuk penjadwalan sistem; kamera FPS dan interaksi sentuhan fisik dapat
berbeda dari Shizuku. Wajib uji di HP/game, bukan jaminan semua game kompatibel.

## Editor lebih ringkas
- Chip tombol 34 dp saat edit / 28 dp saat bermain; bubble GEN 36 dp.
- Seret **⋮⋮** pada toolbar untuk memindahkan; ketuk untuk melipat/membuka.
- Tombol **✓ Simpan** dan **× Batal** tetap tersedia di luar daftar kontrol yang bisa digulir.
- Ketuk chip untuk properti. Seret chip langsung untuk memindahkan; panel otomatis menutup.
- Panel properti dapat diseret lewat judul, dibatasi tinggi, dan dibuka di sisi berlawanan.
- Toolbar/panel dibatasi area layar aman setelah perubahan ukuran/orientasi.


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
3. Backend terpilih mengirim input: `OfflineInputService` melalui gesture Aksesibilitas lokal; `ShellInputService` melalui Shizuku atau stream ADB yang telah diotorisasi.

Pendekatan overlay-fokus + pointer capture terinspirasi proyek terbuka [aka-munan/keysync](https://github.com/aka-munan/keysync) (Apache-2.0). Kode ditulis ulang sendiri.

## Build
Tidak butuh Gradle wrapper. Push ke GitHub lalu jalankan **Actions → Build GenKeymapper Android APK**. APK ada di artifact `GenKeymapper-Android15-Debug-APK`. Jika gagal, artifact `build-reports` berisi laporan.
Lokal: JDK 17, Android SDK 35, Gradle 8.11.1 → `gradle :app:assembleDebug`.

## Keterbatasan (jujur)
- **Fokus**: overlay memegang fokus jendela. Sebagian game menjeda/membisukan diri saat kehilangan fokus.
- Game dapat memblokir overlay atau input sintetis. Pakai dengan bijak; mengubah input di game online bisa melanggar aturan game.
- Untuk melanjutkan setelah *Jeda*, ketuk bubble GEN (keyboard kembali ke game saat jeda).
- Gamepad: hanya tombol, belum stik analog.
- Setelah perangkat restart, backend Shizuku/ADB harus diaktifkan lagi. Mode offline tidak membutuhkan aktivasi ADB, tetapi izin dan layanan Aksesibilitas harus tetap aktif.
- Uji otomatis mencakup antrean input, transisi multitouch offline, dan batas posisi editor. **Belum diuji di perangkat fisik HyperOS**; status build versi ini lihat GitHub Actions.
