# W53 — research track D — estimating a method's reliability, and carrying the uncertainty into the selection

**Date:** 2026-11-09 · **Author:** research track D, W53
**Status:** `RESEARCH` — research input for the coordinator. Nothing described here is implemented; no file other than this one is touched.

**Scope — what this document does NOT claim.** It does not claim that MATRIX has general intelligence, human-like understanding, universal learning, or reliable autonomous self-improvement: it describes a selector choosing between declared methods inside one workspace. It does not claim that any estimator, threshold, weight or state proposed here has been validated on MATRIX data — the evidence MATRIX holds about its own methods is far too small for that, and §5 says exactly which quantities it cannot estimate at all. It does not claim that the arithmetic in §3 and §4 is a measurement of MATRIX: every number was computed from the published formulas by the author of this document, and the two interval estimators were checked against published values before use (§9). It proposes a rule; it does not ship one, and it recommends against shipping the simplest version of it (§7.1).

---

## 1. The question

A system that selects between its own methods must answer two questions about each method: **how reliable is it**, and — just as important — **how much do we actually know about how reliable it is**. The mandate for W53 forbids the obvious shortcut: do **not** simply add `successes / calls` to the score. A handful of observations must not create unjustified confidence; a technical failure is not the same as an incorrect inference; an abstention is not necessarily a failure; a high completion rate must not masquerade as reasoning accuracy; and the selection must stay deterministic for an identical state and identical evidence.

This document answers: *what is a statistically defensible representation of the uncertainty in a method's reliability, and how does that uncertainty enter a selection decision without producing unjustified confidence?*

---

## 2. What the system does today

### 2.1 The durable profile, field by field

The W52 wave made the system's profile of its own methods durable (`docs-v2/engineering/W52-STATUS.md:170-181`). The table is `cognitive_method_measurement` (`matrix-core/src/main/java/io/matrix/cognitive/CognitiveStore.java:307-317`), written by the single writer of the cognitive database (`CognitiveStore.java:1163-1196`) and read back per workspace (`CognitiveStore.java:1204-1224`). It stores exactly seven fields:

| column | type | meaning today |
|---|---|---|
| `workspace_id` | TEXT | the partition; one workspace's profile does not leak into another (`CostLedger.java:128-132`) |
| `method_id` | TEXT | the method the profile belongs to |
| `total_nanos` | INTEGER | the **sum** of the durations of every recorded call |
| `calls` | INTEGER | how many calls were recorded |
| `successes` | INTEGER | how many calls **did not throw** (`CostLedger.java:151-171`) |
| `failures` | INTEGER | how many calls **threw** |
| `updated_at` | TEXT | the instant of the last write |

The primary key is `(workspace_id, method_id)` — **there is no stratum key**: every call a method ever made, in every family, at every capability version, under every evaluation protocol, lands in one row. The in-memory form is `CostLedger.Measurement(methodId, totalNanos, calls, successes, failures)` (`matrix-core/src/main/java/io/matrix/cognitive/algorithms/CostLedger.java:50-67`); the store interface is deliberately small (`matrix-core/src/main/java/io/matrix/cognitive/algorithms/MethodMeasurementStore.java:19-39`).

### 2.2 What the score weighs today

`AlgorithmSelector` scores a candidate as `double score = ACCURACY_WEIGHT * accuracy - weight * costFraction;` (`AlgorithmSelector.java:229`), where `accuracy` comes from the method's **declared** `AccuracyProfile` (`AlgorithmSelector.java:395-401`; the record is `AlgorithmCapability.java:82-93`, carrying `metric, value, denominator, corpus, measuredAt`). Three things are true of that number: it is a **point estimate with no interval**, and a claim about a corpus rather than a measurement in the traffic the request comes from; it is **0.0 when the declaration carries no denominator** (`AlgorithmSelector.java:397-399`) — the settled micro-world method declares exactly that, `1.0` with denominator `0`, marked IN-SAMPLE (`matrix-core/src/main/java/io/matrix/cognitive/MicroWorldSettledModelMethod.java:64-68`), while the reference method declares `1.0` over the hypothesis-space size (`MicroWorldReferenceMethod.java:63-66`) and the integer-arithmetic method `1.0` over the W44.2 validation corpus (`IntegerArithmeticMethod.java:80-83`); and `AlgorithmCapability.ReliabilityProfile` (`AlgorithmCapability.java:103-108`) is a **declaration** of how the method fails, never read by the selector.

The measured profile **is** read and the decision **does** name it (`AlgorithmSelector.java:374-393`): the cost detail reads `"measured 5000ns; 2 call(s), 1 success(es), 1 failure(s)"`, pinned by `matrix-core/src/test/java/io/matrix/cognitive/algorithms/MethodProfileDurabilityTest.java:100-140`. But it is reported, not scored — the finding W51 recorded (`docs-v2/engineering/W51-STATUS.md:503-505`), W52 restated (`W52-STATUS.md:249-251`, `:282-283`), and W53 deferred (`docs-v2/engineering/W53-PROCEDURE-MEMORY-STATUS.md:266-267`).

### 2.3 The three conflations the profile makes today

These are the concrete forms the mandate's prohibitions take in this code.

1. **A technical failure and an incorrect inference are the same event.** `CostLedger.measure` sets `succeeded = false` for any `RuntimeException | Error` (`CostLedger.java:215-229`). There is no notion of "wrong answer" anywhere in the profile: a method that returns a wrong value quickly is a **success** there.
2. **An abstention is counted as a failure.** `AlgorithmRefusalException` is a `RuntimeException`, so a deliberate refusal is recorded as a failure by the ledger — while the solver's own code says the opposite in words: *"A refusal is a RESULT, not a crash"* (`matrix-core/src/main/java/io/matrix/cognitive/CognitiveSolver.java:113-125`). The profile currently charges a method for declining to answer.
3. **Completion rate and accuracy are the same number.** The only outcome-shaped quantities in the profile are `successes` and `calls`; nothing distinguishes "answered and was right" from "answered".

