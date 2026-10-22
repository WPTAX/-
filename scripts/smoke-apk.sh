#!/usr/bin/env bash
set -euo pipefail
apk="$1"
package=com.ironproject.woonghee.v42
adb install "$apk"
adb shell am start -W -n "$package/com.ironproject.woonghee.MainActivity"
sleep 4
test -n "$(adb shell pidof "$package" | tr -d '\r')"
adb install -r "$apk"
adb shell am force-stop "$package"
adb shell am start -W -n "$package/com.ironproject.woonghee.MainActivity"
sleep 4
test -n "$(adb shell pidof "$package" | tr -d '\r')"
adb logcat -d -s AndroidRuntime:E > dist/runtime-errors.txt
if grep -q 'FATAL EXCEPTION' dist/runtime-errors.txt; then exit 1; fi
adb shell screencap -p /sdcard/iron-home.png
adb pull /sdcard/iron-home.png dist/emulator-home.png
