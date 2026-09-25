#!/usr/bin/env bash
# Rebuilds app/src/main/jniLibs/arm64-v8a/libcve43499root.so from the payloads
# repository and verifies it against the recorded provenance.
#
# The helper source lives in an unmerged pull request, so the commit is pinned
# by SHA rather than by branch name: refs/pull/320/head can move, the commit
# cannot. Every hash is checked, and any drift stops the script.
#
# Usage:  tools/build-helper.sh [--check]
#           --check  verify the committed binary without rebuilding anything
#
# Requires: git, python3, and ANDROID_HOME (except for --check).
set -euo pipefail

PROVENANCE=app/src/main/jniLibs/arm64-v8a/libcve43499root.provenance.json
TARGET=app/src/main/jniLibs/arm64-v8a/libcve43499root.so
SCRIPT=tools/build-helper.sh
PAYLOADS_REPO=https://github.com/BuSung-dev/Root-My-Galaxy-Payloads.git
SOURCE_PATH=src/su_daemon.c
SOURCE_COMMIT=a311cb88c28204c3902af9450ae4077199e4d71d
SOURCE_BLOB=6565a0a85d71c0556950cd6d488040b99b4da642
SOURCE_SHA256=3509de28cc194b275ab6f8c254a838b1ebc55e1fa094647ae83ef56639442996
PATCHED_SOURCE_SHA256=4165b1da52c0529cc5a80eed15b5d5cebade39df44bbd83c8ac2c7c3a603ffe0
LOCAL_PATCH=tools/helper-marker-removal.patch
BINARY_SHA256=b63951595d631fc7f259ce2ae596da121bd62765e2c016d460fb61b694b5bb1f
BINARY_SIZE=26040
API=35
NDK_VERSION=28.2.13676358

# Strings the app's install flow depends on being compiled into the helper.
CONTRACTS=(
  '/data/local/tmp/ksud-s25u-kdp'
  '/system/bin/logcat'
  'me.weishu.kernelsu'
  '--run-payload'
  '--late-load'
  '--ephemeral'
  'retrying plain late-load'
)

fail() { printf 'build-helper: %s\n' "$*" >&2; exit 1; }
note() { printf 'build-helper: %s\n' "$*"; }

case "${1:-}" in
  '') ;;
  --check) check_only=true ;;
  *) fail "unknown argument '$1' (expected no arguments or --check)" ;;
esac

root=$(git rev-parse --show-toplevel) || fail "not inside a Git repository"
cd "$root"
[ -f "$PROVENANCE" ] || fail "missing $PROVENANCE"
[ -f "$TARGET" ] || fail "missing $TARGET"
check_only=${check_only:-false}

# --- provenance must agree with this script, field by field ------------------
# Grepping for strings would accept an edited size or a malformed file, so the
# JSON is parsed and every pinned value compared.
verify_provenance() {
  python3 - "$PROVENANCE" <<'PY' || exit 1
import json, sys

path = sys.argv[1]
try:
    with open(path, encoding="utf-8") as handle:
        doc = json.load(handle)
except ValueError as error:
    sys.exit(f"{path} is not valid JSON: {error}")

expected = {
    ("source", "commit"): "a311cb88c28204c3902af9450ae4077199e4d71d",
    ("source", "path"): "src/su_daemon.c",
    ("source", "gitBlob"): "6565a0a85d71c0556950cd6d488040b99b4da642",
    ("source", "sha256"): "3509de28cc194b275ab6f8c254a838b1ebc55e1fa094647ae83ef56639442996",
    ("build", "ndkPackage"): "ndk;28.2.13676358",
    ("build", "apiLevel"): 35,
    ("build", "unpackedSize"): 26040,
    ("build", "sha256"): "b63951595d631fc7f259ce2ae596da121bd62765e2c016d460fb61b694b5bb1f",
    ("build", "patchedSourceSha256"): "4165b1da52c0529cc5a80eed15b5d5cebade39df44bbd83c8ac2c7c3a603ffe0",
    ("build", "localPatch"): "tools/helper-marker-removal.patch",
    ("build", "packedSizeAfterAgpStrip"): 21480,
}

bad = []
for (section, field), want in expected.items():
    got = doc.get(section, {}).get(field)
    if got != want:
        bad.append(f"{section}.{field} is {got!r}, expected {want!r}")

for field in ("command", "toolchainPath", "packedSizeAfterAgpStrip"):
    if not doc.get("build", {}).get(field):
        bad.append(f"build.{field} is missing")
for field in ("binary", "whatItIs", "behaviourChanges", "unchangedContracts", "replaces"):
    if not doc.get(field):
        bad.append(f"{field} is missing")
if doc.get("binary") != "libcve43499root.so":
    bad.append(f"binary is {doc.get('binary')!r}, expected 'libcve43499root.so'")

if bad:
    for line in bad:
        print(f"build-helper: {path}: {line}", file=sys.stderr)
    sys.exit(1)
PY
}
verify_provenance

