#!/usr/bin/env bash
set -euo pipefail
umask 077

: "${MYGO_RELEASE_KEYSTORE_B64:?missing protected Android release keystore}"
: "${MYGO_RELEASE_STORE_PASSWORD:?missing protected Android release store password}"
: "${MYGO_RELEASE_KEY_ALIAS:?missing release key alias}"
: "${MYGO_RELEASE_CERT_SHA256:?missing approved release certificate fingerprint}"

signer="${ANDROID_HOME:?}/build-tools/35.0.0/apksigner"
aligner="$ANDROID_HOME/build-tools/35.0.0/zipalign"
expected=$(printf '%s' "$MYGO_RELEASE_CERT_SHA256" | tr -d ':' | tr '[:upper:]' '[:lower:]')
retired=ac76d91ee08f815376445024494bfbd723a3e59cbb20a14026da97f6647db75a
if [[ ! "$expected" =~ ^[a-f0-9]{64}$ || "$expected" == "$retired" ]]; then
    echo 'The production signing certificate must be pinned and different from the retired certificate.' >&2
    exit 1
fi

input=${1:?missing unsigned APK}
output=${2:?missing output APK destination}
scratch=$(mktemp -d)
trap 'rm -rf "$scratch"' EXIT
printf '%s' "$MYGO_RELEASE_KEYSTORE_B64" | base64 --decode > "$scratch/release.keystore"
"$aligner" -P 16 -f 4 "$input" "$scratch/aligned.apk"
args=(--ks "$scratch/release.keystore" --ks-pass env:MYGO_RELEASE_STORE_PASSWORD --ks-key-alias "$MYGO_RELEASE_KEY_ALIAS")
if [[ -n "${MYGO_RELEASE_KEY_PASSWORD:-}" ]]; then
    args+=(--key-pass env:MYGO_RELEASE_KEY_PASSWORD)
fi
case ${3:-fresh-install} in
    fresh-install)
        "$signer" sign "${args[@]}" --out "$scratch/signed.apk" "$scratch/aligned.apk"
        ;;
    android13-upgrade)
        : "${MYGO_RELEASE_LINEAGE_B64:?missing approved production upgrade lineage}"
        printf '%s' "$MYGO_RELEASE_LINEAGE_B64" | base64 --decode > "$scratch/release.lineage"
        bash tests/sign-android13-upgrade.sh "$scratch/aligned.apk" "$scratch/signed.apk" \
            "$scratch/release.lineage" "$retired" "$expected" "${args[@]}"
        ;;
    *) echo 'Unknown Android build mode.' >&2; exit 1 ;;
esac
certs=$("$signer" verify --verbose --print-certs "$scratch/signed.apk")
actual=$(printf '%s\n' "$certs" | awk -F': ' '/^Signer #[0-9]+ certificate SHA-256 digest:/ {print tolower($2)}')
if [[ "$actual" != "$expected" ]]; then
    echo 'The signed APK certificate does not match the approved fingerprint.' >&2
    exit 1
fi
"$aligner" -c -P 16 4 "$scratch/signed.apk"
unzip -tq "$scratch/signed.apk"
mkdir -p "$(dirname "$output")"
mv "$scratch/signed.apk" "$output"
