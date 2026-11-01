#!/system/bin/sh
# ═══════════════════════════════════════════════════════════════════════════════
# MountX Early Boot Stage (post-fs-data.sh)
# Performs non-blocking early mount of SD_BASE and APP_PACKAGE (/data/app)
# before PackageManagerService scans /data/app during cold boot.
# ═══════════════════════════════════════════════════════════════════════════════

MODDIR="${0%/*}"
export PATH="${MODDIR}/bin:/data/adb/ap/bin:/data/adb/ksu/bin:/data/adb/magisk:/system/bin:/system/xbin:${PATH}"

CONFIG_FILE="${MODDIR}/config.conf"
MOUNTPOINTS_FILE="${MODDIR}/mountpoints.conf"
SD_BLOCK="/dev/block/mmcblk0p3"
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

# Wait up to 2 seconds (non-blocking) for MMC block device to appear in early boot
tries=0
while [ ${tries} -lt 4 ] && [ ! -b "${SD_BLOCK}" ]; do
    sleep 0.5
    tries=$((tries + 1))
done

if [ -b "${SD_BLOCK}" ] && ! mountpoint -q "${SD_BASE}" 2>/dev/null; then
    mkdir -p "${SD_BASE}" 2>/dev/null
    detected_fs="${FS_TYPE}"
    if [ -z "${detected_fs}" ] || [ "${detected_fs}" = "auto" ]; then
        detected_fs=$(blkid "${SD_BLOCK}" 2>/dev/null | sed -n 's/.*TYPE="\([^"]*\)".*/\1/p')
    fi
    case "${detected_fs}" in
        f2fs) mount -t f2fs -o rw,noatime,nodiratime,inline_data,inline_dentry,flush_merge "${SD_BLOCK}" "${SD_BASE}" 2>/dev/null || mount -t f2fs -o rw,noatime "${SD_BLOCK}" "${SD_BASE}" 2>/dev/null ;;
        ext4) mount -t ext4 -o rw,noatime,nodiratime "${SD_BLOCK}" "${SD_BASE}" 2>/dev/null ;;
        exfat|vfat) mount -t "${detected_fs}" -o rw,noatime,uid=1023,gid=1023,fmask=0000,dmask=0000 "${SD_BLOCK}" "${SD_BASE}" 2>/dev/null ;;
        *) mount "${SD_BLOCK}" "${SD_BASE}" 2>/dev/null ;;
    esac
    mount --make-rprivate "${SD_BASE}" 2>/dev/null
fi

# Early bind-mount APP_PACKAGE entries so PackageManagerService sees base.apk during initial boot scan
if mountpoint -q "${SD_BASE}" 2>/dev/null && [ -f "${MOUNTPOINTS_FILE}" ]; then
    while IFS='|' read -r pkg cat src dst preserve_media disk_uuid user_id || [ -n "${pkg}" ]; do
        pkg=$(echo "${pkg}" | tr -d '\r')
        case "${pkg}" in ''|\#*) continue ;; esac
        if [ "${cat}" = "APP_PACKAGE" ] && [ -d "${src}" ] && [ -n "${dst}" ]; then
            mkdir -p "${dst}" 2>/dev/null
            if ! mountpoint -q "${dst}" 2>/dev/null; then
                mount -o bind "${src}" "${dst}" 2>/dev/null
                chown -R 1000:1000 "${dst}" 2>/dev/null
                chmod -R 755 "${dst}" 2>/dev/null
                chcon -R u:object_r:apk_data_file:s0 "${dst}" 2>/dev/null
            fi
        fi
    done < "${MOUNTPOINTS_FILE}"
fi

exit 0
