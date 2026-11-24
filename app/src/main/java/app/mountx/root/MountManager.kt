package app.mountx.root

import android.content.Context
import app.mountx.R

import app.mountx.data.model.GameEntry
import app.mountx.data.model.MountMode
import app.mountx.data.model.MountPointCategory
import app.mountx.data.model.MountPointConfig
import app.mountx.data.model.OperationProgress
import app.mountx.data.model.OperationType
import app.mountx.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Exception thrown when internal storage has substantive data but MicroSD only has an empty skeleton.
 */
class OcclusionHazardException(
    val packageName: String,
    val internalBytes: Long,
    val sdBytes: Long,
    message: String
) : IllegalStateException(message)

/**
 * Exception thrown when attempting to mount a game that is already active from a different storage disk.
 */
class MultiDiskCollisionException(
    val packageName: String,
    val activeDevice: String,
    val requestedDevice: String,
    message: String
) : IllegalStateException(message)

/**
 * Handles mounting and unmounting game & application data directories using bind mounts
 * and Virtual Ext4 Loop Containers for Universal Smart Directory Classification.
 */
class MountManager(
    private val context: Context? = null
) {

    private fun getAppContext(): Context = context ?: app.mountx.MountXApp.instance

    /** Resolves all active Android user IDs (User 0, Dual Apps 999, Work Profile 10+, etc.) */
    suspend fun getActiveUserIds(): List<Int> = MountManager.getActiveUserIds()

    /** Dynamically detects all target runtime namespaces across active users/profiles or an isolated user */
    suspend fun getTargetNamespaces(specificUserId: Int? = null): List<String> = MountManager.getTargetNamespaces(specificUserId)

    /** Purges all active, nested, or stacked mounts matching a target path across all namespaces. */
    suspend fun sweepUnmountTarget(targetPath: String): Boolean = MountManager.sweepUnmountTarget(targetPath)

    /** Universal sweeping unmount: purge any remaining or stacked mounts for a package across all namespaces. */
    suspend fun sweepUnmountPackage(packageName: String): Boolean = MountManager.sweepUnmountPackage(packageName)

    companion object {
        private val mountOperationMutex = Mutex()

        /** Resolves all active Android user IDs (User 0, Dual Apps 999, Work Profile 10+, etc.) */
        suspend fun getActiveUserIds(): List<Int> = withContext(Dispatchers.IO) {
            val userIds = mutableListOf(0)
            val pmUsers = RootShell.exec("pm list users 2>/dev/null").stdout
            for (line in pmUsers) {
                val match = Regex("UserInfo\\{(\\d+):").find(line)
                val id = match?.groupValues?.get(1)?.toIntOrNull()
                if (id != null && !userIds.contains(id)) {
                    userIds.add(id)
                }
            }
            val mediaDirs = RootShell.exec("ls -1d /data/media/* 2>/dev/null").stdout
            for (dir in mediaDirs) {
                val name = dir.trim().substringAfterLast('/')
                val id = name.toIntOrNull()
                if (id != null && !userIds.contains(id)) {
                    userIds.add(id)
                }
            }
            userIds.distinct()
        }

        /** Dynamically detects all target runtime namespaces across active users/profiles or an isolated user */
        private val UNSAFE_BIND_REL_PATHS = setOf("android", "android/data", "android/obb", "android/media")

        private fun isUnsafeBindRelativePath(relPath: String): Boolean {
            val clean = relPath.trim().trim('/').lowercase()
            return clean.isBlank() || clean in UNSAFE_BIND_REL_PATHS
        }

        /**
         * Resolves unique PIDs owning distinct `/proc/<pid>/ns/mnt` namespaces across Zygote and idle (`uid == 0`)
         * USAP workers without ever signalling/killing `usap64`/`usap32` (external SIGTERM leaves stale
         * `read_pipe_fd` entries that cause a permanent `POLLHUP` 100% CPU loop in AOSP `ZygoteServer.runSelectLoop()`).
         */
        internal val ZYGOTE_MNT_PIDS_SNIPPET = """
            _zpids=""
            _zseen=""
            for _zname in zygote64 zygote usap64 usap32; do
              for _zcand in ${'$'}(pidof "${'$'}_zname" 2>/dev/null); do
                [ -e "/proc/${'$'}_zcand/ns/mnt" ] || continue
                _zuid=${'$'}(awk '/^Uid:/{print ${'$'}2; exit}' "/proc/${'$'}_zcand/status" 2>/dev/null)
                case "${'$'}_zuid" in
                  ""|0) ;;
                  *) continue ;;
                esac
                _zns=${'$'}(readlink "/proc/${'$'}_zcand/ns/mnt" 2>/dev/null)
                [ -n "${'$'}_zns" ] || _zns="pid:${'$'}_zcand"
                case " ${'$'}_zseen " in
                  *" ${'$'}_zns "*) continue ;;
                esac
                _zseen="${'$'}_zseen ${'$'}_zns"
                _zpids="${'$'}_zpids ${'$'}_zcand"
              done
            done
            _zpids=${'$'}{_zpids# }
        """.trimIndent()

        /**
         * Safely cleans any corrupted parent occlusion mounts (`.../Android/(data|obb|media)` backed by MountX)
         * and peels stacked/dead FUSE `ENOTCONN` layers across PID 1 and active Zygote namespaces.
         * Never uses `umount -f` (`MNT_FORCE`) so F2FS superblocks never suffer checkpoint shutdowns (`EIO`).
         */
        suspend fun repairDeadFuseAndCorruptedParents() = withContext(Dispatchers.IO) {
            val script = """
                _mi=/proc/1/mountinfo
                [ -r "${'$'}_mi" ] || _mi=/proc/self/mountinfo
                [ -r "${'$'}_mi" ] || exit 0
${ZYGOTE_MNT_PIDS_SNIPPET}
                umount_all_ns() {
                  _t="${'$'}1"
                  [ -n "${'$'}_t" ] || return 0
                  umount "${'$'}_t" 2>/dev/null || umount -l "${'$'}_t" 2>/dev/null
                  for _zp in ${'$'}_zpids; do
                    [ -d "/proc/${'$'}_zp/ns" ] || continue
                    nsenter --mount="/proc/${'$'}_zp/ns/mnt" -- sh -c 'umount "${'$'}1" 2>/dev/null || umount -l "${'$'}1" 2>/dev/null' _ "${'$'}_t" 2>/dev/null
                  done
                }

                # 1. Peel any corrupted parent occlusion mounts directly on .../Android/(data|obb|media|)$
                _occl=${'$'}(awk -F ' - ' '{
                  split(${'$'}1, L, " ");
                  root = L[4]; mp = L[5]; gsub(/\\040/, " ", mp);
                  if (mp ~ /(\/Android\/(data|obb|media)|\/Android|\/emulated\/[0-9]+)${'$'}/ && root ~ /(^|\/)MountX(\/|${'$'})/) print mp
                }' "${'$'}_mi" 2>/dev/null | awk '!seen[${'$'}0]++')
                for _op in ${'$'}_occl; do
                  _kids=${'$'}(T="${'$'}_op/" awk '{p=${'$'}5; gsub(/\\040/," ",p); if (index(p, ENVIRON["T"])==1) print length(p), p}' "${'$'}_mi" 2>/dev/null | sort -rn -k1,1 | cut -d' ' -f2- | awk '!seen[${'$'}0]++')
                  echo "${'$'}_kids" | while IFS= read -r _k; do
                    [ -n "${'$'}_k" ] && umount_all_ns "${'$'}_k"
                  done
                  umount_all_ns "${'$'}_op"
                done

                # 2. Detect stacked duplicate FUSE mounts or dead ENOTCONN FUSE endpoints on emulated roots
                _roots=${'$'}(awk -F ' - ' '{
                  split(${'$'}1, L, " "); split(${'$'}2, R, " ");
                  dev = L[3]; mp = L[5]; gsub(/\\040/, " ", mp); fst = R[1];
                  if (mp ~ /^(\/storage\/emulated|\/mnt\/(user|installer|androidwritable|pass_through)\/[0-9]+\/emulated|\/mnt\/runtime\/(default|read|write|full)\/emulated)${'$'}/) {
                    cnt[mp]++; devs[mp] = (devs[mp] ? devs[mp] "|" dev : dev); fsts[mp] = fst
                  }
                } END {
                  for (m in cnt) {
                    if (fsts[m] ~ /fuse/ || cnt[m] > 1) print cnt[m] "|" m
                  }
                }' "${'$'}_mi" 2>/dev/null | sort -r)

                echo "${'$'}_roots" | while IFS='|' read -r _cnt _rmp; do
                  [ -n "${'$'}_rmp" ] || continue
                  _enotconn=0
                  _st=${'$'}(LC_ALL=C stat -t "${'$'}_rmp" 2>&1)
                  case "${'$'}_st" in *"Transport endpoint"*) _enotconn=1 ;; esac
                  if [ "${'$'}_enotconn" -eq 0 ] && [ -e "${'$'}_rmp/0" ]; then
                    _st0=${'$'}(LC_ALL=C stat -t "${'$'}_rmp/0" 2>&1)
                    case "${'$'}_st0" in *"Transport endpoint"*) _enotconn=1 ;; esac
                  fi
                  if [ "${'$'}_enotconn" -eq 1 ] || [ "${'$'}_cnt" -gt 1 ]; then
                    _fkids=${'$'}(T="${'$'}_rmp/" awk '{p=${'$'}5; gsub(/\\040/," ",p); if (index(p, ENVIRON["T"])==1) print length(p), p}' "${'$'}_mi" 2>/dev/null | sort -rn -k1,1 | cut -d' ' -f2- | awk '!seen[${'$'}0]++')
                    echo "${'$'}_fkids" | while IFS= read -r _fk; do
                      [ -n "${'$'}_fk" ] && umount_all_ns "${'$'}_fk"
                    done
                    umount_all_ns "${'$'}_rmp"
                  fi
                done
            """.trimIndent()
            RootShell.execScript(script)
        }

        /**
         * Injects (source -> target) bind mounts into active Zygote mount namespaces using clean `sh -s` heredoc
         * invocations (one `nsenter` per Zygote PID) so shell quoting errors can never kill `libsu`.
         */
        suspend fun syncBindTargetsToZygotes(
            pairs: List<Pair<String, String>>,
            isNonPosix: Boolean = false,
            preserveExec: Boolean = false
        ) = withContext(Dispatchers.IO) {
            if (pairs.isEmpty()) return@withContext
            val cleanPairs = pairs.map { it.first.trimEnd('/') to it.second.trimEnd('/') }
                .filter { it.first.isNotBlank() && it.second.isNotBlank() }
                .distinct()
            if (cleanPairs.isEmpty()) return@withContext

            val pairLines = cleanPairs.joinToString("\n") { "${it.first}|${it.second}" }
            val nonPosixFlag = if (isNonPosix) "1" else "0"
            val execFlag = if (preserveExec) "1" else "0"
            val script = """
${ZYGOTE_MNT_PIDS_SNIPPET}
                [ -n "${'$'}_zpids" ] || exit 0
                for _zp in ${'$'}_zpids; do
                  [ -d "/proc/${'$'}_zp/ns" ] || continue
                  nsenter --mount="/proc/${'$'}_zp/ns/mnt" -- sh -s "$nonPosixFlag" "$execFlag" <<'__MX_Z_BIND__' 2>/dev/null
                _non_posix="${'$'}1"
                _want_exec="${'$'}2"
                _mi=/proc/self/mountinfo
                is_mp() {
                  if [ -r "${'$'}_mi" ]; then
                    T="${'$'}1" awk '{m=${'$'}5;gsub(/\\040/," ",m);if(m==ENVIRON["T"]){f=1;exit}} END{exit !f}' "${'$'}_mi" 2>/dev/null
                  else
                    mountpoint -q "${'$'}1" 2>/dev/null
                  fi
                }
                root_alive() {
                  _p="${'$'}1"
                  case "${'$'}_p" in
                    /storage/emulated/*) _root="/storage/emulated" ;;
                    /mnt/user/[0-9]*/emulated/*) _u="${'$'}{_p#/mnt/user/}"; _root="/mnt/user/${'$'}{_u%%/*}/emulated" ;;
                    /mnt/installer/[0-9]*/emulated/*) _u="${'$'}{_p#/mnt/installer/}"; _root="/mnt/installer/${'$'}{_u%%/*}/emulated" ;;
                    /mnt/androidwritable/[0-9]*/emulated/*) _u="${'$'}{_p#/mnt/androidwritable/}"; _root="/mnt/androidwritable/${'$'}{_u%%/*}/emulated" ;;
                    /mnt/pass_through/[0-9]*/emulated/*) _u="${'$'}{_p#/mnt/pass_through/}"; _root="/mnt/pass_through/${'$'}{_u%%/*}/emulated" ;;
                    /mnt/runtime/*/emulated/*) _r="${'$'}{_p#/mnt/runtime/}"; _root="/mnt/runtime/${'$'}{_r%%/*}/emulated" ;;
                    *) return 0 ;;
                  esac
                  [ -r "${'$'}_mi" ] || return 0
                  _fst=${'$'}(T="${'$'}_root" awk -F ' - ' '{split(${'$'}1,L," ");split(${'$'}2,R," ");m=L[5];gsub(/\\040/," ",m);if(m==ENVIRON["T"])f=R[1]} END{print f}' "${'$'}_mi" 2>/dev/null)
                  case "${'$'}_fst" in ""|tmpfs) return 1 ;; esac
                  return 0
                }
                while IFS='|' read -r _src _tgt; do
                  [ -n "${'$'}_src" ] && [ -n "${'$'}_tgt" ] || continue
                  [ -d "${'$'}_src" ] || continue
                  root_alive "${'$'}_tgt" || continue
                  _parent=${'$'}{_tgt%/*}
                  if [ -n "${'$'}_parent" ] && [ -d "${'$'}_parent" ]; then
                    _pst=${'$'}(LC_ALL=C stat -t "${'$'}_parent" 2>&1)
                    case "${'$'}_pst" in *"Transport endpoint"*) continue ;; esac
                  fi
                  _need=0
                  if ! is_mp "${'$'}_tgt"; then
                    _need=1
                  else
                    if [ "${'$'}_non_posix" -eq 0 ]; then
                      _si=${'$'}(stat -c '%d:%i' "${'$'}_src" 2>/dev/null)
                      _ti=${'$'}(stat -c '%d:%i' "${'$'}_tgt" 2>/dev/null)
                      if [ -n "${'$'}_si" ] && [ "${'$'}_si" != "${'$'}_ti" ]; then _need=1; fi
                    fi
                    if [ -e "${'$'}_src/.mountx_canary" ] && [ ! -e "${'$'}_tgt/.mountx_canary" ]; then
                      _need=1
                    fi
                    if [ "${'$'}_need" -eq 1 ]; then
                      _c=0
                      while [ ${'$'}_c -lt 16 ] && is_mp "${'$'}_tgt"; do
                        umount "${'$'}_tgt" 2>/dev/null || umount -l "${'$'}_tgt" 2>/dev/null || break
                        _c=${'$'}((_c+1))
                      done
                    fi
                  fi
                  if [ "${'$'}_need" -eq 1 ]; then
                    mkdir -p "${'$'}_tgt" 2>/dev/null || continue
                    if mount -o bind "${'$'}_src" "${'$'}_tgt" 2>/dev/null; then
                      if [ "${'$'}_want_exec" -eq 1 ]; then
                        mount -o remount,bind,exec "${'$'}_tgt" 2>/dev/null || mount -o remount,exec "${'$'}_tgt" 2>/dev/null
                      fi
                      busybox mount --make-rprivate "${'$'}_tgt" 2>/dev/null || mount --make-rprivate "${'$'}_tgt" 2>/dev/null
                    fi
                  else
                    busybox mount --make-rprivate "${'$'}_tgt" 2>/dev/null || mount --make-rprivate "${'$'}_tgt" 2>/dev/null
                  fi
                done <<'__MX_PAIRS__'
$pairLines
__MX_PAIRS__
__MX_Z_BIND__
                done
            """.trimIndent()
            RootShell.execScript(script)
        }

        /**
         * Resolves active emulated/runtime VFS namespaces backed by a live non-tmpfs storage filesystem
         * (FUSE / sdcardfs / esdfs / lower `/data/media` when sdcardfs is absent) across Android 10–17.
         */
        suspend fun getTargetNamespaces(specificUserId: Int? = null): List<String> = withContext(Dispatchers.IO) {
            repairDeadFuseAndCorruptedParents()
            val userIds = if (specificUserId != null) listOf(specificUserId) else getActiveUserIds()
            val uidArgs = userIds.joinToString(" ")
            val script = """
                _mi=/proc/1/mountinfo
                [ -r "${'$'}_mi" ] || _mi=/proc/self/mountinfo
                _has_sdcardfs=0
                grep -qw sdcardfs /proc/filesystems 2>/dev/null && _has_sdcardfs=1
                _is_live_dir() {
                  _cand="${'$'}1"
                  _root="${'$'}2"
                  [ -d "${'$'}_cand" ] && [ ! -L "${'$'}_cand" ] || return 1
                  _st=${'$'}(LC_ALL=C stat -t "${'$'}_cand" 2>&1)
                  case "${'$'}_st" in *"Transport endpoint"*) return 1 ;; esac
                  [ -r "${'$'}_mi" ] || return 0
                  _fst=${'$'}(C="${'$'}_cand" R="${'$'}_root" awk -F ' - ' '{
                    split(${'$'}1, L, " "); split(${'$'}2, RT, " ");
                    m = L[5]; gsub(/\\040/, " ", m);
                    if (m == ENVIRON["C"] || m == ENVIRON["R"]) f = RT[1]
                  } END { print f }' "${'$'}_mi" 2>/dev/null)
                  case "${'$'}_fst" in
                    ""|tmpfs) return 1 ;;
                    *) return 0 ;;
                  esac
                }
                for _u in $uidArgs; do
                  for _r in \
                    "/mnt/runtime/default/emulated" \
                    "/mnt/runtime/read/emulated" \
                    "/mnt/runtime/write/emulated" \
                    "/mnt/runtime/full/emulated" \
                    "/mnt/user/${'$'}_u/emulated" \
                    "/mnt/installer/${'$'}_u/emulated" \
                    "/mnt/androidwritable/${'$'}_u/emulated" \
                    "/mnt/pass_through/${'$'}_u/emulated" \
                    "/storage/emulated"; do
                    _c="${'$'}_r/${'$'}_u"
                    if _is_live_dir "${'$'}_c" "${'$'}_r"; then
                      echo "${'$'}_c"
                    fi
                  done
                  if [ "${'$'}_has_sdcardfs" -eq 0 ] && [ -d "/data/media/${'$'}_u" ] && [ ! -L "/data/media/${'$'}_u" ]; then
                    echo "/data/media/${'$'}_u"
                  fi
                done | awk '!seen[${'$'}0]++'
            """.trimIndent()
            RootShell.execScript(script).output
                .lineSequence()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .toList()
        }

        /**
         * Purges all active, nested, or stacked mounts matching a target path across PID 1 and Zygotes.
         */
        suspend fun sweepUnmountTarget(targetPath: String): Boolean = withContext(Dispatchers.IO) {
            if (targetPath.isBlank() || targetPath == "/" || targetPath == ".") return@withContext false
            val cleanTarget = targetPath.trimEnd('/')
            val rel = extractRelativePath(cleanTarget)
            val sweepScript = """
                _mi=/proc/1/mountinfo
                [ -r "${'$'}_mi" ] || _mi=/proc/self/mountinfo
${ZYGOTE_MNT_PIDS_SNIPPET}
                umount_all_ns() {
                  umount "${'$'}1" 2>/dev/null || umount -l "${'$'}1" 2>/dev/null
                  for _zp in ${'$'}_zpids; do
                    [ -d "/proc/${'$'}_zp/ns" ] || continue
                    nsenter --mount="/proc/${'$'}_zp/ns/mnt" -- sh -c 'umount "${'$'}1" 2>/dev/null || umount -l "${'$'}1" 2>/dev/null' _ "${'$'}1" 2>/dev/null
                  done
                }
                for _i in 1 2; do
                  _list=${'$'}(T="$cleanTarget" R="$rel" awk '{
                    m=${'$'}5; gsub(/\\040/," ",m);
                    if (m ~ /^(\/|\/data|\/storage|\/storage\/emulated|\/storage\/emulated\/[0-9]+|.*\/Android|.*\/Android\/(data|obb|media))${'$'}/) next;
                    t=ENVIRON["T"]; r=ENVIRON["R"];
                    if ((t != "" && index(m, t) > 0) || (r != "" && index(m, r) > 0)) print length(m), m
                  }' "${'$'}_mi" 2>/dev/null | sort -rn -k1,1 | cut -d' ' -f2- | awk '!seen[${'$'}0]++')
                  [ -z "${'$'}_list" ] && break
                  echo "${'$'}_list" | while IFS= read -r _mnt; do
                    [ -n "${'$'}_mnt" ] && umount_all_ns "${'$'}_mnt"
                  done
                done
            """.trimIndent()
            RootShell.execScript(sweepScript).isSuccess
        }

        /**
         * Universal sweeping unmount: purge any remaining or stacked mounts for a package across PID 1 and Zygotes.
         */
        suspend fun sweepUnmountPackage(packageName: String): Boolean = withContext(Dispatchers.IO) {
            if (packageName.isBlank()) return@withContext false
            val sweepScript = """
                _mi=/proc/1/mountinfo
                [ -r "${'$'}_mi" ] || _mi=/proc/self/mountinfo
${ZYGOTE_MNT_PIDS_SNIPPET}
                umount_all_ns() {
                  umount "${'$'}1" 2>/dev/null || umount -l "${'$'}1" 2>/dev/null
                  for _zp in ${'$'}_zpids; do
                    [ -d "/proc/${'$'}_zp/ns" ] || continue
                    nsenter --mount="/proc/${'$'}_zp/ns/mnt" -- sh -c 'umount "${'$'}1" 2>/dev/null || umount -l "${'$'}1" 2>/dev/null' _ "${'$'}1" 2>/dev/null
                  done
                }
                for _i in 1 2 3; do
                  _list=${'$'}(P="$packageName" awk '{
                    m=${'$'}5; gsub(/\\040/," ",m);
                    if (index(m, ENVIRON["P"]) > 0) print length(m), m
                  }' "${'$'}_mi" 2>/dev/null | sort -rn -k1,1 | cut -d' ' -f2- | awk '!seen[${'$'}0]++')
                  [ -z "${'$'}_list" ] && break
                  echo "${'$'}_list" | while IFS= read -r _mnt; do
                    [ -n "${'$'}_mnt" ] && umount_all_ns "${'$'}_mnt"
                  done
                done
            """.trimIndent()
            RootShell.execScript(sweepScript).isSuccess
        }

        /**
         * Safely unmounts one or more paths across PID 1 and Zygote namespaces using regular/lazy umount
         * (`umount || umount -l`, NEVER `-f`), preventing F2FS `f2fs_umount_begin` `EIO` shutdowns.
         */
        suspend fun unmountSafeAcrossNamespaces(paths: List<String>) = withContext(Dispatchers.IO) {
            val cleanPaths = paths.map { it.trimEnd('/') }.filter { it.isNotBlank() && it != "/" }.distinct()
                .sortedByDescending { it.length }
            if (cleanPaths.isEmpty()) return@withContext
            val pathLines = cleanPaths.joinToString("\n")
            val script = """
                _mi=/proc/1/mountinfo
                [ -r "${'$'}_mi" ] || _mi=/proc/self/mountinfo
${ZYGOTE_MNT_PIDS_SNIPPET}
                while IFS= read -r _p; do
                  [ -n "${'$'}_p" ] || continue
                  _c=0
                  while [ ${'$'}_c -lt 16 ] && T="${'$'}_p" awk '{m=${'$'}5;gsub(/\\040/," ",m);if(m==ENVIRON["T"]){f=1;exit}} END{exit !f}' "${'$'}_mi" 2>/dev/null; do
                    umount "${'$'}_p" 2>/dev/null || umount -l "${'$'}_p" 2>/dev/null || break
                    _c=${'$'}((_c+1))
                  done
                  for _zp in ${'$'}_zpids; do
                    [ -d "/proc/${'$'}_zp/ns" ] || continue
                    nsenter --mount="/proc/${'$'}_zp/ns/mnt" -- sh -s "${'$'}_p" <<'__MX_ZU__' 2>/dev/null
                _t="${'$'}1"
                _c=0
                while [ "${'$'}_c" -lt 16 ] && T="${'$'}_t" awk '{m=${'$'}5;gsub(/\\040/," ",m);if(m==ENVIRON["T"]){f=1;exit}} END{exit !f}' /proc/self/mountinfo 2>/dev/null; do
                  umount "${'$'}_t" 2>/dev/null || umount -l "${'$'}_t" 2>/dev/null || break
                  _c=${'$'}((_c+1))
                done
__MX_ZU__
                  done
                done <<'__MX_UM__'
$pathLines
__MX_UM__
            """.trimIndent()
            RootShell.execScript(script)
        }

        /**
         * Verifies all deduplicated real peer targets (e.g., `/data/user/<uid>/...`, `/data/data/...`,
         * `/data_mirror/data_ce/null/<uid>/...`) are mounted cleanly once (`cnt == 1`) with matching
         * source inode/device. If any peer is unmounted, stacked (`cnt > 1`), or mismatched, purges
         * all layers across PID 1 and Zygote without `-f` and returns `false`.
         */
        suspend fun ensurePrivatePeerMountsReady(
            peerTargets: List<String>,
            expectedSource: String,
            expectedDevice: String = "",
            isContainer: Boolean = false,
            isNonPosix: Boolean = false
        ): Boolean = withContext(Dispatchers.IO) {
            val cleanPeers = peerTargets.map { it.trimEnd('/') }.filter { it.isNotBlank() }.distinct()
            if (cleanPeers.isEmpty()) return@withContext false
            val peerLines = cleanPeers.joinToString("\n")
            val isContFlag = if (isContainer) "1" else "0"
            val isNonPosixFlag = if (isNonPosix) "1" else "0"
            val checkScript = """
                _mi=/proc/1/mountinfo
                [ -r "${'$'}_mi" ] || _mi=/proc/self/mountinfo
                _src="$expectedSource"
                _exp_dev="$expectedDevice"
                _is_cont="$isContFlag"
                _non_posix="$isNonPosixFlag"
                _seen_canon=""
                _ready=1
                _src_ino=""
                if [ "${'$'}_is_cont" -eq 0 ] && [ "${'$'}_non_posix" -eq 0 ] && [ -e "${'$'}_src" ]; then
                  _src_ino=${'$'}(stat -c '%d:%i' "${'$'}_src" 2>/dev/null)
                fi
                while IFS= read -r _p; do
                  [ -n "${'$'}_p" ] && [ -d "${'$'}_p" ] && [ ! -L "${'$'}_p" ] || continue
                  _par=${'$'}{_p%/*}
                  _cpar=${'$'}(readlink -f "${'$'}_par" 2>/dev/null || echo "${'$'}_par")
                  _canon="${'$'}_cpar/${'$'}{_p##*/}"
                  case "${'$'}_seen_canon" in *"|${'$'}_canon|"*) continue ;; esac
                  _seen_canon="${'$'}_seen_canon|${'$'}_canon|"
                  _info=${'$'}(T="${'$'}_p" awk -F ' - ' '{
                    split(${'$'}1, L, " "); split(${'$'}2, R, " ");
                    m = L[5]; gsub(/\\040/, " ", m);
                    if (m == ENVIRON["T"]) { c++; src = R[2] }
                  } END { print c+0 "|" src }' "${'$'}_mi" 2>/dev/null)
                  _cnt=${'$'}{_info%%|*}
                  _mdev=${'$'}{_info#*|}
                  if [ "${'$'}_cnt" -ne 1 ]; then
                    _ready=0; break
                  fi
                  if [ "${'$'}_is_cont" -eq 1 ]; then
                    if [ -z "${'$'}_exp_dev" ] || [ "${'$'}_mdev" != "${'$'}_exp_dev" ]; then
                      _ready=0; break
                    fi
                  elif [ -n "${'$'}_src_ino" ]; then
                    _t_ino=${'$'}(stat -c '%d:%i' "${'$'}_p" 2>/dev/null)
                    if [ "${'$'}_t_ino" != "${'$'}_src_ino" ]; then
                      _ready=0; break
                    fi
                  fi
                done <<'__MX_PEERS__'
$peerLines
__MX_PEERS__
                if [ "${'$'}_ready" -eq 1 ]; then
                  while IFS= read -r _p; do
                    [ -n "${'$'}_p" ] && [ -d "${'$'}_p" ] && [ ! -L "${'$'}_p" ] || continue
                    busybox mount --make-rprivate "${'$'}_p" 2>/dev/null || mount --make-rprivate "${'$'}_p" 2>/dev/null
                  done <<'__MX_PEERS_RP__'
$peerLines
__MX_PEERS_RP__
                  echo "READY"
                else
                  echo "PURGE"
                fi
            """.trimIndent()
            val verdict = RootShell.execScript(checkScript).output.trim()
            if (verdict == "READY") {
                true
            } else {
                unmountSafeAcrossNamespaces(cleanPeers)
                false
            }
        }

        /**
         * Atomically binds secondary peer directories (checking `/proc/1/mountinfo` right before each bind
         * and skipping symlinked parents such as `/data/data -> /data/user/0`) so shared-peer groups
         * never produce 2x/4x duplicate stacked mounts, and returns non-symlink `(bindSrc -> target)` pairs
         * for Zygote namespace synchronization.
         */
        suspend fun bindSecondaryPrivatePeersAtomic(
            primaryTarget: String,
            secondaryTargets: List<String>,
            bindSource: String,
            seContext: String,
            recursiveChcon: Boolean = false
        ): List<Pair<String, String>> = withContext(Dispatchers.IO) {
            val cleanSecondary = secondaryTargets.map { it.trimEnd('/') }.filter { it.isNotBlank() && it != primaryTarget.trimEnd('/') }.distinct()
            val tgtLines = cleanSecondary.joinToString("\n")
            val chconFlag = if (recursiveChcon) "-R" else ""
            val script = """
                _mi=/proc/1/mountinfo
                [ -r "${'$'}_mi" ] || _mi=/proc/self/mountinfo
                _prim="${primaryTarget.trimEnd('/')}"
                _bsrc="${bindSource.trimEnd('/')}"
                _ctx="$seContext"
                _prim_cpar=${'$'}(readlink -f "${'$'}{_prim%/*}" 2>/dev/null || echo "${'$'}{_prim%/*}")
                _seen_canon="|${'$'}_prim_cpar/${'$'}{_prim##*/}|"
                if [ "${'$'}_bsrc" != "${'$'}_prim" ]; then
                  echo "${'$'}_bsrc|${'$'}_prim"
                fi
                while IFS= read -r _p; do
                  [ -n "${'$'}_p" ] && [ -d "${'$'}_p" ] && [ ! -L "${'$'}_p" ] || continue
                  _par=${'$'}{_p%/*}
                  [ ! -L "${'$'}_par" ] || continue
                  _cpar=${'$'}(readlink -f "${'$'}_par" 2>/dev/null || echo "${'$'}_par")
                  _canon="${'$'}_cpar/${'$'}{_p##*/}"
                  case "${'$'}_seen_canon" in *"|${'$'}_canon|"*) continue ;; esac
                  _seen_canon="${'$'}_seen_canon|${'$'}_canon|"
                  _cnt=${'$'}(T="${'$'}_p" awk '{m=${'$'}5;gsub(/\\040/," ",m);if(m==ENVIRON["T"])c++} END{print c+0}' "${'$'}_mi" 2>/dev/null)
                  if [ "${'$'}_cnt" -eq 0 ]; then
                    mount -o bind "${'$'}_bsrc" "${'$'}_p" 2>/dev/null
                  fi
                  busybox mount --make-rprivate "${'$'}_p" 2>/dev/null || mount --make-rprivate "${'$'}_p" 2>/dev/null
                  if [ -n "${'$'}_ctx" ]; then
                    chcon $chconFlag "${'$'}_ctx" "${'$'}_p" 2>/dev/null
                  fi
                  echo "${'$'}_bsrc|${'$'}_p"
                done <<'__MX_SEC__'
$tgtLines
__MX_SEC__
            """.trimIndent()
            RootShell.execScript(script).output.lineSequence()
                .mapNotNull { line ->
                    val parts = line.split("|", limit = 2)
                    if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) parts[0] to parts[1] else null
                }
                .toList()
        }

        /**
         * Ensures a mounted virtual ext4 loop container (`loopDev`) and any secondary peer bind targets
         * are present inside active Zygote namespaces.
         */
        suspend fun syncContainerTargetsToZygotes(
            loopDev: String,
            primaryTarget: String,
            secondaryTargets: List<String> = emptyList(),
            preserveExec: Boolean = false
        ) = withContext(Dispatchers.IO) {
            if (loopDev.isBlank() || primaryTarget.isBlank()) return@withContext
            val cleanPrim = primaryTarget.trimEnd('/')
            val secLines = secondaryTargets.map { it.trimEnd('/') }.filter { it.isNotBlank() && it != cleanPrim }.distinct().joinToString("\n")
            val execFlag = if (preserveExec) "1" else "0"
            val script = """
${ZYGOTE_MNT_PIDS_SNIPPET}
                [ -n "${'$'}_zpids" ] || exit 0
                for _zp in ${'$'}_zpids; do
                  [ -d "/proc/${'$'}_zp/ns" ] || continue
                  nsenter --mount="/proc/${'$'}_zp/ns/mnt" -- sh -s "$loopDev" "$cleanPrim" "$execFlag" <<'__MX_ZC__' 2>/dev/null
                _ldev="${'$'}1"
                _prim="${'$'}2"
                _want_exec="${'$'}3"
                _mi=/proc/self/mountinfo
                cur_src() {
                  T="${'$'}1" awk -F ' - ' '{split(${'$'}1,L," ");split(${'$'}2,R," ");m=L[5];gsub(/\\040/," ",m);if(m==ENVIRON["T"])s=R[2]} END{print s}' "${'$'}_mi" 2>/dev/null
                }
                _pcur=${'$'}(cur_src "${'$'}_prim")
                if [ "${'$'}_pcur" != "${'$'}_ldev" ]; then
                  if [ -n "${'$'}_pcur" ]; then umount "${'$'}_prim" 2>/dev/null || umount -l "${'$'}_prim" 2>/dev/null; fi
                  mkdir -p "${'$'}_prim" 2>/dev/null
                  if [ "${'$'}_want_exec" -eq 1 ]; then
                    mount -t ext4 -o rw,noatime "${'$'}_ldev" "${'$'}_prim" 2>/dev/null || mount -t ext4 -o ro "${'$'}_ldev" "${'$'}_prim" 2>/dev/null
                    mount -o remount,exec "${'$'}_prim" 2>/dev/null
                  else
                    mount -t ext4 -o rw,nosuid,nodev,noatime "${'$'}_ldev" "${'$'}_prim" 2>/dev/null || mount -t ext4 -o ro "${'$'}_ldev" "${'$'}_prim" 2>/dev/null
                  fi
                  busybox mount --make-rprivate "${'$'}_prim" 2>/dev/null || mount --make-rprivate "${'$'}_prim" 2>/dev/null
                fi
                while IFS= read -r _sp; do
                  [ -n "${'$'}_sp" ] && [ -d "${'$'}_sp" ] && [ ! -L "${'$'}_sp" ] || continue
                  _scur=${'$'}(cur_src "${'$'}_sp")
                  if [ -z "${'$'}_scur" ]; then
                    mount -o bind "${'$'}_prim" "${'$'}_sp" 2>/dev/null
                  fi
                  busybox mount --make-rprivate "${'$'}_sp" 2>/dev/null || mount --make-rprivate "${'$'}_sp" 2>/dev/null
                done <<'__MX_ZC_SEC__'
$secLines
__MX_ZC_SEC__
__MX_ZC__
                done
            """.trimIndent()
            RootShell.execScript(script)
        }
        /**
         * Resolves the Android user ID from an absolute storage path.
         * Defaults to 0 (primary user) if not identifiable.
         */
        fun resolveUserId(path: String): Int {
            val match = Regex("^/(?:storage/emulated|data/media|mnt/user|data/user)/(\\d+)").find(path)
            return match?.groupValues?.get(1)?.toIntOrNull() ?: 0
        }

        /**
         * Extracts clean relative directory path from an absolute Android path across any user profile.
         * e.g. /data/media/0/Android/data/com.foo -> Android/data/com.foo
         *      /storage/emulated/999/Android/obb/com.bar -> Android/obb/com.bar
         *      /sdcard/Download/1DM -> Download/1DM
         */
        /**
         * Attaches a container image file to an available loop device.
         * Compatible with both BusyBox (where --show is unsupported) and Toybox.
         * Returns the attached loop device path (e.g. /dev/block/loop0) or empty string on failure.
         */
        suspend fun resolveBusybox(): String? {
            val candidates = listOf(
                "/dev/mountx_bin/busybox",
                "/data/adb/modules/MountX/bin/busybox",
                "/data/adb/ap/bin/busybox",
                "/data/adb/ksu/bin/busybox",
                "/data/adb/magisk/busybox"
            )
            for (p in candidates) {
                if (RootShell.exec("[ -x \"$p\" ] && echo YES || echo NO").output.trim() == "YES") {
                    return p
                }
            }
            val whichRes = RootShell.execForOutput("which busybox 2>/dev/null").trim()
            if (whichRes.isNotBlank() && whichRes.contains("busybox")) {
                return whichRes
            }
            return null
        }

        /**
         * Ensures a dynamic sparse EXT4 .img container has sufficient free virtual capacity.
         * If the container has less than 2 GB virtual free space and the underlying storage has >4 GB free physical space,
         * automatically expands the sparse image (+4 GB) and triggers resize2fs.
         */
        suspend fun ensureSparseContainerCapacity(containerPath: String, sdBase: String, isFat32: Boolean = false) {
            if (!RootShell.exists(containerPath)) return
            if (isFat32) return // FAT32 has a hard 4GB file size limit

            val freeSdOut = RootShell.execForOutput("df -k \"$sdBase\" 2>/dev/null | tail -1").trim()
            val freeSdParts = freeSdOut.split(Regex("\\s+"))
            val freeSdKb = if (freeSdParts.size >= 4) freeSdParts[3].toLongOrNull() ?: 0L else 0L
            if (freeSdKb < 4L * 1024L * 1024L) return // Less than 4 GB free physical disk space on MicroSD

            val tuneOut = RootShell.execForOutput("tune2fs -l \"$containerPath\" 2>/dev/null")
            if (tuneOut.isBlank()) return

            var blockSize = 4096L
            var freeBlocks = 0L
            for (line in tuneOut.lines()) {
                val trimmed = line.trim()
                if (trimmed.startsWith("Block size:")) {
                    blockSize = trimmed.substringAfter("Block size:").trim().toLongOrNull() ?: 4096L
                } else if (trimmed.startsWith("Free blocks:")) {
                    freeBlocks = trimmed.substringAfter("Free blocks:").trim().toLongOrNull() ?: 0L
                }
            }

            val freeVirtualMb = (freeBlocks * blockSize) / (1024L * 1024L)
            if (freeVirtualMb < 2048L) {
                AppLogger.info("MountManager", "Auto-expanding sparse container: $containerPath (virtual free: ${freeVirtualMb}MB < 2048MB)")
                val expandMb = 4096L
                RootShell.exec("truncate -s +${expandMb}M \"$containerPath\" 2>/dev/null")
                RootShell.exec("e2fsck -fy \"$containerPath\" >/dev/null 2>&1")
                RootShell.exec("resize2fs \"$containerPath\" >/dev/null 2>&1")
                AppLogger.success("MountManager", "Successfully expanded sparse container: $containerPath (+${expandMb}MB)")
            }
        }

        /**
         * Creates a loop device for a container image.
         * Resolves through sysfs backing_file first, then checks losetup -j/-a,
         * and prefers busybox losetup to bypass Toybox's 64-char path limitation.
         */
        suspend fun attachLoopDevice(containerPath: String): String {
            RootShell.exec("chmod 777 \"$containerPath\" 2>/dev/null")
            // 1. Check sysfs backing_file first (Android kernel truncates lo_file_name in losetup -a to 64 chars)
            val sysfsCheck = RootShell.execForOutput(
                "grep -s -l \"$containerPath\" /sys/block/loop*/loop/backing_file 2>/dev/null | head -n 1"
            ).trim()
            if (sysfsCheck.isNotBlank()) {
                val loopName = sysfsCheck.substringBefore("/loop/").substringAfterLast("/")
                if (loopName.startsWith("loop")) {
                    return "/dev/block/$loopName"
                }
            }
            val bb = resolveBusybox()
            val losetupBin = if (bb != null) "$bb losetup" else "losetup"
            // 2. Fallback check losetup -a with container filename prefix (max 28 chars to bypass 64-char limit)
            val imgName = java.io.File(containerPath).name
            val shortPattern = if (imgName.length > 28) imgName.take(28) else imgName
            val existing = RootShell.execForOutput("$losetupBin -a 2>/dev/null | grep \"$shortPattern\" | head -n 1 | cut -d':' -f1").trim()
            if (existing.isNotBlank() && (existing.startsWith("/dev/block/loop") || existing.startsWith("/dev/loop"))) {
                return existing
            }
            // 3. Find free loop or dynamically allocate one against APEX exhaustion
            val freeLoop = RootShell.execForOutput("$losetupBin -f 2>/dev/null || losetup -f 2>/dev/null").lines().firstOrNull()?.trim().orEmpty()
            var targetLoop = freeLoop
            if (targetLoop.isBlank() || (!targetLoop.startsWith("/dev/block/loop") && !targetLoop.startsWith("/dev/loop"))) {
                // Dynamic Loop Allocator: Query loop-control or create new node if APEX exhausted loop0-loop45
                val allocScript = """
                    dev=""
                    if [ -c /dev/loop-control ]; then
                        num=$(busybox losetup -f 2>/dev/null)
                        [ -n "${'$'}num" ] && dev="${'$'}num"
                    fi
                    if [ -z "${'$'}dev" ]; then
                        for i in $(busybox seq 60 120 2>/dev/null || seq 60 120); do
                            node="/dev/block/loop${'$'}i"
                            if [ ! -b "${'$'}node" ]; then
                                mknod -m 660 "${'$'}node" b 7 "${'$'}i" 2>/dev/null
                                chcon u:object_r:loop_device:s0 "${'$'}node" 2>/dev/null
                                dev="${'$'}node"
                                break
                            fi
                        done
                    fi
                    echo "${'$'}dev"
                """.trimIndent()
                targetLoop = RootShell.execForOutput(allocScript).trim()
            }

            if (targetLoop.isNotBlank() && (targetLoop.startsWith("/dev/block/loop") || targetLoop.startsWith("/dev/loop"))) {
                RootShell.exec("$losetupBin --direct-io=on \"$targetLoop\" \"$containerPath\" 2>/dev/null || $losetupBin \"$targetLoop\" \"$containerPath\" 2>/dev/null || losetup \"$targetLoop\" \"$containerPath\" 2>/dev/null")
                val verifySysfs = RootShell.execForOutput(
                    "grep -s -l \"$containerPath\" /sys/block/loop*/loop/backing_file 2>/dev/null | head -n 1"
                ).trim()
                if (verifySysfs.isNotBlank()) {
                    val loopName = verifySysfs.substringBefore("/loop/").substringAfterLast("/")
                    if (loopName.startsWith("loop")) return "/dev/block/$loopName"
                }
                return targetLoop
            }
            return ""
        }

        /**
         * Detaches all loop devices attached to a container image path.
         */
        suspend fun detachLoopDevice(containerPath: String) {
            val bb = resolveBusybox()
            val losetupBin = if (bb != null) "$bb losetup" else "losetup"
            val imgName = java.io.File(containerPath).name
            val sysfsMatches = RootShell.execForOutput(
                "grep -s -l \"$containerPath\" /sys/block/loop*/loop/backing_file 2>/dev/null"
            ).lines().map { it.trim() }.filter { it.isNotBlank() }
            for (match in sysfsMatches) {
                val loopName = match.substringBefore("/loop/").substringAfterLast("/")
                if (loopName.startsWith("loop")) {
                    RootShell.exec("$losetupBin -d \"/dev/block/$loopName\" 2>/dev/null || losetup -d \"/dev/block/$loopName\" 2>/dev/null")
                    AppLogger.info("MountManager", "Detached loop device via sysfs: /dev/block/$loopName")
                }
            }
            val loopDevs = RootShell.execForOutput(
                "$losetupBin -a 2>/dev/null | grep \"$imgName\" | cut -d':' -f1"
            ).lines().map { it.trim() }.filter { it.isNotBlank() }
            for (ld in loopDevs) {
                RootShell.exec("$losetupBin -d \"$ld\" 2>/dev/null || losetup -d \"$ld\" 2>/dev/null")
                AppLogger.info("MountManager", "Detached loop device: $ld")
            }
        }

        val KNOWN_ABIS = listOf("arm64", "arm", "armeabi-v7a", "armeabi", "x86_64", "x86", "riscv64")

        fun extractAbiFromLibPath(path: String?): String? {
            if (path.isNullOrBlank()) return null
            val norm = path.trimEnd('/')
            for (abi in KNOWN_ABIS) {
                if (norm.endsWith("/lib/$abi") || norm.endsWith("/$abi")) return abi
            }
            val afterLib = norm.substringAfter("/lib/", "")
            return afterLib.substringBefore('/').takeIf { it.isNotBlank() }
        }

        fun defaultDeviceAbi(): String {
            val abi = android.os.Build.SUPPORTED_ABIS.firstOrNull()?.lowercase() ?: "arm64-v8a"
            return when {
                abi.startsWith("arm64") -> "arm64"
                abi.startsWith("armeabi") -> "arm"
                abi.startsWith("x86_64") -> "x86_64"
                abi.startsWith("x86") -> "x86"
                abi.startsWith("riscv64") -> "riscv64"
                else -> "arm64"
            }
        }

        suspend fun detectPreferredAbi(
            apkDir: String? = null,
            sdLibRoot: String? = null,
            hintPath: String? = null
        ): String {
            if (!sdLibRoot.isNullOrBlank()) {
                for (abi in KNOWN_ABIS) {
                    if (RootShell.execForOutput("find \"$sdLibRoot/$abi\" -maxdepth 1 -name '*.so' 2>/dev/null | head -n 1").trim().isNotBlank()) {
                        return abi
                    }
                }
            }
            if (!apkDir.isNullOrBlank()) {
                val foundDir = RootShell.execForOutput("find \"$apkDir/lib\" -mindepth 1 -maxdepth 1 -type d 2>/dev/null | head -n 1").trim()
                if (foundDir.isNotBlank()) {
                    return foundDir.substringAfterLast('/')
                }
            }
            extractAbiFromLibPath(hintPath)?.let { return it }
            return defaultDeviceAbi()
        }

        fun extractRelativePath(path: String): String {
            val clean = path.trim()
            val androidIdx = clean.indexOf("/Android/")
            if (androidIdx >= 0) {
                return clean.substring(androidIdx + 1)
            }
            return clean
                .replace(Regex("^/mnt/user/\\d+/emulated/\\d+/?"), "")
                .replace(Regex("^/mnt/(?:runtime/(?:default|read|write|full)|pass_through/\\d+|installer/\\d+|androidwritable/\\d+)/emulated/\\d+/?"), "")
                .replace(Regex("^/(?:storage/emulated|data/media|mnt/user)(?:/\\d+)?(?:/primary)?/?"), "")
                .removePrefix("/sdcard/")
                .removePrefix("/")
        }

        /**
         * Broadcasts media scanner trigger to update gallery and system media index immediately.
         */
        suspend fun triggerMediaScan(path: String): Unit = withContext(Dispatchers.IO) {
            RootShell.exec("am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d \"file://$path\" 2>/dev/null")
        }

        /**
         * Resolves the current physical block device mounted at a specific path or namespace target.
         * e.g. /data/media/0/Android/data/com.foo -> /dev/block/mmcblk0p3
         */
        suspend fun getActiveMountSourceDevice(targetPath: String): String? = withContext(Dispatchers.IO) {
            val relPath = extractRelativePath(targetPath)
            val targets = listOf(
                targetPath,
                "/storage/emulated/0/$relPath",
                "/data/media/0/$relPath",
                "/mnt/runtime/default/emulated/0/$relPath"
            )
            val grepPattern = targets.joinToString("|") { " $it " }
            val mountRes = RootShell.exec("cat /proc/mounts 2>/dev/null | grep -E '$grepPattern' | head -n 1")
            if (mountRes.isSuccess && mountRes.output.isNotBlank()) {
                val parts = mountRes.output.trim().split(Regex("\\s+"))
                return@withContext parts.getOrNull(0)
            }
            null
        }

        /**
         * Resolves the partition UUID for a given physical block device via blkid.
         */
        suspend fun getBlockDeviceUuid(blockDevice: String): String? = withContext(Dispatchers.IO) {
            val blkidRes = RootShell.exec("blkid \"$blockDevice\" 2>/dev/null")
            if (blkidRes.isSuccess && blkidRes.output.isNotBlank()) {
                val match = Regex("UUID=\"([^\"]+)\"").find(blkidRes.output)
                return@withContext match?.groupValues?.get(1)
            }
            null
        }
    }

    /**
     * Mount an application entry into runtime namespaces.
     * Supports both Multi-Target Array (v2.2.13) and legacy mode fallback.
     */
    suspend fun mountGame(
        game: GameEntry,
        sdBase: String,
        onProgress: ((OperationProgress) -> Unit)? = null,
        autoCreateSource: Boolean = false
    ): Result<Unit> = withContext(Dispatchers.IO) {
        mountOperationMutex.withLock {
        RootShell.awaitServiceMountCycleIfActive(timeoutMillis = 15_000L, waitForWatchdogUnlockGrace = false)
        runCatching {
            val ctx = getAppContext()
            val steps = listOf(
                ctx.getString(R.string.mount_step_sandbox_perms),
                ctx.getString(R.string.mount_step_check_microsd),
                ctx.getString(R.string.mount_step_bind_vfs),
                ctx.getString(R.string.mount_step_verify_selinux),
                ctx.getString(R.string.mount_step_mount_complete)
            )
            val opType = OperationType.MOUNT
            val title = ctx.getString(R.string.op_mount_game_title)
            val subtitle = "${game.displayName} • ${game.packageName}"

            // Step 0: Sandbox & Izin
            onProgress?.invoke(
                OperationProgress(
                    type = opType,
                    title = title,
                    subtitle = subtitle,
                    currentStepIndex = 0,
                    totalSteps = steps.size,
                    stepDescriptions = steps,
                    progressPercent = 0.2f
                )
            )

            // Clear user-unmounted flag since an intentional mount is taking place
            RootShell.exec("rm -f /dev/.mountx_unmounted 2>/dev/null")

            // Pre-Mount Process Guard: unconditionally force-stop app to ensure no file locks or stale namespaces exist
            forceStopApp(game.packageName)

            // Dynamic UID/GID resolution directly from stat /data/user/$appUserId/$pkg
            val appUserId = game.mountPoints.firstOrNull()?.let { resolveUserId(it.targetPath) } ?: 0
            val identity = resolveAppIdentity(game.packageName, appUserId)
            val uid = identity.uid
            val gid = identity.gid

            // Fast non-recursive ownership on internal sandbox directory & databases (Strict 0700 sandbox isolation)
            val sandboxDir = "/data/user/$appUserId/${game.packageName}"
            RootShell.exec("chown $uid:$gid \"$sandboxDir\" 2>/dev/null")
            RootShell.exec("chmod 700 \"$sandboxDir\" 2>/dev/null")
            RootShell.exec("chmod 700 \"$sandboxDir/databases\" 2>/dev/null")

            // Whitelist MountX in system Doze/battery optimization so background transfers & watchdog never get killed
            RootShell.exec("dumpsys deviceidle whitelist +app.mountx 2>/dev/null")

            // Auto-heal storage permissions on Android 11+ so games never crash with EACCES or OBB download error
            RootShell.exec("appops set \"${game.packageName}\" MANAGE_EXTERNAL_STORAGE allow 2>/dev/null")
            RootShell.exec("appops set \"${game.packageName}\" LEGACY_STORAGE allow 2>/dev/null")
            RootShell.exec("pm grant \"${game.packageName}\" android.permission.READ_EXTERNAL_STORAGE 2>/dev/null")
            RootShell.exec("pm grant \"${game.packageName}\" android.permission.WRITE_EXTERNAL_STORAGE 2>/dev/null")

            // APatch stealth isolation: automatically add app to APatch exclusion list so RASP/anti-tamper engines (Promon, etc.) never detect root hooks
            RootShell.exec("""
                if [ -f /data/adb/ap/package_config ]; then
                    if ! grep -q "^${game.packageName}," /data/adb/ap/package_config 2>/dev/null; then
                        echo "${game.packageName},1,0,$uid,0," >> /data/adb/ap/package_config
                    fi
                fi
            """.trimIndent())

            // Step 1: Memeriksa Direktori MicroSD
            onProgress?.invoke(
                OperationProgress(
                    type = opType,
                    title = title,
                    subtitle = subtitle,
                    currentStepIndex = 1,
                    totalSteps = steps.size,
                    stepDescriptions = steps,
                    progressPercent = 0.4f
                )
            )

            // Guard against accidental data occlusion:
            // ── MULTI-DISK COLLISION & ANTI-STACKING GUARD ──
            // Sort by targetPath length ascending so parent mounts (/data/app/pkg, /data/user/0/pkg)
            // are ALWAYS established before child mounts (/data/app/pkg/lib/arm64, /data/user/0/pkg/cache).
            val activeMountPoints = game.mountPoints
                .filter { it.enabled }
                .sortedWith(
                    compareBy<MountPointConfig> { it.targetPath.trimEnd('/').length }
                        .thenByDescending { !it.id.startsWith("legacy_") || it.diskUuid != null }
                        .thenByDescending { it.category == MountPointCategory.CUSTOM }
                        .thenByDescending { it.sizeBytes }
                )
                .distinctBy { it.targetPath.trimEnd('/') }

            if (activeMountPoints.isNotEmpty() && activeMountPoints.none { it.resolveCategory() == MountPointCategory.CUSTOM }) {
                if (!AppPackageInspector.isPackageInstalledHealthyRoot(game.packageName, listOf(sdBase), activeMountPoints)) {
                    throw IllegalStateException(ctx.getString(R.string.app_uninstalled_mount_blocked_toast, game.displayName))
                }
            }

            // Never bind-mount when zero external categories are enabled, when a required source is missing,
            // or when an empty MicroSD folder would occlude populated internal app/APK/lib/private/data dirs.
            if (game.mountPoints.isNotEmpty() && activeMountPoints.isEmpty()) {
                throw OcclusionHazardException(
                    packageName = game.packageName,
                    internalBytes = 0L,
                    sdBytes = 0L,
                    message = getAppContext().getString(R.string.mount_occlusion_hazard_msg)
                )
            }
            for (mp in activeMountPoints) {
                val isVirtual = mp.isEffectivelyVirtualContainer()
                val effectiveSrc = if (isVirtual) {
                    mp.containerImgPath?.ifBlank { null } ?: mp.sourcePath
                } else {
                    mp.sourcePath
                }
                val isLibPoint = mp.category == MountPointCategory.APP_PACKAGE && (mp.targetPath.contains("/lib") || mp.id.startsWith("lib_"))
                val liveTarget = if (mp.category == MountPointCategory.APP_PACKAGE) {
                    val pmOut = RootShell.execForOutput("pm path \"${game.packageName}\" 2>/dev/null | head -n 1").trim()
                    val apkDir = if (pmOut.startsWith("package:")) java.io.File(pmOut.removePrefix("package:")).parent.orEmpty() else ""
                    if (apkDir.isNotBlank()) {
                        if (isLibPoint) {
                            when {
                                mp.targetPath.endsWith("/arm64") -> "$apkDir/lib/arm64"
                                mp.targetPath.endsWith("/arm") -> "$apkDir/lib/arm"
                                else -> "$apkDir/lib"
                            }
                        } else apkDir
                    } else mp.targetPath
                } else mp.targetPath

                val srcExists = RootShell.exists(effectiveSrc)
                val targetExists = RootShell.exists(liveTarget)
                val relCheck = if (mp.isExternalStorageCategory()) extractRelativePath(liveTarget) else ""
                val targetUid = resolveUserId(liveTarget)
                val targetMounted = RootShell.isMountpoint(liveTarget) ||
                    (relCheck.isNotBlank() && RootShell.isMountpoint("/storage/emulated/$targetUid/$relCheck"))
                val targetSizeKb = if (targetExists && !targetMounted) {
                    RootShell.exec("du -sk \"$liveTarget\" 2>/dev/null").output.trim().split(Regex("\\s+")).getOrNull(0)?.toLongOrNull() ?: 0L
                } else 0L

                if (!srcExists) {
                    if (!autoCreateSource || isVirtual ||
                        mp.category == MountPointCategory.APP_PACKAGE ||
                        mp.category == MountPointCategory.PRIVATE_INTERNAL ||
                        mp.category == MountPointCategory.CACHE_SHADERS
                    ) {
                        if (targetSizeKb > 64L) {
                            throw OcclusionHazardException(
                                packageName = game.packageName,
                                internalBytes = targetSizeKb * 1024L,
                                sdBytes = 0L,
                                message = getAppContext().getString(R.string.mount_occlusion_hazard_msg)
                            )
                        } else {
                            throw IllegalStateException("External source missing for ${mp.category.name}: $effectiveSrc")
                        }
                    }
                    continue
                }

                if (isVirtual) {
                    val imgSize = RootShell.execForOutput("stat -c %s \"$effectiveSrc\" 2>/dev/null").trim().toLongOrNull() ?: 0L
                    if (targetSizeKb > 512L && imgSize < 1024L * 1024L) {
                        throw OcclusionHazardException(
                            packageName = game.packageName,
                            internalBytes = targetSizeKb * 1024L,
                            sdBytes = imgSize,
                            message = getAppContext().getString(R.string.mount_occlusion_hazard_msg)
                        )
                    }
                    continue
                }

                if (mp.category == MountPointCategory.APP_PACKAGE) {
                    if (!targetMounted) {
                        if (isLibPoint) {
                            val hasSo = RootShell.execForOutput("find \"$effectiveSrc\" -name '*.so' 2>/dev/null | head -n 1").trim().isNotEmpty()
                            if (targetSizeKb > 64L && !hasSo) {
                                throw OcclusionHazardException(
                                    packageName = game.packageName,
                                    internalBytes = targetSizeKb * 1024L,
                                    sdBytes = 0L,
                                    message = getAppContext().getString(R.string.mount_occlusion_hazard_msg)
                                )
                            }
                        } else if (!RootShell.exists("$effectiveSrc/base.apk")) {
                            throw OcclusionHazardException(
                                packageName = game.packageName,
                                internalBytes = targetSizeKb * 1024L,
                                sdBytes = 0L,
                                message = getAppContext().getString(R.string.mount_occlusion_hazard_msg)
                            )
                        }
                    }
                } else if (mp.category == MountPointCategory.PRIVATE_INTERNAL ||
                    mp.category == MountPointCategory.CACHE_SHADERS ||
                    mp.category == MountPointCategory.MEDIA_DOWNLOADS
                ) {
                    val srcSizeKb = RootShell.exec("du -sk \"$effectiveSrc\" 2>/dev/null").output.trim().split(Regex("\\s+")).getOrNull(0)?.toLongOrNull() ?: 0L
                    if (!targetMounted && targetSizeKb > 128L && (srcSizeKb <= 32L || targetSizeKb > srcSizeKb * 2L)) {
                        throw OcclusionHazardException(
                            packageName = game.packageName,
                            internalBytes = targetSizeKb * 1024L,
                            sdBytes = srcSizeKb * 1024L,
                            message = getAppContext().getString(R.string.mount_occlusion_hazard_msg)
                        )
                    }
                }
            }

            // Do not bind-mount an empty/missing MicroSD directory over a populated internal directory.
            var hasOcclusionRisk = false
            var occlusionInternalBytes = 0L
            var occlusionSdBytes = 0L
            for (mp in activeMountPoints) {
                if (mp.category == MountPointCategory.EXTERNAL_DATA || mp.category == MountPointCategory.OBB_STORAGE || mp.category == MountPointCategory.GAME_ASSETS || mp.category == MountPointCategory.CUSTOM) {
                    val srcExists = RootShell.exists(mp.sourcePath)
                    val targetExists = RootShell.exists(mp.targetPath)
                    val relCheck = extractRelativePath(mp.targetPath)
                    val uidCheck = resolveUserId(mp.targetPath)
                    val targetMounted = RootShell.isMountpoint(mp.targetPath) ||
                        (relCheck.isNotBlank() && RootShell.isMountpoint("/storage/emulated/$uidCheck/$relCheck"))

                    // Fast Path: Check unmigrated internal target size first.
                    // If target doesn't exist, is already mounted, or is merely a skeleton (<= 512KB),
                    // occlusion hazard is impossible, avoiding heavy du scans across tens of thousands of files on MicroSD.
                    val targetSizeKb = if (targetExists && !targetMounted) {
                        val duRes = RootShell.exec("du -sk \"${mp.targetPath}\" 2>/dev/null")
                        duRes.output.trim().split(Regex("\\s+")).getOrNull(0)?.toLongOrNull() ?: 0L
                    } else 0L

                    if (targetSizeKb <= 512L) {
                        if (!srcExists && !autoCreateSource) {
                            hasOcclusionRisk = true
                            break
                        }
                        continue
                    }

                    // Fast bounded probe on SD source (<10ms even on 50GB+ games):
                    // Prevents slow `du -sk` timeouts during boot I/O from falsely reporting 0KB on populated SD dirs.
                    val srcHasSubstantiveFiles = if (srcExists) {
                        val fastProbe = RootShell.exec(
                            "if [ -n \"\$(find \"${mp.sourcePath}\" -maxdepth 4 -type f -size +128k 2>/dev/null | head -n 1)\" ]; then echo 1; " +
                                "elif [ \"\$(find \"${mp.sourcePath}\" -maxdepth 4 -type f -size +0c ! -name '.nomedia' ! -name '.mountx_canary' ! -name '.mountx_perms_ok' ! -name '*.lck' 2>/dev/null | head -n 6 | wc -l)\" -ge 6 ]; then echo 1; " +
                                "else echo 0; fi"
                        )
                        fastProbe.output.trim() == "1"
                    } else false

                    if (srcHasSubstantiveFiles) {
                        // SD source is proven populated with substantive files; skip slow full-tree du scan
                        continue
                    }

                    // Only when SD source did not pass the fast substantive check, measure exact size:
                    val srcDuRes = if (srcExists) {
                        RootShell.exec("du -sk \"${mp.sourcePath}\" 2>/dev/null", timeoutMillis = 30_000L)
                    } else null
                    if (srcDuRes != null && srcDuRes.code == 124) {
                        // `du` timed out scanning a huge directory tree -> definitely not empty
                        AppLogger.warn("MountManager", "Occlusion guard du timed out on ${mp.sourcePath}; assuming populated directory.")
                        continue
                    }
                    val srcSizeKb = srcDuRes?.output?.trim()?.split(Regex("\\s+"))?.getOrNull(0)?.toLongOrNull() ?: 0L

                    val isSourceMissingOrEmpty = !srcExists || srcSizeKb <= 128L
                    val isTargetSubstantiallyLarger = targetSizeKb > 512L && (targetSizeKb > srcSizeKb * 2L)
                    if (isSourceMissingOrEmpty || isTargetSubstantiallyLarger) {
                        hasOcclusionRisk = true
                        occlusionInternalBytes = targetSizeKb * 1024L
                        occlusionSdBytes = srcSizeKb * 1024L
                        AppLogger.warn("MountManager", "Occlusion hazard for ${mp.id}: source has only ${srcSizeKb}KB (exists=$srcExists) while internal target has ${targetSizeKb}KB")
                        break
                    }
                }
            }

            if (hasOcclusionRisk) {
                throw OcclusionHazardException(
                    packageName = game.packageName,
                    internalBytes = occlusionInternalBytes,
                    sdBytes = occlusionSdBytes,
                    message = getAppContext().getString(R.string.mount_occlusion_hazard_msg)
                )
            }

            for (mp in activeMountPoints) {
                val activeDev = getActiveMountSourceDevice(mp.targetPath)
                if (activeDev != null) {
                    val dfOut = RootShell.execForOutput("df \"${mp.sourcePath}\" 2>/dev/null").trim()
                    val requestedDev = dfOut.lines().getOrNull(1)?.trim()?.split(Regex("\\s+"))?.firstOrNull() ?: ""
                    val activeUuid = getBlockDeviceUuid(activeDev)
                    val requestedUuid = if (requestedDev.isNotBlank()) getBlockDeviceUuid(requestedDev) else null

                    if (requestedDev.isNotBlank() && activeDev != requestedDev && activeUuid != null && requestedUuid != null && activeUuid != requestedUuid) {
                        AppLogger.warn("MountManager", "SWITCHING DISK MOUNT: ${game.packageName} target ${mp.targetPath} is mounted from $activeDev ($activeUuid), switching to $requestedDev ($requestedUuid). Unmounting previous device mount cleanly...")
                        val pathsToClean = mutableListOf(mp.targetPath)
                        if (mp.isExternalStorageCategory()) {
                            val relPath = extractRelativePath(mp.targetPath)
                            if (relPath.isNotBlank()) {
                                val targetUserId = resolveUserId(mp.targetPath)
                                val userNamespaces = getTargetNamespaces(targetUserId)
                                for (ns in userNamespaces) {
                                    pathsToClean.add("$ns/$relPath")
                                }
                            }
                        }
                        unmountSafeAcrossNamespaces(pathsToClean)
                    }
                }
            }

            val namespaces = getTargetNamespaces(0)

            // Step 2: Mengaitkan VFS ke Runtime Namespaces
            onProgress?.invoke(
                OperationProgress(
                    type = opType,
                    title = title,
                    subtitle = subtitle,
                    currentStepIndex = 2,
                    totalSteps = steps.size,
                    stepDescriptions = steps,
                    progressPercent = 0.65f
                )
            )

            var totalMountedTargets = 0
            val failedTargets = mutableListOf<String>()
            if (activeMountPoints.isNotEmpty()) {
                // ── UNIVERSAL SMART MULTI-TARGET ARRAY ──
                for (mp in activeMountPoints) {
                    if (!mp.enabled) continue

                    // Security Hard-Lock: reject /data/app unless explicitly marked as APP_PACKAGE
                    if ((mp.targetPath.startsWith("/data/app") || mp.sourcePath.startsWith("/data/app")) && mp.category != MountPointCategory.APP_PACKAGE) {
                        AppLogger.error("MountManager", "Security violation: blocked unverified mount target in /data/app (${mp.id})")
                        continue
                    }

                    val targetUserId = resolveUserId(mp.targetPath)
                    val mpNamespaces = getTargetNamespaces(targetUserId)

                    val mounted = when (mp.category) {
                        MountPointCategory.EXTERNAL_DATA, MountPointCategory.OBB_STORAGE, MountPointCategory.GAME_ASSETS -> {
                            mountDirectoryTarget(mp, uid, gid, mpNamespaces, isMedia = false, autoCreateSource = autoCreateSource)
                        }
                        MountPointCategory.MEDIA_DOWNLOADS -> {
                            RootShell.exec("mkdir -p \"${mp.sourcePath}\" 2>/dev/null")
                            // Localized .nomedia: only place .nomedia in media folder if original target already had .nomedia
                            val hadNomedia = RootShell.exists("${mp.targetPath}/.nomedia")
                            if (hadNomedia) {
                                RootShell.exec("touch \"${mp.sourcePath}/.nomedia\" 2>/dev/null")
                            } else {
                                RootShell.exec("rm -f \"${mp.sourcePath}/.nomedia\" 2>/dev/null")
                            }
                            val res = mountDirectoryTarget(mp, uid, gid, mpNamespaces, isMedia = true, autoCreateSource = autoCreateSource)
                            if (res) triggerMediaScan(mp.targetPath)
                            res
                        }
                        MountPointCategory.CACHE_SHADERS -> {
                            if (mp.targetPath.startsWith("/data/user/") || mp.targetPath.startsWith("/data/data/")) {
                                val pkgUserPrefix = "/data/user/$targetUserId/${game.packageName}/"
                                val pkgDataPrefix = "/data/data/${game.packageName}/"
                                val cacheRelSuffix = mp.targetPath.trimEnd('/')
                                    .removePrefix(pkgUserPrefix)
                                    .removePrefix(pkgDataPrefix)
                                    .trimStart('/')
                                    .ifBlank { "cache" }
                                val primaryCacheTarget = "/data/user/$targetUserId/${game.packageName}/$cacheRelSuffix"
                                val secondaryCacheTargets = listOfNotNull(
                                    if (targetUserId == 0) "/data/data/${game.packageName}/$cacheRelSuffix" else null,
                                    "/data_mirror/data_ce/null/$targetUserId/${game.packageName}/$cacheRelSuffix"
                                )
                                val allCachePeers = (listOf(mp.targetPath.trimEnd('/'), primaryCacheTarget) + secondaryCacheTargets).distinct()

                                val parentContext = RootShell.execForOutput("stat -c %C \"/data/user/$targetUserId/${game.packageName}\" 2>/dev/null").trim()
                                val cacheContext = if (parentContext.isNotBlank() && parentContext.contains("app_data_file")) {
                                    parentContext
                                } else {
                                    "u:object_r:app_data_file:s0"
                                }

                                val candImg = mp.containerImgPath?.ifBlank { null }
                                    ?: mp.resolveContainerImgPath(sdBase, game.packageName)
                                val candNative = mp.resolveNativeFolderPath(sdBase, game.packageName)
                                val isContainer = if (RootShell.exists(candImg) && !RootShell.exists(candNative)) {
                                    true
                                } else if (RootShell.exists(candNative)) {
                                    false
                                } else {
                                    mp.isEffectivelyVirtualContainer() || mp.sourcePath.endsWith(".img", ignoreCase = true)
                                }

                                if (isContainer) {
                                    ensureSparseContainerCapacity(candImg, sdBase, isFat32 = false)
                                    val loopDev = attachLoopDevice(candImg)
                                    if (loopDev.isNotEmpty()) {
                                        RootShell.exec("mkdir -p \"$primaryCacheTarget\" 2>/dev/null")
                                        val alreadyReady = ensurePrivatePeerMountsReady(
                                            peerTargets = allCachePeers,
                                            expectedSource = candImg,
                                            expectedDevice = loopDev,
                                            isContainer = true
                                        )
                                        val mountedOk = if (alreadyReady) {
                                            true
                                        } else {
                                            val res = RootShell.exec("mount -t ext4 -o rw,nosuid,nodev,noatime,discard \"$loopDev\" \"$primaryCacheTarget\" 2>/dev/null || mount -t ext4 \"$loopDev\" \"$primaryCacheTarget\" 2>/dev/null")
                                            res.isSuccess || RootShell.isMountpoint(primaryCacheTarget)
                                        }
                                        if (mountedOk) {
                                            if (!alreadyReady) {
                                                RootShell.exec(RootShell.makeRprivateCmd(primaryCacheTarget))
                                                RootShell.exec("chown -R $uid:$gid \"$primaryCacheTarget\" 2>/dev/null")
                                                RootShell.exec("chmod 700 \"$primaryCacheTarget\" 2>/dev/null && chmod -R u+rwX,go-rwx \"$primaryCacheTarget\" 2>/dev/null")
                                                RootShell.exec("chcon -R \"$cacheContext\" \"$primaryCacheTarget\" 2>/dev/null")
                                            }
                                            bindSecondaryPrivatePeersAtomic(
                                                primaryTarget = primaryCacheTarget,
                                                secondaryTargets = secondaryCacheTargets,
                                                bindSource = primaryCacheTarget,
                                                seContext = cacheContext,
                                                recursiveChcon = !alreadyReady
                                            )
                                            syncContainerTargetsToZygotes(
                                                loopDev = loopDev,
                                                primaryTarget = primaryCacheTarget,
                                                secondaryTargets = secondaryCacheTargets,
                                                preserveExec = false
                                            )
                                            true
                                        } else {
                                            detachLoopDevice(candImg)
                                            false
                                        }
                                    } else false
                                } else {
                                    val finalSrc = if (RootShell.exists(candNative)) candNative else mp.sourcePath
                                    RootShell.exec("mkdir -p \"$finalSrc\" \"$primaryCacheTarget\" 2>/dev/null")
                                    val fsType = RootShell.execForOutput("stat -f -c %T \"$finalSrc\" 2>/dev/null").trim().lowercase()
                                    val isNonPosix = fsType in listOf("exfat", "vfat", "msdos", "ntfs") || finalSrc.startsWith("/mnt/media_rw")
                                    val alreadyReady = ensurePrivatePeerMountsReady(
                                        peerTargets = allCachePeers,
                                        expectedSource = finalSrc,
                                        isNonPosix = isNonPosix
                                    )
                                    val mountedOk = if (alreadyReady) {
                                        true
                                    } else {
                                        if (!isNonPosix) {
                                            RootShell.exec("chown -R $uid:$gid \"$finalSrc\" 2>/dev/null")
                                            RootShell.exec("chmod 700 \"$finalSrc\" 2>/dev/null && chmod -R u+rwX,go-rwx \"$finalSrc\" 2>/dev/null")
                                            RootShell.exec("chcon -R \"$cacheContext\" \"$finalSrc\" 2>/dev/null")
                                        }
                                        val res = RootShell.exec("mount -o bind \"$finalSrc\" \"$primaryCacheTarget\"")
                                        res.isSuccess || RootShell.isMountpoint(primaryCacheTarget)
                                    }
                                    if (mountedOk) {
                                        if (!alreadyReady) {
                                            RootShell.exec(RootShell.makeRprivateCmd(primaryCacheTarget))
                                            RootShell.exec("chown -R $uid:$gid \"$primaryCacheTarget\" 2>/dev/null")
                                            RootShell.exec("chmod 700 \"$primaryCacheTarget\" 2>/dev/null && chmod -R u+rwX,go-rwx \"$primaryCacheTarget\" 2>/dev/null")
                                            RootShell.exec("chcon -R \"$cacheContext\" \"$primaryCacheTarget\" 2>/dev/null")
                                        }
                                        val zPairs = bindSecondaryPrivatePeersAtomic(
                                            primaryTarget = primaryCacheTarget,
                                            secondaryTargets = secondaryCacheTargets,
                                            bindSource = finalSrc,
                                            seContext = cacheContext,
                                            recursiveChcon = !alreadyReady
                                        )
                                        val zygoteCachePairs = zPairs.filter { it.second.startsWith("/data_mirror/") }
                                            .let { if (it.isNotEmpty() && RootShell.exists(it.first().second)) it else zPairs }
                                        syncBindTargetsToZygotes(zygoteCachePairs, isNonPosix = isNonPosix)
                                        true
                                    } else {
                                        false
                                    }
                                }
                            } else {
                                mountDirectoryTarget(mp, uid, gid, mpNamespaces, isMedia = false, autoCreateSource = autoCreateSource)
                            }
                        }
                        MountPointCategory.CUSTOM -> {
                            val res = mountDirectoryTarget(mp, uid, gid, mpNamespaces, isMedia = false, autoCreateSource = autoCreateSource)
                            if (res) triggerMediaScan(mp.targetPath)
                            res
                        }
                        MountPointCategory.PRIVATE_INTERNAL -> {
                            val hasSeparateCache = activeMountPoints.any {
                                it.category == MountPointCategory.CACHE_SHADERS || it.id.startsWith("cache_")
                            }
                            mountPrivateInternalData(game.packageName, mp, sdBase, uid, gid, hasSeparateCache)
                        }
                        MountPointCategory.APP_PACKAGE -> {
                            // Dynamic verification: resolve active APK directory via pm path in case update relocated the hash directory
                            val currentApkPathRes = RootShell.exec("pm path \"${game.packageName}\" 2>/dev/null | head -n 1")
                            val currentApkDir = if (currentApkPathRes.isSuccess && currentApkPathRes.output.contains("package:")) {
                                val fullPath = currentApkPathRes.output.substringAfter("package:").trim()
                                java.io.File(fullPath).parent ?: ""
                            } else ""
                            val isLib = mp.isLibTarget()

                            val candImg = mp.containerImgPath?.ifBlank { null }
                                ?: mp.resolveContainerImgPath(sdBase, game.packageName)
                            val candNative = mp.resolveNativeFolderPath(sdBase, game.packageName)
                            val resolvedAbi = detectPreferredAbi(
                                apkDir = currentApkDir,
                                sdLibRoot = candNative,
                                hintPath = mp.targetPath
                            )
                            val archSubdir = "lib/$resolvedAbi"
                            val isContainer = if (RootShell.exists(candImg) && !RootShell.exists(candNative)) {
                                true
                            } else if (RootShell.exists(candNative)) {
                                false
                            } else {
                                mp.isEffectivelyVirtualContainer() || mp.sourcePath.endsWith(".img", ignoreCase = true)
                            }

                            val (finalSource, finalTarget) = if (isLib) {
                                val hasAbiSource = RootShell.exists("${candNative}/$resolvedAbi") || RootShell.exists("${mp.sourcePath}/$resolvedAbi")
                                val baseSrc = if (RootShell.exists(candNative)) candNative else mp.sourcePath
                                val src = if (hasAbiSource) "$baseSrc/$resolvedAbi" else baseSrc
                                val tgt = if (currentApkDir.isNotBlank()) "$currentApkDir/$archSubdir" else mp.targetPath
                                src to tgt
                            } else {
                                val baseSrc = if (RootShell.exists(candNative)) candNative else mp.sourcePath
                                val tgt = if (currentApkDir.isNotBlank()) currentApkDir else mp.targetPath
                                baseSrc to tgt
                            }

                            val hasSeparateLibMount = activeMountPoints.any {
                                it.isLibTarget()
                            }
                            val libHoldDir = "/dev/mountx_lib_hold_${game.packageName}"
                            var heldInternalLib = false
                            if (!isLib && !hasSeparateLibMount && !RootShell.isMountpoint(finalTarget)) {
                                val internalLibHasFiles = RootShell.execForOutput("find \"$finalTarget/lib\" -type f 2>/dev/null | head -n 1").trim().isNotBlank()
                                val sdLibHasFiles = if (isContainer) false else RootShell.execForOutput("find \"$finalSource/lib\" -type f 2>/dev/null | head -n 1").trim().isNotBlank()
                                if (internalLibHasFiles && !sdLibHasFiles) {
                                    RootShell.exec("mkdir -p \"$libHoldDir\" 2>/dev/null && mount -o bind \"$finalTarget/lib\" \"$libHoldDir\" 2>/dev/null")
                                    heldInternalLib = RootShell.isMountpoint(libHoldDir)
                                }
                            }

                            var appMountAlreadyReady = false
                            val res = if (isContainer) {
                                ensureSparseContainerCapacity(candImg, sdBase, isFat32 = false)
                                val loopDev = attachLoopDevice(candImg)
                                if (loopDev.isNotEmpty()) {
                                    RootShell.exec("mkdir -p \"$finalTarget\" 2>/dev/null")
                                    appMountAlreadyReady = ensureMountSlotReady(
                                        target = finalTarget,
                                        expectedSource = candImg,
                                        expectedDevice = loopDev,
                                        isContainer = true
                                    )
                                    if (appMountAlreadyReady) {
                                        true
                                    } else {
                                        val mRes = RootShell.exec("mount -t ext4 -o rw,noatime,discard \"$loopDev\" \"$finalTarget\" 2>/dev/null || mount -t ext4 \"$loopDev\" \"$finalTarget\" 2>/dev/null")
                                        if (mRes.isSuccess || RootShell.isMountpoint(finalTarget)) {
                                            RootShell.exec("mount -o remount,bind,exec \"$finalTarget\" 2>/dev/null || mount -o remount,exec \"$finalTarget\" 2>/dev/null")
                                            true
                                        } else {
                                            detachLoopDevice(candImg)
                                            false
                                        }
                                    }
                                } else false
                            } else {
                                RootShell.exec("mkdir -p \"$finalSource\" 2>/dev/null")
                                RootShell.exec("mkdir -p \"$finalTarget\" 2>/dev/null")
                                appMountAlreadyReady = ensureMountSlotReady(
                                    target = finalTarget,
                                    expectedSource = finalSource,
                                    isNonPosix = false
                                )
                                if (appMountAlreadyReady) {
                                    true
                                } else {
                                    val mRes = RootShell.exec("mount -o bind \"$finalSource\" \"$finalTarget\"")
                                    mRes.isSuccess || RootShell.isMountpoint(finalTarget)
                                }
                            }

                            if (res || RootShell.isMountpoint(finalTarget)) {
                                if (!appMountAlreadyReady) {
                                    RootShell.exec("mount -o remount,bind,exec \"$finalTarget\" 2>/dev/null || mount -o remount,exec \"$finalTarget\" 2>/dev/null")
                                    RootShell.exec(RootShell.makeRprivateCmd(finalTarget))
                                    RootShell.exec("touch \"$finalSource/.mountx_canary\" 2>/dev/null")
                                    RootShell.exec("chown -R 1000:1000 \"$finalTarget\" 2>/dev/null")
                                    RootShell.exec("chmod -R 755 \"$finalTarget\" 2>/dev/null")
                                    RootShell.exec("[ -f \"$finalTarget/.mountx_canary\" ] && chmod 644 \"$finalTarget/.mountx_canary\" 2>/dev/null")
                                    RootShell.exec("chcon -R u:object_r:apk_data_file:s0 \"$finalTarget\" 2>/dev/null")
                                }
                                val appZygoteBindPairs = mutableListOf<Pair<String, String>>()
                                if (!isContainer) {
                                    appZygoteBindPairs.add(finalSource to finalTarget)
                                }
                                if (heldInternalLib) {
                                    RootShell.exec("mkdir -p \"$finalTarget/lib\" 2>/dev/null && mount -o bind \"$libHoldDir\" \"$finalTarget/lib\" 2>/dev/null && ${RootShell.makeRprivateCmd("$finalTarget/lib")}")
                                    RootShell.exec("umount -l \"$libHoldDir\" 2>/dev/null && rmdir \"$libHoldDir\" 2>/dev/null")
                                } else if (!isLib && !hasSeparateLibMount) {
                                    val sdSeparateLib = "$sdBase/MountX/lib/${game.packageName}"
                                    val libAbi = detectPreferredAbi(apkDir = finalTarget, sdLibRoot = sdSeparateLib)
                                    val resolvedSdLib = if (RootShell.exists("$sdSeparateLib/$libAbi")) "$sdSeparateLib/$libAbi" else sdSeparateLib
                                    if (RootShell.exists(resolvedSdLib) &&
                                        !RootShell.isMountpoint("$finalTarget/lib") &&
                                        !RootShell.isMountpoint("$finalTarget/lib/$libAbi") &&
                                        RootShell.execForOutput("find \"$resolvedSdLib\" -name '*.so' 2>/dev/null | head -n 1").trim().isNotBlank()
                                    ) {
                                        RootShell.exec("touch \"$resolvedSdLib/.mountx_canary\" 2>/dev/null && mkdir -p \"$finalTarget/lib/$libAbi\" 2>/dev/null && mount -o bind \"$resolvedSdLib\" \"$finalTarget/lib/$libAbi\" 2>/dev/null && mount -o remount,bind,exec \"$finalTarget/lib/$libAbi\" 2>/dev/null && ${RootShell.makeRprivateCmd("$finalTarget/lib/$libAbi")} && chown -R 1000:1000 \"$finalTarget/lib/$libAbi\" 2>/dev/null && chmod -R 755 \"$finalTarget/lib/$libAbi\" 2>/dev/null && [ -f \"$finalTarget/lib/$libAbi/.mountx_canary\" ] && chmod 644 \"$finalTarget/lib/$libAbi/.mountx_canary\" 2>/dev/null")
                                        appZygoteBindPairs.add(resolvedSdLib to "$finalTarget/lib/$libAbi")
                                    } else if (RootShell.exists(resolvedSdLib) && RootShell.isMountpoint("$finalTarget/lib/$libAbi")) {
                                        appZygoteBindPairs.add(resolvedSdLib to "$finalTarget/lib/$libAbi")
                                    }
                                }
                                if (isContainer) {
                                    val loopDev = attachLoopDevice(candImg)
                                    if (loopDev.isNotBlank()) {
                                        syncContainerTargetsToZygotes(loopDev, finalTarget, preserveExec = true)
                                    }
                                }
                                if (appZygoteBindPairs.isNotEmpty()) {
                                    syncBindTargetsToZygotes(appZygoteBindPairs, isNonPosix = false, preserveExec = true)
                                }
                                true
                            } else {
                                if (heldInternalLib) {
                                    RootShell.exec("umount -l \"$libHoldDir\" 2>/dev/null && rmdir \"$libHoldDir\" 2>/dev/null")
                                }
                                false
                            }
                        }
                    }
                    if (mounted) {
                        totalMountedTargets++
                    } else {
                        failedTargets.add(mp.category.name)
                    }
                }
                if (totalMountedTargets == 0 || failedTargets.isNotEmpty()) {
                    val msg = if (failedTargets.isNotEmpty()) {
                        "${getAppContext().getString(R.string.op_err_mount_failed)}: ${failedTargets.distinct().joinToString(", ")}"
                    } else {
                        getAppContext().getString(R.string.error_no_sd_directory_found)
                    }
                    throw IllegalStateException(msg)
                }
            } else {
                // ── LEGACY FALLBACK (Mode PKG or FILES) ──
                val dataSrcPath = when (game.mode) {
                    MountMode.FILES -> "$sdBase/Android/data/${game.packageName}/files"
                    MountMode.PKG -> "$sdBase/Android/data/${game.packageName}"
                }
                val dataRelPath = when (game.mode) {
                    MountMode.FILES -> "Android/data/${game.packageName}/files"
                    MountMode.PKG -> "Android/data/${game.packageName}"
                }
                val obbSrcPath = "$sdBase/Android/obb/${game.packageName}"
                val obbRelPath = "Android/obb/${game.packageName}"

                val hasData = RootShell.exists(dataSrcPath)
                val hasObb = RootShell.exists(obbSrcPath)

                if (!hasData && !hasObb) {
                    error(getAppContext().getString(R.string.error_game_data_not_found, game.packageName))
                }

                if (game.mode == MountMode.FILES) {
                    RootShell.exec("chmod 771 \"/data/user/0/${game.packageName}/databases\" 2>/dev/null")
                }

                if (hasData) {
                    bindDirectoryToNamespaces(
                        sourcePath = dataSrcPath,
                        targetPath = "/storage/emulated/$appUserId/$dataRelPath",
                        uid = uid,
                        gid = gid,
                        namespaces = namespaces,
                        autoCreateSource = false,
                        fallbackChmod = "775"
                    )
                }

                if (hasObb) {
                    bindDirectoryToNamespaces(
                        sourcePath = obbSrcPath,
                        targetPath = "/storage/emulated/$appUserId/$obbRelPath",
                        uid = uid,
                        gid = gid,
                        namespaces = namespaces,
                        autoCreateSource = false,
                        fallbackChmod = "775"
                    )
                }
            }

            // Step 3: Memverifikasi Kaitan & SELinux
            onProgress?.invoke(
                OperationProgress(
                    type = opType,
                    title = title,
                    subtitle = subtitle,
                    currentStepIndex = 3,
                    totalSteps = steps.size,
                    stepDescriptions = steps,
                    progressPercent = 0.9f
                )
            )
            // Ensure package is unsuspended if it was previously suspended during migration or MicroSD ejection
            RootShell.exec("pm unsuspend \"${game.packageName}\" 2>/dev/null")
            // Post-Mount Process Guard: force-stop app so Android ActivityManager purges cached process records.
            // When the user opens the app, Zygote spawns a completely fresh process that cleanly inherits the new mounts.
            forceStopApp(game.packageName)
            delay(150)

            // Step 4: Selesai
            onProgress?.invoke(
                OperationProgress(
                    type = opType,
                    title = title,
                    subtitle = subtitle,
                    currentStepIndex = 4,
                    totalSteps = steps.size,
                    stepDescriptions = steps,
                    progressPercent = 1.0f,
                    isFinished = true,
                    isSuccess = true
                )
            )
            Unit
        }.onFailure { err ->
            onProgress?.invoke(
                OperationProgress(
                    type = OperationType.MOUNT,
                    title = getAppContext().getString(R.string.op_title_mount),
                    subtitle = "${game.displayName} • ${game.packageName}",
                    isFinished = true,
                    isSuccess = false,
                    errorMessage = err.message
                )
            )
        }
        }
    }

    /**
     * Bind mount a single directory target into all runtime namespaces.
     */
    private suspend fun mountDirectoryTarget(
        mp: MountPointConfig,
        uid: Int,
        gid: Int,
        namespaces: List<String>,
        isMedia: Boolean,
        autoCreateSource: Boolean = true
    ): Boolean = bindDirectoryToNamespaces(
        sourcePath = mp.sourcePath,
        targetPath = mp.targetPath,
        uid = uid,
        gid = gid,
        namespaces = namespaces,
        autoCreateSource = autoCreateSource
    )

    private suspend fun bindDirectoryToNamespaces(
        sourcePath: String,
        targetPath: String,
        uid: Int,
        gid: Int,
        namespaces: List<String>,
        autoCreateSource: Boolean = true,
        fallbackChmod: String = "775"
    ): Boolean {
        val fsType = RootShell.execForOutput("stat -f -c %T \"$sourcePath\" 2>/dev/null").trim().lowercase()
        val isNonPosix = fsType in listOf("exfat", "vfat", "msdos", "ntfs") || sourcePath.startsWith("/mnt/media_rw")

        if (!RootShell.exists(sourcePath)) {
            if (autoCreateSource) {
                AppLogger.info("MountManager", "Source path $sourcePath does not exist on target storage. Creating directory for direct mount.")
                RootShell.exec("mkdir -p \"$sourcePath\" 2>/dev/null")
                if (!isNonPosix) {
                    RootShell.exec("chown $uid:1023 \"$sourcePath\" 2>/dev/null")
                    RootShell.exec("chmod 777 \"$sourcePath\" 2>/dev/null")
                    RootShell.exec("chcon u:object_r:media_rw_data_file:s0 \"$sourcePath\" 2>/dev/null")
                }
                RootShell.exec("touch \"$sourcePath/.mountx_canary\" 2>/dev/null")
            } else {
                AppLogger.warn("MountManager", "Source path $sourcePath does not exist on SD storage. Skipping mount to prevent hiding internal data.")
                return false
            }
        } else {
            if (!isNonPosix) {
                RootShell.exec("chown $uid:1023 \"$sourcePath\" 2>/dev/null")
                RootShell.exec("chmod 777 \"$sourcePath\" 2>/dev/null")
                RootShell.exec("chcon u:object_r:media_rw_data_file:s0 \"$sourcePath\" 2>/dev/null")
            }
            RootShell.exec("touch \"$sourcePath/.mountx_canary\" 2>/dev/null")
        }

        // Extract relative path from target path across any user profile
        val relPath = extractRelativePath(targetPath)
        val targetUserId = resolveUserId(targetPath)
        if (isUnsafeBindRelativePath(relPath)) {
            AppLogger.error("MountManager", "Refusing unsafe/empty relative bind path '$relPath' for target: $targetPath")
            return false
        }

        // Clear any preexisting /data/media mount point ONLY when kernel sdcardfs is active (prevents EXDEV error).
        val hasSdcardfs = RootShell.execForOutput("grep -w sdcardfs /proc/filesystems 2>/dev/null").isNotBlank()
        if (hasSdcardfs && RootShell.isMountpoint("/data/media/$targetUserId/$relPath")) {
            unmountSafeAcrossNamespaces(listOf("/data/media/$targetUserId/$relPath"))
        }

        // Pre-resolve source block device once
        val reqDev = RootShell.execForOutput("df \"$sourcePath\" 2>/dev/null").lines()
            .getOrNull(1)?.trim()?.split(Regex("\\s+"))?.firstOrNull() ?: ""

        var anyMounted = false
        for (namespace in namespaces) {
            val nsTarget = "$namespace/$relPath"
            if (RootShell.exists(namespace)) {
                // Guard parent directory against dead FUSE ENOTCONN or corrupted MountX parent occlusion
                val guardScript = """
                    _ns="$namespace"
                    _tgt="$nsTarget"
                    _par=${'$'}{_tgt%/*}
                    _st=${'$'}(LC_ALL=C stat -t "${'$'}_ns" 2>&1)
                    case "${'$'}_st" in *"Transport endpoint"*) echo "ENOTCONN"; exit 0 ;; esac
                    if [ -d "${'$'}_par" ]; then
                      _pst=${'$'}(LC_ALL=C stat -t "${'$'}_par" 2>&1)
                      case "${'$'}_pst" in *"Transport endpoint"*) echo "ENOTCONN"; exit 0 ;; esac
                      _mi=/proc/1/mountinfo; [ -r "${'$'}_mi" ] || _mi=/proc/self/mountinfo
                      _bad=${'$'}(P="${'$'}_par" awk -F ' - ' '{split(${'$'}1,L," ");m=L[5];gsub(/\\040/," ",m);if(m==ENVIRON["P"] && L[4]~/(^|\/)MountX(\/|${'$'})/){f=1;exit}} END{print f+0}' "${'$'}_mi" 2>/dev/null)
                      if [ "${'$'}_bad" -eq 1 ]; then echo "OCCLUDED"; exit 0; fi
                    fi
                    echo "OK"
                """.trimIndent()
                val guardStatus = RootShell.execScript(guardScript).output.trim()
                if (guardStatus != "OK") {
                    AppLogger.warn("MountManager", "Parent guard blocked mkdir/bind on $nsTarget ($guardStatus); running FUSE/parent repair.")
                    repairDeadFuseAndCorruptedParents()
                    continue
                }

                val alreadyReady = ensureMountSlotReady(
                    target = nsTarget,
                    expectedSource = sourcePath,
                    expectedDevice = reqDev,
                    isNonPosix = isNonPosix,
                    canarySourceDir = sourcePath
                )
                if (alreadyReady) {
                    anyMounted = true
                    continue
                }
                RootShell.exec("mkdir -p \"$nsTarget\" 2>/dev/null")
                var mountRes = if (isNonPosix) {
                    val sdRes = RootShell.exec("toybox mount -t sdcardfs -o fsuid=1023,fsgid=1023,gid=$uid,mask=0 \"$sourcePath\" \"$nsTarget\" 2>/dev/null")
                    if (sdRes.isSuccess || RootShell.isMountpoint(nsTarget)) {
                        sdRes
                    } else {
                        RootShell.exec("mount -o bind \"$sourcePath\" \"$nsTarget\" 2>/dev/null")
                    }
                } else {
                    RootShell.exec("mount -o bind \"$sourcePath\" \"$nsTarget\" 2>/dev/null")
                }

                // SELinux and Permission Self-Healing Fallback
                if (!mountRes.isSuccess && !RootShell.isMountpoint(nsTarget)) {
                    AppLogger.warn("MountManager", "Bind mount initially failed on $nsTarget. Executing SELinux and permission self-healing...")
                    RootShell.exec("chmod $fallbackChmod \"$nsTarget\" 2>/dev/null")
                    RootShell.exec("chown $uid:$gid \"$nsTarget\" 2>/dev/null")
                    if (!isNonPosix) {
                        RootShell.exec("chcon u:object_r:media_rw_data_file:s0 \"$sourcePath\" 2>/dev/null")
                    }
                    RootShell.exec("chcon u:object_r:media_rw_data_file:s0 \"$nsTarget\" 2>/dev/null")

                    mountRes = RootShell.exec("mount -o bind \"$sourcePath\" \"$nsTarget\" 2>/dev/null")
                    if (mountRes.isSuccess || RootShell.isMountpoint(nsTarget)) {
                        AppLogger.info("MountManager", "Self-healing succeeded: bind mount established on $nsTarget")
                    } else {
                        AppLogger.error("MountManager", "Self-healing bind mount retry failed on $nsTarget: ${mountRes.output}")
                    }
                }

                if (mountRes.isSuccess || RootShell.isMountpoint(nsTarget)) {
                    RootShell.exec(RootShell.makeRprivateCmd(nsTarget))
                    anyMounted = true
                }
            }
        }

        // Inject into active Zygote namespaces via clean heredoc helper (zero '\'' quoting bugs)
        val zygoteRoots = (namespaces + listOf(
            "/storage/emulated/$targetUserId",
            "/mnt/user/$targetUserId/emulated/$targetUserId",
            "/mnt/installer/$targetUserId/emulated/$targetUserId",
            "/mnt/androidwritable/$targetUserId/emulated/$targetUserId",
            "/mnt/runtime/default/emulated/$targetUserId",
            "/mnt/runtime/read/emulated/$targetUserId",
            "/mnt/runtime/write/emulated/$targetUserId",
            "/mnt/runtime/full/emulated/$targetUserId"
        )).distinct()
        syncBindTargetsToZygotes(
            pairs = zygoteRoots.map { sourcePath to "$it/$relPath" },
            isNonPosix = isNonPosix
        )

        // Clean stale verify.fail or partial block files from unmounted internal storage AND source
        RootShell.exec("rm -f \"/data/media/$targetUserId/$relPath/files/verify.fail\" \"$sourcePath/files/verify.fail\" \"/data/media/$targetUserId/$relPath/files/Asb/Android/\"*_tmp.block \"$sourcePath/files/Asb/Android/\"*_tmp.block 2>/dev/null")
        return anyMounted
    }

    /**
     * Checks whether [target] is already cleanly mounted to [expectedSource] (matching live inode on POSIX
     * or active device in /proc/mounts plus optional [canarySourceDir] visibility).
     * Returns true if [target] is already mounted accurately (and ensures rprivate propagation) so callers
     * skip duplicate bind/loop mounts and avoid shared-propagation stacking. Unmounts all stale layers if mismatched.
     */
    private suspend fun ensureMountSlotReady(
        target: String,
        expectedSource: String,
        expectedDevice: String = "",
        isNonPosix: Boolean = false,
        canarySourceDir: String? = null,
        isContainer: Boolean = false
    ): Boolean {
        if (!RootShell.isMountpoint(target)) return false

        val canaryCheck = !canarySourceDir.isNullOrBlank() && RootShell.exists("$canarySourceDir/.mountx_canary")
        val checkScript = buildString {
            append("_mi=/proc/1/mountinfo; [ -r \"\$_mi\" ] || _mi=/proc/self/mountinfo; ")
            append("_st=\$(LC_ALL=C stat -t \"$target\" 2>&1); case \"\$_st\" in *\"Transport endpoint\"*) echo \"ENOTCONN|0||\"; exit 0 ;; esac; ")
            append("if [ -r \"\$_mi\" ]; then ")
            append("curDev=\$(T=\"${target.trimEnd('/')}\" awk -F ' - ' '{split(\$1,L,\" \");split(\$2,R,\" \");m=L[5];gsub(/\\\\040/,\" \",m);if(m==ENVIRON[\"T\"])d=R[2]} END{print d}' \"\$_mi\" 2>/dev/null); ")
            append("else ")
            append("curDev=\$(T=\"${target.trimEnd('/')}\" awk '\$2 == ENVIRON[\"T\"] {print \$1}' /proc/mounts 2>/dev/null | tail -n 1); ")
            append("fi; ")
            append("ids=\$(stat -c '%d:%i' \"$expectedSource\" \"$target\" 2>/dev/null | tr '\\n' '|'); ")
            if (canaryCheck) {
                append("if [ -e \"$target/.mountx_canary\" ]; then cok=1; else cok=0; fi; ")
            } else {
                append("cok=1; ")
            }
            append("echo \"\${curDev}|\${cok}|\${ids}\"")
        }
        val outParts = RootShell.execForOutput(checkScript).trim().split("|")
        val curDev = outParts.getOrNull(0)?.trim() ?: ""
        val canaryOk = (outParts.getOrNull(1)?.trim() ?: "0") == "1"
        val srcId = outParts.getOrNull(2)?.trim() ?: ""
        val tgtId = outParts.getOrNull(3)?.trim() ?: ""

        val matchesSource = if (curDev == "ENOTCONN") {
            false
        } else if (isContainer) {
            expectedDevice.isNotBlank() && curDev == expectedDevice
        } else if (!isNonPosix && srcId.isNotBlank() && tgtId.isNotBlank()) {
            srcId == tgtId
        } else {
            curDev == expectedSource || (curDev.isNotBlank() && expectedDevice.isNotBlank() && curDev == expectedDevice)
        }

        if (matchesSource && canaryOk) {
            RootShell.exec(RootShell.makeRprivateCmd(target))
            return true
        }

        AppLogger.info("MountManager", "Unmounting stale/shadowed mount on $target ($curDev != ${expectedDevice.ifBlank { expectedSource }}, canaryOk=$canaryOk)")
        unmountSafeAcrossNamespaces(listOf(target))
        return false
    }

    /**
     * Mounts PRIVATE_INTERNAL (/data/user/0/<pkg>) using the Adaptive Dual-Engine architecture:
     * - Engine A (Direct Native Folder): Used on POSIX filesystems (F2FS, EXT4) when $sdBase/MountX/data/<pkg> exists
     *   or no legacy .img exists. Zero overhead, 100% native speed, unlimited dynamic capacity.
     * - Engine B (Sparse Dynamic ext4 Loop Container): Used on Non-POSIX filesystems (exFAT, FAT32/vFAT, NTFS)
     *   or when an existing .img container is present. Uses sparse allocation + --direct-io=on.
     * Compatible across Android 10 through Android 17 (including /data_mirror/data_ce/null/0 and 4KB/16KB pages).
     */
    private suspend fun mountPrivateInternalData(
        packageName: String,
        mp: MountPointConfig,
        sdBase: String,
        uid: Int,
        gid: Int,
        hasSeparateCache: Boolean = false
    ): Boolean {
        val containerDir = "$sdBase/.mountx/containers"
        val containerPath = mp.containerImgPath?.ifBlank { null }
            ?: (if (mp.sourcePath.endsWith(".img", ignoreCase = true)) mp.sourcePath else "$containerDir/${packageName}_data.img")
        val nativeFolderPath = if (mp.sourcePath.isNotBlank() && !mp.sourcePath.endsWith(".img", ignoreCase = true)) {
            mp.sourcePath
        } else {
            "$sdBase/MountX/data/$packageName"
        }

        val fsType = RootShell.execForOutput("stat -f -c %T \"$sdBase\" 2>/dev/null").trim().lowercase()
        val isNonPosix = fsType in listOf("exfat", "vfat", "msdos", "ntfs") || sdBase.startsWith("/mnt/media_rw")
        val isFat32 = fsType in listOf("vfat", "msdos", "fat32")

        val hasNativeFolder = RootShell.exists(nativeFolderPath)
        val hasContainerImg = RootShell.exists(containerPath)

        // Choose Engine A (Direct Native Folder) if POSIX and either native folder exists or no legacy container exists
        val useDirectFolderEngine = !isNonPosix && (hasNativeFolder || !hasContainerImg)

        val targetUserId = resolveUserId(mp.targetPath)
        val target = "/data/user/$targetUserId/$packageName"
        val dataDataTarget = if (targetUserId == 0) "/data/data/$packageName" else ""
        val dataCeTarget = "/data_mirror/data_ce/null/$targetUserId/$packageName"
        val secondaryPeers = listOfNotNull(dataDataTarget.ifBlank { null }, dataCeTarget)
        val allPeers = listOf(target) + secondaryPeers
        RootShell.exec("mkdir -p \"$target\" 2>/dev/null")

        val cacheHoldDir = "/dev/mountx_cache_hold_$packageName"
        var heldInternalCache = false
        if (useDirectFolderEngine && !hasSeparateCache && !RootShell.isMountpoint(target)) {
            val internalCacheHasFiles = RootShell.execForOutput("find \"$target/cache\" -type f 2>/dev/null | head -n 1").trim().isNotBlank()
            if (internalCacheHasFiles) {
                RootShell.exec("mkdir -p \"$cacheHoldDir\" 2>/dev/null && mount -o bind \"$target/cache\" \"$cacheHoldDir\" 2>/dev/null && ${RootShell.makeRprivateCmd(cacheHoldDir)}")
                heldInternalCache = RootShell.isMountpoint(cacheHoldDir)
            }
        }

        val origMcsContext = RootShell.execForOutput("stat -c %C \"$target\" 2>/dev/null").trim()
        val effectiveContext = if (origMcsContext.isNotBlank() && origMcsContext.contains("app_data_file")) {
            origMcsContext
        } else {
            "u:object_r:app_data_file:s0"
        }

        var privateAlreadyReady = false
        var activeLoopDev = ""
        val mountSucceeded = if (useDirectFolderEngine) {
            privateAlreadyReady = ensurePrivatePeerMountsReady(
                peerTargets = allPeers,
                expectedSource = nativeFolderPath,
                isNonPosix = isNonPosix
            )
            if (privateAlreadyReady) {
                true
            } else {
                RootShell.exec("mkdir -p \"$nativeFolderPath\" 2>/dev/null")
                RootShell.exec("chown $uid:$gid \"$nativeFolderPath\" 2>/dev/null")
                RootShell.exec("chmod 700 \"$nativeFolderPath\" 2>/dev/null")
                RootShell.exec("chcon \"$effectiveContext\" \"$nativeFolderPath\" 2>/dev/null")

                val mountRes = RootShell.exec("mount -o bind \"$nativeFolderPath\" \"$target\" 2>/dev/null")
                val ok = mountRes.isSuccess || RootShell.isMountpoint(target)
                if (ok) {
                    AppLogger.success("MountManager", "Mounted Engine A (Direct Native Folder) $nativeFolderPath -> $target")
                } else {
                    AppLogger.error("MountManager", "Failed to mount Engine A folder $nativeFolderPath -> $target: ${mountRes.output}")
                }
                ok
            }
        } else {
            RootShell.exec("mkdir -p \"$containerDir\" 2>/dev/null")
            if (!hasContainerImg) {
                val freeSdOut = RootShell.execForOutput("df -k \"$sdBase\" 2>/dev/null | tail -1").trim()
                val freeSdParts = freeSdOut.split(Regex("\\s+"))
                val freeSdMb = if (freeSdParts.size >= 4) (freeSdParts[3].toLongOrNull() ?: 0L) / 1024L else 4096L
                val minRequiredMb = ((mp.sizeBytes / (1024 * 1024)) + 512L).coerceAtLeast(1024L)
                val virtualSizeMb = if (isFat32) {
                    maxOf(minRequiredMb, minOf(freeSdMb - 128L, 3800L)).coerceAtMost(3800L)
                } else {
                    val maxDynamicMb = ((freeSdMb * 95L) / 100L).coerceAtLeast(minRequiredMb)
                    maxOf(maxOf(minRequiredMb, 8192L), maxDynamicMb)
                }
                RootShell.exec("truncate -s ${virtualSizeMb}M \"$containerPath\" 2>/dev/null || dd if=/dev/zero of=\"$containerPath\" bs=1M count=0 seek=$virtualSizeMb 2>/dev/null")
                RootShell.exec("mke2fs -F -t ext4 -b 4096 -m 0 -O ^has_journal -E lazy_itable_init=1 \"$containerPath\" 2>/dev/null || mkfs.ext4 -F -b 4096 -m 0 \"$containerPath\" 2>/dev/null")
                AppLogger.info("MountManager", "Created Sparse Dynamic ext4 container: $containerPath ($virtualSizeMb MB virtual ceiling)")
            } else {
                ensureSparseContainerCapacity(containerPath, sdBase, isFat32)
            }

            val loopDev = attachLoopDevice(containerPath)
            if (loopDev.isBlank()) {
                AppLogger.error("MountManager", "Failed to setup loop device for container: $containerPath")
                return false
            }
            activeLoopDev = loopDev

            privateAlreadyReady = ensurePrivatePeerMountsReady(
                peerTargets = allPeers,
                expectedSource = containerPath,
                expectedDevice = loopDev,
                isContainer = true
            )
            if (privateAlreadyReady) {
                true
            } else {
                val mountRes = RootShell.exec("mount -t ext4 -o rw,nosuid,nodev,noatime,discard \"$loopDev\" \"$target\" 2>/dev/null || mount -t ext4 -o rw,nosuid,nodev,noatime \"$loopDev\" \"$target\" 2>/dev/null")
                val ok = mountRes.isSuccess || RootShell.isMountpoint(target)
                if (ok) {
                    AppLogger.success("MountManager", "Mounted Engine B (Sparse ext4 Container) $loopDev -> $target")
                } else {
                    AppLogger.error("MountManager", "Failed to mount Engine B loop container $loopDev -> $target: ${mountRes.output}")
                }
                ok
            }
        }

        if (mountSucceeded) {
            if (!privateAlreadyReady) {
                RootShell.exec(RootShell.makeRprivateCmd(target))
                RootShell.exec("chown $uid:$gid \"$target\" 2>/dev/null")
                RootShell.exec("chmod 700 \"$target\" 2>/dev/null")
                RootShell.exec("chcon \"$effectiveContext\" \"$target\" 2>/dev/null")
            }
            val bindSrcForPeers = if (useDirectFolderEngine) nativeFolderPath else target
            val peerPairs = bindSecondaryPrivatePeersAtomic(
                primaryTarget = target,
                secondaryTargets = secondaryPeers,
                bindSource = bindSrcForPeers,
                seContext = effectiveContext,
                recursiveChcon = false
            )
            if (useDirectFolderEngine) {
                val zygotePrivPairs = peerPairs.filter { it.second.startsWith("/data_mirror/") }
                    .let { if (it.isNotEmpty() && RootShell.exists(it.first().second)) it else peerPairs }
                syncBindTargetsToZygotes(zygotePrivPairs, isNonPosix = isNonPosix)
            } else if (activeLoopDev.isNotBlank()) {
                syncContainerTargetsToZygotes(activeLoopDev, target, secondaryPeers, preserveExec = false)
            }
            if (heldInternalCache) {
                val cachePeers = secondaryPeers.map { "$it/cache" }
                val primaryCache = "$target/cache"
                RootShell.exec("mkdir -p \"$primaryCache\" 2>/dev/null && mount -o bind \"$cacheHoldDir\" \"$primaryCache\" 2>/dev/null && ${RootShell.makeRprivateCmd(primaryCache)}")
                bindSecondaryPrivatePeersAtomic(
                    primaryTarget = primaryCache,
                    secondaryTargets = cachePeers,
                    bindSource = cacheHoldDir,
                    seContext = effectiveContext,
                    recursiveChcon = false
                )
                RootShell.exec("umount -l \"$cacheHoldDir\" 2>/dev/null && rmdir \"$cacheHoldDir\" 2>/dev/null")
            }
            // Self-healing: ensure direct boot storage and ART profile directories exist to prevent Zygote Signal 11 crash
            RootShell.exec("""
                if [ ! -d "/data/user_de/$targetUserId/$packageName" ]; then
                    mkdir -p "/data/user_de/$targetUserId/$packageName/cache" "/data/user_de/$targetUserId/$packageName/code_cache" 2>/dev/null
                    chown -R $uid:$gid "/data/user_de/$targetUserId/$packageName" 2>/dev/null
                    chmod 700 "/data/user_de/$targetUserId/$packageName" 2>/dev/null
                    restorecon -FR "/data/user_de/$targetUserId/$packageName" 2>/dev/null || chcon -R u:object_r:app_data_file:s0 "/data/user_de/$targetUserId/$packageName" 2>/dev/null
                fi
                if [ ! -d "/data/misc/profiles/cur/$targetUserId/$packageName" ]; then
                    mkdir -p "/data/misc/profiles/cur/$targetUserId/$packageName" 2>/dev/null
                    chown $uid:$gid "/data/misc/profiles/cur/$targetUserId/$packageName" 2>/dev/null
                    chmod 700 "/data/misc/profiles/cur/$targetUserId/$packageName" 2>/dev/null
                    chcon u:object_r:user_profile_data_file:s0 "/data/misc/profiles/cur/$targetUserId/$packageName" 2>/dev/null
                fi
            """.trimIndent())
        } else if (heldInternalCache) {
            RootShell.exec("umount -l \"$cacheHoldDir\" 2>/dev/null && rmdir \"$cacheHoldDir\" 2>/dev/null")
        }
        return mountSucceeded
    }

    /**
     * Unmount a single game from all runtime namespaces and detach any loop containers.
     * Includes Pre-Unmount Process Check: stops active game process before unmounting to prevent corruption.
     */
    suspend fun unmountGame(
        game: GameEntry,
        onProgress: ((OperationProgress) -> Unit)? = null
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            mountOperationMutex.withLock {
            runCatching {
                val ctx = getAppContext()
                val steps = listOf(
                    ctx.getString(R.string.op_unmount_step_stopping),
                    ctx.getString(R.string.op_unmount_step_unmounting),
                    ctx.getString(R.string.op_unmount_step_restoring_context),
                    ctx.getString(R.string.op_unmount_step_finished)
                )
                val opType = OperationType.UNMOUNT
                val title = ctx.getString(R.string.op_title_unmount)
                val subtitle = "${game.displayName} • ${game.packageName}"

                // Step 0: Stop app
                onProgress?.invoke(
                    OperationProgress(
                        type = opType,
                        title = title,
                        subtitle = subtitle,
                        currentStepIndex = 0,
                        totalSteps = steps.size,
                        stepDescriptions = steps,
                        progressPercent = 0.25f
                    )
                )

                // Pre-Unmount Process Check: unconditionally force-stop app to prevent I/O corruption
                forceStopApp(game.packageName)

                // Step 1: Unmount VFS
                onProgress?.invoke(
                    OperationProgress(
                        type = opType,
                        title = title,
                        subtitle = subtitle,
                        currentStepIndex = 1,
                        totalSteps = steps.size,
                        stepDescriptions = steps,
                        progressPercent = 0.6f
                    )
                )

                val allUserIds = getActiveUserIds()

                if (game.mountPoints.isNotEmpty()) {
                    for (mp in game.mountPoints) {
                        val targetUserId = resolveUserId(mp.targetPath)
                        if (mp.category == MountPointCategory.PRIVATE_INTERNAL || mp.isEffectivelyVirtualContainer()) {
                            RootShell.exec("sync")
                            unmountSafeAcrossNamespaces(
                                listOf(
                                    "/data_mirror/data_ce/null/$targetUserId/${game.packageName}",
                                    "/data_mirror/data_ce/null/0/${game.packageName}",
                                    "/data/data/${game.packageName}",
                                    "/data/user/$targetUserId/${game.packageName}"
                                )
                            )
                            val imgPath = mp.containerImgPath?.ifBlank { null }
                                ?: (if (mp.sourcePath.endsWith(".img", ignoreCase = true)) mp.sourcePath else "${game.packageName}_data.img")
                            detachLoopDevice(imgPath)
                        } else if (mp.category == MountPointCategory.APP_PACKAGE || mp.targetPath.startsWith("/data/app")) {
                            val currentApkPathRes = RootShell.exec("pm path \"${game.packageName}\" 2>/dev/null | head -n 1")
                            val currentApkDir = if (currentApkPathRes.isSuccess && currentApkPathRes.output.contains("package:")) {
                                val fullPath = currentApkPathRes.output.substringAfter("package:").trim()
                                java.io.File(fullPath).parent ?: ""
                            } else ""
                            val effectiveTarget = if (mp.targetPath.contains("/lib")) {
                                if (currentApkDir.isNotBlank()) "$currentApkDir/lib" else mp.targetPath
                            } else {
                                if (currentApkDir.isNotBlank()) currentApkDir else mp.targetPath
                            }
                            unmountSafeAcrossNamespaces(
                                buildList {
                                    if (!mp.targetPath.contains("/lib")) {
                                        if (currentApkDir.isNotBlank()) {
                                            add("$currentApkDir/oat")
                                            add("$currentApkDir/lib")
                                            MountManager.KNOWN_ABIS.forEach { add("$currentApkDir/lib/$it") }
                                        }
                                        add("${mp.targetPath.trimEnd('/')}/oat")
                                        MountManager.KNOWN_ABIS.forEach { add("${mp.targetPath.trimEnd('/')}/lib/$it") }
                                    }
                                    add(effectiveTarget)
                                    add(mp.targetPath)
                                    if (currentApkDir.isNotBlank()) add("$currentApkDir/lib")
                                }.distinct()
                            )
                            if (mp.isEffectivelyVirtualContainer() || mp.sourcePath.endsWith(".img", ignoreCase = true)) {
                                val imgPath = mp.containerImgPath?.ifBlank { null } ?: mp.sourcePath
                                detachLoopDevice(imgPath)
                            }
                            RootShell.exec("restorecon -FR \"$effectiveTarget\" 2>/dev/null")
                        } else if (mp.category == MountPointCategory.CACHE_SHADERS && (mp.targetPath.startsWith("/data/user/") || mp.targetPath.startsWith("/data/data/"))) {
                            val altTarget = if (mp.targetPath.startsWith("/data/user/$targetUserId/")) {
                                mp.targetPath.replace("/data/user/$targetUserId/", "/data/data/")
                            } else if (mp.targetPath.startsWith("/data/data/")) {
                                mp.targetPath.replace("/data/data/", "/data/user/$targetUserId/")
                            } else null
                            val ceTarget = "/data_mirror/data_ce/null/$targetUserId/${game.packageName}/cache"
                            unmountSafeAcrossNamespaces(listOfNotNull(mp.targetPath, altTarget, ceTarget))
                            if (mp.isEffectivelyVirtualContainer() || mp.sourcePath.endsWith(".img", ignoreCase = true)) {
                                val imgPath = mp.containerImgPath?.ifBlank { null } ?: mp.sourcePath
                                detachLoopDevice(imgPath)
                            }
                            RootShell.exec("restorecon -FR \"${mp.targetPath}\" 2>/dev/null")
                        } else {
                            val toUnmount = mutableListOf(mp.targetPath)
                            if (mp.isExternalStorageCategory()) {
                                val userNamespaces = getTargetNamespaces(targetUserId)
                                val relPath = extractRelativePath(mp.targetPath)
                                if (relPath.isNotBlank()) {
                                    for (ns in userNamespaces) {
                                        toUnmount.add("$ns/$relPath")
                                    }
                                    toUnmount.add("/data/media/$targetUserId/$relPath")
                                }
                            }
                            unmountSafeAcrossNamespaces(toUnmount)
                        }
                    }
                }

                // Fallback unmount standard default paths across all user profiles
                val legacyRelPaths = listOf(
                    "Android/data/${game.packageName}/files",
                    "Android/data/${game.packageName}",
                    "Android/obb/${game.packageName}"
                )
                val legacyPaths = mutableListOf<String>()
                for (uid in allUserIds) {
                    val userNamespaces = getTargetNamespaces(uid)
                    for (namespace in userNamespaces) {
                        for (rel in legacyRelPaths) {
                            legacyPaths.add("$namespace/$rel")
                        }
                    }
                }
                unmountSafeAcrossNamespaces(legacyPaths)

                // Universal sweeping unmount: purge any remaining or stacked mounts across all namespaces
                sweepUnmountPackage(game.packageName)

                // Step 2: Restore SELinux context across active users
                onProgress?.invoke(
                    OperationProgress(
                        type = opType,
                        title = title,
                        subtitle = subtitle,
                        currentStepIndex = 2,
                        totalSteps = steps.size,
                        stepDescriptions = steps,
                        progressPercent = 0.85f
                    )
                )
                for (uid in allUserIds) {
                    RootShell.exec("restorecon -FR \"/data/media/$uid/Android/data/${game.packageName}\" 2>/dev/null")
                    RootShell.exec("restorecon -FR \"/data/media/$uid/Android/obb/${game.packageName}\" 2>/dev/null")
                }
                // Post-Unmount Process Guard: ensure app processes are completely terminated
                forceStopApp(game.packageName)
                delay(150)

                // Step 3: Finished
                onProgress?.invoke(
                    OperationProgress(
                        type = opType,
                        title = title,
                        subtitle = subtitle,
                        currentStepIndex = 3,
                        totalSteps = steps.size,
                        stepDescriptions = steps,
                        progressPercent = 1.0f,
                        isFinished = true,
                        isSuccess = true
                    )
                )
                Unit
            }.onFailure { err ->
                onProgress?.invoke(
                    OperationProgress(
                        type = OperationType.UNMOUNT,
                        title = getAppContext().getString(R.string.op_title_unmount),
                        subtitle = "${game.displayName} • ${game.packageName}",
                        isFinished = true,
                        isSuccess = false,
                        errorMessage = err.message
                    )
                )
            }
            }
        }

    /**
     * Unmount specific mount points without unmounting other active points of the game.
     */
    suspend fun unmountMountPoints(
        game: GameEntry,
        points: List<MountPointConfig>,
        onProgress: ((OperationProgress) -> Unit)? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        mountOperationMutex.withLock {
        runCatching {
            val ctx = getAppContext()
            val steps = listOf(
                ctx.getString(R.string.mount_step_stop_app),
                ctx.getString(R.string.mount_step_unbind_vfs),
                ctx.getString(R.string.mount_step_restore_selinux),
                ctx.getString(R.string.mount_step_unmount_complete)
            )
            val opType = OperationType.UNMOUNT
            val title = ctx.getString(R.string.op_unmount_categories_title)
            val subtitle = ctx.getString(R.string.op_game_categories_subtitle, game.displayName, points.size)

            onProgress?.invoke(
                OperationProgress(
                    type = opType,
                    title = title,
                    subtitle = subtitle,
                    currentStepIndex = 0,
                    totalSteps = steps.size,
                    stepDescriptions = steps,
                    progressPercent = 0.25f
                )
            )

            // Force-stop app process unconditionally before unmounting
            forceStopApp(game.packageName)

            onProgress?.invoke(
                OperationProgress(
                    type = opType,
                    title = title,
                    subtitle = subtitle,
                    currentStepIndex = 1,
                    totalSteps = steps.size,
                    stepDescriptions = steps,
                    progressPercent = 0.6f
                )
            )

            // Unmount deepest paths first (e.g. cache before private data, lib/arm64 before apk)
            val sortedUnmountPoints = points.sortedByDescending { it.targetPath.trimEnd('/').length }
            for (mp in sortedUnmountPoints) {
                val targetUserId = resolveUserId(mp.targetPath)
                if (mp.category == MountPointCategory.PRIVATE_INTERNAL || mp.isEffectivelyVirtualContainer()) {
                    RootShell.exec("sync")
                    unmountSafeAcrossNamespaces(
                        listOf(
                            "/data_mirror/data_ce/null/$targetUserId/${game.packageName}",
                            "/data_mirror/data_ce/null/0/${game.packageName}",
                            "/data/data/${game.packageName}",
                            "/data/user/$targetUserId/${game.packageName}"
                        )
                    )
                    val imgPath = mp.containerImgPath?.ifBlank { null }
                        ?: (if (mp.sourcePath.endsWith(".img", ignoreCase = true)) mp.sourcePath else "${game.packageName}_data.img")
                    detachLoopDevice(imgPath)
                } else if (mp.category == MountPointCategory.APP_PACKAGE || mp.targetPath.startsWith("/data/app")) {
                    val currentApkPathRes = RootShell.exec("pm path \"${game.packageName}\" 2>/dev/null | head -n 1")
                    val currentApkDir = if (currentApkPathRes.isSuccess && currentApkPathRes.output.contains("package:")) {
                        val fullPath = currentApkPathRes.output.substringAfter("package:").trim()
                        java.io.File(fullPath).parent ?: ""
                    } else ""
                    val isLib = mp.isLibTarget()
                    val effectiveTarget = if (isLib) {
                        val arch = when {
                            mp.targetPath.endsWith("/lib/arm64") || mp.targetPath.endsWith("/arm64") -> "lib/arm64"
                            mp.targetPath.endsWith("/lib/arm") || mp.targetPath.endsWith("/arm") -> "lib/arm"
                            mp.targetPath.endsWith("/lib/armeabi-v7a") -> "lib/armeabi-v7a"
                            mp.targetPath.endsWith("/lib/x86_64") -> "lib/x86_64"
                            else -> "lib/arm64"
                        }
                        if (currentApkDir.isNotBlank()) "$currentApkDir/$arch" else mp.targetPath
                    } else {
                        if (currentApkDir.isNotBlank()) currentApkDir else mp.targetPath
                    }
                    unmountSafeAcrossNamespaces(
                        buildList {
                            if (!isLib) {
                                if (currentApkDir.isNotBlank()) {
                                    add("$currentApkDir/oat")
                                    MountManager.KNOWN_ABIS.forEach { add("$currentApkDir/lib/$it") }
                                    add("$currentApkDir/lib")
                                }
                                add("${mp.targetPath.trimEnd('/')}/oat")
                                MountManager.KNOWN_ABIS.forEach { add("${mp.targetPath.trimEnd('/')}/lib/$it") }
                            }
                            add(effectiveTarget)
                            add(mp.targetPath)
                            if (currentApkDir.isNotBlank()) add("$currentApkDir/lib")
                        }.distinct()
                    )
                    if (mp.isEffectivelyVirtualContainer() || mp.sourcePath.endsWith(".img", ignoreCase = true)) {
                        val imgPath = mp.containerImgPath?.ifBlank { null } ?: mp.sourcePath
                        detachLoopDevice(imgPath)
                    }
                    RootShell.exec("restorecon -FR \"$effectiveTarget\" 2>/dev/null")
                } else if (mp.category == MountPointCategory.CACHE_SHADERS && (mp.targetPath.startsWith("/data/user/") || mp.targetPath.startsWith("/data/data/"))) {
                    val altTarget = if (mp.targetPath.startsWith("/data/user/$targetUserId/")) {
                        mp.targetPath.replace("/data/user/$targetUserId/", "/data/data/")
                    } else if (mp.targetPath.startsWith("/data/data/")) {
                        mp.targetPath.replace("/data/data/", "/data/user/$targetUserId/")
                    } else null
                    val ceTarget = "/data_mirror/data_ce/null/$targetUserId/${game.packageName}/cache"
                    unmountSafeAcrossNamespaces(listOfNotNull(mp.targetPath, altTarget, ceTarget))
                    if (mp.isEffectivelyVirtualContainer() || mp.sourcePath.endsWith(".img", ignoreCase = true)) {
                        val imgPath = mp.containerImgPath?.ifBlank { null } ?: mp.sourcePath
                        detachLoopDevice(imgPath)
                    }
                    RootShell.exec("restorecon -FR \"${mp.targetPath}\" 2>/dev/null")
                } else {
                    val toUnmount = mutableListOf(mp.targetPath)
                    if (mp.isExternalStorageCategory()) {
                        val userNamespaces = getTargetNamespaces(targetUserId)
                        val relPath = extractRelativePath(mp.targetPath)
                        if (relPath.isNotBlank()) {
                            for (ns in userNamespaces) {
                                toUnmount.add("$ns/$relPath")
                            }
                            toUnmount.add("/data/media/$targetUserId/$relPath")
                        }
                    }
                    unmountSafeAcrossNamespaces(toUnmount)
                    RootShell.exec("restorecon -FR \"${mp.targetPath}\" 2>/dev/null")
                    if (mp.category == MountPointCategory.MEDIA_DOWNLOADS || mp.category == MountPointCategory.CUSTOM) {
                        triggerMediaScan(mp.targetPath)
                    }
                }
                sweepUnmountTarget(mp.targetPath)
                if (mp.isExternalStorageCategory()) {
                    sweepUnmountTarget("${mp.targetPath}/files")
                }
            }

            // If all game points are unmounted, sweep all mounts for the package
            val remainingActivePoints = game.mountPoints.filter { pt -> points.none { it.isSameTargetAs(pt) } && pt.enabled }
            if (remainingActivePoints.isEmpty()) {
                sweepUnmountPackage(game.packageName)
            }

            onProgress?.invoke(
                OperationProgress(
                    type = opType,
                    title = title,
                    subtitle = subtitle,
                    currentStepIndex = 3,
                    totalSteps = steps.size,
                    stepDescriptions = steps,
                    progressPercent = 1.0f,
                    isFinished = true,
                    isSuccess = true
                )
            )
            forceStopApp(game.packageName)
            Unit
        }.onFailure { err ->
            onProgress?.invoke(
                OperationProgress(
                    type = OperationType.UNMOUNT,
                    title = getAppContext().getString(R.string.op_title_unmount),
                    subtitle = getAppContext().getString(R.string.op_game_categories_subtitle, game.displayName, points.size),
                    isFinished = true,
                    isSuccess = false,
                    errorMessage = err.message
                )
            )
        }
        }
    }


    /**
     * Unmount all bind-mounted paths and loop devices from the given sdBase.
     * Set [isUserIntentional] to false during emergency disk error / physical ejection so
     * `/dev/.mountx_unmounted` is NOT left behind to block automatic recovery upon reconnection.
     */
    suspend fun unmountAll(sdBase: String, isUserIntentional: Boolean = true): Result<Unit> =
        withContext(Dispatchers.IO) {
            mountOperationMutex.withLock {
            runCatching {
                // Multi-pass deepest-first sweep across registered packages and SD backing device
                // Covers /data_mirror/data_ce/null/*, /data/data/*, /data/user/*, /data/app/*, and all runtime namespaces.
                val script = """
                    _mi=/proc/1/mountinfo
                    [ -r "${'$'}_mi" ] || _mi=/proc/self/mountinfo
                    SD_BLOCK=${'$'}(B="$sdBase" awk -F ' - ' '{split(${'$'}1,L," ");split(${'$'}2,R," ");m=L[5];gsub(/\\040/," ",m);if(m==ENVIRON["B"])d=R[2]} END{print d}' "${'$'}_mi" 2>/dev/null)
                    if [ -z "${'$'}SD_BLOCK" ]; then
                      SD_BLOCK=${'$'}(awk -v b="$sdBase" '${'$'}2 == b {print ${'$'}1; exit}' /proc/mounts 2>/dev/null)
                    fi
                    PACKAGES=""
                    if [ -f /data/adb/modules/MountX/mountpoints.conf ]; then
                      PACKAGES=${'$'}(grep -vE '^(#|[[:space:]]*${'$'})' /data/adb/modules/MountX/mountpoints.conf 2>/dev/null | cut -d'|' -f1 | tr -d '\r' | sort -u)
                    fi
${ZYGOTE_MNT_PIDS_SNIPPET}
                    for i in 1 2 3 4; do
                      targets=""
                      while IFS='|' read -r dev root mnt; do
                        [ -z "${'$'}mnt" ] && continue
                        [ "${'$'}mnt" = "$sdBase" ] && continue
                        case "${'$'}mnt" in
                          /|/data|/data/user|/data/user/[0-9]*|/data/data|/data/app|/data_mirror|/data_mirror/data_ce|/data_mirror/data_ce/null|/data_mirror/data_ce/null/[0-9]*|/storage|/storage/emulated|/storage/emulated/[0-9]*|*/Android|*/Android/data|*/Android/obb|*/Android/media)
                            continue
                            ;;
                        esac
                        is_target=0
                        if [ -n "${'$'}SD_BLOCK" ] && [ "${'$'}dev" = "${'$'}SD_BLOCK" ]; then
                          is_target=1
                        else
                          case "${'$'}root" in
                            *MountX*|*.mountx*) is_target=1 ;;
                          esac
                          if [ "${'$'}is_target" -eq 0 ]; then
                            case "${'$'}mnt" in
                              *.mountx*|*MountX*) is_target=1 ;;
                              *)
                                for pkg in ${'$'}PACKAGES; do
                                  case "${'$'}mnt" in
                                    */${'$'}pkg|*/${'$'}pkg/*|*/${'$'}pkg-*|*/${'$'}pkg-*/*)
                                      is_target=1
                                      break
                                      ;;
                                  esac
                                done
                                ;;
                            esac
                          fi
                        fi
                        if [ ${'$'}is_target -eq 1 ]; then
                          targets="${'$'}targets
${'$'}mnt"
                        fi
                      done <<EOF_MNTS
${'$'}(awk -F ' - ' '{split(${'$'}1,L," ");split(${'$'}2,R," ");m=L[5];gsub(/\\040/," ",m);print R[2] "|" L[4] "|" m}' "${'$'}_mi" 2>/dev/null)
EOF_MNTS
                      sorted=${'$'}(printf '%s\n' "${'$'}targets" | grep -v '^$' | sort -u | awk '{print length(${'$'}0), ${'$'}0}' | sort -rn | cut -d' ' -f2-)
                      [ -z "${'$'}sorted" ] && break
                      sync
                      printf '%s\n' "${'$'}sorted" | while IFS= read -r _t; do
                        [ -n "${'$'}_t" ] || continue
                        umount "${'$'}_t" 2>/dev/null || umount -l "${'$'}_t" 2>/dev/null
                        for _zp in ${'$'}_zpids; do
                          [ -d "/proc/${'$'}_zp/ns" ] || continue
                          nsenter --mount="/proc/${'$'}_zp/ns/mnt" -- sh -c 'umount "${'$'}1" 2>/dev/null || umount -l "${'$'}1" 2>/dev/null' _ "${'$'}_t" 2>/dev/null
                        done
                      done
                    done
                    for loop_dev in ${'$'}(losetup -a 2>/dev/null | grep "\.mountx/containers" | cut -d':' -f1); do
                      if [ -n "${'$'}loop_dev" ]; then
                        losetup -d "${'$'}loop_dev" 2>/dev/null
                      fi
                    done
                """.trimIndent()
                RootShell.execScript(script)
                if (isUserIntentional) {
                    RootShell.exec("touch /dev/.mountx_unmounted")
                } else {
                    RootShell.exec("rm -f /dev/.mountx_unmounted 2>/dev/null")
                }
                Unit
            }
            }
        }

    /**
     * Get all currently mounted paths from /proc/mounts.
     */
    suspend fun getMountedPaths(): List<String> = withContext(Dispatchers.IO) {
        try {
            val procMounts = java.io.File("/proc/mounts")
            if (procMounts.exists() && procMounts.canRead()) {
                return@withContext procMounts.readLines().mapNotNull { line -> line.split(" ").getOrNull(1) }
            }
        } catch (_: Exception) {}
        RootShell.exec("grep -E '(/data/media/0/Android|/data/sdext|/mnt/media_rw)' /proc/mounts 2>/dev/null")
            .stdout
            .mapNotNull { line -> line.split(" ").getOrNull(1) }
    }

    /**
     * Get all currently mounted target paths parsed into an in-memory HashSet.
     * Prefers /proc/1/mountinfo so root sees PID 1's global mount table cleanly even across
     * isolated su mount namespaces, with fallback to /proc/mounts.
     */
    suspend fun getActiveMountpointsSet(): Set<String> = withContext(Dispatchers.IO) {
        val mounts = HashSet<String>()
        val infoRes = RootShell.exec("cat /proc/1/mountinfo 2>/dev/null")
        if (infoRes.isSuccess && infoRes.stdout.isNotEmpty()) {
            for (line in infoRes.stdout) {
                val parts = line.split(Regex("\\s+"))
                if (parts.size >= 5) {
                    val target = parts[4].replace("\\040", " ").trimEnd('/')
                    if (target.isNotBlank()) {
                        mounts.add(target)
                    }
                }
            }
            if (mounts.isNotEmpty()) return@withContext mounts
        }
        val res = RootShell.exec("cat /proc/mounts 2>/dev/null")
        if (res.isSuccess) {
            for (line in res.stdout) {
                val parts = line.split(Regex("\\s+"))
                if (parts.size >= 2) {
                    val target = parts[1].trimEnd('/')
                    if (target.isNotBlank()) {
                        mounts.add(target)
                    }
                }
            }
        }
        mounts
    }

    /**
     * Retrieves all currently mounted target paths inside the active Zygote64/Zygote mount namespace.
     * Used to detect when PID 1 still holds bind mounts after a Zygote soft restart while newly
     * spawned apps forked from Zygote would otherwise see an empty unmounted namespace.
     */
    suspend fun getZygoteMountpointsSet(): Set<String> = withContext(Dispatchers.IO) {
        val mounts = HashSet<String>()
        val res = RootShell.exec(
            "_zp=\$(pidof zygote64 2>/dev/null | awk '{print \$1}'); " +
                "[ -z \"\$_zp\" ] && _zp=\$(pidof zygote 2>/dev/null | awk '{print \$1}'); " +
                "if [ -n \"\$_zp\" ] && [ -r \"/proc/\$_zp/mountinfo\" ]; then cat \"/proc/\$_zp/mountinfo\" 2>/dev/null; fi"
        )
        if (res.isSuccess && res.stdout.isNotEmpty()) {
            for (line in res.stdout) {
                val parts = line.split(Regex("\\s+"))
                if (parts.size >= 5) {
                    val target = parts[4].replace("\\040", " ").trimEnd('/')
                    if (target.isNotBlank()) {
                        mounts.add(target)
                    }
                }
            }
        }
        mounts
    }

    /**
     * Batch queries `dev:ino` identifiers for a collection of paths in a single root shell call.
     * Comparing `dev:ino` between a bind-mount source and its target mathematically proves
     * whether the mount is live and unshadowed on POSIX filesystems without relying on canary files.
     */
    suspend fun batchStatInodes(paths: Collection<String>): Map<String, String> = withContext(Dispatchers.IO) {
        val cleanPaths = paths.map { it.trim().trim('\"').trimEnd('/') }.filter { it.isNotBlank() }.distinct()
        if (cleanPaths.isEmpty()) return@withContext emptyMap()
        val result = HashMap<String, String>(cleanPaths.size)
        cleanPaths.chunked(40).forEach { chunk ->
            val args = chunk.joinToString(" ") { "\"${it.replace("\"", "\\\"")}\"" }
            val out = RootShell.exec("stat -c '%n|%d:%i' $args 2>/dev/null")
            for (line in out.stdout) {
                val idx = line.lastIndexOf('|')
                if (idx > 0 && idx < line.length - 1) {
                    val path = line.substring(0, idx).trim().trimEnd('/')
                    val ino = line.substring(idx + 1).trim()
                    if (path.isNotBlank() && ino.isNotBlank()) {
                        result[path] = ino
                    }
                }
            }
        }
        result
    }

    /**
     * Check if a specific path is currently mounted.
     */
    suspend fun isMounted(path: String): Boolean {
        val clean = path.trimEnd('/')
        return getActiveMountpointsSet().contains(clean)
    }

    /**
     * Thoroughly terminates all active, cached, and background processes, tasks, and services
     * for the specified package to prevent stale mount namespaces, open file descriptor leaks,
     * and force-close crashes upon launching after mount or migration.
     */
    suspend fun forceStopApp(packageName: String) = withContext(Dispatchers.IO) {
        if (packageName.isBlank()) return@withContext
        AppLogger.info("MountManager", "Force-stopping all processes for package: $packageName")
        RootShell.exec("am force-stop \"$packageName\" 2>/dev/null")
        RootShell.exec("pkill -9 -f \"$packageName\" 2>/dev/null || true")
        RootShell.exec("killall -9 \"$packageName\" 2>/dev/null || true")
        delay(150)
    }

    /**
     * Checks whether an application process is actively running in the background or foreground.
     */
    suspend fun isGameRunning(packageName: String): Boolean = withContext(Dispatchers.IO) {
        if (packageName.isBlank()) return@withContext false
        val pgrepRes = RootShell.exec("pgrep -f \"$packageName\" 2>/dev/null || pidof \"$packageName\" 2>/dev/null")
        pgrepRes.isSuccess && pgrepRes.output.isNotBlank()
    }

    /**
     * Get the Linux UID of an installed app via PackageManager.
     * Falls back to 10000 if not found.
     */
    suspend fun getGameUid(packageName: String): Int = withContext(Dispatchers.IO) {
        val result = RootShell.exec(
            "pm list packages -U 2>/dev/null | grep -F \"package:$packageName\" | sed -n 's/.*uid:\\([0-9]*\\).*/\\1/p' | head -n 1"
        )
        result.output.trim().toIntOrNull() ?: 10000
    }

    /**
     * Resolves dynamic UID and GID directly from stat /data/user/$userId/$packageName
     * to ensure full compatibility with Android 11-14+ app and multi-user isolation.
     */
    suspend fun resolveAppIdentity(packageName: String, userId: Int = 0): AppIdentity = withContext(Dispatchers.IO) {
        val statRes = RootShell.exec(
            "stat -c \"%u %g\" \"/data/user/$userId/$packageName\" 2>/dev/null || " +
            "stat -c \"%u %g\" \"/data/data/$packageName\" 2>/dev/null"
        )
        if (statRes.isSuccess && statRes.output.isNotBlank()) {
            val tokens = statRes.output.trim().split("\\s+".toRegex())
            val uid = tokens.getOrNull(0)?.toIntOrNull()
            val gid = tokens.getOrNull(1)?.toIntOrNull()
            if (uid != null && gid != null && uid > 0) {
                return@withContext AppIdentity(uid, gid)
            }
        }
        val fallbackUid = getGameUid(packageName)
        val calculatedUid = if (userId > 0 && fallbackUid < 100000) (userId * 100000) + fallbackUid else fallbackUid
        AppIdentity(calculatedUid, calculatedUid)
    }

    /**
     * Dynamic I/O priority boost for game execution.
     * Sets Real-Time/Best-Effort ionice and elevates scheduling niceness.
     */
    suspend fun boostGameIo(packageName: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val pgrep = RootShell.exec("pgrep -f \"$packageName\" 2>/dev/null")
            if (pgrep.isSuccess && pgrep.output.isNotBlank()) {
                val pids = pgrep.stdout.map { it.trim() }.filter { it.isNotBlank() }
                for (pid in pids) {
                    RootShell.exec("ionice -c 1 -n 0 -p $pid 2>/dev/null || ionice -c 2 -n 0 -p $pid 2>/dev/null")
                    RootShell.exec("renice -n -10 -p $pid 2>/dev/null")
                }
            }
        }
    }

    /**
     * Authoritative batch mount & anti-shadowing verification across PID 1, user-facing emulated views,
     * and the active Zygote mount namespace.
     * Combines `/proc/1/mountinfo`, `/proc/<zygote>/mountinfo`, POSIX `dev:ino` matching, and `.mountx_canary`
     * fallback so MountX never falsely reports an app as Mounted when unmounted, shadowed, or dropped in Zygote.
     */
    suspend fun verifyCanariesBatch(games: List<GameEntry>): Map<String, Boolean> = withContext(Dispatchers.IO) {
        if (games.isEmpty()) return@withContext emptyMap()
        val activeMounts = getActiveMountpointsSet()
        val zygoteMounts = getZygoteMountpointsSet()
        val hasStorageEmulatedRoot = activeMounts.contains("/storage/emulated")

        val canaryPathsToProbe = mutableSetOf<String>()
        val inodePathsToProbe = mutableSetOf<String>()

        for (game in games) {
            val pkg = game.packageName
            if (game.mountPoints.isEmpty()) {
                val dataView = "/storage/emulated/0/Android/data/$pkg"
                val obbView = "/storage/emulated/0/Android/obb/$pkg"
                canaryPathsToProbe.add("$dataView/.mountx_canary")
                canaryPathsToProbe.add("$obbView/.mountx_canary")
                inodePathsToProbe.add(dataView)
                inodePathsToProbe.add(obbView)
                continue
            }
            val escapedPkg = Regex.escape(pkg)
            for (mp in game.mountPoints.filter { it.enabled }) {
                val cleanSrc = mp.sourcePath.trimEnd('/')
                val cleanTgt = mp.targetPath.trimEnd('/')
                val cat = mp.resolveCategory()
                val isExternalCat = mp.isExternalStorageCategory() ||
                    (cat == MountPointCategory.CACHE_SHADERS && (cleanTgt.contains("/Android/") || cleanTgt.contains("/data/media/") || cleanTgt.contains("/storage/")))

                if (isExternalCat) {
                    val rel = extractRelativePath(cleanTgt).trimEnd('/')
                    val userId = resolveUserId(cleanTgt)
                    val checkTarget = if (rel.isNotBlank()) "/storage/emulated/$userId/$rel" else cleanTgt
                    if (cleanSrc.isNotBlank()) {
                        inodePathsToProbe.add(cleanSrc)
                        canaryPathsToProbe.add("$cleanSrc/.mountx_canary")
                    }
                    inodePathsToProbe.add(checkTarget)
                    inodePathsToProbe.add(cleanTgt)
                    canaryPathsToProbe.add("$checkTarget/.mountx_canary")
                    canaryPathsToProbe.add("$cleanTgt/.mountx_canary")
                } else if (cat == MountPointCategory.APP_PACKAGE) {
                    val wantLib = mp.isLibTarget()
                    val pkgPattern = Regex("(?:^|/)$escapedPkg(?:-|/|\\z)")
                    val matchedTarget = activeMounts.firstOrNull { m ->
                        m.startsWith("/data/app/") &&
                            !m.contains("==deleted==") &&
                            !m.contains("/vmdl") &&
                            pkgPattern.containsMatchIn(m) &&
                            (m.endsWith("/lib") || m.contains("/lib/")) == wantLib
                    } ?: cleanTgt
                    val abi = if (wantLib) {
                        extractAbiFromLibPath(matchedTarget) ?: extractAbiFromLibPath(cleanTgt)
                    } else null
                    val effectiveSrc = if (wantLib && !abi.isNullOrBlank() && !cleanSrc.endsWith("/$abi")) {
                        "$cleanSrc/$abi"
                    } else {
                        cleanSrc
                    }
                    if (effectiveSrc.isNotBlank() && !effectiveSrc.endsWith(".img", ignoreCase = true)) {
                        inodePathsToProbe.add(effectiveSrc)
                        inodePathsToProbe.add(cleanSrc)
                        if (!wantLib) {
                            canaryPathsToProbe.add("$effectiveSrc/base.apk")
                        }
                    }
                    if (matchedTarget.isNotBlank()) {
                        inodePathsToProbe.add(matchedTarget)
                    }
                } else if (cat == MountPointCategory.PRIVATE_INTERNAL || cat == MountPointCategory.CACHE_SHADERS) {
                    val userId = resolveUserId(cleanTgt)
                    if (cleanSrc.isNotBlank() && !cleanSrc.endsWith(".img", ignoreCase = true) && !mp.isEffectivelyVirtualContainer()) {
                        inodePathsToProbe.add(cleanSrc)
                    }
                    inodePathsToProbe.add(cleanTgt)
                    if (cat == MountPointCategory.PRIVATE_INTERNAL) {
                        inodePathsToProbe.add("/data/user/$userId/$pkg")
                        inodePathsToProbe.add("/data/data/$pkg")
                    } else {
                        inodePathsToProbe.add("/data/user/$userId/$pkg/cache")
                        inodePathsToProbe.add("/data/data/$pkg/cache")
                    }
                }
            }
        }

        val inodeMap = batchStatInodes(inodePathsToProbe)
        val existingCanaries = RootShell.pathsExistBatch(canaryPathsToProbe)

        games.associate { game ->
            val pkg = game.packageName
            val escapedPkg = Regex.escape(pkg)
            val verified = if (game.mountPoints.isNotEmpty()) {
                val activeConfigs = game.mountPoints.filter { it.enabled }
                activeConfigs.isNotEmpty() && activeConfigs.all { mp ->
                    val cleanSrc = mp.sourcePath.trimEnd('/')
                    val cleanTgt = mp.targetPath.trimEnd('/')
                    val cat = mp.resolveCategory()
                    val isExternalCat = mp.isExternalStorageCategory() ||
                        (cat == MountPointCategory.CACHE_SHADERS && (cleanTgt.contains("/Android/") || cleanTgt.contains("/data/media/") || cleanTgt.contains("/storage/")))

                    if (isExternalCat) {
                        val rel = extractRelativePath(cleanTgt).trimEnd('/')
                        val userId = resolveUserId(cleanTgt)
                        val checkTarget = if (rel.isNotBlank()) "/storage/emulated/$userId/$rel" else cleanTgt
                        val mntUserRoot = "/mnt/user/$userId/emulated"
                        val mntUserTarget = "/mnt/user/$userId/emulated/$userId/$rel"
                        val rtDefaultTarget = "/mnt/runtime/default/emulated/$userId/$rel"
                        val rtWriteTarget = "/mnt/runtime/write/emulated/$userId/$rel"

                        // 1. User-facing /storage/emulated (or runtime default fallback) must be mounted in PID 1
                        val primaryMounted = if (hasStorageEmulatedRoot && rel.isNotBlank()) {
                            activeMounts.contains(checkTarget)
                        } else {
                            activeMounts.contains(checkTarget) ||
                                activeMounts.contains(rtDefaultTarget) ||
                                activeMounts.contains(rtWriteTarget) ||
                                activeMounts.contains(cleanTgt)
                        }
                        if (!primaryMounted) return@all false

                        // 2. When Android 11+ FUSE /mnt/user/<uid>/emulated is active, target must be mounted there too
                        if (rel.isNotBlank() && activeMounts.contains(mntUserRoot)) {
                            if (!activeMounts.contains(mntUserTarget)) return@all false
                        }

                        // 3. Anti-shadowing live visibility check: POSIX inode equality or canary presence on primary user view
                        val probeTarget = if (hasStorageEmulatedRoot && rel.isNotBlank()) checkTarget else cleanTgt
                        val srcIno = inodeMap[cleanSrc]
                        val dstIno = inodeMap[probeTarget]
                        val inodeMatched = srcIno != null && dstIno != null && srcIno == dstIno
                        val canaryMatched = existingCanaries.contains("$cleanSrc/.mountx_canary") &&
                            existingCanaries.contains("$probeTarget/.mountx_canary")
                        if (!inodeMatched && !canaryMatched) return@all false

                        // 4. Zygote mount namespace parity check (when Zygote mountinfo is readable)
                        if (rel.isNotBlank() && zygoteMounts.isNotEmpty()) {
                            val zOk = when {
                                zygoteMounts.contains(mntUserRoot) -> zygoteMounts.contains(mntUserTarget)
                                zygoteMounts.contains("/mnt/runtime/write/emulated") -> zygoteMounts.contains(rtWriteTarget)
                                zygoteMounts.contains("/mnt/runtime/default/emulated") -> zygoteMounts.contains(rtDefaultTarget)
                                else -> true
                            }
                            if (!zOk) return@all false
                        }
                        true
                    } else if (cat == MountPointCategory.APP_PACKAGE) {
                        val wantLib = mp.isLibTarget()
                        val pkgPattern = Regex("(?:^|/)$escapedPkg(?:-|/|\\z)")
                        val matchedTarget = activeMounts.firstOrNull { m ->
                            m.startsWith("/data/app/") &&
                                !m.contains("==deleted==") &&
                                !m.contains("/vmdl") &&
                                pkgPattern.containsMatchIn(m) &&
                                (m.endsWith("/lib") || m.contains("/lib/")) == wantLib
                        } ?: return@all false
                        if (zygoteMounts.isNotEmpty() && !zygoteMounts.contains(matchedTarget)) return@all false
                        if (mp.isEffectivelyVirtualContainer() || cleanSrc.endsWith(".img", ignoreCase = true)) {
                            true
                        } else {
                            val abi = if (wantLib) {
                                extractAbiFromLibPath(matchedTarget) ?: extractAbiFromLibPath(cleanTgt)
                            } else null
                            val effectiveSrc = if (wantLib && !abi.isNullOrBlank() && !cleanSrc.endsWith("/$abi")) {
                                "$cleanSrc/$abi"
                            } else {
                                cleanSrc
                            }
                            if (!wantLib && !existingCanaries.contains("$effectiveSrc/base.apk")) {
                                return@all false
                            }
                            val srcIno = inodeMap[effectiveSrc] ?: inodeMap[cleanSrc]
                            val dstIno = inodeMap[matchedTarget]
                            srcIno != null && dstIno != null && srcIno == dstIno
                        }
                    } else if (cat == MountPointCategory.PRIVATE_INTERNAL) {
                        val userId = resolveUserId(cleanTgt)
                        val mirrorTarget = "/data_mirror/data_ce/null/$userId/$pkg"
                        val matchedTarget = listOf(
                            cleanTgt,
                            "/data/user/$userId/$pkg",
                            "/data/data/$pkg"
                        ).firstOrNull { activeMounts.contains(it) } ?: return@all false
                        if (zygoteMounts.isNotEmpty() && !zygoteMounts.contains(matchedTarget) && !zygoteMounts.contains(mirrorTarget)) return@all false
                        if (mp.isEffectivelyVirtualContainer() || cleanSrc.endsWith(".img", ignoreCase = true)) {
                            true
                        } else {
                            val srcIno = inodeMap[cleanSrc]
                            val dstIno = inodeMap[matchedTarget]
                            srcIno != null && dstIno != null && srcIno == dstIno
                        }
                    } else {
                        val userId = resolveUserId(cleanTgt)
                        val mirrorCacheTarget = "/data_mirror/data_ce/null/$userId/$pkg/cache"
                        val matchedTarget = listOf(
                            cleanTgt,
                            "/data/user/$userId/$pkg/cache",
                            "/data/data/$pkg/cache"
                        ).firstOrNull { activeMounts.contains(it) } ?: return@all false
                        if (zygoteMounts.isNotEmpty() && !zygoteMounts.contains(matchedTarget) && !zygoteMounts.contains(mirrorCacheTarget)) return@all false
                        if (mp.isEffectivelyVirtualContainer() || cleanSrc.endsWith(".img", ignoreCase = true)) {
                            true
                        } else {
                            val srcIno = inodeMap[cleanSrc]
                            val dstIno = inodeMap[matchedTarget]
                            srcIno != null && dstIno != null && srcIno == dstIno
                        }
                    }
                }
            } else {
                val dataView = "/storage/emulated/0/Android/data/$pkg"
                val dataCanary = "$dataView/.mountx_canary"
                val obbCanary = "/storage/emulated/0/Android/obb/$pkg/.mountx_canary"
                val isDataMounted = if (hasStorageEmulatedRoot) {
                    activeMounts.contains(dataView)
                } else {
                    activeMounts.contains(dataView) || activeMounts.contains("/mnt/runtime/default/emulated/0/Android/data/$pkg")
                }
                isDataMounted && (existingCanaries.contains(dataCanary) || existingCanaries.contains(obbCanary))
            }
            pkg to verified
        }
    }

    /**
     * Canary verification check: verifies if the canary file is visible in target namespace.
     * Checks data, obb, and any configured custom mount points.
     */
    suspend fun verifyCanary(packageName: String, appEntry: GameEntry? = null): Boolean = withContext(Dispatchers.IO) {
        val checkPaths = mutableListOf(
            "/storage/emulated/0/Android/data/$packageName/.mountx_canary",
            "/storage/emulated/0/Android/obb/$packageName/.mountx_canary"
        )
        val hasSdcardfs = RootShell.execForOutput("grep -w sdcardfs /proc/filesystems 2>/dev/null").isNotBlank()
        if (!hasSdcardfs) {
            checkPaths.add("/data/media/0/Android/data/$packageName/.mountx_canary")
            checkPaths.add("/data/media/0/Android/obb/$packageName/.mountx_canary")
        }
        if (appEntry != null && appEntry.mountPoints.isNotEmpty()) {
            for (mp in appEntry.mountPoints) {
                if (mp.targetPath.isNotBlank()) {
                    if (mp.isExternalStorageCategory()) {
                        val rel = extractRelativePath(mp.targetPath)
                        if (rel.isNotBlank()) {
                            checkPaths.add("/storage/emulated/0/$rel/.mountx_canary")
                            if (!hasSdcardfs) {
                                checkPaths.add("/data/media/0/$rel/.mountx_canary")
                            }
                        }
                    } else {
                        checkPaths.add("${mp.targetPath}/.mountx_canary")
                    }
                }
            }
        }
        if (appEntry != null) {
            return@withContext verifyCanariesBatch(listOf(appEntry))[packageName] ?: false
        }
        val activeMounts = getActiveMountpointsSet()
        val existing = RootShell.pathsExistBatch(checkPaths)
        val isDataMounted = activeMounts.contains("/storage/emulated/0/Android/data/$packageName") ||
            activeMounts.contains("/mnt/runtime/default/emulated/0/Android/data/$packageName")
        isDataMounted && existing.isNotEmpty()
    }
}

/** Dynamic identity representing UID and GID for modern Android sandboxes */
data class AppIdentity(val uid: Int, val gid: Int)
