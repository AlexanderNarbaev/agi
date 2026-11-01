# W42 — Historical Branch Recovery

**RECON-W42.0** · 2026-11-01 · owner directive Part II §3.2
**Method:** `git log`, `git ls-tree`, `git diff <branch>...develop`, and
reading the classes on each branch. Nothing was merged, restored, or deleted.

---

## 0. The headline

**None of the historical branches contains unmerged work.** Every MIND wave
was already squash-merged into `develop`; the four classes that appear to be
"missing" were **deliberately deleted** by a truthfulness pass, and their
absence is mechanically enforced by a test.

```
$ git log --oneline develop | grep -iE 'MIND-W[0-9]'
1374c480  MIND-W1 #16 …      4d510640  MIND-W2 #18 …      af10ae0b  MIND-W3 #20 …
ef433cd9  MIND-W4 #22 …      985941f4  MIND-W5 #23 …      2f2d83e2  MIND-W6 #25 …
006ec7c7  MIND-W7 partial    3ac66cfe  MIND-W7 remainder …
```

The deletions are documented, not accidental —
`docs-v2/research/RECON-W3-PART-A-REPORT.md` records *"N-1 | Dual instances:
drafts masquerading as production | ✅ CLOSED — drafts deleted"*, and
`matrix-api-gateway/src/test/java/io/matrix/api/SingleInstanceGuardTest.java:23-30`
asserts the deleted names appear **zero** times in the gateway source.

**So this is a task about reading a deletion decision, not about porting
missing code.** The mission's §3.2 asks for a recommendation per branch:
restore, adapt, replace, or permanently retire. For most of them the honest
answer is *permanently retire the branch* and *adapt one idea at most*.

Two more branches are already ancestors of `develop`
(`git merge-base --is-ancestor` → true, empty diff):
`analysis-of-project-goal-implementation-5df35` and
`backup-main-pre-merge-20260921-154355`.

---

## 1. Per-branch verdict

