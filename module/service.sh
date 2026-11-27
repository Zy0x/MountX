#!/system/bin/sh
# =============================================================================
# MountX — service.sh
# Root Module Service Script (Magisk, KernelSU, APatch).
# Runs after boot; bind-mounts game data folders and custom paths from external
# storage into all Android runtime namespaces.
#
# Configuration files (in module directory):
#   config.conf      — SD_BASE, SD_BLOCK, FS_TYPE, I/O tweaks
#   mountpoints.conf — Modern multi-target pipeline (pkg|cat|src|dst|preserveMedia|diskUuid|userId)
#   gamelist.conf    — Legacy fallback (pkg:mode)
#
# Author : Noir
# License: MIT
# =============================================================================

# ── Module paths (Magisk / KernelSU / APatch / SukiSU / KSU-variants auto-discovery) ───
MODULE_DIR=""
_self_mod_dir=$(cd "$(dirname "$0")" 2>/dev/null && pwd)
if [ -n "${_self_mod_dir}" ] && [ -f "${_self_mod_dir}/module.prop" ]; then
    MODULE_DIR="${_self_mod_dir}"
fi
if [ -z "${MODULE_DIR}" ]; then
    for _try_dir in \
        "/data/adb/modules/MountX" \
        "/data/adb/modules/Mountify" \
        "/data/adb/ksu/modules/MountX" \
        "/data/adb/ksud/modules/MountX" \
        "/data/adb/ap/modules/MountX"; do
        if [ -d "${_try_dir}" ]; then
            MODULE_DIR="${_try_dir}"
            break
        fi
    done
fi
[ -z "${MODULE_DIR}" ] && MODULE_DIR="/data/adb/modules/MountX"

# ── Standalone Binary & Environment Setup ─────────────────────────────────────
# Prepend MountX standalone binaries and all known root binary paths to PATH
for _bin_candidate in \
    "/dev/mountx_bin" \
    "${MODULE_DIR}/bin" \
    "/data/adb/modules/MountX/bin" \
    "/data/adb/ap/bin" \
    "/data/adb/magisk" \
    "/data/adb/ksu/bin"; do
    if [ -d "${_bin_candidate}" ]; then
        case ":${PATH}:" in
            *:"${_bin_candidate}":*) ;;
            *) PATH="${_bin_candidate}:${PATH}" ;;
        esac
    fi
done
export PATH

# Ensure standalone busybox applets and filesystem links exist
if [ -f "${MODULE_DIR}/bin/busybox" ]; then
    chmod 755 "${MODULE_DIR}/bin/busybox" 2>/dev/null
    for _applet in fstrim awk flock blkid losetup fdisk tar; do
        if [ ! -e "${MODULE_DIR}/bin/${_applet}" ] && ! command -v "${_applet}" >/dev/null 2>&1; then
            ln -sf "${MODULE_DIR}/bin/busybox" "${MODULE_DIR}/bin/${_applet}" 2>/dev/null || true
        fi
    done
fi
if [ -x "/system/bin/make_f2fs" ] && [ ! -e "${MODULE_DIR}/bin/mkfs.f2fs" ] && ! command -v mkfs.f2fs >/dev/null 2>&1; then
    ln -sf "/system/bin/make_f2fs" "${MODULE_DIR}/bin/mkfs.f2fs" 2>/dev/null || true
fi

CONFIG_FILE="${MODULE_DIR}/config.conf"
MOUNTPOINTS_FILE="${MODULE_DIR}/mountpoints.conf"
GAMELIST_FILE="${MODULE_DIR}/gamelist.conf"
LOG_FILE="${MODULE_DIR}/mountx.log"
BOOT_FLAG_FILE="/dev/.mountx_booted"
BOOT_ACTIVE_FILE="/dev/.mountx_boot_active"
APPLY_LOCK_FILE="/dev/.mountx_apply.lock"
PID_FILE="/dev/.mountx_service.pid"

# ── Default configuration values ─────────────────────────────────────────────
SD_BASE="/data/sdext2"
SD_BLOCK=""
FS_TYPE="f2fs"
IO_TWEAKS_ENABLED=1
READ_AHEAD_KB=2048
IO_SCHEDULER="none"
RQ_AFFINITY=2
NR_REQUESTS=256
VFS_CACHE_PRESSURE=20

# ── Logging helper ────────────────────────────────────────────────────────────
log() {
    local level="$1"
    shift
    local msg="$*"
    local ts
    ts=$(date '+%Y-%m-%d %H:%M:%S')
    echo "[${ts}] [${level}] ${msg}" >> "${LOG_FILE}"
}

log_info()  { log "INFO " "$@"; }
log_warn()  { log "WARN " "$@"; }
log_error() { log "ERROR" "$@"; }

# ── Initialise log for this boot ──────────────────────────────────────────────
# Read dynamic version from module.prop
MOD_VER=$(grep -E '^version=' "${MODULE_DIR}/module.prop" 2>/dev/null | cut -d= -f2 | tr -d '\r\n')
[ -z "${MOD_VER}" ] && MOD_VER="v2.2.47"

# Log rotation: prevent log bloat on cold boot (> 512KB)
if [ -f "${LOG_FILE}" ] && [ "$(stat -c%s "${LOG_FILE}" 2>/dev/null || echo 0)" -gt 524288 ]; then
    tail -n 400 "${LOG_FILE}" > "${LOG_FILE}.tmp" 2>/dev/null && mv -f "${LOG_FILE}.tmp" "${LOG_FILE}" 2>/dev/null
fi

{
    echo "============================================================"
    echo " MountX service started (${MOD_VER}) — $(date)"
    echo "============================================================"
} >> "${LOG_FILE}"

# ── Universal Mount & Propagation Helpers (Android 10-17 / Toybox+BusyBox) ──
_MX_BB_CACHED=""
_mx_busybox() {
    if [ -n "${_MX_BB_CACHED}" ] && [ -x "${_MX_BB_CACHED}" ]; then
        echo "${_MX_BB_CACHED}"
        return 0
    fi
    if [ -x /dev/mountx_bin/busybox ]; then
        _MX_BB_CACHED="/dev/mountx_bin/busybox"
    elif [ -n "${MODULE_DIR}" ] && [ -x "${MODULE_DIR}/bin/busybox" ]; then
        _MX_BB_CACHED="${MODULE_DIR}/bin/busybox"
    elif command -v busybox >/dev/null 2>&1; then
        _MX_BB_CACHED=$(command -v busybox)
    fi
    [ -n "${_MX_BB_CACHED}" ] && echo "${_MX_BB_CACHED}"
}

mx_make_rprivate() {
    local _p="${1%/}"
    [ -z "${_p}" ] && _p="/"
    local _bb
    _bb=$(_mx_busybox)
    if [ -n "${_bb}" ]; then
        "${_bb}" mount --make-rprivate "${_p}" 2>/dev/null && return 0
    fi
    mount --make-rprivate "${_p}" 2>/dev/null
}

mx_is_mounted_exact() {
    local _p="${1%/}"
    [ -z "${_p}" ] && return 1
    local _mi="/proc/1/mountinfo"
    [ ! -r "${_mi}" ] && _mi="/proc/self/mountinfo"
    if [ -r "${_mi}" ]; then
        case "${_p}" in
            *" "*) ;;
            *) grep -qF " ${_p} " "${_mi}" 2>/dev/null || return 1 ;;
        esac
        awk -v t="${_p}" '{m=$5; gsub(/\\040/, " ", m); if (m==t) {f=1; exit}} END {exit (f?0:1)}' "${_mi}" 2>/dev/null
        return $?
    elif [ -r /proc/mounts ]; then
        case "${_p}" in
            *" "*) ;;
            *) grep -qF " ${_p} " /proc/mounts 2>/dev/null || return 1 ;;
        esac
        awk -v t="${_p}" '{m=$2; gsub(/\\040/, " ", m); if (m==t) {f=1; exit}} END {exit (f?0:1)}' /proc/mounts 2>/dev/null
        return $?
    fi
    mountpoint -q "${_p}" 2>/dev/null
}

mx_is_mounted_in_pid() {
    local _pid="$1"
    local _p="${2%/}"
    [ -z "${_pid}" ] || [ -z "${_p}" ] && return 1
    local _mi="/proc/${_pid}/mountinfo"
    [ ! -r "${_mi}" ] && return 1
    awk -v t="${_p}" '{m=$5; gsub(/\\040/, " ", m); if (m==t) {f=1; exit}} END {exit (f?0:1)}' "${_mi}" 2>/dev/null
}

# Resolves unique PIDs owning distinct /proc/<pid>/ns/mnt namespaces across Zygote and idle (uid 0)
# USAP workers without ever signalling/killing usap64/usap32. Idle USAPs normally share Zygote's
# mnt_namespace, and sending external SIGTERM triggers a permanent POLLHUP busy-loop in ZygoteServer.runSelectLoop().
_mx_zygote_mnt_pids() {
    local _seen="" _pname _p _uid_line _ns _out=""
    for _pname in zygote64 zygote usap64 usap32; do
        for _p in $(pidof "${_pname}" 2>/dev/null); do
            [ -e "/proc/${_p}/ns/mnt" ] || continue
            _uid_line=$(awk '/^Uid:/{print $2; exit}' "/proc/${_p}/status" 2>/dev/null)
            case "${_uid_line}" in
                ""|0) ;;
                *) continue ;;
            esac
            _ns=$(readlink "/proc/${_p}/ns/mnt" 2>/dev/null)
            [ -n "${_ns}" ] || _ns="pid:${_p}"
            case " ${_seen} " in
                *" ${_ns} "*) continue ;;
            esac
            _seen="${_seen} ${_ns}"
            _out="${_out} ${_p}"
        done
    done
    echo "${_out# }"
}

mx_is_mounted() {
    local _p="${1%/}"
    [ -z "${_p}" ] && return 1
    if mx_is_mounted_exact "${_p}"; then
        return 0
    fi
    local _rp=""
    _rp=$(readlink -f "${_p}" 2>/dev/null)
    _rp="${_rp%/}"
    if [ -n "${_rp}" ] && [ "${_rp}" != "${_p}" ]; then
        mx_is_mounted_exact "${_rp}" && return 0
    fi
    mountpoint -q "${_p}" 2>/dev/null
}

# Safe non-forceful umount (umount || umount -l): NEVER passes -f so F2FS superblocks never trigger checkpoint shutdown (EIO).
mx_umount_safe() {
    local _t="${1%/}"
    [ -z "${_t}" ] && return 0
    umount "${_t}" 2>/dev/null || umount -l "${_t}" 2>/dev/null
}

# Returns 0 iff directory _r is a real non-symlink directory backed by a live non-tmpfs filesystem in PID 1 mountinfo.
mx_root_is_live_storage() {
    local _cand="${1%/}"
    [ -n "${_cand}" ] && [ -d "${_cand}" ] && [ ! -L "${_cand}" ] || return 1
    local _st
    _st=$(LC_ALL=C stat -t "${_cand}" 2>&1)
    case "${_st}" in *"Transport endpoint"*) return 1 ;; esac
    local _parent="${_cand%/*}"
    local _mi="/proc/1/mountinfo"
    [ ! -r "${_mi}" ] && _mi="/proc/self/mountinfo"
    [ ! -r "${_mi}" ] && return 0
    local _fst
    _fst=$(C="${_cand}" R="${_parent}" awk -F ' - ' '{
        split($1, L, " "); split($2, RT, " ");
        m = L[5]; gsub(/\\040/, " ", m);
        if (m == ENVIRON["C"] || m == ENVIRON["R"]) f = RT[1]
    } END { print f }' "${_mi}" 2>/dev/null)
    case "${_fst}" in
        ""|tmpfs) return 1 ;;
        *) return 0 ;;
    esac
}

# Peels corrupted MountX occlusion mounts on .../Android/(data|obb|media)$ and detached/dead ENOTCONN FUSE stacks
# across PID 1 and active Zygote namespaces without using `umount -f`.
mx_repair_dead_fuse_and_parents() {
    local _mi="/proc/1/mountinfo"
    [ ! -r "${_mi}" ] && _mi="/proc/self/mountinfo"
    [ ! -r "${_mi}" ] && return 0
    local _zpids
    _zpids=$(_mx_zygote_mnt_pids)

    _mx_umount_ns() {
        local _pt="${1%/}"
        [ -z "${_pt}" ] && return 0
        mx_umount_safe "${_pt}"
        local _zp
        for _zp in ${_zpids}; do
            [ -d "/proc/${_zp}/ns" ] || continue
            nsenter --mount="/proc/${_zp}/ns/mnt" -- sh -c 'umount "$1" 2>/dev/null || umount -l "$1" 2>/dev/null' _ "${_pt}" 2>/dev/null
        done
    }

    local _occl
    _occl=$(awk -F ' - ' '{
        split($1, L, " ");
        root = L[4]; mp = L[5]; gsub(/\\040/, " ", mp);
        if (mp ~ /(\/Android\/(data|obb|media)|\/Android|\/emulated\/[0-9]+)$/ && root ~ /(^|\/)MountX(\/|$)/) print mp
    }' "${_mi}" 2>/dev/null | awk '!seen[$0]++')
    local _op
    for _op in ${_occl}; do
        log_warn "[Repair] Removing corrupted parent occlusion mount: ${_op}"
        T="${_op}/" awk '{p=$5; gsub(/\\040/," ",p); if (index(p, ENVIRON["T"])==1) print length(p), p}' "${_mi}" 2>/dev/null \
          | sort -rn -k1,1 | cut -d' ' -f2- | awk '!seen[$0]++' | while IFS= read -r _k; do
            [ -n "${_k}" ] && _mx_umount_ns "${_k}"
        done
        _mx_umount_ns "${_op}"
    done

    local _roots
    _roots=$(awk -F ' - ' '{
        split($1, L, " "); split($2, R, " ");
        dev = L[3]; mp = L[5]; gsub(/\\040/, " ", mp); fst = R[1];
        if (mp ~ /^(\/storage\/emulated|\/mnt\/(user|installer|androidwritable|pass_through)\/[0-9]+\/emulated|\/mnt\/runtime\/(default|read|write|full)\/emulated)$/) {
            cnt[mp]++; fsts[mp] = fst
        }
    } END {
        for (m in cnt) {
            if (fsts[m] ~ /fuse/ || cnt[m] > 1) print cnt[m] "|" m
        }
    }' "${_mi}" 2>/dev/null | sort -r)

    echo "${_roots}" | while IFS='|' read -r _cnt _rmp; do
        [ -z "${_rmp}" ] && continue
        local _enotconn=0 _st _st0
        _st=$(LC_ALL=C stat -t "${_rmp}" 2>&1)
        case "${_st}" in *"Transport endpoint"*) _enotconn=1 ;; esac
        if [ "${_enotconn}" -eq 0 ] && [ -e "${_rmp}/0" ]; then
            _st0=$(LC_ALL=C stat -t "${_rmp}/0" 2>&1)
            case "${_st0}" in *"Transport endpoint"*) _enotconn=1 ;; esac
        fi
        if [ "${_enotconn}" -eq 1 ] || [ "${_cnt}" -gt 1 ]; then
            log_warn "[Repair] Peeling dead/stacked FUSE layer on ${_rmp} (enotconn=${_enotconn}, layers=${_cnt})..."
            T="${_rmp}/" awk '{p=$5; gsub(/\\040/," ",p); if (index(p, ENVIRON["T"])==1) print length(p), p}' "${_mi}" 2>/dev/null \
              | sort -rn -k1,1 | cut -d' ' -f2- | awk '!seen[$0]++' | while IFS= read -r _fk; do
                [ -n "${_fk}" ] && _mx_umount_ns "${_fk}"
            done
            _mx_umount_ns "${_rmp}"
        fi
    done
}

_dir_is_substantive() {
    local _d="$1"
    [ ! -d "${_d}" ] && return 1
    # Fast bounded check (<10ms even on 50GB+ game dirs): any file > 128KB or >= 6 files within depth 4
    if [ -n "$(find "${_d}" -maxdepth 4 -type f -size +128k 2>/dev/null | head -n 1)" ]; then
        return 0
    fi
    local _fc
    _fc=$(find "${_d}" -maxdepth 4 -type f 2>/dev/null | head -n 6 | wc -l)
    [ "${_fc:-0}" -ge 6 ] 2>/dev/null && return 0
    return 1
}

_emulated_root_generation() {
    local _mi="/proc/1/mountinfo"
    [ ! -r "${_mi}" ] && _mi="/proc/self/mountinfo"
    [ ! -r "${_mi}" ] && return 0
    awk '$5 == "/storage/emulated" || $5 ~ /^\/mnt\/user\/[0-9]+\/emulated$/ || $5 == "/mnt/runtime/default/emulated" || $5 == "/mnt/runtime/write/emulated" {print $1 ":" $5}' "${_mi}" 2>/dev/null | sort | tr '\n' ';'
}

_get_mediaprovider_pid() {
    local _p
    _p=$(pidof com.android.providers.media.module 2>/dev/null | awk '{print $1}')
    [ -z "${_p}" ] && _p=$(pgrep -f '^com\.android\.providers\.media\.module$' 2>/dev/null | head -n 1)
    echo "${_p}"
}

_probe_sd_io_healthy() {
    local _blk="$1"
    local _base="$2"
    local _max_attempts="${3:-3}"
    local _attempt=0
    local _mnt_line _mnt_src _mnt_opts
    _mnt_line=$(awk -v t="${_base}" '$2 == t {print $1 "|" $4; exit}' /proc/mounts 2>/dev/null)
    _mnt_src="${_mnt_line%%|*}"
    _mnt_opts="${_mnt_line#*|}"
    while [ ${_attempt} -lt "${_max_attempts}" ]; do
        local _ok=1
        if [ -n "${_blk}" ] && [ ! -b "${_blk}" ]; then
            _ok=0
        fi
        case "${_mnt_src}" in
            /dev/block/*)
                [ ! -b "${_mnt_src}" ] && _ok=0
                ;;
        esac
        if [ -z "${_mnt_line}" ] || echo "${_mnt_opts}" | grep -qE '(^|,)ro(,|$)'; then
            _ok=0
        fi
        if [ ${_ok} -eq 1 ] && [ -n "${_blk}" ]; then
            if ! dd if="${_blk}" of=/dev/null bs=4096 count=1 2>/dev/null; then
                _ok=0
            fi
        fi
        if [ ${_ok} -eq 1 ] && [ -n "${_base}" ]; then
            if ! ls "${_base}" >/dev/null 2>&1; then
                _ok=0
            elif [ -d "${_base}/MountX" ] && ! ls "${_base}/MountX" >/dev/null 2>&1; then
                _ok=0
            else
                # Kernel write + unlink check catches F2FS SBI_IS_SHUTDOWN (-EIO) & silent write-aborted superblocks
                local _rw_probe="${_base}/.mountx/.rw_probe_$$"
                mkdir -p "${_base}/.mountx" 2>/dev/null
                if touch "${_rw_probe}" 2>/dev/null; then
                    rm -f "${_rw_probe}" 2>/dev/null
                else
                    _rw_probe="${_base}/.mx_rw_probe_$$"
                    if touch "${_rw_probe}" 2>/dev/null; then
                        rm -f "${_rw_probe}" 2>/dev/null
                    else
                        _ok=0
                    fi
                fi
            fi
        fi
        if [ ${_ok} -eq 1 ]; then
            return 0
        fi
        _attempt=$((_attempt + 1))
        # Settle grace for SDHCI/MSDC controller resuming from deep sleep / runtime PM
        [ ${_attempt} -lt "${_max_attempts}" ] && sleep 1
    done
    return 1
}

# Authoritative live visibility & anti-shadowing check for a mountpoint target.
# Returns 0 iff target is actively mounted in global + user-facing + Zygote namespaces and not shadowed.
mx_verify_target_live() {
    local _cat="$1"
    local _src="$2"
    local _dst="$3"
    local _rel="$4"
    local _uid="${5:-0}"

    if [ -n "${_rel}" ]; then
        local _st="/storage/emulated/${_uid}/${_rel}"
        local _mu="/mnt/user/${_uid}/emulated/${_uid}/${_rel}"
        local _rd="/mnt/runtime/default/emulated/${_uid}/${_rel}"
        local _rw="/mnt/runtime/write/emulated/${_uid}/${_rel}"

        # 1. User-facing /storage/emulated must be mounted when /storage/emulated exists & is mounted
        if mx_is_mounted_exact "/storage/emulated"; then
            mx_is_mounted_exact "${_st}" || return 1
        elif mx_is_mounted_exact "/mnt/runtime/default/emulated"; then
            mx_is_mounted_exact "${_rd}" || return 1
        fi

        # 2. Android 11+ FUSE /mnt/user/<uid>/emulated must also be mounted when present
        if mx_is_mounted_exact "/mnt/user/${_uid}/emulated"; then
            mx_is_mounted_exact "${_mu}" || return 1
        fi

        # 3. Runtime write namespace must be mounted when present
        if mx_is_mounted_exact "/mnt/runtime/write/emulated"; then
            mx_is_mounted_exact "${_rw}" || return 1
        fi

        # 4. Anti-shadowing live visibility check (POSIX inode parity first, canary fallback for FUSE/non-POSIX)
        local _check_view="${_st}"
        if ! mx_is_mounted_exact "/storage/emulated" && [ ! -d "/storage/emulated/${_uid}" ]; then
            _check_view="${_rd}"
        fi
        local _sid _tid
        _sid=$(stat -c '%d:%i' "${_src}" 2>/dev/null)
        _tid=$(stat -c '%d:%i' "${_check_view}" 2>/dev/null)
        if [ -n "${_sid}" ] && [ "${_sid}" = "${_tid}" ]; then
            :
        elif [ -f "${_src}/.mountx_canary" ]; then
            [ -f "${_check_view}/.mountx_canary" ] || return 1
        else
            # Neither same inode nor canary present: cannot confirm unshadowed
            return 1
        fi

        # 5. Zygote mount namespace check: newly forked apps inherit Zygote's mount table
        local _zpid
        for _zpid in $(_mx_zygote_mnt_pids); do
            [ ! -r "/proc/${_zpid}/mountinfo" ] && continue
            if mx_is_mounted_in_pid "${_zpid}" "/mnt/user/${_uid}/emulated"; then
                mx_is_mounted_in_pid "${_zpid}" "${_mu}" || return 1
            elif mx_is_mounted_in_pid "${_zpid}" "/mnt/runtime/write/emulated"; then
                mx_is_mounted_in_pid "${_zpid}" "${_rw}" || return 1
            elif mx_is_mounted_in_pid "${_zpid}" "/mnt/runtime/default/emulated"; then
                mx_is_mounted_in_pid "${_zpid}" "${_rd}" || return 1
            fi
        done
        return 0
    fi

    # Internal / system target (/data/user/0/..., /data/app/..., etc.)
    mx_is_mounted_exact "${_dst}" || return 1
    case "${_src}" in
        *.img)
            # Loop container: mount presence on exact dst verified above
            return 0
            ;;
    esac
    local _isid _itid
    _isid=$(stat -c '%d:%i' "${_src}" 2>/dev/null)
    _itid=$(stat -c '%d:%i' "${_dst}" 2>/dev/null)
    if [ -n "${_isid}" ] && [ -n "${_itid}" ]; then
        [ "${_isid}" = "${_itid}" ] && return 0
        return 1
    fi
    return 0
}

# ── Load config.conf ─────────────────────────────────────────────────────────
load_config() {
    if [ ! -f "${CONFIG_FILE}" ]; then
        log_warn "config.conf not found; using built-in defaults"
        return
    fi

    while IFS='=' read -r key val; do
        key=$(echo "${key}" | sed 's/#.*//' | tr -d ' \t\r')
        val=$(echo "${val}" | sed 's/#.*//' | tr -d ' \t\r')
        [ -z "${key}" ] && continue

        case "${key}" in
            SD_BASE)             SD_BASE="${val}"             ;;
            SD_BLOCK)            SD_BLOCK="${val}"            ;;
            FS_TYPE)             FS_TYPE="${val}"             ;;
            IO_TWEAKS_ENABLED)   IO_TWEAKS_ENABLED="${val}"   ;;
            READ_AHEAD_KB)       READ_AHEAD_KB="${val}"       ;;
            IO_SCHEDULER)        IO_SCHEDULER="${val}"        ;;
            RQ_AFFINITY)         RQ_AFFINITY="${val}"         ;;
            NR_REQUESTS)         NR_REQUESTS="${val}"         ;;
            VFS_CACHE_PRESSURE)  VFS_CACHE_PRESSURE="${val}"  ;;
            *) log_warn "config.conf: unknown key '${key}'"   ;;
        esac
    done < "${CONFIG_FILE}"

    log_info "Config loaded — SD_BASE=${SD_BASE} SD_BLOCK=${SD_BLOCK} FS_TYPE=${FS_TYPE} IO_TWEAKS=${IO_TWEAKS_ENABLED}"
}

