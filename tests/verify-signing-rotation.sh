#!/usr/bin/env bash
set -euo pipefail

test_dir="$RUNNER_TEMP/mygo-signing-test"
trap 'rm -f "$test_dir/original.apk" "$test_dir/rotated.apk"' EXIT
package=com.mygo.music
adb uninstall "$package" || true
bash tests/verify-upgrade-data.sh "$test_dir/original.apk" "$test_dir/rotated.apk"

if result=$(adb install -r "$test_dir/original.apk" 2>&1); then
    echo "Android accepted an update with the retired test signer" >&2
    exit 1
fi
grep -F 'INSTALL_FAILED_UPDATE_INCOMPATIBLE' <<< "$result"
echo 'MYGO_SIGNING_ROTATION_OK: different Android 13 signer, app data preserved, original signer rejected'
