# Changelog

All notable changes to MountX will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to Semantic Versioning.

## [2.2.19] - 2026-09-20

### Added & Improved
- **Seleksi Multi-Disk & Partisi Spesifik (Disk & Partition Selection)**:
  - Mengembangkan **Langkah 2 (Pilih Disk & Partisi Penyimpanan)** menjadi selektor hierarkis bertingkat:
    - **Tingkat Disk**: Menampilkan seluruh media fisik yang terhubung (SSD Eksternal Type-C, Flashdisk USB OTG, atau MicroSD).
    - **Tingkat Partisi**: Di dalam disk yang dipilih, pengguna kini dapat memilih **partisi spesifik** tempat data game akan disimpan (misalnya Partisi 1 exFAT untuk data umum vs Partisi 2 ext4 khusus direktori game).
    - Menampilkan informasi detail per partisi: Nomor partisi/label, format filesystem (`ext4`, `f2fs`, `exFAT`, `FAT32`), kapasitas total, dan sisa ruang bebas.
    - Menampilkan badge rekomendasi hijau (`✓ Disarankan untuk Game`) untuk partisi Linux native (`ext4`/`f2fs`), serta peringatan edukatif jika memilih format `exFAT`/`FAT32`.
    - Tombol aksi cepat `[⚡ Pasang Partisi (Mount)]` jika partisi target belum terpasang di sistem Android.
- **Opsi Target Penyimpanan Standar MountX (`/data/sdext2`)**:
  - Menyediakan kartu target cadangan untuk Penyimpanan Standar MountX (direktori sistem lokal / virtual container) yang dapat dipilih secara langsung.
  - Memastikan pengguna pada perangkat tanpa slot MicroSD fisik (seperti POCO F5 saat SSD Type-C belum dicolokkan) tetap memiliki opsi target yang jelas dan tidak menemui jalan buntu.
- **Tombol Pindai Ulang Storage (OTG/SSD) Real-Time**:
  - Menambahkan tombol pemindaian ulang storage di header Langkah 2 dan panduan koneksi media eksternal, dilengkapi animasi loading circular saat pemindaian sysfs dan block device berlangsung.
- **Sinkronisasi Langkah 3 & Proteksi Eksekusi**:
  - Ringkasan di Langkah 3 bottom bar menampilkan kombinasi disk dan partisi yang dipilih secara real-time: `Pindahkan [Ukuran] ke [Nama Disk] • [Nama Partisi] (Sisa: [Ruang Bebas])`.
  - Tombol pemindahan dinonaktifkan dengan peringatan jika partisi belum di-mount, mencegah kegagalan pemindahan ke direktori yang belum terpasang.

## [2.2.18] - 2026-09-20

### Added & Improved
- **Alur Berjenjang 3 Langkah (3-Step Guided Migration Hub)**:
  - Menata ulang seluruh alur tab **Kelola (Manage)** di App Detail menjadi 3 langkah berurutan yang intuitif dan terarah:
    1. **Langkah 1: Pilih Data yang Ingin Dialihkan**: Checklist direktori bersih (Data, OBB, Media, dsb) dengan Accordion subfolder untuk melihat path internal & eksternal, Pill Status Lokasi yang jelas (`Di Memori Internal` vs `Di MicroSD`), dan tombol `+ Tambah Direktori Kustom`.
    2. **Langkah 2: Pilih Disk Penyimpanan Tujuan**: Radio card interaktif untuk setiap media penyimpanan eksternal yang terdeteksi (MicroSD, SSD Eksternal Type-C, USB OTG), auto-select jika hanya ada 1 disk aktif, visual capacity progress bar, dan tombol inline quick-mount.
    3. **Langkah 3: Tinjau & Eksekusi**: Pinned sticky bottom bar dengan ringkasan kapasitas mini (`Dipilih: XX • Sisa di [Disk]: YY`), tombol kontekstual `Pindahkan ke [Disk]` dan `Pulihkan ke Internal`, serta Capacity Guard yang otomatis mencegah pemindahan jika ruang disk tujuan atau memori internal tidak cukup.
- **Multi-Disk Selection & UUID Binding**:
  - Menyematkan `diskUuid` pada data model dan katalog portable `.mountx/catalog.json` agar pengikatan direktori selalu menunjuk ke media penyimpanan fisik yang dipilih saat banyak storage terhubung.
- **Penyederhanaan Visual & Lokalisasi Bersih**:
  - Menghapus badge panah ganda `MicroSD ↔ Internal Phone` yang membingungkan.
  - Menggantikan dengan Pill Status Lokasi yang elegan dan ringkas.
  - Memastikan 100% Bahasa Indonesia murni yang komunikatif tanpa istilah campur aduk.

## [2.2.17] - 2026-09-20

### Changed & Improved
- **Directory Classification & Naming Precision (Data vs OBB)**:
  - Corrected directory misclassification in App Detail Manage tab: directories residing under `Android/data` are clearly named **Data**, and directories residing under `Android/obb` are accurately named **OBB**.
  - Added robust automatic category resolution (`resolveCategory()`) in `MountPointConfig` and Room database type converters to seamlessly normalize legacy entries.
- **Novice-Friendly Grouping & Architecture**:
  - Structured directories into clear, intuitive groups:
    1. **Core Game Data (Recommended)**: Highlights primary game assets (`Data` and `OBB`) critical for storage offloading.
    2. **Additional & Custom Data**: Separates optional directories (media downloads, caches, custom user paths).
  - Added interactive **How MountX Works** educational banner explaining transparent filesystem redirection in simple, non-technical terms.
