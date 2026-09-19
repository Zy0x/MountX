package app.mountx.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.topjohnwu.superuser.Shell

/**
 * Audit and Intent Helper for MountX System Permissions.
 */
object PermissionManager {

    data class PermissionState(
        val isRootGranted: Boolean,
        val isStorageGranted: Boolean,
        val isNotificationGranted: Boolean,
        val isBatteryExempted: Boolean
    ) {
        val areEssentialGranted: Boolean
            get() = isRootGranted && isStorageGranted

        val areAllGranted: Boolean
            get() = isRootGranted && isStorageGranted && isNotificationGranted && isBatteryExempted
    }

    fun checkAllPermissions(context: Context): PermissionState {
        val isRoot = checkRootAccess()
        val isStorage = checkStorageAccess(context)
        val isNotif = checkNotificationAccess(context)
        val isBattery = checkBatteryOptimization(context)

        return PermissionState(
            isRootGranted = isRoot,
            isStorageGranted = isStorage,
            isNotificationGranted = isNotif,
            isBatteryExempted = isBattery
        )
    }

    fun checkRootAccess(): Boolean {
        val cached = Shell.isAppGrantedRoot()
        if (cached != null) return cached
        return try {
            app.mountx.root.RootShell.isAvailable
        } catch (_: Exception) {
            false
        }
    }

    fun checkStorageAccess(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun checkNotificationAccess(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }

    fun checkBatteryOptimization(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun openStorageSettings(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } else {
                openAppDetailsSettings(context)
            }
        } catch (_: Exception) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val fallback = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(fallback)
                } else {
                    openAppDetailsSettings(context)
                }
            } catch (_: Exception) {
                openAppDetailsSettings(context)
            }
        }
    }

    fun openNotificationSettings(context: Context) {
        try {
            val intent = Intent().apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                } else {
                    action = "android.settings.APP_NOTIFICATION_SETTINGS"
                    putExtra("app_package", context.packageName)
                    putExtra("app_uid", context.applicationInfo.uid)
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            openAppDetailsSettings(context)
        }
    }

    @SuppressLint("BatteryLife")
    fun requestBatteryOptimizationExemption(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallback)
            } catch (_: Exception) {
                openAppDetailsSettings(context)
            }
        }
    }

    fun openAppDetailsSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            // Ignored
        }
    }
}
