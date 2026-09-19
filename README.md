# MountX

[![Build & Release](https://github.com/Zy0x/Mountify/actions/workflows/build.yml/badge.svg)](https://github.com/Zy0x/Mountify/actions/workflows/build.yml)
[![Min SDK](https://img.shields.io/badge/Min%20SDK-Android%2010%20(API%2029)-brightgreen.svg)](https://developer.android.com)
[![Target SDK](https://img.shields.io/badge/Target%20SDK-Android%2015%20(API%2035)-blue.svg)](https://developer.android.com)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

MountX adalah aplikasi Android native dan modul root untuk memindahkan dan melakukan bind-mount data game (seperti Wuthering Waves, Honkai: Star Rail, Genshin Impact, Zenless Zone Zero, PUBG Mobile) dari penyimpanan internal ke partisi MicroSD eksternal tanpa error FUSE cross-device pada Android 10 ke atas.

---

## Fitur Utama

- **Game Manager**: Pengelolaan daftar game, seleksi mode mount (`PKG` atau `FILES`), dan kontrol mount/unmount langsung dari antarmuka.
- **Physical Data Migration**: Pemindahan berkas data game antara penyimpanan internal (`/data/media/0/Android/data/...`) dan MicroSD (`/data/sdext2/Android/data/...`) secara aman dengan verifikasi integritas data.
- **MicroSD Partition & Formatter**: Deteksi otomatis perangkat blok (`mmcblk*`, `sd*`), pemformatan partisi ke sistem berkas F2FS atau Ext4 dengan konfirmasi pengamanan.
- **Root Compatibility**: Kompatibilitas dengan Magisk, KernelSU, dan APatch melalui integrasi `libsu`.
- **Real-time Log Viewer**: Pemantauan log aktivitas mount secara langsung (`tail` mode) dengan penanda level log.
- **Backup & Restore**: Ekspor dan impor konfigurasi daftar game dalam format JSON.
- **Material Design 3 (Material You)**: Antarmuka modern dengan Dynamic Color, mode terang dan gelap AMOLED.
- **Dukungan Multi-bahasa**: Bahasa Indonesia (ID) dan English (EN).

---

## Dua Mode Mount

| Mode | Keterangan | Rekomendasi Game |
|---|---|---|
| `PKG` | Mount seluruh direktori `Android/data/<package>` | Wuthering Waves, PUBG Mobile, COD Mobile |
| `FILES` | Mount subdirektori `Android/data/<package>/files` (basis data tetap pada penyimpanan internal) | Honkai: Star Rail, Genshin Impact, Zenless Zone Zero |

---

## Persyaratan Sistem

1. Perangkat Android dengan akses Root (Magisk, KernelSU, atau APatch).
2. Android 10 (API 29) hingga Android 15 (API 35+).
3. Kartu MicroSD dengan partisi kedua beralamat `/dev/block/mmcblk0p3` (atau dapat disesuaikan pada menu Pengaturan).
4. Partisi MicroSD diformat dengan sistem berkas F2FS atau Ext4.

---

## Instalasi

1. Unduh file APK dan arsip zip modul dari halaman [GitHub Releases](https://github.com/Zy0x/Mountify/releases).
2. Pasang `MountX-magisk-module.zip` melalui Magisk, KernelSU, atau APatch Manager, lalu muat ulang (reboot) perangkat.
3. Pasang `MountX.apk` dan buka aplikasi.
4. Berikan izin Superuser (Root) saat diminta.
5. Konfigurasikan daftar game pada tab Games dan aktifkan mount.

---

## Struktur Repositori

```
MountX/
├── app/                  # Aplikasi Android Native (Kotlin, Jetpack Compose, Hilt, Room, libsu)
│   ├── src/main/
│   │   ├── java/app/mountx/
│   │   │   ├── data/     # Room Database, Data Models, Repositories
│   │   │   ├── di/       # Hilt Dependency Injection Modules
│   │   │   ├── root/     # Engine Shell libsu, Mount & Storage Manager
│   │   │   ├── service/  # Background Services & Boot Receiver
│   │   │   ├── ui/       # Jetpack Compose Screens, ViewModels, & Navigation
│   │   │   └── util/     # DataStore Preferences, Update Checker, Formatters
│   │   └── res/          # Resource XML, String Localization (EN & ID)
├── module/               # Modul Root (Magisk, KernelSU, APatch)
│   ├── module.prop       # Metadata Modul
│   ├── service.sh        # Skrip Eksekusi Boot-Time
│   ├── config.conf       # Konfigurasi: SD_BASE, SD_BLOCK, FS_TYPE
│   └── gamelist.conf     # Daftar Game Dinamis
├── .github/workflows/    # CI/CD Automated Build Workflow
└── README.md
```

---

## Build dari Source

Untuk melakukan kompilasi proyek secara lokal:

```bash
# Clone repositori
git clone https://github.com/Zy0x/Mountify.git MountX
cd MountX

# Kompilasi Debug APK
./gradlew assembleDebug

# Kompilasi Release APK
./gradlew assembleRelease
```

---

## CI/CD & GitHub Actions

Repositori ini telah dikonfigurasi dengan alur kerja GitHub Actions. Untuk menandatangani APK rilis secara otomatis, ikuti petunjuk konfigurasi pada [KEYSTORE_SETUP.md](KEYSTORE_SETUP.md).

---

## Lisensi & Atribusi

- Dilisensikan di bawah [MIT License](LICENSE).
- Penulis: **Noir** ([@Zy0x](https://github.com/Zy0x)).
