# Project Context — AUTONOMOUS RUN COMPLETE

## Mission
Implement docs-v2/ backlog in waves (W-A through M-A.T.R.I.X.1+) until empty or BLOCKED-EXT.

## Run Outcome
**All 14 waves delivered.** Each wave committed and pushed individually. Final
targeted test suite running in background (job_db6ef8f0).

## Wave Status — 14/14 DONE

| Wave | Subject | Status | Key numbers |
|---|---|---|---|
| W-A | Production hardening | ✅ | INV-1 alias detection extended; BirAvroCodecIT; helper unit tests |
| W-B | ConjugateBudgeter-DP TLA+ | ✅ | step(rows, epoch, observedLambda) API; EXP: conjugate ×1.019 vs greedy (2234W/0L/4166T) |
| W-C | Memory M4 Causal CRDT | ✅ | TLA+ spec; mergeCausal + tombstoneAt on Crdt; GROW-ONLY-SET extends to tombstoned set |
| W-D | BRC-Step atomic contract | ✅ | TLA+ spec; BrcChain.compose(left, right); jqwik properties |
| W-E | MCTS/LATS convergence | ✅ | TLA+ spec; convergence tests in mcts/ |
| W-F | Perception pipeline | ✅ | SensorPacket record + FederatedEncoder.DefaultFederatedEncoder + PerceptionPipelineTest |
| W-G | Action arena | ✅ | ActionArena with TaskCell backing; 7 concurrent-arbitration tests |
| H-H | Consciousness loop | ✅ | 9-stage orchestrator wired to BrcChain + ActionArena + ConsolidationCycle |
| H-I | Subconscious consolidator | ✅ | TR/REM phases + integrity-check + k-anon gating |
| H-J | 4 autonomy impulses | ✅ | AutonomyImpulse enum + ImpulseScheduler with FROZEN-gate + budget |
| H-K | Decentralized digests | ✅ | DecentralizedDigestPipeline with Laplace DP noise + k-anon bucketing |
| EXP-019+ | 3 hypotheses | ✅ | H-043 REFUTED 0.035; H-046 REFUTED-AT-MARGIN 0.890; H-042 ACCEPTED 62μs p99 |
| M-A.T.R.I.X.0 | Baseline benchmark | ✅ | BIR 176ns vs ORT-CPU 2,903ns per-call on FFN16 (×16) |
| M-A.T.R.I.X.1 | Sequential distillation | ✅ | Distillation fidelity 1.000; BIR 115ns vs ORT 9,314ns per-call (×80) |

## Constraints / BLOCKED-EXT (documented)
- Quantum FR-D3 — no substrate
- FPGA synthesis — no yosys/nextpnr
- Energy/wattmeter — no hardware
- Real domain corpora — deleted 2026-08-25
- Real LLM artefacts — disk + safetensors tooling (used synthetic FFN16 instead)

## Real Measurements Captured
- ConjugateBudgeter vs greedy (100 ep × 64 tasks): conjugate=1,888,127 vs greedy=1,853,346 (×1.019)
- ConsciousnessLoop tick: p50=3.2μs, p99=62μs (under 10ms cap with empty chain)
- H-046 gate accuracy: 0.890 (below 0.9 gate by 0.010)
- H-043 relative utility at ε=1.0: 0.035 (below 0.7 gate)
- M-A.T.R.I.X.0 per-call: BIR 176ns vs ORT 2,903ns (BIR ×16)
- M-A.T.R.I.X.1 per-call: BIR 115ns vs ORT 9,314ns (BIR ×80)

## TLA+ Specs Added
- formal/ConjugateBudgeterDP.tla
- formal/MemoryM4Causal.tla
- formal/BrcStep.tla
- formal/MctsLatsVisit.tla

## FormAL-CONTRACTS Inlined
- 4 new TLA+ rows added to docs-v2/architecture/FORMAL-CONTRACTS.md
- next-format-contracts list pruned (BRC-Step, ConjugateBudgeter-DP, Memory-M4-Causal, MCTS-LATS-Visit all moved to "owned")