#!/system/bin/sh
# ═══════════════════════════════════════════════════════════════════════════════
# MountX Early Boot Stage (post-fs-data.sh)
# Performs non-blocking SELinux policy injection and optional early APK mount.
# Fully non-blocking to prevent Android init watchdogd timeouts and bootloops.
# ═══════════════════════════════════════════════════════════════════════════════

MODDIR="${0%/*}"
export PATH="${MODDIR}/bin:/dev/mountx_bin:/data/adb/ap/bin:/data/adb/ksu/bin:/data/adb/magisk:/system/bin:/system/xbin:${PATH}"

# ── Dynamic SELinux Policy Injection (Magisk, KernelSU, APatch) ───────────────
SE_TOOL=""
if command -v magiskpolicy >/dev/null 2>&1; then
    SE_TOOL="magiskpolicy --live"
elif command -v ksud >/dev/null 2>&1; then
    SE_TOOL="ksud sepolicy patch"
elif [ -f /data/adb/ksud ]; then
    SE_TOOL="/data/adb/ksud sepolicy patch"
elif command -v apd >/dev/null 2>&1; then
    SE_TOOL="apd sepolicy patch"
elif [ -f /data/adb/apd ]; then
    SE_TOOL="/data/adb/apd sepolicy patch"
fi

if [ -n "${SE_TOOL}" ]; then
    ${SE_TOOL} "allow vold { sdcard_type fuse unlabeled } dir *" 2>/dev/null
    ${SE_TOOL} "allow vold { sdcard_type fuse unlabeled } file *" 2>/dev/null
    ${SE_TOOL} "allow { untrusted_app untrusted_app_all } { sdcard_type fuse media_rw_data_file unlabeled } dir { ioctl read write create getattr setattr lock rename open watch watch_reads add_name remove_name reparent search rmdir }" 2>/dev/null
    ${SE_TOOL} "allow { untrusted_app untrusted_app_all } { sdcard_type fuse media_rw_data_file unlabeled } file { ioctl read write create getattr setattr lock append map unlink rename open watch watch_reads }" 2>/dev/null
fi

CONFIG_FILE="${MODDIR}/config.conf"
MOUNTPOINTS_FILE="${MODDIR}/mountpoints.conf"

# Fast-path: If no APP_PACKAGE (/data/app) targets are configured, exit immediately.
# External data & obb mounts are safely handled in service.sh after boot completed.
if [ ! -f "${MOUNTPOINTS_FILE}" ] || ! grep -q "|APP_PACKAGE|" "${MOUNTPOINTS_FILE}" 2>/dev/null; then
    exit 0
fi

SD_BLOCK=""
SD_BASE="/data/sdext2"
FS_TYPE="auto"

if [ -f "${CONFIG_FILE}" ]; then
    while IFS='=' read -r k v; do
        k=$(echo "${k}" | tr -d ' \r\t')
        v=$(echo "${v}" | tr -d ' \r\t"')
        case "${k}" in
            SD_BLOCK) [ -n "${v}" ] && SD_BLOCK="${v}" ;;
            SD_BASE)  [ -n "${v}" ] && SD_BASE="${v}" ;;
            FS_TYPE)  [ -n "${v}" ] && FS_TYPE="${v}" ;;
        esac
    done < "${CONFIG_FILE}"
fi

_pfs_is_internal_disk() {
    local _dev="$1"
    [ -z "${_dev}" ] && return 0
    local _bn _dk _dm _mp
    _bn=$(basename "${_dev}")
    case "${_bn}" in
        loop*|ram*|zram*|dm-*|*boot[0-9]|*rpmb) return 0 ;;
        mmcblk*p[0-9]*|nvme*n*p[0-9]*) _dk="${_bn%p[0-9]*}" ;;
        *) _dk=$(echo "${_bn}" | sed 's/[0-9]*$//') ;;
    esac
    [ -z "${_dk}" ] && return 0
    for _mp in $(awk '$2 ~ "^/(data|system|vendor|product|odm|system_ext|metadata|cache|mnt/vendor|apex|persist)$" {print $1}' /proc/mounts 2>/dev/null); do
        case "${_mp}" in
            /dev/block/${_dk}|/dev/block/${_dk}p[0-9]*|/dev/block/${_dk}[0-9]*) return 0 ;;
        esac
    done
    for _dm in /sys/block/dm-*/slaves/${_dk}*; do
        [ -e "${_dm}" ] && return 0
    done
    [ -r "/sys/block/${_dk}/device/type" ] && [ "$(cat "/sys/block/${_dk}/device/type" 2>/dev/null | tr -d '\r\n')" = "MMC" ] && return 0
    return 1
}

if [ -n "${SD_BLOCK}" ] && _pfs_is_internal_disk "${SD_BLOCK}"; then
    SD_BLOCK=""
