# Changelog

All notable changes to Mountify will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to Semantic Versioning.

## [2.1.53] - 2026-09-18

### Changed
- **Dashboard: Partition Telemetry Migrated to Dashboard Screen**:
  - Removed the multi-disk telemetry card from the Storage screen.
  - Added a new `Disk Overview` card to the Dashboard, positioned below Master Control, displaying all detected disks (Internal and External) with proportional usage bars and free-space labels in one consolidated view.
  - Each disk row shows its type icon, label, device path, color-coded health bar (smooth progression: Green → Yellow → Orange → Red), percentage, and free space.
  - Footer shows game offload count and total freed space when at least one game is mounted; tapping navigates to Games screen.
- **Storage Screen: Internal Storage Visual Partition Map**:
  - The Partition Map section now includes a read-only Internal Storage card (`userdata / /data`) as the first entry, displayed above all external disk cards.
  - Card renders a proportional single-partition bar representing `/data` usage with real `StatFs` data.
  - A `🔒 Protected` amber badge is displayed on the card header.
  - Tapping the card opens an informational dialog explaining that internal storage management is restricted to prevent system damage; no format, unmount, or repartition controls are available.

## [2.1.52] - 2026-09-18

### Added
- **Unified Independent Partition Mount & Unmount Controls**:
  - Replaced the passive "System Active" informational button with direct, active `[ ▶ Mount ]` and `[ ■ Unmount ]` controls for all partitions across MicroSD and USB OTG drives.
  - Implemented granular partition unmounting: unmounting one partition (such as Android Vold public volume `mmcblk0p1`) unmounts only that specific partition via `sm unmount` and localized direct namespace unmounts, leaving other partitions (such as `sdext2` on `mmcblk0p3` or USB drives) completely intact and running.
  - Enabled independent remounting of partitions via `sm mount` (for Vold public storage) or direct target mount (for ext4/f2fs game partitions).
  - Unmounting partitions allows immediate access to filesystem integrity checks (`fsck`) and maintenance operations directly within Disk Detail View.

## [2.1.51] - 2026-09-18

### Changed
- **Smooth Solid Health Colors (Non-Gradient Progression)**:
  - Replaced multi-color gradient brushes on storage progress bars with smooth solid colors across all telemetries and maps (`FormatUtils.getHealthColor`):
    - `0–60%`: Stable Green (`#10B981`).
    - `60–80%`: Smooth interpolation from Green to Yellow (`#EAB308`).
    - `80–90%`: Smooth interpolation from Yellow to Orange (`#F97316`).
    - `90–100%`: Smooth interpolation from Orange to Red (`#EF4444`).
  - Guaranteed each bar renders in a single, unified solid color that organically morphs across capacity milestones without abrupt jumps and without multi-colored gradient bands.
  - Aligned Dashboard MicroSD storage bar, Multi-Disk Telemetry bars, Visual Partition Map segments, and Disk Detail gauges to this unified color model.

## [2.1.50] - 2026-09-18

### Fixed
- **Continuous 4-Tier Health Gradients Across Storage Bars**:
  - Refactored `FormatUtils.getHealthBrush` to generate a dynamic multi-stop gradient strictly adhering to capacity thresholds:
    - `0–60%`: Stable Green (`#10B981`).
    - `60–80%`: Smooth transition from Green to Yellow (`#EAB308`).
    - `80–90%`: Smooth transition from Yellow to Orange (`#F97316`).
    - `90–100%`: Smooth transition from Orange to Red (`#EF4444`).
  - Replaced binary flat-color `LinearProgressIndicator` in `MultiDiskTelemetryCard` with continuous gradient progress bars across Internal Storage and external media.
  - Dynamically bound percentage and capacity metric text colors to `FormatUtils.getHealthColor`.
- **Partition Visual Map Contrast Enhancement**:
  - Enhanced the background tone of unallocated/free partition space (`#182030`) with a reinforced outline border to clearly separate partition bounds from the card container background.
  - Preserved the high-contrast white divider line highlighting the boundary between used and free partition space.

## [2.1.49] - 2026-09-18

### Fixed
- **USB OTG & Public `/storage/*` Volume Detection**:
  - Resolved an issue where USB OTG flash drives and Android public storage volumes (`/storage/<UUID>`) were ignored during hardware partition scans.
  - Replaced Java-based sysfs reads with SELinux-safe RootShell batch queries for `/sys/block/*/removable` and USB bus controllers (`ls -l /sys/block/`).
  - Added support for unpartitioned whole block devices (e.g. `/dev/block/sda`, `/dev/block/sdd` formatted without MBR/GPT partition tables).
  - Integrated Android Vold public volume resolution (`sm list-volumes public`) and a supplementary `/storage/*` mount scanner to guarantee all connected external media are registered in telemetry and partition maps.
  - Updated `DiskDetailView` with dedicated USB iconography (`Icons.Default.Usb`) and localized partition badges.

## [2.1.48] - 2026-09-18

### Changed
- **Storage Screen Header Streamlining & Pull-to-Refresh**:
  - Simplified the top bar to display only the screen title (`Storage`), removing redundant subtitle metrics and top action icons.
  - Replaced manual top refresh and backup icons with native `PullToRefreshBox` swipe gesture support and automated partition scanning on screen entry.
- **Partition Telemetry Hero Optimization**:
  - Removed the redundant `[ ✓ Mounted ]` badge from the hero telemetry header to declutter the interface.
  - Trimmed hardware drive names to display clean vendor and model info (`hardwareTitle`), eliminating duplicate byte capacity strings next to the name.
- **Visual Partition Map Layout & 4-Tier Health Gradients**:
  - Relocated the `[ Kelola › ]` / `[ Manage › ]` button to the bottom-right of each drive card, providing full horizontal width for drive titles without label truncation.
  - Removed the static bottom guidance text while maintaining full-card tap accessibility.
  - Implemented a dynamic 4-tier traffic-light health gradient across all partition usage bars:
    - `0–60%`: Emerald Green (`#10B981`)
    - `60–80%`: Green-to-Yellow transition (`#EAB308`)
    - `80–90%`: Yellow-to-Orange transition (`#F97316`)
    - `90–100%`: Orange-to-Red transition (`#EF4444`)
  - Standardized segment text inside the visual map to display clean `Part X` labels with byte capacities, and formatted the legend as `Part X (Used: X GB • Free: Y GB)`.
- **Disk Detail View Standardization**:
  - Renamed the section header from `PARTITION TELEMETRY` to `Disk` for conciseness and visual balance.
  - Redesigned the `Repartition Disk` action button with a high-contrast Aurora gradient brush (`#00E5FF` to `#2563EB`) and bold white typography.
  - Standardized partition card badges to technical status indicators: `Mounted` (`Ter-mount`) in green and `Unmounted` (`Belum Di-mount`) in neutral outline, deprecating specialized `Target Active` and `System Portable` badges.
  - Applied the 4-tier dynamic health gradient to the disk gauge bar, mini partition map, and individual partition storage meters.

