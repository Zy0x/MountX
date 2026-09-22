package app.mountx.ui.apps

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.blur
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import app.mountx.root.RootShell
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mountx.R
import app.mountx.data.model.InstalledAppInfo
import app.mountx.data.model.MountMode
import app.mountx.data.model.SmartGamePresets
import app.mountx.ui.components.AppIconImage
import app.mountx.ui.components.CompactScreenHeader
import app.mountx.ui.theme.AuroraGradientBrush
import app.mountx.ui.theme.BadgeMountedBgDark
import app.mountx.ui.theme.BadgeMountedBgLight
import app.mountx.ui.theme.BadgeMountedTextDark
import app.mountx.ui.theme.BadgeMountedTextLight
import app.mountx.ui.theme.WarmCrimsonBgDark
import app.mountx.ui.theme.WarmCrimsonBgLight
import app.mountx.ui.theme.WarmCrimsonBorderDark
import app.mountx.ui.theme.WarmCrimsonBorderLight
import app.mountx.ui.theme.WarmCrimsonDark
import app.mountx.ui.theme.WarmCrimsonLight

/**
 * Dedicated Full-Screen Application Picker for MountX.
 *
 * Implemented as a full-screen layout embedded within the parent Scaffold,
 * avoiding window-in-window dialog bloat, floating margins, or sub-screen clipping.
 * - Top-right MoreVert menu to toggle system apps visibility
 * - Floating Action Button (pencil icon) in bottom-right for custom/manual game entry
 * - Ultra-minimalist compact warning dialog for system applications
 */
