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

# ── Module paths ──────────────────────────────────────────────────────────────
if [ -d "/data/adb/modules/MountX" ]; then
    MODULE_DIR="/data/adb/modules/MountX"
elif [ -d "/data/adb/modules/Mountify" ]; then
    MODULE_DIR="/data/adb/modules/Mountify"
else
    MODULE_DIR="/data/adb/modules/MountX"
fi

CONFIG_FILE="${MODULE_DIR}/config.conf"
MOUNTPOINTS_FILE="${MODULE_DIR}/mountpoints.conf"
GAMELIST_FILE="${MODULE_DIR}/gamelist.conf"
LOG_FILE="${MODULE_DIR}/mountx.log"
BOOT_FLAG_FILE="/dev/.mountx_booted"

# ── Default configuration values ─────────────────────────────────────────────
SD_BASE="/data/sdext2"
SD_BLOCK="/dev/block/mmcblk0p3"
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
    # Only mirror to /storage/emulated/0/mountx.log if storage is verified mounted to prevent tmpfs file creation
    if [ -d "/data/media/0" ] && grep -q " /storage/emulated " /proc/mounts 2>/dev/null; then
        echo "[${ts}] [${level}] ${msg}" >> "/storage/emulated/0/mountx.log" 2>/dev/null
    fi
}

log_info()  { log "INFO " "$@"; }
log_warn()  { log "WARN " "$@"; }
log_error() { log "ERROR" "$@"; }

# ── Initialise log for this boot ──────────────────────────────────────────────
{
    echo "============================================================"
    echo " MountX service started (v2.2.39) — $(date)"
    echo "============================================================"
} >> "${LOG_FILE}"

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

# ── Wait until Android and Primary Storage have finished booting ──────────────
wait_for_boot() {
    log_info "Waiting for sys.boot_completed…"
    while [ "$(getprop sys.boot_completed | tr -d '\r')" != "1" ]; do
        sleep 2
    done

    log_info "Boot completed detected. Waiting for primary emulated storage and FUSE (/storage/emulated/0/Android/data)…"
    # Wait until user unlocks the device and FUSE creates /storage/emulated/0/Android/data
    while [ ! -d "/storage/emulated/0/Android/data" ] || [ ! -d "/data/media/0" ]; do
        sleep 2
    done

    # Give Vold, FUSE daemon, and runtime namespaces extra time to settle
    sleep 4
    log_info "Primary storage, FUSE, and Android/data readiness confirmed."
}

