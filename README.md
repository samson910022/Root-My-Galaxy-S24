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
| `app/src/main/assets/payloads/e1q-S9210ZHS6DZG1/cve-2026-43499-app.so` | 104128 | `2dc137cc…e9b4059` |
| `app/src/main/assets/payloads/e1q-S9210ZHS6DZG1/ksud-e1q-S9210ZHS6DZG1-kdp` | 4895088 | `67b49cad…ee542` |
| `app/src/main/jniLibs/arm64-v8a/libcve43499root.so` | 27088 | `f6da8c44…52661a0` |

All three come from `BuSung-dev/Root-My-Galaxy-Payloads` at commit
`a311cb88c28204c3902af9450ae4077199e4d71d`. Full provenance, including the
exact build command and the behaviour the helper rebuild changes, is recorded
in
[`libcve43499root.provenance.json`](app/src/main/jniLibs/arm64-v8a/libcve43499root.provenance.json)
and in `app/src/main/assets/builtin-targets.json`.

The standalone `.ko` module is deliberately absent: `ksud late-load` loads its
embedded module, so the `.ko` exists upstream for auditing only.

## How resolution works

1. `app/src/main/assets/builtin-targets.json` is read from the APK. This costs
   no network, so the target device resolves with the radio off.
2. A bundled profile matches only if the model, the numeric kernel version, and
   the **exact** `uname -r` string all match. The offsets and the P0 fingerprint
   table come from one firmware image, and `SM-S9210` and `SM-S9280` both report
   `6.1.145` while differing in exactly those values, so the numeric version
   alone is not enough. A firmware update locks the bundled payload out rather
   than letting it run another build's offsets.
3. If nothing bundled matches, the upstream feed is consulted exactly as before
   — except for `SM-S9210`, which is reserved for the bundled manifest. If that
   manifest is missing or malformed, the app refuses rather than falling back to
   an unverified online payload for the same model.
4. Bundled bytes are published to `filesDir` only when both the length and the
   SHA-256 match the manifest, through an atomic replace. Length alone is not
   enough: ten of the twenty profiles in the upstream feed are all exactly
   104128 bytes.

The online path verifies length only, as it always has. It cannot check a
digest because the upstream manifest does not publish one.

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

`./tools/sync-payloads.sh` refetches the two payload files, verifies their
digests against the manifest, and installs them. It reads `refs/pull/320/head`
while the pull request is open; once that merges, set `PR_MERGED=true` at the
top of the script and it will read the payloads `main` branch instead, still
refusing any content that does not match the recorded digests.

## Known limitations

- Root is RAM-only. A reboot removes it.
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
  are translated for `zh-TW` and `zh-CN`; every locale keeps its existing
  upstream translations and falls back to English for keys it lacks, which is
  between 13 and 26 of the 161 keys depending on locale. `fr` is registered as
  an app locale but has no entry in the in-app language picker, so it is
  reachable only through Android's per-app language settings.
- `requiresFreshP0Session` is carried over from the upstream manifest but is
  neither implemented by the app nor enforced by the bundled payload. The e1q
  target header does not define `APP_REQUIRE_FRESH_P0_SESSION`, so the payload
  retains and reuses a discovered P0 offset. That is the behaviour the
  porting record's validation ran with, so it is left alone rather than changed
  on the strength of a field nothing reads.
- The vendored helper is built from a locally patched `su_daemon.c`; the patch
  is in `tools/helper-marker-removal.patch` and the reasoning is in
  `THIRD_PARTY_NOTICES.md`.

## Licensing and attribution

This repository is a derivative work and is private. See
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for the components it contains
and their terms. Root My Galaxy and Root-My-Galaxy-Payloads are Apache-2.0.

Use only on devices you own or are explicitly authorized to test.
