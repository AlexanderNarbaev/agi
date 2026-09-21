# DESIGN-67: Liquid Federation Architecture

## Overview
Архитектура жидкой федерации узлов без фиксированных ролей, где роль узла 
определяется динамически на основе контекста, репутации и capability level.

## Problem Statement
Традиционные архитектуры с фиксированными ролями (edge/home/office/cloud) не подходят для:
- Динамического перераспределения нагрузки при отключении узлов
- Эволюции системы от "младенца" к "взрослому" специалисту
- Консенсусного принятия решений с разными весами голосов
- Масштабирования до триллионов нейронов

## Solution
Жидкая федерация с:
1. **Динамическими ролями**: Infant → Learner → Adult → Specialist → Coordinator → Guardian
2. **Репутационной системой**: Вес голоса зависит от reputation score + capability level
3. **Консенсусом через Φ-гейты**: Federated digest с Merkle verification
4. **Самовосстановлением**: Автоматическое перераспределение при отказе узлов

## Architecture

### Node Roles (Dynamic)
```
┌──────────────┐     ┌──────────────┐     ┌──────────────┐
│   Infant     │────▶│   Learner    │────▶│    Adult     │
│  (L0-L1)     │     │    (L2)      │     │    (L3)      │
│  Perception  │     │  Learning    │     │  Full rights │
└──────────────┘     └──────────────┘     └──────────────┘
                            │                    │
                            ▼                    ▼
                   ┌──────────────┐     ┌──────────────┐
                   │  Specialist  │◀────│  Coordinator │
                   │    (L4-L5)   │     │  (temporary) │
                   │  High weight │     │  Load balance│
                   └──────────────┘     └──────────────┘
                            │
                            ▼
                   ┌──────────────┐
                   │   Guardian   │
                   │    (L6-L7)   │
                   │  Veto power  │
                   │  Ethics enft │
                   └──────────────┘
```

### Consensus Protocol
```
Proposal Flow:
  [Proposer] → [Broadcast] → [Vote Collection] → [Threshold Check] → [Apply/Reject]

Voting Weights:
  L0-L1: 0.0 (no vote, only perception)
  L2:    1.0
  L3:    1.5
  L4:    2.0
  L5:    3.0
  L6:    5.0
  L7:   10.0 + VETO

Thresholds:
  Standard:   Σ(weights_yes) / Σ(weights_total) ≥ 0.67
  Critical:   Σ(weights_yes) / Σ(weights_total) ≥ 0.80
  Emergency:  Σ(weights_yes) / Σ(weights_total) ≥ 0.50 + 2× L7 confirm
```

### Federation Digest
```protobuf
FederationDigest {
  digest_id = "uuid"
  timestamp_ns = 1234567890
  source_node_id = "node-42"
  merkle_root = sha256(modulator_delta + proposal_delta)
  modulator_delta = [new/updated/deleted modulators]
  proposal_delta = [new/completed proposals]
  total_nodes = 1000
  active_nodes = 847
  average_reputation = 0.73
}
```

### Synchronization Strategy
```
Level 0 (Local):   Redis cache (TTL 5min)
Level 1 (Regional): Pekko cluster (gossip protocol)
Level 2 (Global):  PostgreSQL with logical replication
Level 3 (Federated): Merkle tree delta sync (every 30s)

Conflict Resolution:
  - Last-writer-wins для non-critical updates
  - Consensus-required для safety constraints
  - L7 veto для emergency freezes
```

## Implementation Details

### Node Discovery
```java
// Pseudo-code
class FederationNode {
  String nodeId;
  NodeType role;  // Dynamic
  float reputationScore;
  CapabilityLevel capability;
  
  void heartbeat() {
    broadcast(NodeStatus.ONLINE);
  }
  
  void updateRole() {
    if (reputationScore > 0.9 && capability >= L4) {
      role = NodeType.SPECIALIST;
    }
  }
}
```

### Consensus Engine
```java
// Pseudo-code
class ConsensusEngine {
  PekkoCluster cluster;
  Map<String, Proposal> pendingProposals;
  
  CompletableFuture<VoteResult> vote(Proposal p) {
    var votes = cluster.broadcast(VoteRequest(p));
    var weightedSum = votes.stream()
      .mapToDouble(v -> v.weight * v.confidence)
      .sum();
    
    if (weightedSum >= p.requiredThreshold) {
      return apply(p);
    } else {
      return reject(p);
    }
  }
}
```

