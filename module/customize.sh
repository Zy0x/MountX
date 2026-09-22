#!/sbin/sh
##########################################################################################
# MountX Module Installation Script (Magisk / KernelSU / APatch)
##########################################################################################

ui_print "************************************************"
ui_print "                   MountX                       "
ui_print "     Kernel VFS Bind-Mount Game Engine          "
ui_print "************************************************"

# 1. Check Architecture & Android API Level
ui_print "- Checking Android Environment..."
API=$(getprop ro.build.version.sdk)
if [ -n "$API" ] && [ "$API" -lt 29 ]; then
    abort "! MountX requires Android 10 or newer (API 29+). Detected API: $API"
fi
ui_print "  [✓] Android $(getprop ro.build.version.release) (API $API) verified."

# 2. Extract and Install Companion APK
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

# 3. Setup Permissions
ui_print "- Setting permissions..."
set_perm_recursive "$MODPATH" 0 0 0755 0644
set_perm "$MODPATH/service.sh" 0 0 0755
set_perm "$MODPATH/uninstall.sh" 0 0 0755
[ -f "$MODPATH/banner.png" ] && set_perm "$MODPATH/banner.png" 0 0 0644

# 4. Prepare MountX Data Directories
mkdir -p /data/sdext2
chmod 777 /data/sdext2 2>/dev/null

ui_print "************************************************"
ui_print "  MountX Module installed successfully!         "
ui_print "  Please reboot your device to activate service."
ui_print "************************************************"
