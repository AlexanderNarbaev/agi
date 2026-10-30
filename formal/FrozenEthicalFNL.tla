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

\* A single activation step: evaluate the whole FNL against one feature set.
\*
\* RECON-W38.5: the step used to fire ONE axiom per step and accumulate it
\* into activated[features]. The Java does not work that way:
\*
\*   io.matrix.ethics.frozen.FrozenAxiomNeuron.activate(BitSet) is a PURE
\*   function of (truth table, features) - it recomputes the index and calls
\*   table.evaluate(idx), with no state to accumulate.
\*
\*   io.matrix.ethics.frozen.FrozenEthicalFNL.evaluate(BitSet) then calls
\*   activate() on EVERY neuron against the SAME features and returns the
\*   first one that fires.
\*
\* So the fired set for a feature set is not built up over time: it is
\* computed in one evaluation, and it is exactly the set of neurons whose
\* trigger covers that feature set. The model now says that, and the three
\* properties below stop contradicting each other.
Activate(features) ==
    /\ features \subseteq FeatureBits
    /\ \E a \in Axioms : features \in Triggers[a]
    /\ activated' = [activated EXCEPT ![features] =
                        {neurons[a] : a \in {x \in Axioms : features \in Triggers[x]}}]
    /\ UNCHANGED <<neurons>>

Next ==
    \E features \in SUBSET FeatureBits : Activate(features)

\* RECON-W38.5: the search must not starve a feature set. Weak fairness per
\* feature set says exactly that: while a feature set has a trigger, its
\* evaluation eventually happens. The Java evaluates a feature set when a
\* request arrives, so this models the arrival of requests for it.
Spec == Init /\ [][Next]_vars
        /\ (\A features \in SUBSET FeatureBits : WF_vars(Activate(features)))

\* ── Properties (Safety Invariants) ──────────────────────────────────────────────

\* FROZEN INVARIANT 1: the set of neurons never changes after Init.
NeuronSetImmutable ==
    [][ neurons' = neurons ]_neurons

\* FROZEN INVARIANT 2: activation is deterministic — same features ⇒ same
\*     fired set.
\*
\* RECON-W38.5: the formula was `activated'[features] = activated[features]`,
\* i.e. "the fired set NEVER changes". That is not what determinism means, and
\* it contradicted the module's own activation step: a feature set that fires
\* a neuron is exactly the case where the fired set changes. The property
\* that IS determinism, and that the Java guarantees, is that the fired set
\* is a function of the FEATURES alone: a feature set's fired set is either
\* empty (never evaluated) or exactly the canonical set - never a partial
\* accumulation. The Java's purity (a pure function of table and features,
\* recomputed on every call) is the same guarantee.
DeterministicActivation ==
    [][ \A features \in SUBSET FeatureBits :
            activated'[features] \in
                { {},
                  {neurons[a] : a \in {x \in Axioms : features \in Triggers[x]}} } ]_activated

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
\* RECON-W38.5: the old formula demanded that EVERY feature set's fired set
\* equal the canonical set - including the feature sets that were never
\* evaluated, whose fired set is legitimately empty. It now says what the
\* Java guarantees: a feature set that has fired anything has fired exactly
\* the canonical set, so no neuron is missing and none is extra.
NeuronsAreCanonical ==
    [][ \A features \in SUBSET FeatureBits :
            activated'[features] # {} =>
                activated'[features] =
                    {neurons[a] : a \in {x \in Axioms : features \in Triggers[x]}} ]_activated

\* ── Properties (Liveness) ──────────────────────────────────────────────────────

\* For every non-trivial feature set, eventually some neuron fires (if any
\* does) - and once it has, it stays.
\*
\* RECON-W38.5: the formula was `[]<>(...)` - "always, eventually" - which is
\* the wrong shape for the claim. A feature set with a trigger has an EMPTY
\* fired set until it is evaluated, so the biconditional is false before the
\* evaluation and true after it; `[]<>` would therefore demand that it be
\* true from the very first state, which no evaluation order can deliver.
\* The claim the comment makes is "eventually some neuron fires, and it stays":
\* `<>[]`.
Liveness ==
    <>[](\A features \in SUBSET FeatureBits :
        (activated[features] # {}) <=> (\E a \in Axioms : features \in Triggers[a]))

=============================================================================
\* Modification History
\* v1.0 (2026-07-19) — initial spec; verifies FROZEN invariants for the
\* ethical FNL network. Mirrors the Java implementation in
\* io.matrix.ethics.frozen.FrozenEthicalFNL.
