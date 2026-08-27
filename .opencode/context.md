# Project Context — AUTONOMOUS RUN IN PROGRESS

## Mission
Implement docs-v2/ backlog in waves (W-A through M-A.T.R.I.X.1+) until empty or BLOCKED-EXT.

## Current Wave
**W-A: Production hardening** — IN PROGRESS, targeted suite running in background.

## Wave Status

### W-A: Production hardening — code complete, gate running
- INV-1 alias detection extended in `Inv1SourceGuardTest.java` — type-aware scan
  catches `TruthTable`/`DecisionTree`/`Bir`/`BirForm`/`TtForm`/`BddForm`/`ClauseSetForm`
  typed variables followed by `.evaluate(...)`. Name-based regex preserved for
  backward compatibility.
- File-relative whitelist upgraded: prefix entries (`bir/`, `ethics/frozen/`,
  `neuron/`) plus exact-file entries (`compression/TruthTableMinimizer.java`).
- New `BirAvroCodecIT.java`: filesystem round-trip across TT/CLAUSESET/BDD forms
  + 18-input large-payload round-trip.
- New `Inv1SourceGuardHelpersTest.java`: focused unit tests for the alias logic
  helpers (collectTypedReceivers, matchesTypedAliasCall, comment-stripping).
- Targeted run: `./gradlew :matrix-core:test --tests "io.matrix.bir.*" --tests
  "io.matrix.budgeter.*" --tests "io.matrix.runtime.*"` (job_0ab9f6a5).

## Backlog (subsequent waves)
- W-B: ConjugateBudgeter-DP TLA + per-period extension
- W-C: Memory M4 Causal CRDT
- W-D: BRC-Step atomic contract (BrcStep.java already exists, may need jqwik)
- W-E: MCTS/LATS convergence TLA
- W-F: Perception pipeline (SPEC-004/DESIGN-16)
- W-G: Action arena (SPEC-005/DESIGN-17)
- H-H: Consciousness loop (SPEC-006/DESIGN-18)
- H-I: Subconscious consolidator (SPEC-007/DESIGN-19)
- H-J: 4 autonomy impulses
- H-K: Decentralized digests pipeline
- EXP-019+: 3-5 hypotheses
- M-A.T.R.I.X.0: Baseline benchmark vs open-weights
- M-A.T.R.I.X.1+: Sequential distillation

## Constraints / BLOCKED-EXT
- Quantum FR-D3 — no substrate
- FPGA synthesis — no yosys/nextpnr
- Energy/wattmeter — no hardware
- Real domain corpora — deleted 2026-08-25

## Disk
33 GB free (93% used) — within tolerance.

## Notes
- BrcStep.java already exists at matrix-core/src/main/java/io/matrix/reasoning/
- BirAvroCodec.java already exists; just needed IT for filesystem round-trip
- 350 test classes in matrix-core/src/test/