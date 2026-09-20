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
                        // ── v2.2.27 (Latest) ──
                        GithubReleaseCard(
                            version = "v2.2.27",
                            releaseDate = "20 Sep 2026",
                            isLatest = true,
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
