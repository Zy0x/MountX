# Changelog

All notable changes to Mountify will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to Semantic Versioning.

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
