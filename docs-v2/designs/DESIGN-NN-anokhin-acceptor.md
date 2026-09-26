# DESIGN-NN — Anokhin Acceptor Action (R-B cybernetics)

> TRUE-W11 research-engine iteration #5.
> Domain: Soviet/Russian cybernetics school.
> Source: Anokhin, P.K. (1974) "Biological and physiological foundations
> of psychology (theory of functional systems)"

## Goal

Implement Anokhin's "acceptor of action results" — the cognitive
comparator that detects when an action's actual outcome diverges from
the predicted outcome. Drives mind-level surprise + learning rate.

## Algorithmic Core

```
AFF(forward_model, action, outcome):
    predicted = forward_model(action)
    error     = ||outcome - predicted||²
    if error > threshold:
        return ACCEPTOR_MISMATCH
    else:
        return ACCEPTOR_MATCH
```

Where:
- `forward_model` = `MindCycle` (we already have `predictionError` field)
- `action` = the chosen mind stage output
- `outcome` = next observation

## Why It Might Help MATRIX

1. **Concrete surprise signal**: Currently `predictionError` is a
   scalar; the Acceptor gives a binary match/mismatch + magnitude.
2. **Learning rate modulation**: On mismatch, increase learning rate.
3. **Stage-level attribution**: which stage produced the bad prediction?
   The Acceptor can attribute surprise to a stage.

## Status (TRUE-W11 iteration 5)

- [x] DESIGN-NN drafted
- [x] Prototype: ActionAcceptor (stage-attributed surprise)
- [ ] Benchmark vs incumbent predictionError (deferred)
- [ ] Integration into TrueMindCycle (deferred)