- **Enhanced Directory Path Visibility**:
  - Each directory card presents clean relative paths (e.g., `Android/data/com.kurogame.wutheringwaves.global`) and visual path flow indicators (`MicroSD → Internal HP`).
  - Added live storage telemetry integration so directories display real sizes, gracefully handling empty OBB folders with `0 B (Kosong)`.
- **Interactive Directory Details Dialog**:
  - Tapping any directory card opens a dedicated detail modal with novice-friendly descriptions, live mount status, and full absolute paths with one-tap clipboard copy buttons (`Salin Path`).

## [2.2.16] - 2026-09-20

### Fixed
- **App Detail Storage Redundancy & Double-Counting Elimination**:
  - Dynamically detects active mounts in `/proc/mounts`. When an app's directory is mounted from MicroSD, internal targets (`/data/media/0/Android/data|obb/<pkg>`) are excluded from `du` recursion, ensuring physical flash storage on phone memory is accurately reported as `0 B` with an `[OFFLOADED]` badge.
  - Aligns physical Phone Memory and MicroSD byte calculations, completely eliminating duplicate storage reporting (e.g. Wuthering Waves accurately reported as 12.13 GB total instead of 22.2 GB).
  - Concentric donut chart renders exact physical storage ratios (e.g. 17% Phone / 83% MicroSD) without duplicate or overlapping slices.
- **Floating Action Button (FAB) Positioning & Touch Target**:
  - Fixed double-offset calculation in `GamesScreen`: FAB now rests cleanly 12dp above `ModernNavigationBar` when visible, and glides down smoothly above Android system navigation insets when the bar is hidden.
  - Enlarged FAB touch target from 42dp to 52dp with a 24dp centered icon, fully satisfying mobile accessibility standards (>= 48px target).

## [2.2.15] - 2026-09-20

### Added
- **Interactive Delete App Confirmation Dialog**:
  - Replaced abrupt removal with a 2-option confirmation dialog: *Restore to Internal & Delete* vs *Unmount Only & Delete*.
  - Added real-time data transfer progress tracking with Android WakeLock protection.
- **Strict Physical Disk Validation**:
  - Enforced strict regex (`^(mmcblk[0-9]+|sd[a-z]+|nvme[0-9]+n[0-9]+)$`) and valid volume UUID checks in `StorageManager`, permanently eliminating phantom USB OTG disks.



### Added
- **Full Directory-Driven Architecture Unification (App Detail & Add Flow)**:
  - Replaced legacy `PKG`/`FILES` binary switch and redundant `Transfer Scope` selector chips with active multi-target directory mount points (`MountPointConfig`) as the single source of truth across all views.
  - Unified Add App flow with App Detail by reusing `GameDetailView` (Storage and Manage tabs) in an isolated **Draft / Preview Mode**.
  - **Zero-Trace on Dismiss**: Discarding or navigating back from draft preview guarantees zero database mutations, zero directory creations, and zero file footprints.
  - **Explicit Commit**: Room DB registration and `.mountx/catalog.json` generation occur strictly upon user confirmation via `[Terapkan & Tambahkan]`.
  - **Dynamic Multi-Directory Migration Engine**:
    - Data transfers (Internal to SD / Pulihkan ke Internal) dynamically operate on each mount point's target directory, accurately restoring Media/Custom directories (`/data/media/0/<Folder>`) as well as Android application packages (`/data/media/0/Android/data|obb/<pkg>`).
    - Added virtual ext4 loop container image cleanup when restoring or deleting custom loop configurations.
  - **Backward-Compatible Multi-Target Migration**:
    - Automatic on-the-fly synthesis of legacy game entries into granular `MountPointCategory.GAME_ASSETS` with stable unique identifiers (`legacy_<pkg>_files`, `legacy_<pkg>_pkg`, `legacy_<pkg>_obb`).
- **Responsive Floating Action Button (FAB) Motion**:
  - Implemented zero-recomposition animated vertical offset on Games screen FAB (`Modifier.offset { IntOffset(0, animatedOffsetY) }`) driven by a 220ms `FastOutSlowInEasing` transition.
  - FAB smoothly glides downward when bottom navigation bar hides during list scrolling and returns seamlessly when visible, anchored to navigation bar window insets.
- **Smart App Picker Duplication Filtering**:
  - `AddAppPicker` now dynamically filters out applications already registered in MountX, preventing duplicate registrations and displaying only eligible unmanaged apps.

## [2.2.13] - 2026-09-19

### Added
- **Universal Smart Directory Classification Engine**:
  - Expanded from legacy binary mode (`PKG` vs `FILES`) into a structured **Multi-Target Array** architecture (`mount_points: [{ id, category, source_path, target_path, enabled, ... }]`).
  - Introduced 4 Human-Readable UI Preset Categories:
    1. *Game Assets & Data* (Default: Checked [✔] - 99% stability, maps `files` and `obb`).
    2. *Media & Downloads* (Default: Checked [✔] if detected, maps `/sdcard/<App>/` or `/Android/media/<pkg>/`, with auto `.nomedia` placement in target).
    3. *Cache & Shaders* (Default: Unchecked [ ], with UI warning regarding shader compilation stutter on external storage).
    4. *Custom Path Binding* (Manual Advanced targeting with `[+ Tambah Direktori Kustom]`).
  - Implemented **Virtual Ext4 Loop Container** option (`.img` sparse image in `$sdBase/.mountx/containers/` via loop device `losetup`) for large private data (`/data/data/<pkg>`) exceeding 1 GB, safeguarding against SQLite WAL database locks on exFAT/FAT32 partitions.
