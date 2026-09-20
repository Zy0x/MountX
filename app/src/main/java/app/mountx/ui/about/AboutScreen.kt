package app.mountx.ui.about

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
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

    // Changelog Dialog
    if (showChangelogDialog) {
        AlertDialog(
            onDismissRequest = { showChangelogDialog = false },
            title = {
                Text(
                    stringResource(R.string.about_changelog),
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp)
                    ) {
                        Text(text = "v2.2.17 (Deteksi Cerdas Multi-Partisi & Presisi Ukuran Game)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(text = "• Deteksi Cerdas Multi-Partisi: Memindai seluruh partisi eksternal (/mnt/media_rw, /storage, /data/sdext*) secara dinamis.", fontSize = 11.sp)
                        Text(text = "• Deteksi Ganda Internal & MicroSD: Mendeteksi data game yang tersimpan di internal maupun MicroSD secara cerdas dengan info sekunder jika data ada di kedua media.", fontSize = 11.sp)
                        Text(text = "• Presisi Telemetri Ukuran: Perhitungan ukuran data game (seperti Wuthering Waves) kini akurat menampilkan ukuran fisik sebenarnya (~141 MB) bukan 7 KB.", fontSize = 11.sp)
                        Text(text = "• Optimasi Kinerja Root Shell: Mempercepat pembacaan mount dan query penyimpanan menjadi instan tanpa membebani antarmuka aplikasi.", fontSize = 11.sp)
                        Text(text = "• Sinkronisasi Donut Chart: Grafik donat konsentris dan legenda ringkasan 100% konsisten dengan alokasi fisik media penyimpanan.", fontSize = 11.sp)

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(text = "v2.2.16 (Penyatuan Tab Penyimpanan & Modal Migrasi Cerdas)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(text = "• Penyatuan Tab Penyimpanan: Seluruh kategori data game (APK, Lib, Data Privat, Cache, Data Game, OBB) disatukan di tab Penyimpanan dengan label bersih.", fontSize = 11.sp)
                        Text(text = "• Mode Seleksi In-Place: Menekan Kelola Penyimpanan mengaktifkan checkbox seleksi langsung dengan default aman dan peringatan risiko kinerja.", fontSize = 11.sp)
                        Text(text = "• Modal 3-Langkah Cerdas: Pemilihan target Disk (pembekuan lokasi asal cerdas), pemilihan Partisi, dan Konfirmasi ringkasan pemindahan.", fontSize = 11.sp)
                        Text(text = "• Tab Kelola Minimalis: Placeholder bersih bersiap untuk fitur lanjutan mendatang.", fontSize = 11.sp)

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(text = "v2.2.15 (Revamp total UI Tab Manage - 8-Screen Guided Flow)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(text = "• Revamp total UI Tab Manage (8-Screen Guided Flow): Hybrid Storage Chart, Kategori Data Game, Stepper Progress Pemindahan, Detail Kategori, dan Bottom Sheet Opsi Folder.", fontSize = 11.sp)

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(text = "v2.2.20 (Ultra-Fast Storage Engine & Absolute Size Precision)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(text = "• Eliminasi total perintah df -k: digantikan dengan syscall stat -f berkecepatan 0,09 detik (350x lebih cepat).", fontSize = 11.sp)
                        Text(text = "• Mengatasi pembekuan sistem akibat pemindaian 26.900+ bind mounts dan partisi FUSE /storage/*.", fontSize = 11.sp)
                        Text(text = "• Presisi 100% ukuran Internal, MicroSD (FAT/ext4), dan sdext2 (F2FS) dengan akumulasi basis partisi unik.", fontSize = 11.sp)
                        Text(text = "• Eliminasi kalkulasi duplikat di GamesViewModel untuk loading layar detail instan.", fontSize = 11.sp)
                        Text(text = "• Logging asinkron berkecepatan tinggi dengan fallback langsung tanpa memblokir antrian RootShell.", fontSize = 11.sp)

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(text = "v2.2.19 (Perbaikan Performa: Breakdown Storage Instan)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(text = "• Perbaikan kritis: detail storage kini muncul dalam hitungan detik, bukan menit.", fontSize = 11.sp)
                        Text(text = "• AppLogger direfaktor: tulis ke logcat secara instan, antrian file-write via Channel agar tidak memblokir kalkulasi du.", fontSize = 11.sp)
                        Text(text = "• Setiap log tidak lagi menunggu root shell selesai sebelum log berikutnya dikirim.", fontSize = 11.sp)

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(text = "v2.2.18 (Perbaikan Akurasi Deteksi Storage Eksternal)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(text = "• Perbaikan kritis: data game di MicroSD kini terdeteksi akurat tanpa memerlukan status mount dari DB.", fontSize = 11.sp)
                        Text(text = "• Memindai semua partisi eksternal (/data/sdext*, /mnt/media_rw/*) secara paralel dalam satu batch du.", fontSize = 11.sp)
                        Text(text = "• Ukuran ext1 tidak lagi di-nolkan saat status mounted — mengikuti bind mount aktual untuk presisi.", fontSize = 11.sp)
                        Text(text = "• Deduplikasi per storage base mencegah data yang sama dihitung ganda (berbeda path, fisik sama).", fontSize = 11.sp)
                        Text(text = "• Fallback APK size via root pm path jika PackageManager gagal (QUERY_ALL_PACKAGES).", fontSize = 11.sp)

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(text = "v2.2.17 (Data vs OBB Naming, Path Visibility & Novice Grouping)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(text = "• Fixed directory misclassification: Android/data is accurately labeled 'Data' and Android/obb is labeled 'OBB'", fontSize = 11.sp)
                        Text(text = "• Clear directory path visibility: displays relative target path and MicroSD → Phone Internal flow indicators", fontSize = 11.sp)
                        Text(text = "• Novice-friendly grouping: Core Game Data (Recommended) and Additional & Custom Data separated cleanly", fontSize = 11.sp)
                        Text(text = "• Interactive Directory Details Dialog: tap any directory card to inspect absolute paths with 1-tap clipboard copy", fontSize = 11.sp)
                        Text(text = "• Live telemetry size integration: accurate folder sizes reflecting internal and secondary MicroSD partition status", fontSize = 11.sp)

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(text = "v2.2.16 (Storage Accuracy & FAB Precision)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(text = "• App Detail storage redundancy fix: eliminate double counting when game data is mounted from SD", fontSize = 11.sp)
                        Text(text = "• Shared storage reflects true physical flash allocation: 0 B on internal memory with [OFFLOADED] badge", fontSize = 11.sp)
                        Text(text = "• Donut chart accuracy: 100% physically aligned internal vs MicroSD partition ratio without duplicate slices", fontSize = 11.sp)
                        Text(text = "• FAB layout precision: fixed double-offset, FAB now sits directly above bottom nav bar", fontSize = 11.sp)
                        Text(text = "• FAB touch target enlarged to 52dp with smooth responsive glide down above Android gesture bar", fontSize = 11.sp)

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(text = "v2.2.15 (UI Polish, Delete Flow & FAB Fix)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(text = "• FAB glide fix: floating button stays visible when bottom bar hides, slides down smoothly", fontSize = 11.sp)
                        Text(text = "• Delete dialog upgraded: choose Restore to Internal or Unmount Only with progress tracking", fontSize = 11.sp)
                        Text(text = "• Custom Path dialog now uses dark theme (#111625) consistent with app design system", fontSize = 11.sp)
                        Text(text = "• Mount point cards: removed raw PKG badge, added color-coded Experimental APK section", fontSize = 11.sp)
                        Text(text = "• Phantom USB OTG disk fix: strict UUID validation prevents fake disk entries", fontSize = 11.sp)
                        Text(text = "• New mount categories: EXTERNAL_DATA, OBB_STORAGE, APP_PACKAGE with proper icons", fontSize = 11.sp)
                        Text(text = "• WakeLock protection during restore operations prevents CPU sleep mid-transfer", fontSize = 11.sp)

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(text = "v2.2.4 (Performance & UX Overhaul)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(text = "• Disk title telemetry integrated in Unmount Partition confirmation dialog", fontSize = 11.sp)
                        Text(text = "• AOMEI slider unallocated space block visualization & + Sisa 1-tap absorption", fontSize = 11.sp)
                        Text(text = "• Real-time filesystem kernel/tool capability badges in Partition Wizard", fontSize = 11.sp)
                        Text(text = "• Zero-delay instant navigation when switching to Storage screen", fontSize = 11.sp)
                        Text(text = "• Unified AppLogger logging all root operations in real-time to mountx.log", fontSize = 11.sp)
                        Text(text = "• 120 FPS buttery smooth app list scrolling & icon memory cache", fontSize = 11.sp)
                        Text(text = "• Instant runtime language switching without restarting the app", fontSize = 11.sp)
                        Text(text = "• Standardized MountX branding across all screens and resources", fontSize = 11.sp)

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(text = "v2.2.3 (Unmount & TRIM Stability)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(text = "• Deep unmount teardown for mmcblk0p3 across all runtime namespaces", fontSize = 11.sp)
                        Text(text = "• Deduplicated TRIM results dialog with actionable fsck guidance", fontSize = 11.sp)
                        Text(text = "• FSCK volume busy (EBUSY) error prevention", fontSize = 11.sp)
                        Text(text = "• Smooth 60/120 FPS partition divider gesture dragging", fontSize = 11.sp)

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(text = "v2.2.2 (AOMEI Partitioning Engine)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(text = "• Interactive multi-partition proportional resizing bar", fontSize = 11.sp)
                        Text(text = "• Direct filesystem path resolution for FSTRIM (FUSE bypass)", fontSize = 11.sp)
                        Text(text = "• Progress indicators and optimistic UI states for unmount and eject", fontSize = 11.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showChangelogDialog = false }) {
                    Text(stringResource(R.string.common_close))
                }
            }
        )
    }

    // License Dialog
    if (showLicenseDialog) {
        AlertDialog(
            onDismissRequest = { showLicenseDialog = false },
            title = { Text("MIT License") },
            text = {
                Text(
                    text = "Copyright (c) 2026 Noir / Zy0x\n\nPermission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies.",
                    style = MaterialTheme.typography.bodySmall
                )
            },
            confirmButton = {
                TextButton(onClick = { showLicenseDialog = false }) {
                    Text(stringResource(R.string.common_close))
                }
            }
        )
    }
}
