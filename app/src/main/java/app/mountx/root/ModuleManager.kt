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
     * Directly installs the module files into /data/adb/modules/MountX via Root.
     * This provides instant zero-reboot activation for active tools and persists on boot.
     */
    suspend fun installModuleDirectly(context: Context): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val cacheDir = File(context.cacheDir, "module_staging")
            cacheDir.mkdirs()

            // Copy assets to cache
            val assetFiles = context.assets.list("module") ?: emptyArray()
            if (assetFiles.isEmpty()) {
                throw IllegalStateException("Module assets not found in package")
            }

            for (fileName in assetFiles) {
                val outFile = File(cacheDir, fileName)
                context.assets.open("module/$fileName").use { input ->
                    FileOutputStream(outFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }

            // Staging dir to root destination
            val stagingPath = cacheDir.absolutePath
            val installCmd = """
                mkdir -p $MODULE_DIR
                cp -f $stagingPath/* $MODULE_DIR/
                rm -f $MODULE_DIR/disable $MODULE_DIR/remove
                chmod -R 755 $MODULE_DIR
                chmod 644 $MODULE_DIR/module.prop $MODULE_DIR/config.conf 2>/dev/null
                chmod 755 $MODULE_DIR/service.sh $MODULE_DIR/uninstall.sh 2>/dev/null
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
     * Builds a flashable Magisk/KernelSU ZIP file and saves it to external Downloads.
     */
    suspend fun exportModuleZip(context: Context): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadDir.exists()) downloadDir.mkdirs()

            val targetZip = File(downloadDir, "MountX-Module-v2.2.24.zip")
            val assetFiles = context.assets.list("module") ?: emptyArray()
            if (assetFiles.isEmpty()) {
                throw IllegalStateException("Module assets not found in package")
            }

            FileOutputStream(targetZip).use { fos ->
                ZipOutputStream(fos).use { zos ->
                    // 1. Add all asset files
                    for (fileName in assetFiles) {
                        zos.putNextEntry(ZipEntry(fileName))
                        context.assets.open("module/$fileName").use { it.copyTo(zos) }
                        zos.closeEntry()
                    }

                    // 2. Add META-INF/com/google/android/update-binary
                    val updateBinaryContent = """
                        #!/sbin/sh
                        #################
                        # MountX Magisk Module Installer Script
                        #################
                        OUTFD=${'$'}2
                        ZIPFILE=${'$'}3
                        
                        ui_print() { echo -e "ui_print ${'$'}1\nui_print" >> /proc/self/fd/${'$'}OUTFD; }
                        
                        ui_print "*******************************"
                        ui_print "       MountX Game Engine      "
                        ui_print "*******************************"
                        
                        MODPATH=/data/adb/modules/MountX
                        mkdir -p ${'$'}MODPATH
                        unzip -o "${'$'}ZIPFILE" -d ${'$'}MODPATH "module.prop" "service.sh" "uninstall.sh" "config.conf" "gamelist.conf" >/dev/null 2>&1
                        chmod -R 755 ${'$'}MODPATH
                        chmod 644 ${'$'}MODPATH/module.prop ${'$'}MODPATH/config.conf 2>/dev/null
                        chmod 755 ${'$'}MODPATH/service.sh ${'$'}MODPATH/uninstall.sh 2>/dev/null
                        rm -f ${'$'}MODPATH/disable ${'$'}MODPATH/remove
                        
                        ui_print "- Modul MountX berhasil dipasang!"
                        exit 0
                    """.trimIndent()

                    zos.putNextEntry(ZipEntry("META-INF/com/google/android/update-binary"))
                    zos.write(updateBinaryContent.toByteArray(Charsets.UTF_8))
                    zos.closeEntry()

                    // 3. Add META-INF/com/google/android/updater-script
                    zos.putNextEntry(ZipEntry("META-INF/com/google/android/updater-script"))
                    zos.write("#MAGISK\n".toByteArray(Charsets.UTF_8))
                    zos.closeEntry()
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
