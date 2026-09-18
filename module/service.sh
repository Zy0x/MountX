#!/system/bin/sh
# =============================================================================
# Mountify — service.sh
# Magisk module service script.
# Runs after boot; bind-mounts game data folders from an external SD partition
# into every active Android namespace.
#
# Configuration files (in module directory):
#   config.conf   — SD_BASE, SD_BLOCK, FS_TYPE
#   gamelist.conf — one "pkg_name:mode" entry per line
#
# Author : Noir
# License: MIT
# =============================================================================

# ── Module paths ──────────────────────────────────────────────────────────────
MODULE_DIR="/data/adb/modules/Mountify"
CONFIG_FILE="${MODULE_DIR}/config.conf"
GAMELIST_FILE="${MODULE_DIR}/gamelist.conf"
LOG_FILE="${MODULE_DIR}/mountify.log"

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
    echo "[${ts}] [${level}] ${msg}" >> "/storage/emulated/0/mountify.log" 2>/dev/null
}

log_info()  { log "INFO " "$@"; }
log_warn()  { log "WARN " "$@"; }
log_error() { log "ERROR" "$@"; }

# ── Initialise log for this boot ──────────────────────────────────────────────
{
    echo "============================================================"
    echo " Mountify service started — $(date)"
    echo "============================================================"
} >> "${LOG_FILE}"