| Branch | Purpose (from its commits) | Key classes | Tests | Production sites on `develop` | Verdict |
|---|---|---|---|---|---|
| `feature/mind-w1-cognitive-orchestration` @ `ca2e2941` | MIND-W1 10-stage cognitive orchestration + MIND-W2 persistent mind. **The branch content is one wave ahead of its name.** | `PersistentHdcStore` (NDJSON, atomic writes, FNV-1a, write-time contradiction detection), the HDC/BIR/Signal stages | yes, both still on `develop` | `PersistentHdcStore` **2** (`MinimalHttpServer:147`, `KnowledgeForgeIngest:178`); `MindCycle` 1 (CLI) | **RETIRE the branch / KEEP the class** — `develop`'s version is more evolved (+214/−16) with `PromotionRejectedException` and a richer `ContradictionLevel` |
| `feature/mind-w2-persistent-mind` @ `7c4a1dda` | MIND-W3 "Sleep & Consolidation Engine": episodic log → consolidation → dream report + idle sleep scheduler | `EpisodicLog`, `ConsolidationCycle` (brain-runtime), `SleepScheduler` | yes — `SleepAndConsolidationIntegrationTest` (12), **deleted on `develop`** | `EpisodicLog` **1** (evolved, +151/−12, with a promotion gate); `ConsolidationCycle`/`SleepScheduler` **absent** — replaced by `io.matrix.lifecycle.ConsolidationCycle` and `RealSleepScheduler` | **PERMANENTLY RETIRE** — the drafts were deleted on purpose and `RealSleepScheduler` is a strict superset. **The tombstone idea is worth re-implementing — but not from this branch (§2).** |
| `feature/mind-w3-sleep-consolidation` @ `8db97956` | MIND-W4 "Autonomy, Goals & Stimuli": named goals with progress; inbox watcher | `GoalTracker` (brain-runtime), `InboxWatcher` | yes — `AutonomyIntegrationTest` (13), **deleted on `develop`** | `GoalTracker` **absent** — `develop` has the *weaker* `io.matrix.goals.GoalTracker` (no progress, no `IN_PROGRESS`, no timestamps, **in-memory only**); `InboxWatcher` absent — replaced by `RealInboxWatcher` | **PERMANENTLY RETIRE the branch.** The *shape* is worth adapting onto `develop`'s `io.matrix.knowledge.GoalManager` (§2.4) |
| `feature/mind-w4-autonomy-goals-inbox` @ `c30ffc23` | MIND-W5 "Distillation Factory": ONNX/dataset/text → matrix-native structures | `DistillationLedger`, `ModelToMatrix` | yes, both still on `develop` | **0 each** — `develop`'s live path is `TrueDistillationFactory` | **ADAPT nothing; KEEP `develop`'s.** The branch's distiller reads ONNX **as text** and regexes quoted strings, and its Tsetlin count is a literal `"placeholder for W6"`. Restoring it would be a regression. |
| `feature/mind-w5-distillation-factory` @ `8eed7711` | MIND-W6 "GPU Acceleration of MATRIX-native math" | `GpuKernelEngine` | yes — `GpuAccelerationTest`, byte-identical on `develop` | **0** — `develop`'s live engine is `RealGpuKernelEngine` | **REPLACE — the GPU claim is false on both.** `detectBackend()` **always returns CPU** (`:45-52`), `dispatchAdaptive` only bumps counters (`:99-111`), and `lastSpeedupRatio = dispatched/gpuOps` is published as `matrix_gpu_speedup_ratio` (`:122-125`). |
| `feature/mind-w6-gpu-acceleration` @ `6f6762b7`, `feature/mind-w6-gpu-final` @ `66863858` | the same GPU wave, isolated | as above | as above | 0 | **PERMANENTLY RETIRE** — a strict subset of the w5 branch |
| `feature/mind-w7-complete` / `mind-w7-final` @ `41ef5d44`, `mind-w7-credit-ledger` @ `e9f22d3a` | MIND-W7 remainder: hash-chained audit, licence-tier gating, file-backed federation peer registry | `AuditChain`, `LicenseManager`, `FederationRegistry`, `CreditLedger` | yes — byte-identical on `develop` | `AuditChain` **0**, `LicenseManager` **0**, `FederationRegistry` (brain-runtime) **0**, `CreditLedger` (brain-runtime) **0**; the live equivalents are `io.matrix.audit.HashChain`, `io.matrix.billing.CreditLedger`, `io.matrix.api.federation.FederationRegistry` | **ADAPT ONE PIECE (`LicenseManager`), RETIRE the rest** (§2.2) |
| `feature/liquid-federation-dynamic-modulators` @ `1ec32018` | W347–W351 Liquid Federation foundation: protobuf, SPEC-013/014, DESIGN-67, WAL-split proposal | **no Java** — the proto is byte-identical on `develop` (`6a34901c44677198dddb7c08e36ab2f2fd74c18a`) | **no new tests** — the branch tests nothing it adds | the registry is live: `DynamicModulatorRegistry` (`IntegratedFederation:41`), `KineticModulator` (4 sites) | **PERMANENTLY RETIRE the branch; the ideas are already implemented** — `develop` has 35 `io.matrix.federation.liquid` classes the branch never had. The WAL split it proposed **was** adopted: `wal/GLOBAL_WAL.md` + `wal/SESSION_WAL.md` |
| `analysis-of-project-goal-implementation-5df35` @ `003bc14d` | a history-alignment merge, not a feature | none — empty diff | n/a | n/a | **PERMANENTLY RETIRE** — already an ancestor of `develop` |
| `backup-main-pre-merge-20260921-154355` @ `24023445` | a pre-merge snapshot of `main` | none — empty diff | n/a | n/a | **PERMANENTLY RETIRE** — already an ancestor of `develop` |

---

## 2. The four ideas worth recovering — re-implemented, not restored

Only two of the candidates are worth recovering, and in both cases the
**branch code must not be copied**: it is a draft, and in one case it
fabricates the very number it reports.

### 2.1 (Rank 1) Real tombstones in sleep consolidation

**The gap.** `develop` has a genuine tombstone subsystem —
`io.matrix.privacy.TombstoneService` with five storage backends, live at
`AuditLogResource:59`, `BotEthicsResource:69`, `CascadeResource:52` — and
beside it, `matrix-core/.../memory/SqliteMemoryBackend:156` `compact()`
issues a hard `DELETE FROM memory WHERE level = 0 AND importance < ? AND
created_at < ?`. **The memory path deletes; the tombstone path is unwired
from it.** The mission's §9.1 requires forgetting to be deliberate and
reversible, and §4.5 requires tombstone-based retention.

**Why the branch cannot be restored.** The branch's
`ConsolidationCycle:89` is literally
`report.tombstoned = Math.max(0, entries.size() - freq.size());` — a count of
duplicate lines, presented as tombstones. `staleMillis` (`:35`) and
`pruneConfidence` (`:34`) are never read. The branch's own test asserts the
fabrication: `SleepAndConsolidationIntegrationTest:256` asserts
`report.tombstoned == 9` for ten identical entries, and `:122` hand-sets
`report.tombstoned = 2` before rendering it. **Copying this re-introduces
exactly the defect class RECON-W3 removed: a number asserting a capability
the code does not have.**

