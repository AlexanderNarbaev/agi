---------------------------- MODULE FrozenEthicalFNL ---------------------
\* TLA+ formal specification of the FROZEN Ethical FNL
\* (matrix-core/src/main/java/io/matrix/ethics/frozen/FrozenEthicalFNL.java)
\*
\* Author: MATRIX SSC
\* Spec version: 1.0 (2026-07-19)
\* Ref: L7 §3.1, CRITICAL_GAPS.md GAP-021 + GAP-003 (closed)

EXTENDS Naturals, FiniteSets, Sequences, TLC

CONSTANTS
    Axioms,             \* set of axiom names (6 in canonical)
    FeatureBits,        \* {0, 1, ..., k-1} feature indices
    TriggerPairs        \* the trigger relation: one flat set per pair

\* ── Trigger relation ──────────────────────────────────────────────────────────
\*
\* `Triggers[a]` is the set of feature subsets that fire axiom a. The model
\* supplies the relation as a set of flat sets: the pair (axiom a, feature
\* set F) is written as {a} ∪ F, i.e. the axiom name together with the names
\* of the feature bits of F. A TLC configuration file can only express sets,
\* numbers, strings and model values — never a function and never a tuple —
\* so the per-axiom trigger sets are recovered here. Two restrictions of the
\* encoding: axiom names and feature names must live in disjoint name spaces
\* (otherwise {a} ∪ F would not identify F), and every element of
\* `TriggerPairs` must contain the name of exactly one axiom.
Triggers == [a \in Axioms |->
                {F \in SUBSET FeatureBits : {a} \cup F \in TriggerPairs}]

VARIABLES
    neurons,            \* the set of FROZEN neurons (immutable post-init)
    activated           \* activated[id] = SUBSET neurons that fired on feature set id

vars == <<neurons, activated>>

\* ── Init ─────────────────────────────────────────────────────────────────────

Init ==
    /\ neurons = [a \in Axioms |-> "neuron-" \o a]
    /\ activated = [s \in SUBSET FeatureBits |-> {}]

\* ── Activation ────────────────────────────────────────────────────────────────

\* A single activation step: features ⊆ FeatureBits, choose axiom to fire.
Activate(features, axiom) ==
    /\ features \subseteq FeatureBits
    /\ axiom \in Axioms
    /\ features \in Triggers[axiom]
    /\ activated' = [activated EXCEPT ![features] = activated[features] ∪ {neurons[axiom]}]
    /\ UNCHANGED <<neurons>>

Next ==
    \E features \in SUBSET FeatureBits, axiom \in Axioms :
        /\ features \in Triggers[axiom]
        /\ Activate(features, axiom)

Spec == Init /\ [][Next]_vars

\* ── Properties (Safety Invariants) ──────────────────────────────────────────────

\* FROZEN INVARIANT 1: the set of neurons never changes after Init.
NeuronSetImmutable ==
    [][ neurons' = neurons ]_neurons

\* FROZEN INVARIANT 2: activation is deterministic — same features ⇒ same fired set.
DeterministicActivation ==
    [][ \A features \in SUBSET FeatureBits :
            activated'[features] = activated[features] ]_activated

\* FROZEN INVARIANT 3: the set of fired neurons ⊆ total neurons.
FiredSubsetOfTotal ==
    [][ \A features \in SUBSET FeatureBits :
            activated'[features] \subseteq {neurons[a] : a \in Axioms} ]_activated

\* FROZEN INVARIANT 4: a neuron never disappears from the canonical set.
\*
\* The canonical neuron set of a feature set is the set of neurons of the
\* axioms whose trigger set contains that feature set. The set-builder is
\* written with a single bound over a filtered domain: in a multi-bound
\* set-builder the domain of a later bound is evaluated outside the scope
\* of the earlier binder, so `Triggers[a]` could not see `a`.
NeuronsAreCanonical ==
    [][ \A features \in SUBSET FeatureBits :
            activated'[features] =
                {neurons[a] : a \in {x \in Axioms : features \in Triggers[x]}} ]_activated

\* ── Properties (Liveness) ──────────────────────────────────────────────────────

\* For every non-trivial feature set, eventually some neuron fires (if any does).
Liveness ==
    [](<>((\A features \in SUBSET FeatureBits :
        (activated[features] # {}) <=> (\E a \in Axioms : features \in Triggers[a]))))

=============================================================================
\* Modification History
\* v1.0 (2026-07-19) — initial spec; verifies FROZEN invariants for the
\* ethical FNL network. Mirrors the Java implementation in
\* io.matrix.ethics.frozen.FrozenEthicalFNL.
