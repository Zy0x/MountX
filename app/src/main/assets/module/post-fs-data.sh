#!/system/bin/sh
# ═══════════════════════════════════════════════════════════════════════════════
# MountX — Early Boot post-fs-data Engine
# Mounts /data/sdext2 and pre-binds APP_PACKAGE (APK/Lib) before PMS scans
# ═══════════════════════════════════════════════════════════════════════════════

MODDIR="${0%/*}"

# 1. Early Mount /data/sdext2
if ! grep -q " /data/sdext2 " /proc/mounts 2>/dev/null; then
    mkdir -p /data/sdext2 2>/dev/null
    for dev in /dev/block/mmcblk0p3 /dev/block/mmcblk0p2 /dev/block/mmcblk1p3 /dev/block/mmcblk1p2; do
        if [ -b "${dev}" ]; then
            mount -t f2fs -o rw,noatime "${dev}" /data/sdext2 2>/dev/null || \
            mount -t ext4 -o rw,noatime "${dev}" /data/sdext2 2>/dev/null || \
            mount "${dev}" /data/sdext2 2>/dev/null
            if grep -q " /data/sdext2 " /proc/mounts 2>/dev/null; then
                break
            fi
        fi
    done
fi

# 2. Early Bind-Mount APP_PACKAGE entries from mountpoints.conf
MOUNTPOINTS_FILE="/data/adb/modules/MountX/mountpoints.conf"
[ ! -f "${MOUNTPOINTS_FILE}" ] && MOUNTPOINTS_FILE="/data/adb/mountx/mountpoints.conf"
[ ! -f "${MOUNTPOINTS_FILE}" ] && MOUNTPOINTS_FILE="/data/adb/ap/modules/MountX/mountpoints.conf"
[ ! -f "${MOUNTPOINTS_FILE}" ] && MOUNTPOINTS_FILE="/data/adb/ksu/modules/MountX/mountpoints.conf"

if [ -f "${MOUNTPOINTS_FILE}" ] && grep -q " /data/sdext2 " /proc/mounts 2>/dev/null; then
    while IFS='|' read -r pkg cat src dst flags user_id disk_uuid || [ -n "${pkg}" ]; do
        case "${pkg}" in ''|\#*) continue ;; esac
        [ "${cat}" != "APP_PACKAGE" ] && continue
        [ -z "${src}" ] || [ -z "${dst}" ] && continue
        if [ -d "${src}" ]; then
            mkdir -p "${dst}" 2>/dev/null
            mount -o bind "${src}" "${dst}" 2>/dev/null
            chown -R 1000:1000 "${dst}" 2>/dev/null
            chmod -R 755 "${dst}" 2>/dev/null
            chcon -R u:object_r:apk_data_file:s0 "${dst}" 2>/dev/null
        fi
    done < "${MOUNTPOINTS_FILE}"
fi
