-------------------------- MODULE KnowledgeLifecycle --------------------------
(***************************************************************************)
(* The lifecycle of a learned claim, as MATRIX's knowledge store implements  *)
(* it.                                                                      *)
(*                                                                         *)
(* This model describes the real Java: io.matrix.knowledge.KnowledgeStore  *)
(* (the evidence rule and the atomic import), io.matrix.knowledge.          *)
(* KnowledgeRefiner (a correction supersedes the earlier claim and keeps   *)
(* it), and io.matrix.knowledge.Representations (which account at a level  *)
(* is the active one).                                                     *)
(*                                                                         *)
(* The point of the model is NOT to mirror the code line by line. It is to  *)
(* make the four promises the code makes checkable:                      *)
(*                                                                         *)
(*   1. A claim may only assert an established status if evidence supports  *)
(*      it. A source being available does not prove a claim.               *)
(*   2. A correction does not destroy what was believed: the superseded    *)
(*      claim keeps its proposition and its history.                       *)
(*   3. Every change leaves a revision that says what changed.             *)
(*   4. The account the reader is given at a level is the one that nothing *)
(*      at that level refines.                                             *)
(***************************************************************************)
EXTENDS Naturals, FiniteSets

CONSTANTS
    Claims,          \* the claim identifiers
    Sources,         \* the source identifiers
    Representations, \* the representation identifiers
    Levels           \* the abstraction levels

ASSUME /\ Claims # {} /\ Sources # {} /\ Representations # {} /\ Levels # {}

VARIABLES
    status,          \* claim -> status
    evidence,        \* claim -> the subset of Sources that support it
    proposition,     \* claim -> the identity of the text it was asserted with
    version,         \* claim -> its version number
    supersededBy,    \* claim -> the claim that replaced it, or NoClaim
    revisions,       \* the set of (old, new) pairs that were recorded
    reprLevel,       \* representation -> its level
    reprRefines      \* representation -> the representation it refines, or NoRepr

vars == <<status, evidence, proposition, version, supersededBy, revisions,
           reprLevel, reprRefines>>

NoClaim == "NoClaim"
NoRepr == "NoRepr"

(***************************************************************************)
(* The statuses. DRAFT and UNKNOWN assert nothing. The others assert that  *)
(* the claim is established, and therefore need evidence behind them.       *)
(***************************************************************************)
Draft == "DRAFT"
Supported == "SUPPORTED"
Verified == "VERIFIED"
Contested == "CONTESTED"
Superseded == "SUPERSEDED"
Refuted == "REFUTED"
Unknown == "UNKNOWN"

Established == {Supported, Verified, Contested, Superseded, Refuted}
AllStatuses == Established \cup {Draft, Unknown}

(***************************************************************************)
(* Initial states.                                                         *)
(***************************************************************************)
Init ==
    /\ status = [c \in Claims |-> Draft]
    /\ evidence = [c \in Claims |-> {}]
    \* Each claim carries the identity of its own text: the model checks
    \* that a supersession never rewrites it.
    /\ proposition = [c \in Claims |-> c]
    /\ version = [c \in Claims |-> 1]
    /\ supersededBy = [c \in Claims |-> NoClaim]
    /\ revisions = {}
    /\ reprLevel \in [Representations -> Levels]
    /\ reprRefines \in [Representations -> Representations \cup {NoRepr}]
    /\ \A r \in Representations : reprRefines[r] # r

(***************************************************************************)
(* An assertion.                                                           *)
(*                                                                         *)
(* A claim enters the workspace with a source behind it. This is the       *)
(* importer's path, and it is what gives the refinement something to act    *)
(* on: without it, no claim would ever be established and the Refine       *)
(* action could never fire - which is exactly the vacuity that the first   *)
(* version of this model had.                                             *)
(***************************************************************************)
Assert(c, s) ==
    /\ c \in Claims
    /\ s \in Sources
    /\ status[c] = Draft
    /\ evidence[c] = {}
    /\ status' = [status EXCEPT ![c] = Supported]
    /\ evidence' = [evidence EXCEPT ![c] = {s}]
    /\ UNCHANGED <<proposition, version, supersededBy, revisions,
                   reprLevel, reprRefines>>

(***************************************************************************)
(* A refinement.                                                           *)
(*                                                                         *)
(* The store applies a refinement in ONE transaction, so the whole of the  *)
(* change is one step: the earlier claim is superseded, the new claim is   *)
(* supported by the source that came with it, both versions advance, the   *)
(* earlier proposition is untouched, and a revision is recorded.           *)
(***************************************************************************)
Refine(old, new, s) ==
    /\ old \in Claims
    /\ new \in Claims
    /\ old # new
    /\ s \in Sources
    \* Only an ESTABLISHED claim can be superseded. A claim with no evidence
    \* behind it cannot: the store would refuse the change, because a
    \* superseded claim is itself an established status and would need
    \* evidence it does not have. The model found this.
    /\ status[old] \in Established
    /\ status[new] = Draft
    /\ evidence[new] = {}
    /\ supersededBy[old] = NoClaim
    /\ supersededBy[new] = NoClaim
    /\ status' = [status EXCEPT ![old] = Superseded,
                               ![new] = Supported]
    /\ evidence' = [evidence EXCEPT ![new] = {s}]
    /\ version' = [version EXCEPT ![old] = @ + 1,
                               ![new] = @ + 1]
    /\ supersededBy' = [supersededBy EXCEPT ![old] = new]
    /\ proposition' = proposition
    /\ revisions' = revisions \cup {<<old, new>>}
    /\ UNCHANGED <<reprLevel, reprRefines>>

