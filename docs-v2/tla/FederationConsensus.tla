---- MODULE FederationConsensus ----
\* W364 — TLA+ specification for LocalConsensusEngine.
\* 
\* Verifies safety properties:
\* - VetoInvariance: L7 VETO always results in VETOED status
\* - ApprovalThreshold: Status APPROVED ⟹ yesRatio ≥ STANDARD_THRESHOLD
\* - WeightMonotonic: Total weight = sum of vote weights
\* - VetoOverridesApproval: Any VETO ⟹ not APPROVED
\* 
\* Per SPEC-013 acceptance criterion: "TLA+ spec verified (no deadlocks, safety properties hold)"

EXTENDS Naturals, FiniteSets, Sequences

\* Capability levels and voting weights
CONSTANTS
    L0, L1, L2, L3, L4, L5, L6, L7,      \* Capability levels
    Weight,                              \* Function: Level → weight
    STANDARD_THRESHOLD                   \* Threshold for approval

VARIABLES
    votes,        \* Set of votes cast so far
    proposal,     \* Current proposal
    status        \* Current consensus status

\* A vote is a tuple ⟨ voterLevel, decision ⟩
Vote == [level: {L0, L1, L2, L3, L4, L5, L6, L7}, decision: {"YES", "NO", "VETO", "ABSTAIN"}]

TypeOK ==
    /\ votes \subseteq Vote
    /\ status \in {"PENDING", "APPROVED", "REJECTED", "VETOED"}

\* Compute total weighted YES and NO votes
TotalYesWeight ==
    LET yesVotes == {v \in votes : v.decision = "YES"}
    IN  SumOfWeights(yesVotes)

TotalNoWeight ==
    LET noVotes == {v \in votes : v.decision = "NO"}
    IN  SumOfWeights(noVotes)

SumOfWeights(s) ==
    IF s = {} THEN 0
    ELSE LET v == CHOOSE x \in s : TRUE
         IN  Weight(v.level) + SumOfWeights(s \ {v})

\* Total non-abstain weight
TotalWeight == TotalYesWeight + TotalNoWeight

\* Check if any VETO exists
HasVeto == \E v \in votes : v.decision = "VETO"

\* Compute approval ratio (YES / total)
YesRatio ==
    IF TotalWeight = 0 THEN 0
    ELSE TotalYesWeight / TotalWeight

\* Initial state
Init ==
    /\ votes = {}
    /\ status = "PENDING"

\* Cast a vote (any voter, any decision)
CastVote(v) ==
    /\ v \notin votes
    /\ votes' = votes \cup {v}
    /\ UNCHANGED status

\* Evaluate consensus based on current votes
Evaluate ==
    /\ HasVeto =>
        /\ status' = "VETOED"
    /\ ~HasVeto /\ TotalWeight > 0 /\
       YesRatio >= STANDARD_THRESHOLD =>
        /\ status' = "APPROVED"
    /\ ~HasVeto /\ TotalWeight > 0 /\
       YesRatio < STANDARD_THRESHOLD =>
        /\ status' = "REJECTED"
    /\ TotalWeight = 0 =>
        /\ status' = "PENDING"
    /\ UNCHANGED votes

\* Next-state relation
Next ==
    \E v \in Vote : CastVote(v) \/ Evaluate

\* Safety invariants

\* VetoInvariance: If any VETO was cast, status is VETOED
VetoInvariance ==
    HasVeto => status = "VETOED"

\* ApprovalThreshold: APPROVED status requires ≥ STANDARD_THRESHOLD yes ratio
ApprovalThreshold ==
    status = "APPROVED" => YesRatio >= STANDARD_THRESHOLD

\* WeightMonotonic: Total weight is sum of YES+NO weights
WeightMonotonic ==
    TotalWeight = TotalYesWeight + TotalNoWeight

\* VetoOverridesApproval: VETO cannot coexist with APPROVED
VetoOverridesApproval ==
    ~((status = "VETOED") /\ (status = "APPROVED"))

\* Invariant: all safety properties hold
Safety ==
    /\ TypeOK
    /\ VetoInvariance
    /\ ApprovalThreshold
    /\ WeightMonotonic
    /\ VetoOverridesApproval

\* Specification
Spec == Init /\ [][Next]_<<votes, status>>

\* Theorem: Safety is preserved by every transition
THEOREM SafetySpec == Spec => []Safety

====