# ── Mount the SD partition ────────────────────────────────────────────────────
mount_sd() {
    local attempts=0
    while [ "${attempts}" -lt 5 ]; do
        if mountpoint -q "${SD_BASE}"; then
            log_info "SD already mounted at ${SD_BASE}."
            return 0
        fi

        if [ -b "${SD_BLOCK}" ]; then
            mkdir -p "${SD_BASE}"
            local mnt_opts="rw,noatime,nodiratime"
            if [ "${FS_TYPE}" = "f2fs" ]; then
                mnt_opts="${mnt_opts},inline_data,inline_dentry,flush_merge,mode=adaptive"
            elif [ "${FS_TYPE}" = "ext4" ]; then
                mnt_opts="${mnt_opts},commit=60,delalloc,data=writeback"
            fi

            if mount -t "${FS_TYPE}" -o "${mnt_opts}" "${SD_BLOCK}" "${SD_BASE}"; then
                mount --make-rprivate "${SD_BASE}" 2>/dev/null
                log_info "SD mounted: ${SD_BLOCK} → ${SD_BASE} (${FS_TYPE} with ${mnt_opts})"
                return 0
            else
                log_warn "Optimized mount failed, attempting generic fallback mount…"
                if mount -t "${FS_TYPE}" -o rw,noatime "${SD_BLOCK}" "${SD_BASE}"; then
                    mount --make-rprivate "${SD_BASE}" 2>/dev/null
                    log_info "SD mounted with fallback options: ${SD_BLOCK} → ${SD_BASE}"
                    return 0
                fi
            fi
        fi

        attempts=$((attempts + 1))
        log_warn "Waiting for storage block device ${SD_BLOCK} (attempt ${attempts}/5)..."
        sleep 2
    done

    if mountpoint -q "${SD_BASE}"; then
        mount --make-rprivate "${SD_BASE}" 2>/dev/null
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

    local disk_name
    disk_name=$(basename "${SD_BLOCK}" | sed 's/p[0-9]*$//;s/[0-9]*$//')
    local queue_dir="/sys/block/${disk_name}/queue"

    log_info "Applying I/O tweaks on ${disk_name} (RA=${READ_AHEAD_KB}KB, Sched=${IO_SCHEDULER}, Affinity=${RQ_AFFINITY})…"

    if [ -d "${queue_dir}" ]; then
        [ -w "${queue_dir}/read_ahead_kb" ] && echo "${READ_AHEAD_KB}" > "${queue_dir}/read_ahead_kb"
        [ -n "${IO_SCHEDULER}" ] && [ -w "${queue_dir}/scheduler" ] && echo "${IO_SCHEDULER}" > "${queue_dir}/scheduler" 2>/dev/null
        [ -w "${queue_dir}/rq_affinity" ] && echo "${RQ_AFFINITY}" > "${queue_dir}/rq_affinity"
        [ -w "${queue_dir}/nr_requests" ] && echo "${NR_REQUESTS}" > "${queue_dir}/nr_requests"
        [ -w "${queue_dir}/add_random" ] && echo "0" > "${queue_dir}/add_random"
        [ -w "${queue_dir}/rotational" ] && echo "0" > "${queue_dir}/rotational"
        [ -w "${queue_dir}/nomerges" ] && echo "0" > "${queue_dir}/nomerges"
    fi

    for bdi in /sys/devices/virtual/bdi/*/read_ahead_kb; do
        [ -w "${bdi}" ] && echo "${READ_AHEAD_KB}" > "${bdi}" 2>/dev/null
    done

    if [ -w "/proc/sys/vm/vfs_cache_pressure" ]; then
        echo "${VFS_CACHE_PRESSURE}" > /proc/sys/vm/vfs_cache_pressure
    fi

    log_info "I/O tweaks successfully applied to ${disk_name}."
}

# ── Unmount any stale bind-mounts for a target path or partition ──────────────
umount_stale() {
    local target="$1"
    if mountpoint -q "${target}" 2>/dev/null; then
        if umount -l "${target}" 2>/dev/null; then
            log_info "  Unmounted stale bind-mount: ${target}"
        fi
    fi
}

cleanup_stale_mounts() {
    log_info "Cleaning up any stale bind-mounts pointing to ${SD_BASE}…"
    su -mm -c '
    SD_BASE="'"${SD_BASE}"'"
    SD_BLOCK="'"${SD_BLOCK}"'"
    for i in 1 2 3; do
        has_stale=0
        while read -r dev mnt rest; do
            case "${mnt}" in
                *Android/data/*|*Android/obb/*|*Android/media/*)
                    if [ "${mnt}" != "${SD_BASE}" ]; then
                        if [ -n "${SD_BLOCK}" ] && [ "${dev}" = "${SD_BLOCK}" ]; then
                            umount -f -l "${mnt}" 2>/dev/null
                            has_stale=1
                        fi
                    fi
                    ;;
            esac
        done < /proc/mounts
        [ ${has_stale} -eq 0 ] && break
    done
    '
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

    # Sandbox integrity: restore internal app ownership & database permissions
    if [ -d "${sandbox_dir}" ]; then
        chown -R "${app_uid}:${app_uid}" "${sandbox_dir}" 2>/dev/null
        chmod -R 775 "${sandbox_dir}" 2>/dev/null
        chmod 771 "${sandbox_dir}/databases" 2>/dev/null
    fi

    # MicroSD target permissions: app_uid, GID 1023 (media_rw), mode 777, SELinux media_rw_data_file
    if [ -d "${src_dir}" ]; then
        chown -R "${app_uid}:1023" "${src_dir}" 2>/dev/null
        chmod -R 777 "${src_dir}" 2>/dev/null
        chcon -R u:object_r:media_rw_data_file:s0 "${src_dir}" 2>/dev/null
    fi

    log_info "  [${pkg}] Permissions verified: UID=${app_uid}, GID=1023, Mode=777 on ${src_dir}"
}

# ── Bind-mount into all active Android runtime namespaces (FUSE/Scoped Parity) ──
bind_mount_to_runtime_namespaces() {
    local src="$1"
    local dst="$2"
    local pkg="$3"
    local user_id="$4"
    [ -z "${user_id}" ] && user_id="0"

    # Extract relative path from dst across any user profile
    local rel=""
    case "${dst}" in
        */Android/*)
            rel="Android/"$(echo "${dst}" | sed 's|.*/Android/||')
            ;;
        *)
            rel=$(echo "${dst}" | sed 's|^/sdcard/||;s|^/storage/emulated/[0-9]*/||;s|^/data/media/[0-9]*/||;s|^/mnt/user/[0-9]*/primary/||;s|^/mnt/user/[0-9]*/emulated/[0-9]*/||;s|^/||')
            ;;
    esac

    # Perform bind mounts inside mount master (su -mm) across all runtime namespaces
    su -mm -c '
        SRC="'"${src}"'"
        DST="'"${dst}"'"
        REL="'"${rel}"'"
        USER_ID="'"${user_id}"'"
        PKG="'"${pkg}"'"

        if [ -n "${REL}" ]; then
            for R in \
                "/mnt/runtime/default/emulated/${USER_ID}" \
                "/mnt/runtime/read/emulated/${USER_ID}" \
                "/mnt/runtime/write/emulated/${USER_ID}" \
                "/mnt/runtime/full/emulated/${USER_ID}" \
                "/mnt/user/${USER_ID}/primary" \
                "/mnt/user/${USER_ID}/emulated/${USER_ID}" \
                "/storage/emulated/${USER_ID}" \
                "/data/media/${USER_ID}"
            do
                if [ -d "${R}" ]; then
                    TARGET="${R}/${REL}"
                    if ! mountpoint -q "${TARGET}" 2>/dev/null; then
                        mkdir -p "${TARGET}" 2>/dev/null
                        mount -o bind "${SRC}" "${TARGET}" 2>/dev/null
                    fi
                fi
            done
        else
            if ! mountpoint -q "${DST}" 2>/dev/null; then
                mkdir -p "${DST}" 2>/dev/null
                mount -o bind "${SRC}" "${DST}" 2>/dev/null
            fi
        fi

        # Pre-emptively stop any stale app process / pushservice that started before boot mount
        if [ -n "${PKG}" ]; then
            pids=$(pgrep -f "^${PKG}" 2>/dev/null)
            if [ -n "${pids}" ]; then
                for p in ${pids}; do
                    if [ -e "/proc/${p}/ns/mnt" ]; then
                        nsenter --mount="/proc/${p}/ns/mnt" -- mount --bind "${SRC}" "/storage/emulated/${USER_ID}/${REL}" 2>/dev/null
                        nsenter --mount="/proc/${p}/ns/mnt" -- mount --bind "${SRC}" "${DST}" 2>/dev/null
                    fi
                done
                am force-stop "${PKG}" 2>/dev/null
            fi
        fi
    '

    local count=0
    if [ -n "${rel}" ]; then
        count=$(grep -c "${rel}" /proc/mounts 2>/dev/null || echo 0)
    else
        count=$(grep -c " ${dst}" /proc/mounts 2>/dev/null || echo 0)
    fi
    log_info "  [${pkg}] Bind-mounted to ${count} targets in /proc/mounts: ${src} → ${dst}"
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

        if [ ! -d "${src}" ]; then
            log_warn "  [${pkg}] Source directory missing on SD: ${src}; skipping."
            continue
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
        exit 0
    fi
}

# ── Main ──────────────────────────────────────────────────────────────────────
main() {
    wait_for_boot
    check_safe_uninstall
    load_config
    mount_sd
    cleanup_stale_mounts
    apply_io_tweaks

    # Ensure localized subdirectories exist without placing .nomedia on $SD_BASE/MountX
    mkdir -p "${SD_BASE}/MountX/Android/data" \
             "${SD_BASE}/MountX/Android/obb" \
             "${SD_BASE}/MountX/Android/media" \
             "${SD_BASE}/MountX/containers" 2>/dev/null
    rm -f "${SD_BASE}/MountX/Android/.nomedia" 2>/dev/null
    touch "${SD_BASE}/MountX/Android/data/.nomedia" 2>/dev/null
    touch "${SD_BASE}/MountX/Android/obb/.nomedia" 2>/dev/null

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

    # Touch boot status marker so BootReceiver/UI can recognize completion
    touch "${BOOT_FLAG_FILE}" 2>/dev/null
    log_info "MountX boot marker touched at ${BOOT_FLAG_FILE}. Service completed."
}

main