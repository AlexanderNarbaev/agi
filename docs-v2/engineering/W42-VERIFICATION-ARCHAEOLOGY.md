# W42 — Independent Verification of the Historical-Branch Archaeology

**Auditor:** `archaeologist` (independent check, not the author of the reports under review)
**Date:** 2026-11-02
**Repository:** `/home/alexandr-narbaev/Projects/agi`, branch `develop`, HEAD `6ea07101`
**Reports under review:** `docs-v2/engineering/W42-HISTORICAL-BRANCH-RECOVERY.md` (Report A),
`docs-v2/engineering/W42-RECONNAISSANCE.md` (Report B)
**Method:** read-only. `git show <branch>:<path>`, `git log`, `git merge-base --is-ancestor`,
`git diff --stat`, `git ls-tree`, `git grep` against `develop` and against branch refs.
**No branch was checked out. No file was modified except this one.**

---

## 0. The single most important finding, before the table

Both reports were written against the base **`0296e83e`** ("WAL: The final verification of the
wave", 2026-11-01 21:06:41 +0300). The current `develop` is **`6ea07101`** ("WAL: The false-premise
detector exists, it does not work in Russian", 2026-11-02 16:05:44 +0300) — **176 commits later**.

```
$ git rev-parse HEAD origin/develop
6ea0710197e8440b0ea9c2ed035d368be769104a
6ea0710197e8440b0ea9c2ed035d368be769104a
$ git log --oneline 0296e83e..HEAD | wc -l
176
```

Consequently, several Report A findings are **stale rather than wrong** — they were true at the
base and were superseded by later commits on the very same day. Every such case is marked
**PARTLY TRUE (stale)** and the superseding commit is named. A correction pass must not "fix"
Report A by reversing a conclusion that was correct when written; it must re-date it.

The archaeology itself is **sound**. The branch reading, the defect table and the branch-side
citations are almost all exactly right. The failures cluster in three places: (1) the
**`develop`-side** statements in Report A, which were snapshotted and then overtaken; (2) the
**§4 shadow-class table**, three of whose nine rows are wrong in the owner's favour — the report
asks for an owner decision on classes that are in fact live production engines; and (3) the
**state-of-the-tree** statements in Report B, which are now out of date by 176 commits.

---

## 1. Verdict table — Report A (`W42-HISTORICAL-BRANCH-RECOVERY.md`)

### 1.A Headline and structural claims

| ID | Claim (as written) | Verdict | Evidence |
|---|---|---|---|
| A-01 | §0: *"Every MIND wave was already squash-merged into `develop`"* | **PARTLY TRUE** | The **content** claim is exactly true. For five of the six post-W2 waves the branch-side and develop-side commits have **identical trees**: `7c4a1dda`↔`af10ae0b`, `8db97956`↔`ef433cd9`, `c30ffc23`↔`985941f4`, `8eed7711`↔`2f2d83e2`, `66863858`↔`006ec7c7`, `41ef5d44`↔`3ac66cfe` (all pairs: `git rev-parse <c>^{tree}` equal; e.g. `60d507b1df2e258f788d47d67d797ad4a1559066` for both `af10ae0b` and `7c4a1dda`). But **MIND-W1 and MIND-W2 were not squashed**: `1374c480` and `4d510640` are *the same SHAs* on `develop` and on the branch, and are single-parent commits (`git show --no-patch --format='%P' 1374c480` → one parent `ccd14e5a`) — i.e. fast-forward merges with the SHA preserved. |
| A-02 | §0/§5.1: *"the four 'missing' classes were **deliberately deleted** by a truthfulness pass"* | **TRUE** | The four are `io.matrix.brain.runtime.{ConsolidationCycle,SleepScheduler,GoalTracker,InboxWatcher}`; none is in `git ls-tree -r --name-only develop -- matrix-brain-runtime/src/main/java/io/matrix/brain/runtime/`. The deletion decision is recorded: `docs-v2/research/RECON-W3-PART-A-REPORT.md:11` — *"N-1 \| Dual instances: drafts masquerading as production \| ✅ **CLOSED** — drafts deleted; SingleInstanceGuardTest enforces"*; `:20` — *"RECON-W3 Part A #3 \| Delete draft GoalTracker/ConsolidationCycle \| 11 \| -220"*; commits `6270f453` ("RECON-W3 Part A #1 — N-1/N-2 truthfulness surgery") and `393786c3` ("…#2 — eliminate draft SleepScheduler (N-1)"). |
| A-03 | §0: *"`SingleInstanceGuardTest.java:23-30` asserts the deleted names appear **zero** times in the gateway source"* | **PARTLY TRUE** | The file and the line range exist (`matrix-api-gateway/src/test/java/io/matrix/api/SingleInstanceGuardTest.java`, 87 lines). But **lines 23-30 assert exactly one string**: `countOccurrences(src, "sleepScheduler.triggerNow")` must be `0`; the other three deleted class names are **not mentioned**. Worse, the reflection guard at `:59-70` counts fields of type `io.matrix.brain.runtime.SleepScheduler` and asserts `isLessThanOrEqualTo(1)` — the comment at `:66-67` says *"Once we finish N-1 cleanup, this should be 0. For now we accept 0 or 1"*. So the *absence* of the four classes is **not** mechanically enforced; only one draft call-site string is. |
| A-04 | §0: *"Two more branches are already ancestors of `develop` (empty diff)"* | **TRUE** | `git merge-base --is-ancestor` → true for `analysis-of-project-goal-implementation-5df35` and `backup-main-pre-merge-20260921-154355`; false for all 16 other branch refs tested. |
| A-05 | §5.1/§5.4: *"Do not merge, cherry-pick or restore any of these branches"* | **TRUE** | For every branch in §1 the content is either on `develop` (A-01), deleted on `develop` on purpose (A-02), or an ancestor (A-04). No merge would add anything. |
| A-06 | §5.4: *"Fourteen verified defects in the branch code"* | **PARTLY TRUE** | Thirteen of the fourteen rows (D1–D13) check out exactly as cited (see §1.C). **D14 is not a branch defect** — it is a `develop`-side observation (*"`LicenseManager` on `develop` — 0 production call sites"*). So "14 defects in the branch code" over-counts branch defects by one: **13 branch defects + 1 develop-side observation**. |
| A-07 | §2 heading: *"The four ideas worth recovering — **re-implemented**, not restored"* | **FALSE (as worded)** | The body never asserts they were re-implemented. §2.1–§2.4 each end with *"The smallest experiment that would show whether it helps"* — future tense. §5.3 says *"Four ideas are worth **re-implementing**"*. Only idea #1 has in fact been implemented (§1.B, A-30) and that happened independently, in `d47edb32`, not from the branch. |

### 1.B The four ideas (§2.1–§2.4) — was the gap real, and is it still real?

| ID | Claim | Verdict | Evidence |
|---|---|---|---|
| A-08 | §2.1: *"The branch's `ConsolidationCycle:89` is literally `report.tombstoned = Math.max(0, entries.size() - freq.size());`"* | **TRUE** | `git show feature/mind-w2-persistent-mind:matrix-brain-runtime/src/main/java/io/matrix/brain/runtime/ConsolidationCycle.java` — line 89 is exactly that string. |
| A-09 | §2.1: *"`staleMillis` (`:35`) and `pruneConfidence` (`:34`) are never read"* | **TRUE** | In that file `pruneConfidence` occurs only at `:17` (javadoc), `:34` (field), `:42` (ctor assignment); `staleMillis` only at `:16`, `:35`, `:44`. `run()` (`:54-96`) reads neither. |
| A-10 | §2.1: *"The branch's own test asserts the fabrication: `SleepAndConsolidationIntegrationTest:256` asserts `report.tombstoned == 9` for ten identical entries, and `:122` hand-sets `report.tombstoned = 2`"* | **TRUE** | `:248-250` ten × `log.append("same question","same answer",0.7,true,List.of())`; `:254-256` `assertThat(report.entriesReplayed).isEqualTo(10); assertThat(report.distinctPatterns).isEqualTo(1); assertThat(report.tombstoned).isEqualTo(9);`. `:122` `report.tombstoned = 2;` then `:128` `assertThat(md).contains("Forgotten (tombstoned): 2");`. |
| A-11 | §3 D1: *"the javadoc claims entries are tombstoned 'NOT deleted'"* | **TRUE** | `ConsolidationCycle.java:16-18` — *"…are tombstoned (NOT deleted — see GDPR-compatible behaviour)"*; `:23-24` — *"CONSTITUTION compliance: forgetting is graceful via tombstones (CONSTITUTION Article IV — privacy), not silent overwrite."* |
| A-12 | §2.1: *"beside it, `matrix-core/.../memory/SqliteMemoryBackend:156` `compact()` issues a hard `DELETE FROM memory WHERE level = 0 AND importance < ? AND created_at < ?`. **The memory path deletes**"* | **FALSE (stale)** | True at the report's base — `git show 0296e83e:…/SqliteMemoryBackend.java` line 158 = `"DELETE FROM memory WHERE level = 0 AND importance < ? AND created_at < ?"`. **Not true on `develop` today.** `SqliteMemoryBackend.compact()` is now `:189-202` and issues `UPDATE memory SET forgotten_at = ?, forgotten_reason = ? WHERE level = 0 AND importance < ? AND created_at < ? AND forgotten_at IS NULL`; `tombstones()` is `:213-223` and `restore(String id)` is `:231-239`. Superseded by `d47edb32` "WAL: A forgetting is marked, not performed" (2026-11-01 23:33), i.e. **after** the report's base. The javadoc at `:177-183` now states *"**The entries are not deleted.**"* |
| A-13 | §2.1/§5.3: Rank-1 idea — *"Real tombstones in sleep consolidation"* — is a **gap** to be re-implemented | **FALSE (stale)** | Already implemented on `develop` by `d47edb32`, and covered by `matrix-core/src/test/java/io/matrix/memory/TombstoneCompactionTest.java` (header: *"RECON-W42.5 — a forgetting is marked, not performed"*), which asserts: `backend.compact(0.5,1000)` forgets 1 entry; `backend.tombstones()` has size 1 with non-blank content; `listByLevel(0)` no longer returns it; `backend.restore("m-1")` returns `true` and empties the tombstone list. The report's proposed "smallest experiment" (rows deleted vs tombstoned) **already has a test**. The residual truth is narrower: `io.matrix.privacy.TombstoneService` and the `forgotten_at` marking are still two separate mechanisms (no import of `TombstoneService` in `SqliteMemoryBackend`), and the sleep cycle itself still does not create tombstones. |
| A-14 | §2.2: *"`LicenseManager` exists on `develop` … and has **zero production call sites** — `git grep 'LicenseManager\.' develop -- '*.java'` returns only the test file"* | **TRUE** | `git grep -n "LicenseManager" develop -- '*/src/main/*'` → only `matrix-brain-runtime/src/main/java/io/matrix/brain/runtime/LicenseManager.java:11` (its own declaration). Every other hit is in `AuditChainLicenseFederationTest`. |
| A-15 | §2.2: *"there is currently no gate on any request path"* | **TRUE** | `git grep -n "tier\|Tier" develop -- 'matrix-api-gateway/src/main/*'` → the only tier logic is `inferTierFromEmail` (`MinimalHttpServer:1182-1188`), used once at `:922` in `handleLogin` to stamp a tier into a dev token; nothing reads it back. The 403s at `MinimalHttpServer:390` and `:893` and in `AnalyzeResource:67` / `ExplainResource:62` / `FederateResource:84,137` are FROZEN-axiom and modulator gates, not tier gates. |
| A-16 | §2.2: *"`develop` has a live `io.matrix.billing.CreditLedger` (`BillingModule:31`) that **records spend that nothing gates**"* | **TRUE** | `matrix-billing/src/main/java/io/matrix/billing/BillingModule.java:31` `private static final CreditLedger LEDGER = new CreditLedger();`. No gateway request path references `BillingModule` (the only `ledger()` hit outside `matrix-billing` is `brain/runtime/PersistentMind.java:143`, an unrelated `LearningLedger`). |
| A-17 | §2.3: *"`io.matrix.api.federation.FederationRegistry` is **in-memory only** — `join`/`get`/`size`/`list`/`leave`, no persistence, no heartbeat; its javadoc says 'T-02.5 will replace with sharded cross-cluster discovery'"* | **TRUE** | `matrix-api-gateway/src/main/java/io/matrix/api/federation/FederationRegistry.java:13` — *"T-02.5 will replace with sharded cross-cluster discovery."*; `:21-22` `ConcurrentHashMap` + `CopyOnWriteArrayList`; `:24-50` exactly `join/get/size/list/leave`. No other file-backed peer registry exists in `io/matrix/federation/**`. |
| A-18 | §2.3: the branch CSV registry *"escapes `,`→`%2C` on write (`:120-123`) and **never decodes on read** (`:112`), so a peer name with a comma reloads wrong; `register()` overwrites so the 'idempotent on id' claim is false; and `persist()` uses `synchronized` while the class uses a `ReentrantReadWriteLock`"* | **TRUE** | `feature/mind-w7-final:…/FederationRegistry.java` — `:120-123` `esc()` = `s.replace(",", "%2C")`; `:112-113` `new Peer(parts[0], parts[1], parts[2], Long.parseLong(parts[3]))` (no decode); `:35` javadoc *"idempotent on id"* vs `:39` `new Peer(id, name, endpoint, System.currentTimeMillis())`; `:83` `public synchronized void persist()` against `:28` `private final ReentrantReadWriteLock lock`. |
| A-19 | §2.4: *"`io.matrix.goals.GoalTracker` on `develop` has `Goal(int id, String description, Status status, int priority)` with `Status` = ACTIVE/COMPLETED/ABANDONED — **no progress field, no `IN_PROGRESS`, no timestamps, and an in-memory `ArrayList`**"* | **TRUE** | `matrix-core/src/main/java/io/matrix/goals/GoalTracker.java:16` `public enum Status { ACTIVE, COMPLETED, ABANDONED }`; `:18-19` `public record Goal(int id, String description, Status status, int priority) {}`; `:21` `private final List<Goal> goals = new ArrayList<>();`. |
| A-20 | §2.4: *"The branch's *shape* — a progress fraction and a `PENDING`/`IN_PROGRESS` distinction — is worth adding **to `LearningGoal`**"* | **PARTLY TRUE (stale)** | `LearningGoal` **already has** the `PENDING`/`IN_PROGRESS` distinction (`LearningGoal.java:51` `Status status`; `GoalManager.java:151` and `:181` use `LearningGoal.Status.IN_PROGRESS`), **timestamps** (`LearningGoal.java:59-60` `createdAt` / `updatedAt`) and **progress evidence** (`:52` `progressEvidenceIds`). The only genuinely missing element is a numeric 0..1 **fraction**. The recommendation is therefore partly already implemented. |
| A-21 | §2.4: *"`develop` already has the persistence path this wave built: `io.matrix.knowledge.GoalManager` + `LearningGoal` (schema v8, survives `kill -9`, verified live)"* | **PARTLY TRUE / partly UNVERIFIABLE** | `matrix-core/src/main/java/io/matrix/knowledge/{GoalManager,LearningGoal}.java` exist and `KnowledgeStore` has a `forgotten_at` migration (`KnowledgeStore.java:433`, `:474`). The specific tokens **"schema v8"** and **"survives `kill -9`, verified live"** are **not verifiable from the tree** — no schema-version constant was located, and "verified live" is a claim about a run this audit did not perform. Keep the claim, drop or re-source the two tokens. |

### 1.C The defect table (§3, D1–D14) — every row re-read at the cited line

| ID | Row | Verdict | Evidence at the cited location |
|---|---|---|---|
| A-22 | D1 `ConsolidationCycle:89` — fabricated tombstone metric | **TRUE** | See A-08 / A-11. |
| A-23 | D2 `SleepAndConsolidationIntegrationTest:256, :122` — the test asserts the fabrication | **TRUE** | See A-10. |
| A-24 | D3 `GpuKernelEngine:45-52` — no GPU | **TRUE** | `feature/mind-w5-distillation-factory:…/GpuKernelEngine.java:45-52` — `detectBackend()` returns `Backend.CPU` unconditionally, with the comment *"we ALWAYS return CPU"*. |
| A-25 | D3 `GpuKernelEngine:99-111` — `dispatchAdaptive` only bumps counters | **TRUE** | `:99-111` increments `operationsDispatched` / `operationsOnGpu` and returns; `:107-109` *"Real W6 implementation would call OpenCL/CUDA here. For now we log and continue with the same CPU kernels."* |
| A-26 | D4 `GpuKernelEngine:122-125, :171-173` — a ratio of counters labelled a speedup | **TRUE** | `:122-125` `lastSpeedupRatio = (double) operationsDispatched.get() / (double) operationsOnGpu.get();`; `:171-173` publishes `matrix_gpu_speedup_ratio` whose own HELP text reads *"CPU ns / actual ns (proxy for GPU speedup)"*. |
| A-27 | D5 `ModelToMatrix:133-134` — placeholder Tsetlin count | **TRUE** | `feature/mind-w4-autonomy-goals-inbox:…/ModelToMatrix.java:133` *"// 3) Tsetlin literals: just count unique tokens (placeholder for W6)"*; `:134` `int tsetlinLiterals = seenTokens.size();`. |
| A-28 | D6 `GoalTracker:52` (w3), `CreditLedger:55,70,85` (w6/w7) — non-deterministic runtime ids | **TRUE** | `feature/mind-w3-sleep-consolidation:…/GoalTracker.java:52` `new Goal(UUID.randomUUID().toString(), …)`; `feature/mind-w7-final:…/CreditLedger.java:55`, `:70`, `:85` `new Entry(UUID.randomUUID().toString(), …)`. |
| A-29 | D7 `CreditLedger:52-54` — negative balances allowed | **TRUE** | `:52-54` `int before = balances.getOrDefault(userId,0); int after = before - cost; balances.put(userId, after);` — no floor, no rejection path. |
| A-30 | D8 `FederationRegistry:112, :120-123` — CSV round-trip corruption | **TRUE** | See A-18. |
| A-31 | D9 `FederationRegistry:36-45` vs javadoc `:35` — "idempotent" claim false | **TRUE** | See A-18. |
| A-32 | D10 `LicenseManager:45` — nonsense deny message | **TRUE** | `feature/mind-w7-final:…/LicenseManager.java:45` `return Decision.deny("feature " + feature + " requires tier > " + tier);` |
| A-33 | D11 `LicenseManager:16-22, :42` — overlapping feature flags | **TRUE** | `:16-22` the `Feature` constants carry **both** `free` and `minTier`; `:42` `if (feature.free) return Decision.allow();` — `free` silently wins over `minTier`. |
| A-34 | D12 `ModelToMatrix:143-146`, `CreditLedger:56`, `GoalTracker:34` — wall-clock in runtime paths | **TRUE** | `ModelToMatrix:143` `"run-" + System.currentTimeMillis() + …`, `:146` `…, System.currentTimeMillis(), …`; `CreditLedger:56` `System.currentTimeMillis()`; `GoalTracker:34` `createdAtMillis, System.currentTimeMillis());` |
| A-35 | D12 (cont.) — *"develop later fixed the model-to-matrix case with a content-addressed id (`ModelToMatrix:149` on `develop`)"* | **TRUE** | `develop:…/ModelToMatrix.java:149-150` `ledger.record(new DistillationLedger.Entry("run-" + artifactHash, …`, with the comment at `:142` *"RECON-W32.8: the run id is derived from the artifact hash, not the clock."* |
| A-36 | D13 `AuditChain.tamper(idx, details)` — a test helper shipped in production | **TRUE** | `feature/mind-w7-final:…/AuditChain.java:65-66` — `/** Test helper: tamper with the entry at {@code idx}. */ public synchronized void tamper(long idx, String newDetails) {` |
| A-37 | D14 `LicenseManager` on `develop` — 0 production call sites | **TRUE** | See A-14. |

### 1.D The per-branch table (§1) and the §4 shadow table

| ID | Claim | Verdict | Evidence |
|---|---|---|---|
| A-38 | §1 w1 row: *"`develop`'s version is more evolved (+214/−16) with `PromotionRejectedException` and a richer `ContradictionLevel`"* | **TRUE** | `git diff --stat feature/mind-w1-cognitive-orchestration develop -- …/PersistentHdcStore.java` → *"1 file changed, 214 insertions(+), 16 deletions(-)"*. `PromotionRejectedException` at `PersistentHdcStore.java:103` (thrown at `:89`); `ContradictionLevel` at `:214-226` has six constants. |
| A-39 | §1 w1 row: *"`PersistentHdcStore` **2** (`MinimalHttpServer:147`, `KnowledgeForgeIngest:178`); `MindCycle` **1** (CLI)"* | **TRUE** | `MinimalHttpServer:147` `this.hdcStore = new io.matrix.brain.runtime.PersistentHdcStore(`; `KnowledgeForgeIngest:178` `PersistentHdcStore hdc = new PersistentHdcStore(store, dim);`; the only `new MindCycle` outside the class is `MindRuntimeCli:45`. `PersistentHdcStoreIntegrationTest` and `MindCycleIntegrationTest` are on `develop` → *"both still on `develop`"* holds. |
| A-40 | §1 w1 row: *"The branch content is one wave ahead of its name"* | **TRUE** | `git log --oneline feature/mind-w1-cognitive-orchestration` → tip `ca2e2941` *"MIND-W2 — Persistent Mind + review-fix deltas"* (no PR number), parent `1374c480` = MIND-W1. |
| A-41 | §1 w2 row: *"`EpisodicLog` **1** (evolved, **+151/−12**, with a promotion gate)"* | **PARTLY TRUE** | The promotion gate is real (`develop:…/EpisodicLog.java:206` `public PromotionGate.Decision append(Entry e)`, `:213` `PromotionGate.evaluate`). The **numbers are wrong**: `git diff --shortstat feature/mind-w2-persistent-mind develop -- …/EpisodicLog.java` → *"1 file changed, **170** insertions(+), 12 deletions(-)"* — not +151/−12. |
| A-42 | §1 w2 row: *"`ConsolidationCycle`/`SleepScheduler` **absent** — replaced by `io.matrix.lifecycle.ConsolidationCycle` and `RealSleepScheduler`"* | **TRUE** | Absent from `develop`'s `brain/runtime/`; present as `matrix-core/src/main/java/io/matrix/lifecycle/ConsolidationCycle.java` and `matrix-brain-runtime/src/main/java/io/matrix/brain/runtime/RealSleepScheduler.java`. `RealSleepScheduler` carries the branch's whole surface (`noteActivity`, `triggerNow`, `lastDream`, `cycleCount`, `close`) plus `isHealthy` and `snapshot` — "a strict superset" is a fair characterisation, though the `DreamReport` type differs (`RealSleepScheduler.DreamReport` vs `ConsolidationCycle.DreamReport`), so it is not a drop-in replacement. |
| A-43 | §1 w2/w3 rows: the branch tests `SleepAndConsolidationIntegrationTest` (12) and `AutonomyIntegrationTest` (13) are *"deleted on `develop`"* | **TRUE (with a nuance the report omits)** | Neither `.java` exists under `*/src/test/*` on `develop`. `@Test` counts on the branches are exactly 12 and 13. **Nuance:** the sources were *archived, not destroyed* — `docs-v2/quality/lost-capability-tests/SleepAndConsolidationIntegrationTest.consolidation.java.txt` and `AutonomyIntegrationTest.superseded.java.txt` exist on `develop` (as does `GpuKernelEngineIntegrationTest.runKernel.java.txt`). |
| A-44 | §1 w4 row: *"`develop`'s live path is `TrueDistillationFactory`"; the branch distiller *"reads ONNX **as text** and regexes quoted strings, and its Tsetlin count is a literal `placeholder for W6`"* | **TRUE** | `feature/mind-w4-autonomy-goals-inbox:…/ModelToMatrix.java:150-155` `extractQuotedStrings` = `Pattern.compile("\"([^\"\\\\]{2,80}\"")`; `:133-134` the Tsetlin placeholder. `develop:…/TrueDistillationFactory.java` is the live path. |
| A-45 | §1 w5 row: *"`GpuKernelEngine` … tests — `GpuAccelerationTest`, **byte-identical on `develop`**"* | **TRUE** | `git rev-parse feature/mind-w5-distillation-factory:…/GpuAccelerationTest.java` == `git rev-parse develop:…/GpuAccelerationTest.java` == `dcd0e2a58de2aeda2b52fdd08c2b981b34a37885`. |
| A-46 | §1 w5/w6 row: *"`develop`'s live engine is `RealGpuKernelEngine`"*; §4: *"`RealGpuKernelEngine` (`MinimalHttpServer:277`)"* | **TRUE** | `MinimalHttpServer:277` `this.gpuKernelEngine = new io.matrix.brain.runtime.RealGpuKernelEngine(gpuEnabled);` (with the honest comment at `:274-275` *"gpuEnabled=false by default"*). |
| A-47 | §1 w7 row: *"`AuditChain` **0** … production sites on `develop`"* | **FALSE** | `io.matrix.brain.runtime.AuditChain` is **live**: `MinimalHttpServer:545` `io.matrix.brain.runtime.AuditChain chain = episodicLog.auditChain();`; `brain/runtime/EpisodicLog.java:175, 181, 185, 266`; `brain/runtime/RealAuditService.java:20, 50`; `brain/runtime/ActionAcceptor.java:44, 47`. |
| A-48 | §1 w7 row: *"Tests — yes — **byte-identical on `develop`**"* | **FALSE** | `AuditChainLicenseFederationTest.java` differs (`dd4f3df7457e…` on the branch vs `b227a5667101…` on `develop`; 3 insertions / 1 deletion). More importantly the **class** differs substantially: `git diff --stat feature/mind-w7-final develop -- …/AuditChain.java` → *"47 insertions(+), 45 deletions(-)"* — `develop` rewrote it (W42.9 / D-012) as *"a face of the canonical engine"*, delegating to `io.matrix.audit.HashChainedLog`. Only `LicenseManager`, `FederationRegistry` and `CreditLedger` are byte-identical. |
| A-49 | §4: *"`io.matrix.brain.runtime.AuditChain` \| 0 \| `io.matrix.audit.HashChain` / `HashChainedLog` (`AuditModule:22`)"* | **FALSE** | See A-47. `AuditChain` is not a shadow of `HashChain` — it *is* the runtime's adapter over it and is constructed on the gateway request path. |
| A-50 | §4: *"`io.matrix.brain.runtime.DistillationLedger` \| 0 \| the same package's live path"* | **FALSE** | `DistillationLedger` is used by the live path: `develop:…/TrueDistillationFactory.java:60, 92, 102, 148` (`ledger.record(new DistillationLedger.Entry(…)`) and `develop:…/ModelToMatrix.java:73, 91, 101, 149`. It is not a shadow of the live path — it *is* its ledger. |
| A-51 | §4: *"`io.matrix.cognitive.ErrorDrivenLearner` \| 0 \| **nothing** — and its math needs the four fixes in the cognition audit"* | **FALSE** | It is called in production: `matrix-core/src/main/java/io/matrix/knowledge/KnowledgeWorkbench.java:993` `ErrorDrivenLearner.totalActiveBindings(state)`, `:994` `ErrorDrivenLearner.applyDecay(state, stream, …)`, `:1001`, `:1020`. The live counterpart is not "nothing"; it is the engine behind the knowledge subsystem's decay pass. |
| A-52 | §4: the other six rows — `brain.runtime.CreditLedger`, `.FederationRegistry`, `.LicenseManager`, `.GpuKernelEngine`, `.ModelToMatrix`, `.graph.SleepConsolidator` — all "0 production sites" | **TRUE** | Verified by `git grep` over `*/src/main/*`; the only hits are inside the shadow classes themselves or in same-package siblings that are themselves shadowed. `SleepConsolidator` does exist on `develop` (plus three test files) but has no production caller. |
| A-53 | §5.5: *"**Nine shadow classes on `develop`** are listed in §4 for an owner decision"* | **FALSE** | **Six** are shadows (A-52). `AuditChain`, `DistillationLedger` and `ErrorDrivenLearner` are live (A-47, A-50, A-51). The owner decision list is one-third too large, and it asks the owner to "wire or remove" three engines that are already wired. |
| A-54 | §1 liquid row: *"the proto is byte-identical on `develop` (`6a34901c44677198dddb7c08e36ab2f2fd74c18a`)"* | **TRUE** | `git rev-parse feature/liquid-federation-dynamic-modulators:proto/matrix_federation_v1.proto` == `git rev-parse develop:proto/matrix_federation_v1.proto` == `6a34901c44677198dddb7c08e36ab2f2fd74c18a`. |
| A-55 | §1 liquid row: *"`develop` has **35** `io.matrix.federation.liquid` classes the branch never had"* | **PARTLY TRUE** | The branch has none (`git ls-tree -r --name-only feature/liquid-federation-dynamic-modulators | grep 'io/matrix/federation/liquid/'` → empty). But `develop` has **33** main classes under `matrix-core/src/main/java/io/matrix/federation/liquid/` and **35** *test* classes. The report's "35" is the test count. |
| A-56 | §1 liquid row: *"The WAL split it proposed **was** adopted: `wal/GLOBAL_WAL.md` + `wal/SESSION_WAL.md`"* | **TRUE** | Both files exist on `develop` (`git ls-tree -r --name-only develop | grep '^wal/'`). |
| A-57 | §1 liquid row: *"`DynamicModulatorRegistry` (`IntegratedFederation:41`), `KineticModulator` (4 sites)"* | **PARTLY TRUE** | `IntegratedFederation.java:41` is exactly `this.modulatorRegistry = new DynamicModulatorRegistry();` — the citation is exact. `KineticModulator` is constructed at **8** sites, not 4: `BiochemicalOrchestrator.java:121, 123, 125, 127` and `IntegratedFederation.java:69, 72, 75, 78` (4 per file — the report likely counted one file). |
| A-58 | §2.3: *"`FederationRegistry` (brain-runtime) … its javadoc says 'T-02.5 will replace with sharded cross-cluster discovery'"* (implied as the live counterpart's javadoc) | **TRUE** | See A-17. (Note the file lives at `matrix-api-gateway/src/main/java/io/matrix/api/resource/FederateResource.java` for the consumer; the registry itself is `…/io/matrix/api/federation/FederationRegistry.java`.) |
| A-59 | §1 table, `analysis-of-project-goal-implementation-5df35` and `backup-main-pre-merge-20260921-154355`: *"already an ancestor of `develop`"*, empty diff | **TRUE** | See A-04. |

### 1.E Scope and lifecycle claims

| ID | Claim | Verdict | Evidence |
|---|---|---|---|
| A-60 | §0/§5.1: *"None of the historical branches contains unmerged work."* | **PARTLY TRUE (scope gap)** | True for the **13 branches Report A examined**. The repository holds **10 more** `feature/true-*` branches (`true-w0`, `true-w1`, `true-w2`, `true-w3`, `true-w4`, `true-w5`, `true-w6-w7`, `true-w8`, `true-w10-w11-w12`, `true-w13`) plus `release/v1.0` and `safe-main`, which Report A never mentions. Audit: **every** file touched by the ten `feature/true-*` branches exists on `develop` (0 files absent); the ones that differ are develop-evolved versions (`TrueMindCycle`, `RealSleepScheduler`, `RealInboxWatcher`, `TrueDistillationFactory`, `RealGpuKernelEngine`, `RealAuditService`, `EvalBattery`, `BenchmarkRunner`, `DiskBudget`). So the conclusion **extends**, but Report A did not establish it, and §5.1's *"any of these branches"* does not cover them. |
| A-61 | Task framing: *"every MIND branch is retired"*, vs the fact that `feature/mind-w1-cognitive-orchestration` still exists as a local branch **ahead 5 / behind 4** of origin | **TRUE (the report's silence is the defect)** | `git rev-list --left-right --count origin/feature/mind-w1-cognitive-orchestration...feature/mind-w1-cognitive-orchestration` → `4 5` (behind 4, ahead 5), and `git branch -vv` shows `[origin/…: ahead 5, behind 4]`. **But the two trees are identical**: `git rev-parse origin/…^{tree}` == `git rev-parse feature/…^{tree}` == `f7e9e2de9e67464a8cefd82c3b073c3be94c542c`, and `git diff --stat origin/… feature/…` is empty. The 5-ahead/4-behind is a **history-only divergence** (the same content committed twice, once per remote). Retiring the branch loses nothing. Report A cites the branch at `@ ca2e2941` and never mentions the divergence. |
| A-62 | §4: *"Nothing was deleted. … These are recorded, not removed."* | **TRUE** | No branch was deleted; all 39 local branch refs still exist. `git log --merges --oneline 0296e83e..HEAD` is **empty** — no branch was merged into `develop` either. |

---

## 2. Verdict table — Report B (`W42-RECONNAISSANCE.md`)

| ID | Claim | Verdict | Evidence |
|---|---|---|---|
| B-01 | §1: *"The remote HEAD is `0296e83e` — the W41 baseline the mission named"* and *"`git rev-parse HEAD` → `0296e83e…`"* | **FALSE (stale)** | `git rev-parse HEAD origin/develop` → `6ea0710197e8440b0ea9c2ed035d368be769104a`; `git log --oneline 0296e83e..HEAD \| wc -l` → `176`. |
| B-02 | §1: *"20 local branches, 14 remote"* | **FALSE (stale)** | `git branch --list \| wc -l` → **39**; `git branch -r --list \| wc -l` → **20**. |
| B-03 | §1: *"The working tree carries only the owner's pre-existing modifications"* + the inventory table | **PARTLY TRUE (stale)** | Every listed path is still in the stated state. But the inventory is no longer complete. **Snapshot taken 2026-11-02 16:26 → 16:40 (the tree was being edited concurrently by the owner during this audit):** unlisted modifications `matrix-core/src/main/java/io/matrix/knowledge/{PremiseChecker,KnowledgeRetriever,TextProfiles}.java`; untracked `formal/LearningStateMachine.{tla,cfg}`, `formal/PromotionStage.{tla,cfg}`, `formal/states/`, `matrix-core/src/test/java/io/matrix/knowledge/benchmark/BenchDiagnosticTest.java` <!-- no-link: the test was renamed or removed; the benchmark tests live in matrix-core/src/test/java/io/matrix/benchmark/ -->, `matrix-core/src/main/java/io/matrix/knowledge/EnglishLemma.java`, `matrix-core/src/test/java/io/matrix/knowledge/{EnglishLemmaTest,RussianPremiseTest,TermsDiagTest}.java`, `docs-v2/engineering/W42-VERIFICATION-DOCS.md`. Also the table's typo *"`opencode-swash.json`"* is `.opencode/opencode-swarm.json`. |
| B-04 | §1: *"`git worktree list` — none besides the main checkout"* | **TRUE** | `git worktree list` → a single line, the main checkout. |
| B-05 | §1: *"**all of their work is already on `develop`**, and four of their classes were deliberately deleted by a truthfulness pass"* | **TRUE** | See A-01, A-02. |
| B-06 | §2: *"`matrix-api-gateway` is a hand-written JDK `HttpServer` (`MinimalHttpServer:1564`)"* | **PARTLY TRUE (stale)** | The file is now **1609** lines (`git show develop:…/MinimalHttpServer.java \| wc -l`). It is still a hand-written JDK `HttpServer`, and `build.gradle` still applies no Quarkus plugin (the comment at `:24` says so explicitly). |
| B-07 | §2: *"36 REST endpoints under `/v1/workbench`"* | **FALSE (stale)** | At the base it was exactly 36 (`git show 0296e83e:…/LearningWorkbenchResource.java \| grep -cE '^\s*@(GET\|POST\|PUT\|DELETE)'` → `36`). Today it is **52** (distinct `@Path` values: 52). Added by `416a2b80` and later waves. |
| B-08 | §3 #5: *"**TextFeatures has zero tests in the entire repository**"* | **FALSE (stale)** | `matrix-core/src/test/java/io/matrix/knowledge/TextFeaturesTest.java` exists on `develop`, added by `fa7075f6` ("WAL: The question's demand is checked before the answer is"); it did not exist at `0296e83e` (`git cat-file -e 0296e83e:…` → absent). |
| B-09 | §3: the W42 baseline numbers (accuracy 0.583, abstention 0.400, BM25 overlap 0.593) | **UNVERIFIABLE** | The instrument is still present (`matrix-core/src/main/java/io/matrix/knowledge/benchmark/{BenchmarkRunner,Bm25Index}.java`, `matrix-core/src/main/resources/benchmark/w42-benchmark-v1.json` — still exactly **34 items**, matching the report) but this audit ran **no** benchmark, and `w42-benchmark-v2-candidate.json` now also exists. The numbers are as-recorded, not as-measured-today. |
| B-10 | §5 F1: *"The learning lifecycle is a label, not a state machine"* / *"**None of that exists**"* | **FALSE (stale)** | `matrix-core/src/main/java/io/matrix/knowledge/LearningStateMachine.java` now exists (header: *"The learning lifecycle as a state machine (RECON-W42.1)"*), added by `124d3865`; it defines the seven-stage ladder, legal transitions and recorded regressions. F1 was true at the base and was implemented the same night. |
| B-11 | §5 F2: *"**Nothing decides what to learn next** … There is no `nextAction`, `plan`, `select`, `recommend` or `due` method anywhere in `io.matrix.knowledge`"* | **FALSE (stale)** | `KnowledgeWorkbench.java:655` `public ExerciseSelector.Selection nextExercise(String workspaceId)`; `LearningWorkbenchResource.java:821-822` `@Path("/workspaces/{id}/exercises/next")` → `nextExercise(...)`; `:851` `nextExercises(...)`. Added by `416a2b80` ("WAL: The system chooses what to practise next, and the delay is measured"). |
| B-12 | §5 F3: *"Nothing makes a learner practise … **No exercise generator, no item selector, no 'next item' endpoint**"* | **FALSE (stale)** | Same commit `416a2b80` added `io.matrix.knowledge.ExerciseSelector`, the `/exercises/next` endpoint and `/exercises`. |
| B-13 | §5 F4: *"There is no document ingestion … `KnowledgeImporter.importPack(Path)` and `importPack(String)` have **no production caller**"* | **TRUE** | `git grep -n "importPack" develop -- '*/src/main/*'` returns only self-references inside `KnowledgeImporter.java` (`:54, :61, :73, :80`). |
| B-14 | §5 F5: *"the retrieval is English-only by construction"* | **UNVERIFIABLE** | Out of the archaeology scope; verifying it needs a live retrieval run, which this audit did not perform. The cited code (`TextFeatures:30-38, :141-156`, `ReasoningEngine:290-301, :316-331`, `ContradictionDetector:112,139`) was **not** re-read line-by-line here. |
| B-15 | §6: *"**No branch was merged, restored or deleted.**"* | **TRUE** | `git log --merges --oneline 0296e83e..HEAD` → **empty** (no merge commit reached `develop` since the base); all branch refs still exist; `git branch -vv` shows no deleted-ref traces. |
| B-16 | §2: *"its JAX-RS resources (`AnalyzeResource`, `ExplainResource`) are **never mounted**"* and *"`build.gradle` applies no Quarkus plugin"* | **TRUE** | `git grep -n "AnalyzeResource\|ExplainResource" develop -- 'matrix-api-gateway/src/main/*'` returns nothing outside the two resource files themselves; `build.gradle` has no `plugins { id 'quarkus…' }` (only BOM + dependencies, with the comment at `:24` *"the module applies no Quarkus plugin"*). |
| B-17 | §4.1 D5: *"`ConsciousBrain` has 0 production construction sites"* | **TRUE** | `git grep -n "new ConsciousBrain" develop -- '*/src/main/*'` → no hits. |
| B-18 | §4.1 D7: *"`matrix-core/src/main/resources/application.properties:93` — `atrix.knowledge.assessment`"* — **FIXED** in `b724c79d` | **TRUE** | `git log --oneline -S"atrix.knowledge" develop -- …/application.properties` → `b724c79d` "WAL: The configuration key that was never read". The corrected key is at `application.properties:106` `matrix.knowledge.assessment=knowledge/photosynthesis.assessment.json`. (Note `:93` is now a BIR comment — the line number is stale.) |

---

## 3. CORRECTIONS REQUIRED

Ordered by consequence for a later correction pass. Each item gives the **recommended replacement wording**.

### P1 — `W42-HISTORICAL-BRANCH-RECOVERY.md` §4 and §5.5: three "shadow classes" are live production engines

**Wrong:** §4 lists `io.matrix.brain.runtime.AuditChain` (0 sites), `io.matrix.brain.runtime.DistillationLedger` (0 sites),
`io.matrix.cognitive.ErrorDrivenLearner` (0 sites, "live counterpart: nothing"); §5.5 says "**Nine** shadow classes".

**Correct (replacement for the §4 table):**

> Of the nine classes listed here, **six** are shadow implementations with no production caller on
> `develop`: `io.matrix.brain.runtime.CreditLedger`, `io.matrix.brain.runtime.FederationRegistry`,
> `io.matrix.brain.runtime.LicenseManager`, `io.matrix.brain.runtime.GpuKernelEngine`,
> `io.matrix.brain.runtime.ModelToMatrix` and `io.matrix.brain.runtime.graph.SleepConsolidator`.
> **Three are live and must not be listed as cleanup candidates:**
> `io.matrix.brain.runtime.AuditChain` (constructed on the gateway request path at
> `MinimalHttpServer:545` and held by `EpisodicLog`, `RealAuditService` and `ActionAcceptor`;
> on `develop` it is a *face* of the canonical `io.matrix.audit.HashChainedLog`, rewritten by
> W42.9/D-012), `io.matrix.brain.runtime.DistillationLedger` (the ledger behind the live
> `TrueDistillationFactory` and `ModelToMatrix`) and `io.matrix.cognitive.ErrorDrivenLearner`
> (called by `KnowledgeWorkbench:993-994, 1001, 1020`).

And §5.5 becomes: *"**Six** shadow classes on `develop` are listed in §4 for an owner decision; the
other three rows in that table are live engines and are retained."*

### P2 — §2.1 and §5.3: the Rank-1 idea is already implemented; re-date the recommendation

**Wrong:** *"The memory path deletes; the tombstone path is unwired from it"* (a hard `DELETE` at
`SqliteMemoryBackend:156`), presented as the current state.

**Correct:**

> **Status as of `6ea07101` (2026-11-02).** At the base of this report (`0296e83e`) the memory path
> did hard-delete: `SqliteMemoryBackend.compact()` issued
> `DELETE FROM memory WHERE level = 0 AND importance < ? AND created_at < ?`. Commit `d47edb32`
> ("A forgetting is marked, not performed", 2026-11-01 23:33) replaced it with
> `UPDATE memory SET forgotten_at = ?, forgotten_reason = ? …` and added `tombstones()` and
> `restore(String id)`; `matrix-core/src/test/java/io/matrix/memory/TombstoneCompactionTest.java`
> locks the behaviour in. **The "hard delete" premise of this section no longer holds.**
> What still holds: `io.matrix.privacy.TombstoneService` and the `forgotten_at` marking are two
> separate mechanisms, and the sleep cycle itself still produces no tombstones.

And §5.3 becomes: *"**Three** ideas are worth re-implementing (graceful forgetting in the *sleep*
cycle, a licence/feature gate, a persistent federation peer registry, and a numeric progress
fraction on `LearningGoal`) — of which the memory-layer half of the first was implemented on
2026-11-01, after this report was written."*

### P3 — §2 heading and §5.3: the four ideas were **not** re-implemented

**Correct (replacement heading):**

> ## 2. The four ideas worth recovering — **proposed**, not restored

…and in §5.3: *"Four ideas are worth **re-implementing**; none of them by copying the branch code:
graceful forgetting with real tombstones in the sleep cycle (§2.1 — since implemented for the
memory layer), a licence/feature gate (§2.2), a persistent federation peer registry (§2.3), and a
numeric progress fraction on top of the W41 persistence (§2.4)."*

### P4 — §0: the `SingleInstanceGuardTest` citation overstates what is enforced

**Correct:**

> `matrix-api-gateway/src/test/java/io/matrix/api/SingleInstanceGuardTest.java:23-30` asserts that
> the **draft call** `sleepScheduler.triggerNow` appears **zero** times in `MinimalHttpServer.java`.
> It does **not** enumerate the four deleted class names, and its reflection guard at `:59-70`
> asserts `isLessThanOrEqualTo(1)` draft `SleepScheduler` field — i.e. it *tolerates* one.
> The absence of the four classes is recorded by the truthfulness pass
> (`docs-v2/research/RECON-W3-PART-A-REPORT.md:11`, `:20`), not enforced by a test.

### P5 — §1 w7 row: "byte-identical on `develop`" and "`AuditChain` 0 production sites"

**Correct:**

> | `feature/mind-w7-complete` / `mind-w7-final` @ `41ef5d44`, `mind-w7-credit-ledger` @ `e9f22d3a` | MIND-W7 remainder | `AuditChain`, `LicenseManager`, `FederationRegistry`, `CreditLedger` | yes — `LicenseManager`, `FederationRegistry` and `CreditLedger` are **byte-identical** on `develop`; `AuditChain` was **rewritten** (W42.9/D-012, +47/−45) as an adapter over `io.matrix.audit.HashChainedLog`, and `AuditChainLicenseFederationTest` differs by 3 lines | `AuditChain` **live** (`MinimalHttpServer:545`), `LicenseManager` **0**, `FederationRegistry` (brain-runtime) **0**, `CreditLedger` (brain-runtime) **0** | … |

### P6 — §1: the `EpisodicLog` diff numbers are wrong

**Correct:** replace *"(evolved, +151/−12, with a promotion gate)"* with
*"(evolved, **+170/−12**, with a promotion gate — `EpisodicLog.java:206` `append()` returns a
`PromotionGate.Decision`)"*.

### P7 — §0: "squash-merged" is wrong for MIND-W1 and MIND-W2

**Correct:** replace *"Every MIND wave was already squash-merged into `develop`"* with
*"Every MIND wave is already on `develop`: MIND-W3…W7 were squash-merged (the branch-side and
develop-side commits have **identical trees**), while MIND-W1 (`1374c480`) and MIND-W2
(`4d510640`) were merged with their SHAs preserved."*

