package app.mountify.root

import app.mountify.data.model.RootSolution
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class RootModuleInfo(
    val rootSolution: RootSolution = RootSolution.NONE,
    val isModuleInstalled: Boolean = false,
    val moduleVersion: String = ""
)

/**
 * Detects which root solution is active on the device
 * and whether the Mountify module is installed.
 */
object RootDetector {

    @Volatile
    private var cachedInfo: RootModuleInfo? = null

    /**
     * Batch-detect root solution, module installation, and version in a single shell call.
     */
    suspend fun getRootAndModuleInfo(forceRefresh: Boolean = false): RootModuleInfo = withContext(Dispatchers.IO) {
        if (!forceRefresh) {
            cachedInfo?.let { return@withContext it }
        }

        if (!RootShell.isAvailable) {
            val none = RootModuleInfo()
            cachedInfo = none
            return@withContext none
        }

        val batchScript = """
            if magisk -v >/dev/null 2>&1; then
                echo "ROOT:MAGISK"
            elif [ -e /data/adb/ksud ]; then
                echo "ROOT:KERNELSU"
            elif [ -e /data/adb/apd ]; then
                echo "ROOT:APATCH"
            elif ksud -V >/dev/null 2>&1; then
                echo "ROOT:KERNELSU"
            else
                echo "ROOT:NONE"
            fi
            
            MOD_DIR="/data/adb/modules/Mountify"
            if [ -d "${'$'}MOD_DIR" ] && [ ! -f "${'$'}MOD_DIR/disable" ] && [ ! -f "${'$'}MOD_DIR/remove" ]; then
                echo "MODULE:1"
                if [ -f "${'$'}MOD_DIR/module.prop" ]; then
                    grep '^version=' "${'$'}MOD_DIR/module.prop" | head -n 1
                else
                    echo "version="
                fi
            else
                echo "MODULE:0"
                echo "version="
            fi
        """.trimIndent()

        val res = RootShell.exec(batchScript)
        var rootSol = RootSolution.NONE
        var modInstalled = false
        var modVer = ""

        if (res.isSuccess) {
            for (line in res.stdout) {
                val trimmed = line.trim()
                when {
                    trimmed == "ROOT:MAGISK" -> rootSol = RootSolution.MAGISK
                    trimmed == "ROOT:KERNELSU" -> rootSol = RootSolution.KERNELSU
                    trimmed == "ROOT:APATCH" -> rootSol = RootSolution.APATCH
                    trimmed == "MODULE:1" -> modInstalled = true
                    trimmed == "MODULE:0" -> modInstalled = false
                    trimmed.startsWith("version=") -> modVer = trimmed.removePrefix("version=").trim()
                }
            }
        }

        val info = RootModuleInfo(rootSol, modInstalled, modVer)
        cachedInfo = info
        info
    }

    /**
     * Detect which root solution is currently active.
     */
    suspend fun detectRootSolution(): RootSolution {
        return getRootAndModuleInfo().rootSolution
    }

    /**
     * Check if the Mountify Magisk/KSU module is installed.
     */
    suspend fun isModuleInstalled(): Boolean {
        return getRootAndModuleInfo().isModuleInstalled
    }

    /**
     * Get the installed module version from module.prop
     */
    suspend fun getModuleVersion(): String {
        return getRootAndModuleInfo().moduleVersion
    }

    fun invalidateCache() {
        cachedInfo = null
    }
}
