# User Guide

A complete walkthrough of every feature in MountX.

---

## Interface Overview

MountX has five tabs accessible from the bottom navigation bar:

| Tab | Icon | Purpose |
|---|---|---|
| **Dashboard** | Home | System status, mounted apps summary, quick stats |
| **Apps** | Grid | Browse installed apps, manage mounts per app |
| **Storage** | Disk | View disks, partitions, and disk-level tools |
| **Logs** | List | Live and historical mount activity logs |
| **Settings** | Gear | Configure SD path, I/O Booster, language, and more |

---

## Dashboard

The Dashboard is your at-a-glance status panel.

- **Master Control widget** — Shows all currently mounted apps and their status (Mounted, Unmounted, Error).
- **Disk Overview** — A summary of your MicroSD partition: total capacity, used space, and free space.
- **Stats bar** — Offloaded data total and number of active mount namespaces.

If the module is not running or root access is missing, a warning card appears here with a direct action button.

---

## Apps Tab

This tab lists all installed apps on your device. Apps with recognized game data directories are highlighted.

### Filtering & Sorting
- Use the **search bar** at the top to filter by app name.
- Tap the sort icon to sort by name, size, or mount status.

### App Detail

Tap any app to open its detail screen. This shows:

| Section | What it shows |
|---|---|
| **APK** | APK size and install path |
| **Lib** | Native library size |
| **Data** | `Android/data/<package>` size and current location |
| **OBB** | `Android/obb/<package>` size and current location |

Each directory card shows a status pill — **On Internal** or **On MicroSD** — and the directory's current mount state.

### Manage Storage

Tap **Manage Storage** in App Detail to open the migration hub:

1. **Step 1 — Select Directories**: Choose which directories to move (Data, OBB, custom paths). Each card shows the directory size and current location.
2. **Step 2 — Select Disk**: Choose the target disk from auto-detected storage devices. Partitions are listed with their filesystem, capacity, and free space. Partitions formatted as F2FS or Ext4 display a **Recommended** badge.
3. **Step 3 — Review & Execute**: A sticky bottom bar shows a summary (`Moving X GB to [Disk] • [Partition] (Free: Y GB)`). Tap **Move to [Disk]** to start the transfer.

---

## How to Mount an App

Mounting moves the physical game data to the MicroSD partition and creates a bind-mount so the game sees the data at its original internal path.

1. Open the **Apps** tab and tap the app you want to mount.
2. Tap **Manage Storage**.
3. Select the directories to move (typically Data and OBB).
4. Select your MicroSD partition in Step 2.
5. Review the summary and tap **Move to [Disk]**.
6. Wait for the transfer and mount to complete. The status pill will change to **On MicroSD**.

> The game does not need to know about any of this. It reads and writes to its usual path. MountX handles the redirection transparently.

---

## Mount Modes

Each app can be configured to use one of two mount modes:

| Mode | What is mounted | Best for |
|---|---|---|
| **PKG** | Entire `Android/data/<package>` directory | Wuthering Waves, PUBG Mobile, COD Mobile |
| **FILES** | Only `Android/data/<package>/files` subdirectory | Genshin Impact, Honkai: Star Rail, Zenless Zone Zero |

**When to use FILES mode**: Some games store their local database or save files directly inside `Android/data/<package>` (not in `files`). Moving that directory entirely can cause login failures or corrupted saves. FILES mode keeps the root of the data directory on internal storage and only moves the large `files` subfolder.

To change the mode:
1. Open App Detail.
2. Tap the **Mount Mode** selector (PKG / FILES).
3. If the app is currently mounted, unmount it first, change the mode, then re-mount.

---

## How to Unmount an App

Unmounting moves the data back to internal storage and removes the bind-mount.

1. Open **App Detail** for the mounted app.
2. Tap **Manage Storage**.
3. In the migration hub, tap **Restore to Internal**.
4. Confirm the operation. The data is physically copied back to internal storage and the bind-mount is removed.

---

## Storage Tab

The Storage tab shows all attached storage devices.

### Disk List
Each detected disk (MicroSD, USB OTG, external SSD) is listed with:
- Device node (e.g., `mmcblk0`)
- Total capacity
- Free space progress bar
- Partition list

