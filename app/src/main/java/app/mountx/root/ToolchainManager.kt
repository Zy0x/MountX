package app.mountx.root

import app.mountx.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Universal Toolchain & Block Layer Manager.
 * Initializes an ultra-fast, RAM-backed tmpfs at /dev/mountx_bin populated with
 * symlinks to MountX's native arm64-v8a binaries and standalone Busybox applets.
 *
 * This guarantees uniform, lightning-fast execution across all Android versions (10–15+),
 * kernels, custom ROMs, OEMs, and root providers without triggering Samsung Knox
 * or Android 14+ SELinux 'execute_no_trans' denials.
 */
object ToolchainManager {
    const val BIN_DIR = "/dev/mountx_bin"

    /**
     * Set up the RAM-based toolchain directory with symlinks to native binaries and applets.
     * @param nativeLibDir The application's native library directory (from context.applicationInfo.nativeLibraryDir)
     */
    suspend fun setupToolchain(nativeLibDir: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val script = """
                mkdir -p $BIN_DIR
                if ! grep -q " $BIN_DIR " /proc/mounts 2>/dev/null; then
                    mount -t tmpfs -o mode=0755,nosuid,nodev tmpfs $BIN_DIR 2>/dev/null
                fi

                # 1. Symlink native library binaries extracted by Android Package Manager
                if [ -d "$nativeLibDir" ]; then
                    for lib in "$nativeLibDir"/lib*.so; do
                        [ -f "${'$'}lib" ] || continue
                        name=$(basename "${'$'}lib" | sed 's/^lib//' | sed 's/\.so$//')
                        ln -sf "${'$'}lib" "$BIN_DIR/${'$'}name"
                    done
                fi

                # 2. Ensure Busybox applet symlinks
                if [ -e "$BIN_DIR/busybox" ]; then
                    chmod 755 "$BIN_DIR/busybox" 2>/dev/null
                    for applet in fuser losetup nsenter unshare blkid blockdev partprobe mknod fsck mke2fs tune2fs fdisk truncate fallocate stat sync; do
                        [ -e "$BIN_DIR/${'$'}applet" ] || ln -sf "$BIN_DIR/busybox" "$BIN_DIR/${'$'}applet"
                    done
                fi

                # 3. Ensure sgdisk and parted permissions
                if [ -e "$BIN_DIR/sgdisk" ]; then
                    chmod 755 "$BIN_DIR/sgdisk" 2>/dev/null
                fi
                if [ -e "$BIN_DIR/parted" ]; then
                    chmod 755 "$BIN_DIR/parted" 2>/dev/null
                fi

                # 3b. Symlink active su binary for TempRoot (e.g. /dev/su or /data/local/tmp/su)
                if [ ! -e "$BIN_DIR/su" ]; then
                    for su_cand in /dev/su /data/local/tmp/su /sbin/su /system/xbin/su /system/bin/su; do
                        if [ -x "${'$'}su_cand" ]; then
                            ln -sf "${'$'}su_cand" "$BIN_DIR/su"
                            break
                        fi
                    done
                fi

                # 3c. Prepare volatile RAM mount directory (Zero /data permanent footprint)
                mkdir -p /dev/mountx/sdext2 2>/dev/null

                # 4. Fallback: Symlink from Magisk/KSU/APatch module bin directory if present
                for m_bin in /data/adb/modules/MountX/bin /data/adb/modules/mountx/bin /data/adb/ap/modules/MountX/bin /data/adb/ksu/modules/MountX/bin; do
                    if [ -d "${'$'}m_bin" ]; then
                        for tool in "${'$'}m_bin"/*; do
                            [ -f "${'$'}tool" ] || continue
                            tname=$(basename "${'$'}tool")
                            [ -e "$BIN_DIR/${'$'}tname" ] || ln -sf "${'$'}tool" "$BIN_DIR/${'$'}tname"
                        done
                    fi
                done

                # 5. Health check: busybox must exist
                if [ -e "$BIN_DIR/busybox" ]; then
                    echo "OK"
                else
                    echo "MISSING_BINARIES"
                fi
            """.trimIndent()

            val res = RootShell.exec(script)
            val success = res.output.contains("OK")
            if (success) {
                AppLogger.info("ToolchainManager", "Toolchain initialized successfully at $BIN_DIR")
            } else {
                AppLogger.warn("ToolchainManager", "Toolchain setup status: ${res.output.trim()}")
            }
            success
        } catch (e: Exception) {
            AppLogger.error("ToolchainManager", "setupToolchain failed: ${e.message}")
            false
        }
    }

    /**
     * Optimizes the storage block device queue parameters for ultra-fast sequential read/write
     * streaming (2048 KB read-ahead, zero request merging overhead, low-latency I/O scheduler).
     */
    suspend fun optimizeBlockDeviceQueue(blockDev: String) = withContext(Dispatchers.IO) {
        try {
            val devName = blockDev.substringAfterLast("/")
            val diskName = when {
                devName.startsWith("mmcblk") -> devName.substringBeforeLast("p")
                devName.startsWith("nvme") -> devName.substringBeforeLast("p")
                devName.startsWith("sd") -> devName.filter { it.isLetter() }
                else -> devName
            }
            if (diskName.isBlank()) return@withContext

            val queuePath = "/sys/block/$diskName/queue"
            val tuneScript = """
                if [ -d "$queuePath" ]; then
                    echo 2048 > $queuePath/read_ahead_kb 2>/dev/null
                    echo 0 > $queuePath/nomerges 2>/dev/null
                    for s in mq-deadline kyber bfq none; do
                        if grep -q "\[${'$'}s\]" $queuePath/scheduler 2>/dev/null || grep -qw "${'$'}s" $queuePath/scheduler 2>/dev/null; then
                            echo "${'$'}s" > $queuePath/scheduler 2>/dev/null
                            break
                        fi
                    done
                fi
            """.trimIndent()
            RootShell.exec(tuneScript)
            AppLogger.info("ToolchainManager", "Block device queue optimized for $diskName (2MB read_ahead_kb)")
        } catch (e: Exception) {
            AppLogger.warn("ToolchainManager", "Failed to optimize block device queue for $blockDev: ${e.message}")
        }
    }
}