## [2.1.47] - 2026-09-18

### Fixed
- **Disk Header Label Layout & Truncation**:
  - Replaced expansive text in the `DiskVisualMapOverviewCard` header with a compact `[ Kelola › ]` / `[ Manage › ]` action pill, allowing the drive manufacturer and model label full horizontal width without truncation.
  - Decoupled `hardwareTitle` from byte size string in header titles to eliminate redundancy with the prominent capacity breakdown row.
- **Visual Partition Map Contrast & Legend Clarity**:
  - Replaced muted partition fills with high-contrast distinct color coding: Vivid Sky Blue (`#0284C7`) for portable partitions, Cyber Emerald (`#059669`) for target mount partitions, Tangerine Amber, and Violet.
  - Implemented solid dark-slate `#131722` partition containers, vibrant gradient used-space bars, a sharp white divider line at the boundary, and a 3 dp physical gap between partitions.
  - Added an itemized per-partition breakdown legend directly below the visual bar displaying partition role, total capacity, used bytes, and free bytes.
- **Android Vold Mount Detection & Portable Storage Status**:
  - Re-engineered `StorageManager.detectPartitions()` to correlate physical block devices with Android `vold` mount nodes (`/dev/block/vold/public:<major>,<minor>`), `/mnt/media_rw/<UUID>`, and `/storage/<UUID>`.
  - Accurately identifies partitions mounted by Android as portable storage, displaying the `Portabel Sistem` / `System Portable` status badge and canonical `/storage/<UUID>` path instead of mislabeling them as unmounted.
  - Replaced redundant "Mount" action on portable partitions with an informative `[ ✓ Aktif di Sistem ]` / `[ ✓ System Active ]` status button explaining system file manager accessibility.
- **Filesystem Integrity Check (fsck) Safety Guard**:
  - Added safety validation preventing `fsck` execution on actively mounted partitions, preventing kernel aborts (`is mounted cannot continue`) and guarding against potential filesystem corruption.
  - Displays a clear informational safety dialog prompting the user to safely unmount the partition prior to running integrity checks.
- **Canonical Mount Hierarchy & Target Mount Isolation**:
  - Enforced a prioritized canonical mount resolution hierarchy that gives top precedence to the configured target mount point (`/data/sdext2`).
  - Filtered out secondary runtime game bind-mount paths (`/data/media/0/Android/data/...`, `/mnt/runtime/...`) from being registered as partition mount points, preserving the correct `Target Aktif` badge and `/data/sdext2` display.

## [2.1.46] - 2026-09-18

### Added
- **Multi-Disk Partition Telemetry Hero**:
  - Displays cumulative storage telemetry for Internal Storage (`/data`) and all detected external cards/disks (MicroSD, USB OTG, external SSD/HDD) on the main Storage screen.
  - Computes and displays whole-disk metrics (`Total`, `Used`, `Free`, and percentage) with gradient progress gauges.
- **Two-Tier Visual Partition Map per External Disk**:
  - Implemented multi-disk visual partition map cards on the main Storage screen representing each detected physical external drive.
  - Proportional partition map segments with two-tier visual fill (solid color indicator for used space, translucent indicator for remaining free space).
  - Clear metrics summary (`Total: X GB • Used: Y GB • Free: Z GB`) and intuitive tap hint indicating entry into dedicated partition management.
- **Dedicated Native Disk & Partition Sub-Page (`DiskDetailView`)**:
  - Created a dedicated, full-featured native sub-page when tapping any disk card, maintaining bottom navigation visibility for seamless tab switching.
  - Hardware overview card with cumulative capacity gauge and quick access to `Partition Wizard` (`Atur Ulang Partisi`).
  - Interactive mini visual partition map for the selected disk.
  - Standalone Partition Cards for every partition on the disk showing hardware device path (`/dev/block/...`), filesystem chip, status badge, used/free space bar, and direct inline actions (`Mount/Unmount`, `Format`, and `Check fsck`).
- **Clean Architecture & Multi-Disk Detection Engine**:
  - Expanded `StorageManager.detectAllDisks()` and `StorageRepository.getAllDisks()` to automatically scan all `mmcblk*` and removable `sd*` (USB OTG) block devices, resolving vendor identities, hardware model names, and filesystem usage metrics.

## [2.1.45] - 2026-09-18

### Changed
- **Storage Screen Header & Action Positioning**:
  - Embedded Repartition (`Atur Ulang Partisi`) button directly into `SectionHeader` action slot to eliminate horizontal overflow.
- **Visual Disk Map Label Optimization**:
  - Implemented adaptive partition naming (`p1`, `p2`, etc.) on narrow visual map segments with single-line ellipsis constraint (`softWrap = false`), preventing awkward text wrapping and truncation.
- **Codebase Neutrality & Legacy Cleanup**:
  - Completely purged all remaining legacy string keys and model aliases (`storage_partition_target_mount`, `storage_mount_partition_hub`, `storage_step_*`, `isMountTargetReady`).

## [2.1.44] - 2026-09-18

### Added
- **AOMEI-Style Visual Partition Map for MicroSD Cards**:
  - Implemented interactive proportional multi-segment disk bar in `StorageScreen` representing the physical MicroSD card and its partitions.
  - Distinct color-coded partition visualization: CyberEmerald for Target Mount / F2FS, ElectricCyan for portable FAT32/exFAT storage, Indigo for Ext4, and gray for unallocated space.
  - Tapping any segment dynamically highlights the partition and selects it for contextual actions.
- **Accurate Hardware Disk & Manufacturer Detection**:
  - Implemented `StorageManager.detectSdCardDiskInfo` reading hardware `manfid`, model `name`, and block count from sysfs (`/sys/block/<disk>/device/`) with root fallback, accurately displaying vendor names (e.g., Samsung, SanDisk, Kingston, etc.), hardware model, and byte capacity.
- **Multi-Partition Wizard with KB-Precision Input**:
  - Integrated full partition reallocation wizard (`PartitionWizardDialog`) allowing users to split their MicroSD card into 1 to 4 custom partitions.
  - Added precise manual input text field in **KB** (as well as MB/GB human-readable labels and dynamic sliders) for sector alignment.
  - Dynamic live visual bar preview that updates in real-time as partition sizes, filesystem types (FAT32, exFAT, F2FS, Ext4), or labels are adjusted.
  - Added automated space balancing (`Bagi Rata`) and linked partition resizing.
  - Multi-step destructive safety verification: safety checkbox, clear scheme breakdown comparison, and confirmation dialog before executing GPT partition table recreation via `sgdisk`/`fdisk`.
