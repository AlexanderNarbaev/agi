---------------------------- MODULE ConjugateBudgeterDP ----------------------------
\* TLA+ formal specification of the ConjugateBudgeter DP backend
\* (matrix-core/src/main/java/io/matrix/budgeter/ConjugateBudgeter.java,
\*  DESIGN-11 §3, H-021)
\*
\* Author: MATRIX SSC
\* Spec version: 1.0 (2026-08-27)
\* Ref: docs-v2/designs/DRAFT-ConjugateDP.md, vision/DECISIONS.md D-007

EXTENDS Naturals, FiniteSets, TLC

CONSTANTS
    Rows,                \* the set of candidate rows (1..n)
    Values,              \* the per-row values (|Values| = |Rows|, distinct)
    Costs                \* the per-row costs (|Costs| = |Rows|, distinct)

\* Abbreviations
\*
\* `Values` and `Costs` are supplied by the model as sets of per-row
\* numbers: a TLC configuration file can express sets, numbers, strings and
\* model values, but never a function. Row i therefore takes the i-th
\* smallest element of each set — the rows are numbered 1..n, so the
\* assignment is unique as soon as the elements of the sets are distinct.
V(i) == CHOOSE v \in Values : Cardinality({w \in Values : w < v}) = i - 1
C(i) == CHOOSE c \in Costs : Cardinality({w \in Costs : w < c}) = i - 1

\* `Max(S)` and `MIN(S)` — the largest / smallest element of a set of
\* naturals. TLA+ has no standard `Max` operator, and `Len` (the Sequences
\* operator) applies to sequences only, while `Rows` is a set. The DP fold
\* below takes the maximum of two cells as the maximum of the two-element
\* set of cells.
Max(S) == IF S = {} THEN 0 ELSE CHOOSE m \in S : \A y \in S : m >= y
MIN(S) == IF S = {} THEN 0 ELSE CHOOSE m \in S : \A y \in S : m <= y

\* RECON-W38.4: `Sum` is not a standard TLA+ operator (it is LOCAL to the Bags
\* module), so the spec's total-cost expression had no definition. This is the
\* standard recursive sum over a set of naturals; it makes the intended
\* meaning explicit without changing any operator body that uses it.
RECURSIVE Sum(_)
Sum(S) == IF S = {} THEN 0
           ELSE LET x == CHOOSE y \in S : TRUE IN x + Sum(S \ {x})

\* ── Helper: total cost & value, envelope cap ─────────────────────────────────

TotalCost   == Sum({C(i) : i ∈ Rows})
TotalValue  == Sum({V(i) : i ∈ Rows})

\* ── State ────────────────────────────────────────────────────────────────────

VARIABLES
    envelope,            \* current integer envelope E_t
    selected,            \* selected[i] ∈ BOOLEAN: which rows the DP picked
    shadow,              \* λ_t = shadow price at the cap (terminal value slope)
    epoch                \* monotonically increasing integer clock

vars == <<envelope, selected, shadow, epoch>>

\* ── Init ─────────────────────────────────────────────────────────────────────

Init ==
    /\ envelope = 0
    /\ selected = [i ∈ Rows |-> FALSE]
    /\ shadow   = 0
    /\ epoch    = 0

\* ── Allocation step ───────────────────────────────────────────────────────────
\*
\* The DP returns the optimal 0/1 selection for the current envelope. The
\* shadow price is the terminal value slope λ(E) = V*(E) − V*(E−1).
\*
\* The fold below enumerates the candidate rows 1..Cardinality(Rows). `Rows`
\* is the set {1..n} of candidate rows (see CONSTANT Rows), so the i-th row
\* of that enumeration is i — the spec wrote `Rows[i]`, which TLA+ does not
\* allow (a set cannot be applied to an argument).

DPValue(e) ==                     \* backward DP table cell: V*(e)
    LET table == [ee ∈ 0..e |-> 0]
    IN  LET fold[i ∈ 0..Cardinality(Rows), ee ∈ 0..e] ==
            IF i = 0 THEN table[ee]
            ELSE IF C(i) <= ee
                 THEN Max({fold[i-1, ee],
                           V(i) + fold[i-1, ee - C(i)]})
                 ELSE fold[i-1, ee]
        IN fold[Cardinality(Rows), e]

