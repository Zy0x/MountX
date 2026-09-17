package app.mountify.ui.games

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mountify.R
import app.mountify.data.model.InstalledAppInfo
import app.mountify.data.model.MountMode
import app.mountify.data.model.SmartGamePresets
import app.mountify.ui.components.AppIconImage
import app.mountify.ui.components.CompactScreenHeader
import app.mountify.ui.theme.AuroraGradientBrush
import app.mountify.ui.theme.CyberEmerald
import app.mountify.ui.theme.NeonCrimson

/**
 * Dedicated Full-Screen Application Picker for Mountify.
 * - Displays User Apps by default in pure alphabetical order (A-Z)
 * - Top-right MoreVert menu to toggle system apps visibility
 * - Floating Action Button (pencil icon) in bottom-right for custom/manual game entry
 * - Ultra-minimalist compact warning dialog for system applications
 */
@Composable
fun AddAppPicker(
    installedApps: List<InstalledAppInfo>,
    onDismiss: () -> Unit,
    onAdd: (packageName: String, displayName: String, mode: MountMode) -> Unit,
    modifier: Modifier = Modifier
) {
    var isManualMode by remember { mutableStateOf(false) }
    var selectedApp by remember { mutableStateOf<InstalledAppInfo?>(null) }
    var pendingSystemApp by remember { mutableStateOf<InstalledAppInfo?>(null) }
    var showSystemApps by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }

    // Manual input fields
    var manualPackage by remember { mutableStateOf("") }
    var manualName by remember { mutableStateOf("") }
    var selectedMode by remember { mutableStateOf(MountMode.PKG) }

    // Real-time search query
    var searchQuery by remember { mutableStateOf("") }

    val baseApps = remember(installedApps, showSystemApps) {
        val list = if (showSystemApps) {
            installedApps
        } else {
            installedApps.filter { !it.isSystemApp }
        }
        list.sortedBy { it.displayName.lowercase() }
    }

    val filteredApps = remember(baseApps, searchQuery) {
        if (searchQuery.isBlank()) {
            baseApps
        } else {
            val query = searchQuery.trim()
            baseApps.filter { app ->
                app.displayName.contains(query, ignoreCase = true) ||
                    app.packageName.contains(query, ignoreCase = true)
            }
        }
    }

    val handleBackPress: () -> Unit = {
        when {
            pendingSystemApp != null -> pendingSystemApp = null
            selectedApp != null -> selectedApp = null
            isManualMode -> isManualMode = false
            searchQuery.isNotEmpty() -> searchQuery = ""
            else -> onDismiss()
        }
    }

    BackHandler(onBack = handleBackPress)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Standard Compact Screen Header
        CompactScreenHeader(
            title = when {
                selectedApp != null -> stringResource(R.string.add_app_configure_title, selectedApp!!.displayName)
                isManualMode -> stringResource(R.string.add_app_manual_title)
                else -> stringResource(R.string.add_app_title)
            },
            navigationIcon = {
                IconButton(
                    onClick = handleBackPress,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.common_close),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp)
                    )
                }
            },
            actions = {
                if (selectedApp == null && !isManualMode) {
                    Box {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Menu",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                            shape = RoundedCornerShape(8.dp),
                            containerColor = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                            shadowElevation = 3.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .clickable {
                                        showSystemApps = !showSystemApps
                                        menuExpanded = false
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.add_app_menu_show_system),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (showSystemApps) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when {
                selectedApp != null -> {
                    val app = selectedApp!!
                    ConfigureAppView(
                        app = app,
                        selectedMode = selectedMode,
                        onModeChange = { selectedMode = it },
                        onConfirm = { onAdd(app.packageName, app.displayName, selectedMode) }
                    )
                }

                isManualMode -> {
                    ManualAppView(
                        manualPackage = manualPackage,
                        onPackageChange = {
                            manualPackage = it
                            if (manualName.isBlank()) manualName = it
                        },
                        manualName = manualName,
                        onNameChange = { manualName = it },
                        selectedMode = selectedMode,
                        onModeChange = { selectedMode = it },
                        onConfirm = {
                            if (manualPackage.isNotBlank()) {
                                onAdd(manualPackage.trim(), manualName.trim(), selectedMode)
                            }
                        }
                    )
                }

                else -> {
                    BrowseAppListView(
                        installedApps = installedApps,
                        filteredApps = filteredApps,
                        searchQuery = searchQuery,
                        onSearchQueryChange = { searchQuery = it },
                        onAppSelected = { app ->
                            if (app.isSystemApp) {
                                pendingSystemApp = app
                            } else {
                                selectedApp = app
                            }
                        }
                    )

                    // Floating Action Button for Manual/Custom Game Input
                    FloatingActionButton(
                        onClick = { isManualMode = true },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 16.dp, bottom = 16.dp)
                            .size(46.dp),
                        shape = CircleShape,
                        containerColor = Color.Transparent,
                        contentColor = Color.White,
                        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(brush = AuroraGradientBrush, shape = CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = stringResource(R.string.add_app_manual_title),
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // Ultra-Minimalist System App Warning Dialog
    if (pendingSystemApp != null) {
        val sysApp = pendingSystemApp!!
        AlertDialog(
            onDismissRequest = { pendingSystemApp = null },
            shape = RoundedCornerShape(12.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = NeonCrimson,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.add_app_system_warning_compact_title),
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            text = {
                Text(
                    text = stringResource(
                        R.string.add_app_system_warning_compact_desc,
                        sysApp.displayName,
                        sysApp.packageName
                    ),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        selectedApp = sysApp
                        pendingSystemApp = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCrimson),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(34.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                ) {
                    Text(
                        text = stringResource(R.string.add_app_system_warning_proceed),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { pendingSystemApp = null },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(34.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                ) {
                    Text(
                        text = stringResource(R.string.common_cancel),
                        fontSize = 11.5.sp
                    )
                }
            }
        )
    }
}

/**
 * Backward compatibility wrapper for AddGameSheet callers.
 */
@Composable
fun AddGameSheet(
    installedApps: List<InstalledAppInfo>,
    onDismiss: () -> Unit,
    onAdd: (packageName: String, displayName: String, mode: MountMode) -> Unit,
    modifier: Modifier = Modifier
) {
    AddAppPicker(
        installedApps = installedApps,
        onDismiss = onDismiss,
        onAdd = onAdd,
        modifier = modifier
    )
}

@Composable
private fun BrowseAppListView(
    installedApps: List<InstalledAppInfo>,
    filteredApps: List<InstalledAppInfo>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onAppSelected: (InstalledAppInfo) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp)
    ) {
        // Standard Compact Search Bar (height 38dp)
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                    modifier = Modifier.size(15.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = stringResource(R.string.add_app_search_hint),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                            ),
                            maxLines = 1
                        )
                    }

                    BasicTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = { onSearchQueryChange("") },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = stringResource(R.string.common_cancel),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Application List Content (starts immediately under search bar)
        if (installedApps.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    strokeWidth = 2.5.dp
                )
            }
        } else if (filteredApps.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = stringResource(R.string.add_game_no_apps_found),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
                contentPadding = PaddingValues(bottom = 76.dp)
            ) {
                items(filteredApps, key = { it.packageName }) { app ->
                    val hasPreset = remember(app.packageName) {
                        SmartGamePresets.findPreset(app.packageName) != null
                    }

                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (app.isSystemApp) {
                                NeonCrimson.copy(alpha = 0.28f)
                            } else {
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onAppSelected(app) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            AppIconImage(
                                packageName = app.packageName,
                                size = 36.dp
                            )

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Text(
                                        text = app.displayName,
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )

                                    if (app.isSystemApp) {
                                        Surface(
                                            shape = RoundedCornerShape(3.dp),
                                            color = NeonCrimson.copy(alpha = 0.14f),
                                            border = BorderStroke(1.dp, NeonCrimson.copy(alpha = 0.35f))
                                        ) {
                                            Text(
                                                text = stringResource(R.string.add_app_tag_system),
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp),
                                                fontWeight = FontWeight.Bold,
                                                color = NeonCrimson,
                                                modifier = Modifier.padding(horizontal = 3.5.dp, vertical = 1.dp)
                                            )
                                        }
                                    } else if (app.isGame) {
                                        Surface(
                                            shape = RoundedCornerShape(3.dp),
                                            color = CyberEmerald.copy(alpha = 0.14f),
                                            border = BorderStroke(1.dp, CyberEmerald.copy(alpha = 0.35f))
                                        ) {
                                            Text(
                                                text = stringResource(R.string.add_app_tag_game),
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp),
                                                fontWeight = FontWeight.Bold,
                                                color = CyberEmerald,
                                                modifier = Modifier.padding(horizontal = 3.5.dp, vertical = 1.dp)
                                            )
                                        }
                                    }

                                    if (hasPreset) {
                                        Surface(
                                            shape = RoundedCornerShape(3.dp),
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                                        ) {
                                            Text(
                                                text = "SMART",
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp),
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 3.5.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(1.dp))

                                Text(
                                    text = app.packageName,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 10.5.sp,
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfigureAppView(
    app: InstalledAppInfo,
    selectedMode: MountMode,
    onModeChange: (MountMode) -> Unit,
    onConfirm: () -> Unit
) {
    val preset = remember(app.packageName) { SmartGamePresets.findPreset(app.packageName) }

    LaunchedEffect(preset) {
        if (preset != null) {
            onModeChange(preset.recommendedMode)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp)
    ) {
        // App Hero Card
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AppIconImage(packageName = app.packageName, size = 40.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = app.displayName,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (app.isSystemApp) {
                            Surface(
                                shape = RoundedCornerShape(3.dp),
                                color = NeonCrimson.copy(alpha = 0.14f),
                                border = BorderStroke(1.dp, NeonCrimson.copy(alpha = 0.35f))
                            ) {
                                Text(
                                    text = stringResource(R.string.add_app_tag_system),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp),
                                    fontWeight = FontWeight.Bold,
                                    color = NeonCrimson,
                                    modifier = Modifier.padding(horizontal = 3.5.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = app.packageName,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 10.5.sp,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Smart Preset Banner
        if (preset != null) {
            Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = CyberEmerald.copy(alpha = 0.12f)),
                border = BorderStroke(1.dp, CyberEmerald.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = CyberEmerald,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = stringResource(R.string.add_game_smart_preset) + ": ${preset.recommendedMode.name}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = CyberEmerald
                        )
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = preset.reason,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        Text(
            text = stringResource(R.string.add_game_mode_title),
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Mode Cards
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // PKG Mode Card
            Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (selectedMode == MountMode.PKG)
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                    else
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ),
                border = BorderStroke(
                    1.dp,
                    if (selectedMode == MountMode.PKG) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onModeChange(MountMode.PKG) }
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    RadioButton(
                        selected = selectedMode == MountMode.PKG,
                        onClick = { onModeChange(MountMode.PKG) },
                        modifier = Modifier.size(18.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "PKG Mode",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = if (selectedMode == MountMode.PKG) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(1.dp))
                        Text(
                            text = stringResource(R.string.add_game_mode_pkg_desc),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            // FILES Mode Card
            Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (selectedMode == MountMode.FILES)
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                    else
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ),
                border = BorderStroke(
                    1.dp,
                    if (selectedMode == MountMode.FILES) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onModeChange(MountMode.FILES) }
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    RadioButton(
                        selected = selectedMode == MountMode.FILES,
                        onClick = { onModeChange(MountMode.FILES) },
                        modifier = Modifier.size(18.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "FILES Mode",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = if (selectedMode == MountMode.FILES) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(1.dp))
                        Text(
                            text = stringResource(R.string.add_game_mode_files_desc),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = onConfirm,
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .background(
                    brush = AuroraGradientBrush,
                    shape = RoundedCornerShape(10.dp)
                ),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = Color.White
            ),
            contentPadding = PaddingValues(0.dp)
        ) {
            Text(
                text = stringResource(R.string.add_game_button),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = Color.White
            )
        }
    }
}

@Composable
private fun ManualAppView(
    manualPackage: String,
    onPackageChange: (String) -> Unit,
    manualName: String,
    onNameChange: (String) -> Unit,
    selectedMode: MountMode,
    onModeChange: (MountMode) -> Unit,
    onConfirm: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp)
    ) {
        OutlinedTextField(
            value = manualPackage,
            onValueChange = onPackageChange,
            label = { Text(stringResource(R.string.add_game_package_label), fontSize = 11.5.sp) },
            placeholder = { Text("com.example.app", fontSize = 11.5.sp) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(10.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = manualName,
            onValueChange = onNameChange,
            label = { Text(stringResource(R.string.add_game_name_label), fontSize = 11.5.sp) },
            placeholder = { Text("Application Name", fontSize = 11.5.sp) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(10.dp)
        )

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = stringResource(R.string.add_game_mode_title),
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedMode == MountMode.PKG,
                onClick = { onModeChange(MountMode.PKG) },
                label = { Text("PKG Mode", fontSize = 11.5.sp) },
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = selectedMode == MountMode.FILES,
                onClick = { onModeChange(MountMode.FILES) },
                label = { Text("FILES Mode", fontSize = 11.5.sp) },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = onConfirm,
            enabled = manualPackage.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .background(
                    brush = if (manualPackage.isNotBlank()) AuroraGradientBrush else SolidColor(Color.Gray.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(10.dp)
                ),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = Color.White,
                disabledContainerColor = Color.Transparent,
                disabledContentColor = Color.White.copy(alpha = 0.4f)
            ),
            contentPadding = PaddingValues(0.dp)
        ) {
            Text(
                text = stringResource(R.string.add_game_button),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = if (manualPackage.isNotBlank()) Color.White else Color.White.copy(alpha = 0.4f)
            )
        }
    }
}