fi

# Dynamic UUID resolution first, then safe non-internal external discovery (strictly non-blocking, zero sleep)
if [ -z "${SD_BLOCK}" ] || [ ! -b "${SD_BLOCK}" ]; then
    _conf_uuid=$(grep -E '^[a-zA-Z0-9._]+' "${MOUNTPOINTS_FILE}" 2>/dev/null | cut -d'|' -f6 | grep -v '^$' | head -n 1)
    if [ -n "${_conf_uuid}" ]; then
        _by_uuid=$(blkid 2>/dev/null | grep -i "UUID=\"${_conf_uuid}\"" | head -n 1 | cut -d: -f1)
        if [ -n "${_by_uuid}" ] && [ -b "${_by_uuid}" ] && ! _pfs_is_internal_disk "${_by_uuid}"; then
            SD_BLOCK="${_by_uuid}"
        fi
    fi
fi

if [ -z "${SD_BLOCK}" ] || [ ! -b "${SD_BLOCK}" ]; then
    for candidate in /dev/block/mmcblk*p* /dev/block/sd[a-z][0-9]* /dev/block/nvme*p*; do
        [ ! -b "${candidate}" ] && continue
        _pfs_is_internal_disk "${candidate}" && continue
        case "$(blkid "${candidate}" 2>/dev/null | sed -n 's/.*TYPE="\([^"]*\)".*/\1/p')" in
            f2fs|ext4)
                SD_BLOCK="${candidate}"
                break
                ;;
        esac
    done
fi

if [ -z "${SD_BLOCK}" ] || [ ! -b "${SD_BLOCK}" ]; then
    exit 0
fi

_pfs_busybox() {
    if [ -x /dev/mountx_bin/busybox ]; then
        echo "/dev/mountx_bin/busybox"
    elif [ -x "${MODDIR}/bin/busybox" ]; then
        echo "${MODDIR}/bin/busybox"
    elif command -v busybox >/dev/null 2>&1; then
        command -v busybox
    fi
}

_pfs_make_rprivate() {
    local _p="${1%/}"
    [ -z "${_p}" ] && _p="/"
    local _bb
    _bb=$(_pfs_busybox)
    if [ -n "${_bb}" ]; then
        "${_bb}" mount --make-rprivate "${_p}" 2>/dev/null && return 0
    fi
    mount --make-rprivate "${_p}" 2>/dev/null
}

_pfs_is_mounted() {
    local _p="${1%/}"
    [ -z "${_p}" ] && return 1
    local _mi="/proc/1/mountinfo"
    [ ! -r "${_mi}" ] && _mi="/proc/self/mountinfo"
    if [ -r "${_mi}" ]; then
        awk -v t="${_p}" '{m=$5; gsub(/\\040/, " ", m); if (m==t) {f=1; exit}} END {exit (f?0:1)}' "${_mi}" 2>/dev/null && return 0
        return 1
    fi
    mountpoint -q "${_p}" 2>/dev/null
}

# Mount SD_BASE if not already mounted
if ! _pfs_is_mounted "${SD_BASE}"; then
    mkdir -p "${SD_BASE}" 2>/dev/null
    detected_fs="${FS_TYPE}"
    if [ -z "${detected_fs}" ] || [ "${detected_fs}" = "auto" ]; then
        detected_fs=$(blkid "${SD_BLOCK}" 2>/dev/null | sed -n 's/.*TYPE="\([^"]*\)".*/\1/p')
    fi
    SD_CTX="context=u:object_r:sdcardfs:s0"
    if ! grep -q sdcardfs /proc/filesystems 2>/dev/null; then
        SD_CTX="context=u:object_r:fuse:s0"
    fi

    case "${detected_fs}" in
        f2fs) mount -t f2fs -o rw,noatime,nodiratime,${SD_CTX},inline_data,inline_dentry,flush_merge "${SD_BLOCK}" "${SD_BASE}" 2>/dev/null || mount -t f2fs -o rw,noatime "${SD_BLOCK}" "${SD_BASE}" 2>/dev/null ;;
        ext4) mount -t ext4 -o rw,noatime,nodiratime,${SD_CTX} "${SD_BLOCK}" "${SD_BASE}" 2>/dev/null || mount -t ext4 -o rw,noatime,nodiratime "${SD_BLOCK}" "${SD_BASE}" 2>/dev/null ;;
        exfat|vfat) mount -t "${detected_fs}" -o rw,noatime,uid=1023,gid=1023,fmask=0000,dmask=0000 "${SD_BLOCK}" "${SD_BASE}" 2>/dev/null ;;
        *) mount "${SD_BLOCK}" "${SD_BASE}" 2>/dev/null ;;
    esac
    _pfs_make_rprivate "${SD_BASE}"
fi

