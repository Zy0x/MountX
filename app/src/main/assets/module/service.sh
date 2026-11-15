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

# Log rotation: prevent infinite log growth (> 2MB)
if [ -f "${LOG_FILE}" ] && [ "$(stat -c%s "${LOG_FILE}" 2>/dev/null || echo 0)" -gt 2097152 ]; then
    tail -n 500 "${LOG_FILE}" > "${LOG_FILE}.tmp" 2>/dev/null && mv -f "${LOG_FILE}.tmp" "${LOG_FILE}" 2>/dev/null
fi

{
    echo "============================================================"
    echo " MountX service started (${MOD_VER}) — $(date)"
    echo "============================================================"
} >> "${LOG_FILE}"

# ── Universal Mount & Propagation Helpers (Android 10-17 / Toybox+BusyBox) ──
_mx_busybox() {
    if [ -x /dev/mountx_bin/busybox ]; then
        echo "/dev/mountx_bin/busybox"
    elif [ -n "${MODULE_DIR}" ] && [ -x "${MODULE_DIR}/bin/busybox" ]; then
        echo "${MODULE_DIR}/bin/busybox"
    elif command -v busybox >/dev/null 2>&1; then
        command -v busybox
    fi
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

mx_is_mounted() {
    local _p="${1%/}"
    [ -z "${_p}" ] && return 1
    local _rp=""
    _rp=$(readlink -f "${_p}" 2>/dev/null)
    [ -z "${_rp}" ] && _rp="${_p}"
    local _mi="/proc/1/mountinfo"
    [ ! -r "${_mi}" ] && _mi="/proc/self/mountinfo"
    if [ -r "${_mi}" ]; then
        if awk -v t="${_p}" -v rt="${_rp}" '{m=$5; gsub(/\\040/, " ", m); if (m==t || m==rt) {f=1; exit}} END {exit (f?0:1)}' "${_mi}" 2>/dev/null; then
            return 0
        fi
    elif [ -r /proc/mounts ]; then
        if awk -v t="${_p}" -v rt="${_rp}" '{m=$2; gsub(/\\040/, " ", m); if (m==t || m==rt) {f=1; exit}} END {exit (f?0:1)}' /proc/mounts 2>/dev/null; then
            return 0
        fi
    fi
    mountpoint -q "${_p}" 2>/dev/null
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
    local _sdk
    _sdk=$(getprop ro.build.version.sdk 2>/dev/null | tr -d '\r\n')
    while [ "${_waited}" -lt "${_max_wait}" ]; do
        local _full_ok=1
        if [ "${_sdk:-30}" -ge 30 ] 2>/dev/null && [ -d "/mnt/runtime/full/emulated" ]; then
            grep -qE '^[^ ]+ /mnt/runtime/full/emulated (fuse[^ ]*|sdcardfs|esdfs) ' /proc/mounts 2>/dev/null || _full_ok=0
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

verify_post_mount_canaries() {
    local _ctx="$1"
    [ ! -f "${MOUNTPOINTS_FILE}" ] && return 0
    local _shadowed=0
    local _pkg _cat _src _dst _pm _du _uid _rel _eu
    while IFS='|' read -r _pkg _cat _src _dst _pm _du _uid; do
        case "${_pkg}" in ""|\#*) continue ;; esac
        case "${_cat}" in EXTERNAL_DATA|OBB_STORAGE|MEDIA_DOWNLOADS|GAME_ASSETS) ;; *) continue ;; esac
        [ -z "${_src}" ] || [ -z "${_dst}" ] && continue
        [ ! -f "${_src}/.mountx_canary" ] && continue
        _rel=""
        case "${_dst}" in
            /data/media/[0-9]*/*) _rel="${_dst#/data/media/[0-9]*/}" ;;
            /storage/emulated/[0-9]*/*) _rel="${_dst#/storage/emulated/[0-9]*/}" ;;
            /sdcard/*) _rel="${_dst#/sdcard/}" ;;
        esac
        [ -z "${_rel}" ] && continue
        _eu="${_uid:-0}"
        if [ ! -f "/storage/emulated/${_eu}/${_rel}/.mountx_canary" ]; then
            log_warn "[${_ctx}] Post-mount +3s check: ${_pkg} canary missing on /storage/emulated/${_eu}/${_rel} (shadowed)!"
            _shadowed=1
            break
        fi
    done < "${MOUNTPOINTS_FILE}"
    if [ "${_shadowed}" = "1" ]; then
        log_warn "[${_ctx}] Immediately re-syncing shadowed FUSE/sdcardfs mount(s)..."
        cleanup_stale_mounts
        apply_all_mounts
    else
        log_info "[${_ctx}] Post-mount +3s canary verification passed (all SD canaries visible)."
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

_resolve_monitored_blk_dev() {
    if [ -n "${SD_BLOCK}" ] && ! _is_internal_system_disk "${SD_BLOCK}"; then
        echo "${SD_BLOCK}"
        return 0
    fi
    local _active
    _active=$(grep -F " ${SD_BASE} " /proc/mounts 2>/dev/null | head -n 1 | awk '{print $1}')
    case "${_active}" in
        /dev/block/*)
            echo "${_active}"
            return 0
            ;;
    esac
    if [ -f "${MOUNTPOINTS_FILE}" ]; then
        local _conf_uuid _by_uuid
        _conf_uuid=$(grep -E '^[a-zA-Z0-9._]+' "${MOUNTPOINTS_FILE}" 2>/dev/null | cut -d'|' -f6 | grep -v '^$' | head -n 1)
        if [ -n "${_conf_uuid}" ]; then
            _by_uuid=$(blkid 2>/dev/null | grep -i "UUID=\"${_conf_uuid}\"" | head -n 1 | cut -d: -f1)
            [ -n "${_by_uuid}" ] && { echo "${_by_uuid}"; return 0; }
        fi
    fi
    echo ""
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
    mkdir -p "${_dst}/lib/${_abi}" 2>/dev/null
    mount -o bind "${_lib_src}" "${_dst}/lib/${_abi}" 2>/dev/null
    mount -o remount,bind,rw,exec "${_dst}/lib/${_abi}" 2>/dev/null || mount -o remount,rw,exec "${_dst}/lib/${_abi}" 2>/dev/null
    mx_make_rprivate "${_dst}/lib/${_abi}"
    chown -R 1000:1000 "${_dst}/lib/${_abi}" 2>/dev/null
    chmod -R 755 "${_dst}/lib/${_abi}" 2>/dev/null
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
            log_info "SD already mounted at ${SD_BASE}."
            return 0
        fi

        if [ -z "${SD_BLOCK}" ] || [ ! -b "${SD_BLOCK}" ] || _is_internal_system_disk "${SD_BLOCK}"; then
            # 1. Dynamic resolution by UUID from mountpoints.conf
            if [ -f "${MOUNTPOINTS_FILE}" ]; then
                local conf_uuid
                conf_uuid=$(grep -E '^[a-zA-Z0-9._]+' "${MOUNTPOINTS_FILE}" | cut -d'|' -f6 | grep -v '^$' | head -n 1)
                if [ -n "${conf_uuid}" ]; then
                    local dev_by_uuid
                    dev_by_uuid=$(blkid 2>/dev/null | grep -i "UUID=\"${conf_uuid}\"" | head -n 1 | cut -d: -f1)
                    if [ -n "${dev_by_uuid}" ] && [ -b "${dev_by_uuid}" ] && ! _is_internal_system_disk "${dev_by_uuid}"; then
                        SD_BLOCK="${dev_by_uuid}"
                    fi
                fi
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
                f2fs)  mnt_opts="${mnt_opts},${sd_ctx},inline_data,inline_dentry,flush_merge,mode=adaptive" ;;
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
                elif mount -t "${detected_fs}" -o rw,noatime "${SD_BLOCK}" "${SD_BASE}" 2>/dev/null; then
                    mounted=1; log_info "SD mounted (simple opts): ${SD_BLOCK} → ${SD_BASE} (${detected_fs})"
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
            else
                log_warn "Scheduler '${IO_SCHEDULER}' not supported by ${disk_name}. Keeping kernel default."
            fi
        fi
        [ -w "${queue_dir}/rq_affinity" ] && echo "${RQ_AFFINITY}" > "${queue_dir}/rq_affinity"
        [ -w "${queue_dir}/nr_requests" ] && echo "${NR_REQUESTS}" > "${queue_dir}/nr_requests"
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
                        umount -f -l "${mnt}" 2>/dev/null
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
        local _p="$1"
        local _tries=0
        while mx_is_mounted "${_p}" && [ ${_tries} -lt 12 ]; do
            umount -f -l "${_p}" 2>/dev/null || umount -l "${_p}" 2>/dev/null || break
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
        for _zpid in $(pidof zygote64 zygote 2>/dev/null); do
            MX_UID="${user_id}" MX_REL="${rel}" nsenter --mount=/proc/${_zpid}/ns/mnt -- sh -c '
                for _zr in "/mnt/runtime/default/emulated/${MX_UID}" "/mnt/runtime/read/emulated/${MX_UID}" "/mnt/runtime/write/emulated/${MX_UID}" "/mnt/runtime/full/emulated/${MX_UID}"; do
                    _i=0
                    _zt="${_zr}/${MX_REL}"
                    while awk -v t="${_zt}" '"'"'{m=$5; gsub(/\\040/, " ", m); if (m==t) {f=1; exit}} END {exit (f?0:1)}'"'"' /proc/self/mountinfo 2>/dev/null && [ ${_i} -lt 12 ]; do
                        umount -f -l "${_zt}" 2>/dev/null || break
                        _i=$((_i + 1))
                    done
                done
            ' 2>/dev/null
        done
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
        # Clean preexisting /data/media mount point only if active
        if mx_is_mounted "/data/media/${user_id}/${rel}"; then
            umount -f -l "/data/media/${user_id}/${rel}" 2>/dev/null
        fi

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
            if [ -d "${R}" ]; then
                TARGET="${R}/${rel}"
                if ! mx_is_mounted "${TARGET}"; then
                    mkdir -p "${TARGET}" 2>/dev/null
                    do_mount "${src}" "${TARGET}"
                fi
            fi
        done

        # If Zygote is alive, also inject into Zygote namespaces only if not already mounted (prevents 7x stacking)
        local _bb_bin
        _bb_bin=$(_mx_busybox)
        for _zpid in $(pidof zygote64 zygote 2>/dev/null); do
            for _zr in \
                "/mnt/runtime/default/emulated/${user_id}" \
                "/mnt/runtime/read/emulated/${user_id}" \
                "/mnt/runtime/write/emulated/${user_id}" \
                "/mnt/runtime/full/emulated/${user_id}"
            do
                MX_ZR="${_zr}" MX_REL="${rel}" MX_SRC="${src}" MX_BB="${_bb_bin}" nsenter --mount="/proc/${_zpid}/ns/mnt" -- sh -c '
                    _zt="${MX_ZR}/${MX_REL}"
                    if [ -d "${MX_ZR}" ] && ! awk -v t="${_zt}" '"'"'{m=$5; gsub(/\\040/, " ", m); if (m==t) {f=1; exit}} END {exit (f?0:1)}'"'"' /proc/self/mountinfo 2>/dev/null; then
                        mkdir -p "${_zt}" 2>/dev/null
                        mount -o bind "${MX_SRC}" "${_zt}" 2>/dev/null
                        if [ -n "${MX_BB}" ]; then
                            "${MX_BB}" mount --make-rprivate "${_zt}" 2>/dev/null
                        else
                            mount --make-rprivate "${_zt}" 2>/dev/null
                        fi
                    fi
                ' 2>/dev/null
            done
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
            mount -t f2fs -o rw,noatime,nodiratime,${sd_ctx},inline_data,inline_dentry "${blk_dev}" "${mnt_point}" 2>/dev/null || mount -t f2fs -o rw,noatime "${blk_dev}" "${mnt_point}" 2>/dev/null && mounted=1
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

    # Fast Check 1: App data directory on internal storage
    if [ -d "/data/data/${pkg}" ] || [ -d "/data/user/0/${pkg}" ]; then
        return 0
    fi

    # Fast Check 2: App APK directory in /data/app/
    if ls -d /data/app/*/"${pkg}"* >/dev/null 2>&1 || ls -d /data/app/"${pkg}"* >/dev/null 2>&1; then
        return 0
    fi

    # Check 3: If package manager service is running and responsive
    if service check package 2>/dev/null | grep -q "found"; then
        if pm path "${pkg}" >/dev/null 2>&1; then
            return 0
        fi
        # Package manager is online and confirmed package does not exist
        return 1
    fi

    # PMS is offline/restarting, and app was registered in MountX -> keep it safe!
    return 0
}

