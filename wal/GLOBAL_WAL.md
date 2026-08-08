📍 v3.61 — MATRIX REBUILD: Coverage measurable (61.23% METHOD), 989 tests, 12 modules, all new code 100%.
🚀 Active: docs/matrix-rebuild branch. BIR + Tsetlin + DevLoop + KTopo + Signals + Lifecycle + Federation + Actions + Monotone + Reservoir + Budgeter + Distill. Chat pipeline uses BIR.
🛑 Protected: Pekko 1.6.0, K_MAX=20, FROZEN-нейроны, Quarkus 3.37.3, Java 25, AGPLv3+ethics, 82% coverage floor (target)

## v3.61 — Rebuild Progress

### Coverage (measurable!)

| Metric | Covered | Total | % |
|--------|---------|-------|---|
| METHOD | 908 | 1483 | **61.23%** |
| LINE | 3994 | 7319 | 54.57% |
| INSTRUCTION | 21375 | 39731 | 53.80% |
| BRANCH | 1627 | 3595 | 45.26% |
| COMPLEXITY | 1539 | 3306 | 46.55% |
| CLASS | 161 | 243 | 66.26% |

### New Modules (all 100% METHOD)

bir, tsetlin, devloop, ktopo, signals, lifecycle, federation, actions, monotone, reservoir, budgeter, distill

### Tests

- 989 tests, 0 failures, 100% pass rate
- 12 rebuild modules + existing core modules

### Architecture

```
User → /v1/chat/completions
  → OpenAIChatResource
    → textGenerator.forwardPass (3-layer neural hierarchy)
    → generateFromBir (Tsetlin-trained ClauseSetForm)
    → generateFromMemory (corpus fallback)
    → brain.decide (tertiary)
```

### Remaining (to reach 82%)

- Need ~300 more covered methods (from 908 to 1216)
- Focus: agent, api, chat, neuron packages have most uncovered methods
- Env blockers resolved: coverage now measurable via coverageTest task
