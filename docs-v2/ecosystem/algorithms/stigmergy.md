---
layout: default
title: Stigmergy — Indirect Coordination
parent: Algorithms
nav_order: 7
permalink: /ecosystem/algorithms/stigmergy/
---

# Stigmergy — Indirect Coordination

## What is Stigmergy?

**Stigmergy** is a mechanism of indirect coordination between agents:
agents modify a shared environment, and other agents respond to those
modifications rather than to direct messages. Famous in ant colonies —
each ant leaves pheromones that other ants follow.

MATRIX uses stigmergy for **federation node discovery and consensus**.
Instead of nodes sending messages to each other, they modify a shared
"pheromone table" that other nodes read.

## Mathematical Foundation

The pheromone table is a function:

```
pheromone(node_A, node_B) → float in [0, 1]
```

When node A successfully completes a task involving node B, it deposits
pheromone:

```
pheromone(A, B) += δ
```

Pheromone evaporates over time:

```
pheromone(A, B) *= (1 - ε)  per tick
```

Where δ is the deposit rate (e.g., 0.1) and ε is the evaporation rate
(e.g., 0.01 per second).

A node selecting a partner picks the highest pheromone:

```
partner = argmax_B pheromone(self, B)
```

## Implementation

Source: [`matrix-core/src/main/java/io/matrix/federation/stigmergy/`](https://github.com/AlexanderNarbaev/agi/tree/develop/matrix-core/src/main/java/io/matrix/federation/stigmergy)

Key classes:
- `PheromoneTable` — distributed hash map with TTL-based evaporation
- `StigmergicRouter` — partner selection algorithm
- `EvaporationDaemon` — background tick that decays pheromone values

Performance:
- **Deposit**: ~50µs
- **Lookup top-K**: ~1ms for K=10 from 1M entries

## History

Stigmergy was coined by **Pierre-Paul Grassé** (1959) studying termite
nest construction. Applied to:

- **Ant Colony Optimization** (Dorigo, 1992) — combinatorial optimization
- **Swarm robotics** (Şahin, 2005) — physical robot coordination
- **Distributed systems** (various, 2010s) — eventually-consistent coordination

MATRIX uses a simplified version of ant colony optimization for node
discovery, not for path planning.

## Pros

- ✅ Decentralized (no central coordinator)
- ✅ Self-healing (failed nodes' pheromone evaporates)
- ✅ Eventually consistent
- ✅ Scales to 1000+ nodes

## Cons

- ❌ Convergence is slow (takes many ticks)
- ❌ Sensitive to evaporation rate (too fast = no signal, too slow = stale)
- ❌ Doesn't handle adversarial nodes (Byzantine stigmergy is an open problem)

## When Stigmergy runs in MATRIX

```
[Node A joins federation]
  ↓
[PheromoneTable.deposit(A, default_partner)]
  ↓
[Node B looks up partner]
  ↓
[StigmergicRouter.select()]   ← returns A
  ↓
[Nodes A and B coordinate]
```

The pheromone table is also used for **task routing** — a node with too
many pending requests deposits distress pheromone that signals other
nodes to send help.

## Federate endpoint integration

When you call `POST /v1/federate`, your node is added to the pheromone
table:

```java
PheromoneTable.shared.deposit(nodeId, default_partner, initialPheromone=0.5);
```

When the node receives requests, it looks up the best partner:

```java
StigmergicRouter router = new StigmergicRouter(self, PheromoneTable.shared);
Node partner = router.select();  // highest-pheromone partner
```

## Code example

```java
// Node A deposits after successful coordination
PheromoneTable.shared.deposit("node_A", "node_B", 0.8);

// Evaporation over time
for (int t = 0; t < 100; t++) {
    PheromoneTable.shared.evaporate(0.01);  // 1% per tick
}
// Now: pheromone(A, B) = 0.8 * 0.99^100 ≈ 0.29

// Node C selects partner
Node partner = new StigmergicRouter("node_C", PheromoneTable.shared).select();
// partner = argmax_B pheromone(C, B)
```

---

**Last updated:** 2026-09-21 (Wave T-03)