- **Event-Driven Real-Time Auto-Sync Engine (`SystemSyncMonitor`)**:
  - Continuous reactive monitoring for hardware events (`ACTION_MEDIA_MOUNTED`, `ACTION_MEDIA_UNMOUNTED`, `ACTION_MEDIA_EJECT`, `ACTION_MEDIA_BAD_REMOVAL`) and package lifecycle events (`ACTION_PACKAGE_ADDED`, `ACTION_PACKAGE_REMOVED`, `ACTION_PACKAGE_REPLACED`).
  - Automatically updates storage partitions, package lists, and mounted game states instantly across Dashboard, Games, and Storage screens without waiting for manual swipe or refresh gestures.
  - Emergency Eject Protocol: Instant `umount -f -l`, graceful game process termination via `am force-stop`, and immediate Heads-Up warning notification upon external storage ejection.
- **Clean Module Ecosystem & Zero-Residue Policy**:
  - Integrated official `$MODDIR/uninstall.sh` lifecycle hook cleanly unmounting active namespaces, detaching loop containers, restoring `/data/media/0/Android` POSIX permissions (`chmod 775` & `restorecon -FR`), and safely pruning runtime state.
  - Strict isolation of supplementary binaries within `$MODDIR/bin/` without leaving residues in Android root partitions.

### Changed
- **Notification Drawer Header Polish**:
  - Cleaned system drawer notification header to pure **"MountX"**, removing technical kernel namespace labels.
- **Superuser Root Audit Resolution**:
  - Resolved cold-launch false negative in root permission audit by actively querying `RootShell.isAvailable` on startup.
- **Room Database Schema v2 & Catalog Synchronization**:
  - Seamless automated migration (`MIGRATION_1_2`) serializing multi-target configurations into Room and `.mountx/catalog.json`.

## [2.2.12] - 2026-09-19

### Added
- **Comprehensive Permissions Onboarding & Live Audit Sheet**:
  - Implemented centralized `PermissionManager` and modern `PermissionOnboardingSheet` covering Superuser Root access, All Files Access (`MANAGE_EXTERNAL_STORAGE`), System Notifications (`POST_NOTIFICATIONS`), and Unrestricted Battery Optimization exemption.
  - Automatically guides new users with live status badges and 1-tap direct intent launchers on launch if essential permissions are missing.
  - Added dedicated "Perizinan & Hak Akses" entry in Settings for on-demand auditing and reconfiguration anytime.
- **Swipe-To-Refresh Across Core Tabs (Pull-To-Refresh Gesture)**:
  - Integrated Material3 `PullToRefreshBox` across Dashboard, Games, and Storage tabs for swift, natural pull-down reloading.
  - Streamlined Dashboard header by removing redundant top-right refresh icon button in favor of natural gesture interaction.
- **Modernized Android System Notification Drawer & Quick Actions**:
  - Completely revamped foreground service notification (`MountService.kt`) with sleek, professional copywriting in Indonesian and English, free from AI-slop patterns.
  - Embedded instant quick action buttons `[Buka MountX]` and `[Lepas Semua]` directly into the system drawer for rapid unmounting without entering the app.
- **Kernel Mounts Live Telemetry Expansion & Clipboard Export**:
  - Introduced scrollable accordion view for live `/proc/mounts` bind entries with "Lihat Selengkapnya (N Titik Mount) ▼" / "Sembunyikan ▲" toggle.
  - Added 1-tap "Salin Ringkasan Mount" button to copy active kernel bind configurations directly to clipboard.
- **Tactile Refresh Feedback & 360° Rotation Animations**:
  - Added fluid 360-degree spin animations and live recalculating text feedback ("Menghitung…") across all reload buttons in the app (Namespace Verification sheet, About update check, Logs screen, and Disk Detail partition refresher).

### Changed
- **Total Elimination of App Picker Bottom Void Gap**:
  - Resolved `innerPadding` discrepancy in `NavGraph.kt` by setting bottom pager padding to 0.dp when sub-screens (`AddAppPicker`, `GameDetailView`) are active.
  - Enhanced edge-to-edge flow down to the Android system navigation bar with proper `.navigationBarsPadding()`.

## [2.2.11] - 2026-09-19

### Added
- **Dashboard Active Games Mini-List & Quick Mount Management**:
  - Embedded an interactive 4-game preview section directly within the Dashboard Master Control card.
  - Displays game icon, app label, real package data size, and live mount status badge.
  - Instant one-tap mount/unmount switches per game directly from the Dashboard.
  - "Lihat Semua Game (N) →" shortcut facilitating seamless navigation straight to the full Games management library.
- **Kernel-Level Live Namespace & Telemetry Verification Sheet**:
  - Interactive tap on dashboard metrics (Active Namespaces / Offloaded Data) unveils an in-depth live diagnostics bottom sheet.
  - Inspects real `/proc/mounts` bind entries, Master Mount Namespace readiness (`Shell.FLAG_MOUNT_MASTER`), and live canary sentinel status.
  - Includes on-demand dynamic storage recalculation tool for zero-size games using background multi-threaded kernel stats.

### Changed
- **Adaptive Squircle Brand Emblem (`ic_mountx_emblem`)**:
  - Re-engineered app icon rendering across Dashboard and About screens to use a clean transparent alpha emblem.
  - Nested within an adaptive squircle container with dynamic aurora gradient border, eliminating unsightly opaque bounding boxes.
- **Add Game Sheet Layout Precision & Void Gap Elimination**:
  - Resolved redundant window insets padding in the App Picker view (`AddGameSheet.kt`).
  - Optimized list bottom padding to 48.dp, eliminating the large blank void on 1080×2460 and tall aspect-ratio displays.
