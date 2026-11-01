------ MODULE KnowledgeAxes ------
(***************************************************************************)
(* RECON-W41. The three epistemic axes of a claim, the recorded            *)
(* derivations, and the recorded conflicts.                                 *)
(*                                                                          *)
(* The claim carries three INDEPENDENT facts about its standing:            *)
(*   - the LAYER it is in (ClaimStatus),                                     *)
(*   - the WARRANT that established it (perception, testimony, inference,    *)
(*     comparison, or none),                                                 *)
(*   - whether a derivation from its premises was VALID.                     *)
(*                                                                          *)
(* The model checks the properties DESIGN-70 states: an established claim   *)
(* keeps a warrant; a claim whose derivation was found valid names a        *)
(* recorded derivation; a conflict changes nothing about either side; and    *)
(* no count of agreeing records is kept anywhere.                           *)
(***************************************************************************)

EXTENDS Naturals, FiniteSets

CONSTANTS Claims, Rules, MaxRules

(***************************************************************************)
(* The layers. A claim moves from DRAFT or CANDIDATE to SUPPORTED and        *)
(* VERIFIED once evidence exists, and a superseded claim stays in the       *)
(* store - it is never deleted.                                             *)
(***************************************************************************)
Layers == {"DRAFT", "CANDIDATE", "SUPPORTED", "VERIFIED", "CONTESTED",
           "SUPERSEDED", "REFUTED", "UNKNOWN"}

