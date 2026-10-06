# Root My Galaxy S24

Personal build of [Root My Galaxy](https://github.com/BuSung-dev/Root-My-Galaxy)
targeting the **Galaxy S24 China, `SM-S9210`** (codename `e1q`) on firmware
`S9210ZHS6DZG1`, kernel `6.1.145-android14-11-33419968-abS9210ZHS6DZG1`.

The payload profile this build needs lives in
[Root-My-Galaxy-Payloads#320](https://github.com/BuSung-dev/Root-My-Galaxy-Payloads/pull/320),
which is still open. Neither the manifest entry nor the artifacts are on that
repository's `main` branch, so the app cannot discover or download them. This
build therefore ships the two binaries inside the APK and verifies them against
a manifest that ships alongside them.

Every other device still goes through the upstream online support feed
unchanged.

## What is bundled

| File | Size | SHA-256 |
| --- | ---: | --- |
| `app/src/main/assets/payloads/e1q-S9210ZHS6DZG1/cve-2026-43499-app.so` | 104128 | `1da7c8cd…15b9df` |
| `app/src/main/assets/payloads/e1q-S9210ZHS6DZG1/ksud-e1q-S9210ZHS6DZG1-kdp` | 4995304 | `88748945…dc15049` (EXPERIMENTAL v3.3.0, see below) |
| `app/src/main/jniLibs/arm64-v8a/libcve43499root.so` | 26040 | `b6395159…94b5bb1f` |

> EXPERIMENTAL: this is the e1q v3.3.0 Phase-D pair from Payloads commit
> `8236c843` (KernelSU `932014a`, `KSU_VERSION 32601`), built for exact
> vermagic `6.1.145-android14-11-33419968-abS9210ZHS6DZG1` with
> `CONFIG_KSU_SAMSUNG_NO_PATCH_TEXT=y`. Its `.ko` passed the recorded static
> target-symbol audit with no `stop_machine` import. Payloads later reverted
> the pair before this hardware run. Two end-to-end Shizuku-mode runs with
> app `0.3.2-s24.1` succeeded on SM-S9210 / `S9210ZHS6DZG1` on 2026-10-03
> (attempt 1/24) and 2026-10-07 (attempt 11/24), both with the pre-fix bytes;
> see [`docs/SM-S9210-v3.3.0-validation-20261003.md`](docs/SM-S9210-v3.3.0-validation-20261003.md)
> for the first run record.
> This initial result does not complete broader acceptance: Manager UI status,
> resetprop checks, repeatability, and reboot persistence remain unverified.
> Pair v3.3.0 with Manager `32601`. The helper is unchanged; the exploit `.so`
> is the PI-guard rebuild (`1da7c8cd…15b9df`, fork main@e90759c, NDK r29
> API 35: `make TARGET=e1q-S9210ZHS6DZG1 ANDROID_NDK_HOME=<ndk-r29> release`)
> with no hardware runs yet.

The exploit payload file is rebuilt from our fork main@e90759c
(`samson910022/Root-My-Galaxy-Payloads` PRs #7 code and #8 artifact: PR #320
commit `a311cb88c28204c3902af9450ae4077199e4d71d` plus the rt_mutex PI-guard
fix), with NDK r29 API 35; the v3.3.0 loader is the
separately pinned historical Phase-D artifact from commit `8236c843`. The helper
is not a copied artifact: it is compiled from the exploit commit's
`src/su_daemon.c` and then patched, as described below. Full provenance,
including the exact build command and the behaviour the helper rebuild changes,
is recorded in
[`libcve43499root.provenance.json`](app/src/main/jniLibs/arm64-v8a/libcve43499root.provenance.json)
and in `app/src/main/assets/builtin-targets.json`.

The standalone `.ko` module is deliberately absent: `ksud late-load` loads its
embedded module, so the `.ko` exists upstream for auditing only.

## How resolution works

1. `app/src/main/assets/builtin-targets.json` is read from the APK. This costs
   no network, so the target device resolves with the radio off.
2. A bundled profile matches only if the model, the numeric kernel version, and
   the **exact** `uname -r` string all match. This is a kernel-release gate, not
   an AP/PDA-build or fingerprint check: a firmware change that left the kernel
   release string identical would not be rejected. The release string embeds the
   firmware id (`…-abS9210ZHS6DZG1`), which is what makes it useful here. The
   offsets and the P0 fingerprint
   table come from one firmware image, and `SM-S9210` and `SM-S9280` both report
   `6.1.145` while differing in exactly those values, so the numeric version
   alone is not enough. A firmware update locks the bundled payload out rather
   than letting it run another build's offsets.
3. If nothing bundled matches, the upstream feed is consulted exactly as before
   — except for `SM-S9210`, which is reserved for the bundled manifest. If that
   manifest is missing or malformed, the app refuses rather than falling back to
   an unverified online payload for the same model.
4. Bundled bytes are published to `filesDir` only when both the length and the
   SHA-256 match the manifest, through an atomic replace, falling back to a
   non-atomic replace plus a re-check where the filesystem cannot rename
   atomically. Length alone is not enough: ten of the twenty profiles in the
   current payloads `main` feed are all exactly 104128 bytes.

The online path verifies length only, as it always has, and matches on model
plus numeric kernel version with no exact-release gate, because the upstream
manifest publishes neither a digest nor a release field. Only the bundled path
is digest-checked and release-gated.

## Build

Requirements:

- JDK 21
- Android SDK Platform 37.0, Build Tools 36.0.0
- Android NDK 28.2.13676358 (`ndk;28.2.13676358`)
- CMake 3.22.1

```sh
./gradlew :app:assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

### Local signed APK

This local helper builds the release variant, signs it with the upload
keystore, verifies the signing certificate and alignment, and writes a
versioned APK under `app/build/outputs/apk/release/`. It does not change the
GitHub release workflow, which currently builds and uploads the debug APK.

```sh
./tools/release-apk.sh
```

By default, it reads the PKCS12 keystore and its password file from
`$HOME/.android/keystores/`. Keep both outside the repository. To use a
different local keystore, set `RELEASE_KEYSTORE`, `RELEASE_KEY_ALIAS`, and
`RELEASE_KEYSTORE_PASSWORD_FILE`, and set `RELEASE_CERT_SHA256` to that
keystore's expected signer certificate digest. `ANDROID_SDK_ROOT` (or
`ANDROID_HOME`) must point to the Android SDK; `ANDROID_BUILD_TOOLS_VERSION`
defaults to `36.0.0`. `RELEASE_OUTPUT_DIR` overrides the output directory
(default `app/build/outputs/apk/release/`; relative paths resolve under the
repo root).

The helper follows the official manual signing order (`zipalign -P 16 -f`
before `apksigner sign`, then `apksigner verify` plus `zipalign -c -P 16`).
It requires build-tools 35+ for `-P 16`,
and carries the v4 `.idsig` sidecar next to the final APK when apksigner
emits one (otherwise it removes a stale one). It runs on Linux
and stock macOS (`/bin/bash` 3.2 compatible; `sha256sum` falls back to
`shasum -a 256 --` and then `python3`).

Output example: `app/build/outputs/apk/release/roots24-0.3.2-s24.1.apk`.

Unit tests:

```sh
./gradlew :app:testDebugUnitTest
```

## Verifying and refreshing the vendored binaries

Neither binary is compiled by Gradle, so both are checked explicitly. CI runs
these two commands on every push.

```sh
./tools/build-helper.sh --check    # helper, against its recorded provenance
./tools/sync-payloads.sh --check   # payload, against the bundled manifest
```

`build-helper.sh --check` validates the worktree copy *and* the staged blob, so
a tampered binary cannot be blessed and then committed. A full
`./tools/build-helper.sh` refetches the source by pinned commit SHA, applies
`tools/helper-marker-removal.patch`, rebuilds it with the payloads Makefile's
rule, and verifies the result.

`./tools/sync-payloads.sh` fetches the exploit payload from PR #320 (or from
`main` after setting `PR_MERGED=true`) and fetches the experimental v3.3.0
`ksud` from its separately pinned historical Phase-D commit. It verifies both
against the manifest before installing them; the v3.3.0 binary is not currently
published on Payloads `main` because broader acceptance checks remain pending.

## Known limitations

- Root is RAM-only. A reboot removes it.
- The default transport is the standalone one, which was **not** the transport
  the porting record validated: those runs mirrored the Shizuku-mode
  `LD_PRELOAD` invocation. Prefer Shizuku mode. The standalone path also hands
  the kernel's `call_usermodehelper` a binary from the app's private `/data/app`
  directory, the class of location the porting record warns gets a DEFEX kill.
- The app no longer carries a KASLR slide across runs. Upstream scraped `slide=`
  out of the exploit log and re-injected it as `SLIDE_P0_OFFSET`, which makes
  the payload take its `forced` slide source. That flips
  `data_addr_canonical` to false, so every kernel address it writes is computed
  by a different formula than the tracefs path this device was validated on, and
  the P0 oracle's verification is skipped. The payload already retains a
  discovered offset within a single run, so the app injects nothing and the
  cross-run cache is gone. Whether that in-run branch was taken in the
  validation runs is not established; see the note on `requiresFreshP0Session`
  below.
- The stall and total timeouts are derived from the payload's own budget: 150s
  against its 120s longest silent gap, and 3200s against its 3115s worst case.
  If either fires the helper is stopped, but the payload's children are
  detached and may still be writing, so the message says so in every language.
  Do not retry immediately.
- Force-stopping the app after the exploit can panic the device, by releasing
  retained kernel pages. Leave the app in the foreground until it reports
  success.
- The exact-firmware gate means a firmware update will stop this payload from
  working. That is intentional; re-validate against the new firmware image
  before relaxing it.
- The upstream online path is not digest-verified. See above.
- Only `arm64-v8a` is built.
- Self-update is disabled. This repository is private, so its release feed is
  not readable without an `Authorization` header; the in-app updater would only
  ever report failure. `SELF_UPDATE_ENABLED` in `app/build.gradle.kts` turns the
  feature back on once a token-free distribution endpoint exists.
- Translations are partial. The S24 branding and the bundled-payload messages
  are translated for `zh-TW` and most for `zh-CN`; every locale keeps its existing
  upstream translations and falls back to English for keys it lacks, which is
  between 13 and 26 of the 161 keys depending on locale. `fr` is registered as
  an app locale but has no entry in the in-app language picker, so it is
  reachable only through Android's per-app language settings.
- The on-device exploit harness lives in `app/src/androidTest/.../ExploitUidTest.kt`
  and is run with `./gradlew connectedAndroidTest`. It still accepts a P0 offset
  as an explicit argument
  (`-Pandroid.testInstrumentationRunnerArguments.p0Offset=0x...`), which is
  how the payload's unverified `forced` slide path can be exercised on purpose.
  The app itself never sets it; see the note above on why it must not.
- `requiresFreshP0Session` is carried over from the upstream manifest but is
  neither implemented by the app nor enforced by the bundled payload. The e1q
  target header does not define `APP_REQUIRE_FRESH_P0_SESSION`, so the payload
  takes the branch that retains and reuses a discovered P0 offset within one
  run. The porting record's transcript contains no diagnostic showing that branch
  actually ran, so this is what is compiled, not what is proven.
- The vendored helper is built from a locally patched `su_daemon.c`; the patch
  is in `tools/helper-marker-removal.patch` and the reasoning is in
  `THIRD_PARTY_NOTICES.md`.

## Licensing and attribution

This repository is a derivative work and is private. See
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for the components it contains
and their terms. Root My Galaxy and Root-My-Galaxy-Payloads are Apache-2.0.

Use only on devices you own or are explicitly authorized to test.
