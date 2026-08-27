# L8 — Roadmap, Integration, and Development Protocol

**Status:** normative · **Layer:** 8 (process) · **Date:** 
reframed against current code state; archive reference added.

## 1. Scope

Layer 8 binds L0–L7 into a phased delivery plan. It lists phases,
acceptance criteria, success metrics, and the iteration protocol
between humans (intent) and code-generation tools
(implementation). The protocol is a process specification.

## 2. Stack (Frozen)

Java 25 · Quarkus 3.37.3 · Apache Pekko 1.6.0 · Kafka 3.7 (KRaft)
· PostgreSQL 17 (R2DBC) · Redis 7 · Avro 1.12 · Gradle 9.x. Stack
changes require an RFC and review (CONSTITUTION II).

## 3. Phases

| Phase | Name | Window (2026) | Outcome |
|-------|-----------------------|---------------|----------------------------------|
| 0 | Spark | W01–W04 | MPDT eval; GA trial |
| 1 | Cell | W05–W10 | Cluster + single Mediator |
| 2 | Organism | W11–W18 | Cluster sharding, hierarchy |
| 2.5 | Formal Verification | W19–W22 | TLA+ for consensus + FROZEN |
| 3 | Noosphere | W23–W32 | Federation, multimodal proxy |
| 4 | Digital Shadow | W33–W40 | User-side protective module |
| 5 | Quantum Accelerators | TBD | SAT / min-form feasibility |
| 6 | Civilisational Bridge | W41–W52 | Cross-cultural protocols |
| 7 | Regenerative Economy | W53–W64 | Credit system, certification |

Each phase ends with a working artefact, tests green, and a WAL checkpoint.

## 4. Phase Acceptance (excerpt)

Phase 0 — Spark: a single BirUnit evolves under the L5 GA on a
synthetic task and improves fitness; coverage ≥ 80 %. Phase 1 —
Cell: 1k-neuron cluster survives snapshot/restore; Mediator runs
the driver loop. Phase 2 — Organism: two instances exchange
signals and reach PoA consensus; one-node loss tolerated. Phase
2.5: TLA+ model-checking of consensus and FROZEN immutability;
ethical adversarial tests pass. Phase 3: a snapshot publishes to
the federated pool, imports successfully, and HADES rolls back a
poisoned cluster without total stoppage. Phase 4: Digital Shadow
blocks adversarial content without breaking benign flows.

## 5. Iteration Protocol

Sprints are 1–2 weeks. Each sprint: (1) pick the next unchecked
item from the relevant level document; (2) assemble a prompt that
cites the level section; (3) generate code + tests; (4) run
targeted suites; (5) record evidence in WAL and `engineering/SDD-
COVERAGE.md`; (6) request review. Targeted suites: `./gradlew
:matrix-core:test --tests "io.matrix.<pkg>.*"`. Full suite only
by agreement (OOM risk). JMH gates via `--rerun-tasks
-PjmhBenchmark=Name` (e.g. `BatchStarBench`).

## 6. WAL Format

Each session ends with three lines:

```
Статус: <one sentence on what was done>
Активный этап: <phase / next step>
Защищённые зоны: <frozen components, hard constraints>
```

The format is operational. The status line must reference a
verifiable command and its exit code.

## 7. Success Metrics

| Metric | Target |
|---------------------------------------|------------|
| Test coverage | ≥ 82 % |
| Single-neuron lookup latency | < 10 ns |
| Cluster throughput | > 1e9 sig/s|
| HADES recovery | < 60 s |
| Formal verification of FROZEN | 100 % |
| Per-generation environment adaptation | ≤ 100 gens |

These are measurable gates, not workload-specific guarantees.

## 8. Disclaimers

The system is research-stage. Deployment in critical applications
requires human supervision (CONSTITUTION VI). Prohibited uses:
lethal autonomous systems without a human operator; surveillance
or coercion that removes voluntary consent; any use that
contradicts the Three Prohibitions.

> framed the system as a "path to AGI". The v4 text reframes the
> roadmap as a phased engineering plan with measurable gates.

Next: L9 Deployment — the operational runtime that hosts phases
0–7.