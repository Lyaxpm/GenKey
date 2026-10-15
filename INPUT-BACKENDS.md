# GenKey 3.2 — OTG offline dan editor

Mode **OTG offline · Aksesibilitas** mengirim gesture langsung lewat Android, tanpa
ADB, Shizuku, socket, Wi-Fi, atau koneksi internet. Pengguna harus mengaktifkan layanan
Aksesibilitas secara manual. `canRetrieveWindowContent=false`; tidak membaca UI game.
Backend lama tetap tersedia dan tidak diganti diam-diam.

`GestureQueue` mempertahankan DOWN/UP (termasuk tap singkat dan tekan ulang),
menggabungkan MOVE berurutan, dan melepas jari secara terpisah. Satu dispatch 32 ms
membawa seluruh jari; `continueStroke` menyambung jari yang masih ditahan.
Sentuhan fisik/layanan lain bisa membatalkan gesture. Error terlihat di Diagnosa;
lepaskan tombol sebelum mencoba lagi. Tidak menjanjikan latensi nol atau kompatibilitas
kamera FPS yang sama dengan injeksi shell.

Bila HyperOS mematikan ADB ketika Wi-Fi mati, gunakan mode offline. Menyimpan kunci
pairing tidak membuat daemon ADB tetap hidup. Penyambungan ke endpoint terakhir dicoba
kembali saat Wireless debugging hidup; port yang berubah masih perlu diperbarui lewat
Atur koneksi. Kode enam digit tidak perlu diisi ulang selama otorisasi masih berlaku.
Binder Shizuku yang muncul kembali menghapus status gagal sebelumnya agar bisa bind lagi.

## Verifikasi perangkat versi 3.2
- Pilih offline; Uji sentuhan; matikan Wi-Fi dan Wireless debugging; uji OTG + kuota.
- Tahan W+A, gerakkan mouse, klik kiri, lepas salah satu tombol; periksa jari lain tetap aktif.
- Tap cepat/turbo, tahan-toggle, pause/stop, ganti backend, putar layar; tidak boleh ada jari tersangkut.
- Cabut OTG ketika tombol ditahan; sambungkan kembali dan mulai input ulang.
- Cabut izin Aksesibilitas saat aktif; aplikasi harus menunjukkan layanan belum terhubung.
- Editor: seret chip dengan jari/mouse, pindahkan/lipat toolbar, scroll properti,
  ubah orientasi, coba kontrol dekat tepi dan di bawah lokasi panel lama, simpan/batal.
- Uji HyperOS pada HP nyata masih diperlukan. CI tidak menguji injeksi ke game atau ROM.

## Catatan backend 3.1 (tetap berlaku untuk Shizuku/ADB)



Pilih **Shizuku** atau **Pairing mandiri** di Persiapan. Mengganti mode menghentikan
mapping; mulai kembali setelah layanan baru terhubung. Tidak membutuhkan Accessibility.

## Shizuku

Jalankan Shizuku dan izinkan GenKey. Bind memeriksa `ping()` dari worker sebelum
menampilkan siap, memiliki timeout 12 detik, dan membersihkan bind lama saat mencoba
ulang. Error ditampilkan, bukan spinner tanpa batas. Jalankan **Uji sentuhan**:
Binder tersambung tidak menjamin ROM mengizinkan injeksi ke game.

## Pairing mandiri (Android 11+)

1. Hubungkan HP ke Wi-Fi dan aktifkan Opsi pengembang > Wireless debugging.
2. Catat IP dan **port koneksi** pada halaman utama Wireless debugging.
3. Buka GenKey dalam layar terbagi bersama Pengaturan. Pilih Pairing mandiri > Atur koneksi.
4. Pilih Pair device with pairing code di Pengaturan. Biarkan dialog ini terbuka.
5. Isi IP, port koneksi, **port pairing** dari dialog, dan kode enam digit.
6. Tekan Pair & hubungkan. Setelah siap, jalankan Uji sentuhan.
7. Setelah reboot/port berubah, aktifkan Wireless debugging kembali, masukkan port
   koneksi terbaru, lalu tekan Hubungkan. Pair ulang hanya jika otorisasi dicabut.

Kode pairing tidak disimpan. Kunci ADB tersimpan di direktori privat tanpa backup.
INTERNET digunakan untuk ADB/TLS lokal. Layanan berjalan sebagai shell melalui satu
`app_process` dan satu stream ADB yang diautentikasi, tanpa port perintah publik.
Layanan melepas sentuhan saat stream berakhir. Koneksi dibatasi 30 detik pada UI.

## Mouse dan respons input

Editor menerima wheel atas/bawah/kiri/kanan; rekam dengan menggulir mouse pada arah
tersebut. Klik kiri/kanan/tengah/samping tetap tersedia. Horizontal membutuhkan
mouse/touchpad yang mengirim AXIS_HSCROLL (roda vertikal biasa tidak menghasilkan ini).

Injeksi DOWN/UP memakai mode asynchronous. MOVE berurutan untuk jari yang sama
mengganti sampel lama di antrean, tanpa melewati DOWN/UP atau jari lain. Ini mengurangi
antrean input, bukan jaminan latensi nol. ADB/TLS dapat lebih lambat daripada Binder.

## Verifikasi perangkat

- Kedua mode: uji sentuhan, WASD diagonal + klik mouse bersamaan, tahan/lepas cepat.
- Wheel empat arah: rekam lalu jalankan mapping. Uji mouse dengan dan tanpa pointer capture.
- Matikan Shizuku/ADB saat tombol ditahan; sambung ulang dan pastikan tidak ada sentuhan tersangkut.
- Ganti mode saat mapping, putar layar, background/foreground, ulangi setelah reboot.
- Kode salah/port salah: error terlihat; coba lagi dengan data benar.

Validasi lokal 14 Oktober 2026: `:app:testDebugUnitTest :app:assembleDebug` berhasil
(Gradle 8.11.1, JDK 17, SDK 35). Satu tes urutan/coalescing antrean lulus.
CI dikonfigurasi menjalankan perintah yang sama; belum dijalankan untuk perubahan ini.
Uji hardware Android diperlukan untuk
memastikan perilaku ROM, pointer capture, izin injeksi, dan latensi sebenarnya.

## Dependensi

libadb-android 3.1.1: https://github.com/MuntashirAkon/libadb-android
(dual license GPL-3.0-or-later OR Apache-2.0; digunakan di bawah Apache-2.0).
Conscrypt Android 2.5.3: https://github.com/google/conscrypt (Apache-2.0).
Bouncy Castle 1.81: https://www.bouncycastle.org (MIT).
Shizuku API 13.1.5: https://github.com/RikkaApps/Shizuku-API (Apache-2.0).
