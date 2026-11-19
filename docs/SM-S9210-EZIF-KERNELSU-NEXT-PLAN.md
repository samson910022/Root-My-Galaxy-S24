# SM-S9210 EZIF temporary root via CVE-2026-43499 with KernelSU-Next as the superuser manager (plan)

> **Status (2026-11-19):** the acceptance hardware step this plan called for
> has been met once on SM-S9210 — see
> [`SM-S9210-EZIF-NEXT-validation-20261119.md`](SM-S9210-EZIF-NEXT-validation-20261119.md).
> This plan document otherwise stands as originally written.

Status: implementation plan, produced 2026-11-19; amended after plan
review (stale KernelSU-Next state table; missing no-patch-text decision).
Not device-verified. The Android APK shipped from this branch does **not**
make anything production ready; the hardware step that closes this plan is
listed under [Acceptance and hardware validation](#acceptance-and-hardware-validation) and
is out of scope for the PR.

Plan history:

1. 2026-11-19 initial draft.
2. 2026-11-19 review 1 (issues on the state table, the base-ref rationale,
   the v3.4.2 carry attribution, Phase 1 artifact names) → corrected.
3. 2026-11-19 CI dispatch 44370735279 (`migrate/merge-our-customizations`,
   the branch has no `pair_no_patch_text` input, so the pair built as
   patch-text unconditionally) completed successfully — proof the v3.4.2
   patch and the workflow work end to end for e1q-S9210ZHU6EZIF, and proof
   that the *default pair flavour for `kernelsu-next` is patch-text*. The
   EZIF v3.3.1 pair and the GZIF Next 3.4.2 run are both no-patch-text, so
   a `pair_no_patch_text` input was added on the dedicated branch
   `pair/e1q-S9210ZHU6EZIF-ksun342` (`e32587f`, cut from
   `migrate/merge-our-customizations`) and the ship pair was dispatched
   from it with `pair_no_patch_text=true` (run 44374096436, created
   2026-11-18T18:50:43Z, completed success 2026-11-18T18:54:51Z: the
   no-patch-text module, and the pair daemon embedding it, both built).
   The patch-text pair is not used.

4. 2026-11-19 the original payloads fork (`samson910022/Root-My-Galaxy-Payloads`)
   was found gone (raw 503/404, repo unresolvable under both owner accounts).
   The pair branch `pair/e1q-S9210ZHU6EZIF-ksun342` in
   `Root-My-Galaxy-Payloads-Next` is the pin host for the Next pair; the
   pair commit is `c1a3f9332922431764fa00bc306e184a3d8e4d91`.
5. 2026-11-19 the refresh pins in `tools/sync-payloads.sh` were repointed to
   byte-identical copies in `Root-My-Galaxy-Payloads-Next` (verified by
   SHA-256): the DZG1 exploit to `e3a578d` (byte-identical to the fork pin),
   the shared EZIF/Next exploit to `c1a3f93`, the EZIF v3.3.1 ksud to
   `f91d730` (`main`), the DZG1 ksud stays on `BuSung-dev` `8236c84`
   (unchanged), and the new KernelSU-Next v3.4.2 pair is pinned at
   `c1a3f93`. A full `./tools/sync-payloads.sh` pass now completes end to end
   and leaves the tree unchanged; the EZIF v3.3.1 refresh path is restored.

## Objective

Give the `SM-S9210` / `S9210ZHU6EZIF` target (kernel
`6.1.162-android14-11-34343818-abS9210ZHU6EZIF`, Android 17 / One UI 9,
`CP2A.260605.016`) a temporary-root path whose superuser manager is
**KernelSU-Next** instead of KernelSU. The path must:

- use the existing CVE-2026-43499 (GhostLock) exploit payload, unchanged;
- use a KernelSU-Next module + `ksud` late-load pair built for the exact
  EZIF release string;
- resolve behind the same Settings > Candidate profiles opt-in (default off)
  as the existing KernelSU v3.3.1 EZIF candidate;
- coexist with the KernelSU v3.3.1 candidate instead of replacing it;
- leave the upstream online feed, the DZG1 profile, and the v3.3.1 candidate
  untouched.

## Background and evidence

### The vulnerability

CVE-2026-43499 ("GhostLock") is a use-after-free in `remove_waiter()` in
`kernel/locking/rtmutex.c` on the FUTEX_CMP_REQUEUE_PI proxy-lock rollback
path. The freed `rt_mutex_waiter` lives on the waiter task's own stack; a
later PI chain walk dereferences it. It was fixed upstream in
`5.10.261`, `5.15.212`, `6.1.175`, `6.6.140`, `6.12.86`, `6.18.27`, `>=7.0.4`
(per kernel.org stable changelogs; commit
`3bfdc63936dd4773109b7b8c280c0f3b5ae7d349`). The
EZIF kernel is `6.1.162` — before every fix boundary for the 6.1 line.
Triggering needs only ordinary futex/threading syscalls
(`CONFIG_FUTEX_PI=y`, compiled into Android kernels); no namespaces, no
capabilities. (Introducer `2.6.39` per NVD; CVSS 3.1 7.8 HIGH,
`AV:L/AC:L/PR:L/UI:N`, per the CNA (kernel.org).)

This project already bundles a working exploit for it under the exact-release
gate: `app/src/main/assets/payloads/e1q-S9210ZHU6EZIF/cve-2026-43499-app.so`
(SHA-256 `7b68f38d…22e30cb`, previously pinned at fork commit
`58a677281ab2aa8ae43cfb15619628c2ab2e48b3`; the fork is gone — see Decision 4). The same bytes are shared with
the KernelSU-Next candidate profile; rebuilding the exploit per manager is
out of scope and unnecessary.

### The manager gap

"KernelSU-Next 來管理權限" needs three things the app currently does not
carry for `SM-S9210`:

1. a `ksud` + `<kmi>_kernelsu.ko` pair built from a KernelSU-Next tag;
2. an AndroidManifest/`<queries>` + Manager reference for the Next manager
   package, `com.rifsxd.ksunext`;
3. a way for the resolver to pick the Next candidate instead of the KernelSU
   one on the same device.

All three are addressed below. Nothing about the exploit, the exact-release
gate, the staging format, or the helper binary changes.

### KernelSU-Next state (as of 2026-11-19)

| ref | state |
| --- | --- |
| `v3.3.0` (`3b18216f`) | Next pair was built for `pa3q-S938USQSCCZF9` only; superseded — the committed Next artifacts were rebuilt twice since (`dbe0efa` 2026-09-25, `a31d321` 2026-10-10) |
| `v3.4.0` (`1a879d6a`) | Samsung patch rebased by hand and verified against the tag; **published** via CI on 2026-09-25 (`dbe0efa`), 17 targets; those bytes were later rebuilt onto v3.4.1 |
| `v3.4.1` (`8f902ae`) | conflicts hand-resolved (`docs/KERNELSU-NEXT-3.4.1-UPGRADE.md`, true when written 2026-10-09); **published** via CI on 2026-10-10 (`a31d321`) — the feed serves 17 `…-ksun341` entries, including the `android14-6.1` e1s/e2s/e3q targets; committed `ksud-next-*-kdp` binaries report `3.4.1 (uapi: 5)` |
| `v3.4.2` (`4c706b59`) | Samsung patch carried by merge with nothing to decide (`c6af7dc`, `kernelsu/README.md`); compiled for `android14-6.1` and the other KMIs by CI; generic daemon published as `ksud-next-generic-kdp` (embeds the no-patch-text module per KMI, including `android14-6.1_kernelsu.ko`); a Samsung-patched v3.4.2 pair exists for `pa3q-S938U1UEUDDZJB` (`f510192` 2026-11-10) and is served as `…-ksun…` (`support/targets-v3.json`); **no e1q KernelSU-Next pair is on `main` or served in the feed** — the only e1q Next pair exists on `pair/e1q-S9210ZHU6EZIF-ksun342` (`c1a3f93…`, unmerged) |
| `test` (`931f97d6`) | its tree carries the Samsung delta itself; needs its own compat patch; not a stable base |

Hardware evidence for the Next 3.4.2 path, from this project's own campaign:
on 2026-11-17 the SM-S9180 (`dm3q`, `S9180ZHU8GZIF`, kernel 5.15.197,
Android 17 / One UI 9.0) completed the full chain with
`ksud-next-generic-kdp` (Next 3.4.2, no-patch-text module): module load,
`ksud start: SUCCESS`, `su -c id` → `uid=0(root) … u:r:ksu:s0`,
`su -v` → `3.4.2:KernelSU`
(`../Root-My-Galaxy-Payloads-Next/docs/GZIF-validation-summary-20261117.md` §6 run8 (this document lives in the sibling `Root-My-Galaxy-Payloads-Next` checkout, not in this repo)). The same record shows
both stock (vanilla) and non-Samsung-patched modules **panic at
`init_module()`**, and that only the Samsung-patched no-patch-text builds
load cleanly. The generic daemon was also the fallback after a KMI-name
mismatch killed an exact-KMI Next 3.4.1 pair at the asset lookup stage
(`android13-5.15.189` vs `android13-5.15`).

**Chosen base: `v3.4.2`.** It is the newest release-based tag; its Samsung
patch carried cleanly by merge; and the Next 3.4.2 late-load path
(Samsung-patched, no-patch-text module) has one hardware-validated full
root chain on an SM-S9xxx Android 17 / One UI 9 Samsung device under the
same KDP/RKP/DEFEX constraints this target presents. Note the GZIF chain's
bootstrap primitive was DirtyFrag-class, not CVE-2026-43499 (which its
kernel carried the fix for) — the validation that carries over is the
kernel-module load and manager behavior, which is the part this plan
depends on. The residual gap is narrower and specific: **the exact
e1q-S9210ZHU6EZIF pair has been built by CI (run 44374096436), audited
locally, and committed on `pair/e1q-S9210ZHU6EZIF-ksun342` (`c1a3f93…`) —
not on `main`, not in any feed, not yet pinned in the app, and never run
on this firmware** — it is not true that "no Next artifact has touched a
phone".

### The Samsung delta (why Next is not stock Next)

Both KernelSU and KernelSU-Next cannot write the syscall table on a Samsung
KDP/RKP target (the write is refused, the dispatcher never installs), and
stock live text patching panics at `init_module()` on this device class
(GZIF run2/run3). The Samsung delta in `kernelsu/patches/` therefore:
makes `ksu_patch_text()` a hard `-EOPNOTSUPP` stub under
`CONFIG_KSU_SAMSUNG_NO_PATCH_TEXT=y`, installs kretprobe/kprobe fallback
hooks instead of a syscall-table dispatcher, resolves KDP credential helpers
at runtime, synchronises the DEFEX tuple on every UID transition, stages
`ksud` via `/data/local/tmp/.ksud-stage`, and arms the AVC-spoof kprobe on
the late-load branch (the one a late-loaded module never gets from
upstream's boot-completed event). The Next patch
`KernelSU-Next-v3.4.2-samsung-kdp-rkp-defex.patch` carries the identical
delta and is the file CI applies; the v3.4.1 rebase record
(`docs/KERNELSU-NEXT-3.4.1-UPGRADE.md`) documents the one-time re-anchoring
the delta needed when carried onto v3.4.1 — the v3.4.2 carry itself changed
nothing (`c6af7dc`).

## Decisions (made 2026-11-19, see plan review)

1. **Base ref**: `v3.4.2` (`4c706b59`), `ksu_ref=v3.4.2` for `ksu-build.yml`.
2. **App ships both EZIF candidates** behind the existing candidate opt-in,
   selected by a new persisted Settings preference `candidate_flavour`
   (`kernelsu` default / `kernelsu-next`). The KernelSU v3.3.1 candidate is
   untouched and remains the default.
3. **Build path**: `workflow_dispatch` of `ksu-build.yml` with
   `publish=false`. **No feed entry is created; nothing upstream-facing
   changes.** Artifacts are audited locally against the retained EZIF
   `vmlinux.elf` before any commit. Because the `kernelsu-next` leg of the
   pair job builds the patch-text variant by default, the pair is built on
   a dedicated branch `pair/e1q-S9210ZHU6EZIF-ksun342` (cut from
   `migrate/merge-our-customizations` in
   `samson910022/Root-My-Galaxy-Payloads-Next`) that adds a
   `pair_no_patch_text` input to the workflow, dispatched with
   `pair_no_patch_text=true`. The reasons are recorded in the Samsung
   delta section and the risk table: the EZIF v3.3.1 pair, every
   hardware-validated Samsung build, and the GZIF Next run are all
   no-patch-text; stock/live-text-patching modules panic at
   `init_module()` on this device class. The migration PR
   (`migrate/merge-our-customizations`) is not modified.
4. **Artifact hosting**: the original fork
   `samson910022/Root-My-Galaxy-Payloads` (the pin for the EZIF v3.3.1
   pair, commit `58a67728…`) **no longer exists** (raw 503, repo
   unresolvable under both owner accounts). The Next pair is therefore
   committed to the pair-build branch of the already-active
   `samson910022/Root-My-Galaxy-Payloads-Next`: `pair/e1q-S9210ZHU6EZIF-ksun342`
   (`e32587f` workflow fix, then `c1a3f9332922431764fa00bc306e184a3d8e4d91`
   pair commit). That commit carries, self-contained:
   - `kernelsu/android14-6.1_kernelsu-next-e1q-S9210ZHU6EZIF-kdp.ko`
     (SHA-256 `02f05aab…`),
   - `kernelsu/ksud-next-e1q-S9210ZHU6EZIF-kdp` (SHA-256 `d97d3ba4…`),
   - `artifacts/e1q-S9210ZHU6EZIF-next/cve-2026-43499-app.so`
     (SHA-256 `7b68f38d…22e30cb`; identical bytes to the bundled EZIF
     exploit, vendored here because the fork is gone).
   The app pins both artifacts there by that commit SHA. The committed
   APK assets remain the source of truth; `tools/sync-payloads.sh` only
   refreshes them. On 2026-11-19 the remaining dead refresh pins were
   repointed to byte-identical copies in `Root-My-Galaxy-Payloads-Next`
   (DZG1 exploit → `e3a578d`; shared EZIF/Next exploit → `c1a3f93`; EZIF
   v3.3.1 ksud → `f91d730` on `main`; DZG1 ksud stays on `BuSung-dev`);
   byte-identity was verified by SHA-256, a full `./tools/sync-payloads.sh`
   pass now completes end to end, and `--check` remains fully offline
   (CI runs `--check` only). Payloads-Next `main` still carries a
   *different* exploit rebuild (`efc8b49c…`) and was not substituted for
   any pinned payload.
5. **Manager**: the app documents and queries the **official** KernelSU-Next
   Manager, `KernelSU_Next_v3.4.2_33355-release.apk` (versionCode `33355`),
   package `com.rifsxd.ksunext` (namespace in the v3.4.2 manager sources);
   the APK's SHA-256 `774a5511f7034a4930e881114e202319cbdc34c5aaaa5c7c6b6d2902364e4c58`
   is the asset digest recorded by the release (`gh api …/releases/tags/v3.4.2`,
   `assets[].digest` — the release body itself lists no hash). The spoofed
   build renames its package per release and is not used.
6. **Helper is not rebuilt.** The Samsung-patched `ksud` ignores
   `--package-name` entirely (the restart block was deleted; `_package_name`
   in `late_load.rs`). The compiled-in `me.weishu.kernelsu` string in
   `libcve43499root.so` is inert. It stays; a rebuild is out of scope.
7. **Upstream online feed is untouched.** Only `BUILTIN` profiles change;
   `support/targets-v3.json` is not modified.
8. **Both candidate profiles share the exploit asset** at
   `payloads/e1q-S9210ZHU6EZIF/cve-2026-43499-app.so` (identical bytes,
   digest-pinned). No duplicate payload is committed; the Next profile ships
   only its own `ksud` at
   `payloads/e1q-S9210ZHU6EZIF-next/ksud-next-e1q-S9210ZHU6EZIF-kdp`.
9. **Manifest insertion order**: the Next entry is appended **after** the
   v3.3.1 EZIF entry. Candidate selection is flavour-filtered in *both*
   branches of the resolution (opt-in on and opt-in off), so the order is
   not load-bearing for correctness, only for stable iteration and for the
   opt-in-off error message, which names the profileId it reports; keeping
   the v3.3.1 entry first preserves today's message bytes.

## Design

### Manifest (`app/src/main/assets/builtin-targets.json`)

New `payloads` entry (schema 4, `source: BUILTIN`):

```json
{
  "payloadId": "e1q-S9210ZHU6EZIF-next",
  "displayName": "Galaxy S24 China | Kernel 6.1.162 (S9210ZHU6EZIF) [candidate: KernelSU-Next v3.4.2]",
  "source": "BUILTIN",
  "models": ["SM-S9210"],
  "kernelVersions": ["6.1.162"],
  "kernelReleases": ["6.1.162-android14-11-34343818-abS9210ZHU6EZIF"],
  "requiresFreshP0Session": true,
  "candidateFlavour": "kernelsu-next",
  "exploit":   { "assetPath": "payloads/e1q-S9210ZHU6EZIF/cve-2026-43499-app.so", "sha256": "7b68f38d…", "size": 104128 },
  "kernelsu":  { "assetPath": "payloads/e1q-S9210ZHU6EZIF-next/ksud-next-e1q-S9210ZHU6EZIF-kdp", "sha256": "<fill from audit>", "size": <fill> }
}
```

The entry is appended **after** the existing v3.3.1 EZIF entry in the
`payloads` array (today's order: DZG1 → EZIF → EZIF-Next).

`BuiltinSupportManifest.parse` learns the optional `candidateFlavour` key
(absent ⇒ `kernelsu`; values `kernelsu` / `kernelsu-next`). `TargetProfile`
gains the field; the parser fails closed on unknown values. The shipped
manifest test is updated to expect three targets and to assert the Next
entry's flavour.

### Resolution (`ProfileResolution.kt`)

- `CANDIDATE_PROFILE_IDS` gains `"e1q-S9210ZHU6EZIF-next"`.
- `resolveProfile(snapshot, bundled, remote, allowCandidates = false, candidateFlavour = "kernelsu")`.
- The candidate set is **always** filtered by flavour before it is used
  (`kernelsu-next` ⇒ only candidates whose `candidateFlavour` is
  `kernelsu-next`; `kernelsu` ⇒ flavour `kernelsu`) — in the opt-in-on
  branch and in the opt-in-off `CandidateRequiresOptIn` probe (the failure
  branch selection, so the user-facing `Resolution.Failed` names a profile
  the flavour pref actually selects). Stable profiles are unaffected.
  Default `candidateFlavour` therefore preserves today's resolution for the
  v3.3.1 candidate; what changes is that the Next candidate is reachable
  and deterministic.
- `CandidateRequiresOptIn` still wins over any firmware argument when the
  opt-in is off. The message keeps naming the concrete `profileId`
  (`PayloadRepository.kt` passes it to `repo_candidate_optin_required`);
  no string change.
- The wrong-firmware aggregation with opt-in on lists `DZG1` + `EZIF` as
  before — there is exactly one EZIF release string, shared by both EZIF
  candidate entries, and `flatMap { … }.distinct().sorted()` keeps it once.
- `acquire()`/`requireCompatible()` re-check unchanged.

### Preferences / Settings UI

- `AppPreferences`: new key `candidate_flavour` (file `appearance`),
  default `kernelsu`, `setCandidateFlavour(...)` with validation.
- Settings page: when the candidate opt-in switch is on, an additional card
  offers the flavour choice (KernelSU v3.3.1 / KernelSU-Next v3.4.2) as two
  radio rows; visible only with opt-in on; described as *for validation only*.
- Install confirmation dialog keeps showing `install_confirm_candidate_note`
  for either candidate; the note's wording already covers "candidate profile
opt-in" generically.

### Manager reference (`MainActivity.kt`)

- The global `KERNEL_SU_MANAGER_URL` / `KERNEL_SU_MANAGER_PACKAGE` constants
  become a small pure flavour table:
  - `kernelsu` → `me.weishu.kernelsu`, URL `…/tiann/KernelSU/releases/download/v3.3.0/KernelSU_v3.3.0_32601-release.apk` (unchanged bytes);
  - `kernelsu-next` → `com.rifsxd.ksunext`, URL `…/KernelSU-Next/KernelSU-Next/releases/download/v3.4.2/KernelSU_Next_v3.4.2_33355-release.apk`.
- The "open KernelSU Manager" action reads the same `candidate_flavour`
  preference: with the opt-in on and flavour `kernelsu-next` it queries and
  opens `com.rifsxd.ksunext` (falling back to the release URL); otherwise it
  keeps the existing `me.weishu.kernelsu` behaviour. `AndroidManifest.xml`
  `<queries>` gains `com.rifsxd.ksunext` (tiann stays).

### Assets / tooling / docs

- New asset directory `app/src/main/assets/payloads/e1q-S9210ZHU6EZIF-next/`
  with `ksud-next-e1q-S9210ZHU6EZIF-kdp` (CI artifact, audited).
- `tools/sync-payloads.sh`: new `EZIF_NEXT_*` pin block (Payloads-Next raw base,
  `pair/e1q-S9210ZHU6EZIF-ksun342` immutable commit `c1a3f9332922431764fa00bc306e184a3d8e4d91`, sha256/size),
  manifest-agreement asserts, `verify`/`verify_index`/fetch/install lines;
  `--check` stays offline. In addition, the *dead* refresh pins for the
  existing profiles were repointed to byte-identical copies in
  `Root-My-Galaxy-Payloads-Next` (DZG1 exploit → `e3a578d`; shared EZIF
  exploit → `c1a3f93`; EZIF v3.3.1 ksud → `f91d730` on `main`; DZG1 ksud
  unchanged on `BuSung-dev`) — verified by SHA-256 before the repoint,
  and a full `./tools/sync-payloads.sh` pass now completes end to end.
- `docs/SM-S9210-EZIF-KERNELSU-NEXT-PLAN.md` (this file), plus
  `docs/SM-S9210-EZIF-NEXT-validation-YYYYMMDD.md` written **after** the
  hardware run, not now. A short audit record
  `docs/SM-S9210-EZIF-NEXT-pair-audit-20261119.md` records the CI run id,
  vermagic, `verify_pair.py`, symbol audit results, and receipt values.
- `README.md`: three bundled profiles, Next pairing procedure (install
  `KernelSU_Next_v3.4.2_33355-release.apk`, opt in, pick *KernelSU-Next
  v3.4.2* flavour), provenance table row, and an explicit "not device
  validated" warning for the Next candidate.
- `THIRD_PARTY_NOTICES.md`: confirm the Next `ksud` (GPL-3.0) and its embedded
  module (GPL-2.0) are covered by existing sections; adjust only if the
  licence headers inside the binary prove otherwise.
- Version bump: **not** in this change. The PR is validation-pending;
  `versionCode` stays `18` until hardware signs off.

## Phases and review gates

Each phase ends with: build/verify → review subagent(s) → fix → re-review →
commit. Nothing merges before the final all-green multi-subagent review.

- **Phase 0** — this document. Review: `code-reviewer` (factual/consistency
  pass against the cited sources). Commit.
- **Phase 1** — Payloads-side build & audit (no app changes):
  1. Cut branch `pair/e1q-S9210ZHU6EZIF-ksun342` from
     `migrate/merge-our-customizations` in
     `samson910022/Root-My-Galaxy-Payloads-Next`; add a `pair_no_patch_text`
     boolean input to `ksu-build.yml` (workflow_call + workflow_dispatch);
     when true, the `pair-module` job builds with
     `CONFIG_KSU_SAMSUNG_NO_PATCH_TEXT=y`. Commit and push the branch
     (does not touch the migration PR).
  2. The ship pair was dispatched from that branch (run 44374096436,
     completed success 2026-11-18T18:54:51Z) with `publish=false` and
     `pair_no_patch_text=true`. If a re-build is ever needed:
     `gh workflow run ksu-build.yml --repo samson910022/Root-My-Galaxy-Payloads-Next --ref pair/e1q-S9210ZHU6EZIF-ksun342 -f flavor=kernelsu-next -f ksu_ref=v3.4.2 -f ddk_release=20260828 -f kmi='["android14-6.1"]' -f pair_kmi=android14-6.1 -f target_id=e1q-S9210ZHU6EZIF -f target_release=6.1.162-android14-11-34343818-abS9210ZHU6EZIF -f publish=false -f pair_no_patch_text=true`
  3. `gh run download --repo samson910022/Root-My-Galaxy-Payloads-Next 44374096436 -n pair-module-kernelsu-next-e1q-S9210ZHU6EZIF -n pair-ksud-kernelsu-next-e1q-S9210ZHU6EZIF -D /mnt/240G_SSD/opencode_tmp/pairs/e1q-ksun342` (exact names; no wildcard segment).
  4. Verify `vermagic` is exactly `6.1.162-android14-11-34343818-abS9210ZHU6EZIF SMP preempt mod_unload modversions aarch64`; `tools/verify_pair.py --module <ko> --daemon <ksud> --release …` exits 0; `kernelsu/tools/extract_target_symvers.py` + `audit_module_against_target.py --manual-relocation` vs retained `/mnt/240G_SSD/opencode_tmp/EZIF_work/vmlinux.elf`: zero missing, zero CRC mismatch, zero `__versions` bytes; confirm the build log shows `CONFIG_KSU_SAMSUNG_NO_PATCH_TEXT=y`; daemon version receipts agree with tag (3.4.2 / versionCode derived from tag = 33355).
  5. The bad-news case is handled: the original payloads fork disappeared
     (raw 404/503), so the pair was committed to
     `pair/e1q-S9210ZHU6EZIF-ksun342` on `samson910022/Root-My-Galaxy-Payloads-Next`
     (`c1a3f9332922431764fa00bc306e184a3d8e4d91`; carries the module, the
     daemon and the shared exploit bytes under
     `artifacts/e1q-S9210ZHU6EZIF-next/`). The commit SHA is recorded in the
     app manifest provenance and `tools/sync-payloads.sh` (`syncRepointed`).
  6. Write `docs/SM-S9210-EZIF-NEXT-pair-audit-20261119.md` with all outputs (CI run id, vermagic, no-patch-text config line, verify_pair/audit results, receipts, fork-disappearance note, pin commit SHA).
  Review: `code-reviewer` (audit transcript vs. claimed gates). Commit (S24 repo, doc only).
- **Phase 2a** — app data layer: manifest entry + parser field + resolver
  flavour filter + `AppPreferences` + unit tests (resolution matrix,
  shipped-manifest consistency now 3 targets, `candidateFlavour` parsing
  incl. fail-closed on unknown value). Review: `code-reviewer` +
  `test-engineer`. Commit.
- **Phase 2b** — app UI: flavour picker card (opt-in gated), per-flavour
  Manager constants, `<queries>`, 11 locale strings, install-confirm copy if
  needed. Review: `code-reviewer` + `frontend-ui-engineering` skill check
  (compose conventions/a11y) — no behaviour outside the candidate path.
  Commit.
- **Phase 3** — tooling & docs: `sync-payloads.sh` pin block, README, audit
  record finalization, notices. Review: `code-reviewer` + check `--check`
  offline passes. Commit.
- **Phase 4** — `./gradlew testDebugUnitTest assembleDebug`,
  `tools/build-helper.sh --check`, `tools/sync-payloads.sh --check`;
  then comprehensive final review: `code-reviewer`, `security-auditor`,
  `test-engineer` in parallel; fix → re-review until clean; commit; push
  `feat/s9210-ezif-ksun-candidate`; open PR against `main` with the
  validation-pending status; hold for hardware.

## Risks and mitigations

| risk | mitigation |
| --- | --- |
| Next pair untested on this firmware | candidate opt-in (default off), flavour default `kernelsu`, RAM-only root, PR held for hardware |
| Panic at `init_module()` (live text patching refused by RKP/KDP) | pair is built with `CONFIG_KSU_SAMSUNG_NO_PATCH_TEXT=y` via the dedicated branch (Phase 1); the CI default for `kernelsu-next` pairs is patch-text and must not be used; build log + audit confirm the config line |
| Resolver picks wrong candidate silently | explicit flavour preference; flavour-filtered candidate selection in both branches; deterministic; unit tests for the matrix; default unchanged |
| Wrong firmware runs the payload | exact `kernelReleases` gate (parser-enforced for BUILTIN), unchanged |
| Manager mismatch (tiann vs Next) | per-flavour constants + `<queries>`; docs state exact APK (package `com.rifsxd.ksunext`, versionCode `33355`, asset digest `sha256:774a5511…`); helper unchanged (its `--package-name` is ignored by the Samsung loader) |
| Payload repo disappears / repinned | committed APK assets remain the source of truth; sync `--check` pins index bytes; audit doc records SHAs |
| `__versions`/CRC mismatch panic | module gate: exact vermagic + empty `__versions` + zero CRC mismatch before commit (Phase 1 step 4) |
| Play Integrity / Knox breakage | same as existing candidate; disclaimer in docs and PR |
| Release signature drift (host moves) | pin by commit SHA; the EZIF v3.3.1 precedent's host is now defunct, which is why this pair is pinned on a dedicated branch with byte checks |

## Non-goals

- Replacing or removing the KernelSU v3.3.1 EZIF candidate or the DZG1 profile.
- Publishing anything to the upstream online feed (`support/targets-v3.json`)
  or to Payloads-Next `main`.
- Rebuilding `libcve43499root.so` or changing the exploit bytes.
- Auto-installing or bundling any Manager APK (installs remain manual).
- Bumping app `versionCode`/`versionName` before hardware sign-off.
- Firmware/BL changes of any kind; root remains RAM-only.

## Acceptance and hardware validation

Static (this PR): all unit tests green; `--check` scripts green; CI green;
final multi-agent review clean.

Hardware (separate step, after PR merge or on a validation build): Shizuku
mode install on physical `SM-S9210` `S9210ZHU6EZIF`; capture
`su -c id` (expect `uid=0`), `getenforce` (`Enforcing`, both sides),
`cat /proc/modules` (`kernelsu … Live`), `dmesg | grep handle_setresuid`,
`ksud --version` (expect `3.4.2`, uapi per release), Manager shows
`Working <LKM>` with the Next Manager at versionCode `33355`, one superuser;
`resetprop` read/set/delete pass; reboot ⇒ root gone. Record in
`docs/SM-S9210-EZIF-NEXT-validation-<date>.md`. Until then the profile is a
candidate under opt-in, and the README says so.

## Rollback

Revert the per-phase commits (or simply remove the manifest entry and the
`candidateFlavour` plumbing); the EZIF v3.3.1 candidate and DZG1 remain
untouched throughout. RAM-only root means a reboot clears device state.
