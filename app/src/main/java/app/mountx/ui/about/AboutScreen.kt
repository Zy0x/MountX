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
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.mountx.ui.theme.ElectricIndigo
import app.mountx.ui.theme.ElectricIndigoLight
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
import app.mountx.ui.theme.ForestGreenLight
import app.mountx.ui.theme.adaptiveEmerald
import androidx.compose.ui.graphics.Brush
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import app.mountx.data.model.CategoryChange
import app.mountx.data.model.ChangelogHistory
import app.mountx.data.model.ChangelogRelease
import app.mountx.data.model.FeatureChange

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
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

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
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outline
                    ),
                    modifier = Modifier.size(68.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_mountx_emblem),
                            contentDescription = stringResource(R.string.app_name),
                            modifier = Modifier.size(54.dp)
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.app_description),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                    color = if (isDark) CyberEmerald else ForestGreenLight
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
                            // Ko-fi
                            val kofiColor = Color(0xFFFF5E5B)
                            Surface(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://ko-fi.com/zy0x_noir"))
                                    context.startActivity(intent)
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = kofiColor.copy(alpha = if (isDark) 0.15f else 0.10f),
                                border = BorderStroke(1.dp, kofiColor.copy(alpha = if (isDark) 0.45f else 0.30f)),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 4.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_kofi),
                                        contentDescription = stringResource(R.string.about_kofi),
                                        tint = kofiColor,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.about_kofi),
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = kofiColor
                                    )
                                }
                            }

                            // Saweria
                            val saweriaColor = Color(0xFFE58B05)
                            Surface(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://saweria.co/zy0x"))
                                    context.startActivity(intent)
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = saweriaColor.copy(alpha = if (isDark) 0.15f else 0.10f),
                                border = BorderStroke(1.dp, saweriaColor.copy(alpha = if (isDark) 0.45f else 0.30f)),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 4.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_saweria),
                                        contentDescription = stringResource(R.string.about_saweria),
                                        tint = saweriaColor,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.about_saweria),
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = saweriaColor
                                    )
                                }
                            }

                            // PayPal
                            val paypalColor = Color(0xFF0079C1)
                            Surface(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://paypal.me/theamagenta"))
                                    context.startActivity(intent)
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = paypalColor.copy(alpha = if (isDark) 0.15f else 0.10f),
                                border = BorderStroke(1.dp, paypalColor.copy(alpha = if (isDark) 0.45f else 0.30f)),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 4.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_paypal),
                                        contentDescription = stringResource(R.string.about_paypal),
                                        tint = paypalColor,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.about_paypal),
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = paypalColor
                                    )
                                }
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

    // Changelog Dialog (Compact Categorized Style)
    if (showChangelogDialog) {
        Dialog(
            onDismissRequest = { showChangelogDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
            var visibleCount by remember { mutableIntStateOf(1) }

            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .heightIn(max = 680.dp)
                    .wrapContentHeight()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .padding(16.dp)
                ) {
                    // Header: # Changelog + Close (X)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "#",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = if (isDark) ElectricIndigo else ElectricIndigoLight
                            )
                            Text(
                                text = stringResource(R.string.about_changelog),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Close button (Circle X)
                        Surface(
                            onClick = { showChangelogDialog = false },
                            shape = CircleShape,
                            color = if (isDark) MaterialTheme.colorScheme.surfaceVariant else Color(0xFFE7E5E4),
                            modifier = Modifier.size(28.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringResource(R.string.common_close),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }

                    // Content: Releases + Pagination Button
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 580.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val releasesToShow = ChangelogHistory.releases.take(visibleCount)
                        releasesToShow.forEach { release ->
                            CompactChangelogReleaseCard(
                                release = release,
                                isDark = isDark
                            )
                        }

                        // Pagination Button: "Lihat versi lainnya (2 versi terdahulu) ⌵"
                        if (visibleCount < ChangelogHistory.releases.size) {
                            val remaining = ChangelogHistory.releases.size - visibleCount
                            val countLabel = if (remaining > 1) "2 older releases" else "1 older release"
                            val outlineColor = if (isDark) Color(0xFF6366F1).copy(alpha = 0.45f) else Color(0xFF4F46E5).copy(alpha = 0.45f)
                            val accentColor = if (isDark) Color(0xFF818CF8) else Color(0xFF4F46E5)

                            Surface(
                                onClick = {
                                    visibleCount = minOf(ChangelogHistory.releases.size, visibleCount + 2)
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = Color.Transparent,
                                border = BorderStroke(1.dp, outlineColor),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .height(42.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Text(
                                        text = "View earlier versions ($countLabel)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = accentColor
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = null,
                                        tint = accentColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // License Dialog
    if (showLicenseDialog) {
        AlertDialog(
            onDismissRequest = { showLicenseDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(20.dp),
            title = {
                Text(
                    "MIT License",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    text = "Copyright (c) 2026 Noir / Zy0x\n\nPermission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(onClick = { showLicenseDialog = false }) {
                    Text(stringResource(R.string.common_close), color = MaterialTheme.colorScheme.primary)
                }
            }
        )
    }
}

@Composable
private fun CompactChangelogReleaseCard(
    release: ChangelogRelease,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val emeraldColor = adaptiveEmerald()
    val cardBg = if (isDark) Color(0xFF131620) else Color(0xFFFAF8F5)
    val cardBorder = if (isDark) Color(0xFF232838) else Color(0xFFE5E2DC)
    val secondaryTextColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF57534E)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = cardBg,
        border = BorderStroke(1.dp, cardBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Version Header & Date Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Version Pill: vX.X.X solid #4F46E5, white text 11sp bold
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF4F46E5)
                    ) {
                        Text(
                            text = release.version,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                        )
                    }

                    // Latest Badge
                    if (release.isLatest) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = emeraldColor.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, emeraldColor.copy(alpha = 0.35f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(5.dp)
                                        .background(emeraldColor, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Latest",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = emeraldColor
                                )
                            }
                        }
                    }
                }

                // Date
                Text(
                    text = release.releaseDate,
                    fontSize = 11.sp,
                    color = secondaryTextColor
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Release Summary (13sp bold)
            Text(
                text = release.summary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            // Direct Categories (UI/UX, SYSTEM, FIXED, ADDED, IMPROVED)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                release.categories.forEach { catChange ->
                    val catColor = catChange.category.accentColor()

                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Category Chip/Title: 11sp bold all-caps
                        Text(
                            text = catChange.category.displayName.uppercase(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = catColor,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )

                        // Bullet Items: • Judul: Deskripsi
                        catChange.features.forEach { feature ->
                            val descText = feature.details.joinToString(" ")
                            Row(
                                verticalAlignment = Alignment.Top,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                            ) {
                                Text(
                                    text = "• ",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = secondaryTextColor,
                                    modifier = Modifier.padding(top = 0.5.dp)
                                )
                                val annotated = buildAnnotatedString {
                                    withStyle(
                                        SpanStyle(
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    ) {
                                        append("${feature.title}: ")
                                    }
                                    withStyle(
                                        SpanStyle(
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Normal,
                                            color = secondaryTextColor
                                        )
                                    ) {
                                        append(descText)
                                    }
                                }
                                Text(
                                    text = annotated,
                                    lineHeight = 15.sp,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
