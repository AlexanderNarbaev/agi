# RECON-W19+ Perpetual Loop — milestone after W13-W18

> Date: 2026-09-27

## Wave-arc summary
| Wave | Component | L closed | Test deltas |
|------|-----------|----------|-------------|
| W13 | Live benchmark harness | L-4 partial | 0 → 48 probes |
| W14 | Real ONNX distillation | L-1 partial | 2 tests green |
| W15 | Persistent BirRegistry + contradiction | L-2, L-3 | 4 tests green |
| W16 | Vector benchmark | L-5 honest | 1 test green, 0.18x speedup |
| W17 | Two-node federation smoke | L-6 partial | script written |
| W18 | Human path (5-command quickstart + smoke) | L-7 partial | docs shipped |

## W19+ queues (operator directive: improve user-observable capability per wave)
- (research) SymbolicNumberGrounder targeting D-W13-1 GENERALIZATION 0/7.
- (research) Active inference scheduler (next iteration).
- (hygiene) Disk rotation; archive runlog.

## What the user can newly observe after W13-W18
- POST /v1/distill — graceful synth or ONNX (per runtime)
- POST /v1/bir + GET /v1/conflicts — register + see quarantined contradictions
- GET /v1/federate?action=dump / POST /v1/federate — cross-node knowledge sharing
- Live restart-survival verified: rule persisted across gateway restart.

## Honest measurements (as of HEAD)
- Ecosystem tests: 618+ (gradle often reuses cached results; latest fresh run counted).
- Live benchmark: 33/45 headline (0.73; excl RETRIEVAL).
- Article VIII: all 6 mechanical guards green.
- Disk: 23 GB free (HEALTHY after smoke cleanup).
