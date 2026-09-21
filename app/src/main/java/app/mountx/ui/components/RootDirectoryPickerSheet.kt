package app.mountx.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mountx.R
import app.mountx.root.RootShell
import app.mountx.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DirectoryItem(
    val name: String,
    val isParent: Boolean = false
)

private data class QuickStorageChip(
    val label: String,
    val path: String,
    val icon: ImageVector
)

/**
 * Root File Explorer Modal Bottom Sheet (ala MT-Manager)
 * Allows browsing the full root filesystem, navigating via interactive breadcrumbs,
 * jumping between physical and virtual partitions, and selecting a valid folder.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RootDirectoryPickerSheet(
    initialPath: String = "/data/media/0",
    sdBasePath: String = "/data/sdext2",
    onDismiss: () -> Unit,
    onPathSelected: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    var currentPath by remember {
        mutableStateOf(if (initialPath.isNotBlank() && initialPath.startsWith("/")) initialPath.trimEnd('/') else "/data/media/0")
    }
    var directories by remember { mutableStateOf<List<DirectoryItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showHighRiskDialog by remember { mutableStateOf(false) }

    // Quick access shortcut definitions
    val quickShortcuts = remember(sdBasePath) {
        listOf(
            QuickStorageChip("Internal", "/data/media/0", Icons.Default.Smartphone),
            QuickStorageChip("MicroSD", sdBasePath, Icons.Default.SdCard),
            QuickStorageChip("Root (/)", "/", Icons.Default.Terminal),
            QuickStorageChip("App Data", "/data/data", Icons.Default.FolderSpecial),
            QuickStorageChip("OTG", "/mnt/media_rw", Icons.Default.Usb)
        )
    }

    // Load directories whenever currentPath changes
    LaunchedEffect(currentPath) {
        isLoading = true
        errorMessage = null
        val path = if (currentPath.isEmpty()) "/" else currentPath
        val items = withContext(Dispatchers.IO) {
            try {
                val res = RootShell.exec("ls -1pa \"$path\" 2>/dev/null")
                if (!res.isSuccess && res.output.contains("Permission denied", ignoreCase = true)) {
                    null
                } else {
                    val list = mutableListOf<DirectoryItem>()
                    res.stdout.forEach { line ->
                        val clean = line.trim()
                        if (clean.endsWith("/") && clean != "./" && clean != "../") {
                            val dirName = clean.removeSuffix("/")
                            list.add(DirectoryItem(name = dirName))
                        }
                    }
                    list.sortedBy { it.name.lowercase() }
                }
            } catch (e: Exception) {
                null
            }
        }
        if (items == null) {
            errorMessage = "Akses ditolak atau gagal membaca direktori"
            directories = emptyList()
        } else {
            directories = items
        }
        isLoading = false
    }

    // Hierarchical back handling inside the picker: Navigate up if not at root
    BackHandler {
        if (currentPath != "/" && currentPath.isNotEmpty()) {
            val parent = currentPath.substringBeforeLast('/').ifBlank { "/" }
            currentPath = parent
        } else {
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
        },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .navigationBarsPadding()
        ) {
            // Header: Title & Close
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = ElectricIndigo.copy(alpha = if (isDark) 0.15f else 0.10f),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                tint = if (isDark) ElectricIndigoLight else Color(0xFF4F46E5),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "Penjelajah Berkas Root",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) Color(0xFFFAF8F5) else Color(0xFF1C1917)
                        )
                        Text(
                            text = "Pilih direktori penyimpanan sistem atau kartu memori",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = if (isDark) Color(0xFFA8A29E) else Color(0xFF78716C)
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Tutup",
                        tint = if (isDark) Color(0xFFA8A29E) else Color(0xFF78716C)
                    )
                }
            }

            // Quick Access Chips Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                quickShortcuts.forEach { chip ->
                    val isSelected = currentPath == chip.path || currentPath.startsWith("${chip.path}/")
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) {
                            if (isDark) ElectricIndigo.copy(alpha = 0.25f) else Color(0xFF4F46E5).copy(alpha = 0.12f)
                        } else {
                            if (isDark) Color(0xFF1C1917) else Color(0xFFFAF8F5)
                        },
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) {
                                if (isDark) ElectricIndigo else Color(0xFF4F46E5)
                            } else {
                                if (isDark) Color(0xFF44403C) else Color(0xFFD6D3CD)
                            }
                        ),
                        modifier = Modifier.clickable {
                            currentPath = chip.path
                        }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = chip.icon,
                                contentDescription = null,
                                tint = if (isSelected) {
                                    if (isDark) ElectricIndigoLight else Color(0xFF4F46E5)
                                } else {
                                    if (isDark) Color(0xFFA8A29E) else Color(0xFF78716C)
                                },
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = chip.label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = if (isSelected) {
                                    if (isDark) ElectricIndigoLight else Color(0xFF4F46E5)
                                } else {
                                    if (isDark) Color(0xFFFAF8F5) else Color(0xFF1C1917)
                                }
                            )
                        }
                    }
                }
            }

            // Breadcrumb Navigation Bar
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(10.dp),
                color = if (isDark) Color(0xFF1C1917) else Color(0xFFFAF8F5),
                border = BorderStroke(1.dp, if (isDark) Color(0xFF44403C) else Color(0xFFD6D3CD))
            ) {
                val segments = remember(currentPath) {
                    if (currentPath == "/" || currentPath.isBlank()) listOf("") else currentPath.split("/").filter { it.isNotEmpty() }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Root token
                    Text(
                        text = "/",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        ),
                        color = if (currentPath == "/") (if (isDark) Color(0xFF4ADE80) else Color(0xFF15803D)) else (if (isDark) Color(0xFF818CF8) else Color(0xFF4F46E5)),
                        modifier = Modifier
                            .clickable { currentPath = "/" }
                            .padding(horizontal = 4.dp)
                    )

                    var accum = ""
                    segments.forEachIndexed { index, seg ->
                        if (seg.isNotEmpty()) {
                            accum += "/$seg"
                            val target = accum
                            val isLast = index == segments.size - 1

                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = if (isDark) Color(0xFFA8A29E) else Color(0xFF78716C),
                                modifier = Modifier.size(14.dp)
                            )

                            Text(
                                text = seg,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal
                                ),
                                color = if (isLast) {
                                    if (isDark) Color(0xFFFAF8F5) else Color(0xFF1C1917)
                                } else {
                                    if (isDark) Color(0xFF818CF8) else Color(0xFF4F46E5)
                                },
                                maxLines = 1,
                                modifier = Modifier
                                    .clickable { currentPath = target }
                                    .padding(horizontal = 4.dp)
                            )
                        }
                    }
                }
            }

            // Directory Content List
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = ElectricIndigoLight,
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 2.5.dp
                        )
                    }
                } else if (errorMessage != null) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = errorMessage!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isDark) WarmCrimsonDark else WarmCrimsonLight
                            )
                            OutlinedButton(
                                onClick = {
                                    val parent = currentPath.substringBeforeLast('/').ifBlank { "/" }
                                    currentPath = parent
                                },
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, if (isDark) OutlinedNeutralBorderDark else OutlinedNeutralBorderLight),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (isDark) OutlinedNeutralBgDark else OutlinedNeutralBgLight
                                )
                            ) {
                                Text(
                                    text = "Kembali ke Direktori Induk",
                                    color = if (isDark) OutlinedNeutralTextDark else OutlinedNeutralTextLight,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                } else if (directories.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "(Direktori kosong atau tidak ada subfolder)",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isDark) Color(0xFFA8A29E) else Color(0xFF78716C)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Up to Parent Item (..)
                        if (currentPath != "/" && currentPath.isNotEmpty()) {
                            item {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color.Transparent,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val parent = currentPath.substringBeforeLast('/').ifBlank { "/" }
                                            currentPath = parent
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = "Induk",
                                            tint = if (isDark) Color(0xFFA8A29E) else Color(0xFF78716C),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = ".. (Kembali ke folder induk)",
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontSize = 12.5.sp,
                                                fontWeight = FontWeight.Medium
                                            ),
                                            color = if (isDark) Color(0xFFA8A29E) else Color(0xFF78716C)
                                        )
                                    }
                                }
                            }
                        }

                        items(directories, key = { it.name }) { item ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isDark) Color(0xFF292524) else Color(0xFFF2EFE9),
                                border = BorderStroke(1.dp, if (isDark) Color(0xFF44403C) else Color(0xFFD6D3CD)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        currentPath = if (currentPath == "/") "/${item.name}" else "$currentPath/${item.name}"
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = Color(0xFFD97706),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = item.name,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium
                                        ),
                                        color = if (isDark) Color(0xFFFAF8F5) else Color(0xFF1C1917),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = null,
                                        tint = if (isDark) Color(0xFFA8A29E).copy(alpha = 0.5f) else Color(0xFF78716C).copy(alpha = 0.5f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Bottom Sticky Action Bar: Selected Path & Confirm Button
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = if (isDark) Color(0xFF1C1917) else Color(0xFFFAF8F5),
                border = BorderStroke(1.dp, if (isDark) Color(0xFF44403C) else Color(0xFFD6D3CD))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Path Display Box
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isDark) Color(0xFF0F1117) else Color(0xFFF2EFE9),
                        border = BorderStroke(0.8.dp, if (isDark) Color(0xFF44403C) else Color(0xFFD6D3CD)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Path:",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = if (isDark) Color(0xFFA8A29E) else Color(0xFF78716C)
                            )
                            Text(
                                text = currentPath,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = if (isDark) BadgeMountedTextDark else BadgeMountedTextLight,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    val securityLevel = remember(currentPath) { evaluatePathSecurity(currentPath) }

                    if (securityLevel == PathSecurityLevel.HARD_BLOCKED_KERNEL) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isDark) WarmCrimsonBgDark else WarmCrimsonBgLight,
                            border = BorderStroke(0.8.dp, if (isDark) WarmCrimsonBorderDark else WarmCrimsonBorderLight),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "⚠️ Virtual Kernel Filesystem dilarang untuk mounting",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                                color = if (isDark) WarmCrimsonDark else WarmCrimsonLight,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }

                    // Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, if (isDark) OutlinedNeutralBorderDark else OutlinedNeutralBorderLight),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isDark) OutlinedNeutralBgDark else OutlinedNeutralBgLight
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                        ) {
                            Text(
                                text = "Batal",
                                color = if (isDark) OutlinedNeutralTextDark else OutlinedNeutralTextLight,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Button(
                            onClick = {
                                if (securityLevel == PathSecurityLevel.HIGH_RISK_SYSTEM) {
                                    showHighRiskDialog = true
                                } else {
                                    onPathSelected(currentPath)
                                    onDismiss()
                                }
                            },
                            enabled = securityLevel != PathSecurityLevel.HARD_BLOCKED_KERNEL,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (securityLevel == PathSecurityLevel.HIGH_RISK_SYSTEM) {
                                    if (isDark) WarmCrimsonDark else WarmCrimsonLight
                                } else {
                                    if (isDark) ElectricIndigo else Color(0xFF4F46E5)
                                },
                                disabledContainerColor = if (isDark) Color(0xFF292524) else Color(0xFFE5E7EB)
                            ),
                            modifier = Modifier
                                .weight(1.5f)
                                .height(44.dp)
                        ) {
                            Text(
                                text = if (securityLevel == PathSecurityLevel.HIGH_RISK_SYSTEM) "Pilih (Risiko Tinggi)" else "Pilih Folder Ini",
                                color = if (securityLevel == PathSecurityLevel.HARD_BLOCKED_KERNEL) (if (isDark) Color(0xFFA8A29E) else Color(0xFF9CA3AF)) else Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }

    if (showHighRiskDialog) {
        HighRiskDirectoryConfirmDialog(
            path = currentPath,
            onDismiss = { showHighRiskDialog = false },
            onConfirm = {
                showHighRiskDialog = false
                onPathSelected(currentPath)
                onDismiss()
            }
        )
    }
}

enum class PathSecurityLevel {
    SAFE,
    HIGH_RISK_SYSTEM,
    HARD_BLOCKED_KERNEL
}

private fun evaluatePathSecurity(path: String): PathSecurityLevel {
    val clean = path.trim().trimEnd('/')
    if (clean == "/dev" || clean.startsWith("/dev/") ||
        clean == "/proc" || clean.startsWith("/proc/") ||
        clean == "/sys" || clean.startsWith("/sys/") ||
        clean == "/apex" || clean.startsWith("/apex/")
    ) {
        return PathSecurityLevel.HARD_BLOCKED_KERNEL
    }
    if (clean == "" || clean == "/" ||
        clean == "/system" || clean.startsWith("/system/") ||
        clean == "/vendor" || clean.startsWith("/vendor/") ||
        clean == "/product" || clean.startsWith("/product/") ||
        clean == "/system_ext" || clean.startsWith("/system_ext/") ||
        clean == "/data" || clean == "/data/user" || clean == "/data/user_de"
    ) {
        return PathSecurityLevel.HIGH_RISK_SYSTEM
    }
    return PathSecurityLevel.SAFE
}

@Composable
private fun HighRiskDirectoryConfirmDialog(
    path: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    var userAgreed by remember { mutableStateOf(false) }
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val crimson = if (isDark) WarmCrimsonDark else WarmCrimsonLight
    val crimsonBg = if (isDark) WarmCrimsonBgDark else WarmCrimsonBgLight
    val crimsonBorder = if (isDark) WarmCrimsonBorderDark else WarmCrimsonBorderLight

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Default.Close,
                    contentDescription = null,
                    tint = crimson,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Peringatan Risiko Sistem",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = crimson
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Anda memilih direktori sistem inti:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = crimsonBg,
                    border = BorderStroke(1.dp, crimsonBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = path,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = crimson,
                        modifier = Modifier.padding(10.dp)
                    )
                }
                Text(
                    text = "Mengaitkan bind-mount pada direktori ini dapat menyebabkan kegagalan booting (bootloop), penolakan izin konteks SELinux, atau kerusakan sistem.",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { userAgreed = !userAgreed }
                ) {
                    Checkbox(
                        checked = userAgreed,
                        onCheckedChange = { userAgreed = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = crimson,
                            checkmarkColor = Color.White
                        )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Saya memahami risiko kerusakan sistem",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp, fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = userAgreed,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = crimson,
                    disabledContainerColor = crimson.copy(alpha = 0.25f)
                )
            ) {
                Text("Tetap Gunakan Jalur Ini", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, if (isDark) OutlinedNeutralBorderDark else OutlinedNeutralBorderLight),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (isDark) OutlinedNeutralBgDark else OutlinedNeutralBgLight
                )
            ) {
                Text("Batal", color = if (isDark) OutlinedNeutralTextDark else OutlinedNeutralTextLight)
            }
        }
    )
}