### P8 — §1 liquid row: "35 classes" and "4 sites"

**Correct:** *"`develop` has **33** main classes under `io.matrix.federation.liquid` (plus 35 test
classes); the branch has none. `DynamicModulatorRegistry` is constructed at
`IntegratedFederation:41`; `KineticModulator` is constructed at **8** sites
(`BiochemicalOrchestrator:121,123,125,127` and `IntegratedFederation:69,72,75,78`)."*

### P9 — §3: "Fourteen verified defects in the branch code"

**Correct:** *"**Thirteen** verified defects in the branch code, plus **one** `develop`-side
observation (D14), are recorded in §3."*

### P10 — §0/§5: the "retired" claim vs the diverged local branch

Add to §5:

> `feature/mind-w1-cognitive-orchestration` still exists locally and is **ahead 5 / behind 4** of
> `origin/feature/mind-w1-cognitive-orchestration`. The two refs have **identical trees**
> (`f7e9e2de9e67464a8cefd82c3b073c3be94c542c`); the divergence is history-only, so retiring the
> local ref loses no content. The report does not depend on the branch either way.

### P11 — `W42-RECONNAISSANCE.md` §1: the tree state is 176 commits stale

**Correct (replacement for the "repository state, as found" block):**

> **Superseded — re-read against `6ea07101` (2026-11-02).** The remote HEAD is now `6ea07101`,
> not `0296e83e` (176 commits later). There are now **39** local and **20** remote branches, not
> 20 and 14. The working tree additionally carries
> `matrix-core/src/main/java/io/matrix/knowledge/PremiseChecker.java` (modified) and the
> untracked `formal/*.tla`, `formal/*.cfg`, `formal/states/` and
> `matrix-core/src/test/java/io/matrix/knowledge/benchmark/BenchDiagnosticTest.java` <!-- no-link: the test was renamed or removed; the benchmark tests live in matrix-core/src/test/java/io/matrix/benchmark/ -->.
> `git worktree list` still shows only the main checkout.

