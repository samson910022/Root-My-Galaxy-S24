#!/usr/bin/env bash
# Builds the release APK, signs it with the upload key, and verifies the result.
set -euo pipefail

fail() { printf 'release-apk: %s\n' "$*" >&2; exit 1; }
note() { printf 'release-apk: %s\n' "$*"; }

# Portable SHA-256 printer: GNU sha256sum, stock macOS shasum, then python3
# (already a hard dependency for output-metadata.json parsing) as last resort.
sha256_file() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum -- "$1"
  elif command -v shasum >/dev/null 2>&1; then
    shasum -a 256 -- "$1"
  elif command -v python3 >/dev/null 2>&1; then
    python3 - "$1" <<'PY'
import hashlib
import sys
with open(sys.argv[1], 'rb') as handle:
    print(f"{hashlib.sha256(handle.read()).hexdigest()}  {sys.argv[1]}")
PY
  else
    fail "no SHA-256 tool found (sha256sum, shasum, or python3)"
  fi
}

script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
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
# mktemp -d with trailing XXXXXX is portable to BSD/macOS mktemp, unlike a
# template with XXXXXX in the middle followed by ".apk".
tmp_dir=$(mktemp -d "$output_dir/.roots24-${version_name}.XXXXXX")
aligned_apk="$tmp_dir/aligned.apk"
signed_apk="$tmp_dir/signed.apk"
trap 'rm -rf "$tmp_dir"' EXIT

# Official manual signing order: zipalign first, then apksigner sign. Anything
# that rewrites ZIP entries after signing invalidates the signature, so
# aligning after signing is not a repair path -- only a check.
# See https://developer.android.com/build/building-cmdline#sign_cmdline
note "aligning $version_name before signing"
"$zipalign" -P 16 -f 4 "$unsigned_apk" "$aligned_apk" || fail "zipalign failed for $unsigned_apk"

note "signing $version_name with alias $key_alias"
"$apksigner" sign \
  --ks "$keystore" \
  --ks-type PKCS12 \
  --ks-key-alias "$key_alias" \
  --ks-pass "file:$password_file" \
  --out "$signed_apk" \
  "$aligned_apk"

verification=$("$apksigner" verify --verbose --print-certs "$signed_apk") || fail "APK signature verification failed"
printf '%s\n' "$verification"
actual_cert_sha256=$(printf '%s\n' "$verification" | awk -F': ' '/Signer #1 certificate SHA-256 digest:/ { gsub(/:/, "", $2); print tolower($2); exit }')
# Bash 3.2 (stock macOS /bin/bash) has no ${var,,}; tr is portable.
expected_cert_sha256=$(printf '%s\n' "$expected_cert_sha256" | tr -d ':' | tr '[:upper:]' '[:lower:]')
[ -n "$actual_cert_sha256" ] || fail "could not read the APK signing certificate SHA-256"
[ "$actual_cert_sha256" = "$expected_cert_sha256" ] || fail "APK signer is $actual_cert_sha256; expected $expected_cert_sha256"

"$zipalign" -c -P 16 4 "$signed_apk" >/dev/null || fail "APK is not zipaligned"
mv -f -- "$signed_apk" "$output_apk"
# apksigner writes a v4 signature sidecar next to --out; carry it with the
# final artifact instead of leaving a dotfile orphan in the output dir.
if [ -f "$signed_apk.idsig" ]; then
  mv -f -- "$signed_apk.idsig" "$output_apk.idsig"
else
  # Avoid a stale v4 sidecar from a previous run masquerading as this build's.
  rm -f "$output_apk.idsig"
fi
rm -rf "$tmp_dir"
trap - EXIT

note "created $output_apk"
sha256_file "$output_apk"
