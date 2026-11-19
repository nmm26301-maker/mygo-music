#!/usr/bin/env bash
set -euo pipefail

old=${1:?missing baseline APK}
update=${2:?missing Android 13 upgrade APK}
package=com.mygo.music
if [[ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" != 1 ]]; then
    echo 'This smoke test requires a disposable Android emulator.' >&2
    exit 1
fi
adb root
adb wait-for-device
test "$(adb shell id -u | tr -d '\r')" = 0

adb install "$old"
adb shell am start -W -n "$package/.MainActivity" | grep -F 'Status: ok'
adb shell am force-stop "$package"
data="/data/user/0/$package"
owner=$(adb shell stat -c '%u:%g' "$data" | tr -d '\r')
[[ "$owner" =~ ^[0-9]+:[0-9]+$ ]]
adb shell mkdir -p "$data/shared_prefs"
adb shell "cat > '$data/shared_prefs/library.xml'" <<'XML'
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
  <string name="liked">content://migration/favorite</string>
  <string name="playlists">{"migration-playlist":["content://migration/favorite"]}</string>
  <string name="local_lyrics">{"content://migration/favorite":"migration-lyrics"}</string>
</map>
XML
adb shell chown -R "$owner" "$data/shared_prefs"
adb shell chmod 771 "$data/shared_prefs"
adb shell chmod 660 "$data/shared_prefs/library.xml"
adb shell restorecon -R "$data/shared_prefs"
expected=$(adb shell cat "$data/shared_prefs/library.xml" | sha256sum)

adb install -r "$update"
test "$(adb shell cat "$data/shared_prefs/library.xml" | sha256sum)" = "$expected"
adb shell am start -W -n "$package/.MainActivity" | grep -F 'Status: ok'
for marker in content://migration/favorite migration-playlist migration-lyrics; do
    adb shell cat "$data/shared_prefs/library.xml" | grep -Fq "$marker"
done
echo 'MYGO_UPGRADE_DATA_OK: baseline upgraded in place and representative app data survived'