# ── Authoritative Emulated Primary Storage & Unlock State Probes ──────────────
_emulated_mount_snapshot() {
    local _src_file="/proc/1/mountinfo"
    [ ! -r "${_src_file}" ] && _src_file="/proc/mounts"
    grep -E ' /(storage|mnt/runtime/[^/]+|mnt/(user|pass_through|installer|androidwritable)/[0-9]+)/emulated' "${_src_file}" 2>/dev/null | \
        grep -v '/MountX' | sort
}

is_emulated_storage_ready() {
    # Non-blocking live read access probe across Android 10-17 (fuse, fuse-bpf, sdcardfs, esdfs)
    if ! grep -qE '^[^ ]+ (/storage/emulated|/mnt/user/[0-9]+/emulated|/mnt/runtime/default/emulated) (fuse[^ ]*|sdcardfs|esdfs) ' /proc/mounts 2>/dev/null; then
        return 1
    fi
    local _st
    _st=$(LC_ALL=C stat -t /storage/emulated /storage/emulated/0 2>&1)
    case "${_st}" in *"Transport endpoint"*) return 1 ;; esac
    if ! grep -qw sdcardfs /proc/filesystems 2>/dev/null; then
        # Android 11+ FUSE: verify /mnt/user/0/emulated is live FUSE rather than bare tmpfs when present
        if [ -d "/mnt/user/0/emulated" ] && ! mx_root_is_live_storage "/mnt/user/0/emulated/0"; then
            return 1
        fi
    fi
    if [ -d "/storage/emulated/0/Android" ] && ls /storage/emulated/0/Android >/dev/null 2>&1; then
        return 0
    fi
    if [ -d "/storage/emulated/0" ] && ls /storage/emulated/0 >/dev/null 2>&1; then
        return 0
    fi
    return 1
}

wait_for_emulated_storage_settled() {
    local _max_wait="${1:-30}"
    local _waited=0
    local _prev_snap=""
    local _curr_snap=""
    local _sdk _has_sdcardfs=0
    _sdk=$(getprop ro.build.version.sdk 2>/dev/null | tr -d '\r\n')
    grep -qw sdcardfs /proc/filesystems 2>/dev/null && _has_sdcardfs=1
    while [ "${_waited}" -lt "${_max_wait}" ]; do
        mx_repair_dead_fuse_and_parents
        local _full_ok=1
        if [ "${_sdk:-30}" -ge 30 ] 2>/dev/null && [ -d "/mnt/runtime/full/emulated" ]; then
            grep -qE '^[^ ]+ /mnt/runtime/full/emulated (fuse[^ ]*|sdcardfs|esdfs) ' /proc/mounts 2>/dev/null || _full_ok=0
        fi
        if [ "${_sdk:-30}" -ge 30 ] 2>/dev/null && [ "${_has_sdcardfs}" -eq 0 ]; then
            for _fdir in "/mnt/user/0/emulated" "/mnt/installer/0/emulated" "/mnt/androidwritable/0/emulated"; do
                if [ -d "${_fdir}" ]; then
                    grep -qE "^[^ ]+ ${_fdir} (fuse[^ ]*|sdcardfs|esdfs) " /proc/mounts 2>/dev/null || _full_ok=0
                fi
            done
        fi
        local _android_ok=0
        if [ -d "/storage/emulated/0/Android/data" ] && ls /storage/emulated/0/Android/data >/dev/null 2>&1; then
            _android_ok=1
        elif [ -d "/storage/emulated/0/Android" ] && ls /storage/emulated/0/Android >/dev/null 2>&1; then
            _android_ok=1
        fi
        if is_emulated_storage_ready && [ "${_full_ok}" = "1" ] && [ "${_android_ok}" = "1" ]; then
            _curr_snap=$(_emulated_mount_snapshot)
            if [ -n "${_curr_snap}" ] && [ "${_curr_snap}" = "${_prev_snap}" ]; then
                log_info "Vold FUSE/sdcardfs storage settled cleanly (${_waited}s)."
                return 0
            fi
            _prev_snap="${_curr_snap}"
        else
            _prev_snap=""
        fi
        sleep 2
        _waited=$((_waited + 2))
    done
    log_warn "Vold storage settlement check reached ${_max_wait}s cap; proceeding."
    return 0
}

_mx_collect_skipped_pkgs() {
    [ ! -f "${MOUNTPOINTS_FILE}" ] && { echo " "; return 0; }
    local _skip=" " _pk _c _s _rest
    while IFS='|' read -r _pk _c _s _rest || [ -n "${_pk}" ]; do
        _pk=$(echo "${_pk}" | tr -d '\r')
        _s=$(echo "${_s}" | tr -d '\r')
        case "${_pk}" in ""|\#*) continue ;; esac
        if [ -n "${_s}" ] && [ ! -e "${_s}" ]; then
            case "${_skip}" in
                *" ${_pk} "*) ;;
                *) _skip="${_skip}${_pk} " ;;
            esac
        fi
    done < "${MOUNTPOINTS_FILE}"
    echo "${_skip}"
}

verify_post_mount_canaries() {
    local _ctx="$1"
    [ ! -f "${MOUNTPOINTS_FILE}" ] && return 0
    local _skip_pkgs
    _skip_pkgs=$(_mx_collect_skipped_pkgs)
    local _pkg _cat _src _dst _pm _du _uid _rel _eu

    local _shadowed=0
    while IFS='|' read -r _pkg _cat _src _dst _pm _du _uid || [ -n "${_pkg}" ]; do
        _pkg=$(echo "${_pkg}" | tr -d '\r')
        _cat=$(echo "${_cat}" | tr -d '\r')
        _src=$(echo "${_src}" | tr -d '\r')
        _dst=$(echo "${_dst}" | tr -d '\r')
        _uid=$(echo "${_uid}" | tr -d '\r')
        case "${_pkg}" in ""|\#*) continue ;; esac
        case "${_skip_pkgs}" in *" ${_pkg} "*) continue ;; esac
        case "${_cat}" in EXTERNAL_DATA|OBB_STORAGE|MEDIA_DOWNLOADS|GAME_ASSETS) ;; *) continue ;; esac
        [ -z "${_src}" ] || [ -z "${_dst}" ] && continue
        [ ! -d "${_src}" ] && continue
        if [ -n "${_pkg}" ] && ! is_package_installed "${_pkg}"; then
            continue
        fi
        _rel=""
        case "${_dst}" in
            /data/media/[0-9]*/*) _rel="${_dst#/data/media/[0-9]*/}" ;;
            /storage/emulated/[0-9]*/*) _rel="${_dst#/storage/emulated/[0-9]*/}" ;;
            /sdcard/*) _rel="${_dst#/sdcard/}" ;;
        esac
        [ -z "${_rel}" ] && continue
        _eu="${_uid:-0}"
        if ! mx_verify_target_live "${_cat}" "${_src}" "${_dst}" "${_rel}" "${_eu}"; then
            log_warn "[${_ctx}] Post-mount +3s check: ${_pkg} (${_cat}) mount missing/shadowed on /storage/emulated/${_eu}/${_rel}!"
            _shadowed=1
            break
        fi
    done < "${MOUNTPOINTS_FILE}"
    if [ "${_shadowed}" = "1" ]; then
        log_warn "[${_ctx}] Immediately re-syncing shadowed/missing FUSE/sdcardfs mount(s)..."
        cleanup_stale_mounts
        apply_all_mounts
    else
        log_info "[${_ctx}] Post-mount +3s canary verification passed (all SD mounts live & unshadowed)."
    fi
}

_default_device_abi() {
    case "$(uname -m 2>/dev/null)" in
        aarch64|arm64) echo "arm64" ;;
        armv8*|armv7*|arm*) echo "arm" ;;
        x86_64) echo "x86_64" ;;
        i*86|x86) echo "x86" ;;
        riscv64) echo "riscv64" ;;
        *)
            local _abi_prop
            _abi_prop=$(getprop ro.product.cpu.abi 2>/dev/null | tr -d '\r\n')
            case "${_abi_prop}" in
                arm64*) echo "arm64" ;;
                armeabi*) echo "arm" ;;
                x86_64*) echo "x86_64" ;;
                x86*) echo "x86" ;;
                *) echo "arm64" ;;
            esac
            ;;
    esac
}

_detect_lib_abi() {
    local _src_dir="$1"
    local _apk_dir="$2"
    local _dst_hint="$3"
    local _cand _sub
    for _cand in arm64 arm armeabi-v7a armeabi x86_64 x86 riscv64; do
        if [ -n "${_src_dir}" ] && [ -d "${_src_dir}/${_cand}" ] && \
           [ -n "$(find "${_src_dir}/${_cand}" -maxdepth 1 -name '*.so' 2>/dev/null | head -n 1)" ]; then
            echo "${_cand}"
            return 0
        fi
    done
    if [ -n "${_apk_dir}" ] && [ -d "${_apk_dir}/lib" ]; then
        _sub=$(find "${_apk_dir}/lib" -mindepth 1 -maxdepth 1 -type d 2>/dev/null | head -n 1)
        if [ -n "${_sub}" ]; then
            basename "${_sub}"
            return 0
        fi
    fi
    case "${_dst_hint}" in
        */lib/*)
            _sub="${_dst_hint##*/lib/}"
            _sub="${_sub%%/*}"
            if [ -n "${_sub}" ]; then
                echo "${_sub}"
                return 0
            fi
            ;;
    esac
    _default_device_abi
}

_parent_disk_name() {
    local _bname
    _bname=$(basename "$1")
    case "${_bname}" in
        mmcblk*p[0-9]*|nvme*n*p[0-9]*) echo "${_bname%p[0-9]*}" ;;
        *) echo "${_bname}" | sed 's/[0-9]*$//' ;;
    esac
}

_is_internal_system_disk() {
    local _dev="$1"
    [ -z "${_dev}" ] && return 1
    local _bname _disk _dtype
    _bname=$(basename "${_dev}")
    case "${_bname}" in
        loop*|ram*|zram*|dm-*) return 0 ;;
        *boot[0-9]|*rpmb) return 0 ;;
    esac
    _disk=$(_parent_disk_name "${_dev}")
    [ -z "${_disk}" ] && return 0

    local _mnt_dev
    for _mnt_dev in $(awk '$2 ~ "^/(data|system|vendor|product|odm|system_ext|metadata|cache|mnt/vendor|apex|persist)$" {print $1}' /proc/mounts 2>/dev/null); do
        case "${_mnt_dev}" in
            /dev/block/${_disk}|/dev/block/${_disk}p[0-9]*|/dev/block/${_disk}[0-9]*)
                return 0
                ;;
        esac
    done

    local _dm_slave
    for _dm_slave in /sys/block/dm-*/slaves/${_disk}*; do
        [ -e "${_dm_slave}" ] && return 0
    done

    if [ -r "/sys/block/${_disk}/device/type" ]; then
        _dtype=$(cat "/sys/block/${_disk}/device/type" 2>/dev/null | tr -d '\r\n')
        [ "${_dtype}" = "MMC" ] && return 0
    fi
    return 1
}

_resolve_sd_base_dev_by_uuid() {
    [ ! -f "${MOUNTPOINTS_FILE}" ] && return 1
    local _base="${SD_BASE%/}" _conf_uuid _by_uuid
    _conf_uuid=$(awk -F'|' -v b="${_base}" '!/^#/ && NF>=6 && ($3 == b || index($3, b "/") == 1) && $6 != "" {print $6; exit}' "${MOUNTPOINTS_FILE}" 2>/dev/null)
    [ -z "${_conf_uuid}" ] && return 1
    _by_uuid=$(blkid 2>/dev/null | grep -i "UUID=\"${_conf_uuid}\"" | head -n 1 | cut -d: -f1)
    [ -z "${_by_uuid}" ] && _by_uuid=$(toybox blkid 2>/dev/null | grep -i "UUID=\"${_conf_uuid}\"" | head -n 1 | cut -d: -f1)
    if [ -n "${_by_uuid}" ] && [ -b "${_by_uuid}" ] && ! _is_internal_system_disk "${_by_uuid}"; then
        echo "${_by_uuid}"
        return 0
    fi
    return 1
}

_resolve_monitored_blk_dev() {
    if [ -n "${SD_BLOCK}" ] && [ -b "${SD_BLOCK}" ] && ! _is_internal_system_disk "${SD_BLOCK}"; then
        echo "${SD_BLOCK}"
        return 0
    fi
    local _active
    _active=$(grep -F " ${SD_BASE} " /proc/mounts 2>/dev/null | head -n 1 | awk '{print $1}')
    case "${_active}" in
        /dev/block/*)
            if [ -b "${_active}" ] && ! _is_internal_system_disk "${_active}"; then
                echo "${_active}"
                return 0
            fi
            ;;
    esac
    local _by_uuid
    _by_uuid=$(_resolve_sd_base_dev_by_uuid)
    if [ -n "${_by_uuid}" ]; then
        echo "${_by_uuid}"
        return 0
    fi
    case "${_active}" in
        /dev/block/*)
            echo "${_active}"
            return 0
            ;;
    esac
    if [ -n "${SD_BLOCK}" ] && ! _is_internal_system_disk "${SD_BLOCK}"; then
        echo "${SD_BLOCK}"
        return 0
    fi
    echo ""
}

_mx_busybox_bin() {
    if [ -x "${MODULE_DIR}/bin/busybox" ]; then
        echo "${MODULE_DIR}/bin/busybox"
    elif [ -x "/dev/mountx_bin/busybox" ]; then
        echo "/dev/mountx_bin/busybox"
    elif command -v busybox >/dev/null 2>&1; then
        command -v busybox
    fi
}

# Per-mount read-only / read-write + exec via busybox so parent F2FS/ext4 superblock never flips global RO.
_mx_bind_remount_ro_exec() {
    local _tgt="$1"
    local _bb
    _bb=$(_mx_busybox_bin)
    if [ -n "${_bb}" ]; then
        "${_bb}" mount -o remount,ro,exec,bind "${_tgt}" 2>/dev/null && return 0
    fi
    return 1
}

_mx_bind_remount_rw_exec() {
    local _tgt="$1"
    local _bb
    _bb=$(_mx_busybox_bin)
    if [ -n "${_bb}" ]; then
        "${_bb}" mount -o remount,rw,exec,bind "${_tgt}" 2>/dev/null && return 0
    fi
    mount -o remount,bind,rw,exec "${_tgt}" 2>/dev/null || mount -o remount,rw,exec "${_tgt}" 2>/dev/null
}

# Checks whether a directory holds real non-skeleton files (>0B excluding MountX markers & lockfiles)
_mx_dir_has_real_files() {
    local _dir="$1"
    local _min_kb="${2:-48}"
    [ -d "${_dir}" ] || return 1
    local _rf _kb
    _rf=$(find "${_dir}" -type f -size +0c ! -name '.nomedia' ! -name '.mountx_canary' ! -name '.mountx_perms_ok' ! -name '*.lck' 2>/dev/null | head -n 1)
    [ -z "${_rf}" ] && return 1
    _kb=$(du -sk "${_dir}" 2>/dev/null | cut -f1)
    [ "${_kb:-0}" -ge "${_min_kb}" ] 2>/dev/null
}

# Universal multi-APK + single-APK session installer for Android 10-17 via root pm
_mx_pm_install_apk_dir() {
    local _apk_dir="$1"
    [ -f "${_apk_dir}/base.apk" ] && [ -s "${_apk_dir}/base.apk" ] || return 1
    local _sid _ok=0 _apk _sz _idx=0 _total_apks=0
    for _apk in "${_apk_dir}"/*.apk; do
        [ -f "${_apk}" ] && _total_apks=$((_total_apks + 1))
    done
    [ "${_total_apks}" -eq 0 ] && return 1
    _sid=$(pm install-create -r -d 2>/dev/null | sed -n 's/.*\[\([0-9][0-9]*\)\].*/\1/p' | head -n 1)
    if [ -n "${_sid}" ]; then
        _ok=1
        for _apk in "${_apk_dir}/base.apk" "${_apk_dir}"/*.apk; do
            [ -f "${_apk}" ] || continue
            case "${_apk}" in
                "${_apk_dir}/base.apk") [ "${_idx}" -gt 0 ] && continue ;;
            esac
            _sz=$(stat -c '%s' "${_apk}" 2>/dev/null)
            [ -z "${_sz}" ] && _sz=$(wc -c < "${_apk}" 2>/dev/null | tr -d ' ')
            if [ -z "${_sz}" ] || [ "${_sz}" -le 0 ] 2>/dev/null; then
                _ok=0
                break
            fi
            local _bname
            _bname=$(basename "${_apk}")
            if ! pm install-write -S "${_sz}" "${_sid}" "${_idx}_${_bname}" "${_apk}" >/dev/null 2>&1; then
                _ok=0
                break
            fi
            _idx=$((_idx + 1))
        done
        if [ "${_ok}" -eq 1 ]; then
            if pm install-commit "${_sid}" >/dev/null 2>&1; then
                return 0
            fi
        fi
        pm install-abandon "${_sid}" >/dev/null 2>&1 || true
    fi
    if [ "${_total_apks}" -eq 1 ]; then
        pm install -r -d "${_apk_dir}/base.apk" >/dev/null 2>&1 && return 0
    fi
    return 1
}

auto_heal_apk_native_libs() {
    local _pkg="$1"
    local _dst="$2"
    if echo "${_dst}" | grep -q "/lib"; then
        return 0
    fi
    if [ -d "${_dst}/lib" ] && [ -n "$(find "${_dst}/lib" -name '*.so' 2>/dev/null | head -n 1)" ]; then
        return 0
    fi
    local _sd_lib="${SD_BASE}/MountX/lib/${_pkg}"
    [ ! -d "${_sd_lib}" ] && return 0
    [ -z "$(find "${_sd_lib}" -name '*.so' 2>/dev/null | head -n 1)" ] && return 0
    local _abi _lib_src
    _abi=$(_detect_lib_abi "${_sd_lib}" "${_dst}" "")
    _lib_src="${_sd_lib}"
    [ -d "${_sd_lib}/${_abi}" ] && _lib_src="${_sd_lib}/${_abi}"
    log_info "  [${_pkg}] Auto-healing missing native libs: ${_lib_src} -> ${_dst}/lib/${_abi}..."
    chown -R 1000:1000 "${_lib_src}" 2>/dev/null
    chmod -R 755 "${_lib_src}" 2>/dev/null
    mkdir -p "${_dst}/lib/${_abi}" 2>/dev/null
    if mount -o bind "${_lib_src}" "${_dst}/lib/${_abi}" 2>/dev/null; then
        _mx_bind_remount_ro_exec "${_dst}/lib/${_abi}" || _mx_bind_remount_rw_exec "${_dst}/lib/${_abi}"
        mx_make_rprivate "${_dst}/lib/${_abi}"
    fi
}

is_ce_key_unlocked() {
    local crypto_type
    crypto_type=$(getprop ro.crypto.type 2>/dev/null | tr -d '\r\n')
    if [ "${crypto_type}" = "file" ]; then
        local ce_ready
        ce_ready=$(getprop sys.user.0.ce_available 2>/dev/null | tr -d '\r\n')
        [ "${ce_ready}" = "true" ]
        return $?
    fi
    # Fallback for FDE / unencrypted devices where sys.user.0.ce_available may not flip
    local boot_done
    boot_done=$(getprop sys.boot_completed 2>/dev/null | tr -d '\r\n')
    [ "${boot_done}" = "1" ]
    return $?
}

is_user_unlocked() {
    is_ce_key_unlocked && is_emulated_storage_ready
    return $?
}

# ── Wait until Android core services have finished booting ────────────────────
wait_for_boot() {
    log_info "Waiting for sys.boot_completed…"
    local count=0
    while [ "$(getprop sys.boot_completed | tr -d '\r\n')" != "1" ]; do
        sleep 2
        count=$((count + 2))
        [ ${count} -ge 90 ] && break
    done
    log_info "Boot completed detected (sys.boot_completed=1)."
}

# ── Mount the SD partition (Smart Multi-FS: blkid detection + NTFS support) ───
mount_sd() {
    local attempts=0
    while [ "${attempts}" -lt 5 ]; do
        if mx_is_mounted "${SD_BASE}"; then
            mx_make_rprivate "${SD_BASE}"
            if grep -E " ${SD_BASE} f2fs " /proc/mounts 2>/dev/null | grep -qE "(^|,)discard(,|$)|fsync_mode=posix"; then
                mount -o remount,nodiscard,fsync_mode=nobarrier "${SD_BASE}" 2>/dev/null || \
                mount -o remount,nodiscard "${SD_BASE}" 2>/dev/null || true
            fi
            log_info "SD already mounted at ${SD_BASE}."
            return 0
        fi

        if [ -z "${SD_BLOCK}" ] || [ ! -b "${SD_BLOCK}" ] || _is_internal_system_disk "${SD_BLOCK}"; then
            # 1. Dynamic resolution by UUID from mountpoints.conf (scoped strictly to SD_BASE)
            local dev_by_uuid
            dev_by_uuid=$(_resolve_sd_base_dev_by_uuid)
            if [ -n "${dev_by_uuid}" ]; then
                SD_BLOCK="${dev_by_uuid}"
            fi

            # 2. Safe universal external block discovery (never touches internal eMMC/UFS/dm-slaves)
            if [ -z "${SD_BLOCK}" ] || [ ! -b "${SD_BLOCK}" ] || _is_internal_system_disk "${SD_BLOCK}"; then
                local _ext_cand _ext_fs
                SD_BLOCK=""
                for _ext_cand in /dev/block/mmcblk*p* /dev/block/sd[a-z][0-9]* /dev/block/nvme*p*; do
                    [ ! -b "${_ext_cand}" ] && continue
                    _is_internal_system_disk "${_ext_cand}" && continue
                    _ext_fs=$(blkid "${_ext_cand}" 2>/dev/null | sed -n 's/.*TYPE="\([^"]*\)".*/\1/p')
                    case "${_ext_fs}" in
                        f2fs|ext4)
                            SD_BLOCK="${_ext_cand}"
                            break
                            ;;
                    esac
                done
            fi
        fi

        if [ -b "${SD_BLOCK}" ]; then
            mkdir -p "${SD_BASE}"

            # Auto-detect filesystem via blkid if FS_TYPE is 'auto' or unset
            local detected_fs="${FS_TYPE}"
            if [ -z "${detected_fs}" ] || [ "${detected_fs}" = "auto" ]; then
                detected_fs=$(blkid "${SD_BLOCK}" 2>/dev/null | sed -n 's/.*TYPE="\([^"]*\)".*/\1/p')
                [ -z "${detected_fs}" ] && detected_fs=$(toybox blkid "${SD_BLOCK}" 2>/dev/null | sed -n 's/.*TYPE="\([^"]*\)".*/\1/p')
                if [ -n "${detected_fs}" ]; then
                    log_info "blkid auto-detected filesystem on ${SD_BLOCK}: ${detected_fs}"
                else
                    detected_fs="auto"
                    log_warn "blkid could not detect filesystem on ${SD_BLOCK}; will use kernel auto-detect."
                fi
            fi

            # Determine application-friendly SELinux mount context (bypasses 'unlabeled' denial)
            local sd_ctx="context=u:object_r:sdcardfs:s0"
            if ! grep -q sdcardfs /proc/filesystems 2>/dev/null; then
                sd_ctx="context=u:object_r:fuse:s0"
            fi

            # Build optimized mount options per filesystem
            local mnt_opts="rw,noatime,nodiratime"
            case "${detected_fs}" in
                f2fs)  mnt_opts="${mnt_opts},${sd_ctx},nodiscard,inline_data,inline_dentry,flush_merge,mode=adaptive,fsync_mode=nobarrier" ;;
                ext4)  mnt_opts="${mnt_opts},${sd_ctx},commit=60,delalloc,data=writeback" ;;
                ntfs)  mnt_opts="rw,noatime,nodiratime,uid=1023,gid=1023,fmask=0000,dmask=0000" ;;
                exfat) mnt_opts="rw,noatime,nodiratime,uid=1023,gid=1023,fmask=0000,dmask=0000,iocharset=utf8" ;;
                vfat|fat32) mnt_opts="rw,noatime,nodiratime,uid=1023,gid=1023,fmask=0000,dmask=0000" ;;
            esac

            # Primary mount attempt (filesystem-specific)
            local mounted=0
            if [ "${detected_fs}" = "ntfs" ]; then
                # NTFS: High-speed in-kernel Paragon driver (GKI 5.15+ / 6.1+) → mount.ntfs → ntfs-3g → kernel ntfs
                if grep -q "ntfs3" /proc/filesystems 2>/dev/null && mount -t ntfs3 -o iocharset=utf8,noatime,prealloc "${SD_BLOCK}" "${SD_BASE}" 2>/dev/null; then
                    mounted=1; log_info "SD mounted via in-kernel ntfs3: ${SD_BLOCK} → ${SD_BASE}"
                elif mount.ntfs -o "${mnt_opts}" "${SD_BLOCK}" "${SD_BASE}" 2>/dev/null; then
                    mounted=1; log_info "SD mounted via mount.ntfs: ${SD_BLOCK} → ${SD_BASE}"
                elif mount -t ntfs-3g -o "${mnt_opts}" "${SD_BLOCK}" "${SD_BASE}" 2>/dev/null; then
                    mounted=1; log_info "SD mounted via ntfs-3g: ${SD_BLOCK} → ${SD_BASE}"
                elif mount -t ntfs -o rw,noatime "${SD_BLOCK}" "${SD_BASE}" 2>/dev/null; then
                    mounted=1; log_info "SD mounted via kernel ntfs: ${SD_BLOCK} → ${SD_BASE}"
                fi
            elif [ "${detected_fs}" != "auto" ]; then
                if mount -t "${detected_fs}" -o "${mnt_opts}" "${SD_BLOCK}" "${SD_BASE}" 2>/dev/null; then
                    mounted=1; log_info "SD mounted: ${SD_BLOCK} → ${SD_BASE} (${detected_fs})"
                elif [ "${detected_fs}" = "f2fs" ] && mount -t f2fs -o "rw,noatime,nodiratime,${sd_ctx},nodiscard,inline_data,inline_dentry,flush_merge,mode=adaptive" "${SD_BLOCK}" "${SD_BASE}" 2>/dev/null; then
                    mounted=1; log_info "SD mounted (f2fs nodiscard fallback): ${SD_BLOCK} → ${SD_BASE}"
                elif mount -t "${detected_fs}" -o rw,noatime "${SD_BLOCK}" "${SD_BASE}" 2>/dev/null; then
                    mounted=1; log_info "SD mounted (simple opts): ${SD_BLOCK} → ${SD_BASE} (${detected_fs})"
                elif [ "${detected_fs}" = "f2fs" ] || [ "${detected_fs}" = "ext4" ]; then
                    local _ro_hw_sys
                    _ro_hw_sys=$(cat "/sys/block/$(_parent_disk_name "${SD_BLOCK}")/ro" 2>/dev/null | tr -d '\r\n')
                    if [ "${_ro_hw_sys}" != "1" ] && {
                        mount -t "${detected_fs}" -o "ro,noatime,nodiratime,${sd_ctx}" "${SD_BLOCK}" "${SD_BASE}" 2>/dev/null ||
                        mount -t "${detected_fs}" -o ro,noatime "${SD_BLOCK}" "${SD_BASE}" 2>/dev/null
                    }; then
                        if mount -o remount,rw "${SD_BASE}" 2>/dev/null && _probe_sd_io_healthy "${SD_BLOCK}" "${SD_BASE}" 1; then
                            mounted=1; log_info "SD mounted via RO->RW superblock promotion: ${SD_BLOCK} → ${SD_BASE} (${detected_fs})"
                        else
                            umount -l "${SD_BASE}" 2>/dev/null || true
                        fi
                    fi
                fi
            fi

            # Comprehensive multi-filesystem fallback chain
            if [ ${mounted} -eq 0 ]; then
                log_warn "Primary mount failed for ${detected_fs}; trying multi-fs fallback chain..."
                for alt_fs in ext4 f2fs exfat vfat; do
                    if [ "${alt_fs}" != "${detected_fs}" ]; then
                        if mount -t "${alt_fs}" -o rw,noatime "${SD_BLOCK}" "${SD_BASE}" 2>/dev/null; then
                            mounted=1
                            log_info "SD mounted via fallback fs: ${SD_BLOCK} → ${SD_BASE} (${alt_fs})"
                            break
                        fi
                    fi
                done
                # Try NTFS via mount.ntfs as part of fallback if not already tried
                if [ ${mounted} -eq 0 ] && [ "${detected_fs}" != "ntfs" ]; then
                    if mount.ntfs -o rw,noatime "${SD_BLOCK}" "${SD_BASE}" 2>/dev/null || \
                       mount -t ntfs-3g -o rw,noatime "${SD_BLOCK}" "${SD_BASE}" 2>/dev/null; then
                        mounted=1
                        log_info "SD mounted via NTFS fallback: ${SD_BLOCK} → ${SD_BASE}"
                    fi
                fi
                # Last resort: kernel auto-detect
                if [ ${mounted} -eq 0 ]; then
                    if mount "${SD_BLOCK}" "${SD_BASE}" 2>/dev/null; then
                        mounted=1
                        log_info "SD mounted via kernel auto-detect: ${SD_BLOCK} → ${SD_BASE}"
                    fi
                fi
            fi

            if [ ${mounted} -eq 1 ]; then
                mx_make_rprivate "${SD_BASE}"
                return 0
            fi
        fi

        attempts=$((attempts + 1))
        log_warn "Waiting for storage block device ${SD_BLOCK} (attempt ${attempts}/5)..."
        sleep 2
    done

    if mx_is_mounted "${SD_BASE}"; then
        mx_make_rprivate "${SD_BASE}"
        log_info "Target SD_BASE ${SD_BASE} mounted by system/vold."
        return 0
    fi

    log_error "Failed to mount SD after 5 attempts. Continuing without terminating service to preserve system state."
    return 1
}

