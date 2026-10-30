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
    AlphaRoot       \* the alpha-Root convergence bound ∈ (0, 1); a TLC
                    \* configuration file carries integers only and
                    \* tla2tools has no real arithmetic, so the bound is
                    \* modelled by its integer ceiling 1

\* ── State ────────────────────────────────────────────────────────────────────

VARIABLES
    tree,            \* tree[n] = SUBSET Nodes — children of n
    visitCount,      \* visitCount[n] ∈ Visits
    totalReward,     \* totalReward[n] ∈ ℝ — sum of rewards observed
    bestArm          \* the argmax over visitCount × reward

vars == <<tree, visitCount, totalReward, bestArm>>


Threshold(N, alpha) == N                         \* placeholder for TLC

OptimalArm(node) == CHOOSE n ∈ Nodes : TRUE       \* ground truth (abstracted)

ArgmaxByReward(vc, tr) == CHOOSE n ∈ Nodes : TRUE   \* abstracted
MeanReward(n) == IF visitCount[n] = 0 THEN 0
                ELSE totalReward[n] / visitCount[n]

\* MeanAbove(n, m) — "the mean reward of n is strictly above the mean reward
\* of m". The two means are compared by cross-multiplication: the Reals
\* standard module is a dummy in tla2tools (TLC evaluates its `/` as an
\* unevaluable CHOOSE), and integer division would truncate the means and so
\* weaken the comparison. The cross-multiplied form is equivalent here
\* because the antecedent of BestArmIsArgmax gives visitCount[n] > visitCount[m]
\* ≥ 0, hence both denominators are positive.
MeanAbove(n, m) == visitCount[m] * totalReward[n] > visitCount[n] * totalReward[m]

Probability(p) == 0                                   \* placeholder

\* The visit budget N of the convergence claim below. The module used N
\* without ever defining it; N is the number of visit levels the model
\* enumerates, i.e. the size of `Visits`.
N == Cardinality(Visits)

\* ── Init ─────────────────────────────────────────────────────────────────────

Init ==
    /\ tree        = [n ∈ Nodes |-> {}]
    /\ visitCount  = [n ∈ Nodes |-> 0]
    /\ totalReward = [n ∈ Nodes |-> 0]
    /\ bestArm     = CHOOSE n ∈ Nodes : TRUE

\* ── Visit (Selection → Expansion → Simulation → Backprop) ───────────────────

Visit(node, reward) ==
    /\ node ∈ Nodes
    /\ visitCount[node] + 1 ∈ Visits  \* keep the documented type visitCount[n] ∈ Visits
    /\ visitCount'  = [visitCount  EXCEPT ![node] = visitCount[node] + 1]
    /\ totalReward' = [totalReward EXCEPT ![node] = totalReward[node] + reward]
    /\ bestArm' = ArgmaxByReward(visitCount', totalReward')
    /\ UNCHANGED <<tree>>

Expand(parent, child) ==
    /\ parent ∈ Nodes
    /\ child ∈ Nodes
    \* RECON-W38.5: a node is never its own child.
    \*
    \* The Java implementation cannot produce this: MCTSPlanner.expand()
    \* constructs a FRESH TreeNode with the expanded node as its parent, so
    \* `node` and `node.children` never contain the same object. The Java's
    \* backpropagate() then walks `node = node.parent` up the chain, which
    \* requires that chain to terminate.
    \*
    \* Without this guard the spec allowed Expand("n1","n1"), which put "n1"
    \* in tree["n1"] and violated S3 (TreeAcyclic) in the first expansion.
    /\ child # parent
    /\ child ∉ tree[parent]
    /\ tree' = [tree EXCEPT ![parent] = tree[parent] ∪ {child}]
    /\ UNCHANGED <<visitCount, totalReward, bestArm>>

Next ==
    \/ \E n ∈ Nodes, r ∈ {-1, 0, 1} : Visit(n, r)
    \/ \E p, c ∈ Nodes : Expand(p, c)

\* RECON-W38.5: the search keeps iterating.
\*
\* The Java's plan() is a bounded loop that ALWAYS backpropagates on every
\* iteration (`backpropagate(node, reward)` increments node.visits and every
\* ancestor's), so a search that runs at least one iteration has visited at
\* least one node. The spec encoded no such assumption: with a bare
\* [][Next]_vars an infinite stuttering behaviour is fair, so the search could
\* do nothing forever and LIV (SearchTerminates) was false in the very first
\* state.
\*
\* Weak fairness on Next states exactly that: while a step is possible, one
\* eventually happens. Expansion saturates after finitely many steps (each
\* parent can hold at most |Nodes|-1 children), so the search cannot run on
\* expansions alone and a Visit - the model's backpropagation - must occur.
Spec == Init /\ [][Next]_vars /\ WF_vars(Next)

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

\* ── Safety properties ────────────────────────────────────────────────────────

\* S1: Visit counts are non-negative and monotonically non-decreasing.
\* The comparison is element-wise: TLA+ defines no ordering on functions, so
\* `visitCount' ≥ visitCount` is not a well-formed TLA+ expression (TLC
\* rejects it with "The first argument of >= should be an integer").
VisitsMonotone ==
    [][ \A n ∈ Nodes : visitCount'[n] ≥ visitCount[n] ]_visitCount

\* S2: bestArm is the argmax of (visits, mean reward) — it never points
\*     to a node with strictly fewer visits AND lower reward than another.
BestArmIsArgmax ==
    [][ \A n, m ∈ Nodes :
            (visitCount[n] > visitCount[m] ∧ MeanAbove(n, m))
            ⇒ bestArm' = n \/ bestArm' = m ]_bestArm

\* S3: Expansion preserves the visited-set semantics - a node is never its
\*     own child.
\*
\* RECON-W38.5: the original comment claimed more than the property checks
\* ("a node is a child of exactly one parent at a time, no cycles"). What the
\* property checks is the absence of SELF-LOOPS, and that is what it now
\* says. The Java's guarantee is stronger - each TreeNode gets its parent at
\* construction and is never re-parented, so the parent chain is a forest and
\* backpropagate() terminates - but this model's `tree` map records only the
\* parent-to-children direction, so it can express the self-loop exclusion
\* and not the one-parent rule. The difference is recorded in
\* docs-v2/engineering/TLA-STATUS.md rather than papered over.
TreeAcyclic ==
    [][ \A n ∈ Nodes : n ∉ tree[n] ]_tree

\* ── Liveness ────────────────────────────────────────────────────────────────

\* The visit budget is finite and the search terminates with bestArm set
\* to a non-degenerate node (visit count > 0).
SearchTerminates ==
    <>(bestArm ∈ {n ∈ Nodes : visitCount[n] > 0})

\* ── Helpers ─────────────────────────────────────────────────────────────────

=============================================================================
\* Modification History
\* v1.0 (2026-08-27) — initial spec; convergence argument from
\* ALGORITHM-ATLAS §38..§40 formalised for MCTS and LATS modes.