#!/usr/bin/env bash
# Builds the release APK, signs it with the upload key, and verifies the result.
set -euo pipefail

fail() { printf 'release-apk: %s\n' "$*" >&2; exit 1; }
note() { printf 'release-apk: %s\n' "$*"; }

script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
root=$(git -C "$script_dir" rev-parse --show-toplevel) || fail "not inside a Git repository"
cd "$root"

sdk_root=${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}
[ -n "$sdk_root" ] || fail "set ANDROID_SDK_ROOT or ANDROID_HOME to your Android SDK"

build_tools_version=${ANDROID_BUILD_TOOLS_VERSION:-36.0.0}
build_tools="$sdk_root/build-tools/$build_tools_version"
apksigner="$build_tools/apksigner"
zipalign="$build_tools/zipalign"
[ -x "$apksigner" ] || fail "apksigner not found: $apksigner"
[ -x "$zipalign" ] || fail "zipalign not found: $zipalign"

keystore=${RELEASE_KEYSTORE:-"$HOME/.android/keystores/root-my-galaxy-s24-upload.p12"}
key_alias=${RELEASE_KEY_ALIAS:-root-my-galaxy-s24-upload}
password_file=${RELEASE_KEYSTORE_PASSWORD_FILE:-"$HOME/.android/keystores/root-my-galaxy-s24-upload.password"}
expected_cert_sha256=${RELEASE_CERT_SHA256:-4256991b32a612cf6ec3e5d8730f135fc8afb082082def192b1abb04f9ff2e93}

[ -r "$keystore" ] || fail "release keystore is missing or unreadable: $keystore"
[ -r "$password_file" ] || fail "keystore password file is missing or unreadable: $password_file"

note "building release APK"
"$root/gradlew" --no-daemon :app:assembleRelease

apk_dir="$root/app/build/outputs/apk/release"
unsigned_apk="$apk_dir/app-release-unsigned.apk"
metadata="$apk_dir/output-metadata.json"
[ -f "$unsigned_apk" ] || fail "Gradle did not produce $unsigned_apk"
[ -f "$metadata" ] || fail "Gradle did not produce $metadata"

version_name=$(python3 - "$metadata" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as handle:
    metadata = json.load(handle)

if metadata.get("applicationId") != "dev.samson.s24root":
    sys.exit(f"unexpected applicationId: {metadata.get('applicationId')!r}")
elements = metadata.get("elements", [])
if len(elements) != 1 or not elements[0].get("versionName"):
    sys.exit("expected exactly one APK element with a versionName")
print(elements[0]["versionName"])
PY
) || fail "could not read the release version from $metadata"
[[ "$version_name" =~ ^[A-Za-z0-9._+-]+$ ]] || fail "unsafe versionName for output filename: $version_name"

output_dir=${RELEASE_OUTPUT_DIR:-"$apk_dir"}
if [[ "$output_dir" != /* ]]; then
  output_dir="$root/$output_dir"
fi
mkdir -p "$output_dir"
output_apk="$output_dir/roots24-$version_name.apk"
tmp_apk=$(mktemp "$output_dir/.roots24-${version_name}.XXXXXX.apk")
trap 'rm -f -- "$tmp_apk"' EXIT

note "signing $version_name with alias $key_alias"
"$apksigner" sign \
  --ks "$keystore" \
  --ks-type PKCS12 \
  --ks-key-alias "$key_alias" \
  --ks-pass "file:$password_file" \
  --out "$tmp_apk" \
  "$unsigned_apk"

verification=$("$apksigner" verify --verbose --print-certs "$tmp_apk") || fail "APK signature verification failed"
printf '%s\n' "$verification"
actual_cert_sha256=$(printf '%s\n' "$verification" | awk -F': ' '/Signer #1 certificate SHA-256 digest:/ { gsub(/:/, "", $2); print tolower($2); exit }')
expected_cert_sha256=${expected_cert_sha256//:/}
expected_cert_sha256=${expected_cert_sha256,,}
[ -n "$actual_cert_sha256" ] || fail "could not read the APK signing certificate SHA-256"
[ "$actual_cert_sha256" = "$expected_cert_sha256" ] || fail "APK signer is $actual_cert_sha256; expected $expected_cert_sha256"

"$zipalign" -c 4 "$tmp_apk" >/dev/null || fail "APK is not zipaligned"
mv -f -- "$tmp_apk" "$output_apk"
trap - EXIT

note "created $output_apk"
sha256sum "$output_apk"
