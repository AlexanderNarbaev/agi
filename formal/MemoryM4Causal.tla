---------------------------- MODULE MemoryM4Causal ----------------------------
\* TLA+ formal specification of the Memory M4 Causal CRDT layer
\* (matrix-core/src/main/java/io/matrix/noosphere/Crdt.java,
\*  noosphere/GrowOnlySet.java, DESIGN-05 §Memory)
\*
\* Author: MATRIX SSC
\* Spec version: 1.0 (2026-08-27)
\* Ref: docs-v2/designs/drafts/Design-DRAFT-MemoryM4.md, vision/DECISIONS.md D-005

EXTENDS Naturals, FiniteSets, TLC

CONSTANTS
    Replicas,            \* the set of replicas R = {r1, r2, ...}
    Keys,                \* the set of keys that can be added/tombstoned
    Epochs,              \* monotonic integer epochs
    FrozenKeys           \* the set of FROZEN keys — once added, immutable

\* ── State ────────────────────────────────────────────────────────────────────

VARIABLES
    state,               \* state[r] = SUBSET Keys — keys present at replica r
    tombstones,          \* tombstones[r] = SUBSET (Keys × Epochs) — tombstone history at r
    frozenSeen,          \* frozenSeen[r] = SUBSET FrozenKeys — keys observed frozen at r
    vc                   \* vc[r] = [k ∈ Keys |-> epoch] — last-seen insert epoch per key

vars == <<state, tombstones, frozenSeen, vc>>

\* ── Init ─────────────────────────────────────────────────────────────────────

Init ==
    /\ state      = [r ∈ Replicas |-> {}]
    /\ tombstones = [r ∈ Replicas |-> {}]
    /\ frozenSeen = [r ∈ Replicas |-> {}]
    /\ vc         = [r ∈ Replicas |-> [k ∈ Keys |-> 0]]

\* ── Insert: add a key at the given epoch ────────────────────────────────────
\*
\* Preconditions:
\*   - epoch ≥ vc[r][k]  (causal: this insert causally depends on the last one)
\*   - ¬tombstonedBy(r, k, epoch)  (no tombstone covers this epoch)
\* Postcondition: state[r] gains k.

Insert(r, k, epoch) ==
    /\ r ∈ Replicas
    /\ k ∈ Keys \ FrozenKeys         \* frozen keys cannot be inserted at all
    /\ epoch ∈ Epochs
    /\ epoch ≥ vc[r][k]
    /\ ∀t ∈ Epochs : ⟨k, t⟩ ∈ tombstones[r] ⇒ t > epoch   \* no tombstone before/equal
    /\ state'      = [state      EXCEPT ![r] = state[r] ∪ {k}]
    /\ vc'         = [vc         EXCEPT ![r] = [vc[r] EXCEPT ![k] = epoch]]
    /\ UNCHANGED <<tombstones, frozenSeen>>

\* ── Tombstone: irreversibly mark a key as deleted from epoch E onward ──────

Tombstone(r, k, epoch) ==
    /\ r ∈ Replicas
    /\ k ∈ Keys \ FrozenKeys
    /\ epoch ∈ Epochs
    /\ state'      = [state      EXCEPT ![r] = state[r] \ {k}]
    /\ tombstones' = [tombstones EXCEPT ![r] = tombstones[r] ∪ {⟨k, epoch⟩}]
    /\ UNCHANGED <<frozenSeen, vc>>

\* ── Merge: receive state from another replica ───────────────────────────────
\*
\* Causal merge preserves both states' progress. Tombstones "win" over any
\* state entry whose epoch ≤ tombstone epoch (irreversibility).

Merge(r1, r2) ==
    /\ r1 ∈ Replicas
    /\ r2 ∈ Replicas
    /\ r1 # r2
    /\ state' = [state EXCEPT
        ![r1] = (state[r1] ∪ state[r2])
                 \ {k ∈ Keys : ∃t ∈ Epochs :
                                  ⟨k, t⟩ ∈ tombstones[r1] ∪ tombstones[r2]
                                  ∧ vc[r1][k] ≤ t}]
    /\ tombstones' = [tombstones EXCEPT ![r1] = tombstones[r1] ∪ tombstones[r2]]
    /\ vc'        = [vc EXCEPT ![r1] = [k ∈ Keys |-> Max({vc[r1][k], vc[r2][k]})]]
    /\ UNCHANGED <<frozenSeen>>

\* ── Freeze: explicitly mark a key as FROZEN (ethics/FROZEN zone analog) ────

Freeze(r, k) ==
    /\ r ∈ Replicas
    /\ k ∈ FrozenKeys
    /\ frozenSeen' = [frozenSeen EXCEPT ![r] = frozenSeen[r] ∪ {k}]
    /\ UNCHANGED <<state, tombstones, vc>>

\* ── Next ────────────────────────────────────────────────────────────────────

Next ==
    \/ \E r ∈ Replicas, k ∈ Keys, e ∈ Epochs : Insert(r, k, e)
    \/ \E r ∈ Replicas, k ∈ Keys, e ∈ Epochs : Tombstone(r, k, e)
    \/ \E r1, r2 ∈ Replicas : Merge(r1, r2)
    \/ \E r ∈ Replicas, k ∈ FrozenKeys : Freeze(r, k)

Spec == Init /\ [][Next]_vars

\* ── Safety properties ────────────────────────────────────────────────────────

\* F1: Monotonicity — once a key enters state[r], no operation removes it
\*     unless an explicit Tombstone removes it.
MonotonicityExceptTombstone ==
    [][ \A r ∈ Replicas, k ∈ Keys :
            (k ∈ state'[r] ∧ ⟨k, Epochs⟩ ∉ tombstones'[r])
            ⇒ k ∈ state[r] ]_state

\* F2: TombstoneIrreversible — once ⟨k, E⟩ ∈ tombstones[r], no subsequent
\*     operation may put k back into state[r] with insert epoch ≤ E.
TombstoneIrreversible ==
    [][ \A r ∈ Replicas, k ∈ Keys, E ∈ Epochs :
            ⟨k, E⟩ ∈ tombstones[r]
            ⇒ [][ ¬(k ∈ state[r] ∧ vc[r][k] ≤ E) ]_<<state, vc>> ]_tombstones

\* F3: EventualConsistency — after enough Merge steps, all replicas agree
\*     on the keys they hold (up to tombstone filtering).
EventualConsistency ==
    \A r1, r2 ∈ Replicas :
        <>[(state[r1] \ {k : ∃t : ⟨k, t⟩ ∈ tombstones[r1]})
           = (state[r2] \ {k : ∃t : ⟨k, t⟩ ∈ tombstones[r2]})]_state

\* F4: FrozenImmutability — FROZEN keys, once observed frozen at a replica,
\*     never re-enter the state by any later operation.
FrozenImmutability ==
    [][ \A r ∈ Replicas, k ∈ FrozenKeys :
            k ∈ frozenSeen[r] ⇒ k ∉ state'[r] \ state[r] ]_<<frozenSeen, state>>

\* ── Auxiliary helper ────────────────────────────────────────────────────────

tombstonedBy(r, k, epoch) ==
    \E t ∈ Epochs : ⟨k, t⟩ ∈ tombstones[r] ∧ t ≤ epoch

=============================================================================
\* Modification History
\* v1.0 (2026-08-27) — initial spec; defines the four core invariants of
\* the M4 Causal CRDT layer (monotonicity, tombstone irreversibility,
\* eventual consistency, frozen-immutability). Mirrors the Java additions
\* in noosphere/Crdt.java (mergeCausal, tombstoneAt).