# ── Apply kernel I/O queue & latency tweaks ───────────────────────────────────
apply_io_tweaks() {
    if [ "${IO_TWEAKS_ENABLED}" != "1" ]; then
        log_info "I/O tweaks disabled in config."
        return 0
    fi
    if [ -z "${SD_BLOCK}" ]; then
        return 0
    fi

    local disk_name
    disk_name=$(_parent_disk_name "${SD_BLOCK}")
    local queue_dir="/sys/block/${disk_name}/queue"

    log_info "Applying I/O tweaks on ${disk_name} (RA=${READ_AHEAD_KB}KB, Sched=${IO_SCHEDULER}, Affinity=${RQ_AFFINITY})…"

    if [ -d "${queue_dir}" ]; then
        [ -w "${queue_dir}/read_ahead_kb" ] && echo "${READ_AHEAD_KB}" > "${queue_dir}/read_ahead_kb"
        if [ -n "${IO_SCHEDULER}" ] && [ -w "${queue_dir}/scheduler" ]; then
            if grep -qw "${IO_SCHEDULER}" "${queue_dir}/scheduler" 2>/dev/null; then
                echo "${IO_SCHEDULER}" > "${queue_dir}/scheduler" 2>/dev/null
            elif [ "${IO_SCHEDULER}" = "deadline" ] && grep -qw "mq-deadline" "${queue_dir}/scheduler" 2>/dev/null; then
                echo "mq-deadline" > "${queue_dir}/scheduler" 2>/dev/null
            else
                log_warn "Scheduler '${IO_SCHEDULER}' not supported by ${disk_name}. Keeping kernel default."
            fi
        fi
        [ -w "${queue_dir}/rq_affinity" ] && echo "${RQ_AFFINITY}" > "${queue_dir}/rq_affinity"
        [ -w "${queue_dir}/nr_requests" ] && echo "${NR_REQUESTS}" > "${queue_dir}/nr_requests"
        if [ -w "${queue_dir}/max_sectors_kb" ] && [ -r "${queue_dir}/max_hw_sectors_kb" ]; then
            local _hw_max _tgt_max
            _hw_max=$(cat "${queue_dir}/max_hw_sectors_kb" 2>/dev/null | tr -d '\r\n')
            if [ -n "${_hw_max}" ] && [ "${_hw_max}" -gt 0 ] 2>/dev/null; then
                _tgt_max=1024
                [ "${_hw_max}" -lt "${_tgt_max}" ] && _tgt_max="${_hw_max}"
                echo "${_tgt_max}" > "${queue_dir}/max_sectors_kb" 2>/dev/null
            fi
        fi
        [ -w "${queue_dir}/add_random" ] && echo "0" > "${queue_dir}/add_random"
        [ -w "${queue_dir}/rotational" ] && echo "0" > "${queue_dir}/rotational"
        [ -w "${queue_dir}/nomerges" ] && echo "0" > "${queue_dir}/nomerges"
    fi

    # Target specific block device BDI to prevent zram swap memory bloat
    if [ -w "/sys/class/block/${disk_name}/bdi/read_ahead_kb" ]; then
        echo "${READ_AHEAD_KB}" > "/sys/class/block/${disk_name}/bdi/read_ahead_kb" 2>/dev/null
    fi

    if [ -w "/proc/sys/vm/vfs_cache_pressure" ]; then
        echo "${VFS_CACHE_PRESSURE}" > /proc/sys/vm/vfs_cache_pressure
    fi

    log_info "I/O tweaks successfully applied to ${disk_name}."
}

# ── Unmount any stale bind-mounts for a target path or partition ──────────────
umount_stale() {
    local target="$1"
    if mx_is_mounted "${target}"; then
        if umount -l "${target}" 2>/dev/null; then
            log_info "  Unmounted stale bind-mount: ${target}"
        fi
    fi
}

cleanup_stale_mounts() {
    log_info "Cleaning up any stale bind-mounts for registered MountX targets…"
    mx_repair_dead_fuse_and_parents
    [ ! -f "${MOUNTPOINTS_FILE}" ] && return 0

    while IFS='|' read -r pkg cat src dst preserve_media disk_uuid user_id || [ -n "${pkg}" ]; do
        pkg=$(echo "${pkg}" | tr -d '\r\n')
        case "${pkg}" in ''|\#*) continue ;; esac
        [ -z "${pkg}" ] && continue

        # Only clean mounts specifically belonging to this registered game package
        # NEVER unmount root system mounts (/storage/emulated/0/Android/data or /mnt/...)
        for mnt in $(grep -E "/(data|obb|media)/${pkg}" /proc/mounts 2>/dev/null | awk '{print $2}' | sort -u); do
            case "${mnt}" in
                */Android/data/${pkg}|*/Android/obb/${pkg}|*/Android/media/${pkg})
                    # If backing source does not exist or SD partition is unmounted, clean up
                    if [ ! -d "${src}" ] || ! mx_is_mounted "${SD_BASE}"; then
                        log_info "  Unmounting stale mount: ${mnt}"
                        mx_umount_safe "${mnt}"
                    fi
                    ;;
            esac
        done
    done < "${MOUNTPOINTS_FILE}"
}

# ── Dynamic UID/GID, Sandbox & SELinux Context Application ────────────────────
apply_permissions() {
    local pkg="$1"
    local src_dir="$2"
    local user_id="$3"
    [ -z "${user_id}" ] && user_id="0"

    local app_uid
    if [ "${user_id}" = "0" ]; then
        app_uid=$(stat -c '%u' "/data/data/${pkg}" 2>/dev/null || stat -c '%u' "/data/user/0/${pkg}" 2>/dev/null)
    else
        app_uid=$(stat -c '%u' "/data/user/${user_id}/${pkg}" 2>/dev/null)
    fi

    # Fallback via pm list packages
    if [ -z "${app_uid}" ] || [ "${app_uid}" = "0" ]; then
        app_uid=$(pm list packages -U --user "${user_id}" 2>/dev/null | grep -F "package:${pkg}" | sed -n 's/.*uid:\([0-9]*\).*/\1/p' | head -n 1)
    fi
    [ -z "${app_uid}" ] && app_uid=10000

    local sandbox_dir="/data/user/${user_id}/${pkg}"
    [ ! -d "${sandbox_dir}" ] && sandbox_dir="/data/data/${pkg}"

    # Sandbox integrity: fast non-recursive check (avoids boot freeze)
    # Strictly maintain canonical 0700 sandbox isolation to prevent RASP anti-tamper crashes
    if [ -d "${sandbox_dir}" ]; then
        local cur_sb_u
        cur_sb_u=$(stat -c '%u' "${sandbox_dir}" 2>/dev/null)
        if [ "${cur_sb_u}" != "${app_uid}" ]; then
            chown "${app_uid}:${app_uid}" "${sandbox_dir}" 2>/dev/null
        fi
        chmod 700 "${sandbox_dir}" 2>/dev/null
        [ -d "${sandbox_dir}/databases" ] && chmod 700 "${sandbox_dir}/databases" 2>/dev/null
    fi

    # APatch stealth isolation: automatically add app to APatch exclusion list so RASP/anti-tamper engines never detect root hooks
    if [ -f /data/adb/ap/package_config ]; then
        if ! grep -q "^${pkg}," /data/adb/ap/package_config 2>/dev/null; then
            echo "${pkg},1,0,${app_uid},0," >> /data/adb/ap/package_config 2>/dev/null
        fi
    fi

    # MicroSD target permissions: app_uid, GID 1023 (media_rw), mode 777, SELinux media_rw_data_file
    # Fast non-recursive check: avoids 5+ minute I/O stall over tens of thousands of game asset files
    if [ -d "${src_dir}" ]; then
        local cur_u cur_g
        cur_u=$(stat -c '%u' "${src_dir}" 2>/dev/null)
        cur_g=$(stat -c '%g' "${src_dir}" 2>/dev/null)
        if [ "${cur_u}" != "${app_uid}" ] || [ "${cur_g}" != "1023" ]; then
            chown "${app_uid}:1023" "${src_dir}" 2>/dev/null
        fi
        chmod 777 "${src_dir}" 2>/dev/null
        chcon u:object_r:media_rw_data_file:s0 "${src_dir}" 2>/dev/null
    fi

    log_info "  [${pkg}] Permissions verified: UID=${app_uid}, GID=1023, Mode=777 on ${src_dir}"
}

# ── Bind-mount into all active Android runtime namespaces (FUSE/Scoped Parity) ──
# ── Cleanly purge buried/stacked mounts before re-binding ────────────────────
unmount_target_everywhere() {
    local dst="$1"
    local rel="$2"
    local user_id="$3"
    [ -z "${user_id}" ] && user_id="0"

    _umount_loop() {
        local _p="${1%/}"
        [ -z "${_p}" ] && return 0
        local _tries=0
        while mx_is_mounted_exact "${_p}" && [ ${_tries} -lt 16 ]; do
            mx_umount_safe "${_p}" || break
            _tries=$((_tries + 1))
        done
    }

    if [ -n "${rel}" ]; then
        for R in \
            "/storage/emulated/${user_id}" \
            "/mnt/user/${user_id}/emulated/${user_id}" \
            "/mnt/user/${user_id}/primary" \
            "/mnt/pass_through/${user_id}/emulated/${user_id}" \
            "/mnt/androidwritable/${user_id}/emulated/${user_id}" \
            "/mnt/installer/${user_id}/emulated/${user_id}" \
            "/mnt/runtime/full/emulated/${user_id}" \
            "/mnt/runtime/write/emulated/${user_id}" \
            "/mnt/runtime/read/emulated/${user_id}" \
            "/mnt/runtime/default/emulated/${user_id}" \
            "/data/media/${user_id}"
        do
            _umount_loop "${R}/${rel}"
        done
        for _zpid in $(_mx_zygote_mnt_pids); do
            MX_UID="${user_id}" MX_REL="${rel}" nsenter --mount=/proc/${_zpid}/ns/mnt -- sh -c '
                for _zr in "/storage/emulated/${MX_UID}" "/mnt/user/${MX_UID}/emulated/${MX_UID}" "/mnt/installer/${MX_UID}/emulated/${MX_UID}" "/mnt/androidwritable/${MX_UID}/emulated/${MX_UID}" "/mnt/runtime/default/emulated/${MX_UID}" "/mnt/runtime/read/emulated/${MX_UID}" "/mnt/runtime/write/emulated/${MX_UID}" "/mnt/runtime/full/emulated/${MX_UID}"; do
                    _i=0
                    _zt="${_zr}/${MX_REL}"
                    while awk -v t="${_zt}" '"'"'{m=$5; gsub(/\\040/, " ", m); if (m==t) {f=1; exit}} END {exit (f?0:1)}'"'"' /proc/self/mountinfo 2>/dev/null && [ ${_i} -lt 16 ]; do
                        umount "${_zt}" 2>/dev/null || umount -l "${_zt}" 2>/dev/null || break
                        _i=$((_i + 1))
                    done
                done
            ' 2>/dev/null
        done
    else
        # Clean peer mounts for internal sandbox paths (/data/user/<uid>/<pkg>, /data/data/<pkg>, /data_mirror/...)
        case "${dst}" in
            /data/user/[0-9]*/*|/data/data/*)
                local _sub_after=""
                case "${dst}" in
                    /data/user/[0-9]*/*) _sub_after="${dst#/data/user/[0-9]*/}" ;;
                    /data/data/*) _sub_after="${dst#/data/data/}" ;;
                esac
                if [ -n "${_sub_after}" ]; then
                    _umount_loop "/data_mirror/data_ce/null/${user_id}/${_sub_after}"
                    _umount_loop "/data/data/${_sub_after}"
                    _umount_loop "/data/user/${user_id}/${_sub_after}"
                    for zpid in $(_mx_zygote_mnt_pids); do
                        [ ! -e "/proc/${zpid}/ns/mnt" ] && continue
                        MX_UID="${user_id}" MX_SUB="${_sub_after}" nsenter --mount="/proc/${zpid}/ns/mnt" -- /system/bin/sh -c '
                            for _zp in "/data_mirror/data_ce/null/${MX_UID}/${MX_SUB}" "/data/data/${MX_SUB}" "/data/user/${MX_UID}/${MX_SUB}"; do
                                _i=0
                                while awk -v t="${_zp}" '"'"'{m=$5; gsub(/\\040/, " ", m); if (m==t) {f=1; exit}} END {exit (f?0:1)}'"'"' /proc/self/mountinfo 2>/dev/null && [ ${_i} -lt 16 ]; do
                                    umount "${_zp}" 2>/dev/null || umount -l "${_zp}" 2>/dev/null || break
                                    _i=$((_i + 1))
                                done
                            done
                        ' 2>/dev/null
                    done
                fi
                ;;
            /data/app/*)
                # Always peel writable sub-mounts (oat, lib) before unmounting the read-only APK root
                local _sub_p _abi_p
                _umount_loop "${dst}/oat"
                for _abi_p in "${dst}"/lib/*; do
                    [ -e "${_abi_p}" ] && _umount_loop "${_abi_p}"
                done
                _umount_loop "${dst}/lib"
                for zpid in $(_mx_zygote_mnt_pids); do
                    [ ! -e "/proc/${zpid}/ns/mnt" ] && continue
                    MX_DST="${dst}" nsenter --mount="/proc/${zpid}/ns/mnt" -- /system/bin/sh -c '
                        for _zt in "${MX_DST}/oat" "${MX_DST}"/lib/* "${MX_DST}/lib" "${MX_DST}"; do
                            [ -n "${_zt}" ] || continue
                            _i=0
                            while awk -v t="${_zt}" '"'"'{m=$5; gsub(/\\040/, " ", m); if (m==t) {f=1; exit}} END {exit (f?0:1)}'"'"' /proc/self/mountinfo 2>/dev/null && [ ${_i} -lt 16 ]; do
                                umount "${_zt}" 2>/dev/null || umount -l "${_zt}" 2>/dev/null || break
                                _i=$((_i + 1))
                            done
                        done
                    ' 2>/dev/null
                done
                ;;
        esac
    fi
    [ -n "${dst}" ] && _umount_loop "${dst}"
}

bind_mount_to_runtime_namespaces() {
    local src="$1"
    local dst="$2"
    local pkg="$3"
    local user_id="$4"
    [ -z "${user_id}" ] && user_id="0"

    case "${dst}" in
        /storage/emulated/*|/data/media/*|/sdcard/*|*/Android/*)
            ;;
        *)
            # Non-emulated system destination (e.g. /data/app, /data/user): mount directly to dst only!
            mkdir -p "${dst}" 2>/dev/null
            mount -o bind "${src}" "${dst}" 2>/dev/null
            mx_make_rprivate "${dst}"
            return 0
            ;;
    esac

    # Fast non-blocking UID lookup
    local app_uid=""
    if [ -n "${pkg}" ]; then
        if [ -d "/data/data/${pkg}" ]; then
            app_uid=$(stat -c %u "/data/data/${pkg}" 2>/dev/null)
        elif [ -d "/data/user/${user_id}/${pkg}" ]; then
            app_uid=$(stat -c %u "/data/user/${user_id}/${pkg}" 2>/dev/null)
        fi
    fi
    [ -z "${app_uid}" ] && app_uid=10000

    # Extract relative path from dst across any user profile
    local rel=""
    case "${dst}" in
        */Android/data/*|*/Android/obb/*|*/Android/media/*)
            rel="Android/"$(echo "${dst}" | sed 's|.*/Android/||')
            ;;
        /sdcard/*|/storage/emulated/*|/data/media/*|/mnt/user/*)
            rel=$(echo "${dst}" | sed 's|^/sdcard/||;s|^/storage/emulated/[0-9]*/||;s|^/data/media/[0-9]*/||;s|^/mnt/user/[0-9]*/primary/||;s|^/mnt/user/[0-9]*/emulated/[0-9]*/||')
            ;;
        *)
            rel=""
            ;;
    esac

    local is_non_posix=0
    local fs_t
    fs_t=$(stat -f -c %T "${src}" 2>/dev/null)
    case "${fs_t}" in
        exfat|vfat|msdos|ntfs) is_non_posix=1 ;;
    esac
    case "${src}" in
        /mnt/media_rw/*) is_non_posix=1 ;;
    esac

    do_mount() {
        local _s="$1"
        local _t="$2"
        if [ "${is_non_posix}" -eq 1 ]; then
            toybox mount -t sdcardfs -o fsuid=1023,fsgid=1023,gid="${app_uid}",mask=0 "${_s}" "${_t}" 2>/dev/null || mount -o bind "${_s}" "${_t}" 2>/dev/null
        else
            mount -o bind "${_s}" "${_t}" 2>/dev/null
        fi
        mx_make_rprivate "${_t}"
    }

    if [ -n "${rel}" ]; then
        local _clean_rel="${rel#/}"
        _clean_rel="${_clean_rel%/}"
        case "${_clean_rel}" in
            ""|Android|Android/data|Android/obb|Android/media)
                log_error "  [${pkg}] Refusing unsafe top-level relative bind path: '${_clean_rel}'"
                return 1
                ;;
        esac

        # Clean preexisting /data/media mount point ONLY when kernel sdcardfs is active (prevents EXDEV without flickering FUSE lower FS)
        if grep -qw "sdcardfs" /proc/filesystems 2>/dev/null && mx_is_mounted_exact "/data/media/${user_id}/${rel}"; then
            mx_umount_safe "/data/media/${user_id}/${rel}"
        fi

        local _mi="/proc/1/mountinfo"
        [ ! -r "${_mi}" ] && _mi="/proc/self/mountinfo"
        for R in \
            "/mnt/runtime/default/emulated/${user_id}" \
            "/mnt/runtime/read/emulated/${user_id}" \
            "/mnt/runtime/write/emulated/${user_id}" \
            "/mnt/runtime/full/emulated/${user_id}" \
            "/mnt/installer/${user_id}/emulated/${user_id}" \
            "/mnt/androidwritable/${user_id}/emulated/${user_id}" \
            "/mnt/pass_through/${user_id}/emulated/${user_id}" \
            "/mnt/user/${user_id}/primary" \
            "/mnt/user/${user_id}/emulated/${user_id}" \
            "/storage/emulated/${user_id}"
        do
            if mx_root_is_live_storage "${R}"; then
                TARGET="${R}/${rel}"
                local _tpar="${TARGET%/*}"
                if [ -d "${_tpar}" ]; then
                    local _pst
                    _pst=$(LC_ALL=C stat -t "${_tpar}" 2>&1)
                    case "${_pst}" in *"Transport endpoint"*)
                        log_warn "  [${pkg}] Parent ${_tpar} returned ENOTCONN; triggering FUSE repair..."
                        mx_repair_dead_fuse_and_parents
                        continue
                        ;;
                    esac
                    if [ -r "${_mi}" ] && P="${_tpar}" awk -F ' - ' '{split($1,L," ");m=L[5];gsub(/\\040/," ",m);if(m==ENVIRON["P"] && L[4]~/(^|\/)MountX(\/|$)/){f=1;exit}} END{exit !f}' "${_mi}" 2>/dev/null; then
                        log_warn "  [${pkg}] Parent ${_tpar} occluded by MountX mount; repairing..."
                        mx_repair_dead_fuse_and_parents
                        continue
                    fi
                fi
                if ! mx_is_mounted_exact "${TARGET}"; then
                    mkdir -p "${TARGET}" 2>/dev/null
                    do_mount "${src}" "${TARGET}"
                fi
            fi
        done

        # Inject into active Zygote/USAP namespaces only where parent storage root is live non-tmpfs and healthy
        # Single-pass awk over /proc/self/mountinfo per Zygote PID eliminates 15 redundant awk forks while
        # preserving identical tmpfs, ENOTCONN, mkdir, bind-mount, and rprivate safety guarantees.
        local _bb_bin
        _bb_bin=$(_mx_busybox)
        for _zpid in $(_mx_zygote_mnt_pids); do
            [ -d "/proc/${_zpid}/ns" ] || continue
            MX_UID="${user_id}" MX_REL="${rel}" MX_SRC="${src}" MX_BB="${_bb_bin}" nsenter --mount="/proc/${_zpid}/ns/mnt" -- sh -s <<'__MX_SH_Z__' 2>/dev/null
            _mi="/proc/self/mountinfo"
            _candidates=$(awk -F ' - ' -v uid="${MX_UID}" -v rel="${MX_REL}" '
            BEGIN {
                n = 0
                roots[++n] = "/storage/emulated/" uid
                roots[++n] = "/mnt/user/" uid "/emulated/" uid
                roots[++n] = "/mnt/installer/" uid "/emulated/" uid
                roots[++n] = "/mnt/androidwritable/" uid "/emulated/" uid
                roots[++n] = "/mnt/runtime/default/emulated/" uid
                roots[++n] = "/mnt/runtime/read/emulated/" uid
                roots[++n] = "/mnt/runtime/write/emulated/" uid
                roots[++n] = "/mnt/runtime/full/emulated/" uid
                for (i = 1; i <= n; i++) {
                    r = roots[i]
                    p = r; sub(/\/[^\/]+$/, "", p)
                    par[i] = p
                    tgt[i] = r "/" rel
                }
            }
            {
                split($1, L, " "); split($2, RT, " ")
                m = L[5]; gsub(/\\040/, " ", m)
                fst[m] = RT[1]
                mounted[m] = 1
            }
            END {
                for (i = 1; i <= n; i++) {
                    r = roots[i]; p = par[i]; t = tgt[i]
                    f = (r in fst) ? fst[r] : ((p in fst) ? fst[p] : "")
                    if (f == "" || f == "tmpfs") continue
                    if (t in mounted) continue
                    print r
                }
            }' "${_mi}" 2>/dev/null)
            for _zr in ${_candidates}; do
                [ -d "${_zr}" ] && [ ! -L "${_zr}" ] || continue
                _zst=$(LC_ALL=C stat -t "${_zr}" 2>&1)
                case "${_zst}" in *"Transport endpoint"*) continue ;; esac
                _zt="${_zr}/${MX_REL}"
                _zpar="${_zt%/*}"
                if [ -d "${_zpar}" ]; then
                    _zpst=$(LC_ALL=C stat -t "${_zpar}" 2>&1)
                    case "${_zpst}" in *"Transport endpoint"*) continue ;; esac
                fi
                mkdir -p "${_zt}" 2>/dev/null && mount -o bind "${MX_SRC}" "${_zt}" 2>/dev/null
                if [ -n "${MX_BB}" ]; then
                    "${MX_BB}" mount --make-rprivate "${_zt}" 2>/dev/null
                else
                    mount --make-rprivate "${_zt}" 2>/dev/null
                fi
            done
__MX_SH_Z__
        done

        # Auto-heal: delete stale verify.fail or partial block download markers from both internal storage and source
        if [ -f "/data/media/${user_id}/${rel}/files/verify.fail" ]; then
            rm -f "/data/media/${user_id}/${rel}/files/verify.fail" 2>/dev/null
        fi
        if [ -f "${src}/files/verify.fail" ]; then
            rm -f "${src}/files/verify.fail" 2>/dev/null
        fi
        rm -f "/data/media/${user_id}/${rel}/files/Asb/Android/"*_tmp.block "${src}/files/Asb/Android/"*_tmp.block 2>/dev/null
    else
        if ! mx_is_mounted "${dst}"; then
            mkdir -p "${dst}" 2>/dev/null
            do_mount "${src}" "${dst}"
        fi
    fi

    local count=0
    local _mi="/proc/1/mountinfo"
    [ ! -r "${_mi}" ] && _mi="/proc/self/mountinfo"
    if [ -n "${rel}" ]; then
        count=$(grep -c "${rel}" "${_mi}" 2>/dev/null || echo 0)
    else
        count=$(grep -c " ${dst} " "${_mi}" 2>/dev/null || echo 0)
    fi
    log_info "  [${pkg}] Bind-mounted to ${count} targets: ${src} → ${dst}"
}

# ── Smart Multi-Disk Mounter (resolves block device by UUID and mounts it) ────
# Usage: mount_disk_by_uuid <uuid> <mount_point>
# Returns 0 on success, 1 on failure
mount_disk_by_uuid() {
    local uuid="$1"
    local mnt_point="$2"

    if [ -z "${uuid}" ] || [ -z "${mnt_point}" ]; then
        return 1
    fi

    # Already mounted at that point?
    if mx_is_mounted "${mnt_point}"; then
        mx_make_rprivate "${mnt_point}"
        log_info "  [UUID:${uuid}] Disk already mounted at ${mnt_point}."
        return 0
    fi

    # Resolve block device from UUID via blkid
    local blk_dev
    blk_dev=$(blkid 2>/dev/null | grep -i "UUID=\"${uuid}\"" | cut -d: -f1 | head -n 1)
    [ -z "${blk_dev}" ] && blk_dev=$(toybox blkid 2>/dev/null | grep -i "UUID=\"${uuid}\"" | cut -d: -f1 | head -n 1)

    if [ -z "${blk_dev}" ]; then
        log_warn "  [UUID:${uuid}] Cannot resolve block device; disk may not be connected."
        return 1
    fi

    if ! [ -b "${blk_dev}" ]; then
        log_warn "  [UUID:${uuid}] Resolved device ${blk_dev} is not a block device."
        return 1
    fi

    # Detect filesystem on this partition
    local fs_type
    fs_type=$(blkid "${blk_dev}" 2>/dev/null | sed -n 's/.*TYPE="\([^"]*\)".*/\1/p')

    log_info "  [UUID:${uuid}] Auto-mounting ${blk_dev} (fs=${fs_type:-unknown}) → ${mnt_point}"
    mkdir -p "${mnt_point}"

    local sd_ctx="context=u:object_r:sdcardfs:s0"
    if ! grep -q sdcardfs /proc/filesystems 2>/dev/null; then
        sd_ctx="context=u:object_r:fuse:s0"
    fi

    local mounted=0
    case "${fs_type}" in
        ntfs)
            mount.ntfs -o rw,noatime,nodiratime,uid=1023,gid=1023,fmask=0000,dmask=0000 "${blk_dev}" "${mnt_point}" 2>/dev/null && mounted=1
            [ ${mounted} -eq 0 ] && mount -t ntfs-3g -o rw,noatime,uid=1023,gid=1023,fmask=0000,dmask=0000 "${blk_dev}" "${mnt_point}" 2>/dev/null && mounted=1
            [ ${mounted} -eq 0 ] && mount -t ntfs -o rw,noatime,uid=1023,gid=1023,fmask=0000,dmask=0000 "${blk_dev}" "${mnt_point}" 2>/dev/null && mounted=1
            ;;
        f2fs)
            mount -t f2fs -o rw,noatime,nodiratime,${sd_ctx},nodiscard,inline_data,inline_dentry,flush_merge,mode=adaptive,fsync_mode=nobarrier "${blk_dev}" "${mnt_point}" 2>/dev/null || \
            mount -t f2fs -o rw,noatime,nodiratime,${sd_ctx},nodiscard,inline_data,inline_dentry "${blk_dev}" "${mnt_point}" 2>/dev/null || \
            mount -t f2fs -o rw,noatime "${blk_dev}" "${mnt_point}" 2>/dev/null && mounted=1
            ;;
        ext4)
            mount -t ext4 -o rw,noatime,nodiratime,${sd_ctx},commit=60,delalloc "${blk_dev}" "${mnt_point}" 2>/dev/null || mount -t ext4 -o rw,noatime "${blk_dev}" "${mnt_point}" 2>/dev/null && mounted=1
            ;;
        exfat)
            mount -t exfat -o rw,noatime,nodiratime,uid=1023,gid=1023,fmask=0000,dmask=0000,iocharset=utf8 "${blk_dev}" "${mnt_point}" 2>/dev/null && mounted=1
            [ ${mounted} -eq 0 ] && mount -t exfat -o rw,noatime,nodiratime "${blk_dev}" "${mnt_point}" 2>/dev/null && mounted=1
            ;;
        vfat|fat32)
            mount -t vfat -o rw,noatime,nodiratime,uid=1023,gid=1023,fmask=0000,dmask=0000 "${blk_dev}" "${mnt_point}" 2>/dev/null && mounted=1
            ;;
        *)
            # Try common filesystems in order for unknown/empty fs_type
            for fs in ext4 f2fs; do
                mount -t "${fs}" -o rw,noatime "${blk_dev}" "${mnt_point}" 2>/dev/null && mounted=1 && break
            done
            [ ${mounted} -eq 0 ] && mount -t exfat -o rw,noatime,nodiratime,uid=1023,gid=1023,fmask=0000,dmask=0000,iocharset=utf8 "${blk_dev}" "${mnt_point}" 2>/dev/null && mounted=1
            [ ${mounted} -eq 0 ] && mount.ntfs -o rw,noatime,uid=1023,gid=1023,fmask=0000,dmask=0000 "${blk_dev}" "${mnt_point}" 2>/dev/null && mounted=1
            [ ${mounted} -eq 0 ] && mount -t vfat -o rw,noatime,uid=1023,gid=1023,fmask=0000,dmask=0000 "${blk_dev}" "${mnt_point}" 2>/dev/null && mounted=1
            [ ${mounted} -eq 0 ] && mount "${blk_dev}" "${mnt_point}" 2>/dev/null && mounted=1
            ;;
    esac

    if [ ${mounted} -eq 1 ]; then
        mx_make_rprivate "${mnt_point}"
        log_info "  [UUID:${uuid}] Disk mounted successfully: ${blk_dev} → ${mnt_point} (${fs_type:-auto})"
        return 0
    else
        log_error "  [UUID:${uuid}] Failed to mount ${blk_dev} at ${mnt_point}."
        return 1
    fi
}

