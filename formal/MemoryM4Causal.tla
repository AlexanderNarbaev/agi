---------------------------- MODULE MemoryM4Causal ----------------------------
\* TLA+ formal specification of the Memory M4 Causal CRDT layer
\* (matrix-core/src/main/java/io/matrix/noosphere/EpochAwareCausalSet.java;
\*  see also noosphere/GrowOnlySet.java — the two-phase set variant — and
\*  docs-v2/architecture/ADR-2026-10-30-004-noosphere-crdt-semantics.md)
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

\* ── Helper: does a tombstone cover an insert at this epoch? ────────────────
\*
\* RECON-W38.5: "a tombstone covers an insert at this epoch" means the
\* tombstone is AT OR AFTER the insert - the insert is not newer than the
\* tombstone, so the tombstone wins. The previous definition used `t ≤ epoch`,
\* which made the helper agree with the Insert guard and disagreed with F2
\* (F2 forbids a re-insert with insert epoch ≤ E, i.e. it requires the insert
\* to be NEWER than every tombstone of that key).
\*
\* The helper was dead code: it was referenced by the Insert precondition's
\* COMMENT but never called. It is now the guard itself, so the comment, the
\* helper and F2 all say the same thing.
tombstonedBy(r, k, epoch) ==
    \E t ∈ Epochs : ⟨k, t⟩ ∈ tombstones[r] ∧ t ≥ epoch

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
    \* RECON-W38.5: the guard now CALLS the helper, and the helper's direction
    \* was corrected (see below). The previous inline conjunct
    \* `∀t : ⟨k,t⟩ ∈ tombstones[r] ⇒ t > epoch` read "the insert must be OLDER
    \* than every tombstone of the key" - which is the exact opposite of F2,
    \* and made F2 false in every state where a key was tombstoned and then
    \* re-inserted. F2 requires the opposite: an insert must be strictly NEWER
    \* than every tombstone of that key, so the tombstone is not resurrected
    \* at an epoch it should have covered.
    /\ ¬tombstonedBy(r, k, epoch)
    /\ state'      = [state      EXCEPT ![r] = state[r] ∪ {k}]
    /\ vc'         = [vc         EXCEPT ![r] = [vc[r] EXCEPT ![k] = epoch]]
    /\ UNCHANGED <<tombstones, frozenSeen>>

\* ── Tombstone: irreversibly mark a key as deleted from epoch E onward ──────

Tombstone(r, k, epoch) ==
    /\ r ∈ Replicas
    /\ k ∈ Keys \ FrozenKeys
    /\ epoch ∈ Epochs
    \* RECON-W39: the removal used to be unconditional. A tombstone at epoch
    \* E is a LOGICAL CLOCK, not a wall-clock stamp: it means "deleted as of
    \* logical time E". A key that already holds a STRICTLY NEWER insert
    \* epoch (vc[r][k] > E) is therefore not deleted by it — that value was
    \* written after the deletion in the logical order, and deleting it would
    \* be a stale delete. This is the same rule io.matrix.noosphere
    \* .EpochAwareCausalSet implements (a key is live iff its insert epoch is
    \* strictly greater than its tombstone epoch).
    \*
    \* F2 is unaffected: F2 only forbids a key that is in the state with
    \* vc[r][k] ≤ E, and exactly that case is the one where the key is now
    \* removed.
    /\ state'      = [state      EXCEPT ![r] =
                          IF epoch ≥ vc[r][k] THEN state[r] \ {k} ELSE state[r]]
    /\ tombstones' = [tombstones EXCEPT ![r] = tombstones[r] ∪ {⟨k, epoch⟩}]
    /\ UNCHANGED <<frozenSeen, vc>>

\* ── Merge: receive state from another replica ───────────────────────────────
\*
\* Causal merge preserves both states' progress. Tombstones "win" over any
\* state entry whose epoch ≤ tombstone epoch (irreversibility).
\*
\* Max is not provided by any TLA+ standard module, so it is defined here;
\* Merge uses it to raise the vector clock to the later of the two epochs.
\* (SANY requires an operator to be defined before it is used.)

Max(S) == CHOOSE m ∈ S : \A s ∈ S : s ≤ m