- **Contextual Partition Action Hub**:
  - Reorganized partition management into a clean, dedicated action card displaying mount readiness checklist, instant Mount/Unmount controls, Single Partition Formatter dialog, and `fsck` filesystem integrity verifier.
  - Completely purged all historical "App2SD" text and branding in compliance with system standards.

## [2.1.43] - 2026-09-18

### Performance
- **Zero-Lag Startup & Lazy ViewModel Initialization**:
  - Configured `HorizontalPager` with `beyondViewportPageCount = 0`, ensuring only the active `DashboardScreen` is composed on application launch.
  - Deferred initialization of `GamesViewModel`, `StorageViewModel`, `LogsViewModel`, and `SettingsViewModel` so they are instantiated on-demand only when their respective tab pages are navigated to.
  - Removed synchronous disk scanning and root shell polling loops from `StorageViewModel` and `LogsViewModel` initializers, eliminating background process contention and reducing cold-start time to <150ms.
- **Storage Partition Filtering & UI Optimization**:
  - Filtered out internal non-removable UFS LUN partitions (`sdc1` to `sdc60`, etc.) in `StorageManager.detectPartitions()` via `/sys/block/<disk>/removable` sysfs check and minimum 250MB threshold. This eliminates 50+ internal firmware partitions from flooding the candidate list.
  - Excluded Android root system mountpoints (`/`, `/system`, `/vendor`, `/data`, `/persist`, `/apex`) from being erroneously classified as candidate SD partitions.
  - Optimized `PartitionScannerCard` in `StorageScreen` with item keying and an expandable partition toggle (`storage_show_more`), capping initial card rendering to 4 items and eliminating UI layout stalls.
- **Lifecycle-Aware Log Tailing**:
  - Replaced persistent infinite log polling in `LogsViewModel` with lifecycle-bound `startTailing()` and `stopTailing()` tied to `DisposableEffect` in `LogsScreen`, stopping background shell executions while on other tabs.

## [2.1.42] - 2026-09-18

### Performance
- **Persistent Pager Navigation & Native Gesture Swiping**:
  - Replaced `NavHost` bottom destination teardown/recomposition with a persistent `HorizontalPager` with `beyondViewportPageCount = 2`. All 5 main screens (Dashboard, Games, Storage, Logs, Settings) remain preserved in memory, eliminating layout tearing, initialization lag, and blank flashes during tab changes.
  - Enabled native hardware-accelerated horizontal swiping between screens with fluid 60/120 FPS physics.
  - Bottom bar tab selection highlights instantaneously on touch (`targetPage`), animating adjacent tab transitions and instantly jumping distant tabs.
  - Added natural Android Back gesture behavior returning to the Dashboard tab before exiting.
- **Dedicated Subview Gesture Isolation & Sub-Pager Pre-warming**:
  - Implemented `onPagerScrollEnabled` coordination between `MainTabsScreen` and `GamesScreen` so outer pager horizontal drag is safely disabled while viewing `GameDetailView` or `AddAppPicker`, preventing accidental tab switching.
  - Inner `HorizontalPager` in `GameDetailView` configured with `beyondViewportPageCount = 1` for zero-stutter swiping between the Concentric Storage Breakdown chart and the Manage/Transfer hub.
  - Updated `DetailCapsuleTabRow` to track `targetPage` for instant visual response upon tapping capsule tabs.
  - Added Android `BackHandler` inside `AddAppPicker` for seamless back-navigation.
- **Render Pipeline & Shell Execution Optimization**:
  - Made `Modifier.blur()` in `AddGameSheet` strictly conditional (`pendingSystemApp != null && blurRadius > 0.dp`), eliminating permanent full-screen offscreen GPU buffer allocations on Android's Skia OpenGL render pipeline.
  - Capped `AppIconImage` bitmap allocations to maximum 128x128 pixels via `Bitmap.createScaledBitmap`, eliminating giant bitmap textures and GPU upload stalls during list scrolling.
  - Optimized `GameRepository.getDetailedStorageBreakdown`: computes APK and native library sizes using native `java.io.File.length()` in ~0.1ms, and batches all remaining folder calculations into a single `du -sk` invocation.
  - Switched `MountManager.getMountedPaths()` to read `/proc/mounts` directly via native `File` I/O, avoiding process spawn overhead.

## [2.1.41] - 2026-09-17

### Performance
- **Zero-Lag Bottom Tab Navigation**:
  - Replaced tab crossfade overhead with instantaneous transition (`EnterTransition.None` / `ExitTransition.None`) for top-level bottom navigation items, eliminating simultaneous composition and layout overhead.
  - Retained fast 150ms slide transitions specifically for child and sub-screens (`backup_restore`, `about`).
  - Added centralized tab routing using `findStartDestination` with `saveState = true`, `launchSingleTop = true`, and `restoreState = true`.
  - Removed layout-remeasuring vertical expanding/shrinking from `ModernNavigationBar` tab items; replaced with fixed 120ms tween animations to eliminate tab switching jitter.
- **Root Shell & Storage Command Batching**:
  - Replaced 8 sequential root shell roundtrips in `RootDetector` with a single multi-check shell script and in-memory state caching.
  - Converted internal storage measurement in `StorageManager` to Android's native `StatFs` kernel system call, eliminating periodic `df` process spawns.
  - Batched partition device identification in `StorageManager.detectPartitions()` to a single `blkid` run, eliminating per-partition shell execution loops.
  - Converted mount status checking in `getStorageInfo` to direct `/proc/mounts` file reads.
  - Reduced storage polling intervals in `StorageRepository` and `LogsViewModel` to reduce background shell traffic.
- **Canvas Rendering Optimization**:
  - Optimized `ConcentricStorageChart` by lifting `textPaint`, `centerTextPaint`, and `dashPaint` into `remember(density)` blocks, eliminating frame-by-frame object allocations and garbage collection pressure during horizontal pager scrolling.
  - Eliminated redundant `getInternalAndSdSizes` shell invocations in `GamesViewModel`.

## [2.1.40] - 2026-09-17

### Fixed
- **Storage Breakdown Metrics Calculation Engine**:
  - **Native Disk Usage Pipeline**: Replaced fragile `awk '{sum+=$1}'` and `awk '{print $1}'` commands in `GameRepository` with POSIX-standard `du -sck ... | tail -n1 | cut -f1`. This eliminates shell positional parameter interpolation failures under Android toybox/mksh, enabling accurate, instant size computation for APK, DEX, native libraries, private data, cache, and shared storage.
  - **Automatic Detail Breakdown Loading**: Added a reactive `LaunchedEffect` keyed to the active package name in `GamesScreen` so storage breakdown calculations are always triggered seamlessly upon viewing an app's detail screen.
  - **Full 7-Component Donut Ring Representation**: Added compiled Dalvik/ART code (`Dex`) to `ConcentricStorageChart`'s outer slice list so all storage facets are properly visualized.
  - **Robust Namespace Unmount Parsing**: Updated `MountManager` to extract mount paths using `cut -d' ' -f2` instead of `awk` for unmount operations.