# ── Helpers for Framework & Package Status ────────────────────────────────────
wait_for_framework() {
    local max_wait=30
    local elapsed=0
    while [ ${elapsed} -lt ${max_wait} ]; do
        if service check activity 2>/dev/null | grep -q "found" && \
           service check package 2>/dev/null | grep -q "found"; then
            return 0
        fi
        sleep 1
        elapsed=$((elapsed + 1))
    done
    return 1
}

is_package_installed() {
    local pkg="$1"
    [ -z "${pkg}" ] && return 1

    # Check 1 (Authoritative): Package Manager Service when online
    if service check package 2>/dev/null | grep -q "found"; then
        local _pm_apk
        _pm_apk=$(pm path "${pkg}" 2>/dev/null | head -n 1 | sed -n 's/^package://p' | tr -d '\r')
        case "${_pm_apk}" in
            ""|*==deleted==*)
                return 1
                ;;
        esac
        if [ -f "${_pm_apk}" ] || \
           [ -f "${SD_BASE}/MountX/app/${pkg}/base.apk" ] || \
           [ -f "${SD_BASE}/Android/app/${pkg}/base.apk" ] || \
           [ -f "${SD_BASE}/.mountx/containers/${pkg}_apk.img" ] || \
           [ -f "${SD_BASE}/.mountx/apk_backup/${pkg}/base.apk" ]; then
            return 0
        fi
        return 1
    fi

    # Check 2 (Boot fallback while PMS offline): Require non-deleted APK dir OR intact SD APK source
    local _cand_apk
    for _cand_apk in /data/app/*/"${pkg}"-*/base.apk /data/app/"${pkg}"-*/base.apk /data/app/*/"${pkg}"/base.apk; do
        [ -f "${_cand_apk}" ] || continue
        case "${_cand_apk}" in
            *==deleted==*|*/vmdl*) continue ;;
        esac
        return 0
    done
    if [ -f "${SD_BASE}/MountX/app/${pkg}/base.apk" ] || [ -f "${SD_BASE}/.mountx/containers/${pkg}_apk.img" ]; then
        return 0
    fi
    return 1
}