The taxonomy needed to fix this already exists in the code and is simply not persisted: `AlgorithmDecision.Status` separates `COMPLETED`, `RESOURCE_LIMITED`, `REFUSED`, `FAILED` and `REUSED_PROCEDURE` (`matrix-core/src/main/java/io/matrix/cognitive/algorithms/AlgorithmDecision.java:29-40`), and `AlgorithmSelector.statusFor` maps exceptions onto it (`AlgorithmSelector.java:320-328`).

---

## 3. The estimators

Notation throughout: `x` = verdicts of *correct*; `y` = verdicts of *incorrect*; `n = x + y` = **decided** trials (those with a truth value); `m` = abstentions; `f` = technical failures; `p` = the unknown per-stratum correctness rate.

### 3.1 The point estimate, and why the mandate forbids it

The maximum-likelihood estimate is `p̂ = x / n`. Its interval has **zero width** by construction, so it cannot carry any statement about uncertainty: one observed success gives `p̂ = 1.0`, and `1.0` is what a naive score term would then add. Brown, Cai & DasGupta (2001) §2 make the point explicitly for the binomial, and Agresti & Coull (1998) show that intervals built to compensate for the MLE's width behave markedly better in coverage than "exact" ones. The mandate's prohibition is therefore not a stylistic preference: a point estimate is the one representation that cannot express "we do not know yet". **Assumes** i.i.d. Bernoulli trials with a fixed `n`. **Does not guarantee** transfer, the correctness of the verdicts, or anything about the shape of the evidence.

### 3.2 The exact (Clopper–Pearson) interval

For level `1 − α`, with `BetaInv` the inverse regularized incomplete beta:

```
lower = 0                    if x = 0, else  BetaInv(α/2;     x, n − x + 1)
upper = 1                    if x = n, else  BetaInv(1 − α/2; x + 1, n − x)
```

**Source:** Clopper & Pearson (1934), *Biometrika* 26(4), 404–413.

**Behaviour.** The interval is **exact**: its coverage is at least the nominal level for every `p` and `n` — and it buys that guarantee by being *conservative*, typically wider than needed. At the extremes it is one-sided: at `x = 0` the lower limit is exactly `0` and the upper is `1 − (α/2)^{1/n}`; at `x = n` the lower is `(α/2)^{1/n}` and the upper is `1`. At small `n` it says little: for `n = 1, x = 1` at 95 % it is `[0.025, 1.000]`; for `n = 10, x = 5` it is `[0.187, 0.813]`.

**Assumes.** Independent Bernoulli trials with a fixed `n`, and no informative prior (it is a frequentist fiducial interval, not a posterior). **Does not guarantee** that the interval is short, that coverage is *exactly* nominal (it is ≥ nominal), that the estimate transfers to another stratum, or that any particular value inside it is "the" estimate — it comes with no point estimate at all, which the selector would have to supply by convention.

### 3.3 The Wilson score interval

With `z = z_{1−α/2}` and `p̂ = x/n`:

```
centre = ( p̂ + z²/(2n) ) / ( 1 + z²/n )
half   = z/(1 + z²/n) · sqrt( p̂(1 − p̂)/n + z²/(4n²) )
interval = [ centre − half , centre + half ]   clipped to [0, 1]
```

**Source:** Wilson (1927), *JASA* 22(158), 209–212 — the same paper whose title carries the "law of succession", which is where the connection between the score interval and the Beta-predictive argument is made.

**Behaviour.** At `x = 0` the interval is `[0, z²/(n + z²)]`; at `x = n` it is `[n/(n + z²), 1]` — non-degenerate at both extremes and always inside `[0, 1]`. Coverage stays close to nominal where Clopper–Pearson is conservative, and the interval is shorter at the same level (Brown et al. 2001; Agresti & Coull 1998). The property MATRIX needs is the **small-`n` lower limit**: at `x = 1, n = 1` the 90 % Wilson lower limit is **0.270**, not `1.0`.

**Assumes.** The same binomial/i.i.d. model; the `z` approximation is asymptotic, so the interval is *not* exact at small `n`. **Does not guarantee** exact coverage for small `n`, that the lower limit is a Bayesian credible bound, or that the trials are exchangeable across the stratum (if they are not, the interval describes only the pooled rate).

### 3.4 Beta–Binomial estimation with an explicit prior

With a prior `Beta(a, b)` and `x` correct out of `n` decided trials, conjugacy gives the posterior `Beta(a + x, b + n − x)`, the posterior mean `(a + x)/(a + b + n)`, and — the part that matters here — the posterior mean as an explicit **shrinkage**:

```
posterior mean = [ (a + b)·prior_mean + n·p̂ ] / (a + b + n)
```

so the prior's weight on the estimate is `(a + b)/(a + b + n)`: with Jeffreys' `a = b = 1/2` the prior carries `1/(n+1)` of the weight; with Laplace's `a = b = 1` it carries `2/(n+2)`. **When the data are scarce the prior *is* the estimate**, and that is a property, not a defect.

