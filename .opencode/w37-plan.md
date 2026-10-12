# MISSION: RECON-W37 — The Living Connectome

**Status:** in progress · started 2026-10-12
**Baseline:** `2e61334e` · 9258 tests / 0 failures / 11 modules · gate 0 · FROZEN 0-diff
**Design of record:** `docs-v2/designs/DESIGN-68-living-connectome.md`
**Debt of record:** `docs-v2/quality/KnownFailures.md`

---

## Verified state (measured, not asserted)

| Claim | Value | How measured |
|---|---|---|
| Full suite | 9258 tests / 0 failures, 11 buildable modules | Gradle XML aggregation |
| Tsetlin, 16-bit all-varying | 100% | 16/16 seeds |
| Tsetlin, 16-bit, 15 constant dims | 100% | was 62.5% before `typeOne` fix |
| Tsetlin, parity-3 of 16 | **37% — OPEN** | chance |
| Tsetlin, parity-4 of 16 | **48% — OPEN** | chance |
| Tsetlin, 1024-bit HDC production width | unverified | never measured end-to-end |
| Rule induction sample budget | N <= 20 (K_MAX) | Article II |
| Connectome core | 41 tests, 11 mutants killed | mutation-verified |
| Subagent execution | **non-functional** (11th occurrence) | zero files in 16 min |

---

## Wave status

### W37.1 — Foundation: Graph Core ✅ COMPLETE
- [x] `pack()` collapses 15 of 16 words — FIXED (`d830d3a1`)
- [x] `typeOne` rewards inclusion-state not literal truth — FIXED (`599adb9b`)
- [x] `RuleInductionEngine.predict()` ignored its argument — FIXED (`599adb9b`)
- [x] `DecentralizedDigestPipeline` unseeded RNG on egress — FIXED (`3aa5fdc5`)
- [x] Graph package: NodeId, Activation, CognitiveNode, Synapse, PlasticityRule, SeededRandom, Connectome, HdcNode
- [x] Veto-edge pruning invariant, structural
- [x] Top-K routing, deterministic tie-break by NodeId
- [x] 2-node integration proven against serving path
- [x] DESIGN-68 updated with post-fix measurements

### W37.2 — Dynamics: Propagation & Neuromodulation ✅ CORE COMPLETE (`e48099b7`)
- [x] `NeuroModulatorHub`: Attention, Uncertainty, Reward, Fatigue
- [x] Fatigue → sleep demand; Reward's cancellation is **bounded by a floor**, not a fraction
- [x] Attention → retrieval floor, **measured**: precision 0.92 → 1.00, recall 1.00 → 0.83
- [x] Floor floored at the production value — a modulator can only refuse more, never serve more
- [x] 12 tests, 6 mutants, 6 killed
- [x] **WIRED THROUGH THE SERVING PATH (`8a994f96`)**: `HdcRetrievalStage.retrieve()` takes an
      optional floor; both retrieval bodies and the ignorance trace honour it. The clamp is
      duplicated in the stage so no caller can route around it. `productionDefault()` uses
      attention 0.0 so behaviour is unchanged. Verified: 837 brain-runtime tests / 0 failures.
- [ ] Still NOT called from `TrueMindCycle` — the floor reaches retrieval, but nothing constructs
      a non-default hub in production. Attention is therefore plumbing, not live behaviour.

### W37.3 — Sleep 2.0: Consolidation & Pruning ✅ 2 OF 3 PHASES (`b5c5f441`, `12536ae0`)
- [x] Replay phase (`ReplayPhase`, `2bdc988f`): priority = distance from confident-and-accepted;
      accepted-but-unsure > rejected > confident-correct. Reduced attention 0.5. 14 tests,
      5 mutants, 5 killed
- [x] Pruning phase (`SleepConsolidator`): batch apply, accept only if held-out accuracy holds,
      veto-exempt, reversible via `Connectome.restore()`. Measured: 3 synapses → 1, accuracy 1.0 held
- [x] Exploration phase (`ClauseExplorer`): repairs a real under-trained model 75% → 100% in
      2 proposals. **Local search over the truth table, NOT Gibbs sampling** — javadoc says so
