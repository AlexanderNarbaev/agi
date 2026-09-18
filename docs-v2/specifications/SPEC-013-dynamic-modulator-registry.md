# SPEC-013: Dynamic Modulator Registry

## Overview
Динамический справочник биохимических модуляторов (гормонов, нейромедиаторов, сигналов) 
с поддержкой консенсусного обновления и FROZEN-ограничений безопасности.

## Motivation
Отказ от фиксированного набора из 7 гормонов в пользу расширяемого реестра, который:
- Позволяет системе эволюционировать и добавлять новые типы модуляторов
- Поддерживает разные контексты (песочница vs production)
- Обеспечивает безопасность через capability-based доступ
- Синхронизируется между узлами федерации через консенсус

## Requirements

### Functional
1. **R1**: Создание/обновление/удаление модуляторов только через консенсус
2. **R2**: Уровни доступа на основе CapabilityLevel (L0-L7)
3. **R3**: FROZEN-зоны для критических параметров безопасности
4. **R4**: Поддержка разных типов данных (float, int, bool, vector)
5. **R5**: Версионирование реестра с Merkle root для синхронизации
6. **R6**: Кэширование в Redis для ускорения инференса
7. **R7**: Персистентное хранение в PostgreSQL с CQRS/ES

### Non-Functional
1. **NF1**: Задержка чтения < 1ms (кэш), < 10ms (БД)
2. **NF2**: Доступность 99.9% для production узлов
3. **NF3**: Консистентность eventual с timeout 5s для консенсуса
4. **NF4**: Поддержка до 10,000 модуляторов в реестре
5. **NF5**: Совместимость с ProtoBuf v3+

## Architecture

### Components
```
┌─────────────────┐     ┌──────────────────┐     ┌─────────────────┐
│   Node Local    │────▶│  Consensus       │────▶│  Global         │
│   Cache (Redis) │     │  Engine (Pekko)  │     │  Store (PG)     │
└─────────────────┘     └──────────────────┘     └─────────────────┘
       ▲                        ▲                        ▲
       │                        │                        │
       ▼                        ▼                        ▼
┌─────────────────┐     ┌──────────────────┐     ┌─────────────────┐
│   Inference     │     │  Proposal        │     │  Event          │
│   Engine        │     │  Validator       │     │  Store          │
└─────────────────┘     └──────────────────┘     └─────────────────┘
```

### Data Flow
1. **Чтение**: Node Cache → (miss) → Global Store → Cache
2. **Запись**: Proposal → Consensus Vote → Apply → Event Store → Global Store → Cache Invalidation
3. **Синхронизация**: Federation Digest → Delta Sync → Merkle Verification

## Specification

### Modulator Schema
```protobuf
ModulatorDefinition {
  string id = UUID v7
  string name = "cortisol_like_stress"
  ModulatorType type = HORMONE
  DataType value_type = FLOAT32
  
  ModulatorDefaults {
    default_value = 0.5
    min_allowed = 0.0
    max_allowed = 1.0
    learning_rate = 0.01
    decay_rate = 0.001
  }
  
  SafetyConstraints {
    frozen = false
    requires_consensus = true
    min_consensus_threshold = 67  // 67% голосов
    min_capability = L2_ADULT
  }
}
```

### Consensus Protocol
```
Proposal Lifecycle:
  INITIATED → VOTING → APPROVED/REJECTED/VETOED → APPLIED → CONFIRMED

Voting Rules:
  - L0-L1: No vote rights (только восприятие)
  - L2: 1 vote, weight=1.0
  - L3: 1 vote, weight=1.5
  - L4: 1 vote, weight=2.0
  - L5: 1 vote, weight=3.0
  - L6: 1 vote, weight=5.0
  - L7: VETO power + 1 vote, weight=10.0

Thresholds:
  - Standard: 67% approval, no veto
  - Critical (safety): 80% approval, no veto
  - Emergency: 50% approval, но требует 2× L7 confirmation
```