### P12 — `W42-RECONNAISSANCE.md` §3 #5, §5 F1–F3, §2 (endpoints) and §2 (line number)

**Correct:** F1, F2, F3 and the "TextFeatures has zero tests" finding are **no longer true** and
must be re-dated, not deleted:

> These three findings were measured at base `0296e83e` and were **implemented the same night**:
> `LearningStateMachine` (`124d3865`), `ExerciseSelector` + `/exercises/next` + `/exercises`
> (`416a2b80`), and `TextFeaturesTest` (`fa7075f6`). As of `6ea07101` the learning lifecycle **is**
> a state machine, there **is** a next-item endpoint and a selector, and `TextFeatures` **is**
> covered by tests. The `/v1/workbench` endpoint count moved from 36 to **52**.

### P13 — `W42-RECONNAISSANCE.md` §3: the baseline numbers are now a record, not a measurement

**Correct:** append to the §3 baseline block:

> These numbers were measured at base `0296e83e` on `w42-benchmark-v1.json` (still exactly 34
> items). They have **not** been re-measured against `6ea07101`; `w42-benchmark-v2-candidate.json`
> now also exists. Any plan that depends on 0.583 / 0.400 / 0.593 must re-run the instrument first.

---

## 4. WHAT THE REPORT MISSED

