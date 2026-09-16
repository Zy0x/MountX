# Changelog

All notable changes to Mountify will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to Semantic Versioning.

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
