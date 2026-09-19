package app.mountx.data.rescue

import app.mountx.data.db.GameDao
import app.mountx.data.model.MountStatus
import app.mountx.root.MountManager
import app.mountx.root.RootShell
import app.mountx.util.AppLogger
import app.mountx.util.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EmergencyRescueManager @Inject constructor(
    private val gameDao: GameDao,
    private val mountManager: MountManager,
    private val appPreferences: AppPreferences
) {

    companion object {
        const val RESCUE_SCRIPT_PATH = "/sdcard/MountX_Emergency_Restore.sh"
    }

    /**
     * Generates a standalone emergency restore shell script at /sdcard/MountX_Emergency_Restore.sh
     * Can be run anytime via Termux (su -c "sh /sdcard/MountX_Emergency_Restore.sh") or ADB shell.
     */
    suspend fun generateRescueScript(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val sdBase = appPreferences.sdBasePath.first()
            val scriptContent = """
                #!/system/bin/sh
                # ==============================================================================
                # MountX Emergency Restore & Rescue Script
                # Can be run via ADB Shell, Termux (su), or Recovery Terminal
                # ==============================================================================
                echo "[*] MountX Emergency Restore Initiated..."
                
                # 1. Unmount all bind mounts
                echo "[*] Unmounting all bind mounts from $sdBase..."
                for m in ${'$'}(grep "$sdBase" /proc/mounts 2>/dev/null | cut -d' ' -f2); do
                    if [ "${'$'}m" != "$sdBase" ]; then
                        echo "    Unmounting ${'$'}m"
                        umount -f -l "${'$'}m" 2>/dev/null
                    fi
                done
                
                # 2. Reset and restore internal storage permissions
                echo "[*] Restoring internal storage permissions..."
                chmod 775 /data/media/0/Android/data 2>/dev/null
                chmod 775 /data/media/0/Android/obb 2>/dev/null
                chmod 775 /data/media/0/Android/media 2>/dev/null
                
                # 3. Restore SELinux contexts
                echo "[*] Restoring SELinux contexts..."
                restorecon -R /data/media/0/Android 2>/dev/null
                
                # 4. Sync filesystem buffers
                sync
                
                echo "[+] MountX Emergency Restore Completed Successfully!"
                echo "[+] All bind-mounts detached and storage permissions restored."
            """.trimIndent()

            val escaped = scriptContent.replace("'", "'\\''")
            RootShell.exec("echo '$escaped' > \"$RESCUE_SCRIPT_PATH\" && chmod 755 \"$RESCUE_SCRIPT_PATH\"")
            AppLogger.success("Rescue", "Generated emergency restore script at $RESCUE_SCRIPT_PATH")
            RESCUE_SCRIPT_PATH
        }
    }

    /**
     * Executes 1-click total emergency recovery directly inside MountX.
     * Unmounts all game directories, resets permissions, and restores SELinux.
     */
    suspend fun executeEmergencyReset(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val sdBase = appPreferences.sdBasePath.first()
            AppLogger.warn("Rescue", "Executing Emergency Panic Reset on $sdBase...")

            // 1. Force unmount all game bind-mounts
            mountManager.unmountAll(sdBase)

            // 2. Restore internal storage permissions
            RootShell.exec("chmod 775 /data/media/0/Android/data 2>/dev/null")
            RootShell.exec("chmod 775 /data/media/0/Android/obb 2>/dev/null")
            RootShell.exec("chmod 775 /data/media/0/Android/media 2>/dev/null")
            RootShell.exec("restorecon -R /data/media/0/Android 2>/dev/null")
            RootShell.exec("sync")

            // 3. Mark all games as UNMOUNTED in Room DB
            val games = gameDao.getAllGames().firstOrNull() ?: emptyList()
            for (g in games) {
                gameDao.updateMountStatus(g.packageName, MountStatus.UNMOUNTED)
            }

            AppLogger.success("Rescue", "Emergency Panic Reset completed. All mounts cleared and permissions restored.")
        }
    }
}