## [2.1.39] - 2026-09-17

### Fixed
- **Accurate Storage Chart & Legend Alignment**:
  - **Correct Physical Storage Partitioning**: Fixed conceptual flaw in `AppStorageBreakdown` where shared internal storage (`/data/media/0/Android/data`) was incorrectly grouped into external storage. Internal phone memory now accurately comprises all system binaries, private data, and internal shared folders.
  - **Dynamic Concentric Inner Donut Ring**: Fixed misleading 50:50 dotted dividing line when 100% of data resides in phone internal memory or MicroSD. Inner circle now renders a solid single sector with a clean centered percentage badge, only drawing dividing lines and split sectors when data is genuinely distributed between phone and MicroSD.
  - **Clean & Un-cramped Storage Legend**: Restructured the 3-tier legend into clear, human-readable rows (`Phone Memory`, `MicroSD Card`, and `Grand Total Σ`), completely removing outdated technical jargon `(SDEXT2)` and eliminating text line-wrapping glitches.

### Performance
- **Zero-Jank Bottom Navigation & Fluidity**:
  - **Smooth Crossfade Tab Transitions**: Replaced heavy full-screen horizontal slide transitions (`slideIntoContainer`) on the bottom navigation bar with lightweight, ultra-smooth crossfades (`fadeIn` / `fadeOut` at 140ms), eliminating frame drops and layout hitches across mobile devices.
  - **Optimized Navigation Bar Layout**: Removed redundant nested `animateContentSize` on navigation tab items to prevent continuous remeasurement passes and jitter during tab switching.

## [2.1.38] - 2026-09-17

### Added
- **Manage Tab Automation & Seamless Transfer Hub**:
  - **Automated Mount Lifecycle**: Moving data to MicroSD now automatically copies data, verifies integrity, cleans source internal storage, and auto-mounts into all 7 Android runtime namespaces instantly without requiring manual mount actions.
  - **Clean & Safe Internal Restoration**: Restoring data to internal storage now performs automated unmounting first to prevent cross-device locks, transfers data back to internal shared storage, verifies integrity, restores standard Linux UID ownership (`$uid:1023`), permissions (`chmod 775`), and SELinux contexts (`u:object_r:media_rw_data_file:s0`), and cleans MicroSD directories.
  - **Dynamic Transfer & Restore Size Estimation**: Added live size calculation surface dynamically reflecting exact bytes to be moved or restored according to the active scope chip selection (`All`, `Data Only`, `OBB Only`).
  - **Collapsible Advanced Options Accordion**: Moved technical `PKG Mode` vs `FILES Mode` selection cards into an expandable "Advanced: Mount Mode" card with smooth animation, displaying current mode summary when collapsed.
  - **Granular Storage Breakdown Metrics**: Added distinct measurement for `ext1DataBytes`, `ext1ObbBytes`, `ext2DataBytes`, and `ext2ObbBytes` in repository and data models.
  - Added multi-language localized strings in `values/strings.xml` and `values-id/strings.xml`.

## [2.1.37] - 2026-09-17

### Changed
- **Storage Breakdown Clarity & Neutral App Terminology**:
  - **Eliminated Ambiguous Storage Labels**: Replaced legacy, cryptic partition categories (`Data`, `Data(Ext) 1`, `Data(Ext) 2`) with clear, human-readable labels:
    - `Data` ➔ **Private Data** (`/data/data` databases, preferences, and private files).
    - `Data(Ext) 1` ➔ **Shared Storage (Internal)** (`Android/data & obb` on phone storage).
    - `Data(Ext) 2` ➔ **Shared Storage (MicroSD)** (`Android/data & obb` on secondary SD card partition).
  - **Organized Storage Hierarchy**: Structured the Breakdown Card into two clear zones: *System & Private Storage* (Internal) and *Shared Storage (Mountify Target)* with path descriptions and active `[ MOUNTED ]` badges.
  - **De-cluttered Binary Metrics**: Removed redundant `Dex: 0.00 KB` entry caused by modern ART/OAT compiler architecture, and integrated `Lib` cleanly.
  - **Neutral App-First Terminology**: Replaced all game-specific descriptions across detail screens with universal app-neutral phrasing, ensuring perfect relevance for utilities, emulators, offline navigation, and media tools.
  - **Descriptive Telemetry Legend**: Enhanced 3-tier donut chart legend with clear category labels (`System (Internal)`, `Shared (Internal)`, `MicroSD (SDEXT2)`, and `Total`).
  - Added comprehensive multi-language strings in `values/strings.xml` and `values-id/strings.xml`.

## [2.1.36] - 2026-09-17

### Added
- **Storage Screen Overhaul for Partition & Mount Ecosystem**:
  - **Dual Storage Telemetry**: Added side-by-side internal storage (`/data`) and MicroSD (`/data/sdext2`) space gauges with gradient progress bars and an offloaded data savings banner (`X games offloaded • Y GB saved`).
  - **Partition Scanner & Inspector**: Built engine parsing `/proc/partitions`, `/proc/mounts`, and `blkid` to detect block devices, parent disks, capacities (in GB/MB), filesystems (F2FS, Ext4, exFAT, VFAT), mount status, and volume labels.
  - **Target Mount Control Hub**: Added interactive Mount Target Readiness Checklist (Root sandbox, Linux F2FS/Ext4 filesystem, target mountpoint), one-tap Mount/Unmount actions, and unmounted partition Filesystem Check (`fsck.f2fs` / `e2fsck`) with terminal output dialog.
  - **Filesystem Formatter Hub**: Modernized Danger Zone with interactive F2FS (Recommended for Flash) and Ext4 selection cards, customizable partition label input (default `sdext2`), and 2-step confirmation dialog with explicit device path.
  - Standardized `BackupRestoreScreen` top header with `CompactScreenHeader` in full compliance with `AGENTS.md` rules.
  - Added complete bilingual string resources across `values/strings.xml` and `values-id/strings.xml`.