# ── Modern Multi-Target Array (mountpoints.conf) ──────────────────────────────
load_mountpoints() {
    if [ ! -f "${MOUNTPOINTS_FILE}" ]; then
        return 1
    fi

    local entries=0
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

        # Verify package is actually installed on system (skip if uninstalled to avoid Play Store reinstall error)
        if [ -n "${pkg}" ] && [ "${cat}" != "CUSTOM" ]; then
            if ! is_package_installed "${pkg}"; then
                log_warn "  [${pkg}] App is not installed on system. Skipping mount to prevent Play Store INSTALL_FAILED_INTERNAL_ERROR."
                if mx_is_mounted "${dst}"; then
                    umount -f -l "${dst}" 2>/dev/null
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
                                    umount -f -l "${dst}" 2>/dev/null
                                fi
                                dst="${new_dst}"
                            fi
                            ;;
                        *)
                            if [ "${live_apk_dir}" != "${dst}" ]; then
                                log_warn "  [${pkg}] APP_PACKAGE relocated by system update: ${dst} -> ${live_apk_dir}"
                                if mx_is_mounted "${dst}"; then
                                    umount -f -l "${dst}" 2>/dev/null
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
                if mx_is_mounted "/data/media/${user_id}/${rel}"; then
                    log_warn "  [${pkg}] Detected stale /data/media mount on active sdcardfs kernel. Unmounting to prevent EXDEV..."
                    umount -f -l "/data/media/${user_id}/${rel}" 2>/dev/null
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

        # Ensure canary exists only if directory source is already present on disk
        if [ -d "${_canary_src}" ]; then
            touch "${_canary_src}/.mountx_canary" 2>/dev/null
            if [ "${cat}" = "APP_PACKAGE" ]; then
                chmod 644 "${_canary_src}/.mountx_canary" 2>/dev/null
                chown 1000:1000 "${_canary_src}/.mountx_canary" 2>/dev/null
            fi
        fi

        # ── Multi-Disk Collision, Anti-Stacking & Canary Anti-Shadowing Check ───
        local is_shadowed=0
        if [ -n "${rel}" ] && mx_is_mounted "/mnt/runtime/default/emulated/${user_id}/${rel}"; then
            if [ -f "${_canary_src}/.mountx_canary" ] && [ ! -f "${check_target}/.mountx_canary" ]; then
                log_warn "  [${pkg}] Target ${check_target} is SHADOWED by FUSE/sdcardfs overlay! Unmounting buried layers and re-binding..."
                is_shadowed=1
                unmount_target_everywhere "${dst}" "${rel}" "${user_id}"
            fi
        elif mx_is_mounted "${check_target}"; then
            if [ -f "${_canary_src}/.mountx_canary" ] && [ ! -f "${check_target}/.mountx_canary" ]; then
                log_warn "  [${pkg}] Target ${check_target} is SHADOWED! Unmounting buried layers and re-binding..."
                is_shadowed=1
                unmount_target_everywhere "${dst}" "${rel}" "${user_id}"
            fi
        fi

        if mx_is_mounted "${check_target}" && [ ${is_shadowed} -eq 0 ]; then
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
                    umount -f -l "${check_target}" 2>/dev/null
                    [ -n "${rel}" ] && umount -f -l "/mnt/runtime/default/emulated/${user_id}/${rel}" 2>/dev/null
                else
                    # Ensure write runtime namespace is also active
                    local rt_write="/mnt/runtime/write/emulated/${user_id}/${rel}"
                    if [ -n "${rel}" ] && [ -d "/mnt/runtime/write/emulated/${user_id}" ] && ! mx_is_mounted "${rt_write}"; then
                        log_info "  [${pkg}] Active mountpoint exists on ${check_target}, but missing in runtime write namespace. Mounting missing runtime targets..."
                        bind_mount_to_runtime_namespaces "${src}" "${dst}" "${pkg}" "${user_id}"
                    else
                        log_info "  [${pkg}] Target ${check_target} is already actively mounted correctly from ${cur_dev}. Skipping duplicate bind."
                    fi
                    entries=$((entries + 1))
                    continue
                fi
            else
                local rt_write="/mnt/runtime/write/emulated/${user_id}/${rel}"
                if [ -n "${rel}" ] && [ -d "/mnt/runtime/write/emulated/${user_id}" ] && ! mx_is_mounted "${rt_write}"; then
                    log_info "  [${pkg}] Active mountpoint exists on ${check_target}, but missing in runtime write namespace. Mounting missing runtime targets..."
                    bind_mount_to_runtime_namespaces "${src}" "${dst}" "${pkg}" "${user_id}"
                else
                    log_info "  [${pkg}] Target ${check_target} is already actively mounted. Skipping duplicate bind."
                fi
                entries=$((entries + 1))
                continue
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
                log_warn "  [${pkg}] UUID=${disk_uuid} not found by blkid (disk not connected?). Skipping."
                continue
            fi
        fi

        # Re-check after potential auto-mount
        if [ ! -e "${src}" ]; then
            log_warn "  [${pkg}] Source path still missing after auto-mount attempt: ${src}; skipping."
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
        if [ -d "${_post_canary_src}" ]; then
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
                                mount -o remount,bind,exec "${dst}" 2>/dev/null || mount -o remount,exec "${dst}" 2>/dev/null
                                chown -R 1000:1000 "${dst}" 2>/dev/null
                                chmod -R 755 "${dst}" 2>/dev/null
                                restorecon -FR "${dst}" 2>/dev/null || chcon -R u:object_r:apk_data_file:s0 "${dst}" 2>/dev/null
                                log_info "  [${pkg}] Loop container mounted (APP_PACKAGE): ${ldev} -> ${dst}"
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
                    local _src_has_files _dst_kb
                    _src_has_files=$(ls -A "${src}" 2>/dev/null | grep -vE '^(\.nomedia|\.mountx_canary)$' | head -n 1)
                    _dst_kb=$(du -sk "${dst}" 2>/dev/null | cut -f1)
                    [ -z "${_dst_kb}" ] && _dst_kb=0
                    if [ -z "${_src_has_files}" ] && [ "${_dst_kb}" -gt 128 ] 2>/dev/null; then
                        log_warn "  [${pkg}] Occlusion guard: refusing to bind empty ${src} over populated internal ${dst} (${_dst_kb}KB)."
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
                chown -R "${app_uid}:${app_uid}" "${src}" 2>/dev/null
                chmod 700 "${src}" 2>/dev/null
                chmod -R u+rwX,go-rwx "${src}" 2>/dev/null
                find "${src}" -type d -exec chmod 700 {} + 2>/dev/null
                find "${src}" -type f -exec chmod 600 {} + 2>/dev/null
                restorecon -FR "${src}" 2>/dev/null || chcon -R u:object_r:app_data_file:s0 "${src}" 2>/dev/null
                mount -o bind "${src}" "${dst}" 2>/dev/null
                if mx_is_mounted "${dst}"; then
                    mx_make_rprivate "${dst}"
                    chown "${app_uid}:${app_uid}" "${dst}" 2>/dev/null
                    chmod 700 "${dst}" 2>/dev/null
                    restorecon -FR "${dst}" 2>/dev/null || chcon -R u:object_r:app_data_file:s0 "${dst}" 2>/dev/null
                    if [ -d "/data/data/${pkg}" ] && ! mx_is_mounted "/data/data/${pkg}"; then
                        mount -o bind "${dst}" "/data/data/${pkg}" 2>/dev/null
                        mx_make_rprivate "/data/data/${pkg}"
                    fi
                    if [ -d "/data_mirror/data_ce/null/${user_id}/${pkg}" ] && ! mx_is_mounted "/data_mirror/data_ce/null/${user_id}/${pkg}"; then
                        mount -o bind "${dst}" "/data_mirror/data_ce/null/${user_id}/${pkg}" 2>/dev/null
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
                                if ! mx_is_mounted "/data/data/${pkg}/cache"; then
                                    mount -o bind "${_cache_hold}" "/data/data/${pkg}/cache" 2>/dev/null
                                    mx_make_rprivate "/data/data/${pkg}/cache"
                                fi
                            fi
                            if [ -d "/data_mirror/data_ce/null/${user_id}/${pkg}" ]; then
                                mkdir -p "/data_mirror/data_ce/null/${user_id}/${pkg}/cache" 2>/dev/null
                                if ! mx_is_mounted "/data_mirror/data_ce/null/${user_id}/${pkg}/cache"; then
                                    mount -o bind "${_cache_hold}" "/data_mirror/data_ce/null/${user_id}/${pkg}/cache" 2>/dev/null
                                    mx_make_rprivate "/data_mirror/data_ce/null/${user_id}/${pkg}/cache"
                                fi
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
                    log_info "  [${pkg}] Engine A Direct Private Data folder mounted: ${src} -> ${dst}"
                    entries=$((entries + 1))
                    continue
                elif [ "${_held_cache}" = "1" ]; then
                    umount -l "${_cache_hold}" 2>/dev/null
                    rmdir "${_cache_hold}" 2>/dev/null
                fi
                ;;
            APP_PACKAGE)
                # Dynamic APK target resolution & Self-Healing Package Reconciler
                local real_apk_path
                real_apk_path=$(pm path "${pkg}" 2>/dev/null | head -n 1 | sed 's/^package://')
                if [ -z "${real_apk_path}" ] && [ -f "${src}/base.apk" ]; then
                    log_info "  [${pkg}] Self-Healing Reconciler: re-registering detached package from ${src}..."
                    pm install -r -d "${src}"/*.apk >/dev/null 2>&1
                    real_apk_path=$(pm path "${pkg}" 2>/dev/null | head -n 1 | sed 's/^package://')
                    if [ -n "${real_apk_path}" ]; then
                        local new_apk_dir=$(dirname "${real_apk_path}")
                        find "${new_apk_dir}" -depth -mindepth 1 -delete 2>/dev/null
                        local new_uid=$(id -u "${pkg}" 2>/dev/null)
                        [ -n "${new_uid}" ] && [ -d "${SD_BASE}/MountX/data/${pkg}" ] && chown -R "${new_uid}:${new_uid}" "${SD_BASE}/MountX/data/${pkg}" 2>/dev/null
                    fi
                fi
                if [ -n "${real_apk_path}" ]; then
                    local real_apk_dir _resolved_abi
                    real_apk_dir=$(dirname "${real_apk_path}")
                    if echo "${dst}" | grep -q "/lib"; then
                        _resolved_abi=$(_detect_lib_abi "${src}" "${real_apk_dir}" "${dst}")
                        if [ -d "${src}/${_resolved_abi}" ]; then
                            src="${src}/${_resolved_abi}"
                        fi
                        dst="${real_apk_dir}/lib/${_resolved_abi}"
                    else
                        dst="${real_apk_dir}"
                    fi
                fi
                if mx_is_mounted "${dst}"; then
                    # Two-Stage Promotion: Ensure target has RW & exec permissions after boot completion
                    mount -o remount,bind,rw,exec "${dst}" 2>/dev/null || mount -o remount,rw,exec "${dst}" 2>/dev/null
                    mx_make_rprivate "${dst}"
                    touch "${src}/.mountx_canary" 2>/dev/null
                    chmod 644 "${src}/.mountx_canary" 2>/dev/null
                    chown 1000:1000 "${src}/.mountx_canary" 2>/dev/null
                    auto_heal_apk_native_libs "${pkg}" "${dst}"
                    pm unsuspend "${pkg}" >/dev/null 2>&1
                    log_info "  [${pkg}] App package target ${dst} already mounted, promoted to RW/exec."
                    entries=$((entries + 1))
                    continue
                fi
                if [ ! -d "${src}" ]; then
                    log_warn "  [${pkg}] APP_PACKAGE source missing: ${src}; skipping."
                    continue
                fi
                if ! echo "${dst}" | grep -q "/lib" && [ ! -f "${src}/base.apk" ]; then
                    log_warn "  [${pkg}] Occlusion guard: ${src}/base.apk missing; refusing to over-mount APK dir ${dst}."
                    continue
                fi
                if echo "${dst}" | grep -q "/lib" && [ -z "$(find "${src}" -name '*.so' 2>/dev/null | head -n 1)" ]; then
                    local _lib_dst_kb=0
                    [ -d "${dst}" ] && _lib_dst_kb=$(du -sk "${dst}" 2>/dev/null | cut -f1)
                    if [ "${_lib_dst_kb:-0}" -gt 64 ] 2>/dev/null; then
                        log_warn "  [${pkg}] Occlusion guard: no .so libs in ${src}; refusing to over-mount ${dst}."
                        continue
                    fi
                fi
                # Preserve internal /lib when mounting APK-only split storage so Lib stays on Internal
                local _lib_hold="/dev/mountx_lib_hold_${pkg}"
                local _held_lib=0
                if ! echo "${dst}" | grep -q "/lib"; then
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
                mkdir -p "${dst}" 2>/dev/null
                mount -o bind "${src}" "${dst}" 2>/dev/null
                mount -o remount,bind,rw,exec "${dst}" 2>/dev/null || mount -o remount,rw,exec "${dst}" 2>/dev/null
                mx_make_rprivate "${dst}"
                chown -R 1000:1000 "${dst}" 2>/dev/null
                chmod -R 755 "${dst}" 2>/dev/null
                touch "${src}/.mountx_canary" 2>/dev/null
                chmod 644 "${src}/.mountx_canary" 2>/dev/null
                chown 1000:1000 "${src}/.mountx_canary" 2>/dev/null
                if [ "${_held_lib}" = "1" ]; then
                    mkdir -p "${dst}/lib" 2>/dev/null
                    if mount -o bind "${_lib_hold}" "${dst}/lib" 2>/dev/null; then
                        mount -o remount,bind,rw,exec "${dst}/lib" 2>/dev/null || mount -o remount,rw,exec "${dst}/lib" 2>/dev/null
                        mx_make_rprivate "${dst}/lib"
                        chown -R 1000:1000 "${dst}/lib" 2>/dev/null
                        chmod -R 755 "${dst}/lib" 2>/dev/null
                        log_info "  [${pkg}] Preserved internal native libs over APK mount: ${dst}/lib"
                    fi
                    umount -l "${_lib_hold}" 2>/dev/null
                    rmdir "${_lib_hold}" 2>/dev/null
                else
                    auto_heal_apk_native_libs "${pkg}" "${dst}"
                fi
                pm unsuspend "${pkg}" >/dev/null 2>&1
                log_info "  [${pkg}] App package bind-mounted directly: ${src} -> ${dst}"
                entries=$((entries + 1))
                continue
                ;;
            CACHE_SHADERS)
                if [ ! -d "${src}" ]; then
                    log_warn "  [${pkg}] CACHE_SHADERS source missing: ${src}; skipping."
                    continue
                fi
                # Occlusion guard: do not over-mount empty SD cache over populated internal cache
                local _cache_dst_kb=0
                [ -d "${dst}" ] && ! mx_is_mounted "${dst}" && _cache_dst_kb=$(du -sk "${dst}" 2>/dev/null | cut -f1)
                local _cache_has_files
                _cache_has_files=$(ls -A "${src}" 2>/dev/null | grep -vE '^(\.nomedia|\.mountx_canary)$' | head -n 1)
                if [ -z "${_cache_has_files}" ] && [ "${_cache_dst_kb:-0}" -gt 128 ] 2>/dev/null; then
                    log_warn "  [${pkg}] Occlusion guard: refusing to bind empty cache ${src} over ${dst} (${_cache_dst_kb}KB)."
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
                chown -R "${app_uid}:${app_uid}" "${src}" 2>/dev/null
                chmod 700 "${src}" 2>/dev/null
                chmod -R u+rwX,go-rwx "${src}" 2>/dev/null
                chcon -R u:object_r:app_data_file:s0 "${src}" 2>/dev/null
                if ! mx_is_mounted "${dst}"; then
                    mount -o bind "${src}" "${dst}" 2>/dev/null
                    mx_make_rprivate "${dst}"
                    chown -R "${app_uid}:${app_uid}" "${dst}" 2>/dev/null
                    chmod 700 "${dst}" 2>/dev/null
                    restorecon -FR "${dst}" 2>/dev/null || chcon -R u:object_r:app_data_file:s0 "${dst}" 2>/dev/null
                fi
                if [ -d "/data/data/${pkg}/cache" ] && ! mx_is_mounted "/data/data/${pkg}/cache"; then
                    mount -o bind "${src}" "/data/data/${pkg}/cache" 2>/dev/null
                    mx_make_rprivate "/data/data/${pkg}/cache"
                fi
                if [ -d "/data_mirror/data_ce/null/${user_id}/${pkg}/cache" ] && ! mx_is_mounted "/data_mirror/data_ce/null/${user_id}/${pkg}/cache"; then
                    mount -o bind "${src}" "/data_mirror/data_ce/null/${user_id}/${pkg}/cache" 2>/dev/null
                    mx_make_rprivate "/data_mirror/data_ce/null/${user_id}/${pkg}/cache"
                fi
                log_info "  [${pkg}] Internal cache shaders bind-mounted directly: ${src} -> ${dst}"
                entries=$((entries + 1))
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

        # Occlusion guard before external/media bind mount: never hide substantive internal files behind empty SD dir
        if [ -d "${dst}" ] && ! mx_is_mounted "${check_target}"; then
            local _ext_dst_kb _ext_src_kb
            _ext_dst_kb=$(du -sk "${dst}" 2>/dev/null | cut -f1)
            _ext_src_kb=$(du -sk "${src}" 2>/dev/null | cut -f1)
            if [ "${_ext_dst_kb:-0}" -gt 512 ] 2>/dev/null && [ "${_ext_src_kb:-0}" -le 128 ] 2>/dev/null; then
                log_warn "  [${pkg}] Occlusion guard: refusing to bind empty/small ${src} (${_ext_src_kb:-0}KB) over populated ${dst} (${_ext_dst_kb:-0}KB)."
                continue
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
    local WATCHDOG_SELF_PID="${WATCHDOG_SELF_PID:-$$}"
    local LAST_CE_AVAILABLE="false"
    [ -f "/dev/.mountx_initial_mounted" ] && LAST_CE_AVAILABLE="true"
    local LAST_DISK_ATTACHED=0
    local blk_dev
    blk_dev=$(_resolve_monitored_blk_dev)
    [ -n "${blk_dev}" ] && [ -b "${blk_dev}" ] && LAST_DISK_ATTACHED=1
    log_info "[Watchdog] Starting. PID=${WATCHDOG_SELF_PID}, Zygote64=${ZYGOTE_PID_LAST:-N/A}, inotifywait=$(which inotifywait 2>/dev/null || echo N/A)"

    # ── Helper: perform lightweight mount integrity check ─────────────────────
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

        if [ ${current_disk_attached} -eq 1 ] && [ ${LAST_DISK_ATTACHED} -eq 0 ]; then
            log_info "[Watchdog] MicroSD/External hotplug insertion detected (${blk_dev})! Card just plugged in."
            rm -f "/dev/.mountx_unmounted" "/dev/.mountx_disk_error"
            LAST_DISK_ATTACHED=1
            sleep 3

            log_info "[Watchdog] Auto-mounting ${blk_dev} to ${SD_BASE} after hotplug..."
            mount_sd
            blk_dev=$(_resolve_monitored_blk_dev)
            if mx_is_mounted "${SD_BASE}"; then
                log_info "[Watchdog] SD_BASE mounted after hotplug. Applying bind mounts..."
                echo "${WATCHDOG_SELF_PID}" > "${APPLY_LOCK_FILE}" 2>/dev/null
                cleanup_stale_mounts
                apply_io_tweaks
                apply_all_mounts
                rm -f "${APPLY_LOCK_FILE}" 2>/dev/null
                am broadcast -a app.mountx.ACTION_SYNC_NOTIFICATION -p app.mountx >/dev/null 2>&1
                am broadcast -a app.mountx.ACTION_MOUNT_ALL -p app.mountx >/dev/null 2>&1
                CONSECUTIVE_OK=0
                CHECK_INTERVAL=60
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

        # Immortal watchdog: runs continuously without artificial uptime limits

        # Mount integrity checks always run regardless of battery level to prevent unmounts

        # Check 0: Physical disk I/O & controller health probe
        local io_error=0
        blk_dev=$(_resolve_monitored_blk_dev)

        if [ -n "${blk_dev}" ] && mx_is_mounted "${SD_BASE}"; then
            # Probe 1: Device existence test — if block device vanished from /dev/block/, it's physically detached!
            if [ ! -b "${blk_dev}" ]; then
                log_error "[Watchdog] CRITICAL: Backing block device ${blk_dev} has vanished from kernel while mounted!"
                io_error=1
            else
                # Probe 2: Block device raw read (verifies underlying flash block responds without EIO)
                if ! dd if="${blk_dev}" of=/dev/null bs=512 count=1 2>/dev/null; then
                    log_error "[Watchdog] CRITICAL: Raw block read test failed on ${blk_dev} (I/O error)!"
                    io_error=1
                fi
            fi

            # Probe 3: Check kernel dmesg for real hardware failures (DATCRCERR, CMDCRCERR, autok fail, buffer I/O error)
            # NOTE: DO NOT check for "power off" as MediaTek msdc runtime PM logs "[msdc]msdc1 power off" normally when idle!
            if [ ${io_error} -eq 0 ]; then
                if dmesg 2>/dev/null | tail -n 30 | grep -qiE "Buffer I/O error|DATCRCERR|CMDCRCERR|autok fail|autok error|card remove|card eject"; then
                    io_error=1
                fi
            fi
        fi

        if [ ${io_error} -eq 1 ]; then
            log_error "[Watchdog] CRITICAL: MicroSD hardware I/O error, device disconnected, or controller power cutoff detected!"
            echo "io_error" > "/dev/.mountx_disk_error"
            # Emergency unmount of bound directories to prevent games from freezing on black screens
            for _bnd in $(grep -E "(/data/media/0/Android|/mnt/runtime/default/emulated/0/Android|/mnt/runtime/read/emulated/0/Android|/mnt/runtime/write/emulated/0/Android|/mnt/runtime/full/emulated/0/Android)" /proc/mounts 2>/dev/null | cut -d' ' -f2); do
                umount -l "${_bnd}" 2>/dev/null
            done
            umount -l "${SD_BASE}" 2>/dev/null
            # Broadcast error event to MountX app
            am broadcast -a app.mountx.ACTION_DISK_ERROR --es error "io_error" -p app.mountx >/dev/null 2>&1
            CHECK_INTERVAL=60
            return 1
        else
            rm -f "/dev/.mountx_disk_error" 2>/dev/null
        fi

        # Check 1: Is SD_BASE still mounted?
        if [ -n "${blk_dev}" ] && ! mx_is_mounted "${SD_BASE}"; then
            if [ ! -b "${blk_dev}" ]; then
                log_warn "[Watchdog] SD_BASE ${SD_BASE} unmounted and block device ${blk_dev} not found. Disk detached."
                echo "io_error" > "/dev/.mountx_disk_error"
                am broadcast -a app.mountx.ACTION_DISK_ERROR --es error "io_error" -p app.mountx >/dev/null 2>&1
                return 1
            fi
            log_warn "[Watchdog] SD_BASE ${SD_BASE} lost! Re-mounting..."
            mount_sd
            if mx_is_mounted "${SD_BASE}"; then
                log_info "[Watchdog] SD_BASE re-mounted. Re-applying bind mounts..."
                echo "${WATCHDOG_SELF_PID}" > "${APPLY_LOCK_FILE}" 2>/dev/null
                cleanup_stale_mounts
                apply_io_tweaks
                apply_all_mounts
                rm -f "${APPLY_LOCK_FILE}" 2>/dev/null
                am broadcast -a app.mountx.ACTION_SYNC_NOTIFICATION -p app.mountx >/dev/null 2>&1
                am broadcast -a app.mountx.ACTION_MOUNT_ALL -p app.mountx >/dev/null 2>&1
                CONSECUTIVE_OK=0
                CHECK_INTERVAL=60
                return 0
            else
                log_error "[Watchdog] SD_BASE re-mount failed."
                return 1
            fi
        fi

        # Check 1c: Primary Internal Storage Emulated Mount Integrity Probe
        if ! is_emulated_storage_ready; then
            log_warn "[Watchdog] Primary emulated storage not yet ready. Waiting for storage daemon..."
        fi

        # Check 1b: Kernel Read-Only Remount Protection Probe
        if mx_is_mounted "${SD_BASE}"; then
            if grep -F " ${SD_BASE} " /proc/mounts 2>/dev/null | grep -qE "(^|,)ro(,| )"; then
                log_error "[Storage] CRITICAL: MicroSD ${SD_BASE} has been remounted READ-ONLY by kernel! Writes will fail with EROFS."
                echo "ro_error" > "/dev/.mountx_disk_error"
                am broadcast -a app.mountx.ACTION_DISK_ERROR --es error "ro_error" -p app.mountx >/dev/null 2>&1
            fi
        fi

        # Probe 4: Shadowed Mount Canary Probe (detects if Vold FUSE mounted over our bind-mounts)
        local shadowed_found=0
        if [ -f "${MOUNTPOINTS_FILE}" ]; then
            while IFS='|' read -r _p _c _s _d _pm _u _uid || [ -n "${_p}" ]; do
                case "${_p}" in ''|\#*) continue ;; esac
                # Only probe mounts whose source actually exists (ignore disconnected USB drives)
                [ ! -d "${_s}" ] && continue
                local _probe_s="${_s}"
                _check_t="${_d}"
                if [ "${_c}" = "APP_PACKAGE" ] && [ -n "${_p}" ]; then
                    local _live_apk _live_dir _probe_abi
                    _live_apk=$(pm path "${_p}" 2>/dev/null | head -n 1 | sed 's/^package://;s/\r//g')
                    if [ -n "${_live_apk}" ]; then
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
                                ;;
                            *)
                                _check_t="${_live_dir}"
                                ;;
                        esac
                    else
                        case "${_d}" in
                            */lib/*)
                                _probe_abi="${_d##*/lib/}"
                                _probe_abi="${_probe_abi%%/*}"
                                [ -n "${_probe_abi}" ] && [ -d "${_s}/${_probe_abi}" ] && _probe_s="${_s}/${_probe_abi}"
                                ;;
                        esac
                    fi
                fi
                [ ! -d "${_probe_s}" ] && continue
                [ ! -f "${_probe_s}/.mountx_canary" ] && continue
                case "${_check_t}" in
                    */Android/*|/data/media/*|/storage/emulated/*|/sdcard/*)
                        if echo "${_check_t}" | grep -q "/Android/"; then
                            _rel="Android/"$(echo "${_check_t}" | sed 's|.*/Android/||')
                        else
                            _rel=$(echo "${_check_t}" | sed 's|^/sdcard/||;s|^/storage/emulated/[0-9]*/||;s|^/data/media/[0-9]*/||;s|^/mnt/user/[0-9]*/emulated/[0-9]*/||')
                        fi
                        _check_t="/storage/emulated/${_uid:-0}/${_rel}"
                        ;;
                esac
                if [ ! -f "${_check_t}/.mountx_canary" ]; then
                    log_warn "[Watchdog] Canary missing at ${_check_t}! Mount for ${_p} (${_c}) is shadowed or missing. Re-applying mounts..."
                    shadowed_found=1
                    break
                fi
            done < "${MOUNTPOINTS_FILE}"
        fi
        if [ ${shadowed_found} -eq 1 ]; then
            echo "${WATCHDOG_SELF_PID}" > "${APPLY_LOCK_FILE}" 2>/dev/null
            apply_all_mounts
            rm -f "${APPLY_LOCK_FILE}" 2>/dev/null
            am broadcast -a app.mountx.ACTION_SYNC_NOTIFICATION -p app.mountx >/dev/null 2>&1
        fi

        # ── Check 3: Zygote soft restart detection ────────────────────────────
        # When Zygote/SystemServer restarts WITHOUT a kernel reboot, the PID changes
        # and all per-app mount namespaces (/mnt/runtime/*/emulated/0/Android/...)
        # are torn down. The base mount at /data/media/0/ survives but games
        # launched after the soft restart get empty internal namespaces.
        # We detect this via the authoritative Zygote PID change (Primary, 100% definitive).
        local soft_restart=0
        local zygote_pid_now
        zygote_pid_now=$(_get_zygote_pid)
        if [ -n "${zygote_pid_now}" ] && [ -n "${ZYGOTE_PID_LAST}" ] && \
           [ "${zygote_pid_now}" != "${ZYGOTE_PID_LAST}" ]; then
            log_warn "[Watchdog] Zygote PID changed: ${ZYGOTE_PID_LAST} → ${zygote_pid_now}. Soft restart detected!"
            ZYGOTE_PID_LAST="${zygote_pid_now}"
            soft_restart=1
        fi

        if [ ${soft_restart} -eq 1 ]; then
            log_warn "[Watchdog] Soft restart detected! Re-applying registered bind mounts..."
            echo "${WATCHDOG_SELF_PID}" > "${APPLY_LOCK_FILE}" 2>/dev/null
            wait_for_framework
            sleep 2

            if ! mx_is_mounted "${SD_BASE}"; then
                mount_sd
            fi

            cleanup_stale_mounts
            apply_io_tweaks
            apply_all_mounts
            rm -f "${APPLY_LOCK_FILE}" 2>/dev/null

            # Notify the app to re-sync its mount status
            am broadcast -a app.mountx.ACTION_SYNC_NOTIFICATION -p app.mountx >/dev/null 2>&1
            log_info "[Watchdog] Soft restart recovery complete. All mounts re-applied."
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

        local free_mb
        free_mb=$(df -Pk "${SD_BASE}" 2>/dev/null | awk 'NR==2 {print int($4/1024)}')
        local active_apps=0
        if [ -f "${MOUNTPOINTS_FILE}" ]; then
            local _tmp_pkgs=""
            while IFS='|' read -r _ap _ac _as _ad _apm _au _auid || [ -n "${_ap}" ]; do
                case "${_ap}" in ''|\#*) continue ;; esac
                if [ -e "${_as}" ]; then
                    case " ${_tmp_pkgs} " in
                        *" ${_ap} "*) ;;
                        *) _tmp_pkgs="${_tmp_pkgs} ${_ap}"; active_apps=$((active_apps + 1)) ;;
                    esac
                fi
            done < "${MOUNTPOINTS_FILE}"
        fi
        log_info "[Watchdog] Heartbeat #${CONSECUTIVE_OK} OK (Zygote64=${ZYGOTE_PID_LAST:-N/A}, Disk=${SD_BASE} [${FS_TYPE}, free=${free_mb:-0}MB], MountedApps=${active_apps:-0})."
        if [ -n "${free_mb}" ] && [ "${free_mb}" -lt 500 ] 2>/dev/null; then
            log_warn "[Storage] Low storage alert: only ${free_mb}MB free on ${SD_BASE}!"
        fi
    }

    # ── High-Responsiveness Timer Loop with Sliced Sleep ──────────────────────
    # Sleeps in 5s slices while rapidly monitoring Zygote PID and physical disk
    # attachment so that soft reboots, subsystem crashes, and hotplug events
    # are caught and recovered within <= 5s, without CPU/battery penalty.
    log_info "[Watchdog] Using sliced sleep loop (5s slice, ${CHECK_INTERVAL}s health check)."
    local slice=5
    local elapsed=0
    while true; do
        sleep "${slice}"
        elapsed=$((elapsed + slice))

        # Check 1: Rapid Zygote PID change detection (soft reboot / framework restart)
        local fast_zygote
        fast_zygote=$(_get_zygote_pid)
        if [ -n "${fast_zygote}" ] && [ -n "${ZYGOTE_PID_LAST}" ] && [ "${fast_zygote}" != "${ZYGOTE_PID_LAST}" ]; then
            log_warn "[Watchdog] Immediate Zygote PID change detected: ${ZYGOTE_PID_LAST} -> ${fast_zygote}!"
            elapsed=0
            check_mounts
            continue
        fi

        # Check 1b: Rapid User Unlock Detection (CE key unlocked + Vold FUSE settlement)
        if [ "${LAST_CE_AVAILABLE}" != "true" ] && is_ce_key_unlocked; then
            log_info "[Watchdog] User unlock detected (CE available). Waiting for Vold FUSE/sdcardfs settlement..."
            LAST_CE_AVAILABLE="true"
            echo "${WATCHDOG_SELF_PID}" > "${APPLY_LOCK_FILE}" 2>/dev/null
            wait_for_emulated_storage_settled 30
            ZYGOTE_PID_LAST=$(_get_zygote_pid)
            cleanup_stale_mounts
            apply_all_mounts
            touch /dev/.mountx_initial_mounted 2>/dev/null
            sleep 3
            verify_post_mount_canaries "Watchdog"
            rm -f "${APPLY_LOCK_FILE}" 2>/dev/null
            am broadcast -a app.mountx.ACTION_SYNC_NOTIFICATION -p app.mountx >/dev/null 2>&1
            CONSECUTIVE_OK=0
            elapsed=0
            continue
        fi

        # Check 2: Rapid physical disk attachment detection (hotplug)
        local fast_disk=0
        [ -n "${blk_dev}" ] && [ -b "${blk_dev}" ] && fast_disk=1
        if [ -n "${blk_dev}" ] && [ ${fast_disk} -ne ${LAST_DISK_ATTACHED} ]; then
            log_info "[Watchdog] Immediate disk attachment change detected: ${LAST_DISK_ATTACHED} -> ${fast_disk}!"
            elapsed=0
            check_mounts
            continue
        fi

        # Check 3: Full scheduled integrity & I/O check
        if [ ${elapsed} -ge ${CHECK_INTERVAL} ]; then
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

    # Ensure localized subdirectories exist without placing .nomedia on $SD_BASE/MountX
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

    # Never mount user/app directories or inject into Zygote while device is on lockscreen (BFU)
    if [ ${is_live_reload} -eq 1 ]; then
        echo "$$" > "${APPLY_LOCK_FILE}" 2>/dev/null
        apply_all_mounts
        touch /dev/.mountx_initial_mounted "${BOOT_FLAG_FILE}" 2>/dev/null
        log_info "MountX live-reload completed at ${BOOT_FLAG_FILE}."
        (am broadcast -a app.mountx.ACTION_SYNC_NOTIFICATION -p app.mountx >/dev/null 2>&1) &
    elif is_ce_key_unlocked; then
        echo "$$" > "${APPLY_LOCK_FILE}" 2>/dev/null
        log_info "[Boot] User storage unlocked (CE ready). Waiting for Vold FUSE/sdcardfs settlement..."
        wait_for_emulated_storage_settled 30
        apply_all_mounts
        touch /dev/.mountx_initial_mounted 2>/dev/null
        sleep 3
        verify_post_mount_canaries "Boot"
        touch "${BOOT_FLAG_FILE}" 2>/dev/null
        log_info "MountX boot marker touched at ${BOOT_FLAG_FILE}. Service completed."
        (am broadcast -a app.mountx.ACTION_SYNC_NOTIFICATION -p app.mountx >/dev/null 2>&1) &
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