@Composable
fun AddAppPicker(
    installedApps: List<InstalledAppInfo>,
    addedPackageNames: Set<String> = emptySet(),
    onDismiss: () -> Unit,
    onAdd: (packageName: String, displayName: String, mode: MountMode) -> Unit,
    onConfigureApp: ((InstalledAppInfo) -> Unit)? = null,
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

    // Hierarchical Step-by-Step Back Navigation inside AddAppPicker
    BackHandler(enabled = pendingSystemApp != null) {
        pendingSystemApp = null
    }
    BackHandler(enabled = pendingSystemApp == null && selectedApp != null) {
        selectedApp = null
    }
    BackHandler(enabled = pendingSystemApp == null && selectedApp == null && isManualMode) {
        isManualMode = false
    }
    BackHandler(enabled = pendingSystemApp == null && selectedApp == null && !isManualMode && searchQuery.isNotEmpty()) {
        searchQuery = ""
    }
    BackHandler(enabled = pendingSystemApp == null && selectedApp == null && !isManualMode && searchQuery.isEmpty(), onBack = onDismiss)

    val baseApps = remember(installedApps, showSystemApps, addedPackageNames) {
        val list = if (showSystemApps) {
            installedApps
        } else {
            installedApps.filter { !it.isSystemApp }
        }
        list
            .filter { it.packageName !in addedPackageNames }
            .sortedBy { it.displayName.lowercase() }
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
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
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (showSystemApps) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null,
                                    tint = if (showSystemApps) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = stringResource(
                                        if (showSystemApps) R.string.add_app_menu_hide_system
                                        else R.string.add_app_menu_show_system
                                    ),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
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
                        installedApps = installedApps,
                        addedPackageNames = addedPackageNames,
                        onConfirm = { manualApp ->
                            if (onConfigureApp != null) {
                                onConfigureApp(manualApp)
                            } else {
                                onAdd(manualApp.packageName, manualApp.displayName, MountMode.PKG)
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
                                if (onConfigureApp != null) {
                                    onConfigureApp(app)
                                } else {
                                    selectedApp = app
                                }
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
                        containerColor = Color(0xFF4F46E5),
                        contentColor = Color.White,
                        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp)
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

    // Modern Glassmorphism / Blurred Backdrop System App Warning Dialog
    if (pendingSystemApp != null) {
        val sysApp = pendingSystemApp!!
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    pendingSystemApp = null
                },
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {},
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                shadowElevation = 8.dp
            ) {
                val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
                val crimsonColor = if (isDark) WarmCrimsonDark else WarmCrimsonLight
                val crimsonBg = if (isDark) WarmCrimsonBgDark else WarmCrimsonBgLight
                val crimsonBorder = if (isDark) WarmCrimsonBorderDark else WarmCrimsonBorderLight

                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Header with Warning Icon
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(crimsonBg, RoundedCornerShape(7.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = crimsonColor,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                        Text(
                            text = stringResource(R.string.add_app_system_warning_compact_title),
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Selected App Preview Mini Card (Two-tier layout: App Name up to 2 lines + Full-Width Package Container)
                    Surface(
                        shape = RoundedCornerShape(9.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(9.dp)
                            ) {
                                AppIconImage(
                                    packageName = sysApp.packageName,
                                    size = 36.dp
                                )
                                Text(
                                    text = sysApp.displayName,
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        lineHeight = 16.5.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Surface(
                                shape = RoundedCornerShape(5.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = sysApp.packageName,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 9.5.sp,
                                        fontFamily = FontFamily.Monospace,
                                        lineHeight = 13.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                    maxLines = 2,
                                    softWrap = true,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    // Risk Alert Callout Box
                    Surface(
                        shape = RoundedCornerShape(9.dp),
                        color = crimsonBg,
                        border = BorderStroke(1.dp, crimsonBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.add_app_system_warning_callout_desc),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 10.5.sp,
                                lineHeight = 14.5.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp)
                        )
                    }

                    // Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { pendingSystemApp = null },
                            shape = RoundedCornerShape(7.dp),
                            modifier = Modifier
                                .height(34.dp)
                                .defaultMinSize(minWidth = 68.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.common_cancel),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = {
                                if (onConfigureApp != null) {
                                    onConfigureApp(sysApp)
                                } else {
                                    selectedApp = sysApp
                                }
                                pendingSystemApp = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = crimsonColor),
                            shape = RoundedCornerShape(7.dp),
                            modifier = Modifier
                                .height(34.dp)
                                .defaultMinSize(minWidth = 72.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.add_app_system_warning_proceed),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
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
    onConfigureApp: ((InstalledAppInfo) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    AddAppPicker(
        installedApps = installedApps,
        onDismiss = onDismiss,
        onAdd = onAdd,
        onConfigureApp = onConfigureApp,
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
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp)
    ) {
        // Vertical spacing between TopAppBar and Search Bar
        Spacer(modifier = Modifier.height(10.dp))

        // Standard Compact Search Bar (height 38dp)
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = if (isDark) Color(0xFF1C1917) else Color(0xFFFAF8F5),
            border = BorderStroke(1.dp, if (isDark) Color(0xFF44403C) else Color(0xFFD6D3CD)),
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
            val onSelectCallback = remember<(InstalledAppInfo) -> Unit> {
                { app -> onAppSelected(app) }
            }

            val listState = rememberLazyListState()

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
                contentPadding = PaddingValues(start = 0.dp, end = 0.dp, top = 2.dp, bottom = 48.dp)
            ) {
                items(
                    items = filteredApps,
                    key = { it.packageName },
                    contentType = { "app_card" }
                ) { app ->
                    AppPickerItemCard(
                        app = app,
                        onSelect = onSelectCallback
                    )
                }
            }
        }
    }
}

@Composable
private fun AppPickerItemCard(
    app: InstalledAppInfo,
    onSelect: (InstalledAppInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    // Hoist expensive luminance calculation outside recomposition hot path
    val surfaceColor = MaterialTheme.colorScheme.surface
    val isDark = remember(surfaceColor) { surfaceColor.luminance() < 0.5f }
    val gameBadgeColor = remember(isDark) { if (isDark) BadgeMountedTextDark else BadgeMountedTextLight }
    val gameBadgeBg = remember(isDark) { if (isDark) BadgeMountedBgDark else BadgeMountedBgLight }
    val crimsonColor = remember(isDark) { if (isDark) WarmCrimsonDark else WarmCrimsonLight }
    val crimsonBg = remember(isDark) { if (isDark) WarmCrimsonBgDark else WarmCrimsonBgLight }
    val crimsonBorder = remember(isDark) { if (isDark) WarmCrimsonBorderDark else WarmCrimsonBorderLight }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(
            width = 1.dp,
            color = if (app.isSystemApp) {
                crimsonBorder
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            }
        ),
        modifier = modifier
            .fillMaxWidth()
            .clickable { onSelect(app) }
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
                        Box(
                            modifier = Modifier
                                .background(crimsonBg, RoundedCornerShape(3.dp))
                                .border(1.dp, crimsonBorder, RoundedCornerShape(3.dp))
                                .padding(horizontal = 3.5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.add_app_tag_system),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp),
                                fontWeight = FontWeight.Bold,
                                color = crimsonColor
                            )
                        }
                    } else if (app.isGame) {
                        Box(
                            modifier = Modifier
                                .background(gameBadgeBg, RoundedCornerShape(3.dp))
                                .border(1.dp, gameBadgeColor.copy(alpha = 0.35f), RoundedCornerShape(3.dp))
                                .padding(horizontal = 3.5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.add_app_tag_game),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp),
                                fontWeight = FontWeight.Bold,
                                color = gameBadgeColor
                            )
                        }
                    }

                    if (app.hasPreset) {
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), RoundedCornerShape(3.dp))
                                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), RoundedCornerShape(3.dp))
                                .padding(horizontal = 3.5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "SMART",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
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
                                fontWeight = FontWeight.Bold,
                                lineHeight = 17.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (app.isSystemApp) {
                            val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
                            val crimsonColor = if (isDark) WarmCrimsonDark else WarmCrimsonLight
                            val crimsonBg = if (isDark) WarmCrimsonBgDark else WarmCrimsonBgLight
                            val crimsonBorder = if (isDark) WarmCrimsonBorderDark else WarmCrimsonBorderLight
                            Surface(
                                shape = RoundedCornerShape(3.dp),
                                color = crimsonBg,
                                border = BorderStroke(1.dp, crimsonBorder)
                            ) {
                                Text(
                                    text = stringResource(R.string.add_app_tag_system),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp),
                                    fontWeight = FontWeight.Bold,
                                    color = crimsonColor,
                                    modifier = Modifier.padding(horizontal = 3.5.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = app.packageName,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 13.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                        maxLines = 2,
                        softWrap = true,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Smart Preset Banner
        if (preset != null) {
            val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
            val emeraldColor = if (isDark) BadgeMountedTextDark else BadgeMountedTextLight
            val emeraldBg = if (isDark) BadgeMountedBgDark else BadgeMountedBgLight
            Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = emeraldBg),
                border = BorderStroke(1.dp, emeraldColor.copy(alpha = 0.35f)),
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
                            tint = emeraldColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = stringResource(R.string.add_game_smart_preset) + ": ${preset.recommendedMode.name}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = emeraldColor
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

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onConfirm,
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF4F46E5),
                contentColor = Color.White
            ),
            contentPadding = PaddingValues(0.dp)
        ) {
            Text(
                text = stringResource(R.string.add_game_button),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = Color.White
            )
        }
    }
}

