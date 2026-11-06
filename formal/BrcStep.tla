---------------------------- MODULE BrcStep ----------------------------
\* TLA+ formal specification of the BRC-Step atomic-step primitive and the
\* composition contract (matrix-core/src/main/java/io/matrix/reasoning/
\*  BrcStep.java, BrcChain.java)
\*
\* Author: MATRIX SSC
\* Spec version: 2.0 (2026-10-30) — RECON-W38.5
\* Ref: docs-v2/designs/DRAFT-BrcChain.md, vision/DECISIONS.md D-004

EXTENDS Naturals, FiniteSets, Sequences, TLC

CONSTANTS
    Alpha           \* the composition slack — 0/1 knapsack alpha-cushion

\* ── The step set ────────────────────────────────────────────────────────────
\*
\* RECON-W38.5: `Steps` used to be a configuration constant, but the BEHAVIOUR
\* of a step has to be defined somewhere, and the old module defined it as
\* stubs. The step set is now defined here, together with the concrete
\* function each step computes, so that the composition contract is a
\* statement about real functions.

Steps == {"step_1", "step_2", "step_3"}

\* A vector is the SET OF ITS SET BITS, so a vector is a subset of
\* BitPositions and the Hamming distance is a cardinality.
BitPositions == 0..1
Vectors == SUBSET BitPositions

\* ── Step behaviour ──────────────────────────────────────────────────────────
\*
\* io.matrix.reasoning.BrcStep.apply() computes layer.evaluate(padInput(x)),
\* and the layer's evaluation is deterministic — so a step is a TOTAL FUNCTION
\* from Vectors to Vectors. The three concrete steps below are the functions
\* the model reasons about:
\*
\*   step_1  the identity          (output = input)
\*   step_2  the complement        (every bit flipped)
\*   step_3  a threshold detector  (set bit 0 if any bit was set)
ApplyFn(s, x) ==
    IF s = "step_1" THEN x
    ELSE IF s = "step_2" THEN {b ∈ BitPositions : b ∉ x}
    ELSE IF x # {} THEN {0} ELSE {}

\* The Hamming distance between two bit vectors — io.matrix.reasoning.BrcState
\* .hammingDistance() counts the differing bits.
HammingDist(a, b) == Cardinality(a \ b) + Cardinality(b \ a)

\* The convergence threshold (BrcStep's convergenceThreshold).
ConvergenceThreshold == 1

\* ── State ───────────────────────────────────────────────────────────────────

VARIABLES
    applied,        \* applied[s] ∈ Vectors — the last input applied to s
    result,         \* result[s] ∈ Vectors — the last output from s
    stepState       \* stepState[s] ∈ {"idle", "running", "converged"}

vars == <<applied, result, stepState>>

TypeOK ==
    /\ applied   ∈ [Steps -> Vectors]
    /\ result    ∈ [Steps -> Vectors]
    /\ stepState ∈ [Steps -> {"idle", "running", "converged"}]

\* ── Chains ──────────────────────────────────────────────────────────────────
\*
\* io.matrix.reasoning.BrcChain.compose(left, right) concatenates the two
\* chains' step lists: the super-chain runs left's steps first, then right's.
\*
\* RECON-W38.5: the old model had a single `SuperStep` for a PAIR of steps,
\* with SuperStep(s1, s2) == s1 — but the Java composes CHAINS, and the
\* composition of two steps is generally not itself one of the steps. Chains
\* are therefore modelled as sequences of steps here, and `compose` is the
\* Java's concatenation.
MaxChainLen == 3
\* RECON-W38.5: the domain is written `1..n` and NOT `1..n ∈ Nat` — the
\* latter makes TLC check `{} ∈ Nat` for the empty chain (n = 0), which is
\* an error rather than a value.
Chains == UNION { [1..n -> Steps] : n ∈ 0..MaxChainLen }

\* ApplyChain applies a chain's steps IN ORDER: the first element of the
\* sequence runs first, and the last one runs last. That is the order
\* io.matrix.reasoning.BrcChain uses — compose() puts left's steps before
\* right's, and the chain runs them front to back.
\*
\* RECON-W38.5: the first version folded the other way round (the last
\* element was applied first), which made compose(l, r) run right before
\* left and broke the endpoint law. TLC found it immediately.
RECURSIVE ApplyChain(_, _)
ApplyChain(c, x) ==
    IF Len(c) = 0 THEN x
    ELSE ApplyFn(c[Len(c)], ApplyChain(SubSeq(c, 1, Len(c) - 1), x))

\* compose(left, right) — the Java's BrcChain.compose().
compose(l, r) == l \o r

\* ── Init ────────────────────────────────────────────────────────────────────

\* The all-zeros vector, used as the initial input of every step.
ZeroVector == {}

Init ==
    /\ applied   = [s ∈ Steps |-> ZeroVector]
    /\ result    = [s ∈ Steps |-> ApplyFn(s, ZeroVector)]
    /\ stepState = [s ∈ Steps |-> "idle"]

\* ── Apply a single step ─────────────────────────────────────────────────────
\*
\* The Java computes convergence IN THE SAME CALL: BrcStep.apply() returns
\* state.next(output, converged), where converged is
\* checkConvergence(state, output) — the Hamming distance between the applied
\* vector and the produced output, against the threshold. There is no
\* separate "converge" action, and convergence is NOT a latch — every
\* application recomputes it.
ApplyStep(s, x) ==
    /\ s ∈ Steps
    /\ x ∈ Vectors
    /\ applied' = [applied EXCEPT ![s] = x]
    /\ result'  = [result  EXCEPT ![s] = ApplyFn(s, x)]
    /\ stepState' = [stepState EXCEPT ![s] =
            IF HammingDist(x, ApplyFn(s, x)) ≤ ConvergenceThreshold
            THEN "converged" ELSE "running"]
    /\ UNCHANGED <<>>

