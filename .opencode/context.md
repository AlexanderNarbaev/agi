# Project Context — RUN 2 COMPLETE (2026-08-28)

## Mission
Second autonomous run after disk + GPU became available. Continue
the implementation: GPU benchmarks, real LLM distillation, EXP-019+
batch 2 and 3, retuning, EXP-002/003 production verdict.

## Run Outcome
**All planned tasks for this session delivered.** Each wave committed
and pushed individually. Working tree clean. Final targeted test
suite: BUILD SUCCESSFUL in 3m36s across 12 packages.

## Wave Status — second run

| Wave | Subject | Status | Key numbers |
|---|---|---|---|
| Recovery | git repo .git/objects missing → re-init from origin/main | ✅ | history preserved |
| M-A.T.R.I.X.2 | tiny-distilbert distillation | PARTIAL | fidelity 0.500, GPU ×0.72 |
| M-A.T.R.I.X.3 | **real distilbert-base-sst2** | ✅ | fidelity **1.000**, GPU **×11.26** |
| M-A.T.R.I.X.4 | **GPT-2 (124M)** | ✅ | GPU **×25.55** vs CPU per-call |
| H-039 | Curiosity PE > θ_c | ✅ | precision 0.970, recall 100% |
| H-040 | M2→M3 promotion | MIXED | precision 1.000, recall 0.038 (gate cap) |
| H-041 | Offline dream-replay | REFUTED | ΔF1 = 0 (k=2 unreachable) |
| H-044 | Saliency ECE | ✅ | ECE = 0.050 |
| H-045 | Freeze recovery | REFUTED at names | recovery OK, filter permissive |
| H-047 | Cross-pillar latency | ✅ | p99 = 0.063ms (1024× under 65ms cap) |
| H-048 | Behavior stability | ✅ | 1 unique decision over 1000 ticks |
| H-049 | Share-impulse | ✅ | precision 0.952 |
| H-050 | Arousal monotonicity | ✅ | monotone |
| **H-043 retuning** | (k=5, ε=5.0) | ✅ | utility **0.913** |
| **H-046 retuning** | allow-list code change | ✅ | accuracy **1.000** |
| **EXP-002/003 production** | qa_pairs.json restored | ✅ | GA ×5.71, +8.75pp, ×7569 |

## Constraints / BLOCKED-EXT (still)
- Quantum FR-D3 — no substrate
- FPGA synthesis — no yosys/nextpnr
- Energy/wattmeter — no hardware
- Domain corpora — RESTORED this run for EXP-002/003 (gitignored per policy)

## Real Measurements Captured — second run
- DistilBERT GPU ×11.26 vs CPU per-call
- DistilBERT fidelity 1.000 on synthetic corpus
- GPT-2 GPU ×25.55 vs CPU per-call
- GA vs Tsetlin on real corpus: ×5.71, +8.75pp, ×7569 more compact
- H-043 retuned: utility 0.913 at (k=5, ε=5.0)
- H-046 retuned: accuracy 1.000 via impulse allow-list (code change)
- H-039: precision 0.970, recall 100%
- H-044: ECE 0.050
- H-047: p99 latency 0.063 ms (×1024 under cap)
- H-048: 1 unique decision over 1000 ticks
- H-049: precision 0.952

## Disk / Hardware
- 102 GB free disk (cleared by user between runs)
- CUDA + RTX 5070 Ti functional
- Python + transformers 5.12.1 + safetensors installed

## Commits added this session
- f9fc266 (later rebased + cherry-picked into 4450855): M-A.T.R.I.X.2
- 54a12d3: EXP-039/041/045/047 + 4 test harnesses + 4 reports
- fdf1064: EXP-040/044/048/050 + allow-list plan
- 4d1bf8b: EXP-049 + report
- 50229eb: H-043 + H-046 retuning + ImpulseScheduler allow-list code change
- cd5b334: M-A.T.R.I.X.3 + M-A.T.R.I.X.4 (DistilBERT real + GPT-2) + 2 reports
- d7007a0: EXP-002/003 production verdict (corpus restored from 583fbec)