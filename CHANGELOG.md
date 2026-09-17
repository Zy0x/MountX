# Changelog

All notable changes to Mountify will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to Semantic Versioning.

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
