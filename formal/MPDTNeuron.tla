---------------------------- MODULE MPDTNeuron -----------------------------
\* TLA+ formal specification of the MPDT-neuron invariant family.
\* (matrix-core/src/main/java/io/matrix/neuron/TruthTable.java)
\*
\* Author: MATRIX SSC
\* Spec version: 1.0 (2026-07-19)
\* Ref: L1 §3.1, L5 §5.5 (FROZEN), CRITICAL_GAPS.md GAP-006 (k ∈ [1, K_MAX])

EXTENDS Naturals, FiniteSets, TLC

CONSTANTS
    K_MAX,              \* maximum k supported by a TruthTable
    Neurons             \* set of neuron ids

VARIABLES
    k,                  \* function Neurons → ℕ, input count
    table,              \* function Neurons → SUBSET (0..2^k-1) output bits
    inputs,             \* function Neurons → input bits supplied
    outputs             \* function Neurons → boolean output

vars == <<k, table, inputs, outputs>>

\* ── Init ─────────────────────────────────────────────────────────────────────

Init ==
    /\ k \in [Neurons -> 1..K_MAX]
    /\ table \in [n \in Neurons -> SUBSET 0..(2 ^ k[n]) - 1]
    /\ inputs = [n \in Neurons |-> 0]
    /\ outputs = [n \in Neurons |-> FALSE]

\* ── Evaluate ─────────────────────────────────────────────────────────────────

Evaluate(n, idx) ==
    /\ n \in Neurons
    /\ idx \in 0..(2 ^ k[n]) - 1
    /\ inputs' = [inputs EXCEPT ![n] = idx]
    /\ outputs' = [outputs EXCEPT ![n] = (idx \in table[n])]
    /\ UNCHANGED <<k, table>>

\* Bounded by K_MAX — k > K_MAX ⇒ reject (mirrors TruthTable.validate).
InvalidInput(n, idx) ==
    /\ n \in Neurons
    /\ (idx < 0 \/ idx >= 2 ^ k[n])
    /\ UNCHANGED vars

Next ==
    \E n \in Neurons, idx \in 0..(2 ^ k[n]) - 1 : Evaluate(n, idx)

Spec == Init /\ [][Next]_vars

\* ── Properties (Safety) ────────────────────────────────────────────────────────

\* INV-1: K boundedness — k never exceeds K_MAX.
KBounded ==
    [][ \A n \in Neurons : k[n] \in 1..K_MAX ]_k

\* INV-2: outputs is determined by (k, table, inputs) — no hidden state.
OutputDeterminedByInputs ==
    [][ \A n \in Neurons :
            outputs'[n] = (inputs'[n] \in table[n]) ]_outputs

\* INV-3: K_MAX is the system-wide upper bound on k.
KMaxIsGlobalCeiling ==
    \A n \in Neurons : k[n] <= K_MAX

\* INV-4: outputs are stable — re-evaluating with the same input doesn't flip.
OutputStableUnderIdempotentInput ==
    [] \A n \in Neurons :
        inputs[n] = inputs'[n] =>
        outputs[n] = outputs'[n]

=============================================================================
\* Modification History
\* v1.0 (2026-07-19) — initial spec for the MPDT-neuron invariant family.
\* Covers KBounded, OutputDeterminedByInputs, KMaxIsGlobalCeiling,
\* and OutputStableUnderIdempotentInput.
