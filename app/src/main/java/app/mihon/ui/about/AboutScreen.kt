package app.mihon.ui.about

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.mihon.BuildConfig
import app.mihon.R
import app.mihon.ui.components.SectionHeader
import app.mihon.ui.theme.PrimaryBlue
import app.mihon.ui.theme.SecondaryTeal
import app.mihon.ui.theme.StatusSuccess

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
            TopAppBar(
                title = { Text(stringResource(R.string.about_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null)
                    }
                }
            )
        },
        modifier = modifier
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // App Header & Logo
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Icon(
                    painter = painterResource(id = R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    tint = PrimaryBlue,
                    modifier = Modifier.size(80.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.app_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            // Update Checker Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Software Updates",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (isChecking) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        if (updateInfo != null) {
                            if (updateInfo!!.isUpdateAvailable) {
                                Text(
                                    text = stringResource(R.string.about_update_available, updateInfo!!.latestVersion),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(updateInfo!!.downloadUrl))
                                        context.startActivity(intent)
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(stringResource(R.string.about_download_update))
                                }
                            } else {
                                Text(
                                    text = stringResource(R.string.about_up_to_date),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = StatusSuccess
                                )
                            }
                        } else if (checkError != null) {
                            Text(
                                text = checkError!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = { viewModel.checkForUpdate() },
                            enabled = !isChecking,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.about_update_check))
                        }
                    }
                }
            }

            // Links & Resources
            item {
                SectionHeader(title = "Resources")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column {
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.about_github)) },
                            supportingContent = { Text("https://github.com/Zy0x/Mountify") },
                            trailingContent = { Icon(Icons.Default.OpenInBrowser, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shadowElevation = 0.dp
                        )
                        HorizontalDivider()
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.about_changelog)) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
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
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
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
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
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
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(R.string.about_kofi))
                            }
                            FilledTonalButton(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://saweria.co/noir"))
                                    context.startActivity(intent)
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(R.string.about_saweria))
                            }
                            FilledTonalButton(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://paypal.me/noir"))
                                    context.startActivity(intent)
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(R.string.about_paypal))
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
            title = { Text(stringResource(R.string.about_changelog)) },
            text = {
                Column {
                    Text(text = "v1.0.0 (Initial Release)", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "• Native Jetpack Compose UI with Material You design")
                    Text(text = "• Full support for Magisk, KernelSU, and APatch")
                    Text(text = "• MicroSD partition formatting (F2FS, Ext4, exFAT, NTFS)")
                    Text(text = "• Bind-mount manager for games without FUSE errors")
                    Text(text = "• Physical game data migration (Internal <-> MicroSD)")
                    Text(text = "• Real-time log monitoring with live tail")
                    Text(text = "• JSON configuration backup and restore")
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
