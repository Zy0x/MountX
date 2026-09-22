package app.mountx.root

import app.mountx.data.model.RootSolution
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class RootModuleInfo(
    val rootSolution: RootSolution = RootSolution.NONE,
    val isModuleInstalled: Boolean = false,
    val moduleVersion: String = ""
)

/**
 * Detects which root solution is active on the device
 * and whether the MountX module is installed.
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
            # ── Root solution detection (Magisk / KernelSU / APatch / variants) ──
            if magisk -v >/dev/null 2>&1; then
                echo "ROOT:MAGISK"
            elif [ -e /data/adb/ksud ] || [ -e /data/adb/ksu/ksud ] || ksud -V >/dev/null 2>&1; then
                echo "ROOT:KERNELSU"
            elif [ -e /data/adb/apd ] || apd -V >/dev/null 2>&1; then
                echo "ROOT:APATCH"
            elif su --version 2>/dev/null | grep -qi "supersu"; then
                echo "ROOT:MAGISK"
            else
                echo "ROOT:NONE"
            fi

            # ── Module directory discovery (all known root manager paths) ──
            MODULE_FOUND=0
            for TRY_MOD in \
                "/data/adb/modules/MountX" \
                "/data/adb/modules/Mountify" \
                "/data/adb/ksu/modules/MountX" \
                "/data/adb/ksud/modules/MountX" \
                "/data/adb/ap/modules/MountX"; do
                if [ -d "${'$'}TRY_MOD" ] && [ ! -f "${'$'}TRY_MOD/disable" ] && [ ! -f "${'$'}TRY_MOD/remove" ]; then
                    echo "MODULE:1"
                    if [ -f "${'$'}TRY_MOD/module.prop" ]; then
                        grep '^version=' "${'$'}TRY_MOD/module.prop" | head -n 1
                    else
                        echo "version="
                    fi
                    MODULE_FOUND=1
                    break
                fi
            done
            if [ "${'$'}MODULE_FOUND" -eq 0 ]; then
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
     * Check if the MountX Magisk/KSU module is installed.
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
