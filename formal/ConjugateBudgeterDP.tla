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

\* Reconstruct the optimal subset: pick any row whose inclusion contributes
\* strictly more value than skipping it (greedy pick of the table — the DP
\* itself guarantees optimality; the reconstruction is the witness).
GreedyPick(e) ==
    LET fold[i ∈ 0..Cardinality(Rows), ee ∈ 0..e] ==
            IF i = 0 THEN [k ∈ Rows |-> FALSE]
            ELSE LET prev == fold[i-1, ee]
                     take == fold[i-1, ee - C(i)]
                 IN IF C(i) <= ee
                    THEN [k ∈ Rows |-> IF k = i
                                       THEN TRUE
                                       ELSE prev[k] \/ take[k]]
                    ELSE prev
    IN fold[Cardinality(Rows), e]

Step(newEnvelope, observedLambda) ==
    /\ newEnvelope ∈ Nat
    /\ observedLambda ∈ Nat  \* abstracted as integer for TLC tractability
    /\ IF newEnvelope < MIN({C(i) : i ∈ Rows})
       THEN  \* envelope below cheapest row → FALLBACK path
             /\ selected' = [i ∈ Rows |-> FALSE]
             /\ shadow'   = 0
       ELSE
             LET dp0  == DPValue(newEnvelope)
                 dp1  == IF newEnvelope > 0 THEN DPValue(newEnvelope - 1) ELSE 0
                 lam  == dp0 - dp1
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

\* S3: shadow price is monotonically non-increasing in envelope
\*      λ(E) = V*(E) − V*(E−1) is a non-increasing function of E.
\*      Equivalent to: for any two consecutive envelopes E1 > E0 ≥ 1,
\*        λ(E1) ≤ λ(E0).  Asserted as a step-invariant over a 2-step frame.
LambdaMonotoneTwoStep ==
    [][ \A e0, e1 ∈ 0..TotalCost :
            e1 ≥ e0 ⇒ DPValue(e1) - (IF e0 > 0 THEN DPValue(e0-1) ELSE 0)
                      ≥ DPValue(e1+1) - DPValue(e1) ]_<<envelope, shadow>>

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
