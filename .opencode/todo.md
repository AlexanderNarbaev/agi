# Mission Tasks

## Task List

[x] **T1-T16: All 12 rebuild modules** — BIR, Tsetlin, DevLoop, KTopo, Signals, Lifecycle, Federation, Actions, Monotone, Reservoir, Budgeter, Distill. 86 tests pass.
[x] **T17: BIR into brain pipeline** — generateFromBir() in AgentBrainService.
[x] **T18: Full test suite** — 86 tests, 0 failures, 100% pass rate.
[x] **T19: BIR wired into chat** — Chat pipeline uses BIR generation.
[x] **T22: WAL v3.60** — Rebuild documented.

## Remaining (env blockers)

[ ] **T20: Coverage measurement** — JaCoCo agent filtered by Quarkus native-image plugin (env issue, not code).
[ ] **T21: K8s integration test** — Cluster needs restart after minikube stop/start cycles.