### FROZEN Constraints
```yaml
frozen_modulators:
  - id: "ethical_no_harm_threshold"
    reason: "CONSTITUTION Article IV"
    immutable: true
    
  - id: "consensus_min_threshold"
    reason: "CONSTITUTION Article II"
    min_value: 0.5
    
  - id: "capability_progression_rate"
    reason: "Prevent rapid privilege escalation"
    max_change_per_hour: 0.1
```

## Implementation Plan

> **Note on numbering:** W347-W351 are the foundation waves that produced this spec
> (ProtoBuf schema, DESIGN-67, PROPOSAL WAL split, SPEC-014, extract-waves.py).
> Implementation starts at **W352** (per SESSION-W347-W351-summary.md canonical numbering).
> The phases below are renumbered accordingly.

### Phase 1: Core (W352-W355)
- [ ] ProtoBuf schema compilation
- [ ] Redis cache layer (Reactive Redis Client)
- [ ] PostgreSQL schema with event sourcing tables
- [ ] Basic CRUD API (REST + gRPC)

### Phase 2: Consensus (W356-W360)
- [ ] Pekko cluster setup
- [ ] Proposal engine state machine
- [ ] Voting algorithm implementation
- [ ] Merkle tree delta sync

### Phase 3: Integration (W361-W365)
- [ ] Integration с BiochemicalMediator
- [ ] GPU kernel integration (CUDA/OpenCL)
- [ ] Telemetry & metrics (Prometheus)
- [ ] TLA+ specification for consensus

### Phase 4: Testing (W366-W370)
- [ ] Property-based tests (290+ свойств)
- [ ] Chaos engineering (Chaos Mesh)
- [ ] Performance benchmarks (JMH)
- [ ] Security audit (OWASP)

## Metrics & Observability

### Key Metrics
```promql
# Consensus latency
histogram_quantile(0.95, rate(consensus_vote_duration_seconds_bucket[5m]))

# Cache hit rate
rate(redis_cache_hits_total[5m]) / rate(redis_cache_requests_total[5m])

# Proposal success rate
sum(rate(proposal_status_total{status="approved"}[5m])) 
/ sum(rate(proposal_status_total[5m]))

# Modulator read latency
histogram_quantile(0.99, rate(modulator_read_latency_seconds_bucket[5m]))
```

### Dashboards
- Real-time консенсус статус (pending/approved/rejected)
- Heatmap активных модуляторов по узлам
- Reputation score distribution
- FROZEN violations timeline

## Security Considerations

### Threat Model
1. **Sybil Attack**: Mitigated by capability-based reputation
2. **Eclipse Attack**: Mitigated by multi-path digest sync
3. **Privilege Escalation**: Mitigated by slow capability progression
4. **Data Poisoning**: Mitigated by consensus validation + FROZEN zones

### Audit Trail
Все изменения логируются в Event Store с:
- Proposer node ID
- Timestamp (nanoseconds)
- Vote breakdown
- Reasoning hash (для verifiability)

## Dependencies
- Redis 7+ (cluster mode)
- PostgreSQL 15+ (with logical replication)
- Apache Pekko 1.0+
- ProtoBuf 3.21+
- TLA+ Toolbox (для формальной верификации)

## Risks & Mitigations

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| Consensus deadlock | Medium | High | Timeout + emergency L7 override |
| Cache inconsistency | Low | Medium | TTL + version checking |
| GPU OOM | Medium | Low | Graceful fallback to CPU |
| Network partition | High | Medium | Eventual consistency + replay |

## References
- CONSTITUTION.md Article II, IV, V
- DESIGN-64 (Stratified Stochasticity)
- HYPOTHESIS H-050 (Arousal Dynamics)
- TLA+ Spec: Consensus.tla (extend for modulators)

## Acceptance Criteria
1. ✅ Все property tests проходят (290+ свойств × 1000 случаев)
2. ✅ P99 latency < 10ms для чтения модуляторов
3. ✅ Consensus сходится за < 5s при 100 узлах
4. ✅ FROZEN violations блокируются на 100%
5. ✅ TLA+ spec verified (no deadlocks, safety properties hold)
6. ✅ JaCoCo coverage ≥ 82%
