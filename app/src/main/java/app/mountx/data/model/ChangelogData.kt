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
        // ── v2.2.41 (Latest) ──
        ChangelogRelease(
            version = "v2.2.41",
            releaseDate = "21 Sep 2026",
            summary = "Siluet Murni Ikon Emblem, Sistem Changelog Standar 5-Kategori & Tombol Donasi Brand",
            isLatest = true,
            categories = listOf(
                CategoryChange(
                    category = ChangeCategoryType.UI_UX,
                    features = listOf(
                        FeatureChange(
                            title = "Siluet Murni Ikon Emblem Transparan",
                            details = listOf(
                                "Menghapus bingkai luar squircle, garis tepi tipis, dan halo putih di sekeliling SD card.",
                                "Siluet SD card murni menyatu sempurna di dalam wadah Surface Light & Dark mode."
                            )
                        ),
                        FeatureChange(
                            title = "Sistem Changelog 5-Kategori Terstruktur",
                            details = listOf(
                                "Format rilis standar: Added, Improved, Fixed, UI/UX, dan System.",
                                "Hierarki pohon (tree branching) rapi dengan aksen warna kategori semantik.",
                                "Tombol ekspansi animasi (Lihat Selengkapnya) untuk kenyamanan membaca."
                            )
                        ),
                        FeatureChange(
                            title = "Tombol Dukungan Brand Resmi",
                            details = listOf(
                                "Pembaruan tautan: Ko-fi, Saweria, dan PayPal.",
                                "Ikon vektor resmi dan warna identik platform bergaya Soft Tonal Buttons."
                            )
                        )
                    )
                ),
                CategoryChange(
                    category = ChangeCategoryType.FIXED,
                    features = listOf(
                        FeatureChange(
                            title = "Eliminasi Residu Garis Tepi",
                            details = listOf(
                                "Menghilangkan seluruh garis artefak di seluruh sisi luar kanvas logo.",
                                "Kanvas transparan 1024x1024 terpusat simetris tanpa goresan."
                            )
                        )
                    )
                ),
                CategoryChange(
                    category = ChangeCategoryType.SYSTEM,
                    features = listOf(
                        FeatureChange(
                            title = "Arsitektur Model Data Changelog",
                            details = listOf(
                                "Memisahkan riwayat pembaruan ke dalam model modular ChangelogData.",
                                "Mendukung skalabilitas riwayat rilis tanpa membebani logika UI."
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
            summary = "Kalibrasi Palet Deep Earthy Light Mode & Perbaikan Kontras Komponen",
            isLatest = false,
            categories = listOf(
                CategoryChange(
                    category = ChangeCategoryType.UI_UX,
                    features = listOf(
                        FeatureChange(
                            title = "Palet Warna Deep Earthy Anti-Silau",
                            details = listOf(
                                "Menurunkan saturasi warna neon terang menjadi Forest Green, Warm Amber, Slate Cyan, dan Terracotta Red.",
                                "Wadah badge dan telemetry menggunakan transparansi lembut (8–12% alpha) yang sejuk di mata.",
                                "Preservasi palet Cyber Neon futuristik saat beralih ke Dark Mode."
                            )
                        ),
                        FeatureChange(
                            title = "Penyempurnaan Switch & Badges",
                            details = listOf(
                                "Track switch aktif menggunakan Forest Green lembut tanpa silau pada thumb putih.",
                                "Batas kartu aplikasi ter-mount menggunakan nuansa hijau alami."
                            )
                        )
                    )
                ),
                CategoryChange(
                    category = ChangeCategoryType.FIXED,
                    features = listOf(
                        FeatureChange(
                            title = "Perbaikan Garis Sisi Kiri Ikon",
                            details = listOf(
                                "Menghapus fragmen garis 4px yang terisolasi di sisi kiri logo raster."
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
            summary = "Tema Soft Warm Sandstone Light Mode, Audit Sistem & Dialog Adaptif",
            isLatest = false,
            categories = listOf(
                CategoryChange(
                    category = ChangeCategoryType.UI_UX,
                    features = listOf(
                        FeatureChange(
                            title = "Desain Sandstone Light Mode",
                            details = listOf(
                                "Latar belakang Sandstone (#F2EFE9) dan kartu permukaan (#FAF8F5) dengan border 1.dp solid.",
                                "Seluruh dialog konfirmasi, hapus, migrasi, dan lisensi menggunakan wadah adaptif kontras tinggi."
                            )
                        ),
                        FeatureChange(
                            title = "Pembersihan Istilah Aplikasi",
                            details = listOf(
                                "Menstandarisasi seluruh label antarmuka dari 'Games' menjadi 'Apps' / 'Aplikasi'."
                            )
                        )
                    )
                ),
                CategoryChange(
                    category = ChangeCategoryType.SYSTEM,
                    features = listOf(
                        FeatureChange(
                            title = "Mitigasi Race Condition & Canary Verification",
                            details = listOf(
                                "Canary verification memvalidasi integritas titik kait data, obb, dan custom mountpoints.",
                                "Watchdog screen-on dilengkapi throttle 30 detik untuk mencegah remount loop.",
                                "Pre-flight reserve check memastikan minimal 1 GB cadangan internal sebelum pemulihan."
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
            summary = "Sistem Teardown Penghapusan Aplikasi, Migrasi Bersih Apps & Mutex Root",
            isLatest = false,
            categories = listOf(
                CategoryChange(
                    category = ChangeCategoryType.ADDED,
                    features = listOf(
                        FeatureChange(
                            title = "Teardown Penghapusan Aplikasi",
                            details = listOf(
                                "Tombol hapus di header detail aplikasi dan gestur tekan-lama pada daftar utama.",
                                "Pilihan dua aksi: Pulihkan ke Internal & Hapus, atau Lepas Kaitan Saja & Hapus.",
                                "Force-stop otomatis sebelum unmount dan regenerasi folder internal bersih."
                            )
                        ),
                        FeatureChange(
                            title = "Hub Portabilitas Konfigurasi di Pengaturan",
                            details = listOf(
                                "Pencadangan snapshot JSON lengkap mencakup preferensi, jalur disk, dan titik kait.",
                                "Dialog impor cerdas dengan pilihan Gabungkan (Merge) atau Ganti Penuh (Replace All)."
                            )
                        )
                    )
                ),
                CategoryChange(
                    category = ChangeCategoryType.SYSTEM,
                    features = listOf(
                        FeatureChange(
                            title = "Proteksi Mutex Root & Database v4",
                            details = listOf(
                                "RootExecutionMutex mencegah race condition antar-proses mount/unmount.",
                                "Migrasi Room DB v4 (MIGRATION_3_4) menyatukan tabel games ke apps."
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
            summary = "Isolasi Namespace Multi-User, Pre-Flight Hard-Lock & Scoping Media Presisi",
            isLatest = false,
            categories = listOf(
                CategoryChange(
                    category = ChangeCategoryType.SYSTEM,
                    features = listOf(
                        FeatureChange(
                            title = "Isolasi Namespace Multi-User Universal",
                            details = listOf(
                                "Mendukung Xiaomi Dual Apps (User 999) dan profil kerja (User 10+).",
                                "Kaitan bind-mount khusus diarahkan ke namespace target user masing-masing."
                            )
                        ),
                        FeatureChange(
                            title = "Pre-Flight Unmount Hard-Lock",
                            details = listOf(
                                "Memverifikasi pelepasan kaitan nyata (isMountpoint) sebelum pemindahan TO_INTERNAL.",
                                "Mencegah self-copy dan penolakan pembersihan direktori MicroSD jika target masih terikat."
                            )
                        )
                    )
                ),
                CategoryChange(
                    category = ChangeCategoryType.IMPROVED,
                    features = listOf(
                        FeatureChange(
                            title = "Guardrail Mount All & Scoping Media",
                            details = listOf(
                                "Tombol Mount All otomatis melewati aplikasi berstatus Need Migration.",
                                "Menghapus .nomedia agresif pada root MountX agar galeri sistem tetap terindeks."
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
            summary = "Smart Migration Guardrail & Rekonsiliasi Ground Truth Mount",
            isLatest = false,
            categories = listOf(
                CategoryChange(
                    category = ChangeCategoryType.SYSTEM,
                    features = listOf(
                        FeatureChange(
                            title = "Anti-Occlusion Hazard Proteksi",
                            details = listOf(
                                "Menolak bind-mount jika direktori MicroSD masih kosong berapapun ukuran data internal.",
                                "Deteksi otomatis status NEED_MIGRATION pada aplikasi unmounted dengan data internal."
                            )
                        ),
                        FeatureChange(
                            title = "Rekonsiliasi Ground Truth Linux Mounts",
                            details = listOf(
                                "Sinkronisasi status mount langsung dari kernel /proc/mounts.",
                                "Pemisahan callback onMount dan onUnmount agar transisi status stabil."
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
            summary = "Root Picker Guardrail, Pipeline Multi-Target & Space Guard",
            isLatest = false,
            categories = listOf(
                CategoryChange(
                    category = ChangeCategoryType.SYSTEM,
                    features = listOf(
                        FeatureChange(
                            title = "Pipeline Multi-Target Modul Root",
                            details = listOf(
                                "Parser POSIX membaca mountpoints.conf tanpa dependensi eksternal.",
                                "Kaitan multi-namespace Android menjamin akses berkas setelah reboot."
                            )
                        ),
                        FeatureChange(
                            title = "Pre-Flight Space Guard",
                            details = listOf(
                                "Pemeriksaan sisa ruang dengan margin aman maxOf(500MB, 5% data).",
                                "Penyalinan dotfiles (.config, .save) utuh tanpa kehilangan berkas."
                            )
                        )
                    )
                ),
                CategoryChange(
                    category = ChangeCategoryType.UI_UX,
                    features = listOf(
                        FeatureChange(
                            title = "Root Picker Guardrail",
                            details = listOf(
                                "Memblokir pemilihan folder kernel virtual (/dev, /proc, /sys, /apex).",
                                "Dialog risiko sistem kritis untuk folder root inti."
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
            summary = "Sinkronisasi 2 Arah Modul Root, Restrukturisasi MicroSD & File Explorer",
            isLatest = false,
            categories = listOf(
                CategoryChange(
                    category = ChangeCategoryType.ADDED,
                    features = listOf(
                        FeatureChange(
                            title = "Penjelajah Berkas Root MT-Manager Style",
                            details = listOf(
                                "Bottom sheet penjelajah direktori root dengan breadcrumb dan pintasan cepat.",
                                "Pemilihan folder kustom presisi dengan kalkulasi ukuran nyata."
                            )
                        ),
                        FeatureChange(
                            title = "Restrukturisasi Multi-Pola MicroSD",
                            details = listOf(
                                "Mendeteksi folder data di MountX/Android/, legacy Android/, dan folder non-standar.",
                                "Restrukturisasi atomik transparan dengan penyesuaian izin dan konteks SELinux."
                            )
                        )
                    )
                ),
                CategoryChange(
                    category = ChangeCategoryType.SYSTEM,
                    features = listOf(
                        FeatureChange(
                            title = "Sinkronisasi Senyap Modul Root",
                            details = listOf(
                                "Pembaruan otomatis service.sh dan module.prop tanpa perlu reboot.",
                                "Penanganan status DISK_DETACHED saat MicroSD dilepas."
                            )
                        )
                    )
                )
            )
        ),

        // ── v2.2.30 s/d v2.2.33 ──
        ChangelogRelease(
            version = "v2.2.33",
            releaseDate = "21 Sep 2026",
            summary = "Pembersihan Multi-Jalur MicroSD & Eliminasi Opsi Internal Semu",
            isLatest = false,
            categories = listOf(
                CategoryChange(
                    category = ChangeCategoryType.FIXED,
                    features = listOf(
                        FeatureChange(
                            title = "Penghapusan Kategori Data Tuntas",
                            details = listOf(
                                "Penghapusan menargetkan seluruh jalur fisik MicroSD modern dan kustom.",
                                "Kategori ter-mount mengalokasikan 0 B di internal untuk mencegah hapus semu."
                            )
                        )
                    )
                ),
                CategoryChange(
                    category = ChangeCategoryType.SYSTEM,
                    features = listOf(
                        FeatureChange(
                            title = "Rekonsiliasi Izin Direktori",
                            details = listOf(
                                "Direktori internal lokal selalu diregenerasi dengan UID paket dan chmod 775."
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
            summary = "Proteksi Zero-Loss, Portabilitas Konfigurasi v2 & Adaptive Launcher Icon",
            isLatest = false,
            categories = listOf(
                CategoryChange(
                    category = ChangeCategoryType.SYSTEM,
                    features = listOf(
                        FeatureChange(
                            title = "Proteksi Konflik Data Zero-Loss",
                            details = listOf(
                                "Menolak penimpaan destruktif jika folder sumber kosong sementara tujuan berisi data riil.",
                                "Self-conflict guard mendeteksi data yang telah berada di MicroSD tujuan."
                            )
                        )
                    )
                ),
                CategoryChange(
                    category = ChangeCategoryType.UI_UX,
                    features = listOf(
                        FeatureChange(
                            title = "Ikon Launcher Adaptif",
                            details = listOf(
                                "Ikon launcher mengikuti masking sistem Android (lingkaran, squircle, pebble)."
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
            summary = "Progres Transfer Kernel Real-Time & Safe Zone Ikon Launcher",
            isLatest = false,
            categories = listOf(
                CategoryChange(
                    category = ChangeCategoryType.IMPROVED,
                    features = listOf(
                        FeatureChange(
                            title = "Progres Transfer Kernel Real-Time",
                            details = listOf(
                                "Pemantauan byte I/O Linux kernel (/proc/\$PID/io) dengan interval 300ms.",
                                "Menampilkan kecepatan transfer riil (MB/s) dan estimasi waktu sisa (ETA)."
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
            summary = "Direktori Terpusat MountX, Indikator Akses & Changelog Markdown",
            isLatest = false,
            categories = listOf(
                CategoryChange(
                    category = ChangeCategoryType.ADDED,
                    features = listOf(
                        FeatureChange(
                            title = "Migrasi Direktori Terpusat",
                            details = listOf(
                                "Mengalihkan data legacy ke folder terpusat MountX/Android/ secara aman.",
                                "Pembersihan folder legacy kosong secara otomatis setelah verifikasi."
                            )
                        )
                    )
                ),
                CategoryChange(
                    category = ChangeCategoryType.UI_UX,
                    features = listOf(
                        FeatureChange(
                            title = "Indikator Centang Hijau Izin",
                            details = listOf(
                                "Tampilan centang hijau dan badge Aktif saat seluruh izin sistem terpenuhi."
                            )
                        )
                    )
                )
            )
        ),

        // ── v2.2.20 s/d v2.2.26 ──
        ChangelogRelease(
            version = "v2.2.26",
            releaseDate = "20 Sep 2026",
            summary = "Poles Dialog Modern, Kotak Input Kontras Tinggi & Chip Saran",
            isLatest = false,
            categories = listOf(
                CategoryChange(
                    category = ChangeCategoryType.UI_UX,
                    features = listOf(
                        FeatureChange(
                            title = "Desain Dialog & Input Kontras",
                            details = listOf(
                                "Seluruh dialog menggunakan radius sudut 24dp modern.",
                                "Field input direktori kustom dengan border tegas dan chip rekomendasi 1-ketuk."
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
            summary = "Penyempurnaan Kelola Penyimpanan 8 Layar & Stepper Progres",
            isLatest = false,
            categories = listOf(
                CategoryChange(
                    category = ChangeCategoryType.ADDED,
                    features = listOf(
                        FeatureChange(
                            title = "Arsitektur Kelola Penyimpanan 8 Layar",
                            details = listOf(
                                "Alur bertahap: Overview, Detail Kategori, Pilih Data, Pilih Penyimpanan, dan Progres.",
                                "Visual stepper 5 tahap transparan untuk kaitan dan transfer berkas."
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
            summary = "Pratinjau Aplikasi Aktif Dashboard, Ikon Emblem Adaptif & Telemetri Langsung",
            isLatest = false,
            categories = listOf(
                CategoryChange(
                    category = ChangeCategoryType.ADDED,
                    features = listOf(
                        FeatureChange(
                            title = "Inisialisasi Fitur Dasar MountX",
                            details = listOf(
                                "Pratinjau aplikasi aktif langsung dari kartu dashboard utama.",
                                "Dukungan pengaitan bind-mount ruang penyimpanan internal ke MicroSD eksternal."
                            )
                        )
                    )
                )
            )
        )
    )
}