# ── Load config.conf ─────────────────────────────────────────────────────────
load_config() {
    if [ ! -f "${CONFIG_FILE}" ]; then
        log_warn "config.conf not found; using built-in defaults"
        return
    fi

    while IFS='=' read -r key val; do
        # Strip inline comments and leading/trailing whitespace
        key=$(echo "${key}" | sed 's/#.*//' | tr -d ' \t')
        val=$(echo "${val}" | sed 's/#.*//' | tr -d ' \t')
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

# ── Wait until Android has finished booting ───────────────────────────────────
wait_for_boot() {
    log_info "Waiting for sys.boot_completed…"
    local retries=0
    until [ "$(getprop sys.boot_completed)" = "1" ]; do
        sleep 5
        retries=$((retries + 1))
        if [ "${retries}" -ge 60 ]; then
            log_error "Timed out waiting for boot_completed after 5 min; aborting."
            exit 1
        fi
    done
    # Give system services a few extra seconds to settle
    sleep 5
    log_info "Boot completed detected."
}

# ── Mount the SD partition ────────────────────────────────────────────────────
mount_sd() {
    if ! [ -b "${SD_BLOCK}" ]; then
        log_error "Block device ${SD_BLOCK} not found; aborting."
        exit 1
    fi

    mkdir -p "${SD_BASE}"

    if mountpoint -q "${SD_BASE}"; then
        log_info "SD already mounted at ${SD_BASE}."
        return 0
    fi

    local mnt_opts="rw,noatime,nodiratime"
    if [ "${FS_TYPE}" = "f2fs" ]; then
        mnt_opts="${mnt_opts},inline_data,inline_dentry,flush_merge,mode=adaptive"
    elif [ "${FS_TYPE}" = "ext4" ]; then
        mnt_opts="${mnt_opts},commit=60,delalloc,data=writeback"
    fi

    if mount -t "${FS_TYPE}" -o "${mnt_opts}" "${SD_BLOCK}" "${SD_BASE}"; then
        log_info "SD mounted: ${SD_BLOCK} → ${SD_BASE} (${FS_TYPE} with ${mnt_opts})"
    else
        log_warn "Optimized mount failed, attempting generic fallback mount…"
        if mount -t "${FS_TYPE}" -o rw,noatime "${SD_BLOCK}" "${SD_BASE}"; then
            log_info "SD mounted with fallback options: ${SD_BLOCK} → ${SD_BASE}"
        else
            log_error "Failed to mount ${SD_BLOCK} as ${FS_TYPE} at ${SD_BASE}."
            exit 1
        fi
    fi
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

    # Apply to all virtual block device interfaces (BDI)
    for bdi in /sys/devices/virtual/bdi/*/read_ahead_kb; do
        [ -w "${bdi}" ] && echo "${READ_AHEAD_KB}" > "${bdi}" 2>/dev/null
    done

    # Retain dentry and inode cache in RAM for instantaneous metadata lookups
    if [ -w "/proc/sys/vm/vfs_cache_pressure" ]; then
        echo "${VFS_CACHE_PRESSURE}" > /proc/sys/vm/vfs_cache_pressure
    fi

    log_info "I/O tweaks successfully applied to ${disk_name}."
}

# ── Read gamelist.conf into arrays ────────────────────────────────────────────
# Returns pairs: GAME_PKGS[] and GAME_MODES[]
GAME_PKGS=""
GAME_MODES=""

load_gamelist() {
    if [ ! -f "${GAMELIST_FILE}" ]; then
        log_warn "gamelist.conf not found at ${GAMELIST_FILE}; no games will be mounted."
        return
    fi

    local count=0
    while IFS= read -r line || [ -n "${line}" ]; do
        # Strip leading/trailing whitespace
        line=$(echo "${line}" | tr -d '\r' | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')
        # Skip empty lines and comments
        case "${line}" in
            ''|\#*) continue ;;
        esac

        local pkg mode
        pkg=$(echo "${line}" | cut -d':' -f1)
        mode=$(echo "${line}" | cut -d':' -f2)

        if [ -z "${pkg}" ] || [ -z "${mode}" ]; then
            log_warn "gamelist.conf: malformed line ignored → '${line}'"
            continue
        fi

        case "${mode}" in
            pkg|files) ;;
            *)
                log_warn "gamelist.conf: unknown mode '${mode}' for '${pkg}'; skipping."
                continue
                ;;
        esac

        GAME_PKGS="${GAME_PKGS}${pkg} "
        GAME_MODES="${GAME_MODES}${mode} "
        count=$((count + 1))
    done < "${GAMELIST_FILE}"

    log_info "gamelist.conf loaded: ${count} game(s) registered."
}

# ── Unmount any stale bind-mounts for a target path ──────────────────────────
umount_stale() {
    local target="$1"
    if mountpoint -q "${target}"; then
        if umount -l "${target}" 2>/dev/null; then
            log_info "  Unmounted stale bind-mount: ${target}"
        else
            log_warn "  Could not unmount: ${target}"
        fi
    fi
}

# ── Apply ownership, permissions, and SELinux context to SD source dir ────────
apply_permissions() {
    local pkg="$1"
    local src_dir="$2"

    local uid gid
    uid=$(stat -c '%u' "/data/data/${pkg}" 2>/dev/null)
    gid=$(stat -c '%g' "/data/data/${pkg}" 2>/dev/null)

    if [ -z "${uid}" ] || [ -z "${gid}" ]; then
        log_warn "  [${pkg}] Could not determine UID/GID; skipping chown."
        return
    fi

    chown -R "${uid}:${gid}" "${src_dir}" 2>/dev/null \
        && log_info "  [${pkg}] chown ${uid}:${gid} → ${src_dir}" \
        || log_warn "  [${pkg}] chown failed on ${src_dir}"

    chmod -R 0771 "${src_dir}" 2>/dev/null \
        || log_warn "  [${pkg}] chmod failed on ${src_dir}"

    # Restore SELinux context to match the app's data directory
    local ctx
    ctx=$(ls -Z "/data/data/${pkg}" 2>/dev/null | awk '{print $1}' | head -n1)
    if [ -n "${ctx}" ]; then
        chcon -R "${ctx}" "${src_dir}" 2>/dev/null \
            && log_info "  [${pkg}] SELinux context '${ctx}' applied." \
            || log_warn "  [${pkg}] chcon failed."
    else
        log_warn "  [${pkg}] Could not read SELinux context."
    fi
}

# ── Bind-mount src → dst in every active PID namespace ───────────────────────
bind_mount_all_ns() {
    local src="$1"
    local dst="$2"
    local pkg="$3"

    local bound=0 failed=0

    for pid_ns_dir in /proc/*/ns/mnt; do
        local pid_dir
        pid_dir=$(dirname "$(dirname "${pid_ns_dir}")")
        local pid
        pid=$(basename "${pid_dir}")

        # Only mount into the target package's process(es)
        local cmdline
        cmdline=$(cat "${pid_dir}/cmdline" 2>/dev/null | tr '\0' ' ' | cut -d' ' -f1)
        [ "${cmdline}" = "${pkg}" ] || continue

        # Perform the bind-mount inside the process's mount namespace
        if nsenter --mount="${pid_ns_dir}" -- mount --bind "${src}" "${dst}" 2>/dev/null; then
            log_info "  [${pkg}] bind-mounted in namespace of PID ${pid}"
            bound=$((bound + 1))
        else
            log_warn "  [${pkg}] bind-mount failed in namespace of PID ${pid}"
            failed=$((failed + 1))
        fi
    done

    # Fallback: also bind-mount in the global namespace
    if mount --bind "${src}" "${dst}" 2>/dev/null; then
        log_info "  [${pkg}] bind-mounted in global namespace: ${src} → ${dst}"
        bound=$((bound + 1))
    else
        log_warn "  [${pkg}] global namespace bind-mount failed: ${src} → ${dst}"
        failed=$((failed + 1))
    fi

    log_info "  [${pkg}] bind-mount summary: ${bound} succeeded, ${failed} failed."
}

# ── Process a single game entry ───────────────────────────────────────────────
process_game() {
    local pkg="$1"
    local mode="$2"
    local ts
    ts=$(date '+%Y-%m-%d %H:%M:%S')
    log_info "── Processing [${pkg}] mode=${mode} at ${ts}"

    # Internal (system) data and obb paths
    local int_data="/data/media/0/Android/data/${pkg}"
    local int_obb="/data/media/0/Android/obb/${pkg}"
    # External SD source data and obb paths
    local sd_data="${SD_BASE}/Android/data/${pkg}"
    local sd_obb="${SD_BASE}/Android/obb/${pkg}"

    # Determine data source and destination sub-paths based on mode
    local src_data dst_data
    case "${mode}" in
        pkg)
            src_data="${sd_data}"
            dst_data="${int_data}"
            ;;
        files)
            src_data="${sd_data}/files"
            dst_data="${int_data}/files"
            ;;
    esac

    # 1. Mount Data if present on SD
    if [ -d "${src_data}" ]; then
        if [ ! -d "${dst_data}" ]; then
            mkdir -p "${dst_data}" || log_warn "  [${pkg}] Could not create destination data dir"
        fi
        umount_stale "${dst_data}"
        apply_permissions "${pkg}" "${src_data}"
        bind_mount_all_ns "${src_data}" "${dst_data}" "${pkg}"
    else
        log_warn "  [${pkg}] Data source not found on SD: ${src_data}"
    fi

    # 2. Mount OBB if present on SD
    if [ -d "${sd_obb}" ]; then
        log_info "  [${pkg}] OBB directory detected on SD: ${sd_obb}"
        if [ ! -d "${int_obb}" ]; then
            mkdir -p "${int_obb}" || log_warn "  [${pkg}] Could not create destination obb dir"
        fi
        umount_stale "${int_obb}"
        apply_permissions "${pkg}" "${sd_obb}"
        bind_mount_all_ns "${sd_obb}" "${int_obb}" "${pkg}"
    fi
}

# ── Main ──────────────────────────────────────────────────────────────────────
main() {
    wait_for_boot
    load_config
    mount_sd
    apply_io_tweaks
    load_gamelist

    if [ -z "${GAME_PKGS}" ]; then
        log_warn "No games in gamelist; nothing to mount."
        exit 0
    fi

    log_info "Starting bind-mount loop…"

    # Iterate parallel arrays using positional IFS splitting
    local i=1
    for pkg in ${GAME_PKGS}; do
        local mode
        mode=$(echo "${GAME_MODES}" | cut -d' ' -f"${i}")
        process_game "${pkg}" "${mode}"
        i=$((i + 1))
    done

    log_info "All games processed. Mountify service done."
}

main