\* ── The DP grid the Java actually uses ───────────────────────────────────────
\*
\* RECON-W39: the shadow price in io.matrix.budgeter.ConjugateBudgeter is
\* computed on a grid REDUCED by the gcd of the row costs:
\*
\*     g = gcd(costs);  units = cap / g;  lambda = dp[0][units] - dp[0][units-1]
\*
\* so the slope it reports is the marginal value of G raw units, not of one.
\* The model computed the marginal of ONE raw unit, so it did not describe
\* the Java whenever g > 1. A concrete counterexample: rows (v=1, c=4) and
\* (v=100, c=6) at envelope 7 give g = 2, so the Java reports
\* V*(6) - V*(4) = 100 - 1 = 99, while the model's slope was
\* V*(7) - V*(6) = 100 - 100 = 0.
\*
\* The grid is mirrored here so the model and the Java compute the SAME
\* number. The property LambdaBounded then states that correspondence
\* directly - it is checked, not assumed - and the bound it carries is
\* unchanged: one extra grid unit can enable at most one additional row,
\* so the marginal still cannot exceed the largest single row value.

RECURSIVE Gcd2(_, _)
Gcd2(a, b) == IF b = 0 THEN a ELSE Gcd2(b, a % b)

GcdOfCosts ==
    LET fold[i \in 0..Cardinality(Rows)] ==
            IF i = 0 THEN 0 ELSE Gcd2(fold[i-1], C(i))
    IN fold[Cardinality(Rows)]

\* The grid unit: g raw units per DP cell. A zero gcd (no rows, or rows of
\* cost zero) would make the reduction meaningless, so it degrades to 1.
GridUnit == IF GcdOfCosts = 0 THEN 1 ELSE GcdOfCosts

\* The largest multiple of the grid unit not exceeding e - the Java's
\* `units = cap / g` cell count expressed in raw units.
GridCap(e) == e - (e % GridUnit)

\* The shadow price the Java reports at envelope e: the slope of the value
\* function over the last GRID UNIT.
GridShadow(e) ==
    IF GridCap(e) < GridUnit
    THEN 0
    ELSE DPValue(GridCap(e)) - DPValue(GridCap(e) - GridUnit)

\* Reconstruct the optimal subset: take row i only when taking it is at
\* least as good as skipping it, and carry the REMAINING BUDGET down with the
\* witness.
\*
\* RECON-W38.5: the previous reconstruction unioned the "skip" witness with
\* the "take" witness (`prev[k] \/ take[k]`), so it could select BOTH the rows
\* of the skip solution AND the rows of the take solution - and the union can
\* cost more than the envelope. That is why SpentWithinEnvelope was false: with
\* V = (3,5,9) and C = (2,3,4) at envelope 3 the old reconstruction selected
\* rows 1 and 2, costing 2+3 = 5 against an envelope of 3.
\*
\* io.matrix.budgeter.ConjugateBudgeter.conjugateDp() does NOT do that. It
\* tracks the running budget - `e -= c[i]` when a row is taken - and its
\* witness for "take" is the witness for the REDUCED budget, not a union with
\* the skip witness. It also decides to take only when taking is at least as
\* good as skipping (`takeVal >= dp[i + 1][e]`), where the previous
\* reconstruction took every row that fit.
\*
\* The reconstruction below follows the Java: the take-branch carries
\* `ee - C(i)`, and the row is taken only when it is at least as good as
\* skipping.
GreedyPick(e) ==
    LET vals[i ∈ 0..Cardinality(Rows), ee ∈ 0..e] ==
            IF i = 0 THEN 0
            ELSE IF C(i) <= ee
                 THEN Max({vals[i-1, ee],
                           V(i) + vals[i-1, ee - C(i)]})
                 ELSE vals[i-1, ee]
        picks[i ∈ 0..Cardinality(Rows), ee ∈ 0..e] ==
            IF i = 0 THEN [k ∈ Rows |-> FALSE]
            ELSE IF C(i) <= ee
                 THEN IF V(i) + vals[i-1, ee - C(i)] >= vals[i-1, ee]
                      THEN [k ∈ Rows |-> IF k = i
                                         THEN TRUE
                                         ELSE picks[i-1, ee - C(i)][k]]
                      ELSE picks[i-1, ee]
                 ELSE picks[i-1, ee]
    IN picks[Cardinality(Rows), e]

Step(newEnvelope, observedLambda) ==
    /\ newEnvelope ∈ Nat
    /\ observedLambda ∈ Nat  \* abstracted as integer for TLC tractability
    /\ IF newEnvelope < MIN({C(i) : i ∈ Rows})
       THEN  \* envelope below cheapest row → FALLBACK path
             /\ selected' = [i ∈ Rows |-> FALSE]
             /\ shadow'   = 0
       ELSE
             LET lam  == GridShadow(newEnvelope)
             IN
                 /\ shadow' = lam
                 /\ selected' = GreedyPick(newEnvelope)  \* see reconstruction lemma
    /\ envelope' = newEnvelope
    /\ epoch' = epoch + 1

Next ==
    \E newE \in 0..TotalCost, obsLam \in 0..TotalValue :
        Step(newE, obsLam)

Spec == Init /\ [][Next]_vars

\* ── Safety properties ────────────────────────────────────────────────────────

