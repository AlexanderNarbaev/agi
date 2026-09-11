# REQUIREMENTS — раскладка видения на требования к подсистемам

**Статус:** normative · **Версия:** v1 · **Дата:** 2026-09-07

> Производное от `PARADIGM.md`. Группирует требования по
> функциональным доменам, чтобы каждое направление (память,
> этика, медиатор, восприятие, действие) было явно
> подвязано к общей целостной картине.

---

## R0. Глобальные инварианты

| ID | Требование | Источник |
|----|-----------|----------|
| R0.1 | Любой decision-path детерминирован (без RNG/wall-clock) | CONSTITUTION I |
| R0.2 | Рантайм не вызывает LLM | CONSTITUTION I + PARADIGM §3 |
| R0.3 | Любое решение проходит Action gate (4 каскада) | CONSTITUTION IV |
| R0.4 | K_MAX=20 (расширение через EXP) | CONSTITUTION II |
| R0.5 | Покрытие JaCoCo ≥82% на matrix-core | CONSTITUTION V |
| R0.6 | Все численные характеристики подтверждены EXP-прогоном | CONSTITUTION VI |
| R0.7 | Каждое решение = BRC-цепочка, аудит по `x-matrix-trace` | CONSTITUTION VIII |

---

## R1. Substrate (вычислительный субстрат)

| ID | Требование | Ссылка |
|----|-----------|--------|
| R1.1 | BirUnit: K_MAX=20 входов, бинарный выход | SPEC-001, CONSTITUTION II |
| R1.2 | BIR-формы: TruthTable / ClauseSet / BDD | SPEC-002 |
| R1.3 | Конвертация float→BIR детерминирована и проверена | SPEC-001 |
| R1.4 | BRC-step переход с TLA+ спецификацией | SPEC-002, FORMAL-CONTRACTS |
| R1.5 | K_MAX может быть увеличен через EXP с новыми измерениями | CONSTITUTION II (escape hatch) |
| R1.6 | Hash-chain аудит всех артефактов | FORMAL-CONTRACTS |

## R2. Knowledge (знание)

