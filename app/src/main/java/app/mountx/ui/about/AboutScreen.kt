package app.mountx.ui.about

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.luminance
import app.mountx.BuildConfig
import app.mountx.R
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import app.mountx.ui.components.CompactScreenHeader
import app.mountx.ui.components.SectionHeader
import app.mountx.ui.theme.CyberEmerald
import app.mountx.ui.theme.EmeraldActive
import androidx.compose.ui.graphics.Brush

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    viewModel: AboutViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isChecking by viewModel.isChecking.collectAsState()
    val updateInfo by viewModel.updateInfo.collectAsState()
    val checkError by viewModel.checkError.collectAsState()

    var showChangelogDialog by remember { mutableStateOf(false) }
    var showLicenseDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CompactScreenHeader(
                title = stringResource(R.string.about_title),
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 2.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // App Header & Logo
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                    border = BorderStroke(
                        1.dp,
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        )
                    ),
                    modifier = Modifier.size(68.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_mountx_emblem),
                            contentDescription = stringResource(R.string.app_name),
                            modifier = Modifier.size(42.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.app_description),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            // Update Checker Card
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.about_software_updates),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                            if (isChecking) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.primary)
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        if (updateInfo != null) {
                            if (updateInfo!!.isUpdateAvailable) {
                                Text(
                                    text = stringResource(R.string.about_update_available, updateInfo!!.latestVersion),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(updateInfo!!.downloadUrl))
                                        context.startActivity(intent)
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(36.dp)
                                ) {
                                    Text(stringResource(R.string.about_download_update), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            } else {
                                val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
                                Text(
                                    text = stringResource(R.string.about_up_to_date),
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                    color = if (isDark) CyberEmerald else EmeraldActive
                                )
                            }
                        } else if (checkError != null) {
                            Text(
                                text = checkError!!,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        val infiniteTransition = rememberInfiniteTransition(label = "about_spin")
                        val spinAngle by infiniteTransition.animateFloat(
                            initialValue = 0f,
                            targetValue = 360f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(750, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart
                            ),
                            label = "about_spin_angle"
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { viewModel.checkForUpdate() },
                            enabled = !isChecking,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(16.dp)
                                    .graphicsLayer {
                                        if (isChecking) rotationZ = spinAngle
                                    }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.about_update_check), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Links & Resources
            item {
                SectionHeader(title = stringResource(R.string.about_resources))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column {
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.about_github)) },
                            supportingContent = { Text("https://github.com/Zy0x/MountX") },
                            trailingContent = { Icon(Icons.Default.OpenInBrowser, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                            shadowElevation = 0.dp
                        )
                        HorizontalDivider()
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.about_changelog)) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                            trailingContent = {
                                TextButton(onClick = { showChangelogDialog = true }) {
                                    Text("View")
                                }
                            }
                        )
                        HorizontalDivider()
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.about_license)) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                            trailingContent = {
                                TextButton(onClick = { showLicenseDialog = true }) {
                                    Text("MIT")
                                }
                            }
                        )
                    }
                }
            }

            // Support & Donations
            item {
                SectionHeader(title = stringResource(R.string.about_donate_title))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.about_donate_desc),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilledTonalButton(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://ko-fi.com/noir"))
                                    context.startActivity(intent)
                                },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(34.dp)
                            ) {
                                Text(stringResource(R.string.about_kofi), fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                            }
                            FilledTonalButton(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://saweria.co/noir"))
                                    context.startActivity(intent)
                                },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(34.dp)
                            ) {
                                Text(stringResource(R.string.about_saweria), fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                            }
                            FilledTonalButton(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://paypal.me/noir"))
                                    context.startActivity(intent)
                                },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(34.dp)
                            ) {
                                Text(stringResource(R.string.about_paypal), fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            // Credits
            item {
                Text(
                    text = stringResource(R.string.about_credits),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    // Changelog Dialog (GitHub Markdown Release Notes Style)
    if (showChangelogDialog) {
        AlertDialog(
            onDismissRequest = { showChangelogDialog = false },
            containerColor = Color(0xFF111726),
            shape = RoundedCornerShape(24.dp),
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF1E293B),
                        border = BorderStroke(1.dp, Color(0xFF334155))
                    ) {
                        Text(
                            text = "#",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color(0xFF818CF8),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        stringResource(R.string.about_changelog),
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold),
                        color = Color(0xFFF1F5F9)
                    )
                }
            },
            text = {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF0D1321),
                    border = BorderStroke(1.dp, Color(0xFF222F49)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 440.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // ── v2.2.37 (Latest) ──
                        GithubReleaseCard(
                            version = "v2.2.37",
                            releaseDate = "21 Sep 2026",
                            isLatest = true,
                            title = "5 Pilar Keandalan Arsitektur, Multi-User Isolation, Pre-Flight Unmount Hard-Lock & Scoping Media Presisi"
                        ) {
                            GithubSectionHeader("👥 Isolasi Namespace Multi-User Universal & Ekstraksi Jalur Dinamis")
                            GithubMarkdownBullet("• **Ekstraksi Jalur Relatif Universal**: Menggunakan pemotong regex multi-user `extractRelativePath` untuk mendukung penuh Xiaomi Dual Apps (User 999), profil kerja (User 10+), dan folder kustom tanpa merusak hierarki berkas.")
                            GithubMarkdownBullet("• **Isolasi Namespace Per-User**: Mengarahkan kaitan bind-mount khusus ke namespace target user bersangkutan (`getTargetNamespaces(userId)`) dengan formula matematis UID/GID sandbox resmi Android.")

                            GithubSectionHeader("🛡️ Pre-Flight Unmount Hard-Lock & Proteksi Zero Data Loss")
                            GithubMarkdownBullet("• **Pemeriksaan Kaitan Fail-Fast**: Memverifikasi pelepasan kaitan nyata (`isMountpoint`) sebelum pemindahan berkas `TO_INTERNAL`. Jika direktori internal masih terikat, operasi dibatalkan seketika demi mencegah *self-copy*.")
                            GithubMarkdownBullet("• **Kunci Pengaman Pembersihan MicroSD**: Menolak pembersihan direktori sumber MicroSD jika target internal terdeteksi masih berstatus mountpoint, menjamin data pengguna 100% aman.")

                            GithubSectionHeader("🚀 Guardrail Mount All Games & Mitigasi Error Palsu")
                            GithubMarkdownBullet("• **Pengecualian Status Need Migration**: Tombol 'Mount All Games' secara otomatis melewati aplikasi yang membutuhkan migrasi sehingga tidak memicu error palsu pada dashboard maupun daftar game.")
                            GithubMarkdownBullet("• **Konsistensi Status Room DB**: Eksepsi `OcclusionHazardException` tetap mempertahankan status `NEED_MIGRATION` tanpa pernah menurunkannya ke `ERROR`.")

                            GithubSectionHeader("🖼️ Scoping Media Presisi & Pencegahan Penyembunyian Galeri")
                            GithubMarkdownBullet("• **Eliminasi .nomedia Root**: Menghapus berkas `.nomedia` agresif pada induk `MountX/Android/` dan membersihkan sisa peninggalan versi sebelumnya.")
                            GithubMarkdownBullet("• **Preservasi Media & Galeri**: Folder `Android/media/` serta direktori unduhan kustom (seperti 1DM) tetap dapat dipindai oleh MediaScanner Android.")

                            GithubSectionHeader("🔄 Penyelarasan Alur Migrasi Modern Dashboard")
                            GithubMarkdownBullet("• **Pipeline Migrasi Terpadu**: Fungsi `migrateGame` pada dashboard kini menghentikan aplikasi (*force stop*), melepas mount yang masih aktif, dan menyelaraskan struktur Room DB secara atomik.")
                        }

                        // ── v2.2.36 ──
                        GithubReleaseCard(
                            version = "v2.2.36",
                            releaseDate = "21 Sep 2026",
                            isLatest = false,
                            title = "Smart Migration Guardrail & Rekonsiliasi Ground Truth Mount"
                        ) {
                            GithubSectionHeader("🛡️ Anti-Occlusion Hazard Tanpa Ambang Batas Arbitrer")
                            GithubMarkdownBullet("• **Proteksi Seluruh Ukuran Data Internal**: Menghapus batasan lama (> 5MB). Berapapun ukuran data internal pada aplikasi, sistem menolak bind-mount jika direktori MicroSD masih kosong/belum dibuat.")
                            GithubMarkdownBullet("• **Pencegahan Silent Success**: Memastikan proses mount gagal secara terhormat dengan pengecekan jumlah titik kait aktif (`totalMountedTargets > 0`) agar tidak memicu status palsu.")

                            GithubSectionHeader("⚡ Deteksi Otomatis Status Kebutuhan Migrasi (Need Migration)")
                            GithubMarkdownBullet("• **Auto-Flagging Cerdas**: Saat game unmounted memiliki data di memori internal sementara MicroSD kosong, sistem secara otomatis menandainya sebagai `NEED_MIGRATION`.")
                            GithubMarkdownBullet("• **Tombol Aksi Pintar di Detail Aplikasi**: Tombol aksi bawah pada Detail Game secara adaptif beralih menjadi `Pindahkan Data ke MicroSD` (berwarna amber) dan langsung memicu alur transfer migrasi alih-alih loop mount kosong.")

                            GithubSectionHeader("🔄 Rekonsiliasi Ground Truth Linux Mounts")
                            GithubMarkdownBullet("• **Penyelarasan GamesViewModel**: Sinkronisasi berkala dan refresh UI kini memanggil `refreshMountStatuses()` untuk memverifikasi kaitan nyata langsung dari kernel `/proc/mounts`.")
                            GithubMarkdownBullet("• **Pemisahan Callback Mount & Unmount**: Memisahkan aksi `onMount` dan `onUnmount` di seluruh lapisan UI agar tidak terjadi pemanggilan terbalik saat status sedang bertransisi.")
                        }

                        // ── v2.2.35 ──
                        GithubReleaseCard(
                            version = "v2.2.35",
                            releaseDate = "21 Sep 2026",
                            isLatest = false,
                            title = "11 Pilar Keamanan, Multi-User, Sinkronisasi Modul & Keandalan VFS Terpadu"
                        ) {
                            GithubSectionHeader("🛡️ Root Picker Guardrail & Keamanan Sistem")
                            GithubMarkdownBullet("• **Hard-Blacklist Virtual Filesystem**: Memblokir navigasi dan pemilihan folder kernel virtual (`/dev`, `/proc`, `/sys`, `/apex`) dengan badge bahaya merah dan tombol aksi nonaktif.")
                            GithubMarkdownBullet("• **Dialog Risiko Sistem Kritis**: Folder sistem inti (`/`, `/system`, `/data`, `/vendor`, `/product`) dilindungi dengan modal bahaya merah dan checkbox persetujuan risiko eksplisit untuk mencegah salah pilih.")

                            GithubSectionHeader("⚡ Pipeline Multi-Target Modul Root (mountpoints.conf)")
                            GithubMarkdownBullet("• **Format Pipa POSIX Universal**: Parser POSIX sh pada `service.sh` membaca `mountpoints.conf` tanpa dependensi eksternal, kompatibel dengan Magisk, KernelSU, dan APatch.")
                            GithubMarkdownBullet("• **Kaitan Multi-Namespace Android**: Menjamin akses berkas lintas aplikasi setelah reboot dengan pengaitan ke seluruh runtime namespaces (`/mnt/runtime/*`, `/storage/emulated/0`, `/data/media/0`).")
                            GithubMarkdownBullet("• **Sinkronisasi Modul-Pertama**: Boot coordinator mendeteksi penanda kaitan modul (`/dev/.mountx_booted`) untuk sinkronisasi instan ke Room DB tanpa risiko stacked mounts ganda.")

                            GithubSectionHeader("👥 Dukungan Universal Multi-User & Profil Kloning")
                            GithubMarkdownBullet("• **Discovery Dinamis User ID**: Mendeteksi seluruh profil aktif melalui `pm list users` dan `/data/media/*` (Xiaomi Dual Apps User 999, Work Profile User 10+).")
                            GithubMarkdownBullet("• **Isolasi Folder MicroSD Terpisah**: Setiap user profil memiliki alokasi struktur mandiri di `MountX/users/<userId>/Android/data/` tanpa saling timpa.")
                            GithubMarkdownBullet("• **Penyesuaian UID / GID Resmi**: Formula matematis resmi Android `(userId * 100000) + appId` menjamin izin akses berkas sandbox tepat sasaran.")

                            GithubSectionHeader("🖼️ Manajemen .nomedia Cerdas & Media Scanner")
                            GithubMarkdownBullet("• **Scoping Terlokalisasi**: Mencegah penempatan file `.nomedia` pada direktori root `MountX/`, hanya diterapkan pada subdirektori privat game.")
                            GithubMarkdownBullet("• **Sakelar Galeri Interaktif**: Opsi `Tampilkan di Galeri & Media` pada kategori Media & Unduhan serta folder Kustom dengan pembersihan `.nomedia` otomatis.")
                            GithubMarkdownBullet("• **Pemicu Media Scanner Otomatis**: Menjalankan broadcast `MEDIA_SCANNER_SCAN_FILE` pasca-mount atau pemindahan berkas agar galeri sistem langsung terindeks.")

                            GithubSectionHeader("💾 Pre-Flight Space Guard & Preservasi Dotfiles")
                            GithubMarkdownBullet("• **Pencegahan Kegagalan Migrasi (Guard Space)**: Membatalkan pemindahan lebih awal jika sisa ruang target kurang dari ukuran data ditambah buffer aman `maxOf(500MB, 5% data)`.")
                            GithubMarkdownBullet("• **Preservasi Berkas Tersembunyi**: Penyalinan dan migrasi direktori menggunakan `cp -an \"\$src/.\" \"\$dst/\"` memastikan dotfiles (`.config`, `.save`) tersalin utuh tanpa hilang.")
                            GithubMarkdownBullet("• **Pre-Mount Force Stop**: Menghentikan proses aplikasi game di latar belakang sebelum bind-mount dikaitkan untuk mencegah divergensi mount namespace.")
                        }

                        // ── v2.2.34 ──
                        GithubReleaseCard(
                            version = "v2.2.34",
                            releaseDate = "21 Sep 2026",
                            isLatest = false,
                            title = "4 Pilar Arsitektur Cerdas: Sinkronisasi 2 Arah Modul Root, Restrukturisasi Multi-Pola MicroSD, Penjelajah Berkas Root & Deteksi Disk Dinamis"
                        ) {
                            GithubSectionHeader("⚡ Modul Root & Sinkronisasi 2 Arah (Pilar 1)")
                            GithubMarkdownBullet("• **Dukungan Multi Root Manager**: Skrip booting `service.sh` mendukung penuh Magisk, KernelSU, APatch, dan variannya dengan resolusi otomatis jalur modul.")
                            GithubMarkdownBullet("• **Resolusi Jalur Bertingkat**: Boot mounting otomatis memprioritaskan jalur standar `MountX/Android/` dengan fallback aman ke `Android/` legacy dan mounting direktori media.")
                            GithubMarkdownBullet("• **Sinkronisasi Senyap (Zero-Reboot)**: Aplikasi secara otomatis memperbarui skrip dan metadata modul di `/data/adb/modules/mountx/` saat versi baru dirilis tanpa perlu reboot manual.")

                            GithubSectionHeader("🔍 Pemindaian Cerdas & Restrukturisasi Multi-Pola (Pilar 2)")
                            GithubMarkdownBullet("• **Deteksi Multi-Folder**: Mendeteksi data game di MicroSD baik di folder standar `MountX/Android/`, legacy `Android/`, maupun direktori non-standar (`Games/*`, `GameData/*`).")
                            GithubMarkdownBullet("• **Restrukturisasi Atomik Transparan**: Memindahkan data game yang berantakan ke struktur standar MountX secara atomik (0.1s dengan `mv` separtisi atau streaming dengan visualisasi progres) lengkap dengan penyesuaian izin dan konteks SELinux.")

                            GithubSectionHeader("📁 Penjelajah Berkas Root ala MT-Manager (Pilar 3)")
                            GithubMarkdownBullet("• **Root Directory Picker Interaktif**: Bottom sheet penjelajah direktori root (`/*`) dengan breadcrumb interaktif, navigasi riil, dan pintasan cepat (Internal, MicroSD, Root, App Data, OTG).")
                            GithubMarkdownBullet("• **Pemilihan Jalur Kustom Presisi**: Trailing folder picker pada dialog direktori kustom dan kalkulasi ukuran nyata untuk kategori kustom dan media.")

                            GithubSectionHeader("🛡️ Multi-MicroSD & Penanganan Disk Dinamis (Pilar 4)")
                            GithubMarkdownBullet("• **Status Disk Detached**: Pengenalan status `DISK_DETACHED` dan kolom `preferredDiskUuid` (Room Migration 2 ke 3) untuk mencegah mount saat MicroSD dilepas atau kartu yang salah dimasukkan.")
                            GithubMarkdownBullet("• **Pelepasan Darurat Dinamis**: Watchdog dan SystemSyncMonitor mendeteksi pelepasan disk secara dinamis dari `/proc/mounts` tanpa batasan hardcode `/data/sdext2`.")
                        }

                        // ── v2.2.33 ──
                        GithubReleaseCard(
                            version = "v2.2.33",
                            releaseDate = "21 Sep 2026",
                            isLatest = false,
                            title = "Perbaikan Total Penghapusan Data Kategori, Eliminasi Opsi Internal Semu saat Ter-mount & Pembersihan VFS Multi-Jalur"
                        ) {
                            GithubSectionHeader("🗑️ Perbaikan Total Hapus Data Kategori")
                            GithubMarkdownBullet("• **Pembersihan Multi-Jalur MicroSD**: Penghapusan kategori data, obb, dan media kini menargetkan seluruh jalur fisik MicroSD modern (`MountX/Android/...`, `Android/...`, dan jalur kustom), sehingga berkas benar-benar terhapus tuntas.")
                            GithubMarkdownBullet("• **Dukungan Kategori Lengkap**: Menambahkan penanganan penghapusan berkas untuk kategori `Media & Unduhan` dan direktori kustom pengguna.")
                            GithubMarkdownBullet("• **Dukungan Mode Draf**: Fitur hapus kategori data kini tersambung penuh saat mengonfigurasi game baru pada layar pemilih aplikasi.")

                            GithubSectionHeader("🛡️ Eliminasi Opsi Internal Semu saat Ter-mount")
                            GithubMarkdownBullet("• **Anti-Duplikasi Ukuran Internal**: Kategori yang sedang ter-mount kini mengalokasikan ukuran fisik 0 B pada penyimpanan internal, mencegah munculnya pilihan hapus internal semu yang sebelumnya meremount kembali data dari MicroSD.")
                            GithubMarkdownBullet("• **Inspektor & Dialog Informatif**: Rincian kategori kini menampilkan label `Dialihkan ke MicroSD` dan dialog konfirmasi menyajikan penjelasan lokasi fisik penghapusan secara transparan.")

                            GithubSectionHeader("⚡ Rekonsiliasi Direktori & Sinkronisasi DB")
                            GithubMarkdownBullet("• **Rekonsiliasi Izin Direktori**: Direktori internal lokal selalu diregenerasi secara bersih dengan UID pemilik paket, `chmod 775`, dan konteks SELinux yang valid agar game tetap dapat dijalankan tanpa crash.")
                            GithubMarkdownBullet("• **Sinkronisasi Database Otomatis**: Titik kait yang dihapus dinonaktifkan dari database Room dan status mount VFS kernel disegarkan seketika.")
                        }

                        // ── v2.2.32 ──
                        GithubReleaseCard(
                            version = "v2.2.32",
                            releaseDate = "21 Sep 2026",
                            isLatest = false,
                            title = "Presisi Deteksi Lokasi OBB [MicroSD], Pencegahan Stale Caching Antar-Game & Koreksi Concentric Chart"
                        ) {
                            GithubSectionHeader("🎯 Presisi Lokasi & Kategori OBB")
                            GithubMarkdownBullet("• **Deteksi OBB MicroSD Akurat**: Menghapus ambang batas artifisial (> 64KB) pada kategori OBB sehingga berkas/folder ekspansi (misal 4.00 KB) yang telah dipindahkan ke MicroSD kini konsisten berlabel `[MicroSD]` (hijau).")
                            GithubMarkdownBullet("• **Inspeksi Mountpoint Kernel Riil**: Status mount OBB, Data Game, dan Media kini dievaluasi langsung terhadap VFS kernel mountpoint per-kategori.")
                            GithubMarkdownBullet("• **Tombol Restore Berkas Ringan**: Tombol `Kembalikan ke Memori Internal` kini tetap aktif untuk kategori OBB berukuran kecil (> 0 byte).")

                            GithubSectionHeader("🚀 Isolasi Caching & Anti-Stale Antar-Game")
                            GithubMarkdownBullet("• **Pencegahan Kebocoran Data Breakdown**: Breakdown penyimpanan kini diisolasi per `packageName` dalam map cache, sehingga perpindahan antar-game tidak lagi menampilkan lonjakan data game sebelumnya (*stale flash*).")
                            GithubMarkdownBullet("• **Indikator Loading Halus**: Menampilkan indikator linear saat kalkulasi penyimpanan game baru sedang berlangsung.")

                            GithubSectionHeader("📊 Koreksi Concentric Storage Chart (Anti-Double Counting)")
                            GithubMarkdownBullet("• **Eliminasi Penghitungan Ganda**: Memori telepon internal (`phoneInternalBytes`) kini mengecualikan bind-mount MicroSD, sehingga total ukuran dan persentase irisan chart Donut mencerminkan ruang penyimpanan fisik asli secara akurat (100%).")
                        }

                        // ── v2.2.31 ──
                        GithubReleaseCard(
                            version = "v2.2.31",
                            releaseDate = "20 Sep 2026",
                            isLatest = false,
                            title = "Eliminasi Phantom Conflict Restore, Multi-Namespace VFS Unmount & Proteksi Tabrakan Partisi"
                        ) {
                            GithubSectionHeader("🛡️ Eliminasi Phantom Conflict & Keamanan VFS")
                            GithubMarkdownBullet("• **Penanganan Phantom Conflict Restore**: Mengoreksi penghitungan ukuran target internal saat kaitan mount MicroSD masih aktif (`isMounted == true`) sehingga dialog tidak lagi memicu peringatan konflik fiktif saat mengembalikan data ke memori internal.")
                            GithubMarkdownBullet("• **Pelepasan Mount Multi-Namespace**: Melepaskan kaitan VFS secara tuntas di seluruh namespace Android (`/mnt/runtime/*`, `/mnt/user/0`, `/storage`, dll) sebelum migrasi untuk mencegah tabrakan I/O atau kebocoran berkas.")
                            GithubMarkdownBullet("• **Guard Identitas Partisi & Disk**: Menolak pemindahan data jika direktori sumber dan tujuan berada di partisi fisik yang sama baik di backend `StorageManager` maupun proteksi seleksi antarmuka.")

                            GithubSectionHeader("⚡ Verifikasi Pengujian Nyata")
                            GithubMarkdownBullet("• **Uji End-to-End Pemulihan WuWa Berhasil**: Pemindahan data Wuthering Waves (140.9 MB) dari MicroSD kembali ke Internal berjalan mulus 100% dengan integritas berkas, kepemilikan `media_rw`, dan file `.nomedia` terjaga sempurna.")

                            GithubSectionHeader("💾 Portabilitas Konfigurasi JSON v2")
                            GithubMarkdownBullet("• **Normalisasi Jalur & Cadangan Portabel**: Skema JSON v2 menyimpan konfigurasi titik kait relatif sehingga bebas dipindahkan antar kartu MicroSD atau dipulihkan sewaktu-waktu tanpa terikat UUID hardware.")
                        }

                        // ── v2.2.30 ──
                        GithubReleaseCard(
                            version = "v2.2.30",
                            releaseDate = "20 Sep 2026",
                            isLatest = false,
                            title = "Perbaikan Eksekusi Salin Berkas Shell & Pencegahan Konflik Partisi Sama"
                        ) {
                            GithubSectionHeader("⚡ Perbaikan Penyalinan & Shell Engine")
                            GithubMarkdownBullet("• **Resolusi Eksekusi Salin Berkas**: Menghilangkan sintaks subshell yang tidak kompatibel dengan Android mksh/toybox pada skrip penyalinan latar belakang, mengatasi error `exit code -1` sehingga pemindahan data game berjalan konkret.")
                            GithubMarkdownBullet("• **Guard Tabrakan Jalur Identik (Backend)**: Menolak proses penyalinan atau penghapusan berkas jika jalur sumber dan tujuan identik pada level `StorageManager` untuk menjamin Zero Data Loss mutlak.")

                            GithubSectionHeader("🛡️ Antarmuka Cerdas & Proteksi Partisi Sama")
                            GithubMarkdownBullet("• **Pencegahan Konflik Partisi Sama**: Pada modal Kelola Penyimpanan, partisi MicroSD aktif saat ini otomatis dinonaktifkan dengan status `Lokasi saat ini (Tidak dapat dipilih)` jika data yang dipilih sudah berada di partisi tersebut.")
                            GithubMarkdownBullet("• **Arah & Target Cerdas (Smart Selection)**: Jika semua kategori yang dipilih bersumber dari MicroSD, opsi `Memori Internal` otomatis diaktifkan sebagai target pemulihan, memandu pengguna ke arah yang benar.")
                        }

                        // ── v2.2.29 ──
                        GithubReleaseCard(
                            version = "v2.2.29",
                            releaseDate = "20 Sep 2026",
                            isLatest = false,
                            title = "Proteksi Konflik Data Zero-Loss, Portabilitas Konfigurasi v2 & Adaptive Icon Mulus"
                        ) {
                            GithubSectionHeader("🛡️ Keamanan & Integritas Data (Zero-Loss)")
                            GithubMarkdownBullet("• **Pencegahan Penimpaan Berkas Kosong**: Menolak secara otomatis operasi penimpaan destruktif jika folder sumber kosong/kerangka direktori (<= 64KB) sementara tujuan memiliki berkas riil game (>= 10MB) guna mencegah hilangnya data pengguna.")
                            GithubMarkdownBullet("• **Proteksi Partisi Sama (Self-Conflict Guard)**: Mendeteksi data yang telah berada di MicroSD tujuan sehingga mengeliminasi proses salin sirkular dan langsung memverifikasi izin VFS secara aman.")
                            GithubMarkdownBullet("• **Pemeriksaan Kaitan VFS Pra-Operasi**: Memastikan kaitan mount dilepaskan dengan aman sebelum proses penyalinan atau pembersihan berkas untuk mencegah penghapusan data secara sirkular.")
                            GithubMarkdownBullet("• **Sinkronisasi Status Atomik & Pembersihan Canary**: Memperbarui status unmount secara akurat di database saat pemulihan ke internal selesai serta membersihkan berkas penanda canary internal.")

                            GithubSectionHeader("💾 Portabilitas Konfigurasi (Skema v2)")
                            GithubMarkdownBullet("• **Ekspor & Impor Konfigurasi Skema v2**: Menyimpan metadata terstruktur mencakup profil game, titik kait granular, kategori berkas, dan penataan partisi relatif secara portabel.")
                            GithubMarkdownBullet("• **Dukungan Kompatibilitas Mundur**: Tetap dapat membaca dan menggabungkan berkas konfigurasi lama secara otomatis.")

                            GithubSectionHeader("🎨 Tampilan Ikon Launcher (Adaptive Icon)")
                            GithubMarkdownBullet("• **Ikon App Drawer Mengikuti Wadah Sistem**: Menggunakan emblem transparan MountX di atas kanvas Cyberpunk mulus tanpa bingkai squircle kaku, menyatu elegan dengan bentuk wadah launcher (lingkaran, squircle, atau pebble).")
                        }

                        // ── v2.2.28 ──
                        GithubReleaseCard(
                            version = "v2.2.28",
                            releaseDate = "20 Sep 2026",
                            isLatest = false,
                            title = "Progres Kernel Real-Time & Safe Zone Ikon App Drawer"
                        ) {
                            GithubSectionHeader("⚡ Penyalinan Data & Telemetri Nyata")
                            GithubMarkdownBullet("• **Progres Transfer Kernel Nyata**: Pemantauan langsung melalui penghitung byte I/O Linux kernel (`/proc/\$PID/io`) dengan interval 300ms, menghasilkan kenaikan persentase bertahap yang konkret (0% -> 100%) tanpa lonjakan tiba-tiba.")
                            GithubMarkdownBullet("• **Kecepatan & Sisa Waktu Dinamis**: Menampilkan metrik kecepatan transfer riil (MB/s) dan estimasi sisa waktu (ETA) terhitung otomatis sepanjang proses penyalinan.")
                            GithubMarkdownBullet("• **Fallback Graceful**: Sistem otomatis beralih ke kalkulasi ukuran direktori jika akses kernel I/O dibatasi pada varian kernel tertentu.")

                            GithubSectionHeader("🎨 Penyempurnaan Tampilan (UI/UX)")
                            GithubMarkdownBullet("• **Safe Zone Ikon App Drawer Android**: Menyesuaikan proporsi Adaptive Icon dengan batas aman ~18% inset (72dp pada kanvas 108dp) agar logo MountX tidak terpotong oleh masking lingkaran atau squircle di launcher sistem.")
                        }

                        // ── v2.2.27 ──
                        GithubReleaseCard(
                            version = "v2.2.27",
                            releaseDate = "20 Sep 2026",
                            isLatest = false,
                            title = "Migrasi Terpusat MountX, Indikator Akses Hijau & Markdown Changelog"
                        ) {
                            GithubSectionHeader("🚀 Migrasi Otomatis & Standarisasi Direktori")
                            GithubMarkdownBullet("• **Migrasi Otomatis Direktori MountX**: Mengalihkan seluruh data legacy dari `\$sdBase/Android/data` dan `\$sdBase/Android/obb` ke folder terpusat `\$sdBase/MountX/Android/` secara aman tanpa risiko kehilangan berkas.")
                            GithubMarkdownBullet("• **Pembersihan Bersih Tanpa Sampah**: Direktori legacy kosong (`\$sdBase/Android/`) otomatis dibersihkan dan dihapus setelah migrasi diverifikasi berhasil guna menghindari duplikasi berkas.")
                            GithubMarkdownBullet("• **Sinkronisasi Database Otomatis**: Memperbarui seluruh konfigurasi `sourcePath` pada database Room ke jalur terpusat MountX secara mulus di latar belakang.")

                            GithubSectionHeader("🎨 Pembaruan Antarmuka (UI/UX)")
                            GithubMarkdownBullet("• **Indikator Centang Hijau Izin Sistem**: Menu Perizinan & Hak Akses di Pengaturan kini menampilkan centang hijau modern (`CyberEmerald`) dan badge `Aktif` saat seluruh izin terpenuhi, dengan interaksi klik tetap terjaga.")
                            GithubMarkdownBullet("• **Pembersihan Menu Pengaturan**: Menghilangkan kartu pengaturan \"Penyimpanan\" yang tidak lagi diperlukan agar antarmuka lebih ringkas dan fokus.")
                            GithubMarkdownBullet("• **Changelog Gaya GitHub Markdown**: Seluruh riwayat pembaruan kini disajikan terstruktur layaknya GitHub Release Notes dengan tag rilis, badge status, kategori, dan capsule kode.")

                            GithubSectionHeader("🐛 Perbaikan Bug & Stabilitas")
                            GithubMarkdownBullet("• **Keamanan Jalur VFS**: Memastikan izin 777 dan berkas pelindung `.nomedia` otomatis dikonfigurasikan pada setiap kaitan baru di dalam direktori `MountX/`.")
                        }

                        // ── v2.2.26 ──
                        GithubReleaseCard(
                            version = "v2.2.26",
                            releaseDate = "20 Sep 2026",
                            title = "Desain Dialog Modern, Input Kontras Tinggi & Jalur Terpusat MountX"
                        ) {
                            GithubSectionHeader("🎨 Poles Desain & Antarmuka")
                            GithubMarkdownBullet("• **Desain Dialog Cyber Midnight**: Seluruh dialog aplikasi kini menggunakan tema Cyber Midnight (`#111726`) dengan radius sudut 24dp modern.")
                            GithubMarkdownBullet("• **Kotak Input Kontras Tinggi**: Field input direktori kustom kini berlatar solid (`#162035`) dengan border 1.5dp dan aksen fokus Electric Indigo jelas.")
                            GithubMarkdownBullet("• **Saran Cepat 1-Ketuk**: Chip rekomendasi instan untuk `Telegram`, `WhatsApp`, `Download`, `DCIM`, dan `Pictures`.")
                            GithubMarkdownBullet("• **Label Tombol Rapi**: Mengeliminasi duplikasi ikon `+` pada tombol Tambah Direktori Kustom.")

                            GithubSectionHeader("🛠️ Pengoptimalan VFS & Jalur")
                            GithubMarkdownBullet("• **Penyelarasan Jalur VFS**: Sanitasi canonical prefix `/sdcard/` dan inisialisasi proaktif folder `\$sdBase/MountX/` berizin 775.")
                        }

                        // ── v2.2.25 ──
                        GithubReleaseCard(
                            version = "v2.2.25",
                            releaseDate = "19 Sep 2026",
                            title = "Penyimpanan Terpusat MountX, Deteksi Cerdas Media & Aksi Cepat Migrasi"
                        ) {
                            GithubSectionHeader("📦 Penyimpanan Terpusat & Media")
                            GithubMarkdownBullet("• **Struktur Direktori Terpusat MountX**: Seluruh data pengalihan dikumpulkan rapi di dalam folder induk `\$sdBase/MountX/` (Android/data, obb, media, app, containers).")
                            GithubMarkdownBullet("• **Kompatibilitas Mundur Penuh**: Deteksi transparan data di jalur lama tanpa memerlukan migrasi paksa manual.")
                            GithubMarkdownBullet("• **Deteksi Cerdas Media & Unduhan**: Mendukung pemindahan folder media bersama (`/data/media/0/Android/media/<pkg>`) dengan aturan proteksi `.nomedia` pintar.")
                            GithubMarkdownBullet("• **Aksi Cepat Migrasi**: Badge status oranye `Perlu Migrasi` dengan aksi 1-klik `Satukan & Pindahkan ke MicroSD`.")
                        }

                        // ── v2.2.24 ──
                        GithubReleaseCard(
                            version = "v2.2.24",
                            releaseDate = "18 Sep 2026",
                            title = "Stabilisasi Ukuran VFS, Manajemen Modul Root & Info Aplikasi"
                        ) {
                            GithubSectionHeader("🛠️ Modul Root & Stabilitas VFS")
                            GithubMarkdownBullet("• **Stabilisasi Deteksi Ukuran Game**: Mencegah penurunan ukuran ke 20KB saat skeleton ter-mount di atas data internal.")
                            GithubMarkdownBullet("• **Manajemen Terpadu Modul Root**: Banner aktivasi instan via root serta ekspor berkas ZIP flashable langsung ke Download.")
                            GithubMarkdownBullet("• **Tab Info Aplikasi Lengkap**: Panel komprehensif identitas paket, versi, SDK target/min, dan UID/GID sandbox.")
                            GithubMarkdownBullet("• **Tombol Kaitan Bergantian**: Tombol dinamis tunggal bergantian (`Kaitkan Game` vs `Lepaskan Mount` aman).")
                        }

                        // ── v2.2.23 ──
                        GithubReleaseCard(
                            version = "v2.2.23",
                            releaseDate = "17 Sep 2026",
                            title = "Back Navigation Berjenjang, Konfirmasi Migrasi & Resolusi Konflik Data"
                        ) {
                            GithubSectionHeader("🚀 Navigasi & Mesin Salin Atomic")
                            GithubMarkdownBullet("• **Navigasi Back Gesture Berjenjang**: Back gesture pada seluruh sub-layar mundur bertahap per level secara intuitif.")
                            GithubMarkdownBullet("• **Dialog Konfirmasi Pra-Migrasi**: Menampilkan pratinjau arah sumber-tujuan, ukuran fisik riil, dan validasi ruang.")
                            GithubMarkdownBullet("• **Mesin Salin Atomic**: Penyalinan aman (`cp -a`) dengan verifikasi ukuran integritas sebelum pembersihan berkas sumber.")
                        }

                        // ── v2.2.22 ──
                        GithubReleaseCard(
                            version = "v2.2.22",
                            releaseDate = "16 Sep 2026",
                            title = "Real-Time Progress Engine & Live Telemetry Stepper"
                        ) {
                            GithubSectionHeader("⚡ Mesin Telemetri Real-Time")
                            GithubMarkdownBullet("• **Kecepatan & Estimasi Real-Time**: Kecepatan transfer MB/s (EMA) dan estimasi sisa waktu (ETA) real-time.")
                            GithubMarkdownBullet("• **Visual Stepper 5 Tahap**: Checklist multi-tahap transparan untuk pemindahan dan kaitan VFS.")
                        }

                        // ── v2.2.21 ──
                        GithubReleaseCard(
                            version = "v2.2.21",
                            releaseDate = "15 Sep 2026",
                            title = "Category Inspector Modal & Safe Multi-Storage Deletion"
                        ) {
                            GithubSectionHeader("🔍 Inspektur Kategori Data")
                            GithubMarkdownBullet("• **Category Inspector Modal**: Ketuk kartu kategori data game untuk melihat rincian path lengkap di setiap media.")
                            GithubMarkdownBullet("• **Penghapusan Data Aman Bertingkat**: Opsi hapus data kategori dengan pilihan lokasi (Internal Saja, MicroSD Saja, atau Keduanya).")
                        }

                        // ── v2.2.20 & Terdahulu ──
                        GithubReleaseCard(
                            version = "v2.2.20",
                            releaseDate = "14 Sep 2026",
                            title = "Ultra-Fast Storage Engine & Absolute Size Precision"
                        ) {
                            GithubSectionHeader("⚡ Performa Tinggi & Pengurangan Latensi")
                            GithubMarkdownBullet("• **Syscall stat -f Berkecepatan Tinggi**: Menggantikan perintah `df -k` dengan respon 0,09 detik (350x lebih cepat).")
                            GithubMarkdownBullet("• **Logging Asinkron**: Menghindari antrian pemblokiran RootShell saat operasi I/O intensif.")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showChangelogDialog = false }) {
                    Text(stringResource(R.string.common_close), color = Color(0xFF818CF8), fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }

    // License Dialog
    if (showLicenseDialog) {
        AlertDialog(
            onDismissRequest = { showLicenseDialog = false },
            containerColor = Color(0xFF111726),
            shape = RoundedCornerShape(24.dp),
            title = {
                Text(
                    "MIT License",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFF1F5F9)
                )
            },
            text = {
                Text(
                    text = "Copyright (c) 2026 Noir / Zy0x\n\nPermission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFCBD5E1)
                )
            },
            confirmButton = {
                TextButton(onClick = { showLicenseDialog = false }) {
                    Text(stringResource(R.string.common_close), color = Color(0xFF818CF8))
                }
            }
        )
    }
}

@Composable
private fun GithubReleaseCard(
    version: String,
    releaseDate: String,
    title: String,
    isLatest: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF141C2E),
        border = BorderStroke(
            1.dp,
            if (isLatest) Color(0xFF6366F1).copy(alpha = 0.55f) else Color(0xFF26354D)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isLatest) Color(0xFF6366F1).copy(alpha = 0.2f) else Color(0xFF1E293B),
                        border = BorderStroke(1.dp, if (isLatest) Color(0xFF6366F1) else Color(0xFF334155))
                    ) {
                        Text(
                            text = version,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = if (isLatest) Color(0xFFA5B4FC) else Color(0xFFCBD5E1),
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = releaseDate,
                        fontSize = 10.5.sp,
                        color = Color(0xFF64748B)
                    )
                }

                if (isLatest) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = CyberEmerald.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, CyberEmerald.copy(alpha = 0.4f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(CyberEmerald, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Latest",
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = CyberEmerald
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFF1F5F9),
                modifier = Modifier.padding(bottom = 6.dp)
            )

            content()
        }
    }
}

@Composable
private fun GithubSectionHeader(title: String) {
    Column(modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            ),
            color = Color(0xFF94A3B8)
        )
        HorizontalDivider(
            thickness = 0.8.dp,
            color = Color(0xFF334155).copy(alpha = 0.5f),
            modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
        )
    }
}

@Composable
private fun GithubMarkdownBullet(rawText: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = buildMarkdownAnnotatedString(rawText),
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 11.sp,
                lineHeight = 16.sp
            ),
            color = Color(0xFFCBD5E1)
        )
    }
}

private fun buildMarkdownAnnotatedString(text: String): androidx.compose.ui.text.AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            if (text.startsWith("**", i)) {
                val end = text.indexOf("**", i + 2)
                if (end != -1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color(0xFFF1F5F9))) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 2
                    continue
                }
            } else if (text.startsWith("`", i)) {
                val end = text.indexOf("`", i + 1)
                if (end != -1) {
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = Color(0xFF38BDF8),
                            background = Color(0xFF1E293B)
                        )
                    ) {
                        append(" ${text.substring(i + 1, end)} ")
                    }
                    i = end + 1
                    continue
                }
            }
            append(text[i])
            i++
        }
    }
}
