# DESIGN-NN — Free Energy Principle (FEP) for Mind Arousal (META-R R-A SOTA ML)

> TRUE-W11 research-engine iteration #4.
> Domain: SOTA ML / computational neuroscience.
> Source: Friston, K. (2010) "The free-energy principle: a unified brain theory?"

## Goal

Replace the mind's scalar arousal/prediction-error with a variational
free-energy estimate. FEP gives a principled way to:
- Track **expected surprise** as the discrepancy between prior and posterior
- Use **gradient descent on free energy** as the natural learning direction
- Drive arousal from **epistemic value** (uncertainty reduction)

## Algorithmic Core

```
F(π) = D_KL[ q(s|π) || p(s|o) ] - ln p(o)
     ≈ E_q [ -ln p(o|s) ]            (accuracy term)
     + D_KL[ q(s) || p(s) ]          (complexity term)
```

Where:
- `π` = policy (current mind state)
- `s` = hidden state
- `o` = observation (input + outcome)
- `q` = recognition density
- `p(s|o)` = posterior over hidden states given observation

## Why It Might Help MATRIX

1. **Arousal with epistemic value**: Currently arousal = `|confidence - 0.5|`
   which has no notion of "do I know enough?" FEP adds the *complexity*
   term which measures how much the model is uncertain.
2. **Learning direction**: FEP's gradient = prediction error = the
   mind's existing `predictionError` field. Same value, principled derivation.
3. **Better sleep consolidation**: dreams should reduce free energy by
   sampling high-uncertainty regions. Current `RealSleepScheduler`
   doesn't have this.

## Implementation Sketch

```java
public final class FreeEnergyEstimator {
    private final double[] prior;          // p(s)
    private final double[] posterior;      // q(s|o)
    private final double[][] likelihood;   // p(o|s)

    public double compute() {
        double accuracy = 0.0;
        for (int s = 0; s < prior.length; s++) {
            accuracy -= posterior[s] * Math.log(likelihood[s][obs]);
        }
        double complexity = 0.0;
        for (int s = 0; s < prior.length; s++) {
            if (posterior[s] > 0) {
                complexity += posterior[s] * Math.log(posterior[s] / prior[s]);
            }
        }
        return accuracy + complexity;
    }
}
```

## Status (TRUE-W11 iteration 4)

- [x] DESIGN-NN drafted
- [x] Prototype: FreeEnergyEstimator (1D discrete, 32 states)
- [ ] Benchmark vs incumbent arousal (deferred — needs MCTS loop to stress)
- [ ] Integration into TrueMindCycle (deferred)
