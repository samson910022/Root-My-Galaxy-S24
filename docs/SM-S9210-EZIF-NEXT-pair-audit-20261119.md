# SM-S9210 EZIF KernelSU-Next pair — build and audit record (2026-11-19)

> Companion to [`SM-S9210-EZIF-KERNELSU-NEXT-PLAN.md`](SM-S9210-EZIF-KERNELSU-NEXT-PLAN.md).
> This records how the KernelSU-Next late-load pair for the candidate profile
> `e1q-S9210ZHU6EZIF-next` was built and what was verified about it.
> It is **not** a hardware validation record; no phone has loaded this pair.

## What was built

| item | value |
| --- | --- |
| CI run | [44374096436](https://github.com/samson910022/Root-My-Galaxy-Payloads-Next/actions/runs/44374096436) (`workflow_dispatch`, completed success 2026-11-18T18:54:51Z) |
| Branch / head | `pair/e1q-S9210ZHU6EZIF-ksun342` @ `e32587f` (workflow input), pair commit `c1a3f9332922431764fa00bc306e184a3d8e4d91` |
| Flavour / ref | `kernelsu-next` / `v3.4.2` (`4c706b5938665866ebe16944a481a7b91e3396af`); patch `kernelsu/patches/KernelSU-Next-v3.4.2-samsung-kdp-rkp-defex.patch` applied by CI |
| Build config | `CONFIG_KSU=m CONFIG_KSU_SAMSUNG_KDP=y CONFIG_KSU_SAMSUNG_RKP=y CONFIG_KSU_SAMSUNG_DEFEX=y CONFIG_KSU_SAMSUNG_NO_PATCH_TEXT=y` (line from the CI log at 2026-11-18T18:51:58.3046765Z) |
| KMI / KDD image | `android14-6.1` / `ghcr.io/ylarod/ddk-min:android14-6.1-20260828` |
| Target release | `6.1.162-android14-11-34343818-abS9210ZHU6EZIF` (substituted into `utsrelease.h` / `kernel.release` before building) |
| `pair_no_patch_text` | `true` (the branch adds the input; the default for `kernelsu-next` pairs is patch-text) |
| publish | `false` — no feed entry created or modified |

A first dispatch (44370735279, `migrate/merge-our-customizations`) built the
**patch-text** pair successfully and was deliberately not used; the pair is the
no-patch-text variant, matching the EZIF v3.3.1 pair and every
hardware-validated Samsung Next build.

## Artifacts

| artifact | size | SHA-256 |
| --- | ---: | --- |
| `kernelsu/android14-6.1_kernelsu-next-e1q-S9210ZHU6EZIF-kdp.ko` | 570584 | `02f05aabd32e5bcfb17ab98b4888f9dafb487695da1dda8e1f7de78a0a2e73f5` |
| `kernelsu/ksud-next-e1q-S9210ZHU6EZIF-kdp` | 4309840 | `d97d3ba464e188fb280177860e006bfa92d48a09e3c7600eee06d906e032f866` |
| `artifacts/e1q-S9210ZHU6EZIF-next/cve-2026-43499-app.so` | 104128 | `7b68f38d9c3836c83cfc5af3af454036fe6d3f5f455c504df5670c44c22e30cb` (identical bytes to the bundled EZIF exploit; vendored because the original payloads fork disappeared — see below) |

Pinned by commit: `c1a3f9332922431764fa00bc306e184a3d8e4d91` in
`samson910022/Root-My-Galaxy-Payloads-Next`:

```
https://raw.githubusercontent.com/samson910022/Root-My-Galaxy-Payloads-Next/c1a3f9332922431764fa00bc306e184a3d8e4d91/kernelsu/ksud-next-e1q-S9210ZHU6EZIF-kdp
https://raw.githubusercontent.com/samson910022/Root-My-Galaxy-Payloads-Next/c1a3f9332922431764fa00bc306e184a3d8e4d91/artifacts/e1q-S9210ZHU6EZIF-next/cve-2026-43499-app.so
```

Note: at the audit instant (2026-11-19 ~03:00 UTC) the raw CDN served 503
for the ~4.3 MB daemon; it recovered later the same day (all pin URLs
returned 200 with matching sizes), which is what the full
`./tools/sync-payloads.sh` pass ran against after the pins were repointed
to this branch. `tools/sync-payloads.sh` retries 503s with `--retry-all-errors`,
and `--check` works offline.

## Version receipts (both halves, agreeing)

```
version=33355          (module-version.txt)
commit=4c706b5938665866ebe16944a481a7b91e3396af
ref=v3.4.2
tag=v3.4.2
```
```
version=33355          (daemon-version.txt)
name=3.4.2
commit=4c706b5938665866ebe16944a481a7b91e3396af
ref=v3.4.2
tag=v3.4.2
```

`check_pair_version.py` ran in the daemon job (it would have failed the job
on any mismatch); the tag is `v3.4.2` and the derived version code is
`30000 + 3355 = 33355`. `git describe` in the daemon checkout reported the tag.

## Module metadata and loader-facing checks

```
$ modinfo android14-6.1_kernelsu-next-e1q-S9210ZHU6EZIF-kdp.ko
license:        GPL
description:    Android KernelSU
vermagic:       6.1.162-android14-11-34343818-abS9210ZHU6EZIF SMP preempt mod_unload modversions aarch64
depends:
```

- vermagic starts with the exact target release — the loader's
  `CONFIG_MODULE_FORCE_LOAD=n` requirement is met.
- `readelf -SW`: `__versions` present but **zero length** (`size 000000`),
  as the manual-relocation loader requires; `.symtab`/`.strtab` retained
  (size `0x145e0` / `0x5bbe`) for Name-based resolution.
- `tools/verify_pair.py --module <ko> --daemon <ksud> --release
  6.1.162-android14-11-34343818-abS9210ZHU6EZIF`:

```
module vermagic: 6.1.162-android14-11-34343818-abS9210ZHU6EZIF
daemon: an AArch64 ELF
```

- `file` on the daemon: `ELF 64-bit LSB pie executable, ARM aarch64 … for Android 26, built by NDK r28c (13676358), stripped`; strings contain `3.4.2 (uapi: 5)`, `33355`, `KernelSU Next`.
- CI gates that ran and passed in the pair jobs: `check_rkp_branch.py --tree
  KernelSU` (the spoof-arming assertion), the DDK image pull check,
  `check_kernel_version.py` (module receipt), the vermagic gate, and
  `check_pair_version.py` (daemon job). The import-diff step against the
  device-tested 6.1 module was **skipped**: the run logged
  `could not fetch a tested module to compare against` (both candidate
  URLs 404; the EZIF pair never existed on BuSung-dev main). The
  operative static gate is the kallsyms-vs-`vmlinux.elf` audit below.

## Symbol / relocation audit vs the recovered EZIF image

Target: `/mnt/240G_SSD/opencode_tmp/EZIF_work/vmlinux.elf` (43222470 B,
`vmlinux-to-elf` output, base `0xffffffc008000000`, 107567 symbols; from the
retained EZIF firmware extraction, see
[`../Root-My-Galaxy-Payloads-Next/docs/SM-S9210-S9210ZHU6EZIF.md`](../Root-My-Galaxy-Payloads-Next/docs/SM-S9210-S9210ZHU6EZIF.md) §7/§9).
Symvers regenerated by `kernelsu/tools/extract_target_symvers.py`:

```
wrote 8346 exact target CRCs to /mnt/240G_SSD/opencode_tmp/EZIF_work/Module.symvers.ezif
```

Audit (`kernelsu/tools/audit_module_against_target.py --manual-relocation`):

```
undefined symbols: 226
module version entries: 0
missing from target symbol table: 0
symbols resolved from kallsyms rather than target exports: 80
undefined symbols intentionally without module CRC: 226
target CRC mismatches: 0
```

For reference, the KernelSU v3.3.1 pair for the same target audited at
221 undefined / 0 missing / 81 kallsyms; the v3.3.0 fallback at
202 / 70. The Next pair needs five more imports than the v3.3.1 pair (226 vs 221), none missing.

## What this does NOT establish

- No phone has loaded this pair. Hardware behaviour of the module
  (`init_module()` outcome, KDP/RKP/DEFEX interaction), the manager UI
  status, `resetprop`, module reload across a userspace restart, and
  repeatability are all unmeasured.
- Reboot persistence is absent by design (late-load, RAM-only).
- CVE-2026-43499 reliability on this firmware remains weakly measured:
  the 2026-11-02/03 record captures a single bootstrap-root success
  (`su -c id` → `uid=0`) with the bundled bytes; repeatability is
  unmeasured.
- The pair is committed only on the `pair/e1q-S9210ZHU6EZIF-ksun342` branch,
  not on `main` and not in any feed; nothing about this record authorizes
  publishing it upstream.

## Fork disappearance note

`samson910022/Root-My-Galaxy-Payloads` (raw 404/503, repo unresolvable under
both owner accounts) was the original pin for the EZIF v3.3.1 pair and the
DZG1 exploit. It disappeared after the 2026-11-04 verification recorded in
the EZIF validation doc. Consequences, and how they were resolved on
2026-11-19 (a full `./tools/sync-payloads.sh` pass now completes end to end and
leaves the tree unchanged):

- Payloads-Next `main` carries a *different* exploit rebuild
  (`efc8b49c…`), not a substitute;
- the DZG1 exploit pin was repointed to `e3a578d` (byte-identical, verified
  by SHA-256), the shared EZIF/Next exploit to `c1a3f93` (this branch),
  the EZIF v3.3.1 ksud to `f91d730` (`main`, byte-identical, verified),
  the DZG1 ksud stays on `BuSung-dev` `8236c84` (unchanged), and the new
  Next pair is pinned at `c1a3f93` (this branch);
- `--check` was and remains fully offline; CI uses `--check` only;
- the fork remains gone; do not repoint anything at different bytes.
