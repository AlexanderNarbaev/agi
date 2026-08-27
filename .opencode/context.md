# Project Context — AUTONOMOUS RUN IN PROGRESS

## Mission
Implement docs-v2/ backlog in waves (W-A through M-A.T.R.I.X.1+) until empty or BLOCKED-EXT.

## Wave Status

| Wave | Status | Notes |
|---|---|---|
| W-A: Production hardening | ✅ done | INV-1 alias detection, BirAvroCodecIT, helper tests |
| W-B: ConjugateBudgeter-DP | ✅ done | TLA+ spec, step() API, EXP ×1.019 vs greedy (2234W/0L/4166T) |
| W-C: Memory M4 Causal CRDT | ✅ done | TLA+ spec, mergeCausal, tombstoneAt, FORMAL-CONTRACTS inlined |
| W-D: BRC-Step atomic contract | ✅ done | TLA+ spec, compose(), jqwik properties |
| W-E: MCTS/LATS convergence | ✅ done | TLA+ spec, convergence tests |
| W-F: Perception pipeline | ✅ done | SensorPacket record, FederatedEncoder dispatch, round-trip tests |
| W-G: Action arena | ✅ done | ActionArena with TaskCell, concurrent arbitration tests |
| H-H: Consciousness loop | ✅ done | 9-stage orchestrator + concurrent tick tests |
| H-I: Subconscious consolidator | ✅ done | TR/REM phases + integrity + k-anon gating |
| H-J: 4 autonomy impulses | ✅ done | FROZEN-gate + budget-bounded fire |
| H-K: Decentralized digests | ✅ done | Anonymizer DP-noise pipeline |

## Remaining Waves
- EXP-019+: 3-5 hypotheses (brain-wave cards)
- M-A.T.R.I.X.0: Baseline benchmark vs open-weights
- M-A.T.R.I.X.1+: Sequential distillation

## Constraints / BLOCKED-EXT
- Quantum FR-D3 — no substrate
- FPGA synthesis — no yosys/nextpnr
- Energy/wattmeter — no hardware
- Real domain corpora — deleted 2026-08-25

## Disk
33 GB free (93% used) — within tolerance.

## EXP Numbers Captured
- ConjugateBudgeter vs greedy: conjugate=1,888,127 vs greedy=1,853,346, ratio=1.019, 100 epochs × 64 tasks. Conjugate wins 2234, ties 4166, never loses.