#!/usr/bin/env bash
set -euo pipefail

baseline=${1:?missing delivered base APK}
preview=${2:?missing signed preview APK}
base_package=com.mygo.music
preview_package=com.mygo.music.preview
activity=com.mygo.music.MainActivity

adb install "$baseline"
adb shell am start -W -n "$base_package/$activity" | grep -F 'Status: ok'
base_path=$(adb shell pm path "$base_package" | tr -d '\r')
adb install "$preview"
adb shell am start -W -n "$preview_package/$activity" | grep -F 'Status: ok'
test "$base_path" = "$(adb shell pm path "$base_package" | tr -d '\r')"
base_uid=$(adb shell pm list packages -U "$base_package" | awk -v pkg="package:$base_package" '$1 == pkg {print $2}')
preview_uid=$(adb shell pm list packages -U "$preview_package" | awk -v pkg="package:$preview_package" '$1 == pkg {print $2}')
[[ "$base_uid" == uid:* && "$preview_uid" == uid:* && "$base_uid" != "$preview_uid" ]]
adb shell am start -W -n "$base_package/$activity" | grep -F 'Status: ok'
echo 'MYGO_PREVIEW_PARALLEL_OK: signed preview and delivered 5.53.0 both launched with separate Android app identities'
