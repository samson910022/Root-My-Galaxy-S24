#!/usr/bin/env bash
# Refreshes the bundled SM-S9210 payload from the payloads repository.
#
# The exploit profile comes from the samson910022/Root-My-Galaxy-Payloads fork
# at a pinned commit: upstream PR #320 code plus the rt_mutex PI-guard fix.
# The KernelSU v3.3.0 e1q binary comes from the separately pinned Phase-D commit
# in BuSung-dev/Root-My-Galaxy-Payloads because Payloads later reverted it from
# main pending broader hardware acceptance. Full-chain app/Shizuku runs
# succeeded on 2026-10-03, 2026-10-07, and 2026-10-19 (the last with the
# PI-guard bytes); see the validation records.
#
# Both artifacts are fetched by immutable commit rather than by branch or ref,
# and both are verified against the digests in the bundled manifest, so a
# silent upstream change cannot reach the APK.
#
# Usage:  tools/sync-payloads.sh [--check]
#           (default)  fetch, verify, and install into the assets tree
#           --check    verify the committed assets without fetching anything
set -euo pipefail

SCRIPT=tools/sync-payloads.sh
PROVENANCE=app/src/main/assets/builtin-targets.json
ASSET_DIR=app/src/main/assets/payloads/e1q-S9210ZHS6DZG1
PROFILE=e1q-S9210ZHS6DZG1
EXPLOIT_RAW_BASE=https://raw.githubusercontent.com/samson910022/Root-My-Galaxy-Payloads
EXPLOIT_SOURCE_COMMIT=e90759c5e4167f271ef46cc4a57835cf42a2ffc6
KSUD_RAW_BASE=https://raw.githubusercontent.com/BuSung-dev/Root-My-Galaxy-Payloads
# The e1q v3.3.0 pair is recorded in Payloads Phase D and was reverted from
# main before its first successful app/Shizuku run (2026-10-03). Keep the
# published feed on v3.2.5 while broader acceptance remains pending. Pin that
# reviewed commit independently from the exploit profile.
KSUD_SOURCE_COMMIT=8236c84301282b5415412d867ab71d4a739e0809

EXPLOIT_NAME=cve-2026-43499-app.so
KSUD_NAME=ksud-e1q-S9210ZHS6DZG1-kdp
EXPLOIT_UPSTREAM="artifacts/$PROFILE/$EXPLOIT_NAME"
KSUD_UPSTREAM="kernelsu/$KSUD_NAME"
# e1q exploit with the PI-guard fix, built from the fork at
# samson910022/Root-My-Galaxy-Payloads commit e90759c (upstream PR #320 code
# plus the PI-guard fix). Pinned by commit because the fork's main has moved on.
EXPLOIT_SHA256=1da7c8cd12d1125453fcd9e14a458296a62e37530257d152c9d8f9a26415b9df
# EXPERIMENTAL v3.3.0 e1q Phase-D pair from Payloads commit 8236c84.
# It uses CONFIG_KSU_SAMSUNG_NO_PATCH_TEXT=y, passed static audit, and has three
# successful SM-S9210 app/Shizuku runs (2026-10-03 and 2026-10-07 pre-fix,
# 2026-10-19 with the PI-guard exploit); broader acceptance remains pending.
KSUD_SHA256=8874894560e46dd3ab711386f07c3d89635bdaae0e02a806878555bdbdc15049
EXPLOIT_SIZE=104128
KSUD_SIZE=4995304

fail() { printf 'sync-payloads: %s\n' "$*" >&2; exit 1; }
note() { printf 'sync-payloads: %s\n' "$*"; }

check_only=false
case "${1:-}" in
  '') ;;
  --check) check_only=true ;;
  *) fail "unknown argument '$1' (expected no arguments or --check)" ;;
esac

root=$(git rev-parse --show-toplevel) || fail "not inside a Git repository"
cd "$root"
[ -f "$PROVENANCE" ] || fail "missing $PROVENANCE"

# The manifest is the trust anchor, so its recorded digests are the ones the
# assets are checked against rather than a second set of constants here.
read_manifest() {
  python3 - "$PROVENANCE" "$1" <<'PY'
import json, sys

path, key = sys.argv[1], sys.argv[2]
with open(path, encoding="utf-8") as handle:
    doc = json.load(handle)
match = [p for p in doc["payloads"] if p["payloadId"] == "e1q-S9210ZHS6DZG1"]
if len(match) != 1:
    sys.exit(f"{path}: expected exactly one e1q-S9210ZHS6DZG1 entry, found {len(match)}")
entry = match[0][key]
print(entry["sha256"], entry["size"], entry["assetPath"])
PY
}

EXPLOIT_SHA256_MANIFEST=$(read_manifest exploit | cut -d' ' -f1)
EXPLOIT_SIZE_MANIFEST=$(read_manifest exploit | cut -d' ' -f2)
EXPLOIT_ASSET_PATH=$(read_manifest exploit | cut -d' ' -f3)
KSUD_SHA256_MANIFEST=$(read_manifest kernelsu | cut -d' ' -f1)
KSUD_SIZE_MANIFEST=$(read_manifest kernelsu | cut -d' ' -f2)
KSUD_ASSET_PATH=$(read_manifest kernelsu | cut -d' ' -f3)