Material findings that are absent from both reports.

1. **The two reports describe a tree that no longer exists.** Both are anchored to `0296e83e`;
   `develop` is 176 commits ahead. This is the root cause of most of §3 above and should be a
   standing header on both documents.
2. **A live engine is scheduled for deletion.** `io.matrix.cognitive.ErrorDrivenLearner` is
   called from `KnowledgeWorkbench:993-994, 1001, 1020` on the live request path, yet §4 lists it
   with "live counterpart: **nothing**" and asks the owner to wire or remove it. The same is true
   of `brain.runtime.AuditChain` (`MinimalHttpServer:545`) and `brain.runtime.DistillationLedger`
   (`TrueDistillationFactory:148`).
3. **`SingleInstanceGuardTest` reads a hard-coded absolute path.**
   `SingleInstanceGuardTest.java:75` — `Path.of("/home/alexandr-narbaev/Projects/agi/matrix-api-gateway/src/main/java/io/matrix/api/MinimalHttpServer.java")`.
   On any other machine (CI, another developer) `readSource()` throws and the guard **fails loudly
   rather than guarding**. This is a defect in the very test the report cites as the mechanism that
   enforces the deletion.
4. **The deleted branch tests were archived, not destroyed.**
   `docs-v2/quality/lost-capability-tests/` on `develop` holds
   `SleepAndConsolidationIntegrationTest.consolidation.java.txt`,
   `AutonomyIntegrationTest.superseded.java.txt` and `GpuKernelEngineIntegrationTest.runKernel.java.txt`.
   The report says the tests were "deleted on `develop`" without noting the archive — which
   matters because §12.4 forbids discarding unsuccessful implementations.
