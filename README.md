# Mountify 🚀

[![Build & Release](https://github.com/Zy0x/Mountify/actions/workflows/build.yml/badge.svg)](https://github.com/Zy0x/Mountify/actions/workflows/build.yml)
[![Min SDK](https://img.shields.io/badge/Min%20SDK-Android%2010%20(API%2029)-brightgreen.svg)](https://developer.android.com)
[![Target SDK](https://img.shields.io/badge/Target%20SDK-Android%2015%20(API%2035)-blue.svg)](https://developer.android.com)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

**Mountify** adalah aplikasi native Android & modul root yang memungkinkan pemindahan dan bind-mount data game besar (Wuthering Waves, Honkai: Star Rail, Genshin Impact, ZZZ, PUBG Mobile, dll.) dari penyimpanan internal ke **partisi MicroSD eksternal** tanpa terkena FUSE cross-device errors di Android 10+.

---

## ✨ Fitur Utama

- 🎮 **Game Manager**: Tambah/hapus game, pilih mode mount (`PKG` atau `FILES`), toggle mount/unmount langsung dari UI.
- ⚡ **Physical Data Migration**: Pindah file fisik game dari Internal Storage (`/data/media/0/Android/data/...`) ke MicroSD (`/data/sdext2/Android/data/...`) atau sebaliknya dengan aman.
- 💾 **MicroSD Partition & Format**: Deteksi block device otomatis (`mmcblk*`, `sd*`), format partisi ke **F2FS** (Direkomendasikan untuk flash memory) atau **Ext4** dengan konfirmasi keamanan berlapis.
- 🛡️ **Universal Root Compatibility**: Mendukung penuh **Magisk**, **KernelSU**, dan **APatch** via `libsu`.
- 📊 **Real-time Log Viewer**: Pantau proses mounting dan riwayat event secara live (`tail -f` style) dengan syntax highlighting per level log.
- 📦 **Backup & Restore**: Ekspor dan impor konfigurasi daftar game dalam format JSON.
- 🎨 **Material You (Material Design 3)**: Desain modern dengan Dynamic Color, dark/light theme, dan animasi transisi halus.
- 🌐 **Multilingual**: Dukungan penuh Bahasa Indonesia (ID) dan Bahasa Inggris (EN).

---

## 📋 Dua Mode Mount

| Mode | Keterangan | Rekomendasi Game |
|---|---|---|
| `PKG` | Mount seluruh folder `Android/data/<package>` | Wuthering Waves, PUBG Mobile, COD Mobile |
| `FILES` | Mount hanya subfolder `Android/data/<package>/files` (database tetap di internal) | Honkai: Star Rail, Genshin Impact, Zenless Zone Zero |

---

## 🛠️ Persyaratan Sistem

1. Perangkat Android dengan akses **Root** (**Magisk**, **KernelSU**, atau **APatch**).
2. Android 10 (API 29) hingga Android 15 (API 35+).
3. MicroSD Card dengan partisi kedua beralamat `/dev/block/mmcblk0p3` (atau disesuaikan di Pengaturan).
4. Partisi diformat dengan **F2FS** atau **Ext4**.

---

## 🚀 Cara Instalasi

1. Download file **Mountify APK** dan **Modul Magisk Zip** dari halaman [GitHub Releases](https://github.com/Zy0x/Mountify/releases).
2. Flash `mountify-magisk-module.zip` melalui Magisk / KernelSU / APatch Manager, lalu reboot perangkat.
3. Install `Mountify.apk` dan buka aplikasinya.
4. Berikan izin **Superuser (Root)** saat diminta.
5. Atur daftar game di tab **Games** dan aktifkan mount!

---

## ⚙️ Struktur Repositori

```
Mountify/
├── app/                  # Android Native App (Kotlin, Jetpack Compose, Hilt, Room, libsu)
│   ├── src/main/
│   │   ├── java/app/mihon/
│   │   │   ├── data/     # Room Database, Models, Repositories
│   │   │   ├── root/     # Low-level Mount & Storage shell managers
│   │   │   ├── service/  # Background & Boot services
│   │   │   ├── ui/       # Jetpack Compose UI Screens & Navigation
│   │   │   └── util/     # AppPreferences, UpdateChecker, Formatters
│   │   └── res/          # Vector drawables, strings (EN & ID)
├── module/               # Modul Magisk / KernelSU
│   ├── module.prop       # Metadata modul
│   ├── service.sh        # Boot-time service script
│   ├── config.conf       # Konfigurasi SD_BASE, SD_BLOCK, FS_TYPE
│   └── gamelist.conf     # Daftar game dinamis
├── .github/workflows/    # CI/CD automated build & release
└── README.md
```

---

## 🔨 Build dari Source

Untuk membangun project secara lokal:

```bash
# Clone repositori
git clone https://github.com/Zy0x/Mountify.git
cd Mountify

# Build Debug APK
./gradlew assembleDebug

# Build Release APK
./gradlew assembleRelease
```

---

## 🔑 CI/CD & GitHub Actions

Project ini sudah dilengkapi dengan CI/CD otomatis. Untuk mengaktifkan release APK yang sudah ditandatangani (*signed release APK*), ikuti panduan di [KEYSTORE_SETUP.md](KEYSTORE_SETUP.md).

---

## 📜 Lisensi & Kredit

- Dilisensikan di bawah **MIT License**.
- Dibuat dengan ❤️ oleh **Noir** ([@Zy0x](https://github.com/Zy0x)).
