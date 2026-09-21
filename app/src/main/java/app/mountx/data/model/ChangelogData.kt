package app.mountx.data.model

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import app.mountx.ui.theme.ElectricIndigo
import app.mountx.ui.theme.ElectricIndigoLight
import app.mountx.ui.theme.adaptiveAmber
import app.mountx.ui.theme.adaptiveCrimson
import app.mountx.ui.theme.adaptiveCyan
import app.mountx.ui.theme.adaptiveEmerald
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.luminance

enum class ChangeCategoryType(
    val icon: String,
    val displayName: String
) {
    ADDED("✨", "Added"),
    IMPROVED("🚀", "Improved"),
    FIXED("🐛", "Fixed"),
    UI_UX("🎨", "UI/UX"),
    SYSTEM("⚙️", "System");

    @Composable
    @ReadOnlyComposable
    fun accentColor(): Color {
        val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
        return when (this) {
            ADDED -> adaptiveCyan()
            IMPROVED -> adaptiveEmerald()
            FIXED -> adaptiveCrimson()
            UI_UX -> if (isDark) ElectricIndigo else ElectricIndigoLight
            SYSTEM -> adaptiveAmber()
        }
    }
}

data class FeatureChange(
    val title: String,
    val details: List<String>
)

data class CategoryChange(
    val category: ChangeCategoryType,
    val features: List<FeatureChange>
)

data class ChangelogRelease(
    val version: String,
    val releaseDate: String,
    val summary: String,
    val isLatest: Boolean = false,
    val categories: List<CategoryChange>
)