5. **The gateway's billing tier is derived from a substring of the user's email.**
   `MinimalHttpServer.inferTierFromEmail` (`:1182-1188`) returns `ENTERPRISE` if the lowercased
   address contains `"ent"`, `PRO` if it contains `"pro"`, `FREE` otherwise. The tier is stamped into
   a dev token at `:922-930` and consumed by nothing. Any future licence gate (§2.2 of Report A)
   would be reading a tier derived this way — the report recommends a gate without noting that the
   tier source is itself a truthfulness problem of exactly the class RECON-W3 removed.
6. **The live federation registry violates the repo's own determinism rule.**
   `io.matrix.api.federation.FederationRegistry.java:25` `UUID.randomUUID()` and `:26`
   `Instant.now()` — Article III ("Reproducibility", `docs-v2/ecosystem/constitution/index.md:51`)
   forbids wall-clock and unseeded randomness in runtime paths. Report A flags exactly this defect
   class on the *branches* (D6, D12) but not on the `develop`-side class it proposes to keep.
   `MinimalHttpServer:924` does the same for the login token.
7. **`develop`'s `LearningGoal` javadoc already names the branch under audit.**
   `matrix-core/src/main/java/io/matrix/knowledge/LearningGoal.java:23-26` — *"The historical
   `GoalTracker` on the branch `feature/mind-w3-sleep-consolidation` had the shape of a goal but
   declared itself in memory, and the wave that was supposed to persist it never came."* A
   production file already carries the archaeology verdict; Report A's §2.4 conclusion is
   consistent with it but does not cite it.