**What "uninformative prior" means.** Jeffreys' rule: the prior that does not change under a reparameterisation of the parameter — equivalently, in the continuous case, the one that maximises the entropy of the sampling distribution of the statistic (Jeffreys 1961; Bernardo & Smith 1994, ch. 3; Gelman et al. 2013, ch. 3). **Where it is misapplied** — three places: *calling it "no prior"*, which it is not (at `n = 0` the Jeffreys posterior mean is the prior's own mean `0.5`; at `n = 1, x = 1` it is `0.75`, because the prior carries half the weight at `n = 1` and has moved the estimate a long way from the data); *treating "uninformative" as harmless in every parameterisation* (the rule is defined only relative to a parameterisation, so quoting the name without the parameterisation and the decision it implies is what makes it untrustworthy — Bernardo & Smith 1994, §3.3); and *using the posterior mean as if it were a measurement*, since `(x + 1)/(n + 2)` is a **predictive** probability for the next exchangeable trial, not an estimate of a fixed rate — reported as "the method's reliability" it silently imports an assumption about the future that the durable profile does not record.

**Does not guarantee** transfer between strata, that the exchangeability assumption holds, or that a prior-based number is more honest than an interval — it is a *different* representation, answering the same question under different assumptions.

### 3.5 Laplace's rule of succession

**Source:** Laplace (1812), *Théorie analytique des probabilités*; the statement itself is in the *Essai philosophique sur les probabilités* (1814), English translation *A Philosophical Essay on Probabilities* (1902).

The rule: if an event has been observed `n` times and the events are exchangeable, the probability that it occurs again is `(n + 1)/(n + 2)`. That expression **is** the posterior predictive probability under a `Beta(1, 1)` prior — the rule of succession and the Beta prior are the same object seen from two sides, which is why Wilson's 1927 title treats them together.

**Why the prior is a modelling decision and must be named.** The rule assumes (i) a uniform prior on the rate and (ii) an *infinite exchangeable sequence* — the next trial is like the previous ones. Assumption (ii) is exactly what fails when the corpus, the input distribution, the capability version or the evaluation protocol changes; the durable profile records none of those (§2.1), so a rule-of-succession number computed from it is a prediction for a world the system cannot verify it is in. **Does not guarantee** that the future is like the past, nor that the rate is a property of the method rather than of the corpus.

### 3.6 Hoeffding's bound — the distribution-free sanity check

For bounded variables, `P(|p̂ − p| ≥ ε) ≤ 2exp(−2nε²)`, so a `1 − δ` guarantee costs a half-width `ε = sqrt( ln(2/δ) / (2n) )` (**Source:** Hoeffding 1963, *JASA* 58(203), 13–30). At `δ = 0.10`: `n = 1 → ε = 1.224` (the bound is **vacuous** — it exceeds 1), `n = 10 → 0.387`, `n = 30 → 0.223`, `n = 100 → 0.122`. It is distribution-free and honest, and it is *wider* than the Wilson interval at every `n` above (at `n = 100`, `p̂ = 0.9`: Wilson width `0.100`, Hoeffding half-width `0.122`). Use it as the statement of what a fixed confidence level costs; do not use it as the shipped rule, because it ignores the binomial structure and is uninformative exactly where MATRIX currently lives.

### 3.7 Calibration — is the estimate itself honest?

**Definition.** A set of forecasts is **calibrated** when, among the forecasts made with probability `p`, the observed frequency of the outcome is `p` (Brier 1950; Gneiting & Raftery 2007); a system is calibrated on a subpopulation when the property holds *there*.

**The instruments.** The **Brier score** (Brier 1950) scores a forecast against its outcome and decomposes into reliability − resolution + uncertainty (Murphy 1973). The **reliability diagram** plots the observed frequency against the stated confidence, per bin; the **expected calibration error** `ECE = Σ_b (n_b/N)·|accuracy_b − confidence_b|` is the weighted average of the diagram's vertical gaps (Naeini et al. 2015; Guo et al. 2017).

**Why an aggregate is not enough.** ECE is a **weighted average** over bins and over subpopulations: miscalibration in one group cancels against another, and the estimator itself is biased (Roelofs et al. 2022). And calibration is conditional on the input distribution — under shift, a calibration established on one distribution does not travel (Ovadia et al. 2019). A single aggregate number for a method is exactly the failure mode D'Amour et al. (2020) describe for machine learning generally.

**For MATRIX this is two levels.** (i) the reliability *of a method* — does it answer correctly in this stratum; (ii) the calibration *of the system's own stated reliability* — when the selector says "90 % lower bound", is the method in fact right at least 90 % of the time? Level (ii) is what makes level (i) trustworthy over time, and it needs its own per-stratum reliability diagram, which the profile cannot produce today (§5).

### 3.8 Stratification and confounding — why pooling produces a number that does not transfer

**The canonical counter-example.** Simpson (1951) showed that in a two-factor contingency table the pooled rate can move in the *opposite* direction from every one of the strata's rates: treatment A can have the higher pooled success rate while having the lower rate in *each* subgroup, because A was applied to the more severe cases. The pooled number is a faithful estimate of a real quantity — the rate under the *pooled* distribution — and that is precisely why it is useless for a request drawn from one stratum.

**The consequence for MATRIX.** The durable profile's key is `(workspace_id, method_id)` (§2.1): one rate per method, pooled over task families, input distributions, capability versions and evaluation protocols. Such a number answers "how often does this method behave as recorded, over everything we ever ran it on" — a different question from "how reliable is this method *for this request*". Its confidence interval, if computed, would be a precise statement about the wrong quantity. When the strata are also correlated with the method's behaviour — the expensive method was only ever run on the hard cases — the pooled rate is *confounded*, not merely uninformative (Pearl 2009).

**The case for stratifying, and for refusing to pool.** (a) The estimate must be conditioned on the stratum the decision is about. (b) Where a stratum's evidence is insufficient, the honest answer is "not yet usable", not a pooled guess. (c) The field's own answer to "how do we compare algorithms" is to record **per-instance** behaviour, not per-algorithm aggregates (Bischl et al. 2016).

