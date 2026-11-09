# SM-S9210 EZIF KernelSU v3.3.1 candidate validation (2026-11-02)

> Filename uses the UTC date: the run below was 2026-11-03 05:45–05:46 +08:00
> (`2026-11-02T21:45:04Z–21:46:53Z` UTC).

## Result

One read-only post-install evidence collection succeeded on a physical
SM-S9210 on 2026-11-03 (~05:45–05:46 +08:00,
`2026-11-02T21:45:04Z–21:46:53Z` UTC). The collection ran read-only checks via
`ssh <host> "adb ..."` and did not reboot, re-run the exploit, or
otherwise modify the phone.

What this established (single run only — not repeatability, not the full
post-install regression suite): bootstrap root (`su -c id` → `uid=0`),
SELinux `Enforcing` on both the root and non-root sides, the `kernelsu`
module `Live` in `/proc/modules`, a `dmesg handle_setresuid` stream,
`ksud --version` reporting `3.3.1 (uapi: 5)`, and the Manager package at
`v3.3.1` / versionCode `32680`.

What it did NOT establish: byte-identity of the running daemon against the
APK-bundled bytes (the version string and package version do not prove which
bytes are executing — see the byte-identity note below), the Manager UI
`Working` status, `resetprop` behaviour, or repeatability (one evidence run).
Reboot persistence is now tested separately (2026-11-04, non-persistent as
expected — see the reboot note in Validation scope).

| Item | Observed value |
| --- | --- |
| Device | `SM-S9210` / `e1qzhx` |
| Firmware | `S9210ZHU6EZIF` (`CP2A.260605.016.S9210ZHU6EZIF` / incremental `S9210ZHU6EZIF`) |
| Kernel | `6.1.162-android14-11-34343818-abS9210ZHU6EZIF` |
| Root My Galaxy app | `0.3.4-s24.1`, versionCode `18` |
| Run | `2026-11-03 05:45–05:46 +08:00` read-only evidence collection (no exploit run, no reboot) |
| Profile / transport | `e1q-S9210ZHU6EZIF`, Shizuku artifact present (`shizuku.root`, see `c06b`; enabled not directly probed) |
| Exploit bytes | `7b68f38d…22e30cb` (104128 bytes, `payloads/e1q-S9210ZHU6EZIF/cve-2026-43499-app.so`) |
| Bundled `ksud` | 5065680 bytes, SHA-256 `fde99b5a…ca0abe61` (`payloads/e1q-S9210ZHU6EZIF/ksud-e1q-S9210ZHU6EZIF-kdp`, KernelSU v3.3.1 candidate) |
| Pair provenance | Payloads fork `samson910022/Root-My-Galaxy-Payloads` commit `58a677281ab2aa8ae43cfb15619628c2ab2e48b3` (`feature/e1q-S9210ZHU6EZIF-port`); both artifacts fetched from the fork (`RAW_BASE` fork x2; EZIF exists only there). Reference `.ko` `android14-6.1_kernelsu-e1q-S9210ZHU6EZIF-kdp.ko` (432656 bytes, `af640cda…`) and the v3.3.0 fallback `ksud-e1q-S9210ZHU6EZIF-v3.3.0-kdp` ship alongside in the payloads repo but are not used by this profile. |
| KernelSU Manager package | `me.weishu.kernelsu`, `v3.3.1`, versionCode `32680` |
| `boot_id` (truncated) | `de00aae0-…` (full value retained off-repo; per-boot random, no validation value; App-root success boot on 2026-11-03) |
| `boot_id` post-reboot (2026-11-04) | `2f39dbe6-…` (full `2f39dbe6-446b-4928-a9af-79cd4895174d`; `su` absent, `Enforcing`, no `kernelsu` module → root not persistent, expected; see reboot note in Validation scope) |

Raw evidence is retained off-repo (38 `.log` files + `SHA256SUMS.txt` +
`verdict.md` + `verdict.sha256` = 41 files, not committed to this repo).

## Post-install checks

Single representative lines only; full outputs stay in the retained
off-repo raw files named below (not committed).

- `su -c id` (`b01_su_id.log`):
  `uid=0(root) gid=0(root) groups=0(root) context=u:r:ksu:s0`
- `su -c getenforce` vs shell `getenforce`
  (`b02_su_id_getenforce.log`, `b03_shell_getenforce.log`): both report
  `Enforcing` — root works under an enforcing policy, and the non-root
  shell agrees.
- `/proc/modules` first line (`c01_proc_modules.log`):
  `kernelsu 237568 4 - Live 0x0000000000000000 (O)`
- `su -c 'ksud --version'` (`c04_ksud_version.log`):
  `ksud 3.3.1 (uapi: 5)`
- `su -c dmesg` representative lines
  (`c03b_dmesg_su_kernelsu.log`, 128 `KernelSU:` lines total, of which 86 `handle_setresuid`):
  `[ 5197.921265] KernelSU: handle_setresuid from 0 to 10325`,
  `[ 5198.120192] KernelSU: handle_setresuid from 0 to 10333`.
  (Plain `dmesg` without root is `klogctl: Permission denied` as
  expected — see `c03_dmesg_kernelsu.log`.)
- `dumpsys` version lines (`d03_dumpsys_kernelsu.log`,
  `e01_dumpsys_s24root.log`):
  Manager `versionName=v3.3.1` / `versionCode=32680`;
  app `versionName=0.3.4-s24.1` / `versionCode=18`.
  (Package/version lines only — they do not identify the running daemon
  bytes; see the byte-identity note below.)

