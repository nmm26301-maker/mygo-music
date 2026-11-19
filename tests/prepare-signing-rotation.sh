#!/usr/bin/env bash
set -euo pipefail
umask 077

signer="${ANDROID_HOME:?}/build-tools/35.0.0/apksigner"
target="$RUNNER_TEMP/mygo-signing-test"
mkdir -p "$target"
keys=$(mktemp -d)
trap 'rm -rf "$keys"' EXIT
export MYGO_TEST_KEY_PASSWORD
MYGO_TEST_KEY_PASSWORD=$(openssl rand -hex 24)
for key in original rotated; do
    keytool -genkeypair -alias rotation-test -dname "CN=Disposable Android Test $key" \
        -keyalg RSA -keysize 2048 -validity 30 -storetype PKCS12 \
        -keystore "$keys/$key.p12" -storepass:env MYGO_TEST_KEY_PASSWORD
done
original=(--ks "$keys/original.p12" --ks-key-alias rotation-test --ks-pass env:MYGO_TEST_KEY_PASSWORD)
rotated=(--ks "$keys/rotated.p12" --ks-key-alias rotation-test --ks-pass env:MYGO_TEST_KEY_PASSWORD)
"$signer" rotate --out "$keys/lineage" --old-signer "${original[@]}" --set-installed-data true --set-shared-uid false \
    --set-permission false --set-rollback false --set-auth false --new-signer "${rotated[@]}"
input=app/build/outputs/apk/debug/app-debug.apk
"$signer" sign "${original[@]}" --out "$target/original.apk" "$input"
for alias in original rotated; do
    keytool -exportcert -keystore "$keys/$alias.p12" -alias rotation-test \
        -storepass:env MYGO_TEST_KEY_PASSWORD | sha256sum | awk '{print $1}' > "$keys/$alias.pin"
done
bash tests/sign-android13-upgrade.sh "${1:?missing SDK 33 test APK}" "$target/rotated.apk" \
    "$keys/lineage" "$(cat "$keys/original.pin")" "$(cat "$keys/rotated.pin")" "${rotated[@]}"

for apk in original rotated; do
    expected=$(keytool -exportcert -keystore "$keys/$apk.p12" -alias rotation-test \
        -storepass:env MYGO_TEST_KEY_PASSWORD | sha256sum | awk '{print $1}')
    "$signer" verify --min-sdk-version 33 --max-sdk-version 33 --print-certs "$target/$apk.apk" \
        | awk -F': ' '/certificate SHA-256 digest/ {print tolower($2)}' > "$keys/$apk.sha256"
    if ! grep -Fxq "$expected" "$keys/$apk.sha256"; then
        echo "Signing report for $apk did not contain the expected disposable certificate." >&2
        cat "$keys/$apk.sha256" >&2
        exit 1
    fi
done