# --- the worktree file and the staged blob must both be the pinned binary ----
# Checking only the worktree would let a staged-but-different helper through,
# because that is the copy a commit would actually record.
# Verifies the worktree copy. The index is only consulted by --check: a rebuild
# legitimately leaves the index holding the previous binary until it is staged.
verify_worktree() {
  local size digest
  size=$(stat -c %s "$TARGET")
  digest=$(sha256sum "$TARGET" | cut -d' ' -f1)
  [ "$size" = "$BINARY_SIZE" ] || fail "$TARGET is $size bytes, expected $BINARY_SIZE"
  [ "$digest" = "$BINARY_SHA256" ] || fail "$TARGET digest $digest, expected $BINARY_SHA256"
  note "verified $TARGET ($size bytes, $digest)"
}

# --check additionally proves the copy a commit would record is the same, so a
# tampered binary cannot be blessed here and then committed from the index.
verify_index() {
  local staged
  if staged=$(git cat-file blob ":$TARGET" 2>/dev/null | sha256sum | cut -d' ' -f1); then
    [ "$staged" = "$BINARY_SHA256" ] || \
      fail "the staged $TARGET is $staged; the index does not hold the pinned binary"
  else
    fail "cannot read the staged $TARGET from the index"
  fi
  git diff --quiet -- "$PROVENANCE" "$TARGET" "$SCRIPT" "$LOCAL_PATCH" ||
    fail "staged content differs from the worktree for these files; commit or unstage first"
  note "verified the staged copy as well"
}

verify_contracts() {
  local file=$1 expected
  for expected in "${CONTRACTS[@]}"; do
    strings -a "$file" | grep -qF -- "$expected" || fail "$file is missing the contract string $expected"
  done
}

if $check_only; then
  verify_worktree
  verify_index
  verify_contracts "$TARGET"
  exit 0
fi

: "${ANDROID_HOME:?set ANDROID_HOME to your Android SDK}"
CC="$ANDROID_HOME/ndk/$NDK_VERSION/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android${API}-clang"
[ -x "$CC" ] || fail "missing compiler $CC (sdkmanager \"ndk;$NDK_VERSION\")"

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

# Read the source from the pinned commit, never from a checkout: the payloads
# repository carries unrelated local modifications.
git init --quiet "$work/repo"
git -C "$work/repo" remote add origin "$PAYLOADS_REPO"
git -C "$work/repo" fetch --quiet --depth 1 origin "$SOURCE_COMMIT"
blob=$(git -C "$work/repo" rev-parse "FETCH_HEAD:$SOURCE_PATH")
[ "$blob" = "$SOURCE_BLOB" ] || fail "$SOURCE_PATH blob is $blob, expected $SOURCE_BLOB"
git -C "$work/repo" show "FETCH_HEAD:$SOURCE_PATH" > "$work/su_daemon.c"
[ "$(sha256sum "$work/su_daemon.c" | cut -d' ' -f1)" = "$SOURCE_SHA256" ] || fail "source digest mismatch"

# A local patch removes the bootstrap marker feature. Upstream's version reads a
# path from a shell-writable file and then creates that path as root, which is a
# root-write primitive this app never uses. The patch is kept in the repository
# so the divergence from upstream stays reviewable.
[ -f "$LOCAL_PATCH" ] || fail "missing $LOCAL_PATCH"
patch --batch --forward --silent "$work/su_daemon.c" "$LOCAL_PATCH" ||
  fail "$LOCAL_PATCH does not apply to the pinned source"
[ "$(sha256sum "$work/su_daemon.c" | cut -d' ' -f1)" = "$PATCHED_SOURCE_SHA256" ] ||
  fail "patched source digest mismatch"
grep -q 'bootstrap_marker_path\|BOOTSTRAP_MARKER_CONFIG' "$work/su_daemon.c" &&
  fail "the marker primitive is still present after patching"

# The helper must stay self-contained: no local header means no target context
# leaks into this build.
if grep -qE '^[[:space:]]*#[[:space:]]*include[[:space:]]*"' "$work/su_daemon.c"; then
  fail "su_daemon.c now includes a local header; this build assumes it does not"
fi

"$CC" -fPIE -pie -O2 -g0 -Wall -Wextra "$work/su_daemon.c" -ldl -o "$work/helper"
[ "$(stat -c %s "$work/helper")" = "$BINARY_SIZE" ] || fail "rebuilt size differs from $BINARY_SIZE"
[ "$(sha256sum "$work/helper" | cut -d' ' -f1)" = "$BINARY_SHA256" ] || fail "rebuilt digest differs from $BINARY_SHA256"

# Everything is checked before the committed file is touched, so a failed
# rebuild never leaves a half-good binary in the tree.
verify_contracts "$work/helper"
file "$work/helper" | grep -q 'pie executable' || fail "rebuilt helper is not a PIE executable"
readelf -hW "$work/helper" | grep -q 'AArch64' || fail "rebuilt helper is not AArch64"
readelf -lW "$work/helper" | awk '/LOAD/{print $NF}' | sort -u | grep -qx '0x4000' ||
  fail "rebuilt helper is not 16 KB page-size aligned"

# 0644 on purpose: PackageManager applies 0755 when it extracts a native-library
# entry, and recording the executable bit here would only make the worktree look
# dirty after every rebuild.
install -m 0644 "$work/helper" "$TARGET"
verify_worktree
verify_contracts "$TARGET"
note "rebuilt from $SOURCE_COMMIT with $LOCAL_PATCH applied, and verified"
