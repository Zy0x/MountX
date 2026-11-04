#!/sbin/sh
##########################################################################################
# MountX Module Installation Script (Magisk / KernelSU / APatch)
##########################################################################################

ui_print "************************************************"
ui_print "                   MountX                       "
ui_print "     Kernel VFS Bind-Mount Game Engine          "
ui_print "************************************************"

# 1. Preserve Existing User Configuration (Upgrade Protection)
PREV_MOD=""
if [ -d "/data/adb/modules/MountX" ]; then
    PREV_MOD="/data/adb/modules/MountX"
elif [ -d "/data/adb/modules/mountx" ]; then
    PREV_MOD="/data/adb/modules/mountx"
fi

if [ -n "$PREV_MOD" ]; then
    ui_print "- Preserving existing configuration..."
    [ -f "$PREV_MOD/config.conf" ] && cp -f "$PREV_MOD/config.conf" /data/local/tmp/mountx_cfg.bak 2>/dev/null
    [ -f "$PREV_MOD/gamelist.conf" ] && cp -f "$PREV_MOD/gamelist.conf" /data/local/tmp/mountx_game.bak 2>/dev/null
fi

# 2. Check Architecture & Android API Level
ui_print "- Checking Android Environment..."
API=$(getprop ro.build.version.sdk)
if [ -n "$API" ] && [ "$API" -lt 29 ]; then
    abort "! MountX requires Android 10 or newer (API 29+). Detected API: $API"
fi
ui_print "  [✓] Android $(getprop ro.build.version.release) (API $API) verified."

# 3. Extract and Install Companion APK
APK_FILE=""
if [ -f "$MODPATH/MountX.apk" ]; then
    APK_FILE="$MODPATH/MountX.apk"
elif [ -f "$ZIPFILE" ]; then
    ui_print "- Extracting MountX companion app..."
    unzip -o "$ZIPFILE" "MountX.apk" -d "$MODPATH" >/dev/null 2>&1
    [ -f "$MODPATH/MountX.apk" ] && APK_FILE="$MODPATH/MountX.apk"
fi

if [ -n "$APK_FILE" ] && [ -f "$APK_FILE" ]; then
    ui_print "- Installing companion app (MountX.apk)..."
    cp -f "$APK_FILE" /data/local/tmp/MountX_install.apk
    pm install -r /data/local/tmp/MountX_install.apk >/dev/null 2>&1
    if [ $? -eq 0 ]; then
        ui_print "  [✓] MountX app installed successfully!"
    else
        pm install /data/local/tmp/MountX_install.apk >/dev/null 2>&1
        if [ $? -eq 0 ]; then
            ui_print "  [✓] MountX app installed successfully!"
        else
            ui_print "  [!] Direct pm install failed. App package saved to /data/local/tmp/MountX_install.apk"
        fi
    fi
    rm -f /data/local/tmp/MountX_install.apk
    # Remove APK from module directory to save disk space
    rm -f "$APK_FILE"
fi

# 4. Setup Permissions
ui_print "- Setting permissions..."
set_perm_recursive "$MODPATH" 0 0 0755 0644
set_perm "$MODPATH/service.sh" 0 0 0755
[ -f "$MODPATH/post-fs-data.sh" ] && set_perm "$MODPATH/post-fs-data.sh" 0 0 0755
set_perm "$MODPATH/uninstall.sh" 0 0 0755
[ -f "$MODPATH/banner.png" ] && set_perm "$MODPATH/banner.png" 0 0 0644

# Setup standalone binaries and applets
if [ -d "$MODPATH/bin" ]; then
    ui_print "- Setting up standalone binary toolchain..."
    set_perm_recursive "$MODPATH/bin" 0 0 0755 0755
    if [ -f "$MODPATH/bin/busybox" ]; then
        set_perm "$MODPATH/bin/busybox" 0 0 0755
        for _tool in fstrim awk flock blkid losetup fdisk tar; do
            if [ ! -e "$MODPATH/bin/${_tool}" ] && ! command -v "${_tool}" >/dev/null 2>&1; then
                ln -sf "$MODPATH/bin/busybox" "$MODPATH/bin/${_tool}" 2>/dev/null || true
            fi
        done
    fi
    if [ -x "/system/bin/make_f2fs" ] && [ ! -e "$MODPATH/bin/mkfs.f2fs" ] && ! command -v mkfs.f2fs >/dev/null 2>&1; then
        ln -sf "/system/bin/make_f2fs" "$MODPATH/bin/mkfs.f2fs" 2>/dev/null || true
    fi
fi

# Restore preserved user configurations if existed
if [ -f /data/local/tmp/mountx_cfg.bak ]; then
    cp -f /data/local/tmp/mountx_cfg.bak "$MODPATH/config.conf" 2>/dev/null
    set_perm "$MODPATH/config.conf" 0 0 0644
    rm -f /data/local/tmp/mountx_cfg.bak
    ui_print "  [✓] Restored user config.conf"
fi
if [ -f /data/local/tmp/mountx_game.bak ]; then
    cp -f /data/local/tmp/mountx_game.bak "$MODPATH/gamelist.conf" 2>/dev/null
    set_perm "$MODPATH/gamelist.conf" 0 0 0644
    rm -f /data/local/tmp/mountx_game.bak
    ui_print "  [✓] Restored user gamelist.conf"
fi

# 5. Prepare MountX Data Directories
mkdir -p /data/sdext2
chmod 777 /data/sdext2 2>/dev/null

# 6. Zero-Residue Policy: Purge Installation Files & Residual Artifacts
ui_print "- Purging installation residue (Zero-Residue Policy)..."
rm -rf "$MODPATH/META-INF" 2>/dev/null || true
rm -f "$MODPATH/MountX.apk" 2>/dev/null || true
rm -f /data/local/tmp/MountX* /data/local/tmp/mountx* 2>/dev/null || true
rm -rf /data/adb/modules/Mountify /data/adb/modules_update/Mountify 2>/dev/null || true
rm -f "$MODPATH/customize.sh" 2>/dev/null || true

ui_print "************************************************"
ui_print "  MountX Module installed successfully!         "
ui_print "  Please reboot your device to activate service."
ui_print "************************************************"