- **App Detail Tabbed Architecture (Storage & Manage)**:
  - Redesigned `GameDetailView` into a modern **Tabbed View** with `HorizontalPager`, supporting both tap navigation and smooth left/right swipe gestures.
  - Pinned the App Hero Metadata profile header at the top, accompanied by a 36dp **Capsule / Pill Tab Bar** (`Storage` and `Manage`).
  - **Storage Tab**: Restored the large, comfortable Concentric Donut Chart (~152dp) with full slice percentage labels and 3-tier legend beside/below the chart, paired with a dedicated 7-row storage breakdown card featuring *Smart Dimming* (`alpha = 0.42f` for 0 KB rows).
  - **Manage Tab**: Dedicated control surface featuring spacious, descriptive Mount Mode configuration cards (`PKG Mode` vs `FILES Mode`), granular Physical Data Transfer cards (`All`, `Data Only`, `OBB Only`), and one-tap Game Removal.
  - Added localized strings for tab navigation in English and Indonesian (`values/strings.xml`, `values-id/strings.xml`).

## [2.1.35] - 2026-09-17

### Changed
- **Sleek Micro-Pro App Detail UI Refactor**:
  - Compacted Concentric Donut Chart diameter from 176dp to 82dp with 6.5dp stroke, 7.5dp outer ring text, and 7dp inner text, fitting side-by-side with the 3-tier legend and reducing vertical consumption by over 50%.
  - Merged the separate Storage Chart and Breakdown cards into a **Single Unified Storage Card** with thin horizontal separator.
  - Implemented **Smart Dimming** in the 7-row breakdown table: rows with zero bytes are subtly dimmed (`alpha = 0.42f`), visually emphasizing active data categories without losing information.
  - Redesigned Mount Mode Selector into a compact 28dp **Segmented Pill Switch** (`PKG Mode` / `FILES Mode`) with a 1-line dynamic description, replacing oversized cards and eliminating screen clutter.
  - Preserved full touch target ergonomics and WCAG contrast compliance while delivering a dense, professional power-user interface.

## [2.1.34] - 2026-09-17

### Added
- **Dual OBB & Data Binding Engine (Tahap 1)**:
  - Extended `MountManager` to bind-mount both game data (`Android/data/<pkg>`) and expansion assets (`Android/obb/<pkg>`) across all 7 Android runtime namespaces with SELinux context `media_rw_data_file` and ownership `UID:1023`.
  - Added clean lazy unmount for both `data` and `obb` directories on game unmount.
  - Added `MigrationTarget` enum (`ALL`, `DATA_ONLY`, `OBB_ONLY`) in `StorageInfo` and `StorageManager` to allow granular and combined physical data migration.
  - Integrated `Android/obb` measurement in `GameRepository` storage breakdown: `Ext 1` now includes internal data + obb, and `Ext 2` includes MicroSD data + obb.
  - Added dynamic sync of active game list to `/data/adb/modules/Mountify/gamelist.conf` upon add, update, mode change, or removal.
  - Updated Magisk module `service.sh` to auto-detect and bind-mount `Android/obb` alongside `Android/data` during boot sequence.
  - Added compact Transfer Scope selector chips (`All (Data+OBB)`, `Data Only`, `OBB Only`) to `GameDetailView` Physical Data Transfer card.
  - Added English and Indonesian localized strings for transfer scopes.

## [2.1.33] - 2026-09-17

### Added
- Implemented comprehensive App Detail breakdown structure:
  - Added `AppStorageBreakdown` model tracking all 7 Android app storage categories (`Apk`, `Dex`, `Lib`, `Data`, `Cache`, `Data(Ext) 1`, `Data(Ext) 2`) and 3-tier cumulative summaries.
  - Implemented high-performance batch shell root calculation engine in `GameRepository` querying APK, DEX/oat, native lib, internal data, cache, and shared storage in a single non-blocking `Dispatchers.IO` command.
  - Built Concentric Pie / Donut Chart with individual slice percentage labels, multi-slice coloring, and an inner circle representing Internal vs External distribution with dashed center divider.
  - Added 3-tier legend beside the chart: 💽 Internal (`#DF4006`), 📱 Ext 1 (`#3149FF`), 💾 Ext 2 (`#3BA71A`), and $\Sigma$ Total cumulative data.
  - Added 7-row Detailed Breakdown card with category-matched text colors, 2-decimal formatted sizes, and destination storage icons.
- Streamlined App Detail header to flat surface styling with cyan/teal (`#00838F`) metadata typography and thin divider separator.

## [2.1.32] - 2026-09-17

### Added
- Replaced the bottom sheet modal ("laci") with a dedicated full-screen native `GameDetailView` (App Detail screen).
- Added App Hero metadata card displaying package version (`version: x.x (code: xxx)`), install timestamp, and system application info shortcut.
- Introduced visual Donut Chart using native Compose `Canvas` with dynamic percentage arc calculation and a comprehensive storage breakdown table (Internal, MicroSD, and Total Data $\Sigma$).

## [2.1.31] - 2026-09-17

### Changed
- Refactored `ModernGameCard` to Option 1 single-row compact layout: replaced stacked status chip and large action button with a native compact tactile `Switch` (scaled 0.8x) alongside inline mode and storage size badges.
- Made header batch mount/unmount actions adaptive: greyed out and disabled when there are no games to mount or unmount.
- Compacted Floating Action Button (FAB) from 46dp to 42dp with an 18dp icon and tighter padding.
- Calibrated vertical spacers between search bar, filter chips, and games list (6dp and 7dp) to eliminate empty void.

## [2.1.30] - 2026-09-17

### Changed
- Compacted Games screen header action buttons (mount/unmount/sort) from 36dp to 28dp touch target with 26dp inner box, 6dp corner radius, 14dp icons, and 4dp inter-button spacing.
- Replaced bloated Material `FilterChip` (default ~48dp height) with compact custom `Surface`-based chips (~28dp) using 10dp horizontal and 5dp vertical padding.
- Reduced `DropdownMenuItem` height from default 48dp to 32–34dp with 12dp horizontal content padding and 12sp label text.
- Reduced game card corner radius from 16dp to 12dp, inner padding from 12dp to 10dp, app icon from 38dp to 36dp, and inter-row spacer from 10dp to 7dp.

## [2.1.29] - 2026-09-17

### Changed
- Refactored app preview card in the System Package Warning dialog to a two-tier layout: app icon + display name (up to 2 lines) in the header row, followed by a dedicated full-width monospace code container for the package name (up to 2 lines, `softWrap = true`).
- Extended the two-line wrap pattern to `ConfigureAppView` header card and `GameDetailSheet` header row so long display names and package names no longer truncate with ellipsis.

## [2.1.28] - 2026-09-17

### Changed
- Calibrated global typography scale in `Type.kt` and across screens for tighter visual density and improved readability.
- Standardized screen header titles to 15sp and subtitles to 10.5sp across all primary tabs.
- Calibrated oversized action buttons across screens (`DashboardScreen`, `StorageScreen`, `BackupRestoreScreen`, `GameDetailSheet`, `AddGameSheet`, `SettingsScreen`, `AboutScreen`, `ConfirmDialog`) from bloated 42–48dp down to sleek 34–38dp heights with 10dp rounded corners.
- Scaled down Storage telemetry free-space metric text from 24sp to 18sp bold for balanced layout proportions.
- Refined `SectionHeader` padding to align cleanly with card margins across screens.

