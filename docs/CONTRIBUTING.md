# Contributing to MountX

Thank you for your interest in contributing. This document covers everything you need to build the project locally, understand the codebase, and submit a pull request.

---

## Getting Started

### Requirements

| Tool | Version |
|---|---|
| Android Studio | Ladybug (2024.2.x) or newer |
| JDK | **17** (required — do not change) |
| Kotlin | 2.0+ (managed by Gradle version catalog) |
| Gradle | Wrapper included in the repo |

### Clone and Open

```bash
git clone https://github.com/Zy0x/MountX.git
cd MountX
```

Open the project in Android Studio. Allow Gradle to sync.

### Build

Always build the **release** variant for any functional testing or deliverable:

```bash
.\gradlew.bat assembleRelease
# Output: app/build/outputs/apk/release/app-release.apk
```

For quick compilation checks only (not for testing behavior):

```bash
.\gradlew.bat assembleDebug
```

> Do **not** submit PRs that only compile in debug mode. All changes must produce a working release build.

### Signing (Local)

To produce a signed release APK locally:

1. Create `keystore.properties` in the project root (this file is in `.gitignore` — never commit it):
   ```properties
   storeFile=/path/to/your.jks
   storePassword=yourStorePassword
   keyAlias=yourKeyAlias
   keyPassword=yourKeyPassword
   ```
2. Run `.\gradlew.bat assembleRelease`.

For CI signing setup, refer to [KEYSTORE_SETUP.md](../KEYSTORE_SETUP.md).

---

## Project Structure

```
MountX/
├── app/
│   ├── build.gradle.kts              # App-level build config, version, dependencies
│   └── src/main/
│       ├── assets/module/module.prop # In-app module version reference
│       ├── java/app/mountx/
│       │   ├── data/                 # Room database, DAOs, data models, repositories
│       │   ├── di/                   # Hilt dependency injection modules
│       │   ├── root/                 # libsu shell engine, mount manager, storage manager
│       │   ├── service/              # Background WorkManager jobs, boot receiver
│       │   ├── ui/                   # Jetpack Compose screens, ViewModels, navigation
│       │   └── util/                 # DataStore preferences, formatters, update checker
│       └── res/
│           ├── values/strings.xml    # English strings
│           └── values-id/strings.xml # Indonesian strings
├── module/
│   ├── module.prop                   # Magisk module metadata
│   ├── service.sh                    # Boot-time bind-mount and I/O tweak script
│   ├── config.conf                   # Module runtime config (SD_BLOCK, FS_TYPE, etc.)
│   └── gamelist.conf                 # Dynamic app list read by the boot service
├── .github/workflows/build.yml       # CI/CD — builds release APK and publishes releases on tags
├── CHANGELOG.md                      # Public changelog (English only)
└── docs/                             # Documentation
```

---

## Code Style

### Kotlin & Compose

- Follow official [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html).
- Use **Jetpack Compose** for all UI — no XML layouts.
- Composables are pure functions; all state lives in ViewModels.
- Use `StateFlow` and `collectAsStateWithLifecycle()` — never `LiveData` in new code.
- Avoid business logic inside `@Composable` functions. Logic belongs in the ViewModel or repository layer.

### Theme & Colors

- Use semantic color tokens defined in `ui/theme/Color.kt` — do not hardcode hex values in composables.
- For theme detection inside composables, always use luminance check:
  ```kotlin
  val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
  ```
  Do **not** use `isSystemInDarkTheme()`.

### Strings

- All user-visible strings must be in `res/values/strings.xml` (English) **and** `res/values-id/strings.xml` (Indonesian).
- Do not hardcode user-visible strings in Kotlin source files.
- Changelog text in `ChangelogData.kt` and `CHANGELOG.md` is English-only — no translation needed.

### Root Operations

- All shell commands run through `libsu` — do not use `Runtime.exec()`.
- Shell operations that block the UI must be wrapped in a coroutine with `Dispatchers.IO`.
- Always handle error output from shell commands and surface errors to the ViewModel.

---

## Commit Conventions

We follow [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>(<scope>): <short description>
```

| Type | When to use |
|---|---|
| `feat` | New feature |
| `fix` | Bug fix |
| `refactor` | Code restructure with no behavior change |
| `style` | UI or visual changes |
| `chore` | Build, dependencies, CI, config |
| `docs` | Documentation only |
| `perf` | Performance improvement |
| `test` | Adding or fixing tests |
| `release` | Version bump + changelog (maintainer only) |

**Examples:**
```
feat(storage): add multi-disk partition selection in migration hub
fix(ui): correct dark theme badge contrast on disk detail screen
refactor(root): extract bind-mount logic into dedicated MountEngine class
chore(ci): add workflow_dispatch trigger for manual release builds
docs: add FAQ and contributing guide
```

Keep the subject line under 72 characters. Write in the imperative mood ("add feature", not "added feature").

---

## Submitting a Pull Request

1. **Fork** the repository and create a branch from `main`:
   ```bash
   git checkout -b feat/your-feature-name
   ```

2. **Make your changes.** Keep PRs focused — one feature or fix per PR.

3. **Build and verify** the release APK compiles without errors:
   ```bash
   .\gradlew.bat assembleRelease
   ```

4. **Commit** using the conventional commit format above.

5. **Push** your branch and open a PR against `main`.

6. Fill out the PR description:
   - What does this change do?
   - Why is it needed?
   - How was it tested?
   - Screenshots (if UI changes).

---

## Issue Reporting

When filing a bug report, please include:

- **MountX version** (visible in About screen)
- **Android version** and device model
- **Root framework** (Magisk / KernelSU / APatch) and version
- **Steps to reproduce** the issue
- **Expected vs. actual behavior**
- **Log output** from the Logs tab (copy and paste or attach as a file)

Feature requests are welcome. Please describe the use case clearly — "why" matters as much as "what".

---

## What We Accept

✅ Bug fixes with clear reproduction steps  
✅ Performance improvements with measurable benchmarks  
✅ New game-specific configurations or mount mode presets  
✅ UI improvements that maintain Material Design 3 consistency  
✅ Documentation improvements  
✅ New language translations (must cover all existing strings)  

## What We Don't Accept

❌ Features that require breaking changes to the mount engine without prior discussion  
❌ UI changes that deviate from the established theme or design tokens  
❌ New third-party dependencies without justification  
❌ PRs that only compile in debug mode  
❌ Hardcoded credentials, keys, or device-specific paths  
❌ Changelog or version bump commits (these are managed by the maintainer at release time)  

---

## License

By contributing, you agree that your contributions will be licensed under the [MIT License](../LICENSE).

---

**Documentation Navigation:**
[Home](../README.md#documentation) &bull; [Installation Guide](INSTALL.md) &bull; [User Guide](USAGE.md) &bull; [Architecture & Internals](ARCHITECTURE.md) &bull; [FAQ & Troubleshooting](FAQ.md) &bull; [Contributing Guide](CONTRIBUTING.md)
