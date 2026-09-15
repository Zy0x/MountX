# Changelog

Semua perubahan penting pada proyek **Mountify** akan didokumentasikan dalam berkas ini.

Format changelog ini mengacu pada [Keep a Changelog](https://keepachangelog.com/id/1.0.0/) dan menganut [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [1.0.0] - 2026-09-16

### Ditambahkan
- **Aplikasi Native Android (Jetpack Compose & Material You)**
  - Tampilan modern dengan Dynamic Color (Android 12+) serta mode terang dan gelap.
  - Dukungan multi-bahasa: Bahasa Indonesia (ID) dan English (EN).
  - Navigasi interaktif berbasis animasi transisi halus dengan 5 seksi utama: Beranda, Game, Penyimpanan, Log, dan Pengaturan.
- **Dukungan Universal Root Solution**
  - Kompatibel penuh dengan Magisk, KernelSU, dan APatch melalui integrasi `libsu`.
  - Pengecekan status root dan keberadaan modul secara real-time di layar Beranda.
- **Manajemen Game & Bind Mount**
  - Pengelolaan daftar game dengan dua mode: `PKG` (seluruh folder) dan `FILES` (subfolder files untuk game berbasis basis data internal).
  - Pilihan cepat untuk memilih aplikasi terpasang dari sistem.
  - Kontrol manual untuk mount dan unmount per game atau sekaligus (*mount all* / *unmount all*).
  - Fitur perpindahan data fisik game antara penyimpanan internal dan partisi MicroSD eksternal.
- **Manajemen Partisi MicroSD & Format**
  - Deteksi otomatis perangkat blok MicroSD (`mmcblk*`, `sd*`) dengan opsi penyesuaian manual.
  - Fitur format partisi dengan pilihan sistem berkas **F2FS** (Direkomendasikan untuk memori flash) dan **Ext4** disertai konfirmasi bertingkat.
  - Indikator penggunaan kapasitas penyimpanan MicroSD interaktif.
- **Pemantau Log Real-time**
  - Pembaca log aktivitas mount secara live (`tail` otomatis).
  - Pewarnaan baris berdasarkan level (Info, Sukses, Error, Debug) dan opsi ekspor/berbagi log.
- **Pencadangan & Pemulihan (Backup & Restore)**
  - Ekspor dan impor konfigurasi daftar game dalam format JSON.
- **Pembaruan & Tentang**
  - Integrasi pemeriksaan pembaruan versi langsung dari GitHub Releases.
  - Informasi lisensi, tautan repositori, dan tautan dukungan pengembangan.
- **Modul Magisk / KernelSU Mountify**
  - Skrip `service.sh` dinamis membaca berkas `gamelist.conf` dan `config.conf`.
  - Dukungan pemasangan otomatis saat perangkat selesai melakukan proses *booting*.
- **CI/CD Otomatis**
  - Alur kerja GitHub Actions untuk kompilasi otomatis APK rilis (*signed*) dan pengemasan modul zip.