**The smallest experiment that would show whether it helps.**
1. Write 50 episodic entries older than the threshold.
2. Run `RealSleepScheduler.triggerNow()` (`RealSleepScheduler:140`).
3. Assert `TombstoneService` holds ≥ 1 tombstone **and** the memory row count
   is **unchanged** — tombstoned, not deleted.

Metric: *tombstones per sleep cycle*, and *rows deleted vs tombstoned* under
the current `compact()` path. If the first is 0, the feature is worthless.

### 2.2 (Rank 2) A licence/feature gate at the gateway

**The gap.** `LicenseManager` exists on `develop` (byte-identical to the
branch) and has **zero production call sites** — `git grep 'LicenseManager\.' develop -- '*.java'`
returns only the test file. Meanwhile `develop` has a live
`io.matrix.billing.CreditLedger` (`BillingModule:31`) that **records spend
that nothing gates**. The mission's §4.8 and the W42.6 product shape both
imply an operation budget; there is currently no gate on any request path.

**Why the branch cannot be copied as-is.** The deny reason is
`"feature " + feature + " requires tier > " + tier` (`:45`) — it compares a
feature to a tier. `Feature.free` and `Feature.minTier` overlap, and `free`
silently wins. The gate is static, so a tier cannot be revoked mid-session.

**The smallest experiment.** An endpoint-level test: `POST /v1/analyze`
under a FREE tier and under a PRO tier; assert 200 vs 403, and exactly one
`CreditLedger` entry of `Operation.ANALYZE` with cost 1. Metric: *request
paths consulting a tier decision* (today 0) and *credits charged per
request* (today 0 in the mind path).

### 2.3 (Rank 3) A federation peer registry that survives a restart

**The gap.** `io.matrix.api.federation.FederationRegistry` is **in-memory
only** — `join`/`get`/`size`/`list`/`leave`, no persistence, no heartbeat;
its javadoc says "T-02.5 will replace with sharded cross-cluster discovery".
Peer identity is lost on restart and there is no liveness signal. The
branch's file-backed CSV registry has the right shape but is defective: it
escapes `,`→`%2C` on write (`:120-123`) and **never decodes on read**
(`:112`), so a peer name with a comma reloads wrong; `register()` overwrites
so the "idempotent on id" claim is false; and `persist()` uses `synchronized`
while the class uses a `ReentrantReadWriteLock`.

**The smallest experiment.** Round-trip test: write three peers (one with a
comma in its name) to a file, construct a second registry over the same path,
assert a byte-identical reload including `lastSeenMillis`. Metric: *peers
surviving a gateway restart* (today 0).

### 2.4 (Rank 4) Goal progress and persistence — the shape, not the code

