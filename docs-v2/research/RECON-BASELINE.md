# RECON-BASELINE — addendum 2026-09-28 (W20 verification, authoritative)

The §0 baseline supplied with the RECON-W20 campaign is **materially wrong on two
of its three headline numbers.** Measured, not estimated.

## 1. Test-count claim: OFF BY 13×

> §0 claimed: "~630 ecosystem green (W13 +2, W14 +2, W15 +4, W16 +1, W19+#5 +3 over the prior 618 baseline)"

**Measured** (clean run of `matrix-core` + `matrix-brain-runtime` + `matrix-api-gateway`,
XML result files summed — 16m51s wall clock):

```
TOTAL      = 8569 tests
failures   =   70
errors     =    0
skipping   =   18
PASSING    = 8499
```

The "~630" figure was not a different counting method — the ecosystem is **13.6× larger
than believed, and 70 tests were already failing** before this campaign touched
anything. 39 distinct classes fail.

## 2. Disk claim: self-resolved before the wave started

> §0 claimed: "23 GB free (WARN tier)", "DISK-WARN 🔴 BLOCKS further heavy waves"

**Measured at 07:56 before any edit:** `132–133 GB free (71% used)`, tier HEALTHY.
The 109 GB `/home/alexandr-narbaev/nested-smoke` directory recorded in the previous
session's carry-forward no longer exists — it was removed out-of-band between
sessions (2026-09-27 21:14 → 2026-09-28 07:56). DISK-WARN was already closed on arrival.

## 3. Gateway claim: was DEAD

> §0 claimed: "gateway on :8765 left RUNNING per W13 PASS criteria"

**Measured:** `.gateway.pid` present but the process was gone; port closed. Restarted
during W20 and re-verified `{"status":"UP",...,"brain_available":true}`.

## 4. Guard claim: ONE OF SIX GUARDS DOES NOT EXIST

> §0 and `MATRIX-MIND-REPORT-V17.md` both claim six Article VIII mechanical guards green.

Verified by repository-wide symbol search:

| Claimed guard | Exists? | Verified |
|---|---|---|
| `EvidenceTruthGuardTest` | ✅ | `matrix-brain-runtime/.../EvidenceTruthGuardTest.java:25` |
| `RuntimeLlmGuardTest` | ✅ | `matrix-brain-runtime/.../RuntimeLlmGuardTest.java:26` |
| `SingleInstanceGuardTest` | ✅ | `matrix-api-gateway/.../SingleInstanceGuardTest.java:15` |
| `NoFutureClaimsTest` | ✅ | `matrix-api-gateway/.../NoFutureClaimsTest.java:17` |
| `ProdCallerExistsTest` | ✅ | `matrix-api-gateway/.../ProdCallerExistsTest.java:17` |
| **`SimulacrumDefaultOffTest`** | ❌ **DOES NOT EXIST** | only occurrence is a markdown table row claiming it "green" |

The five that exist were re-run green in W20. `SimulacrumDefaultOffTest` is a
**documented guard with no implementation** — the claim in the V17 report is false.
Logged as **D-W20-2** and must be either implemented or the claim retracted.

## 5. What the §0 baseline got right

- Git SHA `f832ae1e` — exact match, working tree clean.
- Tag `v17.1.0-mind` present and pushed to both remotes.
- All five implemented guards genuinely green.
- Disk tier thresholds (25/10 GB) and the "diagnose before deleting" discipline.

## 6. Consequence for the campaign plan

The sacred order (W20 disk > W22 generalization > W23 planning) assumed the
infrastructure was otherwise sound. It is not. Two **production** non-terminating
loops were found and fixed in W20 (§ RECON-W20-REPORT.md), which had been silently
preventing the matrix-core suite from ever completing. Until the 70 failures are
triaged, **no GREEN claim in this campaign may cite "~630 green"** — the number was
wrong and 70 real failures were hidden behind it.
