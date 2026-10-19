# Third-party notices

This repository is a derivative work of the components below. The Apache-2.0
licence covering the application source is in [LICENSE](LICENSE); the terms of
each component still apply to that component.

In the repository the licence texts are under `LICENSES/` and the notices sit
next to this file; inside the APK the same files are packaged under
`assets/licenses/`.

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

The helper is checked in unstripped (26,040 bytes, SHA-256
`b63951595d631fc7f259ce2ae596da121bd62765e2c016d460fb61b694b5bb1f`); AGP
strips it to 21,480 bytes, SHA-256
`1818103f4345b9049741fbdc1b5379f9cc48a69a3e3f5cc07c69c51f145278fe`, inside
the APK.

### Exploit payload

Root-My-Galaxy-Payloads states that the port is based on exploit source
published at
<https://github.com/NebuSec/CyberMeowfia/tree/main/IonStack/CVE-2026-43499/exploit>.
That upstream is licensed Apache-2.0, and the revision used here is pinned at
commit `2fb9d24e1408f5567bbeb5d7fa2d36ce808c5a1a`, which is recorded alongside
the licence in this repository. The MIT-licensed components and all other
attributions are in [NOTICE](NOTICE) and [LICENSES/](LICENSES/).

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
The overall install flow has succeeded on three separate dates; the Manager UI
status was captured and the resetprop regression suite passed on 2026-10-19.
Root is RAM-only and does not survive a reboot (verified previously); no
persistence mechanism is implemented.
The earlier device test (2026-09-11) covered the v3.2.5 loader
(`b0bc817b4e966aa6aa830834eaf6ef765d821d40`, 4895088 bytes, SHA-256
`67b49cad…ee542`). Pair v3.3.0 with Manager `32601`; a 3.2.5 Manager reports
version skew.

KernelSU is licensed under two different GNU GPL versions depending on the
part, and neither is the Apache-2.0 licence covering this repository. The
`ksud` userspace loader is GPL-3.0-or-later (used here under GPL-3.0), and the
kernel module embedded inside `ksud` (`android14-6.1_kernelsu.ko`) is
GPL-2.0-only. The obligations for both are discharged in this repository:

- The full GPL-3.0 and GPL-2.0 texts are checked in at
  [LICENSES/GPL-3.0.txt](LICENSES/GPL-3.0.txt) and
  [LICENSES/GPL-2.0.txt](LICENSES/GPL-2.0.txt), and packaged inside the APK at
  `assets/licenses/LICENSE-GPL-3.0.txt` and
  `assets/licenses/LICENSE-GPL-2.0.txt`.
- The Samsung patch file
  (`kernelsu/patches/KernelSU-v3.3.0-samsung-kdp-rkp-defex.patch`, dated
  2026-10-01 in Payloads Phase-D commit `8236c843`) records the changes made to
  KernelSU before the loader and module were built.
- The [NOTICE](NOTICE) file records the Corresponding Source pointers (the
  KernelSU base commit for `userspace/` and `kernel/`, and the Payloads commit
  for the patch set and build record) together with a written offer to provide
  the Corresponding Source free of charge on request.

The GPL terms apply to `ksud` and its embedded kernel module only. The
Apache-2.0 app is a separate program that executes `ksud`; the APK is an
aggregate, not a combined work.

The following statement from `kernelsu/README.md` in the upstream payloads
repository is historical and describes its v3.2.5 binaries, not this v3.3.0
loader; the fork's copy of that file was rewritten for v3.3.0:

> The files in this directory are built from KernelSU `v3.2.5`, commit
> `b0bc817b4e966aa6aa830834eaf6ef765d821d40`. They are not interchangeable
> between KMIs.

and:

> The standalone `.ko` files are retained for auditing. Root My Galaxy
> downloads the corresponding `ksud-*` file because `ksud late-load` loads its
> embedded `<kmi>_kernelsu.ko` asset.

An Apache-2.0 licence on the surrounding repository does not remove the GPL
obligations for the embedded GPL-3.0 and GPL-2.0 binaries, which is why the
loader and module are conveyed with both licence texts, their attribution and
the Corresponding Source pointers recorded above.

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
the change. This repository ships the change as a unified diff, and the diff
header now carries a section 4(b) modification notice naming the modified file,
the pinned source commit (`a311cb88` in Root-My-Galaxy-Payloads) and the
change; the same provenance is recorded in the helper's provenance file.
`tools/build-helper.sh` pins the upstream commit, applies the patch and
verifies both the original source hash and the patched source hash, so the
notice and the change it describes travel together with the reproducible
build.

### Samsung-derived material

The kernel offsets, the BTF-derived structures and the P0 fingerprint table
encode information extracted from Samsung firmware images. No Samsung firmware
is redistributed by this repository or by the binaries it produces; the
constants and structures are derived information. Samsung's firmware licence
terms are separate from the licences in this repository and do not affect the
Apache-2.0 licence covering this repository's own work.

## Build-time dependencies

Resolved at build time, not vendored:

- Android Gradle Plugin 9.2.1, Kotlin 2.4.0, Gradle 9.5.1 — Apache-2.0
- Jetpack Compose, AndroidX — Apache-2.0
- `com.materialkolor:material-kolor:4.1.1` — MIT
- `com.materialkolor:material-color-utilities:4.1.1` — MIT (POM); includes an
  Apache-2.0 port of Google's Material Color Utilities
- `com.github.ajalt:colormath:3.6.1` — MIT, Copyright 2021 AJ Alt (transitive
  dependency of MaterialKolor)
- `dev.rikka.shizuku:api:13.1.5`, `dev.rikka.shizuku:provider:13.1.5` — MIT
- `org.json:json:20250517` (test only) — JSON License
- `junit:junit:4.13.2` — Eclipse Public License 1.0 (test only; not conveyed in
  distributed binaries)

The licence texts and attributions for the MIT-licensed components conveyed in
the distributed binaries are checked in under [LICENSES/](LICENSES/) and
[NOTICE](NOTICE), and the same files are packaged inside the APK at
`assets/licenses/`.

## Attribution retained from upstream

- Root My Galaxy and Root-My-Galaxy-Payloads are copyright their authors and
  are used under Apache-2.0.
- KernelSU is copyright its authors and is used under GPL-3.0-or-later for
  `ksud` and GPL-2.0-only for the kernel module embedded in it.
- The CVE-2026-43499 exploit is based on work published by NebuSec in
  <https://github.com/NebuSec/CyberMeowfia>, licensed Apache-2.0 and pinned at
  commit `2fb9d24e1408f5567bbeb5d7fa2d36ce808c5a1a`.

The licence texts and notices for the conveyed MIT, GPL-2.0 and GPL-3.0
components are checked in under [LICENSES/](LICENSES/) and
[NOTICE](NOTICE), and the same files (including this notice) are packaged
inside the APK under `assets/licenses/`.