# The manifest and this script must agree, or the check below would silently
# compare the assets against the wrong expectations.
[ "$EXPLOIT_SHA256_MANIFEST" = "$EXPLOIT_SHA256" ] || fail "manifest exploit digest differs from the script pin"
[ "$KSUD_SHA256_MANIFEST" = "$KSUD_SHA256" ] || fail "manifest ksud digest differs from the script pin"
[ "$EXPLOIT_SIZE_MANIFEST" = "$EXPLOIT_SIZE" ] || fail "manifest exploit size differs from the script pin"
[ "$KSUD_SIZE_MANIFEST" = "$KSUD_SIZE" ] || fail "manifest ksud size differs from the script pin"
[ "$EXPLOIT_ASSET_PATH" = "payloads/$PROFILE/$EXPLOIT_NAME" ] || fail "unexpected exploit assetPath"
[ "$KSUD_ASSET_PATH" = "payloads/$PROFILE/$KSUD_NAME" ] || fail "unexpected ksud assetPath"

verify() {
  local file=$1 size=$2 digest=$3 label=$4
  [ -f "$file" ] || fail "$label is missing: $file"
  [ "$(stat -c %s "$file")" = "$size" ] || fail "$label is $(stat -c %s "$file") bytes, expected $size"
  local got
  got=$(sha256sum "$file" | cut -d' ' -f1)
  [ "$got" = "$digest" ] || fail "$label digest $got, expected $digest"
}

verify "$ASSET_DIR/$EXPLOIT_NAME" "$EXPLOIT_SIZE" "$EXPLOIT_SHA256" exploit
verify "$ASSET_DIR/$KSUD_NAME" "$KSUD_SIZE" "$KSUD_SHA256" ksud

# Also prove the copy a commit would record is the same, so a tampered asset
# cannot be blessed here and then committed from the index.
verify_index() {
  local path=$1 digest=$2 label=$3 staged
  if staged=$(git cat-file blob ":$path" 2>/dev/null | sha256sum | cut -d' ' -f1); then
    [ "$staged" = "$digest" ] || fail "the staged $label is $staged; the index does not hold the pinned bytes"
  else
    fail "cannot read the staged $label from the index"
  fi
  git diff --quiet -- "$PROVENANCE" "$ASSET_DIR" "$SCRIPT" ||
    fail "staged content differs from the worktree; commit or unstage first"
}

if $check_only; then
  verify_index "$ASSET_DIR/$EXPLOIT_NAME" "$EXPLOIT_SHA256" exploit
  verify_index "$ASSET_DIR/$KSUD_NAME" "$KSUD_SHA256" ksud
  note "assets match the manifest, in the worktree and in the index"
  exit 0
fi

for tool in git curl sha256sum python3; do
  command -v "$tool" >/dev/null 2>&1 || fail "required tool not found: $tool"
done

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

fetch() {
  local raw_base=$1 source_commit=$2 upstream=$3 destination=$4 size=$5 digest=$6 label=$7
  curl --fail --silent --show-error --location \
    --proto '=https' --proto-redir '=https' \
    --retry 3 --connect-timeout 15 --max-time 300 \
    --output "$destination" \
    "$raw_base/$source_commit/$upstream" ||
    fail "$label download failed: $upstream"
  verify "$destination" "$size" "$digest" "$label (downloaded)"
}

# Fetch and verify both before the committed tree is touched. The two installs
# remain separate operations, so a failure of the second could leave one
# updated; re-running repairs it, and the manifest digests do not change in
# between, so a partial state is detected rather than shipped.
fetch "$EXPLOIT_RAW_BASE" "$EXPLOIT_SOURCE_COMMIT" "$EXPLOIT_UPSTREAM" "$work/$EXPLOIT_NAME" "$EXPLOIT_SIZE" "$EXPLOIT_SHA256" exploit
fetch "$KSUD_RAW_BASE" "$KSUD_SOURCE_COMMIT" "$KSUD_UPSTREAM" "$work/$KSUD_NAME" "$KSUD_SIZE" "$KSUD_SHA256" ksud

install -m 0644 "$work/$EXPLOIT_NAME" "$ASSET_DIR/$EXPLOIT_NAME"
install -m 0644 "$work/$KSUD_NAME" "$ASSET_DIR/$KSUD_NAME"

verify "$ASSET_DIR/$EXPLOIT_NAME" "$EXPLOIT_SIZE" "$EXPLOIT_SHA256" exploit
verify "$ASSET_DIR/$KSUD_NAME" "$KSUD_SIZE" "$KSUD_SHA256" ksud
note "synced $PROFILE exploit from samson910022/Root-My-Galaxy-Payloads@$EXPLOIT_SOURCE_COMMIT and ksud from BuSung-dev/Root-My-Galaxy-Payloads@$KSUD_SOURCE_COMMIT"