Merge(r1, r2) ==
    /\ r1 ∈ Replicas
    /\ r2 ∈ Replicas
    /\ r1 # r2
    \* RECON-W39: the filter used the RECEIVER's pre-merge clock vc[r1][k]
    \* while the very next conjunct raises that clock to
    \* Max(vc[r1][k], vc[r2][k]). The two disagreed: a merge could keep a
    \* key that the new clock says is covered by a tombstone, and drop a key
    \* that a NEWER insert on the other side had legitimately resurrected.
    \* The filter now uses the same merged clock that vc' installs, which is
    \* also the rule io.matrix.noosphere.EpochAwareCausalSet implements: a
    \* key is live iff its insert epoch is strictly greater than its tombstone
    \* epoch.
    /\ state' = [state EXCEPT
        ![r1] = (state[r1] ∪ state[r2])
                 \ {k ∈ Keys : ∃t ∈ Epochs :
                                  ⟨k, t⟩ ∈ tombstones[r1] ∪ tombstones[r2]
                                  ∧ Max({vc[r1][k], vc[r2][k]}) ≤ t}]
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
\*
\* RECON-W38.5: the implication was INVERTED with respect to its own comment.
\* As written it said "a key may only APPEAR if a tombstone does not cover
\* it" - i.e. it forbade ADDING a key, which the Insert action does by
\* construction, so the property was false in the second state of every run.
\* The comment (and the Java this models) say the opposite: the set only
\* grows, and a key leaves it only by being tombstoned.
\*
\* io.matrix.noosphere.GrowOnlySet is exactly this: "a CRDT that only supports
\* additions ... once a neuron identity is added to the set, it is never
\* removed" - and its only removal path, tombstoneAt(), records a tombstone
\* in the same step. This is that rule.
MonotonicityExceptTombstone ==
    [][ \A r ∈ Replicas, k ∈ Keys :
            (k ∈ state[r] ∧ k \notin state'[r])
            ⇒ (\E E ∈ Epochs : ⟨k, E⟩ ∈ tombstones'[r]) ]_state

\* F2: TombstoneIrreversible — once ⟨k, E⟩ ∈ tombstones[r], no subsequent
\*     operation may put k back into state[r] with insert epoch ≤ E.
\*     Tombstones are never removed, so checking the implication in every
\*     state in which the tombstone is present is equivalent to checking it
\*     from the moment the tombstone is created onwards.
TombstoneIrreversible ==
    \A r ∈ Replicas, k ∈ Keys, E ∈ Epochs :
        ⟨k, E⟩ ∈ tombstones[r]
        ⇒ ¬(k ∈ state[r] ∧ vc[r][k] ≤ E)

\* F3: EventualConsistency — after enough Merge steps, all replicas agree
\*     on the keys they hold (up to tombstone filtering).
EventualConsistency ==
    <> \A r1, r2 ∈ Replicas :
        (state[r1] \ {k ∈ Keys : ∃t ∈ Epochs : ⟨k, t⟩ ∈ tombstones[r1]})
        = (state[r2] \ {k ∈ Keys : ∃t ∈ Epochs : ⟨k, t⟩ ∈ tombstones[r2]})

\* F5: PresentImpliesUncovered — a key that is in the state is not covered
\*     by a tombstone that covers its clock.
\*
\* RECON-W39: this property and its twin below were added together with the
\* two corrections they pin. The correction to the Merge action (the filter
\* must use the MERGED clock, not the receiver's pre-merge clock) was NOT
\* detectable by F1-F4: reverting it left every existing property holding.
\* That is what the mutation harness found, and it is exactly the kind of
\* blind spot a property set is supposed not to have.

PresentImpliesUncovered ==
    [][ \A r \in Replicas, k \in Keys :
            (k \in state[r])
            => ¬(\E E \in Epochs : ⟨k, E⟩ \in tombstones[r] ∧ E ≥ vc[r][k])
    ]_state

\* F6: AbsentImpliesCovered — a key that has been inserted (its clock is
\*     above zero) and is absent from the state is covered by a tombstone
\*     that covers its clock.
\*
\* This pins the correction to the Tombstone action: a tombstone at a STALE
\* epoch does not delete a strictly newer value, so the key stays in the
\* state. Reverting the correction to an unconditional removal violated this
\* property, and violated no other.

AbsentImpliesCovered ==
    [][ \A r \in Replicas, k \in Keys :
            (k \notin state[r] ∧ vc[r][k] > 0)
            => (\E E \in Epochs : ⟨k, E⟩ \in tombstones[r] ∧ E ≥ vc[r][k])
    ]_state

\* F4: FrozenImmutability — FROZEN keys, once observed frozen at a replica,
\*     never re-enter the state by any later operation.
FrozenImmutability ==
    [][ \A r ∈ Replicas, k ∈ FrozenKeys :
            k ∈ frozenSeen[r] ⇒ k ∉ state'[r] \ state[r] ]_<<frozenSeen, state>>

\* ── Auxiliary helper ────────────────────────────────────────────────────────


=============================================================================
\* Modification History
\* v1.0 (2026-08-27) — initial spec; defines the four core invariants of
\* the M4 Causal CRDT layer (monotonicity, tombstone irreversibility,
\* eventual consistency, frozen-immutability). Mirrors the Java additions
\* in noosphere/Crdt.java (mergeCausal, tombstoneAt).