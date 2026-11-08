#!/system/bin/sh
# ═══════════════════════════════════════════════════════════════════════════════
# MountX Early Boot Stage (post-fs-data.sh)
# Performs non-blocking early mount of SD_BASE and APP_PACKAGE (/data/app)
# before PackageManagerService scans /data/app during cold boot.
# Protected against VFS F2FS deadlock & cross-namespace lock contention.
# ═══════════════════════════════════════════════════════════════════════════════

MODDIR="${0%/*}"
export PATH="${MODDIR}/bin:/dev/mountx_bin:/data/adb/ap/bin:/data/adb/ksu/bin:/data/adb/magisk:/system/bin:/system/xbin:${PATH}"

# ── Dynamic SELinux Policy Injection (Magisk, KernelSU, APatch) ───────────────
# Injects explicit rules so Vold, untrusted_app, and Android framework can
# traverse and read/write mounted partitions without 'unlabeled' denial.
SE_TOOL=""
if command -v magiskpolicy >/dev/null 2>&1; then
    SE_TOOL="magiskpolicy --live"
elif [ -f /data/adb/ksud ]; then
    SE_TOOL="/data/adb/ksud sepolicy patch"
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

# Dual-MMC Security Filter: Reject internal eMMC (mmcblk0) when MicroSD (mmcblk1) exists
if [ -d "/sys/block/mmcblk1" ]; then
    case "${SD_BLOCK}" in
        /dev/block/mmcblk0*|mmcblk0*)
            SD_BLOCK=""
            ;;
    esac
fi

# Dynamic fallback discovery if SD_BLOCK was unconfigured
if [ -z "${SD_BLOCK}" ] || [ ! -b "${SD_BLOCK}" ]; then
    for candidate in /dev/block/mmcblk1p2 /dev/block/mmcblk1p1 /dev/block/mmcblk0p2 /dev/block/sda2; do
        if [ -b "${candidate}" ]; then
            # Verify candidate is not internal eMMC
            if [ -d "/sys/block/mmcblk1" ] && echo "${candidate}" | grep -q "mmcblk0"; then
                continue
            fi
            SD_BLOCK="${candidate}"
            break
        fi
    done
fi

# Ultra-fast non-blocking probe (max 0.2s total delay) to prevent delaying Android init
if [ -n "${SD_BLOCK}" ] && [ ! -b "${SD_BLOCK}" ]; then
    sleep 0.2
fi

# Exit early if storage media is detached — allow Android init & framework to boot freely
if [ -z "${SD_BLOCK}" ] || [ ! -b "${SD_BLOCK}" ]; then
    exit 0
fi

# Mount SD_BASE with strict VFS namespace isolation
if ! mountpoint -q "${SD_BASE}" 2>/dev/null; then
    mkdir -p "${SD_BASE}" 2>/dev/null
    detected_fs="${FS_TYPE}"
    if [ -z "${detected_fs}" ] || [ "${detected_fs}" = "auto" ]; then
        detected_fs=$(blkid "${SD_BLOCK}" 2>/dev/null | sed -n 's/.*TYPE="\([^"]*\)".*/\1/p')
    fi
    # Determine application-friendly SELinux mount context (bypasses 'unlabeled' denial)
    SD_CTX="context=u:object_r:sdcardfs:s0"
    if ! grep -q sdcardfs /proc/filesystems 2>/dev/null; then
        SD_CTX="context=u:object_r:fuse:s0"
    fi

    case "${detected_fs}" in
        f2fs) mount -t f2fs -o rw,noatime,nodiratime,${SD_CTX},inline_data,inline_dentry,flush_merge "${SD_BLOCK}" "${SD_BASE}" 2>/dev/null || mount -t f2fs -o rw,noatime,${SD_CTX} "${SD_BLOCK}" "${SD_BASE}" 2>/dev/null || mount -t f2fs -o rw,noatime "${SD_BLOCK}" "${SD_BASE}" 2>/dev/null ;;
        ext4) mount -t ext4 -o rw,noatime,nodiratime,${SD_CTX} "${SD_BLOCK}" "${SD_BASE}" 2>/dev/null || mount -t ext4 -o rw,noatime,nodiratime "${SD_BLOCK}" "${SD_BASE}" 2>/dev/null ;;
        exfat|vfat) mount -t "${detected_fs}" -o rw,noatime,uid=1023,gid=1023,fmask=0000,dmask=0000 "${SD_BLOCK}" "${SD_BASE}" 2>/dev/null ;;
        *) mount "${SD_BLOCK}" "${SD_BASE}" 2>/dev/null ;;
    esac
    mount --make-rprivate "${SD_BASE}" 2>/dev/null
fi

# Two-Stage Early Mount for APP_PACKAGE:
# Bind-mount existing APK folders with private namespace propagation.
# Zero recursive chcon/chmod during boot to eliminate VFS F2FS semaphore deadlocks.
if mountpoint -q "${SD_BASE}" 2>/dev/null && [ -f "${MOUNTPOINTS_FILE}" ]; then
    while IFS='|' read -r pkg cat src dst preserve_media disk_uuid user_id || [ -n "${pkg}" ]; do
        pkg=$(echo "${pkg}" | tr -d '\r')
        case "${pkg}" in ''|\#*) continue ;; esac
        if [ "${cat}" = "APP_PACKAGE" ] && [ -d "${src}" ] && [ -n "${dst}" ]; then
            mkdir -p "${dst}" 2>/dev/null
            if ! mountpoint -q "${dst}" 2>/dev/null; then
                mount -o bind,ro "${src}" "${dst}" 2>/dev/null || mount -o bind "${src}" "${dst}" 2>/dev/null
                mount --make-rprivate "${dst}" 2>/dev/null
            fi
        fi
    done < "${MOUNTPOINTS_FILE}"
fi

exit 0