- **Automatic Background Game Data Size Resolution**:
  - Enhanced repository layer to automatically compute real offloaded storage footprints for newly imported or restored games via background coroutines.

## [2.2.10] - 2026-09-19

### Added
- **MicroSD Disk Portability & Smart Discovery Engine**:
  - Implemented portable catalog management (`.mountx/catalog.json`) stored directly on external storage with atomic write protection (`.tmp -> mv`).
  - Added ultra-fast shallow directory scanning of MicroSD `Android/data` and `Android/obb` to automatically identify portable game assets without heavy recursive traversal.
  - 1-Click Game Restoration & Reconciliation: dynamically resolves sandbox UID and GID (`stat -c "%u %g" /data/data/$PACKAGE_NAME`) and restores SELinux contexts, enabling effortless plug-and-play across devices and fresh installs.
- **Passive Event-Driven Integrity Watchdog (`MountWatchdogDaemon`)**:
  - Battery-friendly passive monitoring hooked into `ACTION_SCREEN_ON`, storage ejects, and pre-launch triggers.
  - Canary file verification (`.mountx_canary`) ensures mounts never silently drop after deep sleep.
  - Instant lazy unmount safeguard (`umount -l`) upon `ACTION_MEDIA_EJECT` to protect the Linux kernel from deadlocks.
- **Safe-Uninstall & Emergency Rescue Protocol**:
  - Enhanced module `service.sh` to autonomously detect app removal (`pm path`), cleanly unmount active binds, restore default internal Android storage permissions, and self-disable.
  - Added standalone emergency restore script generator (`/sdcard/MountX_Emergency_Restore.sh`) for ADB and Termux recovery.
  - Added Emergency Panic Reset button in Settings for instant 1-click unmount and permission restoration.
- **Official Brand Icon Integration**:
  - Added full animated splash screen utilizing MountX brand foreground icon on dark cyber background.
  - Integrated official brand icon in the Dashboard top header and About screen.

### Changed
- **Extreme App Picker Performance Overhaul (MT Manager-Grade 120 FPS)**:
  - Eliminated recomposition cascade by removing scroll-state dependencies from item cards and leveraging stable Compose skipping.
  - Implemented application metadata caching (`registerAppInfos`) to completely bypass repetitive `PackageManager` Binder IPC during icon rendering.
  - Enabled release build R8 optimization and minification with automatic debug signing fallback for real-world performance verification.

## [2.2.9] - 2026-09-19

### Added
- **Dynamic Island Floating Notification (Top-Center)**:
  - Designed and deployed an intelligent floating capsule/pill notification at top-center (`DynamicIslandFeedback`) with smooth entry/exit slide animations.
  - Automatic 4-second dismissal timer for successful operations and persistent warning/error badges with manual dismiss action.
  - Completely replaced static inline feedback banners in the Storages screen.

### Changed
- **I/O Booster Preset Workflow Decoupling**:
  - Selecting an I/O Booster preset (Gaming Ultra, Balanced, Default System) now only dynamically populates the scheduler, buffer, and persistence controls in the sheet.
  - Root command execution is deferred until the user intentionally clicks the "Apply Optimization" / "Terapkan Optimasi" button.
- **Settings Screen Visual & Layout Polish**:
  - Center-aligned typography and full-width touch zones across all Theme (Light, Dark, System) and Language (English, Indonesia) FilterChips.
  - Removed redundant "Storage Paths" section from Settings, consolidating storage path configuration exclusively within the Storages tab.

### Fixed
- **Systemic Anti-Lag & Frame-Rate Stabilization (120 FPS Optimization)**:
  - Eliminated full-tree layout and measurement churn in `NavGraph` by replacing `AnimatedVisibility` on the bottom navigation bar with GPU hardware-accelerated `graphicsLayer` translation (`translationY = animatedOffset * size.height`).
  - Serialized app icon loading in `AppIconManager` to `Dispatchers.IO.limitedParallelism(1)` to eliminate Binder IPC queue saturation on `PackageManagerService`.
  - Added scroll-aware icon loading throttle (`isScrollingFast = listState.isScrollInProgress`) to pause asynchronous icon decoding during active high-speed fling gestures.
  - Removed unused CPU-intensive DP animation from `AddGameSheet` and memoized surface luminance computations in `AppPickerItemCard`.
- **Complete Operation Localization Fix**:
  - Eliminated hardcoded Indonesian text leaks in `StorageViewModel` operations (Guided fsck repair, filesystem integrity checks, formatting, partition label changes, I/O parameter tuning, flash TRIM, and F2FS garbage collection) by integrating string resources across English (`values`), Indonesian (`values-id`), and system fallback (`values-in`).

## [2.2.8] - 2026-09-19

### Changed
- **Storage & Logging Engine Total Purge of Legacy Names**:
  - Ceased generation of `/storage/emulated/0/mountify.log` in `AppLogger`; user logs are now written exclusively to `/storage/emulated/0/mountx.log`.
  - Added automatic purge of legacy `mountify.log` in user storage during log clear operations in `LogsViewModel`.
  - Cleaned ProGuard rules in `app/proguard-rules.pro` to strictly enforce `app.mountx.data.model.**`.
  - Fully updated official repository clauses in `AGENTS.md` to reference `https://github.com/Zy0x/MountX`.

## [2.2.7] - 2026-09-19