**The gap.** `io.matrix.goals.GoalTracker` on `develop` has
`Goal(int id, String description, Status status, int priority)` with
`Status` = ACTIVE/COMPLETED/ABANDONED — **no progress field, no
`IN_PROGRESS`, no timestamps, and an in-memory `ArrayList`**, so its goals
do not survive a restart. The branch's `GoalTracker` has the right shape
(progress 0..1, PENDING→IN_PROGRESS→COMPLETED/ABANDONED, timestamps) but
**cannot be copied**: it uses `UUID.randomUUID()` for ids (violating
CONSTITUTION Art. III and `AGENTS.md`'s ban on Random in runtime paths) and
calls `System.currentTimeMillis()` inside record accessors.

**Where it should go.** `develop` already has the persistence path this
wave built: `io.matrix.knowledge.GoalManager` + `LearningGoal` (schema v8,
survives `kill -9`, verified live). The branch's *shape* — a progress
fraction and a `PENDING`/`IN_PROGRESS` distinction — is worth adding **to
`LearningGoal`**, where the deterministic ids and the persistence already
are.

**The smallest experiment.** A test in `io/matrix/knowledge`: create a goal
with progress 0.4, restart the store from the same file, assert it reloads
with `progress == 0.4` and `status == IN_PROGRESS`. Metric: *goal survival
across restart* (already 1 for the W41 goal) and *goals with non-terminal
progress* (today 0).

---

## 3. The branch defects, verified by reading

Recorded so that a future wave does not rediscover them as if they were
features.

| # | Defect | Where (on the branch) | Why it matters |
|---|---|---|---|
| D1 | **Fabricated tombstone metric** | `ConsolidationCycle:89` (w2) | the javadoc claims entries are tombstoned "NOT deleted"; the number is a count of duplicates |
| D2 | **The test asserts the fabrication** | `SleepAndConsolidationIntegrationTest:256, :122` (w2) | a green suite proving nothing |
| D3 | **No GPU** | `GpuKernelEngine:45-52, :99-111` (w5/w6) | the commit advertises "bit-exact CPU==GPU results" for a GPU that does not exist |
| D4 | **A ratio of counters labelled a speedup** | `GpuKernelEngine:122-125, :171-173` (w5/w6) | `matrix_gpu_speedup_ratio` = `dispatched/gpuOps` |
| D5 | **A placeholder Tsetlin count** | `ModelToMatrix:133-134` (w4/w5) | the ledger reports a Tsetlin count no automaton produced |
| D6 | **Non-deterministic runtime ids** | `GoalTracker:52` (w3), `CreditLedger:55,70,85` (w6/w7) | violates CONSTITUTION Art. III |
| D7 | **Negative balances allowed** | `CreditLedger:52-54` (w6/w7) | arbitrarily negative balance, no rejection path |
| D8 | **CSV round-trip corruption** | `FederationRegistry:112, :120-123` (w7) | peer fields containing a comma reload wrong |
| D9 | **"Idempotent" claim false** | `FederationRegistry:36-45` vs javadoc `:35` (w7) | re-registering changes `lastSeenMillis` |
| D10 | **Nonsense deny message** | `LicenseManager:45` (w7) | compares a feature to a tier |
| D11 | **Overlapping feature flags** | `LicenseManager:16-22, :42` (w7) | two sources of truth for one decision |
| D12 | **Wall-clock in runtime paths** | `ModelToMatrix:143-146`, `CreditLedger:56`, `GoalTracker:34` | breaks reproducibility — `develop` later fixed the model-to-matrix case with a content-addressed id (`ModelToMatrix:149` on `develop`) |
| D13 | **A test helper shipped in production** | `AuditChain.tamper(idx, details)` (w7) | any caller can corrupt the chain it protects |
| D14 | **A gate that gates nothing** | `LicenseManager` on `develop` — 0 production call sites | a feature that exists and denies nothing |

---

## 4. The cleanup candidates that are on `develop` itself

These are not branch restores. They are shadow implementations of live
engines, on `develop`, right now — the same defect class RECON-W3 Part A
closed for the drafts. Each is a decision for the owner, not for the
coordinator: wire it, or remove it from the product narrative.

| Class | Production sites | Live counterpart |
|---|---|---|
| `io.matrix.brain.runtime.CreditLedger` | 0 | `io.matrix.billing.CreditLedger` (`BillingModule:31`) |
| `io.matrix.brain.runtime.AuditChain` | 0 | `io.matrix.audit.HashChain` / `HashChainedLog` (`AuditModule:22`) |
| `io.matrix.brain.runtime.FederationRegistry` | 0 | `io.matrix.api.federation.FederationRegistry` (`FederateResource:34`) — different semantics, no persistence |
| `io.matrix.brain.runtime.LicenseManager` | 0 | nothing |
| `io.matrix.brain.runtime.GpuKernelEngine` | 0 | `io.matrix.brain.runtime.RealGpuKernelEngine` (`MinimalHttpServer:277`) |
| `io.matrix.brain.runtime.ModelToMatrix` | 0 | `TrueDistillationFactory` / `DistillationPipeline` |
| `io.matrix.brain.runtime.DistillationLedger` | 0 | the same package's live path |
| `io.matrix.brain.runtime.graph.SleepConsolidator` | 0 | `RealSleepScheduler` / `SleepCycle` |
| `io.matrix.cognitive.ErrorDrivenLearner` | 0 | nothing — and its math needs the four fixes in the cognition audit |

**Nothing was deleted.** The mission's §16.1 reserves destructive Git
operations for the owner, and §12.4 forbids discarding unsuccessful
implementations. These are recorded, not removed.

---

## 5. The bottom line

1. **Do not merge, cherry-pick or restore any of these branches.** Every MIND
   wave is already on `develop`; the four "missing" classes were deleted on
   purpose and their absence is test-enforced.
2. **Two branches are already ancestors of `develop`** — empty diffs.
3. **Four ideas are worth re-implementing**, none of them by copying the
   branch code: graceful forgetting with real tombstones (§2.1), a
   licence/feature gate (§2.2), a persistent federation peer registry
   (§2.3), and goal progress on top of the W41 persistence (§2.4).
4. **Fourteen verified defects in the branch code** are recorded in §3 so
   that a future wave cannot mistake a draft for a capability.
5. **Nine shadow classes on `develop`** are listed in §4 for an owner
   decision.