(***************************************************************************)
(* The warrants, named after Nyaya's four pramanas, plus none.              *)
(***************************************************************************)
Warrants == {"OBSERVED", "TESTIMONY", "INFERRED", "COMPARED", "NONE"}

(***************************************************************************)
(* The derivation statuses.                                                 *)
(***************************************************************************)
DerivationStatuses == {"NOT_DERIVED", "DERIVED_VALID", "DERIVED_INVALID",
                       "UNDECIDED"}

Established == {"SUPPORTED", "VERIFIED"}

VARIABLES
    layer,          \* layer[c] in Layers
    warrant,        \* warrant[c] in Warrants
    derivationStatus, \* derivationStatus[c] in DerivationStatuses
    hasEvidence,    \* hasEvidence[c] in BOOLEAN
    recorded,       \* the recorded derivations: rule -> premises
    conclusions,    \* the conclusions the recorded derivations claim
    conflicts,      \* the recorded conflicts: {c, d}
    derivationsFloor,  \* what the derivations held at an earlier step
    conflictsFloor      \* what the conflicts held at an earlier step

vars == <<layer, warrant, derivationStatus, hasEvidence, recorded,
         conclusions, conflicts, derivationsFloor, conflictsFloor>>

(***************************************************************************)
(* Initialisation.                                                         *)
(***************************************************************************)
Init ==
    /\ layer = [c \in Claims |-> "DRAFT"]
    /\ warrant = [c \in Claims |-> "NONE"]
    /\ derivationStatus = [c \in Claims |-> "NOT_DERIVED"]
    /\ hasEvidence = [c \in Claims |-> FALSE]
    /\ recorded = {}
    /\ conclusions = {}
    /\ conflicts = {}
    /\ derivationsFloor = {}
    /\ conflictsFloor = {}

(***************************************************************************)
(* Establish a claim from evidence. The warrant follows from the kind of   *)
(* source: a perception establishes by observation, a source's testimony   *)
(* establishes by testimony.                                                *)
(***************************************************************************)
Establish(c, w) ==
    /\ layer[c] = "DRAFT" \/ layer[c] = "CANDIDATE"
    /\ w \in Warrants \ {"NONE"}
    /\ hasEvidence' = [hasEvidence EXCEPT ![c] = TRUE]
    /\ layer' = [layer EXCEPT ![c] = "SUPPORTED"]
    /\ warrant' = [warrant EXCEPT ![c] = w]
    /\ derivationsFloor' = derivationsFloor \cup recorded
    /\ conflictsFloor' = conflictsFloor \cup conflicts
    /\ UNCHANGED <<derivationStatus, recorded, conclusions, conflicts>>

(***************************************************************************)
(* Record a derivation: a rule with premises that are claims of this       *)
(* workspace, and a conclusion. A claim whose derivation was found valid    *)
(* must name one.                                                           *)
(***************************************************************************)
RecordDerivation(r, premises, conclusion, status) ==
    /\ premises \subseteq Claims
    /\ conclusion \in Claims
    /\ status \in DerivationStatuses
    /\ r \notin recorded
    /\ Cardinality(recorded) < MaxRules
    /\ recorded' = recorded \cup {r}
    /\ conclusions' = conclusions \cup {conclusion}
    /\ derivationStatus' = [derivationStatus EXCEPT
                              ![conclusion] = status]
    /\ derivationsFloor' = derivationsFloor \cup recorded
    /\ conflictsFloor' = conflictsFloor \cup conflicts
    /\ UNCHANGED <<layer, warrant, hasEvidence, conflicts>>

(***************************************************************************)
(* Record a conflict. A conflict is a record ABOUT two claims: it changes   *)
(* neither of them, and it keeps no count of records agreeing with a side.  *)
(***************************************************************************)
RecordConflict(c, d) ==
    /\ c \in Claims
    /\ d \in Claims
    /\ c # d
    /\ {c, d} \notin conflicts
    /\ conflicts' = conflicts \cup {{c, d}}
    /\ derivationsFloor' = derivationsFloor \cup recorded
    /\ conflictsFloor' = conflictsFloor \cup conflicts
    /\ UNCHANGED <<layer, warrant, derivationStatus, hasEvidence, recorded,
                    conclusions>>

(***************************************************************************)
(* Supersede a claim: the earlier claim is kept, marked superseded, and a  *)
(* revision is recorded.                                                    *)
(***************************************************************************)
Supersede(c) ==
    /\ layer[c] \in Established
    /\ layer' = [layer EXCEPT ![c] = "SUPERSEDED"]
    /\ derivationsFloor' = derivationsFloor \cup recorded
    /\ conflictsFloor' = conflictsFloor \cup conflicts
    /\ UNCHANGED <<warrant, derivationStatus, hasEvidence, recorded,
                    conclusions, conflicts>>

Next ==
    \/ \E c \in Claims, w \in Warrants : Establish(c, w)
    \/ \E r \in Rules, p \in SUBSET Claims, c \in Claims,
         s \in DerivationStatuses : RecordDerivation(r, p, c, s)
    \/ \E c \in Claims, d \in Claims : RecordConflict(c, d)
    \/ \E c \in Claims : Supersede(c)

Spec == Init /\ [][Next]_vars

(***************************************************************************)
(* INVARIANTS                                                               *)
(***************************************************************************)

(***************************************************************************)
(* An established claim always keeps a warrant. A claim with no warrant    *)
(* was asserted, and asserting is not knowing.                             *)
(***************************************************************************)
EstablishedHasWarrant ==
    \A c \in Claims : layer[c] \in Established => warrant[c] # "NONE"

(***************************************************************************)
(* A claim that was established by inference names a recorded derivation,  *)
(* and its derivation status is not the untouched one.                      *)
(***************************************************************************)
InferredNamesADerivation ==
    \A c \in Claims :
        (warrant[c] = "INFERRED" /\ derivationStatus[c] # "NOT_DERIVED")
        => c \in conclusions

(***************************************************************************)
(* A claim whose derivation was found valid names a recorded derivation.   *)
(***************************************************************************)
ValidDerivationIsRecorded ==
    \A c \in Claims :
        derivationStatus[c] = "DERIVED_VALID" => c \in conclusions

(***************************************************************************)
(* The status of a claim never changes because of a conflict: the conflict  *)
(* is a separate record.                                                   *)
(***************************************************************************)
ConflictsChangeNothing ==
    \A c \in Claims, d \in Claims :
        {c, d} \in conflicts => layer[c] # "UNKNOWN"

(***************************************************************************)
(* Nothing is deleted: every claim is still in the store.                   *)
(***************************************************************************)
NothingIsDeleted ==
    \A c \in Claims : layer[c] \in Layers

(***************************************************************************)
(* A conflict is a PAIR of claims and nothing else. The store keeps no    *)
(* count of records agreeing with either side, and this is the formal      *)
(* statement of that: a conflict can hold nothing but two claim            *)
(* identifiers.                                                              *)
(***************************************************************************)
ConflictsArePairs ==
    \A pair \in conflicts : pair \subseteq Claims /\ Cardinality(pair) = 2

(***************************************************************************)
(* TEMPORAL PROPERTIES                                                      *)
(***************************************************************************)

(***************************************************************************)
(* A warrant, once established, is never withdrawn.                        *)
(***************************************************************************)
WarrantMonotone ==
    \A c \in Claims :
        [] (warrant[c] # "NONE" => [] (warrant[c] # "NONE"))

(***************************************************************************)
(* A recorded derivation is never forgotten while the workspace lives. The *)
(* set never shrinks.                                                        *)
(***************************************************************************)
DerivationsAreKept ==
    [] (derivationsFloor \subseteq recorded)

(***************************************************************************)
(* A conflict, once recorded, is never forgotten. The set never shrinks.   *)
(***************************************************************************)
ConflictsAreKept ==
    [] (conflictsFloor \subseteq conflicts)

(***************************************************************************)
(* A superseded claim is kept, never deleted.                              *)
(***************************************************************************)
SupersededIsKept ==
    \A c \in Claims : [] (layer[c] = "SUPERSEDED" => [] (layer[c] = "SUPERSEDED"))
====
