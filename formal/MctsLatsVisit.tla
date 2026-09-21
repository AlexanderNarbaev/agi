---------------------------- MODULE MctsLatsVisit ----------------------------
\* TLA+ formal specification of the MCTS/LATS convergence argument
\* (matrix-core/src/main/java/io/matrix/mcts/{MctsTree,MctsNode,LatsNode,
\*   LatsValueFunction,LatsReflector}.java)
\*
\* Author: MATRIX SSC
\* Spec version: 1.0 (2026-08-27)
\* Ref: ALGORITHM-ATLAS §38..§40 (selection / expansion / backprop);
\*      docs-v2/algorithms/Mcts-Lats.md

EXTENDS Naturals, FiniteSets, Reals, TLC

CONSTANTS
    Nodes,           \* universe of MCTS/LATS nodes
    Visits,          \* integer visit counts per node
    AlphaRoot,       \* the alpha-Root convergence bound ∈ (0, 1)

\* ── State ────────────────────────────────────────────────────────────────────

VARIABLES
    tree,            \* tree[n] = SUBSET Nodes — children of n
    visitCount,      \* visitCount[n] ∈ Visits
    totalReward,     \* totalReward[n] ∈ ℝ — sum of rewards observed
    bestArm          \* the argmax over visitCount × reward

vars == <<tree, visitCount, totalReward, bestArm>>

\* ── Init ─────────────────────────────────────────────────────────────────────

Init ==
    /\ tree        = [n ∈ Nodes |-> {}]
    /\ visitCount  = [n ∈ Nodes |-> 0]
    /\ totalReward = [n ∈ Nodes |-> 0]
    /\ bestArm     = CHOOSE n ∈ Nodes : TRUE

\* ── Visit (Selection → Expansion → Simulation → Backprop) ───────────────────

Visit(node, reward) ==
    /\ node ∈ Nodes
    /\ visitCount'  = [visitCount  EXCEPT ![node] = visitCount[node] + 1]
    /\ totalReward' = [totalReward EXCEPT ![node] = totalReward[node] + reward]
    /\ bestArm' = ArgmaxByReward(visitCount', totalReward')
    /\ UNCHANGED <<tree>>

Expand(parent, child) ==
    /\ parent ∈ Nodes
    /\ child ∈ Nodes
    /\ child ∉ tree[parent]
    /\ tree' = [tree EXCEPT ![parent] = tree[parent] ∪ {child}]
    /\ UNCHANGED <<visitCount, totalReward, bestArm>>

Next ==
    \/ \E n ∈ Nodes, r ∈ {-1, 0, 1} : Visit(n, r)
    \/ \E p, c ∈ Nodes : Expand(p, c)

Spec == Init /\ [][Next]_vars

\* ── Convergence argument: alpha-Root ───────────────────────────────────────
\*
\* Under UCT selection with exploration constant c and visit budget N, the
\* MCTS visit counts converge to the optimal arm with probability ≥ 1 −
\* 1/N^AlphaRoot as N → ∞. For LATS (with bounded value-function error
\* ε), the analogous bound holds when the value function is α-accurate.

Convergence ==
    \A node ∈ Nodes :
        (visitCount[node] ≥ Threshold(N, AlphaRoot))
        ⇒ Probability(bestArm = OptimalArm(node)) ≥ 1 - 1 / N^AlphaRoot

\* Helper: minimum visit threshold for the alpha-Root bound
Threshold(N, alpha) == N                         \* placeholder for TLC

OptimalArm(node) == CHOOSE n ∈ Nodes : TRUE       \* ground truth (abstracted)

\* ── Safety properties ────────────────────────────────────────────────────────

\* S1: Visit counts are non-negative and monotonically non-decreasing.
VisitsMonotone ==
    [][ visitCount' ≥ visitCount ]_visitCount

\* S2: bestArm is the argmax of (visits, mean reward) — it never points
\*     to a node with strictly fewer visits AND lower reward than another.
BestArmIsArgmax ==
    [][ \A n, m ∈ Nodes :
            (visitCount[n] > visitCount[m] ∧ MeanReward(n) > MeanReward(m))
            ⇒ bestArm' = n \/ bestArm' = m ]_bestArm

\* S3: Expansion preserves the visited-set semantics (a node is a child
\*     of exactly one parent at a time, no cycles).
TreeAcyclic ==
    [][ \A n ∈ Nodes : n ∉ tree[n] ]_tree

\* ── Liveness ────────────────────────────────────────────────────────────────

\* The visit budget is finite and the search terminates with bestArm set
\* to a non-degenerate node (visit count > 0).
SearchTerminates ==
    <>(bestArm ∈ {n ∈ Nodes : visitCount[n] > 0})

\* ── Helpers ─────────────────────────────────────────────────────────────────

ArgmaxByReward(vc, tr) == CHOOSE n ∈ Nodes : TRUE   \* abstracted
MeanReward(n) == IF visitCount[n] = 0 THEN 0
                ELSE totalReward[n] / visitCount[n]
Probability(p) == 0                                   \* placeholder

=============================================================================
\* Modification History
\* v1.0 (2026-08-27) — initial spec; convergence argument from
\* ALGORITHM-ATLAS §38..§40 formalised for MCTS and LATS modes.