# ── Modern Multi-Target Array (mountpoints.conf) ──────────────────────────────
load_mountpoints() {
    if [ ! -f "${MOUNTPOINTS_FILE}" ]; then
        return 1
    fi

    local entries=0
    local _failed_pkgs=" "
    local _pms_online=0
    service check package 2>/dev/null | grep -q "found" && _pms_online=1

    # Pre-flight multi-mountpoint check:
    # 1) Defer packages whose required disk UUID is detached (prevents OBB bind+unmount thrashing).
    # 2) Self-heal ghost/broken packages (dropped codePath / ==deleted==) BEFORE earlier PRIVATE/DATA rows are skipped.
    local _blkid_snap _healed_pkgs=" "
    _blkid_snap=$(blkid 2>/dev/null)
    while IFS='|' read -r _p_pkg _p_cat _p_src _p_dst _p_pres _p_uuid _p_uid || [ -n "${_p_pkg}" ]; do
        case "${_p_pkg}" in ''|\#*) continue ;; esac
        _p_pkg=$(echo "${_p_pkg}" | tr -d '\r')
        _p_cat=$(echo "${_p_cat}" | tr -d '\r')
        _p_src=$(echo "${_p_src}" | tr -d '\r')
        _p_dst=$(echo "${_p_dst}" | tr -d '\r')
        _p_uuid=$(echo "${_p_uuid}" | tr -d '\r')
        [ -z "${_p_pkg}" ] || [ -z "${_p_src}" ] && continue
        case "${_failed_pkgs}" in *" ${_p_pkg} "*) continue ;; esac
        if [ -n "${_p_uuid}" ] && [ ! -e "${_p_src}" ]; then
            if ! echo "${_blkid_snap}" | grep -qiF "${_p_uuid}"; then
                log_warn "  [${_p_pkg}] Pre-flight: required disk UUID=${_p_uuid} is not attached. Deferring all mounts for ${_p_pkg}."
                _failed_pkgs="${_failed_pkgs}${_p_pkg} "
                continue
            fi
        fi
        if [ "${_pms_online}" -eq 1 ] && [ "${_p_cat}" = "APP_PACKAGE" ] && ! echo "${_p_dst}" | grep -q "/lib"; then
            case "${_healed_pkgs}" in *" ${_p_pkg} "*) continue ;; esac
            _healed_pkgs="${_healed_pkgs}${_p_pkg} "
            local _live_check
            _live_check=$(pm path "${_p_pkg}" 2>/dev/null | head -n 1 | sed -n 's/^package://p' | tr -d '\r')
            if [ -z "${_live_check}" ] && [ -f "${_p_src}/base.apk" ] && [ -s "${_p_src}/base.apk" ]; then
                if [ -d "/data/user/0/${_p_pkg}" ] || ls -d /data/app/*/"${_p_pkg}"* >/dev/null 2>&1 || pm list packages -u 2>/dev/null | grep -qxF "package:${_p_pkg}"; then
                    log_info "  [${_p_pkg}] Pre-flight Self-Healing Reconciler: re-registering detached package from ${_p_src}..."
                    if _mx_pm_install_apk_dir "${_p_src}"; then
                        local _new_uid
                        _new_uid=$(id -u "${_p_pkg}" 2>/dev/null)
                        [ -z "${_new_uid}" ] && _new_uid=$(pm list packages -U 2>/dev/null | grep -F "package:${_p_pkg} " | sed -n 's/.*uid:\([0-9]*\).*/\1/p' | head -n 1)
                        if [ -n "${_new_uid}" ]; then
                            [ -d "${SD_BASE}/MountX/data/${_p_pkg}" ] && chown -R "${_new_uid}:${_new_uid}" "${SD_BASE}/MountX/data/${_p_pkg}" 2>/dev/null && rm -f "${SD_BASE}/MountX/data/${_p_pkg}/.mountx_perms_ok" 2>/dev/null
                            [ -d "${SD_BASE}/MountX/cache/${_p_pkg}" ] && chown -R "${_new_uid}:${_new_uid}" "${SD_BASE}/MountX/cache/${_p_pkg}" 2>/dev/null && rm -f "${SD_BASE}/MountX/cache/${_p_pkg}/.mountx_perms_ok" 2>/dev/null
                        fi
                        log_info "  [${_p_pkg}] Self-Healing Reconciler restored package registration cleanly."
                    else
                        log_warn "  [${_p_pkg}] Self-Healing Reconciler install failed; protecting SD data by skipping mounts."
                        _failed_pkgs="${_failed_pkgs}${_p_pkg} "
                    fi
                fi
            fi
        fi
    done < "${MOUNTPOINTS_FILE}"

    while IFS='|' read -r pkg cat src dst preserve_media disk_uuid user_id || [ -n "${pkg}" ]; do
        case "${pkg}" in ''|\#*) continue ;; esac

        pkg=$(echo "${pkg}" | tr -d '\r')
        cat=$(echo "${cat}" | tr -d '\r')
        src=$(echo "${src}" | tr -d '\r')
        dst=$(echo "${dst}" | tr -d '\r')
        preserve_media=$(echo "${preserve_media}" | tr -d '\r')
        disk_uuid=$(echo "${disk_uuid}" | tr -d '\r')
        user_id=$(echo "${user_id}" | tr -d '\r')
        [ -z "${user_id}" ] && user_id="0"

        if [ -z "${pkg}" ] || [ -z "${src}" ] || [ -z "${dst}" ]; then
            continue
        fi

        case "${_failed_pkgs}" in
            *" ${pkg} "*)
                log_warn "  [${pkg}] Skipping ${cat} (${dst}) because an earlier required mountpoint for ${pkg} is missing its disk/source."
                continue
                ;;
        esac

        # Verify package is actually installed on system (skip if uninstalled to avoid Play Store reinstall error)
        if [ -n "${pkg}" ] && [ "${cat}" != "CUSTOM" ]; then
            if ! is_package_installed "${pkg}"; then
                log_warn "  [${pkg}] App is not installed on system. Skipping mount to prevent Play Store INSTALL_FAILED_INTERNAL_ERROR."
                if mx_is_mounted "${dst}"; then
                    mx_umount_safe "${dst}"
                fi
                continue
            fi
        fi

        # Dynamic APK hash path auto-resolution for APP_PACKAGE across Play Store updates
        if [ "${cat}" = "APP_PACKAGE" ] && [ -n "${pkg}" ]; then
            live_apk_path=$(pm path "${pkg}" 2>/dev/null | head -n 1 | sed -n 's/^package://p' | tr -d '\r')
            if [ -n "${live_apk_path}" ]; then
                live_apk_dir=$(dirname "${live_apk_path}")
                if [ -n "${live_apk_dir}" ]; then
                    case "${dst}" in
                        */lib/*|*/lib)
                            lib_sub=$(echo "${dst}" | sed 's|.*/lib|lib|')
                            new_dst="${live_apk_dir}/${lib_sub}"
                            if [ "${new_dst}" != "${dst}" ]; then
                                log_warn "  [${pkg}] APP_PACKAGE (lib) relocated: ${dst} -> ${new_dst}"
                                if mx_is_mounted "${dst}"; then
                                    mx_umount_safe "${dst}"
                                fi
                                dst="${new_dst}"
                            fi
                            ;;
                        *)
                            if [ "${live_apk_dir}" != "${dst}" ]; then
                                log_warn "  [${pkg}] APP_PACKAGE relocated by system update: ${dst} -> ${live_apk_dir}"
                                if mx_is_mounted "${dst}"; then
                                    mx_umount_safe "${dst}"
                                fi
                                dst="${live_apk_dir}"
                            fi
                            ;;
                    esac
                fi
            fi
        fi

        # Extract relative path to inspect user-facing runtime namespace (ONLY for external storage!)
        local rel=""
        case "${dst}" in
            */Android/data/*|*/Android/obb/*|*/Android/media/*)
                rel="Android/"$(echo "${dst}" | sed 's|.*/Android/||')
                ;;
            /sdcard/*|/storage/emulated/*|/data/media/*|/mnt/user/*)
                rel=$(echo "${dst}" | sed 's|^/sdcard/||;s|^/storage/emulated/[0-9]*/||;s|^/data/media/[0-9]*/||;s|^/mnt/user/[0-9]*/primary/||;s|^/mnt/user/[0-9]*/emulated/[0-9]*/||')
                ;;
            *)
                rel=""
                ;;
        esac

        # Determine effective user-facing check target
        local check_target="${dst}"
        if [ -n "${rel}" ]; then
            if [ -d "/storage/emulated/${user_id}" ]; then
                check_target="/storage/emulated/${user_id}/${rel}"
            else
                check_target="/mnt/runtime/default/emulated/${user_id}/${rel}"
            fi

            # Clean any accidental /data/media mount on sdcardfs to prevent EXDEV
            if grep -qw "sdcardfs" /proc/filesystems 2>/dev/null; then
                if mx_is_mounted_exact "/data/media/${user_id}/${rel}"; then
                    log_warn "  [${pkg}] Detected stale /data/media mount on active sdcardfs kernel. Unmounting to prevent EXDEV..."
                    mx_umount_safe "/data/media/${user_id}/${rel}"
                fi
            fi
        fi

        # Resolve canary source (handles ABI subdir for APP_PACKAGE lib mounts)
        local _canary_src="${src}"
        if [ "${cat}" = "APP_PACKAGE" ]; then
            case "${dst}" in
                */lib/*)
                    local _c_abi="${dst##*/lib/}"
                    _c_abi="${_c_abi%%/*}"
                    if [ -n "${_c_abi}" ] && [ -d "${src}/${_c_abi}" ]; then
                        _canary_src="${src}/${_c_abi}"
                    fi
                    ;;
            esac
        fi

        # Ensure canary exists only if directory source is present and NOT a volatile cache dir
        if [ -d "${_canary_src}" ] && [ "${cat}" != "CACHE_SHADERS" ]; then
            touch "${_canary_src}/.mountx_canary" 2>/dev/null
            if [ "${cat}" = "APP_PACKAGE" ]; then
                chmod 644 "${_canary_src}/.mountx_canary" 2>/dev/null
                chown 1000:1000 "${_canary_src}/.mountx_canary" 2>/dev/null
            fi
        fi

        # ── Multi-Disk Collision, Anti-Stacking & Inode/Canary Anti-Shadowing Check ───
        local is_shadowed=0
        if [ -n "${rel}" ] && (mx_is_mounted_exact "/mnt/runtime/default/emulated/${user_id}/${rel}" || mx_is_mounted_exact "${check_target}"); then
            local _esid _etid
            _esid=$(stat -c '%d:%i' "${_canary_src}" 2>/dev/null)
            _etid=$(stat -c '%d:%i' "${check_target}" 2>/dev/null)
            if [ -n "${_esid}" ] && [ "${_esid}" = "${_etid}" ]; then
                :
            elif [ -f "${_canary_src}/.mountx_canary" ] && [ ! -f "${check_target}/.mountx_canary" ]; then
                log_warn "  [${pkg}] Target ${check_target} is SHADOWED by FUSE/sdcardfs overlay! Unmounting buried layers and re-binding..."
                is_shadowed=1
                unmount_target_everywhere "${dst}" "${rel}" "${user_id}"
            fi
        elif [ -z "${rel}" ] && mx_is_mounted_exact "${check_target}"; then
            case "${_canary_src}" in
                *.img) ;;
                *)
                    local _isid _itid
                    _isid=$(stat -c '%d:%i' "${_canary_src}" 2>/dev/null)
                    _itid=$(stat -c '%d:%i' "${check_target}" 2>/dev/null)
                    if [ -n "${_isid}" ] && [ -n "${_itid}" ] && [ "${_isid}" != "${_itid}" ]; then
                        log_warn "  [${pkg}] Target ${check_target} is SHADOWED (inode mismatch)! Unmounting buried layers and re-binding..."
                        is_shadowed=1
                        unmount_target_everywhere "${dst}" "" "${user_id}"
                    fi
                    ;;
            esac
        fi

        if mx_is_mounted_exact "${check_target}" && [ ${is_shadowed} -eq 0 ]; then
            local cur_dev
            cur_dev=$(awk -v t="${check_target}" '{m=$2; gsub(/\\040/, " ", m); if (m==t) {print $1; exit}}' /proc/mounts 2>/dev/null)
            if [ -n "${disk_uuid}" ]; then
                local exp_dev
                exp_dev=$(blkid 2>/dev/null | grep -i "UUID=\"${disk_uuid}\"" | cut -d: -f1 | head -n 1)
                [ -z "${exp_dev}" ] && exp_dev=$(toybox blkid 2>/dev/null | grep -i "UUID=\"${disk_uuid}\"" | cut -d: -f1 | head -n 1)
                
                # Check if cur_dev is a loop device backing the container on this disk
                local is_loop_match=0
                case "${cur_dev}" in
                    /dev/block/loop*|/dev/loop*)
                        local loop_bf
                        loop_bf=$(cat "/sys/block/$(basename "${cur_dev}")/loop/backing_file" 2>/dev/null)
                        if [ -n "${loop_bf}" ] && [ "${loop_bf}" = "${src}" ]; then
                            is_loop_match=1
                        fi
                        ;;
                esac

                if [ "${is_loop_match}" = "0" ] && [ -n "${exp_dev}" ] && [ -n "${cur_dev}" ] && [ "${cur_dev}" != "${exp_dev}" ]; then
                    log_warn "  [${pkg}] Target ${check_target} mounted from ${cur_dev}, but configured UUID=${disk_uuid} expects ${exp_dev}. Unmounting stale device mount..."
                    unmount_target_everywhere "${dst}" "${rel}" "${user_id}"
                else
                    # Ensure all runtime, /mnt/user, and Zygote namespaces are also active
                    if ! mx_verify_target_live "${cat}" "${_canary_src}" "${dst}" "${rel}" "${user_id}"; then
                        log_info "  [${pkg}] Active mountpoint exists on ${check_target}, but missing or out-of-sync in one or more runtime/Zygote namespaces. Re-syncing..."
                        if [ -n "${rel}" ]; then
                            bind_mount_to_runtime_namespaces "${src}" "${dst}" "${pkg}" "${user_id}"
                            entries=$((entries + 1))
                            continue
                        else
                            unmount_target_everywhere "${dst}" "" "${user_id}"
                        fi
                    else
                        log_info "  [${pkg}] Target ${check_target} is already actively mounted correctly from ${cur_dev}. Skipping duplicate bind."
                        entries=$((entries + 1))
                        continue
                    fi
                fi
            else
                if ! mx_verify_target_live "${cat}" "${_canary_src}" "${dst}" "${rel}" "${user_id}"; then
                    log_info "  [${pkg}] Active mountpoint exists on ${check_target}, but missing or out-of-sync in one or more runtime/Zygote namespaces. Re-syncing..."
                    if [ -n "${rel}" ]; then
                        bind_mount_to_runtime_namespaces "${src}" "${dst}" "${pkg}" "${user_id}"
                        entries=$((entries + 1))
                        continue
                    else
                        unmount_target_everywhere "${dst}" "" "${user_id}"
                    fi
                else
                    log_info "  [${pkg}] Target ${check_target} is already actively mounted. Skipping duplicate bind."
                    entries=$((entries + 1))
                    continue
                fi
            fi
        fi

        # ── Smart Multi-Disk Auto-Mount ────────────────────────────────────────
        # If source path doesn't exist but disk_uuid is present, try to auto-mount
        # the target disk using its UUID so the source becomes accessible.
        if [ ! -e "${src}" ] && [ -n "${disk_uuid}" ]; then
            log_info "  [${pkg}] Source missing: ${src}; attempting auto-mount via UUID=${disk_uuid}"

            # Derive the disk mount base from the source path
            # sourcePath format: /data/sdext2/MountX/Android/data/... → base = /data/sdext2
            # or /mnt/media_rw/<uuid>/MountX/... → base = /mnt/media_rw/<uuid>
            # We try to match the UUID-resolved mount point to an ancestor directory of src
            local disk_base
            disk_base=$(blkid 2>/dev/null | grep -i "UUID=\"${disk_uuid}\"" | cut -d: -f1 | head -n 1)
            if [ -n "${disk_base}" ]; then
                # Find current mount of this block device
                local existing_mount
                existing_mount=$(grep -F "${disk_base} " /proc/mounts 2>/dev/null | cut -d' ' -f2 | head -n 1)
                if [ -z "${existing_mount}" ]; then
                    # Disk not mounted yet — figure out the expected mount base from source path
                    # Walk source path upward to find a plausible mount root
                    local try_base="${SD_BASE}"
                    # If src starts with something other than SD_BASE, extract base from path
                    case "${src}" in
                        "${SD_BASE}/"*) try_base="${SD_BASE}" ;;
                        /mnt/media_rw/*) try_base=$(echo "${src}" | cut -d/ -f1-4) ;;
                        /mnt/pass_through/*) try_base=$(echo "${src}" | cut -d/ -f1-5) ;;
                        *) try_base="${SD_BASE}" ;;
                    esac
                    mount_disk_by_uuid "${disk_uuid}" "${try_base}"
                else
                    log_info "  [${pkg}] UUID=${disk_uuid} already mounted at ${existing_mount}."
                fi
            else
                # blkid can't find block device: disk not plugged in
                log_warn "  [${pkg}] UUID=${disk_uuid} not found by blkid (disk not connected?). Skipping package ${pkg} and cleaning any partial mounts."
                _failed_pkgs="${_failed_pkgs}${pkg} "
                unmount_target_everywhere "${dst}" "${rel}" "${user_id}"
                continue
            fi
        fi

        # Re-check after potential auto-mount
        if [ ! -e "${src}" ]; then
            log_warn "  [${pkg}] Source path still missing after auto-mount attempt: ${src}; skipping package ${pkg} and cleaning any partial mounts."
            _failed_pkgs="${_failed_pkgs}${pkg} "
            unmount_target_everywhere "${dst}" "${rel}" "${user_id}"
            continue
        fi

        local _post_canary_src="${src}"
        if [ "${cat}" = "APP_PACKAGE" ]; then
            case "${dst}" in
                */lib/*)
                    local _post_abi="${dst##*/lib/}"
                    _post_abi="${_post_abi%%/*}"
                    if [ -n "${_post_abi}" ] && [ -d "${src}/${_post_abi}" ]; then
                        _post_canary_src="${src}/${_post_abi}"
                    fi
                    ;;
            esac
        fi
        if [ -d "${_post_canary_src}" ] && [ "${cat}" != "CACHE_SHADERS" ]; then
            touch "${_post_canary_src}/.mountx_canary" 2>/dev/null
            if [ "${cat}" = "APP_PACKAGE" ]; then
                chmod 644 "${_post_canary_src}/.mountx_canary" 2>/dev/null
                chown 1000:1000 "${_post_canary_src}/.mountx_canary" 2>/dev/null
            fi
        fi

        # If source is a virtual ext4 loop container (.img)
        case "${src}" in
            *.img)
                local ldev=""
                for bf in /sys/block/loop*/loop/backing_file; do
                    if [ -f "${bf}" ] && [ "$(cat "${bf}" 2>/dev/null)" = "${src}" ]; then
                        ldev="/dev/block/$(basename "$(dirname "$(dirname "${bf}")")")"
                        break
                    fi
                done
                local losetup_bin="losetup"
                [ -x "${MODULE_DIR}/bin/busybox" ] && losetup_bin="${MODULE_DIR}/bin/busybox losetup"
                if [ -z "${ldev}" ]; then
                    ldev=$(${losetup_bin} -f 2>/dev/null || losetup -f 2>/dev/null)
                    if [ -n "${ldev}" ]; then
                        ${losetup_bin} --direct-io=on "${ldev}" "${src}" 2>/dev/null || ${losetup_bin} "${ldev}" "${src}" 2>/dev/null || ldev=$(${losetup_bin} -fs "${src}" 2>/dev/null) || losetup "${ldev}" "${src}" 2>/dev/null
                    fi
                fi
                if [ -n "${ldev}" ]; then
                    mkdir -p "${dst}" 2>/dev/null
                    mount -t ext4 -o rw,noatime "${ldev}" "${dst}" 2>/dev/null
                    if mx_is_mounted "${dst}"; then
                        mx_make_rprivate "${dst}"
                        case "${cat}" in
                            APP_PACKAGE)
                                local _is_loop_lib=0
                                case "${dst}" in */lib/*|*/lib) _is_loop_lib=1 ;; esac
                                chown -R 1000:1000 "${dst}" 2>/dev/null
                                chmod -R 755 "${dst}" 2>/dev/null
                                restorecon -FR "${dst}" 2>/dev/null || chcon -R u:object_r:apk_data_file:s0 "${dst}" 2>/dev/null
                                local _loop_oat_rw="${SD_BASE}/MountX/cache/${pkg}_oat"
                                if [ "${_is_loop_lib}" -eq 0 ]; then
                                    [ ! -d "${dst}/oat" ] && mkdir -p "${dst}/oat" 2>/dev/null
                                    chown 1000:1000 "${dst}/oat" 2>/dev/null
                                    chmod 755 "${dst}/oat" 2>/dev/null
                                    mkdir -p "${_loop_oat_rw}" 2>/dev/null
                                    chown 1000:1000 "${_loop_oat_rw}" 2>/dev/null
                                    chmod 755 "${_loop_oat_rw}" 2>/dev/null
                                fi
                                _mx_bind_remount_ro_exec "${dst}" || _mx_bind_remount_rw_exec "${dst}"
                                mx_make_rprivate "${dst}"
                                if [ "${_is_loop_lib}" -eq 0 ] && [ -d "${_loop_oat_rw}" ] && [ -d "${dst}/oat" ]; then
                                    mount -o bind "${_loop_oat_rw}" "${dst}/oat" 2>/dev/null
                                    _mx_bind_remount_rw_exec "${dst}/oat"
                                    mx_make_rprivate "${dst}/oat"
                                fi
                                log_info "  [${pkg}] Loop container mounted (APP_PACKAGE RO/exec protected): ${ldev} -> ${dst}"
                                entries=$((entries + 1))
                                continue
                                ;;
                            CACHE_SHADERS)
                                local app_uid=$(id -u "${pkg}" 2>/dev/null)
                                [ -z "${app_uid}" ] && app_uid="${APP_UID}"
                                chown -R "${app_uid}:${app_uid}" "${dst}" 2>/dev/null
                                chmod -R 700 "${dst}" 2>/dev/null
                                restorecon -FR "${dst}" 2>/dev/null || chcon -R u:object_r:app_data_file:s0 "${dst}" 2>/dev/null
                                log_info "  [${pkg}] Loop container mounted (CACHE_SHADERS): ${ldev} -> ${dst}"
                                entries=$((entries + 1))
                                continue
                                ;;
                            *)
                                local app_uid=$(id -u "${pkg}" 2>/dev/null)
                                [ -z "${app_uid}" ] && app_uid="${APP_UID}"
                                chown "${app_uid}:${app_uid}" "${dst}" 2>/dev/null
                                chmod 700 "${dst}" 2>/dev/null
                                chmod -R u+rwX,go-rwx "${dst}" 2>/dev/null
                                find "${dst}" -type d -exec chmod 700 {} + 2>/dev/null
                                find "${dst}" -type f -exec chmod 600 {} + 2>/dev/null
                                restorecon -FR "${dst}" 2>/dev/null || chcon -R u:object_r:app_data_file:s0 "${dst}" 2>/dev/null
                                if [ -d "/data/data/${pkg}" ] && ! mx_is_mounted "/data/data/${pkg}"; then
                                    mount -o bind "${dst}" "/data/data/${pkg}" 2>/dev/null
                                    mx_make_rprivate "/data/data/${pkg}"
                                fi
                                if [ -d "/data_mirror/data_ce/null/${user_id}/${pkg}" ] && ! mx_is_mounted "/data_mirror/data_ce/null/${user_id}/${pkg}"; then
                                    mount -o bind "${dst}" "/data_mirror/data_ce/null/${user_id}/${pkg}" 2>/dev/null
                                    mx_make_rprivate "/data_mirror/data_ce/null/${user_id}/${pkg}"
                                fi
                                # Self-healing: ensure direct boot storage and ART profile directories exist to prevent Zygote crash
                                mkdir -p "/data/user_de/${user_id}/${pkg}/cache" "/data/user_de/${user_id}/${pkg}/code_cache" 2>/dev/null
                                chown "${app_uid}:${app_uid}" "/data/user_de/${user_id}/${pkg}" 2>/dev/null
                                chmod 700 "/data/user_de/${user_id}/${pkg}" 2>/dev/null
                                restorecon -FR "/data/user_de/${user_id}/${pkg}" 2>/dev/null || chcon -R u:object_r:app_data_file:s0 "/data/user_de/${user_id}/${pkg}" 2>/dev/null
                                if [ ! -d "/data/misc/profiles/cur/${user_id}/${pkg}" ]; then
                                    mkdir -p "/data/misc/profiles/cur/${user_id}/${pkg}" 2>/dev/null
                                    chown "${app_uid}:${app_uid}" "/data/misc/profiles/cur/${user_id}/${pkg}" 2>/dev/null
                                    chmod 700 "/data/misc/profiles/cur/${user_id}/${pkg}" 2>/dev/null
                                    chcon u:object_r:user_profile_data_file:s0 "/data/misc/profiles/cur/${user_id}/${pkg}" 2>/dev/null
                                fi
                                log_info "  [${pkg}] Loop container mounted: ${ldev} -> ${dst}"
                                entries=$((entries + 1))
                                continue
                                ;;
                        esac
                    fi
                fi
                ;;
        esac

        # PRIVATE_INTERNAL (Engine A Direct Folder), APP_PACKAGE, and CACHE_SHADERS
        # Require non-empty existing source data so we never occlude populated internal dirs.
        case "${cat}" in
            PRIVATE_INTERNAL)
                if [ ! -d "${src}" ]; then
                    log_warn "  [${pkg}] PRIVATE_INTERNAL source missing: ${src}; skipping."
                    continue
                fi
                if ! mx_is_mounted "${dst}" && [ -d "${dst}" ]; then
                    local _dst_kb
                    _dst_kb=$(du -sk "${dst}" 2>/dev/null | cut -f1)
                    [ -z "${_dst_kb}" ] && _dst_kb=0
                    if ! _mx_dir_has_real_files "${src}" 48 && [ "${_dst_kb}" -gt 96 ] 2>/dev/null; then
                        log_warn "  [${pkg}] Occlusion guard: refusing to bind empty/skeleton ${src} over populated internal ${dst} (${_dst_kb}KB)."
                        continue
                    fi
                fi
                mkdir -p "${dst}" 2>/dev/null
                local app_uid=$(id -u "${pkg}" 2>/dev/null)
                [ -z "${app_uid}" ] && app_uid=$(pm list packages -U 2>/dev/null | grep -F "package:${pkg} " | sed -n 's/.*uid:\([0-9]*\).*/\1/p' | head -n 1)
                [ -z "${app_uid}" ] && app_uid="${APP_UID}"
                local _cache_hold="/dev/mountx_cache_hold_${pkg}"
                local _held_cache=0
                if ! cut -d'|' -f1,2 "${MOUNTPOINTS_FILE}" 2>/dev/null | grep -qxF "${pkg}|CACHE_SHADERS"; then
                    if ! mx_is_mounted "${dst}" && [ -d "${dst}/cache" ] && [ -n "$(find "${dst}/cache" -type f 2>/dev/null | head -n 1)" ]; then
                        umount -l "${_cache_hold}" 2>/dev/null
                        mkdir -p "${_cache_hold}" 2>/dev/null
                        if mount -o bind "${dst}/cache" "${_cache_hold}" 2>/dev/null; then
                            mx_make_rprivate "${_cache_hold}"
                            _held_cache=1
                        fi
                    fi
                fi
                if [ ! -f "${src}/.mountx_perms_ok" ] || [ "$(cat "${src}/.mountx_perms_ok" 2>/dev/null)" != "${app_uid}" ] || [ "$(stat -c '%u' "${src}" 2>/dev/null)" != "${app_uid}" ]; then
                    chown -R "${app_uid}:${app_uid}" "${src}" 2>/dev/null
                    chmod 700 "${src}" 2>/dev/null
                    chmod -R u+rwX,go-rwx "${src}" 2>/dev/null
                    find "${src}" -type d -exec chmod 700 {} + 2>/dev/null
                    find "${src}" -type f -exec chmod 600 {} + 2>/dev/null
                    restorecon -FR "${src}" 2>/dev/null || chcon -R u:object_r:app_data_file:s0 "${src}" 2>/dev/null
                    printf '%s' "${app_uid}" > "${src}/.mountx_perms_ok" 2>/dev/null
                    chown "${app_uid}:${app_uid}" "${src}/.mountx_perms_ok" 2>/dev/null
                    chmod 600 "${src}/.mountx_perms_ok" 2>/dev/null
                fi
                # Purge orphaned peer mounts before binding dst so shared peer-group propagation never stacks 2x
                if ! mx_is_mounted_exact "${dst}"; then
                    unmount_target_everywhere "${dst}" "" "${user_id}"
                    mount -o bind "${src}" "${dst}" 2>/dev/null
                fi
                if mx_is_mounted_exact "${dst}"; then
                    mx_make_rprivate "${dst}"
                    chown "${app_uid}:${app_uid}" "${dst}" 2>/dev/null
                    chmod 700 "${dst}" 2>/dev/null
                    restorecon -F "${dst}" 2>/dev/null || chcon u:object_r:app_data_file:s0 "${dst}" 2>/dev/null
                    if [ -d "/data/data/${pkg}" ]; then
                        if ! mx_is_mounted_exact "/data/data/${pkg}"; then
                            mount -o bind "${dst}" "/data/data/${pkg}" 2>/dev/null
                        fi
                        mx_make_rprivate "/data/data/${pkg}"
                    fi
                    if [ -d "/data_mirror/data_ce/null/${user_id}/${pkg}" ]; then
                        if ! mx_is_mounted_exact "/data_mirror/data_ce/null/${user_id}/${pkg}"; then
                            mount -o bind "${dst}" "/data_mirror/data_ce/null/${user_id}/${pkg}" 2>/dev/null
                        fi
                        mx_make_rprivate "/data_mirror/data_ce/null/${user_id}/${pkg}"
                    fi
                    if [ "${_held_cache}" = "1" ]; then
                        mkdir -p "${dst}/cache" 2>/dev/null
                        if mount -o bind "${_cache_hold}" "${dst}/cache" 2>/dev/null; then
                            mx_make_rprivate "${dst}/cache"
                            chown "${app_uid}:${app_uid}" "${dst}/cache" 2>/dev/null
                            chmod 700 "${dst}/cache" 2>/dev/null
                            restorecon -FR "${dst}/cache" 2>/dev/null || chcon -R u:object_r:app_data_file:s0 "${dst}/cache" 2>/dev/null
                            if [ -d "/data/data/${pkg}" ]; then
                                mkdir -p "/data/data/${pkg}/cache" 2>/dev/null
                                if ! mx_is_mounted_exact "/data/data/${pkg}/cache"; then
                                    mount -o bind "${_cache_hold}" "/data/data/${pkg}/cache" 2>/dev/null
                                fi
                                mx_make_rprivate "/data/data/${pkg}/cache"
                            fi
                            if [ -d "/data_mirror/data_ce/null/${user_id}/${pkg}" ]; then
                                mkdir -p "/data_mirror/data_ce/null/${user_id}/${pkg}/cache" 2>/dev/null
                                if ! mx_is_mounted_exact "/data_mirror/data_ce/null/${user_id}/${pkg}/cache"; then
                                    mount -o bind "${_cache_hold}" "/data_mirror/data_ce/null/${user_id}/${pkg}/cache" 2>/dev/null
                                fi
                                mx_make_rprivate "/data_mirror/data_ce/null/${user_id}/${pkg}/cache"
                            fi
                            log_info "  [${pkg}] Preserved internal cache over Private Data mount: ${dst}/cache"
                        fi
                        umount -l "${_cache_hold}" 2>/dev/null
                        rmdir "${_cache_hold}" 2>/dev/null
                    fi
                    mkdir -p "/data/user_de/${user_id}/${pkg}/cache" "/data/user_de/${user_id}/${pkg}/code_cache" 2>/dev/null
                    chown "${app_uid}:${app_uid}" "/data/user_de/${user_id}/${pkg}" 2>/dev/null
                    chmod 700 "/data/user_de/${user_id}/${pkg}" 2>/dev/null
                    restorecon -FR "/data/user_de/${user_id}/${pkg}" 2>/dev/null || chcon -R u:object_r:app_data_file:s0 "/data/user_de/${user_id}/${pkg}" 2>/dev/null
                    if [ ! -d "/data/misc/profiles/cur/${user_id}/${pkg}" ]; then
                        mkdir -p "/data/misc/profiles/cur/${user_id}/${pkg}" 2>/dev/null
                        chown "${app_uid}:${app_uid}" "/data/misc/profiles/cur/${user_id}/${pkg}" 2>/dev/null
                        chmod 700 "/data/misc/profiles/cur/${user_id}/${pkg}" 2>/dev/null
                        chcon u:object_r:user_profile_data_file:s0 "/data/misc/profiles/cur/${user_id}/${pkg}" 2>/dev/null
                    fi
                    # Ensure active Zygote/USAP namespaces see PRIVATE_INTERNAL on live remount without signalling USAP workers.
                    # On Android 11+ (/data_mirror present), Zygote uses /data_mirror/data_ce/null/<uid>/<pkg> when
                    # creating app sandboxes; injecting /data/user/0 & /data/data directly into Zygote buries them under tmpfs in every app.
                    local _z_probe_path="${dst}"
                    [ -d "/data_mirror/data_ce/null/${user_id}/${pkg}" ] && _z_probe_path="/data_mirror/data_ce/null/${user_id}/${pkg}"
                    for zpid in $(_mx_zygote_mnt_pids); do
                        [ ! -e "/proc/${zpid}/ns/mnt" ] && continue
                        if ! mx_is_mounted_in_pid "${zpid}" "${_z_probe_path}"; then
                            MX_SRC="${src}" MX_DST="${dst}" MX_PKG="${pkg}" MX_UID="${user_id}" nsenter --mount="/proc/${zpid}/ns/mnt" -- /system/bin/sh -s <<'__MX_PRIV_Z__' 2>/dev/null
                                _dm="/data_mirror/data_ce/null/${MX_UID}/${MX_PKG}"
                                if [ -d "${_dm}" ]; then
                                    _zlist="${_dm}"
                                else
                                    _zlist="${MX_DST} /data/data/${MX_PKG}"
                                fi
                                for _zp in ${_zlist}; do
                                    [ -d "${_zp}" ] || continue
                                    if ! T="${_zp}" awk '{m=$5; gsub(/\\040/, " ", m); if (m==ENVIRON["T"]) {f=1; exit}} END {exit (f?0:1)}' /proc/self/mountinfo 2>/dev/null; then
                                        mount -o bind "${MX_SRC}" "${_zp}" 2>/dev/null
                                    fi
                                    mount --make-rprivate "${_zp}" 2>/dev/null || true
                                done