### Changed
- **Comprehensive Project Rebranding Cleanup**:
  - Aligned ProGuard obfuscation rules in `app/proguard-rules.pro` to retain `app.mountx.data.model.**` while preserving legacy compatibility.
  - Updated default backup JSON export filename from `mountify_config.json` to `mountx_config.json` in both `BackupRestoreScreen.kt` and `StorageScreen.kt`.
  - Updated keytool generation commands and alias in `KEYSTORE_SETUP.md` to `mountx-release.jks` and `mountx`.
  - Updated `README.md` repository clone and navigation commands to reflect the official `MountX` workspace directory.
  - Aligned Web preview badges and backup dialog extensions in `preview.html` to `MOUNTX v2.2.6+` and `.mountx-bak`.
  - Updated technical standards in `AGENTS.md` to declare the primary project folder identity as `MountX`.

## [2.2.6] - 2026-09-19

### Changed
- **Total Rebrand Enforcement to MountX**:
  - Restored and strictly enforced **MountX** as the sole official identity across all application strings (`app_name`, `dashboard_title`, `dashboard_module_install_guide`, `about_donate_desc`, `game_detail_section_shared`, `game_detail_delete_action`) in English (`values`), Indonesian (`values-id`), and system alias (`values-in`).
  - Updated Gradle root project name in `settings.gradle.kts` to `rootProject.name = "MountX"`.
  - Updated foreground notification channel name, titles, and progress descriptions in `MountService` to MountX.
  - Aligned core logging targets in `AppLogger` to write primarily to `/storage/emulated/0/mountx.log` and `/data/adb/modules/MountX/mountx.log` with transparent fallback for legacy module paths.
  - Updated `GameRepository.syncModuleGamelist` to target `/data/adb/modules/MountX/gamelist.conf` with legacy directory fallback.
  - Updated technical standards in `AGENTS.md` with explicit anti-regression rules (§10.4) prohibiting accidental reversion of the project name back to legacy Mountify.

## [2.2.5] - 2026-09-19

### Added
- **Instant App-Wide Localization Engine**:
  - Configured `LocalContext` in Compose to supply dynamic `localizedContext` created from `createConfigurationContext`, enabling instantaneous translation of 100% of UI strings across all screens upon language selection without restarting.
  - Aligned locale synchronization across Android 10 through 15+ using `LocaleManager` and `applyOverrideConfiguration`.
  - Comprehensive English (EN) and Indonesian (ID) coverage across all navigation labels, settings, picker headers, and dialogs.
- **High-Performance App Picker Architecture (120 FPS Anti-Lag)**:
  - Background asynchronous icon pre-warming: Proactively decodes application icons in memory via a dedicated background coroutine immediately following app discovery.
  - Optimized in-memory icon `LruCache` with 72x72/96x96 px rendering dimensions, eliminating repeat Binder IPC calls to `PackageManagerService`.
  - Isolated `@Composable fun AppPickerItemCard` with `@Stable` parameters to enable Compose smart recomposition skipping during high-speed flings.
  - Native Full-Screen sub-screen architecture: Automatically hides the floating bottom navigation bar when opening the App Picker, maximizing screen real estate and eliminating redundant view rendering.
- **Smart Auto-Hide Navigation Bar**:
  - Implemented directional scroll detection that smoothly translates the bottom navigation bar out of view on downward scroll and restores it on upward scroll.
- **Polished Light Theme & System Insets Audit**:
  - Adaptive status bar icons: Integrated `WindowInsetsControllerCompat` to render crisp, dark system icons (`isAppearanceLightStatusBars = true`) in Light Mode, eliminating invisible white-on-white text.
  - Dedicated *Frost Light* dock container for `ModernNavigationBar`: Clean white/frost surface (`#FFFFFF`) with subtle border (`#E2E8F0`) and soft ambient shadow, replacing the dark navy block in Light Mode.
  - High-contrast card backgrounds, borders, and text labels for WCAG AAA accessibility compliance in Light Mode.
  - Preserved high-contrast dark *Cyber Terminal* container (`#090D16`) for the Logs tab and filesystem check (fsck/trim) diagnostic output across all themes.

### Fixed
- Fixed Compose `stringResource` ignoring runtime language change due to `LocalContext` retaining system base context.
- Fixed catastrophic frame drops and stuttering when scrolling through 200+ apps in the Add Game picker.
- Fixed bottom navigation bar dock background remaining hardcoded to dark navy (`#131620`) in Light Theme.
- Fixed status bar icons becoming unreadable white-on-white in Light Theme.

## [2.2.4] - 2026-09-19

### Added
- **Unallocated Space Visualization in Partition Wizard**:
  - `InteractivePartitionSliderBar` now renders an explicit unallocated space block (amber/yellow color) when total partition sizes fall short of disk capacity, preventing silent space loss.
  - Each `WizardPartitionCard` shows a **+ Remaining (xGB)** action button when unallocated space exceeds 1 MB, allowing instant one-tap allocation of the remainder to that partition.
  - `StorageViewModel.allocateUnallocatedToPartition(idx)` calculates leftover disk space and appends it to the chosen partition, clamped to respect minimum partition sizes.
- **Filesystem Support Badges in Partition Wizard**:
  - Filesystem type chips within wizard partition cards now display a red dot indicator and dimmed label for kernel-unsupported or tool-unsupported filesystem types.
  - A contextual warning note (`⚠ <description>`) appears below the chip row when the currently selected filesystem is not fully supported on the device.
- **Runtime Language Switching (Android 13+)**:
  - `SettingsViewModel.setLanguage()` now calls `LocaleManager.setApplicationLocales` on API 33+ for per-app locale switching without a full app restart.
  - `MainActivity` wraps the Compose tree in a `CompositionLocalProvider` supplying a localized `Context` and `Configuration` derived from the active language preference.
