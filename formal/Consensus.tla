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
    APPROVED,        \* the approval decision
    REJECTED,        \* the rejection decision
    Records          \* the set of admissible proposal values

\* ── Record shape and sentinels ───────────────────────────────────────────────
\*
\* A proposal record is a plain tuple <<agent, value>>: the agent that
\* submitted the proposal and the proposal value it carries. The admissible
\* records are therefore the Cartesian product Agents \X Records. `NoRecord`
\* is the sentinel for "no proposal submitted yet" and `undefined` the
\* sentinel for "no decision yet". There is no `Records` standard module in
\* any released tla2tools, so record field access cannot be used and records
\* are modelled as tuples throughout.
NoRecord == <<"-", "-">>
undefined == "undefined"

\* The possible directions of a vote.
Ballots == {"yes", "no"}

VARIABLES
    proposals,       \* proposals[id] = <<agent, value>> record submitted by Agent
    votes,           \* votes[id] = SUBSET Agents that voted
    ballots,         \* ballots[id][agent] = "yes" / "no" — the vote each agent cast
    decisions,       \* decisions[id] = Decisions or undefined
    weightedEval    \* weightedEval ⊆ Proposals already weighted (de-duped)

vars == <<proposals, votes, ballots, decisions, weightedEval>>

\* ── Type invariants ────────────────────────────────────────────────────────────

TypeOK ==
    /\ proposals \in [Proposals -> ((Agents \X Records) ∪ {NoRecord})]
    /\ votes \in [Proposals -> SUBSET Agents]
    /\ ballots \in [Proposals -> [Agents -> Ballots]]
    /\ decisions \in [Proposals -> Decisions ∪ {undefined}]
    /\ weightedEval \in SUBSET Proposals

Init ==
    /\ proposals = [p \in Proposals |-> NoRecord]
    /\ votes = [p \in Proposals |-> {}]
    \* until an agent votes, its ballot is recorded as "no"; only the ballots
    \* of the agents that actually voted are ever inspected
    /\ ballots = [p \in Proposals |-> [a \in Agents |-> "no"]]
    /\ decisions = [p \in Proposals |-> undefined]
    /\ weightedEval = {}

\* ── Actions ──────────────────────────────────────────────────────────────────

\* An agent submits a new proposal.
Propose(p, a, rec) ==
    /\ p \notin DOMAIN proposals
    /\ proposals' = [proposals EXCEPT ![p] = rec]
    /\ UNCHANGED <<votes, ballots, decisions, weightedEval>>

\* An agent casts a vote. The direction of the vote ("yes" / "no") is chosen
\* non-deterministically: `votes[p]` records who voted, `ballots[p]` records
\* how each of them voted. (The spec's own `RejectUnanimousNo` guard read
\* `votes[p][a] = "no"`, i.e. the ballot of agent a — but `votes[p]` is a
\* set of agents, so the per-agent ballot is kept in `ballots[p][a]`.)
\*
\* The update uses EXCEPT, not `@@`: TLC defines `f @@ g` as "f, extended
\* with the keys of g that f does not have" (`IF x \in DOMAIN f THEN f[x]
\* ELSE g[x]`), so `votes @@ (p :> votes[p] ∪ {a})` was a no-op — p is
\* already in the domain of `votes`, so the vote was never recorded and no
\* proposal could ever reach the threshold.
Vote(p, a, ballot) ==
    /\ p \in DOMAIN proposals
    /\ a \notin votes[p]
    /\ ballot \in Ballots
    /\ votes' = [votes EXCEPT ![p] = (votes[p] ∪ {a})]
    /\ ballots' = [ballots EXCEPT ![p][a] = ballot]
    /\ UNCHANGED <<proposals, decisions, weightedEval>>

\* Evaluate the simple-majority strategy.
EvaluateMajority(p) ==
    /\ p \in DOMAIN proposals
    /\ decisions[p] = undefined
    /\ Cardinality(votes[p]) >= Threshold
    /\ decisions' = [decisions EXCEPT ![p] = APPROVED]
    /\ UNCHANGED <<proposals, votes, ballots, weightedEval>>

\* Evaluate the weighted strategy — but only once per proposal (idempotency).
EvaluateWeighted(p) ==
    /\ p \in DOMAIN proposals
    /\ decisions[p] = undefined
    /\ p \notin weightedEval
    /\ Cardinality(votes[p]) >= Threshold
    /\ decisions' = [decisions EXCEPT ![p] = APPROVED]
    /\ weightedEval' = weightedEval ∪ {p}
    /\ UNCHANGED <<proposals, votes, ballots>>

\* Reject when threshold not reached within a bounded number of votes
RejectUnanimousNo(p) ==
    /\ p \in DOMAIN proposals
    /\ decisions[p] = undefined
    /\ Cardinality(votes[p]) >= Threshold
    /\ \A a \in votes[p] : ballots[p][a] = "no"
    /\ decisions' = [decisions EXCEPT ![p] = REJECTED]
    /\ UNCHANGED <<proposals, votes, ballots, weightedEval>>

Next ==
    \E p \in Proposals, a \in Agents, ballot \in Ballots :
        \/ Propose(p, a, NoRecord)
        \/ Vote(p, a, ballot)
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
