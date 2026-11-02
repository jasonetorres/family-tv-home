#!/bin/bash
# Family Home on your TV: build, install, make it the home screen, and undo that.
# Adapted from upstream Luma TV's luma.sh (MIT, 0xProbability).
#   ./family-home.sh [-s DEVICE] build | install | home | widgets | restore | status
# DEVICE is the TV adb address (config/family.json tv.address) or a serial.
# Default: $FAMILY_TV, else tv.address when it is not the example placeholder, else the only adb device.
set -euo pipefail
cd "$(dirname "$0")"
DEVICE="${FAMILY_TV:-${LUMA_DEVICE:-}}"
if [ "${1:-}" = "-s" ]; then DEVICE="$2"; shift 2; fi
ACTION="${1:-status}"
PKG="app.familytv.launcher"
HOME_ACTIVITY="$PKG/.MainActivity"
# The only packages this script ever switches off: stock TV launchers, and Google TV's setup "recovery" home,
# which outranks any other home screen once the launcher is off.
KNOWN="com.google.android.tvlauncher com.google.android.apps.tv.launcherx com.google.android.leanbacklauncher com.google.android.tungsten.setupwraith"

die() { echo "family-home: $*" >&2; exit 1; }
pick_device() {
    if [ -n "$DEVICE" ]; then
        case "$DEVICE" in *:*) adb connect "$DEVICE" >/dev/null 2>&1 || true ;; esac
        return
    fi
    local list; list="$(adb devices | awk 'NR>1 && $2=="device" {print $1}')"
    [ -n "$list" ] || die "no TV found. Turn on ADB debugging on the TV, then: adb connect <TV IP>:5555"
    [ "$(echo "$list" | wc -l | tr -d ' ')" = "1" ] || die "several devices connected; choose one with -s DEVICE:
$list"
    DEVICE="$list"
}
tv() { adb -s "$DEVICE" shell "$@" | tr -d '\r'; }
home_now() { tv cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -n 1; }
state_file() { mkdir -p .local; echo ".local/home-$(tv getprop ro.serialno | tr -cd 'A-Za-z0-9').txt"; }
installed() { tv pm list packages "$1" | grep -qx "package:$1"; }
show_home() { tv dumpsys power | grep -q 'mWakefulness=Awake' && tv input keyevent KEYCODE_HOME || true; } # never wakes a sleeping TV

case "$ACTION" in
build)
    bash build.sh ;;
install)
    pick_device
    [ -f dist/family-home.apk ] || bash build.sh
    adb -s "$DEVICE" install --no-incremental -r dist/family-home.apk ;;
home)
    pick_device
    installed app.familytv.launcher || die "Family Home is not installed yet: ./family-home.sh install"
    state="$(state_file)"
    if [ ! -s "$state" ]; then echo "original=$(home_now)" > "$state"; fi
    tv cmd package set-home-activity --user 0 "$HOME_ACTIVITY" >/dev/null
    for _ in 1 2 3 4; do
        current="$(home_now)"
        [ "$current" = "$HOME_ACTIVITY" ] && break
        pkg="${current%%/*}"
        case " $KNOWN " in
        *" $pkg "*)
            echo "Switching off $pkg, which the TV puts above any other home screen."
            tv pm disable-user --user 0 "$pkg" >/dev/null
            echo "disabled=$pkg" >> "$state"
            tv cmd package set-home-activity --user 0 "$HOME_ACTIVITY" >/dev/null ;;
        *) die "the TV still opens $current at Home, and family-home.sh does not switch off unknown apps. Nothing else was changed; ./family-home.sh restore undoes this." ;;
        esac
    done
    [ "$(home_now)" = "$HOME_ACTIVITY" ] || die "the TV did not accept Family Home as its home screen. ./family-home.sh restore undoes the changes."
    show_home
    echo "Family Home is the home screen. Undo at any time: ./family-home.sh restore" ;;
widgets)
    # The Home widgets work without this; it switches on what a TV remote makes tedious to allow by hand.
    pick_device
    installed app.familytv.launcher || die "Family Home is not installed yet: ./family-home.sh install"
    tv pm grant app.familytv.launcher android.permission.READ_TV_LISTINGS && echo "Up Next: Continue watching from your apps."
    tv cmd notification allow_listener app.familytv.launcher/app.familytv.launcher.MediaListener && echo "Now Playing: music playing on the TV."
    tv appops set app.familytv.launcher GET_USAGE_STATS allow && echo "Up Next: recently used apps."
    echo "Undo any of these in the TV's Settings → Apps → Special app access, or Settings → Apps → Family Home → Permissions." ;;
restore)
    pick_device
    state="$(state_file)"
    if [ -s "$state" ]; then
        disabled="$(sed -n 's/^disabled=//p' "$state")"; original="$(sed -n 's/^original=//p' "$state" | head -n 1)"
    else # set up by hand or on another computer: switch every known launcher back on
        disabled=""; original=""
        for pkg in $KNOWN; do installed "$pkg" && tv pm list packages -d "$pkg" | grep -qx "package:$pkg" && disabled="$disabled $pkg"; done
    fi
    for pkg in $disabled; do echo "Switching $pkg back on."; tv pm enable --user 0 "$pkg" >/dev/null || true; done
    if [ -n "$original" ] && [ "$original" != "$HOME_ACTIVITY" ] && [[ "$original" =~ ^[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+$ ]]; then
        tv cmd package set-home-activity --user 0 "$original" >/dev/null || true
    fi
    rm -f "$state"
    show_home
    echo "Home now opens $(home_now). Family Home stays installed (adb uninstall app.familytv.launcher removes it)." ;;
status)
    pick_device
    echo "TV:   $(tv getprop ro.product.manufacturer) $(tv getprop ro.product.model), Android $(tv getprop ro.build.version.release)"
    echo "Family Home: $(tv dumpsys package app.familytv.launcher | sed -n 's/^ *versionName=//p' | head -n 1 | grep . || echo 'not installed')"
    echo "Home: $(home_now)" ;;
*)
    die "unknown command '$ACTION'. Use build, install, home, widgets, restore or status." ;;
esac