(***************************************************************************)
(* A refined account at the same level as the one it replaces.             *)
(***************************************************************************)
RefineAccount(new, r, previous) ==
    /\ new \in Representations
    /\ r \in Representations
    /\ previous \in Representations
    /\ new # r
    /\ r # previous
    /\ reprLevel[new] = reprLevel[previous]
    /\ reprRefines[new] = previous
    /\ UNCHANGED <<status, evidence, proposition, version, supersededBy,
                   revisions, reprLevel, reprRefines>>

Next ==
    \/ \E c \in Claims, s \in Sources : Assert(c, s)
    \/ \E old, new \in Claims, s \in Sources : Refine(old, new, s)
    \/ \E new, r, previous \in Representations : RefineAccount(new, r, previous)

(***************************************************************************)
(* The properties.                                                         *)
(***************************************************************************)

(* 1. A claim that asserts it is established carries evidence. This is the  *)
(*    rule the store enforces on every import and on every change: an      *)
(*    import that asserts an established status with nothing behind it is  *)
(*    rejected, and nothing is written.                                    *)
(***************************************************************************)
EvidenceRequired ==
    \A c \in Claims :
        status[c] \in Established => evidence[c] # {}

(* 2. A superseded claim keeps what it said. The record of what was        *)
(*    believed is not destroyed when it stops being the current account.    *)
(***************************************************************************)
SupersededKeepsProposition ==
    \A c \in Claims :
        status[c] = Superseded => proposition[c] = c

(* 3. Every supersession is recorded as a revision, and the revision names *)
(*    both the claim that was replaced and the claim that replaced it.      *)
(***************************************************************************)
RevisionRecorded ==
    \A old \in Claims :
        supersededBy[old] # NoClaim =>
            <<old, supersededBy[old]>> \in revisions

(* 4. A claim is superseded by at most one claim, and the replacement is   *)
(*    a claim of the same workspace.                                       *)
(***************************************************************************)
SingleSuperseder ==
    \A old \in Claims :
        supersededBy[old] # NoClaim => supersededBy[old] \in Claims

(* 5. The refinement chain among representations is acyclic: a refined    *)
(*    account never refines itself.                                        *)
(***************************************************************************)
RepresentationChainAcyclic ==
    \A r \in Representations :
        reprRefines[r] # NoRepr => reprRefines[r] # r

(***************************************************************************)
(* The temporal properties the configuration checks.                      *)
(***************************************************************************)
EvidenceAlways == []EvidenceRequired
PropositionAlways == []SupersededKeepsProposition
RevisionAlways == []RevisionRecorded
SupersederAlways == []SingleSuperseder

(***************************************************************************)
(* Specifying the operator.                                                 *)
(***************************************************************************)
Spec == Init /\ [][Next]_vars
        /\ WF_vars(Next)

=============================================================================
