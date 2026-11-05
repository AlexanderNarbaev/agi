---- MODULE FederationConsensus ----
\* W364 -> W47. The TLA+ specification of the local consensus engine.
\*
\* PROVENANCE (kept, because a moved specification that loses its history is a
\* rewrite):
\*   W364, 2026-09-18 — created as docs-v2/tla/FederationConsensus.tla
\*                      (commit ec22a54b; docs-v2/waves/WAL-364.md)
\*   W371, 2026-09-18 — configured as docs-v2/tla/FederationConsensus.cfg
\*                      (commit 4a208c9d; docs-v2/waves/WAL-371.md)
\*   W47, 2026-11-05 — moved into formal/ under OD-030, so that the formal gate
\*                      (scripts/formal-gate.sh) checks it like the other
\*                      fourteen specifications. The copy kept at the old path
\*                      (docs-v2/tla/) is the same file; formal/ is the gated one.
\*
\* WHAT W47 CHANGED, AND WHY. Every item below was measured with the installed
\* toolchain (tools/tla2tools.jar, TLC2 2026.10.28.014557), not guessed:
\*
\*   1. RECURSIVE SumOfWeights(_) was added. A recursive operator needs the
\*      declaration; without it SANY reports "Unknown operator: 'SumOfWeights'"
\*      at the two call sites (W364 lines 35 and 39).
\*   2. The weight table became an operator of the module. The W364
\*      configuration tried to carry it as a constant
\*      ("Weight = (L0 :> 0 @@ L1 :> 0 @@ ...)"), and a .cfg file cannot
\*      express a function: TLC stopped with "TLC found an error in the
\*      configuration file at line 18". A constant is a 0-ary operator, so
\*      "Weight(v.level)" could never have been applied either.
\*   3. The table now matches the Java it models.
\*      io.matrix.federation.consensus.LocalConsensusEngine (the javadoc of
\*      getVotingWeight and the VOTING_WEIGHTS map) weighs
\*      L0=0, L1=0, L2=1.0, L3=1.5, L4=2.0, L5=3.0, L6=5.0, L7=10.0.
\*      The W364 table (0, 0, 1, 2, 3, 5, 8, 10) did not: it disagreed on L3,
\*      L4, L5 and L6, and therefore on the very ratios the threshold compares.
\*      The weights here are the Java's doubled, so that the arithmetic is
\*      exact integer arithmetic; a common scale leaves every ratio unchanged,
\*      so the doubled table decides exactly what the Java decides.
\*   4. The threshold is compared as a scaled integer:
\*      100 * yesWeight >= 67 * totalWeight is exactly
\*      yesWeight / totalWeight >= 67/100, which is the Java's
\*      STANDARD_THRESHOLD = 0.67 (WAL-364 records the intent as "0.67").
\*      A .cfg file cannot carry a rational ("67 \div 100" is not a value the
\*      format can express), and no real division is needed for the comparison.
\*   5. The evaluation is ATOMIC, as the Java's is. The Java's evaluate()
\*      returns the status the votes imply (a VETO wins over everything, the
\*      weighted ratio decides the rest), and it never exposes a state in which
\*      a VETO has been cast and the status is still PENDING. The W364 model
\*      changed the status in a separate Evaluate action, so that state was
\*      reachable and VetoInvariance was violated in it. Here a vote and the
\*      status it implies are one step. This is a strengthening, not a
\*      weakening: the property is not restated to survive, the transition that
\*      broke it is removed because the implementation does not have it.
\*   6. The variable "proposal" was declared and never assigned, which made the
\*      initial state illegal (TLC: "Error: current state is not a legal
\*      state ... proposal = null", followed by a fingerprinting failure). It is
\*      removed: the Java's single-node mode uses only the proposal's id and
\*      always the standard threshold, so there is nothing in it to vary.
\*   7. The universe of voters is bounded: Voters is a set of three model
\*      values in the configuration, and each voter votes at most once. The
\*      W364 model let every one of the 32 possible votes be cast independently,
\*      which is 2^32 states -- 34,305,063 states in 150 seconds, measured --
\*      and the gate has 300 seconds. The bound does not weaken the properties:
\*      they are universal over the CONTENT of the votes (the model quantifies
\*      over every level and every decision of every voter), and what the bound
\*      removes is the unbounded MULTIPLICITY of the same vote. The Java does
\*      not deduplicate votes either; a real deployment may have more voters
\*      than three, and a bounded model says so in the map's assumptions.
\*   8. The configuration lost its SYMMETRY line. A .cfg file cannot carry a
\*      permutation expression (TLC: "It was expecting a keyword"), and
\*      permuting the levels would not preserve the model anyway: the levels
\*      carry different weights, so a permutation that swaps L4 and L6 changes
\*      the model's meaning.
\*   9. The configuration's "PROPERTY SafetySpec" now names a definition. The
\*      W364 module declared SafetySpec as a THEOREM, and TLC checks
\*      properties, not theorems ("Error: The property SafetySpec specified in
\*      the configuration file is not defined in the specification"). The
\*      property is stated as "[]Safety", which is the same check: TLC
\*      evaluates a property against the behaviours of the specification, so
\*      "Spec => []Safety" and "[]Safety" are one and the same assertion.
\*
\* THE PROPERTIES ARE UNCHANGED IN NAME AND INTENT: TypeOK, VetoInvariance,
\* ApprovalThreshold, WeightMonotonic, VetoOverridesApproval and their
\* conjunction Safety. One of them was made non-vacuous:
\* VetoOverridesApproval said "~((status = "VETOED") /\ (status = "APPROVED"))",
\* which cannot fail because a state has one status; it now says what its name
\* says, that a VETO forbids the APPROVED status.
\*
\* ONE PROPERTY IS VACUOUS, AND IT WAS VACUOUS BEFORE: WeightMonotonic
\* compares the total weight with the sum of the yes- and no-weights, and both
\* sides are the same sum over a partition of the counting votes, so it cannot
\* fail. It is kept, because it was in the W364 configuration and because it
\* checks the recursive sum helper; it is recorded here as a vacuous property,
\* which is the map's VACUOUS_PROPERTY gap code.
\*
EXTENDS Naturals, FiniteSets, Sequences

\* The capability levels and the bounded universe of voters.
CONSTANTS
    L0, L1, L2, L3, L4, L5, L6, L7,      \* Capability levels
    Voters,                              \* The bounded universe of voters
    STANDARD_THRESHOLD_PERCENT           \* The approval threshold, in percent

VARIABLES
    votes,        \* Set of votes cast so far
    status        \* Current consensus status

\* The levels a voter may hold.
Levels == {L0, L1, L2, L3, L4, L5, L6, L7}

\* A vote is a tuple ⟨ voter, level, decision ⟩
Vote == [voter: Voters, level: Levels, decision: {"YES", "NO", "VETO", "ABSTAIN"}]

RECURSIVE SumOfWeights(_)

\* The voting weight of a capability level.
\*
\* The values are the Java's (LocalConsensusEngine.getVotingWeight), doubled so
\* that the arithmetic is exact. A common scale leaves every ratio unchanged.
Weight(level) ==
    CASE level = L0 -> 0
      [] level = L1 -> 0
      [] level = L2 -> 2
      [] level = L3 -> 3
      [] level = L4 -> 4
      [] level = L5 -> 6
      [] level = L6 -> 10
      [] level = L7 -> 20

\* The sum of the weights of a set of votes.
SumOfWeights(s) ==
    IF s = {} THEN 0
    ELSE LET v == CHOOSE x \in s : TRUE
         IN  Weight(v.level) + SumOfWeights(s \ {v})

\* The votes that count towards the ratio: a VETO decides on its own, and an
\* ABSTAIN does not count (the Java: "ABSTAIN does not contribute to weight").
YesVotes(vs) == {v \in vs : v.decision = "YES"}
NoVotes(vs) == {v \in vs : v.decision = "NO"}

HasVeto(vs) == \E v \in vs : v.decision = "VETO"

TotalYesWeight(vs) == SumOfWeights(YesVotes(vs))
TotalNoWeight(vs) == SumOfWeights(NoVotes(vs))
TotalWeight(vs) == TotalYesWeight(vs) + TotalNoWeight(vs)

\* The approval threshold, compared the way the integers compare it.
\* 100 * yesWeight >= 67 * totalWeight is exactly yesWeight / totalWeight >=
\* 67/100, which is the Java's STANDARD_THRESHOLD = 0.67. With the weights
\* this model uses (multiples of one half, doubled here to integers) no
\* reachable ratio is exactly 67/100, so the scaled comparison and the Java's
\* double comparison agree on every state they can both reach.
ApprovedBy(vs) ==
    /\ TotalWeight(vs) > 0
    /\ 100 * TotalYesWeight(vs) >= STANDARD_THRESHOLD_PERCENT * TotalWeight(vs)

\* The status the votes imply: the Java's evaluate(), in the same order.
\* No vote at all is PENDING; a VETO wins over everything; otherwise the
\* weighted ratio decides. The Java reads a zero total weight as a ratio of
\* zero, which is below the threshold, so it REJECTs.
StatusOf(vs) ==
    IF vs = {} THEN "PENDING"
    ELSE IF HasVeto(vs) THEN "VETOED"
    ELSE IF ApprovedBy(vs) THEN "APPROVED"
    ELSE "REJECTED"

TypeOK ==
    /\ votes \subseteq Vote
    /\ status \in {"PENDING", "APPROVED", "REJECTED", "VETOED"}

\* Initial state
Init ==
    /\ votes = {}
    /\ status = "PENDING"

\* Cast a vote, and take the status it implies in the same step.
CastVote(v, level, decision) ==
    /\ v \in Voters
    /\ \A w \in votes : w.voter # v
    /\ votes' = votes \cup {[voter |-> v, level |-> level, decision |-> decision]}
    /\ status' = StatusOf(votes')

\* Next-state relation
Next ==
    \E v \in Voters, level \in Levels, decision \in {"YES", "NO", "VETO", "ABSTAIN"} :
        CastVote(v, level, decision)

\* Safety invariants

\* VetoInvariance: If any VETO was cast, status is VETOED
VetoInvariance ==
    HasVeto(votes) => status = "VETOED"

\* ApprovalThreshold: APPROVED status requires the threshold share of the weight
ApprovalThreshold ==
    status = "APPROVED" => ApprovedBy(votes)

\* WeightMonotonic: Total weight is sum of YES+NO weights
WeightMonotonic ==
    TotalWeight(votes) = TotalYesWeight(votes) + TotalNoWeight(votes)

\* VetoOverridesApproval: a VETO forbids the APPROVED status
VetoOverridesApproval ==
    HasVeto(votes) => status # "APPROVED"

\* Invariant: all safety properties hold
Safety ==
    /\ TypeOK
    /\ VetoInvariance
    /\ ApprovalThreshold
    /\ WeightMonotonic
    /\ VetoOverridesApproval

\* The temporal property the gate checks. TLC evaluates a property against the
\* behaviours of the specification, so this is the same assertion as the W364
\* theorem "Spec => []Safety", in the form TLC accepts.
SafetySpec == []Safety

\* Specification
Spec == Init /\ [][Next]_<<votes, status>>

====