## Daemon byte-identity: known unresolved discrepancy

Three distinct byte sets must not be conflated:

(a) APK-bundled bytes (this repo at payloads snapshot `58a6772`):
exploit `7b68f38d…22e30cb` (104128 bytes),
`ksud` `fde99b5a…ca0abe61` (5065680 bytes).
(b) The late-load-time staged loader copied to the device during install.
(c) The on-device daemon present at verification time (`/data/adb/ksud`).

The Payloads-side evidence for this run records on-device `/data/adb/ksud`
as 4,990,848 bytes — differing from the pinned 5,065,680-byte `ksud` in
(a). This discrepancy is UNRESOLVED: no on-device `sha256sum` of
`/data/adb/ksud` or of the staged loader was captured, so nothing here
adjudicates which bytes were executing. A plausible mechanism exists (the
KernelSU Manager can replace `/data/adb/ksud` with its own bundled build —
on 2026-10-19 Manager `32601` overwrote the DZG1 staged loader
`88748945…dc15049` → `99aaa607…3b5c41`; see README), but that analogy does
not prove what happened on this EZIF run. Verdict: the root,
module-live, `dmesg`, `ksud --version`, and Manager package-version lines
stand as observed; byte-identity of the running daemon against the
APK-bundled bytes does not.

## Artifacts

> Validated/released snapshot for THIS PR is fork commit `58a6772`
> (`samson910022/Root-My-Galaxy-Payloads`,
> `feature/e1q-S9210ZHU6EZIF-port`). Payloads PR #10 head has since moved
> (`a62f118`) with a different exploit artifact — results from #10 head
> must not be read as results of this APK. Keeping the pinned `58a6772`
> bytes is deliberate; bumping to a newer head requires
> manifest + script + binaries + regression evidence together.

- Exploit: `cve-2026-43499-app.so`, 104128 bytes, SHA-256
  `7b68f38d9c3836c83cfc5af3af454036fe6d3f5f455c504df5670c44c22e30cb`,
  source commit `58a6772` (fork `feature/e1q-S9210ZHU6EZIF-port`).
- `ksud`: `ksud-e1q-S9210ZHU6EZIF-kdp`, 5065680 bytes, SHA-256
  `fde99b5a129e5714fe6f598cf8276c76d2cbaf1a7f4e6ce9ae69d5ebca0abe61`,
  source commit `58a6772` (same fork/branch).
- Release APK (maintainer-local build, not committed, not CI-verified in
  this PR): `roots24-0.3.4-s24.1.apk`, 53775826 bytes (~53 MB),
  SHA-256 `1f6f990bfb521387bf31bed84fb794b95d9ced14d8d839c2285da48be4562bc8`,
  signer cert SHA-256
  `4256991b32a612cf6ec3e5d8730f135fc8afb082082def192b1abb04f9ff2e93`
  — verified locally via `sha256sum` + `apksigner verify --print-certs`.
  Reviewers cannot reproduce this from the PR alone; the releasable
  artifact must come from a green `release.yml` run on the merged commit.
- Payload pins are filled (`tools/sync-payloads.sh` EZIF pins +
  `builtin-targets.json` second entry, verified size/SHA in the assets
  tree). Provenance note (updated 2026-11-04): payloads commit `58a6772`
  is pushed to the fork and both `raw.githubusercontent.com` URLs now
  return HTTP 200 with byte-identical SHA-256/size; the staged bytes were
  copied from the local payloads worktree at the same commit and verified
  against those digests. Prefer a clean `tools/sync-payloads.sh` re-fetch
  transcript before tagging a release.

## Validation scope

- PASS: bootstrap root (`su -c id` → `uid=0`), KernelSU `late-load`
  (module `kernelsu` live in `/proc/modules` + `dmesg handle_setresuid`
  stream), `su` grant path, `/proc/modules` check, `ksud --version`
  (`3.3.1 (uapi: 5)`), Manager package version (`v3.3.1` / `32680`).
- PENDING: Manager UI `Working` status (needs a user screenshot),
  `resetprop` regressions, repeatability (single evidence run only).
- TESTED 2026-11-04 (read-only `adb` post-reboot check, evidence retained
  off-repo at
  `/mnt/240G_SSD/opencode_tmp/reboot_nopersist_20261104/reboot_persist_check.log`):
  reboot persistence — NOT persistent, as expected for RAM-only LKM root.
  New `boot_id` `2f39dbe6-446b-4928-a9af-79cd4895174d` (prior App-root
  success boot `de00aae0-…`); `su` not found
  (`/system/bin/sh: su: inaccessible or not found`), `Enforcing`, no
  `kernelsu` line in `/proc/modules`; no new lastkmsg (still `#132–#136`),
  `bootreason=reboot,shell` (clean reboot).
- Release caution: this profile is `source: BUILTIN`, so merging this PR
  ships the candidate inside every release build. Since Phase 1 it resolves
  only on the exact `kernelReleases` string AND with the Settings >
  candidate-profiles opt-in enabled (default off; the
  `[candidate: KernelSU v3.3.1]` display text is not a gate). Hold the
  `v0.3.4-s24.1` tag until the PENDING items clear or the risk is
  explicitly accepted. Companion dependency: payloads fork branch
  `feature/e1q-S9210ZHU6EZIF-port` (commit `58a6772`, payloads PR #10)
  is not merged upstream.
- The upstream online support feed is unchanged; the DZG1 bundled entry
  and both DZG1 binaries are byte-identical. This note records the
  candidate test only.