- [x] **PASS criterion met and measured:** network shrinks, accuracy does not drop
- [x] Annealing `step` parameter accepted but deliberately UNUSED, documented rather than faked

### W37.3c — the honest remaining gap
- [ ] `TrueMindCycle` still constructs no `NeuroModulatorHub`, so attention/reward/fatigue never
      reach production. The mechanisms are built and proven; they are not switched on.
- [ ] `ClauseExplorer` is local search over a truth table, not Gibbs sampling over clause automata.
      Documented as such; a real sampler would need a stationary distribution and mixing guarantees.
- [ ] `SleepConsolidator`'s annealing `step` is accepted and unused; a real anneal needs a cooling
      schedule and a restart budget.
- [ ] `TsetlinTrainer.recommendS` is FITTED to measurement, not derived.

### W37.4 — Bootstrap: The Child Brain ⬜ NOT STARTED
- [ ] Mass-import Wikidata/ONNX facts into graph structure
- [ ] Auto-connect related concepts by HDC proximity
- [ ] Establish "Professional Baseline": what the system knows at startup

### W37.4b — parity-k >= 3 ✅ RESOLVED (`4590c986`)

**The cause was `s`, a parameter never swept.** All the earlier elimination was measured at the
default `s=4.0`:

    parity-k   s=4     s=8     s=16    s=32    s=64    (1024 clauses, 200 epochs, 16 seeds)
    parity-3  87.5%  100.0%  100.0% 100.0%  100.0%
    parity-4  48.0%   75.0%  100.0% 100.0%  100.0%
    parity-5  50.8%   51.0%   62.9% 100.0%  100.0%
    parity-6   ~51%   51.7%   51.7%  57.2%  100.0%

`s >= 2^k * 4` gives 100% (min 100%, 16/16 seeds) for parity-3 through parity-6.
Conjunctions are 100% at every `s` from 4 to 32, so raising it is safe.

- [x] `TsetlinTrainer.recommendS(informativeBits)` and `countInformativeBits(inputs, width)`
- [x] 7 tests, 4 mutants, 4 killed
- [ ] The bound is FITTED TO MEASUREMENT, not derived. It coincides with a canonical bound for k-bit
      parity with 2^k clauses; the two were not proven equivalent.

### W37.5 — Release v18.0.0-mind ⬜ NOT STARTED
- [ ] Full 11-module suite
- [ ] Release report distinguishing **wired** from **core-and-tested-only**
- [ ] Must state: parity open, N<=20 on rule induction, no graph in TrueMindCycle

---

## Non-negotiable invariants

1. **FROZEN 0-diff**: CONSTITUTION.md, AGENTS.md, ethics/, .github/, EvalBattery.java
2. **No test may be weakened, skipped or deleted.** A suite that trains everyone to ignore red is
   worse than a red suite.
3. **Article I**: seeded randomness only in the cognitive layer; unseeded RNG is a build failure
   if it can reach a decision.
4. **Article VI**: no unverified numeric claims; every figure traces to a measurement.
5. **Veto edges structurally un-prunable** — enforced inside `prune()`, not by convention.
6. **Mutation-verification for every safety or learning fix.** A test that cannot fail is not a
   test. Three of the graph mutants survived round one and each found a weak test.
7. **LSP in this environment is unreliable.** It repeatedly reports false duplicate-method and
   unresolved-symbol errors in files javac compiles cleanly. Ground truth is javac + grep.

---

## Sequencing rationale

W37.2 is unblocked and independent of the parity defect — neuromodulation drives HDC retrieval,
which works. Do it next.

W37.3 exploration is **blocked** on W37.4b: mutating clauses of a learner that cannot represent
the target produces confident nonsense. Rather than build consolidation on top of it, either fix
parity or scope W37.3 to HDC/BIR plasticity (which does work) and state the Tsetlin limit.

W37.5 ships whatever is verified, with the open items named. A release that says "parity-3 fails,
here is the number" is more useful than one that waits for perfection.