### Merkle Tree Sync
```
Tree Structure:
  Root Hash
  ├── Modulator Registry Hash
  │   ├── Modulator[0..999] Hash
  │   ├── Modulator[1000..1999] Hash
  │   └── ...
  ├── Proposal Log Hash
  │   ├── Proposal[0..99] Hash
  │   └── ...
  └── Node State Hash

Delta Sync:
  1. Compare root hashes
  2. If different, traverse tree
  3. Request missing branches
  4. Verify individual entries
  5. Apply delta
```

## Scaling to Trillions of Neurons

### Hierarchical Federation
```
Level 1: Micro-cluster (10-100 nodes)
  └── Local consensus (< 100ms)
  
Level 2: Meso-cluster (100-10,000 nodes)
  └── Regional consensus (< 1s)
  
Level 3: Macro-cluster (10k-1M nodes)
  └── Global consensus (< 10s)
  
Level 4: Federation of federations (1M+ nodes)
  └── Eventual consistency (minutes)
```

### Sharding Strategy
```
Shard Key: hash(node_id) % num_shards

Each shard maintains:
  - Local modulator subset
  - Local proposal log
  - Cross-shard digest pointers

Cross-shard queries:
  - Broadcast to relevant shards
  - Aggregate results
  - Timeout after 5s
```

## Security & Safety

### Threat Mitigation
| Threat | Mitigation |
|--------|------------|
| Sybil Attack | Capability-based reputation, slow progression |
| Eclipse Attack | Multi-path gossip, random peer selection |
| 51% Attack | Weighted voting, L7 veto |
| Data Poisoning | Consensus validation, FROZEN zones |
| Network Partition | Eventual consistency, replay on reconnect |

### FROZEN Enforcement
```java
// Pseudo-code
class FrozenFilter {
  Set<String> frozenModulators;
  Map<String, Float> minValues;
  Map<String, Float> maxValues;
  
  boolean validate(ModulatorUpdate update) {
    if (frozenModulators.contains(update.id)) {
      return false; // Immutable
    }
    if (update.value < minValues.get(update.id)) {
      return false; // Below minimum
    }
    if (update.value > maxValues.get(update.id)) {
      return false; // Above maximum
    }
    return true;
  }
}
```

## Metrics & Observability

### Key Metrics
```promql
# Federation health
federation_total_nodes
federation_active_nodes
federation_average_reputation

# Consensus performance
consensus_proposal_duration_seconds{quantile="0.95"}
consensus_vote_participation_rate
consensus_approval_rate

# Sync latency
merkle_sync_duration_seconds{level="global"}
delta_size_bytes

# Role distribution
federation_nodes_by_role{role="infant"}
federation_nodes_by_role{role="guardian"}
```

### Dashboards
- Federation topology map (real-time)
- Consensus heatmap by region
- Reputation distribution histogram
- Role progression timeline

## Testing Strategy

### Unit Tests
- Node role transitions
- Voting weight calculations
- Merkle tree construction
- FROZEN constraint validation

### Integration Tests
- Consensus convergence (100 nodes)
- Network partition recovery
- Cache invalidation propagation
- GPU fallback on OOM

### Chaos Engineering
- Random node failures
- Network delays (100ms-5s)
- Message loss (1-10%)
- Byzantine nodes (lying votes)

## Migration Path

> **Note on numbering:** W347-W351 are the foundation waves that produced this design
> (ProtoBuf schema, SPEC-013, this DESIGN-67, PROPOSAL WAL split, SPEC-014, extract-waves.py).
> Implementation starts at **W352**. The phases below are renumbered accordingly.

### Phase 1: Single Node (W352-W355)
- Local modulator registry
- Basic consensus simulation
- No federation yet

### Phase 2: Small Cluster (W356-W365) — unchanged
- 10-100 nodes
- Regional consensus
- Gossip protocol

### Phase 3: Large Scale (W366-W380) — unchanged
- 1000+ nodes
- Hierarchical federation
- Sharding implementation

### Phase 4: Production (W381+) — unchanged
- 10,000+ nodes
- Multi-region deployment
- Formal verification (TLA+)

## References
- SPEC-013: Dynamic Modulator Registry
- CONSTITUTION.md Article II, IV
- DESIGN-58: Capability Levels L0-L7
- TLA+ Spec: Consensus.tla (to be extended)

## Acceptance Criteria
1. ✅ Node roles change dynamically based on reputation
2. ✅ Consensus converges in < 5s for 100 nodes
3. ✅ Merkle sync detects and repairs inconsistencies
4. ✅ FROZEN violations blocked at 100%
5. ✅ System recovers from 50% node failure
6. ✅ TLA+ spec verifies no deadlocks
