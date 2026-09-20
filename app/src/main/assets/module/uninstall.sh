#!/system/bin/sh
# =============================================================================
# MountX — uninstall.sh
# Magisk / KernelSU / APatch official module uninstall script.
# Triggered automatically when the module is removed.
# Ensures clean ecosystem teardown with Zero-Residue Policy.
# =============================================================================

MODDIR="${0%/*}"

# 1. Unmount all active bind mounts from MicroSD instantly (lazy unmount)
if [ -f "${MODDIR}/config.conf" ]; then
    . "${MODDIR}/config.conf"
fi
SD_BASE="${SD_BASE:-/data/sdext2}"

for m in $(grep "$SD_BASE" /proc/mounts 2>/dev/null | cut -d' ' -f2); do
    if [ "$m" != "$SD_BASE" ]; then
        umount -f -l "$m" 2>/dev/null
    fi
done

# Also lazy unmount SD_BASE itself if mounted
umount -f -l "$SD_BASE" 2>/dev/null

# 2. Detach any virtual loop containers associated with .mountx/containers
for loop_dev in $(losetup -a 2>/dev/null | grep "\.mountx/containers" | cut -d':' -f1); do
    if [ -n "$loop_dev" ]; then
        sync
        umount -f -l "$loop_dev" 2>/dev/null
        losetup -d "$loop_dev" 2>/dev/null
    fi
done

# 3. Restore internal storage folder permissions and SELinux contexts to factory stock
chmod -R 775 /data/media/0/Android/data 2>/dev/null
chmod -R 775 /data/media/0/Android/obb 2>/dev/null
restorecon -FR /data/media/0/Android 2>/dev/null

# Dual apps / multi-user restore if present
if [ -d "/data/media/999/Android" ]; then
    chmod -R 775 /data/media/999/Android/data 2>/dev/null
    chmod -R 775 /data/media/999/Android/obb 2>/dev/null
    restorecon -FR /data/media/999/Android 2>/dev/null
fi

# 4. Clean up runtime memory buffers and temporary mounts in /dev/mountx/
rm -rf /dev/mountx 2>/dev/null

# Exit cleanly
exit 0
