package app.mountx.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import app.mountx.R
import app.mountx.ui.components.CompactScreenHeader
import app.mountx.ui.components.ConfirmDialog
import app.mountx.ui.components.SectionHeader
import app.mountx.ui.theme.CyberEmerald
import app.mountx.ui.theme.NeonCrimson
import app.mountx.util.PermissionManager
import app.mountx.util.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateToAbout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val themeMode by viewModel.themeMode.collectAsState()
    val language by viewModel.language.collectAsState()
    val autoMount by viewModel.autoMountOnBoot.collectAsState()

    val isExporting by viewModel.isExporting.collectAsState()
    val isImporting by viewModel.isImporting.collectAsState()
    val portabilityMessage by viewModel.portabilityMessage.collectAsState()
    val pendingImportSummary by viewModel.pendingImportSummary.collectAsState()

    var showResetDialog by remember { mutableStateOf(false) }
    var showEmergencyPanicDialog by remember { mutableStateOf(false) }
    var showPermissionSheet by remember { mutableStateOf(false) }
    val isExecutingRescue by viewModel.isExecutingRescue.collectAsState()
    val rescueMessage by viewModel.rescueMessage.collectAsState()

    val exportSnapshotLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let { viewModel.exportFullSnapshot(it) }
    }

    val importSnapshotLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.validateAndPrepareImport(it) }
    }

    var permState by remember {
        mutableStateOf(PermissionManager.checkAllPermissions(context))
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permState = PermissionManager.checkAllPermissions(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        topBar = {
            CompactScreenHeader(
                title = stringResource(R.string.settings_title),
                actions = {
                    IconButton(
                        onClick = onNavigateToAbout,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = stringResource(R.string.about_title),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            )
        },
        snackbarHost = {
            val messageToShow = rescueMessage ?: portabilityMessage?.let { msg ->
                when {
                    msg == "SNAPSHOT_EXPORT_OK" -> stringResource(R.string.settings_portability_export_success)
                    msg.startsWith("SNAPSHOT_IMPORT_OK:") -> {
                        val count = msg.substringAfter("SNAPSHOT_IMPORT_OK:").toIntOrNull() ?: 0
                        stringResource(R.string.settings_portability_import_success, count)
                    }
                    else -> msg
                }
            }
            if (messageToShow != null) {
                Snackbar(
                    action = {
                        TextButton(onClick = {
                            viewModel.clearRescueMessage()
                            viewModel.clearPortabilityMessage()
                        }) {
                            Text(stringResource(R.string.common_ok), color = MaterialTheme.colorScheme.primary)
                        }
                    },
                    modifier = Modifier.padding(14.dp)
                ) {
                    Text(messageToShow)
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 2.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Appearance
            item {
                SectionHeader(title = stringResource(R.string.settings_appearance))
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = stringResource(R.string.settings_theme),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = themeMode == ThemeMode.LIGHT,
                                onClick = { viewModel.setThemeMode(ThemeMode.LIGHT) },
                                label = {
                                    Text(
                                        text = stringResource(R.string.settings_theme_light),
                                        fontSize = 11.sp,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = themeMode == ThemeMode.DARK,
                                onClick = { viewModel.setThemeMode(ThemeMode.DARK) },
                                label = {
                                    Text(
                                        text = stringResource(R.string.settings_theme_dark),
                                        fontSize = 11.sp,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = themeMode == ThemeMode.SYSTEM,
                                onClick = { viewModel.setThemeMode(ThemeMode.SYSTEM) },
                                label = {
                                    Text(
                                        text = stringResource(R.string.settings_theme_system),
                                        fontSize = 11.sp,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Language
            item {
                SectionHeader(title = stringResource(R.string.settings_language))
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = language == "en",
                                onClick = { viewModel.setLanguage("en") },
                                label = {
                                    Text(
                                        text = stringResource(R.string.settings_language_en),
                                        fontSize = 11.sp,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = language == "id",
                                onClick = { viewModel.setLanguage("id") },
                                label = {
                                    Text(
                                        text = stringResource(R.string.settings_language_id),
                                        fontSize = 11.sp,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Behavior
            item {
                SectionHeader(title = stringResource(R.string.settings_behavior))
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_auto_mount),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.settings_auto_mount_desc),
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                        Switch(
                            checked = autoMount,
                            onCheckedChange = { viewModel.setAutoMountOnBoot(it) }
                        )
                    }
                }
            }

            // Permissions & System Access Section
            item {
                SectionHeader(title = stringResource(R.string.settings_permissions_title))
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(
                        1.dp,
                        if (permState.areAllGranted) CyberEmerald.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outline
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showPermissionSheet = true }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = stringResource(R.string.settings_permissions_title),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (permState.areAllGranted) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = CyberEmerald.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = "Aktif",
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = CyberEmerald,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (permState.areAllGranted) {
                                    "Seluruh izin sistem dan hak akses telah aktif secara optimal."
                                } else {
                                    stringResource(R.string.settings_permissions_desc)
                                },
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = if (permState.areAllGranted) {
                                    CyberEmerald.copy(alpha = 0.85f)
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                }
                            )
                        }
                        if (permState.areAllGranted) {
                            Surface(
                                shape = androidx.compose.foundation.shape.CircleShape,
                                color = CyberEmerald.copy(alpha = 0.15f),
                                modifier = Modifier.size(28.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Semua Izin Diberikan",
                                        tint = CyberEmerald,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        } else {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // ── Configuration Portability Section ──
            item {
                SectionHeader(title = stringResource(R.string.settings_portability_title))
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.settings_portability_desc),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val timestamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
                                    exportSnapshotLauncher.launch("mountx_backup_$timestamp.json")
                                },
                                enabled = !isExporting && !isImporting,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FileDownload,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        stringResource(R.string.settings_portability_export),
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    importSnapshotLauncher.launch(arrayOf("application/json"))
                                },
                                enabled = !isExporting && !isImporting,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FileUpload,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp),
                                        tint = Color.White
                                    )
                                    Text(
                                        stringResource(R.string.settings_portability_import),
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Emergency Rescue Section
            item {
                SectionHeader(title = stringResource(R.string.settings_emergency_title))
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, NeonCrimson.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.settings_emergency_desc),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.generateRescueScript() },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Text(
                                    stringResource(R.string.settings_emergency_generate_script),
                                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                )
                            }

                            Button(
                                onClick = { showEmergencyPanicDialog = true },
                                enabled = !isExecutingRescue,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = NeonCrimson),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Text(
                                    stringResource(R.string.settings_emergency_panic_confirm_title),
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Advanced / Reset
            item {
                SectionHeader(title = stringResource(R.string.settings_advanced))
                OutlinedButton(
                    onClick = { showResetDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                ) {
                    Text(
                        stringResource(R.string.settings_reset),
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    if (showResetDialog) {
        ConfirmDialog(
            title = stringResource(R.string.settings_reset),
            message = stringResource(R.string.settings_reset_confirm),
            isDestructive = true,
            confirmText = stringResource(R.string.common_confirm),
            onConfirm = {
                viewModel.resetSettings()
                showResetDialog = false
            },
            onDismiss = { showResetDialog = false }
        )
    }

    if (showEmergencyPanicDialog) {
        ConfirmDialog(
            title = stringResource(R.string.settings_emergency_panic_confirm_title),
            message = stringResource(R.string.settings_emergency_panic_confirm_msg),
            isDestructive = true,
            confirmText = stringResource(R.string.common_confirm),
            onConfirm = {
                viewModel.executeEmergencyReset()
                showEmergencyPanicDialog = false
            },
            onDismiss = { showEmergencyPanicDialog = false }
        )
    }

    if (showPermissionSheet) {
        app.mountx.ui.components.PermissionOnboardingSheet(
            onDismiss = { showPermissionSheet = false }
        )
    }

    pendingImportSummary?.let { summary ->
        AlertDialog(
            onDismissRequest = { viewModel.clearPendingImport() },
            title = {
                Text(
                    text = stringResource(R.string.settings_portability_import_dialog_title),
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.5.sp, fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(R.string.settings_portability_import_dialog_msg, summary.appCount, summary.deviceModel),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (summary.appNames.isNotEmpty()) {
                        val preview = summary.appNames.take(5).joinToString(", ") + if (summary.appNames.size > 5) "..." else ""
                        Text(
                            text = "Aplikasi: $preview",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Option 1: Merge
                    Card(
                        onClick = { viewModel.executeImport(ImportMode.MERGE) },
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = stringResource(R.string.settings_portability_merge_btn),
                                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = stringResource(R.string.settings_portability_merge_desc),
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Option 2: Replace All
                    Card(
                        onClick = { viewModel.executeImport(ImportMode.REPLACE_ALL) },
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        border = BorderStroke(1.dp, NeonCrimson.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = stringResource(R.string.settings_portability_replace_btn),
                                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                                color = NeonCrimson
                            )
                            Text(
                                text = stringResource(R.string.settings_portability_replace_desc),
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { viewModel.clearPendingImport() }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}