private sealed interface PackageValidationState {
    object Idle : PackageValidationState
    object Checking : PackageValidationState
    data class Installed(val appInfo: InstalledAppInfo) : PackageValidationState
    object AlreadyRegistered : PackageValidationState
    object NotInstalled : PackageValidationState
}

@Composable
private fun ManualAppView(
    installedApps: List<InstalledAppInfo>,
    addedPackageNames: Set<String>,
    onConfirm: (InstalledAppInfo) -> Unit
) {
    val context = LocalContext.current
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val fieldBg = if (isDark) Color(0xFF1C1917) else Color(0xFFFAF8F5)
    val fieldBorder = if (isDark) Color(0xFF44403C) else Color(0xFFD6D3CD)
    val fieldText = if (isDark) Color(0xFFFAF8F5) else Color(0xFF1C1917)
    val fieldPlaceholder = Color(0xFFA8A29E)

    var manualPackage by remember { mutableStateOf("") }
    var manualName by remember { mutableStateOf("") }
    var isNameManuallyEdited by remember { mutableStateOf(false) }
    var validationState by remember { mutableStateOf<PackageValidationState>(PackageValidationState.Idle) }

    LaunchedEffect(manualPackage) {
        val pkg = manualPackage.trim()
        if (pkg.isBlank()) {
            validationState = PackageValidationState.Idle
            if (!isNameManuallyEdited) manualName = ""
            return@LaunchedEffect
        }
        if (addedPackageNames.any { it.equals(pkg, ignoreCase = true) }) {
            validationState = PackageValidationState.AlreadyRegistered
            return@LaunchedEffect
        }

        validationState = PackageValidationState.Checking

        // 1. Fast cache check from already loaded installedApps
        val cached = installedApps.firstOrNull { it.packageName.equals(pkg, ignoreCase = true) }
        if (cached != null) {
            validationState = PackageValidationState.Installed(cached)
            if (!isNameManuallyEdited) {
                manualName = cached.displayName
            }
            return@LaunchedEffect
        }

        // 2. Query PackageManager for uninstalled / all users
        val resolved = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val app = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getApplicationInfo(
                        pkg,
                        PackageManager.ApplicationInfoFlags.of(
                            PackageManager.MATCH_UNINSTALLED_PACKAGES.toLong() or PackageManager.MATCH_ALL.toLong()
                        )
                    )
                } else {
                    @Suppress("DEPRECATION")
                    pm.getApplicationInfo(pkg, PackageManager.MATCH_UNINSTALLED_PACKAGES)
                }
            }.getOrNull()

            if (app != null) {
                val label = pm.getApplicationLabel(app).toString().ifBlank { pkg }
                val isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                    (app.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                val isGame = app.category == ApplicationInfo.CATEGORY_GAME ||
                    SmartGamePresets.findPreset(pkg) != null
                InstalledAppInfo(
                    packageName = pkg,
                    displayName = label,
                    isGame = isGame,
                    isSystemApp = isSystem,
                    hasPreset = SmartGamePresets.findPreset(pkg) != null
                )
            } else {
                // 3. Fallback Root Shell check for frozen, hidden, or isolated apps
                val pmPathRes = RootShell.exec("pm path $pkg")
                if (pmPathRes.isSuccess && pmPathRes.output.isNotBlank()) {
                    InstalledAppInfo(
                        packageName = pkg,
                        displayName = manualName.ifBlank { pkg },
                        isGame = false,
                        isSystemApp = false
                    )
                } else {
                    val existsInternal = RootShell.exists("/data/data/$pkg") || RootShell.exists("/data/user/0/$pkg")
                    if (existsInternal) {
                        InstalledAppInfo(
                            packageName = pkg,
                            displayName = manualName.ifBlank { pkg },
                            isGame = false,
                            isSystemApp = false
                        )
                    } else {
                        null
                    }
                }
            }
        }

        if (resolved != null) {
            validationState = PackageValidationState.Installed(resolved)
            if (!isNameManuallyEdited) {
                manualName = resolved.displayName
            }
        } else {
            validationState = PackageValidationState.NotInstalled
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val manualFieldColors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = fieldBg,
            unfocusedContainerColor = fieldBg,
            disabledContainerColor = fieldBg.copy(alpha = 0.5f),
            focusedBorderColor = if (isDark) Color(0xFF6366F1) else Color(0xFF4F46E5),
            unfocusedBorderColor = fieldBorder,
            focusedTextColor = fieldText,
            unfocusedTextColor = fieldText,
            focusedLabelColor = if (isDark) Color(0xFF818CF8) else Color(0xFF4F46E5),
            unfocusedLabelColor = fieldPlaceholder,
            focusedPlaceholderColor = fieldPlaceholder,
            unfocusedPlaceholderColor = fieldPlaceholder
        )

        // Package Name Input
        OutlinedTextField(
            value = manualPackage,
            onValueChange = { manualPackage = it },
            label = { Text(stringResource(R.string.add_game_package_label), fontSize = 11.5.sp) },
            placeholder = { Text("com.example.app", fontSize = 11.5.sp) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = manualFieldColors,
            trailingIcon = {
                if (manualPackage.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            manualPackage = ""
                            if (!isNameManuallyEdited) manualName = ""
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = null,
                            tint = fieldPlaceholder,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        )

        // Real-Time Validation Feedback Row
        when (val state = validationState) {
            PackageValidationState.Idle -> {
                Text(
                    text = stringResource(R.string.add_game_package_hint),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp),
                    color = fieldPlaceholder
                )
            }
            PackageValidationState.Checking -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = if (isDark) Color(0xFF818CF8) else Color(0xFF4F46E5)
                    )
                    Text(
                        text = stringResource(R.string.manual_app_status_checking),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = if (isDark) Color(0xFF818CF8) else Color(0xFF4F46E5)
                    )
                }
            }
            is PackageValidationState.Installed -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = if (isDark) BadgeMountedTextDark else BadgeMountedTextLight,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = stringResource(R.string.manual_app_status_detected),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                        color = if (isDark) BadgeMountedTextDark else BadgeMountedTextLight
                    )
                }

                // Live Preview Card
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, (if (isDark) BadgeMountedTextDark else BadgeMountedTextLight).copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AppIconImage(packageName = state.appInfo.packageName, size = 40.dp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = state.appInfo.displayName,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = state.appInfo.packageName,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 10.5.sp,
                                    fontFamily = FontFamily.Monospace
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (state.appInfo.isSystemApp) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isDark) WarmCrimsonBgDark else WarmCrimsonBgLight,
                                border = BorderStroke(1.dp, if (isDark) WarmCrimsonBorderDark else WarmCrimsonBorderLight)
                            ) {
                                Text(
                                    text = stringResource(R.string.add_app_tag_system),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                    color = if (isDark) WarmCrimsonDark else WarmCrimsonLight,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        } else if (state.appInfo.isGame) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isDark) Color(0xFF6366F1).copy(alpha = 0.15f) else Color(0xFF4F46E5).copy(alpha = 0.10f),
                                border = BorderStroke(1.dp, if (isDark) Color(0xFF818CF8).copy(alpha = 0.4f) else Color(0xFF4F46E5).copy(alpha = 0.3f))
                            ) {
                                Text(
                                    text = stringResource(R.string.add_app_tag_game),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                    color = if (isDark) Color(0xFF818CF8) else Color(0xFF4F46E5),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
            PackageValidationState.AlreadyRegistered -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (isDark) WarmCrimsonDark else WarmCrimsonLight,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = stringResource(R.string.manual_app_status_already_added),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
                        color = if (isDark) WarmCrimsonDark else WarmCrimsonLight
                    )
                }
            }
            PackageValidationState.NotInstalled -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Cancel,
                        contentDescription = null,
                        tint = if (isDark) WarmCrimsonDark else WarmCrimsonLight,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = stringResource(R.string.manual_app_status_not_found),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
                        color = if (isDark) WarmCrimsonDark else WarmCrimsonLight
                    )
                }
            }
        }

        // Display Name Input (Editable, auto-filled)
        OutlinedTextField(
            value = manualName,
            onValueChange = {
                manualName = it
                isNameManuallyEdited = true
            },
            label = { Text(stringResource(R.string.add_game_name_label), fontSize = 11.5.sp) },
            placeholder = { Text("Application Name", fontSize = 11.5.sp) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = manualFieldColors
        )

        // Informative Helper Card
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = if (isDark) Color(0xFF818CF8) else Color(0xFF4F46E5),
                    modifier = Modifier.size(16.dp).padding(top = 1.dp)
                )
                Text(
                    text = stringResource(R.string.manual_app_desc),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp, lineHeight = 14.5.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Confirm Button
        val isConfirmedEnabled = validationState is PackageValidationState.Installed && manualPackage.isNotBlank()
        Button(
            onClick = {
                val state = validationState
                if (state is PackageValidationState.Installed) {
                    val appToConfirm = state.appInfo.copy(
                        displayName = manualName.trim().ifBlank { state.appInfo.displayName }
                    )
                    onConfirm(appToConfirm)
                }
            },
            enabled = isConfirmedEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF4F46E5),
                contentColor = Color.White,
                disabledContainerColor = Color(0xFF4F46E5).copy(alpha = 0.35f),
                disabledContentColor = Color.White.copy(alpha = 0.4f)
            ),
            contentPadding = PaddingValues(0.dp)
        ) {
            Text(
                text = stringResource(R.string.add_game_button),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = if (isConfirmedEnabled) Color.White else Color.White.copy(alpha = 0.4f)
            )
        }
    }
}
