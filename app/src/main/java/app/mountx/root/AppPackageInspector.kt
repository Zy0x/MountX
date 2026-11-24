package app.mountx.root

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import app.mountx.R
import app.mountx.data.model.GameEntry
import app.mountx.data.model.MountPointCategory
import app.mountx.data.model.MountPointConfig
import app.mountx.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Completeness and metadata report for an app's APK bundle stored on external storage (MicroSD / USB).
 */
data class SdApkInstallStatus(
    val packageName: String,
    val isAvailable: Boolean = false,
    val isComplete: Boolean = false,
    val sourceDir: String = "",
    val containerImgPath: String? = null,
    val isVirtualContainer: Boolean = false,
    val apkFiles: List<String> = emptyList(),
    val totalApkBytes: Long = 0L,
    val appLabel: String? = null,
    val versionName: String? = null,
    val versionCode: Long = 0L,
    val splitCount: Int = 0,
    val missingReason: String? = null
)

/**
 * Authoritative package health inspector, read-only APK vault guardian,
 * and universal multi-APK session installer for Android 10-17.
 */
object AppPackageInspector {

    /**
     * Resolves [PackageInfo] ONLY if Android's PackageManager points to an actual, non-deleted APK path.
     * Filters out "ghost" PMS entries (`sourceDir == null` or `==deleted==`) left behind when an
     * SD-moved APK becomes detached without root.
     */
    fun resolveHealthyPackageInfo(context: Context, packageName: String): PackageInfo? {
        return try {
            val pm = context.packageManager
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, 0)
            }
            val srcDir = info.applicationInfo?.sourceDir ?: return null
            if (!isSourceDirPlausible(srcDir)) return null
            info
        } catch (_: Exception) {
            null
        }
    }

    fun isSourceDirPlausible(sourceDir: String?): Boolean {
        if (sourceDir.isNullOrBlank()) return false
        if (sourceDir.contains("==deleted==") || sourceDir.contains("/vmdl")) return false
        return true
    }

    /**
     * Root-verified package health check: queries `pm path` and ensures either the internal APK exists
     * or MountX holds an intact SD APK ready for mount.
     */
    suspend fun isPackageInstalledHealthyRoot(
        packageName: String,
        sdBases: List<String> = listOf("/data/sdext2"),
        mountPoints: List<MountPointConfig> = emptyList()
    ): Boolean = withContext(Dispatchers.IO) {
        val liveApk = queryLivePmApkPath(packageName) ?: return@withContext false
        val hasSdApk = mountPoints.any {
            it.resolveCategory() == MountPointCategory.APP_PACKAGE &&
                !it.isLibTarget() &&
                it.sourcePath.isNotBlank()
        } || sdBases.any { b ->
            val clean = b.trimEnd('/')
            RootShell.exists("$clean/MountX/app/$packageName/base.apk") ||
                RootShell.exists("$clean/.mountx/containers/${packageName}_apk.img") ||
                RootShell.exists("$clean/.mountx/apk_backup/$packageName/base.apk")
        }
        val escapedApk = liveApk.replace("\"", "\\\"")
        val existsLive = RootShell.exec("[ -f \"$escapedApk\" ] && echo 1 || echo 0").output.trim() == "1"
        existsLive || hasSdApk
    }

    /**
     * Queries Android PackageManager via root for the first valid, non-deleted `pm path` entry.
     */
    suspend fun queryLivePmApkPath(packageName: String): String? = withContext(Dispatchers.IO) {
        val raw = RootShell.execForOutput(
            "pm path \"$packageName\" 2>/dev/null | head -n 1 | sed -n 's/^package://p' | tr -d '\\r'"
        ).trim()
        if (!isSourceDirPlausible(raw)) null else raw
    }

    suspend fun queryLivePmApkDir(packageName: String): String? {
        val p = queryLivePmApkPath(packageName) ?: return null
        return File(p).parent?.takeIf { isSourceDirPlausible(it) }
    }

    /**
     * Creates a zero-overhead POSIX hardlink snapshot of SD APKs under `.mountx/apk_backup/<pkg>`
     * when supported by the filesystem so unexpected deletions in `MountX/app/<pkg>` never destroy the APK.
     */
    suspend fun ensureApkHardlinkBackup(packageName: String, sdBase: String) = withContext(Dispatchers.IO) {
        val cleanBase = sdBase.trimEnd('/')
        val appDir = "$cleanBase/MountX/app/$packageName"
        val backupDir = "$cleanBase/.mountx/apk_backup/$packageName"
        val script = """
            APP_DIR="$appDir"
            BK_DIR="$backupDir"
            [ -f "${'$'}APP_DIR/base.apk" ] && [ -s "${'$'}APP_DIR/base.apk" ] || exit 0
            mkdir -p "${'$'}BK_DIR" 2>/dev/null || exit 0
            for f in "${'$'}APP_DIR"/*.apk; do
                [ -f "${'$'}f" ] && [ -s "${'$'}f" ] || continue
                bn=$(basename "${'$'}f")
                rm -f "${'$'}BK_DIR/${'$'}bn" 2>/dev/null
                ln "${'$'}f" "${'$'}BK_DIR/${'$'}bn" 2>/dev/null || break
                chmod 444 "${'$'}BK_DIR/${'$'}bn" 2>/dev/null
            done
        """.trimIndent()
        RootShell.execScript(script)
    }

    /**
     * Enforces per-mount `ro,exec` protection on APK/lib mounts via BusyBox while binding
     * a writable `oat/` directory underneath so `artd`/`dex2oat` keep functioning and `installd`
     * cannot delete `base.apk` on MicroSD.
     */
    suspend fun applyReadOnlyApkProtection(game: GameEntry, sdBase: String) = withContext(Dispatchers.IO) {
        val cleanBase = sdBase.trimEnd('/')
        val apkRootMp = game.mountPoints.firstOrNull {
            it.enabled && it.resolveCategory() == MountPointCategory.APP_PACKAGE && !it.isLibTarget()
        } ?: return@withContext
        val liveApkDir = queryLivePmApkDir(game.packageName) ?: return@withContext
        val oatRwDir = "$cleanBase/MountX/cache/${game.packageName}_oat"
        val srcDir = apkRootMp.sourcePath.trimEnd('/')
        val script = """
            _bb=""
            if [ -x /dev/mountx_bin/busybox ]; then _bb=/dev/mountx_bin/busybox
            elif [ -x /data/adb/modules/MountX/bin/busybox ]; then _bb=/data/adb/modules/MountX/bin/busybox
            elif command -v busybox >/dev/null 2>&1; then _bb=$(command -v busybox)
            fi
            [ -n "${'$'}_bb" ] || exit 0
            DST="$liveApkDir"
            SRC="$srcDir"
            OAT_RW="$oatRwDir"
            mkdir -p "${'$'}OAT_RW" 2>/dev/null
            chown 1000:1000 "${'$'}OAT_RW" 2>/dev/null
            chmod 755 "${'$'}OAT_RW" 2>/dev/null
            if [ -d "${'$'}SRC" ]; then
                chown 1000:1000 "${'$'}SRC" 2>/dev/null
                chmod 755 "${'$'}SRC" 2>/dev/null
                [ -d "${'$'}SRC/oat" ] || mkdir -p "${'$'}SRC/oat" 2>/dev/null
                if [ -d "$cleanBase/MountX/lib/${game.packageName}" ]; then
                    for _ab_dir in "$cleanBase/MountX/lib/${game.packageName}"/*; do
                        [ -d "${'$'}_ab_dir" ] || continue
                        _ab="${'$'}(basename "${'$'}_ab_dir")"
                        mkdir -p "${'$'}SRC/lib/${'$'}_ab" 2>/dev/null
                        chown 1000:1000 "${'$'}SRC/lib" "${'$'}SRC/lib/${'$'}_ab" 2>/dev/null
                        chmod 755 "${'$'}SRC/lib" "${'$'}SRC/lib/${'$'}_ab" 2>/dev/null
                    done
                fi
            fi
            T="${'$'}DST" awk '{m=${'$'}5;gsub(/\\040/," ",m);if(m==ENVIRON["T"]){f=1;exit}} END{exit !f}' /proc/1/mountinfo 2>/dev/null || exit 0
            "${'$'}_bb" mount -o remount,ro,exec,bind "${'$'}DST" 2>/dev/null || true
            "${'$'}_bb" mount --make-rprivate "${'$'}DST" 2>/dev/null || true
            if [ -d "${'$'}DST/oat" ]; then
                if ! T="${'$'}DST/oat" awk '{m=${'$'}5;gsub(/\\040/," ",m);if(m==ENVIRON["T"]){f=1;exit}} END{exit !f}' /proc/1/mountinfo 2>/dev/null; then
                    mount -o bind "${'$'}OAT_RW" "${'$'}DST/oat" 2>/dev/null
                fi
                "${'$'}_bb" mount -o remount,rw,exec,bind "${'$'}DST/oat" 2>/dev/null || true
                "${'$'}_bb" mount --make-rprivate "${'$'}DST/oat" 2>/dev/null || true
            fi
${MountManager.ZYGOTE_MNT_PIDS_SNIPPET}
            for _zp in ${'$'}_zpids; do
                [ -e "/proc/${'$'}_zp/ns/mnt" ] || continue
                MX_DST="${'$'}DST" MX_OAT="${'$'}OAT_RW" MX_BB="${'$'}_bb" nsenter --mount="/proc/${'$'}_zp/ns/mnt" -- /system/bin/sh -c '
                    _mi=/proc/self/mountinfo
                    if T="${'$'}MX_DST" awk '"'"'{m=$5;gsub(/\\040/," ",m);if(m==ENVIRON["T"]){f=1;exit}} END{exit !f}'"'"' "${'$'}_mi" 2>/dev/null; then
                        "${'$'}MX_BB" mount -o remount,ro,exec,bind "${'$'}MX_DST" 2>/dev/null || true
                        "${'$'}MX_BB" mount --make-rprivate "${'$'}MX_DST" 2>/dev/null || true
                        if [ -d "${'$'}MX_DST/oat" ] && [ -d "${'$'}MX_OAT" ]; then
                            if ! T="${'$'}MX_DST/oat" awk '"'"'{m=$5;gsub(/\\040/," ",m);if(m==ENVIRON["T"]){f=1;exit}} END{exit !f}'"'"' "${'$'}_mi" 2>/dev/null; then
                                mount -o bind "${'$'}MX_OAT" "${'$'}MX_DST/oat" 2>/dev/null
                            fi
                            "${'$'}MX_BB" mount -o remount,rw,exec,bind "${'$'}MX_DST/oat" 2>/dev/null || true
                            "${'$'}MX_BB" mount --make-rprivate "${'$'}MX_DST/oat" 2>/dev/null || true
                        fi
                    fi
                ' 2>/dev/null
            done
        """.trimIndent()
        RootShell.execScript(script)
    }

    /**
     * Scans all external storage locations and MountX vaults to determine whether a complete,
     * installable APK bundle exists on external storage for [packageName].
     */
    suspend fun inspectSdApkBundle(
        context: Context,
        packageName: String,
        sdBases: List<String>,
        mountPoints: List<MountPointConfig> = emptyList()
    ): SdApkInstallStatus = withContext(Dispatchers.IO) {
        val candidates = linkedSetOf<String>()
        for (mp in mountPoints) {
            if (mp.resolveCategory() == MountPointCategory.APP_PACKAGE && !mp.isLibTarget()) {
                mp.containerImgPath?.takeIf { it.isNotBlank() }?.let { candidates.add(it.trimEnd('/')) }
                mp.sourcePath.takeIf { it.isNotBlank() }?.let { candidates.add(it.trimEnd('/')) }
            }
        }
        for (rawBase in sdBases) {
            val base = rawBase.trimEnd('/')
            if (base.isBlank()) continue
            candidates.add("$base/MountX/app/$packageName")
            candidates.add("$base/.mountx/apk_backup/$packageName")
            candidates.add("$base/.mountx/containers/${packageName}_apk.img")
            candidates.add("$base/Android/app/$packageName")
            candidates.add("$base/MountX/Android/app/$packageName")
        }

        if (candidates.isEmpty()) {
            return@withContext SdApkInstallStatus(
                packageName = packageName,
                missingReason = context.getString(R.string.apk_incomplete_reason_no_base)
            )
        }

        val candBlock = candidates.joinToString("\n")
        val tempMount = "/dev/mountx_inspect_apk_${packageName.replace(Regex("[^a-zA-Z0-9._]"), "_")}"
        var attachedTempLoop = false
        var inspectedContainerPath: String? = null
        var resolvedDir = ""

        try {
            val probeScript = """
                FOUND_MODE=""
                FOUND_PATH=""
                LOOP_MNT="$tempMount"
                while IFS= read -r cand; do
                    [ -n "${'$'}cand" ] || continue
                    case "${'$'}cand" in
                        *.img)
                            if [ -f "${'$'}cand" ] && [ -s "${'$'}cand" ]; then
                                umount -l "${'$'}LOOP_MNT" 2>/dev/null
                                mkdir -p "${'$'}LOOP_MNT" 2>/dev/null
                                _ld=""
                                for _bf in /sys/block/loop*/loop/backing_file; do
                                    if [ -f "${'$'}_bf" ] && [ "$(cat "${'$'}_bf" 2>/dev/null)" = "${'$'}cand" ]; then
                                        _ld="/dev/block/$(basename "$(dirname "$(dirname "${'$'}_bf")")")"
                                        break
                                    fi
                                done
                                if [ -z "${'$'}_ld" ]; then
                                    _ld=$(losetup -f 2>/dev/null)
                                    [ -n "${'$'}_ld" ] && { losetup -r "${'$'}_ld" "${'$'}cand" 2>/dev/null || losetup "${'$'}_ld" "${'$'}cand" 2>/dev/null || _ld=""; }
                                fi
                                if [ -n "${'$'}_ld" ] && mount -t ext4 -o ro "${'$'}_ld" "${'$'}LOOP_MNT" 2>/dev/null; then
                                    if ls "${'$'}LOOP_MNT"/*.apk >/dev/null 2>&1; then
                                        FOUND_MODE="IMG"
                                        FOUND_PATH="${'$'}cand"
                                        break
                                    fi
                                    umount -l "${'$'}LOOP_MNT" 2>/dev/null
                                    losetup -d "${'$'}_ld" 2>/dev/null || true
                                fi
                                rmdir "${'$'}LOOP_MNT" 2>/dev/null || true
                            fi
                            ;;
                        *)
                            if [ -d "${'$'}cand" ] && ls "${'$'}cand"/*.apk >/dev/null 2>&1; then
                                FOUND_MODE="DIR"
                                FOUND_PATH="${'$'}cand"
                                break
                            fi
                            ;;
                    esac
                done <<'__MX_CANDS__'
$candBlock
__MX_CANDS__
                [ -z "${'$'}FOUND_MODE" ] && exit 0
                echo "MODE=${'$'}FOUND_MODE"
                echo "PATH=${'$'}FOUND_PATH"
                SCAN_DIR="${'$'}FOUND_PATH"
                [ "${'$'}FOUND_MODE" = "IMG" ] && SCAN_DIR="${'$'}LOOP_MNT"
                echo "SCAN_DIR=${'$'}SCAN_DIR"
                for f in "${'$'}SCAN_DIR"/*.apk; do
                    [ -f "${'$'}f" ] || continue
                    sz=$(stat -c '%s' "${'$'}f" 2>/dev/null || wc -c < "${'$'}f" 2>/dev/null | tr -d ' ')
                    pk=0
                    case "$(dd if="${'$'}f" bs=2 count=1 2>/dev/null)" in PK*) pk=1 ;; esac
                    echo "APK|${'$'}sz|${'$'}pk|${'$'}f"
                done
            """.trimIndent()

            val res = RootShell.execScript(probeScript, timeoutMillis = 20_000L)
            val lines = res.stdout.map { it.trim() }.filter { it.isNotBlank() }
            val mode = lines.firstOrNull { it.startsWith("MODE=") }?.substringAfter("MODE=") ?: ""
            val path = lines.firstOrNull { it.startsWith("PATH=") }?.substringAfter("PATH=") ?: ""
            val scanDir = lines.firstOrNull { it.startsWith("SCAN_DIR=") }?.substringAfter("SCAN_DIR=") ?: path

            if (mode == "IMG") {
                attachedTempLoop = true
                inspectedContainerPath = path
            }
            resolvedDir = scanDir

            if (mode.isBlank() || scanDir.isBlank()) {
                return@withContext SdApkInstallStatus(
                    packageName = packageName,
                    isAvailable = false,
                    isComplete = false,
                    missingReason = context.getString(R.string.apk_incomplete_reason_no_base)
                )
            }

            data class ParsedApkFile(val path: String, val name: String, val size: Long, val validZip: Boolean)

            val parsedFiles = lines.filter { it.startsWith("APK|") }.mapNotNull { line ->
                val parts = line.split("|", limit = 4)
                if (parts.size == 4) {
                    val fPath = parts[3]
                    ParsedApkFile(
                        path = fPath,
                        name = File(fPath).name,
                        size = parts[1].toLongOrNull() ?: 0L,
                        validZip = parts[2] == "1"
                    )
                } else null
            }

            val baseApk = parsedFiles.firstOrNull { it.name.equals("base.apk", ignoreCase = true) }
                ?: parsedFiles.singleOrNull()
            if (baseApk == null || baseApk.size < 4 * 1024L || !baseApk.validZip) {
                return@withContext SdApkInstallStatus(
                    packageName = packageName,
                    isAvailable = parsedFiles.isNotEmpty(),
                    isComplete = false,
                    sourceDir = if (mode == "IMG") "" else resolvedDir,
                    containerImgPath = inspectedContainerPath,
                    isVirtualContainer = mode == "IMG",
                    apkFiles = parsedFiles.map { it.path },
                    totalApkBytes = parsedFiles.sumOf { it.size },
                    missingReason = context.getString(
                        if (baseApk == null) R.string.apk_incomplete_reason_no_base else R.string.apk_incomplete_reason_corrupt
                    )
                )
            }

            val invalidSplit = parsedFiles.firstOrNull { it.path != baseApk.path && (it.size < 2048L || !it.validZip) }
            if (invalidSplit != null) {
                return@withContext SdApkInstallStatus(
                    packageName = packageName,
                    isAvailable = true,
                    isComplete = false,
                    sourceDir = if (mode == "IMG") "" else resolvedDir,
                    containerImgPath = inspectedContainerPath,
                    isVirtualContainer = mode == "IMG",
                    apkFiles = parsedFiles.map { it.path },
                    totalApkBytes = parsedFiles.sumOf { it.size },
                    missingReason = context.getString(R.string.apk_incomplete_reason_corrupt)
                )
            }

            // Inspect binary AndroidManifest inside base.apk safely
            var pkgInfo: PackageInfo? = null
            var tempStagedApk: File? = null
            var readableApk = false
            try {
                val pm = context.packageManager
                val baseFile = File(baseApk.path)
                val probePath = if (baseFile.canRead()) {
                    readableApk = true
                    baseApk.path
                } else {
                    val tmpFile = File(context.cacheDir, "mx_probe_${packageName.hashCode()}.apk")
                    tempStagedApk = tmpFile
                    val cpRes = RootShell.exec("cp \"${baseApk.path}\" \"${tmpFile.absolutePath}\" && chmod 644 \"${tmpFile.absolutePath}\"")
                    if (cpRes.isSuccess && tmpFile.canRead()) {
                        readableApk = true
                        tmpFile.absolutePath
                    } else null
                }
                if (probePath != null) {
                    pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        pm.getPackageArchiveInfo(probePath, PackageManager.PackageInfoFlags.of(PackageManager.GET_META_DATA.toLong()))
                    } else {
                        @Suppress("DEPRECATION")
                        pm.getPackageArchiveInfo(probePath, PackageManager.GET_META_DATA)
                    }
                    pkgInfo?.applicationInfo?.sourceDir = probePath
                    pkgInfo?.applicationInfo?.publicSourceDir = probePath
                }
            } catch (e: Exception) {
                AppLogger.warn("AppPackageInspector", "Manifest parse note for $packageName: ${e.message}")
            } finally {
                tempStagedApk?.delete()
            }

            if (readableApk && (pkgInfo == null || pkgInfo.packageName != packageName)) {
                return@withContext SdApkInstallStatus(
                    packageName = packageName,
                    isAvailable = true,
                    isComplete = false,
                    sourceDir = if (mode == "IMG") "" else resolvedDir,
                    containerImgPath = inspectedContainerPath,
                    isVirtualContainer = mode == "IMG",
                    apkFiles = parsedFiles.map { it.path },
                    totalApkBytes = parsedFiles.sumOf { it.size },
                    missingReason = context.getString(R.string.apk_incomplete_reason_corrupt)
                )
            }

            val splitFiles = parsedFiles.filter { it.path != baseApk.path }
            val splitsRequired = pkgInfo?.applicationInfo?.metaData?.getBoolean("com.android.vending.splits.required", false) == true
            if (splitsRequired && splitFiles.isEmpty()) {
                return@withContext SdApkInstallStatus(
                    packageName = packageName,
                    isAvailable = true,
                    isComplete = false,
                    sourceDir = if (mode == "IMG") "" else resolvedDir,
                    containerImgPath = inspectedContainerPath,
                    isVirtualContainer = mode == "IMG",
                    apkFiles = parsedFiles.map { it.path },
                    totalApkBytes = parsedFiles.sumOf { it.size },
                    missingReason = context.getString(R.string.apk_incomplete_reason_missing_splits)
                )
            }

            val label = pkgInfo?.applicationInfo?.let { ai ->
                runCatching { ai.loadLabel(context.packageManager).toString() }.getOrNull()
            }?.takeIf { it.isNotBlank() && it != packageName }

            val verName = pkgInfo?.versionName
            val verCode = pkgInfo?.let { PackageInfoCompat.getLongVersionCode(it) } ?: 0L
            val orderedPaths = listOf(baseApk.path) + splitFiles.sortedBy { it.name }.map { it.path }

            SdApkInstallStatus(
                packageName = packageName,
                isAvailable = true,
                isComplete = true,
                sourceDir = if (mode == "IMG") path else resolvedDir,
                containerImgPath = inspectedContainerPath,
                isVirtualContainer = mode == "IMG",
                apkFiles = if (mode == "IMG") parsedFiles.map { it.name } else orderedPaths,
                totalApkBytes = parsedFiles.sumOf { it.size },
                appLabel = label,
                versionName = verName,
                versionCode = verCode,
                splitCount = splitFiles.size,
                missingReason = null
            )
        } finally {
            if (attachedTempLoop) {
                val cleanImg = inspectedContainerPath ?: ""
                RootShell.execScript(
                    """
                    umount -l "$tempMount" 2>/dev/null || true
                    rmdir "$tempMount" 2>/dev/null || true
                    if [ -n "$cleanImg" ]; then
                        _ld=$(losetup -a 2>/dev/null | grep -F "$cleanImg" | cut -d: -f1 | head -n 1)
                        [ -n "${'$'}_ld" ] && ! grep -q "^${'$'}_ld " /proc/mounts 2>/dev/null && losetup -d "${'$'}_ld" 2>/dev/null || true
                    fi
                    """.trimIndent()
                )
            }
        }
    }

    /**
     * Installs an app directly from a validated [SdApkInstallStatus] bundle via `pm install-create`
     * session API (supporting single APKs, split App Bundles, and virtual `.img` containers),
     * then reconciles UID/GID/SELinux permissions across all saved MountX storage folders.
     */
    suspend fun installAppFromSd(
        context: Context,
        status: SdApkInstallStatus,
        sdBases: List<String>,
        onProgress: ((Float, String) -> Unit)? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(status.isComplete && status.sourceDir.isNotBlank()) {
                status.missingReason ?: context.getString(R.string.apk_incomplete_reason_no_base)
            }
            val pkg = status.packageName
            onProgress?.invoke(0.15f, context.getString(R.string.install_step_preparing, pkg))

            val tempMount = "/dev/mountx_inst_apk_${pkg.replace(Regex("[^a-zA-Z0-9._]"), "_")}"
            val imgFile = status.containerImgPath?.takeIf { it.isNotBlank() } ?: status.sourceDir
            var mountedContainer = false

            try {
                val activeApkDir = if (status.isVirtualContainer) {
                    val mntRes = RootShell.execScript(
                        """
                        mkdir -p "$tempMount" 2>/dev/null
                        umount -l "$tempMount" 2>/dev/null
                        _ld=$(losetup -f 2>/dev/null)
                        [ -n "${'$'}_ld" ] || exit 1
                        losetup -r "${'$'}_ld" "$imgFile" 2>/dev/null || losetup "${'$'}_ld" "$imgFile" 2>/dev/null || exit 1
                        mount -t ext4 -o ro "${'$'}_ld" "$tempMount" 2>/dev/null || { losetup -d "${'$'}_ld" 2>/dev/null; exit 1; }
                        """.trimIndent()
                    )
                    if (!mntRes.isSuccess) {
                        error(context.getString(R.string.apk_install_err_container_mount))
                    }
                    mountedContainer = true
                    tempMount
                } else {
                    status.sourceDir.trimEnd('/')
                }

                // Clean any broken/deleted package remnants blocking installation
                RootShell.exec("am force-stop \"$pkg\" 2>/dev/null")
                onProgress?.invoke(0.40f, context.getString(R.string.install_step_installing, status.appLabel ?: pkg))

                val installScript = """
                    DIR="$activeApkDir"
                    _apks=""
                    _count=0
                    if [ -f "${'$'}DIR/base.apk" ]; then
                        _apks="${'$'}DIR/base.apk"
                        _count=1
                    fi
                    for _f in "${'$'}DIR"/*.apk; do
                        [ -f "${'$'}_f" ] || continue
                        case "${'$'}_f" in
                            "${'$'}DIR/base.apk") continue ;;
                        esac
                        if [ -n "${'$'}_apks" ]; then
                            _apks="${'$'}_apks
${'$'}_f"
                        else
                            _apks="${'$'}_f"
                        fi
                        _count=$((_count + 1))
                    done
                    [ "${'$'}_count" -gt 0 ] || { echo "ERR:No valid .apk files in ${'$'}DIR"; exit 2; }
                    _first_apk=$(printf '%s\n' "${'$'}_apks" | head -n 1)
                    _create_out=$(pm install-create -r -d 2>&1)
                    _sid=$(echo "${'$'}_create_out" | sed -n 's/.*\[\([0-9][0-9]*\)\].*/\1/p' | head -n 1)
                    if [ -z "${'$'}_sid" ]; then
                        if [ "${'$'}_count" -eq 1 ]; then
                            _fb=$(pm install -r -d "${'$'}_first_apk" 2>&1)
                            echo "${'$'}_fb"
                            echo "${'$'}_fb" | grep -qi "Success" && exit 0
                        fi
                        echo "${'$'}_create_out"
                        exit 1
                    fi
                    _idx=0
                    _write_err=""
                    while IFS= read -r _apk; do
                        [ -n "${'$'}_apk" ] || continue
                        _sz=$(stat -c '%s' "${'$'}_apk" 2>/dev/null || wc -c < "${'$'}_apk" 2>/dev/null | tr -d ' ')
                        _bn=$(basename "${'$'}_apk")
                        _wout=$(pm install-write -S "${'$'}_sz" "${'$'}_sid" "${'$'}{_idx}_${'$'}_bn" "${'$'}_apk" 2>&1)
                        _wrc=$?
                        if [ "${'$'}_wrc" -ne 0 ] || echo "${'$'}_wout" | grep -qiE "Failure|Error|Exception"; then
                            _write_err="${'$'}_wout"
                            break
                        fi
                        _idx=$((_idx + 1))
                    done <<__MX_APKS__
${'$'}_apks
__MX_APKS__
                    if [ -n "${'$'}_write_err" ]; then
                        pm install-abandon "${'$'}_sid" >/dev/null 2>&1 || true
                        echo "${'$'}_write_err"
                        exit 1
                    fi
                    _cout=$(pm install-commit "${'$'}_sid" 2>&1)
                    _crc=$?
                    echo "${'$'}_cout"
                    if [ "${'$'}_crc" -eq 0 ] && echo "${'$'}_cout" | grep -qi "Success"; then
                        exit 0
                    fi
                    pm install-abandon "${'$'}_sid" >/dev/null 2>&1 || true
                    exit 1
                """.trimIndent()

                val instRes = RootShell.execScript(installScript, timeoutMillis = 120_000L)
                if (!instRes.isSuccess) {
                    val msg = (instRes.stdout + instRes.stderr).joinToString("\n").trim()
                    error(context.getString(R.string.apk_install_err_pm_failed, msg.ifBlank { "exit ${instRes.code}" }))
                }

                onProgress?.invoke(0.75f, context.getString(R.string.install_step_reconciling))
                val newApkDir = queryLivePmApkDir(pkg)
                    ?: error(context.getString(R.string.apk_install_err_pm_failed, "No pm path after install"))

                val uid = RootShell.exec(
                    "pm list packages -U 2>/dev/null | grep -F \"package:$pkg \" | sed -n 's/.*uid:\\([0-9]*\\).*/\\1/p' | head -n 1"
                ).output.trim().toIntOrNull()
                    ?: runCatching { context.packageManager.getPackageUid(pkg, 0) }.getOrNull()
                    ?: 10000

                // Reconcile ownership & permissions on all preserved SD folders for the newly assigned UID
                val basesArg = sdBases.map { it.trimEnd('/') }.filter { it.isNotBlank() }.distinct().joinToString("\n")
                RootShell.execScript(
                    """
                    PKG="$pkg"
                    APP_UID="$uid"
                    pm unsuspend "${'$'}PKG" >/dev/null 2>&1 || true
                    appops set "${'$'}PKG" MANAGE_EXTERNAL_STORAGE allow 2>/dev/null || true
                    appops set "${'$'}PKG" LEGACY_STORAGE allow 2>/dev/null || true
                    pm grant "${'$'}PKG" android.permission.READ_EXTERNAL_STORAGE 2>/dev/null || true
                    pm grant "${'$'}PKG" android.permission.WRITE_EXTERNAL_STORAGE 2>/dev/null || true
                    while IFS= read -r _b; do
                        [ -n "${'$'}_b" ] || continue
                        for _priv in "${'$'}_b/MountX/data/${'$'}PKG" "${'$'}_b/MountX/cache/${'$'}PKG"; do
                            if [ -d "${'$'}_priv" ]; then
                                chown -R "${'$'}APP_UID:${'$'}APP_UID" "${'$'}_priv" 2>/dev/null
                                find "${'$'}_priv" -type d -exec chmod 700 {} + 2>/dev/null
                                find "${'$'}_priv" -type f -exec chmod 600 {} + 2>/dev/null
                                rm -f "${'$'}_priv/.mountx_perms_ok" 2>/dev/null
                                restorecon -FR "${'$'}_priv" 2>/dev/null || chcon -R u:object_r:app_data_file:s0 "${'$'}_priv" 2>/dev/null || true
                            fi
                        done
                        for _ext in \
                            "${'$'}_b/MountX/Android/data/${'$'}PKG" "${'$'}_b/Android/data/${'$'}PKG" \
                            "${'$'}_b/MountX/Android/obb/${'$'}PKG" "${'$'}_b/Android/obb/${'$'}PKG" \
                            "${'$'}_b/MountX/Android/media/${'$'}PKG" "${'$'}_b/Android/media/${'$'}PKG"; do
                            if [ -d "${'$'}_ext" ]; then
                                chown -R "${'$'}APP_UID:1023" "${'$'}_ext" 2>/dev/null
                                chmod -R 775 "${'$'}_ext" 2>/dev/null
                            fi
                        done
                        _img="${'$'}_b/.mountx/containers/${'$'}{PKG}_data.img"
                        if [ -f "${'$'}_img" ]; then
                            _tm="/dev/mountx_uidfix_${'$'}PKG"
                            mkdir -p "${'$'}_tm" 2>/dev/null
                            _ld=$(losetup -f 2>/dev/null)
                            if [ -n "${'$'}_ld" ] && losetup "${'$'}_ld" "${'$'}_img" 2>/dev/null && mount -t ext4 -o rw "${'$'}_ld" "${'$'}_tm" 2>/dev/null; then
                                chown -R "${'$'}APP_UID:${'$'}APP_UID" "${'$'}_tm" 2>/dev/null
                                find "${'$'}_tm" -type d -exec chmod 700 {} + 2>/dev/null
                                find "${'$'}_tm" -type f -exec chmod 600 {} + 2>/dev/null
                                sync
                                umount -l "${'$'}_tm" 2>/dev/null
                            fi
                            [ -n "${'$'}_ld" ] && losetup -d "${'$'}_ld" 2>/dev/null || true
                            rmdir "${'$'}_tm" 2>/dev/null || true
                        fi
                    done <<'__MX_BASES__'
$basesArg
__MX_BASES__
                    """.trimIndent(),
                    timeoutMillis = 60_000L
                )

                onProgress?.invoke(0.92f, context.getString(R.string.install_step_mounting))
                AppLogger.success("AppPackageInspector", "Installed $pkg cleanly from SD (${status.apkFiles.size} APK(s)) -> $newApkDir (UID $uid)")
                newApkDir
            } finally {
                if (mountedContainer) {
                    RootShell.execScript(
                        """
                        umount -l "$tempMount" 2>/dev/null || true
                        rmdir "$tempMount" 2>/dev/null || true
                        _ld=$(losetup -a 2>/dev/null | grep -F "$imgFile" | cut -d: -f1 | head -n 1)
                        [ -n "${'$'}_ld" ] && ! grep -q "^${'$'}_ld " /proc/mounts 2>/dev/null && losetup -d "${'$'}_ld" 2>/dev/null || true
                        """.trimIndent()
                    )
                }
            }
        }
    }
}
