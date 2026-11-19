# SM-S9210 EZIF KernelSU-Next v3.4.2 candidate validation (2026-11-19)

> Filename uses the UTC date of the evidence collection: 2026-11-19 07:23–07:24
> +08:00 (2026-11-18 23:23–23:24 UTC). This record flips the hardware-status
> claim for the `e1q-S9210ZHU6EZIF-next` candidate profile; the pair build and
> audit record stays in [`SM-S9210-EZIF-NEXT-pair-audit-20261119.md`](SM-S9210-EZIF-NEXT-pair-audit-20261119.md).

## Result

One read-only post-install evidence collection ran on a physical SM-S9210 on
2026-11-19 (~07:23–07:24 +08:00, `2026-11-18T23:23:48Z`–`2026-11-18T23:24:29Z` UTC
per the logcat window). The collection ran via `ssh <host> "adb …"`; it did not
reboot the phone, did not re-run the exploit, and did not modify it.

What this established (single boot, single collection): the KernelSU-Next
v3.4.2 Samsung no-patch-text pair is **resident and functioning on this
kernel**: `kernelsu` is `Live` in `/proc/modules`, a shell `su` lands as uid 0
with context `u:r:ksu:s0`, `ksud --version` reports `3.4.2 (uapi: 5)`,
SELinux is `Enforcing` on both sides, and the dmesg stream shows the Samsung
KDP credential-install path firing on calls into `su`.

What it did NOT establish: see **Daemon byte-identity** and **Validation
scope** below — in particular, byte-identity of the running daemon against the
APK-bundled bytes, the Manager UI `Working` screen, the launch-time run itself
(no re-run was done as part of this collection), repeatability, and reboot
behaviour. One collection on one boot is not repeatability.

| Item | Observed value |
| --- | --- |
| Device | `SM-S9210` / `e1qzhx` |
| Firmware | `S9210ZHU6EZIF` (`CP2A.260605.016.S9210ZHU6EZIF` / incremental `S9210ZHU6EZIF`) |
| Security patch | `2026-09-05` |
| Fingerprint | `samsung/e1qzhx/e1q:17/CP2A.260605.016/S9210ZHU6EZIF:user/release-keys` |
| Kernel | `6.1.162-android14-11-34343818-abS9210ZHU6EZIF` |
| Root My Galaxy app | `dev.samson.s24root`, `0.3.5-s24.1`, versionCode `19` (maintainer-local install 2026-11-19; app-package presence observed in logs during the collection window) |
| Collection window | 2026-11-19 ~07:23–07:24 +08:00 (read-only; no exploit run, no reboot) |
| Profile / transport | `e1q-S9210ZHU6EZIF-next`; the install itself was done with Shizuku; see [`SM-S9210-EZIF-KERNELSU-NEXT-PLAN.md`](SM-S9210-EZIF-KERNELSU-NEXT-PLAN.md) §Transport |
| Exploit bytes | `cve-2026-43499-app.so`, 104128 bytes, SHA-256 `7b68f38d…22e30cb` (identical to the v3.3.1/EZIF exploit; see pair audit) |
| Bundled `ksud` | `ksud-next-e1q-S9210ZHU6EZIF-kdp`, 4309840 bytes, SHA-256 `d97d3ba4…f866` (candidate KernelSU-Next v3.4.2, CI pair build 44374096436, branch `pair/e1q-S9210ZHU6EZIF-ksun342`, pair commit `c1a3f9332922431764fa00bc306e184a3d8e4d91` in `samson910022/Root-My-Galaxy-Payloads-Next`) |
| Pair provenance | Payloads fork branch `pair/e1q-S9210ZHU6EZIF-ksun342` @ `c1a3f9332922431764fa00bc306e184a3d8e4d91`; flavour `kernelsu-next`; tag `v3.4.2` (`4c706b5938665866ebe16944a481a7b91e3396af`); `pair_no_patch_text=true`; KMI android14-6.1; DDK `ghcr.io/ylarod/ddk-min:android14-6.1-20260828` (full breakdown in the pair audit doc) |
| KernelSU Manager package | `com.rifsxd.ksunext`, v3.4.2, versionCode `33355` (`KernelSU_Next_v3.4.2_33355-release.apk`); present on device (observed in logcat at collection time; see also [`SM-S9210-EZIF-KERNELSU-NEXT-PLAN.md`](SM-S9210-EZIF-KERNELSU-NEXT-PLAN.md) §Manager) |
| Official manager also installed | `me.weishu.kernelsu` — **not** the pairing for this candidate; do not confuse the two |
| `boot_id` | `6068e96a-…` (full value retained off-repo; per-boot random, no validation value) |

Raw evidence is retained off-repo (`evidence-20261119/`, 7 files incl. a
0-byte `kernelsu-module.txt`, ~700 KB total: `dmesg-ksu.txt`,
`dmesg-tail.txt`, `evidence-basics.txt`, `kernelsu-module.txt`,
`logcat-long.txt`, `logcat-tail.txt`, `modules-su.txt`) — **not** committed
to this repo.

## Post-install checks

Single representative lines only; full outputs stay in the retained off-repo
files named below (not committed).

