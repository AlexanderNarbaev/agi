---
layout: default
title: MCTS — Monte Carlo Tree Search
parent: Algorithms
nav_order: 3
permalink: /ecosystem/algorithms/mcts/
---

# MCTS — Monte Carlo Tree Search

## What is MCTS?

**MCTS** is a planning algorithm that searches a decision tree by simulating
random playouts. Famous for defeating humans in Go (AlphaGo, 2016).

## Mathematical Foundation

MCTS has 4 phases, repeated until a budget (time or iterations) is exhausted:

### 1. Selection

Starting from the root, descend the tree using **UCB1** (Upper Confidence Bound):

```
UCB1(node) = (wins / visits) + c × √(ln(parent.visits) / visits)
```

The constant `c` controls exploration vs exploitation (typically √2 ≈ 1.414).

### 2. Expansion

When a leaf node is reached, add one or more child nodes representing the
next possible actions.

### 3. Simulation (Rollout)

From the new node, simulate random play to the end of the episode. Record
the outcome (win/loss).

### 4. Backpropagation

Update the visit count and win count for all nodes on the path from root
to the new node.

After the budget is exhausted, select the child with the highest visit count.

## Implementation

Source: [`matrix-core/src/main/java/io/matrix/mcts/`](https://github.com/AlexanderNarbaev/agi/tree/develop/matrix-core/src/main/java/io/matrix/mcts)

Key classes:
- `TreeNode` — state, parent, children, wins, visits
- `MctsPlanner` — runs the 4-phase loop
- `RolloutPolicy` — random vs heuristic simulation

Performance:
- **1,000 iterations**: ~50ms
- **10,000 iterations**: ~500ms
- **100,000 iterations**: ~5s

## History

MCTS was introduced by **Rémi Coulom** (2006, Crazy Stone) and applied to Go
by **AlphaGo** (Silver et al., DeepMind, 2016).

MATRIX uses MCTS for action sequence planning over the BIR rule base.
Not for game-playing per se.

## Pros

- ✅ Anytime algorithm (can stop at any iteration)
- ✅ Doesn't require a heuristic evaluation function
- ✅ Scales with compute (more iterations = better plans)
- ✅ Asymmetric tree growth (focuses on promising branches)

## Cons

- ❌ Random rollouts can be wasteful if domain has structure
- ❌ Requires many iterations for high-quality decisions
- ❌ State representation must be hashable for caching

## When MCTS runs in MATRIX

```
[BIR_RULES_FIRED]
  ↓
[HDC_MEMORY_RETRIEVED]
  ↓
[MCTS_PLAN_SELECTED]   ← MCTS searches action sequences
   ├─ root: current state (input + facts + memories)
   ├─ children: possible next actions (BIR rule firings)
   ├─ rollout: simulate each plan to completion
   └─ best: highest expected value plan
  ↓
[MODULATORS_APPLIED]
```

## Code example

```java
MctsPlanner planner = new MctsPlanner(
    rootState,
    rolloutPolicy,
    iterations = 10_000,
    explorationC = 1.414
);

TreeNode bestChild = planner.run();
ActionSequence plan = bestChild.actionSequence();
```

---

**Last updated:** 2026-09-21 (Wave T-03)
