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

All three derive from Root-My-Galaxy-Payloads at commit
`a311cb88c28204c3902af9450ae4077199e4d71d` (pull request #320, not merged). The
two payload binaries are copied verbatim; the helper is compiled from that
commit's source and then patched, as described below.

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

`ksud-e1q-S9210ZHS6DZG1-kdp` is built from KernelSU `v3.2.5`, commit
`b0bc817b4e966aa6aa830834eaf6ef765d821d40`, as stated in the payloads
repository's `kernelsu/README.md`. It embeds the matching
`android14-6.1` KernelSU module.

KernelSU is licensed GPL-3.0, which is a different licence from the
Apache-2.0 covering this repository. The payloads repository's own statement is:

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