- **Real-time Unified Log Engine (`AppLogger`)**:
  - New `AppLogger` singleton writes structured log entries to `/storage/emulated/0/mountify.log`, `/data/adb/modules/Mountify/mountify.log`, and the legacy `mountx.log` path simultaneously.
  - All game repository operations (add, remove, mount, unmount, mode change, enable toggle) now emit structured log entries at appropriate severity levels.
- **WARN Log Level Support**:
  - Added `WARN` variant to the `LogLevel` enum and amber color rendering (`#FFB74D`) in the log viewer for warning-level entries.
- **Disk Hardware Info Cache**:
  - `StorageManager` caches disk hardware attributes (manufacturer, model, vendor, size) in `diskHardwareCache` after the first sysfs query, eliminating redundant root shell executions on subsequent disk detection calls.
- **Storage Tab Pre-warming**:
  - `StorageViewModel` now eagerly starts partition detection and filesystem capability loading in the `init` block on `Dispatchers.IO`, so the Storage tab renders instantly on first navigation.

### Fixed
- Removed duplicate `updateGameMode` method in `GameRepository` that caused overload resolution ambiguity.
- Added missing `updateEnabled` DAO method in `GameDao` required by `GameRepository.setGameEnabled`.
- Fixed missing `AppLogger` import in `GameRepository` that caused ~15 unresolved reference compile errors.
- Fixed `LogsScreen` `when` expression non-exhaustiveness after `WARN` was added to `LogLevel`.
- Fixed `StorageScreen` reference to non-existent `SupportedFilesystemInfo.statusNote` field; now correctly uses `description`.
- Added missing `verticalScroll` and `rememberScrollState` imports to `AboutScreen` for the scrollable changelog dialog.

## [2.2.3] - 2026-09-19


### Fixed
- **Deep Unmount & Kernel Lock Teardown**:
  - Resolved `mmcblk0p3` (`/data/sdext2`) requiring double unmount attempts by deeply unbinding all game folders across runtime namespaces (`/mnt/runtime/*`, `/storage/emulated/0/*`, `/data/media/0/*`), tearing down mounts across all active process namespaces via `nsenter`, dropping VM caches (`echo 3 > /proc/sys/vm/drop_caches`), and executing clean unmount with immediate optimistic UI status feedback.
  - Added active `CircularProgressIndicator` spinner and dynamic "Memasang..." / "Mounting..." label directly on partition Mount buttons to eliminate user confusion during mounting.
- **Deduplicated TRIM Results Dialog & Layout Fix**:
  - Eliminated overlapping duplicate dialogs (`OperationProgressOverlay` and `AlertDialog`) by directly surfacing a single clean TRIM result dialog upon completion.
  - Re-engineered TRIM dialog layout with structured target volume metadata cards, actionable remediation banner, and horizontal/vertical scrollable monospace terminal box to prevent awkward text wrapping of long mount points and UUIDs.
- **FSCK Volume In-Use (EBUSY) Error Prevention**:
  - Fixed `fsck.f2fs: In use by the system!` by tearing down all runtime namespace sub-mounts and releasing process file locks before invoking filesystem repair tools.
- **Butter-Smooth 60/120 FPS Partition Wizard Slider**:
  - Eliminated severe stutter and frame drops when dragging partition dividers in the Repartition Wizard by isolating divider handles into dedicated composables with static `pointerInput(dividerIndex)` keys, `rememberUpdatedState`, local touch accumulators, and 4MB quantized drag steps.
- **App Navigation & Shell Overhead Optimization**:
  - Added `beyondViewportPageCount = 1` in `HorizontalPager` to retain adjacent tabs in memory and eliminate tab switching frame drops.
  - Cached supported filesystem capabilities in `StorageManager` to eliminate redundant root shell executions.
  - Batched sysfs disk attribute inspection queries and reused pre-scanned partitions during disk detection.
- **Streamlined Confirmation Dialogs**:
  - Replaced repetitive and redundant text paragraphs in Unmount Partition and Eject Disk confirmation dialogs with concise question prompts and compact metadata cards featuring monospace paths, filesystem badges, and high-priority active game warning banners.

## [2.2.2] - 2026-09-19

### Added
- **AOMEI-Style Interactive Multi-Partition Slider Bar**:
  - Interactive proportional visual partition bar in `PartitionWizardDialog` allowing intuitive division of disk space across 2 or more partitions.
  - Draggable touch-friendly divider handles (`||`) between adjacent partitions enabling real-time resizing with automatic 512 MB minimum partition clamping.
  - Quick proportional stepper controls (`-1 GB`, `+1 GB`, `+5 GB`) and precision slider on each partition card alongside exact manual KB inputs.
- **Unmount & Batch Eject Dynamic Progress Indicators**:
  - Partition unmount buttons and batch "Eject Disk" header button now render active `CircularProgressIndicator` spinners with dynamic labels ("Unmounting..." / "Ejecting...") and disable interactive controls to prevent duplicate taps or race conditions.
- **Direct Filesystem Path Resolution for FSTRIM (FUSE Bypass)**:
  - Added automatic resolution of public Android mount points (`/storage/<UUID>`) to direct block-level mount paths (`/mnt/media_rw/<UUID>` or `/mnt/pass_through/0/<UUID>`), bypassing Android FUSE limitations that cause `FITRIM: Function not implemented`.
  - Comprehensive bind-mount teardown across runtime namespaces prior to running `fsck.f2fs` / `e2fsck` during dirty volume remediation, followed by automatic remount and game restoration.

## [2.2.1] - 2026-09-18

### Added
- **Disk & Partition Unmount Confirmation Safeguards**:
  - Added modal confirmation dialogs for single partition unmounting and batch "Eject Disk" operations.
  - Automatic inspection of active game bind-mounts targeting the partition (`isTargetMount`) with high-priority crimson warning alerts to prevent application freezes or crashes.
  - Modernized "Eject Disk" button with official eject icon and clear multi-partition unmount metrics.
