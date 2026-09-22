# MountX Architecture & Technical Internals

This document provides an in-depth technical overview of how MountX operates at the Linux kernel and Android framework layers.

---

## 1. The Core Problem: Modern Game Storage & Scoped Storage

### 1.1 Heavy Game Asset Sizes
Modern AAA mobile titles built on Unreal Engine 4/5 or Unity (e.g., *Wuthering Waves*, *Genshin Impact*, *Honkai: Star Rail*, *Zenless Zone Zero*) package only a small base APK (1–3 GB) from the Play Store. Upon launch, the in-game patcher downloads 30 GB to 80 GB of additional asset bundles into `/data/media/0/Android/data/<package>/files/`.

### 1.2 The Failure of User-Space Migration (FUSE & Scoped Storage)
In Android 10+ (API 29+), Google introduced Scoped Storage and migrated external storage emulation to a user-space FUSE/sdcardfs architecture. When users attempt to move or symlink these directories:
1. **`EXDEV: Cross-device link`**: User-space symlinks across storage devices are explicitly rejected by Android's VFS sandbox.
2. **FUSE Overhead**: File transfers passing through user-space FUSE daemons suffer severe I/O throughput penalties and high CPU context-switch latency.
3. **Database Lockup**: Moving SQLite databases (`/data/data/<package>/databases`) to external flash frequently results in `SQLITE_BUSY` or `SQLITE_IOERR` due to missing POSIX locking semantics on non-Linux filesystems or slow random write performance.

---

## 2. MountX Kernel Bind-Mount Architecture

MountX completely bypasses user-space storage daemons by executing kernel-level **VFS bind-mounts** (`mount --bind`) within the root mount namespace.

```
+-------------------------------------------------------------------------+
|                              Android Framework                          |
|   Game Engine (Unity / UE4) requests /sdcard/Android/data/<package>/... |
+-------------------------------------------------------------------------+
                                    |
                                    v
+-------------------------------------------------------------------------+
|                           Linux Kernel VFS                              |
|   Global Mount Namespace with Shared Propagation (MS_SHARED / MS_REC)   |
|                                                                         |
|   mount --bind -o noatime /data/sdext2/MountX/app/<pkg>/Android/data     |
|                           /data/media/0/Android/data/<pkg>              |
+-------------------------------------------------------------------------+
                 |                                      |
                 v                                      v
+----------------------------------+  +-----------------------------------+
|     Internal Flash (UFS 3.1/4.0) |  |   External Media (MicroSD / SSD)  |
|  - APK & Compiled Native Libs    |  |  - High-volume Asset Bundles      |
|  - Private SQLite Databases      |  |  - OBB Expansion Packs            |
|  - Ephemeral Shader Caches       |  |  - Custom User Directories        |
+----------------------------------+  +-----------------------------------+
```

### 2.1 Namespace Propagation
On Android, each application process runs inside its own isolated mount namespace created by Zygote. Standard `mount` commands executed from an app shell are only visible within that sub-shell.

MountX solves this by:
1. Interfacing with root daemons (**Magisk**, **KernelSU**, or **APatch**) via `libsu`.
2. Executing bind-mounts directly in the global/init mount namespace (PID 1).
3. Using slave/shared propagation (`MS_SHARED | MS_REC`) so the bind-mount automatically propagates to all existing and newly spawned application namespaces.

---

## 3. Granular Category Storage Offloading

Instead of an all-or-nothing approach, MountX decouples app storage into 6 distinct categories:

| Category | Source Path | Target Destination | Handling Strategy |
|---|---|---|---|
| **Data Aplikasi** | `/data/media/0/Android/data/<pkg>` | External partition (`/data/sdext2/...`) | **Bind-mounted**. Contains 90%+ of total game size. |
| **OBB** | `/data/media/0/Android/obb/<pkg>` | External partition (`/data/sdext2/...`) | **Bind-mounted**. Expansion files. |
| **APK & Libs** | `/data/app/<pkg>...` | Internal Flash | **Kept Internal**. Preserves instant cold boot launch speed. |
| **Private Data** | `/data/data/<pkg>` | Internal Flash | **Kept Internal**. Protects SQLite databases and credentials from I/O bottlenecks. |
| **Cache** | `/data/data/<pkg>/cache` | Internal Flash | **Kept Internal**. Temporary shader compilation stays on high-speed UFS. |
| **Custom Path** | User-defined path | External partition | **Bind-mounted**. For emulators, standalone loaders, or offline maps. |

---

## 4. Block Device & Partition Subsystem

### 4.1 Device Discovery
MountX queries sysfs (`/sys/block/`) and `/proc/partitions` to detect available block devices:
- **MicroSD cards**: Detected via `mmcblk*` nodes.
- **External USB SSDs / Flash drives**: Detected via `sd*` nodes.
- **NVMe drives**: Detected via `nvme*` nodes.

### 4.2 Supported Filesystems
* **F2FS (Flash-Friendly File System)**: Strongly recommended. Designed specifically for NAND flash memory, offering append-only logging, low write amplification, and native POSIX permissions.
* **Ext4**: Recommended alternative. Mature, rock-solid Linux filesystem with full permission and symlink support.
* **exFAT / FAT32**: Supported for standard storage, but lacks native Linux file permission semantics.

---

## 5. Kernel I/O Queue Optimization (I/O Booster)

External storage buses (e.g., SDR104 MicroSD controllers operating up to 208 MHz) require optimized block queue parameters for sustained sequential read streaming:

| Parameter | Path | MountX Optimized Value | Purpose |
|---|---|---|---|
| `read_ahead_kb` | `/sys/block/<dev>/queue/read_ahead_kb` | `512` – `2048` KB | Pre-fetches large texture blocks into memory cache before the game requests them. |
| `scheduler` | `/sys/block/<dev>/queue/scheduler` | `noop` / `deadline` / `kyber` | Bypasses complex CPU scheduling queues for low-latency solid-state storage. |
| `rq_affinity` | `/sys/block/<dev>/queue/rq_affinity` | `2` | Pins I/O completion interrupts to the CPU core that initiated the request. |
| `nr_requests` | `/sys/block/<dev>/queue/nr_requests` | `256` | Increases queue depth for high-throughput batch operations. |

---

## 6. Boot Service Lifecycle

MountX provides a companion module script (`service.sh`) installed in the root manager's late-start directory:
1. Device boots into Android.
2. The root framework triggers `service.sh` during `late-start` (after data decryption).
3. The script verifies block device readiness and mounts the external partition to `/data/sdext2`.
4. Reads the configured active mount list and executes `mount --bind` for all enabled apps.
5. Applies I/O Booster sysfs parameters.
6. When the user launches a game, all mount points are already transparently active.

---

**Documentation Navigation:**
[Home](../README.md#documentation) &bull; [Installation Guide](INSTALL.md) &bull; [User Guide](USAGE.md) &bull; [Architecture & Internals](ARCHITECTURE.md) &bull; [FAQ & Troubleshooting](FAQ.md) &bull; [Contributing Guide](CONTRIBUTING.md)
