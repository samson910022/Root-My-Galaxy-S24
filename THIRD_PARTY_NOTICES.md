# Third-party notices

This repository is a private derivative work. It contains or derives from the
components below. The Apache-2.0 licence covering the application source is in
[LICENSE](LICENSE); the terms of each component still apply to that component.

## Application source

| Component | Source | Licence |
| --- | --- | --- |
| Root My Galaxy | <https://github.com/BuSung-dev/Root-My-Galaxy> | Apache-2.0 |
| Root-My-Galaxy-Payloads | <https://github.com/BuSung-dev/Root-My-Galaxy-Payloads> | Apache-2.0 |

The application source and the Gradle configuration originate in Root My
Galaxy. The bundled-manifest format does not: upstream publishes schema 3,
which pins artifacts by url, and has no `builtin-targets.json`. The schema 4
format used here, with `assetPath` and a digest instead of a url, is an addition
by this fork. Changes made here: the package and
application id, the CMake and JNI names, the bundled-payload support described
in the README, the removal of the advanced-mode UI, and the helper rebuild.

## Bundled binaries

The exploit payload is rebuilt from our fork main@e90759c5e4167f271ef46cc4a57835cf42a2ffc6
(samson910022/Root-My-Galaxy-Payloads PRs #7 code and #8 artifact: PR #320
commit `a311cb88c28204c3902af9450ae4077199e4d71d` plus the rt_mutex PI-guard
fix), with NDK r29 API 35, bit-identical across rebuilds.
The helper is compiled from that commit's source and then patched, as described
below (from `a311cb88`'s `src/su_daemon.c`, untouched by the PI-guard fix). The bundled KernelSU loader is a separate Phase-D artifact from commit
`8236c84301282b5415412d867ab71d4a739e0809` (see its provenance below).

| Artifact | Upstream path |
| --- | --- |
| `assets/payloads/e1q-S9210ZHS6DZG1/cve-2026-43499-app.so` | `artifacts/e1q-S9210ZHS6DZG1/cve-2026-43499-app.so` |
| `assets/payloads/e1q-S9210ZHS6DZG1/ksud-e1q-S9210ZHS6DZG1-kdp` | `kernelsu/ksud-e1q-S9210ZHS6DZG1-kdp` |
| `jniLibs/arm64-v8a/libcve43499root.so` | built from `src/su_daemon.c` |

### Exploit payload

Root-My-Galaxy-Payloads states that the port is based on exploit source
published at
<https://github.com/NebuSec/CyberMeowfia/tree/main/IonStack/CVE-2026-43499/exploit>.
That upstream is referenced by a mutable `main` branch rather than a pinned
commit, so the exact revision used here is not recorded anywhere in either
repository. The licence of that upstream has not been audited as part of this
work. **If this repository is ever shared beyond its owner, resolve and record
the exploit source revision and its licence first.**

### KernelSU loader

`ksud-e1q-S9210ZHS6DZG1-kdp` is the EXPERIMENTAL e1q Phase-D pair recorded in
`BuSung-dev/Root-My-Galaxy-Payloads` commit
`8236c84301282b5415412d867ab71d4a739e0809`; Payloads main later reverted it in
`7690b8b`. Full-chain app/Shizuku runs succeeded on SM-S9210 on 2026-10-03,
2026-10-07 and 2026-10-19, but broader acceptance remains pending. It is based
on KernelSU `v3.3.0`, commit
`932014ab5b2c9b74a3d11e2ec4d17dd10fc9442e` (`KSU_VERSION 32601`) with the
Samsung KDP/RKP/DEFEX patch and the target-specific
`CONFIG_KSU_SAMSUNG_NO_PATCH_TEXT=y` configuration. The exact-release module
has vermagic `6.1.145-android14-11-33419968-abS9210ZHS6DZG1`, size 406160,
SHA-256 `d64647a118b91833ad0580076d06c946f1b5e4f34a52ae8a13aeeeebeb646687`;
the Phase-C audit recorded 202 undefined imports, 0 missing target symbols,
0 CRC mismatches, empty `__versions`, and no `UND stop_machine`.

The paired `ksud` is 4995304 bytes, SHA-256
`8874894560e46dd3ab711386f07c3d89635bdaae0e02a806878555bdbdc15049`; it
reports `3.3.0 (uapi: 2)` and embeds the `android14-6.1_kernelsu.ko` asset.
Payloads’ Phase-D record documents the DDK
`ghcr.io/ylarod/ddk-min:android14-6.1-20260313`, exact release override, and
NDK r29 Rust build with workspace-only Cargo dependency overrides. The Phase-D
pair is statically audited. Full-chain Shizuku-mode runs with this exact pair
succeeded on SM-S9210 on 2026-10-03, 2026-10-07 and 2026-10-19 (the last
through app v0.3.3-s24.1 with the PI-guard exploit); the captured evidence and
remaining validation scope are recorded in
[`docs/SM-S9210-v3.3.0-validation-20261003.md`](docs/SM-S9210-v3.3.0-validation-20261003.md)
and
[`docs/SM-S9210-v3.3.0-piguard-validation-20261019.md`](docs/SM-S9210-v3.3.0-piguard-validation-20261019.md).
The Manager UI status was captured and the resetprop regression suite passed on
2026-10-19; repeatability and reboot persistence have not been checked.
The earlier device test (2026-09-11) covered the v3.2.5 loader
(`b0bc817b4e966aa6aa830834eaf6ef765d821d40`, 4895088 bytes, SHA-256
`67b49cad…ee542`). Pair v3.3.0 with Manager `32601`; a 3.2.5 Manager reports
version skew.

KernelSU is licensed GPL-3.0, which is a different licence from the
Apache-2.0 covering this repository. The following statement from the Payloads
README is historical and describes its v3.2.5 binaries, not this v3.3.0 loader:

> The files in this directory are built from KernelSU `v3.2.5`, commit
> `b0bc817b4e966aa6aa830834eaf6ef765d821d40`. They are not interchangeable
> between KMIs.

and:

> The standalone `.ko` files are retained for auditing. Root My Galaxy
> downloads the corresponding `ksud-*` file because `ksud late-load` loads its
> embedded `<kmi>_kernelsu.ko` asset.

An Apache-2.0 licence on the surrounding repository does not remove GPL-3.0
obligations for an embedded GPL-3.0 binary. GPL-3.0 section 6 governs
conveying object code, and the corresponding-source obligations are not
discharged by this file. **This has not been resolved here.** Before the APK or
this repository is conveyed to anyone else, either satisfy GPL-3.0 section 6
for the KernelSU component, including offering the corresponding source, or
remove it.

### Root helper

`libcve43499root.so` is compiled from `src/su_daemon.c` in the same payloads
repository, under the same Apache-2.0 licence, using the command and NDK
recorded in
[`libcve43499root.provenance.json`](app/src/main/jniLibs/arm64-v8a/libcve43499root.provenance.json).
It is a fresh build, and it differs from the upstream source by
[`tools/helper-marker-removal.patch`](tools/helper-marker-removal.patch).

That patch removes the bootstrap readiness marker. Upstream's version reads a
destination path from `/data/local/tmp/cve43499-root-marker-path` — a directory
any shell can write to — and then opens that path as root with `O_CREAT|O_TRUNC`
and no ownership, mode, symlink or directory check. It is a root-write
primitive with an attacker-chosen destination, this app never uses the feature,
so it is removed rather than left dormant. Removing it also restores
`umh_main`'s original three-argument check, since the fourth argument existed
only to carry the marker path. No other source line is changed, so the
`--ephemeral` attempt and its fallback, the loader path, the logcat bind-mount
target and the package name are byte-identical to upstream.

Apache-2.0 section 4(b) requires modified files to carry prominent notices of
the change. This repository ships the change as a unified diff, not as the
modified `su_daemon.c` itself, and the diff has no modification notice in its
header. Whether that discharges 4(b) for any future conveyance is a
distributional question that has not been settled here; carry the patched
source with an explicit notice if this is ever shared.

### Samsung-derived material

The kernel offsets, the BTF-derived structures and the P0 fingerprint table
encode information extracted from Samsung firmware images. Samsung's firmware
licence terms are separate from any licence on the repositories above and have
not been audited as part of this work.

## Build-time dependencies

Resolved at build time, not vendored:

- Android Gradle Plugin 9.2.1, Kotlin 2.4.0, Gradle 9.5.1 — Apache-2.0
- Jetpack Compose, AndroidX, Material Color Utilities — Apache-2.0
- `com.materialkolor:material-kolor:4.1.1` — MIT
- `dev.rikka.shizuku:api:13.1.5`, `dev.rikka.shizuku:provider:13.1.5` — MIT
- `org.json:json:20250517` (test only) — public domain / JSON licence
- `junit:junit:4.13.2` — Eclipse Public License 1.0

The MIT-licensed components require the licence text and copyright notice to
travel with the binary distributions; their POMs carry those, and Gradle's
cached metadata retains them, but no copy is checked into this repository.

## Attribution retained from upstream

- Root My Galaxy and Root-My-Galaxy-Payloads are copyright their authors and
  are used under Apache-2.0.
- KernelSU is copyright its authors and is used under GPL-3.0.
- The CVE-2026-43499 exploit is based on work published by NebuSec.
