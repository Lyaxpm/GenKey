# Pemeriksaan perangkat fisik

Status awal: belum diuji di perangkat fisik oleh lingkungan pengembangan. Catat model HP, versi Android/ROM, versi Shizuku, koneksi USB/Bluetooth, dan nama game ketika menguji.

| Pemeriksaan | Hasil yang diharapkan |
|---|---|
| Install debug APK baru | Dashboard terbuka, inset status/nav tidak menutup isi |
| Shizuku mati/izin ditolak | Penjelasan yang sesuai, tidak menampilkan input siap |
| Hubungkan Shizuku | Backend siap setelah panggilan Binder input dan pemeriksaan izin injeksi berhasil |
| Izin sudah aktif sebelum app dibuka | Otomatis tersambung tanpa dialog izin ulang atau menunggu UserService |
| Berikan izin dari manager lalu kembali ke GenKey | Status diperbarui dan backend dihubungkan |
| Ketuk koneksi berulang kali | Satu percobaan berjalan; tidak ada timeout dari percobaan lama |
| Matikan lalu mulai kembali Shizuku | Status terputus lalu tersambung dengan Binder baru; input lama tidak diputar ulang |
| Koneksi gagal lalu Coba lagi | Pesan lama dibersihkan dan percobaan baru bisa berjalan |
| Detail koneksi | Bisa menyalin model/Android, versi, status, dan kesalahan aktual |
| Tes tap | LULUS dan event DOWN/UP diterima layar tes |
| Tes dua jari | LULUS dan maksimum 2 pointer diterima layar tes |
| Izin overlay belum diberi | Alur menuju izin, tidak crash |
| Tambah game/cari package | Profil disimpan dan ditemukan setelah app dibuka ulang |
| Rotasi portrait/landscape | Editor, END, preview, dan titik injeksi sejajar |
| Hold + WASD + tap | Sentuhan simultan; tap tidak menahan pergerakan |
| W+D, lalu lepas D | Diagonal ternormalisasi, kembali bergerak ke atas |
| Tombol arah berlawanan | Joystick kembali netral |
| Toggle | Sekali tekan menahan, tekan lagi melepas |
| Turbo | Berulang selama tombol ditekan, berhenti setelah key-up |
| Double tap | Dua tap berurutan tanpa menahan input lain |
| Swipe | Durasi/arah mengikuti konfigurasi END |
| F8 mouse | Callback mengonfirmasi capture; kursor Android tersembunyi |
| Mouse bergerak lama | Tidak mentok tepi layar, kamera melakukan recenter |
| Klik beberapa tombol mouse | Key-up tiap tombol tidak hilang saat tombol lain masih ditekan |
| Esc / F9 | Capture dilepas / mapping dijeda, semua pointer dilepas |
| Fokus game berubah | Status fokus hilang; tidak ada pointer tertahan |
| Cabut keyboard/mouse/gamepad | Semua input dilepas |
| Shizuku dimatikan / layar dikunci | Input dilepas, tidak ada turbo tertinggal |
| Heartbeat berhenti, proses tetap hidup | Watchdog membatalkan input setelah >3 detik |
| Matikan proses app saat hold | Catat perilaku ROM; watchdog dalam proses tidak berjalan setelah proses mati |
| Gamepad analog | Dead zone bekerja, stick kiri menggerakkan joystick pertama |
| Export → import | Profil/aksi/interval tetap sama |
| Impor file invalid/duplikat/terlalu besar | Ditolak tanpa menimpa profil sebelumnya |
| Stop dari notifikasi | Bubble, preview, dan semua sentuhan hilang |

Jangan menyentuh area tes secara manual ketika menguji injeksi. LULUS hanya memvalidasi layar tes; ulangi skenario hold/WASD/mouse dalam game yang dituju. Jika sebuah game menolak overlay atau injeksi, catat sebagai batas kompatibilitas, bukan menganggap status backend sebagai bukti keberhasilan.