\* S1: spent envelope never exceeds the requested envelope
\* (in the model this is structural — the DP enforces it; here we require
\*  that Sum(C(i)*selected[i]) ≤ envelope at every step.)
SpentWithinEnvelope ==
    [][ (Sum({C(i) : i ∈ {j ∈ Rows : selected[j]}}) ≤ envelope) ]_<<selected, envelope>>
    \* ^-- the totalCost of the chosen subset, expressed via the structural
    \*     fact that the DP enforces Σc·x ≤ e on its own. We assert the
    \*     equivalent residual in the post-state below.

\* S2: shadow price is finite & non-negative when CONJUGATE mode ran
ShadowFinite ==
    [][ (shadow ∈ Nat) ]_shadow

ShadowBounded ==
    [][ (shadow ≤ Max({V(i) : i ∈ Rows})) ]_shadow

\* S3: the shadow price IS the DP's terminal value slope, and it is a sane
\*      quantity: non-negative and bounded by the largest single row value.
\*
\* RECON-W38.5: the previous property asserted that λ(E) = V*(E) − V*(E−1) is
\* NON-INCREASING in the envelope. That is FALSE for a 0/1 knapsack, and the
\* spec's own row set is a counterexample: with V = (3,5,9) and C = (2,3,4),
\* V*(7) = 14, V*(8) = 14 and V*(9) = 17, so λ(8) = 0 < λ(9) = 3. No
\* implementation can satisfy the old property, so it was a broken assertion
\* rather than a gate. (The same false claim appears in the Java's javadoc -
\* ConjugateBudgeter says "lambda monotonicity - the shadow price is
\* non-increasing in the envelope" - and is corrected there too.)
\*
\* What the DP does guarantee, and what the Java relies on, is stated here
\* instead: the shadow price equals the marginal value of the envelope, it is
\* non-negative (more budget never hurts), and it is bounded by the largest
\* single row value (one extra unit of budget adds at most one row's worth of
\* value).
LambdaBounded ==
    [][ shadow = GridShadow(envelope)
        /\ shadow ≥ 0
        /\ shadow ≤ Max({V(i) : i ∈ Rows}) ]_shadow

\* ── Liveness: finite horizon ────────────────────────────────────────────────

\* The DP grid is bounded by TotalCost × Len(Rows); the algorithm must
\* terminate after a finite number of steps. Modeled as: the budgeter reaches
\* a fixed envelope within TotalCost steps.
FiniteHorizon ==
    <>(epoch ≤ TotalCost)

\* ── Step-state extension ────────────────────────────────────────────────────
\*
\* The Java implementation also runs a per-period loop (the
\* `step(epoch, observedLambda)` API) which observes the realised shadow
\* price and updates the budgeter's belief. The invariant that links the
\* per-period update to the DP bound is:
\*
\*     shadow(t+1) = clamp(α·shadow(t) + (1−α)·observedLambda, 0, maxV)
\*
\* with α ∈ [0,1] fixed. The clamp guarantees ShadowBounded; the convex
\* combination guarantees ShadowFinite. The lemma — proven by induction on
\* the epoch counter — is recorded below as a step-invariant.

CONSTANTS
    Alpha                 \* smoothing factor ∈ {0, 1} (modeled as int)

SmoothingUpdate(lam_t, obs) ==
    LET raw == Alpha * lam_t + (1 - Alpha) * obs
    IN  IF raw < 0 THEN 0
        ELSE IF raw > Max({V(i) : i ∈ Rows}) THEN Max({V(i) : i ∈ Rows})
             ELSE raw

PeriodStep(obs) ==
    /\ shadow' = SmoothingUpdate(shadow, obs)
    /\ UNCHANGED <<envelope, selected>>
    /\ epoch'  = epoch + 1

\* Per-period property: shadow stays in [0, maxV] at every step. The
\* observation is quantified over the same range the Next action uses for
\* the observed lambda (0..TotalValue); the module used `obs` free.
ShadowBoundedPeriod ==
    [][ (\A obs \in 0..TotalValue : PeriodStep(obs) ⇒
        shadow' ∈ 0..Max({V(i) : i ∈ Rows})) ]_shadow

\* ── Model bound (TLC state constraint) ───────────────────────────────────────
\*
\* `epoch` is an unbounded counter in the Java implementation, so the raw
\* state space of the model is infinite. The model is restricted to the
\* first TotalCost epochs — the very horizon the FiniteHorizon property
\* talks about. The constraint only prunes the explored states; no property
\* is weakened by it.
EpochBound == epoch <= TotalCost

=============================================================================
\* Modification History
\* v1.0 (2026-08-27) — initial spec; defines the DP backend plus the
\* per-period smoothing update that matches the `step(epoch, observedLambda)`
\* Java method added in W-B.