- `su -c id` (`evidence-basics.txt`):
  `uid=0(root) gid=0(root) groups=0(root) context=u:r:ksu:s0`
- `su -c getenforce` vs shell `getenforce` (`evidence-basics.txt`):
  both report `Enforcing`.
- `/proc/modules` first line (`modules-su.txt`):
  `kernelsu 512000 4 - Live 0x0000000000000000 (O)`
  (a second read at a slightly different time showed reference count `3`;
  both put the module at size 512000, `Live`)
- `su -c 'ksud --version'` (`evidence-basics.txt`):
  `ksud 3.4.2 (uapi: 5)`
- `su -c dmesg` KernelSU lines (`dmesg-ksu.txt`, 60 lines total):
  - KDP credential-install path fired three times for `su` processes —
    `[ 847.272654] KernelSU: Samsung KDP task-scoped credential install pid=27612 uid=0 euid=0`,
    `[ 860.877072] KernelSU: Samsung KDP task-scoped credential install pid=27662 uid=0 euid=0`,
    `[ 862.605328] KernelSU: Samsung KDP task-scoped credential install pid=27667 uid=0 euid=0`
  - the UMH/`su` plumbing followed it to the `su` execve —
    `newfstatat su->ksud!`, `faccessat su->ksud!` (6 lines matched)
  - `ksu fd installed: 3 for pid 27612` (and the release after each call)
  - 42 `Samsung DEFEX bypass: uid-0 task outside the KernelSU domain (pid …)`
    lines for system uid-0 tasks (`init` pid 249, `storaged`, `iod`,
    `PERFD-SERVER`, binder threads) — the expected no-patch-text chatter on
    Samsung, not a by itself success signal
  - `sys_execve su found` (the su hook firing)
- `pm list packages` evidence (`evidence-basics.txt`):
  `me.weishu.kernelsu` and `com.rifsxd.ksunext` both installed (see table).

## Daemon byte-identity: known unresolved discrepancy

Same caveat class as the v3.3.1 EZIF record. Three distinct byte sets must
not be conflated:

(a) APK-bundled bytes (this PR's pinned pair at fork commit `c1a3f93`):
`ksud-next-…-kdp` `d97d3ba4…f866` (4309840 B).
(b) The late-load-time staged loader copied to the device during install.
(c) The on-device daemon present at verification time (`/data/adb/ksud`).

No on-device `sha256sum` of `/data/adb/ksud` or of the staged loader was
captured in this collection. Nothing here adjudicates which bytes were
executing; `ksud --version` proves the running daemon answers as v3.4.2 with
uapi 5, it does not prove byte-identity with (a). A plausible mechanism
exists for drift (the Next manager can replace the staged daemon, as the
v3.3.0/`32601` manager did on 2026-10-19) but that analogy proves nothing
here. Verdict: root, module Live, dmesg KDP-path, `ksud --version`, and
manager package lines stand as observed; byte-identity against the bundled
bytes does not.

## Artifacts

> Pair snapshot for this PR is fork commit `c1a3f93`
> (`samson910022/Root-My-Galaxy-Payloads-Next`,
> `pair/e1q-S9210ZHU6EZIF-ksun342`) with the build receipts in
> [`SM-S9210-EZIF-NEXT-pair-audit-20261119.md`](SM-S9210-EZIF-NEXT-pair-audit-20261119.md).
> Do not read results against a later head of that branch as results for
> this APK.

- Exploit: `cve-2026-43499-app.so`, 104128 bytes, SHA-256
  `7b68f38d9c3836c83cfc5af3af454036fe6d3f5f455c504df5670c44c22e30cb`,
  byte-identical to the EZIF v3.3.1 profile exploit.
- `ksud`: `ksud-next-e1q-S9210ZHU6EZIF-kdp`, 4309840 bytes, SHA-256
  `d97d3ba464e188fb280177860e006bfa92d48a09e3c7600eee06d906e032f866`,
  pair commit `c1a3f93` (see pair audit).

## Validation scope

- This was **one boot, one evidence pass**. The install itself (Shizuku-mode
  run, RAM-only module load) was performed on 2026-11-19; this collection
  measures the resident state, not the launch path. Cross-reboot
  repeatability and the pre-`AccessRootManager` settling behaviour are
  unmeasured.
- RAM-only: a reboot removes the module (design). Expected clean boot;
  not re-verified in this pass.
- Manager UI `Working` screen was not captured; only package presence and
  the in-kernel side are evidenced.
- `resetprop` / property read-write behaviour was not probed in this pass.
- The module-count diff between two `/proc/modules` reads (`4` vs `3`) is
  normal reference-count drift on a live system, not instability.
- The Samsung DEFEX-bypass lines are background chatter for uid-0 processes
  outside the KernelSU domain; by themselves they do not distinguish
  success from failure. The success signals are the KDP credential-install
  lines and the `u:r:ksu:s0` context, both of which were observed.
- Root is a one-shot-per-boot prerequisite; this evidence pass does not
  cover a fresh boot re-trigger.

*Collected with read-only `adb` checks; the phone was not rebooted or
re-exploited as part of this record.*