__MX_PRIV_Z__
                        fi
                    done
                    log_info "  [${pkg}] Engine A Direct Private Data folder mounted: ${src} -> ${dst}"
                    entries=$((entries + 1))
                    continue
                elif [ "${_held_cache}" = "1" ]; then
                    umount -l "${_cache_hold}" 2>/dev/null
                    rmdir "${_cache_hold}" 2>/dev/null
                fi
                ;;
            APP_PACKAGE)
                # Dynamic APK target resolution (rejects ==deleted== or broken paths when PMS is online)
                local real_apk_path
                real_apk_path=$(pm path "${pkg}" 2>/dev/null | head -n 1 | sed 's/^package://')
                if [ "${_pms_online:-0}" -eq 1 ]; then
                    case "${real_apk_path}" in
                        ""|*==deleted==*)
                            log_warn "  [${pkg}] APP_PACKAGE target invalid or missing in PMS (${real_apk_path:-none}); skipping mount to keep SD data safe."
                            continue
                            ;;
                    esac
                fi
                if [ -n "${real_apk_path}" ]; then
                    local real_apk_dir _resolved_abi
                    real_apk_dir=$(dirname "${real_apk_path}")
                    case "${dst}" in
                        */lib/*|*/lib)
                            _resolved_abi=$(_detect_lib_abi "${src}" "${real_apk_dir}" "${dst}")
                            if [ -d "${src}/${_resolved_abi}" ]; then
                                src="${src}/${_resolved_abi}"
                            fi
                            dst="${real_apk_dir}/lib/${_resolved_abi}"
                            ;;
                        *)
                            dst="${real_apk_dir}"
                            ;;
                    esac
                fi
                case "${dst}" in
                    *==deleted==*|*/vmdl*)
                        log_warn "  [${pkg}] Refusing to bind APP_PACKAGE onto invalid/deleted path ${dst}."
                        continue
                        ;;
                esac
                local _is_lib_target=0
                case "${dst}" in
                    */lib/*|*/lib) _is_lib_target=1 ;;
                esac
                if [ ! -d "${src}" ]; then
                    log_warn "  [${pkg}] APP_PACKAGE source missing: ${src}; skipping."
                    continue
                fi
                if [ "${_is_lib_target}" -eq 0 ] && { [ ! -f "${src}/base.apk" ] || [ ! -s "${src}/base.apk" ]; }; then
                    log_warn "  [${pkg}] Occlusion guard: ${src}/base.apk missing; refusing to over-mount APK dir ${dst}."
                    continue
                fi
                if [ "${_is_lib_target}" -eq 1 ] && [ -z "$(find "${src}" -maxdepth 2 -name '*.so' 2>/dev/null | head -n 1)" ]; then
                    local _lib_dst_kb=0
                    [ -d "${dst}" ] && _lib_dst_kb=$(du -sk "${dst}" 2>/dev/null | cut -f1)
                    if [ "${_lib_dst_kb:-0}" -gt 64 ] 2>/dev/null; then
                        log_warn "  [${pkg}] Occlusion guard: no .so libs in ${src}; refusing to over-mount ${dst}."
                        continue
                    fi
                fi

                # Apply ownership, permissions, and canary directly on RW backing store BEFORE read-only bind
                if [ ! -f "${src}/.mountx_perms_ok" ] || [ "$(cat "${src}/.mountx_perms_ok" 2>/dev/null)" != "1000" ] || [ "$(stat -c '%u' "${src}" 2>/dev/null)" != "1000" ]; then
                    chown -R 1000:1000 "${src}" 2>/dev/null
                    chmod -R 755 "${src}" 2>/dev/null
                    printf '%s' "1000" > "${src}/.mountx_perms_ok" 2>/dev/null
                    chown 1000:1000 "${src}/.mountx_perms_ok" 2>/dev/null
                    chmod 644 "${src}/.mountx_perms_ok" 2>/dev/null
                fi
                touch "${src}/.mountx_canary" 2>/dev/null
                chmod 644 "${src}/.mountx_canary" 2>/dev/null
                chown 1000:1000 "${src}/.mountx_canary" 2>/dev/null

                local _oat_rw="${SD_BASE}/MountX/cache/${pkg}_oat"
                if [ "${_is_lib_target}" -eq 0 ]; then
                    [ ! -d "${src}/oat" ] && mkdir -p "${src}/oat" 2>/dev/null
                    chown 1000:1000 "${src}/oat" 2>/dev/null
                    chmod 755 "${src}/oat" 2>/dev/null
                    mkdir -p "${_oat_rw}" 2>/dev/null
                    chown 1000:1000 "${_oat_rw}" 2>/dev/null
                    chmod 755 "${_oat_rw}" 2>/dev/null
                    if [ -d "${SD_BASE}/MountX/lib/${pkg}" ]; then
                        for _lib_sub in "${SD_BASE}/MountX/lib/${pkg}"/*; do
                            [ -d "${_lib_sub}" ] || continue
                            local _ab
                            _ab=$(basename "${_lib_sub}")
                            case "${_ab}" in
                                arm64|arm|armeabi*|x86*|riscv64)
                                    mkdir -p "${src}/lib/${_ab}" 2>/dev/null
                                    chown 1000:1000 "${src}/lib" "${src}/lib/${_ab}" 2>/dev/null
                                    chmod 755 "${src}/lib" "${src}/lib/${_ab}" 2>/dev/null
                                    ;;
                            esac
                        done
                    fi
                fi

                if mx_is_mounted_exact "${dst}" && mx_verify_target_live "${cat}" "${src}" "${dst}" "" "${user_id}"; then
                    # Keep APK dir & libs read-only/exec via busybox per-mount flag so installd cannot purge SD APKs,
                    # while keeping dst/oat writable/exec for ART dex2oat profiles.
                    _mx_bind_remount_ro_exec "${dst}" || _mx_bind_remount_rw_exec "${dst}"
                    mx_make_rprivate "${dst}"
                    if [ "${_is_lib_target}" -eq 0 ] && [ -d "${_oat_rw}" ] && [ -d "${dst}/oat" ]; then
                        if ! mx_is_mounted_exact "${dst}/oat"; then
                            mount -o bind "${_oat_rw}" "${dst}/oat" 2>/dev/null
                        fi
                        _mx_bind_remount_rw_exec "${dst}/oat"
                        mx_make_rprivate "${dst}/oat"
                    fi
                    auto_heal_apk_native_libs "${pkg}" "${dst}"
                    pm unsuspend --user "${user_id}" "${pkg}" >/dev/null 2>&1 || pm unsuspend "${pkg}" >/dev/null 2>&1 || true
                    log_info "  [${pkg}] App package target ${dst} verified (RO/exec protected + RW oat)."
                    entries=$((entries + 1))
                    continue
                elif mx_is_mounted_exact "${dst}"; then
                    unmount_target_everywhere "${dst}" "" "${user_id}"
                fi

                # Preserve internal /lib when mounting APK-only split storage so Lib stays on Internal
                local _lib_hold="/dev/mountx_lib_hold_${pkg}"
                local _held_lib=0
                if [ "${_is_lib_target}" -eq 0 ]; then
                    if [ -d "${dst}/lib" ] && [ -n "$(find "${dst}/lib" -name '*.so' 2>/dev/null | head -n 1)" ] && [ -z "$(find "${src}/lib" -name '*.so' 2>/dev/null | head -n 1)" ]; then
                        if [ ! -d "${SD_BASE}/MountX/lib/${pkg}" ] || [ -z "$(find "${SD_BASE}/MountX/lib/${pkg}" -name '*.so' 2>/dev/null | head -n 1)" ]; then
                            umount -l "${_lib_hold}" 2>/dev/null
                            mkdir -p "${_lib_hold}" 2>/dev/null
                            if mount -o bind "${dst}/lib" "${_lib_hold}" 2>/dev/null; then
                                mx_make_rprivate "${_lib_hold}"
                                _held_lib=1
                            fi
                        fi
                    fi
                fi
                if [ "${_is_lib_target}" -eq 1 ] && [ ! -d "${dst}" ]; then
                    local _parent_apk_dir="${dst%/lib*}"
                    if [ -n "${_parent_apk_dir}" ] && [ "${_parent_apk_dir}" != "${dst}" ] && ! mkdir -p "${dst}" 2>/dev/null; then
                        if mx_is_mounted_exact "${_parent_apk_dir}"; then
                            _mx_bind_remount_rw_exec "${_parent_apk_dir}"
                            mkdir -p "${dst}" 2>/dev/null
                            chown 1000:1000 "${_parent_apk_dir}/lib" "${dst}" 2>/dev/null
                            chmod 755 "${_parent_apk_dir}/lib" "${dst}" 2>/dev/null
                            _mx_bind_remount_ro_exec "${_parent_apk_dir}"
                        fi
                    fi
                fi
                mkdir -p "${dst}" 2>/dev/null
                if mount -o bind "${src}" "${dst}" 2>/dev/null; then
                    _mx_bind_remount_ro_exec "${dst}" || _mx_bind_remount_rw_exec "${dst}"
                    mx_make_rprivate "${dst}"
                    if [ "${_is_lib_target}" -eq 0 ] && [ -d "${_oat_rw}" ] && [ -d "${dst}/oat" ]; then
                        mount -o bind "${_oat_rw}" "${dst}/oat" 2>/dev/null
                        _mx_bind_remount_rw_exec "${dst}/oat"
                        mx_make_rprivate "${dst}/oat"
                    fi
                fi
                if [ "${_held_lib}" = "1" ]; then
                    mkdir -p "${dst}/lib" 2>/dev/null
                    if mount -o bind "${_lib_hold}" "${dst}/lib" 2>/dev/null; then
                        _mx_bind_remount_rw_exec "${dst}/lib"
                        mx_make_rprivate "${dst}/lib"
                        log_info "  [${pkg}] Preserved internal native libs over APK mount: ${dst}/lib"
                    fi
                    umount -l "${_lib_hold}" 2>/dev/null
                    rmdir "${_lib_hold}" 2>/dev/null
                else
                    auto_heal_apk_native_libs "${pkg}" "${dst}"
                fi
                # Mirror protected RO/exec APK + RW oat into Zygote/USAP namespaces without touching USAP signals
                local _bb_bin
                _bb_bin=$(_mx_busybox_bin)
                for zpid in $(_mx_zygote_mnt_pids); do
                    [ ! -e "/proc/${zpid}/ns/mnt" ] && continue
                    if ! mx_is_mounted_in_pid "${zpid}" "${dst}"; then
                        MX_SRC="${src}" MX_DST="${dst}" MX_OAT="${_oat_rw}" MX_IS_LIB="${_is_lib_target}" MX_BB="${_bb_bin}" nsenter --mount="/proc/${zpid}/ns/mnt" -- /system/bin/sh -c '
                            mkdir -p "${MX_DST}" 2>/dev/null
                            if mount -o bind "${MX_SRC}" "${MX_DST}" 2>/dev/null; then
                                if [ -n "${MX_BB}" ]; then
                                    "${MX_BB}" mount -o remount,ro,exec,bind "${MX_DST}" 2>/dev/null || true
                                fi
                                mount --make-rprivate "${MX_DST}" 2>/dev/null || true
                                if [ "${MX_IS_LIB}" -eq 0 ] && [ -n "${MX_OAT}" ] && [ -d "${MX_OAT}" ] && [ -d "${MX_DST}/oat" ]; then
                                    mount -o bind "${MX_OAT}" "${MX_DST}/oat" 2>/dev/null
                                    [ -n "${MX_BB}" ] && "${MX_BB}" mount -o remount,rw,exec,bind "${MX_DST}/oat" 2>/dev/null || true
                                    mount --make-rprivate "${MX_DST}/oat" 2>/dev/null || true
                                fi
                            fi
                        ' 2>/dev/null
                    fi
                done
                pm unsuspend --user "${user_id}" "${pkg}" >/dev/null 2>&1 || pm unsuspend "${pkg}" >/dev/null 2>&1 || true
                log_info "  [${pkg}] App package bind-mounted (RO/exec protected): ${src} -> ${dst}"
                entries=$((entries + 1))
                continue
                ;;
            CACHE_SHADERS)
                if [ ! -d "${src}" ]; then
                    log_warn "  [${pkg}] CACHE_SHADERS source missing: ${src}; skipping."
                    continue
                fi
                # Occlusion guard: do not over-mount empty/skeleton SD cache over populated internal cache
                local _cache_dst_kb=0
                [ -d "${dst}" ] && ! mx_is_mounted "${dst}" && _cache_dst_kb=$(du -sk "${dst}" 2>/dev/null | cut -f1)
                if ! _mx_dir_has_real_files "${src}" 32 && [ "${_cache_dst_kb:-0}" -gt 128 ] 2>/dev/null; then
                    log_warn "  [${pkg}] Occlusion guard: refusing to bind empty/skeleton cache ${src} over ${dst} (${_cache_dst_kb}KB)."
                    continue
                fi
                # If external storage path (/data/media, /storage/emulated, */Android/*), route via runtime namespaces
                case "${dst}" in
                    */Android/*|/data/media/*|/storage/emulated/*|/sdcard/*)
                        touch "${src}/.nomedia" 2>/dev/null
                        apply_permissions "${pkg}" "${src}" "${user_id}"
                        bind_mount_to_runtime_namespaces "${src}" "${dst}" "${pkg}" "${user_id}"
                        entries=$((entries + 1))
                        continue
                        ;;
                esac
                mkdir -p "${dst}" 2>/dev/null
                local app_uid=$(id -u "${pkg}" 2>/dev/null)
                [ -z "${app_uid}" ] && app_uid=$(pm list packages -U 2>/dev/null | grep -F "package:${pkg} " | sed -n 's/.*uid:\([0-9]*\).*/\1/p' | head -n 1)
                [ -z "${app_uid}" ] && app_uid="${APP_UID}"
                if [ ! -f "${src}/.mountx_perms_ok" ] || [ "$(cat "${src}/.mountx_perms_ok" 2>/dev/null)" != "${app_uid}" ] || [ "$(stat -c '%u' "${src}" 2>/dev/null)" != "${app_uid}" ]; then
                    chown -R "${app_uid}:${app_uid}" "${src}" 2>/dev/null
                    chmod 700 "${src}" 2>/dev/null
                    chmod -R u+rwX,go-rwx "${src}" 2>/dev/null
                    chcon -R u:object_r:app_data_file:s0 "${src}" 2>/dev/null
                    printf '%s' "${app_uid}" > "${src}/.mountx_perms_ok" 2>/dev/null
                    chown "${app_uid}:${app_uid}" "${src}/.mountx_perms_ok" 2>/dev/null
                    chmod 600 "${src}/.mountx_perms_ok" 2>/dev/null
                fi
                if ! mx_is_mounted_exact "${dst}"; then
                    unmount_target_everywhere "${dst}" "" "${user_id}"
                    mount -o bind "${src}" "${dst}" 2>/dev/null
                fi
                if mx_is_mounted_exact "${dst}"; then
                    mx_make_rprivate "${dst}"
                    chown "${app_uid}:${app_uid}" "${dst}" 2>/dev/null
                    chmod 700 "${dst}" 2>/dev/null
                    restorecon -F "${dst}" 2>/dev/null || chcon u:object_r:app_data_file:s0 "${dst}" 2>/dev/null
                    if [ -d "/data/data/${pkg}/cache" ]; then
                        if ! mx_is_mounted_exact "/data/data/${pkg}/cache"; then
                            mount -o bind "${src}" "/data/data/${pkg}/cache" 2>/dev/null
                        fi
                        mx_make_rprivate "/data/data/${pkg}/cache"
                    fi
                    if [ -d "/data_mirror/data_ce/null/${user_id}/${pkg}/cache" ]; then
                        if ! mx_is_mounted_exact "/data_mirror/data_ce/null/${user_id}/${pkg}/cache"; then
                            mount -o bind "${src}" "/data_mirror/data_ce/null/${user_id}/${pkg}/cache" 2>/dev/null
                        fi
                        mx_make_rprivate "/data_mirror/data_ce/null/${user_id}/${pkg}/cache"
                    fi
                    # Ensure active Zygote/USAP namespaces see CACHE_SHADERS on live remount without burying under tmpfs /data/data
                    local _z_cache_probe="${dst}"
                    [ -d "/data_mirror/data_ce/null/${user_id}/${pkg}/cache" ] && _z_cache_probe="/data_mirror/data_ce/null/${user_id}/${pkg}/cache"
                    for zpid in $(_mx_zygote_mnt_pids); do
                        [ ! -e "/proc/${zpid}/ns/mnt" ] && continue
                        if ! mx_is_mounted_in_pid "${zpid}" "${_z_cache_probe}"; then
                            MX_SRC="${src}" MX_DST="${dst}" MX_PKG="${pkg}" MX_UID="${user_id}" nsenter --mount="/proc/${zpid}/ns/mnt" -- /system/bin/sh -s <<'__MX_CACHE_Z__' 2>/dev/null
                                _dm="/data_mirror/data_ce/null/${MX_UID}/${MX_PKG}/cache"
                                if [ -d "${_dm}" ]; then
                                    _zlist="${_dm}"
                                else
                                    _zlist="${MX_DST} /data/data/${MX_PKG}/cache"
                                fi
                                for _zp in ${_zlist}; do
                                    [ -d "${_zp}" ] || continue
                                    if ! T="${_zp}" awk '{m=$5; gsub(/\\040/, " ", m); if (m==ENVIRON["T"]) {f=1; exit}} END {exit (f?0:1)}' /proc/self/mountinfo 2>/dev/null; then
                                        mount -o bind "${MX_SRC}" "${_zp}" 2>/dev/null
                                    fi
                                    mount --make-rprivate "${_zp}" 2>/dev/null || true
                                done
__MX_CACHE_Z__
                        fi
                    done
                    log_info "  [${pkg}] Internal cache shaders bind-mounted directly: ${src} -> ${dst}"
                    entries=$((entries + 1))
                else
                    log_warn "  [${pkg}] Failed to bind-mount internal cache shaders: ${src} -> ${dst}"
                fi
                continue
                ;;
        esac

        # Hard Guard: Never pass /data/app, /data/user, or /data/data to emulated runtime namespaces
        case "${dst}" in
            /data/app/*|/data/user/*|/data/data/*)
                mkdir -p "${dst}" 2>/dev/null
                mount -o bind "${src}" "${dst}" 2>/dev/null
                mx_make_rprivate "${dst}"
                entries=$((entries + 1))
                continue
                ;;
        esac

        # Timeout-proof Occlusion guard before external/media bind mount:
        # Check fast internal target FIRST; only traverse potentially huge external SD directory when internal actually holds data.
        if [ -d "${dst}" ] && ! mx_is_mounted_exact "${check_target}"; then
            if _dir_is_substantive "${dst}" && ! _dir_is_substantive "${src}"; then
                local _ext_dst_kb _ext_src_kb _du_rc
                _ext_dst_kb=$(du -sk "${dst}" 2>/dev/null | cut -f1)
                _ext_src_kb=$(du -sk "${src}" 2>/dev/null | cut -f1)
                _du_rc=$?
                if [ "${_du_rc}" -eq 0 ] && [ "${_ext_dst_kb:-0}" -gt 512 ] 2>/dev/null && [ "${_ext_src_kb:-0}" -le 128 ] 2>/dev/null; then
                    log_warn "  [${pkg}] Occlusion guard: refusing to bind empty/small ${src} (${_ext_src_kb:-0}KB) over populated ${dst} (${_ext_dst_kb:-0}KB)."
                    continue
                fi
            fi
        fi

        # Smart .nomedia localized scoping
        if [ "${preserve_media}" = "1" ] || [ "${cat}" = "MEDIA_DOWNLOADS" ] || [ "${cat}" = "CUSTOM" ]; then
            # Do not hide from gallery unless original destination had .nomedia
            if [ ! -f "${dst}/.nomedia" ]; then
                rm -f "${src}/.nomedia" 2>/dev/null
            fi
        else
            # Ensure private game data has .nomedia
            touch "${src}/.nomedia" 2>/dev/null
        fi

        apply_permissions "${pkg}" "${src}" "${user_id}"
        bind_mount_to_runtime_namespaces "${src}" "${dst}" "${pkg}" "${user_id}"
        entries=$((entries + 1))
    done < "${MOUNTPOINTS_FILE}"

    # If any multi-target package failed on one of its required mountpoints (e.g. detached disk),
    # ensure all other targets of that package are unmounted so the app is never left half-mounted.
    if [ "${_failed_pkgs}" != " " ]; then
        while IFS='|' read -r _f_pkg _f_cat _f_src _f_dst _f_pres _f_uuid _f_uid || [ -n "${_f_pkg}" ]; do
            case "${_f_pkg}" in ''|\#*) continue ;; esac
            _f_pkg=$(echo "${_f_pkg}" | tr -d '\r')
            _f_dst=$(echo "${_f_dst}" | tr -d '\r')
            _f_uid=$(echo "${_f_uid}" | tr -d '\r')
            [ -z "${_f_uid}" ] && _f_uid="0"
            case "${_failed_pkgs}" in
                *" ${_f_pkg} "*)
                    local _f_rel=""
                    case "${_f_dst}" in
                        */Android/data/*|*/Android/obb/*|*/Android/media/*)
                            _f_rel="Android/"$(echo "${_f_dst}" | sed 's|.*/Android/||')
                            ;;
                        /sdcard/*|/storage/emulated/*|/data/media/*|/mnt/user/*)
                            _f_rel=$(echo "${_f_dst}" | sed 's|^/sdcard/||;s|^/storage/emulated/[0-9]*/||;s|^/data/media/[0-9]*/||;s|^/mnt/user/[0-9]*/primary/||;s|^/mnt/user/[0-9]*/emulated/[0-9]*/||')
                            ;;
                    esac
                    unmount_target_everywhere "${_f_dst}" "${_f_rel}" "${_f_uid}"
                    ;;
            esac
        done < "${MOUNTPOINTS_FILE}"
    fi

    # Auto-unsuspend every package whose required mountpoints succeeded (covers OBB/EXTERNAL_DATA/PRIVATE/CACHE/APK,
    # not only APP_PACKAGE) so mounted apps are never left greyed-out/unavailable in the Android launcher.
    local _unsusp_done=" "
    while IFS='|' read -r _u_pkg _u_cat _u_src _u_dst _u_pres _u_uuid _u_uid || [ -n "${_u_pkg}" ]; do
        case "${_u_pkg}" in ''|\#*) continue ;; esac
        _u_pkg=$(echo "${_u_pkg}" | tr -d '\r')
        _u_uid=$(echo "${_u_uid}" | tr -d '\r')
        [ -z "${_u_uid}" ] && _u_uid="0"
        case "${_failed_pkgs}" in
            *" ${_u_pkg} "*) continue ;;
        esac
        case "${_unsusp_done}" in
            *" ${_u_pkg}:${_u_uid} "*) continue ;;
        esac
        _unsusp_done="${_unsusp_done}${_u_pkg}:${_u_uid} "
        if dumpsys package "${_u_pkg}" 2>/dev/null | grep -q 'suspended=true'; then
            pm unsuspend --user "${_u_uid}" "${_u_pkg}" >/dev/null 2>&1 || pm unsuspend "${_u_pkg}" >/dev/null 2>&1 || true
            log_info "  [${_u_pkg}] Auto-unsuspended package (user ${_u_uid}) after mount verification."
        fi
    done < "${MOUNTPOINTS_FILE}"

    log_info "Processed ${entries} entry(ies) from mountpoints.conf."
    [ ${entries} -gt 0 ] && return 0 || return 1
}

# ── Legacy gamelist.conf Processor (Fallback) ─────────────────────────────────
GAME_PKGS=""
GAME_MODES=""

load_gamelist() {
    if [ ! -f "${GAMELIST_FILE}" ]; then
        log_warn "gamelist.conf not found; no legacy games to process."
        return
    fi

    local count=0
    while IFS= read -r line || [ -n "${line}" ]; do
        line=$(echo "${line}" | tr -d '\r' | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')
        case "${line}" in ''|\#*) continue ;; esac

        local pkg mode
        pkg=$(echo "${line}" | cut -d':' -f1)
        mode=$(echo "${line}" | cut -d':' -f2)

        [ -z "${pkg}" ] || [ -z "${mode}" ] && continue

        GAME_PKGS="${GAME_PKGS}${pkg} "
        GAME_MODES="${GAME_MODES}${mode} "
        count=$((count + 1))
    done < "${GAMELIST_FILE}"

    log_info "gamelist.conf loaded: ${count} game(s) registered."
}

process_game() {
    local pkg="$1"
    local mode="$2"

    if ! is_package_installed "${pkg}"; then
        log_warn "  [${pkg}] App is not installed on system. Skipping legacy mount to prevent Play Store reinstall errors."
        return
    fi

    local int_data="/data/media/0/Android/data/${pkg}"
    local int_obb="/data/media/0/Android/obb/${pkg}"
    local int_media="/data/media/0/Android/media/${pkg}"

    local sd_data="${SD_BASE}/MountX/Android/data/${pkg}"
    [ ! -d "${sd_data}" ] && [ -d "${SD_BASE}/Android/data/${pkg}" ] && sd_data="${SD_BASE}/Android/data/${pkg}"

    local sd_obb="${SD_BASE}/MountX/Android/obb/${pkg}"
    [ ! -d "${sd_obb}" ] && [ -d "${SD_BASE}/Android/obb/${pkg}" ] && sd_obb="${SD_BASE}/Android/obb/${pkg}"

    local sd_media="${SD_BASE}/MountX/Android/media/${pkg}"
    [ ! -d "${sd_media}" ] && [ -d "${SD_BASE}/Android/media/${pkg}" ] && sd_media="${SD_BASE}/Android/media/${pkg}"

    local src_data dst_data
    case "${mode}" in
        pkg)   src_data="${sd_data}"; dst_data="${int_data}" ;;
        files) src_data="${sd_data}/files"; dst_data="${int_data}/files" ;;
    esac

    if [ -d "${src_data}" ]; then
        touch "${src_data}/.nomedia" 2>/dev/null
        apply_permissions "${pkg}" "${src_data}" "0"
        bind_mount_to_runtime_namespaces "${src_data}" "${dst_data}" "${pkg}" "0"
    fi

    if [ -d "${sd_obb}" ]; then
        touch "${sd_obb}/.nomedia" 2>/dev/null
        apply_permissions "${pkg}" "${sd_obb}" "0"
        bind_mount_to_runtime_namespaces "${sd_obb}" "${int_obb}" "${pkg}" "0"
    fi

    if [ -d "${sd_media}" ]; then
        apply_permissions "${pkg}" "${sd_media}" "0"
        bind_mount_to_runtime_namespaces "${sd_media}" "${int_media}" "${pkg}" "0"
    fi
}

# ── Unified Pipeline Mount Executor ──────────────────────────────────────────
apply_all_mounts() {
    local loaded_pipeline=0
    if load_mountpoints; then
        loaded_pipeline=1
    fi

    if [ ${loaded_pipeline} -eq 0 ]; then
        log_info "Running fallback gamelist processor..."
        load_gamelist
        if [ -n "${GAME_PKGS}" ]; then
            local i=1
            for pkg in ${GAME_PKGS}; do
                local mode
                mode=$(echo "${GAME_MODES}" | cut -d' ' -f"${i}")
                process_game "${pkg}" "${mode}"
                i=$((i + 1))
            done
        fi
    fi
}


