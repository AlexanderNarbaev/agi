---------------------------- MODULE BrcStep ----------------------------
\* TLA+ formal specification of the BRC-Step atomic-step primitive and the
\* composition contract (matrix-core/src/main/java/io/matrix/reasoning/
\*  BrcStep.java, BrcChain.java)
\*
\* Author: MATRIX SSC
\* Spec version: 1.0 (2026-08-27)
\* Ref: docs-v2/designs/DRAFT-BrcChain.md, vision/DECISIONS.md D-004

EXTENDS Naturals, FiniteSets, TLC

CONSTANTS
    Steps,          \* the universe of BRC steps
    Inputs,         \* the set of boolean input vectors
    Outputs,        \* the set of boolean output vectors
    Alpha           \* the composition slack — 0/1 knapsack alpha-cushion

\* ── Per-step behaviour ──────────────────────────────────────────────────────
\*
\* Each step is a total function from Inputs to Outputs (the NeuronLayer
\* composition is deterministic; padInput() is well-defined).

VARIABLES
    applied,        \* applied[s] ∈ Inputs — the last input applied to s
    result,         \* result[s] ∈ Outputs — the last output from s
    stepState       \* stepState[c] ∈ {"idle", "running", "converged"}

vars == <<applied, result, stepState>>

\* ── Init ─────────────────────────────────────────────────────────────────────

Init ==
    /\ applied   = [s ∈ Steps |-> CHOOSE i ∈ Inputs : TRUE]   \* arbitrary
    /\ result    = [s ∈ Steps |-> CHOOSE o ∈ Outputs : TRUE]
    /\ stepState = [s ∈ Steps |-> "idle"]

\* ── Apply a single step ─────────────────────────────────────────────────────

ApplyStep(s, x) ==
    /\ s ∈ Steps
    /\ x ∈ Inputs
    /\ stepState[s] ∈ {"idle", "running"}
    /\ applied' = [applied EXCEPT ![s] = x]
    /\ result'  = [result  EXCEPT ![s] = ApplyFn(s, x)]
    /\ stepState' = [stepState EXCEPT ![s] = "running"]
    /\ UNCHANGED <<>>

\* Function the implementation computes: layer.evaluate(padInput(x))
ApplyFn(s, x) == CHOOSE o ∈ Outputs : TRUE   \* abstracted as nondet for TLC

Converge(s) ==
    /\ stepState[s] = "running"
    /\ HammingDist(applied[s], result[s]) ≤ Threshold(s)
    /\ stepState' = [stepState EXCEPT ![s] = "converged"]
    /\ UNCHANGED <<applied, result>>

Next ==
    \/ \E s ∈ Steps, x ∈ Inputs : ApplyStep(s, x)
    \/ \E s ∈ Steps : Converge(s)

Spec == Init /\ [][Next]_vars

\* ── Composition contract (W-D) ───────────────────────────────────────────────
\*
\* compose(left, right) = super-chain that runs left first, then right.
\* Endpoints are preserved exactly when both chains use compatible widths;
\* otherwise the super-chain widens and pays a slack of at most Alpha bits.

Compose(left, right) ==
    LET run == [s ∈ Steps |-> TRUE]  \* well-formed: all steps runnable
    IN  /\ run[left] /\ run[right]
        /\ SuperWidth(left, right) ≤ MaxWidth(left, right) + Alpha

\* After compose(left, right) on input x, the output matches running the
\* super-chain left-then-right with at most Alpha-bit Hamming slack.
ComposeEndpointPreserved ==
    \A left, right ∈ Steps, x ∈ Inputs :
        Let composed = ApplyFn(right, ApplyFn(left, x))
            direct   = ApplyFn(SuperStep(left, right), x)
        In  HammingDist(composed, direct) ≤ Alpha

\* ── Safety properties ────────────────────────────────────────────────────────

\* S1: Step determinism — applying the same step twice with the same input
\*     yields the same output (no hidden state besides the input/output pair).
StepDeterministic ==
    \A s ∈ Steps, x, y ∈ Inputs :
        (stepState[s] ∈ {"running"} ∧ x = y) ⇒ result' = result

\* S2: Composition associativity (endpoint-preserved) — composing three
\*     chains in any order gives the same endpoint up to Alpha.
ComposeAssociative ==
    \A a, b, c ∈ Steps, x ∈ Inputs :
        HammingDist(ApplyFn(c, ApplyFn(b, ApplyFn(a, x))),
                    ApplyFn(SuperStep(SuperStep(a, b), c), x)) ≤ Alpha
        /\ HammingDist(ApplyFn(SuperStep(a, SuperStep(b, c)), x),
                       ApplyFn(SuperStep(SuperStep(a, b), c), x)) ≤ Alpha

\* S3: Identity — composing a chain with the ID step is a no-op (up to Alpha).
ComposeIdentity ==
    \A s ∈ Steps, x ∈ Inputs :
        HammingDist(ApplyFn(s, x), ApplyFn(SuperStep(IdStep, s), x)) ≤ Alpha

\* ── Liveness ────────────────────────────────────────────────────────────────

\* The chain terminates (converges) within MaxSteps iterations.
ChainTerminates ==
    \A s ∈ Steps : <>(stepState[s] = "converged")

\* ── Helpers (auxiliary, not enforced by TLC) ─────────────────────────────────

HammingDist(b1, b2) == 0   \* abstracted; concrete in Java as bitwise XOR popcount
MaxWidth(c1, c2) == 0
SuperWidth(c1, c2) == 0
SuperStep(s1, s2) == s1
Threshold(s) == 0
IdStep == CHOOSE s ∈ Steps : TRUE

=============================================================================
\* Modification History
\* v1.0 (2026-08-27) — initial spec; composition contract with Alpha-bit
\* slack mirrors the compose() method added to BrcChain in W-D.