- **Actionable FSTRIM Remediation Pipeline**:
  - Upgraded global and partition TRIM execution with structured output parsing detecting kernel `EUCLEAN` (`Structure needs cleaning`) and unsupported discard operations.
  - Added dedicated actionable remediation alert banner with **"Run fsck Now"** direct execution button in the TRIM results dialog.
- **Cyber AMOLED Edit Partition Label Dialog**:
  - Re-engineered volume rename dialog to match the `#121622` AMOLED cyber aesthetic with glowing cyan accents, monospace target path telemetry, and streamlined touch targets.

### Fixed
- **Sequential Read Benchmark 0.0 MB/s Calculation**:
  - Resolved Toybox `dd` incompatibility (`bad iflag=direct`) on Android userspace by standardizing block count/size flags, validating byte throughput records, and calculating accurate read speeds (>0 MB/s, measured 48.7 MB/s on real hardware).
- **Kernel Filesystem Detection Accuracy**:
  - Fixed false `[Unsupported]` badges in `SingleFormatDialog` by replacing combined multi-binary check with individual executable probes (`[ -x /system/bin/... ] || command -v ...`), correctly detecting F2FS, Ext4, FAT32, and exFAT as `[Kernel Ready]`.

## [2.2.0] - 2026-09-18

### Added
- **Total Rebrand to MountX**:
  - Rebranded entire project, package ID (`app.mountx`), and application display name to **MountX**.
  - Integrated official high-resolution MountX launcher icons from `Icon/` across all mipmap densities (hdpi, mdpi, xhdpi, xxhdpi, xxxhdpi) and adaptive foreground vector assets.
  - Transparent auto-migration of legacy database (`mountify.db` to `mountx.db`) and legacy shared preferences (`mountify_prefs` to `mountx_prefs`).
  - Rebranded root module to `MountX` (`id=MountX`, `name=MountX`) with backward-compatibility detection for `/data/adb/modules/MountX` and `/data/adb/modules/Mountify`.
- **Universal Operation Progress Overlay**:
  - Created cyber-styled `OperationProgressOverlay` modal providing step-by-step progress tracking and live terminal output for all long-running root operations (I/O optimization, global FSTRIM, partition formatting, filesystem checks, and volume labeling).
  - Features pulsing cyber status badges, auto-dismiss countdown timer, and manual dismiss controls.
- **Kernel Filesystem Detection Engine**:
  - Dynamic discovery of supported filesystems directly from `/proc/filesystems` and device binary utilities (`mkfs.f2fs`, `mke2fs`, `mkfs.ext4`, `mkfs.fat`, `mkfs.exfat`).
  - Added dynamic badges (`[Kernel Ready]` vs `[Unsupported]`) in `SingleFormatDialog` with automatic disabling of unsupported filesystem options to prevent system corruption.
- **Non-Destructive Partition Labeling**:
  - Added in-place volume label editing dialog in `PartitionToolsBottomSheet` using native Linux utilities (`tune2fs`, `e2label`, `fatlabel`, `exfatlabel`, `f2fs.setlabel`) without formatting or risking data loss.
- **Structured Filesystem Diagnostics (fsck)**:
  - Added structured `FsckReport` model and interactive diagnostics dialog displaying status badges (Clean, Repaired, Dirty Warning, Error), inode counts, block statistics, and collapsible raw log accordion.
- **Intelligent I/O Schedulers & Factory Baseline Persistence**:
  - Live query of physical disk queue schedulers from `/sys/block/<disk>/queue/scheduler`.
  - Automatic detection and persistent storage of factory baseline (`read_ahead_kb`, stock scheduler) in `AppPreferences` on first initialization.
  - "Default" performance preset cleanly restores exact factory read-ahead buffer and stock kernel scheduler.
- **Visual & Ergonomics Refinements**:
  - Harmonized "Repartition Disk" button in `DiskDetailView` into a sleek cyber outlined card with glowing icon and theme-adaptive borders.
  - Streamlined Disk Tools bottom sheet into 3 focused action tiles (I/O Speed Booster, Maintenance, Benchmark) with hardware telemetry embedded directly in the Hub footer.

## [2.1.57] - 2026-09-18

### Added
- **Disk Tools Animated Menu Hub**:
  - Replaced the long monolithic Disk Tools bottom sheet with a modular in-sheet navigation system powered by Compose `AnimatedContent` with horizontal slide and crossfade transitions.
  - Implemented 4 Cyber Menu Tiles on the Hub:
    1. **I/O Speed Booster**: Quick presets (Gaming Ultra, Balanced, Default), read-ahead buffer tuning (128 KB - 4096 KB), I/O schedulers, and boot persistence.
    2. **Flash Storage Maintenance**: Global FSTRIM block reclamation and F2FS urgent garbage collection defragmentation.
    3. **Quick Read & Latency Benchmark**: 64 MB sequential read and access latency test with live result cards.
    4. **Hardware & Bus Telemetry**: CID, manufacturer identification, bus clock frequency, and UHS speed class.
  - Added in-sheet back navigation with both a header back button (`[ ← ]`) and Android system `BackHandler` integration.
- **Partition Tools Non-Destructive Verification**:
  - Verified non-destructive filesystem check (`fsck.f2fs`) diagnostics workflow on target partitions with real-time terminal output modal.
  - Verified partition-level FSTRIM execution and filesystem metadata inspection.
  - Enforced zero data-loss safety barriers on destructive formatting and filesystem conversion workflows.