### Disk Detail
Tap a disk to open its detail view, showing all partitions with filesystem type, size, and mount status.

### Disk Tools

Tap **Disk Tools** on a disk to access:

| Tool | Description |
|---|---|
| **Rescan** | Re-detect block devices (useful after plugging in new storage) |
| **Benchmark** | Run a sequential read/write speed test on the selected partition |
| **Partition & Format** | Repartition and format the disk (destructive — erases all data) |
| **Mount Partition** | Manually mount an unmounted partition to `/data/sdext2` |

### Partition Tools
Tap a specific partition to access partition-level tools:
- **Format** — Reformat the partition to F2FS or Ext4.
- **Run Benchmark** — Test I/O speed (useful for validating card quality).
- **Set as Default** — Set this partition as the active MountX target in Settings.

---

## I/O Booster

I/O Booster applies kernel block queue optimizations at boot to improve MicroSD throughput:

| Parameter | Default Value | Effect |
|---|---|---|
| `READ_AHEAD_KB` | 2048 | Increases sequential read buffer |
| `IO_SCHEDULER` | none | Bypasses the scheduler queue for low-latency |
| `RQ_AFFINITY` | 2 | Pins I/O requests to performance CPU cores |
| `NR_REQUESTS` | 256 | Increases request queue depth |
| `VFS_CACHE_PRESSURE` | 20 | Retains more filesystem cache in RAM |

These values are applied by the module's boot service. You can toggle I/O Booster on or off in **Settings → I/O Booster** without reflashing the module.

---

## Logs Tab

The Logs tab shows real-time output from MountX's mount engine.

- **Live mode (tail)** — Streams new log lines as they arrive. Useful during a mount/unmount operation to track progress.
- **Log levels** — Lines are color-coded: INFO (white), WARN (yellow), ERROR (red).
- **Copy / Share** — Tap and hold a log line to copy it, or use the share button to export the full log.

When reporting a bug, always attach the relevant log output.

---

## Backup & Restore Configuration

MountX stores your app list and mount configurations in a local database. You can export this as a JSON file for backup or transfer.

### Export
1. Go to **Settings → Backup & Restore**.
2. Tap **Export Configuration**.
3. Choose a save location. The file is named `mountx_config_<timestamp>.json`.

### Import
1. Tap **Import Configuration**.
2. Select the previously exported `.json` file.
3. MountX will merge the imported configuration with the current database.

> Importing does not automatically re-mount apps. After restoring a configuration, go to the Apps tab and re-mount apps individually.

---

## Settings Reference

| Setting | Description |
|---|---|
| **SD Block Device** | Block device node for the target partition (e.g., `/dev/block/mmcblk0p3`). Auto-populated from Storage scan. |
| **SD Mount Point** | Directory where the partition is mounted (default: `/data/sdext2`). |
| **Filesystem Type** | F2FS or Ext4 — must match the actual partition format. |
| **I/O Booster** | Enable/disable kernel I/O optimizations applied at boot. |
| **Language** | App display language: English or Bahasa Indonesia. |
| **Theme** | Light, Dark, or System default. |
| **Check for Updates** | Manually check GitHub for a newer release. |
| **Backup & Restore** | Export and import app configuration. |
| **About** | App version, module version, author info, open-source licenses. |

---

## Tips for Best Performance

1. **Use F2FS over Ext4** when formatting your MicroSD partition. F2FS is designed for flash storage and delivers lower write latency.
2. **Enable I/O Booster** — the read-ahead buffer (`READ_AHEAD_KB=2048`) alone can significantly reduce game asset loading times.
3. **Use a fast card** — UHS-I Speed Class 3 (U3) or higher is strongly recommended. A1/A2 Application Performance Class cards are even better for random I/O.
4. **Mount only Data/OBB** — Avoid mounting APK or Lib directories unless you know what you're doing. APKs are re-read frequently and mounting them to a slower MicroSD can harm app startup time.
5. **Keep 10–15% of the partition free** — Both F2FS and Ext4 benefit from free space for garbage collection.
6. **Don't remove the MicroSD while a game is running** — This will immediately crash the game and may corrupt the mounted directory. Always close the game first.

---

*For common questions, see [FAQ.md](FAQ.md).*