8. **`develop` already implements the memory-layer half of Report A's Rank-1 idea, with a test.**
   `matrix-core/src/test/java/io/matrix/memory/TombstoneCompactionTest.java` (RECON-W42.5) —
   Report A proposes the experiment as future work.
9. **`develop` already implements Report A's F1/F2/F3 findings.** `LearningStateMachine`
   (`124d3865`), `ExerciseSelector` + `/exercises/next` (`416a2b80`), `TextFeaturesTest`
   (`fa7075f6`), plus `w42-benchmark-v2-candidate.json`. Report B's five "findings that decide
   the plan" were decided by code that now exists.
10. **Ten `feature/true-*` branches and `release/v1.0` / `safe-main` are outside the report's
    scope.** The audit found no unmerged files in the `true-*` branches either (all files exist on
    `develop`), so Report A's conclusion is safe — but it was never established, and a reader of
    §5.1 has no way to know the archaeology covered 13 of 23 non-ancestor branches.
11. **The report's own branch-commit IDs are a moving target.** Report A pins each branch to a
    SHA (`ca2e2941`, `7c4a1dda`, …) but never states that these are *local* refs; the
    `gitverse/*` upstreams for most of them are gone (`[gitverse/…: gone]` in `git branch -vv`).
    A reader who fetches cannot resolve half the report's pins.

