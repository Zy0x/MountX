# Frequently Asked Questions

---

## General

### Do I need a specific MicroSD brand?
No specific brand is required, but card quality matters significantly. Look for:
- **UHS-I Speed Class 3 (U3)** or higher — ensures at least 30 MB/s write speed.
- **A1 or A2 Application Performance Class** — optimized for random I/O, which games rely on heavily.

Avoid cheap, unbranded cards. Slow cards will cause longer loading times and occasional frame stutters even with I/O Booster enabled. Samsung EVO Plus, SanDisk Extreme, and Lexar PLAY are commonly used with good results.

---

### Which storage categories should I offload to external media?

MountX breaks app storage into discrete categories. For optimal stability and performance:

| Category | Recommended Location | Rationale |
|---|---|---|
| **Data Aplikasi (`Android/data`)** | **External Storage** | Largest bulk of game data (textures, audio, 3D assets). Moving this frees 90%+ of space. |
| **OBB (`Android/obb`)** | **External Storage** | Game installation expansion packs. Safely stored externally. |
| **APK / Native Libs** | **Internal Storage** | Fast app startup and code execution without external bus bottlenecks. |
| **Private Data (`/data/data`)** | **Internal Storage** | SQLite databases, credentials, and settings. Keeping internal prevents database lockup. |
| **Cache** | **Internal Storage** | Temporary compile and shader cache. |

---

### Is it safe for my game data?

Yes, with caveats:

- MountX **physically copies** your game data to the MicroSD before setting up the bind-mount. If the transfer is interrupted, the original internal data is untouched.
- Once mounted, data integrity depends on the health of your MicroSD card. A failing or low-quality card can cause corruption — the same as writing to any unreliable disk.
- Always keep cloud saves enabled for your games where possible.
- Do not remove the MicroSD card while a game is open.

---

### Can I use it with multiple MicroSD partitions?

Yes. MountX supports multiple physical storage devices (MicroSD, USB OTG, external SSD via Type-C). In the migration hub (Step 2), you can select which disk and which specific partition to use per app. Each app's target partition is stored independently, so different games can be spread across different partitions.

---

## Installation

### My game crashes immediately after mounting — what should I do?

1. Go to **App Detail → Manage Storage** and tap **Restore to Internal** to unmount the game.
2. Check the **Logs** tab for specific errors during the mount operation.
3. In **App Detail → Kelola Penyimpanan**, ensure private data (`/data/data`) remains on internal storage, offloading only **Data Aplikasi** or **OBB**.
4. Verify the MicroSD partition is healthy: check free space and run a **Benchmark** from Disk Tools.
5. If the crash persists, the game may have anti-cheat or integrity checks that detect the modified mount namespace. Unfortunately, some games (particularly those with kernel-level anti-cheat) may not be compatible.

---

### App not showing in the Apps list?

- Only apps that have an `Android/data/<package>` directory are listed. Newly installed games that haven't been launched yet may not have created this directory.
- Launch the game once to initialize its data directory, then return to MountX and refresh the Apps tab.
- If the app is a system app, it will not appear — MountX is designed for user-installed apps.

---

### MountX says "Needs Migration" — what does that mean?

This banner appears when MountX detects an inconsistent state — for example, the game data is partially on internal and partially on MicroSD, or a previous mount was removed without properly restoring the data.

To resolve it:
1. Tap the **Needs Migration** banner.
2. Follow the on-screen steps. Typically this involves choosing whether to consolidate everything to internal storage or re-establish the mount to MicroSD.

---

## MicroSD & Storage

### What happens if I remove the MicroSD while a game is running?

The game will crash immediately. The bind-mount kernel namespace becomes invalid when the underlying device is disconnected. This is expected behavior — the kernel has no way to "pause" the game transparently.

After reinserting the MicroSD and rebooting, the mount service will re-establish the bind-mount automatically. Your data should be intact unless a write was in progress at the exact moment of removal.

---

### What happens on reboot with the MicroSD inserted?

The module's boot service (`service.sh`) runs automatically at each boot. It:
1. Mounts your SD partition to `/data/sdext2`.
2. Applies bind-mounts for all apps in your configuration.
3. Applies I/O Booster kernel parameters (if enabled).

This all happens before most user-space processes start, so games load directly from MicroSD without any delay.

---

### What happens on reboot without the MicroSD?

If the MicroSD is not present, the boot service detects that the block device is unavailable and skips the mount steps. Your games will revert to their internal storage paths. Since the data is physically on the MicroSD, they may fail to launch or launch with missing data. Reinserting the card and rebooting restores normal operation.

---

## Performance

### Does this affect battery life?

The impact is negligible in normal use. The kernel-level bind-mounts themselves consume essentially no CPU. I/O Booster's `VFS_CACHE_PRESSURE=20` setting keeps more data in RAM cache, which slightly increases RAM usage but reduces MicroSD reads — actually improving battery life on long gaming sessions.

The largest battery factor is the MicroSD card itself. Higher-performance cards (A2 class) consume slightly more power than slower cards, but the difference is minimal compared to the display and CPU.

---

### Will game loading times improve or get worse?

It depends entirely on your MicroSD card speed:
- **A2 U3 card + I/O Booster enabled** → loading times comparable to internal storage, sometimes faster due to larger read-ahead buffer.
- **Slow card (U1 or unrated)** → noticeably slower loading times. Not recommended.

---

## Root & Compatibility

### Which root frameworks are supported?

| Framework | Status |
|---|---|
| Magisk (v24+) | ✅ Fully supported |
| KernelSU | ✅ Fully supported |
| APatch | ✅ Fully supported |

Root access is handled via `libsu`, which is compatible with all three frameworks.

---

### How do I update MountX?

1. Download the new APK and module `.zip` from [GitHub Releases](https://github.com/Zy0x/MountX/releases/latest).
2. Flash the new module `.zip` via your root manager (same process as the initial install). This updates the boot service.
3. Install the new APK over the existing installation (no need to uninstall first).
4. Reboot.

Your app configuration, mount states, and settings are preserved across updates.

---

### Can MountX be used alongside other storage management apps?

Generally yes, but avoid running two apps that both manage bind-mounts for the same game simultaneously. Conflicting mount namespaces can cause unexpected behavior. If you previously used another tool (e.g., App2SD, Link2SD), remove those mounts before setting up MountX for the same app.

---

*For detailed configuration options, see [USAGE.md](USAGE.md). For installation help, see [INSTALL.md](INSTALL.md).*