- **Localization Completion**:
  - Synchronized English (`values/strings.xml`) and Indonesian (`values-id/strings.xml`) resources for all diagnostic, telemetry, and maintenance components.

## [2.1.56] - 2026-09-18

### Added
- **Hierarchical Disk vs. Partition Operations Architecture**:
  - Restructured Storage Disk Detail view into a clean 2-tier hierarchy separating physical drive operations from logical volume data tasks.
  - **Disk Level Controls (`mmcblk*`)**:
    - Batch Mount & Eject All Partitions button with real-time state feedback.
    - Partition Wizard trigger for non-destructive or full drive repartitioning.
    - Dedicated **Disk Tools Modal Bottom Sheet** with full sysfs hardware access and diagnostics.
  - **Partition Level Controls (`mmcblk*p*`)**:
    - Compact 2-button row layout (65% width primary 1-Click Mount/Unmount, 35% width Partition Tools button) eliminating text truncation across 720p to 4K resolutions.
    - Dedicated **Partition Tools Modal Bottom Sheet** for volume maintenance.
- **Root I/O Speed Booster Engine (App2SD Style)**:
  - Dynamic kernel sysfs queue tuning directly on `/sys/block/<disk>/queue/`.
  - Configurable Read-Ahead buffer slider (128 KB to 4096 KB) with quick chips (512, 1024, 2048, 4096 KB).
  - Kernel I/O scheduler switcher (dynamically enumerates available schedulers: `none`, `mq-deadline`, `kyber`, `bfq`).
  - CPU I/O completion affinity (`rq_affinity`) and VM cache pressure tuning (`vfs_cache_pressure`).
  - 3 Instant Performance Presets:
    - *Gaming Ultra*: 2048 KB Read-Ahead, `none` scheduler, `rq_affinity=2`, `vfs_cache_pressure=20` for minimal game texture streaming latency.
    - *Balanced*: 1024 KB Read-Ahead, `mq-deadline`, `rq_affinity=1`, `vfs_cache_pressure=50`.
    - *Default / Battery Saver*: 128 KB Read-Ahead, stock scheduler, `rq_affinity=0`, `vfs_cache_pressure=100`.
  - Full boot-time persistence: stores active configuration into `/data/adb/modules/Mountify/config.conf` and applies automatically on startup via `service.sh`.
- **Advanced Disk Diagnostic & Maintenance Tools**:
  - **Global & Partition FSTRIM**: Discards unused flash memory blocks on mounted volumes via `fstrim -v` with dialog output.
  - **F2FS Urgent Garbage Collection**: Triggers flash defragmentation via `/sys/fs/f2fs/<dev>/gc_urgent` to eliminate stutter during intense game asset streaming.
  - **Quick Disk Benchmark**: 64 MB sequential read & write throughput test via `dd` reporting exact MB/s speeds.
  - **Hardware Telemetry**: Reads eMMC/SD CID, CSD, serial number, manufacture date, OEM ID, and bus speed mode.
- **Filesystem Conversion & Partition Tools**:
  - Directed Format & Filesystem Switcher supporting F2FS, Ext4, FAT32, and exFAT with safety confirmation.
  - Filesystem Integrity Check (`fsck`) with real-time log viewer.
  - Ultra low latency mount flags for game storage partitions: `inline_data,inline_dentry,flush_merge,mode=adaptive` for F2FS and `commit=60,delalloc,data=writeback` for Ext4.

## [2.1.55] - 2026-09-18

### Changed
- **Modern Cyber Header (~56dp Height)**:
  - Redesigned and scaled the app header: enlarged title typography from 15sp to 18sp Bold with improved letter-spacing.
  - Enlarged brand mark icon container from 28dp to 36dp with rounded corner accent.
  - Enhanced root status pill to 30dp height with 12sp Bold label and glowing LED halo dot.
  - Increased tactile refresh button touch target to 38dp with 18dp icon.
  - Added a subtle bottom outline divider line (`HorizontalDivider`) to cleanly delineate the header from screen content.
  - Applied the enlarged 18sp bold header styling and divider consistently across all sub-screens via `CompactScreenHeader`.
- **Floating Cyber Pill Navigation Bar (Anti-Lag Architecture)**:
  - Replaced the bottom navigation bar with a floating cyber capsule dock with 26dp rounded corners, outer margin padding, and subtle border stroke.
  - Fixed structural slot per tab (zero dynamic layout shift) eliminating runtime re-layout passes for instantaneous 120 FPS tab switching.
  - Prominent 22dp icons and permanent 10.5sp labels across all 5 navigation destinations.
  - Cyber active state featuring a semi-transparent HyperCyan capsule background, vibrant cyan text/icon, and a micro cyber LED indicator bar underneath.

## [2.1.54] - 2026-09-18

### Changed
- **Harmonized Capacity Health Colors (Used & Free Synchronization)**:
  - Synchronized the text color of both `Used` and `Free` metrics across all visual partition map cards (`InternalDiskVisualMapCard` and `DiskVisualMapOverviewCard`) to dynamically follow the disk's usage health color (`FormatUtils.getHealthColor(usedPercent)`).
  - Eliminated the visual discrepancy where `Free` was previously hardcoded to green even when storage reached critical (red/orange) thresholds.
  - The `Total` metric remains neutral (`onSurface`), preserving visual hierarchy.
- **Label Refinements**:
  - Updated the Dashboard disk telemetry row label from `"Internal"` to `"Internal Storage"`.
  - Updated the Storage Visual Map card header from `"userdata (/data)"` to `"Internal Storage (/data)"` across English and Indonesian localizations.

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