object ChangelogHistory {
    val releases: List<ChangelogRelease> = listOf(
        // ── v2.2.44 (Latest) ──
        ChangelogRelease(
            version = "v2.2.44",
            releaseDate = "22 Sep 2026",
            summary = "Light Theme Aesthetic Overhaul: Zero Neon Glare, Elimination of Pitch-Black Containers in MT-Manager File Picker & Harmonized Storage Badges",
            isLatest = true,
            categories = listOf(
                CategoryChange(
                    category = ChangeCategoryType.UI_UX,
                    features = listOf(
                        FeatureChange(
                            title = "MT-Manager Style File Picker Light Mode Harmonization",
                            details = listOf(
                                "Eliminated pitch-black container leaks in RootDirectoryPickerSheet bottom action bar, path container box, and quick access chips when Light theme is active.",
                                "Switched color resolution from isSystemInDarkTheme() to dynamic surface luminance detection, resolving inverted theme bugs when device system night mode is active.",
                                "Refactored High Risk directory warning dialog to use semantic Warm Crimson tokens."
                            )
                        ),
                        FeatureChange(
                            title = "Storage Screen & Sub-sections Neon Glare Elimination",
                            details = listOf(
                                "Replaced harsh neon cyan, emerald, and crimson colors across StorageScreen with soft, high-contrast semantic tokens (BadgeMounted, WarmCrimson, SlateCyanLight).",
                                "Refactored interactive partition slider bar and draggable handle pill to render Soft Warm Sandstone surfaces and subtle slate borders in Light mode.",
                                "Updated partition cards, recommended badges, filesystem selector chips, and destructive repartition warnings to gracefully adapt to both Light and Dark themes."
                            )
                        ),
                        FeatureChange(
                            title = "Universal Operation Overlay & Sheet Polishing",
                            details = listOf(
                                "Overhauled OperationProgressOverlay progress indicators, technical log views, and action buttons for accessible contrast and zero black container bleed.",
                                "Refactored DiskToolsBottomSheet, PartitionToolsBottomSheet, DiskDetailView, and BackupRestoreScreen to eliminate harsh neon badges and ensure cohesive Sandstone aesthetics."
                            )
                        )
                    )
                )
            )
        ),

        // ── v2.2.43 ──
        ChangelogRelease(
            version = "v2.2.43",
            releaseDate = "22 Sep 2026",
            summary = "Soft Warm Sandstone & Earthy Mineral Visual Refactor, Tight Emblem Scaling, Compact Typography & Responsive Changelog Modal",
            isLatest = false,
            categories = listOf(
                CategoryChange(
                    category = ChangeCategoryType.UI_UX,
                    features = listOf(
                        FeatureChange(
                            title = "Soft Warm Sandstone & Earthy Mineral Refactor",
                            details = listOf(
                                "Eliminated cyan and neon gradients across Light Mode in favor of solid Electric Indigo, Forest Emerald, Warm Amber, and Warm Crimson.",
                                "Refactored input fields and directory browser to use Sandstone card surfaces (#FAF8F5) with neutral borders (#D6D3CD).",
                                "Standardized secondary action buttons with Outlined Neutral and destructive operations with Outlined Crimson."
                            )
                        ),
                        FeatureChange(
                            title = "Tight In-App Emblem Proportions",
                            details = listOf(
                                "Re-rendered master in-app emblem with tight cropping to eliminate 32% empty transparent padding.",
                                "Enlarged emblem display across TopBar and About Screen to 28dp and 54dp for crisp visual prominence."
                            )
                        ),
                        FeatureChange(
                            title = "Responsive Height Changelog Modal",
                            details = listOf(
                                "Replaced fixed modal height with dynamic wrap-content sizing (heightIn max 680dp) to prevent empty vertical gaps on initial releases.",
                                "Balanced typography hierarchy with 11sp bold all-caps category chips, 12sp semi-bold titles, and 11.5sp descriptions."
                            )
                        ),
                        FeatureChange(
                            title = "Search Bar Spacing & Logs Streamlining",
                            details = listOf(
                                "Added 10dp spacer between header and search bar in Add App Sheet.",
                                "Removed redundant auto-refresh toggle card in LogsScreen, setting auto-refresh as the seamless default."
                            )
                        )
                    )
                ),
                CategoryChange(
                    category = ChangeCategoryType.SYSTEM,
                    features = listOf(
                        FeatureChange(
                            title = "English Changelog Standardization",
                            details = listOf(
                                "Fully translated the complete changelog history to English and established an English-only documentation directive in AGENTS.md.",
                                "Bumped application version to v2.2.43 across build configuration and root module properties."
                            )
                        )
                    )
                )
            )
        ),

        // ── v2.2.42 ──
        ChangelogRelease(
            version = "v2.2.42",
            releaseDate = "21 Sep 2026",
            summary = "Master Icon Refresh, Compact Changelog Redesign & Earthy Mineral Terminal",
            isLatest = false,
            categories = listOf(
                CategoryChange(
                    category = ChangeCategoryType.UI_UX,
                    features = listOf(
                        FeatureChange(
                            title = "Master Transparent App Icon",
                            details = listOf(
                                "Regenerated all launcher assets, emblem, and mipmaps from the ultra-high-resolution master.",
                                "Eliminated border artifacts to deliver a clean crystal silhouette matching the design theme."
                            )
                        ),
                        FeatureChange(
                            title = "Compact Changelog Dialog Redesign",
                            details = listOf(
                                "Streamlined layout grouped directly by category without nested cards or excessive hierarchy.",
                                "Moved close button to top-right corner (X) and added incremental pagination loading 2 versions per click."
                            )
                        ),
                        FeatureChange(
                            title = "Earthy Mineral Terminal Console",
                            details = listOf(
                                "Deep Stone container (#1C1917) with eye-friendly semantic token highlighting.",
                                "Dynamic header subtitle displaying live active entry counts."
                            )
                        ),
                        FeatureChange(
                            title = "Sandstone Navbar Color Harmonization",
                            details = listOf(
                                "Navbar dock background color harmonized with Sandstone card surface (#FAF8F5).",
                                "Soft, eye-friendly navigation color transitions for light mode."
                            )
                        )
                    )
                ),
                CategoryChange(
                    category = ChangeCategoryType.SYSTEM,
                    features = listOf(
                        FeatureChange(
                            title = "Tagline & Version Modernization",
                            details = listOf(
                                "Updated application subtitle to 'Seamless storage redirection & external bind engine'.",
                                "Bumped application version to v2.2.42 in build gradle and root module."
                            )
                        )
                    )
                )
            )
        ),

        // ── v2.2.41 ──
        ChangelogRelease(
            version = "v2.2.41",
            releaseDate = "21 Sep 2026",
            summary = "Pure Silhouette Emblem, Standard 5-Category Changelog & Platform Brand Support",
            isLatest = false,
            categories = listOf(
                CategoryChange(
                    category = ChangeCategoryType.UI_UX,
                    features = listOf(
                        FeatureChange(
                            title = "Pure Silhouette Transparent Emblem",
                            details = listOf(
                                "Removed outer squircle frame, hairline borders, and white halos around the SD card icon.",
                                "Pure SD card silhouette blends seamlessly inside Light and Dark mode surfaces."
                            )
                        ),
                        FeatureChange(
                            title = "Structured 5-Category Changelog System",
                            details = listOf(
                                "Standardized release categories: Added, Improved, Fixed, UI/UX, and System.",
                                "Clean categorized view with semantic accent colors."
                            )
                        ),
                        FeatureChange(
                            title = "Official Brand Support Buttons",
                            details = listOf(
                                "Updated support links for Ko-fi, Saweria, and PayPal.",
                                "Official vector icons and brand colors styled as Soft Tonal Buttons."
                            )
                        )
                    )
                ),
                CategoryChange(
                    category = ChangeCategoryType.FIXED,
                    features = listOf(
                        FeatureChange(
                            title = "Canvas Border Artifact Removal",
                            details = listOf(
                                "Eliminated residual boundary lines across outer canvas edges.",
                                "Symmetrically centered 1024x1024 transparent canvas without scratch lines."
                            )
                        )
                    )
                ),
                CategoryChange(
                    category = ChangeCategoryType.SYSTEM,
                    features = listOf(
                        FeatureChange(
                            title = "Changelog Data Model Architecture",
                            details = listOf(
                                "Decoupled update history into modular ChangelogData model.",
                                "Supports scalable release history without bloating UI components."
                            )
                        )
                    )
                )
            )
        ),

        // ── v2.2.40 ──
        ChangelogRelease(
            version = "v2.2.40",
            releaseDate = "21 Sep 2026",
            summary = "Deep Earthy Light Mode Calibration & Component Contrast Fixes",
            isLatest = false,
            categories = listOf(
                CategoryChange(
                    category = ChangeCategoryType.UI_UX,
                    features = listOf(
                        FeatureChange(
                            title = "Anti-Glare Deep Earthy Color Palette",
                            details = listOf(
                                "Desaturated bright neon tones into Forest Green, Warm Amber, Slate Cyan, and Terracotta Red.",
                                "Badges and telemetry containers use gentle transparency (8–12% alpha) that is easy on the eyes.",
                                "Preserved futuristic Cyber Neon palette when switching to Dark Mode."
                            )
                        ),
                        FeatureChange(
                            title = "Switch & Badge Polish",
                            details = listOf(
                                "Active switch tracks use soft Forest Green without glaring against the white thumb.",
                                "Mounted application card borders calibrated to natural green tones."
                            )
                        )
                    )
                ),
                CategoryChange(
                    category = ChangeCategoryType.FIXED,
                    features = listOf(
                        FeatureChange(
                            title = "Left Icon Border Artifact Fix",
                            details = listOf(
                                "Removed isolated 4px border fragment on the left side of the raster icon."
                            )
                        )
                    )
                )
            )
        ),

        // ── v2.2.39 ──
        ChangelogRelease(
            version = "v2.2.39",
            releaseDate = "21 Sep 2026",
            summary = "Soft Warm Sandstone Light Mode, System Audit & Adaptive Dialogs",
            isLatest = false,
            categories = listOf(
                CategoryChange(
                    category = ChangeCategoryType.UI_UX,
                    features = listOf(
                        FeatureChange(
                            title = "Sandstone Light Mode Design",
                            details = listOf(
                                "Sandstone background (#F2EFE9) and card surfaces (#FAF8F5) with solid 1.dp borders.",
                                "All confirmation, delete, migration, and license dialogs use high-contrast adaptive containers."
                            )
                        ),
                        FeatureChange(
                            title = "App Terminology Standardization",
                            details = listOf(
                                "Standardized all UI labels from 'Games' to 'Apps' across the entire interface."
                            )
                        )
                    )
                ),
                CategoryChange(
                    category = ChangeCategoryType.SYSTEM,
                    features = listOf(
                        FeatureChange(
                            title = "Race Condition Mitigation & Canary Verification",
                            details = listOf(
                                "Canary verification validates data, obb, and custom mount point integrity.",
                                "Screen-on watchdog equipped with 30-second throttle to prevent remount loops.",
                                "Pre-flight reserve check guarantees at least 1 GB internal storage buffer before restoration."
                            )
                        )
                    )
                )
            )
        ),

        // ── v2.2.38 ──
        ChangelogRelease(
            version = "v2.2.38",
            releaseDate = "21 Sep 2026",
            summary = "App Deletion Teardown System, Clean Apps Migration & Root Mutex",
            isLatest = false,
            categories = listOf(
                CategoryChange(
                    category = ChangeCategoryType.ADDED,
                    features = listOf(
                        FeatureChange(
                            title = "App Deletion Teardown",
                            details = listOf(
                                "Delete button in app detail header and long-press gesture on main app list.",
                                "Two action options: Restore to Internal & Delete, or Unmount Only & Delete.",
                                "Automatic force-stop before unmount and clean internal directory regeneration."
                            )
                        ),
                        FeatureChange(
                            title = "Settings Configuration Portability Hub",
                            details = listOf(
                                "Full JSON snapshot backup covering preferences, disk paths, and mount points.",
                                "Smart import dialog with options to Merge or Replace All."
                            )
                        )
                    )
                ),
                CategoryChange(
                    category = ChangeCategoryType.SYSTEM,
                    features = listOf(
                        FeatureChange(
                            title = "Root Mutex Protection & Database v4",
                            details = listOf(
                                "RootExecutionMutex prevents race conditions between concurrent mount/unmount operations.",
                                "Room DB v4 migration (MIGRATION_3_4) unifies games table into apps."
                            )
                        )
                    )
                )
            )
        ),

        // ── v2.2.37 ──
        ChangelogRelease(
            version = "v2.2.37",
            releaseDate = "21 Sep 2026",
            summary = "Multi-User Namespace Isolation, Pre-Flight Hard-Lock & Scoped Media Handling",
            isLatest = false,
            categories = listOf(
                CategoryChange(
                    category = ChangeCategoryType.SYSTEM,
                    features = listOf(
                        FeatureChange(
                            title = "Universal Multi-User Namespace Isolation",
                            details = listOf(
                                "Added support for Xiaomi Dual Apps (User 999) and work profiles (User 10+).",
                                "Dedicated bind-mount targets directed into each user's specific runtime namespace."
                            )
                        ),
                        FeatureChange(
                            title = "Pre-Flight Unmount Hard-Lock",
                            details = listOf(
                                "Verifies physical unmount (isMountpoint) prior to TO_INTERNAL data transfer.",
                                "Prevents self-copy hazards and MicroSD cleanup rejection while targets remain bound."
                            )
                        )
                    )
                ),
                CategoryChange(
                    category = ChangeCategoryType.IMPROVED,
                    features = listOf(
                        FeatureChange(
                            title = "Mount All Guardrail & Media Scoping",
                            details = listOf(
                                "Mount All button automatically bypasses apps with Need Migration status.",
                                "Removed aggressive .nomedia at MountX root so system media gallery indexes properly."
                            )
                        )
                    )
                )
            )
        ),

        // ── v2.2.36 ──
        ChangelogRelease(
            version = "v2.2.36",
            releaseDate = "21 Sep 2026",
            summary = "Smart Migration Guardrail & Linux Mounts Ground Truth Reconciliation",
            isLatest = false,
            categories = listOf(
                CategoryChange(
                    category = ChangeCategoryType.SYSTEM,
                    features = listOf(
                        FeatureChange(
                            title = "Anti-Occlusion Hazard Protection",
                            details = listOf(
                                "Rejects bind-mount if MicroSD directory is empty regardless of internal data size.",
                                "Automatic NEED_MIGRATION status detection for unmounted apps with internal data."
                            )
                        ),
                        FeatureChange(
                            title = "Linux Mounts Ground Truth Reconciliation",
                            details = listOf(
                                "Synchronizes mount status directly from kernel /proc/mounts.",
                                "Separated onMount and onUnmount callbacks for rock-solid status transitions."
                            )
                        )
                    )
                )
            )
        ),

        // ── v2.2.35 ──
        ChangelogRelease(
            version = "v2.2.35",
            releaseDate = "21 Sep 2026",
            summary = "Root Picker Guardrail, Multi-Target Pipeline & Space Guard",
            isLatest = false,
            categories = listOf(
                CategoryChange(
                    category = ChangeCategoryType.SYSTEM,
                    features = listOf(
                        FeatureChange(
                            title = "Root Module Multi-Target Pipeline",
                            details = listOf(
                                "POSIX parser reads mountpoints.conf without external dependencies.",
                                "Android multi-namespace binding guarantees file access following system reboot."
                            )
                        ),
                        FeatureChange(
                            title = "Pre-Flight Space Guard",
                            details = listOf(
                                "Remaining space check with safety margin of maxOf(500MB, 5% data).",
                                "Preserves dotfiles (.config, .save) intact during migration without file loss."
                            )
                        )
                    )
                ),
                CategoryChange(
                    category = ChangeCategoryType.UI_UX,
                    features = listOf(
                        FeatureChange(
                            title = "Root Picker Guardrails",
                            details = listOf(
                                "Blocks selection of virtual kernel directories (/dev, /proc, /sys, /apex).",
                                "Critical system risk dialog warning for core root filesystem paths."
                            )
                        )
                    )
                )
            )
        ),

        // ── v2.2.34 ──
        ChangelogRelease(
            version = "v2.2.34",
            releaseDate = "21 Sep 2026",
            summary = "2-Way Root Module Sync, MicroSD Restructuring & Root File Explorer",
            isLatest = false,
            categories = listOf(
                CategoryChange(
                    category = ChangeCategoryType.ADDED,
                    features = listOf(
                        FeatureChange(
                            title = "MT-Manager Style Root File Explorer",
                            details = listOf(
                                "Root directory bottom sheet explorer with breadcrumbs and quick shortcuts.",
                                "Precise custom folder selection with real-time size calculations."
                            )
                        ),
                        FeatureChange(
                            title = "Multi-Pattern MicroSD Restructuring",
                            details = listOf(
                                "Detects app folders in MountX/Android/, legacy Android/, and non-standard paths.",
                                "Transparent atomic restructuring with automatic permission and SELinux context fixes."
                            )
                        )
                    )
                ),
                CategoryChange(
                    category = ChangeCategoryType.SYSTEM,
                    features = listOf(
                        FeatureChange(
                            title = "Silent Root Module Sync",
                            details = listOf(
                                "Automatic zero-reboot updates for service.sh and module.prop.",
                                "Graceful DISK_DETACHED state handling when MicroSD is removed."
                            )
                        )
                    )
                )
            )
        ),

        // ── v2.2.33 ──
        ChangelogRelease(
            version = "v2.2.33",
            releaseDate = "21 Sep 2026",
            summary = "MicroSD Multi-Path Cleanup & Elimination of Phantom Internal Data",
            isLatest = false,
            categories = listOf(
                CategoryChange(
                    category = ChangeCategoryType.FIXED,
                    features = listOf(
                        FeatureChange(
                            title = "Thorough Category Data Deletion",
                            details = listOf(
                                "Targeted deletion across all modern and custom physical MicroSD paths.",
                                "Mounted category allocates 0 B in internal storage calculation to prevent phantom deletions."
                            )
                        )
                    )
                ),
                CategoryChange(
                    category = ChangeCategoryType.SYSTEM,
                    features = listOf(
                        FeatureChange(
                            title = "Directory Permission Reconciliation",
                            details = listOf(
                                "Local internal directories are always regenerated with package UID and chmod 775."
                            )
                        )
                    )
                )
            )
        ),

        // ── v2.2.29 ──
        ChangelogRelease(
            version = "v2.2.29",
            releaseDate = "20 Sep 2026",
            summary = "Zero-Loss Data Protection, Config Portability v2 & Adaptive Launcher Icon",
            isLatest = false,
            categories = listOf(
                CategoryChange(
                    category = ChangeCategoryType.SYSTEM,
                    features = listOf(
                        FeatureChange(
                            title = "Zero-Loss Conflict Protection",
                            details = listOf(
                                "Rejects destructive overwrite if source folder is empty while destination holds real data.",
                                "Self-conflict guard detects data that already resides on the target MicroSD."
                            )
                        )
                    )
                ),
                CategoryChange(
                    category = ChangeCategoryType.UI_UX,
                    features = listOf(
                        FeatureChange(
                            title = "Adaptive Launcher Icon",
                            details = listOf(
                                "Launcher icon conforms to Android system masking (circle, squircle, pebble)."
                            )
                        )
                    )
                )
            )
        ),

        // ── v2.2.28 ──
        ChangelogRelease(
            version = "v2.2.28",
            releaseDate = "20 Sep 2026",
            summary = "Real-Time Kernel Transfer Progress & Launcher Icon Safe Zone",
            isLatest = false,
            categories = listOf(
                CategoryChange(
                    category = ChangeCategoryType.IMPROVED,
                    features = listOf(
                        FeatureChange(
                            title = "Real-Time Kernel Transfer Progress",
                            details = listOf(
                                "Monitors Linux kernel I/O bytes (/proc/\$PID/io) at 300ms intervals.",
                                "Displays real transfer speed (MB/s) and estimated time of arrival (ETA)."
                            )
                        )
                    )
                )
            )
        ),

        // ── v2.2.27 ──
        ChangelogRelease(
            version = "v2.2.27",
            releaseDate = "20 Sep 2026",
            summary = "Centralized MountX Directory, Access Indicators & Markdown Changelog",
            isLatest = false,
            categories = listOf(
                CategoryChange(
                    category = ChangeCategoryType.ADDED,
                    features = listOf(
                        FeatureChange(
                            title = "Centralized Directory Migration",
                            details = listOf(
                                "Safely migrates legacy data into centralized MountX/Android/ storage structure.",
                                "Automatic empty legacy folder cleanup following successful verification."
                            )
                        )
                    )
                ),
                CategoryChange(
                    category = ChangeCategoryType.UI_UX,
                    features = listOf(
                        FeatureChange(
                            title = "Permission Green Checkmark Indicators",
                            details = listOf(
                                "Green checkmarks and Active badge display when all system permissions are satisfied."
                            )
                        )
                    )
                )
            )
        ),

        // ── v2.2.26 ──
        ChangelogRelease(
            version = "v2.2.26",
            releaseDate = "20 Sep 2026",
            summary = "Modern Dialog Polish, High-Contrast Inputs & Suggestion Chips",
            isLatest = false,
            categories = listOf(
                CategoryChange(
                    category = ChangeCategoryType.UI_UX,
                    features = listOf(
                        FeatureChange(
                            title = "Modern Dialogs & High-Contrast Inputs",
                            details = listOf(
                                "All dialogs styled with modern 24dp corner radius.",
                                "Custom directory input field with bold borders and 1-tap recommendation chips."
                            )
                        )
                    )
                )
            )
        ),

        // ── v2.2.15 ──
        ChangelogRelease(
            version = "v2.2.15",
            releaseDate = "18 Sep 2026",
            summary = "8-Screen Storage Management Revamp & Multi-Step Progress Stepper",
            isLatest = false,
            categories = listOf(
                CategoryChange(
                    category = ChangeCategoryType.ADDED,
                    features = listOf(
                        FeatureChange(
                            title = "8-Screen Storage Management Architecture",
                            details = listOf(
                                "Step-by-step workflow: Overview, Category Details, Select Data, Select Storage, and Progress.",
                                "Transparent 5-stage visual progress stepper for bind-mount operations and file transfers."
                            )
                        )
                    )
                )
            )
        ),

        // ── v2.2.11 ──
        ChangelogRelease(
            version = "v2.2.11",
            releaseDate = "16 Sep 2026",
            summary = "Active App Dashboard Preview, Adaptive Emblem Icon & Live Telemetry",
            isLatest = false,
            categories = listOf(
                CategoryChange(
                    category = ChangeCategoryType.ADDED,
                    features = listOf(
                        FeatureChange(
                            title = "MountX Core Feature Initialization",
                            details = listOf(
                                "Active app previews displayed directly on the main dashboard card.",
                                "Bind-mount redirection support linking internal application storage to external MicroSD."
                            )
                        )
                    )
                )
            )
        )
    )
}