| ID | Требование | Ссылка |
|----|-----------|--------|
| R2.1 | Дистилляция знаний: Qwen (или другая LLM) генерирует корпус → булевы веса | PARADIGM §3 |
| R2.2 | Инструмент дистилляции в `tools/distill/` — изолирован от runtime | это решение интервью |
| R2.3 | Noosphere: shared knowledge pool с PoA-консенсусом | SPEC-003 |
| R2.4 | Knowledge hierarchy: эпизодическая / семантическая / процедурная | L5/L6 |
| R2.5 | Knowledge versioning с возможностью отката | snapshot/* |

## R3. Perception (восприятие)

| ID | Требование | Ссылка |
|----|-----------|--------|
| R3.1 | Текст → бинарный вектор (BPE/Hash/Booleanize) | SPEC-004 |
| R3.2 | Аудио/видео: плейсхолдер-сигналы + text fallback | SPEC-004 |
| R3.3 | Multimodal proxy text↔binary | SPEC-004 |
| R3.4 | Детерминированный Signal bus | DESIGN-06 |

## R4. Memory (память)

| ID | Требование | Ссылка |
|----|-----------|--------|
| R4.1 | M0: working memory (миллисекунды, в-процессе) | SPEC-011 |
| R4.2 | M1: episodic memory (секунды, между циклами) | SPEC-011 |
| R4.3 | M2: long-term (sqlite/redis) | SPEC-011 |
| R4.4 | TR-консолидация M2→M1 | DESIGN-19 (subconscious) |
| R4.5 | REM-консолидация M1→M0 | DESIGN-19 |
| R4.6 | SDM (Sparse Distributed Memory) reader | SdmReader |
| R4.7 | Persistent backend (SQLite/Redis) | PersistentHierarchicalMemory |

## R5. Consciousness (сознание)

| ID | Требование | Ссылка |
|----|-----------|--------|
| R5.1 | Attention router: top-down импульсы × bottom-up салиентность | SPEC-006 |
| R5.2 | Deliberation engine: BRC + MCTS/LATS | SPEC-006 |
| R5.3 | Budget (tokens/wallMs/birEvals) с детерминированным счётчиком | SPEC-006 |
| R5.4 | Prediction-error → arousal update | DESIGN-18 |
| R5.5 | ConsciousTrace append-only | SPEC-006 |
| R5.6 | BrainLoopService — production CDI wiring | DESIGN-18 |

## R6. Action (действие)

| ID | Требование | Ссылка |
|----|-----------|--------|
| R6.1 | ActionGate: 4-каскад (AdversarialInput → Ethical → Structural → Lie → FROZEN) | SPEC-006 |
| R6.2 | PlanPreprocessor (AC-3 fast-fail) | DESIGN-15 |
| R6.3 | PlanRunner с Hoare pre/post | DESIGN-17 |
| R6.4 | ActionArena | DESIGN-17 |
| R6.5 | PeriodicProactiveScanner — инициатива без входа | SPEC-005, L0 |

## R7. Reasoning (рассуждение)

| ID | Требование | Ссылка |
|----|-----------|--------|
| R7.1 | BRC-step (forward reasoning) | SPEC-002 |
| R7.2 | MCTS-visit с TLA+ спецификацией | SPEC-008 |
| R7.3 | LATS (Language Agents Tree Search) | SPEC-008 |
| R7.4 | Mediator consensus | SPEC-009 |
| R7.5 | PlaneРешение — каждое решение объяснимо через trace | FORMAL-CONTRACTS |

## R8. Mediator (иерархический координатор)

| ID | Требование | Ссылка |
|----|-----------|--------|
| R8.1 | DriverState, DriverType, Goal, Task | SPEC-009 |
| R8.2 | InstanceMediator с GoldenRatioAllocator | SPEC-009 |
| R8.3 | MetaGoalValidator | SPEC-009 |
| R8.4 | HierarchyMediator + scheduler | SPEC-009 |
| R8.5 | Взвешенный консенсус импульсов | PARADIGM §4.4 |

## R9. Ethics (этика)

| ID | Требование | Ссылка |
|----|-----------|--------|
| R9.1 | AdversarialInputFilter — reject injection | CONSTITUTION IV |
| R9.2 | EthicalFilter — основные правила | CONSTITUTION IV |
| R9.3 | StructuralSafetyGuard | CONSTITUTION IV |
| R9.4 | LieDetector | CONSTITUTION IV |
| R9.5 | FROZENFNLGuardian — единственный математически проверяемый носитель | CONSTITUTION III/IV |
| R9.6 | FROZEN-GDPR Escalator | CONSTITUTION IV |
| R9.7 | PeriodicProactiveScanner + ProactiveEthicalScanner | SPEC-005 |
| R9.8 | OutputSafetyFilter | DESIGN-19 |

## R10. Lifecycle (жизненный цикл)

| ID | Требование | Ссылка |
|----|-----------|--------|
| R10.1 | TaskCell с бюджетом CPU/mem/wall/ttl | DESIGN-12 |
| R10.2 | ConsolidationCycle (TR/REM) | DESIGN-19 |
| R10.3 | FreezeRecoveryManager | lifecycle/ |
| R10.4 | Snapshot + rollback | snapshot/ |

## R11. Federation (федерация)

| ID | Требование | Ссылка |
|----|-----------|--------|
| R11.1 | Decentralized digests (не сырые данные) | SPEC-009 |
| R11.2 | PoA consensus для смены FROZEN-FNL | FORMAL-CONTRACTS |
| R11.3 | FederatedMesh | algorithms/FederatedMesh |

## R12. Pilots (пилоты)

| ID | Требование | Ссылка |
|----|-----------|--------|
| R12.1 | Pilot #1 GridWorld (4-8 нейрон агент, GA) | L13 §2 |
| R12.2 | Pilot #2 Proactive chatbot (этический гейт + инициатива) | L13 §3 |
| R12.3 | Pilot #3 Smart home | L13 |
| R12.4 | Pilot #4 Robotic arm | L13 |
| R12.5 | Pilot #5 Cauldron | L13 |
| R12.6 | Pilot #6 HADES | L13 |
| R12.7 | Pilot #7 Noosphere | L13 |

## R13. Operations (эксплуатация)

| ID | Требование | Ссылка |
|----|-----------|--------|
| R13.1 | Helm/Compose деплой | operations/DEPLOYMENT |
| R13.2 | Prometheus/Grafana стек | L9-L10 |
| R13.3 | Chaos Mesh drills | L10 |
| R13.4 | SLO/SLI dashboard | L10 |
| R13.5 | Alertmanager → Slack/Telegram | L11 |

## R14. Community (сообщество)

| ID | Требование | Ссылка |
|----|-----------|--------|
| R14.1 | CONTRIBUTING.md, CODE_OF_CONDUCT, SECURITY.md | сделано |
| R14.2 | GitHub Discussions, Matrix/Discord | L11 |
| R14.3 | Weblate translations | L11 |
| R14.4 | "What is MATRIX?" 5-min видео | L15 |
| R14.5 | WASM Playground | L17 |
| R14.6 | Jupyter notebooks | L18 |
| R14.7 | Video course 7 modules + advanced 8 weeks | L19/L20 |

## R15. Business (бизнес-модель)

| ID | Требование | Ссылка |
|----|-----------|--------|
| R15.1 | Business model canvas | L14 |
| R15.2 | Ed-tech certificates | L19/L20 |
| R15.3 | FaaS tier | L14 |

---

## Приоритезация (для следующей волны)

Не "выбрать топ-3", а **закрыть петлю**:

### Фаза α (1-2 дня): Substrate готовность
- R1.1-R1.6: подтвердить инварианты ядра (K_MAX-тесты, hash-chain, TLA+)
- R0.7: x-matrix-trace в каждом decision-path
- Qwen-код → `tools/distill/` (отдельный пакет, не в brain/)

### Фаза β (5-7 дней): Cognition loop
- R3.* Perception → R5.* Attention/Deliberation → R6.* Gate/Action
- R9.* Этический гейт полный
- Простой демо end-to-end

### Фаза γ (5-7 дней): Memory & Learning
- R4.* Memory hierarchy
- R10.* Lifecycle (TR/REM consolidation)
- R2.2 Дистилляция через Qwen (offline, tools/distill/)
- R11.* Federation digests

### Фаза δ (3-5 дней): Pilots & Verification
- R12.1 GridWorld agent
- R12.2 Proactive chatbot
- TLA+ BRC-step, FrozenEthicalFNL, MCTS-visit
- EXP-отчёты

См. `engineering/PHASES.md` — детальный план RUN-ов.
