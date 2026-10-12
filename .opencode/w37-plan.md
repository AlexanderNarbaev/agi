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

### W37.2 — Dynamics: Propagation & Neuromodulation ⬜ NOT STARTED
- [ ] `NeuroModulatorHub`: Attention, Uncertainty, Reward, Fatigue
- [ ] Fatigue → sleep demand, with a bound that Reward cannot cancel indefinitely
- [ ] Attention → HDC retrieval threshold; **must be measured**, not asserted
- [ ] Modulators must not loosen veto thresholds (DESIGN-68 §4.2)
- [ ] **Verification demanded by the brief:** prove Attention measurably changes retrieval
      precision/recall. HDC thresholds are currently fixed constants, so this needs a real
      experiment, not a wiring change.
- [ ] **Blocker to state honestly:** §4.2 is currently a *proposal* in DESIGN-68, not verified.

### W37.3 — Sleep 2.0: Consolidation & Pruning ⬜ NOT STARTED
- [ ] Replay phase: select high-uncertainty episodes, Hebbian update
- [ ] Pruning phase: simulated annealing, evaluate on **held-out** data (reuse RuleInductionEngine's 80/20 split, do not invent a second one)
- [ ] Exploration phase: Gibbs sampling over Tsetlin clause mutations
- [ ] **PASS criterion from the brief:** network shrinks, accuracy does not drop
- [ ] Resolve Tsetlin hyperparameter target — the brief's ">80%" framing was based on the fixture
      bug and is void. Conjunctions are already 100%; the real gap is parity (see W37.4b).

### W37.4 — Bootstrap: The Child Brain ⬜ NOT STARTED
- [ ] Mass-import Wikidata/ONNX facts into graph structure
- [ ] Auto-connect related concepts by HDC proximity
- [ ] Establish "Professional Baseline": what the system knows at startup

### W37.4b — OPEN DEFECT: parity-k >= 3 ⬜ BLOCKING W37.3
- [ ] **Why it blocks:** W37.3's exploration phase mutates Tsetlin clauses. A learner that cannot
      represent parity will mutate toward whatever it happened to fit, and pruning will then remove
      correct edges for looking unused.
- [x] Hypothesis 1: clause count (64→2048) — ELIMINATED, no effect
- [x] Hypothesis 2: `feedbackT` (16→128) — ELIMINATED, no effect
- [x] Hypothesis 3: `includes()` threshold `>=` vs `>` — ELIMINATED, `>` regresses everything
- [x] Hypothesis 4: remove `typeOneGrowth` (canonical) — ELIMINATED, collapses everything
- [x] Hypothesis 5: canonical dataset-level vote for `p` gating — ELIMINATED, regresses
- [ ] Remaining lead: canonical `p` gate uses a **dataset-level vote damped by s**; this
      implementation uses the per-example vote. Tested and regressed, so the difference is
      subtler than the formula.
- [ ] Untested: `TsetlinAutomaton` uses 2n states with threshold n; canonical uses 2n+1 with
      threshold 0. Verified *not* the direct cause but never compared behaviourally.

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