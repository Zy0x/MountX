package app.mountify.ui.games

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.pm.PackageInfoCompat
import app.mountify.R
import app.mountify.data.model.GameEntry
import app.mountify.data.model.MountMode
import app.mountify.data.model.MoveDirection
import app.mountify.ui.components.AppIconImage
import app.mountify.ui.components.CompactScreenHeader
import app.mountify.ui.components.StatusChip
import app.mountify.ui.theme.CyberEmerald
import app.mountify.ui.theme.NeonCrimson
import app.mountify.util.FormatUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun GameDetailView(
    game: GameEntry,
    storageBreakdown: Pair<Long, Long>,
    isMoving: Boolean,
    moveMessage: String?,
    onDismiss: () -> Unit,
    onMove: (MoveDirection) -> Unit,
    onUpdateMode: (MountMode) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onDismiss)

    val context = LocalContext.current
    val (internalBytes, sdBytes) = storageBreakdown
    var currentMode by remember(game.mode) { mutableStateOf(game.mode) }

    // Resolve app package metadata from PackageManager
    val packageInfo = remember(game.packageName) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(game.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(game.packageName, 0)
            }
        } catch (_: Exception) {
            null
        }
    }

    val versionName = packageInfo?.versionName ?: "—"
    val versionCode = packageInfo?.let { PackageInfoCompat.getLongVersionCode(it) } ?: 0L
    val installTimeStr = remember(packageInfo?.firstInstallTime) {
        val time = packageInfo?.firstInstallTime ?: 0L
        if (time > 0L) {
            val sdf = SimpleDateFormat("dd-MMM-yyyy hh:mm a", Locale.getDefault())
            sdf.format(Date(time))
        } else {
            "—"
        }
    }

    Scaffold(
        topBar = {
            CompactScreenHeader(
                title = stringResource(R.string.game_detail_app_title),
                subtitle = game.displayName.ifBlank { game.packageName },
                navigationIcon = {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(6.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.common_back),
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            try {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", game.packageName, null)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (_: Exception) {
                            }
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(6.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = stringResource(R.string.game_detail_system_info),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ── APP HERO METADATA CARD (App2SD Pro Style) ──
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AppIconImage(
                        packageName = game.packageName,
                        size = 46.dp
                    )

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = game.displayName.ifBlank { game.packageName },
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 17.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = game.packageName,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 13.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = stringResource(R.string.game_detail_version, versionName, versionCode.toString()),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = stringResource(R.string.game_detail_install_time, installTimeStr),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    StatusChip(status = game.mountStatus)
                }
            }

            // ── STORAGE DISTRIBUTION CARD (Donut Chart & Detailed Breakdown) ──
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.game_detail_storage_breakdown),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )

                    val totalBytes = internalBytes + sdBytes
                    val internalPercent = if (totalBytes > 0) ((internalBytes.toDouble() / totalBytes.toDouble()) * 100).roundToInt() else 0
                    val sdPercent = if (totalBytes > 0) (100 - internalPercent) else 0

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Donut Chart
                        Box(
                            modifier = Modifier.size(92.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            val internalColor = Color(0xFFE65100)
                            val sdColor = CyberEmerald
                            val neutralTrack = MaterialTheme.colorScheme.surfaceVariant

                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val strokeWidth = 14.dp.toPx()
                                val radius = (size.minDimension - strokeWidth) / 2f
                                val centerOffset = Offset(size.width / 2f, size.height / 2f)

                                if (totalBytes == 0L) {
                                    drawCircle(
                                        color = neutralTrack,
                                        radius = radius,
                                        center = centerOffset,
                                        style = Stroke(width = strokeWidth)
                                    )
                                } else {
                                    val internalSweep = (internalPercent / 100f) * 360f
                                    val sdSweep = 360f - internalSweep

                                    // Draw Internal Arc (starting from top -90 deg)
                                    drawArc(
                                        color = internalColor,
                                        startAngle = -90f,
                                        sweepAngle = internalSweep,
                                        useCenter = false,
                                        topLeft = Offset(centerOffset.x - radius, centerOffset.y - radius),
                                        size = Size(radius * 2f, radius * 2f),
                                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                                    )

                                    // Draw MicroSD Arc
                                    drawArc(
                                        color = sdColor,
                                        startAngle = -90f + internalSweep,
                                        sweepAngle = sdSweep,
                                        useCenter = false,
                                        topLeft = Offset(centerOffset.x - radius, centerOffset.y - radius),
                                        size = Size(radius * 2f, radius * 2f),
                                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                                    )
                                }
                            }

                            // Center Text inside Donut
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = if (totalBytes > 0L) "$sdPercent%" else "0%",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = if (sdPercent > 0) CyberEmerald else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "SD Card",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                                )
                            }
                        }

                        // Breakdown Legend Table (App2SD Pro style)
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Internal Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFE65100))
                                    )
                                    Icon(
                                        Icons.Default.Smartphone,
                                        contentDescription = null,
                                        modifier = Modifier.size(13.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = stringResource(R.string.game_detail_internal_storage),
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = FormatUtils.formatBytes(internalBytes),
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            // MicroSD Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(CyberEmerald)
                                    )
                                    Icon(
                                        Icons.Default.SdCard,
                                        contentDescription = null,
                                        modifier = Modifier.size(13.dp),
                                        tint = CyberEmerald
                                    )
                                    Text(
                                        text = stringResource(R.string.game_detail_microsd_storage),
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp),
                                        color = CyberEmerald
                                    )
                                }
                                Text(
                                    text = FormatUtils.formatBytes(sdBytes),
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = CyberEmerald
                                )
                            }

                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                modifier = Modifier.padding(vertical = 2.dp)
                            )

                            // Total Cumulative Row (Σ)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Text(
                                        text = "Σ",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = stringResource(R.string.game_detail_total_storage),
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Text(
                                    text = FormatUtils.formatBytes(totalBytes),
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            // ── MOUNT MODE SELECTOR CARD ──
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.game_detail_mode_title),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // PKG Option
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (currentMode == MountMode.PKG)
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            else
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(
                                1.dp,
                                if (currentMode == MountMode.PKG) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    currentMode = MountMode.PKG
                                    onUpdateMode(MountMode.PKG)
                                }
                        ) {
                            Column(modifier = Modifier.padding(9.dp)) {
                                Text(
                                    text = "PKG Mode",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = if (currentMode == MountMode.PKG) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = stringResource(R.string.add_game_mode_pkg_desc),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 9.5.sp,
                                        lineHeight = 12.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                                )
                            }
                        }

                        // FILES Option
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (currentMode == MountMode.FILES)
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            else
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(
                                1.dp,
                                if (currentMode == MountMode.FILES) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    currentMode = MountMode.FILES
                                    onUpdateMode(MountMode.FILES)
                                }
                        ) {
                            Column(modifier = Modifier.padding(9.dp)) {
                                Text(
                                    text = "FILES Mode",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = if (currentMode == MountMode.FILES) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = stringResource(R.string.add_game_mode_files_desc),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 9.5.sp,
                                        lineHeight = 12.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                                )
                            }
                        }
                    }
                }
            }

            // ── PHYSICAL DATA TRANSFER CARD ──
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.game_detail_move_title),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )

                    if (isMoving) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.common_loading),
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.5.sp),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    } else {
                        if (moveMessage != null) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (moveMessage == "SUCCESS")
                                    CyberEmerald.copy(alpha = 0.12f)
                                else
                                    NeonCrimson.copy(alpha = 0.12f),
                                border = BorderStroke(
                                    1.dp,
                                    if (moveMessage == "SUCCESS") CyberEmerald.copy(alpha = 0.4f) else NeonCrimson.copy(alpha = 0.4f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = if (moveMessage == "SUCCESS") Icons.Default.CheckCircle else Icons.Default.Error,
                                        contentDescription = null,
                                        tint = if (moveMessage == "SUCCESS") CyberEmerald else NeonCrimson,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text(
                                        text = if (moveMessage == "SUCCESS")
                                            stringResource(R.string.move_data_success)
                                        else
                                            stringResource(R.string.move_data_error, moveMessage),
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp),
                                        color = if (moveMessage == "SUCCESS") CyberEmerald else NeonCrimson,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Move to MicroSD
                            Button(
                                onClick = { onMove(MoveDirection.TO_SD) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(34.dp),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Icon(Icons.Default.ArrowDownward, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.game_detail_move_to_sd),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Restore to Internal
                            OutlinedButton(
                                onClick = { onMove(MoveDirection.TO_INTERNAL) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(34.dp),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                            ) {
                                Icon(Icons.Default.ArrowUpward, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.game_detail_move_to_internal),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // ── DANGER ZONE: REMOVE GAME ──
            OutlinedButton(
                onClick = onDelete,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCrimson),
                border = BorderStroke(1.dp, NeonCrimson.copy(alpha = 0.5f))
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp), tint = NeonCrimson)
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = stringResource(R.string.game_detail_delete_action),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeonCrimson
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