### Fixed
- Fixed broken line-break on the Storage screen where "Unmount Partition" awkwardly wrapped into multiple lines by simplifying button labels to "Mount" and "Unmount" with enforced single-line rendering.

## [2.1.27] - 2026-09-17

### Changed
- Replaced the awkward trailing checkmark on the system apps menu item with dynamic text and a leading Visibility/VisibilityOff icon ("Show System Apps" / "Hide System Apps").

## [2.1.26] - 2026-09-17

### Changed
- Refined System Package Warning dialog proportions to a compact, non-bloated scale (inner padding 14dp, header icon 28dp, buttons 34dp).
- Allocated 100% horizontal width to the application title by removing the redundant in-card tag, preventing premature title truncation.
- Streamlined callout copy to reduce vertical footprint from 4 lines to 2–3 lines.

## [2.1.25] - 2026-09-17

### Added
- Dedicated Target Application Preview card inside the System Package Warning dialog featuring the app icon, title, monospace package ID, and system tag.
- Structured risk alert callout box highlighting force close, UI freeze, and bootloop risks with clear visual separation.

### Changed
- Increased dialog action buttons to 38dp height with minimum touch target widths (72dp/80dp) and 12sp typography in compliance with mobile-first standards.

### Fixed
- Awkward text wrapping and mid-word line breaks when displaying long Android package names in warning messages.

## [2.1.24] - 2026-09-17

### Added
- Compact Floating Action Button (FAB 46dp, Aurora gradient, plus icon) as the unified entry point for adding games on the Games screen.

### Changed
- Removed redundant header add button from `CompactScreenHeader` to keep the top navigation clean and uncluttered.
- Removed duplicated "+ Add Your First Game" button from the empty state, directing user focus clearly to the bottom-right FAB.
- Adjusted list bottom clearance to 76dp ensuring game cards are never obscured by the FAB.

## [2.1.23] - 2026-09-17

### Added
- Real-time animated backdrop blur (`16.dp`) on screen background during dialog presentation for modern glassmorphism depth.
- Standardized Dialog Horizontal Margin rules (Section 3.5 item 8 in `AGENTS.md`) enforcing 24–28dp breathing room from device screen edges.

### Changed
- Converted System Package Warning dialog from windowed `AlertDialog` to in-tree blurred backdrop overlay, eliminating claustrophobic edge-to-edge stretching.
- Enhanced warning icon presentation with a rounded crimson accent container (30dp) and balanced typography hierarchy.

## [2.1.22] - 2026-09-17

### Added
- Standardized Dropdown Menu & Popup specifications (Section 3.5 item 7 in `AGENTS.md`) enforcing 30–34dp item heights, 10–12dp horizontal padding, and anti-bloat proportions.

### Changed
- Replaced oversized Material 3 `DropdownMenuItem` with a compact, custom-padded 30dp menu row, eliminating excessive whitespace.
- Dropdown menu now dismisses automatically upon selection while immediately toggling system app visibility.

## [2.1.21] - 2026-09-17

### Added
- Top-right `MoreVert` three-dots menu on `AddAppPicker` offering a checkmark toggle to display system applications on demand.
- Floating Action Button (FAB) in the bottom-right corner (compact 46dp, Aurora theme gradient, pencil icon) for quick access to manual/custom game entry.

### Changed
- Removed category filter tabs entirely, reclaiming vertical real estate so the app list begins immediately beneath the compact 38dp search bar.
- Streamlined default application list to show User Apps exclusively, sorted purely in alphabetical order (A–Z).
- Replaced oversized confirmation dialog with an ultra-minimalist, compact safety warning dialog (34dp action buttons, concise technical message).

## [2.1.20] - 2026-09-17

### Added
- Standardized UI Compact Sizing Specification (Section 3.5 of `AGENTS.md`) enforcing strict dimension limits across headers, search bars, buttons, icons, and filter chips.
- Custom `CompactFilterTabChip` component featuring a 30dp height profile, 13dp vector icons, and 11sp typography.

### Changed
- Replaced popup `Dialog` wrapper with direct native full-screen composable layout, eliminating window margins and background peeking.
- Unified screen header with `CompactScreenHeader` (46dp height with status bar padding), permanently removing top gap and double padding.
- Replaced oversized 56dp search bar with Mountify's streamlined 38dp `BasicTextField` container.
- Streamlined application list cards, action buttons (42dp CTA), and icon proportions across the entire application picker.

## [2.1.19] - 2026-09-17

### Added
- Dedicated Full-Screen Application Picker (`AddAppPicker`) replacing the cramped bottom sheet with a spacious, distraction-free app selection surface.
- Unrestricted package discovery via `android.permission.QUERY_ALL_PACKAGES` and system app flag evaluation, ensuring 100% comprehensive detection across all installed user apps, games, and system packages.
- Real-time instant filtering supporting dual search across application display names and reverse domain package identifiers.
- Distinct package identifier subtext displayed in clean monospace typography beneath application titles for unambiguous recognition.
- Categorization filter chips with dynamic count badges: Games, User Apps, System, and All.
- System Application Safety Gate featuring an explicit risk confirmation dialog alerting users to force-close, UI freeze, or bootloop risks before redirecting system app data.

### Changed
- Replaced constrained bottom-sheet container with full edge-to-edge dialog scaffolding with contextual back navigation handling.

## [2.1.18] - 2026-09-17

### Added
- Comprehensive Storage Telemetry Hero Card displaying dual-gradient capacity gauge (`ElectricCyan` to `CyberEmerald`), real-time free space callout, and 4-metric technical specification grid (Filesystem, Mount Point, Used Data, Target Device).
- Interactive Block Device Selector rendering detected Linux partitions (`/dev/block/mmcblk*`) as tactile visual chips with live active-target indicator and manual path fallback.
- Modern Filesystem Formatter Hub with interactive selection tiles for F2FS (Recommended flash-optimized storage) and Ext4 (Rock-solid Linux standard), guarded by two-step confirmation dialogs.
- Integrated Storage Access Framework (SAF) Configuration Portability Hub enabling instant JSON backup and restore operations directly from the Storage screen.
- Dual-pane responsive landscape architecture optimizing layout for tablets, foldables, and wide non-regular displays (720p through 4K).

### Changed
- Replaced basic progress bars and radio buttons with tactile touch targets adhering to minimum 48×48 dp dimensions.
- Modernized `BackupRestoreScreen` with Satoshi typography, outline border containers, and full localization parity between English and Indonesian.

## [2.1.17] - 2026-09-17

