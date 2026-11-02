# SM-S9210 EZIF KernelSU v3.3.1 candidate validation (2026-11-02)

## Result

One read-only post-install evidence collection succeeded on a physical
SM-S9210 on 2026-11-03 (~05:45–05:46 +08:00,
`2026-11-02T21:45:04Z–21:46:53Z` UTC). This validates the installed state
of the exact e1q candidate pair, not repeatability or the full
post-install regression suite. The collection ran read-only checks via
`ssh ubuntu-13g1 "adb ..."` and did not reboot, re-run the exploit, or
otherwise modify the phone.

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
| `boot_id` | `de00aae0-f7cd-4789-8a79-274b1d8ad9a4` |

Raw evidence: `/mnt/240G_SSD/opencode_tmp/ezif_approot_20261103/`
(38 logs + `SHA256SUMS.txt` + `verdict.md`).

## Post-install checks

Single representative lines only; full outputs stay in the named raw
files under `/mnt/240G_SSD/opencode_tmp/ezif_approot_20261103/`.

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

## Artifacts

- Exploit: `cve-2026-43499-app.so`, 104128 bytes, SHA-256
  `7b68f38d9c3836c83cfc5af3af454036fe6d3f5f455c504df5670c44c22e30cb`,
  source commit `58a6772` (fork `feature/e1q-S9210ZHU6EZIF-port`).
- `ksud`: `ksud-e1q-S9210ZHU6EZIF-kdp`, 5065680 bytes, SHA-256
  `fde99b5a129e5714fe6f598cf8276c76d2cbaf1a7f4e6ce9ae69d5ebca0abe61`,
  source commit `58a6772` (same fork/branch).
- Release APK: `roots24-0.3.4-s24.1.apk`, 53775826 bytes (~53 MB),
  SHA-256 `1f6f990bfb521387bf31bed84fb794b95d9ced14d8d839c2285da48be4562bc8`,
  signer cert SHA-256
  `4256991b32a612cf6ec3e5d8730f135fc8afb082082def192b1abb04f9ff2e93`
  — verified locally via `sha256sum` + `apksigner verify --print-certs`.
- Payload pins are filled (`tools/sync-payloads.sh` EZIF pins +
  `builtin-targets.json` second entry, verified size/SHA in the assets
  tree). Remaining blocker note from scaffolding still applies:
  payloads commit `58a6772` is local to the fork;
  `raw.githubusercontent.com` fetch 404s until it is pushed, so the
  staged bytes were copied from the local payloads worktree at the same
  commit and verified.

## Validation scope

- PASS: bootstrap root (`su -c id` → `uid=0`), KernelSU `late-load`
  (module `kernelsu` live in `/proc/modules` + `dmesg handle_setresuid`
  stream), `su` grant path, `/proc/modules` check, `ksud --version`
  (`3.3.1 (uapi: 5)`), Manager package version (`v3.3.1` / `32680`).
- PENDING: Manager UI `Working` status (needs a user screenshot),
  `resetprop` regressions, repeatability (single evidence run only),
  reboot persistence (RAM-only root is expected to vanish on reboot;
  not re-tested on this firmware).
- The support-feed e1q artifact remains unchanged; this note records the
  candidate test only.