# ── Mount Watchdog Daemon (event-driven, zero-drain background) ───────────────
# Runs in background after initial mount. Uses inotifywait when available
# (zero CPU, kernel-event driven), falls back to a conservative 120s loop.
# Respects battery: exits on critical level, Doze, or module disable.
mount_watchdog() {
    local START_TS
    START_TS=$(date +%s)
    local CONSECUTIVE_OK=0
    local CHECK_INTERVAL=120
    local MAX_INTERVAL=120
    local RECOVERY_STREAK=0
    local RO_RECOVERY_ATTEMPTED=0
    local WATCHDOG_LOG="${MODULE_DIR}/mountx_watchdog.log"

    # ── Capture Zygote PID at start for soft-restart detection ───────────────
    # A soft restart (SystemServer / Zygote restart without kernel reboot) changes
    # the zygote64 PID but does NOT trigger service.sh re-execution. We detect
    # this by comparing the current zygote64 PID against the one recorded at boot.
    _get_zygote_pid() {
        local p
        p=$(pidof zygote64 2>/dev/null)
        [ -z "${p}" ] && p=$(toybox pidof zygote64 2>/dev/null)
        [ -z "${p}" ] && p=$(busybox pidof zygote64 2>/dev/null)
        [ -z "${p}" ] && p=$(pidof zygote 2>/dev/null)
        [ -z "${p}" ] && p=$(toybox pidof zygote 2>/dev/null)
        [ -z "${p}" ] && p=$(busybox pidof zygote 2>/dev/null)
        if [ -n "${p}" ]; then
            local _cand _st _ppid
            for _cand in ${p}; do
                _st=$(cat "/proc/${_cand}/stat" 2>/dev/null)
                _st="${_st#*) }"
                _ppid="${_st#* }"; _ppid="${_ppid%% *}"
                if [ "${_ppid}" = "1" ]; then
                    echo "${_cand}"
                    return 0
                fi
            done
            echo "${p%% *}"
            return 0
        fi
        p=$(pgrep -P 1 -f "^zygote64$" 2>/dev/null | head -n 1)
        [ -z "${p}" ] && p=$(pgrep -P 1 -f "^zygote$" 2>/dev/null | head -n 1)
        [ -z "${p}" ] && p=$(pgrep -f "^zygote64$" 2>/dev/null | head -n 1)
        [ -z "${p}" ] && p=$(pgrep -f "^zygote$" 2>/dev/null | head -n 1)
        echo "${p}"
    }
    ZYGOTE_PID_LAST=$(_get_zygote_pid)
    local PENDING_SOFT_RESTART=0
    local LAST_EMULATED_GEN
    LAST_EMULATED_GEN=$(_emulated_root_generation)
    local LAST_MP_PID
    LAST_MP_PID=$(_get_mediaprovider_pid)
    local WATCHDOG_SELF_PID="${WATCHDOG_SELF_PID:-$$}"
    local LAST_CE_AVAILABLE="false"
    [ -f "/dev/.mountx_initial_mounted" ] && LAST_CE_AVAILABLE="true"
    local LAST_DISK_ATTACHED=0
    local LAST_DISK_NODES_SIG
    LAST_DISK_NODES_SIG=$(echo /dev/block/mmcblk*p* /dev/block/sd[a-z][0-9]* 2>/dev/null)
    local blk_dev
    blk_dev=$(_resolve_monitored_blk_dev)
    [ -n "${blk_dev}" ] && [ -b "${blk_dev}" ] && LAST_DISK_ATTACHED=1
    log_info "[Watchdog] Starting. PID=${WATCHDOG_SELF_PID}, Zygote64=${ZYGOTE_PID_LAST:-N/A}, MediaProvider=${LAST_MP_PID:-N/A}, inotifywait=$(which inotifywait 2>/dev/null || echo N/A)"

    _mx_unmount_game_binds() {
        local _bnd _target_blk
        _target_blk=$(_resolve_monitored_blk_dev)
        # Unmount all active bind targets derived from our monitored block device (deepest paths first, excluding SD_BASE)
        if [ -n "${_target_blk}" ]; then
            local _rblk
            _rblk=$(readlink -f "${_target_blk}" 2>/dev/null || echo "${_target_blk}")
            for _zp in 1 $(_mx_zygote_mnt_pids); do
                [ "${_zp}" = "1" ] && _mf="/proc/1/mounts" || _mf="/proc/${_zp}/mounts"
                [ -r "${_mf}" ] || continue
                for _bnd in $(awk -v d1="${_rblk}" -v d2="${_target_blk}" -v sb="${SD_BASE%/}" '($1 == d1 || $1 == d2) && $2 != sb {print length($2), $2}' "${_mf}" 2>/dev/null | sort -rn -k1,1 | cut -d' ' -f2- | awk '!seen[$0]++'); do
                    if [ "${_zp}" = "1" ]; then
                        umount "${_bnd}" 2>/dev/null || umount -l "${_bnd}" 2>/dev/null
                    else
                        nsenter --mount="/proc/${_zp}/ns/mnt" -- /system/bin/sh -c 'umount "$1" 2>/dev/null || umount -l "$1" 2>/dev/null' _ "${_bnd}" 2>/dev/null
                    fi
                done
            done
        fi
        # Fallback cleanup for any remaining Android runtime paths
        for _bnd in $(grep -E "(/data/media/[0-9]+/Android|/storage/emulated/[0-9]+/Android|/mnt/user/[0-9]+/emulated/[0-9]+/Android|/mnt/runtime/(default|read|write|full)/emulated/[0-9]+/Android)" /proc/mounts 2>/dev/null | cut -d' ' -f2 | sort -r | uniq); do
            case "${_bnd}" in
                */Android/data|*/Android/obb|*/Android/media) continue ;;
            esac
            umount -l "${_bnd}" 2>/dev/null
        done
    }

    _mx_detach_block_namespaces() {
        local _blk="$1"
        local _base="$2"
        local _rblk _needle _seen_ns _mf _pid _ns _z64 _z32 _ppid _cmd _uid _oom _mpkg _bg=0
        [ -z "${_blk}" ] && _blk=$(awk -v t="${_base}" '$2 == t {print $1; exit}' /proc/1/mounts 2>/dev/null)
        [ -z "${_blk}" ] && _blk=$(awk -v t="${_base}" '$2 == t {print $1; exit}' /proc/mounts 2>/dev/null)
        _rblk=$(readlink -f "${_blk}" 2>/dev/null || echo "${_blk}")

        # Gracefully stop only MountX-managed apps before resetting the block superblock
        if [ -f "${MOUNTPOINTS_FILE}" ]; then
            for _mpkg in $(awk -F'|' '!/^#/ && $1 != "" {print $1}' "${MOUNTPOINTS_FILE}" 2>/dev/null | sort -u); do
                [ -n "${_mpkg}" ] && am force-stop "${_mpkg}" >/dev/null 2>&1 || true
            done
        fi

        _mx_unmount_game_binds

        for _zp in 1 $(_mx_zygote_mnt_pids); do
            if [ "${_zp}" = "1" ]; then
                umount "${_base}" 2>/dev/null || umount -l "${_base}" 2>/dev/null || true
            elif [ -d "/proc/${_zp}/ns" ]; then
                nsenter --mount="/proc/${_zp}/ns/mnt" -- /system/bin/sh -c 'umount "$1" 2>/dev/null || umount -l "$1" 2>/dev/null' _ "${_base}" 2>/dev/null || true
            fi
        done

        [ -z "${_rblk}" ] && [ -z "${_blk}" ] && return 0
        _needle="${_rblk:-${_blk}}"
        _seen_ns=" "
        for _mf in $(grep -l -F "${_needle}" /proc/[0-9]*/mounts 2>/dev/null); do
            _pid="${_mf#/proc/}"
            _pid="${_pid%/mounts}"
            [ -n "${_pid}" ] || continue
            _ns=$(readlink "/proc/${_pid}/ns/mnt" 2>/dev/null) || continue
            case "${_seen_ns}" in *" ${_ns} "*) continue ;; esac
            _seen_ns="${_seen_ns}${_ns} "
            (
                D1="${_needle}" D2="${_blk}" /system/bin/nsenter --mount="/proc/${_pid}/ns/mnt" -- /system/bin/sh -s <<'__MX_DETACH__' 2>/dev/null || true
                _p=0
                while [ "${_p}" -lt 8 ]; do
                    _mps=$(awk '$1 == ENVIRON["D1"] || (ENVIRON["D2"] != "" && $1 == ENVIRON["D2"]) {print $2}' /proc/self/mounts 2>/dev/null | sort -r | uniq)
                    [ -z "${_mps}" ] && break
                    for _m in ${_mps}; do
                        umount "${_m}" 2>/dev/null || umount -l "${_m}" 2>/dev/null || true
                    done
                    _p=$((_p + 1))
                done
__MX_DETACH__
            ) &
            _bg=$((_bg + 1))
            if [ "${_bg}" -ge 8 ]; then
                wait
                _bg=0
            fi
        done
        wait

        # Recycle ONLY non-persistent user app PIDs still pinning buried mounts (NEVER kill SystemUI, Phone, MediaProvider, etc.)
        _z64=$(pidof zygote64 2>/dev/null)
        _z32=$(pidof zygote 2>/dev/null)
        for _mf in $(grep -l -F "${_needle}" /proc/[0-9]*/mounts 2>/dev/null); do
            _pid="${_mf#/proc/}"
            _pid="${_pid%/mounts}"
            [ "${_pid}" -gt 2200 ] 2>/dev/null || continue
            _ppid=$(awk '/^PPid:/{print $2}' "/proc/${_pid}/status" 2>/dev/null)
            case " ${_z64} ${_z32} " in
                *" ${_ppid} "*)
                    _uid=$(awk '/^Uid:/{print $2}' "/proc/${_pid}/status" 2>/dev/null)
                    [ "${_uid:-0}" -ge 10000 ] 2>/dev/null || continue
                    _oom=$(cat "/proc/${_pid}/oom_score_adj" 2>/dev/null)
                    [ "${_oom:-0}" -ge 0 ] 2>/dev/null || continue
                    _cmd=$(tr '\0' ' ' < "/proc/${_pid}/cmdline" 2>/dev/null)
                    case "${_cmd}" in
                        *system_server*|*webview_zygote*|*_zygote*|*systemui*|*com.android.phone*|*providers.media*|*externalstorage*|*launcher*|*app.mountx*) continue ;;
                    esac
                    kill -9 "${_pid}" 2>/dev/null || true
                    ;;
            esac
        done
        sleep 1
    }

    _mx_try_safe_fsck_and_remount() {
        local _blk="$1"
        local _rem _fstype
        [ -n "${_blk}" ] && [ -b "${_blk}" ] || return 1
        _rem=$(grep -l -F "${_blk}" /proc/[0-9]*/mounts 2>/dev/null | wc -l)
        if [ "${_rem}" -eq 0 ] 2>/dev/null; then
            _fstype=$(blkid "${_blk}" 2>/dev/null | sed -n 's/.*TYPE="\([^"]*\)".*/\1/p')
            case "${_fstype}" in
                f2fs)
                    if [ -x /system/bin/fsck.f2fs ]; then
                        log_warn "[Storage] Running preen fsck.f2fs -p on unmounted ${_blk}..."
                        /system/bin/fsck.f2fs -p "${_blk}" >/dev/null 2>&1 || /system/bin/fsck.f2fs -a "${_blk}" >/dev/null 2>&1
                    fi
                    ;;
                ext4)
                    if [ -x /system/bin/e2fsck ]; then
                        log_warn "[Storage] Running preen e2fsck -p on unmounted ${_blk}..."
                        /system/bin/e2fsck -p "${_blk}" >/dev/null 2>&1
                    fi
                    ;;
                exfat)
                    if [ -x /system/bin/fsck.exfat ]; then
                        log_warn "[Storage] Running repair fsck.exfat -y on unmounted ${_blk}..."
                        /system/bin/fsck.exfat -y "${_blk}" >/dev/null 2>&1
                    fi
                    ;;
            esac
            mount_sd
            mx_is_mounted "${SD_BASE}" && _probe_sd_io_healthy "${_blk}" "${SD_BASE}" 2 && return 0
        fi
        return 1
    }

    _mx_reapply_mounts_after_recovery() {
        rm -f "/dev/.mountx_disk_error" 2>/dev/null
        echo "${WATCHDOG_SELF_PID}" > "${APPLY_LOCK_FILE}" 2>/dev/null
        cleanup_stale_mounts
        apply_io_tweaks
        apply_all_mounts
        LAST_EMULATED_GEN=$(_emulated_root_generation)
        LAST_MP_PID=$(_get_mediaprovider_pid)
        rm -f "${APPLY_LOCK_FILE}" 2>/dev/null
        (timeout 10 am broadcast -a app.mountx.ACTION_SYNC_NOTIFICATION -p app.mountx >/dev/null 2>&1) &
        RECOVERY_STREAK=0
        RO_RECOVERY_ATTEMPTED=0
        CONSECUTIVE_OK=0
        CHECK_INTERVAL=60
    }

    _mx_advance_recovery_backoff() {
        RECOVERY_STREAK=$((RECOVERY_STREAK + 1))
        if [ ${RECOVERY_STREAK} -le 4 ]; then
            CHECK_INTERVAL=5
        elif [ ${RECOVERY_STREAK} -le 6 ]; then
            CHECK_INTERVAL=15
        else
            CHECK_INTERVAL=60
        fi
    }

    # ── Helper: perform authoritative mount integrity check ───────────────────
    check_mounts() {
        # Exit conditions
        [ -f "${MODULE_DIR}/disable" ] && { log_info "[Watchdog] Module disabled. Exiting."; exit 0; }
        [ -f "${MODULE_DIR}/remove" ]  && { log_info "[Watchdog] Module removing. Exiting."; exit 0; }

        # Dynamically resolve configured or mounted SD block device (no hardcoded paths)
        blk_dev=$(_resolve_monitored_blk_dev)

        # 0. Hotplug Connection Sentinel:
        # Detect if a previously missing/detached disk has just been plugged into the phone
        local current_disk_attached=0
        [ -n "${blk_dev}" ] && [ -b "${blk_dev}" ] && current_disk_attached=1

        if [ ${current_disk_attached} -eq 1 ] && [ ${LAST_DISK_ATTACHED} -eq 0 ] && [ ! -f "/dev/.mountx_migrating" ]; then
            log_info "[Watchdog] MicroSD/External hotplug insertion detected (${blk_dev})! Card just plugged in."
            rm -f "/dev/.mountx_unmounted" "/dev/.mountx_disk_error"
            LAST_DISK_ATTACHED=1
            RECOVERY_STREAK=0
            RO_RECOVERY_ATTEMPTED=0
            sleep 3

            log_info "[Watchdog] Auto-mounting ${blk_dev} to ${SD_BASE} after hotplug..."
            mount_sd
            blk_dev=$(_resolve_monitored_blk_dev)
            if mx_is_mounted "${SD_BASE}"; then
                if is_user_unlocked; then
                    log_info "[Watchdog] SD_BASE mounted after hotplug. Applying bind mounts..."
                    _mx_reapply_mounts_after_recovery
                else
                    log_info "[Watchdog] SD_BASE mounted after hotplug while BFU locked; deferring bind mounts until unlock."
                    CONSECUTIVE_OK=0
                    CHECK_INTERVAL=60
                fi
                return 0
            else
                log_error "[Watchdog] Failed to mount ${blk_dev} to ${SD_BASE} after hotplug."
            fi
        fi
        LAST_DISK_ATTACHED=${current_disk_attached}

        # Intentional unmount or active migration guard: do not re-mount while transferring/unmounted
        { [ -f "/dev/.mountx_unmounted" ] || [ -f "/dev/.mountx_migrating" ]; } && return 0

        # Lockscreen / CE Storage Safety Guard: NEVER touch game bind-mounts while device is locked on BFU lockscreen
        if ! is_user_unlocked; then
            return 0
        fi

        # Check 0: Functional live block & filesystem I/O health probe (immune to transient SDHCI/MSDC wakeup dmesg noise)
        local io_error=0
        blk_dev=$(_resolve_monitored_blk_dev)

        if [ -n "${blk_dev}" ] && mx_is_mounted "${SD_BASE}"; then
            if ! _probe_sd_io_healthy "${blk_dev}" "${SD_BASE}" 3; then
                log_error "[Watchdog] CRITICAL: Live block/filesystem I/O probe failed on ${blk_dev} (${SD_BASE})!"
                io_error=1
            fi
        fi

        if [ ${io_error} -eq 1 ]; then
            # Fast-path recovery if block only flipped READ-ONLY (no -EIO shutdown superblock)
            if [ -n "${blk_dev}" ] && [ -b "${blk_dev}" ] && grep -F " ${SD_BASE} " /proc/mounts 2>/dev/null | grep -qE "(^|[[:space:],])ro($|[[:space:],])"; then
                local _ro_hw_chk
                _ro_hw_chk=$(cat "/sys/block/$(_parent_disk_name "${blk_dev}")/ro" 2>/dev/null | tr -d '\r\n')
                if [ "${_ro_hw_chk}" != "1" ] && mount -o remount,rw "${SD_BASE}" 2>/dev/null; then
                    if _probe_sd_io_healthy "${blk_dev}" "${SD_BASE}" 1; then
                        log_info "[Watchdog] ${SD_BASE} recovered back to READ-WRITE instantly via remount!"
                        rm -f "/dev/.mountx_disk_error" 2>/dev/null
                        RECOVERY_STREAK=0
                        RO_RECOVERY_ATTEMPTED=0
                        return 0
                    fi
                fi
            fi

            # Hold APPLY_LOCK_FILE BEFORE detaching namespaces so companion app never races mid-recovery
            echo "${WATCHDOG_SELF_PID}" > "${APPLY_LOCK_FILE}" 2>/dev/null
            log_warn "[Watchdog] Detaching all namespace mounts on ${blk_dev:-unknown} & ${SD_BASE} to clear dead/shutdown superblock..."
            _mx_detach_block_namespaces "${blk_dev}" "${SD_BASE}"
            blk_dev=$(_resolve_monitored_blk_dev)
            if [ -n "${blk_dev}" ] && [ -b "${blk_dev}" ] && dd if="${blk_dev}" of=/dev/null bs=4096 count=1 2>/dev/null; then
                log_info "[Watchdog] Block device ${blk_dev} readable after superblock detach. Remounting ${SD_BASE} cleanly..."
                mount_sd
                if (mx_is_mounted "${SD_BASE}" && _probe_sd_io_healthy "${blk_dev}" "${SD_BASE}" 2) || _mx_try_safe_fsck_and_remount "${blk_dev}"; then
                    log_info "[Watchdog] SD_BASE superblock recovered writable & healthy on ${blk_dev}. Re-applying bind mounts..."
                    _mx_reapply_mounts_after_recovery
                    return 0
                fi
                _mx_detach_block_namespaces "${blk_dev}" "${SD_BASE}"
            fi
            rm -f "${APPLY_LOCK_FILE}" 2>/dev/null
            log_error "[Watchdog] CRITICAL: MicroSD hardware I/O error or physical detachment confirmed!"
            echo "io_error" > "/dev/.mountx_disk_error"
            (timeout 10 am broadcast -a app.mountx.ACTION_DISK_ERROR --es error "io_error" -p app.mountx >/dev/null 2>&1) &
            LAST_DISK_ATTACHED=0
            [ -n "${blk_dev}" ] && [ -b "${blk_dev}" ] && LAST_DISK_ATTACHED=1
            _mx_advance_recovery_backoff
            return 1
        fi

        # Check 1: Is SD_BASE still mounted?
        if ! mx_is_mounted "${SD_BASE}"; then
            if [ -z "${blk_dev}" ] || [ ! -b "${blk_dev}" ]; then
                if [ -n "${blk_dev}" ] || grep -q "|APP_PACKAGE|" "${MOUNTPOINTS_FILE}" 2>/dev/null; then
                    log_warn "[Watchdog] SD_BASE ${SD_BASE} unmounted and block device ${blk_dev:-unknown} not found. Disk detached."
                    echo "io_error" > "/dev/.mountx_disk_error"
                    (timeout 10 am broadcast -a app.mountx.ACTION_DISK_ERROR --es error "io_error" -p app.mountx >/dev/null 2>&1) &
                    LAST_DISK_ATTACHED=0
                    _mx_advance_recovery_backoff
                    return 1
                fi
            else
                echo "${WATCHDOG_SELF_PID}" > "${APPLY_LOCK_FILE}" 2>/dev/null
                log_warn "[Watchdog] SD_BASE ${SD_BASE} lost! Re-mounting ${blk_dev}..."
                mount_sd
                if mx_is_mounted "${SD_BASE}"; then
                    log_info "[Watchdog] SD_BASE re-mounted. Re-applying bind mounts..."
                    _mx_reapply_mounts_after_recovery
                    return 0
                else
                    rm -f "${APPLY_LOCK_FILE}" 2>/dev/null
                    log_error "[Watchdog] SD_BASE re-mount failed."
                    _mx_advance_recovery_backoff
                    return 1
                fi
            fi
        fi

        # Check 1b: Kernel Read-Only Remount Protection Probe (attempt single safe RW recovery unless hw write-protected)
        if mx_is_mounted "${SD_BASE}"; then
            if grep -F " ${SD_BASE} " /proc/mounts 2>/dev/null | grep -qE "(^|[[:space:],])ro($|[[:space:],])"; then
                local _ro_hw=""
                if [ -n "${blk_dev}" ]; then
                    _ro_hw=$(cat "/sys/block/$(_parent_disk_name "${blk_dev}")/ro" 2>/dev/null | tr -d '\r\n')
                fi
                if [ "${_ro_hw}" != "1" ] && [ "${RO_RECOVERY_ATTEMPTED}" -eq 0 ]; then
                    RO_RECOVERY_ATTEMPTED=1
                    if mount -o remount,rw "${SD_BASE}" 2>/dev/null && _probe_sd_io_healthy "${blk_dev}" "${SD_BASE}" 1; then
                        log_info "[Storage] ${SD_BASE} recovered back to READ-WRITE instantly via remount!"
                        rm -f "/dev/.mountx_disk_error" 2>/dev/null
                        RECOVERY_STREAK=0
                        RO_RECOVERY_ATTEMPTED=0
                        return 0
                    fi
                    echo "${WATCHDOG_SELF_PID}" > "${APPLY_LOCK_FILE}" 2>/dev/null
                    log_warn "[Storage] ${SD_BASE} remounted READ-ONLY; detaching namespaces & attempting clean RW remount..."
                    _mx_detach_block_namespaces "${blk_dev}" "${SD_BASE}"
                    mount_sd
                    if (mx_is_mounted "${SD_BASE}" && _probe_sd_io_healthy "${blk_dev}" "${SD_BASE}" 2) || _mx_try_safe_fsck_and_remount "${blk_dev}"; then
                        log_info "[Storage] ${SD_BASE} recovered back to READ-WRITE cleanly!"
                        _mx_reapply_mounts_after_recovery
                        return 0
                    fi
                    rm -f "${APPLY_LOCK_FILE}" 2>/dev/null
                fi
                log_error "[Storage] CRITICAL: MicroSD ${SD_BASE} has been remounted READ-ONLY by kernel! Writes will fail with EROFS."
                echo "ro_error" > "/dev/.mountx_disk_error"
                (timeout 10 am broadcast -a app.mountx.ACTION_DISK_ERROR --es error "ro_error" -p app.mountx >/dev/null 2>&1) &
            else
                rm -f "/dev/.mountx_disk_error" 2>/dev/null
                RECOVERY_STREAK=0
                RO_RECOVERY_ATTEMPTED=0
            fi
        fi

        # ── Check 2: Zygote soft restart / Vold emulated remount / MediaProvider restart ──
        local soft_restart=0
        local need_framework_wait=0
        local zygote_pid_now curr_em_gen curr_mp_pid
        zygote_pid_now=$(_get_zygote_pid)
        curr_em_gen=$(_emulated_root_generation)
        curr_mp_pid=$(_get_mediaprovider_pid)

        if [ "${PENDING_SOFT_RESTART}" = "1" ]; then
            soft_restart=1
            need_framework_wait=1
            PENDING_SOFT_RESTART=0
        fi
        if [ -n "${zygote_pid_now}" ] && [ -n "${ZYGOTE_PID_LAST}" ] && \
           [ "${zygote_pid_now}" != "${ZYGOTE_PID_LAST}" ]; then
            log_warn "[Watchdog] Zygote PID changed: ${ZYGOTE_PID_LAST} → ${zygote_pid_now}. Soft restart detected!"
            ZYGOTE_PID_LAST="${zygote_pid_now}"
            soft_restart=1
            need_framework_wait=1
        fi
        if [ -n "${LAST_EMULATED_GEN}" ] && [ -n "${curr_em_gen}" ] && [ "${curr_em_gen}" != "${LAST_EMULATED_GEN}" ]; then
            log_warn "[Watchdog] Emulated storage mount root changed (Vold/FUSE remount). Re-syncing mounts!"
            soft_restart=1
        fi
        if [ -n "${LAST_MP_PID}" ] && [ -n "${curr_mp_pid}" ] && [ "${curr_mp_pid}" != "${LAST_MP_PID}" ]; then
            log_warn "[Watchdog] MediaProvider PID changed: ${LAST_MP_PID} → ${curr_mp_pid}. Re-syncing mounts!"
            soft_restart=1
        fi

        if [ ${soft_restart} -eq 1 ]; then
            log_warn "[Watchdog] Subsystem/storage restart recovery triggered. Waiting for storage settlement..."
            echo "${WATCHDOG_SELF_PID}" > "${APPLY_LOCK_FILE}" 2>/dev/null
            [ ${need_framework_wait} -eq 1 ] && wait_for_framework
            wait_for_emulated_storage_settled 30

            ZYGOTE_PID_LAST=$(_get_zygote_pid)
            LAST_EMULATED_GEN=$(_emulated_root_generation)
            LAST_MP_PID=$(_get_mediaprovider_pid)

            if ! mx_is_mounted "${SD_BASE}"; then
                mount_sd
            fi

            cleanup_stale_mounts
            apply_io_tweaks
            apply_all_mounts
            sleep 3
            verify_post_mount_canaries "Recovery"
            LAST_EMULATED_GEN=$(_emulated_root_generation)
            LAST_MP_PID=$(_get_mediaprovider_pid)
            rm -f "${APPLY_LOCK_FILE}" 2>/dev/null

            # Notify the app to re-sync its mount status
            (timeout 10 am broadcast -a app.mountx.ACTION_SYNC_NOTIFICATION -p app.mountx >/dev/null 2>&1) &
            log_info "[Watchdog] Subsystem/storage restart recovery complete. All mounts re-applied."
            CONSECUTIVE_OK=0
            CHECK_INTERVAL=60
            return 0
        fi

        [ -n "${curr_em_gen}" ] && LAST_EMULATED_GEN="${curr_em_gen}"
        [ -n "${curr_mp_pid}" ] && LAST_MP_PID="${curr_mp_pid}"

        # Check 3: Repair dead/stacked ENOTCONN FUSE or occluded parents before verifying primary storage
        mx_repair_dead_fuse_and_parents
        if ! is_emulated_storage_ready; then
            log_warn "[Watchdog] Primary emulated storage not yet ready. Waiting for storage daemon..."
            return 0
        fi

        # Probe 4: Authoritative Multi-Namespace + Inode/Canary + Zygote Live Probe
        local shadowed_found=0
        local _verified_pkgs=""
        local active_apps=0
        local _wd_skip_pkgs
        _wd_skip_pkgs=$(_mx_collect_skipped_pkgs)
        if [ -f "${MOUNTPOINTS_FILE}" ]; then
            while IFS='|' read -r _p _c _s _d _pm _u _uid || [ -n "${_p}" ]; do
                case "${_p}" in ''|\#*) continue ;; esac
                _p=$(echo "${_p}" | tr -d '\r')
                case "${_wd_skip_pkgs}" in *" ${_p} "*) continue ;; esac
                _c=$(echo "${_c}" | tr -d '\r')
                _s=$(echo "${_s}" | tr -d '\r')
                _d=$(echo "${_d}" | tr -d '\r')
                _uid=$(echo "${_uid}" | tr -d '\r')
                [ -z "${_uid}" ] && _uid="0"
                # Only probe mounts whose source actually exists (ignore disconnected USB drives)
                [ ! -e "${_s}" ] && continue
                # Never thrash on uninstalled/broken ghost packages or missing APK sources
                if [ "${_c}" != "CUSTOM" ] && [ -n "${_p}" ]; then
                    if ! is_package_installed "${_p}"; then
                        continue
                    fi
                fi
                local _probe_s="${_s}"
                local _check_t="${_d}"
                local _rel=""
                if [ "${_c}" = "APP_PACKAGE" ] && [ -n "${_p}" ]; then
                    local _live_apk _live_dir _probe_abi
                    _live_apk=$(pm path "${_p}" 2>/dev/null | head -n 1 | sed -n 's/^package://p' | tr -d '\r')
                    case "${_live_apk}" in
                        /*==deleted==*|*/vmdl*) continue ;;
                        /*)
                            _live_dir=$(dirname "${_live_apk}")
                            case "${_d}" in
                                */lib/*|*/lib)
                                    _probe_abi=$(_detect_lib_abi "${_s}" "${_live_dir}" "${_d}")
                                    if [ -n "${_probe_abi}" ]; then
                                        [ -d "${_s}/${_probe_abi}" ] && _probe_s="${_s}/${_probe_abi}"
                                        _check_t="${_live_dir}/lib/${_probe_abi}"
                                    else
                                        _check_t="${_live_dir}/$(echo "${_d}" | sed 's|.*/lib|lib|')"
                                    fi
                                    [ -z "$(find "${_probe_s}" -maxdepth 2 -name '*.so' 2>/dev/null | head -n 1)" ] && continue
                                    ;;
                                *)
                                    _check_t="${_live_dir}"
                                    [ ! -f "${_probe_s}/base.apk" ] || [ ! -s "${_probe_s}/base.apk" ] && continue
                                    ;;
                            esac
                            ;;
                        *)
                            # When PMS is online and has no pm path, skip APP_PACKAGE probe (avoid infinite watchdog loop)
                            service check package 2>/dev/null | grep -q "found" && continue
                            case "${_d}" in
                                */lib/*|*/lib)
                                    _probe_abi="${_d##*/lib/}"
                                    _probe_abi="${_probe_abi%%/*}"
                                    [ "${_probe_abi}" != "${_d}" ] && [ -n "${_probe_abi}" ] && [ -d "${_s}/${_probe_abi}" ] && _probe_s="${_s}/${_probe_abi}"
                                    [ -z "$(find "${_probe_s}" -maxdepth 2 -name '*.so' 2>/dev/null | head -n 1)" ] && continue
                                    ;;
                                *)
                                    [ ! -f "${_probe_s}/base.apk" ] || [ ! -s "${_probe_s}/base.apk" ] && continue
                                    ;;
                            esac
                            ;;
                    esac
                elif [ "${_c}" = "PRIVATE_INTERNAL" ] || [ "${_c}" = "CACHE_SHADERS" ]; then
                    if ! mx_is_mounted "${_d}" && [ -d "${_d}" ]; then
                        local _wd_dst_kb
                        _wd_dst_kb=$(du -sk "${_d}" 2>/dev/null | cut -f1)
                        if ! _mx_dir_has_real_files "${_probe_s}" 32 && [ "${_wd_dst_kb:-0}" -gt 96 ] 2>/dev/null; then
                            continue
                        fi
                    fi
                fi
                case "${_check_t}" in
                    *==deleted==*|*/vmdl*) continue ;;
                esac
                [ ! -e "${_probe_s}" ] && continue
                case "${_check_t}" in
                    */Android/*|/data/media/*|/storage/emulated/*|/sdcard/*)
                        if echo "${_check_t}" | grep -q "/Android/"; then
                            _rel="Android/"$(echo "${_check_t}" | sed 's|.*/Android/||')
                        else
                            _rel=$(echo "${_check_t}" | sed 's|^/sdcard/||;s|^/storage/emulated/[0-9]*/||;s|^/data/media/[0-9]*/||;s|^/mnt/user/[0-9]*/emulated/[0-9]*/||')
                        fi
                        ;;
                esac
                if mx_verify_target_live "${_c}" "${_probe_s}" "${_check_t}" "${_rel}" "${_uid}"; then
                    case " ${_verified_pkgs} " in
                        *" ${_p} "*) ;;
                        *) _verified_pkgs="${_verified_pkgs} ${_p}"; active_apps=$((active_apps + 1)) ;;
                    esac
                else
                    log_warn "[Watchdog] Mount missing/shadowed/Zygote-desynced at ${_check_t} for ${_p} (${_c}). Re-applying mounts..."
                    shadowed_found=1
                    break
                fi
            done < "${MOUNTPOINTS_FILE}"
        fi
        if [ ${shadowed_found} -eq 1 ]; then
            echo "${WATCHDOG_SELF_PID}" > "${APPLY_LOCK_FILE}" 2>/dev/null
            apply_all_mounts
            sleep 2
            LAST_EMULATED_GEN=$(_emulated_root_generation)
            LAST_MP_PID=$(_get_mediaprovider_pid)
            rm -f "${APPLY_LOCK_FILE}" 2>/dev/null
            (timeout 10 am broadcast -a app.mountx.ACTION_SYNC_NOTIFICATION -p app.mountx >/dev/null 2>&1) &
            CONSECUTIVE_OK=0
            CHECK_INTERVAL=60
            return 0
        fi

        CONSECUTIVE_OK=$((CONSECUTIVE_OK + 1))
        # If disk is currently detached, poll rapidly (4s) so hotplug is caught instantly
        if [ ${current_disk_attached} -eq 0 ]; then
            CHECK_INTERVAL=4
        elif [ ${CONSECUTIVE_OK} -ge 5 ] && [ ${CHECK_INTERVAL} -lt ${MAX_INTERVAL} ]; then
            CHECK_INTERVAL=$((CHECK_INTERVAL + 60))
            [ ${CHECK_INTERVAL} -gt ${MAX_INTERVAL} ] && CHECK_INTERVAL=${MAX_INTERVAL}
            log_info "[Watchdog] System stable (${CONSECUTIVE_OK} checks OK). Interval → ${CHECK_INTERVAL}s."
        fi

        # Self-heal any verified mounted app unexpectedly left suspended in launcher
        for _vp in ${_verified_pkgs}; do
            [ -z "${_vp}" ] && continue
            if dumpsys package "${_vp}" 2>/dev/null | grep -q 'suspended=true'; then
                pm unsuspend --user 0 "${_vp}" >/dev/null 2>&1 || pm unsuspend "${_vp}" >/dev/null 2>&1 || true
                log_info "[Watchdog] Auto-unsuspended verified mounted app: ${_vp}"
            fi
        done

        local free_mb
        free_mb=$(df -Pk "${SD_BASE}" 2>/dev/null | awk 'NR==2 {print int($4/1024)}')
        log_info "[Watchdog] Heartbeat #${CONSECUTIVE_OK} OK (Zygote64=${ZYGOTE_PID_LAST:-N/A}, Disk=${SD_BASE} [${FS_TYPE}, free=${free_mb:-0}MB], VerifiedApps=${active_apps:-0})."
        if [ -n "${free_mb}" ] && [ "${free_mb}" -lt 500 ] 2>/dev/null; then
            log_warn "[Storage] Low storage alert: only ${free_mb}MB free on ${SD_BASE}!"
        fi
    }

    # ── High-Responsiveness Timer Loop with Sliced Sleep & Deep Sleep Wakeup ──
    # Sleeps in 5s monotonic slices while rapidly monitoring Deep Sleep resume
    # (wall-clock jump), Zygote PID, MediaProvider/Vold emulated mount-root
    # generation, user unlock, and physical disk attachment (<= 5s recovery).
    log_info "[Watchdog] Using sliced sleep loop (5s slice, ${CHECK_INTERVAL}s health check)."
    local slice=5
    local elapsed=0
    while true; do
        local _before_ts _after_ts _wall_delta
        _before_ts=$(date +%s)
        sleep "${slice}"
        _after_ts=$(date +%s)
        _wall_delta=$((_after_ts - _before_ts))
        elapsed=$((elapsed + slice))

        # Check 0: Deep Sleep / Suspend-to-RAM wakeup detection (CLOCK_MONOTONIC paused while RTC jumped)
        if [ "${_wall_delta}" -ge 15 ] 2>/dev/null; then
            log_info "[Watchdog] Deep sleep resume detected (wall jump=${_wall_delta}s). Verifying mounts after 2s settle..."
            sleep 2
            elapsed=0
            check_mounts
            continue
        fi

        # Check 1: Rapid Zygote PID change detection (soft reboot / framework restart)
        local fast_zygote
        fast_zygote=$(_get_zygote_pid)
        if [ -n "${fast_zygote}" ] && [ -n "${ZYGOTE_PID_LAST}" ] && [ "${fast_zygote}" != "${ZYGOTE_PID_LAST}" ]; then
            log_warn "[Watchdog] Immediate Zygote PID change detected: ${ZYGOTE_PID_LAST} -> ${fast_zygote}!"
            ZYGOTE_PID_LAST="${fast_zygote}"
            PENDING_SOFT_RESTART=1
            if is_user_unlocked; then
                elapsed=0
                check_mounts
            fi
            continue
        fi

        # Check 1b: Rapid User Unlock Detection (CE key unlocked + Vold FUSE settlement)
        if [ "${LAST_CE_AVAILABLE}" != "true" ] && is_ce_key_unlocked; then
            log_info "[Watchdog] User unlock detected (CE available). Waiting for Vold FUSE/sdcardfs settlement..."
            LAST_CE_AVAILABLE="true"
            PENDING_SOFT_RESTART=0
            echo "${WATCHDOG_SELF_PID}" > "${APPLY_LOCK_FILE}" 2>/dev/null
            wait_for_emulated_storage_settled 30
            ZYGOTE_PID_LAST=$(_get_zygote_pid)
            cleanup_stale_mounts
            apply_all_mounts
            touch /dev/.mountx_initial_mounted 2>/dev/null
            sleep 3
            verify_post_mount_canaries "Watchdog"
            LAST_EMULATED_GEN=$(_emulated_root_generation)
            LAST_MP_PID=$(_get_mediaprovider_pid)
            rm -f "${APPLY_LOCK_FILE}" 2>/dev/null
            (timeout 10 am broadcast -a app.mountx.ACTION_SYNC_NOTIFICATION -p app.mountx >/dev/null 2>&1) &
            CONSECUTIVE_OK=0
            elapsed=0
            continue
        fi

        # Check 1c: Rapid Vold/MediaProvider Emulated Root Remount Detection
        if is_user_unlocked && [ ! -f "/dev/.mountx_unmounted" ] && [ ! -f "/dev/.mountx_migrating" ]; then
            local _fast_em_gen _fast_mp
            _fast_em_gen=$(_emulated_root_generation)
            _fast_mp=$(_get_mediaprovider_pid)
            if [ -n "${LAST_EMULATED_GEN}" ] && [ -n "${_fast_em_gen}" ] && [ "${_fast_em_gen}" != "${LAST_EMULATED_GEN}" ]; then
                log_warn "[Watchdog] Emulated storage mount root changed immediately! Recovering mounts..."
                elapsed=0
                check_mounts
                continue
            fi
            if [ -n "${LAST_MP_PID}" ] && [ -n "${_fast_mp}" ] && [ "${_fast_mp}" != "${LAST_MP_PID}" ]; then
                log_warn "[Watchdog] MediaProvider PID changed immediately (${LAST_MP_PID} -> ${_fast_mp})! Verifying mounts..."
                elapsed=0
                check_mounts
                continue
            fi
            [ -z "${LAST_EMULATED_GEN}" ] && [ -n "${_fast_em_gen}" ] && LAST_EMULATED_GEN="${_fast_em_gen}"
            [ -z "${LAST_MP_PID}" ] && [ -n "${_fast_mp}" ] && LAST_MP_PID="${_fast_mp}"
        fi

        # Check 2: Rapid physical disk attachment detection (hotplug & post-sleep node re-enumeration)
        local _curr_nodes
        _curr_nodes=$(echo /dev/block/mmcblk*p* /dev/block/sd[a-z][0-9]* 2>/dev/null)
        if [ "${_curr_nodes}" != "${LAST_DISK_NODES_SIG}" ]; then
            LAST_DISK_NODES_SIG="${_curr_nodes}"
            blk_dev=$(_resolve_monitored_blk_dev)
        fi
        local fast_disk=0
        [ -n "${blk_dev}" ] && [ -b "${blk_dev}" ] && fast_disk=1
        if [ -n "${blk_dev}" ] && [ ${fast_disk} -ne ${LAST_DISK_ATTACHED} ]; then
            log_info "[Watchdog] Immediate disk attachment change detected: ${LAST_DISK_ATTACHED} -> ${fast_disk}!"
            elapsed=0
            check_mounts
            continue
        fi

        # Check 3: Full scheduled integrity & I/O check
        if [ ${elapsed} -ge ${CHECK_INTERVAL} ] || [ "${PENDING_SOFT_RESTART}" = "1" ]; then
            elapsed=0
            check_mounts
        fi
    done
}