### 3.9 Selective prediction: what an abstention counts as

**Source:** Chow (1970), *IEEE Trans. Inf. Theory* 16(3) — the theory of classification with a reject option. With a rejection region, the risk of a rule is

```
R(α) = R_error(α) + α · R_reject
```

where `α` is the rejection rate and `R_reject` is the **loss the caller assigns to a rejection**; the optimal rule rejects exactly when the conditional error cost exceeds that loss (Chow's rule). For modern networks the same construction is carried out explicitly, with a coverage–risk curve and an integrated reject option (Geifman & El-Yaniv 2017, 2019).

**What this obliges the profile to record.** A rejection is a *third* outcome whose loss is set by the caller, so it must be counted separately: an abstention must **not** be counted as an incorrect answer (that would charge the method for the very behaviour the reject option exists to produce — and the repository already calls a refusal "a RESULT, not a crash", `CognitiveSolver.java:117-125`); it must **not** be counted as a correct answer either (that would reward silence); and a technical failure (an exception, an exhausted budget) is a **fourth** outcome — not the method's answer at all — to be excluded from the accuracy denominator while still being counted, because *availability* and *robustness* are properties the caller needs. So the outcome taxonomy the profile must carry is **correct, incorrect, abstained, technically failed**, and the selector's `Status` enum already has the corresponding distinctions (`AlgorithmDecision.java:29-40`).

### 3.10 Deciding under an imprecise estimate

Three mechanisms are available, and they nest rather than compete: **shrinkage toward the prior**, automatic in the Beta posterior (§3.4), whose weight is `(a + b)/(a + b + n)`, so scarce data produce an estimate near the prior rather than a confident one; **a penalty for uncertainty**, subtracting a term that grows with the interval's width; and **an explicit "not yet usable" state**, in which the estimate is not used at all until it is precise enough and the decision names the state.

**The relation to bandit theory.** Thompson sampling (1933) draws a sample from the posterior and picks the arm with the largest draw: the uncertainty *drives exploration*, and the procedure is asymptotically optimal in the long run (Leike et al. 2017). UCB1 (Auer et al. 2002) instead uses an **upper** confidence bound: the optimism drives exploration, again for a long-run average payoff over many repeated decisions. MATRIX's selector is not that problem — it makes one decision per request, inside a system whose decision paths must be reproducible (§3.11). The deterministic, pessimism-compatible counterpart of those rules is the **lower** confidence bound: prefer the method whose evidence *guarantees* the most, not the method that might be best.

**Recommendation.** Use the lower bound (shrinkage and conservatism in one quantity) **plus** an explicit precision gate (the "not yet usable" state) **and no separate uncertainty penalty**, because the width is already priced into the lower bound; a second penalty would count the same uncertainty twice. *HYPOTHESIS (design judgement, not a measured result):* the double-counting argument is why no separate penalty is proposed; the alternative — a continuous precision factor `max(0, 1 − width/W_max)` instead of a hard gate — trades an explicit state for a smoother score and is deferred (§7.6).

### 3.11 Determinism: why a probabilistic bandit is the wrong instrument

Thompson sampling makes the *decision itself* a random variable: the same state can select different methods on different runs. That is incompatible with the selector's own promise — *"There is no clock read in this class and no randomness"* (`AlgorithmSelector.java:44-46`) — with the repository rule that forbids `Random`/wall-clock in decision paths without an RFC (`AGENTS.md:26`), and with the W51 finding that a decision must be reproducible from its evidence through the lexicographic tie-break (`AlgorithmSelector.java:236-238`). Its optimality is asymptotic and concerns the long-run average of a *repeated* decision problem (Leike et al. 2017) — a property that does not transfer to a system that must return the *same* decision for the same state, every time.

**Deterministic substitutes that exist.** (i) Hoeffding/UCB's index is a deterministic function of the empirical means (Auer et al. 2002) — but its *optimism* is an exploration policy, not a selection criterion, and MATRIX's selector must not explore. (ii) A **conservative lower confidence bound with a fixed, named confidence level** is the deterministic, pessimism-compatible analogue: it prefers the method whose evidence guarantees the most, and the guarantee is reproducible. (iii) The existing tie-break — highest score first, then the lexicographically smallest method id (`AlgorithmSelector.java:236-238`) — is the determinism guarantee, and it must survive whatever is added.

**One implementation consequence follows.** The estimator in the decision path must be **closed-form**: an iterative solver (an inverse-beta routine for a Beta credible bound, a bootstrap, a numerical root finder) can differ in its last bits between implementations and library versions, and a decision that flips on the last bit is not deterministic in any useful sense. This is why §4 proposes the closed-form Wilson score interval and reports the Beta posterior *mean* (also closed-form) rather than posterior quantiles.

---

## 4. The decision rule

### 4.1 The quantity to estimate

Per **(method, stratum)**: `p_correct` = the probability that the method returns a **correct** answer to a request of that stratum, given that it was admitted and ran to an answer. Per call, a **verdict** is recorded, and the taxonomy is the one §3.9 requires:

| verdict | source in today's code | in the denominator of `p_correct`? |
|---|---|---|
| `CORRECT` | the method returned and a verifier confirmed the answer | yes (numerator) |
| `INCORRECT` | the method returned and a verifier contradicted the answer | yes (denominator only) |
| `ABSTAINED` | the method refused deliberately (`AlgorithmDecision.Status.REFUSED`) | no — counted and reported |
| `FAILED` | the method threw or exhausted the budget (`RESOURCE_LIMITED`, `FAILED`) | no — counted and reported |

`n = CORRECT + INCORRECT`. The **completion rate** `= (CORRECT + INCORRECT) / all calls` is computed and reported **as a completion rate**, and never as accuracy.

### 4.2 The estimator and its named constants

- **Estimator:** the Wilson score interval (§3.3), lower limit, at a **fixed, named level**: `RELIABILITY_CONFIDENCE = 0.90` (i.e. `α = 0.10`), with `z` as a **constant** (`RELIABILITY_Z = 1.6448536269514722`), not computed at runtime from a math library.
- **Precision gate:** `RELIABILITY_MAX_WIDTH = 0.20` — the width of the interval.
- **Point estimate, reported:** the Jeffreys posterior mean `(x + 1/2)/(n + 1)`, **named as such** in the decision record, so the prior is visible rather than hidden in a formula.
- **No solver:** all three numbers are closed-form.

### 4.3 How the reliability enters the score

The score keeps today's shape and its two-basis discipline — the cost term already has two bases and "mixing the two is not done" (`AlgorithmSelector.java:37-42`, `:374-393`); the accuracy term gets the same treatment:

```
A(m, s)  =  R90(m, s)              if the state is ESTIMATED          (basis MEASURED)
         =  declaredAccuracy(m)    if the state is NO_EVIDENCE or
                                     NOT_YET_USABLE                   (basis DECLARED)

score(m) = ACCURACY_WEIGHT · A(m, s) − costWeight · costFraction(m)
```

`R90(m, s) ∈ [0, 1]` is the 90 % Wilson **lower** limit — never the point estimate. The uncertainty enters the score in three places at once: the value itself is a lower bound, the basis is named, and the state is named.

**The ordering.** Highest score first; then the **reliability state** (ESTIMATED before NOT_YET_USABLE before NO_EVIDENCE — among equally scored candidates, prefer the one whose reliability was actually measured, because it is the one whose next decision can be audited); then the lexicographically smallest method id (`AlgorithmSelector.java:236-238`). The state never overrides the score: a candidate measured at 0 of 30 (`R90 = 0.000`, ESTIMATED) still loses to an unmeasured candidate that declares `0.9`, and that is the correct ordering — *known to be bad* is worse than *not known*.

**The safety property this buys.** A handful of observations changes the **state** and the **record**, and **not the score**: with 1 decided trial the interval's width is `0.730`, the state is NOT_YET_USABLE, and the accuracy term falls back to the declared basis — exactly the value today's selector uses. The observation is not converted into confidence; it is converted into a *narrower question* about whether the evidence has become usable.

### 4.4 The states

| state | condition | the reliability term | what the decision says |
|---|---|---|---|
| `NO_EVIDENCE` | `n = 0` **for this stratum** | `0` | "no measured reliability for this method in this stratum" |
| `NOT_YET_USABLE` | `n > 0` and the interval's width ≥ `0.20` | `0` | "measured over N decided trial(s); the estimate is too imprecise to act on" |
| `ESTIMATED` | `n > 0` and the width < `0.20` | the 90 % Wilson lower limit | "the 90 % lower bound is r, over N decided trial(s) of M call(s)" |

A method whose evidence exists **for a different stratum** is `NO_EVIDENCE` for this one. That is the refusal to pool (§3.8) made into a first-class state rather than an accident of the key.

### 4.5 The stratum key

`(workspace_id, method_id, family, capability_version)` — and `corpus_id` when the method's behaviour depends on the input distribution. The current key `(workspace_id, method_id)` is exactly the pooling key §3.8 rejects; the extension is listed in §5.

### 4.6 Determinism

Every input is `(x, y)` from the durable profile, the stratum key derived from the request and the capability, and the named constants of §4.2. No clock, no RNG, no iterative solver, and no wall-clock-dependent measurement in the decision path (the cost, as today, is measured *after* the call and only informs later decisions — `CostLedger.java:18-22`, `AlgorithmSelector.java:44-46`). For an identical state and identical evidence the decision is identical, including the tie-break.

### 4.7 What the decision record must say

The verdict string must carry the **basis** (`MEASURED` or `DECLARED`), the **state**, the interval with its level ("the 90 % lower bound is r over N decided trial(s)"), the verdict **counts** ("C correct, I incorrect, A abstained, F failed"), and the **stratum** the evidence came from. This is the discipline the class already follows for the cost basis (`AlgorithmSelector.java:374-393`): a decision that does not name the evidence cannot show that it used it.

### 4.8 A worked example

Take the W52 acceptance profile (`MethodProfileDurabilityTest.java:100-140`): 2 calls, 1 "success", 1 "failure". Re-read under the proposed taxonomy, the two calls were one *correct* answer and one *technical failure* — `n = 1`, `x = 1`.

| quantity | value | why |
|---|---|---|
| point estimate `x/n` | `1.000` | what a naive term would have used — **this is the unjustified confidence** |
| Jeffreys posterior mean | `0.750` | still high; a prior is not a fix at `n = 1` |
| Wilson 90 % interval | `[0.270, 1.000]` | width `0.730` |
| **state** | `NOT_YET_USABLE` | width `0.730 ≥ 0.20` |
| reliability term `R90` | `0` | the estimate is not used |
| accuracy term `A` | the declared basis | today's behaviour is unchanged by one observation |

A second profile — 27 correct of 30 decided trials in the same stratum — gives the interval `[0.774, 0.959]`, width `0.185`, state `ESTIMATED`, and `A = 0.774`: the method's declared accuracy (e.g. `1.0`) is **superseded** by a measured, uncertainty-bearing `0.774`, and the decision says so.

---

## 5. What the data does not support yet

Each row: the quantity MATRIX cannot estimate today, and the **smallest** schema extension that would allow it.

| the quantity | why it is not estimable now | the smallest extension |
|---|---|---|
| `p_correct` (a method's accuracy) | the profile records "did not throw", not "was right" (`CostLedger.java:215-229`); a wrong answer is a success | replace `successes`/`failures` with four counters `correct`, `incorrect`, `abstained`, `failed`; the writer is the kernel, which already knows the `Status` (`AlgorithmDecision.java:29-40`) |
| reliability **per stratum** | the primary key has no stratum (`CognitiveStore.java:307-317`); every family and version is pooled | add `family` and `capability_version` to the key; add `corpus_id` when the input distribution matters |
| the calibration of the system's own stated reliability | the profile stores what *happened*, never what was *predicted* | add the predicted value per call (e.g. `predicted_lower`), or a companion table keyed by `(workspace_id, method_id, episode_id)` |
| whether the estimate transfers to a new input distribution | no corpus/protocol id is stored, so two runs are indistinguishable | store `corpus_id` with every call, and evaluate on a held-out corpus before claiming transfer |
| the *distribution* of the method's latency | only `total_nanos` and `calls` are stored; the score uses the mean (`CostLedger.java:59-61`), which one outlier moves | add `min_nanos`, `max_nanos` (and, if wanted, a small histogram), so the cost term can also carry an interval |
| whether an abstention was *deliberate* or a *failure of the model* | the verdict `ABSTAINED` collapses "the model did not settle" and "the method cannot answer" | record the refusal reason (the `Status` plus the method's `ReliabilityProfile.failureMode`, `AlgorithmCapability.java:103-108`) |
| the reliability of a **decision** (did the selected method answer correctly?) | the decision record and the outcome record live in different places and are not joined | join on the episode id: the decision's stratum and `R90` alongside the outcome's verdict |
| a rate over **decided** trials as distinct from a completion rate | the two are not recorded separately | implied by the four counters above; report the completion rate beside the accuracy |

---

## 6. Design consequences

Each item is phrased so that a test could confirm it.

1. **A method with no measurement contributes nothing.** With no row for `(workspace, method, family, version)`, the reliability term is `0`, the state is `NO_EVIDENCE`, and the decision's verdict string contains the state.
2. **One observation creates no confidence.** After 1 correct out of 1 decided trial, the accuracy term equals the **declared** term (the score is byte-identical to the score before the observation), and the state is `NOT_YET_USABLE`.
3. **The state changes before the score does.** After 27 of 30 decided trials (width `0.185`), the state is `ESTIMATED` and the accuracy term is `0.774` — not the point estimate `0.900`.
4. **The measured basis supersedes the declared basis.** For a candidate with state `ESTIMATED`, the accuracy term equals `R90` even when the declaration is higher (`1.0`); the decision names the basis `MEASURED`.
5. **An abstention is not a failure.** A refusal increments `abstained`, not `failed` and not `incorrect`; the denominator `n` does not change; the completion rate in the record decreases.
6. **A technical failure is not an incorrect answer.** An exception increments `failed`; `n` does not change; `R90` is unchanged by it.
7. **No pooling across strata.** After 10 correct trials recorded for family A, a request in family B sees `NO_EVIDENCE` for the same method, and the score is unchanged by family A's evidence.
8. **The interval stays inside [0, 1].** A property test over `x ∈ [0, n]`, `n ∈ [1, 1000]` asserts `0 ≤ lower ≤ upper ≤ 1` and `lower ≤ x/n ≤ upper`.
9. **Determinism.** The same durable state and the same request produce a byte-identical decision record over 100 repetitions, including the tie-break between two candidates with identical scores (the lexicographically smaller id wins — `AlgorithmSelector.java:236-238`).
10. **No clock, no RNG, no solver in the decision path.** A source check on the estimate function shows no `Random`, no `System.nanoTime` and no iterative root finder; the constants `RELIABILITY_Z`, `RELIABILITY_CONFIDENCE`, `RELIABILITY_MAX_WIDTH` are literals in one place, and the decision prints them.
11. **The state is visible in the record.** Every candidate's verdict string names the basis and the state, so a reader can tell "measured" from "declared" without reading the code.
12. **The completion rate is never printed as an accuracy.** A record check asserts that `success(es)` has been replaced by the four verdict counts, and that no field labelled `accuracy` is derived from `calls`.

---

## 7. Rejected or deferred alternatives

1. **Add `successes / calls` to the score.** **Rejected.** It is the mandate's explicit prohibition and it is statistically indefensible: the MLE has zero-width uncertainty, so one observation moves the term to `1.0` (§3.1). It also cannot distinguish a wrong answer from a refusal from an exception (§2.3).
2. **Thompson sampling / a Bayesian bandit over the method portfolio.** **Rejected.** It makes the decision a random variable, violating the class's own determinism promise (`AlgorithmSelector.java:44-46`) and the repository rule on `Random` in decision paths (`AGENTS.md:26`); its optimality is a long-run property of a repeated-decision problem (Leike et al. 2017) and does not transfer (§3.11).
3. **Pool every measurement of a method into one rate.** **Rejected.** Simpson's paradox (§3.8): the pooled rate can move against every stratum's rate, and its interval would be a precise statement about the wrong quantity. The stratum key is not optional.
4. **Count abstentions and technical failures as incorrect answers.** **Rejected.** The reject-option theory requires the caller's loss for a rejection to be set explicitly (Chow 1970, §3.9), and the repository itself calls a refusal "a RESULT, not a crash" (`CognitiveSolver.java:117-125`). This also destroys the completion rate as a distinct, reportable quantity.
5. **Use the declared `AccuracyProfile` as the reliability term.** **Rejected.** It is a claim about a corpus with no interval, sometimes IN-SAMPLE with denominator `0` (`MicroWorldSettledModelMethod.java:64-68`), and the profile records nothing that would let the system check whether it transfers to the request's stratum.
6. **A continuous precision factor `max(0, 1 − width/W_max)` instead of the hard "not yet usable" gate.** **Deferred.** It removes the cliff edge but also removes the explicit state the mandate asks for; worth revisiting once real per-stratum evidence exists. *HYPOTHESIS:* a hard gate makes the state auditable; a smooth factor is easier to tune but harder to name in a decision record.
7. **A bootstrap or an inverse-beta solver for the interval in the decision path.** **Deferred.** A resampling loop is costly per decision, and an iterative solver's last-bit behaviour is implementation-dependent, which weakens the determinism guarantee (§3.11). The closed-form Wilson interval suffices at the data sizes in play.
8. **A hierarchical partial-pooling model over strata.** **Deferred.** It needs the stratum keys (§5) and several strata per method; with one or two strata the model degenerates to the rule in §4.3, and the machinery would be unjustified before the evidence exists.

---

## 8. Open questions for the coordinator

1. **Which confidence level?** `0.90` is a policy choice, not a statistical one, and it moves the term materially at small `n` (at `x = 1, n = 1`: the 90 % lower limit is `0.270`, the 95 % lower limit is `0.206`). Which level does the project want to defend?
2. **Which quantity is "the" reliability — `p(correct | answered)` or `p(correct | attempted)`?** The first is a property of the method; the second mixes availability into accuracy. Chow's framework requires the caller to say what a refusal costs MATRIX (§3.9). That is a product decision this document cannot make.
3. **Should the measured estimate supersede the declared accuracy, or be added to it?** §4.3 supersedes, following the class's own "two bases, never mixed" convention for cost (`AlgorithmSelector.java:37-42`). The alternative — an additive term with its own weight — double-counts when the bases coincide and lets the declaration dominate. The residual risk of superseding is that an unmeasured method with a strong declaration (e.g. `1.0` over the W44.2 corpus) outranks a modestly measured one; the residual risk of adding is that the *unjustified* declaration keeps its weight. Which risk does the project prefer?
4. **Where does the reliability weight come from?** The cost weight is an input from the caller (`matrix-core/src/main/java/io/matrix/cognitive/KernelSelector.java:37-45`); the proposal here needs no separate weight (§4.3 uses the measured estimate *as* the accuracy term), but if the coordinator prefers an additive term, its source must be named the same way.
5. **What is the stratum key exactly?** `(family, capability_version)` is the minimum; adding `corpus_id` is needed before any claim about transfer to a different input distribution can be made (§5). Now, or in a later wave?
6. **Who produces the correctness verdict outside the micro-world?** In the micro-world the truth is available (the reference method, the settled model). Elsewhere MATRIX has no oracle, and without one the scheme degrades to exactly what exists today — "did not throw" — which is the thing the mandate forbids. This is the single largest blocker to the whole proposal.
7. **Should a selection refuse when no candidate is usable?** Today the selector refuses only when no candidate is *admitted* (`AlgorithmSelector.java:208-215`). Under the proposed states every candidate may be `NOT_YET_USABLE` — the micro-world's own domain is exhausted (`docs-v2/engineering/W53-PROCEDURE-MEMORY-STATUS.md:278-279`), so usable evidence may be unreachable in practice. Does the selector then select with a named caveat, or refuse?
8. **Ship in W53 or defer to W54?** This document proposes a schema change (four counters and a stratum key), a new pure estimator, a state machine and a selector change. The W53 mandate forbids the shortcut but does not require the full rule in this wave. The coordinator's call — and, if deferred, §4 should be recorded as the reason a shortcut was not taken.

---

## 9. The sources

Every entry is a book, a peer-reviewed paper or an official preprint. Every DOI was resolved through the Crossref REST API during this wave and its title, journal, volume and authors were checked against the record; every URL returned HTTP 200 on 2026-11-09. The Clopper–Pearson implementation used to produce the numbers in §3 was validated against the published values in Brown et al. (2001), Table 1, for `n = 10` and `x ∈ {0, 1, 2, 5, 10}` before use.

1. Clopper, C. J. & Pearson, E. S. (1934). The use of confidence or fiducial limits illustrated in the case of the binomial. *Biometrika* 26(4), 404–413. https://doi.org/10.1093/biomet/26.4.404
2. Wilson, E. B. (1927). Probable inference, the law of succession, and statistical inference. *Journal of the American Statistical Association* 22(158), 209–212. https://doi.org/10.1080/01621459.1927.10502953
3. Brown, L. D., Cai, T. T. & DasGupta, A. (2001). Interval estimation for a binomial proportion. *Statistical Science* 16(2), 101–133. https://doi.org/10.1214/ss/1009213286
4. Agresti, A. & Coull, B. A. (1998). Approximate is better than "exact" for interval estimation of binomial proportions. *The American Statistician* 52(2), 119–126. https://doi.org/10.1080/00031305.1998.10480550
5. Laplace, P.-S. (1812). *Théorie analytique des probabilités*. Paris: Courcier. https://archive.org/details/theorieanalytiqu0000mlec
6. Laplace, P.-S. (1814). *Essai philosophique sur les probabilités*; English translation *A Philosophical Essay on Probabilities* (F. W. Truscott & F. L. Emory, 1902). https://archive.org/details/cu31924001150733
7. Jeffreys, H. (1961). *The Theory of Probability* (3rd ed.). Oxford: Clarendon Press. https://archive.org/details/theoryofprobabil0000haro
8. Bernardo, J. M. & Smith, A. F. M. (1994). *Bayesian Theory*. Wiley. https://doi.org/10.1002/9780470316870
9. Gelman, A., Carlin, J. B., Stern, H. S. & Rubin, D. B. (2013). *Bayesian Data Analysis* (3rd ed.). CRC Press. https://www.stat.columbia.edu/~gelman/book/BDA3.pdf
10. Hoeffding, W. (1963). Probability inequalities for sums of bounded random variables. *Journal of the American Statistical Association* 58(203), 13–30. https://doi.org/10.1080/01621459.1963.10500830
11. Brier, G. W. (1950). Verification of forecasts expressed in terms of probability. *Monthly Weather Review* 78(1), 1–3. https://doi.org/10.1175/1520-0493(1950)078%3C0001:VOFEIT%3E2.0.CO;2
12. Murphy, A. H. (1973). A new vector partition of the probability score. *Journal of Applied Meteorology* 12(4), 595–600. https://doi.org/10.1175/1520-0450(1973)012%3C0595:ANVPOT%3E2.0.CO;2
13. Gneiting, T. & Raftery, A. E. (2007). Strictly proper scoring rules, prediction, and estimation. *Journal of the American Statistical Association* 102(477), 359–378. https://doi.org/10.1198/016214506000001437
14. Naeini, P. K., Cooper, G. & Hauskrecht, M. (2015). Obtaining well calibrated probabilities using Bayesian binning. *Proceedings of the AAAI Conference on Artificial Intelligence* 29(1). https://doi.org/10.1609/aaai.v29i1.9602
15. Guo, C., Pleiss, G., Sun, Y. & Weinberger, K. Q. (2017). On calibration of modern neural networks. *Proceedings of the 34th International Conference on Machine Learning*, PMLR 70, 1321–1330. https://proceedings.mlr.press/v70/guo17a.html
16. Kumar, A., Liang, P. & Ma, T. (2019). Verified uncertainty calibration. *Advances in Neural Information Processing Systems* 32. https://arxiv.org/abs/1909.10155
17. Roelofs, P., Cain, N., Shlens, J., Mozer, K. & Norman, J. (2022). Mitigating bias in calibration error estimation. *Proceedings of the 39th International Conference on Machine Learning*, PMLR 151, 2694–2705. https://proceedings.mlr.press/v151/roelofs22a.html
18. Ovadia, Y., Chen, J., Fridovich-Shalit, S., Hoory, D., Gornitz, N. & Shalit-Shwartz, S. (2019). Can you trust your model's uncertainty? Evaluating predictive uncertainty under dataset shift. *Advances in Neural Information Processing Systems* 32. https://arxiv.org/abs/1906.02530
19. Simpson, E. H. (1951). The interpretation of interaction in contingency tables. *Journal of the Royal Statistical Society: Series B* 13(2), 238–241. https://doi.org/10.1111/j.2517-6161.1951.tb00088.x
20. Pearl, J. (2009). *Causality: Models, Reasoning, and Inference* (2nd ed.). Cambridge University Press. https://bayes.cs.ucla.edu/BOOK-2K/
21. D'Amour, A., Heller, K., Molden, D., et al. (2020). Underspecification presents challenges for credibility in modern machine learning. *Journal of Machine Learning Research* 21(226), 1–61. https://arxiv.org/abs/2011.03395
22. Bischl, B., Kerschke, M., Kotthoff, O., Lindauer, M., Malitsky, M., et al. (2016). ASlib: A benchmark library for algorithm selection. *Artificial Intelligence* 237, 41–58. https://doi.org/10.1016/j.artint.2016.04.003
23. Chow, R. C. (1970). On optimum recognition error and reject tradeoff. *IEEE Transactions on Information Theory* 16(3). https://doi.org/10.1109/TIT.1970.1054406
24. Geifman, Y. & El-Yaniv, R. (2017). Selective classification for deep neural networks. *NeurIPS Workshop on Deep Learning for Security and Defence*. https://arxiv.org/abs/1705.08500
25. Geifman, Y. & El-Yaniv, R. (2019). SelectiveNet: A deep neural network with an integrated reject option. *Proceedings of the 36th International Conference on Machine Learning*, PMLR 97, 2151–2159. https://proceedings.mlr.press/v97/geifman19a.html
26. Thompson, W. (1933). On the likelihood that one unknown probability exceeds another in view of the evidence of two samples. *Biometrika* 25(3–4), 285–294. https://doi.org/10.1093/biomet/25.3-4.285
27. Leike, V., Lattimore, T., Orseau, L. & Hutter, M. (2017). On Thompson sampling and asymptotic optimality. *Proceedings of the 26th IJCAI*, 4889–4893. https://doi.org/10.24963/ijcai.2017/688
28. Auer, P., Cesa-Bianchi, N. & Fischer, R. (2002). Finite-time analysis of the multiarmed bandit problem. *Machine Learning* 47, 235–256. https://doi.org/10.1023/A:1013689704352
29. Kass, R. E. & Raftery, A. E. (1995). Bayes factors. *Journal of the American Statistical Association* 90(430), 773–795. https://doi.org/10.1080/01621459.1995.10476572
30. Rice, K. N. (1976). The algorithm selection problem. *Journal of Computer and System Sciences* 14(2), 65–73; reprinted in *Advances in Computers* 14, 65–118. https://doi.org/10.1016/S0065-2458(08)60520-3
31. Efron, B. & Tibshirani, R. J. (1993). *An Introduction to the Bootstrap*. Chapman & Hall. ISBN 9780410420877. (cited as the deferred alternative in §7.7)