# Bind-mount only validated APP_PACKAGE entries read-only with executable bit (resolving ABI subdir for /lib/<abi> targets)
_pfs_bb=$(_pfs_busybox)
if _pfs_is_mounted "${SD_BASE}"; then
    while IFS='|' read -r pkg cat src dst preserve_media disk_uuid user_id || [ -n "${pkg}" ]; do
        pkg=$(echo "${pkg}" | tr -d '\r')
        cat=$(echo "${cat}" | tr -d '\r')
        src=$(echo "${src}" | tr -d '\r')
        dst=$(echo "${dst}" | tr -d '\r')
        case "${pkg}" in ''|\#*) continue ;; esac
        if [ "${cat}" = "APP_PACKAGE" ] && [ -d "${src}" ] && [ -n "${dst}" ]; then
            case "${dst}" in
                *==deleted==*|*/vmdl*) continue ;;
            esac
            _eff_src="${src}"
            _is_lib=0
            case "${dst}" in
                */lib/*|*/lib)
                    _is_lib=1
                    _abi="${dst##*/lib/}"
                    _abi="${_abi%%/*}"
                    if [ "${_abi}" != "${dst}" ] && [ -n "${_abi}" ] && [ -d "${src}/${_abi}" ]; then
                        _eff_src="${src}/${_abi}"
                    fi
                    if [ -z "$(find "${_eff_src}" -maxdepth 2 -name '*.so' 2>/dev/null | head -n 1)" ]; then
                        continue
                    fi
                    ;;
                *)
                    if [ ! -f "${_eff_src}/base.apk" ] || [ ! -s "${_eff_src}/base.apk" ]; then
                        continue
                    fi
                    ;;
            esac
            # Do not fabricate orphaned /data/app hash directories if Android already removed the parent hash dir
            if [ ! -d "${dst}" ] && [ ! -d "${dst%/*}" ]; then
                continue
            fi
            _oat_rw=""
            if [ "${_is_lib}" -eq 0 ]; then
                [ ! -d "${_eff_src}/oat" ] && mkdir -p "${_eff_src}/oat" 2>/dev/null
                _oat_rw="${SD_BASE}/MountX/cache/${pkg}_oat"
                mkdir -p "${_oat_rw}" 2>/dev/null
                chmod 755 "${_oat_rw}" 2>/dev/null
                chown 1000:1000 "${_oat_rw}" 2>/dev/null
                if [ -d "${SD_BASE}/MountX/lib/${pkg}" ]; then
                    for _lib_sub in "${SD_BASE}/MountX/lib/${pkg}"/*; do
                        [ -d "${_lib_sub}" ] || continue
                        _ab=$(basename "${_lib_sub}")
                        case "${_ab}" in
                            arm64|arm|armeabi*|x86*|riscv64)
                                [ ! -d "${_eff_src}/lib/${_ab}" ] && mkdir -p "${_eff_src}/lib/${_ab}" 2>/dev/null
                                ;;
                        esac
                    done
                fi
            elif [ ! -d "${dst}" ]; then
                _parent_apk="${dst%/lib*}"
                if [ -n "${_parent_apk}" ] && [ "${_parent_apk}" != "${dst}" ] && _pfs_is_mounted "${_parent_apk}" && [ -n "${_pfs_bb}" ]; then
                    "${_pfs_bb}" mount -o remount,rw,exec,bind "${_parent_apk}" 2>/dev/null || true
                    mkdir -p "${dst}" 2>/dev/null
                    "${_pfs_bb}" mount -o remount,ro,exec,bind "${_parent_apk}" 2>/dev/null || true
                fi
            fi
            mkdir -p "${dst}" 2>/dev/null
            if ! _pfs_is_mounted "${dst}"; then
                if mount -o bind "${_eff_src}" "${dst}" 2>/dev/null; then
                    if [ -n "${_pfs_bb}" ]; then
                        "${_pfs_bb}" mount -o remount,ro,exec,bind "${dst}" 2>/dev/null || true
                    fi
                    _pfs_make_rprivate "${dst}"
                fi
            fi
            if [ "${_is_lib}" -eq 0 ] && [ -n "${_oat_rw}" ] && [ -d "${_oat_rw}" ] && [ -d "${dst}/oat" ]; then
                if ! _pfs_is_mounted "${dst}/oat"; then
                    if mount -o bind "${_oat_rw}" "${dst}/oat" 2>/dev/null; then
                        if [ -n "${_pfs_bb}" ]; then
                            "${_pfs_bb}" mount -o remount,rw,exec,bind "${dst}/oat" 2>/dev/null || true
                        fi
                        _pfs_make_rprivate "${dst}/oat"
                    fi
                fi
            fi
        fi
    done < "${MOUNTPOINTS_FILE}"
fi

exit 0
