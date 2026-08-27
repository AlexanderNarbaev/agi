# LONGTERM_PLAN — Delivery Schedule and Dependencies

**Status:** normative · **Layer:** orchestration · **Date:** 
phases collapsed, baseline re-anchored against current code state;
archive reference added.

## 1. Baseline ()

| Item | Value |
|----------------------------|----------------------------------------|
| Version | v1.2.0 |
| Tests | 1 055+ (`./gradlew test`) |
| Coverage | ≥ 82 % (JaCoCo gate) |
| Stack | Java 25 / Quarkus 3.37.3 / Pekko 1.6.0|
| Infra | Compose (Prometheus, Jaeger, Grafana, Kafka, Postgres) |
| CI/CD | GitHub Actions |
| Specs | L0–L19 (L0/L4/L6 rewrites normative) |

Implemented in code: BirUnit, TruthTable, DecisionTree, GA,
clusters, Mediator hierarchy, signal protocols, PoA consensus,
ethical filter, snapshots, Noosphere registry/index/credits,
Cauldron, HADES, Digital Shadow.

## 2. Phase Schedule

| Phase | Name | Status | Depends on |
|-------|-------------------------------|----------|------------|
| 1.1 | Core finalisation | done | — |
| 1.2 | Infra + DevOps | done | 1.1 |
| 1.3 | Pilot #1 (Minecraft GridWorld)| planned | 1.1 |
| 1.4 | Pilot #2 (Proactive chatbot) | planned | 1.1 |
| 2 | Production-grade platform | planned | 1.2 |
| 3 | Pilots #3–7 (smart home, arm, Cauldron, HADES, Noosphere) | planned | 1.2, 1.4 |
| 4 | Physical interfaces (ESP32, ROS2, FPGA) | planned | 1.1 |
| 5 | Community + governance | partial | — |
| 6 | Education (video, sandbox) | planned | 5, 1.3 |
| 7 | Business model | planned | 2, 5, 6 |
| 8 | Formal verification | planned | 1.1 |

## 3. Pilot #1 — Minecraft GridWorld

Sub-tasks: simulator (terrain, food, hazards, day/night);
4–8-neuron agent; GA with elitism; Jupyter notebook; video of
generations 0 / 50 / 200. Acceptance: survival > 80 % by
generation 200, video published. Spec: L13 §2, L5, L15 §4.3.

## 4. Pilot #2 — Proactive Chatbot

Sub-tasks: Telegram bot; multimodal proxy text ↔ binary vector;
`D_social` driver for unsolicited initiation; ethical filter on
adversarial inputs; interpretability chain; fact learning via
mutation. Acceptance: 100 % block on Three-Prohibition inputs;
proactive initiative observable. Spec: L13 §3, L4, L5, L7.

## 5. Production-Grade Platform

mTLS via Istio/Linkerd; Network Policies; Chaos Mesh drills
(`kill pod`, network partition); SLO/SLI dashboard with error
budget; Alertmanager → Slack/Telegram; Thanos or VictoriaMetrics
for long-term metrics. Spec: L9, L10.

## 6. Community and Education

CONTRIBUTING.md, CODE_OF_CONDUCT, SECURITY.md (done). Remaining:
GitHub Discussions, Matrix/Discord rooms, Weblate translations,
video "What is MATRIX?" (5 min), WASM Playground, Jupyter
notebooks, video course (7 modules), advanced course (8 weeks,
certification). Spec: L11, L15, L17, L19, L20.

## 7. Formal Verification

TLA+ model-checking of consensus and FROZEN immutability; ethical
unit tests covering adversarial prompts, filter bypass attempts,
and structural hazard scenarios. Spec: L8 §3.4.

## 8. Dependency Highlights

- Pilots (#1, #2) unlock Education and physical interfaces.
- Production-grade (#2) + Community (#5) + Education (#6)
 unlock Business (#7).
- Formal Verification (#8) underwrites certifications in #7 and
 university outreach.
- Dependency changes require an RFC (L11 §4.2).

## 9. Update Protocol

Weekly progress review. On phase close: update WAL and
`engineering/SDD-COVERAGE.md`. On priority change: RFC. Phases
may be reordered when evidence warrants.

> framed the schedule as a "march toward v2.0.0 ecosystem". The
> v4 text reframes it as a dependency-ordered engineering plan.
> Archive copy:

Next: L0 Manifesto — the axioms and prohibitions that bound all
phases above.