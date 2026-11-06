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

\* The reward values a SINGLE Visit can produce. The accumulated total
\* reward is a sum of those, so it is an integer that can exceed ±1 — the
\* first version of TypeOK bounded it by the per-visit domain and TLC
\* refuted that immediately, which is the type invariant doing its job.
RewardValues == -1..1

\* RECON-W38.5: the module had NO type invariant at all — the state of the
\* MCTS model was never checked against its own declared domains, and the
\* configuration file's only invariant was a property that could not fail
\* (see the convergence section below).
TypeOK ==
    /\ tree        ∈ [Nodes -> SUBSET Nodes]
    /\ visitCount  ∈ [Nodes -> Visits]
    /\ totalReward ∈ [Nodes -> Int]
    /\ bestArm     ∈ Nodes

\* The visit budget is finite: a node's count never leaves the modelled set.
\* (The Visit action's guard already enforces it; this states it as an
\* invariant of the reachable states.)
VisitBudgetRespected ==
    ∀n ∈ Nodes : visitCount[n] ∈ Visits

Threshold(N, alpha) == N                         \* placeholder for TLC

OptimalArm(node) == CHOOSE n ∈ Nodes : TRUE       \* ground truth (abstracted)

\* MeanAboveIn(vc, tr, n, m) — "n's mean reward is strictly above m's",
\* over an explicit count/reward pair (the state variables are not available
\* inside ArgmaxByReward, which is called with the POST-state).
\*
\* The comparison is by cross-multiplication for the reason given below
\* MeanAbove: the Reals standard module is a dummy in tla2tools, and integer
\* division would truncate the means. A node with no visits has no mean, so
\* it never beats anything.
\* The Java's rule for a node with no visits: io.matrix.federation.liquid
\* .TreeNode.getAverageReward() returns 0 when visits == 0, so such a node has
\* the mean 0 and is neither better nor worse than a node whose mean is 0.
\* (A first version of this operator treated a zero-visit node as having NO
\* mean at all, which let the argmax select a node with no visits while
\* another node had a strictly higher mean — TLC refuted the property, and
\* it was right.)
\* mean(n) > mean(m), i.e. tr[n]/vc[n] > tr[m]/vc[m], cross-multiplied as
\* tr[n]*vc[m] > tr[m]*vc[n]. (The operands are easy to swap; the original
\* MeanAbove had them the right way round and the first version of this
\* operator had them reversed, which TLC caught immediately.)
MeanAboveIn(vc, tr, n, m) ==
    IF vc[n] = 0 THEN
        IF vc[m] = 0 THEN FALSE ELSE tr[m] < 0
    ELSE IF vc[m] = 0 THEN tr[n] > 0
    ELSE tr[n] * vc[m] > tr[m] * vc[n]

\* ArgmaxByReward(vc, tr) — the node with the highest MEAN reward.
\*
\* RECON-W38.5: this was `CHOOSE n ∈ Nodes : TRUE`, i.e. an arbitrary node.
\* The Java it models is not arbitrary: io.matrix.federation.liquid.MCTSPlanner
\* .extractBestPath() walks the children and keeps the one with the strictly
\* greatest average reward ("if (child.getAverageReward() > bestReward)"), so
\* the FIRST child with the maximum wins a tie. The operator now implements
\* that rule: the chosen node has no rival with a strictly higher mean.
ArgmaxByReward(vc, tr) ==
    CHOOSE n ∈ Nodes : ∀m ∈ Nodes : MeanAboveIn(vc, tr, m, n) => FALSE

MeanReward(n) == IF visitCount[n] = 0 THEN 0
                ELSE totalReward[n] / visitCount[n]

\* MeanAbove(n, m) — "the mean reward of n is strictly above the mean reward
\* of m". The two means are compared by cross-multiplication: the Reals
\* standard module is a dummy in tla2tools (TLC evaluates its `/` as an
\* unevaluable CHOOSE), and integer division would truncate the means and so
\* weaken the comparison. The cross-multiplied form is equivalent here
\* because the antecedent of BestArmIsArgmax gives visitCount[n] > visitCount[m]
\* ≥ 0, hence both denominators are positive.
MeanAbove(n, m) == MeanAboveIn(visitCount, totalReward, n, m)

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
\*
\* RECON-W38.5: the claim used to be in the configuration file's INVARIANTS
\* list, and it could not fail:
\*   - Probability(p) was the placeholder 0;
\*   - OptimalArm(node) was `CHOOSE n ∈ Nodes : TRUE` — not a ground truth
\*     but an arbitrary node;
\*   - Threshold(N, alpha) was N, which the modelled visit budget never
\*     reaches, so the antecedent was unreachable.
\* A property that cannot fail is not a check, and leaving it in the CI's
\* invariant list gives false assurance: the gate reports a green that no
\* computation produced.
\*
\* The claim itself is a PROBABILISTIC convergence result from the MCTS
\* literature: it concerns the asymptotic distribution of visit counts under
\* a randomised selection rule, and TLC has no probability at all (the Reals
\* module in tla2tools is a dummy, and `1 / N^AlphaRoot` is not evaluable).
\* No finite model check can establish it.
\*
\* It is therefore recorded here as an IMPORTED CLAIM, marked UNVERIFIED, and
\* it is NOT in the configuration file:
\*
\*   The bound as written rests on the PAC-MCTS convergence result in the
\*   MCTS literature (the bandit-based Monte-Carlo planning line of work).
\*   The primary source was NOT re-read while this note was written and the
\*   exact bound form has NOT been verified against it — per META-R3 it is
\*   marked UNVERIFIED rather than asserted.
\*
\* What this module DOES check about selection is the deterministic part: the
\* selected arm is one of maximal mean reward (BestArmIsArgmax), the visit
\* counts never decrease (VisitsMonotone), the tree has no self-loops
\* (TreeAcyclic), the search terminates (SearchTerminates), and the state
\* respects its declared types (TypeOK).
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
\* RECON-W38.5: the old formula was "if n has more visits AND a higher mean
\* than m, then bestArm' is n or m" — which says nothing about what bestArm
\* IS, and could not fail while ArgmaxByReward returned an arbitrary node.
\* The property now states the Java's rule: bestArm' is a node of maximal
\* mean reward, compared over the POST-state (which is what the action
\* passes to ArgmaxByReward). The comparison is written "m above bestArm'",
\* i.e. NO rival may beat the selected arm - the argmax condition itself.
BestArmIsArgmax ==
    [][ \A m ∈ Nodes : MeanAboveIn(visitCount', totalReward', m, bestArm') => FALSE ]_bestArm

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