# ── Safe-Uninstall Guard ──────────────────────────────────────────────────────
check_safe_uninstall() {
    if ! pm path app.mountx >/dev/null 2>&1 && ! pm path app.mountx.debug >/dev/null 2>&1; then
        log_warn "MountX app is not installed! Safe-Uninstall protocol triggered."
        for m in $(grep "${SD_BASE}" /proc/mounts 2>/dev/null | cut -d' ' -f2); do
            if [ "${m}" != "${SD_BASE}" ]; then
                umount -l "${m}" 2>/dev/null
            fi
        done
        chmod 775 /data/media/0/Android/data 2>/dev/null
        chmod 775 /data/media/0/Android/obb 2>/dev/null
        restorecon -R /data/media/0/Android 2>/dev/null
        touch "${MODULE_DIR}/disable" 2>/dev/null
        rm -f "${PID_FILE}" "${BOOT_FLAG_FILE}" 2>/dev/null
        exit 0
    fi
}

# ── Main ──────────────────────────────────────────────────────────────────────
main() {
    rm -f /dev/.mountx_unmounted 2>/dev/null

    local is_live_reload=0
    [ -f "${BOOT_FLAG_FILE}" ] && is_live_reload=1

    if [ ${is_live_reload} -eq 0 ]; then
        echo "$$" > "${BOOT_ACTIVE_FILE}" 2>/dev/null
        wait_for_boot
        check_safe_uninstall
        load_config
        mount_sd
        cleanup_stale_mounts
        apply_io_tweaks
    else
        log_info "MountX running in live-reload mode (system already booted). Preserving active mounts."
        check_safe_uninstall
        load_config
        if ! mx_is_mounted "${SD_BASE}"; then
            mount_sd
        else
            mx_make_rprivate "${SD_BASE}"
        fi
        apply_io_tweaks
    fi

    # Guard against cold-boot Vold/SDHCI reset leaving SD_BASE unmounted or in shutdown -EIO superblock
    if is_ce_key_unlocked; then
        if [ ${is_live_reload} -eq 0 ]; then
            log_info "[Boot] User storage unlocked (CE ready). Waiting for Vold FUSE/sdcardfs settlement..."
            wait_for_emulated_storage_settled 30
        fi
        local _boot_blk _boot_wait=0
        _boot_blk=$(_resolve_monitored_blk_dev)
        # Wait up to 6s if Vold is still re-enumerating external MMC/USB node after settlement
        while [ ${_boot_wait} -lt 6 ]; do
            if [ -n "${_boot_blk}" ] && [ -b "${_boot_blk}" ] && dd if="${_boot_blk}" of=/dev/null bs=4096 count=1 2>/dev/null; then
                break
            fi
            sleep 1
            _boot_blk=$(_resolve_monitored_blk_dev)
            _boot_wait=$((_boot_wait + 1))
        done
        if [ -n "${_boot_blk}" ] && [ -b "${_boot_blk}" ]; then
            if ! mx_is_mounted "${SD_BASE}" || ! _probe_sd_io_healthy "${_boot_blk}" "${SD_BASE}" 2; then
                log_warn "[Boot] ${SD_BASE} unmounted or non-writable after Vold settle; cleanly resetting superblock & remounting..."
                for _zp in 1 $(_mx_zygote_mnt_pids); do
                    [ -r "/proc/${_zp}/mounts" ] || continue
                    for _bm in $(awk -v b="${_boot_blk}" -v sb="${SD_BASE%/}" '$1 == b || $2 == sb {print length($2), $2}' "/proc/${_zp}/mounts" 2>/dev/null | sort -rn -k1,1 | cut -d' ' -f2- | awk '!seen[$0]++'); do
                        if [ "${_zp}" = "1" ]; then
                            umount "${_bm}" 2>/dev/null || umount -l "${_bm}" 2>/dev/null || true
                        elif [ -d "/proc/${_zp}/ns" ]; then
                            nsenter --mount="/proc/${_zp}/ns/mnt" -- /system/bin/sh -c 'umount "$1" 2>/dev/null || umount -l "$1" 2>/dev/null' _ "${_bm}" 2>/dev/null || true
                        fi
                    done
                done
                mount_sd
            fi
        fi
    fi

    # Never create internal stub directories under unmounted SD_BASE! Only prepare MountX layout when SD_BASE is actively mounted & writable.
    local _layout_blk
    _layout_blk=$(_resolve_monitored_blk_dev)
    if mx_is_mounted "${SD_BASE}" && _probe_sd_io_healthy "${_layout_blk:-${SD_BLOCK}}" "${SD_BASE}" 1; then
        mkdir -p "${SD_BASE}/MountX/Android/data" \
                 "${SD_BASE}/MountX/Android/obb" \
                 "${SD_BASE}/MountX/Android/media" \
                 "${SD_BASE}/MountX/containers" 2>/dev/null
        rm -f "${SD_BASE}/MountX/Android/.nomedia" 2>/dev/null
        touch "${SD_BASE}/MountX/Android/data/.nomedia" 2>/dev/null
        touch "${SD_BASE}/MountX/Android/obb/.nomedia" 2>/dev/null

        # Auto-sanitize any misplaced legacy rogue subfolders
        for _cat in data obb; do
            for _p in "${SD_BASE}/MountX/Android/${_cat}"/*; do
                [ ! -d "${_p}" ] && continue
                rm -rf "${_p}/Android" 2>/dev/null
                _pn=$(basename "${_p}")
                [ -d "${_p}/${_pn}" ] && rm -rf "${_p}/${_pn}" 2>/dev/null
            done
        done
    else
        log_warn "[Storage] Skipping SD_BASE layout creation: ${SD_BASE} is not mounted RW."
    fi

    # Never mount user/app directories or inject into Zygote while device is on lockscreen (BFU)
    if [ ${is_live_reload} -eq 1 ]; then
        echo "$$" > "${APPLY_LOCK_FILE}" 2>/dev/null
        apply_all_mounts
        touch /dev/.mountx_initial_mounted "${BOOT_FLAG_FILE}" 2>/dev/null
        log_info "MountX live-reload completed at ${BOOT_FLAG_FILE}."
        (timeout 10 am broadcast -a app.mountx.ACTION_SYNC_NOTIFICATION -p app.mountx >/dev/null 2>&1) &
    elif is_ce_key_unlocked; then
        echo "$$" > "${APPLY_LOCK_FILE}" 2>/dev/null
        apply_all_mounts
        touch /dev/.mountx_initial_mounted 2>/dev/null
        sleep 3
        verify_post_mount_canaries "Boot"
        touch "${BOOT_FLAG_FILE}" 2>/dev/null
        log_info "MountX boot marker touched at ${BOOT_FLAG_FILE}. Service completed."
        (timeout 10 am broadcast -a app.mountx.ACTION_SYNC_NOTIFICATION -p app.mountx >/dev/null 2>&1) &
    else
        rm -f /dev/.mountx_initial_mounted 2>/dev/null
        log_info "[Boot] Device is on lockscreen (FBE CE locked). Deferring app mounts until screen unlock to protect system stability."
        touch "${BOOT_FLAG_FILE}" 2>/dev/null
    fi

    # Run asynchronous fstrim in background to ensure peak flash controller wear-leveling & speed
    (sleep 300 && fstrim "${SD_BASE}" 2>/dev/null) &

    # Whitelist MountX in system Doze/battery optimization so background transfers never get killed
    dumpsys deviceidle whitelist +app.mountx 2>/dev/null

    # Single-Instance Supervisor Guard: terminate previous watchdog supervisor before starting new one
    if [ -f "${PID_FILE}" ]; then
        local _prev_pid
        _prev_pid=$(cat "${PID_FILE}" 2>/dev/null | tr -d ' \r\t\n')
        if [ -n "${_prev_pid}" ] && [ "${_prev_pid}" != "$$" ] && [ -d "/proc/${_prev_pid}" ]; then
            log_info "[Supervisor] Stopping previous watchdog supervisor (PID=${_prev_pid})..."
            kill -9 "${_prev_pid}" 2>/dev/null || true
            pkill -P "${_prev_pid}" 2>/dev/null || true
        fi
    fi
    for _orphan in $(pgrep -f "${MODULE_DIR}/service.sh" 2>/dev/null); do
        if [ "${_orphan}" != "$$" ] && [ "${_orphan}" != "$PPID" ]; then
            kill -9 "${_orphan}" 2>/dev/null || true
        fi
    done

# ── Immortal Watchdog Supervisor ──────────────────────────────────────────────
watchdog_supervisor() {
    WATCHDOG_SELF_PID=$(sh -c 'echo $PPID' 2>/dev/null)
    [ -z "${WATCHDOG_SELF_PID}" ] && WATCHDOG_SELF_PID="$$"
    log_info "[Supervisor] Starting immortal watchdog supervisor (PID=${WATCHDOG_SELF_PID})..."
    echo "${WATCHDOG_SELF_PID}" > "${PID_FILE}" 2>/dev/null
    while true; do
        mount_watchdog
        local exit_code=$?
        log_warn "[Supervisor] mount_watchdog exited with code ${exit_code}! Respawning in 3s..."
        sleep 3
    done
}

    # Launch immortal watchdog supervisor in background (event-driven, minimal battery impact)
    watchdog_supervisor &
    log_info "Mount watchdog supervisor launched in background (PID=$!)"
    rm -f "${APPLY_LOCK_FILE}" "${BOOT_ACTIVE_FILE}" 2>/dev/null
}

main