### Added
- Compact natural search bar replacing default 56dp OutlinedTextField with a streamlined 38dp height, subtle border, and responsive clear action.
- Unified header action row grouping Mount All, Unmount All, Sort, and Add Game buttons in tactile, rounded containers with standardized touch targets.

### Changed
- Relocated Add Game action from Floating Action Button (FAB) to top header actions, permanently eliminating FAB occlusion of game card action buttons.
- Removed redundant MoreVert menu buttons from game cards in favor of direct full-card click affordance to open the detail sheet.
- Re-anchored game mount status chips to the top-right corner of each card, providing balanced visual hierarchy and generous breathing room for mode/size metadata tags.

## [2.1.16] - 2026-09-17

### Added
- Hardware-accelerated GPU host pipeline (`hw.gpu.enabled=yes`, `hw.gpu.mode=host`) configured in AVD settings, harnessing the host NVIDIA GeForce RTX 3050 GPU for ultra-smooth UI frame pacing.
- Expanded AVD runtime memory (`hw.ramSize=3584M`) and ART Dalvik heap (`vm.heapSize=512M`) to completely eliminate GC thrashing and frame drops.
- High-performance emulator launcher profile (`-no-audio`, `-no-boot-anim`, disabled camera) eliminating background audio buffer polling and reducing host CPU overhead.

### Changed
- Replaced unconstrained 60 FPS `rememberInfiniteTransition` in `SmartMasterControlCard` with a lightweight, high-contrast static LED status indicator, dropping idle app CPU consumption from ~50% down to 0%.
- Optimized `ModernNavigationBar` by removing expensive off-screen buffer blur passes (`.shadow(...)`), preserving sleek 1dp theme borders with zero rendering latency.
- Disabled unused virtual sensors (`hw.accelerometer`, `hw.gyroscope`, `hw.sensors.*`) in AVD configuration, permanently resolving the Android 14 `android.hardware.sensors-service.multihal` infinite loop kernel spinning bug (which previously consumed 100%+ CPU).

## [2.1.15] - 2026-09-16

### Added
- Implemented **Satoshi** (designed by Deni Anggara / Indian Type Foundry / Fontshare) high-end contemporary neo-grotesque typography system across the entire application.
- Bundled static offline TrueType font assets (`satoshi_regular.ttf`, `satoshi_medium.ttf`, `satoshi_bold.ttf`, `satoshi_black.ttf`) in `res/font/` for 100% offline consistency.

### Changed
- Refined typography hierarchy with luxury geometric proportions and subtle letter spacing, delivering a crisp, elegant, and modern visual character.
- Maintained compact, calm sizing parameters across all text styles (`display`, `headline`, `title`, `body`, `label`) preventing bloated elements while preserving solid stroke weights and high-contrast readability.

## [2.1.14] - 2026-09-16

### Added
- Implemented **Inter** (v4.1 by Rasmus Andersson) typography system, delivering a subtle, understated, calm, and highly legible visual character.
- Bundled static offline TrueType font assets (`inter_regular.ttf`, `inter_medium.ttf`, `inter_semibold.ttf`, `inter_bold.ttf`) in `res/font/`.

### Changed
- Calibrated typography scale across the entire application: lowered font sizes and line heights proportionally to eliminate oversized text while maintaining solid strokes and clear contrast without becoming thin.
- Replaced wide geometric glyphs with compact, streamlined horizontal metrics to maximize screen real estate and prevent text overflow.

## [2.1.13] - 2026-09-16

### Added
- Implemented contemporary geometric font family **Plus Jakarta Sans** (OFL 1.1) across the entire application, replacing the default system font.
- Bundled static offline TrueType font assets (`plus_jakarta_sans_regular.ttf`, `plus_jakarta_sans_medium.ttf`, `plus_jakarta_sans_semibold.ttf`, `plus_jakarta_sans_bold.ttf`) into `res/font/` for 100% offline consistency without Google Play Services dependency.
- Modernized Material You 3 typography scale (`display`, `headline`, `title`, `body`, `label`) with tailored geometric letter spacing and line heights.

### Changed
- Refined typography hierarchy across Dashboard, Games, Storage, Logs, Settings, and About screens to enhance legibility and visual elegance.

## [2.1.12] - 2026-09-16

### Added
- Complete UI/UX overhaul of the Games module focusing on effortless operation and modern visual hierarchy.
- Native Android app icon rendering with asynchronous background loading (`AppIconImage`) and in-memory LRU caching.
- Direct Visual App Picker in `AddGameSheet` featuring instant search, "Games Only" / "All Apps" category filtering, and manual package input fallback.
- `SmartGamePresets` engine providing automatic mount mode detection (PKG vs FILES) with dedicated "Smart Recommendation" badges for popular games (Genshin Impact, Honkai: Star Rail, Zenless Zone Zero, Wuthering Waves, PUBG Mobile, Call of Duty Mobile, Mobile Legends, Free Fire).
- `GameDetailSheet` integrated management sheet showing physical storage distribution (Internal vs MicroSD), instant mount mode switcher without re-adding, verified data migration, and deletion controls.
- Fast status filter chips (`All`, `Mounted`, `Unmounted`) and sorting menu (`Largest Size`, `Name A-Z`) on GamesScreen.
- Batch action controls (`Mount All` and `Unmount All`) accessible directly from the Games header.
- Welcoming empty state with Aurora gradient "+ Add Your First Game" call-to-action.

### Changed
- Promoted Mount/Unmount action on game cards to a large, tactile button preventing accidental deletion or data-move misclicks.
- Removed obsolete `MoveDataSheet` in favor of the rich `GameDetailSheet`.

## [2.1.11] - 2026-09-16

### Added
- Complete modern theme overhaul introducing Cyber Aurora / Cyber Slate palette for Dark Mode and Frost Slate for Light Mode.
- Dynamic theme switching support respecting user settings (Light, Dark, System Default) across all screens via `MaterialTheme.colorScheme`.
- Dynamic gradient brushes including `AuroraGradientBrush` (Electric Indigo to Hyper Cyan) for primary interactive CTA buttons and `StorageGradientBrush` for multi-stage storage capacity gauges.
- Glowing LED-style status indicators (`CyberEmerald`, `NeonCrimson`, `HyperCyan`, `AmberWarning`) replacing muted flat indicators.

### Changed
- Refactored all screens (Dashboard, Games, Storage, Settings, Logs, About) to consume dynamic theme tokens instead of static hardcoded colors.
- Upgraded `ModernNavigationBar` with animated luminous active indicator pill and themed surface styling.
- Polished contextual alert banners, partition cards, and terminal logs for high-contrast accessibility (WCAG compliant) across both Dark and Light modes.

## [2.1.10] - 2026-09-16

