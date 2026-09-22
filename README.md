<div align="center">

<img src="Icon/MountX_icon_Transparent.png" alt="MountX Logo" width="100"/>

# MountX

**Granular Storage Offloader & Native Bind-Mount Engine for Android**

[![Build & Release](https://github.com/Zy0x/MountX/actions/workflows/build.yml/badge.svg)](https://github.com/Zy0x/MountX/actions/workflows/build.yml)
[![Latest Release](https://img.shields.io/github/v/release/Zy0x/MountX?color=blue&label=Latest%20Release)](https://github.com/Zy0x/MountX/releases)
[![Android](https://img.shields.io/badge/Android-10--16%20(API%2029--36)-brightgreen.svg)](https://developer.android.com)
[![Root](https://img.shields.io/badge/Root-Magisk%20%7C%20KernelSU%20%7C%20APatch-orange.svg)](https://github.com/topjohnwu/Magisk)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

</div>

Modern mobile titles like *Wuthering Waves*, *Genshin Impact*, and *Honkai: Star Rail* frequently demand 30 GB to 80 GB each, quickly exhausting internal flash storage. Standard Android storage migration fails due to FUSE isolation and `EXDEV: Cross-device link` errors.

**MountX** solves this at the kernel level. Using Linux VFS bind-mounts with namespace propagation, MountX redirects data from internal storage (`/data/media/0/Android/data`, `obb`, or custom paths) to a dedicated MicroSD partition, external SSD, or USB OTG drive. Games read and write at full speed without realizing their assets live on external media.

---

## Screenshots

<table align="center">
  <tr>
    <td align="center" width="25%">
      <img src="docs/screenshots/real/01_dashboard_dark.png" alt="Dashboard Dark" width="100%"/><br/>
      <sub><b>Master Control (Dark)</b><br/>Mount status & disk overview</sub>
    </td>
    <td align="center" width="25%">
      <img src="docs/screenshots/real/02_dashboard_light.png" alt="Dashboard Light" width="100%"/><br/>
      <sub><b>Master Control (Light)</b><br/>Sandstone light theme</sub>
    </td>
    <td align="center" width="25%">
      <img src="docs/screenshots/real/03_app_storage_breakdown.png" alt="Storage Breakdown" width="100%"/><br/>
      <sub><b>Granular Storage</b><br/>Asset breakdown & custom paths</sub>
    </td>
    <td align="center" width="25%">
      <img src="docs/screenshots/real/04_apps_light.png" alt="Apps Manager Light" width="100%"/><br/>
      <sub><b>Apps Manager</b><br/>Per-app mount toggles</sub>
    </td>
  </tr>
  <tr>
    <td align="center" width="25%">
      <img src="docs/screenshots/real/05_disk_management.png" alt="Disk Management" width="100%"/><br/>
      <sub><b>Disk & Partitions</b><br/>Visual partition map & telemetry</sub>
    </td>
    <td align="center" width="25%">
      <img src="docs/screenshots/real/06_partition_tools.png" alt="Partition Tools" width="100%"/><br/>
      <sub><b>Partition Tools</b><br/>F2FS / Ext4 / exFAT formatting</sub>
    </td>
    <td align="center" width="25%">
      <img src="docs/screenshots/real/07_disk_performance.png" alt="Disk Tools" width="100%"/><br/>
      <sub><b>I/O Speed Booster</b><br/>Queue tuning & benchmarks</sub>
    </td>
    <td align="center" width="25%">
      <img src="docs/screenshots/real/08_settings_dark.png" alt="Settings" width="100%"/><br/>
      <sub><b>Settings & Backup</b><br/>Snapshot portability & recovery</sub>
    </td>
  </tr>
</table>

---

## Core Capabilities

### Granular Category Storage Offloading
Unlike legacy tools that force an all-or-nothing move, MountX inspects and isolates storage components individually:
* **Asset Bundles (`Android/data`)** and **Expansion Files (`Android/obb`)**: Safely moved to external storage.
* **Internal Data & SQLite Databases (`/data/data`)**: Retained in high-speed internal flash to eliminate database lockups and micro-stutters.
* **Custom Directories**: Bind-mount any user-defined folder path for emulators, standalone game engines, or offline map packages.

### Storage & Block Device Management
* **Universal Hardware Support**: Automatically detects MicroSD cards (`mmcblk*`), external USB-C SSDs (`sd*`), and NVMe storage devices.
* **Integrated Partitioning & Formatting**: Format partitions to **F2FS**, **Ext4**, **exFAT**, or **FAT32** with safety verification.
* **Filesystem Health**: Built-in partition label editor, unmounted filesystem checking (`fsck`), and manual or scheduled `FSTRIM` execution.

### Kernel I/O Queue Optimizer
* Tune block-device read-ahead buffers (up to 2048 KB) for sustained sequential streaming of large game assets.
* Select optimal Linux I/O schedulers (`noop`, `deadline`, `kyber`, `bfq`) applied automatically at boot.
* Built-in sequential read throughput and access latency benchmark tool.

### Root Ecosystem Integration
* Built entirely on `libsu` for reliable, isolated root process execution.
* Compatible across **Magisk**, **KernelSU**, and **APatch**.
* Companion root module executes at late-boot (`service.sh`) to mount configured paths before user applications launch.

### Interface & Usability
* Dual-theme design: High-contrast AMOLED Cyber Dark and warm Sandstone Light.
* Real-time mount activity logs with logcat filtering.
* Single-file JSON snapshot export and import for seamless migration across ROM installs or devices.
* Emergency rescue script generator (`/sdcard/mountx_panic_reset.sh`) for failsafe recovery.

---

## Requirements

1. **Root Solution**: Magisk 24.0+, KernelSU 0.9.0+, or APatch 10.0+
2. **Android Version**: Android 10 to Android 16 (API 29–36)
3. **External Storage**: MicroSD card, External SSD, or USB-C OTG flash drive
4. **Filesystem**: **F2FS** or **Ext4** recommended for full POSIX permission support, symlinks, and flash endurance. **exFAT** and **FAT32** are supported with standard Android permission compatibility.

---

## Quick Start

### 1. Flash the Module
Download `MountX-Magisk-v<version>.zip` from [GitHub Releases](https://github.com/Zy0x/MountX/releases). Flash it in Magisk, KernelSU, or APatch, then reboot your device.

### 2. Install the APK
Install `MountX-v<version>.apk`. On first launch, grant Superuser permissions when prompted.

### 3. Configure & Offload
1. Open the **Storage** tab to verify your MicroSD or external disk partition is mounted.
2. Go to the **Apps** tab, select your target game, and tap **Kelola Penyimpanan**.
3. Choose the storage categories to offload (e.g. Data Aplikasi / OBB) and tap **Pindahkan**.
4. Once migration completes, the bind-mount is active. Launch your game normally.

---

## Documentation

Detailed technical guides and reference manuals:

| Document | Focus |
|---|---|
| [Installation Guide](docs/INSTALL.md) | Step-by-step setup, module flashing, root permissions, and initial configuration |
| [User Guide](docs/USAGE.md) | Complete walkthrough of all 5 tabs, storage categories, partition tools, and I/O booster |
| [FAQ & Troubleshooting](docs/FAQ.md) | Answers to common questions, error codes, filesystem choices, and recovery |
| [Contributing Guide](docs/CONTRIBUTING.md) | Architecture overview, code style, building, and pull request workflow |
| [Changelog](CHANGELOG.md) | Full release and version history |

---

## Build from Source

MountX uses Gradle and Jetpack Compose. JDK 17 is required.

```powershell
# Clone the repository
git clone https://github.com/Zy0x/MountX.git
cd MountX

# Build Release APK
.\gradlew.bat assembleRelease

# Output: app/build/outputs/apk/release/app-release.apk
```

For release signing setup, refer to [KEYSTORE_SETUP.md](KEYSTORE_SETUP.md).

---

## Support the Project

MountX is free and open source. If this project saves your phone storage, consider supporting ongoing development:

| Platform | Link |
|---|---|
| ☕ Ko-fi | [Support on Ko-fi](https://ko-fi.com/Zy0x) |
| 💳 PayPal | [Donate via PayPal](https://paypal.me/Zy0x) |
| 🇮🇩 Saweria | [Dukung via Saweria](https://saweria.co/Zy0x) |

---

## License

MountX is licensed under the [MIT License](LICENSE).

Developed and maintained by **Noir** ([@Zy0x](https://github.com/Zy0x)).
