#!/usr/bin/env bash
set -euo pipefail

input=${1:?missing APK}
output=${2:?missing output APK}
lineage=${3:?missing signing lineage}
old=${4:?missing original certificate pin}
new=${5:?missing new certificate pin}
shift 5

tools="${ANDROID_HOME:?}/build-tools/35.0.0"
"$tools/aapt" dump badging "$input" | awk '
    /^sdkVersion:/ { found = ($0 == "sdkVersion:\04733\047") }
    END { exit !found }
' || { echo 'An Android 13 upgrade APK must require SDK 33 in its manifest.' >&2; exit 1; }
"$tools/apksigner" lineage --in "$lineage" --print-certs -v | python3 tests/check-signing-lineage.py "$old" "$new"
"$tools/apksigner" sign --lineage "$lineage" --rotation-min-sdk-version 28 \
    --v1-signing-enabled false --v2-signing-enabled false --v3-signing-enabled true \
    --v4-signing-enabled false "$@" --out "$output" "$input"
