#!/usr/bin/env bash
set -euo pipefail
umask 077

unsigned=${1:?missing unsigned APK}
original=${2:?missing baseline APK}
recipient=${3:?missing transfer recipient certificate}
baseline=72b182157b215012444594a99bfbc1594cf4e4f2
retired=ac76d91ee08f815376445024494bfbd723a3e59cbb20a14026da97f6647db75a
signer="${ANDROID_HOME:?}/build-tools/35.0.0/apksigner"
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

echo "191541167a0433cdacea969c20387cce87e75dc2833ca20d2274be29e59e2c5d  $original" | sha256sum -c
apk_pin=$("$signer" verify --print-certs "$original" | awk -F': ' '/^Signer #1 certificate SHA-256 digest:/ {print tolower($2)}')
test "$apk_pin" = "$retired"

git fetch --quiet --depth=1 origin "$baseline"
git show "$baseline:signing/mygo-release.keystore.b64" | base64 --decode > "$work/retired.keystore"
mapfile -t legacy < <(git show "$baseline:app/build.gradle" | python3 -c '
import re, sys
gradle = sys.stdin.read()
for option in ("keyAlias", "storePassword", "keyPassword"):
    matches = re.findall(r"\b" + option + r"\s+\x27([^\x27\r\n]+)\x27", gradle)
    if len(matches) != 1:
        raise SystemExit("Unexpected historical signing config")
    print(matches[0])
')
test "${#legacy[@]}" -eq 3
export MYGO_RETIRED_STORE_PASS="${legacy[1]}" MYGO_RETIRED_KEY_PASS="${legacy[2]}"
retired_pin=$(keytool -exportcert -keystore "$work/retired.keystore" -alias "${legacy[0]}" \
    -storepass:env MYGO_RETIRED_STORE_PASS | sha256sum | awk '{print $1}')
test "$retired_pin" = "$retired"

export MYGO_RELEASE_STORE_PASSWORD
MYGO_RELEASE_STORE_PASSWORD=$(openssl rand -hex 32)
export MYGO_RELEASE_KEY_ALIAS=mygo-release-2026
keytool -genkeypair -keystore "$work/release.p12" -storetype PKCS12 -keyalg RSA -keysize 3072 \
    -alias "$MYGO_RELEASE_KEY_ALIAS" -dname 'CN=MyGo Music Android Release' -validity 10000 \
    -storepass:env MYGO_RELEASE_STORE_PASSWORD -noprompt
export MYGO_RELEASE_CERT_SHA256
MYGO_RELEASE_CERT_SHA256=$(keytool -exportcert -keystore "$work/release.p12" -alias "$MYGO_RELEASE_KEY_ALIAS" \
    -storepass:env MYGO_RELEASE_STORE_PASSWORD | sha256sum | awk '{print $1}')
"$signer" rotate --out "$work/release.lineage" \
    --old-signer --ks "$work/retired.keystore" --ks-key-alias "${legacy[0]}" \
    --ks-pass env:MYGO_RETIRED_STORE_PASS --key-pass env:MYGO_RETIRED_KEY_PASS \
    --set-installed-data true --set-shared-uid false --set-permission false --set-rollback false --set-auth false \
    --new-signer --ks "$work/release.p12" --ks-key-alias "$MYGO_RELEASE_KEY_ALIAS" \
    --ks-pass env:MYGO_RELEASE_STORE_PASSWORD
rm "$work/retired.keystore"
unset legacy MYGO_RETIRED_STORE_PASS MYGO_RETIRED_KEY_PASS

export MYGO_RELEASE_KEYSTORE_B64 MYGO_RELEASE_LINEAGE_B64
MYGO_RELEASE_KEYSTORE_B64=$(base64 -w0 "$work/release.p12")
MYGO_RELEASE_LINEAGE_B64=$(base64 -w0 "$work/release.lineage")
apk=cover/mygo-music-v5.56.0-android13-cover.apk
bash tests/sign-release.sh "$unsigned" "$apk" android13-upgrade
"${ANDROID_HOME}/build-tools/35.0.0/aapt" dump badging "$apk" > "$work/badging"
grep -F "package: name='com.mygo.music' versionCode='106' versionName='5.56.0'" "$work/badging"
grep -Fx "sdkVersion:'33'" "$work/badging"
"$signer" verify --min-sdk-version 33 --max-sdk-version 33 --verbose --print-certs "$apk"
sha256sum "$apk"
unset MYGO_RELEASE_KEYSTORE_B64 MYGO_RELEASE_LINEAGE_B64

printf '%s\n' "$MYGO_RELEASE_STORE_PASSWORD" > "$work/release.password"
printf '%s\n' "$MYGO_RELEASE_CERT_SHA256" > "$work/release.pin"
printf '%s\n' "$MYGO_RELEASE_KEY_ALIAS" > "$work/release.alias"
mkdir -p sealed
tar -C "$work" -cf - release.p12 release.lineage release.password release.pin release.alias \
    | openssl cms -encrypt -binary -aes-256-gcm -outform DER -recip "$recipient" \
        -out sealed/mygo-release-signing.cms
echo 'MYGO_COVER_SIGNED: new release certificate and installed-data-only rotation proof verified'