---

## 5. UNVERIFIABLE

Items that could not be settled from the tree, with the reason.

| # | Item | Why it could not be settled |
|---|---|---|
| U-1 | Report A §2.4 — *"`LearningGoal` (schema v8, survives `kill -9`, verified live)"* | No schema-version constant for the knowledge store was located in `KnowledgeStore.java`; "schema v8" is not derivable from the source. "Verified live" describes a process run that this audit did not perform and that the repository does not record. |
| U-2 | Report B §3 — the W42 baseline numbers (0.583 / 0.400 / 0.593) | Re-measuring requires building and running `matrix-core`; this audit was read-only and did not run the benchmark. The instrument and the 34-item JSON are present and unchanged, so the numbers are as-recorded, not as-measured-today. |
| U-3 | Report B §5 F5 — "the retrieval is English-only by construction" | Requires a live retrieval run; the cited English-only code paths were not re-read line-by-line in this audit. |
| U-4 | Report A §2.2 / §5 — the behaviour of the branch's `LicenseManager` at runtime (the report says "the gate is static, so a tier cannot be revoked mid-session") | Correct as a code reading (`LicenseManager` is a static utility with only static methods), but the behavioural consequence was not exercised. |
| U-5 | Report A §1 — *"the branch's distiller reads ONNX **as text**"* | The regex-based text reader is confirmed (`ModelToMatrix.extractQuotedStrings`, `:150-155`), but whether the branch's *entry point* ever receives a real ONNX binary was not established. |
| U-6 | Whether any branch was **deleted** in the past | `git branch -vv` shows the present refs; git does not record deleted branch refs, so "nothing was deleted" can only be asserted for the current ref set, not for history. |
| U-7 | Report A §5.2 — *"Two branches are already ancestors of `develop` — **empty diffs**"* | The ancestor relation is verified (`git merge-base --is-ancestor` → true for both). The "empty diff" phrasing was not re-measured as a `git diff` of two refs that are, by definition, the same commit. |