Next == ∃ s ∈ Steps, x ∈ Vectors : ApplyStep(s, x)

Spec == Init /\ [][Next]_vars

\* ── The state machine ───────────────────────────────────────────────────────
\*
\* RECON-W38.5: the old module's three configured invariants were
\* constant-level — they mentioned only constants and stub helpers and never
\* touched applied/result/stepState, so the state machine was constrained by
\* NOTHING. These two constrain it, and they are the Java's own rules.

\* S1: the recorded output is exactly what the step computes.
AppliedResultMatchesFn ==
    ∀ s ∈ Steps : result[s] = ApplyFn(s, applied[s])

\* S2: convergence is the Java's check — the Hamming distance between the
\*     applied input and the produced output, against the threshold.
ConvergenceIsComputed ==
    ∀ s ∈ Steps :
        stepState[s] # "idle" =>
            (stepState[s] = "converged" <=>
                HammingDist(applied[s], result[s]) ≤ ConvergenceThreshold)

\* ── Composition contract (W-D) ───────────────────────────────────────────────
\*
\* compose(left, right) = the super-chain that runs left first, then right.
\* Endpoints are preserved exactly when both chains use compatible widths;
\* otherwise the super-chain widens and pays a slack of at most Alpha bits.
\*
\* These three are ALGEBRAIC LAWS over the modelled step set: they do not
\* depend on the state variables, and TLC will say so. That is expected for a
\* law of composition — the state machine is constrained by S1 and S2 above.

ComposeEndpointPreserved ==
    ∀ l, r ∈ Chains, x ∈ Vectors :
        HammingDist(ApplyChain(compose(l, r), x),
                    ApplyChain(r, ApplyChain(l, x))) ≤ Alpha

\* S3: Composition associativity — composing three chains in any order gives
\*     the same endpoint up to Alpha.
ComposeAssociative ==
    ∀ a, b, c ∈ Chains, x ∈ Vectors :
        HammingDist(ApplyChain(compose(compose(a, b), c), x),
                    ApplyChain(compose(a, compose(b, c)), x)) ≤ Alpha

\* S4: Identity — composing a chain with the EMPTY chain is a no-op.
\*     RECON-W38.5: the old identity was `IdStep == CHOOSE s ∈ Steps : TRUE` —
\*     an arbitrary step that is not an identity of anything. The Java has no
\*     identity STEP; it has the empty chain, which is the identity of the
\*     concatenation. That is what is modelled here.
ComposeIdentity ==
    ∀ l ∈ Chains, x ∈ Vectors :
        /\ HammingDist(ApplyChain(compose(l, <<>>), x), ApplyChain(l, x)) ≤ Alpha
        /\ HammingDist(ApplyChain(compose(<<>>, l), x), ApplyChain(l, x)) ≤ Alpha

\* ── Liveness ────────────────────────────────────────────────────────────────
\*
\* RECON-W38.5: the old property was "every step eventually converges".
\* That is FALSE of the implementation: with a threshold of 1, a step whose
\* output is always two bits away from its input never converges — and
\* step_2 (the complement) is exactly such a step, in the Java as much as
\* here. The guarantee the Java actually gives is CONDITIONAL, and it is the
\* invariant S2 above. What is left to say is that a convergent step is
\* REACHABLE: applying a step whose output is within the threshold marks it
\* converged, and the identity step always does.
ConvergenceReachable ==
    ∃ s ∈ Steps, x ∈ Vectors : HammingDist(x, ApplyFn(s, x)) ≤ ConvergenceThreshold

=============================================================================
\* Modification History
\* v1.0 (2026-08-27) — initial spec; composition contract with Alpha-bit
\*   slack mirrors the compose() method added to BrcChain in W-D.
\* v2.0 (2026-10-30) — RECON-W38.5. The helpers were stubs (HammingDist == 0,
\*   SuperStep == s1, ApplyFn == CHOOSE ... TRUE), so all three configured
\*   invariants were constant-level and the state machine was constrained by
\*   nothing. The step functions, the Hamming distance and the chain
\*   composition are now real, the composition is modelled as the Java's
\*   chain concatenation, and two state-constraining invariants were added.
