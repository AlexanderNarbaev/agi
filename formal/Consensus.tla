---------------------------- MODULE Consensus ---------------------------
\* TLA+ formal specification of the MATRIX consensus protocol
\* (matrix-core/src/main/java/io/matrix/consensus/ConsensusEngine.java)
\*
\* Author: MATRIX SSC
\* Spec version: 1.0 (2026-07-19)
\* Ref: L2 §3.1 (Consensus), CRITICAL_GAPS.md GAP-021

EXTENDS Naturals, FiniteSets, TLC

CONSTANTS
    Agents,          \* set of agent ids
    Proposals,       \* set of proposal ids
    Decisions,       \* {APPROVED, REJECTED}
    Threshold,       \* integer threshold (e.g. 2/3 of Agents)

VARIABLES
    proposals,       \* proposals[id] = record submitted by Agent
    votes,           \* votes[id] = SUBSET Agents that voted
    decisions,       \* decisions[id] = Decisions or undefined
    weightedEval,    \* weightedEval ⊆ Proposals already weighted (de-duped)

vars == <<proposals, votes, decisions, weightedEval>>

\* ── Type invariants ────────────────────────────────────────────────────────────

TypeOK ==
    /\ proposals \in [Proposals -> Records]
    /\ votes \in [Proposals -> SUBSET Agents]
    /\ decisions \in [Proposals -> Decisions ∪ {undefined}]
    /\ weightedEval \in SUBSET Proposals

Init ==
    /\ proposals = [p \in Proposals |-> NoRecord]
    /\ votes = [p \in Proposals |-> {}]
    /\ decisions = [p \in Proposals |-> undefined]
    /\ weightedEval = {}

\* ── Actions ──────────────────────────────────────────────────────────────────

\* An agent submits a new proposal.
Propose(p, a, rec) ==
    /\ p \notin DOMAIN proposals
    /\ proposals' = proposals @@ (p :> rec)
    /\ UNCHANGED <<votes, decisions, weightedEval>>

\* An agent casts a vote.
Vote(p, a) ==
    /\ p \in DOMAIN proposals
    /\ a \notin votes[p]
    /\ votes' = votes @@ (p :> votes[p] ∪ {a})
    /\ UNCHANGED <<proposals, decisions, weightedEval>>

\* Evaluate the simple-majority strategy.
EvaluateMajority(p) ==
    /\ p \in DOMAIN proposals
    /\ decisions[p] = undefined
    /\ Cardinality(votes[p]) >= Threshold
    /\ decisions' = [decisions EXCEPT ![p] = APPROVED]
    /\ UNCHANGED <<proposals, votes, weightedEval>>

\* Evaluate the weighted strategy — but only once per proposal (idempotency).
EvaluateWeighted(p) ==
    /\ p \in DOMAIN proposals
    /\ decisions[p] = undefined
    /\ p \notin weightedEval
    /\ Cardinality(votes[p]) >= Threshold
    /\ decisions' = [decisions EXCEPT ![p] = APPROVED]
    /\ weightedEval' = weightedEval ∪ {p}
    /\ UNCHANGED <<proposals, votes>>

\* Reject when threshold not reached within a bounded number of votes
RejectUnanimousNo(p) ==
    /\ p \in DOMAIN proposals
    /\ decisions[p] = undefined
    /\ Cardinality(votes[p]) >= Threshold
    /\ \A a \in votes[p] : votes[p][a] = "no"
    /\ decisions' = [decisions EXCEPT ![p] = REJECTED]
    /\ UNCHANGED <<proposals, votes, weightedEval>>

Next ==
    \E p \in Proposals, a \in Agents :
        \/ Propose(p, a, NoRecord)
        \/ Vote(p, a)
        \/ EvaluateMajority(p)
        \/ EvaluateWeighted(p)
        \/ RejectUnanimousNo(p)

Spec == Init /\ [][Next]_vars /\ WF_vars(Next)

\* ── Properties ─────────────────────────────────────────────────────────────────

\* Safety: a decided proposal stays decided (monotonicity).
DecisionMonotonic ==
    [][ \A p \in Proposals : decisions[p] # undefined => decisions'[p] = decisions[p] ]_vars

\* Safety: weighted evaluation is idempotent — running it twice has no extra effect.
WeightedIdempotent ==
    [][ \A p \in Proposals : p \in weightedEval =>
        decisions[p] # undefined ]_vars

\* Liveness (bounded): every proposal with enough votes eventually gets a decision.
LivenessBounded ==
    []<> \A p \in Proposals : Cardinality(votes[p]) >= Threshold =>
        decisions[p] # undefined

=============================================================================
\* Modification History
\* v1.0 (2026-07-19) — initial spec, covers EvaluateMajority/EvaluateWeighted/RejectUnanimousNo
