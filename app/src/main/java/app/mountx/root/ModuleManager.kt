package app.mountx.root

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import app.mountx.data.model.RootSolution
import app.mountx.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Manager for installing, enabling, and exporting MountX Magisk/KernelSU/APatch modules.
 */
object ModuleManager {

    private const val TAG = "ModuleManager"
    private const val MODULE_DIR = "/data/adb/modules/MountX"
    private const val ALT_MODULE_DIR = "/data/adb/modules/Mountify"

    /**
     * Checks if the module directory exists but is disabled via 'disable' flag.
     */
    suspend fun isModuleDisabled(): Boolean = withContext(Dispatchers.IO) {
        val check = RootShell.exec("[ -d '$MODULE_DIR' -a -f '$MODULE_DIR/disable' ] && echo 1 || echo 0")
        check.output.trim() == "1"
    }

    /**
     * Re-enables a disabled module by deleting the disable/remove markers.
     */
    suspend fun enableModule(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            RootShell.exec("rm -f $MODULE_DIR/disable $MODULE_DIR/remove $ALT_MODULE_DIR/disable $ALT_MODULE_DIR/remove")
            RootDetector.getRootAndModuleInfo(forceRefresh = true)
            AppLogger.success(TAG, "Module re-enabled successfully")
        }
    }

    /**
     * Recursively copies assets from an assets subfolder into a local target directory.
     */
    private fun copyAssetFolderRecursively(context: Context, assetPath: String, targetDir: File) {
        val items = context.assets.list(assetPath) ?: emptyArray()
        if (items.isEmpty()) {
            // It's a file
            context.assets.open(assetPath).use { input ->
                FileOutputStream(targetDir).use { output ->
                    input.copyTo(output)
                }
            }
        } else {
            // It's a directory
            targetDir.mkdirs()
            for (item in items) {
                val subAsset = if (assetPath.isEmpty()) item else "$assetPath/$item"
                val subTarget = File(targetDir, item)
                copyAssetFolderRecursively(context, subAsset, subTarget)
            }
        }
    }

    /**
     * Recursively writes files from an assets subfolder into a ZipOutputStream.
     */
    private fun zipAssetFolderRecursively(context: Context, assetPath: String, zos: ZipOutputStream, zipPrefix: String = "") {
        val items = context.assets.list(assetPath) ?: emptyArray()
        if (items.isEmpty()) {
            // File
            val entryName = if (zipPrefix.isEmpty()) assetPath.substringAfterLast("/") else zipPrefix
            zos.putNextEntry(ZipEntry(entryName))
            context.assets.open(assetPath).use { it.copyTo(zos) }
            zos.closeEntry()
        } else {
            // Directory
            for (item in items) {
                val subAsset = if (assetPath.isEmpty()) item else "$assetPath/$item"
                val subPrefix = if (zipPrefix.isEmpty()) item else "$zipPrefix/$item"
                zipAssetFolderRecursively(context, subAsset, zos, subPrefix)
            }
        }
    }

    /**
     * Directly installs the module files into /data/adb/modules/MountX via Root.
     * This provides instant zero-reboot activation for active tools and persists on boot.
     */
    suspend fun installModuleDirectly(context: Context): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val cacheDir = File(context.cacheDir, "module_staging")
            cacheDir.deleteRecursively()
            cacheDir.mkdirs()

            // Copy entire module assets folder recursively
            copyAssetFolderRecursively(context, "module", cacheDir)

            // Staging dir to root destination
            val stagingPath = cacheDir.absolutePath
            val installCmd = """
                mkdir -p $MODULE_DIR
                cp -rf $stagingPath/* $MODULE_DIR/
                rm -f $MODULE_DIR/disable $MODULE_DIR/remove
                chmod -R 755 $MODULE_DIR
                chmod 644 $MODULE_DIR/module.prop $MODULE_DIR/config.conf $MODULE_DIR/banner.png 2>/dev/null || true
                chmod 755 $MODULE_DIR/service.sh $MODULE_DIR/uninstall.sh $MODULE_DIR/customize.sh 2>/dev/null || true
                chcon -R u:object_r:magisk_file:s0 $MODULE_DIR 2>/dev/null || true
            """.trimIndent()

            val res = RootShell.exec(installCmd)
            cacheDir.deleteRecursively()

            if (!res.isSuccess) {
                throw IllegalStateException("Failed to copy module files: ${res.stderr.joinToString(" ")}")
            }

            // Also execute Magisk / KSU CLI if available for registry sync
            val rootInfo = RootDetector.getRootAndModuleInfo(forceRefresh = true)
            when (rootInfo.rootSolution) {
                RootSolution.MAGISK -> {
                    RootShell.exec("magisk --post-fs-data 2>/dev/null || true")
                }
                RootSolution.KERNELSU -> {
                    RootShell.exec("ksud module enable MountX 2>/dev/null || true")
                }
                RootSolution.APATCH -> {
                    RootShell.exec("apd module enable MountX 2>/dev/null || true")
                }
                else -> {}
            }

            RootDetector.getRootAndModuleInfo(forceRefresh = true)
            AppLogger.success(TAG, "MountX module installed and activated directly via Root")
            "Modul MountX berhasil dipasang dan diaktifkan!"
        }
    }

    /**
     * Checks if the module is installed in /data/adb/modules/MountX.
     * If installed but outdated (versionCode < app versionCode) or broken,
     * quietly synchronizes the module files without requiring reboot.
     */
    suspend fun checkAndSyncModuleSilently(context: Context): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val propPath = "$MODULE_DIR/module.prop"
            val exists = RootShell.exists(propPath)
            if (!exists) {
                // Also check ALT_MODULE_DIR
                if (RootShell.exists("$ALT_MODULE_DIR/module.prop")) {
                    installModuleDirectly(context)
                    return@runCatching true
                }
                return@runCatching false
            }

            val propOut = RootShell.execForOutput("cat \"$propPath\" 2>/dev/null")
            val installedVersionCode = propOut.lines()
                .firstOrNull { it.startsWith("versionCode=") }
                ?.substringAfter("=")
                ?.trim()
                ?.toIntOrNull() ?: 0

            val currentVersionCode = app.mountx.BuildConfig.VERSION_CODE
            if (installedVersionCode < currentVersionCode) {
                AppLogger.info(TAG, "Syncing outdated module: installed=$installedVersionCode, current=$currentVersionCode")
                installModuleDirectly(context)
                true
            } else {
                false
            }
        }
    }

    /**
     * Builds a flashable Magisk/KernelSU ZIP file containing the module files,
     * META-INF, customize.sh, banner.png, and the running companion MountX.apk.
     * Saves the ZIP file to the external Downloads directory.
     */
    suspend fun exportModuleZip(context: Context): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadDir.exists()) downloadDir.mkdirs()

            val targetZip = File(downloadDir, "MountX-Module-v${app.mountx.BuildConfig.VERSION_NAME}.zip")

            FileOutputStream(targetZip).use { fos ->
                ZipOutputStream(fos).use { zos ->
                    // 1. Recursively add all module assets (service.sh, module.prop, customize.sh, banner.png, META-INF/...)
                    zipAssetFolderRecursively(context, "module", zos, "")

                    // 2. Add companion MountX.apk from running package so the ZIP is fully self-contained
                    try {
                        val appSourceApk = File(context.applicationInfo.sourceDir)
                        if (appSourceApk.exists() && appSourceApk.canRead()) {
                            zos.putNextEntry(ZipEntry("MountX.apk"))
                            appSourceApk.inputStream().use { it.copyTo(zos) }
                            zos.closeEntry()
                            AppLogger.info(TAG, "Bundled companion MountX.apk into module ZIP (${appSourceApk.length() / 1024 / 1024} MB)")
                        }
                    } catch (e: Exception) {
                        AppLogger.warn(TAG, "Could not bundle running APK into module ZIP: ${e.message}")
                    }
                }
            }

            // Inform media scanner so user sees it in file manager right away
            MediaScannerConnection.scanFile(
                context,
                arrayOf(targetZip.absolutePath),
                arrayOf("application/zip"),
                null
            )

            AppLogger.success(TAG, "Module ZIP exported to: ${targetZip.absolutePath}")
            targetZip
        }
    }
}