---

## 6. Summary of verdicts

| Report | TRUE | PARTLY TRUE | FALSE | UNVERIFIABLE | rows |
|---|---|---|---|---|---|
| A — `W42-HISTORICAL-BRANCH-RECOVERY.md` | 44 | 9 | 9 | 0 (1 row flagged *partly* UNVERIFIABLE: A-21) | 62 |
| B — `W42-RECONNAISSANCE.md` | 7 | 2 | 7 | 2 (B-09, B-14) | 18 |
| **Total** | **51** | **11** | **16** | **2 (+1 partly flagged)** | **80** |

(The §5 UNVERIFIABLE list adds seven more detail items; U-1, U-2 and U-3 restate A-21, B-09 and
B-14, and U-4…U-7 are sub-claims of rows already counted.)

The dominant pattern: **the branch reading is reliable, the `develop`-side snapshot is not.**
Fourteen of the sixteen FALSE verdicts concern a statement about the current tree or about
`develop`-side classes — never a statement about what a branch contains.

* **Nine of the sixteen were true at `0296e83e` and were overtaken the same night** (2026-11-01
  21:06 → 23:33): A-12, A-13 (`d47edb32`), B-01, B-02, B-07, B-08, B-10 (`124d3865`),
  B-11 and B-12 (`416a2b80`).
* **Seven were never true**: A-07 (the §2 heading), A-47/A-49 (`AuditChain` sites), A-48
  (`AuditChain` "byte-identical"), A-50 (`DistillationLedger` sites), A-51
  (`ErrorDrivenLearner` sites), A-53 ("nine shadow classes").

The seven never-true rows are the ones a correction pass must rewrite rather than re-date.
