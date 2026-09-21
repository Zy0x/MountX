# Installation Guide

This guide walks you through installing MountX from scratch — from checking prerequisites to verifying your first successful mount.

---

## Prerequisites

Before you begin, make sure your device meets all of the following requirements:

### 1. Root Access
Your device must be rooted using one of the supported frameworks:
- **Magisk** (recommended) — v24.0 or newer
- **KernelSU** — any stable release
- **APatch** — any stable release

### 2. Android Version
Android **10 to 15** (API 29–35). Older versions are not supported due to bind-mount restrictions introduced in Android 10.

### 3. MicroSD Card
- A MicroSD card installed in the device's physical slot.
- The card must have **at least two partitions**:
  - Partition 1: Standard FAT32/exFAT (for regular files, optional but recommended).
  - Partition 2: **F2FS or Ext4** — this is where game data will be stored.

  > If your card only has one partition, see [Partition Setup](#partition-setup) below.

### 4. Sufficient Space
Ensure your MicroSD partition has enough free space for the games you plan to move. Wuthering Waves, for example, requires ~30 GB.

---

## Download

1. Navigate to the [GitHub Releases page](https://github.com/Zy0x/MountX/releases/latest).
2. Download both files:
   - `MountX-Magisk-vX.X.X.zip` — the root module (for Magisk / KernelSU / APatch).
   - `MountX-vX.X.X.apk` — the companion Android app.

---

## Step 1 — Flash the Root Module

The module runs a boot-time service that applies bind-mounts and I/O tweaks before the game launchers start.

### Magisk
1. Open **Magisk** app → **Modules** tab.
2. Tap **Install from storage**.
3. Select `MountX-Magisk-vX.X.X.zip`.
4. Wait for the flash to complete, then tap **Reboot**.

### KernelSU
1. Open **KernelSU Manager** → **Modules** tab.
2. Tap the **+** button.
3. Select `MountX-Magisk-vX.X.X.zip`.
4. Tap **Reboot** when prompted.

### APatch
1. Open **APatch** → **Modules**.
2. Tap **Install** and select the `.zip` file.
3. Reboot when prompted.

---

## Step 2 — Install the App

After the device boots back up:

1. Open your file manager and locate `MountX-vX.X.X.apk`.
2. Tap it to install. If prompted, allow installation from unknown sources.
3. Open **MountX** from your app drawer.

---

## Step 3 — Grant Root Permission

On the first launch, MountX will request superuser access.

- Tap **Grant** in the root manager prompt.
- If the prompt doesn't appear, open your root manager, find MountX in the app list, and manually grant access.

> MountX requires root to execute bind-mount commands and read block device information. It does not phone home or collect any data.

---

## Step 4 — Initial Configuration

### Set the SD Partition Path
1. Go to **Settings** (gear icon in the bottom navigation bar).
2. Under **SD Partition**, tap the block device field.
3. MountX will auto-scan and list all detected block devices. Select the partition that is formatted as F2FS or Ext4 (e.g., `/dev/block/mmcblk0p3`).
4. Tap **Save**.

### Verify the Mount Point
The default mount point is `/data/sdext2`. You can leave this as-is unless you have a specific reason to change it.

### Configure I/O Booster (Optional)
Under **Settings → I/O Booster**, enable the toggle to apply kernel block queue optimizations at boot. This improves MicroSD read/write throughput significantly on most devices.

---

## Step 5 — Verify the Setup

1. Go to the **Storage** tab. You should see your MicroSD partition listed with its total and free space.
2. Go to the **Apps** tab. Installed games should appear in the list.
3. Select a game, tap **Manage Storage**, and attempt to move a directory to the MicroSD. If it completes without errors, the setup is working correctly.

---

## Partition Setup

If your MicroSD card doesn't have an Ext4/F2FS partition, you can create one directly from within MountX:

1. Go to **Storage** → select your MicroSD disk → tap **Disk Tools**.
2. Tap **Partition & Format**.
3. Choose your desired layout (e.g., keep partition 1 as exFAT, create partition 2 as F2FS).
4. Confirm the operation. **This will erase data on the card.**
5. After formatting, go to **Settings** and select the new partition.

---

## Troubleshooting

### MountX shows "Root access denied"
- Open your root manager and check if MountX has been granted superuser access.
- Try revoking and re-granting access, then restarting the app.

### MicroSD partition not detected
- Ensure the card is properly seated.
- Go to **Storage → Disk Tools → Rescan** to force a re-detection of block devices.
- Check that the partition is formatted as F2FS or Ext4 (not exFAT or FAT32).

### Module not listed in Magisk after reboot
- Re-flash the module. If it fails again, check that your Magisk version is v24.0 or newer.
- On KernelSU/APatch, ensure your kernel version supports the required bind-mount syscalls.

### "Needs Migration" shown on an app
- This appears when game data is split across internal and MicroSD storage, or when a previous mount state is inconsistent. Tap the banner and follow the on-screen steps to clean up the state.

### Game crashes immediately after mounting
- Unmount the game (tap **Unmount** in App Detail).
- Verify the MicroSD partition mount is healthy: `ls /data/sdext2` should list game files.
- Check the **Logs** tab for specific bind-mount errors.
- Try switching mount mode (PKG ↔ FILES) in App Detail settings.

### Mount fails with "No such file or directory"
- The module's boot service may not have mounted the SD partition yet. Reboot and try again.
- Confirm that `/data/sdext2` exists: tap **Rescan** in the Storage tab.

---

*For usage instructions after installation, see [USAGE.md](USAGE.md).*
