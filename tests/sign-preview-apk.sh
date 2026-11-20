#!/usr/bin/env bash
set -euo pipefail
umask 077

input=${1:?missing unsigned preview APK}
output=${2:?missing signed preview path}
tools="${ANDROID_HOME:?}/build-tools/35.0.0"
scratch=$(mktemp -d)
trap 'rm -rf "$scratch"' EXIT

badging=$("$tools/aapt" dump badging "$input")
grep -q "^package: name='com.mygo.music.preview' " <<< "$badging"
grep -q "^application-label:'MyGo Music 体验版'$" <<< "$badging"
grep -q "^native-code: 'arm64-v8a' 'x86_64'" <<< "$badging"
if grep -q '^application-debuggable' <<< "$badging"; then
    echo 'Preview delivery requires a non-debuggable release build.' >&2
    exit 1
fi

export MYGO_PREVIEW_PASSWORD
MYGO_PREVIEW_PASSWORD=$(openssl rand -hex 32)
keytool -genkeypair -keystore "$scratch/preview.p12" -storetype PKCS12 -keyalg RSA -keysize 3072 \
    -alias preview -dname 'CN=MyGo Music Preview' -validity 3650 \
    -storepass:env MYGO_PREVIEW_PASSWORD -keypass:env MYGO_PREVIEW_PASSWORD -noprompt
"$tools/zipalign" -P 16 -f 4 "$input" "$scratch/aligned.apk"
mkdir -p "$(dirname "$output")"
"$tools/apksigner" sign --ks "$scratch/preview.p12" --ks-key-alias preview \
    --ks-pass env:MYGO_PREVIEW_PASSWORD --out "$output" "$scratch/aligned.apk"
"$tools/apksigner" verify --verbose --print-certs "$output"
"$tools/zipalign" -c -P 16 4 "$output"
unzip -tq "$output"
