#!/usr/bin/env bash
# Refreshes the bundled SM-S9210 payload from the payloads repository.
#
# The profile lives in an unmerged pull request, so this script fetches the
# source by pinned commit rather than by a branch name. Once the pull request
# merges, set PR_MERGED=true and the script switches to the payloads main
# branch while still requiring the same content digests, so a silent upstream
# change cannot reach the APK.
#
# Usage:  tools/sync-payloads.sh [--check]
#           (default)  fetch, verify, and install into the assets tree
#           --check    verify the committed assets without fetching anything
set -euo pipefail

SCRIPT=tools/sync-payloads.sh
PROVENANCE=app/src/main/assets/builtin-targets.json
ASSET_DIR=app/src/main/assets/payloads/e1q-S9210ZHS6DZG1
PROFILE=e1q-S9210ZHS6DZG1
REPO_SLUG=BuSung-dev/Root-My-Galaxy-Payloads
RAW_BASE=https://raw.githubusercontent.com/BuSung-dev/Root-My-Galaxy-Payloads
PR_NUMBER=320
# Set to true once the pull request is merged; the payloads main branch then
# carries the profile and is used instead of the pull request ref.
PR_MERGED=false
PR_REF="refs/pull/$PR_NUMBER/head"
PINNED_COMMIT=a311cb88c28204c3902af9450ae4077199e4d71d
MAIN_REF=refs/heads/main

EXPLOIT_NAME=cve-2026-43499-app.so
KSUD_NAME=ksud-e1q-S9210ZHS6DZG1-kdp
EXPLOIT_UPSTREAM="artifacts/$PROFILE/$EXPLOIT_NAME"
KSUD_UPSTREAM="kernelsu/$KSUD_NAME"
EXPLOIT_SHA256=2dc137cc1dca72f896ee16347f3137dd0c521ecdee56e1922fb58e733e9b4059
# EXPERIMENTAL v3.3.0 (2026-10-02): ksud rebuilt locally from KernelSU v3.3.0
# (932014a, KSU_VERSION 32601) + v3.3.0 Samsung patch, exact e1q release
# 6.1.145-android14-11-33419968-abS9210ZHS6DZG1, NDK r29 platform 29.
# The pinned upstream commit below does NOT carry this binary, so a plain
# fetch will fail the digest check until the payloads repo publishes it.
# --check (worktree + index vs manifest) is the supported verification.
KSUD_SHA256=50f1d62e40dd39689c45a6ab6e767a4196d8f044cd727c761b681d854ebb1de7
EXPLOIT_SIZE=104128
KSUD_SIZE=4992888

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

if [ "$PR_MERGED" = true ]; then
  SOURCE_REF=$MAIN_REF
  EXPECTED_COMMIT=""
  note "reading from the payloads main branch"
else
  SOURCE_REF=$PR_REF
  EXPECTED_COMMIT=$PINNED_COMMIT
  note "reading from $PR_REF"
fi

source_commit=$(git ls-remote "https://github.com/$REPO_SLUG.git" "$SOURCE_REF" | cut -f1)
[ -n "$source_commit" ] || fail "could not resolve $SOURCE_REF"
if [ -n "$EXPECTED_COMMIT" ] && [ "$source_commit" != "$EXPECTED_COMMIT" ]; then
  fail "$SOURCE_REF is $source_commit, expected $EXPECTED_COMMIT (the pull request head moved)"
fi

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

fetch() {
  local upstream=$1 destination=$2 size=$3 digest=$4 label=$5
  curl --fail --silent --show-error --location \
    --proto '=https' --proto-redir '=https' \
    --retry 3 --connect-timeout 15 --max-time 300 \
    --output "$destination" \
    "$RAW_BASE/$source_commit/$upstream" ||
    fail "$label download failed: $upstream"
  verify "$destination" "$size" "$digest" "$label (downloaded)"
}

# Fetch and verify both before the committed tree is touched. The two installs
# remain separate operations, so a failure of the second could leave one
# updated; re-running repairs it, and the manifest digests do not change in
# between, so a partial state is detected rather than shipped.
fetch "$EXPLOIT_UPSTREAM" "$work/$EXPLOIT_NAME" "$EXPLOIT_SIZE" "$EXPLOIT_SHA256" exploit
fetch "$KSUD_UPSTREAM" "$work/$KSUD_NAME" "$KSUD_SIZE" "$KSUD_SHA256" ksud

install -m 0644 "$work/$EXPLOIT_NAME" "$ASSET_DIR/$EXPLOIT_NAME"
install -m 0644 "$work/$KSUD_NAME" "$ASSET_DIR/$KSUD_NAME"

verify "$ASSET_DIR/$EXPLOIT_NAME" "$EXPLOIT_SIZE" "$EXPLOIT_SHA256" exploit
verify "$ASSET_DIR/$KSUD_NAME" "$KSUD_SIZE" "$KSUD_SHA256" ksud
note "synced $PROFILE from $source_commit"
