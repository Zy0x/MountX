package app.mountx.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.mountx.R
import app.mountx.ui.theme.AuroraGradientBrush
import app.mountx.util.PermissionManager
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionOnboardingSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    var permState by remember {
        mutableStateOf(PermissionManager.checkAllPermissions(context))
    }

    // Asynchronously verify root and permissions upon entering composition
    androidx.compose.runtime.LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                app.mountx.root.RootShell.isAvailable
            } catch (_: Exception) {}
        }
        permState = PermissionManager.checkAllPermissions(context)
    }

    // Auto-refresh permission status when app returns to foreground
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                coroutineScope.launch {
                    withContext(Dispatchers.IO) {
                        try {
                            app.mountx.root.RootShell.isAvailable
                        } catch (_: Exception) {}
                    }
                    permState = PermissionManager.checkAllPermissions(context)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val activeEmerald = Color(0xFF00E676)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Drag Handle Bar
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(width = 36.dp, height = 4.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant, CircleShape)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Header Title & Description
            Text(
                text = stringResource(R.string.permissions_title),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = stringResource(R.string.permissions_subtitle),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Card 1: Root Access
            PermissionItemCard(
                icon = Icons.Default.Security,
                title = stringResource(R.string.permissions_root_title),
                description = stringResource(R.string.permissions_root_desc),
                isGranted = permState.isRootGranted,
                isEssential = true,
                onGrantClick = {
                    coroutineScope.launch {
                        withContext(Dispatchers.IO) {
                            try {
                                Shell.getShell()
                            } catch (_: Exception) {}
                        }
                        permState = PermissionManager.checkAllPermissions(context)
                    }
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Card 2: All Files Access (Storage)
            PermissionItemCard(
                icon = Icons.Default.Folder,
                title = stringResource(R.string.permissions_storage_title),
                description = stringResource(R.string.permissions_storage_desc),
                isGranted = permState.isStorageGranted,
                isEssential = true,
                onGrantClick = {
                    PermissionManager.openStorageSettings(context)
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Card 3: System Notifications
            PermissionItemCard(
                icon = Icons.Default.Notifications,
                title = stringResource(R.string.permissions_notif_title),
                description = stringResource(R.string.permissions_notif_desc),
                isGranted = permState.isNotificationGranted,
                isEssential = false,
                onGrantClick = {
                    PermissionManager.openNotificationSettings(context)
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Card 4: Battery Optimization Exemption
            PermissionItemCard(
                icon = Icons.Default.BatteryChargingFull,
                title = stringResource(R.string.permissions_battery_title),
                description = stringResource(R.string.permissions_battery_desc),
                isGranted = permState.isBatteryExempted,
                isEssential = false,
                onGrantClick = {
                    PermissionManager.requestBatteryOptimizationExemption(context)
                }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Continue Button
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = stringResource(R.string.permissions_btn_continue),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun PermissionItemCard(
    icon: ImageVector,
    title: String,
    description: String,
    isGranted: Boolean,
    isEssential: Boolean,
    onGrantClick: () -> Unit
) {
    val activeEmerald = Color(0xFF00E676)

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(
            1.dp,
            if (isGranted) activeEmerald.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isGranted) activeEmerald.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (isGranted) activeEmerald else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (isEssential) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = stringResource(R.string.common_recommended),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Status Badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isGranted) activeEmerald.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(
                        1.dp,
                        if (isGranted) activeEmerald.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outlineVariant
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(
                                    if (isGranted) activeEmerald else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    CircleShape
                                )
                        )
                        Text(
                            text = if (isGranted) {
                                stringResource(R.string.permissions_status_granted)
                            } else {
                                stringResource(R.string.permissions_status_missing)
                            },
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = if (isGranted) activeEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Grant Button (only if not granted)
                if (!isGranted) {
                    OutlinedButton(
                        onClick = onGrantClick,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.permissions_btn_grant),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = activeEmerald,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
