package app.mihon.root

import app.mihon.data.model.RootSolution
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Detects which root solution is active on the device
 * and whether the Mountify module is installed.
 */
object RootDetector {

    /**
     * Detect which root solution is currently active.
     * Checks Magisk → KernelSU → APatch in order.
     */
    suspend fun detectRootSolution(): RootSolution = withContext(Dispatchers.IO) {
        if (!RootShell.isAvailable) return@withContext RootSolution.NONE

        // Check Magisk
        val magiskCheck = RootShell.exec("magisk --version 2>/dev/null")
        if (magiskCheck.isSuccess && magiskCheck.output.isNotBlank()) {
            return@withContext RootSolution.MAGISK
        }

        // Check KernelSU
        if (RootShell.exists("/data/adb/ksud")) {
            return@withContext RootSolution.KERNELSU
        }

        // Check APatch
        if (RootShell.exists("/data/adb/apd")) {
            return@withContext RootSolution.APATCH
        }

        // Alternative KSU check via ksu_version
        val ksuCheck = RootShell.exec("ksud --version 2>/dev/null")
        if (ksuCheck.isSuccess) return@withContext RootSolution.KERNELSU

        RootSolution.NONE
    }

    /**
     * Check if the Mountify Magisk/KSU module is installed.
     * @return true if module directory exists and is not disabled
     */
    suspend fun isModuleInstalled(): Boolean = withContext(Dispatchers.IO) {
        val path = "/data/adb/modules/Mountify"
        RootShell.exists(path) &&
            !RootShell.exists("$path/disable") &&
            !RootShell.exists("$path/remove")
    }

    /**
     * Get the installed module version from module.prop
     */
    suspend fun getModuleVersion(): String = withContext(Dispatchers.IO) {
        val path = "/data/adb/modules/Mountify"
        if (RootShell.exists("$path/module.prop")) {
            val result = RootShell.exec("grep '^version=' $path/module.prop")
            if (result.isSuccess) {
                return@withContext result.output.removePrefix("version=").trim()
            }
        }
        ""
    }
}
