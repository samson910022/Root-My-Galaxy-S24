# SM-S9210 EZIF KernelSU v3.3.1 candidate validation (2026-11-02)

## Result

Status: **candidate (pending)** — no validation run has been recorded yet for
the `e1q-S9210ZHU6EZIF` profile. Payload pins below are filled from the
payloads-side publish; run-dependent fields stay `TODO` until a device run
completes.

| Item | Observed value |
| --- | --- |
| Device | `SM-S9210` / `e1qzhx` (expected; no run yet) — TODO |
| Firmware | `S9210ZHU6EZIF` (expected; no run yet) — TODO |
| Kernel | `6.1.162-android14-11-34343818-abS9210ZHU6EZIF` (manifest gate; no device read yet) — TODO |
| Root My Galaxy app | `0.3.4-s24.1`, versionCode `18` (expected) — TODO |
| Run | TODO (no run yet) |
| Profile / transport | `e1q-S9210ZHU6EZIF`, Shizuku enabled (expected) — TODO |
| Exploit bytes | `7b68f38d…22e30cb` (104128 bytes, `payloads/e1q-S9210ZHU6EZIF/cve-2026-43499-app.so`) |
| Bundled `ksud` | 5065680 bytes, SHA-256 `fde99b5a…ca0abe61` (`payloads/e1q-S9210ZHU6EZIF/ksud-e1q-S9210ZHU6EZIF-kdp`, KernelSU v3.3.1 candidate) |
| Pair provenance | Payloads fork `samson910022/Root-My-Galaxy-Payloads` commit `58a677281ab2aa8ae43cfb15619628c2ab2e48b3` (`feature/e1q-S9210ZHU6EZIF-port`); both artifacts fetched from the fork (`RAW_BASE` fork x2; EZIF exists only there). Reference `.ko` `android14-6.1_kernelsu-e1q-S9210ZHU6EZIF-kdp.ko` (432656 bytes, `af640cda…`) and the v3.3.0 fallback `ksud-e1q-S9210ZHU6EZIF-v3.3.0-kdp` ship alongside in the payloads repo but are not used by this profile. |
| KernelSU Manager package | TODO (no run yet) |

No app-history record exists yet for this profile — TODO.

## Validation scope

- Not established yet: bootstrap root, KernelSU `late-load`, the app's
  control-channel check, Manager UI recognition, `resetprop` regressions,
  repeatability, reboot behavior, or temperature — TODO, pending a device run.
- The support-feed e1q artifact remains unchanged; this note records the
  candidate test only.
- Payload pins are now filled (`tools/sync-payloads.sh` EZIF pins +
  `builtin-targets.json` second entry, verified size/SHA in the assets tree).
  Remaining blocker: device run on `S9210ZHU6EZIF` (payloads commit
  `58a6772` is local to the fork; `raw.githubusercontent.com` fetch 404s until
  it is pushed, so the staged bytes were copied from the local payloads
  worktree at the same commit and verified).
