#!/usr/bin/env bash
set -euo pipefail

test_dir="$RUNNER_TEMP/mygo-signing-test"
trap 'rm -f "$test_dir/original.apk" "$test_dir/rotated.apk"' EXIT
package=com.mygo.music
adb uninstall "$package" || true
adb install "$test_dir/original.apk"
adb shell "run-as $package mkdir -p shared_prefs"
adb shell "run-as $package tee shared_prefs/library.xml" >/dev/null <<'XML'
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
  <string name="liked">rotation-favorite</string>
  <string name="playlists">rotation-playlists</string>
  <string name="local_lyrics">rotation-lyrics</string>
</map>
XML

adb install -r "$test_dir/rotated.apk"
adb shell am start -W -n "$package/.MainActivity" | grep -F 'Status: ok'
for record in rotation-favorite rotation-playlists rotation-lyrics; do
    adb shell "run-as $package cat shared_prefs/library.xml" | grep -F "$record"
done

if result=$(adb install -r "$test_dir/original.apk" 2>&1); then
    echo "Android accepted an update with the retired test signer" >&2
    exit 1
fi
grep -F 'INSTALL_FAILED_UPDATE_INCOMPATIBLE' <<< "$result"
echo 'MYGO_SIGNING_ROTATION_OK: different Android 13 signer, app data preserved, original signer rejected'