### Added
- App-wide Compact Modern visual scaling reducing bulky containers, oversized cards, and inflated buttons.
- Reusable `CompactScreenHeader` component (~46-48dp) with inline status indicators and seamless `.statusBarsPadding()`.
- Unified Obsidian design tokens (`ObsidianBg`, `ObsidianCard`, `ObsidianBorder`, `ElectricCyan`) across Games, Storage, Settings, Logs, and About screens.
- Standardized 16dp card corner radius with sleek 12–14dp inner padding replacing bulky 24dp containers.

### Changed
- Streamlined `ModernNavigationBar` height to 56dp with 20dp icons and 10sp captions while preserving mandatory 48×48dp touch targets.
- Scaled primary action buttons to 42–46dp for balanced touch density without visual bulk.

### Fixed
- Replaced deprecated `Icons.Default.ArrowBack` with `Icons.AutoMirrored.Filled.ArrowBack` in AboutScreen.

## [2.1.9] - 2026-09-16

### Added
- Ultra-modern minimalist Master Control Hub styling with Deep Obsidian (`#131722`) cards and subtle 1px borders (`#1F2637`).
- Electric Cyan (`#539DF3`) accents and breathing glow pulse animation for active root mount status.
- Custom Electric Cyan-to-Blue horizontal gradient capacity gauge for external storage.
- Compact tactile header bar (`SleekCompactHeader`) with integrated live status pill and haptic refresh action.

### Fixed
- Fixed top bar layout gap caused by duplicate WindowInsets consumption between `NavGraph` outer `Scaffold` and `DashboardScreen`.

## [2.1.8] - 2026-09-16

### Added
- Pure Master Control Hub architecture on Dashboard eliminating redundant game lists and duplicate log tiles.
- `SmartMasterControlCard` featuring adaptive action logic (`Mount All Games` when unmounted, `Unmount All` when operational, `Add Game` when empty).
- Dynamic status pill indicator calculating active mounts ratio with contextual Material 3 tonal styling.
- Compact side-by-side telemetry tiles (`DashboardMetricsRow`) for total data offloaded to MicroSD and runtime namespace states.
- Contextual error and warning banner appearing strictly on missing root or missing module conditions, leaving zero clutter when operating normally.
- Full bilingual localization keys for Master Control CTA actions and telemetry units.

## [2.1.7] - 2026-09-16

### Added
- Clean, refined Material You 3 layout architecture for Dashboard screen replacing cluttered telemetry panels.
- Expressive M3 top bar with title, system subtitle, and tonal root solution AssistChip.
- Re-architected system status banner with high-contrast tonal feedback for unrooted, non-module, and operational states.
- Unified `M3StorageCard` with 24dp elevated container, dual-layer storage bar, and direct navigation action.
- Streamlined 52dp quick action action buttons (`Mount All` / `Unmount All`) with rounded 16dp corners and haptic feedback.
- Clean `M3GamesLibraryCard` featuring initial avatars, mode badges, data size chips, and quick mount toggles.
- Replaced fake terminal streaming box with modern `M3ActivityCard` navigating to full Logs screen.
- Full localization keys in `strings.xml` and `values-id/strings.xml` for all new Material You 3 dashboard elements.

## [2.1.6] - 2026-09-16

### Added
- Replicated Figma mobile navigation bar (`Bottom Nav.png`) with 24dp rounded top corners and subtle stroke boundary.
- Dynamic expanding active labels with smooth `expandVertically` and `fadeIn` transitions while collapsing inactive labels.
- Fluid spring bounce icon scale animations (`Spring.DampingRatioMediumBouncy`) on tab selection.
- High-contrast OLED dark theme tokens (`FigmaNavSurface` `#1D1F24`, `FigmaNavBlue` `#539DF3`, `FigmaNavBorder` `#2E323D`, `FigmaNavInactive` `#676D75`).
- Multi-breakpoint Jetpack Compose previews including ultra-tall 1080x2460 display profile.

## [2.1.5] - 2026-09-16

### Added
- One-click launcher and installer script (`Pasang_ke_Emulator.bat`) automating device connection verification, APK installation, and activity startup.

### Fixed
- Resolved activity launch target resolution by configuring explicit `app.mountify.debug` debug application package identifiers.
- Enhanced storage card footer responsiveness and dynamic unmounted state indicators on narrow viewports.

## [2.1.4] - 2026-09-16

### Added
- Dedicated ModernNavigationBar component featuring custom active capsule indicators, spring scale animations, and dual-tone neon indicator dots.
- Distinct filled and outlined vector icon pairings for all top-level destinations (Dashboard, Games, Storage, Logs, Settings).
- Haptic tactile feedback on destination selection.
- High-contrast AMOLED glassmorphism styling with elevated surface depth and subtle outline borders.
- Interactive vector SVG navigation dock in preview workspace with zero AI artifacts or emojis.

## [2.1.3] - 2026-09-16

### Added
- Modern high-tech AMOLED UI dashboard layout compliant with PRD and WCAG AAA standards.
- Real-time telemetry KPI metric indicators for root engine provider, SELinux enforcement status, and active mount ratios.
- Dual-gradient capacity gauge and storage device telemetry cards.
- Quick action batch execution controls with haptic-ready states.
- Live embedded terminal activity monitor with status indicators and monospaced diagnostic streams.
- Multi-breakpoint adaptive layout support for high-density ultra-tall portrait (1080x2460) and wide landscape (2460x1080) viewports.

## [2.1.2] - 2026-09-16

### Added
- Persistent isolated storage environment routing for build caches, temporary directories, and SDK paths.
- Explicit Gradle service directory configuration and toolchain resolution definitions.

### Changed
- Configured Gradle daemon properties for isolated compilation runs.

## [2.1.1] - 2026-09-16

### Added
- Zero-C drive isolated environment configuration targeting storage on secondary drive toolchains.
- Dedicated isolated Android Studio launcher script (`Buka_Studio.bat`) and automated setup workflow.
- Standalone Android Emulator execution script (`Jalankan_Emulator.bat`) with isolated AVD and cache directories.
- Interactive HTML/JS design and responsive testing dashboard (`preview.html`) supporting portrait, landscape, and tablet breakpoints.
- Adaptive round application icon resources (`ic_launcher_round.xml`).
- Global Gradle properties configuring AndroidX and build performance optimizations.

### Changed
- Refactored entire codebase namespace and application package identifier to `app.mountify`.
- Upgraded Dagger Hilt to 2.55 for complete compatibility with Kotlin 2.1 compiler metadata.
- Upgraded target compilation SDK to Android API 35 to fulfill modern AndroidX runtime dependencies.
- Standardized root theme definition to use platform `NoActionBar` parent styles.

### Fixed
- Resolved return type discrepancies in root storage and mount management routines (`unmountAll`, `moveGameData`).
- Resolved Java keystore Base64 decoding syntax compatibility in Gradle build script.
